package com.demo.logindemo.service;
import com.demo.logindemo.mapper.*;
import com.demo.logindemo.entity.User;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.springframework.web.server.ResponseStatusException;
class MeOverviewServiceTest {
 @Test void countsUseAuthenticatedOwnerAndDoNotInventCloudCounts(){
  var users=mock(UserMapper.class);var collects=mock(PostCollectQueryMapper.class);
  var u=new User();u.setId(9L);u.setNickname("用户9");u.setPassword("must-not-leak");
  when(users.selectById(9L)).thenReturn(u);when(collects.countVisible(9L)).thenReturn(3L);
  var r=new MeOverviewService(users,collects).getOverview(9L);
  assertEquals(9L,r.getUserId());assertEquals(3L,r.getPostCollectCount());assertNull(r.getPetCount());
  assertEquals("LOCAL_PENDING_SYNC",r.getDataSources().get("pets"));
  verify(collects).countVisible(9L);verify(collects,never()).countVisible(10L);
 }
 @Test void missingAccountCannotReadAnyCounts(){
  var users=mock(UserMapper.class);var collects=mock(PostCollectQueryMapper.class);
  var s=new MeOverviewService(users,collects);
  assertThrows(ResponseStatusException.class,()->s.getOverview(null));
  assertThrows(ResponseStatusException.class,()->s.getOverview(10L));verifyNoInteractions(collects);
 }
}
