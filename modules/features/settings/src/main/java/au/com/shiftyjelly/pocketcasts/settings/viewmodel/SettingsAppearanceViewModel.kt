package au.com.shiftyjelly.pocketcasts.settings.viewmodel

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.toLiveData
import androidx.lifecycle.viewModelScope
import au.com.shiftyjelly.pocketcasts.analytics.AnalyticsEvent
import au.com.shiftyjelly.pocketcasts.analytics.AnalyticsTracker
import au.com.shiftyjelly.pocketcasts.models.type.SignInState
import au.com.shiftyjelly.pocketcasts.preferences.Settings
import au.com.shiftyjelly.pocketcasts.repositories.notification.NotificationManager
import au.com.shiftyjelly.pocketcasts.repositories.notification.OnboardingNotificationType
import au.com.shiftyjelly.pocketcasts.repositories.podcast.UserEpisodeManager
import au.com.shiftyjelly.pocketcasts.repositories.user.UserManager
import au.com.shiftyjelly.pocketcasts.settings.onboarding.OnboardingUpgradeSource
import au.com.shiftyjelly.pocketcasts.ui.helper.AppIcon
import au.com.shiftyjelly.pocketcasts.ui.theme.Theme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsAppearanceViewModel @Inject constructor(
    userManager: UserManager,
    private val settings: Settings,
    val userEpisodeManager: UserEpisodeManager,
    val theme: Theme,
    private val appIcon: AppIcon,
    private val analyticsTracker: AnalyticsTracker,
    private val notificationManager: NotificationManager,
) : ViewModel() {

    val signInState: LiveData<SignInState> = userManager.getSignInState().toLiveData()
    val createAccountState = MutableLiveData<SettingsAppearanceState>().apply { value = SettingsAppearanceState.Empty }
    val showArtworkOnLockScreen = settings.showArtworkOnLockScreen.flow
    val artworkConfiguration = settings.artworkConfiguration.flow

    var changeThemeType: Pair<Theme.ThemeType?, Theme.ThemeType?> = Pair(null, null)
    var changeAppIconType: Pair<AppIcon.AppIconType?, AppIcon.AppIconType?> = Pair(null, null)

    fun onShown() {
        analyticsTracker.track(AnalyticsEvent.SETTINGS_APPEARANCE_SHOWN)
    }

    fun onRefreshArtwork() {
        analyticsTracker.track(AnalyticsEvent.SETTINGS_APPEARANCE_REFRESH_ALL_ARTWORK_TAPPED)
    }

    fun onThemeChanged(theme: Theme.ThemeType) {
        analyticsTracker.track(
            AnalyticsEvent.SETTINGS_APPEARANCE_THEME_CHANGED,
            mapOf(
                "value" to when (theme) {
                    Theme.ThemeType.LIGHT -> "default_light"
                    Theme.ThemeType.DARK -> "default_dark"
                    Theme.ThemeType.ROSE -> "rose"
                    Theme.ThemeType.INDIGO -> "indigo"
                    Theme.ThemeType.EXTRA_DARK -> "extra_dark"
                    Theme.ThemeType.DARK_CONTRAST -> "dark_contrast"
                    Theme.ThemeType.LIGHT_CONTRAST -> "light_contrast"
                    Theme.ThemeType.ELECTRIC -> "electric"
                    Theme.ThemeType.CLASSIC_LIGHT -> "classic"
                    Theme.ThemeType.RADIOACTIVE -> "radioactive"
                },
            ),
        )
        viewModelScope.launch {
            notificationManager.updateUserFeatureInteraction(OnboardingNotificationType.Themes)
        }
    }

    fun loadThemesAndIcons() {
        createAccountState.postValue(SettingsAppearanceState.ThemesAndIconsLoading)
        val appIcons = appIcon.allAppIconTypes.toList()

        createAccountState.postValue(
            SettingsAppearanceState.ThemesAndIconsLoaded(
                theme.activeTheme,
                theme.allThemes.toList(),
                appIcon.activeAppIcon,
                appIcons,
            ),
        )
    }

    fun updateGlobalIcon(appIconType: AppIcon.AppIconType) {
        appIcon.activeAppIcon = appIconType
        appIcon.enableSelectedAlias(appIconType)
        analyticsTracker.track(
            AnalyticsEvent.SETTINGS_APPEARANCE_APP_ICON_CHANGED,
            mapOf(
                "value" to when (appIconType) {
                    AppIcon.AppIconType.DEFAULT -> "default"
                    AppIcon.AppIconType.DARK -> "dark"
                    AppIcon.AppIconType.ROUND_LIGHT -> "round_light"
                    AppIcon.AppIconType.ROUND_DARK -> "round_dark"
                    AppIcon.AppIconType.INDIGO -> "indigo"
                    AppIcon.AppIconType.ROSE -> "rose"
                    AppIcon.AppIconType.CAT -> "pocket_cats"
                    AppIcon.AppIconType.REDVELVET -> "red_velvet"
                    AppIcon.AppIconType.PLUS -> "plus"
                    AppIcon.AppIconType.CLASSIC -> "classic"
                    AppIcon.AppIconType.ELECTRIC_BLUE -> "electric_blue"
                    AppIcon.AppIconType.ELECTRIC_PINK -> "electric_pink"
                    AppIcon.AppIconType.RADIOACTIVE -> "radioactive"
                    AppIcon.AppIconType.HALLOWEEN -> "halloween"
                    AppIcon.AppIconType.PATRON_CHROME -> "patron_chrome"
                    AppIcon.AppIconType.PATRON_ROUND -> "patron_round"
                    AppIcon.AppIconType.PATRON_GLOW -> "patron_glow"
                    AppIcon.AppIconType.PATRON_DARK -> "patron_dark"
                    AppIcon.AppIconType.PRIDE -> "pride_2023"
                },
            ),
        )
    }

    fun updateUpNextDarkTheme(value: Boolean) {
        settings.useDarkUpNextTheme.set(value, updateModifiedAt = true)
        analyticsTracker.track(
            AnalyticsEvent.SETTINGS_APPEARANCE_USE_DARK_UP_NEXT_TOGGLED,
            mapOf("enabled" to value),
        )
    }

    fun updateWidgetForDynamicColors(value: Boolean) {
        settings.useDynamicColorsForWidget.set(value, updateModifiedAt = true)
        analyticsTracker.track(
            AnalyticsEvent.SETTINGS_APPEARANCE_USE_DYNAMIC_COLORS_WIDGET_TOGGLED,
            mapOf("enabled" to value),
        )
    }

    fun updateShowArtworkOnLockScreen(value: Boolean) {
        settings.showArtworkOnLockScreen.set(value, updateModifiedAt = true)
        analyticsTracker.track(
            AnalyticsEvent.SETTINGS_APPEARANCE_SHOW_ARTWORK_ON_LOCK_SCREEN_TOGGLED,
            mapOf("enabled" to value),
        )
    }

    fun updateUseEpisodeArtwork(value: Boolean) {
        val currentConfiguration = settings.artworkConfiguration.value
        settings.artworkConfiguration.set(currentConfiguration.copy(useEpisodeArtwork = value), updateModifiedAt = true)
        analyticsTracker.track(
            AnalyticsEvent.SETTINGS_APPEARANCE_USE_EPISODE_ARTWORK_TOGGLED,
            mapOf("enabled" to value),
        )
    }

    fun updateChangeThemeType(value: Pair<Theme.ThemeType?, Theme.ThemeType?>) {
        changeThemeType = value
    }

    fun updateChangeAppIconType(value: Pair<AppIcon.AppIconType?, AppIcon.AppIconType?>) {
        changeAppIconType = value
    }

    fun useAndroidLightDarkMode(use: Boolean, activity: AppCompatActivity?) {
        theme.setUseSystemTheme(use, activity)
        analyticsTracker.track(
            AnalyticsEvent.SETTINGS_APPEARANCE_FOLLOW_SYSTEM_THEME_TOGGLED,
            mapOf("enabled" to use),
        )
    }

    fun onUpgradeBannerDismissed(source: OnboardingUpgradeSource) {
        analyticsTracker.track(AnalyticsEvent.UPGRADE_BANNER_DISMISSED, mapOf("source" to source.analyticsValue))
    }
}

sealed class SettingsAppearanceState {
    object Empty : SettingsAppearanceState()
    object ThemesAndIconsLoading : SettingsAppearanceState()
    data class ThemesAndIconsLoaded(
        val currentThemeType: Theme.ThemeType,
        val themeList: List<Theme.ThemeType>,
        val currentAppIcon: AppIcon.AppIconType,
        val iconList: List<AppIcon.AppIconType>,
    ) : SettingsAppearanceState()
}
