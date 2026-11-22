package io.github.sorry4nothing.cevios.lager

import android.content.Context
import io.github.sorry4nothing.cevios.R

/** Die Sätze der Warteschlange und die Feldnamen des Konfliktdialogs aus `strings.xml`. */
class AndroidTaskTexts(private val context: Context) : TaskTexts {
    override fun created(title: String) = context.getString(R.string.summary_created, title)

    override fun changed(title: String) = context.getString(R.string.summary_changed, title)

    override fun statusSet(title: String, status: TaskStatus) =
        context.getString(R.string.summary_status, title, context.getString(statusText(status)))

    override fun deleted(title: String) = context.getString(R.string.summary_deleted, title)

    override fun fieldLabels(): Map<String, String> = mapOf(
        "titel" to context.getString(R.string.field_title),
        "status" to context.getString(R.string.field_status),
        "notiz" to context.getString(R.string.field_note),
        "frist" to context.getString(R.string.field_due),
        "verantwortlichPersonId" to context.getString(R.string.field_assignee),
        "ressortId" to context.getString(R.string.field_ressort),
        "stern" to context.getString(R.string.field_star),
        "spaeterAb" to context.getString(R.string.field_snooze),
        "teil" to context.getString(R.string.field_part),
        "wiederholung" to context.getString(R.string.field_repeat),
    )
}

/** Die Feldnamen des Konfliktbildschirms für Sitzungen und Kommunikation. */
class AndroidCommsTexts(private val context: Context) : CommsTexts {
    override fun fieldLabels(): Map<String, String> = mapOf(
        "titel" to context.getString(R.string.field_title),
        "text" to context.getString(R.string.field_text),
        "entscheid" to context.getString(R.string.field_decision),
        "status" to context.getString(R.string.field_status),
        "datum" to context.getString(R.string.field_date),
        "zeit" to context.getString(R.string.field_time),
        "ort" to context.getString(R.string.field_place),
        "gaeste" to context.getString(R.string.field_guests),
        "art" to context.getString(R.string.field_kind),
        "wichtig" to context.getString(R.string.field_important),
        "angeheftet" to context.getString(R.string.field_pinned),
        "sichtbarAb" to context.getString(R.string.field_visible_from),
        "sichtbarBis" to context.getString(R.string.field_visible_until),
        "antwort" to context.getString(R.string.field_reply),
        "geschlossen" to context.getString(R.string.field_closed),
        "verantwortlichePersonIds" to context.getString(R.string.field_responsible),
        "empfaenger" to context.getString(R.string.field_recipients),
        "name" to context.getString(R.string.field_name),
        "plaetze" to context.getString(R.string.field_seats),
        "notiz" to context.getString(R.string.field_note),
        "vorname" to context.getString(R.string.field_first_name),
        "nachname" to context.getString(R.string.field_last_name),
        "stufe" to context.getString(R.string.field_stage),
        "ceviName" to context.getString(R.string.field_cevi_name),
        "treffpunkt" to context.getString(R.string.field_meeting_point),
        "von" to context.getString(R.string.field_from),
        "nach" to context.getString(R.string.field_to),
        "link" to context.getString(R.string.field_link),
        "mindestbesetzung" to context.getString(R.string.field_minimum),
        "nacht" to context.getString(R.string.field_night),
        "fahrerPersonId" to context.getString(R.string.field_driver),
    )
}
