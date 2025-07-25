package au.com.shiftyjelly.pocketcasts.reimagine.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import au.com.shiftyjelly.pocketcasts.compose.components.PagerDotIndicator
import au.com.shiftyjelly.pocketcasts.compose.components.TextH30
import au.com.shiftyjelly.pocketcasts.compose.components.TextH40
import au.com.shiftyjelly.pocketcasts.sharing.CardType
import au.com.shiftyjelly.pocketcasts.sharing.SocialPlatform
import au.com.shiftyjelly.pocketcasts.sharing.VisualCardType

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun VerticalSharePage(
    shareTitle: String,
    shareDescription: String,
    shareColors: ShareColors,
    socialPlatforms: Set<SocialPlatform>,
    snackbarHostState: SnackbarHostState,
    onClose: () -> Unit,
    onShareToPlatform: (SocialPlatform, VisualCardType) -> Unit,
    middleContent: @Composable (VisualCardType, DpSize, Modifier) -> Unit,
) {
    Box(
        modifier = Modifier
            .background(shareColors.background)
            .fillMaxSize(),
    ) {
        Column {
            val pagerState = rememberPagerState(pageCount = { CardType.visualEntries.size })
            val scrollState = rememberScrollState()
            val selectedCard = CardType.visualEntries[pagerState.currentPage]
            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .scrollBottomFade(scrollState)
                    .nestedScroll(rememberNestedScrollInteropConnection())
                    .verticalScroll(scrollState),
            ) {
                var topContentHeight by remember { mutableIntStateOf(0) }
                TopContent(
                    shareTitle = shareTitle,
                    shareDescription = shareDescription,
                    shareColors = shareColors,
                    modifier = Modifier.onGloballyPositioned { coordinates -> topContentHeight = coordinates.size.height },
                )
                MiddleContent(
                    shareColors = shareColors,
                    middleContent = middleContent,
                    pagerState = pagerState,
                    scrollState = scrollState,
                    topContentHeight = topContentHeight,
                )
            }
            BottomContent(
                shareColors = shareColors,
                platforms = socialPlatforms,
                selectedCard = selectedCard,
                onShareToPlatform = onShareToPlatform,
            )
        }
        CloseButton(
            shareColors = shareColors,
            onClick = onClose,
            modifier = Modifier
                .padding(top = 12.dp, end = 12.dp)
                .align(Alignment.TopEnd),
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
            snackbar = { data -> SharingThemedSnackbar(data, shareColors) },
        )
    }
}

@Composable
private fun TopContent(
    shareTitle: String,
    shareDescription: String,
    shareColors: ShareColors,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxWidth(),
    ) {
        Spacer(
            modifier = Modifier.height(24.dp),
        )
        TextH30(
            text = shareTitle,
            textAlign = TextAlign.Center,
            color = shareColors.onBackgroundPrimary,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(
            modifier = Modifier.height(8.dp),
        )
        TextH40(
            text = shareDescription,
            textAlign = TextAlign.Center,
            color = shareColors.onBackgroundSecondary,
            modifier = Modifier.sizeIn(maxWidth = 220.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ColumnScope.MiddleContent(
    shareColors: ShareColors,
    scrollState: ScrollState,
    pagerState: PagerState,
    topContentHeight: Int,
    middleContent: @Composable (VisualCardType, DpSize, Modifier) -> Unit,
) {
    var indicatorHeight by remember { mutableIntStateOf(0) }
    Row(
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .onGloballyPositioned { coordinates -> indicatorHeight = coordinates.size.height }
            .fillMaxWidth()
            .padding(vertical = 24.dp),
    ) {
        PagerDotIndicator(
            state = pagerState,
            activeDotColor = shareColors.onBackgroundPrimary,
            inactiveDotColor = shareColors.onBackgroundSecondary,
        )
    }

    val coordiantes = CardCoordinates.estimate(
        topContentHeight = topContentHeight + indicatorHeight,
        scrollState = scrollState,
    )
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.height(coordiantes.size.height),
    ) { pageIndex ->
        val cardType = CardType.visualEntries[pageIndex]
        val modifier = Modifier
            .offset { coordiantes.offset(cardType) }
            .fillMaxSize()
            .padding(coordiantes.padding)
        middleContent(cardType, coordiantes.size, modifier)
    }
}

@Composable
private fun BottomContent(
    shareColors: ShareColors,
    platforms: Set<SocialPlatform>,
    selectedCard: VisualCardType,
    onShareToPlatform: (SocialPlatform, VisualCardType) -> Unit,
) {
    Box(
        modifier = Modifier.padding(vertical = 24.dp),
    ) {
        PlatformBar(
            platforms = platforms,
            shareColors = shareColors,
            onClick = { platform ->
                onShareToPlatform(platform, selectedCard)
            },
        )
    }
}

@Composable
internal fun HorizontalSharePage(
    shareTitle: String,
    shareDescription: String,
    shareColors: ShareColors,
    socialPlatforms: Set<SocialPlatform>,
    snackbarHostState: SnackbarHostState,
    onClose: () -> Unit,
    onShareToPlatform: (SocialPlatform, VisualCardType) -> Unit,
    middleContent: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .background(shareColors.background)
            .fillMaxSize(),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            ) {
                TextH30(
                    text = shareTitle,
                    textAlign = TextAlign.Center,
                    color = shareColors.onBackgroundPrimary,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(
                    modifier = Modifier.height(8.dp),
                )
                TextH40(
                    text = shareDescription,
                    textAlign = TextAlign.Center,
                    color = shareColors.onBackgroundSecondary,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxSize(),
            ) {
                Spacer(
                    modifier = Modifier.weight(0.1f),
                )
                Box(
                    contentAlignment = Alignment.Center,
                    content = middleContent,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                )
                Spacer(
                    modifier = Modifier.weight(0.05f),
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    PlatformBar(
                        platforms = socialPlatforms,
                        shareColors = shareColors,
                        onClick = { platform ->
                            onShareToPlatform(platform, CardType.Horizontal)
                        },
                    )
                }
                Spacer(
                    modifier = Modifier.weight(0.1f),
                )
            }
        }
        CloseButton(
            shareColors = shareColors,
            onClick = onClose,
            modifier = Modifier
                .padding(top = 12.dp, end = 12.dp)
                .align(Alignment.TopEnd),
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
            snackbar = { data -> SharingThemedSnackbar(data, shareColors) },
        )
    }
}
