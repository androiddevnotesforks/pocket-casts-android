package au.com.shiftyjelly.pocketcasts.compose.folder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import au.com.shiftyjelly.pocketcasts.compose.AppThemeWithBackground
import au.com.shiftyjelly.pocketcasts.compose.components.PodcastImage
import au.com.shiftyjelly.pocketcasts.compose.extensions.nonScaledSp
import au.com.shiftyjelly.pocketcasts.compose.images.CountBadge
import au.com.shiftyjelly.pocketcasts.compose.images.CountBadgeStyle
import au.com.shiftyjelly.pocketcasts.preferences.model.BadgeType
import au.com.shiftyjelly.pocketcasts.ui.theme.Theme
import com.airbnb.android.showkase.annotation.ShowkaseComposable

private val gradientTop = Color(0x00000000)
private val gradientBottom = Color(0x33000000)
private val topPodcastImageGradient = listOf(Color(0x00000000), Color(0x16000000))
private val bottomPodcastImageGradient = listOf(Color(0x16000000), Color(0x33000000))
private const val PADDING_IMAGE_RATIO = 4f / 120f
private const val IMAGE_SIZE_RATIO = 38f / 120f

@Composable
fun FolderImage(
    name: String,
    color: Color,
    podcastUuids: List<String>,
    textSpacing: Boolean,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 11.nonScaledSp,
    badgeCount: Int = 0,
    badgeType: BadgeType = BadgeType.OFF,
) {
    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier,
    ) {
        val constraints = this
        Card(
            elevation = 1.dp,
            shape = RoundedCornerShape(4.dp),
            backgroundColor = color,
            modifier = Modifier
                .fillMaxSize()
                .aspectRatio(1f),
        ) {
            Box(
                modifier = Modifier
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                gradientTop,
                                gradientBottom,
                            ),
                        ),
                    )
                    .fillMaxSize(),
            ) {}
            val podcastSize = (constraints.maxWidth.value * IMAGE_SIZE_RATIO).dp
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                val imagePadding = (constraints.maxWidth.value * PADDING_IMAGE_RATIO).dp
                Spacer(modifier = Modifier.height(imagePadding))
                Row(horizontalArrangement = Arrangement.Center) {
                    Column(horizontalAlignment = Alignment.End) {
                        FolderPodcastImage(
                            uuid = podcastUuids.getOrNull(0),
                            color = color,
                            gradientColor = topPodcastImageGradient,
                            modifier = Modifier.size(podcastSize),
                        )
                        Spacer(modifier = Modifier.height(imagePadding))
                        FolderPodcastImage(
                            uuid = podcastUuids.getOrNull(2),
                            color = color,
                            gradientColor = bottomPodcastImageGradient,
                            modifier = Modifier.size(podcastSize),
                        )
                    }
                    Spacer(modifier = Modifier.width(imagePadding))
                    Column(horizontalAlignment = Alignment.Start) {
                        FolderPodcastImage(
                            uuid = podcastUuids.getOrNull(1),
                            color = color,
                            gradientColor = topPodcastImageGradient,
                            modifier = Modifier.size(podcastSize),
                        )
                        Spacer(modifier = Modifier.height(imagePadding))
                        FolderPodcastImage(
                            uuid = podcastUuids.getOrNull(3),
                            color = color,
                            gradientColor = bottomPodcastImageGradient,
                            modifier = Modifier.size(podcastSize),
                        )
                    }
                }
                if (name.isNotBlank()) {
                    if (textSpacing) {
                        Spacer(modifier = Modifier.height(imagePadding * 2))
                    }
                    Text(
                        text = name,
                        color = Color.White,
                        fontSize = fontSize,
                        fontWeight = FontWeight.W700,
                        letterSpacing = 0.25.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = LocalTextStyle.current.copy(
                            shadow = Shadow(
                                color = Color(0x33000000),
                                offset = Offset(0f, 2f),
                                blurRadius = 4f,
                            ),
                            platformStyle = PlatformTextStyle(
                                includeFontPadding = false,
                            ),
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.Both,
                            ),
                        ),
                        modifier = Modifier.padding(horizontal = 2.dp),
                    )
                }
            }
        }
        CountBadge(
            count = badgeCount,
            style = if (badgeType == BadgeType.LATEST_EPISODE) CountBadgeStyle.Small else CountBadgeStyle.Medium,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 6.dp, y = (-6).dp),
        )
    }
}

@Composable
private fun FolderPodcastImage(
    uuid: String?,
    color: Color,
    gradientColor: List<Color>,
    modifier: Modifier = Modifier,
) {
    if (uuid == null) {
        BoxWithConstraints(modifier) {
            val corners = when {
                maxWidth <= 50.dp -> 3.dp
                maxWidth <= 200.dp -> 4.dp
                else -> 8.dp
            }
            val elevation = when {
                maxWidth <= 50.dp -> 1.dp
                maxWidth <= 200.dp -> 2.dp
                else -> 4.dp
            }
            Card(
                elevation = elevation,
                shape = RoundedCornerShape(corners),
                backgroundColor = color,
            ) {
                Box(
                    modifier = Modifier
                        .size(maxWidth)
                        .background(brush = Brush.verticalGradient(colors = gradientColor)),
                ) {}
                Box(
                    modifier = Modifier
                        .size(maxWidth)
                        .background(color = Color(0x19000000)),
                ) {}
            }
        }
    } else {
        PodcastImage(
            uuid = uuid,
            modifier = modifier,
        )
    }
}

@ShowkaseComposable(name = "FolderImage", group = "Folder", styleName = "Light", defaultStyle = true)
@Preview(name = "Light")
@Composable
private fun FolderImageLightPreview() {
    AppThemeWithBackground(Theme.ThemeType.LIGHT) {
        FolderImagePreview()
    }
}

@ShowkaseComposable(name = "FolderImage", group = "Folder", styleName = "Dark")
@Preview(name = "Dark")
@Composable
private fun FolderImageDarkPreview() {
    AppThemeWithBackground(Theme.ThemeType.DARK) {
        FolderImagePreview()
    }
}

@Composable
private fun FolderImagePreview() {
    FolderImage(
        name = "Favourites",
        color = Color.Blue,
        podcastUuids = emptyList(),
        badgeCount = 1,
        badgeType = BadgeType.ALL_UNFINISHED,
        textSpacing = true,
        modifier = Modifier.size(100.dp),
    )
}
