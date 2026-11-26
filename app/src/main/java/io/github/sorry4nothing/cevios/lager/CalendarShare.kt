package io.github.sorry4nothing.cevios.lager

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Teilt die Kalenderdatei eines Höcks (vom Server erzeugt, nur Titel, Ort, Datum und Zeit) über die Systemauswahl. Die Datei liegt
 * kurz im Cache und wird beim nächsten Teilen überschrieben; ältere Dateien räumt der Aufruf vorher weg.
 */
fun shareCalendarFile(context: Context, bytes: ByteArray) = shareFile(context, bytes, "hoeck.ics", "text/calendar")

/**
 * Teilt einen Aushang als PDF (der Server erzeugt ihn und speichert ihn nirgends). Er enthält Namen aus dem Lager (Zimmer,
 * Gruppen, Dienste) und liegt darum nur so lange im Cache, bis die nächste Datei geteilt wird oder das System den Cache räumt.
 */
fun sharePdf(context: Context, bytes: ByteArray, name: String) = shareFile(context, bytes, "$name.pdf", "application/pdf")

/** Abmelden und Sitzungsende: Geteilte Dateien (Aushänge mit Namen aus dem Lager) bleiben nicht im Cache liegen. */
fun clearSharedFiles(context: Context) {
    File(context.cacheDir, "shared").listFiles()?.forEach { it.delete() }
}

/**
 * Öffnet die Kalenderdatei eines Termins (`GET /termine/{id}.ics`) direkt in der Kalender-App, wo die Person sie speichert. Die Datei
 * liegt dafür kurz im Cache (wie beim Teilen, `clearSharedFiles` räumt sie beim Abmelden weg). `false`, wenn keine App sie annimmt.
 */
fun openCalendarFile(context: Context, bytes: ByteArray): Boolean {
    val view = Intent(Intent.ACTION_VIEW).setDataAndType(cacheShared(context, bytes, "termin.ics"), "text/calendar")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        .apply { if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    return try {
        context.startActivity(view)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}

/** Legt die Bytes im Cache ab (FileProvider-Ordner `shared`) und gibt die Adresse, die eine andere App lesen darf. */
private fun cacheShared(context: Context, bytes: ByteArray, fileName: String): Uri {
    clearSharedFiles(context)
    val folder = File(context.cacheDir, "shared").apply { mkdirs() }
    val file = File(folder, fileName).apply { writeBytes(bytes) }
    return FileProvider.getUriForFile(context, context.packageName + ".files", file)
}

private fun shareFile(context: Context, bytes: ByteArray, fileName: String, type: String) {
    val uri = cacheShared(context, bytes, fileName)
    val send = Intent(Intent.ACTION_SEND).apply {
        this.type = type
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        // Keine App nimmt die Datei an: Es gibt nichts zu teilen.
    }
}
