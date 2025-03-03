package au.com.shiftyjelly.pocketcasts.podcasts.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.toLiveData
import androidx.lifecycle.viewModelScope
import au.com.shiftyjelly.pocketcasts.analytics.AnalyticsEvent
import au.com.shiftyjelly.pocketcasts.analytics.AnalyticsTracker
import au.com.shiftyjelly.pocketcasts.models.entity.Folder
import au.com.shiftyjelly.pocketcasts.models.entity.Podcast
import au.com.shiftyjelly.pocketcasts.models.entity.SuggestedFolder
import au.com.shiftyjelly.pocketcasts.models.to.FolderItem
import au.com.shiftyjelly.pocketcasts.models.to.RefreshState
import au.com.shiftyjelly.pocketcasts.models.to.SignInState
import au.com.shiftyjelly.pocketcasts.models.type.PodcastsSortType
import au.com.shiftyjelly.pocketcasts.podcasts.view.folders.toFolders
import au.com.shiftyjelly.pocketcasts.preferences.Settings
import au.com.shiftyjelly.pocketcasts.preferences.model.BadgeType
import au.com.shiftyjelly.pocketcasts.repositories.podcast.EpisodeManager
import au.com.shiftyjelly.pocketcasts.repositories.podcast.FolderManager
import au.com.shiftyjelly.pocketcasts.repositories.podcast.PodcastManager
import au.com.shiftyjelly.pocketcasts.repositories.podcast.SuggestedFoldersManager
import au.com.shiftyjelly.pocketcasts.repositories.user.UserManager
import au.com.shiftyjelly.pocketcasts.utils.featureflag.Feature
import au.com.shiftyjelly.pocketcasts.utils.featureflag.FeatureFlag
import com.jakewharton.rxrelay2.BehaviorRelay
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.Flowable.combineLatest
import java.util.Collections
import java.util.Optional
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.rx2.asObservable
import timber.log.Timber
import au.com.shiftyjelly.pocketcasts.podcasts.view.folders.Folder as SuggestedFolderModel

@HiltViewModel
class PodcastsViewModel
@Inject constructor(
    private val podcastManager: PodcastManager,
    private val episodeManager: EpisodeManager,
    private val folderManager: FolderManager,
    private val settings: Settings,
    private val analyticsTracker: AnalyticsTracker,
    private val suggestedFoldersManager: SuggestedFoldersManager,
    userManager: UserManager,
) : ViewModel(), CoroutineScope {
    var isFragmentChangingConfigurations: Boolean = false
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default

    private val folderUuidObservable = BehaviorRelay.create<Optional<String>>().apply { accept(Optional.empty()) }

    data class FolderState(
        val folder: Folder?,
        val items: List<FolderItem>,
        val isSignedInAsPlusOrPatron: Boolean,
    )

    val folderState: LiveData<FolderState> = combineLatest(
        // monitor all subscribed podcasts, get the podcast in 'Episode release date' as the rest can be done in memory
        podcastManager.podcastsOrderByLatestEpisodeRxFlowable(),
        // monitor all the folders
        folderManager.observeFolders()
            .switchMap { folders ->
                if (folders.isEmpty()) {
                    Flowable.just(emptyList())
                } else {
                    // monitor the folder podcasts
                    val observeFolderPodcasts = folders.map { folder ->
                        podcastManager
                            .podcastsInFolderOrderByUserChoiceRxFlowable(folder)
                            .map { podcasts ->
                                FolderItem.Folder(
                                    folder = folder,
                                    podcasts = podcasts,
                                )
                            }
                    }
                    Flowable.zip(observeFolderPodcasts) { results ->
                        results.toList().filterIsInstance<FolderItem.Folder>()
                    }
                }
            },
        // monitor the folder uuid
        folderUuidObservable.toFlowable(BackpressureStrategy.LATEST),
        // monitor the home folder sort order
        settings.podcastsSortType.flow.asObservable(coroutineContext).toFlowable(BackpressureStrategy.LATEST),
        // show folders for Plus users
        userManager.getSignInState(),
    ) { podcasts, folders, folderUuidOptional, podcastSortOrder, signInState ->
        val folderUuid = folderUuidOptional.orElse(null)
        if (!signInState.isSignedInAsPlusOrPatron) {
            FolderState(
                items = buildPodcastItems(podcasts, podcastSortOrder),
                folder = null,
                isSignedInAsPlusOrPatron = false,
            )
        } else if (folderUuid == null) {
            FolderState(
                items = buildHomeFolderItems(podcasts, folders, podcastSortOrder),
                folder = null,
                isSignedInAsPlusOrPatron = true,
            )
        } else {
            val openFolder = folders.firstOrNull { it.uuid == folderUuid }
            if (openFolder == null) {
                FolderState(
                    items = emptyList(),
                    folder = null,
                    isSignedInAsPlusOrPatron = true,
                )
            } else {
                FolderState(
                    items = openFolder.podcasts.map { FolderItem.Podcast(it) },
                    folder = openFolder.folder,
                    isSignedInAsPlusOrPatron = true,
                )
            }
        }
    }
        .doOnNext { adapterState = it.items.toMutableList() }
        .toLiveData()

    val folder: Folder?
        get() = folderState.value?.folder

    private val _suggestedFoldersState = MutableStateFlow<SuggestedFoldersState>(SuggestedFoldersState.Idle)
    val suggestedFoldersState: SuggestedFoldersState
        get() = _suggestedFoldersState.value

    val userSuggestedFoldersState: Flow<Pair<SignInState, SuggestedFoldersState>> = userManager.getSignInState().asFlow()
        .combine(_suggestedFoldersState) { signIn, suggestedFolders ->
            Pair(signIn, suggestedFolders)
        }

    private fun buildHomeFolderItems(podcasts: List<Podcast>, folders: List<FolderItem>, podcastSortType: PodcastsSortType): List<FolderItem> {
        if (podcastSortType == PodcastsSortType.EPISODE_DATE_NEWEST_TO_OLDEST) {
            val folderUuids = folders.mapTo(mutableSetOf()) { it.uuid }
            val items = mutableListOf<FolderItem>()
            val uuidToFolder = folders.associateByTo(mutableMapOf(), FolderItem::uuid)
            for (podcast in podcasts) {
                if (podcast.folderUuid == null || !folderUuids.contains(podcast.folderUuid)) {
                    items.add(FolderItem.Podcast(podcast))
                } else {
                    // add the folder in the position of the podcast with the latest release date
                    val folder = uuidToFolder.remove(podcast.folderUuid)
                    if (folder != null) {
                        items.add(folder)
                    }
                }
            }
            if (uuidToFolder.isNotEmpty()) {
                items.addAll(uuidToFolder.values)
            }
            return items
        } else {
            val folderUuids = folders.map { it.uuid }.toHashSet()
            val items = podcasts
                // add the podcasts not in a folder or if the folder doesn't exist
                .filter { podcast -> podcast.folderUuid == null || !folderUuids.contains(podcast.folderUuid) }
                .map { FolderItem.Podcast(it) }
                .toMutableList<FolderItem>()
                // add the folders
                .apply { addAll(folders) }

            return items.sortedWith(podcastSortType.folderComparator)
        }
    }

    private fun buildPodcastItems(podcasts: List<Podcast>, podcastSortType: PodcastsSortType): List<FolderItem> {
        val items = podcasts.map { podcast -> FolderItem.Podcast(podcast) }
        return when (podcastSortType) {
            PodcastsSortType.EPISODE_DATE_NEWEST_TO_OLDEST -> items
            else -> items.sortedWith(podcastSortType.folderComparator)
        }
    }

    val podcastUuidToBadge: LiveData<Map<String, Int>> =
        settings.podcastBadgeType.flow
            .asObservable(coroutineContext)
            .toFlowable(BackpressureStrategy.LATEST)
            .switchMap { badgeType ->
                return@switchMap when (badgeType) {
                    BadgeType.ALL_UNFINISHED -> episodeManager.getPodcastUuidToBadgeUnfinishedRxFlowable()
                    BadgeType.LATEST_EPISODE -> episodeManager.getPodcastUuidToBadgeLatestRxFlowable()
                    else -> Flowable.just(emptyMap())
                }
            }.toLiveData()

    // We only want the current badge type when loading for this observable or else it will rebind the adapter every time the badge changes. We use take(1) for this.
    val layoutChangedLiveData = settings.podcastGridLayout.flow
        .combine(settings.podcastBadgeType.flow.take(1), ::Pair)
        .asObservable(coroutineContext)
        .toFlowable(BackpressureStrategy.LATEST)
        .toLiveData()

    val refreshObservable: LiveData<RefreshState> = settings.refreshStateObservable
        .toFlowable(BackpressureStrategy.LATEST)
        .toLiveData()

    private var adapterState: MutableList<FolderItem> = mutableListOf()

    /**
     * User rearranges the grid with a drag
     */
    fun moveFolderItem(fromPosition: Int, toPosition: Int): List<FolderItem> {
        if (adapterState.isEmpty()) {
            return adapterState
        }

        try {
            if (fromPosition < toPosition) {
                for (index in fromPosition until toPosition) {
                    Collections.swap(adapterState, index, index + 1)
                }
            } else {
                for (index in fromPosition downTo toPosition + 1) {
                    Collections.swap(adapterState, index, index - 1)
                }
            }
        } catch (ex: IndexOutOfBoundsException) {
            Timber.e("Move folder item failed: $ex")
        }

        return adapterState.toList()
    }

    fun commitMoves() {
        launch {
            saveSortOrder()
        }
    }

    fun refreshPodcasts() {
        analyticsTracker.track(
            AnalyticsEvent.PULLED_TO_REFRESH,
            mapOf("source" to "podcasts_list"),
        )
        podcastManager.refreshPodcasts("Pull down")
    }

    fun setFolderUuid(folderUuid: String?) {
        folderUuidObservable.accept(Optional.ofNullable(folderUuid))
    }

    fun isFolderOpen(): Boolean {
        return folderUuidObservable.value?.isPresent ?: false
    }

    private suspend fun saveSortOrder() {
        folderManager.updateSortPosition(adapterState)

        val folder = folder
        if (folder == null) {
            settings.podcastsSortType.set(PodcastsSortType.DRAG_DROP, updateModifiedAt = true)
        } else {
            folderManager.updateSortType(folderUuid = folder.uuid, podcastsSortType = PodcastsSortType.DRAG_DROP)
        }
    }

    fun updateFolderSort(uuid: String, podcastsSortType: PodcastsSortType) {
        launch {
            folderManager.updateSortType(folderUuid = uuid, podcastsSortType = podcastsSortType)
        }
    }

    fun onFragmentPause(isChangingConfigurations: Boolean?) {
        isFragmentChangingConfigurations = isChangingConfigurations ?: false
    }

    fun trackPodcastsListShown() {
        launch {
            val properties = HashMap<String, Any>()
            properties[NUMBER_OF_FOLDERS_KEY] = folderManager.countFolders()
            properties[NUMBER_OF_PODCASTS_KEY] = podcastManager.countSubscribed()
            properties[BADGE_TYPE_KEY] = settings.podcastBadgeType.value.analyticsValue
            properties[LAYOUT_KEY] = settings.podcastGridLayout.value.analyticsValue
            properties[SORT_ORDER_KEY] = settings.podcastsSortType.value.analyticsValue
            analyticsTracker.track(AnalyticsEvent.PODCASTS_LIST_SHOWN, properties)
        }
    }

    fun trackFolderShown(folderUuid: String) {
        launch {
            val properties = HashMap<String, Any>()
            properties[SORT_ORDER_KEY] = (folderManager.findByUuid(folderUuid)?.podcastsSortType ?: PodcastsSortType.DATE_ADDED_NEWEST_TO_OLDEST).analyticsValue
            properties[NUMBER_OF_PODCASTS_KEY] = folderManager.findFolderPodcastsSorted(folderUuid).size
            analyticsTracker.track(AnalyticsEvent.FOLDER_SHOWN, properties)
        }
    }

    @OptIn(FlowPreview::class)
    suspend fun loadSuggestedFolders() {
        if (FeatureFlag.isEnabled(Feature.SUGGESTED_FOLDERS)) {
            _suggestedFoldersState.emit(SuggestedFoldersState.Loading)
            suggestedFoldersManager.getSuggestedFolders()
                .debounce(200)
                .collect { folders ->
                    if (folders.isEmpty()) {
                        _suggestedFoldersState.emit(SuggestedFoldersState.Empty)
                    } else {
                        _suggestedFoldersState.emit(SuggestedFoldersState.Loaded(folders))
                    }
                }
        }
    }

    fun refreshSuggestedFolders() {
        viewModelScope.launch {
            if (FeatureFlag.isEnabled(Feature.SUGGESTED_FOLDERS)) {
                val uuids = podcastManager.findSubscribedUuids()
                suggestedFoldersManager.refreshSuggestedFolders(uuids)
            }
        }
    }

    suspend fun showSuggestedFoldersPaywallOnOpen(isSignedInAsPlusOrPatron: Boolean): Boolean {
        val uuids = podcastManager.findSubscribedUuids()
        return FeatureFlag.isEnabled(Feature.SUGGESTED_FOLDERS) &&
            !isSignedInAsPlusOrPatron &&
            settings.isEligibleToShowSuggestedFolderPaywall() &&
            uuids.size >= FOLLOWED_PODCASTS_THRESHOLD
    }

    sealed class SuggestedFoldersState {
        data object Idle : SuggestedFoldersState()
        data object Loading : SuggestedFoldersState()
        data class Loaded(private val folders: List<SuggestedFolder>) : SuggestedFoldersState() {
            private val convertedFolders: List<SuggestedFolderModel> by lazy {
                folders.toFolders()
            }

            fun folders(): List<SuggestedFolderModel> = convertedFolders
        }
        data object Empty : SuggestedFoldersState()
    }

    companion object {
        private const val NUMBER_OF_FOLDERS_KEY = "number_of_folders"
        private const val NUMBER_OF_PODCASTS_KEY = "number_of_podcasts"
        private const val BADGE_TYPE_KEY = "badge_type"
        private const val LAYOUT_KEY = "layout"
        private const val SORT_ORDER_KEY = "sort_order"
        private const val FOLLOWED_PODCASTS_THRESHOLD = 4
    }
}
