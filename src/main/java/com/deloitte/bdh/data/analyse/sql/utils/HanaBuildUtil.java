package com.deloitte.bdh.data.analyse.sql.utils;

import com.deloitte.bdh.data.analyse.enums.AggregateTypeEnum;
import com.deloitte.bdh.data.analyse.enums.DataModelTypeEnum;
import com.deloitte.bdh.data.analyse.sql.enums.HanaFormatTypeEnum;
import com.deloitte.bdh.data.collation.enums.HanaDataTypeEnum;
import com.google.common.collect.Lists;
import org.apache.commons.lang.StringUtils;

import java.util.List;


public class HanaBuildUtil {
    public static final List<String> MENSURE_DECIMAL_TYPE = Lists.newArrayList(
            HanaDataTypeEnum.BIGINT.getType().toUpperCase(),
            HanaDataTypeEnum.INTEGER.getType().toUpperCase(),
            HanaDataTypeEnum.SMALLINT.getType().toUpperCase(),
            HanaDataTypeEnum.TINYINT.getType().toUpperCase(),
            HanaDataTypeEnum.DECIMAL.getType().toUpperCase(),
            HanaDataTypeEnum.DOUBLE.getType().toUpperCase(),
            HanaDataTypeEnum.SMALLDECIMAL.getType().toUpperCase()
    );

    public static final List<String> DATE_TYPE = Lists.newArrayList(
            HanaDataTypeEnum.DATE.getType().toUpperCase(),
            HanaDataTypeEnum.SECONDDATE.getType().toUpperCase(),
            HanaDataTypeEnum.TIME.getType().toUpperCase(),
            HanaDataTypeEnum.TIMESTAMP.getType().toUpperCase()
    );

    public static final List<String> ESCAPE_CHARACTER = Lists.newArrayList(
            "'", "_", "\"", "%");


    public static String select(String tableName, String field, String quota, String aggregateType,
                                String formatType, String dataType, Integer precision, String alias, String defaultValue) {
        //获取表名+字段名
        String fieldExpress = selectField(tableName, field);
        //判断度量和维度
        if (DataModelTypeEnum.DL.getCode().equals(quota)) {
            fieldExpress = aggregate(fieldExpress, aggregateType);
            if (StringUtils.isNotBlank(dataType) && null != precision && MENSURE_DECIMAL_TYPE.contains(dataType.toUpperCase())) {
                fieldExpress = formatPrecision(fieldExpress, precision);
            }

            if (StringUtils.isNotBlank(defaultValue)) {
                fieldExpress = ifNull(fieldExpress);
            }
        } else {
            if (StringUtils.isNotBlank(formatType) && DATE_TYPE.contains(dataType.toUpperCase())) {
                fieldExpress = format(fieldExpress, formatType);
            }
        }
        return fieldExpress + " AS " + (StringUtils.isBlank(alias) ? field : alias) + " ";
    }

    public static String from(String tableName, String alias) {
        return tableName + " AS " + (StringUtils.isBlank(alias) ? tableName : alias) + " ";

    }

    public static String where(String tableName, String field, String quota, String symbol, String value) {
        if (DataModelTypeEnum.DL.getCode().equals(quota)) {
            return null;
        }
        return condition(selectField(tableName, field), symbol, value);
    }

    public static String where(String tableName, String field, String quota, String formatType, String symbol, String value) {
        if (DataModelTypeEnum.DL.getCode().equals(quota)) {
            return null;
        }
        String fieldExpress = selectField(tableName, field);
        if (StringUtils.isNotBlank(formatType)) {
            fieldExpress = format(fieldExpress, formatType);
        }
        return condition(fieldExpress, symbol, value);
    }

    public static String groupBy(String tableName, String field, String quota, String formatType, String dataType, boolean needGroup) {
        if (DataModelTypeEnum.DL.getCode().equals(quota)) {
            return null;
        }
        if (!needGroup) {
            return null;
        }
        if (StringUtils.isNotBlank(formatType) && DATE_TYPE.contains(dataType.toUpperCase())) {
            return format(field, formatType);
        } else {
            return selectField(tableName, field);
        }
    }

    public static String having(String tableName, String field, String quota, String aggregateType, String symbol, String value) {
        if (DataModelTypeEnum.WD.getCode().equals(quota)) {
            return null;
        }
        return condition(aggregate(selectField(tableName, field), aggregateType), symbol, value);
    }

    public static String orderBy(String tableName, String field, String quota, String aggregateType, String formatType, String orderType) {
        if (StringUtils.isBlank(orderType)) {
            return null;
        }
        String fieldExpress = selectField(tableName, field);
        if (DataModelTypeEnum.DL.getCode().equals(quota)) {
            fieldExpress = aggregate(fieldExpress, aggregateType);
        } else {
            if (StringUtils.isNotBlank(formatType)) {
                fieldExpress = format(fieldExpress, formatType);
            }
        }
        return fieldExpress + " " + orderType;
    }

    private static String selectField(String tableName, String field) {
        return " " + tableName + "." + field + " ";
    }

    private static String aggregate(String field, String aggregateType) {
        return AggregateTypeEnum.get(aggregateType).expression(field);
    }

    private static String ifNull(String field) {
        return " IFNULL( " + field + " ,0)";
    }

    private static String formatPrecision(String field, Integer precision) {
        return " ROUND( " + field + " ," + precision + ")";
    }

    private static String format(String field, String formatType) {
        return HanaFormatTypeEnum.get(formatType).expression(field);
    }

    private static String condition(String field, String symbol, String value) {
        if (StringUtils.isBlank(symbol) || StringUtils.isBlank(value)) {
            return null;
        }
        return field + " " + symbol + " " + value;
    }

    public static String append(String sql, String insertField, int type) {
        StringBuilder sb = new StringBuilder(sql);
        switch (type) {
            case 1:
                //select
                sb.insert(sb.indexOf("FROM"), "," + insertField + " ");
                break;
            case 2:
                //where
                sb.insert(sb.indexOf("1=1") + 3, " AND " + insertField + " ");
                break;
            default:
                return sql;
        }
        return sb.toString();
    }

}