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
fun shareCalendarFile(context: Context, bytes: ByteArray) {
    val folder = File(context.cacheDir, "shared").apply { mkdirs() }
    folder.listFiles()?.forEach { it.delete() }
    val file = File(folder, "hoeck.ics").apply { writeBytes(bytes) }
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/calendar"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        // Keine App nimmt Kalenderdateien an: Es gibt nichts zu teilen.
    }
}
