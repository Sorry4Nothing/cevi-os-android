package io.github.sorry4nothing.cevios.termine

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.auth.SectionTitle
import io.github.sorry4nothing.cevios.auth.SettingsCard
import io.github.sorry4nothing.cevios.chat.Profile
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.ui.ActionBanner
import io.github.sorry4nothing.cevios.ui.ChipKind
import io.github.sorry4nothing.cevios.ui.NoticeBanner
import io.github.sorry4nothing.cevios.ui.NoticeKind
import io.github.sorry4nothing.cevios.ui.OutlinedPillButton
import io.github.sorry4nothing.cevios.ui.SlowHint
import io.github.sorry4nothing.cevios.ui.Spinner
import io.github.sorry4nothing.cevios.ui.StatusChip
import io.github.sorry4nothing.cevios.ui.TextActionButton
import io.github.sorry4nothing.cevios.ui.theme.Spacing
import io.github.sorry4nothing.cevios.ui.theme.cevi

/** Eine Gruppe der Person mit ihrem Typ als Wort («Stufe Wölfe»); `stageId` nur bei einer Stufe, für das Logo. */
data class GroupLabel(val type: Int, val name: String, val stageId: String? = null)

/**
 * Die Gruppen der Person, soweit das Profil sie nennt: ihre Stufen (aus Rollen und Kindern) und ihre Funktion in der Stufe. Teams
 * und Lager liefert `GET /api/v1/ich` nicht; sie fehlen hier, bis es eine Route dafür gibt.
 */
fun groupLabels(profile: Profile, adminName: String, groupLeaderName: String, leaderName: String): List<GroupLabel> = buildList {
    profile.ageGroups.forEach { add(GroupLabel(R.string.group_type_stufe, it.name, it.id)) }
    if (profile.admin) add(GroupLabel(R.string.group_type_funktion, adminName))
    if (profile.roles.any { it.type == "stufenleitung" }) add(GroupLabel(R.string.group_type_funktion, groupLeaderName))
    else if (profile.roles.any { it.type == "leitung" }) add(GroupLabel(R.string.group_type_funktion, leaderName))
}

/** «Meine Gruppen»: Chips mit dem Typ als Wort. Ohne Gruppe steht ein Satz, kein leerer Abschnitt. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GroupsSection(profile: Profile) {
    SectionTitle(R.string.profile_groups_title)
    val groups = groupLabels(
        profile, stringResource(R.string.role_function_admin), stringResource(R.string.role_function_leader), stringResource(R.string.role_function_leading),
    )
    if (groups.isEmpty()) {
        Text(stringResource(R.string.profile_groups_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp))
        return
    }
    FlowRow(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        groups.forEach { StatusChip(ChipKind.AGE_GROUP, stringResource(R.string.group_chip, stringResource(it.type), it.name), stageId = it.stageId) }
    }
    Text(stringResource(R.string.profile_groups_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp))
}

/**
 * «Mein Kalender-Link» (Entwurf A6). Den Klartext gibt es nur direkt nach dem Erzeugen: Dann steht «Link kopieren» da. Danach nennt
 * der Abschnitt nur noch die Endung und bietet «Link neu erzeugen» an. Kopiert wird über die Zwischenablage mit dem Vermerk «vertraulich».
 */
@Composable
fun CalendarLinkSection(
    status: ApiResult<CalendarLinkStatus>?,
    hasFresh: Boolean,
    busy: CalendarLinkViewModel.Busy,
    url: () -> String?,
    onCreate: () -> Unit,
    onRevoke: () -> Unit,
    onReload: () -> Unit,
    onDismissError: () -> Unit,
) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(hasFresh) { if (!hasFresh) copied = false }
    SectionTitle(R.string.cal_link_title)
    SettingsCard {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = Spacing.s3), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            Text(stringResource(R.string.cal_link_text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when (status) {
                null -> Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalAlignment = Alignment.CenterVertically) {
                    Spinner(size = 20.dp, color = MaterialTheme.cevi.accent)
                    Text(stringResource(R.string.cal_link_loading), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
                is ApiResult.Failure -> {
                    NoticeBanner(NoticeKind.ERROR, stringResource(R.string.cal_link_failed))
                    OutlinedPillButton(stringResource(R.string.retry), onReload, Modifier.fillMaxWidth())
                }
                is ApiResult.Ok -> {
                    val link = status.value
                    when {
                        hasFresh -> {
                            NoticeBanner(NoticeKind.INFO, stringResource(R.string.cal_link_fresh))
                            OutlinedPillButton(
                                stringResource(R.string.cal_link_copy),
                                { url()?.let { copySecret(context, context.getString(R.string.cal_link_clipboard_label), it); copied = true } },
                                Modifier.fillMaxWidth(), icon = R.drawable.ic_file,
                            )
                        }
                        link.active -> Text(stringResource(R.string.cal_link_active, link.ending.orEmpty()), style = MaterialTheme.typography.bodyMedium)
                        else -> Text(stringResource(R.string.cal_link_none), style = MaterialTheme.typography.bodyMedium)
                    }
                    if (copied) ActionBanner(NoticeKind.SUCCESS, stringResource(R.string.cal_link_copied), null, null, { copied = false })
                    OutlinedPillButton(
                        stringResource(if (link.active) R.string.cal_link_renew else R.string.cal_link_create), onCreate, Modifier.fillMaxWidth(),
                        loading = busy.running, loadingText = stringResource(R.string.cal_link_saving),
                    )
                    SlowHint(busy.running)
                    if (link.active) {
                        Text(stringResource(R.string.cal_link_renew_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextActionButton(stringResource(R.string.cal_link_revoke), onRevoke)
                    }
                    if (busy.error != null) ActionBanner(NoticeKind.ERROR, stringResource(R.string.cal_link_failed), stringResource(R.string.retry), onCreate, onDismissError)
                }
            }
        }
    }
}

/** Legt einen geheimen Wert in die Zwischenablage und vermerkt ihn als vertraulich (das System zeigt ihn dann nicht in der Vorschau). */
fun copySecret(context: Context, label: String, text: String) {
    val clip = ClipData.newPlainText(label, text)
    clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
}
