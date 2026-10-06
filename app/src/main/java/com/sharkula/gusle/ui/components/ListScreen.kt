package com.sharkula.gusle.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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

/**
 * A full-width row in a [ListScreen] that narrows toward the round edges like every other row,
 * given the [transformationSpec] the list hands its content.
 */
@Composable
fun TransformingLazyColumnItemScope.ListButton(
    onClick: () -> Unit,
    transformationSpec: TransformationSpec,
    label: @Composable RowScope.() -> Unit,
    secondaryLabel: (@Composable RowScope.() -> Unit)? = null,
    icon: (@Composable BoxScope.() -> Unit)? = null,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
        secondaryLabel = secondaryLabel,
        icon = icon,
        enabled = enabled,
        colors = colors,
        transformation = SurfaceTransformation(transformationSpec),
        label = label,
    )
}
