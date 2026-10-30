package io.github.sorry4nothing.cevios.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.net.ApiFehler
import io.github.sorry4nothing.cevios.ui.theme.Abstand
import io.github.sorry4nothing.cevios.ui.theme.Voll
import io.github.sorry4nothing.cevios.ui.theme.cevi
import kotlinx.coroutines.delay

/** Welche ganzseitige Fehlerseite ein [ApiFehler] bekommt (Design: Zustand-Screens). */
enum class FehlerSeite {
    OFFLINE, NICHT_GEFUNDEN, KEIN_ZUGRIFF, SITZUNG, VERALTET, FEHLER;

    companion object {
        fun von(fehler: ApiFehler) = when (fehler) {
            ApiFehler.NETZWERK -> OFFLINE
            ApiFehler.NICHT_GEFUNDEN -> NICHT_GEFUNDEN
            ApiFehler.KEINE_BERECHTIGUNG -> KEIN_ZUGRIFF
            ApiFehler.NICHT_ANGEMELDET -> SITZUNG
            ApiFehler.APP_VERALTET -> VERALTET
            ApiFehler.ZU_VIELE_ANFRAGEN, ApiFehler.ANMELDUNG_GESCHLOSSEN, ApiFehler.UNBEKANNT -> FEHLER
        }
    }
}

/**
 * Ganzseitiger Zustand nach dem Muster des Designs: rundes Icon, Titel, ein Satz und höchstens eine Hauptaktion.
 * Fehler werden angesagt (`liveRegion`).
 */
@Composable
fun ZustandsSeite(
    @DrawableRes icon: Int,
    titel: String,
    text: String,
    modifier: Modifier = Modifier,
    kreis: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    iconFarbe: Color = MaterialTheme.cevi.accent,
    fehler: Boolean = false,
    aktionen: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Abstand.s3, Alignment.CenterVertically),
    ) {
        Box(Modifier.size(88.dp).background(kreis, Voll), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, tint = iconFarbe, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(Abstand.s2))
        Column(
            Modifier.semantics { liveRegion = if (fehler) LiveRegionMode.Assertive else LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Abstand.s3),
        ) {
            Text(
                titel,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp, lineHeight = 30.sp),
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 300.dp),
            )
        }
        aktionen()
    }
}

/**
 * Die Fehlerseite zu einem [ApiFehler]. Eine Aktion, die es nicht gibt, fehlt auch als Knopf: «Anmelden» erscheint erst,
 * wenn es den Login gibt, «Zu den Programmen» nur, wo `onZuProgrammen` gesetzt ist.
 */
@Composable
fun FehlerZustand(
    fehler: ApiFehler,
    onNochmals: () -> Unit,
    modifier: Modifier = Modifier,
    onZuProgrammen: (() -> Unit)? = null,
    @StringRes nichtGefundenText: Int = R.string.zustand_404_text,
) {
    val schema = MaterialTheme.colorScheme
    when (FehlerSeite.von(fehler)) {
        FehlerSeite.OFFLINE -> ZustandsSeite(
            R.drawable.ic_offline, stringResource(R.string.zustand_offline_titel), stringResource(R.string.zustand_offline_text),
            modifier, fehler = true,
        ) { HauptKnopf(stringResource(R.string.nochmals), onNochmals, icon = R.drawable.ic_aktualisieren) }

        FehlerSeite.NICHT_GEFUNDEN -> ZustandsSeite(
            R.drawable.ic_nicht_gefunden, stringResource(R.string.zustand_404_titel), stringResource(nichtGefundenText), modifier,
        ) { onZuProgrammen?.let { HauptKnopf(stringResource(R.string.zu_den_programmen), it) } }

        FehlerSeite.KEIN_ZUGRIFF -> ZustandsSeite(
            R.drawable.ic_schloss, stringResource(R.string.zustand_zugriff_titel), stringResource(R.string.zustand_zugriff_text), modifier,
        ) { onZuProgrammen?.let { HauptKnopf(stringResource(R.string.zu_den_programmen), it) } }

        FehlerSeite.SITZUNG -> ZustandsSeite(
            R.drawable.ic_uhr, stringResource(R.string.zustand_sitzung_titel), stringResource(R.string.zustand_sitzung_text), modifier,
        )

        FehlerSeite.VERALTET -> ZustandsSeite(
            R.drawable.ic_warndreieck, stringResource(R.string.zustand_fehler_titel), stringResource(fehler.text), modifier,
            kreis = schema.errorContainer, iconFarbe = schema.onErrorContainer, fehler = true,
        )

        FehlerSeite.FEHLER -> ZustandsSeite(
            R.drawable.ic_warndreieck, stringResource(R.string.zustand_fehler_titel), stringResource(R.string.zustand_fehler_text), modifier,
            kreis = schema.errorContainer, iconFarbe = schema.onErrorContainer, fehler = true,
        ) {
            HauptKnopf(stringResource(R.string.nochmals), onNochmals, icon = R.drawable.ic_aktualisieren)
            onZuProgrammen?.let { TextKnopf(stringResource(R.string.zu_den_programmen), it) }
        }
    }
}

@Composable
fun LeerZustand(@DrawableRes icon: Int, titel: String, text: String, onAktualisieren: () -> Unit, modifier: Modifier = Modifier) {
    ZustandsSeite(icon, titel, text, modifier) {
        HauptKnopf(stringResource(R.string.aktualisieren), onAktualisieren, icon = R.drawable.ic_aktualisieren)
    }
}

/** Ein Balken des Skeletts: grau, ohne Animation (das Design legt keinen Schimmer fest). */
@Composable
private fun Balken(breite: Int, hoehe: Int, modifier: Modifier = Modifier) {
    Box(modifier.width(breite.dp).height(hoehe.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp)))
}

/**
 * Skelett-Karten statt Inhalt (Laden). Der Screenreader hört «wird geladen» erst nach einer Sekunde: kurze Ladezeiten
 * sollen nicht dazwischenreden.
 */
@Composable
fun LadeSkelett(beschreibung: String, modifier: Modifier = Modifier) {
    var angesagt by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(1000)
        angesagt = true
    }
    Column(
        modifier.fillMaxWidth().padding(start = Abstand.s3, end = Abstand.s5).then(
            if (angesagt) {
                Modifier.semantics {
                    contentDescription = beschreibung
                    liveRegion = LiveRegionMode.Polite
                }
            } else {
                Modifier.semantics { contentDescription = "" }
            },
        ),
    ) {
        val tage = listOf(listOf(0, 1), listOf(2), listOf(3))
        tage.forEachIndexed { index, karten ->
            val letzter = index == tage.lastIndex
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Abstand.s2)) {
                Column(Modifier.width(52.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Balken(22, 10)
                    Spacer(Modifier.height(6.dp))
                    Balken(30, 26)
                    if (!letzter) {
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.width(2.dp).weight(1f).background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(1.dp)))
                    }
                }
                Column(
                    Modifier.weight(1f).padding(bottom = if (letzter) 0.dp else 18.dp),
                    verticalArrangement = Arrangement.spacedBy(Abstand.s2),
                ) {
                    karten.forEach { SkelettKarte(it) }
                }
            }
        }
    }
}

@Composable
private fun SkelettKarte(nr: Int) {
    val breiten = listOf(listOf(110, 200, 90), listOf(110, 150, 120), listOf(110, 180, 100), listOf(110, 170, 80))[nr % 4]
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.medium).padding(Abstand.s4),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Balken(breiten[0], 12)
        Balken(breiten[1], 18)
        Balken(breiten[2], 12)
    }
}
