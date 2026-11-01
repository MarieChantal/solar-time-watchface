package io.github.mariechantal.pouls

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.FileProvider
import java.util.concurrent.Executors

/**
 * Historique de la fréquence cardiaque : courbe sur 24 h, 7 jours ou 30 jours (toucher la courbe
 * pour changer de période), minimum, maximum et moyenne, et export CSV vers le téléphone.
 */
class HistoriqueActivity : Activity() {

    private enum class Periode(val libelle: String, val millis: Long) {
        JOUR("24 heures", 24 * 3_600_000L),
        SEMAINE("7 jours", 7 * 24 * 3_600_000L),
        MOIS("30 jours", 30 * 24 * 3_600_000L),
    }

    private var periode = Periode.JOUR
    private lateinit var titre: TextView
    private lateinit var courbe: CourbeView
    private lateinit var stats: TextView
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        titre = TextView(this).apply {
            gravity = Gravity.CENTER; textSize = 16f
            setOnClickListener { periodeSuivante() }
        }
        courbe = CourbeView(this).apply { setOnClickListener { periodeSuivante() } }
        stats = TextView(this).apply { gravity = Gravity.CENTER; textSize = 14f }
        val exporter = Button(this).apply {
            text = "Envoyer au téléphone"
            setOnClickListener { exporter() }
        }
        val hauteurCourbe = (resources.displayMetrics.heightPixels * 0.45f).toInt()
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(LinearLayout(this@HistoriqueActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(24, 40, 24, 72)
                addView(titre)
                addView(courbe, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, hauteurCourbe))
                addView(stats)
                addView(exporter)
            })
        })
    }

    override fun onResume() {
        super.onResume()
        charger()
    }

    private fun periodeSuivante() {
        periode = Periode.entries[(periode.ordinal + 1) % Periode.entries.size]
        charger()
    }

    private fun charger() {
        titre.text = "♥ ${periode.libelle}  ›"
        val p = periode
        executor.execute {
            val fin = System.currentTimeMillis()
            val debut = fin - p.millis
            val mesures = Historique.get(this).entre(debut, fin)
            runOnUiThread {
                if (p != periode) return@runOnUiThread
                courbe.afficher(mesures, debut, fin)
                stats.text = if (mesures.isEmpty()) "Pas encore de mesure sur cette période"
                else "min ${mesures.minOf { it.bpm }} · moy ${mesures.map { it.bpm }.average().toInt()} · max ${mesures.maxOf { it.bpm }}\n${mesures.size} mesures"
            }
        }
    }

    /** Écrit le CSV et ouvre le partage Android : choisir « Bluetooth » pour l'envoyer au téléphone. */
    private fun exporter() {
        stats.text = "Préparation du fichier…"
        executor.execute {
            val fichier = Historique.get(this).exporterCsv(this)
            val uri = FileProvider.getUriForFile(this, "$packageName.fichiers", fichier)
            val envoi = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Historique du pouls")
                clipData = ClipData.newRawUri("pouls.csv", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runOnUiThread {
                if (envoi.resolveActivity(packageManager) == null) {
                    stats.text = "Aucun moyen d'envoi trouvé sur la montre"
                } else {
                    startActivity(Intent.createChooser(envoi, "Envoyer l'historique"))
                    charger()
                }
            }
        }
    }
}
