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
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

class StepService : Service(), SensorEventListener {

    private var lastShown = -1
    private var sm: SensorManager? = null

    override fun onCreate() {
        super.onCreate()
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
        // batching do 30 s – oszczędza baterię
        manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL, 30_000_000)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        sm?.unregisterListener(this)
        super.onDestroy()
    }

    override fun onSensorChanged(e: SensorEvent) {
        StepStore.onCounter(e.values[0].toLong())
        val n = StepStore.todaySteps()
        if (n != lastShown) {
            lastShown = n
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, build(n))
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun build(steps: Int): Notification {
        val goal = StepStore.settings.value.goal
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("$steps kroków")
            .setContentText("Cel dzienny: $goal")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pi)
            .build()
    }

    companion object {
        private const val CHANNEL = "steps"
        private const val NOTIF_ID = 1

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, StepService::class.java))
        }
    }
}
