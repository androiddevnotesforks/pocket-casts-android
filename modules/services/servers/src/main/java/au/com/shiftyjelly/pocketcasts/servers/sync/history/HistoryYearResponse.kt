package au.com.shiftyjelly.pocketcasts.servers.sync.history

import au.com.shiftyjelly.pocketcasts.models.to.HistorySyncResponse
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class HistoryYearResponse(
    @Json(name = "count") val count: Long?,
    @Json(name = "history") val history: HistorySyncResponse?,
)
