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
