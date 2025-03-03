package au.com.shiftyjelly.pocketcasts.podcasts.view.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import au.com.shiftyjelly.pocketcasts.analytics.AnalyticsEvent
import au.com.shiftyjelly.pocketcasts.analytics.AnalyticsTracker
import au.com.shiftyjelly.pocketcasts.models.entity.SuggestedFolderDetails
import au.com.shiftyjelly.pocketcasts.models.type.PodcastsSortType
import au.com.shiftyjelly.pocketcasts.preferences.Settings
import au.com.shiftyjelly.pocketcasts.repositories.podcast.FolderManager
import au.com.shiftyjelly.pocketcasts.repositories.podcast.PodcastManager
import au.com.shiftyjelly.pocketcasts.repositories.podcast.SuggestedFoldersManager
import au.com.shiftyjelly.pocketcasts.utils.UUIDProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class SuggestedFoldersViewModel @Inject constructor(
    private val folderManager: FolderManager,
    private val suggestedFoldersManager: SuggestedFoldersManager,
    private val podcastManager: PodcastManager,
    private val settings: Settings,
    private val analyticsTracker: AnalyticsTracker,
    private val uuidProvider: UUIDProvider,
) : ViewModel() {

    private val _state = MutableStateFlow<FoldersState>(FoldersState.Idle)
    val state: StateFlow<FoldersState> = _state

    fun onShown() {
        analyticsTracker.track(AnalyticsEvent.SUGGESTED_FOLDERS_MODAL_SHOWN)
    }

    fun onDismissed() {
        analyticsTracker.track(AnalyticsEvent.SUGGESTED_FOLDERS_MODAL_DISMISSED)
    }

    fun onReplaceExistingFoldersShown() {
        analyticsTracker.track(AnalyticsEvent.SUGGESTED_FOLDERS_REPLACE_EXISTING_FOLDERS_MODAL_SHOWN)
        _state.value = FoldersState.Idle
    }

    fun onReplaceExistingFoldersTapped() {
        analyticsTracker.track(AnalyticsEvent.SUGGESTED_FOLDERS_REPLACE_FOLDERS_TAPPED)
    }

    fun onUseTheseFolders(folders: List<Folder>) {
        analyticsTracker.track(AnalyticsEvent.SUGGESTED_FOLDERS_MODAL_USE_THESE_FOLDERS_TAPPED)
        viewModelScope.launch {
            val currentFoldersCount = folderManager.countFolders()
            if (currentFoldersCount > 0) {
                _state.value = FoldersState.ShowConfirmationDialog
            } else {
                overrideFoldersWithSuggested(folders)
            }
        }
    }

    fun onCreateCustomFolders() {
        analyticsTracker.track(AnalyticsEvent.SUGGESTED_FOLDERS_MODAL_CREATE_CUSTOM_FOLDERS_TAPPED)
    }

    fun overrideFoldersWithSuggested(folders: List<Folder>) {
        _state.value = FoldersState.Creating
        viewModelScope.launch {
            val newFolders = folders.map {
                SuggestedFolderDetails(
                    uuid = uuidProvider.generateUUID().toString(),
                    name = it.name,
                    color = it.color,
                    podcastsSortType = settings.podcastsSortType.value,
                    podcasts = it.podcasts,
                )
            }
            settings.podcastsSortType.set(PodcastsSortType.NAME_A_TO_Z, updateModifiedAt = true)
            folderManager.overrideFoldersWithSuggested(newFolders)
            podcastManager.refreshPodcasts("suggested-folders")
            suggestedFoldersManager.deleteSuggestedFolders(folders.toSuggestedFolders())
            _state.value = FoldersState.Created
        }
    }

    fun onHowItWorksTapped() {
        analyticsTracker.track(AnalyticsEvent.SUGGESTED_FOLDERS_HOW_IT_WORKS_TAPPED)
    }

    fun onHowItWorksGotItTapped() {
        analyticsTracker.track(AnalyticsEvent.SUGGESTED_FOLDERS_HOW_IT_WORKS_GOT_IT_TAPPED)
    }

    sealed class FoldersState {
        data object Idle : FoldersState()
        data object Creating : FoldersState()
        data object Created : FoldersState()
        data object ShowConfirmationDialog : FoldersState()
    }
}
