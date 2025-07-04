package au.com.shiftyjelly.pocketcasts.utils.di

import au.com.shiftyjelly.pocketcasts.utils.config.FirebaseConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class FirebaseModule {
    @Provides
    @Singleton
    fun provideFirebaseRemoteConfig(): FirebaseRemoteConfig {
        return FirebaseRemoteConfig.getInstance().apply {
            val config = FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(2L * 60L * 60L)
                .build()
            setConfigSettingsAsync(config)
            setDefaultsAsync(FirebaseConfig.defaults)
        }
    }
}
