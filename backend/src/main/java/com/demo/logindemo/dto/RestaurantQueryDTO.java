package com.demo.logindemo.dto;

public class RestaurantQueryDTO {

    private String city;
    // 前端传宠物筛选编码：CAN_BRING / PARTIAL_ALLOW ……
    private String petCode;

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getPetCode() {
        return petCode;
    }

    public void setPetCode(String petCode) {
        this.petCode = petCode;
    }
}