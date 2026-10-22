package com.sharkula.gusle.ui.songs

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.LocalReduceMotion
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.tooling.preview.devices.WearDevices
import com.sharkula.gusle.R
import com.sharkula.gusle.data.Song
import com.sharkula.gusle.ui.components.edgeTransformation
import com.sharkula.gusle.ui.covers.CoverArt
import com.sharkula.gusle.ui.favorites.HeartIcon
import com.sharkula.gusle.ui.favorites.rememberHeartTap
import com.sharkula.gusle.ui.nowplaying.Cover
import com.sharkula.gusle.ui.nowplaying.DesignScale
import com.sharkula.gusle.ui.nowplaying.DimmedCoverAlpha
import com.sharkula.gusle.ui.nowplaying.artistColor
import com.sharkula.gusle.ui.nowplaying.designText
import com.sharkula.gusle.ui.nowplaying.rememberCoverPicture
import com.sharkula.gusle.ui.nowplaying.rememberStandInArt
import com.sharkula.gusle.ui.nowplaying.seeThroughLabelButtonColors
import com.sharkula.gusle.ui.theme.GusleTheme

// The song actions screen, layout B of designs/v2/SONG-ACTIONS.md: the song's cover behind, its title
// and artist, and its actions as see-through buttons, which scroll over the cover while it stays.

/** A cover read after the screen opened comes up this gently, rather than all at once. */
private const val CoverFadeInMs = 250

/**
 * Around the title and artist: below the list's own top padding, the title starts 44 dp down, as in
 * the design, over the upper part of the cover; and the actions start a little below the artist.
 */
private val SongPadding = PaddingValues(start = 30.dp, top = 21.dp, end = 30.dp, bottom = 10.dp)

/** The actions are this tall and at least this wide, with their labels this far from the ends. */
private val ActionHeight = 48.dp
private val ActionMinWidth = 150.dp
private val ActionPadding = PaddingValues(horizontal = 16.dp)
private val ActionIconSize = 20.dp
private val ActionIconGap = 7.dp

/**
 * What can be done to a [song], opened by holding it anywhere: over its cover, dimmed, its title and
 * artist, and its actions, for now one that adds it to the favorites or removes it ([isFavorite],
 * [onFavorite]). In the colors of the screen it came from, the playing song's. It stays open after a
 * tap, showing what it did.
 */
@Composable
fun SongActionsScreen(song: Song, isFavorite: Boolean, onFavorite: (favorite: Boolean) -> Unit) {
    SongActions(song.title, song.artist, rememberCoverPicture(song), isFavorite, onFavorite)
}

/** The screen as drawn in the design, given the song's [art] once it's read (null until then). */
@Composable
private fun SongActions(
    title: String,
    artist: String?,
    art: CoverArt?,
    isFavorite: Boolean,
    onFavorite: (favorite: Boolean) -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
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
        // Behind the list, and staying put as it scrolls; its glow, for a song without a cover, in
        // this screen's colors rather than the app theme's.
        if (art != null) {
            DesignScale {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Cover(art.picture, DimmedCoverAlpha, Modifier.graphicsLayer { alpha = coverShown.value }, noCoverColors = colors)
                }
            }
        }
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item(key = "song") {
                // Read out as one heading: "Bismarck, Sabaton".
                Column(
                    Modifier
                        .edgeTransformation(this, transformationSpec)
                        .fillMaxWidth()
                        .padding(SongPadding)
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
                    if (!artist.isNullOrBlank()) {
                        Text(
                            artist,
                            color = artistColor(),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = designText(12, FontWeight.Normal, shadowAlpha = 0.85f, lineHeight = 16.sp),
                        )
                    }
                }
            }
            item(key = "favorite") {
                val tap = rememberHeartTap()
                // See-through, like Now playing's play button.
                Button(
                    onClick = { tap.tap(adding = !isFavorite, onFavorite) },
                    modifier = Modifier
                        .transformedHeight(this, transformationSpec)
                        .widthIn(min = ActionMinWidth)
                        .heightIn(min = ActionHeight),
                    colors = seeThroughLabelButtonColors(),
                    contentPadding = ActionPadding,
                    transformation = SurfaceTransformation(transformationSpec),
                ) {
                    HeartIcon(
                        isFavorite,
                        tap,
                        outlineColor = Color.White,
                        modifier = Modifier.align(Alignment.CenterVertically).size(ActionIconSize),
                    )
                    Spacer(Modifier.width(ActionIconGap))
                    Text(
                        stringResource(if (isFavorite) R.string.remove_from_favorites else R.string.add_to_favorites),
                        modifier = Modifier.align(Alignment.CenterVertically),
                        maxLines = 2,
                        style = designText(13, FontWeight.SemiBold, shadowAlpha = 0.6f, shadowBlur = 3.dp, lineHeight = 17.sp),
                    )
                }
            }
        }
    }
}

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun SongActionsPreview() {
    SongActionsPreview(title = "River at Dawn", art = rememberStandInArt(), isFavorite = false)
}

/** A favorite with a long title and no cover. */
@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun SongActionsNoCoverPreview() {
    SongActionsPreview(
        title = "A Very Long Song Title That Runs Past the Edge",
        art = CoverArt(null, null, null),
        isFavorite = true,
    )
}

@Composable
private fun SongActionsPreview(title: String, art: CoverArt, isFavorite: Boolean) {
    GusleTheme {
        AppScaffold {
            SongActions(title, artist = "Test Ensemble", art, isFavorite, onFavorite = {})
        }
    }
}
