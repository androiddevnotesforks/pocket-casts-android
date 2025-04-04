package au.com.shiftyjelly.pocketcasts.filters

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.toLiveData
import androidx.lifecycle.viewModelScope
import au.com.shiftyjelly.pocketcasts.analytics.AnalyticsEvent
import au.com.shiftyjelly.pocketcasts.analytics.AnalyticsTracker
import au.com.shiftyjelly.pocketcasts.models.entity.Playlist
import au.com.shiftyjelly.pocketcasts.preferences.Settings
import au.com.shiftyjelly.pocketcasts.repositories.playback.PlaybackManager
import au.com.shiftyjelly.pocketcasts.repositories.podcast.EpisodeManager
import au.com.shiftyjelly.pocketcasts.repositories.podcast.PlaylistManager
import au.com.shiftyjelly.pocketcasts.repositories.podcast.PlaylistManagerImpl.Companion.IN_PROGRESS_UUID
import au.com.shiftyjelly.pocketcasts.repositories.podcast.PlaylistManagerImpl.Companion.NEW_RELEASE_UUID
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Collections
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

@HiltViewModel
class FiltersFragmentViewModel @Inject constructor(
    val playlistManager: PlaylistManager,
    private val analyticsTracker: AnalyticsTracker,
    private val settings: Settings,
    private val episodeManager: EpisodeManager,
    private val playbackManager: PlaybackManager,
) : ViewModel(), CoroutineScope {

    companion object {
        private const val FILTER_COUNT_KEY = "filter_count"
    }

    var isFragmentChangingConfigurations: Boolean = false
        private set

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default

    val filters: LiveData<List<Playlist>> = playlistManager.findAllRxFlowable().toLiveData()

    val countGenerator = { playlist: Playlist ->
        playlistManager.countEpisodesRxFlowable(playlist, episodeManager, playbackManager).onErrorReturn { 0 }
    }

    var adapterState: MutableList<Playlist> = mutableListOf()
    fun movePlaylist(fromPosition: Int, toPosition: Int): List<Playlist> {
        if (fromPosition < toPosition) {
            for (index in fromPosition until toPosition) {
                Collections.swap(adapterState, index, index + 1)
            }
        } else {
            for (index in fromPosition downTo toPosition + 1) {
                Collections.swap(adapterState, index, index - 1)
            }
        }
        return adapterState.toList()
    }

    fun commitMoves(moved: Boolean) {
        val playlists = adapterState

        playlists.forEachIndexed { index, playlist ->
            playlist.sortPosition = index
            playlist.syncStatus = Playlist.SYNC_STATUS_NOT_SYNCED
        }

        runBlocking(Dispatchers.Default) {
            playlistManager.updateAllBlocking(playlists)
            if (moved) {
                analyticsTracker.track(AnalyticsEvent.FILTER_LIST_REORDERED)
            }
        }
    }

    fun onFragmentPause(isChangingConfigurations: Boolean?) {
        isFragmentChangingConfigurations = isChangingConfigurations ?: false
    }

    fun trackFilterListShown(filterCount: Int) {
        val properties = mapOf(FILTER_COUNT_KEY to filterCount)
        analyticsTracker.track(AnalyticsEvent.FILTER_LIST_SHOWN, properties)
    }

    fun findPlaylistByUuid(playlistUuid: String, onSuccess: (Playlist) -> Unit) {
        viewModelScope.launch {
            val playlist = playlistManager.findByUuid(playlistUuid) ?: return@launch
            onSuccess(playlist)
        }
    }

    fun trackOnCreateFilterTap() {
        analyticsTracker.track(AnalyticsEvent.FILTER_CREATE_BUTTON_TAPPED)
    }

    fun trackTooltipShown() {
        analyticsTracker.track(AnalyticsEvent.FILTER_TOOLTIP_SHOWN)
    }

    suspend fun shouldShowTooltip(filters: List<Playlist>): Boolean {
        if (!settings.showEmptyFiltersListTooltip.value) return false
        if (filters.size > 2) return false

        val requiredUuids = setOf(NEW_RELEASE_UUID, IN_PROGRESS_UUID)
        val filterUuids = filters.map { it.uuid }.toSet()

        if (filterUuids != requiredUuids) return false

        return withContext(Dispatchers.IO) {
            filters.all { playlist ->
                val episodeCount = playlistManager.countEpisodesBlocking(playlist.id, episodeManager, playbackManager)
                episodeCount == 0
            }
        }
    }

    fun onTooltipClosed() {
        settings.showEmptyFiltersListTooltip.set(false, updateModifiedAt = false)
        analyticsTracker.track(AnalyticsEvent.FILTER_TOOLTIP_CLOSED)
    }
}
