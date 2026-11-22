package io.github.sorry4nothing.cevios.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.lager.CampApi
import io.github.sorry4nothing.cevios.net.ApiClient
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.programs.ProgramsApi
import io.github.sorry4nothing.cevios.ui.CardSkeleton
import io.github.sorry4nothing.cevios.ui.OutlinedPillButton
import io.github.sorry4nothing.cevios.ui.PrimaryButton
import io.github.sorry4nothing.cevios.ui.TextActionButton
import io.github.sorry4nothing.cevios.ui.focusRing
import io.github.sorry4nothing.cevios.ui.theme.CeviTheme
import io.github.sorry4nothing.cevios.ui.theme.Sizes
import io.github.sorry4nothing.cevios.ui.theme.Spacing
import io.github.sorry4nothing.cevios.ui.theme.cevi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Eine Zeile der Auswahl: nur die Kennung und ein Name (Stufe oder Lager), nie ein Kindername. */
data class ChoiceOption(val id: String, val title: String, val subtitle: String?)

internal sealed interface ChoiceState {
    data object Loading : ChoiceState
    data class Ready(val options: List<ChoiceOption>) : ChoiceState

    /** Es gibt nichts zu wählen: Das Widget nimmt den einzigen Eintrag selbst. */
    data object Single : ChoiceState
    data object Failed : ChoiceState
}

/**
 * Konfiguration beim Hinzufügen (Entwurf WC1, WC2): W1 «Stufe wählen» (nur bei Kindern in mehreren Stufen, zeigt Stufennamen), W3
 * «Lager wählen» (nur bei mehreren aktiven Lagern). Die Wahl bleibt als Kennung beim Widget (`WidgetChoice`).
 */
class WidgetConfigActivity : ComponentActivity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Ohne Abschluss gilt «Abbrechen»: Das System nimmt das Widget wieder weg.
        setResult(Activity.RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        // Die Aktivität ist von aussen erreichbar: Sie gilt nur für ein Widget dieser App.
        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)?.provider
        val stage = provider?.className == ProgramsWidget::class.java.name
        val camp = provider?.className == CampTodayWidget::class.java.name
        if (provider?.packageName != packageName || !(stage || camp)) {
            finish()
            return
        }
        val client = ApiClient.create(applicationContext)
        setContent {
            CeviTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    ChoiceScreen(
                        title = stringResource(if (stage) R.string.widget_config_stage_title else R.string.widget_config_camp_title),
                        hint = stringResource(if (stage) R.string.widget_config_stage_hint else R.string.widget_config_camp_hint),
                        single = stringResource(if (stage) R.string.widget_config_stage_single else R.string.widget_config_camp_single),
                        load = { withContext(Dispatchers.IO) { if (stage) stageOptions(client) else campOptions(client) } },
                        onDone = { chosen -> finishWith(chosen) },
                        onCancel = { finish() },
                    )
                }
            }
        }
    }

    private fun finishWith(chosen: String?) {
        if (chosen != null) WidgetChoice(this).write(widgetId, chosen) else WidgetChoice(this).remove(widgetId)
        setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        WidgetProvider.refreshAll(applicationContext)
        finish()
    }

    /** Die Stufen der eigenen Kinder, nur mit Namen der Stufe. */
    private suspend fun stageOptions(client: ApiClient): ChoiceState {
        val profile = when (val result = ProgramsApi(client).profile()) {
            is ApiResult.Ok -> result.value
            is ApiResult.Failure -> return ChoiceState.Failed
        }
        val names = profile.ageGroups.associate { it.id to it.name }
        val options = profile.children.map { it.ageGroupId }.distinct().mapNotNull { id ->
            names[id]?.let { ChoiceOption(id, it, getString(R.string.widget_config_stage_option, it)) }
        }
        return if (options.size <= 1) ChoiceState.Single else ChoiceState.Ready(options)
    }

    private suspend fun campOptions(client: ApiClient): ChoiceState {
        val camps = when (val result = CampApi(client).list()) {
            is ApiResult.Ok -> result.value.camps.filter { it.active }
            is ApiResult.Failure -> return ChoiceState.Failed
        }
        val options = camps.map { ChoiceOption(it.id, it.title, campDates(it.from, it.until)) }
        return if (options.size <= 1) ChoiceState.Single else ChoiceState.Ready(options)
    }
}

@Composable
private fun ChoiceScreen(
    title: String,
    hint: String,
    single: String,
    load: suspend () -> ChoiceState,
    onDone: (String?) -> Unit,
    onCancel: () -> Unit,
) {
    var attempt by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<ChoiceState>(ChoiceState.Loading) }
    var chosen by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(attempt) {
        state = ChoiceState.Loading
        state = load()
        (state as? ChoiceState.Ready)?.let { chosen = chosen ?: it.options.first().id }
    }
    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.s5, vertical = Spacing.s6),
        verticalArrangement = Arrangement.spacedBy(Spacing.s4),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        when (val s = state) {
            ChoiceState.Loading -> {
                Text(hint, style = MaterialTheme.typography.bodyLarge)
                CardSkeleton(stringResource(R.string.widget_config_loading), count = 2)
            }
            is ChoiceState.Ready -> {
                Text(hint, style = MaterialTheme.typography.bodyLarge)
                s.options.forEach { OptionRow(it, selected = it.id == chosen) { chosen = it.id } }
                PrimaryButton(stringResource(R.string.widget_config_add), { onDone(chosen) }, Modifier.fillMaxWidth())
            }
            ChoiceState.Single -> {
                Text(single, style = MaterialTheme.typography.bodyLarge)
                PrimaryButton(stringResource(R.string.widget_config_add), { onDone(null) }, Modifier.fillMaxWidth())
            }
            ChoiceState.Failed -> {
                Text(stringResource(R.string.widget_config_error_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.widget_config_error_text), style = MaterialTheme.typography.bodyLarge)
                OutlinedPillButton(stringResource(R.string.widget_config_retry), { attempt++ }, Modifier.fillMaxWidth())
            }
        }
        TextActionButton(stringResource(R.string.widget_config_cancel), onCancel)
    }
}

/** Eine Auswahlzeile als Karte mit feinem Rand; die gewählte trägt Häkchen und `accent` (nie nur Farbe). */
@Composable
private fun OptionRow(option: ChoiceOption, selected: Boolean, onSelect: () -> Unit) {
    val shape = MaterialTheme.shapes.medium
    val accent = MaterialTheme.cevi.accent
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touchTarget)
            .focusRing(shape)
            .clip(shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) accent else MaterialTheme.colorScheme.outlineVariant, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(Spacing.s4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Column(Modifier.weight(1f)) {
            Text(option.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            option.subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (selected) Icon(painterResource(R.drawable.ic_check_selected), contentDescription = null, tint = accent)
    }
}
