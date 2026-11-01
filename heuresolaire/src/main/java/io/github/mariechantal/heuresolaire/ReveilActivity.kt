package io.github.mariechantal.heuresolaire

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/**
 * Réglages du réveil solaire : durée de sommeil, vibreur, son.
 * Les heures de coucher et de lever se déduisent du minuit solaire.
 */
class ReveilActivity : Activity() {

    private lateinit var resume: TextView
    private lateinit var duree: TextView
    private lateinit var actif: Switch
    private lateinit var vibreur: Switch
    private lateinit var son: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val r = Reveil.reglages(this)

        resume = TextView(this).apply { gravity = Gravity.CENTER; textSize = 14f }
        duree = TextView(this).apply { gravity = Gravity.CENTER; textSize = 20f }
        actif = Switch(this).apply { text = "Réveil"; isChecked = r.actif }
        vibreur = Switch(this).apply { text = "Vibreur"; isChecked = r.vibreur }
        son = Switch(this).apply { text = "Son"; isChecked = r.son }
        val moins = Button(this).apply { text = "−"; setOnClickListener { changerDuree(-0.5) } }
        val plus = Button(this).apply { text = "+"; setOnClickListener { changerDuree(+0.5) } }

        actif.setOnCheckedChangeListener { _, coche ->
            if (coche) demanderNotifications()
            sauver()
        }
        vibreur.setOnCheckedChangeListener { _, _ -> sauver() }
        son.setOnCheckedChangeListener { _, _ -> sauver() }

        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(LinearLayout(this@ReveilActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(40, 48, 40, 72)
                addView(TextView(this@ReveilActivity).apply {
                    gravity = Gravity.CENTER; textSize = 12f; text = "Durée de sommeil"
                })
                // Trois parts égales : le style des boutons Wear est très large par défaut,
                // ce qui poussait « + » hors de l'écran.
                addView(LinearLayout(this@ReveilActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    val part = { LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
                    moins.minWidth = 0; moins.minimumWidth = 0
                    plus.minWidth = 0; plus.minimumWidth = 0
                    addView(moins, part())
                    addView(duree, part())
                    addView(plus, part())
                })
                addView(resume)
                addView(actif)
                addView(vibreur)
                addView(son)
            })
        })
        afficher()
    }

    private var heures = 0.0

    private fun changerDuree(pas: Double) {
        heures = (heures + pas).coerceIn(Reveil.HEURES_MIN, Reveil.HEURES_MAX)
        sauver()
    }

    private fun sauver() {
        Reveil.enregistrer(this, Reveil.Reglages(actif.isChecked, heures, vibreur.isChecked, son.isChecked))
        afficher()
    }

    private fun afficher() {
        val r = Reveil.reglages(this)
        heures = r.heures
        duree.text = if (heures % 1.0 == 0.0) "%d h".format(heures.toInt())
            else "%d h 30".format(heures.toInt())
        val lon = SolarTime.savedLongitude(this)
        resume.text = if (lon == null) {
            "Position inconnue : ouvrez d'abord Heure solaire et mettez la position à jour."
        } else {
            val nuit = Reveil.prochaine(lon, heures) { it.lever }
            val demi = (heures * 30).toLong()
            buildString {
                appendLine("Minuit solaire : ${Reveil.hhmm(nuit.minuit)}")
                appendLine("Coucher : ${Reveil.hhmm(nuit.coucher)}")
                appendLine("☀ ${Reveil.hhmmSolaire(-demi)} solaire")
                appendLine("Rappel : ${Reveil.hhmm(nuit.rappel)}")
                appendLine("Lever : ${Reveil.hhmm(nuit.lever)}")
                appendLine("☀ ${Reveil.hhmmSolaire(demi)} solaire")
                if (r.actif && !r.son && !r.vibreur) appendLine("Ni son ni vibreur : l'écran s'allumera seulement.")
            }
        }
    }

    private fun demanderNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
    }
}
