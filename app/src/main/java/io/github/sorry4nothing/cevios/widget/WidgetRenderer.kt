package io.github.sorry4nothing.cevios.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.DrawableRes
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import io.github.sorry4nothing.cevios.MainActivity
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.programs.weekdayLong
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val STAND_TIME = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Baut aus einem [WidgetScreen] die RemoteViews. Alle Texte stammen aus Ressourcen, alle Farben aus den Layouts (sie folgen so dem Thema
 * des Systems), nichts ist als Farbwert eingebrannt. Jede Fläche mit Ziel trägt eine Beschreibung als ganzer Satz.
 */
class WidgetRenderer(private val context: Context) {
    fun render(kind: WidgetKind, screen: WidgetScreen, plan: LayoutPlan, widgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_frame)
        views.setTextViewText(R.id.widget_name, context.getString(nameOf(kind)))
        views.removeAllViews(R.id.widget_content)
        views.removeAllViews(R.id.widget_stand)
        var describe = ""
        var target: WidgetTarget? = null
        when (screen) {
            is WidgetScreen.Showing -> {
                describe = showing(views, kind, screen, plan, widgetId)
                target = headerTarget(screen.content, kind)
            }
            WidgetScreen.Placeholder -> {
                views.addView(R.id.widget_content, RemoteViews(context.packageName, R.layout.widget_bars))
                describe = context.getString(R.string.widget_loading)
            }
            is WidgetScreen.NeverOpened -> {
                message(views, if (screen.offline) R.string.widget_offline_no_data else R.string.widget_never_opened, if (screen.offline) R.drawable.ic_offline else R.drawable.ic_info)
                describe = context.getString(if (screen.offline) R.string.widget_offline_no_data else R.string.widget_never_opened)
                target = WidgetTarget.Programs.takeIf { kind == WidgetKind.PROGRAM }
            }
            WidgetScreen.SignedOut -> {
                message(views, R.string.widget_signed_out, R.drawable.ic_profile)
                describe = context.getString(R.string.widget_signed_out)
            }
            is WidgetScreen.Locked -> {
                locked(views, screen.redacted, plan)
                describe = context.getString(R.string.widget_locked)
            }
            WidgetScreen.NoCamp -> {
                message(views, R.string.widget_no_camp, R.drawable.ic_tent)
                describe = context.getString(R.string.widget_no_camp)
            }
            WidgetScreen.Failed -> {
                message(views, R.string.widget_error, R.drawable.ic_warning)
                describe = context.getString(R.string.widget_error)
            }
        }
        views.setContentDescription(R.id.widget_root, describe)
        // Die ganze Fläche öffnet das Ziel; ohne Ziel die App.
        views.setOnClickPendingIntent(R.id.widget_root, open(widgetId, target))
        if (screen is WidgetScreen.Showing && plan.size != SizeClass.SMALL && kind != WidgetKind.EMERGENCY && kind != WidgetKind.CHAT) {
            // Ab «mittel» öffnet die Kopfzeile die Liste, jede Zeile ihr eigenes Ziel (siehe `showing`).
            views.setViewVisibility(R.id.widget_head_chevron, View.VISIBLE)
            views.setOnClickPendingIntent(R.id.widget_head, open(widgetId, listTarget(kind, screen.content)))
            views.setContentDescription(R.id.widget_head, context.getString(R.string.widget_a11y_open, context.getString(nameOf(kind))))
        }
        return views
    }

    // ---- Inhalt ----

    private fun showing(views: RemoteViews, kind: WidgetKind, screen: WidgetScreen.Showing, plan: LayoutPlan, widgetId: Int): String {
        val content = screen.content
        val small = plan.size == SizeClass.SMALL
        var description = when (content) {
            is WidgetContent.Programs -> programs(views, content, plan, widgetId)
            is WidgetContent.Calendar -> calendar(views, content, plan, widgetId)
            is WidgetContent.CampToday -> campToday(views, content, plan)
            is WidgetContent.Tasks -> tasks(views, content, plan, widgetId)
            is WidgetContent.Emergency -> emergency(views, content, plan, screen.stand)
            is WidgetContent.Chat -> chat(views, content, plan)
        }
        val standSentence = when (screen.stand.freshness) {
            Freshness.FRESH -> ""
            Freshness.OFFLINE -> " " + context.getString(R.string.widget_stand_offline, screen.stand.at.format(STAND_TIME))
            Freshness.STALE -> " " + context.getString(R.string.widget_stand_stale_spoken, screen.stand.hours)
        }
        if (kind != WidgetKind.EMERGENCY) stand(views, screen.stand, always = !small)
        description += standSentence
        return description
    }

    private fun programs(views: RemoteViews, content: WidgetContent.Programs, plan: LayoutPlan, widgetId: Int): String {
        if (content.isEmpty) {
            message(views, R.string.widget_empty_programs, R.drawable.ic_calendar)
            return context.getString(R.string.widget_empty_programs)
        }
        val small = plan.size == SizeClass.SMALL
        val rows = content.rows.take(plan.rows)
        rows.forEachIndexed { i, program ->
            val row = RemoteViews(context.packageName, R.layout.widget_row)
            row.setTextViewText(R.id.row_title, program.title)
            row.setTextViewText(R.id.row_line, whenLong(program.date, program.time, context.getString(R.string.time_list_all_day)))
            if (plan.showPlace && program.place != null) {
                row.setViewVisibility(R.id.row_place, View.VISIBLE)
                row.setTextViewText(R.id.row_place_text, program.place)
            }
            if (plan.showGroups) program.groups.take(MAX_GROUPS).forEach { group(row, R.id.row_groups, it) }
            val status = program.status
            if (status != null) {
                val (layout, text) = statusChip(status, long = !small)
                // Auf der schmalen Kachel steht der Stand unter den Gruppen, sonst daneben.
                chip(row, if (small) R.id.row_status else R.id.row_groups, layout, text)
            }
            val spoken = programSentence(program, status, plan.showPlace)
            rowTarget(row, widgetId, small, WidgetTarget.Program(program.id), spoken, divider = i > 0)
            views.addView(R.id.widget_content, row)
        }
        return programSentence(rows.first(), rows.first().status, plan.showPlace)
    }

    private fun programSentence(row: ProgramRow, status: ProgramStatus?, withPlace: Boolean): String {
        val place = if (withPlace && row.place != null) context.getString(R.string.widget_a11y_program_place, row.place) else ""
        val statusText = status?.let { statusSpoken(it) + "." }.orEmpty()
        return context.getString(R.string.widget_a11y_program, row.title, spokenWhen(row.date, row.time), place, if (statusText.isEmpty()) "" else " $statusText")
    }

    private fun calendar(views: RemoteViews, content: WidgetContent.Calendar, plan: LayoutPlan, widgetId: Int): String {
        if (content.isEmpty) {
            message(views, R.string.widget_empty_calendar, R.drawable.ic_calendar)
            return context.getString(R.string.widget_empty_calendar)
        }
        val rows = content.rows.take(plan.rows)
        rows.forEachIndexed { i, entry ->
            val sentence = context.getString(R.string.widget_a11y_row, "${entry.title}, ${spokenWhen(entry.date, entry.time)}")
            when (plan.size) {
                SizeClass.MEDIUM -> {
                    val row = RemoteViews(context.packageName, R.layout.widget_row_inline)
                    row.setTextViewText(R.id.row_line, whenShort(entry.date, entry.time, withTime = false))
                    row.setTextViewText(R.id.row_title, entry.title)
                    row.setContentDescription(R.id.row_root, sentence)
                    row.setOnClickPendingIntent(R.id.row_root, open(widgetId, WidgetTarget.Program(entry.id)))
                    views.addView(R.id.widget_content, row)
                }
                else -> {
                    val row = RemoteViews(context.packageName, R.layout.widget_row)
                    row.setTextViewText(R.id.row_title, entry.title)
                    row.setTextViewText(R.id.row_line, whenShort(entry.date, entry.time, withTime = true))
                    if (plan.showGroups) entry.groups.take(MAX_GROUPS).forEach { group(row, R.id.row_groups, it) }
                    rowTarget(row, widgetId, plan.size == SizeClass.SMALL, WidgetTarget.Program(entry.id), sentence, divider = i > 0)
                    views.addView(R.id.widget_content, row)
                }
            }
        }
        val first = rows.first()
        val groups = if (first.groups.isEmpty()) "" else ", " + first.groups.joinToString(", ")
        val count = context.resources.getQuantityString(R.plurals.widget_a11y_calendar_count, content.total, content.total)
        return context.getString(R.string.widget_a11y_calendar, count, first.title, spokenWhen(first.date, first.time, weekdayOnly = true), groups)
    }

    private fun campToday(views: RemoteViews, content: WidgetContent.CampToday, plan: LayoutPlan): String {
        val dayText = content.day?.let { context.getString(R.string.widget_day_of, it, content.days) }
        if (dayText != null) {
            val row = RemoteViews(context.packageName, R.layout.widget_row)
            row.setTextViewText(R.id.row_title, dayText)
            views.addView(R.id.widget_content, row)
            if (plan.size == SizeClass.LARGE) {
                val progress = RemoteViews(context.packageName, R.layout.widget_day_progress)
                progress.setProgressBar(R.id.widget_progress_bar, 100, content.day!! * 100 / content.days.coerceAtLeast(1), false)
                views.addView(R.id.widget_content, progress)
            }
        }
        if (content.isEmpty) {
            message(views, R.string.widget_empty_camp, R.drawable.ic_tent)
            return listOfNotNull(dayText, context.getString(R.string.widget_empty_camp)).joinToString(". ") + "."
        }
        content.duty?.let { duty ->
            val row = RemoteViews(context.packageName, R.layout.widget_row)
            row.setViewVisibility(R.id.row_label, View.VISIBLE)
            row.setTextViewText(R.id.row_label, context.getString(R.string.widget_your_duty))
            row.setTextViewText(R.id.row_title, dutyText(duty))
            if (duty.time != null) row.setTextViewText(R.id.row_line, duty.time) else row.setViewVisibility(R.id.row_line, View.GONE)
            views.addView(R.id.widget_content, row)
        }
        content.important.take(plan.rows).forEachIndexed { i, title ->
            val row = RemoteViews(context.packageName, R.layout.widget_row)
            if (i == 0) {
                row.setViewVisibility(R.id.row_label, View.VISIBLE)
                row.setTextViewText(R.id.row_label, context.getString(R.string.widget_important_today))
            }
            row.setTextViewText(R.id.row_title, title)
            row.setViewVisibility(R.id.row_line, View.GONE)
            // Der Zeileninhalt ist für Bedienhilfen verborgen: ohne Satz hörte TalkBack nichts.
            row.setContentDescription(R.id.row_root, context.getString(R.string.widget_a11y_important, title))
            views.addView(R.id.widget_content, row)
        }
        val duty = content.duty?.let { context.getString(R.string.widget_a11y_camp_duty, dutyText(it)) }.orEmpty()
        return context.getString(R.string.widget_a11y_camp, dayText ?: context.getString(R.string.widget_name_camp), duty)
    }

    private fun dutyText(duty: DutyLine) =
        if (duty.section != null) context.getString(R.string.widget_duty_line, duty.title, duty.section) else duty.title

    private fun tasks(views: RemoteViews, content: WidgetContent.Tasks, plan: LayoutPlan, widgetId: Int): String {
        if (content.isEmpty) {
            message(views, R.string.widget_empty_tasks, R.drawable.ic_list_check)
            return context.getString(R.string.widget_empty_tasks)
        }
        counter(views, content.overdue, R.string.widget_overdue, if (plan.size == SizeClass.SMALL) context.getString(R.string.widget_open_for_you, content.open) else null)
        val rows = if (plan.size == SizeClass.SMALL) emptyList() else content.rows.take(plan.rows)
        rows.forEachIndexed { i, task ->
            val row = RemoteViews(context.packageName, R.layout.widget_row)
            row.setTextViewText(R.id.row_title, task.title)
            val due = task.due?.let { context.getString(R.string.widget_due, dueLong(it)) }
            if (due != null) row.setTextViewText(R.id.row_line, due) else row.setViewVisibility(R.id.row_line, View.GONE)
            urgencyChip(task.urgency)?.let { (layout, text) -> chip(row, R.id.row_groups, layout, text) }
            val spoken = listOfNotNull(task.title, due, urgencyChip(task.urgency)?.second).joinToString(", ")
            rowTarget(row, widgetId, small = false, WidgetTarget.CampTask(task.id), context.getString(R.string.widget_a11y_row, spoken), divider = i > 0)
            views.addView(R.id.widget_content, row)
        }
        val next = rows.firstOrNull()?.let { task ->
            context.getString(R.string.widget_a11y_tasks_next, task.title, task.due?.let(::dueLong) ?: "")
        }.orEmpty()
        return context.getString(R.string.widget_a11y_tasks, content.overdue, next)
    }

    private fun emergency(views: RemoteViews, content: WidgetContent.Emergency, plan: LayoutPlan, stand: Stand): String {
        val button = RemoteViews(context.packageName, R.layout.widget_sos)
        button.setTextViewText(R.id.sos_text, context.getString(R.string.widget_emergency_button))
        if (plan.size != SizeClass.SMALL) {
            button.setViewVisibility(R.id.sos_hint, View.VISIBLE)
            button.setTextViewText(R.id.sos_hint, context.getString(R.string.widget_emergency_hint))
        }
        views.addView(R.id.widget_content, button)
        // Der Knopf gilt auch ohne Netz: Der Hinweis ersetzt dort den Stand.
        if (stand.freshness != Freshness.FRESH) standLine(views, R.layout.widget_stand_normal, R.string.widget_emergency_offline, R.drawable.ic_offline)
        return context.getString(R.string.widget_a11y_emergency)
    }

    private fun chat(views: RemoteViews, content: WidgetContent.Chat, plan: LayoutPlan): String {
        val sentence = when {
            content.isEmpty -> context.getString(R.string.widget_empty_chat)
            plan.size == SizeClass.SMALL -> null
            else -> context.resources.getQuantityString(R.plurals.widget_chat_messages, content.unread, content.unread) + " " +
                context.resources.getQuantityString(R.plurals.widget_chat_chats, content.chats, content.chats)
        }
        counter(views, content.unread, R.string.widget_unread, sentence)
        return if (content.isEmpty) context.getString(R.string.widget_a11y_chat_none)
        else context.resources.getQuantityString(R.plurals.widget_a11y_chat, content.unread, content.unread)
    }

    /** Redigiert: Art, Zeit und Zähler bleiben, alles andere ersetzt ein Satz. */
    private fun locked(views: RemoteViews, redacted: Redacted?, plan: LayoutPlan) {
        if (redacted != null) {
            redacted.whens.take(plan.rows.coerceAtLeast(1)).forEach { (date, time) ->
                val row = RemoteViews(context.packageName, R.layout.widget_row)
                row.setTextViewText(R.id.row_title, whenLong(date, time, context.getString(R.string.time_list_all_day)))
                row.setViewVisibility(R.id.row_line, View.GONE)
                views.addView(R.id.widget_content, row)
            }
            redacted.day?.let { day ->
                val row = RemoteViews(context.packageName, R.layout.widget_row)
                row.setTextViewText(R.id.row_title, context.getString(R.string.widget_day_of, day, redacted.days ?: day))
                if (redacted.dutyTime != null) row.setTextViewText(R.id.row_line, context.getString(R.string.widget_your_duty) + " " + redacted.dutyTime) else row.setViewVisibility(R.id.row_line, View.GONE)
                views.addView(R.id.widget_content, row)
            }
            if (redacted.kind == WidgetKind.TASKS || redacted.kind == WidgetKind.CHAT) {
                redacted.counter?.let { counter(views, it, if (redacted.kind == WidgetKind.TASKS) R.string.widget_overdue else R.string.widget_unread, null) }
            }
        }
        message(views, R.string.widget_locked, R.drawable.ic_lock)
    }

    // ---- Bausteine ----

    private fun message(views: RemoteViews, @StringRes text: Int, @DrawableRes icon: Int) {
        val message = RemoteViews(context.packageName, R.layout.widget_message)
        message.setTextViewText(R.id.msg_text, context.getString(text))
        message.setImageViewResource(R.id.msg_icon, icon)
        views.addView(R.id.widget_content, message)
    }

    private fun counter(views: RemoteViews, number: Int, @StringRes word: Int, sentence: String?) {
        val counter = RemoteViews(context.packageName, R.layout.widget_counter)
        counter.setTextViewText(R.id.counter_num, number.toString())
        counter.setTextViewText(R.id.counter_word, context.getString(word))
        if (sentence != null) {
            counter.setViewVisibility(R.id.counter_sentence, View.VISIBLE)
            counter.setTextViewText(R.id.counter_sentence, sentence)
        }
        views.addView(R.id.widget_content, counter)
    }

    private fun group(row: RemoteViews, container: Int, name: String) {
        val chip = RemoteViews(context.packageName, R.layout.widget_chip_stage)
        chip.setTextViewText(R.id.chip_text, name)
        row.setViewVisibility(container, View.VISIBLE)
        row.addView(container, chip)
    }

    private fun chip(row: RemoteViews, container: Int, @LayoutRes layout: Int, text: String) {
        val chip = RemoteViews(context.packageName, layout)
        chip.setTextViewText(R.id.chip_text, text)
        row.setViewVisibility(container, View.VISIBLE)
        row.addView(container, chip)
    }

    private fun statusChip(status: ProgramStatus, long: Boolean): Pair<Int, String> = when (status.state) {
        ProgramState.CHILDREN_OPEN -> R.layout.widget_chip_open to
            if (long) countText(R.string.widget_status_open_one_long, R.string.widget_status_open_many_long, status.count)
            else countText(R.string.widget_status_open_one, R.string.widget_status_open_many, status.count)
        ProgramState.YOU_OPEN -> R.layout.widget_chip_open to context.getString(R.string.widget_status_you_open)
        ProgramState.CHILDREN_NO -> R.layout.widget_chip_no to countText(R.string.widget_status_no_one, R.string.widget_status_no_many, status.count)
        ProgramState.YOU_NO -> R.layout.widget_chip_no to context.getString(R.string.widget_status_you_no)
        ProgramState.ALL_YES -> R.layout.widget_chip_yes to context.getString(R.string.widget_status_all_yes)
        ProgramState.YOU_YES -> R.layout.widget_chip_yes to context.getString(R.string.widget_status_you_yes)
    }

    private fun statusSpoken(status: ProgramStatus): String = when (status.state) {
        ProgramState.CHILDREN_OPEN -> countText(R.string.widget_status_open_one_long, R.string.widget_status_open_many_long, status.count)
        else -> statusChip(status, long = true).second
    }

    private fun countText(@StringRes one: Int, @StringRes many: Int, count: Int) =
        if (count == 1) context.getString(one) else context.getString(many, count)

    private fun urgencyChip(urgency: Urgency): Pair<Int, String>? = when (urgency) {
        Urgency.OVERDUE -> R.layout.widget_chip_error to context.getString(R.string.widget_urgency_overdue)
        Urgency.SOON -> R.layout.widget_chip_warn to context.getString(R.string.widget_urgency_soon)
        Urgency.LATER -> R.layout.widget_chip_open to context.getString(R.string.widget_urgency_later)
        Urgency.NONE -> null
    }

    /** Die Stand-Zeile: auf der kleinen Kachel nur, wenn der Stand nicht frisch ist (nie ein stiller alter Stand). */
    private fun stand(views: RemoteViews, stand: Stand, always: Boolean) {
        val time = stand.at.format(STAND_TIME)
        when (stand.freshness) {
            Freshness.FRESH -> if (always) standLine(views, R.layout.widget_stand_normal, R.string.widget_stand, null, time)
            Freshness.OFFLINE -> standLine(views, R.layout.widget_stand_normal, R.string.widget_stand_offline, R.drawable.ic_offline, time)
            Freshness.STALE -> standLine(views, R.layout.widget_stand_warn, R.string.widget_stand_stale, R.drawable.ic_warning, stand.hours)
        }
    }

    private fun standLine(views: RemoteViews, @LayoutRes layout: Int, @StringRes text: Int, @DrawableRes icon: Int?, vararg args: Any) {
        val line = RemoteViews(context.packageName, layout)
        line.setTextViewText(R.id.stand_text, context.getString(text, *args))
        if (icon != null) {
            line.setViewVisibility(R.id.stand_icon, View.VISIBLE)
            line.setImageViewResource(R.id.stand_icon, icon)
        }
        views.addView(R.id.widget_stand, line)
    }

    /** Ab «mittel» hat jede Zeile ihr Ziel und den Pfeil; die kleine Kachel öffnet über die ganze Fläche. */
    private fun rowTarget(row: RemoteViews, widgetId: Int, small: Boolean, target: WidgetTarget, sentence: String, divider: Boolean) {
        if (divider) row.setViewVisibility(R.id.row_divider, View.VISIBLE)
        if (small) return
        row.setViewVisibility(R.id.row_chevron, View.VISIBLE)
        row.setContentDescription(R.id.row_root, sentence)
        row.setOnClickPendingIntent(R.id.row_root, open(widgetId, target))
    }

    private fun listTarget(kind: WidgetKind, content: WidgetContent): WidgetTarget = when (kind) {
        WidgetKind.PROGRAM, WidgetKind.CALENDAR -> WidgetTarget.Programs
        else -> headerTarget(content, kind)
    }

    private fun spokenWhen(date: LocalDate?, time: String?, weekdayOnly: Boolean = false): String {
        date ?: return ""
        val day = if (weekdayOnly) weekdayLong(date) else "${weekdayLong(date)}, ${date.dayOfMonth}. ${date.month.getDisplayName(TextStyle.FULL, Locale.GERMAN)}"
        return if (time != null) "$day, $time" else day
    }

    /** Öffnet die App; mit Ziel gleich dort (derselbe Weg wie das Tippen auf einen Push, auch beim Kaltstart). */
    private fun open(widgetId: Int, target: WidgetTarget?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        var key = "app"
        if (target != null) {
            val (type, id) = intentExtras(target)
            intent.putExtra("typ", type)
            if (id != null) intent.putExtra("id", id)
            key = "$type/${id.orEmpty()}"
        }
        // Eigene Adresse je Widget und Ziel: Sonst überschriebe ein PendingIntent den anderen (Extras zählen nicht).
        intent.data = Uri.parse("cevios-widget://$widgetId/${Uri.encode(key)}")
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun nameOf(kind: WidgetKind) = when (kind) {
        WidgetKind.PROGRAM -> R.string.widget_name_programs
        WidgetKind.CALENDAR -> R.string.widget_name_calendar
        WidgetKind.CAMP_TODAY -> R.string.widget_name_camp
        WidgetKind.TASKS -> R.string.widget_name_tasks
        WidgetKind.EMERGENCY -> R.string.widget_name_emergency
        WidgetKind.CHAT -> R.string.widget_name_chat
    }

    private companion object {
        const val MAX_GROUPS = 2
    }
}
