package montafra.beam

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log

const val runOnlyWhileChargingKey = "runOnlyWhileCharging"

/**
 * "Run only while charging" needs something that wakes the app when a charger is connected.
 * ACTION_POWER_CONNECTED cannot be declared in the manifest since Android 8, so a one-shot
 * JobScheduler job with a charging constraint does it instead: it fires as soon as the device
 * starts charging, even if the app process is gone, and survives reboots.
 *
 * The service schedules it when it shuts itself down on unplug. Starting a foreground service
 * from a job is only allowed while the app is exempt from battery optimisation, which is why the
 * app already asks for "unrestricted" background usage.
 */
object ChargeTrigger {
    private const val JOB_ID = 4201

    fun isPlugged(context: Context): Boolean {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return (intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
    }

    fun schedule(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        if (scheduler.getPendingJob(JOB_ID) != null) return
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, ChargeJobService::class.java))
            .setRequiresCharging(true)
            .setPersisted(true)
            .build()
        try {
            scheduler.schedule(job)
        } catch (e: Exception) {
            Log.e("ChargeTrigger", "Failed to schedule charge job: ${e.message}")
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(JobScheduler::class.java)?.cancel(JOB_ID)
    }
}

class ChargeJobService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        val prefs = getSharedPreferences(settingsName, MODE_MULTI_PROCESS)
        if (prefs.getBoolean(runOnlyWhileChargingKey, false) &&
            prefs.getBoolean("notificationEnabled", true)
        ) {
            try {
                startForegroundService(Intent(this, StatusService::class.java))
            } catch (e: Exception) {
                Log.e("ChargeJobService", "Failed to start StatusService: ${e.message}")
            }
        }
        // Nothing left to do on this thread: the service takes it from here.
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = false
}
