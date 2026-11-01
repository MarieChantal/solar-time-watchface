package io.github.mariechantal.heuresolaire

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.CancellationSignal
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors

/**
 * Écran ouvert en touchant la complication : affiche l'heure solaire et met à jour la position
 * par GPS. Le cadran n'utilise jamais le GPS en arrière-plan, pour ménager la batterie.
 */
class PositionActivity : Activity() {

    private lateinit var info: TextView
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        info = TextView(this).apply { gravity = Gravity.CENTER; textSize = 14f }
        val button = Button(this).apply {
            text = "Mettre à jour la position"
            setOnClickListener { updatePosition() }
        }
        // Défilement : sur un écran rond, le bouton peut sortir de l'écran sous un texte long.
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(LinearLayout(this@PositionActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(32, 48, 32, 64)
                addView(info)
                addView(button)
                addView(Button(this@PositionActivity).apply {
                    text = "Réveil solaire"
                    setOnClickListener {
                        startActivity(android.content.Intent(this@PositionActivity, ReveilActivity::class.java))
                    }
                })
            })
        })
        refresh()
        if (SolarTime.savedLongitude(this) == null) updatePosition()
    }

    private fun refresh(status: String? = null) {
        val lon = SolarTime.savedLongitude(this)
        info.text = if (lon == null) {
            status ?: "Position inconnue"
        } else {
            val updated = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(Date(SolarTime.savedTime(this)))
            buildString {
                appendLine("☀ ${SolarTime.format(lon)}")
                appendLine("Longitude %.3f°".format(lon))
                appendLine("Décalage %+.1f min".format(SolarTime.offsetMinutes(lon)))
                appendLine("Position du $updated")
                appendLine(reveil(lon))
                status?.let { appendLine(it) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun reveil(lon: Double): String {
        val r = Reveil.reglages(this)
        if (!r.actif) return "Réveil coupé"
        val nuit = Reveil.prochaine(lon, r.heures) { it.lever }
        return "Coucher ${Reveil.hhmm(nuit.coucher)}, lever ${Reveil.hhmm(nuit.lever)}"
    }

    private fun updatePosition() {
        val perms = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (perms.none { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) {
            requestPermissions(perms, 1)
            return
        }
        refresh("Recherche GPS…")
        val lm = getSystemService(LocationManager::class.java)
        val provider = listOf(LocationManager.GPS_PROVIDER, LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        if (provider == null) {
            refresh("Activez la localisation de la montre")
            return
        }
        try {
            lm.getCurrentLocation(provider, CancellationSignal(), executor) { location: Location? ->
                runOnUiThread {
                    if (location != null) {
                        SolarTime.saveLongitude(this, location.longitude)
                        SolarTimeService.requestUpdate(this)
                        Reveil.programmer(this)
                        refresh("Position mise à jour")
                    } else {
                        refresh("Pas de position, réessayez dehors")
                    }
                }
            }
        } catch (e: SecurityException) {
            refresh("Autorisation de localisation refusée")
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.any { it == PackageManager.PERMISSION_GRANTED }) updatePosition()
        else refresh("Autorisation de localisation refusée")
    }
}
