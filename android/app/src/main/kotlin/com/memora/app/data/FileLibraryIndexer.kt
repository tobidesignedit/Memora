package com.memora.app.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.zip.ZipInputStream
import kotlin.math.max

data class FileCandidate(
  val dedupeKey: String,
  val uri: Uri,
  val displayName: String,
  val mime: String,
  val kind: String, // pdf | docx | text | audio | video
  val source: String,
)

data class ExtractedFile(
  val text: String,
  val bitmap: Bitmap?,
  val kindHint: String,
)

/**
 * Indexes everything beyond photos: PDFs, Word/text documents, audio recordings and videos.
 *
 * - Audio and video come from MediaStore (READ_MEDIA_AUDIO / READ_MEDIA_VIDEO).
 * - Documents come from folders the user connects once with the system folder picker
 *   (Storage Access Framework), so no broad "all files" permission is required.
 * - Everything is read on-device; nothing is uploaded unless the privacy mode allows cloud inference.
 */
class FileLibraryIndexer(private val context: Context) {
  private val prefs = context.getSharedPreferences("memora_file_library", Context.MODE_PRIVATE)

  fun requiredPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_AUDIO,
      )
    } else {
      arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

  private fun hasAnyPermission(vararg perms: String): Boolean =
    perms.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

  private fun canReadAudio(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      hasAnyPermission(Manifest.permission.READ_MEDIA_AUDIO)
    } else {
      hasAnyPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

  private fun canReadVideo(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      hasAnyPermission(Manifest.permission.READ_MEDIA_VIDEO)
    } else {
      hasAnyPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

  fun connectedFolders(): Set<String> = prefs.getStringSet(KEY_TREES, emptySet()) ?: emptySet()

  fun connectFolder(treeUri: Uri) {
    runCatching {
      context.contentResolver.takePersistableUriPermission(
        treeUri,
        Intent.FLAG_GRANT_READ_URI_PERMISSION,
      )
    }
    prefs.edit().putStringSet(KEY_TREES, connectedFolders() + treeUri.toString()).apply()
  }

  fun disconnectAllFolders() {
    connectedFolders().forEach { s ->
      runCatching {
        context.contentResolver.releasePersistableUriPermission(
          Uri.parse(s),
          Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
      }
    }
    prefs.edit().remove(KEY_TREES).apply()
  }

  suspend fun findNewFiles(
    alreadyIndexed: Set<String>,
    maxBatch: Int,
  ): List<FileCandidate> = withContext(Dispatchers.IO) {
    val out = mutableListOf<FileCandidate>()
    out += findDocuments(alreadyIndexed, maxBatch)
    if (out.size < maxBatch) out += findMediaStoreFiles(Kind.AUDIO, alreadyIndexed, maxBatch - out.size)
    if (out.size < maxBatch) out += findMediaStoreFiles(Kind.VIDEO, alreadyIndexed, maxBatch - out.size)
    out
  }

  private enum class Kind { AUDIO, VIDEO }

  private fun findMediaStoreFiles(
    kind: Kind,
    alreadyIndexed: Set<String>,
    limit: Int,
  ): List<FileCandidate> {
    val isAudio = kind == Kind.AUDIO
    if (isAudio && !canReadAudio()) return emptyList()
    if (!isAudio && !canReadVideo()) return emptyList()

    val base = if (isAudio) MediaStore.Audio.Media.EXTERNAL_CONTENT_URI else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    val idCol = MediaStore.MediaColumns._ID
    val nameCol = MediaStore.MediaColumns.DISPLAY_NAME
    val modCol = MediaStore.MediaColumns.DATE_MODIFIED
    val sizeCol = MediaStore.MediaColumns.SIZE
    val mimeCol = MediaStore.MediaColumns.MIME_TYPE
    // Skip bulk music tracks: voice notes, call recordings and meeting audio are what matter.
    val selection = if (isAudio) "${MediaStore.Audio.Media.IS_MUSIC} = 0" else null

    val result = mutableListOf<FileCandidate>()
    runCatching {
      context.contentResolver.query(
        base,
        arrayOf(idCol, nameCol, modCol, sizeCol, mimeCol),
        selection,
        null,
        "$modCol DESC",
      )?.use { c ->
        var inspected = 0
        while (c.moveToNext() && result.size < limit && inspected < MAX_INSPECT) {
          inspected += 1
          val id = c.getLong(0)
          val name = c.getString(1).orEmpty()
          val mod = c.getLong(2)
          val size = c.getLong(3)
          val mime = c.getString(4).orEmpty()
          if (size <= 0L) continue
          val key = "${if (isAudio) "au" else "vi"}:${id}_${mod}_$size"
          if (key in alreadyIndexed) continue
          result.add(
            FileCandidate(
              dedupeKey = key,
              uri = ContentUris.withAppendedId(base, id),
              displayName = name.ifBlank { if (isAudio) "Audio $id" else "Video $id" },
              mime = mime,
              kind = if (isAudio) "audio" else "video",
              source = if (isAudio) "Audio" else "Videos",
            ),
          )
        }
      }
    }
    return result
  }

  private fun findDocuments(alreadyIndexed: Set<String>, limit: Int): List<FileCandidate> {
    val out = mutableListOf<FileCandidate>()
    var visited = 0
    for (treeStr in connectedFolders()) {
      val tree = Uri.parse(treeStr)
      val rootId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: continue
      val label = rootId.substringAfterLast(':').substringAfterLast('/').ifBlank { "Folder" }
      val stack = ArrayDeque<Pair<String, Int>>()
      stack.add(rootId to 0)
      while (stack.isNotEmpty() && out.size < limit && visited < MAX_VISIT) {
        val (docId, depth) = stack.removeLast()
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        runCatching {
          context.contentResolver.query(
            children,
            arrayOf(
              DocumentsContract.Document.COLUMN_DOCUMENT_ID,
              DocumentsContract.Document.COLUMN_DISPLAY_NAME,
              DocumentsContract.Document.COLUMN_MIME_TYPE,
              DocumentsContract.Document.COLUMN_LAST_MODIFIED,
              DocumentsContract.Document.COLUMN_SIZE,
            ),
            null,
            null,
            null,
          )?.use { c ->
            while (c.moveToNext() && out.size < limit) {
              visited += 1
              val id = c.getString(0) ?: continue
              val name = c.getString(1).orEmpty()
              val mime = c.getString(2).orEmpty()
              val mod = c.getLong(3)
              val size = c.getLong(4)
              if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                if (depth < MAX_DEPTH) stack.add(id to depth + 1)
                continue
              }
              val kind = classify(name, mime) ?: continue
              if (size <= 0L) continue
              val key = "doc:${id}_${mod}_$size"
              if (key in alreadyIndexed) continue
              out.add(
                FileCandidate(
                  dedupeKey = key,
                  uri = DocumentsContract.buildDocumentUriUsingTree(tree, id),
                  displayName = name,
                  mime = mime,
                  kind = kind,
                  source = label,
                ),
              )
            }
          }
        }
      }
    }
    return out
  }

  private fun classify(name: String, mime: String): String? {
    val ext = name.substringAfterLast('.', "").lowercase()
    return when {
      mime == "application/pdf" || ext == "pdf" -> "pdf"
      ext == "docx" || mime.contains("wordprocessingml") -> "docx"
      mime.startsWith("text/") || ext in setOf("txt", "md", "csv", "json", "log") -> "text"
      mime.startsWith("audio/") -> "audio"
      mime.startsWith("video/") -> "video"
      else -> null
    }
  }

  suspend fun extract(c: FileCandidate): ExtractedFile? = withContext(Dispatchers.IO) {
    runCatching {
      when (c.kind) {
        "text" -> extractText(c)
        "docx" -> extractDocx(c)
        "pdf" -> extractPdf(c)
        "audio" -> extractAudio(c)
        "video" -> extractVideo(c)
        else -> null
      }
    }.getOrNull()
  }

  private fun extractText(c: FileCandidate): ExtractedFile? {
    val body = context.contentResolver.openInputStream(c.uri)?.bufferedReader()?.use { r ->
      val buf = CharArray(MAX_CHARS)
      val n = r.read(buf)
      String(buf, 0, max(n, 0))
    }.orEmpty().trim()
    if (body.isBlank()) return null
    return ExtractedFile("File: ${c.displayName}\n$body", null, "note")
  }

  private fun extractDocx(c: FileCandidate): ExtractedFile? {
    var xml = ""
    context.contentResolver.openInputStream(c.uri)?.use { raw ->
      ZipInputStream(raw).use { zip ->
        var entry = zip.nextEntry
        while (entry != null) {
          if (entry.name == "word/document.xml") {
            xml = zip.readBytes().toString(Charsets.UTF_8)
            break
          }
          entry = zip.nextEntry
        }
      }
    }
    val plain = xml
      .replace("</w:p>", "\n")
      .replace(Regex("<[^>]+>"), "")
      .replace("&amp;", "&")
      .replace("&lt;", "<")
      .replace("&gt;", ">")
      .replace("&quot;", "\"")
      .trim()
      .take(MAX_CHARS)
    if (plain.isBlank()) return null
    return ExtractedFile("File: ${c.displayName}\n$plain", null, "document")
  }

  private fun extractPdf(c: FileCandidate): ExtractedFile? {
    val pfd = context.contentResolver.openFileDescriptor(c.uri, "r") ?: return null
    pfd.use { fd ->
      PdfRenderer(fd).use { renderer ->
        val pages = renderer.pageCount
        if (pages <= 0) return null
        renderer.openPage(0).use { page ->
          val scale = 1400f / max(page.width, page.height).coerceAtLeast(1)
          val w = (page.width * scale).toInt().coerceAtLeast(1)
          val h = (page.height * scale).toInt().coerceAtLeast(1)
          val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
          bmp.eraseColor(Color.WHITE)
          page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
          return ExtractedFile("PDF: ${c.displayName} · $pages page${if (pages == 1) "" else "s"} (first page read)", bmp, "document")
        }
      }
    }
  }

  private fun durationLabel(ms: Long): String {
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
  }

  private fun extractAudio(c: FileCandidate): ExtractedFile? {
    val r = MediaMetadataRetriever()
    return try {
      r.setDataSource(context, c.uri)
      val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
      val title = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).orEmpty()
      ExtractedFile(
        "Audio recording: ${c.displayName}${if (title.isNotBlank()) " · $title" else ""} · ${durationLabel(dur)}",
        null,
        "voice",
      )
    } finally {
      runCatching { r.release() }
    }
  }

  private fun extractVideo(c: FileCandidate): ExtractedFile? {
    val r = MediaMetadataRetriever()
    return try {
      r.setDataSource(context, c.uri)
      val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
      val frame = r.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
      val scaled = frame?.let {
        val s = 1024f / max(it.width, it.height).coerceAtLeast(1)
        if (s < 1f) Bitmap.createScaledBitmap(it, (it.width * s).toInt(), (it.height * s).toInt(), true) else it
      }
      ExtractedFile("Video: ${c.displayName} · ${durationLabel(dur)}", scaled, "photo")
    } finally {
      runCatching { r.release() }
    }
  }

  private companion object {
    const val KEY_TREES = "tree_uris"
    const val MAX_INSPECT = 4000
    const val MAX_VISIT = 8000
    const val MAX_DEPTH = 6
    const val MAX_CHARS = 24_000
  }
}
