package com.flawiddsouza.writer.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        Log.d(TAG, "Starting background sync...")

        return try {
            // Initialize ConflictStorage
            ConflictStorage.initialize(applicationContext)

            // Create repository and sync engine
            val repository = WriterSyncRepository(applicationContext)
            val syncEngine = SyncEngine(applicationContext, repository)

            // Check if sync is configured
            if (!syncEngine.isConfigured()) {
                Log.d(TAG, "Sync not configured, skipping")
                return Result.success()
            }

            // Perform sync (blocking since we're already on a background thread)
            val result = runBlocking {
                syncEngine.performSync()
            }

            if (result.success) {
                Log.d(TAG, "Background sync completed successfully")
                Log.d(TAG, "Synced ${result.entriesSynced} entries, ${result.categoriesSynced} categories, ${result.conflictsDetected} conflicts")
                Result.success()
            } else {
                Log.e(TAG, "Background sync failed: ${result.error}")
                Result.retry()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error during background sync", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "SyncWorker"
        private const val WORK_NAME = "WriterPeriodicSync"

        /**
         * Schedule periodic sync with given interval
         * @param context Application context
         * @param intervalMinutes Sync interval in minutes (minimum 15)
         * @param wifiOnly Whether to sync only on WiFi
         */
        fun schedulePeriodic(
            context: Context,
            intervalMinutes: Long,
            wifiOnly: Boolean
        ) {
            // Minimum interval is 15 minutes for PeriodicWorkRequest
            val interval = maxOf(intervalMinutes, 15)

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .build()

            val syncWorkRequest = PeriodicWorkRequestBuilder<SyncWorker>(
                interval,
                TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .addTag("sync")
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                syncWorkRequest
            )

            Log.d(TAG, "Scheduled periodic sync every $interval minutes (WiFi only: $wifiOnly)")
        }

        /**
         * Cancel all scheduled sync work
         */
        fun cancelPeriodicSync(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Cancelled periodic sync")
        }

        /**
         * Trigger immediate one-time sync
         */
        fun triggerImmediateSync(context: Context) {
            val syncWorkRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .addTag("sync")
                .build()

            WorkManager.getInstance(context).enqueue(syncWorkRequest)
            Log.d(TAG, "Triggered immediate sync")
        }
    }
}
