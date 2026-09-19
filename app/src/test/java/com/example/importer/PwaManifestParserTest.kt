package com.example.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PwaManifestParserTest {

  @get:Rule
  val tempFolder = TemporaryFolder()

  @Test
  fun testParseStandardManifest() {
    val rootDir = tempFolder.newFolder("pwa_root")
    val manifestFile = File(rootDir, "manifest.json")
    val dummyIcon = File(rootDir, "icon-512.png")
    dummyIcon.writeText("fake-image-bytes")

    manifestFile.writeText(
      """
      {
        "name": "Retro 2048 Game",
        "short_name": "2048",
        "theme_color": "#000000",
        "icons": [
          {
            "src": "icon-512.png",
            "sizes": "512x512",
            "type": "image/png"
          }
        ]
      }
      """.trimIndent()
    )

    val parsed = PwaManifestParser.parse(manifestFile, rootDir)
    assertEquals("Retro 2048 Game", parsed.name)
    assertEquals("2048", parsed.shortName)
    assertEquals("#000000", parsed.themeColor)
    assertNotNull(parsed.iconRelativePath)
    assertEquals(dummyIcon.absolutePath, parsed.iconRelativePath)
  }

  @Test
  fun testParseMissingManifestGracefully() {
    val rootDir = tempFolder.newFolder("empty_root")
    val missingFile = File(rootDir, "non_existent.json")

    val parsed = PwaManifestParser.parse(missingFile, rootDir)
    assertNull(parsed.name)
    assertNull(parsed.shortName)
    assertNull(parsed.iconRelativePath)
  }
}
