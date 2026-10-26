package com.demo.logindemo.enums;

public enum PetFriendlyEnum {
    CAN_BRING("CAN_BRING", "?可带宠", "可带宠"),
    PARTIAL_ALLOW("PARTIAL_ALLOW", "??仅户外或部分区", "仅户外或部分区域可带宠物"),
    FORBIDDEN("FORBIDDEN", "?禁止", "禁止携带宠物"),
    NO("NO", "不可带宠物", "不可带宠物"),
    UNKNOWN("UNKNOWN", "?未查到", "未查到"),
    NOT_MENTION("NOT_MENTION", "未提及", "未提及");

    private final String code;
    private final String dbValue;
    private final String showText;

    PetFriendlyEnum(String code, String dbValue, String showText) {
        this.code = code;
        this.dbValue = dbValue;
        this.showText = showText;
    }

    public String getCode() {
        return code;
    }

    public String getDbValue() {
        return dbValue;
    }

    public String getShowText() {
        return showText;
    }

    public static PetFriendlyEnum getByCode(String code) {
        if (code == null) {
            return null;
        }
        for (PetFriendlyEnum e : values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }
}