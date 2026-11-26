package io.github.sorry4nothing.cevios.downloads

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/** Die Datei ist grösser als erlaubt. */
class TooBigException : IOException()

/** Das Gerät konnte die Datei nicht schreiben (kein Platz, kein Zugriff). */
class StorageException(cause: IOException) : IOException(cause)

/** Eine Datei, die die Person gezielt heruntergeladen hat. `id` ist der Ordnername, `name` der Anzeigename. */
data class DownloadedFile(val campId: String, val id: String, val name: String, val size: Long, val savedAt: Long, val file: File)

/**
 * Gezielt heruntergeladene Dateien, nur in der App: `files/downloads/<Lager>/<Kennung>/<Name>`, nicht in der Galerie oder im öffentlichen
 * Speicher, nicht im Geräte-Backup (`allowBackup=false`). Geöffnet wird über den FileProvider. Eine Datei gehört zu einem Lager und geht
 * mit ihm weg ([deleteCamp], [retainOnly]); beim Abmelden geht alles ([clear]). Pfade entstehen nie aus Eingaben: Ordner sind UUIDs,
 * der Dateiname ist bereinigt ([safeName]).
 */
class Downloads(private val root: File, private val now: () -> Long = System::currentTimeMillis) {
    /** Alle Dateien, neueste zuerst; `campId` begrenzt auf ein Lager. */
    fun list(campId: String? = null): List<DownloadedFile> {
        val camps = if (campId != null) listOfNotNull(campFolder(campId)) else root.listFiles()?.filter { it.isDirectory && ID.matches(it.name) }.orEmpty()
        return camps.flatMap { camp ->
            camp.listFiles()?.filter { it.isDirectory && ID.matches(it.name) }.orEmpty().mapNotNull { folder ->
                folder.listFiles()?.filter { it.isFile && !it.name.startsWith(TEMP_PREFIX) }?.singleOrNull()?.let { DownloadedFile(camp.name, folder.name, it.name, it.length(), it.lastModified(), it) }
            }
        }.sortedByDescending { it.savedAt }
    }

    /**
     * Speichert den Inhalt von `source`. Eine Datei über `maxBytes` ([TooBigException]) oder ein Fehler beim Schreiben ([StorageException])
     * gibt es nicht halb: Es bleibt nichts liegen. Fehler beim Lesen von `source` kommen unverändert als [IOException].
     */
    @Throws(IOException::class)
    fun save(campId: String, name: String, source: InputStream, maxBytes: Long): DownloadedFile {
        val camp = campFolder(campId) ?: throw StorageException(IOException("Lager unbekannt"))
        val folder = File(camp, UUID.randomUUID().toString())
        val target = File(folder, safeName(name))
        val temp = File(folder, "$TEMP_PREFIX${target.name}")
        try {
            if (!folder.mkdirs()) throw StorageException(IOException("Ordner fehlt"))
            var total = 0L
            temp.outputStream().use { out ->
                val buffer = ByteArray(BUFFER)
                while (true) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > maxBytes) throw TooBigException()
                    try {
                        out.write(buffer, 0, read)
                    } catch (e: IOException) {
                        throw StorageException(e)
                    }
                }
            }
            try {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } catch (e: IOException) {
                throw StorageException(e)
            }
            target.setLastModified(now())
            return DownloadedFile(camp.name, folder.name, target.name, total, target.lastModified(), target)
        } catch (e: IOException) {
            folder.deleteRecursively()
            throw e
        }
    }

    @Throws(IOException::class)
    fun saveBytes(campId: String, name: String, bytes: ByteArray): DownloadedFile = save(campId, name, bytes.inputStream(), bytes.size.toLong())

    /** Entfernt genau diese Datei (der Pfad kommt aus der Liste, nie aus einer Eingabe: er muss unter dem Ordner der App liegen). */
    fun remove(file: DownloadedFile) {
        val folder = file.file.parentFile ?: return
        if (folder.canonicalFile.startsWith(root.canonicalFile) && folder != root) folder.deleteRecursively()
    }

    fun deleteCamp(campId: String) {
        campFolder(campId)?.deleteRecursively()
    }

    /** Löscht alles zu Lagern, die nicht in `campIds` stehen. */
    fun retainOnly(campIds: Set<String>) {
        val keep = campIds.map { it.lowercase() }.toSet()
        root.listFiles()?.forEach { if (it.name !in keep) it.deleteRecursively() }
    }

    fun clear() {
        root.deleteRecursively()
    }

    private fun campFolder(campId: String): File? = if (ID.matches(campId)) File(root, campId.lowercase()) else null

    companion object {
        private const val TEMP_PREFIX = ".laden-"
        private const val BUFFER = 8192
        private val ID = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

        /** Dateien, die die App nie speichert: Seiten und Programme, die beim Öffnen etwas ausführen oder installieren könnten. */
        private val BLOCKED = setOf("html", "htm", "xhtml", "apk", "js", "jar", "exe", "bat", "cmd", "sh")

        const val MAX_NAME = 80

        /**
         * Ein Dateiname, der nirgends etwas anrichtet: ohne Pfadteile, Steuerzeichen und Sonderzeichen (Buchstaben, Ziffern, Leerzeichen,
         * `.` `-` `_` `(` `)` bleiben), ohne Punkt am Anfang, höchstens 80 Zeichen mit der Endung. Leer wird «Datei»; `extension` ergänzt
         * eine fehlende Endung.
         */
        fun safeName(name: String, extension: String? = null): String {
            val cleaned = name.substringAfterLast('/').substringAfterLast('\\')
                .map { if (it.isLetterOrDigit() || it in " .-_()") it else '_' }.joinToString("")
                .replace(Regex("\\.{2,}"), ".").trim(' ', '.', '_')
            val base = cleaned.ifEmpty { "Datei" }
            val own = base.substringAfterLast('.', "").takeIf { it.length in 1..5 && it.all(Char::isLetterOrDigit) && base.length > it.length + 1 }
            val ext = (own ?: extension?.lowercase()?.filter { it.isLetterOrDigit() }?.take(5)?.ifEmpty { null })
            val stem = if (own != null) base.dropLast(own.length + 1) else base
            val clipped = stem.take(MAX_NAME - (ext?.length?.plus(1) ?: 0)).trim(' ', '.', '_').ifEmpty { "Datei" }
            val full = if (ext == null) clipped else "$clipped.$ext"
            return if (full.substringAfterLast('.', "").lowercase() in BLOCKED) "$full.txt" else full
        }

        /** Auf dem Gerät: im Ordner der App (`files/downloads`). */
        fun create(context: Context): Downloads = Downloads(File(context.applicationContext.filesDir, "downloads"))

        /** Abmelden und Sitzungsende: alle heruntergeladenen Dateien gehen weg. */
        fun clear(context: Context) = create(context).clear()

        /** Öffnet die Datei in einer App des Geräts (Lesen über den FileProvider, nur diese Datei). `false`, wenn keine App sie öffnet. */
        fun open(context: Context, file: DownloadedFile): Boolean {
            val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file.file)
            val type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.name.substringAfterLast('.', "").lowercase()) ?: "application/octet-stream"
            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            return try {
                context.startActivity(intent)
                true
            } catch (e: ActivityNotFoundException) {
                false
            }
        }
    }
}
