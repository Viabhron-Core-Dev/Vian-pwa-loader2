package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents an installed PWA stored locally in app sandboxed storage.
 */
@Entity(tableName = "pwas")
data class PwaEntity(
  @PrimaryKey
  val id: String,
  val title: String,
  val shortName: String? = null,
  val relativeRootDir: String,
  val entryHtmlPath: String = "index.html",
  val virtualHost: String,
  val iconPath: String? = null,
  val themeColorHex: String? = null,
  val sizeBytes: Long = 0L,
  val createdAt: Long = System.currentTimeMillis()
)
