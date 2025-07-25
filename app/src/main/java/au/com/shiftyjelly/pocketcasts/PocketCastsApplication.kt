package au.com.shiftyjelly.pocketcasts

import android.app.Application
import android.os.Environment
import android.os.StrictMode
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import au.com.shiftyjelly.pocketcasts.analytics.AnalyticsTracker
import au.com.shiftyjelly.pocketcasts.analytics.experiments.ExperimentProvider
import au.com.shiftyjelly.pocketcasts.crashlogging.InitializeRemoteLogging
import au.com.shiftyjelly.pocketcasts.discover.worker.CuratedPodcastsSyncWorker
import au.com.shiftyjelly.pocketcasts.engage.EngageSdkBridge
import au.com.shiftyjelly.pocketcasts.models.db.dao.UpNextDao
import au.com.shiftyjelly.pocketcasts.models.type.EpisodeStatusEnum
import au.com.shiftyjelly.pocketcasts.preferences.Settings
import au.com.shiftyjelly.pocketcasts.repositories.di.ApplicationScope
import au.com.shiftyjelly.pocketcasts.repositories.download.DownloadManager
import au.com.shiftyjelly.pocketcasts.repositories.endofyear.EndOfYearSync
import au.com.shiftyjelly.pocketcasts.repositories.file.FileStorage
import au.com.shiftyjelly.pocketcasts.repositories.file.StorageOptions
import au.com.shiftyjelly.pocketcasts.repositories.jobs.VersionMigrationsWorker
import au.com.shiftyjelly.pocketcasts.repositories.notification.NotificationHelper
import au.com.shiftyjelly.pocketcasts.repositories.notification.NotificationManager
import au.com.shiftyjelly.pocketcasts.repositories.playback.PlaybackManager
import au.com.shiftyjelly.pocketcasts.repositories.playback.SleepTimerRestartWhenShakingDevice
import au.com.shiftyjelly.pocketcasts.repositories.podcast.EpisodeManager
import au.com.shiftyjelly.pocketcasts.repositories.podcast.PodcastManager
import au.com.shiftyjelly.pocketcasts.repositories.podcast.SmartPlaylistManager
import au.com.shiftyjelly.pocketcasts.repositories.podcast.UserEpisodeManager
import au.com.shiftyjelly.pocketcasts.repositories.support.DatabaseExportHelper
import au.com.shiftyjelly.pocketcasts.repositories.sync.SyncManager
import au.com.shiftyjelly.pocketcasts.repositories.user.StatsManager
import au.com.shiftyjelly.pocketcasts.repositories.user.UserManager
import au.com.shiftyjelly.pocketcasts.shared.AppLifecycleObserver
import au.com.shiftyjelly.pocketcasts.shared.DownloadStatisticsReporter
import au.com.shiftyjelly.pocketcasts.ui.helper.AppIcon
import au.com.shiftyjelly.pocketcasts.utils.TimberDebugTree
import au.com.shiftyjelly.pocketcasts.utils.featureflag.Feature
import au.com.shiftyjelly.pocketcasts.utils.featureflag.FeatureFlag
import au.com.shiftyjelly.pocketcasts.utils.log.LogBuffer
import au.com.shiftyjelly.pocketcasts.utils.log.LogBufferUncaughtExceptionHandler
import au.com.shiftyjelly.pocketcasts.utils.log.RxJavaUncaughtExceptionHandling
import au.com.shiftyjelly.pocketcasts.widget.PlayerWidgetManager
import coil.Coil
import coil.ImageLoader
import com.google.firebase.FirebaseApp
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import java.util.concurrent.Executors
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.rx2.asFlow
import kotlinx.coroutines.withContext
import timber.log.Timber

@HiltAndroidApp
class PocketCastsApplication :
    Application(),
    Configuration.Provider {

    @Inject lateinit var appLifecycleObserver: AppLifecycleObserver

    @Inject lateinit var statsManager: StatsManager

    @Inject lateinit var podcastManager: PodcastManager

    @Inject lateinit var episodeManager: EpisodeManager

    @Inject lateinit var settings: Settings

    @Inject lateinit var fileStorage: FileStorage

    @Inject lateinit var smartPlaylistManager: SmartPlaylistManager

    @Inject lateinit var playbackManager: PlaybackManager

    @Inject lateinit var downloadManager: DownloadManager

    @Inject lateinit var notificationHelper: NotificationHelper

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var userEpisodeManager: UserEpisodeManager

    @Inject lateinit var appIcon: AppIcon

    @Inject lateinit var coilImageLoader: ImageLoader

    @Inject lateinit var userManager: UserManager

    @Inject lateinit var analyticsTracker: AnalyticsTracker

    @Inject lateinit var syncManager: SyncManager

    @Inject lateinit var downloadStatisticsReporter: DownloadStatisticsReporter

    @Inject @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    @Inject lateinit var playerWidgetManager: PlayerWidgetManager

    @Inject lateinit var upNextDao: UpNextDao

    @Inject lateinit var sleepTimerRestartWhenShakingDevice: SleepTimerRestartWhenShakingDevice

    @Inject lateinit var initializeRemoteLogging: InitializeRemoteLogging

    @Inject lateinit var databaseExportHelper: DatabaseExportHelper

    @Inject lateinit var engageSdkBridge: EngageSdkBridge

    @Inject lateinit var experimentProvider: ExperimentProvider

    @Inject lateinit var endOfYearSync: EndOfYearSync

    @Inject lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build(),
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    // .penaltyDeath()
                    .build(),
            )
        }

        super.onCreate()

        RxJavaUncaughtExceptionHandling.setUp()
        setupCrashLogging()
        setupLogging()
        setupAnalytics()
        setupApp()
        cleanupDatabaseExportFileIfExists()
    }

    private fun setupAnalytics() {
        analyticsTracker.clearAllData()
        analyticsTracker.refreshMetadata()
        downloadStatisticsReporter.setup()
        experimentProvider.initialize()
    }

    private fun setupCrashLogging() {
        Thread.getDefaultUncaughtExceptionHandler()?.let {
            Thread.setDefaultUncaughtExceptionHandler(LogBufferUncaughtExceptionHandler(it))
        }

        initializeRemoteLogging()

        // Setup the Firebase, the documentation says this isn't needed but in production we sometimes get the following error "FirebaseApp is not initialized in this process au.com.shiftyjelly.pocketcasts. Make sure to call FirebaseApp.initializeApp(Context) first."
        FirebaseApp.initializeApp(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setExecutor(Executors.newFixedThreadPool(3))
            .setJobSchedulerJobIdRange(1000, 20000)
            .build()

    private fun setupApp() {
        LogBuffer.i("Application", "App started. ${settings.getVersion()} (${settings.getVersionCode()})")

        runBlocking {
            appIcon.enableSelectedAlias(appIcon.activeAppIcon)

            notificationHelper.setupNotificationChannels()
            notificationManager.setupOnboardingNotifications()
            notificationManager.setupReEngagementNotifications()
            notificationManager.setupTrendingAndRecommendationsNotifications()
            notificationManager.setupNewFeaturesNotifications()
            notificationManager.setupOffersNotifications()
            appLifecycleObserver.setup()

            Coil.setImageLoader(coilImageLoader)

            withContext(Dispatchers.Default) {
                playbackManager.setup()
                downloadManager.setup(episodeManager, podcastManager, smartPlaylistManager, playbackManager)

                val isRestoreFromBackup = settings.isRestoreFromBackup()
                // as this may be a different device clear the storage location on a restore
                if (isRestoreFromBackup) {
                    settings.setStorageChoice(null, null)
                }

                // migrate old storage locations
                val storageChoice = settings.getStorageChoice()
                if (storageChoice == null) {
                    // the user doesn't have a storage choice, give them one
                    val storageOptions = StorageOptions()
                    val locationsAvailable = storageOptions.getFolderLocations(this@PocketCastsApplication)
                    if (locationsAvailable.size > 0) {
                        val folder = locationsAvailable[0]
                        settings.setStorageChoice(folder.filePath, folder.label)
                    } else {
                        val location = this@PocketCastsApplication.filesDir
                        settings.setStorageCustomFolder(location.absolutePath)
                    }
                } else if (storageChoice.equals(Settings.LEGACY_STORAGE_ON_PHONE, ignoreCase = true)) {
                    val location = this@PocketCastsApplication.filesDir
                    settings.setStorageCustomFolder(location.absolutePath)
                } else if (storageChoice.equals(Settings.LEGACY_STORAGE_ON_SD_CARD, ignoreCase = true)) {
                    val location = findExternalStorageDirectory()
                    settings.setStorageCustomFolder(location.absolutePath)
                }

                // after the app is installed check it
                if (isRestoreFromBackup) {
                    val podcasts = podcastManager.findSubscribedBlocking()
                    val restoredFromBackup = podcasts.isNotEmpty()
                    if (restoredFromBackup) {
                        // check to see if the episode files already exist
                        episodeManager.updateAllEpisodeStatusBlocking(EpisodeStatusEnum.NOT_DOWNLOADED)
                        fileStorage.fixBrokenFiles(episodeManager)
                        // reset stats
                        statsManager.reset()
                    }
                    settings.setRestoreFromBackupEnded()
                }

                // create opml import folder
                try {
                    fileStorage.getOrCreateOpmlDir()
                } catch (e: Exception) {
                    Timber.e(e, "Unable to create opml folder.")
                }

                VersionMigrationsWorker.performMigrations(
                    podcastManager = podcastManager,
                    settings = settings,
                    syncManager = syncManager,
                    context = this@PocketCastsApplication,
                )

                // check that we have .nomedia files in existing folders
                fileStorage.checkNoMediaDirs()

                // init the stats engine
                statsManager.initStatsEngine()

                sleepTimerRestartWhenShakingDevice.init() // Begin detecting when the device has been shaken to restart the sleep timer.
            }
        }

        applicationScope.launch(Dispatchers.IO) { fileStorage.fixBrokenFiles(episodeManager) }

        userEpisodeManager.monitorUploads(applicationContext)
        downloadManager.beginMonitoringWorkManager(applicationContext)
        userManager.beginMonitoringAccountManager(playbackManager)
        CuratedPodcastsSyncWorker.enqueuePeriodicWork(this)
        engageSdkBridge.registerIntegration()

        keepPlayerWidgetsUpdated()

        if (FeatureFlag.isEnabled(Feature.SYNC_EOY_DATA_ON_STARTUP)) {
            applicationScope.launch { endOfYearSync.sync() }
        }

        Timber.i("Launched ${BuildConfig.APPLICATION_ID}")
    }

    private fun keepPlayerWidgetsUpdated() {
        settings.artworkConfiguration.flow
            .onEach { playerWidgetManager.updateUseEpisodeArtwork(it.useEpisodeArtwork) }
            .launchIn(applicationScope)
        settings.useDynamicColorsForWidget.flow
            .onEach(playerWidgetManager::updateUseDynamicColors)
            .launchIn(applicationScope)
        settings.skipBackInSecs.flow
            .onEach(playerWidgetManager::updateSkipBackwardDuration)
            .launchIn(applicationScope)
        settings.skipForwardInSecs.flow
            .onEach(playerWidgetManager::updateSkipForwardDuration)
            .launchIn(applicationScope)
        playbackManager.playbackStateRelay.asFlow()
            .map { state -> state.isPlaying }
            .distinctUntilChanged()
            .onEach(playerWidgetManager::updateIsPlaying)
            .launchIn(applicationScope)
        val queueFlow = flow {
            while (true) {
                emit(upNextDao.getUpNextBaseEpisodes(limit = PlayerWidgetManager.EPISODE_LIMIT))
                // Emit every second to update playback durations
                delay(1.seconds)
            }
        }
        queueFlow
            .distinctUntilChangedBy { queue -> queue.map { it.uuid to it.playedUpToMs } }
            .onEach(playerWidgetManager::updateQueue)
            .launchIn(applicationScope)
    }

    @Suppress("DEPRECATION")
    private fun findExternalStorageDirectory(): File {
        return Environment.getExternalStorageDirectory()
    }

    private fun cleanupDatabaseExportFileIfExists() {
        applicationScope.launch(Dispatchers.IO) {
            val email = File(applicationContext.filesDir, "email")
            val zipFile = File(email, "${DatabaseExportHelper.EXPORT_FOLDER_NAME}.zip")
            if (zipFile.exists()) {
                databaseExportHelper.cleanup(zipFile)
            }
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        LogBuffer.i("Application", "Application terminating")
    }

    private fun setupLogging() {
        LogBuffer.setup(File(filesDir, "logs").absolutePath)
        if (BuildConfig.DEBUG) {
            Timber.plant(TimberDebugTree())
        }
    }
}
