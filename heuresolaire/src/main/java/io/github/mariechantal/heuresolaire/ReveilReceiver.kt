package io.github.mariechantal.heuresolaire

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Rappel avant le coucher (vibration et notification), et reprogrammation des alarmes
 * au démarrage de la montre ou après une mise à jour de l'appli.
 */
class ReveilReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_RAPPEL) rappel(context)
        Reveil.programmer(context)
    }

    private fun rappel(context: Context) {
        val r = Reveil.reglages(context)
        val lon = SolarTime.savedLongitude(context) ?: return
        if (!r.actif) return
        // Le rappel vient de sonner : la nuit visée est la prochaine dont le coucher est à venir.
        val nuit = Reveil.prochaine(lon, r.heures) { it.coucher }

        if (r.vibreur) vibrer(context)
        notifier(context, r.son, "Coucher à ${Reveil.hhmm(nuit.coucher)}",
            "Lever à ${Reveil.hhmm(nuit.lever)}")
    }

    private fun vibrer(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= 31)
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 200, 300), -1))
    }

    private fun notifier(context: Context, son: Boolean, titre: String, texte: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = context.getSystemService(NotificationManager::class.java)
        // Le son d'un canal ne se change plus après sa création : un canal avec son, un sans.
        val canal = if (son) CANAL_SON else CANAL_SILENCE
        if (nm.getNotificationChannel(canal) == null) {
            nm.createNotificationChannel(
                NotificationChannel(canal, if (son) "Rappel du coucher" else "Rappel du coucher (silencieux)",
                    NotificationManager.IMPORTANCE_HIGH).apply {
                    enableVibration(false) // la vibration est faite à part, selon le réglage
                    if (!son) setSound(null, null)
                }
            )
        }
        nm.notify(NOTIF_RAPPEL, Notification.Builder(context, canal)
            .setSmallIcon(R.drawable.ic_soleil)
            .setContentTitle(titre)
            .setContentText(texte)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentIntent(Reveil.reglagesIntent(context))
            .setAutoCancel(true)
            .setTimeoutAfter(Reveil.RAPPEL_MINUTES * 60_000)
            .build())
    }

    companion object {
        const val ACTION_RAPPEL = "io.github.mariechantal.heuresolaire.RAPPEL"
        private const val CANAL_SON = "rappel_son"
        private const val CANAL_SILENCE = "rappel_silence"
        private const val NOTIF_RAPPEL = 1
    }
}
