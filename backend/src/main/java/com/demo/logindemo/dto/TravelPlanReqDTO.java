package com.demo.logindemo.dto;

import lombok.Data;
import java.util.List;

@Data
public class TravelPlanReqDTO {
    // 目的地城市
    private String city;
    // 出行天数
    private Integer dayNum;
    // 人均预算，可以为null（代表不限预算）
    private Integer budget;
    // 游玩偏好集合，例 ["宠物友好","休闲","打卡拍照","美食","自然风光"]
    private List<String> preferenceList;
    // 是否优先宠物友好地点
    private Boolean petFriendlyFlag;
    // 用户自定义备注
    private String remark;
}