package com.example.citygo.skeleton.data.sync

import com.google.gson.GsonBuilder
import org.junit.Assert.*
import org.junit.Test

class TripSyncRulesTest {
    @Test fun acknowledgementDoesNotClearEditsMadeDuringUpload() {
        assertTrue(TripSyncRules.stillDirty(localRevision = 7, sentRevision = 6))
        assertFalse(TripSyncRules.stillDirty(localRevision = 7, sentRevision = 7))
    }
    @Test fun remoteCannotReplaceDirtyPendingOrConflictedDocument() {
        assertFalse(TripSyncRules.canAcceptRemote(true, false, false))
        assertFalse(TripSyncRules.canAcceptRemote(false, true, false))
        assertFalse(TripSyncRules.canAcceptRemote(false, false, true))
        assertTrue(TripSyncRules.canAcceptRemote(false, false, false))
    }
    @Test fun persistedRequestRetainsMutationAndExactContentForRetry() {
        val gson = GsonBuilder().serializeNulls().create()
        val request = TripWrite(12, "mutation-id", "成都", TripDocument(), false)
        val persisted = gson.toJson(request)
        assertTrue(persisted.contains("\"plan\":null"))
        assertEquals(request, gson.fromJson(persisted, TripWrite::class.java))
        assertEquals(persisted, gson.toJson(gson.fromJson(persisted, TripWrite::class.java)))
    }
}
