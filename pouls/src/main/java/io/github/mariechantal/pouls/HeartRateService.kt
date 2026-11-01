package io.github.mariechantal.pouls

import android.Manifest
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.util.Log
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Complication « Fréquence cardiaque » qui lit directement le capteur de la montre,
 * sans Samsung Health ni Health Services. À chaque mise à jour (toutes les 5 min),
 * le capteur est allumé jusqu'à obtenir une mesure fiable (20 s au plus), puis éteint.
 * Même principe de lecture que HeartRateRecorder d'OpenVitals (Sensor.TYPE_HEART_RATE).
 */
class HeartRateService : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        if (hasPermission(this)) {
            measure()?.let { save(this, it) }
        }
        val (bpm, at) = saved(this)
        val fresh = bpm != null && System.currentTimeMillis() - at < STALE_AFTER_MILLIS
        val text = when {
            !hasPermission(this) -> "♥ ?"
            fresh -> bpm.toString()
            else -> "--"
        }
        return build(request.complicationType, text, if (fresh) bpm!! else 0)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? = build(type, "68", 68)

    private fun build(type: ComplicationType, text: String, bpm: Int): ComplicationData? {
        val t = PlainComplicationText.Builder(text).build()
        val description = PlainComplicationText.Builder("Fréquence cardiaque").build()
        val tap = tapIntent(this)
        return when (type) {
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(t, description).setTapAction(tap).build()
            ComplicationType.RANGED_VALUE ->
                RangedValueComplicationData.Builder(bpm.toFloat().coerceIn(30f, 200f), 30f, 200f, description)
                    .setText(t).setTapAction(tap).build()
            else -> null
        }
    }

    /** Allume le capteur et rend la première mesure fiable, ou null au bout de 20 s. */
    private suspend fun measure(): Int? {
        val sm = getSystemService(SensorManager::class.java) ?: return null
        val sensor = sm.getDefaultSensor(Sensor.TYPE_HEART_RATE) ?: return null
        val startedAt = SystemClock.elapsedRealtimeNanos()
        return withTimeoutOrNull(MEASURE_TIMEOUT_MILLIS) {
            suspendCancellableCoroutine { cont ->
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        // Ignore la valeur en cache d'avant l'allumage, l'absence de contact et les mesures douteuses.
                        if (event.timestamp < startedAt) return
                        if (event.accuracy == SensorManager.SENSOR_STATUS_NO_CONTACT ||
                            event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) return
                        val bpm = event.values.firstOrNull()?.toInt() ?: return
                        if (bpm !in 30..230) return
                        sm.unregisterListener(this)
                        if (cont.isActive) cont.resume(bpm)
                    }
                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                }
                val ok = try {
                    sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                } catch (e: SecurityException) {
                    Log.w(TAG, "Capteur cardiaque refusé", e)
                    false
                }
                if (!ok) cont.resume(null) else cont.invokeOnCancellation { sm.unregisterListener(listener) }
            }
        }
    }

    companion object {
        private const val TAG = "HeartRateService"
        private const val PREFS = "pouls"
        private const val MEASURE_TIMEOUT_MILLIS = 20_000L
        private const val STALE_AFTER_MILLIS = 30 * 60 * 1000L

        fun hasPermission(context: Context): Boolean =
            context.checkSelfPermission(Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED

        fun saved(context: Context): Pair<Int?, Long> {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return (if (p.contains("bpm")) p.getInt("bpm", 0) else null) to p.getLong("at", 0L)
        }

        private fun save(context: Context, bpm: Int) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt("bpm", bpm).putLong("at", System.currentTimeMillis()).apply()
            Historique.get(context).ajouter(System.currentTimeMillis(), bpm)
        }

        fun tapIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 0, Intent(context, PoulsActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        fun requestUpdate(context: Context) {
            ComplicationDataSourceUpdateRequester.create(
                context, ComponentName(context, HeartRateService::class.java)
            ).requestUpdateAll()
        }
    }
}
