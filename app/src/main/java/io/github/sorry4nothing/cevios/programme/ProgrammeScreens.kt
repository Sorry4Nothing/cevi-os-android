package io.github.sorry4nothing.cevios.programme

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.ui.FehlerAnzeige
import io.github.sorry4nothing.cevios.ui.Laden
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val DE_CH = Locale.forLanguageTag("de-CH")
private val UHRZEIT = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun ProgrammeListeScreen(
    zustand: ApiResult<ProgrammListe>?,
    onOeffnen: (String) -> Unit,
    onNochmals: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.programme_titel),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(16.dp).semantics { heading() },
        )
        when (zustand) {
            null -> Laden()
            is ApiResult.Fehler -> FehlerAnzeige(zustand, onNochmals)
            is ApiResult.Ok ->
                if (zustand.wert.programme.isEmpty()) {
                    Text(stringResource(R.string.programme_leer), Modifier.padding(16.dp))
                } else {
                    LazyColumn {
                        items(zustand.wert.programme, key = { it.id }) { programm ->
                            ProgrammZeile(programm, onOeffnen)
                            HorizontalDivider()
                        }
                    }
                }
        }
    }
}

@Composable
private fun ProgrammZeile(programm: Programm, onOeffnen: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.programm_oeffnen)) { onOeffnen(programm.id) }
            .heightIn(min = 48.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(programm.titel, style = MaterialTheme.typography.titleMedium)
        Text(zeitText(programm.zeit()), style = MaterialTheme.typography.bodyMedium)
        programm.ort?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        StatusText(programm)
    }
}

@Composable
fun ProgrammDetailScreen(
    zustand: ApiResult<Programm>?,
    antwort: Antwortstand,
    onKind: (kindId: String, ja: Boolean) -> Unit,
    onLeitung: (ja: Boolean) -> Unit,
    onZurueck: () -> Unit,
    onNochmals: () -> Unit,
) {
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        TextButton(onClick = onZurueck, Modifier.padding(horizontal = 8.dp)) {
            Text(stringResource(R.string.zurueck))
        }
        when (zustand) {
            null -> Laden()
            is ApiResult.Fehler -> FehlerAnzeige(zustand, onNochmals)
            is ApiResult.Ok -> ProgrammInhalt(zustand.wert, antwort, onKind, onLeitung)
        }
    }
}

@Composable
private fun ProgrammInhalt(
    programm: Programm,
    antwort: Antwortstand,
    onKind: (kindId: String, ja: Boolean) -> Unit,
    onLeitung: (ja: Boolean) -> Unit,
) {
    // Nach dem Laden springt der Fokus auf den Titel, damit der Screenreader den Bildschirm ansagt.
    val fokus = remember { FocusRequester() }
    LaunchedEffect(programm.id) { fokus.requestFocus() }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            programm.titel,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.focusRequester(fokus).focusable().semantics { heading() },
        )
        StatusText(programm)
        Abschnitt(R.string.abschnitt_zeit, zeitText(programm.zeit()))
        AnmeldungAbschnitt(programm, antwort, onKind, onLeitung)
        Abschnitt(R.string.abschnitt_treffpunkt, programm.ort)
        Abschnitt(R.string.abschnitt_schluss, schlussText(programm))
        Abschnitt(R.string.abschnitt_mitnehmen, programm.mitnehmen)
        Abschnitt(R.string.abschnitt_info, programm.infoEltern)
        Abschnitt(R.string.abschnitt_stufen, programm.stufen.joinToString(", ") { it.name }.ifEmpty { null })
    }
}

@Composable
private fun Abschnitt(@StringRes titel: Int, text: String?) {
    if (text.isNullOrBlank()) return
    Column {
        Text(
            stringResource(titel),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Abgesagt und «wird geprüft» stehen als Text da, nie nur als Farbe. */
@Composable
private fun StatusText(programm: Programm) {
    val text = when {
        programm.abgesagt -> R.string.status_abgesagt
        programm.pruefen -> R.string.status_pruefen
        else -> return
    }
    Text(stringResource(text), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
}

@Composable
private fun zeitText(zeit: Zeit?): String {
    val voll = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(DE_CH)
    val kurz = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(DE_CH)
    return when (zeit) {
        null -> stringResource(R.string.zeit_offen)
        is Zeit.Ganztaegig ->
            if (zeit.von == zeit.bis) {
                stringResource(R.string.zeit_ganztaegig_ein_tag, voll.format(zeit.von))
            } else {
                stringResource(R.string.zeit_ganztaegig_mehrere_tage, kurz.format(zeit.von), kurz.format(zeit.bis))
            }
        is Zeit.Termin ->
            if (zeit.beginn.toLocalDate() == zeit.ende.toLocalDate()) {
                stringResource(
                    R.string.zeit_gleicher_tag,
                    voll.format(zeit.beginn), UHRZEIT.format(zeit.beginn), UHRZEIT.format(zeit.ende),
                )
            } else {
                stringResource(
                    R.string.zeit_mehrere_tage,
                    kurz.format(zeit.beginn), UHRZEIT.format(zeit.beginn),
                    kurz.format(zeit.ende), UHRZEIT.format(zeit.ende),
                )
            }
    }
}

/** Uhrzeit (gilt am letzten Tag) und Ort des Schlusses, soweit angegeben. */
@Composable
private fun schlussText(programm: Programm): String? {
    val zeit = programm.schlussZeit?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        ?.let { stringResource(R.string.uhrzeit, UHRZEIT.format(it)) }
    return listOfNotNull(zeit, programm.schlussOrt?.takeIf { it.isNotBlank() })
        .joinToString(", ").ifEmpty { null }
}
