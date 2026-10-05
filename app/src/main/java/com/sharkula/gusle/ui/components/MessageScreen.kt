package com.sharkula.gusle.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight

/**
 * A screen that says what's wrong and offers the fix: an [icon] in a circle, a short [title],
 * one line of [text], and the [action] in the edge button at the bottom, after its optional
 * [actionIcon]. It scrolls when a large font doesn't fit.
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
) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(onClick = onAction, buttonSize = EdgeButtonSize.Small) {
                if (actionIcon != null) {
                    actionIcon()
                    Spacer(Modifier.width(6.dp))
                }
                // Smaller than the default label, so "Open settings" fits on one line at the
                // default font size. Larger fonts wrap it onto two lines, which still fit.
                Text(action, style = MaterialTheme.typography.labelSmall)
            }
        },
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

/**
 * Shrinks and fades an item that has no surface of its own toward the round edges while the
 * list scrolls, the way list rows do.
 */
private fun Modifier.edgeTransformation(item: TransformingLazyColumnItemScope, spec: TransformationSpec) =
    transformedHeight(item, spec).graphicsLayer {
        with(item) { with(spec) { applyContainerTransformation(scrollProgress) } }
    }
