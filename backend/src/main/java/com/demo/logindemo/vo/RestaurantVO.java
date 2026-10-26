package com.demo.logindemo.vo;

public class RestaurantVO {

    private Long restId;
    private String restName;
    private String city;
    private String district;
    private String foodType;
    private String avgPrice;
    private String recommend;
    // 友好展示文本（APP页面展示，不带问号！）
    private String petFriendlyText;

    public Long getRestId() {
        return restId;
    }

    public void setRestId(Long restId) {
        this.restId = restId;
    }

    public String getRestName() {
        return restName;
    }

    public void setRestName(String restName) {
        this.restName = restName;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getDistrict() {
        return district;
    }

    public void setDistrict(String district) {
        this.district = district;
    }

    public String getFoodType() {
        return foodType;
    }

    public void setFoodType(String foodType) {
        this.foodType = foodType;
    }

    public String getAvgPrice() {
        return avgPrice;
    }

    public void setAvgPrice(String avgPrice) {
        this.avgPrice = avgPrice;
    }

    public String getRecommend() {
        return recommend;
    }

    public void setRecommend(String recommend) {
        this.recommend = recommend;
    }

    public String getPetFriendlyText() {
        return petFriendlyText;
    }

    public void setPetFriendlyText(String petFriendlyText) {
        this.petFriendlyText = petFriendlyText;
    }
}