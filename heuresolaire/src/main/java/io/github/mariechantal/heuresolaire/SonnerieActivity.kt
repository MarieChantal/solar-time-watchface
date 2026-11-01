package io.github.mariechantal.heuresolaire

import android.app.Activity
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.time.ZonedDateTime

/**
 * Écran du réveil : sonne et/ou vibre jusqu'à ce qu'on touche « Je suis debout ».
 * Pas de répétition. Arrêt automatique au bout de [DUREE_MAX_MILLIS] pour ménager la batterie.
 */
class SonnerieActivity : Activity() {

    private var sonnerie: Ringtone? = null
    private var vibreur: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
            addView(TextView(this@SonnerieActivity).apply {
                gravity = Gravity.CENTER
                textSize = 28f
                text = "☀ ${Reveil.hhmm(ZonedDateTime.now())}"
            })
            addView(Button(this@SonnerieActivity).apply {
                text = "Je suis debout"
                setOnClickListener { arreter() }
            })
        })

        val r = Reveil.reglages(this)
        if (r.son) sonner()
        if (r.vibreur) vibrer()
        handler.postDelayed({ arreter() }, DUREE_MAX_MILLIS)
        // Le réveil de demain.
        Reveil.programmer(this)
    }

    private fun sonner() {
        val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        sonnerie = RingtoneManager.getRingtone(this, uri)?.apply {
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            isLooping = true
            play()
        }
    }

    private fun vibrer() {
        val v = if (Build.VERSION.SDK_INT >= 31)
            getSystemService(VibratorManager::class.java).defaultVibrator
        else @Suppress("DEPRECATION") getSystemService(Vibrator::class.java)
        val motif = VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0)
        if (Build.VERSION.SDK_INT >= 33) {
            v.vibrate(motif, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(motif, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        }
        vibreur = v
    }

    private fun arreter() {
        handler.removeCallbacksAndMessages(null)
        sonnerie?.stop()
        sonnerie = null
        vibreur?.cancel()
        vibreur = null
        if (!isFinishing) finish()
    }

    override fun onDestroy() {
        arreter()
        super.onDestroy()
    }

    private companion object {
        const val DUREE_MAX_MILLIS = 10 * 60 * 1000L
    }
}
