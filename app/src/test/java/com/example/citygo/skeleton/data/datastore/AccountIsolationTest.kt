package com.example.citygo.skeleton.data.datastore

import androidx.lifecycle.ViewModel
import com.example.citygo.skeleton.data.AccountModelStore
import org.junit.Assert.*
import org.junit.Test

class AccountIsolationTest {
    @Test fun databaseFilesAreStablePrivateAndSeparateFromLegacy() {
        val a = AccountStorage.keyFor(" 13800000001 ")
        val b = AccountStorage.keyFor("13800000002")
        assertEquals(a, AccountStorage.keyFor("13800000001"))
        assertNotEquals(a, b)
        assertNotEquals(a, AccountStorage.keyFor(null))
        assertNotEquals("citygo_app.db", AccountStorage.databaseName(a))
        assertFalse(AccountStorage.databaseName(a).contains("13800000001"))
    }

    @Test fun invalidPartitionCannotEscapeDatabaseDirectory() {
        assertThrows(IllegalArgumentException::class.java) { AccountStorage.databaseName("../citygo_app") }
    }

    @Test fun oldResponseIsRejectedEvenAfterSwitchingBackToSameAccount() {
        val fence = SessionFence()
        val oldA = fence.update("13800000001", "token-a")
        fence.update("13800000002", "token-b")
        val newA = fence.update("13800000001", "token-a")
        assertFalse(fence.isCurrent(oldA))
        assertTrue(fence.isCurrent(newA))
        assertEquals(oldA.storageKey, newA.storageKey)
    }

    @Test fun logoutAndTokenRotationInvalidateRequestsButKeepAccountData() {
        val fence = SessionFence()
        val first = fence.update("13800000001", "first")
        assertEquals(first, fence.update("13800000001", "first"))
        val renewed = fence.update("13800000001", "second")
        assertFalse(fence.isCurrent(first))
        assertEquals(first.storageKey, renewed.storageKey)
        val loggedOut = fence.update("13800000001", "")
        assertEquals("guest", loggedOut.storageKey)
        assertNull(loggedOut.token)
        assertFalse(fence.isCurrent(renewed))
        assertFalse(renewed.toString().contains("second"))
    }

    @Test fun switchingAccountClearsOldViewModelsAndDoesNotResurrectTheirMemory() {
        val stores = AccountModelStore()
        val a = stores.forAccount("a:1")
        val marker = TrackingViewModel()
        a.viewModelStore.put("marker", marker)
        assertSame(a, stores.forAccount("a:1"))
        assertFalse(marker.cleared)
        val b = stores.forAccount("b:2")
        assertTrue(marker.cleared)
        assertNotSame(a, b)
        val again = stores.forAccount("a:3")
        assertNotSame(a, again)
        assertNull(again.viewModelStore["marker"])
    }

    private class TrackingViewModel : ViewModel() {
        var cleared = false
        override fun onCleared() { cleared = true }
    }
}
