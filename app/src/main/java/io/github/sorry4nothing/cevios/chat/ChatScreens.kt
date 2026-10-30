package io.github.sorry4nothing.cevios.chat

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.programme.SCHWEIZ
import io.github.sorry4nothing.cevios.ui.FehlerAnzeige
import io.github.sorry4nothing.cevios.ui.Laden
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import java.util.Locale

private const val MAX_ZEICHEN = 2000
private val ZEIT = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(Locale.forLanguageTag("de-CH"))

@Composable
fun chatTitel(chat: Chat?): String = when {
    chat == null -> stringResource(R.string.chat_titel)
    chat.istGruppe -> stringResource(R.string.chat_gruppe, chat.stufe.name)
    else -> stringResource(R.string.chat_einzel, chat.eltern?.name.orEmpty()).trim()
}

@Composable
fun ChatListeScreen(
    zustand: ApiResult<ChatListe>?,
    profil: Profil?,
    aktion: Aktionsstand,
    onOeffnen: (String) -> Unit,
    onEinzelchat: (stufeId: String) -> Unit,
    onHilfe: () -> Unit,
    onNochmals: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.chat_titel),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(16.dp).semantics { heading() },
        )
        TextButton(onClick = onHilfe, Modifier.padding(horizontal = 8.dp)) { Text(stringResource(R.string.hilfe_knopf)) }
        AktionsMeldung(aktion)
        when (zustand) {
            null -> Laden()
            is ApiResult.Fehler -> FehlerAnzeige(zustand, onNochmals)
            is ApiResult.Ok -> {
                val starten = ChatRegeln.stufenFuerEinzelchat(profil, zustand.wert.chats)
                if (zustand.wert.chats.isEmpty() && starten.isEmpty()) {
                    Text(stringResource(R.string.chat_leer), Modifier.padding(16.dp))
                } else {
                    LazyColumn {
                        items(zustand.wert.chats, key = { it.id }) { chat ->
                            ChatZeile(chat, onOeffnen)
                            HorizontalDivider()
                        }
                        items(starten, key = { "starten-" + it.id }) { stufe ->
                            OutlinedButton(
                                onClick = { onEinzelchat(stufe.id) },
                                enabled = !aktion.laeuft,
                                modifier = Modifier.padding(16.dp),
                            ) { Text(stringResource(R.string.einzelchat_starten, stufe.name)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatZeile(chat: Chat, onOeffnen: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.chat_oeffnen)) { onOeffnen(chat.id) }
            .heightIn(min = 48.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(chatTitel(chat), style = MaterialTheme.typography.titleMedium)
        if (!chat.istGruppe) Text(stringResource(R.string.chat_stufe, chat.stufe.name))
    }
}

@Composable
fun ChatVerlaufScreen(
    titel: String,
    chat: Chat?,
    profil: Profil?,
    zustand: ApiResult<Verlauf>?,
    aeltereLaedt: Boolean,
    senden: Sendestand,
    aktion: Aktionsstand,
    onSenden: (text: String, onGesendet: () -> Unit) -> Unit,
    onAeltere: () -> Unit,
    onAktualisieren: () -> Unit,
    onAusblenden: (nachrichtId: String) -> Unit,
    onMelden: (nachrichtId: String) -> Unit,
    onEinzelchat: (stufeId: String, elternUserId: String) -> Unit,
    onZurueck: () -> Unit,
    onNochmals: () -> Unit,
) {
    // Safe-Drawing-Padding enthält die Tastatur: Die Eingabe bleibt sichtbar.
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onZurueck) { Text(stringResource(R.string.zurueck)) }
            TextButton(onClick = onAktualisieren) { Text(stringResource(R.string.aktualisieren)) }
        }
        Text(
            titel,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics { heading() },
        )
        // Im Einzelchat lesen alle Stufenleitungen der Stufe mit; das steht im Kopf, nicht erst im Kleingedruckten.
        if (chat != null && !chat.istGruppe && chat.stufenleitung.isNotEmpty()) {
            Text(
                stringResource(R.string.chat_mitlesen, chat.stufenleitung.joinToString(", ") { it.name }),
                Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Column(Modifier.weight(1f)) {
            when (zustand) {
                null -> Laden()
                is ApiResult.Fehler -> FehlerAnzeige(zustand, onNochmals)
                is ApiResult.Ok ->
                    Nachrichten(zustand.wert, chat, profil, aeltereLaedt, onAeltere, onAusblenden, onMelden, onEinzelchat)
            }
        }
        AktionsMeldung(aktion)
        Eingabe(senden, onSenden)
    }
}

@Composable
private fun Nachrichten(
    verlauf: Verlauf,
    chat: Chat?,
    profil: Profil?,
    aeltereLaedt: Boolean,
    onAeltere: () -> Unit,
    onAusblenden: (String) -> Unit,
    onMelden: (String) -> Unit,
    onEinzelchat: (stufeId: String, elternUserId: String) -> Unit,
) {
    if (verlauf.nachrichten.isEmpty()) {
        Text(stringResource(R.string.nachrichten_leer), Modifier.padding(16.dp))
        return
    }
    var gewaehlt by remember { mutableStateOf<ChatNachricht?>(null) }
    var frage by remember { mutableStateOf<Frage?>(null) }

    // Neueste zuerst und von unten nach oben: so wächst der Verlauf wie in jedem Chat nach oben.
    LazyColumn(reverseLayout = true, contentPadding = PaddingValues(16.dp)) {
        items(verlauf.nachrichten, key = { it.id }) { nachricht ->
            NachrichtZeile(nachricht, onAktionen = { gewaehlt = nachricht })
        }
        if (verlauf.naechster != null) {
            item {
                OutlinedButton(onClick = onAeltere, enabled = !aeltereLaedt, modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(stringResource(if (aeltereLaedt) R.string.laden else R.string.nachrichten_aeltere))
                }
            }
        }
    }

    gewaehlt?.let { nachricht ->
        AlertDialog(
            onDismissRequest = { gewaehlt = null },
            title = { Text(stringResource(R.string.aktionen_titel, nachricht.autor.name)) },
            text = {
                Column {
                    if (ChatRegeln.darfEinzelchatOeffnen(chat, nachricht, profil)) {
                        TextButton(onClick = {
                            gewaehlt = null
                            chat?.let { onEinzelchat(it.stufe.id, nachricht.autor.id) }
                        }) { Text(stringResource(R.string.aktion_einzelchat, nachricht.autor.name)) }
                    }
                    if (ChatRegeln.darfAusblenden(chat, nachricht, profil)) {
                        TextButton(onClick = {
                            gewaehlt = null
                            frage = Frage(Art.AUSBLENDEN, nachricht.id)
                        }) { Text(stringResource(R.string.aktion_ausblenden)) }
                    }
                    if (ChatRegeln.darfMelden(nachricht)) {
                        TextButton(onClick = {
                            gewaehlt = null
                            frage = Frage(Art.MELDEN, nachricht.id)
                        }) { Text(stringResource(R.string.aktion_melden)) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { gewaehlt = null }) { Text(stringResource(R.string.abbrechen)) } },
        )
    }

    frage?.let { f ->
        AlertDialog(
            onDismissRequest = { frage = null },
            title = { Text(stringResource(f.art.titel)) },
            text = { Text(stringResource(f.art.frage)) },
            confirmButton = {
                Button(onClick = {
                    frage = null
                    if (f.art == Art.AUSBLENDEN) onAusblenden(f.nachrichtId) else onMelden(f.nachrichtId)
                }) { Text(stringResource(f.art.bestaetigen)) }
            },
            dismissButton = { TextButton(onClick = { frage = null }) { Text(stringResource(R.string.abbrechen)) } },
        )
    }
}

private enum class Art(@StringRes val titel: Int, @StringRes val frage: Int, @StringRes val bestaetigen: Int) {
    AUSBLENDEN(R.string.aktion_ausblenden, R.string.ausblenden_frage, R.string.aktion_bestaetigen_ausblenden),
    MELDEN(R.string.aktion_melden, R.string.melden_frage, R.string.aktion_bestaetigen_melden),
}

private data class Frage(val art: Art, val nachrichtId: String)

@Composable
private fun NachrichtZeile(nachricht: ChatNachricht, onAktionen: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val autor = if (nachricht.eigene) stringResource(R.string.nachricht_du) else nachricht.autor.name
        Text("$autor, ${zeitText(nachricht.erstelltAm)}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        // Ausgeblendet steht als Hinweis da, nie als leere Zeile.
        Text(
            if (nachricht.ausgeblendet) stringResource(R.string.nachricht_ausgeblendet) else nachricht.text.orEmpty(),
            style = MaterialTheme.typography.bodyLarge,
        )
        nachricht.programm?.let { Text(stringResource(R.string.nachricht_programm, it.titel), style = MaterialTheme.typography.bodyMedium) }
        val beschreibung = stringResource(R.string.aktionen_beschreibung, autor)
        TextButton(onClick = onAktionen, Modifier.semantics { contentDescription = beschreibung }) {
            Text(stringResource(R.string.aktionen_knopf))
        }
    }
}

/** Läuft, Fehler oder Bestätigung einer Aktion, angesagt als Live-Region. */
@Composable
private fun AktionsMeldung(aktion: Aktionsstand) {
    val text = when {
        aktion.laeuft -> stringResource(R.string.aktion_laeuft)
        aktion.fehler != null -> stringResource(aktion.fehler.text)
        aktion.erledigt != null -> stringResource(aktion.erledigt)
        else -> return
    }
    Text(
        text,
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics {
            liveRegion = if (aktion.fehler != null) LiveRegionMode.Assertive else LiveRegionMode.Polite
        },
    )
}

@Composable
private fun Eingabe(senden: Sendestand, onSenden: (String, () -> Unit) -> Unit) {
    var entwurf by rememberSaveable { mutableStateOf("") }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (senden.laeuft) {
            Text(stringResource(R.string.nachricht_sendet), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        senden.fehler?.let {
            Text(stringResource(it.text), Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = entwurf,
                onValueChange = { if (it.length <= MAX_ZEICHEN) entwurf = it },
                label = { Text(stringResource(R.string.nachricht_eingabe)) },
                maxLines = 4,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = { onSenden(entwurf) { entwurf = "" } },
                enabled = entwurf.isNotBlank() && !senden.laeuft,
            ) { Text(stringResource(R.string.nachricht_senden)) }
        }
    }
}

@Composable
fun HilfeScreen(zustand: ApiResult<HilfeAntwort>?, onZurueck: () -> Unit, onNochmals: () -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        TextButton(onClick = onZurueck, Modifier.padding(horizontal = 8.dp)) { Text(stringResource(R.string.zurueck)) }
        Text(
            stringResource(R.string.hilfe_titel),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics { heading() },
        )
        when (zustand) {
            null -> Laden()
            is ApiResult.Fehler -> FehlerAnzeige(zustand, onNochmals)
            is ApiResult.Ok -> Text(
                zustand.wert.vertrauenspersonen?.takeIf { it.isNotBlank() } ?: stringResource(R.string.hilfe_leer),
                Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

private fun zeitText(iso: String): String =
    try {
        ZEIT.format(Instant.parse(iso).atZone(SCHWEIZ))
    } catch (e: DateTimeParseException) {
        ""
    }
