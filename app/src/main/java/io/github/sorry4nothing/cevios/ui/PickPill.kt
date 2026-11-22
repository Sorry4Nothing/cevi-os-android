package io.github.sorry4nothing.cevios.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.ui.theme.Pill
import io.github.sorry4nothing.cevios.ui.theme.Sizes
import io.github.sorry4nothing.cevios.ui.theme.Spacing
import io.github.sorry4nothing.cevios.ui.theme.cevi

/**
 * Auswahlpille (Entwurf `cv-pickpill`): offen mit Rand, gewählt in `accent` mit Häkchen. Der Zustand steht in Farbe, Häkchen und
 * für den Screenreader als «ausgewählt»; die Pille ist immer mindestens 48 dp hoch. `role` bestimmt, wie TalkBack sie ansagt
 * (Auswahl aus einer Gruppe: [Role.RadioButton], Reiter: [Role.Tab]).
 */
@Composable
fun PickPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    role: Role = Role.RadioButton,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.cevi
    val scheme = MaterialTheme.colorScheme
    val content = if (selected) colors.onAccent else scheme.onSurface
    Row(
        modifier
            .heightIn(min = Sizes.touchTarget)
            .focusRing(Pill)
            .clip(Pill)
            .then(if (selected) Modifier.background(colors.accent) else Modifier.border(1.dp, scheme.outline, Pill))
            .selectable(selected = selected, enabled = enabled, role = role, onClick = onClick)
            .padding(start = if (selected) Spacing.s3 else Spacing.s4, end = Spacing.s4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (selected) Icon(painterResource(R.drawable.ic_check_selected), contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = content, fontWeight = FontWeight.Bold)
    }
}
