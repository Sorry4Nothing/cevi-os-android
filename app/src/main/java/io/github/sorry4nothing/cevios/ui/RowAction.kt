package io.github.sorry4nothing.cevios.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/** «Annehmen: Frühstück richten»: So hört TalkBack einen Knopf in einer Zeile; ohne Zeilenname bleibt es beim Knopf. */
fun rowActionLabel(action: String, row: String?): String {
    val name = row?.trim().orEmpty()
    return if (name.isEmpty()) action else "$action: $name"
}

/** Für Knöpfe, die in jeder Zeile einer Liste wiederkehren: Der Screenreader nennt die Zeile mit. */
fun Modifier.rowAction(action: String, row: String?): Modifier = semantics { contentDescription = rowActionLabel(action, row) }
