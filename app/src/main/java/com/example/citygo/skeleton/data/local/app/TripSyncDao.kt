package com.example.citygo.skeleton.data.local.app

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "trip_sync_state")
data class TripSyncEntity(
    @PrimaryKey val tripId: String,
    val title: String,
    val documentJson: String,
    val remoteVersion: Long = 0,
    val localRevision: Long = 1,
    val dirty: Boolean = true,
    val pendingRequestJson: String? = null,
    val pendingRevision: Long? = null,
    val conflictJson: String? = null,
    val updatedAt: Long,
)

@Entity(tableName = "trip_sync_active")
data class ActiveTripEntity(@PrimaryKey val id: Int = 1, val tripId: String, val needsActivation: Boolean = true)

@Dao
interface TripSyncDao {
    @Query("SELECT * FROM trip_sync_state ORDER BY updatedAt DESC")
    suspend fun all(): List<TripSyncEntity>
    @Query("SELECT * FROM trip_sync_state WHERE tripId=:id")
    suspend fun get(id: String): TripSyncEntity?
    @Query("SELECT tripId FROM trip_sync_active WHERE id=1")
    suspend fun activeId(): String?
    @Query("SELECT * FROM trip_sync_active WHERE id=1")
    suspend fun active(): ActiveTripEntity?
    @Query("SELECT s.* FROM trip_sync_state s JOIN trip_sync_active a ON a.tripId=s.tripId WHERE a.id=1")
    fun observeActive(): Flow<TripSyncEntity?>
    @Upsert suspend fun save(state: TripSyncEntity)
    @Upsert suspend fun setActive(active: ActiveTripEntity)
}
