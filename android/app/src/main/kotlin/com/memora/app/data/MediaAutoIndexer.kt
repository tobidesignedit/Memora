package com.memora.app.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MediaCandidate(
  val dedupeKey: String,
  val contentUri: Uri,
  val displayName: String,
  val bucketName: String,
  val isScreenshot: Boolean,
  val dateAddedSec: Long,
)

/**
 * Permission-gated MediaStore scanner + live ContentObserver for automatic
 * Screenshots & Recent Photos indexing on Android.
 *
 * - Requires explicit user opt-in AND runtime permission:
 *   READ_MEDIA_IMAGES (Android 13+ / API 33+) or READ_EXTERNAL_STORAGE (API <= 32).
 * - Deduplicates via stable `ms:<id>_<dateModified>_<size>` keys.
 * - Supports folder filter ("screenshots" vs "all_photos") and OOM-safe downsampling.
 */
class MediaAutoIndexer(private val context: Context) {
  private var observer: ContentObserver? = null
  private val handler = Handler(Looper.getMainLooper())
  private var debounceRunnable: Runnable? = null

  fun requiredPermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      Manifest.permission.READ_MEDIA_IMAGES
    } else {
      Manifest.permission.READ_EXTERNAL_STORAGE
    }

  fun hasMediaPermission(): Boolean =
    ContextCompat.checkSelfPermission(context, requiredPermission()) ==
      PackageManager.PERMISSION_GRANTED

  fun startObserving(onNewMediaDetected: () -> Unit) {
    stopObserving()
    if (!hasMediaPermission()) return
    val obs = object : ContentObserver(handler) {
      override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        // Debounce burst MediaStore writes while screenshot finishes saving to disk
        debounceRunnable?.let { handler.removeCallbacks(it) }
        val task = Runnable { onNewMediaDetected() }
        debounceRunnable = task
        handler.postDelayed(task, 1800L)
      }
    }
    observer = obs
    runCatching {
      context.contentResolver.registerContentObserver(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        true,
        obs,
      )
    }
  }

  fun stopObserving() {
    debounceRunnable?.let { handler.removeCallbacks(it) }
    debounceRunnable = null
    observer?.let {
      runCatching { context.contentResolver.unregisterContentObserver(it) }
    }
    observer = null
  }

  suspend fun findNewCandidates(
    scope: String,
    alreadyIndexedKeys: Set<String>,
    maxBatch: Int = 5,
  ): List<MediaCandidate> = withContext(Dispatchers.IO) {
    if (!hasMediaPermission()) return@withContext emptyList()

    val projection = buildList {
      add(MediaStore.Images.Media._ID)
      add(MediaStore.Images.Media.DISPLAY_NAME)
      add(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
      add(MediaStore.Images.Media.DATE_ADDED)
      add(MediaStore.Images.Media.DATE_MODIFIED)
      add(MediaStore.Images.Media.SIZE)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        add(MediaStore.Images.Media.RELATIVE_PATH)
      }
    }.toTypedArray()

    val candidates = mutableListOf<MediaCandidate>()
    runCatching {
      context.contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        projection,
        null,
        null,
        "${MediaStore.Images.Media.DATE_ADDED} DESC",
      )?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
        val bucketCol = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
        val addedCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)
        val modCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)
        val sizeCol = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)
        val relPathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
        } else {
          -1
        }

        var inspected = 0
        while (cursor.moveToNext() && candidates.size < maxBatch && inspected < 6000) {
          inspected += 1
          val id = cursor.getLong(idCol)
          val name = cursor.getString(nameCol).orEmpty()
          val bucket = if (bucketCol >= 0) cursor.getString(bucketCol).orEmpty() else ""
          val relPath = if (relPathCol >= 0) cursor.getString(relPathCol).orEmpty() else ""
          val dateAdded = if (addedCol >= 0) cursor.getLong(addedCol) else 0L
          val dateMod = if (modCol >= 0) cursor.getLong(modCol) else dateAdded
          val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L

          if (size <= 0L) continue

          val isScreenshot =
            bucket.contains("screenshot", ignoreCase = true) ||
              relPath.contains("screenshot", ignoreCase = true) ||
              name.startsWith("Screenshot", ignoreCase = true) ||
              name.contains("screen_shot", ignoreCase = true)

          if (scope == "screenshots" && !isScreenshot) continue

          val dedupeKey = "ms:${id}_${dateMod}_${size}"
          if (dedupeKey in alreadyIndexedKeys) continue

          val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
          candidates.add(
            MediaCandidate(
              dedupeKey = dedupeKey,
              contentUri = uri,
              displayName = name.ifBlank { "Image $id" },
              bucketName = bucket.ifBlank { if (isScreenshot) "Screenshots" else "Photos" },
              isScreenshot = isScreenshot,
              dateAddedSec = dateAdded,
            ),
          )
        }
      }
    }
    candidates
  }

  suspend fun decodeDownsampledBitmap(uri: Uri, maxDim: Int = 1024): Bitmap? =
    withContext(Dispatchers.IO) {
      runCatching {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
          BitmapFactory.decodeStream(it, null, opts)
        }
        var sample = 1
        while (opts.outWidth / sample > maxDim || opts.outHeight / sample > maxDim) {
          sample *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use {
          BitmapFactory.decodeStream(it, null, decodeOpts)
        }
      }.getOrNull()
    }
}
