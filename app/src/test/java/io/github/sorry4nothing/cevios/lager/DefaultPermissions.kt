package io.github.sorry4nothing.cevios.lager

/**
 * Die Rechte je Rolle im Standard des Rechtekatalogs (Plan des Backends; `GET /api/v1/lager/{id}` nennt sie als `rechte`). Nur für
 * Tests: Die App rät nie aus der Rolle, sie liest die Liste der Antwort.
 */
private val LEADERSHIP_RIGHTS = setOf(
    "ressorts.lesen", "ressorts.bearbeiten", "aufgaben.lesen_alle", "aufgaben.bearbeiten", "aufgaben.eigene", "zeitplan.lesen",
    "zeitplan.bearbeiten", "vorlagen.anwenden", "check.lesen", "hoecks.lesen_alle", "hoecks.team_lesen", "hoecks.bearbeiten",
    "protokolle.lesen", "protokolle.bearbeiten", "pinnwand.lesen", "pinnwand.bearbeiten", "interaktion.schreiben",
    "eingang.senden", "eingang.lesen", "eingang.antworten", "teilnehmer.lesen", "teilnehmer.bearbeiten", "einteilung.lesen",
    "einteilung.bearbeiten", "abzaehlen.schreiben", "diensttausch.anfragen", "diensttausch.entscheiden",
    "anwesenheit.lesen_alle", "anwesenheit.eigene", "anwesenheit.bearbeiten", "tagesseiten.lesen", "tagesseiten.bearbeiten",
    "einkauf.lesen", "einkauf.bearbeiten", "packliste.lesen", "packliste.abhaken", "packliste.bearbeiten", "notfall.lesen",
    "notfall.bearbeiten", "infos.lesen", "infos.bearbeiten", "vertrauliches.lesen", "wetter.lesen", "dokumente.lesen_alle",
    "dokumente.team_lesen", "dokumente.bearbeiten", "adressbuch.lesen", "adressbuch.bearbeiten", "verlauf.lesen",
)

private val LEADER_RIGHTS = setOf(
    "ressorts.lesen", "aufgaben.lesen_alle", "aufgaben.bearbeiten", "aufgaben.eigene", "zeitplan.lesen", "check.lesen",
    "hoecks.lesen_alle", "hoecks.team_lesen", "hoecks.bearbeiten", "protokolle.lesen", "protokolle.bearbeiten", "pinnwand.lesen",
    "pinnwand.bearbeiten", "interaktion.schreiben", "eingang.senden", "teilnehmer.lesen", "einteilung.lesen",
    "einteilung.bearbeiten", "abzaehlen.schreiben", "diensttausch.anfragen", "diensttausch.entscheiden",
    "anwesenheit.lesen_alle", "anwesenheit.eigene", "tagesseiten.lesen", "tagesseiten.bearbeiten", "einkauf.lesen",
    "einkauf.bearbeiten", "packliste.lesen", "packliste.abhaken", "notfall.lesen", "infos.lesen", "wetter.lesen",
    "dokumente.lesen_alle", "dokumente.team_lesen", "dokumente.bearbeiten", "verlauf.lesen",
)

private val TEAM_RIGHTS = setOf(
    "aufgaben.eigene", "zeitplan.lesen", "hoecks.team_lesen", "pinnwand.lesen", "interaktion.schreiben", "eingang.senden",
    "teilnehmer.lesen", "einteilung.lesen", "abzaehlen.schreiben", "diensttausch.anfragen", "anwesenheit.eigene",
    "packliste.lesen", "packliste.abhaken", "notfall.lesen", "infos.lesen", "wetter.lesen", "dokumente.team_lesen",
)

private val READ_RIGHTS = setOf(
    "ressorts.lesen", "aufgaben.lesen_alle", "zeitplan.lesen", "check.lesen", "hoecks.lesen_alle", "hoecks.team_lesen",
    "protokolle.lesen", "pinnwand.lesen", "teilnehmer.lesen", "einteilung.lesen", "anwesenheit.lesen_alle", "tagesseiten.lesen",
    "einkauf.lesen", "packliste.lesen", "notfall.lesen", "infos.lesen", "wetter.lesen", "dokumente.lesen_alle",
    "dokumente.team_lesen", "verlauf.lesen",
)

fun defaultPermissions(role: CampRole, readOnly: Boolean = false, extra: Set<String> = emptySet(), without: Set<String> = emptySet()) =
    CampPermissions(
        role, readOnly,
        granted = (when (role) {
            CampRole.LEADERSHIP -> LEADERSHIP_RIGHTS
            CampRole.LEADER -> LEADER_RIGHTS
            CampRole.TEAM -> TEAM_RIGHTS
            CampRole.READ_ONLY -> READ_RIGHTS
        } + extra) - without,
    )
