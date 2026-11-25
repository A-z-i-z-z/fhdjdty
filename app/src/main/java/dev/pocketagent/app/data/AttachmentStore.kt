package dev.pocketagent.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.core.content.FileProvider
import dev.pocketagent.app.runtime.Paths
import dev.pocketagent.app.util.AppLog
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import dev.pocketagent.app.R
import dev.pocketagent.app.ui.text.Texts

/**
 * Imports photos and documents from Android content URIs into app-private storage (`files/chat/attachments/`),
 * mirrors them into the Hermes rootfs (`/root/.hermes/attachments/`) so Hermes shell/Python tools can inspect them,
 * and encodes images as compact JPEG data URLs for multimodal requests.
 */
class AttachmentStore(private val context: Context, private val paths: Paths) {
    private val appDir: File get() = File(paths.chatDir, "attachments").also { it.mkdirs() }
    private val guestDir: File get() = File(paths.hermesHome, "attachments")
    private val cameraDir: File get() = File(paths.cacheDir, "camera").also { it.mkdirs() }

    /** Creates a temporary JPEG capture target in cache and returns its FileProvider content URI. */
    fun createCameraCaptureTarget(): Pair<File, Uri> {
        val file = File(cameraDir, "cap_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return file to uri
    }

    /** Imports an image URI, downscales longest edge to 1600 px, fixes EXIF orientation, and saves as JPEG. */
    fun importImage(uri: Uri, suggestedName: String? = null): Attachment {
        val id = newId()
        val rawName = suggestedName?.takeIf { it.isNotBlank() } ?: queryDisplayName(uri) ?: "photo_$id.jpg"
        val cleanName = sanitizeName(rawName).let { if (it.endsWith(".jpg", true) || it.endsWith(".jpeg", true)) it else "$it.jpg" }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (maxDim / sample > MAX_IMAGE_DIM * 2) sample *= 2

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw IllegalStateException(Texts.get(context, R.string.eng_attach_decode))

        val orientation = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Exception) { ExifInterface.ORIENTATION_NORMAL }

        bmp = applyExif(bmp, orientation)
        bmp = scaleDown(bmp, MAX_IMAGE_DIM)

        val outFile = File(appDir, "$id.jpg")
        FileOutputStream(outFile).use { fos ->
            if (!bmp.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, fos)) {
                throw IllegalStateException(Texts.get(context, R.string.eng_attach_encode))
            }
        }
        bmp.recycle()

        val guestPath = mirrorToGuest(outFile, "$id.jpg")
        return Attachment(
            id = id,
            name = cleanName,
            mime = "image/jpeg",
            kind = AttachmentKind.IMAGE,
            localPath = outFile.absolutePath,
            guestPath = guestPath,
            sizeBytes = outFile.length(),
        )
    }

    /** Imports a document URI (up to 20 MiB), mirrors to guest attachments, and extracts text/PDF metadata. */
    fun importDocument(uri: Uri): Attachment {
        val id = newId()
        val rawName = queryDisplayName(uri) ?: "document_$id"
        val safeName = sanitizeName(rawName)
        val mime = context.contentResolver.getType(uri)?.takeIf { it.isNotBlank() } ?: guessMime(safeName)
        val fileName = "${id}_$safeName"
        val outFile = File(appDir, fileName)

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(outFile).use { output ->
                val buf = ByteArray(16 * 1024)
                var total = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_DOC_BYTES) {
                        outFile.delete()
                        throw IllegalArgumentException(Texts.get(context, R.string.eng_attach_too_large, MAX_DOC_BYTES / (1024 * 1024)))
                    }
                    output.write(buf, 0, n)
                }
            }
        } ?: throw IllegalStateException(Texts.get(context, R.string.eng_attach_open))

        val guestPath = mirrorToGuest(outFile, fileName)
        val excerpt = extractExcerpt(outFile, mime, safeName)
        return Attachment(
            id = id,
            name = rawName,
            mime = mime,
            kind = AttachmentKind.DOCUMENT,
            localPath = outFile.absolutePath,
            guestPath = guestPath,
            sizeBytes = outFile.length(),
            textExcerpt = excerpt,
        )
    }

    /** Returns a `data:image/jpeg;base64,...` URL for an image attachment, or null if absent/too large. */
    fun toDataUrl(attachment: Attachment): String? {
        if (attachment.kind != AttachmentKind.IMAGE) return null
        // Message JSON can come from a restored backup: only ever read inside the attachments folder (by base name).
        val f = resolveIn(appDir, attachment.localPath) ?: return null
        if (!f.isFile || f.length() > MAX_DATA_URL_BYTES) return null
        return try {
            val bytes = f.readBytes()
            "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            AppLog.w("Attach", "failed to read image for data URL: ${e.message}")
            null
        }
    }

    /** Decodes a scaled bitmap thumbnail from [path] for UI display. */
    fun loadThumbnail(path: String, maxPx: Int = 360): Bitmap? {
        val f = resolveIn(appDir, path) ?: return null
        if (!f.isFile) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.absolutePath, bounds)
            val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
            var sample = 1
            while (maxDim / sample > maxPx * 2) sample *= 2
            BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (_: Exception) { null }
    }

    private fun mirrorToGuest(src: File, guestName: String): String {
        val guestRel = "/root/.hermes/attachments/$guestName"
        return try {
            if (!paths.hermesHome.isDirectory) return ""
            guestDir.mkdirs()
            src.copyTo(File(guestDir, guestName), overwrite = true)
            guestRel
        } catch (e: Exception) {
            AppLog.w("Attach", "could not mirror attachment to guest: ${e.message}")
            ""
        }
    }

    private fun extractExcerpt(file: File, mime: String, safeName: String): String {
        val ext = safeName.substringAfterLast('.', "").lowercase()
        if (mime == "application/pdf" || ext == "pdf") {
            return try {
                android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer -> "[PDF document, ${renderer.pageCount} pages]" }
                }
            } catch (_: Exception) { "[PDF document]" }
        }
        val textExt = ext in TEXT_EXTENSIONS || mime.startsWith("text/") || mime in TEXT_MIMES
        if (textExt || (file.length() <= 64 * 1024 && looksLikeText(file))) {
            return try {
                val raw = file.inputStream().use { input ->
                    val buf = ByteArray(minOf(file.length(), 48 * 1024L).toInt())
                    val read = input.read(buf)
                    if (read <= 0) "" else String(buf, 0, read, Charsets.UTF_8)
                }
                if (raw.length > MAX_TEXT_EXCERPT) raw.take(MAX_TEXT_EXCERPT) + "\n…[truncated]" else raw
            } catch (_: Exception) { "" }
        }
        return ""
    }

    private fun looksLikeText(file: File): Boolean = try {
        file.inputStream().use { s ->
            val buf = ByteArray(4096)
            val n = s.read(buf)
            if (n <= 0) true else (0 until n).none { buf[it] == 0.toByte() }
        }
    } catch (_: Exception) { false }

    private fun queryDisplayName(uri: Uri): String? {
        if (uri.scheme == "file") return uri.lastPathSegment
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (_: Exception) { null }
    }

    private fun applyExif(src: Bitmap, orientation: Int): Bitmap {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            else -> return src
        }
        val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        if (out !== src) src.recycle()
        return out
    }

    private fun scaleDown(src: Bitmap, maxEdge: Int): Bitmap {
        val longest = maxOf(src.width, src.height)
        if (longest <= maxEdge) return src
        val ratio = maxEdge.toFloat() / longest.toFloat()
        val w = (src.width * ratio).toInt().coerceAtLeast(1)
        val h = (src.height * ratio).toInt().coerceAtLeast(1)
        val out = Bitmap.createScaledBitmap(src, w, h, true)
        if (out !== src) src.recycle()
        return out
    }

    private fun sanitizeName(name: String): String =
        name.trim().replace(Regex("[^A-Za-z0-9._-]"), "_").trimStart('.').take(64).ifEmpty { "file" }

    private fun guessMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "pdf" -> "application/pdf"
        "txt", "log" -> "text/plain"
        "md" -> "text/markdown"
        "json" -> "application/json"
        "csv" -> "text/csv"
        "xml" -> "application/xml"
        "yaml", "yml" -> "application/yaml"
        else -> "application/octet-stream"
    }

    private fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(12)

    companion object {
        private val FLAT_NAME = Regex("[A-Za-z0-9._-]{1,200}")

        /**
         * Maps a stored attachment path to a file inside [dir], using only its base name: an absolute path from another
         * Android user/profile (`/data/user/N/...`) or another install still finds the restored file, and a path that points
         * anywhere else (`../secrets/x`, an absolute path to another folder, a symlink out of [dir]) gives null.
         */
        fun resolveIn(dir: File, path: String): File? {
            if (path.isBlank()) return null
            val name = path.substringAfterLast('/').substringAfterLast('\\')
            if (name.startsWith(".") || !FLAT_NAME.matches(name)) return null
            val f = File(dir, name)
            return try {
                val base = dir.canonicalFile
                if (f.canonicalFile.parentFile == base) f else null
            } catch (_: java.io.IOException) { null }
        }

        private const val MAX_IMAGE_DIM = 1600
        private const val JPEG_QUALITY = 84
        private const val MAX_DOC_BYTES = 20L * 1024L * 1024L
        private const val MAX_DATA_URL_BYTES = 6L * 1024L * 1024L
        private const val MAX_TEXT_EXCERPT = 12_000

        private val TEXT_MIMES = setOf(
            "application/json", "application/xml", "application/javascript", "application/x-sh",
            "application/yaml", "application/x-yaml", "application/toml", "application/csv",
        )
        private val TEXT_EXTENSIONS = setOf(
            "txt", "md", "json", "csv", "xml", "yaml", "yml", "py", "kt", "kts", "java",
            "js", "ts", "tsx", "jsx", "html", "htm", "css", "sh", "bash", "zsh", "log",
            "ini", "toml", "conf", "cfg", "env", "c", "cpp", "h", "hpp", "rs", "go", "sql",
        )
    }
}
