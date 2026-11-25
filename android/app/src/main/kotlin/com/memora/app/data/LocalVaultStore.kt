package com.memora.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Local-first encrypted-boundary offline storage inside app-private filesDir.
 * Preserves local media files, `only_on_device` private memories, pending sync queue,
 * and benchmarks with zero network dependency.
 */
class LocalVaultStore(private val context: Context) {
  private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = false
  }

  private val vaultFile: File
    get() = File(context.filesDir, "memora_vault_v1.json")

  private val mediaDir: File
    get() = File(context.filesDir, "memora_media").apply { if (!exists()) mkdirs() }

  suspend fun loadSnapshot(): VaultSnapshot = withContext(Dispatchers.IO) {
    if (!vaultFile.exists()) return@withContext VaultSnapshot()
    runCatching {
      json.decodeFromString<VaultSnapshot>(vaultFile.readText())
    }.getOrElse { VaultSnapshot() }
  }

  suspend fun saveSnapshot(snapshot: VaultSnapshot) = withContext(Dispatchers.IO) {
    val tmp = File(context.filesDir, "memora_vault_v1.tmp")
    tmp.writeText(json.encodeToString(snapshot))
    tmp.renameTo(vaultFile)
  }

  suspend fun saveLocalImage(id: String, bitmap: Bitmap): Pair<String, String> =
    withContext(Dispatchers.IO) {
      val fileName = "$id.jpg"
      val outFile = File(mediaDir, fileName)
      outFile.outputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
      }

      // Create compact base64 thumbnail (<120KB) for instant cross-device Firestore sync
      val maxDim = 512f
      val scale = minOf(1f, maxDim / maxOf(bitmap.width, bitmap.height).coerceAtLeast(1))
      val thumb = if (scale < 1f) {
        Bitmap.createScaledBitmap(
          bitmap,
          (bitmap.width * scale).toInt().coerceAtLeast(1),
          (bitmap.height * scale).toInt().coerceAtLeast(1),
          true,
        )
      } else {
        bitmap
      }
      val baos = ByteArrayOutputStream()
      thumb.compress(Bitmap.CompressFormat.JPEG, 68, baos)
      val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
      fileName to "data:image/jpeg;base64,$base64"
    }

  fun loadLocalBitmap(keyOrThumb: String?): Bitmap? {
    if (keyOrThumb.isNullOrBlank()) return null
    return runCatching {
      if (keyOrThumb.startsWith("data:image/")) {
        val comma = keyOrThumb.indexOf(',')
        if (comma == -1) return null
        val bytes = Base64.decode(keyOrThumb.substring(comma + 1), Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
      } else {
        val f = File(mediaDir, keyOrThumb)
        if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
      }
    }.getOrNull()
  }

  fun deleteLocalMedia(key: String?) {
    if (key.isNullOrBlank()) return
    runCatching { File(mediaDir, key).delete() }
  }
}
