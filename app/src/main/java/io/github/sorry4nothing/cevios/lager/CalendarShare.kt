package io.github.sorry4nothing.cevios.lager

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
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

private fun shareFile(context: Context, bytes: ByteArray, fileName: String, type: String) {
    val folder = File(context.cacheDir, "shared").apply { mkdirs() }
    folder.listFiles()?.forEach { it.delete() }
    val file = File(folder, fileName).apply { writeBytes(bytes) }
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
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
