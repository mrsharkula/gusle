package com.sharkula.gusle.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.sharkula.gusle.ui.theme.AppTheme

/**
 * An adaptive icon's layers are half as big again as the icon: launchers show their middle two
 * thirds, cut to the launcher's shape.
 */
private const val AdaptiveIconLayerScale = 1.5f

/**
 * Gusle's launcher icon in [theme]'s colors, [size] across, as the watch's launcher shows it: the
 * adaptive icon's foreground on its background color, cut to a circle. painterResource can't draw
 * the adaptive icon itself, so this draws its layers the way a launcher does. It's only a picture,
 * on About and leading the Theme screen's "Icon follows theme" row; the words beside it say what
 * it is.
 */
@Composable
fun LauncherIconPicture(theme: AppTheme, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(colorResource(theme.iconBackground)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painterResource(theme.iconForeground),
            contentDescription = null,
            modifier = Modifier.requiredSize(size * AdaptiveIconLayerScale),
        )
    }
}
