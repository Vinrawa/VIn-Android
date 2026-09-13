package com.vin.browser.adblock

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vin.browser.data.StorageService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads the latest EasyList + EasyPrivacy from easylist.to on a WorkManager
 * schedule, stores them under filesDir/filterlists/ (atomic temp-file rename) and
 * hot-swaps the parsed rules into AdBlockEngine without dropping requests.
 * On any failure the previously synced lists stay untouched (Result.failure).
 */
class FilterSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val easylist = download(EASYLIST_URL) ?: return Result.failure()
        val easyprivacy = download(EASYPRIVACY_URL) ?: return Result.failure()

        return try {
            val dir = File(applicationContext.filesDir, "filterlists")
            if (!dir.exists()) dir.mkdirs()
            saveAtomic(File(dir, "easylist.txt"), easylist)
            saveAtomic(File(dir, "easyprivacy.txt"), easyprivacy)

            StorageService(applicationContext).setLastFilterSync(System.currentTimeMillis())

            val merged = FilterListLoader.merge(
                listOf(FilterListLoader.parse(easylist), FilterListLoader.parse(easyprivacy))
            )
            AdBlockEngine.instance.replaceRemoteRules(merged)
            Result.success()
        } catch (_: Exception) {
            Result.failure()
        }
    }

    private suspend fun download(urlStr: String): String? = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android) ViNBrowser/1.0")
            }
            if (conn.responseCode != 200) return@withContext null

            val input = conn.inputStream
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            var total = 0
            while (true) {
                val read = input.read(chunk)
                if (read == -1) break
                total += read
                if (total > MAX_LIST_BYTES) return@withContext null
                buffer.write(chunk, 0, read)
            }
            buffer.toString("UTF-8")
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    /** Write to a temp file first, then rename over the target so a crash never leaves a truncated list. */
    private fun saveAtomic(target: File, content: String) {
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeText(content, Charsets.UTF_8)
        if (target.exists()) target.delete()
        if (!tmp.renameTo(target)) {
            tmp.delete()
            throw java.io.IOException("Failed to move ${tmp.name} into place")
        }
    }

    companion object {
        private const val EASYLIST_URL = "https://easylist.to/easylist/easylist.txt"
        private const val EASYPRIVACY_URL = "https://easylist.to/easylist/easyprivacy.txt"
        private const val MAX_LIST_BYTES = 4 * 1024 * 1024
    }
}
