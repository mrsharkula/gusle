package com.sharkula.gusle.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.LocalContentColor

/**
 * List rows lead with a circle this size: an icon in it, or a song's cover, so every list row
 * in the app lines up the same way.
 */
val RowCircleSize = 32.dp

/** The icon in a list row's circle. */
val RowCircleIconSize = 18.dp

/** An icon in a filled circle; list-row size unless [size] says otherwise. */
@Composable
fun IconCircle(
    @DrawableRes icon: Int,
    background: Color,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = RowCircleSize,
    iconSize: Dp = RowCircleIconSize,
) {
    Box(
        modifier = modifier.size(size).background(background, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(iconSize), tint = tint)
    }
}
