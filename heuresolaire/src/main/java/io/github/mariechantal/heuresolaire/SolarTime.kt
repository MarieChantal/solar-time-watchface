package io.github.mariechantal.heuresolaire

import android.content.Context
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Heure solaire vraie : 12:00 au moment où le soleil passe au méridien (zénith local).
 *
 * heure solaire = UTC + longitude × 4 min + équation du temps.
 * On expose surtout le décalage (en minutes) entre l'heure solaire et l'heure légale du téléphone,
 * pour que le cadran puisse l'ajouter à l'heure courante à chaque minute.
 */
object SolarTime {

    private const val PREFS = "position"
    private const val KEY_LONGITUDE = "longitude"
    private const val KEY_UPDATED = "updated"

    /** Équation du temps en minutes (formule de la NOAA, précision ~30 s). */
    fun equationOfTimeMinutes(utc: ZonedDateTime): Double {
        val dayOfYear = utc.dayOfYear
        val hour = utc.hour + utc.minute / 60.0
        val gamma = 2 * PI / 365 * (dayOfYear - 1 + (hour - 12) / 24)
        return 229.18 * (0.000075 + 0.001868 * cos(gamma) - 0.032077 * sin(gamma) -
            0.014615 * cos(2 * gamma) - 0.040849 * sin(2 * gamma))
    }

    /** Décalage heure solaire − heure légale, en minutes. */
    fun offsetMinutes(longitude: Double, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): Double {
        val utc = now.atZone(ZoneId.of("UTC"))
        val legalOffsetMinutes = zone.rules.getOffset(now).totalSeconds / 60.0
        return longitude * 4.0 + equationOfTimeMinutes(utc) - legalOffsetMinutes
    }

    /** Heure solaire formatée "HH:MM". */
    fun format(longitude: Double, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String {
        val local = now.atZone(zone)
        val localMinutes = local.hour * 60 + local.minute + local.second / 60.0
        val solar = Math.floorMod((localMinutes + offsetMinutes(longitude, now, zone)).toInt(), 1440)
        return "%02d:%02d".format(solar / 60, solar % 60)
    }

    fun savedLongitude(context: Context): Double? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return if (prefs.contains(KEY_LONGITUDE)) prefs.getFloat(KEY_LONGITUDE, 0f).toDouble() else null
    }

    fun savedTime(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_UPDATED, 0L)

    fun saveLongitude(context: Context, longitude: Double) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat(KEY_LONGITUDE, longitude.toFloat())
            .putLong(KEY_UPDATED, System.currentTimeMillis())
            .apply()
    }
}
