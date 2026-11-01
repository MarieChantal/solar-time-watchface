package io.github.mariechantal.pouls

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * Courbe de la fréquence cardiaque sur une période. Les mesures sont regroupées par colonne
 * de pixels (moyenne), et la ligne s'interrompt là où il n'y a pas de mesure.
 */
class CourbeView(context: Context) : View(context) {

    private var mesures: List<Historique.Mesure> = emptyList()
    private var debut = 0L
    private var fin = 1L

    private val ligne = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(230, 70, 60); strokeWidth = 3f; style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
    }
    private val point = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(230, 70, 60) }
    private val grille = Paint().apply { color = Color.argb(70, 255, 255, 255); strokeWidth = 1f }
    private val texte = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.LTGRAY; textSize = 20f }

    fun afficher(mesures: List<Historique.Mesure>, debut: Long, fin: Long) {
        this.mesures = mesures; this.debut = debut; this.fin = fin
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val g = 40f; val d = width - 16f; val h = 8f; val b = height - 8f
        if (mesures.isEmpty()) {
            texte.textAlign = Paint.Align.CENTER
            canvas.drawText("Aucune mesure", width / 2f, height / 2f, texte)
            return
        }
        val lo = (mesures.minOf { it.bpm } - 5).coerceAtLeast(30)
        val hi = (mesures.maxOf { it.bpm } + 5).coerceAtMost(230)
        val y = { bpm: Float -> b - (bpm - lo) / (hi - lo).toFloat() * (b - h) }

        texte.textAlign = Paint.Align.RIGHT
        for (v in listOf(lo, (lo + hi) / 2, hi)) {
            canvas.drawLine(g, y(v.toFloat()), d, y(v.toFloat()), grille)
            canvas.drawText("$v", g - 4f, y(v.toFloat()) + 7f, texte)
        }

        // Une moyenne par colonne de 2 px.
        val colonnes = max(1, ((d - g) / 2f).toInt())
        val somme = DoubleArray(colonnes); val n = IntArray(colonnes)
        val duree = (fin - debut).toDouble()
        for (m in mesures) {
            val i = min(colonnes - 1, (((m.t - debut) / duree) * colonnes).toInt().coerceAtLeast(0))
            somme[i] += m.bpm.toDouble(); n[i]++
        }
        val chemin = Path()
        var dansTrait = false
        var vides = 0
        // Tolère quelques colonnes vides avant de couper la ligne (mesures toutes les 5 min).
        val trouMax = max(1, (colonnes * 15 * 60_000.0 / duree).toInt())
        for (i in 0 until colonnes) {
            if (n[i] == 0) { if (++vides > trouMax) dansTrait = false; continue }
            vides = 0
            val px = g + (i + 0.5f) * (d - g) / colonnes
            val py = y((somme[i] / n[i]).toFloat())
            if (dansTrait) chemin.lineTo(px, py) else { chemin.moveTo(px, py); dansTrait = true }
            canvas.drawCircle(px, py, 2.5f, point) // visible même pour une mesure isolée
        }
        canvas.drawPath(chemin, ligne)
    }
}
