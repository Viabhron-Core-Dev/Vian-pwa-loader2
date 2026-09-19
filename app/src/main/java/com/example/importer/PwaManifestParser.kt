package com.example.importer

import org.json.JSONObject
import java.io.File

data class ParsedManifest(
  val name: String?,
  val shortName: String?,
  val themeColor: String?,
  val iconRelativePath: String?
)

object PwaManifestParser {

  fun parse(manifestFile: File, rootDir: File): ParsedManifest {
    if (!manifestFile.exists()) {
      return ParsedManifest(null, null, null, null)
    }

    return try {
      val jsonString = manifestFile.readText()
      val json = JSONObject(jsonString)

      val name = json.optString("name").takeIf { it.isNotBlank() }
      val shortName = json.optString("short_name").takeIf { it.isNotBlank() }
      val themeColor = json.optString("theme_color").takeIf { it.isNotBlank() }

      var bestIconPath: String? = null
      val iconsArray = json.optJSONArray("icons")
      if (iconsArray != null && iconsArray.length() > 0) {
        var largestSize = 0
        for (i in 0 until iconsArray.length()) {
          val iconObj = iconsArray.getJSONObject(i)
          val src = iconObj.optString("src")
          val sizes = iconObj.optString("sizes") // e.g. "192x192" or "512x512"

          val sizePx = parseSize(sizes)
          val cleanedSrc = src.removePrefix("/").removePrefix("./")
          val iconFile = File(manifestFile.parentFile ?: rootDir, cleanedSrc)

          if (sizePx >= largestSize && (iconFile.exists() || File(rootDir, cleanedSrc).exists())) {
            largestSize = sizePx
            bestIconPath = if (iconFile.exists()) iconFile.absolutePath else File(rootDir, cleanedSrc).absolutePath
          }
        }
      }

      ParsedManifest(
        name = name,
        shortName = shortName,
        themeColor = themeColor,
        iconRelativePath = bestIconPath
      )
    } catch (e: Exception) {
      ParsedManifest(null, null, null, null)
    }
  }

  private fun parseSize(sizes: String): Int {
    if (sizes.isBlank()) return 0
    return try {
      val firstPart = sizes.split(" ").firstOrNull() ?: sizes
      val dim = firstPart.split("x").firstOrNull()?.toIntOrNull()
      dim ?: 0
    } catch (e: Exception) {
      0
    }
  }
}
