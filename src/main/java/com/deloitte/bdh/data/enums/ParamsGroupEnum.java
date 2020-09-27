package com.deloitte.bdh.data.enums;

import org.apache.commons.lang3.StringUtils;

public enum ParamsGroupEnum {
    SETTINGS("SETTINGS", "SETTINGS"),
    PROPERTIES("PROPERTIES", "PROPERTIES"),
    COMMENTS("COMMENTS", "COMMENTS"),
    SCHEDULING("SCHEDULING", "SCHEDULING");

    private String key;

    private String value;

    ParamsGroupEnum(String key, String value) {
        this.key = key;
        this.value = value;
    }

    /**
     * 根据缓存key获取描述
     *
     * @param key 环境key
     * @return String
     */
    public static String getValue(String key) {
        ParamsGroupEnum[] enums = ParamsGroupEnum.values();
        for (int i = 0; i < enums.length; i++) {
            if (StringUtils.equals(key, enums[i].getKey())) {
                return enums[i].getvalue();
            }
        }
        return "";
    }

    public String getKey() {
        return key;
    }

    public String getvalue() {
        return value;
    }
}