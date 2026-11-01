package io.github.mariechantal.heuresolaire

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService

/**
 * Complication « Heure solaire ».
 *
 * RANGED_VALUE : la valeur est le décalage (minutes) à ajouter à l'heure légale. Le cadran
 * l'ajoute lui-même à l'heure courante, donc l'affichage change bien chaque minute alors que
 * cette complication n'est mise à jour qu'une fois par heure.
 * SHORT_TEXT : l'heure solaire en texte, pour les autres cadrans (rafraîchie moins souvent).
 */
class SolarTimeService : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val longitude = currentLongitude(this) ?: return noPosition(request.complicationType)
        return build(request.complicationType, longitude)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? = build(type, -1.68)

    private fun build(type: ComplicationType, longitude: Double): ComplicationData? {
        val offset = SolarTime.offsetMinutes(longitude).toFloat()
        val text = PlainComplicationText.Builder(SolarTime.format(longitude)).build()
        val description = PlainComplicationText.Builder("Heure solaire").build()
        val tap = tapIntent(this)
        return when (type) {
            ComplicationType.RANGED_VALUE ->
                RangedValueComplicationData.Builder(offset, -720f, 720f, description)
                    .setText(text)
                    .setTapAction(tap)
                    .build()
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(text, description)
                    .setTitle(PlainComplicationText.Builder("SOL").build())
                    .setTapAction(tap)
                    .build()
            else -> null
        }
    }

    private fun noPosition(type: ComplicationType): ComplicationData? {
        val text = PlainComplicationText.Builder("GPS ?").build()
        val description = PlainComplicationText.Builder("Touchez pour obtenir la position").build()
        return when (type) {
            ComplicationType.RANGED_VALUE ->
                RangedValueComplicationData.Builder(0f, -720f, 720f, description)
                    .setText(text).setTapAction(tapIntent(this)).build()
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(text, description)
                    .setTapAction(tapIntent(this)).build()
            else -> null
        }
    }

    companion object {
        /** Dernière longitude connue : position mémorisée, sinon dernière position du système. */
        fun currentLongitude(context: Context): Double? {
            lastKnownLongitude(context)?.let { return it }
            return SolarTime.savedLongitude(context)
        }

        private fun lastKnownLongitude(context: Context): Double? = try {
            val lm = context.getSystemService(LocationManager::class.java)
            listOf(LocationManager.FUSED_PROVIDER, LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
                .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
                .maxByOrNull { it.time }
                ?.takeIf { it.time > SolarTime.savedTime(context) }
                ?.also { SolarTime.saveLongitude(context, it.longitude) }
                ?.longitude
        } catch (e: SecurityException) {
            null
        }

        fun tapIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 0, Intent(context, PositionActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        fun requestUpdate(context: Context) {
            ComplicationDataSourceUpdateRequester.create(
                context, ComponentName(context, SolarTimeService::class.java)
            ).requestUpdateAll()
        }
    }
}
