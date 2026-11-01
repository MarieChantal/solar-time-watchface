package io.github.mariechantal.pouls

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Historique local des mesures : une ligne par mesure (instant en ms, battements par minute).
 * Environ 30 octets par ligne, soit ~9 Ko par jour à une mesure toutes les 5 minutes.
 * Rien ne sort de la montre, sauf par l'export CSV demandé par l'utilisateur.
 */
class Historique private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "pouls.db", null, 1) {

    data class Mesure(val t: Long, val bpm: Int)

    override fun onCreate(db: SQLiteDatabase) {
        // La clé primaire sur l'instant sert aussi d'index pour les lectures par période.
        db.execSQL("CREATE TABLE mesures (t INTEGER PRIMARY KEY, bpm INTEGER NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun ajouter(t: Long, bpm: Int) {
        writableDatabase.insertWithOnConflict("mesures", null,
            ContentValues().apply { put("t", t); put("bpm", bpm) }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** Mesures entre [debut] et [fin] (ms), dans l'ordre chronologique. */
    fun entre(debut: Long, fin: Long): List<Mesure> =
        readableDatabase.rawQuery(
            "SELECT t, bpm FROM mesures WHERE t >= ? AND t < ? ORDER BY t",
            arrayOf(debut.toString(), fin.toString()),
        ).use { c ->
            buildList { while (c.moveToNext()) add(Mesure(c.getLong(0), c.getInt(1))) }
        }

    fun nombre(): Long = readableDatabase.rawQuery("SELECT COUNT(*) FROM mesures", null)
        .use { if (it.moveToFirst()) it.getLong(0) else 0L }

    /** Écrit tout l'historique en CSV (date ISO locale, timestamp ms, bpm) et rend le fichier. */
    fun exporterCsv(context: Context): File {
        val dossier = File(context.cacheDir, "export").apply { mkdirs() }
        val fichier = File(dossier, "pouls.csv")
        val iso = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault())
        fichier.bufferedWriter().use { w ->
            w.write("date,timestamp_ms,bpm\n")
            readableDatabase.rawQuery("SELECT t, bpm FROM mesures ORDER BY t", null).use { c ->
                while (c.moveToNext()) {
                    val t = c.getLong(0)
                    w.write("${iso.format(Instant.ofEpochMilli(t))},$t,${c.getInt(1)}\n")
                }
            }
        }
        return fichier
    }

    companion object {
        @Volatile private var instance: Historique? = null
        fun get(context: Context): Historique =
            instance ?: synchronized(this) { instance ?: Historique(context).also { instance = it } }
    }
}
