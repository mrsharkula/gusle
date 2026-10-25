package com.sharkula.gusle.ui.songs

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.LocalReduceMotion
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.sharkula.gusle.R
import com.sharkula.gusle.ui.ambient.LocalAmbient
import com.sharkula.gusle.ui.components.edgeTransformation
import com.sharkula.gusle.ui.covers.CoverArt
import com.sharkula.gusle.ui.nowplaying.Cover
import com.sharkula.gusle.ui.nowplaying.DesignScale
import com.sharkula.gusle.ui.nowplaying.DimmedCoverAlpha
import com.sharkula.gusle.ui.nowplaying.artistColor
import com.sharkula.gusle.ui.nowplaying.designText
import com.sharkula.gusle.ui.nowplaying.seeThroughLabelButtonColors
import java.text.BreakIterator

// What a song's or a playlist's actions screen has in common: layout B of designs/v2/SONG-ACTIONS.md,
// the cover behind, a title and the line under it, and the actions as see-through buttons, which
// scroll over the cover while it stays; and Gusle's question before deleting either.

/** A cover read after the screen opened comes up this gently, rather than all at once. */
private const val CoverFadeInMs = 250

/**
 * Around the title and the line under it: below the list's own top padding, the title starts 44 dp
 * down, as in the design, over the upper part of the cover; and the actions start a little below.
 */
private val HeadingPadding = PaddingValues(start = 30.dp, top = 21.dp, end = 30.dp, bottom = 10.dp)

/** The actions are this tall and at least this wide, with their labels this far from the ends. */
private val ActionHeight = 48.dp
private val ActionMinWidth = 150.dp
private val ActionPadding = PaddingValues(horizontal = 16.dp)
private val ActionIconSize = 20.dp
private val ActionIconGap = 7.dp

/** Where the actions start in an [ActionsScreen]'s list: after the title and the line under it. */
const val FirstActionIndex = 1

/**
 * What can be done to something, e.g. a song: over its cover ([art], null until it's read), dimmed,
 * or else a glow with [noCoverIcon], its [title] (two lines at most) and the [subtitle] under it,
 * read out together as a heading; then its [actions], e.g. [ActionButton]s, given the spec they
 * shrink toward the round edges by. In the screen's colors.
 */
@Composable
fun ActionsScreen(
    title: String,
    subtitle: String?,
    art: CoverArt?,
    listState: TransformingLazyColumnState,
    @DrawableRes noCoverIcon: Int = R.drawable.ic_music_note,
    actions: TransformingLazyColumnScope.(TransformationSpec) -> Unit,
) {
    val transformationSpec = rememberTransformationSpec()
    val reduceMotion = LocalReduceMotion.current
    val colors = MaterialTheme.colorScheme
    // Up at once when it's there in the first frame; faded in when it's read after.
    val coverShown = remember { Animatable(if (art != null) 1f else 0f) }
    LaunchedEffect(art != null) {
        if (art != null && coverShown.value < 1f) {
            if (reduceMotion) coverShown.snapTo(1f) else coverShown.animateTo(1f, tween(CoverFadeInMs))
        }
    }

    ScreenScaffold(scrollState = listState) { contentPadding ->
        // Behind the list, and staying put as it scrolls; its glow, without a cover, in this
        // screen's colors rather than the app theme's.
        if (art != null) {
            DesignScale {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Cover(
                        art.picture,
                        DimmedCoverAlpha,
                        Modifier.graphicsLayer { alpha = coverShown.value },
                        noCoverColors = colors,
                        noCoverIcon = noCoverIcon,
                    )
                }
            }
        }
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            // The one item before the actions ([FirstActionIndex]).
            item(key = "heading") {
                // Read out as one heading: "Bismarck, Sabaton".
                Column(
                    Modifier
                        .edgeTransformation(this, transformationSpec)
                        .fillMaxWidth()
                        .padding(HeadingPadding)
                        .semantics(mergeDescendants = true) { heading() },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        title,
                        color = colors.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = designText(16, FontWeight.SemiBold, shadowAlpha = 0.75f, lineHeight = 20.sp),
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            subtitle,
                            color = artistColor(),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = designText(12, FontWeight.Normal, shadowAlpha = 0.85f, lineHeight = 16.sp),
                        )
                    }
                }
            }
            actions(transformationSpec)
        }
    }
}

/**
 * One of the actions: a see-through button, like Now playing's play button, its [icon] (given the
 * modifier that sizes and places it) before its [label].
 */
@Composable
fun TransformingLazyColumnItemScope.ActionButton(
    label: String,
    onClick: () -> Unit,
    transformationSpec: TransformationSpec,
    modifier: Modifier = Modifier,
    icon: @Composable (Modifier) -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .transformedHeight(this, transformationSpec)
            .widthIn(min = ActionMinWidth)
            .heightIn(min = ActionHeight),
        colors = seeThroughLabelButtonColors(),
        contentPadding = ActionPadding,
        transformation = SurfaceTransformation(transformationSpec),
    ) {
        icon(Modifier.align(Alignment.CenterVertically).size(ActionIconSize))
        Spacer(Modifier.width(ActionIconGap))
        Text(label, modifier = Modifier.align(Alignment.CenterVertically), maxLines = 2, style = actionLabelStyle())
    }
}

/** An action's label, over the cover. */
@Composable
private fun actionLabelStyle() =
    designText(13, FontWeight.SemiBold, shadowAlpha = 0.6f, shadowBlur = 3.dp, lineHeight = 17.sp)

/**
 * Gusle's question before something is deleted, while [visible]: Wear's dialog, the [question] with
 * the [name] of what's deleted in it ([questionTitle]), the [text] under it if there's any, ✕
 * ([onDismiss]) and ✓ ([onConfirm]). The dialog's window is its own, so Gusle's always-on display
 * doesn't cover it: it hides meanwhile, and comes back as the wrist comes up.
 */
@Composable
fun DeleteQuestion(
    visible: Boolean,
    @StringRes question: Int,
    name: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    text: String? = null,
) {
    AlertDialog(
        visible = visible && !LocalAmbient.current.isAmbient,
        onDismissRequest = onDismiss,
        confirmButton = { AlertDialogDefaults.ConfirmButton(onClick = onConfirm) },
        // Whole, past the dialog's usual three lines, which would cut e.g. "from the watch?" short; a
        // long name is shortened instead.
        title = { Text(stringResource(question, questionTitle(name)), maxLines = Int.MAX_VALUE) },
        dismissButton = { AlertDialogDefaults.DismissButton(onClick = onDismiss) },
        icon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
        text = text?.let { { Text(it) } },
    )
}

/** A song's title or a playlist's name in Gusle's question: at most this many characters. */
private const val QuestionTitleMax = 40

/**
 * [title] as Gusle's question names it, e.g. a song's or a playlist's: as it is, or cut to
 * [QuestionTitleMax] characters with "…", so the question stays a few lines long. Cut between
 * characters as the reader sees them: never inside an emoji, a flag, or a letter with its accent.
 */
internal fun questionTitle(title: String): String {
    if (title.length <= QuestionTitleMax) return title
    val characters = BreakIterator.getCharacterInstance().apply { setText(title) }
    val end = characters.preceding(QuestionTitleMax)
    return title.substring(0, end.coerceAtLeast(0)).trimEnd() + "…"
}
