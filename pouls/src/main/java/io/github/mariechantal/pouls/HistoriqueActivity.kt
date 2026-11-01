package io.github.mariechantal.pouls

import android.app.Activity
import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

/**
 * Historique de la fréquence cardiaque : courbe sur 24 h, 7 jours ou 30 jours (toucher la courbe
 * pour changer de période), minimum, maximum et moyenne, et envoi du CSV au téléphone par Bluetooth.
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

    /** Demande l'accès Bluetooth si besoin, puis choisit le téléphone et envoie le CSV. */
    private fun exporter() {
        if (Build.VERSION.SDK_INT >= 31 &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 1)
            return
        }
        val bt = getSystemService(BluetoothManager::class.java)?.adapter
        if (bt == null || !bt.isEnabled) {
            stats.text = "Bluetooth désactivé"
            return
        }
        val appareils = try { bt.bondedDevices.toList() } catch (e: SecurityException) { emptyList() }
        // Les téléphones d'abord ; s'il n'y en a qu'un, pas besoin de demander.
        val telephones = appareils.filter {
            runCatching { it.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.PHONE }.getOrDefault(false)
        }
        when {
            telephones.size == 1 -> envoyer(telephones[0])
            appareils.isEmpty() -> stats.text = "Aucun appareil Bluetooth associé"
            else -> {
                val liste = telephones.ifEmpty { appareils }
                val noms = liste.map { runCatching { it.name }.getOrNull() ?: it.address }.toTypedArray()
                AlertDialog.Builder(this)
                    .setTitle("Envoyer à")
                    .setItems(noms) { _, i -> envoyer(liste[i]) }
                    .show()
            }
        }
    }

    private fun envoyer(appareil: BluetoothDevice) {
        val nomAppareil = runCatching { appareil.name }.getOrNull() ?: appareil.address
        stats.text = "Connexion à $nomAppareil…"
        executor.execute {
            val message = try {
                val fichier = Historique.get(this).exporterCsv(this)
                val nom = "pouls-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) + ".csv"
                runOnUiThread { stats.text = "Acceptez le fichier sur le téléphone…" }
                EnvoiBluetooth.envoyer(appareil, fichier, nom) { part ->
                    runOnUiThread { stats.text = "Envoi : %d %%".format((part * 100).toInt()) }
                }
                "Fichier $nom envoyé à $nomAppareil"
            } catch (e: EnvoiBluetooth.Refus) {
                "Le téléphone a refusé le fichier"
            } catch (e: Exception) {
                "Échec de l'envoi : ${e.message ?: e.javaClass.simpleName}"
            }
            runOnUiThread { stats.text = message }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.any { it == PackageManager.PERMISSION_GRANTED }) exporter()
        else stats.text = "Accès Bluetooth refusé"
    }
}
