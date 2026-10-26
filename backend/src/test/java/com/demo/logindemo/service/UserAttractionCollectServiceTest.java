package com.demo.logindemo.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.demo.logindemo.dto.AttractionCardResp;
import com.demo.logindemo.entity.Attraction;
import com.demo.logindemo.entity.UserAttractionCollect;
import com.demo.logindemo.mapper.AttractionMapper;
import com.demo.logindemo.mapper.UserAttractionCollectMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.server.ResponseStatusException;
import java.util.Date;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserAttractionCollectServiceTest {
    private UserAttractionCollectMapper collects;
    private AttractionMapper attractions;
    private AttractionQueryService queries;
    private UserAttractionCollectService service;

    @BeforeEach void setup() {
        collects = mock(UserAttractionCollectMapper.class);
        attractions = mock(AttractionMapper.class);
        queries = mock(AttractionQueryService.class);
        service = new UserAttractionCollectService(collects, attractions, queries);
    }

    private UserAttractionCollect record(long id, int attractionId) {
        UserAttractionCollect r = new UserAttractionCollect();
        r.setId(id); r.setUserId(9L); r.setAttractionId(attractionId); r.setCreateTime(new Date());
        return r;
    }
    private void activeAttraction() {
        Attraction a = new Attraction(); a.setId(10L); a.setStatus("ACTIVE");
        when(attractions.selectById(10)).thenReturn(a);
    }

    @Test void newCollectionBelongsToAuthenticatedUserAndRepeatsDoNotInsert() {
        activeAttraction();
        when(collects.insert(any(UserAttractionCollect.class))).thenReturn(1);
        assertTrue(service.collect(9L, 10).isCollected());
        ArgumentCaptor<UserAttractionCollect> saved = ArgumentCaptor.forClass(UserAttractionCollect.class);
        verify(collects).insert(saved.capture());
        assertEquals(9L, saved.getValue().getUserId());
        assertEquals(10, saved.getValue().getAttractionId());
        clearInvocations(collects);
        when(collects.selectOne(any(Wrapper.class))).thenReturn(record(1, 10));
        assertTrue(service.collect(9L, 10).isCollected());
        verify(collects, never()).insert(any(UserAttractionCollect.class));
    }

    @Test void concurrentDuplicateIsIdempotentWhenLatestRecordExists() {
        activeAttraction();
        when(collects.selectOne(any(Wrapper.class))).thenReturn(null, record(1, 10));
        when(collects.insert(any(UserAttractionCollect.class))).thenThrow(new DuplicateKeyException("duplicate"));
        assertTrue(service.collect(9L, 10).isCollected());
    }

    @Test void listContainsRealCardFieldsAndUnavailablePlaceholderWithoutLeakingCardData() {
        when(collects.selectCount(any(Wrapper.class))).thenReturn(2L);
        when(collects.selectList(any(Wrapper.class))).thenReturn(List.of(record(1, 10), record(2, 9)));
        AttractionCardResp card = new AttractionCardResp();
        card.setAttractionId(10L); card.setName("青岩古镇景区");
        card.setCoverUrl("https://example.test/cover.jpg"); card.setCanNavigate(true);
        when(queries.getCardsByIds(List.of(10L, 9L))).thenReturn(List.of(card));
        var page = service.getMyCollections(9L, 1, 20);
        assertEquals(2, page.getTotal()); assertFalse(page.isHasMore());
        var available = page.getRecords().get(0);
        assertTrue(available.isAvailable()); assertTrue(available.isCanNavigate());
        assertEquals(1L, available.getCollectId()); assertEquals(card.getCoverUrl(), available.getCoverUrl());
        var unavailable = page.getRecords().get(1);
        assertFalse(unavailable.isAvailable()); assertFalse(unavailable.isCanNavigate());
        assertEquals(9L, unavailable.getAttractionId()); assertNull(unavailable.getCoverUrl());
        var json = new ObjectMapper().valueToTree(page);
        assertEquals("青岩古镇景区", json.path("records").get(0).path("name").asText());
        assertFalse(json.path("records").get(0).has("records"));
    }

    @Test void emptyPageAndInvalidInputsAreHandledBeforeReadingCards() {
        when(collects.selectCount(any(Wrapper.class))).thenReturn(0L);
        assertTrue(service.getMyCollections(9L, 1, 20).getRecords().isEmpty());
        verifyNoInteractions(queries);
        assertThrows(ResponseStatusException.class, () -> service.getMyCollections(null, 1, 20));
        assertThrows(IllegalArgumentException.class, () -> service.getMyCollections(9L, 0, 20));
        assertThrows(IllegalArgumentException.class, () -> service.getMyCollections(9L, Long.MAX_VALUE, 50));
    }

    @Test void cancelledOrAlreadyAbsentRelationshipReturnsUncollectedEvenForRemovedAttraction() {
        assertFalse(service.cancel(9L, 10).isCollected());
        verify(collects).delete(any(Wrapper.class));
        verifyNoInteractions(attractions);
    }
}
