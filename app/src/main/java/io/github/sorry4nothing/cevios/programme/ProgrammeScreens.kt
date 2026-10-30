package io.github.sorry4nothing.cevios.programme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.ui.ChipArt
import io.github.sorry4nothing.cevios.ui.FehlerZustand
import io.github.sorry4nothing.cevios.ui.Kopf
import io.github.sorry4nothing.cevios.ui.KopfKnopf
import io.github.sorry4nothing.cevios.ui.LadeSkelett
import io.github.sorry4nothing.cevios.ui.LeerZustand
import io.github.sorry4nothing.cevios.ui.StatusChip
import io.github.sorry4nothing.cevios.ui.fokusRing
import io.github.sorry4nothing.cevios.ui.theme.Abstand
import io.github.sorry4nothing.cevios.ui.theme.cevi
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val UHRZEIT = DateTimeFormatter.ofPattern("HH:mm")
private val KARTE = RoundedCornerShape(16.dp)

@Composable
fun ProgrammeListeScreen(
    zustand: ApiResult<ProgrammListe>?,
    onOeffnen: (String) -> Unit,
    onNochmals: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Kopf(
            titel = stringResource(R.string.programme_titel),
            aktion = KopfKnopf(R.drawable.ic_aktualisieren, stringResource(R.string.aktualisieren), onNochmals),
        )
        when (zustand) {
            null -> LadeSkelett(stringResource(R.string.programme_laden))
            is ApiResult.Fehler -> FehlerZustand(zustand.fehler, onNochmals)
            is ApiResult.Ok ->
                if (zustand.wert.programme.isEmpty()) {
                    LeerZustand(
                        R.drawable.ic_kalender,
                        stringResource(R.string.programme_leer_titel),
                        stringResource(R.string.programme_leer_text),
                        onNochmals,
                    )
                } else {
                    Zeitleiste(gruppiereNachTag(zustand.wert.programme), onOeffnen)
                }
        }
    }
}

/** Die Liste als Zeitleiste: links das Datum an einer Linie, rechts die Programme dieses Tages als Karten. */
@Composable
private fun Zeitleiste(tage: List<Tag>, onOeffnen: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(start = Abstand.s3, end = Abstand.s5, bottom = Abstand.s4)) {
        itemsIndexed(tage, key = { _, tag -> tag.programme.first().id }) { index, tag ->
            val letzter = index == tage.lastIndex
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Abstand.s2)) {
                DatumsSpalte(tag, mitLinie = !letzter)
                Column(
                    Modifier.weight(1f).padding(bottom = if (letzter) 0.dp else 18.dp),
                    verticalArrangement = Arrangement.spacedBy(Abstand.s2),
                ) {
                    tag.programme.forEach { ProgrammKarte(it, onOeffnen) }
                }
            }
        }
    }
}

@Composable
private fun DatumsSpalte(tag: Tag, mitLinie: Boolean) {
    Column(Modifier.width(52.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
        val datum = tag.datum
        if (datum != null) {
            Text(wochentagKurz(datum), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.cevi.accent)
            Text(
                datum.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 26.sp, lineHeight = 30.sp, fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (mitLinie) {
            Box(
                Modifier.padding(vertical = 6.dp).width(2.dp).weight(1f)
                    .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(1.dp)),
            )
        }
    }
}

@Composable
private fun ProgrammKarte(programm: Programm, onOeffnen: (String) -> Unit) {
    val stufen = programm.stufen.joinToString(", ") { it.name }
    val kopfzeile = listOf(listenZeit(programm), stufen).filter { it.isNotBlank() }.joinToString(" · ")
    Column(
        Modifier
            .fillMaxWidth()
            .fokusRing(KARTE)
            .clip(KARTE)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClickLabel = stringResource(R.string.programm_oeffnen)) { onOeffnen(programm.id) }
            .padding(horizontal = Abstand.s4, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            kopfzeile,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
            color = MaterialTheme.cevi.accent,
        )
        Text(programm.titel, style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 24.sp))
        programm.ort?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        ProgrammStatus(programm, Modifier.padding(top = 6.dp))
    }
}

/** Abgesagt oder «wird noch geprüft»: als Etikett mit Icon und Wort. Ein aktives Programm braucht keins. */
@Composable
private fun ProgrammStatus(programm: Programm, modifier: Modifier = Modifier) {
    when {
        programm.abgesagt -> StatusChip(ChipArt.NEIN, stringResource(R.string.status_abgesagt), modifier)
        programm.pruefen -> StatusChip(ChipArt.PRUEFEN, stringResource(R.string.status_pruefen), modifier)
    }
}

/** «14:00–17:00», bei mehreren Tagen der Beginn, bei ganztägigen «Ganztägig». */
@Composable
private fun listenZeit(programm: Programm): String = when (val zeit = programm.zeit()) {
    null -> stringResource(R.string.zeit_offen)
    is Zeit.Ganztaegig ->
        if (zeit.von == zeit.bis) {
            stringResource(R.string.zeit_liste_ganztaegig)
        } else {
            stringResource(R.string.zeit_liste_bis, DateTimeFormatter.ofPattern("d.M.").format(zeit.bis))
        }
    is Zeit.Termin ->
        if (zeit.beginn.toLocalDate() == zeit.ende.toLocalDate()) {
            "${UHRZEIT.format(zeit.beginn)}–${UHRZEIT.format(zeit.ende)}"
        } else {
            UHRZEIT.format(zeit.beginn)
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
    val zurueck = KopfKnopf(R.drawable.ic_zurueck, stringResource(R.string.zurueck_zu_programme), onZurueck)
    Column(Modifier.fillMaxSize()) {
        when (zustand) {
            null -> {
                Kopf(titel = null, zurueck = zurueck)
                LadeSkelett(stringResource(R.string.programm_laden))
            }
            is ApiResult.Fehler -> {
                Kopf(titel = null, zurueck = zurueck)
                FehlerZustand(zustand.fehler, onNochmals, onZuProgrammen = onZurueck)
            }
            is ApiResult.Ok -> ProgrammInhalt(zustand.wert, antwort, zurueck, onKind, onLeitung)
        }
    }
}

@Composable
private fun ProgrammInhalt(
    programm: Programm,
    antwort: Antwortstand,
    zurueck: KopfKnopf,
    onKind: (kindId: String, ja: Boolean) -> Unit,
    onLeitung: (ja: Boolean) -> Unit,
) {
    val stufen = programm.stufen.joinToString(", ") { it.name }
    val datum = programm.beginnTag()?.let { "${wochentagKurz(it)} ${tagUndMonat(it)}" }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Kopf(
            titel = programm.titel,
            ueberzeile = listOfNotNull(stufen.ifBlank { null }, datum).joinToString(" · "),
            zurueck = zurueck,
            titelStil = MaterialTheme.typography.headlineMedium,
            fokusAufTitel = true,
        )
        Column(Modifier.padding(horizontal = Abstand.s5), verticalArrangement = Arrangement.spacedBy(Abstand.s3)) {
            ProgrammStatus(programm)
            Kacheln(programm)
            Fakten(programm)
            AnmeldungAbschnitt(programm, antwort, onKind, onLeitung)
            Box(Modifier.height(Abstand.s4))
        }
    }
}

/** Zwei Kacheln, die Eltern am häufigsten suchen: wann und wo man sich trifft, wann und wo es endet. */
@Composable
private fun Kacheln(programm: Programm) {
    val schluss = programm.schlussZeit?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
    val treffzeit = when (val zeit = programm.zeit()) {
        is Zeit.Termin -> UHRZEIT.format(zeit.beginn)
        is Zeit.Ganztaegig -> stringResource(R.string.zeit_liste_ganztaegig)
        null -> null
    }
    val treffpunkt = programm.treffpunkt?.takeIf { it.isNotBlank() }
    val schlussOrt = programm.schlussOrt?.takeIf { it.isNotBlank() }
    Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(Abstand.s2)) {
        if (treffzeit != null || treffpunkt != null) {
            Kachel(stringResource(R.string.abschnitt_treffpunkt), treffzeit, treffpunkt, Modifier.weight(1f).fillMaxHeight())
        }
        if (schluss != null || schlussOrt != null) {
            Kachel(
                stringResource(R.string.abschnitt_schluss), schluss?.let { UHRZEIT.format(it) }, schlussOrt,
                Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun Kachel(label: String, zeit: String?, ort: String?, modifier: Modifier) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLow, KARTE)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, KARTE)
            .padding(horizontal = 14.dp, vertical = Abstand.s3),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        zeit?.let { Text(it, style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 24.sp, fontFeatureSettings = "tnum")) }
        ort?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** Liste mit Ort, Mitnehmen und Info, je mit Icon. Felder ohne Inhalt fallen weg. */
@Composable
private fun Fakten(programm: Programm) {
    val zeilen = listOf(
        Triple(R.drawable.ic_ort, R.string.fakt_ort, programm.googleOrt?.takeIf { it.isNotBlank() }),
        Triple(R.drawable.ic_mitnehmen, R.string.abschnitt_mitnehmen, programm.mitnehmen?.takeIf { it.isNotBlank() }),
        Triple(R.drawable.ic_info, R.string.abschnitt_info, programm.infoEltern?.takeIf { it.isNotBlank() }),
    ).filter { it.third != null }
    if (zeilen.isEmpty()) return
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, KARTE)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, KARTE)
            .padding(horizontal = 14.dp, vertical = 4.dp),
    ) {
        zeilen.forEachIndexed { index, (icon, label, wert) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(Abstand.s3)) {
                Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.cevi.accent, modifier = Modifier.size(24.dp))
                Column {
                    Text(stringResource(label), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(wert.orEmpty(), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp))
                }
            }
            if (index < zeilen.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        }
    }
}
