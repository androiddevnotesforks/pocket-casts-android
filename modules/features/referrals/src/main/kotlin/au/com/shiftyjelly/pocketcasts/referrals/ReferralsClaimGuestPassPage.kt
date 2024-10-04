package au.com.shiftyjelly.pocketcasts.referrals

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
import androidx.compose.material.Snackbar
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.TextButton
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import au.com.shiftyjelly.pocketcasts.compose.AppTheme
import au.com.shiftyjelly.pocketcasts.compose.CallOnce
import au.com.shiftyjelly.pocketcasts.compose.Devices
import au.com.shiftyjelly.pocketcasts.compose.buttons.GradientRowButton
import au.com.shiftyjelly.pocketcasts.compose.components.TextH10
import au.com.shiftyjelly.pocketcasts.compose.components.TextH50
import au.com.shiftyjelly.pocketcasts.compose.components.TextP30
import au.com.shiftyjelly.pocketcasts.compose.components.TextP60
import au.com.shiftyjelly.pocketcasts.compose.extensions.plusBackgroundBrush
import au.com.shiftyjelly.pocketcasts.compose.images.SubscriptionBadge
import au.com.shiftyjelly.pocketcasts.compose.loading.LoadingView
import au.com.shiftyjelly.pocketcasts.models.type.ReferralsOfferInfo
import au.com.shiftyjelly.pocketcasts.models.type.ReferralsOfferInfoMock
import au.com.shiftyjelly.pocketcasts.referrals.ReferralPageDefaults.pageCornerRadius
import au.com.shiftyjelly.pocketcasts.referrals.ReferralPageDefaults.pageWidthPercent
import au.com.shiftyjelly.pocketcasts.referrals.ReferralPageDefaults.shouldShowFullScreen
import au.com.shiftyjelly.pocketcasts.referrals.ReferralsClaimGuestPassViewModel.NavigationEvent
import au.com.shiftyjelly.pocketcasts.referrals.ReferralsClaimGuestPassViewModel.ReferralsClaimGuestPassError
import au.com.shiftyjelly.pocketcasts.referrals.ReferralsClaimGuestPassViewModel.SnackbarEvent
import au.com.shiftyjelly.pocketcasts.referrals.ReferralsClaimGuestPassViewModel.UiState
import au.com.shiftyjelly.pocketcasts.referrals.ReferralsGuestPassFragment.ReferralsPageType
import au.com.shiftyjelly.pocketcasts.settings.onboarding.OnboardingFlow
import au.com.shiftyjelly.pocketcasts.settings.onboarding.OnboardingLauncher.Companion.openOnboardingFlow
import au.com.shiftyjelly.pocketcasts.ui.helper.FragmentHostListener
import au.com.shiftyjelly.pocketcasts.ui.theme.Theme
import au.com.shiftyjelly.pocketcasts.utils.extensions.getActivity
import au.com.shiftyjelly.pocketcasts.images.R as IR
import au.com.shiftyjelly.pocketcasts.localization.R as LR

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun ReferralsClaimGuestPassPage(
    onDismiss: () -> Unit,
    viewModel: ReferralsClaimGuestPassViewModel = hiltViewModel(),
) {
    AppTheme(Theme.ThemeType.DARK) {
        val context = LocalContext.current
        val windowSize = calculateWindowSizeClass(context.getActivity() as Activity)
        val state by viewModel.state.collectAsStateWithLifecycle()
        val activity = LocalContext.current.getActivity()
        val snackbarHostState = remember { SnackbarHostState() }

        CallOnce {
            viewModel.onShown()
        }

        ReferralsClaimGuestPassContent(
            windowWidthSizeClass = windowSize.widthSizeClass,
            windowHeightSizeClass = windowSize.heightSizeClass,
            state = state,
            onDismiss = onDismiss,
            onActivatePassClick = viewModel::onActivatePassClick,
            onRetry = viewModel::retry,
            snackbarHostState = snackbarHostState,
        )

        LaunchedEffect(Unit) {
            viewModel.navigationEvent.collect { navigationEvent ->
                when (navigationEvent) {
                    NavigationEvent.InValidOffer -> {
                        val fragment = ReferralsGuestPassFragment.newInstance(ReferralsPageType.InvalidOffer)
                        (activity as FragmentHostListener).showBottomSheet(fragment)
                    }

                    NavigationEvent.LoginOrSignup -> openOnboardingFlow(
                        activity = activity,
                        onboardingFlow = OnboardingFlow.ReferralLoginOrSignUp,
                    )

                    is NavigationEvent.LaunchBillingFlow -> {
                        activity?.let {
                            viewModel.launchBillingFlow(
                                activity = activity,
                                subscriptionWithOffer = navigationEvent.subscriptionWithOffer,
                            )
                        }
                    }

                    NavigationEvent.Close -> { onDismiss() }
                }
            }
        }

        LaunchedEffect(Unit) {
            viewModel.snackBarEvent.collect { snackBarEvent ->
                val text = when (snackBarEvent) {
                    SnackbarEvent.NoNetwork -> context.getString(LR.string.error_no_network)
                    SnackbarEvent.PurchaseFailed -> context.getString(LR.string.referrals_create_subscription_failed)
                    SnackbarEvent.RedeemFailed -> context.getString(LR.string.referrals_redeem_code_failed)
                }
                snackbarHostState.showSnackbar(text)
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                // Fragment will remain on orientation changes
                val fragmentRemoved = activity?.supportFragmentManager?.findFragmentByTag(ReferralsGuestPassFragment::class.java.name) == null
                if (fragmentRemoved) viewModel.onDispose()
            }
        }
    }
}

@Composable
private fun ReferralsClaimGuestPassContent(
    windowWidthSizeClass: WindowWidthSizeClass,
    windowHeightSizeClass: WindowHeightSizeClass,
    state: UiState,
    onDismiss: () -> Unit,
    onActivatePassClick: () -> Unit,
    onRetry: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .background(Color.Transparent)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onDismiss,
            )
            .fillMaxSize(),
    ) {
        val showFullScreen = shouldShowFullScreen(windowWidthSizeClass, windowHeightSizeClass)
        val pageWidth = if (showFullScreen) maxWidth else (maxWidth.value * pageWidthPercent).dp
        val pageModifier = if (showFullScreen) {
            Modifier
                .fillMaxSize()
        } else {
            Modifier
                .width(pageWidth)
                .wrapContentSize()
        }

        Card(
            elevation = 8.dp,
            shape = RoundedCornerShape(pageCornerRadius(showFullScreen)),
            backgroundColor = Color.Black,
            modifier = pageModifier
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {},
                ),
        ) {
            when (state) {
                is UiState.Loading ->
                    LoadingView(color = Color.White)

                is UiState.Loaded -> {
                    ClaimGuestPassContent(
                        pageWidth = pageWidth,
                        windowHeightSizeClass = windowHeightSizeClass,
                        showFullScreen = showFullScreen,
                        referralsOfferInfo = state.referralsOfferInfo,
                        onDismiss = onDismiss,
                        onActivatePassClick = onActivatePassClick,
                    )

                    if (state.isLoading) {
                        LoadingView(color = Color.White)
                    }
                }

                is UiState.Error -> {
                    val errorMessage = when (state.error) {
                        ReferralsClaimGuestPassError.FailedToLoadOffer -> stringResource(LR.string.error_generic_message)
                    }
                    ReferralsGuestPassError(errorMessage, onRetry, onDismiss)
                }
            }
        }

        SnackbarHost(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            hostState = snackbarHostState,
            snackbar = { snackbarData ->
                Snackbar(
                    content = { TextH50(snackbarData.message, color = Color.Black) },
                    backgroundColor = Color.White,
                )
            },
        )
    }
}

@Composable
private fun ClaimGuestPassContent(
    pageWidth: Dp,
    windowHeightSizeClass: WindowHeightSizeClass,
    showFullScreen: Boolean,
    referralsOfferInfo: ReferralsOfferInfo,
    onDismiss: () -> Unit,
    onActivatePassClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        val language = Locale.current.language
        val titleTextResId = if (language == "en") {
            LR.string.referrals_claim_guest_pass_title_english_only
        } else {
            LR.string.referrals_claim_guest_pass_title
        }
        val price = referralsOfferInfo.localizedPriceAfterOffer

        TextButton(
            modifier = Modifier
                .align(Alignment.End),
            onClick = onDismiss,
        ) {
            TextP30(
                text = stringResource(LR.string.not_now),
            )
        }

        SubscriptionBadge(
            fontSize = 16.sp,
            padding = 4.dp,
            iconRes = IR.drawable.ic_plus,
            shortNameRes = LR.string.pocket_casts_plus_short,
            iconColor = Color.Black,
            backgroundBrush = plusBackgroundBrush,
            textColor = Color.Black,
        )

        Spacer(modifier = Modifier.height(24.dp))

        TextH10(
            text = stringResource(titleTextResId, referralsOfferInfo.localizedOfferDurationAdjective),
            textAlign = TextAlign.Center,
        )

        val guestPassCardWidth = pageWidth * 0.8f
        if (windowHeightSizeClass != WindowHeightSizeClass.Compact) {
            Spacer(modifier = Modifier.height(24.dp))

            val guestPassCardHeight = (guestPassCardWidth.value * ReferralGuestPassCardDefaults.cardAspectRatio).dp
            ReferralGuestPassCardView(
                modifier = Modifier
                    .size(guestPassCardWidth, guestPassCardHeight),
                source = ReferralGuestPassCardViewSource.Claim,
                referralsOfferInfo = referralsOfferInfo,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextP60(
            text = stringResource(LR.string.referrals_claim_guest_pass_description, price),
            textAlign = TextAlign.Center,
            modifier = Modifier.width(guestPassCardWidth),
        )

        if (showFullScreen) {
            Spacer(modifier = Modifier.weight(1f))
        } else {
            Spacer(modifier = Modifier.height(32.dp))
        }

        GradientRowButton(
            primaryText = stringResource(LR.string.referrals_activate_my_pass),
            textColor = Color.Black,
            gradientBackgroundColor = plusBackgroundBrush,
            modifier = Modifier.padding(16.dp),
            onClick = onActivatePassClick,
        )
    }
}

@Preview(device = Devices.PortraitRegular)
@Composable
fun ReferralsClaimGuestPassPortraitPhonePreview() {
    ReferralsClaimGuestPassContentPreview(
        windowWidthSizeClass = WindowWidthSizeClass.Compact,
        windowHeightSizeClass = WindowHeightSizeClass.Medium,
    )
}

@Preview(device = Devices.LandscapeRegular)
@Composable
fun ReferralsClaimGuestPassLandscapePhonePreview() {
    ReferralsClaimGuestPassContentPreview(
        windowWidthSizeClass = WindowWidthSizeClass.Compact,
        windowHeightSizeClass = WindowHeightSizeClass.Compact,
    )
}

@Preview(device = Devices.PortraitTablet)
@Composable
fun ReferralsClaimGuestPassPortraitTabletPreview() {
    ReferralsClaimGuestPassContentPreview(
        windowWidthSizeClass = WindowWidthSizeClass.Medium,
        windowHeightSizeClass = WindowHeightSizeClass.Medium,
    )
}

@Preview(device = Devices.LandscapeTablet)
@Composable
fun ReferralsClaimGuestPassLandscapeTabletPreview() {
    ReferralsClaimGuestPassContentPreview(
        windowWidthSizeClass = WindowWidthSizeClass.Medium,
        windowHeightSizeClass = WindowHeightSizeClass.Expanded,
    )
}

@Composable
fun ReferralsClaimGuestPassContentPreview(
    windowWidthSizeClass: WindowWidthSizeClass,
    windowHeightSizeClass: WindowHeightSizeClass,
) {
    AppTheme(Theme.ThemeType.DARK) {
        ReferralsClaimGuestPassContent(
            windowWidthSizeClass = windowWidthSizeClass,
            windowHeightSizeClass = windowHeightSizeClass,
            state = UiState.Loaded(ReferralsOfferInfoMock),
            onDismiss = {},
            onActivatePassClick = {},
            onRetry = {},
            snackbarHostState = SnackbarHostState(),
        )
    }
}
