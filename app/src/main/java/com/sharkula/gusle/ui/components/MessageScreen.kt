package com.sharkula.gusle.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec

/**
 * The edge button of a screen's one action, e.g. a [MessageScreen]'s fix, or New playlist: its
 * [label], after an optional [icon] and [iconGap].
 */
@Composable
fun ActionEdgeButton(label: String, onClick: () -> Unit, icon: (@Composable () -> Unit)? = null, iconGap: Dp = 6.dp) {
    EdgeButton(onClick = onClick, buttonSize = EdgeButtonSize.Small) {
        if (icon != null) {
            icon()
            Spacer(Modifier.width(iconGap))
        }
        // Smaller than the default label, so "Open settings" fits on one line at the default font
        // size. Larger fonts wrap it onto two lines, which still fit.
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

/**
 * A screen that says what's wrong and offers the fix: an [icon] in a circle, a short [title],
 * one line of [text], and the [action] in the edge button at the bottom, after its optional
 * [actionIcon] and [actionIconGap]. It scrolls when a large font doesn't fit.
 */
@Composable
fun MessageScreen(
    @DrawableRes icon: Int,
    title: String,
    text: String,
    action: String,
    onAction: () -> Unit,
    iconBackground: Color = MaterialTheme.colorScheme.secondaryContainer,
    iconTint: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    actionIcon: (@Composable () -> Unit)? = null,
    actionIconGap: Dp = 6.dp,
) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    ScreenScaffold(
        scrollState = listState,
        edgeButton = { ActionEdgeButton(action, onAction, actionIcon, actionIconGap) },
    ) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                IconCircle(
                    icon = icon,
                    background = iconBackground,
                    modifier = Modifier.edgeTransformation(this, transformationSpec).padding(top = 8.dp),
                    tint = iconTint,
                    size = 44.dp,
                    iconSize = 24.dp,
                )
            }
            // The text narrows toward the round edge: the title less, the longer line more.
            item {
                Text(
                    title,
                    modifier = Modifier
                        .edgeTransformation(this, transformationSpec)
                        .fillMaxWidth(0.9f)
                        .padding(top = 4.dp)
                        .semantics { heading() },
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            item {
                Text(
                    text,
                    modifier = Modifier.edgeTransformation(this, transformationSpec).fillMaxWidth(0.8f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
