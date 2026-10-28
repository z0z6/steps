package pl.dotsteps

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener2
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

class StepService : Service(), SensorEventListener2 {

    private var lastShown = -1
    private var sm: SensorManager? = null
    private var quitting = false
    private var finished = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        running = true
        StepStore.init(this)

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Licznik kroków", NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) }
        )
        ServiceCompat.startForeground(
            this, NOTIF_ID, build(StepStore.todaySteps()),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
            else ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
        )

        val manager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (sensor == null) {
            stopSelf()
            return
        }
        sm = manager
        manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL, 30_000_000)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_QUIT) {
            quit()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        running = false
        sm?.unregisterListener(this)
        super.onDestroy()
    }

    /** Wymusza dostarczenie zbuforowanych kroków, zapisuje i kończy proces. */
    private fun quit() {
        if (quitting) return
        quitting = true
        StepStore.setStopped(true)
        val m = sm
        if (m == null) {
            finishQuit()
            return
        }
        handler.postDelayed({ finishQuit() }, 1500) // zabezpieczenie, gdyby flush nie wrócił
        val ok = try { m.flush(this) } catch (_: Exception) { false }
        if (!ok) finishQuit()
    }

    private fun finishQuit() {
        if (finished) return
        finished = true
        handler.removeCallbacksAndMessages(null)
        sm?.unregisterListener(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Process.killProcess(Process.myPid())
    }

    override fun onFlushCompleted(sensor: Sensor?) {
        if (quitting) finishQuit()   // wszystkie zbuforowane zdarzenia już zapisane w onSensorChanged
    }

    override fun onSensorChanged(e: SensorEvent) {
        StepStore.onCounter(e.values[0].toLong())
        if (quitting) return
        val n = StepStore.todaySteps()
        if (n != lastShown) {
            lastShown = n
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, build(n))
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun build(steps: Int): Notification {
        val goal = StepStore.settings.value.goal
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val quitPi = PendingIntent.getService(
            this, 1,
            Intent(this, StepService::class.java).setAction(ACTION_QUIT),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("$steps kroków")
            .setContentText("Cel dzienny: $goal")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, "Zakończ", quitPi)
            .build()
    }

    companion object {
        const val ACTION_QUIT = "pl.dotsteps.QUIT"
        private const val CHANNEL = "steps"
        private const val NOTIF_ID = 1

        @Volatile
        var running = false

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, StepService::class.java))
        }
    }
}
