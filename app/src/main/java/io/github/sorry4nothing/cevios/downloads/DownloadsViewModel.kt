package io.github.sorry4nothing.cevios.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/** Was die Oberfläche nach einem Download oder Entfernen sagt. Der Hinweis bleibt, bis ihn die Person schliesst. */
sealed interface DownloadNotice {
    data class Done(val file: DownloadedFile) : DownloadNotice
    data class Removed(val name: String) : DownloadNotice
    data class Failed(val error: DownloadError) : DownloadNotice
}

/**
 * Die heruntergeladenen Dateien des offenen Lagers und die Downloads, die gerade laufen. Alles geschieht auf Tipp: Nichts lädt von
 * allein. Die Dateien selbst liegen in [Downloads]; hier steht nur die Liste.
 */
class DownloadsViewModel(
    private val downloads: Downloads,
    private val downloader: LinkDownloader,
) : ViewModel() {
    private val _files = MutableStateFlow<List<DownloadedFile>>(emptyList())
    val files: StateFlow<List<DownloadedFile>> = _files.asStateFlow()

    private val _working = MutableStateFlow<Set<String>>(emptySet())

    /** Die Schlüssel (`key`), die gerade laden; ein zweiter Tipp darauf zählt nicht. */
    val working: StateFlow<Set<String>> = _working.asStateFlow()

    private val _notice = MutableStateFlow<DownloadNotice?>(null)
    val notice: StateFlow<DownloadNotice?> = _notice.asStateFlow()

    private var campId: String? = null

    /** Das Lager wechselt oder schliesst: Liste und Hinweis gehören zum offenen Lager. */
    fun open(campId: String?) {
        if (this.campId != campId) {
            this.campId = campId
            _notice.value = null
        }
        refresh()
    }

    fun refresh() {
        val id = campId
        viewModelScope.launch(Dispatchers.IO) { _files.value = if (id == null) emptyList() else downloads.list(id) }
    }

    /** Lädt die Datei hinter einem Link. `key` benennt den Download (zum Beispiel die ID des Dokuments) für «wird geladen». */
    fun downloadLink(key: String, name: String, link: String) {
        val id = campId ?: return
        if (!begin(key)) return
        viewModelScope.launch {
            val result = downloader.download(id, link, name)
            _working.update { it - key }
            finish(id, result)
        }
    }

    /** Legt Bytes ab, die die App schon hat (Kalenderdatei, Aushang). */
    fun saveBytes(name: String, bytes: ByteArray) {
        val id = campId ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    DownloadResult.Done(downloads.saveBytes(id, name, bytes))
                } catch (e: TooBigException) {
                    DownloadResult.Failed(DownloadError.TOO_BIG)
                } catch (e: IOException) {
                    DownloadResult.Failed(DownloadError.STORAGE)
                }
            }
            finish(id, result)
        }
    }

    fun remove(file: DownloadedFile) {
        viewModelScope.launch(Dispatchers.IO) {
            downloads.remove(file)
            _files.value = campId?.let(downloads::list).orEmpty()
            _notice.value = DownloadNotice.Removed(file.name)
        }
    }

    /** Öffnet die Datei in einer App des Geräts; gibt es keine, sagt es der Hinweis. */
    fun open(context: android.content.Context, file: DownloadedFile) {
        if (!Downloads.open(context, file)) _notice.value = DownloadNotice.Failed(DownloadError.NO_APP)
    }

    /** Das Lager gibt es nicht mehr offen: Seine Dateien gehen vom Gerät. */
    fun deleteCamp(campId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            downloads.deleteCamp(campId)
            _files.value = this@DownloadsViewModel.campId?.let(downloads::list).orEmpty()
        }
    }

    /** Behält nur die Dateien dieser Lager (die Lagerliste kam frisch vom Server). */
    fun retainOnly(campIds: Set<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            downloads.retainOnly(campIds)
            _files.value = this@DownloadsViewModel.campId?.let(downloads::list).orEmpty()
        }
    }

    fun dismissNotice() {
        _notice.value = null
    }

    private fun begin(key: String): Boolean {
        var started = false
        _working.update { if (key in it) it else { started = true; it + key } }
        if (started) _notice.value = null
        return started
    }

    private fun finish(campId: String, result: DownloadResult) {
        // Das Lager ist unterdessen gewechselt: Die Datei liegt im richtigen Ordner, der Hinweis gehört nicht mehr hierher.
        if (this.campId == campId) {
            _notice.value = when (result) {
                is DownloadResult.Done -> DownloadNotice.Done(result.file)
                is DownloadResult.Failed -> DownloadNotice.Failed(result.error)
            }
        }
        if (this.campId == campId) refresh()
    }
}
