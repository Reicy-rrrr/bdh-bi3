package com.deloitte.bdh.data.analyse.enums;

import org.apache.commons.lang3.StringUtils;

/**
 * Author:LIJUN
 * Date:13/11/2020
 * Description:
 */
public enum DataImplEnum {
    TABLE_NORMAL("table", "normal", "tableNormalDataImpl", "普通表格"),
    TABLE_CROSS("table", "cross", "crossPivotDataImpl", "交叉透视图"),
    //圆图
    GRAPHICS_PIE("graphics", "pie", "graphicsDataImpl", "饼状图"),
    GRAPHICS_RING("graphics", "ring", "graphicsDataImpl", "水波图"),
    //指标图
    QUOTA_WATER("quota", "water", "quotaWaterDataImpl", "水波图"),
    QUOTA_CORE("quota", "core", "quotaCoreDataImpl", "指标图"),
    QUOTA_DASHBOARD("quota", "Dashboard", "quotaDashboardDataImpl", "仪表盘"),
    //过滤
    DATA_RANGE("filter", "range", "dataRangeDataImpl", "数据范围过滤"),
    BASE_DATA("filter", "base", "normalFilterDataImpl", "普通字段过滤"),
    //图表
    LINE_SIMPLE("line", "simple", "categoryDataImpl", "折线图"),
    LINE_DOUBLE("line", "double", "categoryDataImpl", "双折线图"),
    LINE_AREA("line", "area", "categoryDataImpl", "面积图"),
    //柱状图
    BAR_SIMPLE("column", "simple", "categoryDataImpl", "柱状图"),
    BAR_DUAL("column", "dual", "categoryDataImpl", "柱线混合"),
    BAR_STACK("column", "stack", "categoryDataImpl", "堆叠柱状图"),
    //子弹图
    BAR_PROGRESS("bar", "progress", "barProgressImpl", "子弹图"),
    BAR_RADAR("bar", "radar", "radarDataImpl", "雷达图"),
    BAR_SCATTER("bar", "scatter", "scatterDataImpl", "散点图"),

    WORD_CLOUD("word", "cloud", "wordCloudDataImpl", "词云图"),

    MAP_SYMBOL("map", "symbol", "mapDataImpl", "符号地图"),
    MAP_FILL("map", "fill", "mapDataImpl", "填充地图"),
    ;

    private final String type;

    private final String tableType;

    private final String dataImpl;

    private final String desc;

    DataImplEnum(String type, String tableType, String dataImpl, String desc) {
        this.type = type;
        this.tableType = tableType;
        this.dataImpl = dataImpl;
        this.desc = desc;
    }

    public static String getImpl(String type, String tableType) {
        DataImplEnum[] enums = DataImplEnum.values();
        for (DataImplEnum anEnum : enums) {
            if (StringUtils.equals(anEnum.getType(), type) && StringUtils.equals(anEnum.getTableType(), tableType)) {
                return anEnum.getDataImpl();
            }
        }
        return "";
    }

    public String getType() {
        return type;
    }

    public String getTableType() {
        return tableType;
    }

    public String getDataImpl() {
        return dataImpl;
    }

    public String getDesc() {
        return desc;
    }
}
