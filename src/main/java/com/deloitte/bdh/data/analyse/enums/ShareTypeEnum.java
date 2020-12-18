package com.deloitte.bdh.data.analyse.enums;


import org.apache.commons.lang3.StringUtils;

public enum ShareTypeEnum {

    ZERO("0", "不公开"),
    ONE("1", "公开"),
    TWO("2", "加密公开"),
    FOUR("4", "订阅公开"),
    FIVE("5", "分享"),

    ;

    private String key;

    private String value;

    ShareTypeEnum(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public static String getValue(String key) {
        if (null != key && !"".equals(key)) {
            ShareTypeEnum[] enums = ShareTypeEnum.values();
            for (int i = 0; i < enums.length; i++) {
                if (StringUtils.equals(key, enums[i].getKey())) {
                    return enums[i].getValue();
                }
            }
        }
        return "未执行";
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }
}
