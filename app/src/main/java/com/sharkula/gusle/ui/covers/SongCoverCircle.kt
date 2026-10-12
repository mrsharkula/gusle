package com.sharkula.gusle.ui.covers

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.wear.compose.material3.MaterialTheme
import coil3.compose.AsyncImage
import com.sharkula.gusle.R
import com.sharkula.gusle.ui.components.RowCircleIconSize
import com.sharkula.gusle.ui.components.RowCircleSize

/**
 * A song's [cover] in a list row's circle, center-cropped, so a 16:9 cover shows its middle. The
 * [noCover] painter stands in while it loads and when there's none.
 */
@Composable
fun SongCoverCircle(cover: SongCover, noCover: Painter, modifier: Modifier = Modifier) {
    AsyncImage(
        model = cover,
        contentDescription = null,
        modifier = modifier.size(RowCircleSize).clip(CircleShape),
        placeholder = noCover,
        error = noCover,
        contentScale = ContentScale.Crop,
    )
}

/**
 * The [icon] in a list row's circle, while a cover loads and for songs without one: the music note,
 * or e.g. a person for artists. A list makes it once for all its rows, and it stays the one
 * painter: when the colors change, it takes them on, so rows already showing it change too.
 */
@Composable
fun rememberRowNoCoverPainter(@DrawableRes icon: Int = R.drawable.ic_music_note): Painter {
    val note = painterResource(icon)
    val colors = MaterialTheme.colorScheme
    val painter = remember {
        NoCoverPainter(note, SolidColor(colors.secondaryContainer), colors.onSecondaryContainer, RowCircleIconSize)
    }
    SideEffect {
        painter.note = note
        painter.background = SolidColor(colors.secondaryContainer)
        painter.noteColor = colors.onSecondaryContainer
    }
    return painter
}
