package com.memora.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-app Over-the-Air (OTA) APK downloader with live 0-100% progress reporting.
 * Streams the APK directly on-device and launches Android's Package Installer
 * automatically via FileProvider the moment download reaches 100%.
 */
object AppUpdater {
  suspend fun downloadAndInstallWithProgress(
    context: Context,
    apkUrl: String,
    versionName: String,
    onProgress: (Int) -> Unit,
    onStatus: (String) -> Unit,
  ) {
    val cleanUrl = apkUrl.trim()
    if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
      onStatus("No valid update URL configured yet.")
      return
    }

    val downloadedFile: File? = withContext(Dispatchers.IO) {
      runCatching {
        val appCtx = context.applicationContext
        val targetDir = appCtx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val outFile = File(targetDir, "MEMORA-$versionName.apk")
        if (outFile.exists()) outFile.delete()

        val conn = (URL(cleanUrl).openConnection() as HttpURLConnection).apply {
          instanceFollowRedirects = true
          connectTimeout = 15_000
          readTimeout = 30_000
          requestMethod = "GET"
        }
        conn.connect()
        if (conn.responseCode !in 200..299) {
          error("Server returned HTTP ${conn.responseCode}")
        }

        val totalBytes = conn.contentLengthLong
        var downloadedBytes = 0L
        var lastPct = -1

        conn.inputStream.use { input ->
          FileOutputStream(outFile).use { output ->
            val buf = ByteArray(16 * 1024)
            while (true) {
              val read = input.read(buf)
              if (read == -1) break
              output.write(buf, 0, read)
              downloadedBytes += read
              if (totalBytes > 0L) {
                val pct = ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
                if (pct != lastPct) {
                  lastPct = pct
                  onProgress(pct)
                }
              }
            }
            output.flush()
          }
        }
        conn.disconnect()
        onProgress(100)
        outFile
      }.onFailure { err ->
        onStatus("Update download failed: ${err.localizedMessage ?: "check Wi-Fi/connection"}")
      }.getOrNull()
    }

    if (downloadedFile != null && downloadedFile.exists()) {
      withContext(Dispatchers.Main) {
        launchInstaller(context.applicationContext, downloadedFile, cleanUrl, onStatus)
      }
    }
  }

  private fun launchInstaller(
    context: Context,
    apkFile: File,
    fallbackUrl: String,
    onStatus: (String) -> Unit,
  ) {
    runCatching {
      val apkUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
      } else {
        Uri.fromFile(apkFile)
      }
      val installIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(apkUri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
      context.startActivity(installIntent)
      onStatus("Update downloaded — tap Install to finish updating MEMORA.")
    }.onFailure {
      val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      runCatching { context.startActivity(browserIntent) }
      onStatus("Opening update in browser…")
    }
  }
}
