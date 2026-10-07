package com.sharkula.gusle.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonColors
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight

/**
 * A screen that is one scrolling list: a [title] header, items that shrink toward the round
 * edges, and an optional [edgeButton] hugging the bottom. [content] adds the items and gets
 * the spec to pass to each [ListButton].
 */
@Composable
fun ListScreen(
    title: String,
    edgeButton: (@Composable BoxScope.() -> Unit)? = null,
    content: TransformingLazyColumnScope.(TransformationSpec) -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val list: @Composable BoxScope.(PaddingValues) -> Unit = { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader(
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                ) {
                    Text(title)
                }
            }
            content(transformationSpec)
        }
    }

    if (edgeButton != null) {
        ScreenScaffold(scrollState = listState, edgeButton = edgeButton, content = list)
    } else {
        ScreenScaffold(scrollState = listState, content = list)
    }
}

/** Between a row's labels and what's at its end. */
private val TrailingGap = 10.dp

/**
 * A full-width row in a [ListScreen] that narrows toward the round edges like every other row,
 * given the [transformationSpec] the list hands its content. [trailing] goes at the row's end,
 * centered on its height, e.g. the playing song's equalizer.
 */
@Composable
fun TransformingLazyColumnItemScope.ListButton(
    onClick: () -> Unit,
    transformationSpec: TransformationSpec,
    label: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    secondaryLabel: (@Composable RowScope.() -> Unit)? = null,
    icon: (@Composable BoxScope.() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
) {
    // Button has no slot at the end. Its single-slot form takes the icon and labels laid out as
    // Button lays them out itself, with the same padding and type, and then [trailing]. Every row
    // uses it, so one that gains or loses [trailing] isn't made anew.
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
        enabled = enabled,
        colors = colors,
        transformation = SurfaceTransformation(transformationSpec),
    ) {
        ButtonDefaults.Content(
            modifier = Modifier.weight(1f),
            secondaryLabel = secondaryLabel,
            icon = icon,
            enabled = enabled,
            colors = colors,
            label = label,
        )
        if (trailing != null) {
            Spacer(Modifier.width(TrailingGap))
            trailing()
        }
    }
}
