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
import io.github.sorry4nothing.cevios.programs.SWISS_ZONE
import io.github.sorry4nothing.cevios.ui.ErrorDisplay
import io.github.sorry4nothing.cevios.ui.Loading
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import java.util.Locale

private const val MAX_CHARS = 2000
private val DATE_TIME_FORMAT = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(Locale.forLanguageTag("de-CH"))

@Composable
fun chatTitle(chat: Chat?): String = when {
    chat == null -> stringResource(R.string.chat_title)
    chat.isGroup -> stringResource(R.string.chat_group, chat.ageGroup.name)
    else -> stringResource(R.string.chat_direct, chat.parent?.name.orEmpty()).trim()
}

@Composable
fun ChatListScreen(
    state: ApiResult<ChatList>?,
    profile: Profile?,
    action: ActionState,
    onOpen: (String) -> Unit,
    onDirectChat: (ageGroupId: String) -> Unit,
    onHelp: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.chat_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(16.dp).semantics { heading() },
        )
        TextButton(onClick = onHelp, Modifier.padding(horizontal = 8.dp)) { Text(stringResource(R.string.help_button)) }
        ActionMessage(action)
        when (state) {
            null -> Loading()
            is ApiResult.Failure -> ErrorDisplay(state, onRetry)
            is ApiResult.Ok -> {
                val startable = ChatRules.ageGroupsForDirectChat(profile, state.value.chats)
                if (state.value.chats.isEmpty() && startable.isEmpty()) {
                    Text(stringResource(R.string.chat_empty), Modifier.padding(16.dp))
                } else {
                    LazyColumn {
                        items(state.value.chats, key = { it.id }) { chat ->
                            ChatRow(chat, onOpen)
                            HorizontalDivider()
                        }
                        items(startable, key = { "start-" + it.id }) { ageGroup ->
                            OutlinedButton(
                                onClick = { onDirectChat(ageGroup.id) },
                                enabled = !action.running,
                                modifier = Modifier.padding(16.dp),
                            ) { Text(stringResource(R.string.direct_chat_start, ageGroup.name)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatRow(chat: Chat, onOpen: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.chat_open)) { onOpen(chat.id) }
            .heightIn(min = 48.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(chatTitle(chat), style = MaterialTheme.typography.titleMedium)
        if (!chat.isGroup) Text(stringResource(R.string.chat_age_group, chat.ageGroup.name))
    }
}

@Composable
fun ChatConversationScreen(
    title: String,
    chat: Chat?,
    profile: Profile?,
    state: ApiResult<Conversation>?,
    loadingOlder: Boolean,
    send: SendState,
    action: ActionState,
    onSend: (text: String, onSent: () -> Unit) -> Unit,
    onOlder: () -> Unit,
    onRefresh: () -> Unit,
    onHide: (messageId: String) -> Unit,
    onReport: (messageId: String) -> Unit,
    onDirectChat: (ageGroupId: String, parentUserId: String) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    // Safe-Drawing-Padding enthält die Tastatur: Die Eingabe bleibt sichtbar.
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            TextButton(onClick = onRefresh) { Text(stringResource(R.string.refresh)) }
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics { heading() },
        )
        // Im Einzelchat lesen alle Stufenleitungen der Stufe mit; das steht im Kopf, nicht erst im Kleingedruckten.
        if (chat != null && !chat.isGroup && chat.groupLeaders.isNotEmpty()) {
            Text(
                stringResource(R.string.chat_read_along, chat.groupLeaders.joinToString(", ") { it.name }),
                Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Column(Modifier.weight(1f)) {
            when (state) {
                null -> Loading()
                is ApiResult.Failure -> ErrorDisplay(state, onRetry)
                is ApiResult.Ok ->
                    Messages(state.value, chat, profile, loadingOlder, onOlder, onHide, onReport, onDirectChat)
            }
        }
        ActionMessage(action)
        MessageInput(send, onSend)
    }
}

@Composable
private fun Messages(
    conversation: Conversation,
    chat: Chat?,
    profile: Profile?,
    loadingOlder: Boolean,
    onOlder: () -> Unit,
    onHide: (String) -> Unit,
    onReport: (String) -> Unit,
    onDirectChat: (ageGroupId: String, parentUserId: String) -> Unit,
) {
    if (conversation.messages.isEmpty()) {
        Text(stringResource(R.string.messages_empty), Modifier.padding(16.dp))
        return
    }
    var selected by remember { mutableStateOf<ChatMessage?>(null) }
    var question by remember { mutableStateOf<ConfirmQuestion?>(null) }

    // Neueste zuerst und von unten nach oben: so wächst der Verlauf wie in jedem Chat nach oben.
    LazyColumn(reverseLayout = true, contentPadding = PaddingValues(16.dp)) {
        items(conversation.messages, key = { it.id }) { message ->
            MessageRow(message, onActions = { selected = message })
        }
        if (conversation.next != null) {
            item {
                OutlinedButton(onClick = onOlder, enabled = !loadingOlder, modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(stringResource(if (loadingOlder) R.string.loading else R.string.messages_older))
                }
            }
        }
    }

    selected?.let { message ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(stringResource(R.string.actions_title, message.author.name)) },
            text = {
                Column {
                    if (ChatRules.mayOpenDirectChat(chat, message, profile)) {
                        TextButton(onClick = {
                            selected = null
                            chat?.let { onDirectChat(it.ageGroup.id, message.author.id) }
                        }) { Text(stringResource(R.string.action_direct_chat, message.author.name)) }
                    }
                    if (ChatRules.mayHide(chat, message, profile)) {
                        TextButton(onClick = {
                            selected = null
                            question = ConfirmQuestion(ConfirmKind.HIDE, message.id)
                        }) { Text(stringResource(R.string.action_hide)) }
                    }
                    if (ChatRules.mayReport(message)) {
                        TextButton(onClick = {
                            selected = null
                            question = ConfirmQuestion(ConfirmKind.REPORT, message.id)
                        }) { Text(stringResource(R.string.action_report)) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    question?.let { f ->
        AlertDialog(
            onDismissRequest = { question = null },
            title = { Text(stringResource(f.kind.title)) },
            text = { Text(stringResource(f.kind.question)) },
            confirmButton = {
                Button(onClick = {
                    question = null
                    if (f.kind == ConfirmKind.HIDE) onHide(f.messageId) else onReport(f.messageId)
                }) { Text(stringResource(f.kind.confirm)) }
            },
            dismissButton = { TextButton(onClick = { question = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private enum class ConfirmKind(@StringRes val title: Int, @StringRes val question: Int, @StringRes val confirm: Int) {
    HIDE(R.string.action_hide, R.string.hide_question, R.string.action_confirm_hide),
    REPORT(R.string.action_report, R.string.report_question, R.string.action_confirm_report),
}

private data class ConfirmQuestion(val kind: ConfirmKind, val messageId: String)

@Composable
private fun MessageRow(message: ChatMessage, onActions: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val author = if (message.own) stringResource(R.string.message_you) else message.author.name
        Text("$author, ${timeText(message.createdAt)}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        // Ausgeblendet steht als Hinweis da, nie als leere Zeile.
        Text(
            if (message.hidden) stringResource(R.string.message_hidden) else message.text.orEmpty(),
            style = MaterialTheme.typography.bodyLarge,
        )
        message.program?.let { Text(stringResource(R.string.message_program, it.title), style = MaterialTheme.typography.bodyMedium) }
        val description = stringResource(R.string.actions_description, author)
        TextButton(onClick = onActions, Modifier.semantics { contentDescription = description }) {
            Text(stringResource(R.string.actions_button))
        }
    }
}

/** Läuft, Fehler oder Bestätigung einer Aktion, angesagt als Live-Region. */
@Composable
private fun ActionMessage(action: ActionState) {
    val text = when {
        action.running -> stringResource(R.string.action_running)
        action.error != null -> stringResource(action.error.text)
        action.done != null -> stringResource(action.done)
        else -> return
    }
    Text(
        text,
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics {
            liveRegion = if (action.error != null) LiveRegionMode.Assertive else LiveRegionMode.Polite
        },
    )
}

@Composable
private fun MessageInput(send: SendState, onSend: (String, () -> Unit) -> Unit) {
    var draft by rememberSaveable { mutableStateOf("") }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (send.running) {
            Text(stringResource(R.string.message_sending), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        send.error?.let {
            Text(stringResource(it.text), Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = draft,
                onValueChange = { if (it.length <= MAX_CHARS) draft = it },
                label = { Text(stringResource(R.string.message_input)) },
                maxLines = 4,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = { onSend(draft) { draft = "" } },
                enabled = draft.isNotBlank() && !send.running,
            ) { Text(stringResource(R.string.message_send)) }
        }
    }
}

@Composable
fun HelpScreen(state: ApiResult<HelpResponse>?, onBack: () -> Unit, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        TextButton(onClick = onBack, Modifier.padding(horizontal = 8.dp)) { Text(stringResource(R.string.back)) }
        Text(
            stringResource(R.string.help_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics { heading() },
        )
        when (state) {
            null -> Loading()
            is ApiResult.Failure -> ErrorDisplay(state, onRetry)
            is ApiResult.Ok -> Text(
                state.value.trustedPersons?.takeIf { it.isNotBlank() } ?: stringResource(R.string.help_empty),
                Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

private fun timeText(iso: String): String =
    try {
        DATE_TIME_FORMAT.format(Instant.parse(iso).atZone(SWISS_ZONE))
    } catch (e: DateTimeParseException) {
        ""
    }
