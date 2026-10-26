package com.demo.logindemo.trips;

import com.demo.logindemo.entity.User;
import com.demo.logindemo.mapper.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.demo.logindemo.trips.TripContracts.*;

class TripSyncServiceTest {
    private final TripStore store = mock(TripStore.class);
    private final UserMapper users = mock(UserMapper.class);
    private final ObjectMapper json = new ObjectMapper();
    private final TripSyncService service = new TripSyncService(store, users, json);
    private final String id = "7aed9392-2b14-47ad-b3e4-d9d8e9344c52";
    private final String mutation = "7aed9392-2b14-47ad-b3e4-d9d8e9344c53";
    private Write write(long version) throws Exception {
        return new Write(version, mutation, "成都", json.readTree("{\"plan\":null,\"messages\":[]}"), true);
    }
    private void login() { var u = new User(); u.setId(9L); when(users.selectById(9L)).thenReturn(u); }

    @Test void missingIdentityCannotReadOrWrite() throws Exception {
        assertEquals(401, assertThrows(ResponseStatusException.class, () -> service.current(null)).getStatusCode().value());
        assertEquals(401, assertThrows(ResponseStatusException.class, () -> service.save(9L,id,write(0))).getStatusCode().value());
        verifyNoInteractions(store);
    }
    @Test void otherUsersTripIsHiddenForReadWriteAndActivation() throws Exception {
        login(); var remote = new Snapshot(id, 1,"他人行程",write(0).document(), 1);
        when(store.find(id)).thenReturn(new Stored(10L,remote));
        assertEquals(404,assertThrows(ResponseStatusException.class,()->service.get(9L,id)).getStatusCode().value());
        assertEquals(404,assertThrows(ResponseStatusException.class,()->service.save(9L,id,write(1))).getStatusCode().value());
        assertEquals(404,assertThrows(ResponseStatusException.class,()->service.activate(9L,id)).getStatusCode().value());
        verify(store,never()).update(anyLong(),anyLong(),any());
        verify(store,never()).activate(anyLong(),anyString());
    }
    @Test void staleVersionReturnsRemoteWithoutOverwriting() throws Exception {
        login(); var remote = new Snapshot(id, 3,"成都",write(0).document(),1);
        when(store.find(id)).thenReturn(new Stored(9L,remote));
        var conflict = assertThrows(Conflict.class,()->service.save(9L,id,write(2)));
        assertEquals(remote, conflict.getRemote());
        verify(store,never()).update(anyLong(),anyLong(),any());
        verify(store,never()).record(anyLong(),anyString(),anyString(),any());
    }
    @Test void lostAcknowledgementCanBeRetriedWithoutAnotherVersion() throws Exception {
        login(); var req = write(0);
        var first = service.save(9L,id,req);
        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(store).record(eq(9L),eq(mutation),captor.capture(),eq(first));
        when(store.find(id)).thenReturn(new Stored(9L,first));
        when(store.receipt(9L,mutation)).thenReturn(new Receipt(captor.getValue(),first));
        assertEquals(first, service.save(9L,id,req));
        verify(store,times(1)).insert(eq(9L),any());
        verify(store,times(1)).activate(9L,id);
        assertEquals(400,assertThrows(ResponseStatusException.class,()->service.save(9L,id,write(1))).getStatusCode().value());
    }
    @Test void successfulWriteAdvancesExactlyOneVersion() throws Exception {
        login(); var remote = new Snapshot(id,4,"成都",write(0).document(),1);
        when(store.find(id)).thenReturn(new Stored(9L,remote));
        when(store.update(eq(9L),eq(4L),any())).thenReturn(true);
        assertEquals(5,service.save(9L,id,write(4)).version());
        verify(store).update(eq(9L),eq(4L),any());
    }
    @Test void malformedDocumentCannotReachDatabase() throws Exception {
        login(); var bad = new Write(0,mutation,"成都",json.readTree("{\"messages\":{}}"),true);
        assertEquals(400,assertThrows(ResponseStatusException.class,()->service.save(9L,id,bad)).getStatusCode().value());
        verifyNoInteractions(store);
    }
}
