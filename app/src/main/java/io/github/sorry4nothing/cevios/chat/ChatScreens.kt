package io.github.sorry4nothing.cevios.chat

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
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
import java.time.format.FormatStyle
import java.time.format.DateTimeParseException
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
fun ChatListeScreen(zustand: ApiResult<ChatListe>?, onOeffnen: (String) -> Unit, onNochmals: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.chat_titel),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(16.dp).semantics { heading() },
        )
        when (zustand) {
            null -> Laden()
            is ApiResult.Fehler -> FehlerAnzeige(zustand, onNochmals)
            is ApiResult.Ok ->
                if (zustand.wert.chats.isEmpty()) {
                    Text(stringResource(R.string.chat_leer), Modifier.padding(16.dp))
                } else {
                    LazyColumn {
                        items(zustand.wert.chats, key = { it.id }) { chat ->
                            ChatZeile(chat, onOeffnen)
                            HorizontalDivider()
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
    zustand: ApiResult<Verlauf>?,
    aeltereLaedt: Boolean,
    senden: Sendestand,
    onSenden: (text: String, onGesendet: () -> Unit) -> Unit,
    onAeltere: () -> Unit,
    onAktualisieren: () -> Unit,
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
        Column(Modifier.weight(1f)) {
            when (zustand) {
                null -> Laden()
                is ApiResult.Fehler -> FehlerAnzeige(zustand, onNochmals)
                is ApiResult.Ok -> Nachrichten(zustand.wert, aeltereLaedt, onAeltere)
            }
        }
        Eingabe(senden, onSenden)
    }
}

@Composable
private fun Nachrichten(verlauf: Verlauf, aeltereLaedt: Boolean, onAeltere: () -> Unit) {
    if (verlauf.nachrichten.isEmpty()) {
        Text(stringResource(R.string.nachrichten_leer), Modifier.padding(16.dp))
        return
    }
    // Neueste zuerst und von unten nach oben: so wächst der Verlauf wie in jedem Chat nach oben.
    LazyColumn(reverseLayout = true, contentPadding = PaddingValues(16.dp)) {
        items(verlauf.nachrichten, key = { it.id }) { NachrichtZeile(it) }
        if (verlauf.naechster != null) {
            item {
                OutlinedButton(onClick = onAeltere, enabled = !aeltereLaedt, modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(stringResource(if (aeltereLaedt) R.string.laden else R.string.nachrichten_aeltere))
                }
            }
        }
    }
}

@Composable
private fun NachrichtZeile(nachricht: ChatNachricht) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val autor = if (nachricht.eigene) stringResource(R.string.nachricht_du) else nachricht.autor.name
        Text("$autor, ${zeitText(nachricht.erstelltAm)}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        // Ausgeblendet steht als Hinweis da, nie als leere Zeile.
        Text(
            if (nachricht.ausgeblendet) stringResource(R.string.nachricht_ausgeblendet) else nachricht.text.orEmpty(),
            style = MaterialTheme.typography.bodyLarge,
        )
        nachricht.programm?.let { Text(stringResource(R.string.nachricht_programm, it.titel), style = MaterialTheme.typography.bodyMedium) }
    }
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

private fun zeitText(iso: String): String =
    try {
        ZEIT.format(Instant.parse(iso).atZone(SCHWEIZ))
    } catch (e: DateTimeParseException) {
        ""
    }
