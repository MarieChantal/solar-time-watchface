package io.github.mariechantal.pouls

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.DateFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date

/**
 * Écran de l'appli « Pouls » : affiche la dernière mesure et demande l'accès au capteur.
 * La complication lit le capteur en arrière-plan : il faut BODY_SENSORS puis, à partir
 * d'Android 13, l'accès « tout le temps » (BODY_SENSORS_BACKGROUND).
 */
class PoulsActivity : Activity() {

    private lateinit var info: TextView
    private lateinit var button: Button
    private lateinit var sport: Button
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val rafraichir = object : Runnable {
        override fun run() { refresh(); handler.postDelayed(this, 15_000) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        info = TextView(this).apply { gravity = Gravity.CENTER; textSize = 14f }
        button = Button(this).apply { setOnClickListener { request() } }
        sport = Button(this).apply { setOnClickListener { basculerSport() } }
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(LinearLayout(this@PoulsActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(32, 48, 32, 64)
                addView(info)
                addView(button)
                addView(sport)
                addView(Button(this@PoulsActivity).apply {
                    text = "Historique"
                    setOnClickListener {
                        startActivity(android.content.Intent(this@PoulsActivity, HistoriqueActivity::class.java))
                    }
                })
            })
        })
    }

    override fun onResume() {
        super.onResume()
        handler.post(rafraichir)
    }

    override fun onPause() {
        handler.removeCallbacks(rafraichir)
        super.onPause()
    }

    /** Mode sport : une mesure toutes les 30 s pendant 1 h au plus ; un second appui l'arrête. */
    private fun basculerSport() {
        if (SportService.actif(this)) {
            SportService.arreter(this)
            refresh("Retour à une mesure toutes les 5 min")
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            // La notification permet d'arrêter la séance sans rouvrir l'appli ; on démarre quand même si refusée.
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 3)
            return
        }
        SportService.demarrer(this)
        handler.postDelayed({ refresh("Mesure toutes les 30 s pendant 1 h") }, 300)
    }

    private fun needsBackground(): Boolean = Build.VERSION.SDK_INT >= 33 &&
        checkSelfPermission(Manifest.permission.BODY_SENSORS_BACKGROUND) != PackageManager.PERMISSION_GRANTED

    private fun refresh(status: String? = null) {
        val (bpm, at) = HeartRateService.saved(this)
        info.text = buildString {
            appendLine(if (bpm == null) "♥ --" else "♥ $bpm")
            if (bpm != null) appendLine("Mesuré à " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(at)))
            when {
                !HeartRateService.hasPermission(this@PoulsActivity) -> appendLine("Accès au capteur : non autorisé")
                needsBackground() -> appendLine("Accès au capteur : à autoriser « tout le temps »")
                SportService.actif(this@PoulsActivity) -> appendLine("Mesure toutes les 30 s, jusqu'à " +
                    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(SportService.fin(this@PoulsActivity)!!)))
                else -> appendLine("Mesure toutes les 5 min")
            }
            status?.let { appendLine(it) }
        }
        val ready = HeartRateService.hasPermission(this) && !needsBackground()
        button.text = if (ready) "Mesurer maintenant" else "Autoriser le capteur"
        button.visibility = View.VISIBLE
        sport.visibility = if (ready) View.VISIBLE else View.GONE
        sport.text = if (SportService.actif(this)) "Arrêter la mesure à 30 s" else "Mesure à 30 s"
    }

    private fun request() {
        when {
            !HeartRateService.hasPermission(this) ->
                requestPermissions(arrayOf(Manifest.permission.BODY_SENSORS), 1)
            needsBackground() ->
                requestPermissions(arrayOf(Manifest.permission.BODY_SENSORS_BACKGROUND), 2)
            else -> mesurerMaintenant()
        }
    }

    /**
     * Mesure directement depuis l'appli : le bouton marche même si la complication n'est sur
     * aucun cadran (par exemple après une réinstallation, qui la retire du cadran).
     */
    private fun mesurerMaintenant() {
        button.isEnabled = false
        refresh("Mesure en cours, gardez la montre au poignet…")
        CoroutineScope(Dispatchers.Default).launch {
            val bpm = HeartRateService.measure(this@PoulsActivity)
            if (bpm != null) HeartRateService.save(this@PoulsActivity, bpm)
            HeartRateService.requestUpdate(this@PoulsActivity)
            runOnUiThread {
                button.isEnabled = true
                refresh(if (bpm != null) "Mesure enregistrée" else "Pas de mesure fiable, réessayez")
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 3) {
            SportService.demarrer(this)
            handler.postDelayed({ refresh("Mesure toutes les 30 s pendant 1 h") }, 300)
            return
        }
        if (grantResults.any { it == PackageManager.PERMISSION_GRANTED }) request()
        else refresh("Autorisation refusée")
    }
}
