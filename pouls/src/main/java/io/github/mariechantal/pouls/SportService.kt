package io.github.mariechantal.pouls

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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.ServiceCompat

/**
 * Mode sport : le capteur cardiaque reste allumé et une mesure est enregistrée toutes les
 * [INTERVALLE_MILLIS] (30 s), au lieu d'une toutes les 5 min. S'arrête au second appui sur le
 * bouton, depuis la notification, ou de lui-même au bout de [DUREE_MILLIS] (1 h).
 *
 * Garder le capteur allumé coûte moins cher que l'allumer toutes les 30 s, car il lui faut
 * plusieurs secondes pour se caler à chaque démarrage.
 */
class SportService : Service(), SensorEventListener {

    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private var derniere = 0L
    private var demarre = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_ARRETER || !HeartRateService.hasPermission(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (demarre) return START_NOT_STICKY
        demarre = true

        val fin = System.currentTimeMillis() + DUREE_MILLIS
        prefs(this).edit().putLong(KEY_FIN, fin).apply()
        ServiceCompat.startForeground(this, NOTIF_ID, notification(fin),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)

        // Garde le processeur éveillé pendant la séance, écran éteint compris.
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pouls:sport")
            .apply { acquire(DUREE_MILLIS + 60_000) }

        val sm = getSystemService(SensorManager::class.java)
        val capteur = sm.getDefaultSensor(Sensor.TYPE_HEART_RATE, true)
            ?: sm.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        val ok = capteur != null && runCatching {
            sm.registerListener(this, capteur, SensorManager.SENSOR_DELAY_NORMAL)
        }.getOrDefault(false)
        if (!ok) {
            stopSelf()
            return START_NOT_STICKY
        }
        handler.postDelayed({ stopSelf() }, DUREE_MILLIS)
        HeartRateService.requestUpdate(this)
        return START_NOT_STICKY
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_HEART_RATE) return
        if (event.accuracy == SensorManager.SENSOR_STATUS_NO_CONTACT ||
            event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) return
        val bpm = event.values.firstOrNull()?.toInt() ?: return
        if (bpm !in 30..230) return
        val maintenant = SystemClock.elapsedRealtime()
        if (maintenant - derniere < INTERVALLE_MILLIS - 1_000) return
        derniere = maintenant
        HeartRateService.save(this, bpm)
        HeartRateService.requestUpdate(this)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        getSystemService(SensorManager::class.java).unregisterListener(this)
        wakeLock?.let { if (it.isHeld) it.release() }
        prefs(this).edit().remove(KEY_FIN).apply()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        HeartRateService.requestUpdate(this)
        super.onDestroy()
    }

    private fun notification(fin: Long): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CANAL) == null) {
            nm.createNotificationChannel(NotificationChannel(CANAL, "Mesure à 30 s",
                NotificationManager.IMPORTANCE_LOW))
        }
        val arreter = PendingIntent.getService(this, 1,
            Intent(this, SportService::class.java).setAction(ACTION_ARRETER),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val ouvrir = PendingIntent.getActivity(this, 2, Intent(this, PoulsActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, CANAL)
            .setSmallIcon(R.drawable.ic_coeur)
            .setContentTitle("Mesure à 30 s")
            .setContentText("Arrêt automatique à " +
                java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(fin)))
            .setContentIntent(ouvrir)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Arrêter", arreter).build())
            .build()
    }

    companion object {
        const val INTERVALLE_MILLIS = 30_000L
        const val DUREE_MILLIS = 60 * 60_000L
        private const val ACTION_ARRETER = "io.github.mariechantal.pouls.ARRETER_SPORT"
        private const val CANAL = "sport"
        private const val NOTIF_ID = 30
        private const val KEY_FIN = "sport_fin"

        private fun prefs(context: Context) = context.getSharedPreferences("sport", Context.MODE_PRIVATE)

        /** Heure de fin du mode sport en cours, ou null s'il est arrêté. */
        fun fin(context: Context): Long? =
            prefs(context).getLong(KEY_FIN, 0L).takeIf { it > System.currentTimeMillis() }

        fun actif(context: Context): Boolean = fin(context) != null

        fun demarrer(context: Context) {
            context.startForegroundService(Intent(context, SportService::class.java))
        }

        fun arreter(context: Context) {
            context.stopService(Intent(context, SportService::class.java))
        }
    }
}
