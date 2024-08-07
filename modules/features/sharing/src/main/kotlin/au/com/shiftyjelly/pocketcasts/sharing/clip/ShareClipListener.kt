package au.com.shiftyjelly.pocketcasts.sharing.clip

import android.widget.Toast
import androidx.compose.runtime.ExperimentalComposeApi
import au.com.shiftyjelly.pocketcasts.models.entity.Podcast
import au.com.shiftyjelly.pocketcasts.models.entity.PodcastEpisode
import au.com.shiftyjelly.pocketcasts.sharing.SharingClient
import au.com.shiftyjelly.pocketcasts.sharing.SharingRequest
import au.com.shiftyjelly.pocketcasts.sharing.ui.BackgroundAssetController
import au.com.shiftyjelly.pocketcasts.sharing.ui.CardType
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class ShareClipListener(
    private val fragment: ShareClipFragment,
    private val viewModel: ShareClipViewModel,
    private val sharingClient: SharingClient,
    private val assetController: BackgroundAssetController,
) : ShareClipPageListener {
    override suspend fun onShareClipLink(podcast: Podcast, episode: PodcastEpisode, clipRange: Clip.Range) {
        val request = SharingRequest.clipLink(podcast, episode, clipRange).build()
        val response = sharingClient.share(request)
        if (response.feedbackMessage != null) {
            Toast.makeText(fragment.requireContext(), response.feedbackMessage, Toast.LENGTH_SHORT).show()
        }
    }

    override suspend fun onShareClipAudio(podcast: Podcast, episode: PodcastEpisode, clipRange: Clip.Range) = coroutineScope {
        launch { delay(1.seconds) } // Launch a delay job to allow the loading animation to run even if clipping happens faster
        val request = SharingRequest.audioClip(podcast, episode, clipRange).build()
        val response = sharingClient.share(request)
        if (response.feedbackMessage != null) {
            Toast.makeText(fragment.requireContext(), response.feedbackMessage, Toast.LENGTH_SHORT).show()
        }
    }

    @OptIn(ExperimentalComposeApi::class)
    override suspend fun onShareClipVideo(podcast: Podcast, episode: PodcastEpisode, clipRange: Clip.Range) = coroutineScope {
        launch { delay(1.seconds) } // Launch a delay job to allow the loading animation to run even if clipping happens faster
        val backgroundImage = assetController.capture(CardType.Vertical).getOrNull()
        if (backgroundImage == null) {
            Toast.makeText(fragment.requireContext(), "Error", Toast.LENGTH_SHORT).show()
        } else {
            val request = SharingRequest.videoClip(podcast, episode, clipRange, backgroundImage).build()
            val response = sharingClient.share(request)
            if (response.feedbackMessage != null) {
                Toast.makeText(fragment.requireContext(), response.feedbackMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onClickPlay() {
        viewModel.playClip()
    }

    override fun onClickPause() {
        viewModel.pauseClip()
    }

    override fun onUpdateClipStart(duration: Duration) {
        viewModel.updateClipStart(duration)
    }

    override fun onUpdateClipEnd(duration: Duration) {
        viewModel.updateClipEnd(duration)
    }

    override fun onUpdateClipProgress(duration: Duration) {
        viewModel.updateClipProgress(duration)
    }

    override fun onUpdateTimeline(scale: Float, secondsPerTick: Int) {
        viewModel.updateProgressPollingPeriod(scale, secondsPerTick)
    }

    override fun onClose() {
        fragment.dismiss()
    }
}
