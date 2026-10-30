package io.github.sorry4nothing.cevios.ui

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Ob Bewegung erlaubt ist. Sind die Animationen im System ausgeschaltet («Bewegung entfernen» oder Entwickleroptionen),
 * bleibt alles an seinem Platz und blendet höchstens über (Checkliste 5.9).
 */
@Composable
fun rememberMotionEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }
}