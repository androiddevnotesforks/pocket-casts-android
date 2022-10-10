package au.com.shiftyjelly.pocketcasts.filters

import androidx.lifecycle.LiveData
import androidx.lifecycle.LiveDataReactiveStreams
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import au.com.shiftyjelly.pocketcasts.models.entity.Episode
import au.com.shiftyjelly.pocketcasts.models.entity.Playlist
import au.com.shiftyjelly.pocketcasts.repositories.extensions.calculateCombinedIconId
import au.com.shiftyjelly.pocketcasts.repositories.playback.PlaybackManager
import au.com.shiftyjelly.pocketcasts.repositories.podcast.EpisodeManager
import au.com.shiftyjelly.pocketcasts.repositories.podcast.PlaylistManager
import au.com.shiftyjelly.pocketcasts.repositories.podcast.PlaylistProperty
import au.com.shiftyjelly.pocketcasts.repositories.podcast.PlaylistUpdateSource
import au.com.shiftyjelly.pocketcasts.repositories.podcast.UserPlaylistUpdate
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

@HiltViewModel
class CreateFilterViewModel @Inject constructor(val playlistManager: PlaylistManager, val episodeManager: EpisodeManager, val playbackManager: PlaybackManager) :
    ViewModel(),
    CoroutineScope {
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default

    private var hasBeenInitialised = false
    var isAutoDownloadSwitchInitialized = false
    lateinit var playlist: LiveData<Playlist>
    val lockedToFirstPage = MutableLiveData<Boolean>(true)

    suspend fun createFilter(name: String, iconId: Int, colorId: Int) =
        withContext(Dispatchers.IO) { playlistManager.createPlaylist(name, Playlist.calculateCombinedIconId(colorId, iconId), draft = true) }

    val filterName = MutableLiveData("")
    var iconId: Int = 0
    var colorIndex = MutableLiveData(0)

    var userChangedFilterName = false
    var userChangedIcon = false
    var userChangedColor = false

    fun saveNewFilterDetails() {
        val colorIndex = colorIndex.value ?: return
        launch {
            saveFilter(
                iconIndex = iconId,
                colorIndex = colorIndex,
                isCreatingNewFilter = true
            )
            withContext(Dispatchers.Main) {
                reset()
            }
        }
    }

    suspend fun saveFilter(
        iconIndex: Int,
        colorIndex: Int,
        isCreatingNewFilter: Boolean
    ) = withContext(Dispatchers.Default) {
        val playlist = playlist.value ?: return@withContext
        playlist.title = filterName.value ?: ""
        playlist.iconId = Playlist.calculateCombinedIconId(colorIndex, iconIndex)
        playlist.draft = false

        // If in filter creation flow a filter is not being updated by the user,
        // there are no user updated playlist properties
        val userPlaylistUpdate = if (!isCreatingNewFilter) {
            val properties = listOfNotNull(
                if (userChangedFilterName) PlaylistProperty.FilterName else null,
                if (userChangedIcon) PlaylistProperty.Icon else null,
                if (userChangedColor) PlaylistProperty.Color else null,
            )
            if (properties.isNotEmpty()) {
                UserPlaylistUpdate(properties, PlaylistUpdateSource.FILTER_OPTIONS)
            } else null
        } else null

        // Reset the property flags after they have been read when the update is sent to the PlaylistManager
        userChangedFilterName = false
        userChangedIcon = false
        userChangedColor = false

        playlistManager.update(playlist, userPlaylistUpdate)
    }

    fun updateAutodownload(autoDownload: Boolean) {
        launch {
            playlist.value?.let { playlist ->
                playlist.autoDownload = autoDownload
                val userPlaylistUpdate = if (isAutoDownloadSwitchInitialized) {
                    UserPlaylistUpdate(
                        listOf(PlaylistProperty.AutoDownload),
                        PlaylistUpdateSource.FILTER_EPISODE_LIST
                    )
                } else null
                playlistManager.update(playlist, userPlaylistUpdate)
            }
        }
    }

    suspend fun setup(playlistUUID: String?) {
        if (hasBeenInitialised) {
            return
        }

        playlist = if (playlistUUID != null) {
            LiveDataReactiveStreams.fromPublisher(playlistManager.findByUuidRx(playlistUUID).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).toFlowable())
        } else {
            val newFilter = createFilter("", 0, 0)
            LiveDataReactiveStreams.fromPublisher(playlistManager.observeByUuid(newFilter.uuid))
        }

        hasBeenInitialised = true
    }

    fun observeFilter(filter: Playlist): LiveData<List<Episode>> {
        return LiveDataReactiveStreams.fromPublisher(playlistManager.observeEpisodesPreview(filter, episodeManager, playbackManager))
    }

    fun updateDownloadLimit(limit: Int) {
        launch {
            val playlist = playlist.value ?: return@launch
            playlist.autodownloadLimit = limit

            val userPlaylistUpdate = UserPlaylistUpdate(
                listOf(PlaylistProperty.AutoDownloadLimit),
                PlaylistUpdateSource.FILTER_OPTIONS
            )
            playlistManager.update(playlist, userPlaylistUpdate)
        }
    }

    fun reset() {
        filterName.value = ""
        iconId = 0
        colorIndex.value = 0
        hasBeenInitialised = false
        lockedToFirstPage.value = true
    }

    fun clearNewFilter() {
        reset()
        launch(Dispatchers.Default) {
            val playlist = playlist.value ?: return@launch
            playlistManager.delete(playlist)
        }
    }

    fun starredChipTapped(isCreatingFilter: Boolean) {
        lockedToFirstPage.value = false
        launch {
            playlist.value?.let { playlist ->
                playlist.starred = !playlist.starred

                // Only indicate user is updating the starred property if this is not
                // the filter creation flow
                val userPlaylistUpdate = if (!isCreatingFilter) {
                    UserPlaylistUpdate(
                        listOf(PlaylistProperty.Starred),
                        PlaylistUpdateSource.FILTER_EPISODE_LIST
                    )
                } else null
                playlistManager.update(playlist, userPlaylistUpdate)
            }
        }
    }

    fun userChangedFilterName() {
        userChangedFilterName = true
    }

    fun userChangedIcon() {
        userChangedIcon = true
    }

    fun userChangedColor() {
        userChangedColor = true
    }
}
