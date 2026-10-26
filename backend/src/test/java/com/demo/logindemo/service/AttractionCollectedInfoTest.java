package com.demo.logindemo.service;
import com.demo.logindemo.entity.Attraction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AttractionCollectedInfoTest {
 @Test void preservesCollectedTextScoreAndSourceWithoutInventingVerificationDate() {
  Attraction a = new Attraction();
  a.setTicketPrice("免费入城，青龙洞60元");a.setDiscountPolicy("子景点各自优惠");
  a.setOpenTime("全天开放");a.setFeature("太极图上的千年水城");
  a.setPetFriendlyLevel("可直接入园");a.setPetNote1("5");a.setPetPolicy("游客记录可以牵绳携宠");
  a.setSource("携程携宠游记 2026-04");a.setVerifyTime("2026年10月");
  var result=AttractionDetailService.mapCollectedInfo(a);
  assertEquals(a.getTicketPrice(),result.getTicketPrice());
  assertEquals(a.getDiscountPolicy(),result.getDiscountPolicy());
  assertEquals(a.getOpenTime(),result.getOpenTime());
  assertEquals(a.getFeature(),result.getFeature());
  assertEquals("5",result.getPetFriendlinessScore());
  assertEquals("可直接入园",result.getPetFriendlyLevel());
  assertEquals(a.getSource(),result.getPetPolicySource());
  assertEquals("2026年10月",result.getRecordedReviewTime());
 }
}
