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
 * An icon in a filled circle. List rows lead with the default 32dp circle holding an 18dp icon;
 * song rows show their cover in the same 32dp circle, so every list row in the app lines up
 * the same way.
 */
@Composable
fun IconCircle(
    @DrawableRes icon: Int,
    background: Color,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = 32.dp,
    iconSize: Dp = 18.dp,
) {
    Box(
        modifier = modifier.size(size).background(background, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(iconSize), tint = tint)
    }
}
