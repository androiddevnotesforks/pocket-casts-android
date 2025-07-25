package au.com.shiftyjelly.pocketcasts.repositories.sync

import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkManager
import au.com.shiftyjelly.pocketcasts.analytics.AnalyticsEvent
import au.com.shiftyjelly.pocketcasts.analytics.AnalyticsTracker
import au.com.shiftyjelly.pocketcasts.models.entity.Bookmark
import au.com.shiftyjelly.pocketcasts.models.entity.ChapterIndices
import au.com.shiftyjelly.pocketcasts.models.entity.Folder
import au.com.shiftyjelly.pocketcasts.models.entity.Podcast
import au.com.shiftyjelly.pocketcasts.models.entity.PodcastEpisode
import au.com.shiftyjelly.pocketcasts.models.entity.SmartPlaylist
import au.com.shiftyjelly.pocketcasts.models.entity.UserPodcastRating
import au.com.shiftyjelly.pocketcasts.models.to.StatsBundle
import au.com.shiftyjelly.pocketcasts.models.type.EpisodePlayingStatus
import au.com.shiftyjelly.pocketcasts.models.type.EpisodesSortType
import au.com.shiftyjelly.pocketcasts.models.type.PodcastsSortType
import au.com.shiftyjelly.pocketcasts.models.type.SyncStatus
import au.com.shiftyjelly.pocketcasts.preferences.Settings
import au.com.shiftyjelly.pocketcasts.repositories.bookmark.BookmarkManager
import au.com.shiftyjelly.pocketcasts.repositories.file.FileStorage
import au.com.shiftyjelly.pocketcasts.repositories.playback.PlaybackManager
import au.com.shiftyjelly.pocketcasts.repositories.podcast.EpisodeManager
import au.com.shiftyjelly.pocketcasts.repositories.podcast.FolderManager
import au.com.shiftyjelly.pocketcasts.repositories.podcast.PodcastManager
import au.com.shiftyjelly.pocketcasts.repositories.podcast.SmartPlaylistManager
import au.com.shiftyjelly.pocketcasts.repositories.podcast.UserEpisodeManager
import au.com.shiftyjelly.pocketcasts.repositories.ratings.RatingsManager
import au.com.shiftyjelly.pocketcasts.repositories.shortcuts.PocketCastsShortcuts
import au.com.shiftyjelly.pocketcasts.repositories.subscription.SubscriptionManager
import au.com.shiftyjelly.pocketcasts.repositories.user.StatsManager
import au.com.shiftyjelly.pocketcasts.servers.extensions.toDate
import au.com.shiftyjelly.pocketcasts.servers.podcast.PodcastCacheServiceManager
import au.com.shiftyjelly.pocketcasts.servers.sync.SyncSettingsTask
import au.com.shiftyjelly.pocketcasts.servers.sync.update.SyncUpdateResponse
import au.com.shiftyjelly.pocketcasts.utils.Util
import au.com.shiftyjelly.pocketcasts.utils.extensions.toIsoString
import au.com.shiftyjelly.pocketcasts.utils.log.LogBuffer
import com.automattic.android.tracks.crashlogging.CrashLogging
import com.pocketcasts.service.api.PodcastFolder
import com.pocketcasts.service.api.UserPodcastResponse
import com.pocketcasts.service.api.dateAddedOrNull
import com.pocketcasts.service.api.folderUuidOrNull
import com.pocketcasts.service.api.sortPositionOrNull
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.rxkotlin.Singles
import io.reactivex.schedulers.Schedulers
import java.time.Instant
import java.util.Date
import java.util.Locale
import kotlin.coroutines.CoroutineContext
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.rx2.rxCompletable
import kotlinx.coroutines.rx2.rxSingle
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber

class PodcastSyncProcess(
    val context: Context,
    val applicationScope: CoroutineScope,
    var settings: Settings,
    var episodeManager: EpisodeManager,
    var podcastManager: PodcastManager,
    var smartPlaylistManager: SmartPlaylistManager,
    var bookmarkManager: BookmarkManager,
    var statsManager: StatsManager,
    var fileStorage: FileStorage,
    var playbackManager: PlaybackManager,
    var podcastCacheServiceManager: PodcastCacheServiceManager,
    var userEpisodeManager: UserEpisodeManager,
    var subscriptionManager: SubscriptionManager,
    var folderManager: FolderManager,
    var syncManager: SyncManager,
    var ratingsManager: RatingsManager,
    val crashLogging: CrashLogging,
    val analyticsTracker: AnalyticsTracker,
) : CoroutineScope {
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default

    fun run(): Completable {
        if (!syncManager.isLoggedIn()) {
            smartPlaylistManager.deleteSyncedBlocking()

            Timber.i("SyncProcess: User not logged in")
            return Completable.complete()
        }

        val lastSyncTimeString = settings.getLastModified()
        val lastSyncTime = runCatching { Instant.parse(lastSyncTimeString) }
            .onFailure { Timber.e(it, "Could not convert lastModified String to Long: $lastSyncTimeString") }
            .getOrDefault(Instant.EPOCH)

        val downloadObservable = if (lastSyncTime == Instant.EPOCH) {
            performFullSync().andThen(syncUpNext())
        } else {
            if (settings.getHomeGridNeedsRefresh()) {
                Timber.i("SyncProcess: Refreshing home grid")
                performHomeGridRefresh()
                    .andThen(syncUpNext())
                    .andThen(performIncrementalSync(lastSyncTime))
            } else {
                syncUpNext()
                    .andThen(performIncrementalSync(lastSyncTime))
            }
        }
        val syncUpNextObservable = downloadObservable
            .andThen(rxCompletable { syncSettings(lastSyncTime) })
            .andThen(rxCompletable { syncCloudFiles() })
            .andThen(rxCompletable { firstSyncChanges() })
            .andThen(
                if (Util.isWearOs(context)) {
                    // We don't use the play history on wear os, so we can skip this potentially large call
                    Completable.complete()
                } else {
                    Completable.fromAction {
                        syncPlayHistory()
                    }
                },
            )
            .andThen(rxCompletable { syncRatings() })
        return syncUpNextObservable
            .doOnError { throwable ->
                crashLogging.sendReport(throwable, message = "Sync failed")
                LogBuffer.e(LogBuffer.TAG_BACKGROUND_TASKS, throwable, "SyncProcess: Sync failed")
            }
            .doOnComplete {
                Timber.i("SyncProcess: Sync success")
            }
    }

    @VisibleForTesting
    fun performIncrementalSync(lastSyncTime: Instant): Completable {
        val uploadData = uploadChanges()
        val uploadObservable = rxSingle {
            val startTime = SystemClock.elapsedRealtime()
            val updateResponse = syncManager.syncUpdate(uploadData.first, lastSyncTime)
            logTime("Refresh - sync update", startTime)
            updateResponse
        }
        return uploadObservable.flatMap {
            val startTime = SystemClock.elapsedRealtime()
            val response = processServerResponse(it, uploadData.second)
            logTime("Refresh - process server response", startTime)
            response
        }.ignoreElement()
    }

    private fun performFullSync(): Completable {
        // grab the last sync date before we begin
        return syncManager.getLastSyncAtRxSingle()
            .flatMapCompletable { lastSyncAt ->
                rxCompletable { cacheStats() }
                    .andThen(downloadAndImportHomeFolder())
                    .andThen(rxCompletable { downloadAndImportFilters() })
                    .andThen(rxCompletable { downloadAndImportBookmarks() })
                    .andThen(Completable.fromAction { settings.setLastModified(lastSyncAt) })
            }
    }

    private fun downloadAndImportHomeFolder(): Completable {
        // get all the current podcasts and folder uuids before the sync
        val localPodcasts = podcastManager.findSubscribedRxSingle()
        val localFolderUuids = folderManager.findFoldersSingle().map { it.map { folder -> folder.uuid } }
        // get all the users podcast uuids from the server
        val serverHomeFolder = rxSingle { syncManager.getHomeFolder() }
        return Singles.zip(serverHomeFolder, localPodcasts, localFolderUuids)
            .flatMapCompletable { (serverHomeFolder, localPodcasts, localFolderUuids) ->
                importPodcastsFullSync(serverPodcasts = serverHomeFolder.podcastsList, localPodcasts = localPodcasts)
                    .andThen(importFoldersFullSync(serverFolders = serverHomeFolder.foldersList, localFolderUuids = localFolderUuids))
            }
    }

    private fun performHomeGridRefresh(): Completable {
        return downloadAndImportHomeFolder()
            .andThen(markAllPodcastsUnsynced())
    }

    private fun markAllPodcastsUnsynced(): Completable {
        return rxCompletable {
            podcastManager.markAllPodcastsUnsynced()
        }
    }

    private fun importPodcastsFullSync(serverPodcasts: List<UserPodcastResponse>, localPodcasts: List<Podcast>): Completable {
        val localUuids = localPodcasts.map { podcast -> podcast.uuid }.toSet()
        val localPodcastsMap = localPodcasts.associateBy { it.uuid }
        val serverUuids = serverPodcasts.map { it.uuid }.toSet()
        val serverPodcastsMap = serverPodcasts.associateBy { it.uuid }
        // mark the podcasts missing from the server as not synced
        val serverMissingUuids = localUuids - serverUuids
        val markMissingNotSynced = Observable.fromIterable(serverMissingUuids).flatMapCompletable { uuid -> Completable.fromAction { podcastManager.markPodcastUuidAsNotSyncedBlocking(uuid) } }
        // subscribe to each podcast
        val localMissingUuids = serverUuids - localUuids
        val subscribeToPodcasts = Observable
            .fromIterable(localMissingUuids)
            .flatMap(
                { uuid ->
                    Observable.just(uuid)
                        .subscribeOn(Schedulers.io())
                        .flatMapMaybe { importPodcast(serverPodcastsMap[it]) }
                },
                5,
            )
            .ignoreElements()
        // update existing podcasts
        val existingUuids = localUuids.intersect(serverUuids)
        val updatePodcasts = Observable
            .fromIterable(existingUuids)
            .flatMapCompletable { uuid ->
                val serverPodcast = serverPodcastsMap[uuid]
                val localPodcast = localPodcastsMap[uuid]
                updatePodcastSyncValues(localPodcast, serverPodcast)
            }
        return markMissingNotSynced.andThen(subscribeToPodcasts).andThen(updatePodcasts)
    }

    private fun importFoldersFullSync(serverFolders: List<PodcastFolder>, localFolderUuids: List<String>): Completable {
        val serverUuids = serverFolders.map { it.folderUuid }
        val serverMissingUuids = localFolderUuids - serverUuids
        // delete the local folders that aren't on the server
        val deleteLocalFolders = Observable.fromIterable(serverMissingUuids).flatMapCompletable { uuid ->
            rxCompletable {
                folderManager.deleteSynced(uuid)
            }
        }
        // upsert the rest of the folders
        val upsertServerFolders = Observable.fromIterable(serverFolders.mapNotNull { it.toFolder() }).flatMapCompletable { folder ->
            rxCompletable {
                folderManager.upsertSynced(folder)
            }
        }
        return deleteLocalFolders.andThen(upsertServerFolders)
    }

    private fun PodcastFolder.toFolder(): Folder? {
        val dateAdded = dateAdded?.toDate()
        if (folderUuid == null || name == null || dateAdded == null) {
            return null
        }
        return Folder(
            uuid = folderUuid,
            name = name,
            color = color,
            addedDate = dateAdded,
            sortPosition = sortPosition,
            podcastsSortType = PodcastsSortType.fromServerId(podcastsSortType),
            deleted = false,
            syncModified = Folder.SYNC_MODIFIED_FROM_SERVER,
        )
    }

    private suspend fun downloadAndImportFilters() {
        val filters = syncManager.getFilters()
        importFilters(filters)
    }

    private suspend fun downloadAndImportBookmarks() {
        val bookmarks = syncManager.getBookmarks()
        importBookmarks(bookmarks)
    }

    private fun importPodcast(podcastResponse: UserPodcastResponse?): Maybe<Podcast> {
        val podcastUuid = podcastResponse?.uuid ?: return Maybe.empty()
        return podcastManager.subscribeToPodcastRxSingle(podcastUuid = podcastUuid, sync = false, shouldAutoDownload = false)
            .flatMap { podcast ->
                podcast.isHeaderExpanded = false
                updatePodcastSyncValues(podcast, podcastResponse).toSingleDefault(podcast)
            }
            .toMaybe()
            .doOnError { LogBuffer.e(LogBuffer.TAG_BACKGROUND_TASKS, it, "Could not import server podcast %s", podcastUuid) }
            .onErrorComplete()
    }

    private fun updatePodcastSyncValues(podcast: Podcast?, podcastResponse: UserPodcastResponse?): Completable = rxCompletable {
        if (podcast == null || podcastResponse == null) {
            return@rxCompletable
        }

        // use the oldest local or server added date
        val serverAddedDate = podcastResponse.dateAddedOrNull?.toDate() ?: Date()
        val localAddedDate = podcast.addedDate
        val resolvedAddedDate = if (localAddedDate == null || serverAddedDate < localAddedDate) serverAddedDate else localAddedDate

        podcast.apply {
            startFromSecs = podcastResponse.autoStartFrom
            skipLastSecs = podcastResponse.autoSkipLast
            folderUuid = podcastResponse.folderUuidOrNull?.value
            sortPosition = podcastResponse.sortPositionOrNull?.value ?: podcast.sortPosition
            addedDate = resolvedAddedDate
        }

        podcastManager.updatePodcastBlocking(podcast)
    }

    private fun syncUpNext() = Completable.create { emitter ->
        val startTime = SystemClock.elapsedRealtime()
        val workRequestId = UpNextSyncWorker.enqueue(syncManager, context)
        if (workRequestId == null) {
            logTime("Refresh - sync up next, work request id is null", startTime)
            emitter.onComplete()
        } else {
            ProcessLifecycleOwner.get().lifecycleScope.launch {
                WorkManager.getInstance(context).getWorkInfoByIdFlow(workRequestId).firstOrNull { it?.state?.isFinished ?: true }
                logTime("Refresh - sync up next", startTime)
                emitter.onComplete()
            }
        }
    }

    private fun syncPlayHistory() {
        val startTime = SystemClock.elapsedRealtime()
        SyncHistoryTask.scheduleToRun(context)
        logTime("Refresh - sync play history", startTime)
    }

    private suspend fun syncSettings(lastSyncTime: Instant) {
        val startTime = SystemClock.elapsedRealtime()
        SyncSettingsTask.run(settings, lastSyncTime, syncManager)
        logTime("Refresh - sync settings", startTime)
    }

    private suspend fun syncCloudFiles() {
        val subscription = subscriptionManager.fetchFreshSubscription()
        if (subscription != null) {
            userEpisodeManager.syncFiles(playbackManager)
        }
    }

    private suspend fun syncRatings() {
        syncManager.getPodcastRatings()?.podcastRatingsList
            ?.mapNotNull { rating ->
                val modifiedAt = rating.modifiedAt.toDate() ?: return@mapNotNull null
                UserPodcastRating(
                    podcastUuid = rating.podcastUuid,
                    rating = rating.podcastRating,
                    modifiedAt = modifiedAt,
                )
            }
            ?.let { ratingsManager.updateUserRatings(it) }
    }

    private fun uploadChanges(): Pair<String, List<PodcastEpisode>> {
        val records = JSONArray()
        uploadPodcastChanges(records)
        val episodes = uploadEpisodesChanges(records)
        uploadPlaylistChanges(records)
        uploadFolderChanges(records)
        uploadBookmarksChanges(records)
        uploadStatChanges(records)

        val data = JSONObject()

        data.put("records", records)

        return Pair(data.toString(), episodes)
    }

    private fun uploadFolderChanges(records: JSONArray) {
        try {
            val folders = folderManager.findFoldersToSyncBlocking()
            for (folder in folders) {
                val fields = JSONObject()

                try {
                    fields.put("folder_uuid", folder.uuid)
                    fields.put("is_deleted", if (folder.deleted) "1" else "0")
                    fields.put("name", folder.name)
                    fields.put("color", folder.color)
                    fields.put("sort_position", folder.sortPosition)
                    fields.put("podcasts_sort_type", folder.podcastsSortType.serverId)
                    fields.put("date_added", folder.addedDate.toIsoString())

                    val record = JSONObject()
                    record.put("fields", fields)
                    record.put("type", "UserFolder")

                    records.put(record)
                } catch (e: JSONException) {
                    Timber.e(e, "Unable to upload folder")
                    throw PocketCastsSyncException(e)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Unable to upload folders to sync.")
            throw PocketCastsSyncException(e)
        }
    }

    private fun uploadPlaylistChanges(records: JSONArray) {
        try {
            val playlists = smartPlaylistManager.findPlaylistsToSyncBlocking()
            for (playlist in playlists) {
                val fields = JSONObject()

                if (playlist.manual) {
                    continue
                }

                try {
                    fields.put("uuid", playlist.uuid)
                    fields.put("is_deleted", if (playlist.deleted) "1" else "0")
                    fields.put("title", playlist.title)
                    fields.put("all_podcasts", if (playlist.allPodcasts) "1" else "0")
                    fields.put("podcast_uuids", playlist.podcastUuids)
                    fields.put("episode_uuids", null)
                    fields.put("audio_video", playlist.audioVideo)
                    fields.put("not_downloaded", if (playlist.notDownloaded) "1" else "0")
                    fields.put("downloaded", if (playlist.downloaded) "1" else "0")
                    fields.put("downloading", if (playlist.downloading) "1" else "0")
                    fields.put("finished", if (playlist.finished) "1" else "0")
                    fields.put("partially_played", if (playlist.partiallyPlayed) "1" else "0")
                    fields.put("unplayed", if (playlist.unplayed) "1" else "0")
                    fields.put("starred", if (playlist.starred) "1" else "0")
                    fields.put("manual", "0")
                    fields.put("sort_position", playlist.sortPosition)
                    fields.put("sort_type", playlist.sortType.serverId)
                    fields.put("icon_id", playlist.iconId)
                    fields.put("filter_hours", playlist.filterHours)
                    fields.put("filter_duration", playlist.filterDuration)
                    fields.put("longer_than", playlist.longerThan)
                    fields.put("shorter_than", playlist.shorterThan)

                    val record = JSONObject()
                    record.put("fields", fields)
                    record.put("type", "UserPlaylist")

                    records.put(record)
                } catch (e: JSONException) {
                    Timber.e(e, "Unable to save playlist")
                    throw PocketCastsSyncException(e)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Unable to upload playlist to sync.")
            throw PocketCastsSyncException(e)
        }
    }

    private fun uploadStatChanges(records: JSONArray) {
        if (statsManager.isSynced(settings) || statsManager.isEmpty) {
            return
        }
        try {
            val fields = JSONObject()
            val stats = statsManager.localStatsInServerFormat
            val itr = stats.keys.iterator()
            while (itr.hasNext()) {
                val key = itr.next()

                fields.put(key, stats[key])
            }

            fields.put(StatsBundle.SERVER_KEY_SKIPPING, statsManager.timeSavedSkippingSecs)
            fields.put(StatsBundle.SERVER_KEY_AUTO_SKIPPING, statsManager.timeSavedSkippingIntroSecs)
            fields.put(StatsBundle.SERVER_KEY_VARIABLE_SPEED, statsManager.timeSavedVariableSpeedSecs)
            fields.put(StatsBundle.SERVER_KEY_TOTAL_LISTENED, statsManager.totalListeningTimeSecs)
            fields.put(StatsBundle.SERVER_KEY_STARTED_AT, statsManager.statsStartTimeSecs)

            val record = JSONObject()
            record.put("fields", fields)
            record.put("type", "UserDevice")

            records.put(record)
        } catch (e: JSONException) {
            Timber.e(e, "Unable to save stats")
            throw PocketCastsSyncException(e)
        }
    }

    private fun uploadPodcastChanges(records: JSONArray) {
        try {
            val podcasts = podcastManager.findPodcastsToSyncBlocking()
            for (podcast in podcasts) {
                try {
                    val fields = JSONObject().apply {
                        put("uuid", podcast.uuid)
                        put("is_deleted", if (podcast.isSubscribed) "0" else "1")
                        put("auto_start_from", podcast.startFromSecs)
                        put("auto_skip_last", podcast.skipLastSecs)
                        put("folder_uuid", if (podcast.folderUuid.isNullOrEmpty()) Folder.HOME_FOLDER_UUID else podcast.folderUuid)
                        put("sort_position", podcast.sortPosition)
                        put("date_added", podcast.addedDate?.toIsoString())
                    }
                    val record = JSONObject().apply {
                        put("fields", fields)
                        put("type", "UserPodcast")
                    }
                    records.put(record)
                } catch (e: JSONException) {
                    Timber.e(e, "Unable to save podcast")
                    throw PocketCastsSyncException(e)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Unable to upload podcast to sync.")
            throw PocketCastsSyncException(e)
        }
    }

    private fun uploadEpisodesChanges(records: JSONArray): List<PodcastEpisode> {
        try {
            val episodes = episodeManager.findEpisodesToSyncBlocking()
            episodes.forEach { episode ->
                uploadEpisodeChanges(episode, records)
            }

            return episodes
        } catch (e: Exception) {
            Timber.e(e, "Unable to load episodes to sync.")
            throw PocketCastsSyncException(e)
        }
    }

    private fun uploadEpisodeChanges(episode: PodcastEpisode, records: JSONArray) {
        val fields = JSONObject()

        try {
            val playingStatus = when (episode.playingStatus) {
                EpisodePlayingStatus.IN_PROGRESS -> 2
                EpisodePlayingStatus.COMPLETED -> 3
                else -> 1
            }

            fields.put("uuid", episode.uuid)
            episode.playingStatusModified?.let { playingStatusModified ->
                fields.put("playing_status", playingStatus)
                fields.put("playing_status_modified", playingStatusModified)
            }

            episode.starredModified?.let { starredModified ->
                fields.put("starred", if (episode.isStarred) "1" else "0")
                fields.put("starred_modified", starredModified)
            }
            episode.playedUpToModified?.let { playedUpToModified ->
                fields.put("played_up_to", episode.playedUpTo)
                fields.put("played_up_to_modified", playedUpToModified)
            }
            episode.durationModified?.let { durationModified ->
                val duration = episode.duration
                if (duration != 0.0) {
                    fields.put("duration", duration)
                    fields.put("duration_modified", durationModified)
                }
            }
            episode.archivedModified?.let { archiveModified ->
                fields.put("is_deleted", if (episode.isArchived) "1" else "0")
                fields.put("is_deleted_modified", archiveModified)
            }
            fields.put("user_podcast_uuid", episode.podcastUuid)

            episode.deselectedChaptersModified?.let { deselectedChaptersModified ->
                fields.put("deselected_chapters", ChapterIndices.toString(episode.deselectedChapters))
                fields.put("deselected_chapters_modified", deselectedChaptersModified.time)
            }

            val record = JSONObject().apply {
                put("fields", fields)
                put("type", "UserEpisode")
            }

            records.put(record)
        } catch (e: JSONException) {
            Timber.e(e, "Unable to save episode")
        }
    }

    private fun uploadBookmarksChanges(records: JSONArray) {
        try {
            val bookmarks = bookmarkManager.findBookmarksToSyncBlocking()
            bookmarks.forEach { bookmark ->
                @Suppress("DEPRECATION")
                uploadBookmarkChanges(bookmark, records)
            }
        } catch (e: Exception) {
            Timber.e(e, "Unable to load bookmarks to sync.")
            throw PocketCastsSyncException(e)
        }
    }

    private fun uploadBookmarkChanges(bookmark: Bookmark, records: JSONArray) {
        try {
            val fields = JSONObject().apply {
                put("bookmark_uuid", bookmark.uuid)
                put("podcast_uuid", bookmark.podcastUuid)
                put("episode_uuid", bookmark.episodeUuid)
                put("time", bookmark.timeSecs)
                put("created_at", bookmark.createdAt.toIsoString())
            }
            bookmark.titleModified?.let { titleModified ->
                fields.put("title", bookmark.title)
                fields.put("title_modified", titleModified)
            }
            bookmark.deletedModified?.let { deletedModified ->
                fields.put("is_deleted", if (bookmark.deleted) "1" else "0")
                fields.put("is_deleted_modified", deletedModified)
            }
            val record = JSONObject().apply {
                put("fields", fields)
                put("type", "UserBookmark")
            }
            records.put(record)
        } catch (e: JSONException) {
            crashLogging.sendReport(e)
            Timber.e(e, "Unable to save bookmark")
        }
    }

    private fun processServerResponse(response: SyncUpdateResponse, episodes: List<PodcastEpisode>): Single<String> {
        if (response.lastModified == null) {
            return Single.error(Exception("Server response doesn't return a last modified"))
        }
        // import episodes first so that newly added podcasts get their own episodes from the server.
        return rxCompletable { markAllLocalItemsSynced(episodes) }
            .andThen(importEpisodes(response.episodes))
            .andThen(importPodcasts(response.podcasts))
            .andThen(rxCompletable { importFilters(response.smartPlaylists) })
            .andThen(importFolders(response.folders))
            .andThen(rxCompletable { importBookmarks(response.bookmarks) })
            .andThen(updateSettings(response))
            .andThen(rxCompletable { updateShortcuts(response.smartPlaylists) })
            .andThen(rxCompletable { cacheStats() })
            .toSingle { response.lastModified }
    }

    private suspend fun cacheStats() {
        statsManager.cacheMergedStats()
        statsManager.setSyncStatus(true)
    }

    private suspend fun markAllLocalItemsSynced(episodes: List<PodcastEpisode>) {
        podcastManager.markAllPodcastsSynced()
        episodeManager.markAllEpisodesSynced(episodes)
        smartPlaylistManager.markAllSynced()
        folderManager.markAllSynced()
    }

    private suspend fun firstSyncChanges() {
        val firstSync = settings.isFirstSyncRun()
        if (firstSync) {
            fileStorage.fixBrokenFiles(episodeManager)
            settings.setFirstSyncRun(false)
        }
    }

    private fun updateSettings(response: SyncUpdateResponse): Completable {
        return Completable.fromAction {
            settings.setLastModified(response.lastModified)
        }
    }

    private suspend fun updateShortcuts(smartPlaylists: List<SmartPlaylist>) {
        // if any playlists have changed update the launcher shortcuts
        if (smartPlaylists.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            PocketCastsShortcuts.update(
                smartPlaylistManager = smartPlaylistManager,
                force = true,
                context = context,
                source = PocketCastsShortcuts.Source.UPDATE_SHORTCUTS,
            )
        }
    }

    private fun importPodcasts(podcasts: List<SyncUpdateResponse.PodcastSync>): Completable {
        return Observable.fromIterable(podcasts)
            .flatMap(
                { podcastSync ->
                    Observable.just(podcastSync)
                        .subscribeOn(Schedulers.computation())
                        .flatMap { importPodcast(it).toObservable() }
                },
                10,
            )
            .ignoreElements()
    }

    private fun importEpisodes(episodes: List<SyncUpdateResponse.EpisodeSync>): Completable {
        return Observable.fromIterable(episodes)
            .flatMap { episode -> importEpisode(episode).toObservable() }
            .ignoreElements()
    }

    private suspend fun importFilters(smartPlaylists: List<SmartPlaylist>) {
        for (playlist in smartPlaylists) {
            importPlaylist(playlist)
        }
    }

    private fun importFolders(folders: List<Folder>): Completable {
        return Observable.fromIterable(folders)
            .flatMapCompletable { folder -> importFolder(folder) }
    }

    private suspend fun importBookmarks(bookmarks: List<Bookmark>) {
        for (bookmark in bookmarks) {
            importBookmark(bookmark)
        }
    }

    private fun importFolder(sync: Folder): Completable {
        return rxCompletable {
            if (sync.deleted) {
                folderManager.deleteSynced(sync.uuid)
            } else {
                folderManager.upsertSynced(sync)
            }
        }
    }

    private suspend fun importPlaylist(sync: SmartPlaylist): SmartPlaylist? {
        val uuid = sync.uuid
        if (uuid.isBlank()) {
            return null
        }
        // manual playlists are no longer supported
        if (sync.manual) {
            return null
        }

        var playlist = smartPlaylistManager.findByUuid(uuid)
        if (sync.deleted) {
            playlist?.let { smartPlaylistManager.deleteSynced(it) }
            return null
        }

        if (playlist == null) {
            playlist = SmartPlaylist(uuid = sync.uuid)
        }

        with(playlist) {
            title = sync.title
            audioVideo = sync.audioVideo
            notDownloaded = sync.notDownloaded
            downloaded = sync.downloaded
            downloading = sync.downloading
            finished = sync.finished
            partiallyPlayed = sync.partiallyPlayed
            unplayed = sync.unplayed
            starred = sync.starred
            manual = sync.manual
            sortPosition = sync.sortPosition
            sortType = sync.sortType
            iconId = sync.iconId
            allPodcasts = sync.allPodcasts
            podcastUuids = sync.podcastUuids
            filterHours = sync.filterHours
            syncStatus = SmartPlaylist.SYNC_STATUS_SYNCED
            filterDuration = sync.filterDuration
            longerThan = sync.longerThan
            shorterThan = sync.shorterThan
        }

        if (playlist.id == null) {
            playlist.id = smartPlaylistManager.create(playlist)
        } else {
            smartPlaylistManager.update(playlist, userPlaylistUpdate = null)
        }

        return playlist
    }

    private fun importPodcast(sync: SyncUpdateResponse.PodcastSync): Maybe<Podcast> {
        val uuid = sync.uuid
        if (uuid.isNullOrBlank()) {
            return Maybe.empty()
        }

        val podcast = podcastManager.findPodcastByUuidBlocking(uuid)
        return if (podcast == null) {
            importServerPodcast(sync)
        } else {
            importExistingPodcast(podcast, sync)
        }
    }

    private fun importServerPodcast(podcastSync: SyncUpdateResponse.PodcastSync): Maybe<Podcast> {
        // don't import podcasts deleted or aren't subscribed too
        val isSubscribed = podcastSync.subscribed
        val podcastUuid = podcastSync.uuid
        if (podcastSync.subscribed && isSubscribed && podcastUuid != null) {
            return podcastManager.subscribeToPodcastRxSingle(podcastUuid, sync = false, shouldAutoDownload = false)
                .doOnSuccess { podcast ->
                    applyPodcastSyncUpdatesToPodcast(podcast, podcastSync)
                    podcastManager.updatePodcastBlocking(podcast)
                }
                .toMaybe()
                .doOnError { LogBuffer.e(LogBuffer.TAG_BACKGROUND_TASKS, it, "Could not import server podcast  %s", podcastUuid) }
                .onErrorComplete()
        } else {
            return Maybe.empty<Podcast>()
        }
    }

    private fun importExistingPodcast(podcast: Podcast, podcastSync: SyncUpdateResponse.PodcastSync): Maybe<Podcast> {
        if (podcastSync.subscribed) {
            podcast.syncStatus = Podcast.SYNC_STATUS_SYNCED
            podcast.isSubscribed = true
            applyPodcastSyncUpdatesToPodcast(podcast, podcastSync)

            podcastManager.updatePodcastBlocking(podcast)
        } else if (podcast.isSubscribed && !podcastSync.subscribed) { // Unsubscribed on the server but subscribed on device
            Timber.d("Unsubscribing from podcast $podcast during sync")
            podcastManager.unsubscribeBlocking(podcast.uuid, playbackManager)
        }
        return Maybe.just(podcast)
    }

    private fun applyPodcastSyncUpdatesToPodcast(podcast: Podcast, podcastSync: SyncUpdateResponse.PodcastSync) {
        podcast.addedDate = podcastSync.dateAdded
        podcast.folderUuid = podcastSync.folderUuid
        podcastSync.sortPosition?.let { podcast.sortPosition = it }
        podcastSync.episodesSortOrder?.let { podcast.episodesSortType = EpisodesSortType.fromServerId(it) ?: EpisodesSortType.EPISODES_SORT_BY_TITLE_ASC }
        podcastSync.startFromSecs?.let { podcast.startFromSecs = it }
        podcastSync.skipLastSecs?.let { podcast.skipLastSecs = it }
    }

    fun importEpisode(episodeSync: SyncUpdateResponse.EpisodeSync): Maybe<PodcastEpisode> {
        val uuid = episodeSync.uuid ?: return Maybe.empty()

        // check if the episode already exists
        val episode = runBlocking {
            episodeManager.findByUuid(uuid)
        }
        return if (episode == null) {
            Maybe.empty()
        } else {
            importExistingEpisode(episodeSync, episode).toMaybe()
        }
    }

    private fun importExistingEpisode(sync: SyncUpdateResponse.EpisodeSync, episode: PodcastEpisode): Single<PodcastEpisode> {
        return Single.fromCallable {
            val playingEpisodeUuid = playbackManager.getCurrentEpisode()?.uuid
            val episodeInPlayer = playingEpisodeUuid != null && episode.uuid == playingEpisodeUuid
            val isPlaying = playbackManager.isPlaying()
            val isEpisodePlaying = episodeInPlayer && isPlaying

            sync.starred?.let {
                episode.isStarred = it
                episode.starredModified = null
            }

            sync.duration?.let {
                if (it > 0) {
                    episode.duration = it
                    episode.durationModified = null
                }
            }

            sync.isArchived?.let { newIsArchive ->
                if (episode.isArchived == newIsArchive) return@let

                if (isEpisodePlaying) {
                    // if we're playing this episode, marked the archive status as unsynced because the server might have a different one to us now
                    episode.archivedModified = System.currentTimeMillis()
                } else {
                    episode.archivedModified = null
                    if (newIsArchive) {
                        episodeManager.archiveBlocking(episode, playbackManager, sync = false)
                    } else {
                        episode.isArchived = false
                        episode.lastArchiveInteraction = Date().time
                    }
                }
            }

            sync.playingStatus?.let { newPlayingStatus ->
                if (episode.playingStatus == newPlayingStatus) return@let

                if (isEpisodePlaying) {
                    // if we're playing this episode, marked the status as unsynced because the server might have a different one to us now
                    episode.playingStatusModified = System.currentTimeMillis()
                } else {
                    episode.playingStatusModified = null
                    episode.playingStatus = newPlayingStatus
                    if (episode.isFinished) {
                        episodeManager.markedAsPlayedExternally(episode, playbackManager, podcastManager)
                    }
                }
            }

            sync.playedUpTo?.let { playedUpTo ->
                if (playedUpTo < 0 || isEpisodePlaying) {
                    return@let
                }

                // don't update if times are very close
                val currentUpTo = episode.playedUpTo

                val negativeSeekThresholdSecs = settings.getPlaybackEpisodePositionChangedOnSyncThresholdSecs()
                if (playedUpTo < currentUpTo - negativeSeekThresholdSecs || playedUpTo > currentUpTo + 2) {
                    episode.playedUpTo = playedUpTo
                    episode.playedUpToModified = null
                    if (episodeInPlayer) {
                        val diffSeconds = (playedUpTo - currentUpTo).roundToInt()
                        if (diffSeconds < 0) {
                            // Track if the position is skipping back on sync while the player is in paused state. This is to help debug the playback jumping issue.
                            analyticsTracker.track(
                                AnalyticsEvent.PLAYBACK_EPISODE_POSITION_CHANGED_ON_SYNC,
                                mapOf(
                                    "position_change" to diffSeconds,
                                    "is_downloaded" to episode.isDownloaded,
                                    "episode_uuid" to episode.uuid,
                                    "podcast_uuid" to episode.podcastOrSubstituteUuid,
                                ),
                            )
                        }
                        playbackManager.seekIfPlayingToTimeMs(episode.uuid, (playedUpTo * 1000).toInt())
                    }
                }
            }

            sync.deselectedChapters?.let {
                episode.deselectedChapters = it
                episode.deselectedChaptersModified = null
            }

            episodeManager.updateBlocking(episode)

            episode
        }
    }

    private suspend fun importBookmark(bookmark: Bookmark) {
        if (bookmark.deleted) {
            bookmarkManager.deleteSynced(bookmark.uuid)
        } else {
            bookmarkManager.upsertSynced(bookmark.copy(syncStatus = SyncStatus.SYNCED))
        }
    }

    private fun logTime(message: String, startTime: Long) {
        val time = SystemClock.elapsedRealtime() - startTime
        LogBuffer.i(LogBuffer.TAG_BACKGROUND_TASKS, "$message - ${String.format(Locale.ENGLISH, "%d ms", time)}")
    }
}
