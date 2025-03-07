package au.com.shiftyjelly.pocketcasts.analytics

import android.content.SharedPreferences
import androidx.core.content.edit
import java.util.UUID
import timber.log.Timber

abstract class IdentifyingTracker(
    private val preferences: SharedPreferences,
) : Tracker {
    private var anonymousID: String? = null // do not access this variable directly. Use methods.
    protected abstract val anonIdPrefKey: String?
    var userId: String? = null

    abstract override fun track(event: AnalyticsEvent, properties: Map<String, Any>)
    abstract override fun refreshMetadata()

    abstract override fun flush()
    override fun clearAllData() {
        userId = null
    }

    protected fun clearAnonID() {
        anonymousID = null
        if (preferences.contains(anonIdPrefKey)) {
            val editor = preferences.edit()
            editor.remove(anonIdPrefKey)
            editor.apply()
        }
    }

    val anonID: String?
        get() {
            if (anonymousID == null) {
                anonymousID = preferences.getString(anonIdPrefKey, null)
            }
            return anonymousID
        }

    internal fun generateNewAnonID(): String {
        val uuid = UUID.randomUUID().toString().replace("-", "")
        Timber.d("\uD83D\uDD35 New anonID generated in " + this.javaClass.simpleName + ": " + uuid)
        preferences.edit {
            putString(anonIdPrefKey, uuid)
        }
        anonymousID = uuid
        return uuid
    }
}
