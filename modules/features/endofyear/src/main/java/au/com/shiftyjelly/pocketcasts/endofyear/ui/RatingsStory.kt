package au.com.shiftyjelly.pocketcasts.endofyear.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntOffset
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import au.com.shiftyjelly.pocketcasts.compose.Devices
import au.com.shiftyjelly.pocketcasts.compose.components.ScrollDirection
import au.com.shiftyjelly.pocketcasts.compose.components.ScrollingRow
import au.com.shiftyjelly.pocketcasts.compose.components.TextH10
import au.com.shiftyjelly.pocketcasts.compose.components.TextH20
import au.com.shiftyjelly.pocketcasts.compose.components.TextP40
import au.com.shiftyjelly.pocketcasts.compose.extensions.nonScaledSp
import au.com.shiftyjelly.pocketcasts.endofyear.Story
import au.com.shiftyjelly.pocketcasts.models.to.Rating
import au.com.shiftyjelly.pocketcasts.models.to.RatingStats
import kotlin.math.roundToLong
import kotlinx.coroutines.delay
import au.com.shiftyjelly.pocketcasts.localization.R as LR
import au.com.shiftyjelly.pocketcasts.ui.R as UR

@Composable
internal fun RatingsStory(
    story: Story.Ratings,
    measurements: EndOfYearMeasurements,
) = RatingsStory(story, measurements, showBars = false)

@Composable
private fun RatingsStory(
    story: Story.Ratings,
    measurements: EndOfYearMeasurements,
    showBars: Boolean,
) {
    val maxRatingCount = story.stats.max().second
    if (maxRatingCount != 0) {
        PresentRatings(story, measurements, showBars)
    } else {
        AbsentRatings(story, measurements)
    }
}

@Composable
private fun PresentRatings(
    story: Story.Ratings,
    measurements: EndOfYearMeasurements,
    showBars: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(story.backgroundColor)
            .padding(top = measurements.closeButtonBottomEdge + 100.dp),
    ) {
        BoxWithConstraints(
            contentAlignment = Alignment.BottomCenter,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 24.dp),
        ) {
            RatingBars(
                stats = story.stats,
                showBars = showBars,
            )
        }
        Column(
            modifier = Modifier.background(story.backgroundColor),
        ) {
            Spacer(
                modifier = Modifier.height(32.dp),
            )
            TextH10(
                text = stringResource(LR.string.eoy_story_ratings_title_1),
                disableScale = true,
                color = colorResource(UR.color.coolgrey_90),
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(
                modifier = Modifier.height(16.dp),
            )
            TextP40(
                text = when (val rating = story.stats.max().first) {
                    Rating.One, Rating.Two, Rating.Three -> stringResource(LR.string.eoy_story_ratings_subtitle_2)
                    Rating.Four, Rating.Five -> stringResource(LR.string.eoy_story_ratings_subtitle_1, rating.numericalValue)
                },
                fontSize = 15.sp,
                disableScale = true,
                color = colorResource(UR.color.coolgrey_90),
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            ShareStoryButton(onClick = {})
        }
    }
}

private val BarHeight = 1.5.dp
private val SpaceHeight = 4.dp
private val SectionHeight = BarHeight + SpaceHeight

@Composable
private fun BoxWithConstraintsScope.RatingBars(
    stats: RatingStats,
    showBars: Boolean,
) {
    // Measure text height to account for available space for rating lines
    val textMeasurer = rememberTextMeasurer()
    val fontSize = 22.nonScaledSp
    val ratingTextHeight = remember(maxHeight) {
        textMeasurer.measure(
            text = "1",
            style = TextStyle(
                fontSize = fontSize,
                lineHeight = 30.sp,
                fontWeight = FontWeight.W700,
            ),
        ).size.height.dp + 8.dp // Plus padding
    }
    val maxLineCount = (maxHeight - ratingTextHeight) / SectionHeight

    var show by remember { mutableStateOf(showBars) }
    LaunchedEffect(Unit) {
        delay(350)
        show = true
    }

    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        Rating.entries.forEach { rating ->
            RatingBar(
                rating = rating.numericalValue,
                lineCount = (maxLineCount * stats.relativeToMax(rating)).toInt().coerceAtLeast(1),
                textHeight = ratingTextHeight,
                show = show,
            )
        }
    }
}

@Composable
private fun RowScope.RatingBar(
    rating: Int,
    lineCount: Int,
    textHeight: Dp,
    show: Boolean,
) {
    val density = LocalDensity.current
    val linesHeight = SectionHeight * lineCount

    val transition = updateTransition(
        targetState = show,
        label = "bar-transition-$rating",
    )

    val textOffset by transition.animateIntOffset(
        label = "text-offset",
        transitionSpec = {
            spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = 50f,
                visibilityThreshold = IntOffset(1, 1),
            )
        },
        targetValueByState = { state ->
            when (state) {
                true -> IntOffset(0, 0)
                false -> IntOffset(0, density.run { textHeight.roundToPx() })
            }
        },
    )
    val textAlpha by transition.animateFloat(
        label = "text-alpha",
        transitionSpec = {
            spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = 50f,
            )
        },
        targetValueByState = { state ->
            when (state) {
                true -> 1f
                false -> 0f
            }
        },
    )
    val barOffset by transition.animateIntOffset(
        label = "bar-offset",
        transitionSpec = {
            spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = 50f,
                visibilityThreshold = IntOffset(1, 1),
            )
        },
        targetValueByState = { state ->
            when (state) {
                true -> IntOffset(0, 0)
                false -> IntOffset(0, density.run { (linesHeight * 1.1f).roundToPx() })
            }
        },
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.weight(1f),
    ) {
        TextH20(
            text = "$rating",
            disableScale = true,
            color = colorResource(UR.color.coolgrey_90),
            modifier = Modifier
                .offset { textOffset }
                .padding(bottom = 8.dp)
                .alpha(textAlpha),
        )
        repeat(lineCount) {
            Box(
                modifier = Modifier
                    .offset { barOffset }
                    .padding(top = SpaceHeight)
                    .fillMaxWidth()
                    .height(BarHeight)
                    .background(Color.Black),
            )
        }
    }
}

@Composable
private fun AbsentRatings(
    story: Story.Ratings,
    measurements: EndOfYearMeasurements,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(story.backgroundColor)
            .padding(top = measurements.closeButtonBottomEdge),
    ) {
        SubcomposeLayout { constraints ->
            val noRatingsInfo = subcompose("noRatingsInfo") {
                NoRatingsInfo(
                    story = story,
                )
            }[0].measure(constraints)
            val oopsiesSection = subcompose("oopsiesSection") {
                OopsiesSection(
                    measurements = measurements,
                )
            }[0].measure(constraints)

            val emptySpaceHeight = constraints.maxHeight - noRatingsInfo.height
            val oopsiesPosition = (emptySpaceHeight - oopsiesSection.height).coerceAtLeast(0) / 2

            layout(constraints.maxWidth, constraints.maxHeight) {
                oopsiesSection.place(0, oopsiesPosition)
                noRatingsInfo.place(0, emptySpaceHeight)
            }
        }
    }
}

@Composable
private fun OopsiesSection(
    measurements: EndOfYearMeasurements,
) {
    // Measure text to postion things better
    val textMeasurer = rememberTextMeasurer()
    val fontSize = 227.nonScaledSp
    val textMeasurement = remember {
        textMeasurer.measure(
            text = "OOOOPSIES",
            style = TextStyle(
                fontFamily = humaneFontFamily,
                fontSize = fontSize,
            ),
        )
    }

    Column(
        modifier = Modifier
            .offset(x = measurements.width / 2)
            .requiredWidth(measurements.width * 2),
    ) {
        OopsiesText(
            scrollDirection = ScrollDirection.Left,
            textMeasurement = textMeasurement,
        )
        OopsiesText(
            scrollDirection = ScrollDirection.Right,
            textMeasurement = textMeasurement,
        )
    }
}

@Composable
private fun OopsiesText(
    scrollDirection: ScrollDirection,
    textMeasurement: TextLayoutResult,
) {
    val textHeight = LocalDensity.current.run { textMeasurement.firstBaseline.toDp() } + 12.dp
    ScrollingRow(
        scrollDelay = { (20 / it.density).roundToLong().coerceAtLeast(4L) },
        items = listOf("OOOOPSIES"),
        scrollDirection = scrollDirection,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "OOOOPSIES",
                fontFamily = humaneFontFamily,
                fontSize = 227.nonScaledSp,
                maxLines = 1,
                modifier = Modifier.requiredHeight(textHeight),
            )
            Image(
                painter = painterResource(au.com.shiftyjelly.pocketcasts.images.R.drawable.eoy_star_text_stop),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colorResource(UR.color.coolgrey_90)),
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

@Composable
private fun NoRatingsInfo(
    story: Story.Ratings,
) {
    Column(
        modifier = Modifier.background(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                0.1f to story.backgroundColor,
            ),
        ),
    ) {
        Spacer(
            modifier = Modifier.height(16.dp),
        )
        TextH10(
            text = stringResource(LR.string.eoy_story_ratings_title_2),
            disableScale = true,
            color = colorResource(UR.color.coolgrey_90),
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(
            modifier = Modifier.height(16.dp),
        )
        TextP40(
            text = stringResource(LR.string.eoy_story_ratings_subtitle_3),
            fontSize = 15.sp,
            disableScale = true,
            color = colorResource(UR.color.coolgrey_90),
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        OutlinedEoyButton(
            text = stringResource(LR.string.eoy_story_ratings_learn_button_label),
            onClick = {},
        )
    }
}

@Preview(device = Devices.PortraitRegular)
@Composable
private fun RatingsHighPreview() {
    PreviewBox { measurements ->
        RatingsStory(
            story = Story.Ratings(
                stats = RatingStats(
                    ones = 20,
                    twos = 30,
                    threes = 0,
                    fours = 25,
                    fives = 130,
                ),
            ),
            measurements = measurements,
            showBars = true,
        )
    }
}

@Preview(device = Devices.PortraitRegular)
@Composable
private fun RatingsLowPreview() {
    PreviewBox { measurements ->
        RatingsStory(
            story = Story.Ratings(
                stats = RatingStats(
                    ones = 20,
                    twos = 50,
                    threes = 0,
                    fours = 25,
                    fives = 0,
                ),
            ),
            measurements = measurements,
            showBars = true,
        )
    }
}

@Preview(device = Devices.PortraitRegular)
@Composable
private fun RatingsNonePreview() {
    PreviewBox { measurements ->
        RatingsStory(
            story = Story.Ratings(
                stats = RatingStats(
                    ones = 0,
                    twos = 0,
                    threes = 0,
                    fours = 0,
                    fives = 0,
                ),
            ),
            measurements = measurements,
            showBars = true,
        )
    }
}
