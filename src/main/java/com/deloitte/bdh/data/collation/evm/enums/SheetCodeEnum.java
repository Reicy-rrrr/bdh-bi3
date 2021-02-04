package com.deloitte.bdh.data.collation.evm.enums;


public enum SheetCodeEnum {

    zcfzb("zcfzb", "资产负债表"),
    lrb("lrb", "利润表"),
    xjllb("xjllb", "现金流量表"),
    kmyeb("kmyeb", "科目余额表"),
    yszkb("yszkb", "应收账款账龄表");

    private String name;

    private String value;


    SheetCodeEnum(String name, String value) {
        this.name = name;
        this.value = value;
    }


    public String getValue() {
        return value;
    }

    public String getName() {
        return name;
    }

}