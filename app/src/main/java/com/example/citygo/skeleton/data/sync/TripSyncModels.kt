package com.example.citygo.skeleton.data.sync

import com.example.citygo.skeleton.data.model.AiPlanOptions
import com.example.citygo.skeleton.data.model.TravelPlan
import com.example.citygo.skeleton.data.model.chat.ChatMessage

data class TripDocument(
    val plan: TravelPlan? = null,
    val messages: List<ChatMessage> = emptyList(),
    val options: AiPlanOptions? = null,
)
data class CloudTrip(val tripId: String, val version: Long, val title: String,
    val document: TripDocument, val updatedAt: Long)
data class TripSummary(val tripId: String, val version: Long, val title: String, val updatedAt: Long)
data class TripWrite(val baseVersion: Long, val mutationId: String, val title: String,
    val document: TripDocument, val makeCurrent: Boolean)
data class TripSyncUiState(
    val message: String = "已保存到本机",
    val busy: Boolean = false,
    val activeTripId: String? = null,
    val conflicts: List<String> = emptyList(),
    val trips: List<TripSummary> = emptyList(),
)

/** ACK 只能确认发送时的那份修订；发送期间新增的编辑必须继续保留为待上传。 */
object TripSyncRules {
    fun stillDirty(localRevision: Long, sentRevision: Long): Boolean = localRevision != sentRevision
    fun canAcceptRemote(dirty: Boolean, pending: Boolean, conflict: Boolean): Boolean =
        !dirty && !pending && !conflict
}
