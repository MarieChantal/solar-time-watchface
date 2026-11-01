package io.github.mariechantal.pouls

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import java.io.DataInputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

/**
 * Envoi d'un fichier par Bluetooth avec le profil OPP (Object Push), celui de
 * « Partager par Bluetooth » sur Android. Le téléphone n'a besoin d'aucune appli :
 * il demande d'accepter le fichier et le range dans ses téléchargements.
 *
 * Protocole OBEX minimal : CONNECT, une suite de PUT (Name, Type, Length, Body…, End-of-Body),
 * puis DISCONNECT.
 */
object EnvoiBluetooth {

    private val OPP_UUID: UUID = UUID.fromString("00001105-0000-1000-8000-00805f9b34fb")

    private const val CONNECT = 0x80
    private const val DISCONNECT = 0x81
    private const val PUT = 0x02
    private const val PUT_FINAL = 0x82
    private const val CONTINUE = 0x90
    private const val OK = 0xA0

    private const val HI_NAME = 0x01
    private const val HI_TYPE = 0x42
    private const val HI_LENGTH = 0xC3
    private const val HI_BODY = 0x48
    private const val HI_END_OF_BODY = 0x49

    private const val MAX_PAQUET = 0x2000

    class Refus(message: String) : IOException(message)

    /**
     * Envoie [fichier] à [appareil] sous le nom [nom]. Bloquant : à appeler hors du fil principal.
     * [progression] reçoit la part envoyée (0 à 1). Lève [Refus] si le téléphone refuse le fichier.
     */
    @SuppressLint("MissingPermission") // vérifiée par l'appelant (BLUETOOTH_CONNECT)
    fun envoyer(appareil: BluetoothDevice, fichier: File, nom: String, progression: (Float) -> Unit) {
        appareil.createRfcommSocketToServiceRecord(OPP_UUID).use { socket ->
            socket.connect()
            val sortie = socket.outputStream
            val entree = DataInputStream(socket.inputStream)

            // CONNECT : version 1.0, pas de drapeaux, taille de paquet proposée.
            ecrire(sortie, CONNECT, byteArrayOf(0x10, 0x00) + u16(MAX_PAQUET))
            val rep = lire(entree)
            if (rep.code != OK) throw IOException("Connexion OBEX refusée (0x%02X)".format(rep.code))
            val maxServeur = if (rep.donnees.size >= 4)
                ((rep.donnees[2].toInt() and 0xFF) shl 8) or (rep.donnees[3].toInt() and 0xFF) else 255
            val max = minOf(MAX_PAQUET, maxServeur).coerceAtLeast(255)

            val total = fichier.length()
            // « text/plain » : Android n'accepte en réception que certains types, et le CSV
            // n'en fait pas partie. Le nom garde l'extension .csv.
            var entetes = entete(HI_NAME, utf16(nom)) +
                entete(HI_TYPE, "text/plain".toByteArray(Charsets.US_ASCII) + byteArrayOf(0)) +
                byteArrayOf(HI_LENGTH.toByte()) + u32(total)
            var envoye = 0L
            fichier.inputStream().buffered().use { flux ->
                while (true) {
                    val place = max - 3 - entetes.size - 3
                    val morceau = ByteArray(place)
                    val n = flux.read(morceau).coerceAtLeast(0)
                    envoye += n
                    val dernier = envoye >= total
                    val corps = entete(if (dernier) HI_END_OF_BODY else HI_BODY, morceau.copyOf(n))
                    ecrire(sortie, if (dernier) PUT_FINAL else PUT, entetes + corps)
                    entetes = ByteArray(0)
                    // Le premier PUT attend que la personne accepte le fichier sur le téléphone.
                    val r = lire(entree).code
                    progression(if (total == 0L) 1f else envoye.toFloat() / total)
                    if (dernier) {
                        if (r != OK) throw Refus("Fichier refusé par le téléphone (0x%02X)".format(r))
                        break
                    }
                    if (r != CONTINUE) throw Refus("Fichier refusé par le téléphone (0x%02X)".format(r))
                }
            }
            runCatching {
                ecrire(sortie, DISCONNECT, ByteArray(0))
                lire(entree)
            }
        }
    }

    private class Reponse(val code: Int, val donnees: ByteArray)

    private fun ecrire(sortie: OutputStream, code: Int, contenu: ByteArray) {
        sortie.write(byteArrayOf(code.toByte()) + u16(3 + contenu.size) + contenu)
        sortie.flush()
    }

    private fun lire(entree: DataInputStream): Reponse {
        val code = entree.readUnsignedByte()
        val longueur = entree.readUnsignedShort()
        val donnees = ByteArray((longueur - 3).coerceAtLeast(0))
        entree.readFully(donnees)
        return Reponse(code, donnees)
    }

    /** En-tête OBEX à longueur sur 2 octets (identifiant + longueur + valeur). */
    private fun entete(id: Int, valeur: ByteArray): ByteArray =
        byteArrayOf(id.toByte()) + u16(3 + valeur.size) + valeur

    /** Chaîne Unicode OBEX : UTF-16 gros-boutiste terminée par un zéro. */
    private fun utf16(s: String): ByteArray = s.toByteArray(Charsets.UTF_16BE) + byteArrayOf(0, 0)

    private fun u16(v: Int) = byteArrayOf((v shr 8).toByte(), v.toByte())
    private fun u32(v: Long) = byteArrayOf((v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte())
}
