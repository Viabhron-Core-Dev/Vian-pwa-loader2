package com.example.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.PwaEntity
import com.example.logkeeper.LogKeeperCatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

sealed class ImportResult {
  data class Success(val pwa: PwaEntity) : ImportResult()
  data class Error(val message: String) : ImportResult()
}

class PwaZipImporter(private val context: Context) {

  private val catcher = LogKeeperCatcher.getInstance(context)

  suspend fun importFromUri(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
    catcher.log("Importer", "Starting import from SAF URI: $uri")

    val originalFileName = queryFileName(uri) ?: "imported_app.zip"
    val pwaId = UUID.randomUUID().toString()
    val pwaTargetDir = File(context.filesDir, "pwas/$pwaId")

    try {
      if (!pwaTargetDir.exists()) {
        pwaTargetDir.mkdirs()
      }

      val inputStream: InputStream = context.contentResolver.openInputStream(uri)
        ?: return@withContext ImportResult.Error("Could not open file stream from selected document.")

      var totalBytes = 0L
      ZipInputStream(inputStream).use { zis ->
        var entry: ZipEntry? = zis.nextEntry
        while (entry != null) {
          val entryName = entry.name

          // Zip Slip path traversal security check
          val destFile = File(pwaTargetDir, entryName)
          val canonicalDestPath = destFile.canonicalPath
          val canonicalBaseDir = pwaTargetDir.canonicalPath

          if (!canonicalDestPath.startsWith(canonicalBaseDir + File.separator) && canonicalDestPath != canonicalBaseDir) {
            catcher.log("Importer", "Security Alert: Zip Slip attempted for entry $entryName", isError = true)
            throw SecurityException("Invalid zip entry path: $entryName")
          }

          if (entry.isDirectory) {
            destFile.mkdirs()
          } else {
            destFile.parentFile?.mkdirs()
            FileOutputStream(destFile).use { fos ->
              val buffer = ByteArray(8192)
              var len: Int
              while (zis.read(buffer).also { len = it } > 0) {
                fos.write(buffer, 0, len)
                totalBytes += len
              }
            }
          }
          zis.closeEntry()
          entry = zis.nextEntry
        }
      }

      catcher.log("Importer", "Extracted $totalBytes bytes into ${pwaTargetDir.name}")

      // Locate index.html
      val indexHtmlFile = findIndexHtml(pwaTargetDir)
      if (indexHtmlFile == null) {
        pwaTargetDir.deleteRecursively()
        catcher.log("Importer", "Validation error: No index.html found in zip", isError = true)
        return@withContext ImportResult.Error("Selected zip is not a valid PWA dist package: No index.html found.")
      }

      // Root dir for the PWA is the directory containing index.html
      val appRootDir = indexHtmlFile.parentFile ?: pwaTargetDir
      val relativeRootDir = appRootDir.relativeTo(context.filesDir).path

      // Search for manifest.json or manifest.webmanifest
      val manifestFile = findManifestFile(appRootDir)
      val parsedManifest = if (manifestFile != null) {
        PwaManifestParser.parse(manifestFile, appRootDir)
      } else {
        null
      }

      val fallbackTitle = originalFileName.removeSuffix(".zip").replace("_", " ").replace("-", " ")
      val pwaTitle = parsedManifest?.name ?: parsedManifest?.shortName ?: fallbackTitle
      val virtualHost = "pwa-$pwaId.pwalocal"

      val entity = PwaEntity(
        id = pwaId,
        title = pwaTitle,
        shortName = parsedManifest?.shortName,
        relativeRootDir = relativeRootDir,
        entryHtmlPath = "index.html",
        virtualHost = virtualHost,
        iconPath = parsedManifest?.iconRelativePath,
        themeColorHex = parsedManifest?.themeColor,
        sizeBytes = totalBytes,
        createdAt = System.currentTimeMillis()
      )

      catcher.log("Importer", "Successfully registered PWA '${entity.title}' ($virtualHost)")
      ImportResult.Success(entity)
    } catch (e: Exception) {
      pwaTargetDir.deleteRecursively()
      catcher.log("Importer", "Import failed: ${e.message}", isError = true, throwable = e)
      ImportResult.Error("Import failed: ${e.localizedMessage ?: "Unknown extraction error"}")
    }
  }

  private fun findIndexHtml(dir: File): File? {
    // Check direct child first
    val direct = File(dir, "index.html")
    if (direct.exists()) return direct

    // Check one level deep (e.g. dist/index.html or build/index.html)
    dir.listFiles()?.forEach { sub ->
      if (sub.isDirectory) {
        val nested = File(sub, "index.html")
        if (nested.exists()) return nested
      }
    }

    // Deep search fallback
    return dir.walkTopDown().maxDepth(3).firstOrNull { it.isFile && it.name.equals("index.html", ignoreCase = true) }
  }

  private fun findManifestFile(dir: File): File? {
    val candidates = listOf("manifest.json", "manifest.webmanifest", "app.webmanifest")
    for (candidate in candidates) {
      val direct = File(dir, candidate)
      if (direct.exists()) return direct
    }
    return dir.walkTopDown().maxDepth(2).firstOrNull { file ->
      file.isFile && (file.name.equals("manifest.json", ignoreCase = true) || file.name.endsWith(".webmanifest", ignoreCase = true))
    }
  }

  private fun queryFileName(uri: Uri): String? {
    return try {
      context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && nameIndex != -1) {
          cursor.getString(nameIndex)
        } else null
      }
    } catch (e: Exception) {
      null
    }
  }
}
