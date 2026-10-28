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
import android.os.PowerManager
import android.os.Process
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class StepService : Service(), SensorEventListener2 {

    private var lastShown = -1
    private var sm: SensorManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var quitting = false
    private var finished = false
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

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

        sm = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        if (!registerForSource()) {
            stopSelf()
            return
        }
        // przełączenie źródła w ustawieniach działa od razu, bez restartu usługi
        scope.launch {
            StepStore.settings.map { it.source }.distinctUntilChanged().drop(1).collect {
                registerForSource()
            }
        }
    }

    /** Rejestruje czujnik zgodnie z wybranym źródłem (z awaryjnym wyborem, gdy go brak). */
    private fun registerForSource(): Boolean {
        val manager = sm ?: return false
        manager.unregisterListener(this)
        releaseWakeLock()

        val counter = manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        val detector = manager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        val accel = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        var src = StepStore.settings.value.source
        if (src == 1 && detector == null) src = 0
        if (src == 2 && accel == null) src = 0
        if (src == 0 && counter == null) src = if (detector != null) 1 else if (accel != null) 2 else -1
        if (src < 0) return false

        StepStore.noteSource(src)
        StepEngine.reset()
        when (src) {
            0 -> manager.registerListener(this, counter, SensorManager.SENSOR_DELAY_NORMAL, 30_000_000)
            1 -> manager.registerListener(this, detector, SensorManager.SENSOR_DELAY_NORMAL, 30_000_000)
            else -> {
                // akcelerometr przy wygaszonym ekranie wymaga, by CPU nie zasypiało
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "dotsteps:accel").also { it.acquire() }
                manager.registerListener(this, accel, 20_000)
            }
        }
        return true
    }

    private fun releaseWakeLock() {
        try { wakeLock?.takeIf { it.isHeld }?.release() } catch (_: Exception) { }
        wakeLock = null
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
        scope.cancel()
        sm?.unregisterListener(this)
        releaseWakeLock()
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
        handler.postDelayed({ finishQuit() }, 1500)
        val ok = try { m.flush(this) } catch (_: Exception) { false }
        if (!ok) finishQuit()
    }

    private fun finishQuit() {
        if (finished) return
        finished = true
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        sm?.unregisterListener(this)
        releaseWakeLock()
        StepStore.sync()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Process.killProcess(Process.myPid())
    }

    override fun onFlushCompleted(sensor: Sensor?) {
        if (quitting) finishQuit()
    }

    override fun onSensorChanged(e: SensorEvent) {
        when (e.sensor.type) {
            Sensor.TYPE_STEP_COUNTER -> StepEngine.onCounter(e.values[0].toLong(), e.timestamp)
            Sensor.TYPE_STEP_DETECTOR -> StepEngine.onDetectorStep(e.timestamp)
            Sensor.TYPE_ACCELEROMETER ->
                StepEngine.onAccel(e.timestamp, e.values[0], e.values[1], e.values[2])
        }
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
