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

        // Bootstrap: load bundled asset lists FIRST, then merge any newer synced
        // lists from filesDir/filterlists (written by FilterSyncWorker) on top.
        CoroutineScope(Dispatchers.IO).launch {
            try {
                assets.open("easylist.txt").bufferedReader().use { reader ->
                    AdBlockEngine.instance.loadRules(FilterListLoader.parse(reader.readText()))
                }
            } catch (_: Exception) { }

            try {
                assets.open("easyprivacy.txt").bufferedReader().use { reader ->
                    AdBlockEngine.instance.loadRules(FilterListLoader.parse(reader.readText()))
                }
            } catch (_: Exception) { }

            val syncedDir = File(filesDir, "filterlists")
            syncedDir.listFiles()?.forEach { file ->
                if (file.extension != "txt") return@forEach
                try {
                    AdBlockEngine.instance.loadRules(FilterListLoader.parse(file.readText()))
                } catch (_: Exception) { }
            }
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
