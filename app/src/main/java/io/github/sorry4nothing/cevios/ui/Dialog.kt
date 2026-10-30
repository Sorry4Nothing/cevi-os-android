package io.github.sorry4nothing.cevios.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp

/**
 * Dialog im Design: `surfaceContainerHigh`, Ecken 24 dp, Titel als Frage. Aktionen rechts: «Abbrechen» als Text-Knopf in
 * `dismissButton`, die Aktion in `confirmButton` (bei Bestätigungen [DangerButton]).
 */
@Composable
fun CeviDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() }) },
        text = content,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Fliesstext im Dialog: 15/22 in `onSurfaceVariant`. */
@Composable
fun DialogText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Der Fokus startet auf diesem Element, sobald es erscheint (im Dialog: «Abbrechen»). */
@Composable
fun Modifier.initialFocus(): Modifier {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    return focusRequester(focus)
}
