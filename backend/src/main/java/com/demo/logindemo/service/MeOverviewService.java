package com.demo.logindemo.service;
import com.demo.logindemo.dto.MeOverviewResp;
import com.demo.logindemo.mapper.PostCollectQueryMapper;
import com.demo.logindemo.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
@Service
public class MeOverviewService {
 private final UserMapper users;
 private final PostCollectQueryMapper collections;
 public MeOverviewService(UserMapper users,PostCollectQueryMapper collections){this.users=users;this.collections=collections;}
 @Transactional(readOnly=true)
 public MeOverviewResp getOverview(Long userId){
  if(userId==null||userId<=0)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"请先登录");
  var user=users.selectById(userId);
  if(user==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"登录用户不存在，请重新登录");
  MeOverviewResp resp=new MeOverviewResp();
  resp.setUserId(user.getId());resp.setNickname(user.getNickname());resp.setAvatarUrl(user.getAvatarUrl());
  // 与收藏列表共用可见性SQL，删除或当前无权查看的帖子不计入。
  resp.setPostCollectCount(collections.countVisible(userId));
  return resp;
 }
}
