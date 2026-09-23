package com.vin.browser

import android.app.Application
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.vin.browser.adblock.AdBlockEngine
import com.vin.browser.adblock.FilterListLoader
import com.vin.browser.adblock.FilterParseResult
import com.vin.browser.adblock.FilterSyncWorker
import com.vin.browser.data.StorageService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit

class VinBrowserApp : Application() {
    lateinit var storage: StorageService
        private set

    val adBlockEngine: AdBlockEngine
        get() = AdBlockEngine.instance

    override fun onCreate() {
        super.onCreate()
        storage = StorageService(this)

        // Initialize AdBlockEngine global flags from user preferences
        AdBlockEngine.instance.isGlobalAdBlockEnabled = storage.isGlobalAdBlockEnabled()
        AdBlockEngine.instance.isCosmeticFilterEnabled = storage.isCosmeticFilterEnabled()
        AdBlockEngine.instance.isTrackerBlockEnabled = storage.isTrackerBlockEnabled()
        AdBlockEngine.instance.isCryptoBlockEnabled = storage.isCryptoBlockEnabled()

        scheduleFilterSync()

        // Bootstrap: assemble the base rule set ONCE and hot-swap it into the engine.
        // Synced copies (filesDir/filterlists, written by FilterSyncWorker) win over the
        // bundled assets -- loading both would previously merge the same EasyList twice.
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val syncedDir = File(filesDir, "filterlists")
                val syncedFiles = syncedDir.listFiles()
                    ?.filter { it.extension == "txt" }
                    ?.sortedBy { it.name }
                    .orEmpty()

                fun readAsset(name: String): String? = try {
                    assets.open(name).bufferedReader().use { it.readText() }
                } catch (_: Exception) { null }

                fun readFile(file: File): String? = try {
                    file.readText()
                } catch (_: Exception) { null }

                val easylist = syncedFiles.firstOrNull { it.name.startsWith("easylist") }
                    ?.let(::readFile) ?: readAsset("easylist.txt")
                val easyprivacy = syncedFiles.firstOrNull { it.name.startsWith("easyprivacy") }
                    ?.let(::readFile) ?: readAsset("easyprivacy.txt")
                val extras = syncedFiles.filter {
                    !it.name.startsWith("easylist") && !it.name.startsWith("easyprivacy")
                }.mapNotNull(::readFile)

                val parseStats = mutableMapOf<String, Int>()
                val parsed = mutableListOf<FilterParseResult>()
                easylist?.let { parsed.add(FilterListLoader.parse(it, parseStats)) }
                easyprivacy?.let { parsed.add(FilterListLoader.parse(it, parseStats)) }
                extras.forEach { parsed.add(FilterListLoader.parse(it, parseStats)) }

                if (parsed.isNotEmpty()) {
                    AdBlockEngine.instance.replaceBaseRules(FilterListLoader.merge(parsed))
                }
                android.util.Log.i(
                    "VinBrowserApp",
                    "Filter lists loaded: ${parseStats["network"] ?: 0} network, " +
                        "${parseStats["popup"] ?: 0} popup, " +
                        "${parseStats["networkExceptions"] ?: 0} exceptions, " +
                        "${parseStats["cosmetic"] ?: 0} cosmetic rules " +
                        "(${parseStats["networkDropped"] ?: 0} unsupported lines skipped)"
                )
            } catch (_: Exception) { }
        }
    }

    /** Weekly refresh while online (keep policy) plus a best-effort one-shot fetch on launch. */
    private fun scheduleFilterSync() {
        val workManager = WorkManager.getInstance(this)
        val networkConstraint = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicSync = PeriodicWorkRequestBuilder<FilterSyncWorker>(7, TimeUnit.DAYS)
            .setConstraints(networkConstraint)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniquePeriodicWork(
            "filter_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            periodicSync
        )

        val bootSync = OneTimeWorkRequestBuilder<FilterSyncWorker>()
            .setConstraints(networkConstraint)
            .build()

        workManager.enqueueUniqueWork("filter_sync_boot", ExistingWorkPolicy.KEEP, bootSync)
    }
}