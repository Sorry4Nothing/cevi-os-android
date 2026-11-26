package io.github.sorry4nothing.cevios.laden

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.images.LocalProfileImages
import io.github.sorry4nothing.cevios.images.mediumPath
import io.github.sorry4nothing.cevios.ui.theme.cevi

/** Wie weit das Bild eines Artikels ist. */
private sealed interface ImageState {
    data object Loading : ImageState
    data object Failed : ImageState
    data class Ready(val bitmap: ImageBitmap) : ImageState
}

/**
 * Das Bild eines Artikels als Quadrat. Solange es lädt, steht eine ruhige Fläche (`skeleton`); lädt es nicht, bleibt die Fläche mit dem
 * Tüten-Symbol, ohne Absturz und ohne Meldung (der Name und der Preis stehen immer daneben). Das Bild ist dekorativ: Der Artikelname
 * steht schon als Text da, ein zweites Vorlesen würde nur stören. Ohne `mediumId` gibt es nichts zu zeichnen (der Aufrufer lässt den Platz weg).
 */
@Composable
fun ItemImage(mediumId: String, modifier: Modifier = Modifier, size: Dp = 72.dp) {
    val images = LocalProfileImages.current
    val state by produceState<ImageState>(ImageState.Loading, mediumId, images) {
        value = if (images == null) {
            ImageState.Failed
        } else {
            images.get(mediumPath(mediumId))?.let { ImageState.Ready(it.asImageBitmap()) } ?: ImageState.Failed
        }
    }
    val shape = MaterialTheme.shapes.small
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(if (state is ImageState.Loading) MaterialTheme.cevi.skeleton else MaterialTheme.colorScheme.surfaceContainerHigh)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        when (val shown = state) {
            is ImageState.Ready -> Image(shown.bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            ImageState.Failed -> Icon(painterResource(R.drawable.ic_bag), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(size / 3))
            ImageState.Loading -> Unit
        }
    }
}
