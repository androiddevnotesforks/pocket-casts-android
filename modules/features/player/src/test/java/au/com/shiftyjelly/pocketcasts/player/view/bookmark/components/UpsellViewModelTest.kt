package au.com.shiftyjelly.pocketcasts.player.view.bookmark.components

import au.com.shiftyjelly.pocketcasts.analytics.AnalyticsTracker
import au.com.shiftyjelly.pocketcasts.models.type.SubscriptionTier
import au.com.shiftyjelly.pocketcasts.repositories.subscription.FreeTrial
import au.com.shiftyjelly.pocketcasts.repositories.subscription.SubscriptionManager
import au.com.shiftyjelly.pocketcasts.sharedtest.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class UpsellViewModelTest {
    @get:Rule
    val coroutineRule = MainCoroutineRule()

    @Mock
    private lateinit var subscriptionManager: SubscriptionManager

    private lateinit var upsellViewModel: UpsellViewModel

    @Test
    fun `given a trial for subscription PLUS, early access message not shown`() {
        whenever(subscriptionManager.freeTrialForSubscriptionTierFlow(SubscriptionTier.PLUS))
            .thenReturn(flowOf(FreeTrial(subscriptionTier = SubscriptionTier.PLUS)))

        upsellViewModel = UpsellViewModel(
            analyticsTracker = AnalyticsTracker.test(),
            subscriptionManager = subscriptionManager,
        )

        val state = upsellViewModel.state.value as UpsellViewModel.UiState.Loaded

        assertFalse(state.showEarlyAccessMessage)
        assertEquals(SubscriptionTier.PLUS, state.freeTrial.subscriptionTier)
    }
}
