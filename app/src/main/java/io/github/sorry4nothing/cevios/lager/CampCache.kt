package io.github.sorry4nothing.cevios.lager

import android.content.Context
import io.github.sorry4nothing.cevios.net.AesGcmSealer
import io.github.sorry4nothing.cevios.net.CachedBody
import io.github.sorry4nothing.cevios.net.ResponseCache
import io.github.sorry4nothing.cevios.net.Sealer
import io.github.sorry4nothing.cevios.net.keystoreAesKey
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.ProviderException

/** Der Pfad der Lagerliste; ihre Antwort liegt nicht unter einem Lager. */
const val CAMP_LIST_PATH = "/api/v1/lager"

/**
 * Der zuletzt geladene Stand der Lager auf dem Gerät: je Antwort eine Datei, AES-GCM-verschlüsselt (Schlüssel im Android Keystore),
 * in einem Ordner je Lager. Die Datei trägt den Zeitpunkt des Ladens; nach 30 Tagen gilt sie als nicht mehr da. Verschlüsselung
 * und Dateien sind austauschbar ([Sealer]), damit die Löschlogik ohne Gerät testbar ist.
 *
 * Was hier landet, entscheidet [cacheable]: nur Arbeitsinhalte, nie Personenlisten der Kinder, Adressbuch, Eingang, Suche, Rechtetabelle.
 * Der Dateiname ist der SHA-256 des Pfades, der Ordner die Lager-ID (nur eine UUID gilt): Aus Eingaben entsteht nie ein Pfad.
 */
class CampCache(
    private val dir: File,
    private val sealer: Sealer,
    private val now: () -> Long = System::currentTimeMillis,
) : ResponseCache {
    override fun accepts(path: String): Boolean = cacheable(path)

    @Synchronized
    override fun store(path: String, body: String) {
        val file = fileFor(path) ?: return
        val bytes = body.toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_BYTES) return
        try {
            val plain = ByteBuffer.allocate(Long.SIZE_BYTES + bytes.size).putLong(now()).put(bytes).array()
            val sealed = sealer.seal(plain, path)
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, file.name + TEMP)
            temp.writeBytes(sealed)
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (e: IOException) {
            // Kein Platz oder kein Zugriff: Es gibt dann nur keinen Stand ohne Netz.
        } catch (e: GeneralSecurityException) {
            // Der Schlüssel liess sich nicht holen: nichts speichern.
        } catch (e: ProviderException) {
            // Der Keystore des Geräts hakt (kommt vor): nichts speichern.
        }
    }

    @Synchronized
    override fun load(path: String): CachedBody? {
        val file = fileFor(path)?.takeIf { it.isFile } ?: return null
        val plain = try {
            sealer.open(file.readBytes(), path)
        } catch (e: IOException) {
            null
        } catch (e: ProviderException) {
            null
        }
        // Kaputt, vom falschen Ort oder zu alt: Die Datei geht weg, es gibt keinen Stand.
        if (plain == null || plain.size < Long.SIZE_BYTES) {
            file.delete()
            return null
        }
        val savedAt = ByteBuffer.wrap(plain, 0, Long.SIZE_BYTES).long
        if (now() - savedAt > MAX_AGE_MS) {
            file.delete()
            return null
        }
        return CachedBody(String(plain, Long.SIZE_BYTES, plain.size - Long.SIZE_BYTES, Charsets.UTF_8), savedAt)
    }

    @Synchronized
    override fun deleteCamp(campId: String) {
        if (UUID.matches(campId)) File(dir, campId.lowercase()).deleteRecursively()
    }

    @Synchronized
    override fun retainOnly(campIds: Set<String>) {
        val keep = campIds.map { it.lowercase() }.toSet()
        dir.listFiles()?.forEach { if (it.name != ROOT && it.name !in keep) it.deleteRecursively() }
    }

    @Synchronized
    override fun clear() {
        dir.deleteRecursively()
    }

    private fun fileFor(path: String): File? {
        val folder = if (path == CAMP_LIST_PATH) ROOT else campOf(path) ?: return null
        val name = MessageDigest.getInstance("SHA-256").digest(path.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        return File(File(dir, folder), "$name.bin")
    }

    companion object {
        private const val ROOT = "_"
        private const val TEMP = ".tmp"
        private const val MAX_BYTES = 1 shl 20
        private const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
        private val UUID = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
        private val CAMP_PATH = Regex("^/api/v1/lager/($UUID)(?:/([a-z-]+)(?:/([A-Za-z0-9-]+))?)?(?:/[A-Za-z0-9-]+)*(?:\\?.*)?$")

        /**
         * Nur diese Bereiche (und das Lager selbst) werden gespeichert. Nie dabei: Teilnehmende, Zimmer, Fahrten, Gruppen, Anwesenheit,
         * Abzählliste, Diensttausch, Schichtplan (Personen, auch Kinder), Mitglieder, Adressbuch, anonymer Eingang, Suche,
         * Benachrichtigungen, Änderungsfeed, Rechtetabelle und Notfall (der hat seinen eigenen Stand im Arbeitsspeicher).
         */
        private val AREAS = setOf(
            "start", "heute", "wetter", "meine-woche", "zeitplan", "aufgaben", "ressorts", "hoecks", "traktanden", "protokolle",
            "beschluesse", "pinnwand", "erwaehnungen", "tagesseiten", "packliste", "infos", "todos", "einkauf", "vorlagen", "dokumente",
        )

        /** Unterpfade, die Eingaben der Person tragen (Suchtext, Duplikate nach Titel): nie speichern. */
        private val EXCLUDED = setOf("vorschlaege", "duplikate")

        /** Die Lager-ID aus dem Pfad einer Lager-Route (kleingeschrieben), sonst `null`. */
        fun campOf(path: String): String? = CAMP_PATH.matchEntire(path)?.groupValues?.get(1)?.lowercase()

        fun cacheable(path: String): Boolean {
            val match = CAMP_PATH.matchEntire(path) ?: return false
            val area = match.groupValues[2]
            if (area.isEmpty()) return !path.contains('?') // das Lager selbst (Rolle, Rechte)
            return area in AREAS && match.groupValues[3] !in EXCLUDED
        }

        /** Auf dem Gerät: im Ordner ohne Geräte-Backup, Schlüssel im Android Keystore. */
        fun create(context: Context): CampCache = CampCache(
            File(context.applicationContext.noBackupFilesDir, "lagercache"),
            AesGcmSealer { keystoreAesKey("cevios_lagercache") },
        )
    }
}
