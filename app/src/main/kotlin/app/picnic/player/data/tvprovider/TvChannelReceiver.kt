package app.picnic.player.data.tvprovider

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Receives Android TV home-screen channel lifecycle broadcasts and keeps the
 * periodic sync worker scheduled. Immediate publishes are triggered via
 * [enqueueImmediateSync] once a session is available.
 */
class TvChannelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received broadcast: ${intent.action}")
        schedulePeriodicSync(context)
        enqueueImmediateSync(context)
    }

    companion object {
        private const val TAG = "TvChannelReceiver"
        private const val IMMEDIATE_WORK_NAME = "TvChannelSyncImmediate"

        private val networkConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /** Keep the hourly refresh scheduled (idempotent). */
        fun schedulePeriodicSync(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<TvChannelSyncWorker>(1, TimeUnit.HOURS)
                .setConstraints(networkConstraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                TvChannelSyncWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }

        /**
         * Run a sync as soon as WorkManager can. Periodic work alone is not enough —
         * the first periodic execution can be delayed for a long time, so the app
         * never appears under Channels until this one-shot has published at least
         * one preview channel.
         */
        fun enqueueImmediateSync(context: Context) {
            val workRequest = OneTimeWorkRequestBuilder<TvChannelSyncWorker>()
                .setConstraints(networkConstraints)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                // REPLACE so a no-op run before login doesn't block the real
                // publish once Home has an active session.
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }

        /** App start: schedule the hourly job; immediate sync waits for a session. */
        fun enqueueWorker(context: Context) {
            schedulePeriodicSync(context)
        }
    }
}
