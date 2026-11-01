package io.github.mariechantal.heuresolaire

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToLong

/**
 * Réveil solaire : la nuit de sommeil est centrée sur le minuit solaire.
 *
 * coucher = minuit solaire − durée / 2, lever = minuit solaire + durée / 2.
 * Un rappel vibre [RAPPEL_MINUTES] minutes avant le coucher, et le réveil sonne au lever.
 * Les deux alarmes sont reprogrammées après chaque déclenchement, au démarrage de la montre,
 * au changement d'heure ou de fuseau, et quand la position change.
 */
object Reveil {

    private const val TAG = "Reveil"
    private const val PREFS = "reveil"
    const val RAPPEL_MINUTES = 15L
    const val HEURES_MIN = 4.0
    const val HEURES_MAX = 12.0

    private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    data class Reglages(val actif: Boolean, val heures: Double, val vibreur: Boolean, val son: Boolean)

    data class Nuit(val minuit: ZonedDateTime, val coucher: ZonedDateTime, val lever: ZonedDateTime) {
        val rappel: ZonedDateTime get() = coucher.minusMinutes(RAPPEL_MINUTES)
    }

    fun reglages(context: Context): Reglages {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Reglages(
            actif = p.getBoolean("actif", false),
            heures = p.getFloat("heures", 8f).toDouble(),
            vibreur = p.getBoolean("vibreur", true),
            son = p.getBoolean("son", true),
        )
    }

    fun enregistrer(context: Context, r: Reglages) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("actif", r.actif)
            .putFloat("heures", r.heures.toFloat())
            .putBoolean("vibreur", r.vibreur)
            .putBoolean("son", r.son)
            .apply()
        programmer(context)
    }

    /** Instant (heure légale) où l'heure solaire passe à 00:00 au début du jour [date]. */
    fun minuitSolaire(date: LocalDate, longitude: Double, zone: ZoneId = ZoneId.systemDefault()): ZonedDateTime {
        val minuitLegal = date.atStartOfDay(zone)
        var t = minuitLegal
        // Le décalage dépend (très peu) de l'instant : deux itérations suffisent.
        repeat(2) {
            val decalage = SolarTime.offsetMinutes(longitude, t.toInstant(), zone)
            t = minuitLegal.minusSeconds((decalage * 60).roundToLong())
        }
        return t
    }

    fun nuit(date: LocalDate, longitude: Double, heures: Double, zone: ZoneId = ZoneId.systemDefault()): Nuit {
        val minuit = minuitSolaire(date, longitude, zone)
        val demi = (heures * 30).roundToLong()
        return Nuit(minuit, minuit.minusMinutes(demi), minuit.plusMinutes(demi))
    }

    /** Première nuit dont l'instant choisi par [quand] est encore à venir. */
    fun prochaine(
        longitude: Double, heures: Double,
        maintenant: ZonedDateTime = ZonedDateTime.now(),
        quand: (Nuit) -> ZonedDateTime,
    ): Nuit = (-1L..2L).asSequence()
        .map { nuit(maintenant.toLocalDate().plusDays(it), longitude, heures, maintenant.zone) }
        .first { quand(it).isAfter(maintenant) }

    fun hhmm(t: ZonedDateTime): String = t.format(HHMM)

    /** Heure solaire "HH:MM" d'un nombre de minutes après le minuit solaire (négatif = avant). */
    fun hhmmSolaire(minutesApresMinuit: Long): String {
        val m = Math.floorMod(minutesApresMinuit, 1440L)
        return "%02d:%02d".format(m / 60, m % 60)
    }

    /** (Re)programme le réveil et le rappel, ou les annule si le réveil est coupé. */
    fun programmer(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val sonnerie = sonnerieIntent(context)
        val rappel = rappelIntent(context)
        am.cancel(sonnerie)
        am.cancel(rappel)

        val r = reglages(context)
        val lon = SolarTime.savedLongitude(context)
        if (!r.actif || lon == null) return

        val lever = prochaine(lon, r.heures) { it.lever }.lever
        val avant = prochaine(lon, r.heures) { it.rappel }.rappel
        try {
            // setAlarmClock : alarme exacte, même en veille, et autorisée à ouvrir l'écran de sonnerie.
            am.setAlarmClock(
                AlarmManager.AlarmClockInfo(lever.toInstant().toEpochMilli(), reglagesIntent(context)),
                sonnerie,
            )
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, avant.toInstant().toEpochMilli(), rappel)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, avant.toInstant().toEpochMilli(), rappel)
            }
            Log.i(TAG, "Rappel à ${hhmm(avant)}, réveil à ${hhmm(lever)}")
        } catch (e: SecurityException) {
            Log.e(TAG, "Alarmes exactes refusées", e)
        }
    }

    private fun sonnerieIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 10,
        Intent(context, SonnerieActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun rappelIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 11,
        Intent(context, ReveilReceiver::class.java).setAction(ReveilReceiver.ACTION_RAPPEL),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun reglagesIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 12, Intent(context, ReveilActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
