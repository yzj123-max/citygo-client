package com.demo.logindemo.trips;

import com.demo.logindemo.interceptor.JwtInterceptor;
import com.demo.logindemo.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.demo.logindemo.trips.TripContracts.*;

class TripSyncHttpTest {
    private final String id="7aed9392-2b14-47ad-b3e4-d9d8e9344c52";
    @Test void jwtIdentityIsUsedAndOtherOwnerReceives404() throws Exception {
        var service=mock(TripSyncService.class);var json=new ObjectMapper();
        var trip=new Snapshot(id,2,"成都",json.readTree("{\"plan\":null,\"messages\":[]}"),1);
        when(service.get(9L,id)).thenReturn(trip);
        when(service.get(10L,id)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        var mvc=MockMvcBuilders.standaloneSetup(new TripSyncController(service)).addInterceptors(new JwtInterceptor()).build();
        mvc.perform(get("/api/me/trips/"+id).header("Authorization","Bearer "+JwtUtil.generateToken(9L)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(2));
        mvc.perform(get("/api/me/trips/"+id).header("Authorization","Bearer "+JwtUtil.generateToken(10L)))
                .andExpect(status().isNotFound());
        verify(service).get(9L,id);verify(service).get(10L,id);
    }
    @Test void unauthenticatedRequestCannotReachService() throws Exception {
        var service=mock(TripSyncService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new TripSyncController(service)).addInterceptors(new JwtInterceptor()).build();
        mvc.perform(get("/api/me/trips")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
        verifyNoInteractions(service);
    }
    @Test void conflictIsHttp409WithRemoteSnapshot() throws Exception {
        var service=mock(TripSyncService.class);var json=new ObjectMapper();
        var trip=new Snapshot(id,3,"另一设备",json.readTree("{\"plan\":null,\"messages\":[]}"),1);
        when(service.save(eq(9L),eq(id),any())).thenThrow(new Conflict(trip));
        var mvc=MockMvcBuilders.standaloneSetup(new TripSyncController(service)).addInterceptors(new JwtInterceptor()).build();
        var write=new Write(2,"7aed9392-2b14-47ad-b3e4-d9d8e9344c53","本地",trip.document(),false);
        mvc.perform(put("/api/me/trips/"+id).header("Authorization","Bearer "+JwtUtil.generateToken(9L))
                .contentType("application/json").content(json.writeValueAsString(write)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.data.version").value(3));
    }
}
