package app.yerel.data

import android.app.ActivityManager
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import app.yerel.core.Catalog
import app.yerel.core.GgufInfo
import app.yerel.core.GgufReader
import app.yerel.core.HfFile
import app.yerel.core.HfRef
import app.yerel.core.HuggingFace
import app.yerel.core.ModelEntry
import app.yerel.core.SamplingPreset
import app.yerel.core.formatBytes
import app.yerel.core.mapObjects
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class PendingDownload(
    val downloadId: Long,
    val repo: String,
    val revision: String,
    val filePath: String,
    val dest: String,
    val name: String,
    val totalBytes: Long,
    val preset: SamplingPreset?,
    // live state, not persisted
    val downloaded: Long = 0,
    val status: Int = DownloadManager.STATUS_PENDING,
    val reason: Int = 0,
) {
    val progress get() = if (totalBytes > 0) (downloaded.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
    val failed get() = status == DownloadManager.STATUS_FAILED

    fun toJson() = JSONObject().apply {
        put("id", downloadId)
        put("repo", repo)
        put("rev", revision)
        put("file", filePath)
        put("dest", dest)
        put("name", name)
        put("total", totalBytes)
        preset?.let { put("preset", it.toJson()) }
    }

    companion object {
        fun fromJson(o: JSONObject) = PendingDownload(
            downloadId = o.getLong("id"),
            repo = o.getString("repo"),
            revision = o.optString("rev", "main"),
            filePath = o.getString("file"),
            dest = o.getString("dest"),
            name = o.optString("name"),
            totalBytes = o.optLong("total"),
            preset = SamplingPreset.fromJson(o.optJSONObject("preset")),
        )
    }
}

data class ImportProgress(val name: String, val copied: Long, val total: Long)

data class RamInfo(val availBytes: Long, val totalBytes: Long, val lowMemory: Boolean)

class UserFacingException(message: String) : Exception(message)

/** Hugging Face lookups, downloads through the system DownloadManager, and local GGUF import. */
class ModelManager(private val context: Context, private val models: ModelStore, private val token: () -> String) {
    private val dm = context.getSystemService(DownloadManager::class.java)
    private val pendingFile = File(context.filesDir, "downloads.json")
    private val _downloads = MutableStateFlow(loadPending())
    val downloads: StateFlow<List<PendingDownload>> = _downloads.asStateFlow()

    private val _import = MutableStateFlow<ImportProgress?>(null)
    val import: StateFlow<ImportProgress?> = _import.asStateFlow()

    val modelsDir: File get() = (context.getExternalFilesDir("models") ?: File(context.filesDir, "models")).also { it.mkdirs() }

    private fun loadPending(): List<PendingDownload> = pendingFile.readOrNull()?.let {
        runCatching { JSONArray(it).mapObjects(PendingDownload::fromJson) }.getOrNull()
    } ?: emptyList()

    private fun savePending() = pendingFile.writeAtomic(JSONArray(_downloads.value.map { it.toJson() }).toString())

    fun ramInfo(): RamInfo {
        val am = context.getSystemService(ActivityManager::class.java)
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return RamInfo(mi.availMem, mi.totalMem, mi.lowMemory)
    }

    fun freeSpace(): Long = try { StatFs(modelsDir.path).availableBytes } catch (e: Exception) { Long.MAX_VALUE }

    private fun authHeaders(): Map<String, String> {
        val t = token().trim()
        return if (t.isEmpty()) emptyMap() else mapOf("Authorization" to "Bearer $t")
    }

    /** Lists the GGUF files of a repo, or the single file a link points to. */
    suspend fun resolve(ref: HfRef): List<HfFile> = withContext(Dispatchers.IO) {
        val revision = when (ref) { is HfRef.Repo -> ref.revision; is HfRef.File -> ref.revision }
        val c = URL(HuggingFace.treeUrl(ref.repo, revision)).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            authHeaders().forEach { (k, v) -> c.setRequestProperty(k, v) }
            when (val code = c.responseCode) {
                200 -> {}
                401, 403 -> throw UserFacingException(
                    "Bu depo özel ya da erişim onayı istiyor. Ayarlar → Hugging Face token girin ve model sayfasında koşulları kabul edin.",
                )
                404 -> throw UserFacingException("Depo bulunamadı: ${ref.repo}")
                else -> throw UserFacingException("Hugging Face HTTP $code döndürdü")
            }
            val files = HuggingFace.parseTree(c.inputStream.bufferedReader().use { it.readText() })
            when (ref) {
                is HfRef.File -> files.filter { it.path == ref.path }.ifEmpty { listOf(HfFile(ref.path, 0)) }
                is HfRef.Repo -> files.ifEmpty { throw UserFacingException("Bu depoda GGUF dosyası yok. GGUF sürümünü arayın (ör. …-GGUF).") }
            }
        } catch (e: java.io.IOException) {
            throw UserFacingException("Hugging Face'e bağlanılamadı: ${e.message}")
        } finally {
            c.disconnect()
        }
    }

    /** Starts a download; returns an error message or null. */
    suspend fun download(repo: String, revision: String, file: HfFile, preset: SamplingPreset? = null): String? = withContext(Dispatchers.IO) {
        if (_downloads.value.any { it.repo == repo && it.filePath == file.path && !it.failed }) return@withContext "Bu dosya zaten iniyor."
        if (models.value.any { it.source == "hf:$repo/${file.path}" }) return@withContext "Bu model zaten yüklü."
        if (file.size > 0 && file.size + (200L shl 20) > freeSpace()) {
            return@withContext "Yetersiz depolama: ${formatBytes(file.size)} gerekiyor, ${formatBytes(freeSpace())} boş."
        }
        val original = HuggingFace.fileUrl(repo, revision, file.path)
        val url = try {
            preflight(original)
        } catch (e: UserFacingException) {
            return@withContext e.message
        }
        val destName = "${repo.substringBefore('/')}_${file.fileName}".replace(Regex("[^A-Za-z0-9._-]"), "_")
        val dest = File(modelsDir, destName)
        dest.delete()
        val req = DownloadManager.Request(Uri.parse(url))
            .setTitle(file.fileName)
            .setDescription("Yerel • $repo")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setDestinationInExternalFilesDir(context, "models", destName)
            .addRequestHeader("User-Agent", "Yerel-Android/1.0")
        val id = try {
            dm.enqueue(req)
        } catch (e: Exception) {
            return@withContext "İndirme başlatılamadı: ${e.message}"
        }
        val catalogPreset = preset ?: Catalog.models.firstOrNull { it.repo.equals(repo, true) }?.preset
        val p = PendingDownload(id, repo, revision, file.path, dest.absolutePath, displayName(file.fileName), file.size, catalogPreset)
        _downloads.update { it + p }
        savePending()
        null
    }

    /** Checks access; with a token, returns the signed redirect so the token is not sent to the CDN. */
    private fun preflight(url: String): String {
        val auth = authHeaders()
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "HEAD"
            c.instanceFollowRedirects = false
            c.connectTimeout = 15_000
            c.readTimeout = 15_000
            auth.forEach { (k, v) -> c.setRequestProperty(k, v) }
            val code = c.responseCode
            return when {
                code == 401 || code == 403 -> throw UserFacingException(
                    "Bu model erişim onayı istiyor. Model sayfasında koşulları kabul edin ve Ayarlar'a Hugging Face token girin.",
                )
                code == 404 -> throw UserFacingException("Dosya bulunamadı.")
                code in 300..399 && auth.isNotEmpty() -> c.getHeaderField("Location")?.let { URL(URL(url), it).toString() } ?: url
                else -> url
            }
        } catch (e: java.io.IOException) {
            throw UserFacingException("Hugging Face'e bağlanılamadı: ${e.message}")
        } finally {
            c.disconnect()
        }
    }

    private fun displayName(fileName: String) = fileName.removeSuffix(".gguf").removeSuffix(".GGUF")

    /** Refreshes download progress and registers finished files; returns true while any download is active. */
    suspend fun poll(): Boolean = withContext(Dispatchers.IO) {
        val list = _downloads.value
        if (list.isEmpty()) return@withContext false
        val byId = HashMap<Long, Triple<Int, Long, Int>>()
        val totals = HashMap<Long, Long>()
        try {
            dm.query(DownloadManager.Query().setFilterById(*list.map { it.downloadId }.toLongArray()))?.use { cur ->
                val iId = cur.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
                val iStatus = cur.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                val iSoFar = cur.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val iTotal = cur.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                val iReason = cur.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)
                while (cur.moveToNext()) {
                    val id = cur.getLong(iId)
                    byId[id] = Triple(cur.getInt(iStatus), cur.getLong(iSoFar), cur.getInt(iReason))
                    totals[id] = cur.getLong(iTotal)
                }
            }
        } catch (e: Exception) {
            return@withContext true
        }
        val keep = mutableListOf<PendingDownload>()
        for (p in list) {
            val s = byId[p.downloadId]
            if (s == null) {
                // removed from the system list (e.g. cleared by the user); keep the file if it is complete
                if (!finishIfComplete(p)) keep += p.copy(status = DownloadManager.STATUS_FAILED, reason = -1)
                continue
            }
            val total = totals[p.downloadId]?.takeIf { it > 0 } ?: p.totalBytes
            val updated = p.copy(status = s.first, downloaded = s.second, reason = s.third, totalBytes = total)
            if (s.first == DownloadManager.STATUS_SUCCESSFUL) {
                if (!finishIfComplete(updated)) keep += updated.copy(status = DownloadManager.STATUS_FAILED, reason = -2)
            } else {
                keep += updated
            }
        }
        val changed = keep.map { it.downloadId } != list.map { it.downloadId }
        _downloads.value = keep
        if (changed) savePending()
        keep.any { !it.failed }
    }

    private fun finishIfComplete(p: PendingDownload): Boolean {
        val f = File(p.dest)
        if (!f.exists() || f.length() == 0L || (p.totalBytes > 0 && f.length() < p.totalBytes)) return false
        val info = runCatching { f.inputStream().use { GgufReader.read(it) } }.getOrNull() ?: return false
        models.upsert(
            ModelEntry(
                name = p.name, path = f.absolutePath, sizeBytes = f.length(), source = "hf:${p.repo}/${p.filePath}",
                info = info, preset = p.preset,
            ),
        )
        return true
    }

    fun cancelDownload(id: Long) {
        runCatching { dm.remove(id) }
        _downloads.value.firstOrNull { it.downloadId == id }?.let { File(it.dest).delete() }
        _downloads.update { list -> list.filter { it.downloadId != id } }
        savePending()
    }

    fun failureText(p: PendingDownload): String = when (p.reason) {
        -1 -> "İndirme listeden kaldırılmış"
        -2 -> "Dosya eksik ya da GGUF değil"
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Depolama yetersiz"
        DownloadManager.ERROR_HTTP_DATA_ERROR, DownloadManager.ERROR_CANNOT_RESUME -> "Bağlantı koptu"
        DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "Çok fazla yönlendirme"
        401, 403 -> "Erişim reddedildi (token gerekli)"
        404 -> "Dosya bulunamadı"
        else -> "Hata (${p.reason})"
    }

    data class LocalFile(val uri: Uri, val name: String, val size: Long, val info: GgufInfo)

    /** Reads a picked file's GGUF header so the user sees what it is before importing. */
    suspend fun inspect(uri: Uri): LocalFile = withContext(Dispatchers.IO) {
        val cr = context.contentResolver
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "model.gguf"
        var size = -1L
        cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getString(0)?.let { name = it }
                if (!c.isNull(1)) size = c.getLong(1)
            }
        }
        val info = try {
            cr.openInputStream(uri)?.use { GgufReader.read(it) } ?: throw UserFacingException("Dosya açılamadı")
        } catch (e: UserFacingException) {
            throw e
        } catch (e: Exception) {
            throw UserFacingException("Bu bir GGUF model dosyası değil (${e.message})")
        }
        if (info.isMmproj) throw UserFacingException("Bu bir görüntü projektörü (mmproj) dosyası; sohbet modeli değil.")
        if (Regex("-\\d{5}-of-\\d{5}\\.gguf$", RegexOption.IGNORE_CASE).containsMatchIn(name)) {
            throw UserFacingException("Parçalı (split) GGUF dosyaları desteklenmiyor; tek dosyalı bir sürüm seçin.")
        }
        LocalFile(uri, name, size, info)
    }

    /** Copies the file into app storage (fast loading) or links it in place (no extra space). */
    suspend fun importLocal(file: LocalFile, copy: Boolean): ModelEntry = withContext(Dispatchers.IO) {
        val cr = context.contentResolver
        val base = file.info.name.ifBlank { file.name.removeSuffix(".gguf") }
        val entry = if (copy) {
            if (file.size > 0 && file.size + (200L shl 20) > freeSpace()) {
                throw UserFacingException("Yetersiz depolama: ${formatBytes(file.size)} gerekiyor. \"Bağlantı\" seçeneğini deneyin.")
            }
            val dest = uniqueFile(file.name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
            val tmp = File(dest.path + ".part")
            try {
                _import.value = ImportProgress(file.name, 0, file.size)
                cr.openInputStream(file.uri)!!.use { input ->
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(1 shl 20)
                        var total = 0L
                        var lastReport = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            total += n
                            if (total - lastReport > (8L shl 20)) {
                                lastReport = total
                                _import.value = ImportProgress(file.name, total, file.size)
                            }
                        }
                    }
                }
                if (!tmp.renameTo(dest)) throw UserFacingException("Dosya kaydedilemedi")
            } catch (e: Exception) {
                tmp.delete()
                throw if (e is UserFacingException) e else UserFacingException("Kopyalama başarısız: ${e.message}")
            } finally {
                _import.value = null
            }
            ModelEntry(name = base, path = dest.absolutePath, sizeBytes = dest.length(), source = "local", info = file.info)
        } else {
            try {
                cr.takePersistableUriPermission(file.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {
                throw UserFacingException("Bu dosya için kalıcı izin alınamadı; \"Kopyala\" seçeneğini kullanın.")
            }
            ModelEntry(name = base, uri = file.uri.toString(), sizeBytes = file.size.coerceAtLeast(0), source = "link", info = file.info)
        }
        models.upsert(entry)
        entry
    }

    private fun uniqueFile(name: String): File {
        var f = File(modelsDir, name)
        var i = 1
        while (f.exists()) f = File(modelsDir, name.removeSuffix(".gguf") + "-${i++}.gguf")
        return f
    }

    /** Registers GGUF files that were put into the models folder by hand (USB, file manager, adb). */
    suspend fun scanFolder() = withContext(Dispatchers.IO) {
        val known = models.value.mapNotNull { it.path }.toSet() + _downloads.value.map { it.dest }.toSet()
        val dirs = listOf(modelsDir, File(context.filesDir, "models")).distinct()
        dirs.flatMap { d -> d.listFiles { f -> f.isFile && f.name.endsWith(".gguf", true) }?.toList() ?: emptyList() }.forEach { f ->
            if (f.absolutePath in known) return@forEach
            val info = runCatching { f.inputStream().use { GgufReader.read(it) } }.getOrNull() ?: return@forEach
            if (info.isMmproj) return@forEach
            models.upsert(
                ModelEntry(name = info.name.ifBlank { f.name.removeSuffix(".gguf") }, path = f.absolutePath, sizeBytes = f.length(), source = "folder", info = info),
            )
        }
    }

    fun delete(entry: ModelEntry) {
        entry.path?.let { p ->
            val f = File(p)
            if (f.absolutePath.startsWith(modelsDir.absolutePath) || f.absolutePath.startsWith(context.filesDir.absolutePath)) f.delete()
        }
        entry.uri?.let { u ->
            runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(u), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        models.remove(entry.id)
    }

    fun hfSourceOf(entry: ModelEntry): String? = entry.source.removePrefix("hf:").takeIf { entry.source.startsWith("hf:") }
}
