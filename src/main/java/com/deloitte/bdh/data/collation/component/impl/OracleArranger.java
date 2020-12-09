package com.deloitte.bdh.data.collation.component.impl;

import com.deloitte.bdh.common.exception.BizException;
import com.deloitte.bdh.data.collation.component.ArrangerSelector;
import com.deloitte.bdh.data.collation.component.constant.ComponentCons;
import com.deloitte.bdh.data.collation.component.model.*;
import com.deloitte.bdh.data.collation.database.po.TableField;
import com.deloitte.bdh.data.collation.enums.ComponentTypeEnum;
import com.deloitte.bdh.data.collation.enums.DataTypeEnum;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 整理组件
 *
 * @author chenghzhang
 * @date 2020/10/26
 */
@Slf4j
@Service("oracleArranger")
public class OracleArranger implements ArrangerSelector {
    @Override
    public List<ArrangeResultModel> split(FieldMappingModel fromFieldMapping, String separator, String fromTable, ComponentTypeEnum fromType) {
        String desc = fromFieldMapping.getTableField().getDesc();
        if (StringUtils.isBlank(desc)) {
            desc = fromFieldMapping.getFinalFieldName();
        }

        String leftField = fromFieldMapping.getFinalFieldName() + "_left";
        String leftFieldTemp = getColumnAlias(fromFieldMapping.getOriginalTableName() + sql_key_separator + leftField);
        FieldMappingModel leftMapping = fromFieldMapping.clone();
        leftMapping.setFinalFieldDesc(desc + "(left)");
        leftMapping.setTempFieldName(leftFieldTemp);
        leftMapping.setFinalFieldName(leftField);
        leftMapping.getTableField().setName(leftField);
        leftMapping.getTableField().setDesc(leftMapping.getFinalFieldDesc());

        String rightField = fromFieldMapping.getFinalFieldName() + "_right";
        String rightFieldTemp = getColumnAlias(fromFieldMapping.getOriginalTableName() + sql_key_separator + rightField);
        FieldMappingModel rightMapping = fromFieldMapping.clone();
        rightMapping.setTempFieldName(rightFieldTemp);
        rightMapping.setFinalFieldName(rightField);
        rightMapping.getTableField().setName(rightField);
        rightMapping.getTableField().setDesc(rightMapping.getFinalFieldDesc());

        String fromField = getFromField(fromFieldMapping, fromType);
        String leftSql = "SUBSTR(" + fromField + ", 1, INSTR(" + fromField + ", '" + separator + "', 1, 1) - 1) AS " + leftFieldTemp;
        String rightSql = "SUBSTR(" + fromField + ", INSTR(" + fromField + ", '" + separator + "', 1, 1) + 1, LENGTH(" + fromField + ")) AS " + rightFieldTemp;

        List<ArrangeResultModel> result = Lists.newArrayList();
        result.add(new ArrangeResultModel(leftMapping.getTempFieldName(), leftSql, true, leftMapping));
        result.add(new ArrangeResultModel(rightMapping.getTempFieldName(), rightSql, true, rightMapping));
        return result;
    }

    @Override
    public List<ArrangeResultModel> split(FieldMappingModel fromFieldMapping, int length, String fromTable, ComponentTypeEnum fromType) {
        String desc = fromFieldMapping.getTableField().getDesc();
        if (StringUtils.isBlank(desc)) {
            desc = fromFieldMapping.getFinalFieldName();
        }

        String leftField = fromFieldMapping.getFinalFieldName() + "_left";
        String leftFieldTemp = getColumnAlias(fromFieldMapping.getOriginalTableName() + sql_key_separator + leftField);
        FieldMappingModel leftMapping = fromFieldMapping.clone();
        leftMapping.setFinalFieldDesc(desc + "(left)");
        leftMapping.setTempFieldName(leftFieldTemp);
        leftMapping.setFinalFieldName(leftField);
        leftMapping.getTableField().setName(leftField);
        leftMapping.getTableField().setDesc(leftMapping.getFinalFieldDesc());

        String rightField = fromFieldMapping.getFinalFieldName() + "_right";
        String rightFieldTemp = getColumnAlias(fromFieldMapping.getOriginalTableName() + sql_key_separator + rightField);
        FieldMappingModel rightMapping = fromFieldMapping.clone();
        rightMapping.setFinalFieldDesc(desc + "(right)");
        rightMapping.setTempFieldName(rightFieldTemp);
        rightMapping.setFinalFieldName(rightField);
        rightMapping.getTableField().setName(rightField);
        rightMapping.getTableField().setDesc(rightMapping.getFinalFieldDesc());

        String fromField = getFromField(fromFieldMapping, fromType);
        String leftSql = "SUBSTR(" + fromField + ", 1, " + length + ") AS " + leftFieldTemp;
        String rightSql = "SUBSTR(" + fromField + ", " + length + " + 1, LENGTH(" + fromField + ")) AS " + rightFieldTemp;

        List<ArrangeResultModel> result = Lists.newArrayList();
        result.add(new ArrangeResultModel(leftMapping.getTempFieldName(), leftSql, true, leftMapping));
        result.add(new ArrangeResultModel(rightMapping.getTempFieldName(), rightSql, true, rightMapping));
        return result;
    }

    @Override
    public ArrangeResultModel replace(FieldMappingModel fromFieldMapping, List<ArrangeReplaceContentModel> contents, String fromTable, ComponentTypeEnum fromType) {
        String fromField = getFromField(fromFieldMapping, fromType);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < contents.size(); i++) {
            ArrangeReplaceContentModel replaceContent = contents.get(i);
            String source = replaceContent.getSource();
            String target = replaceContent.getTarget();
            if (i == 0) {
                builder.append("REPLACE (" + fromField + ", '" + source + "', '" + target + "' )");
            } else {
                builder.append("REPLACE (" + builder.toString() + ", '" + source + "', '" + target + "' )");
            }
        }
        builder.append(" AS ");
        builder.append(fromFieldMapping.getTempFieldName());
        return new ArrangeResultModel(fromFieldMapping.getTempFieldName(), builder.toString(), false, fromFieldMapping);
    }

    @Override
    public ArrangeResultModel combine(FieldMappingModel leftMapping, FieldMappingModel rightMapping, String connector, String fromTable, ComponentTypeEnum fromType) {
        String fieldName = leftMapping.getFinalFieldName() + "_combine";
        String tempName = getColumnAlias(leftMapping.getOriginalTableName() + sql_key_separator + fieldName);
        // 连接符为空
        if (StringUtils.isEmpty(connector)) {
            connector = "''";
        } else {
            connector = "'" + connector + "'";
        }
        String leftField = getFromField(leftMapping, fromType);
        String rightField = getFromField(rightMapping, fromType);

        StringBuilder fieldBuilder = new StringBuilder();
        fieldBuilder.append("CONCAT(CONCAT(");
        fieldBuilder.append(leftField);
        fieldBuilder.append(",");
        fieldBuilder.append(connector);
        fieldBuilder.append("),");
        fieldBuilder.append(rightField);
        fieldBuilder.append(") AS ");
        fieldBuilder.append(tempName);

        // 新字段的属性
        Integer length = getCombineColumnLength(leftMapping, rightMapping);
        String columnType = "varchar(" + length + ")";
        // 新字段描述
        String desc = leftMapping.getTableField().getDesc();
        String columnDesc = null;
        if (StringUtils.isBlank(desc)) {
            columnDesc = fieldName;
        } else {
            columnDesc = desc + "(combine)";
        }
        TableField tableField = new TableField(DataTypeEnum.Text.getType(), fieldName, columnDesc, columnType, "varchar", String.valueOf(length));
        FieldMappingModel newMapping = leftMapping.clone();
        newMapping.setTempFieldName(tempName);
        newMapping.setFinalFieldName(fieldName);
        newMapping.setFinalFieldType(DataTypeEnum.Text.getType());
        newMapping.setFinalFieldDesc(columnDesc);
        newMapping.setOriginalColumnType(columnType);
        newMapping.setTableField(tableField);
        return new ArrangeResultModel(newMapping.getTempFieldName(), fieldBuilder.toString(), true, newMapping);
    }

    @Override
    public List<String> nonNull(List<FieldMappingModel> fromFieldMappings, String fromTable, ComponentTypeEnum fromType) {
        List<String> results = Lists.newArrayList();
        fromFieldMappings.forEach(fromMapping -> {
            String fromField = getFromField(fromMapping, fromType);
            // 日期类型不能用 ='' 判断
            if (DataTypeEnum.Date.getType().equals(fromMapping.getFinalFieldType()) || DataTypeEnum.DateTime.getType().equals(fromMapping.getFinalFieldType())) {
                results.add(fromField + " IS NOT NULL");
            } else {
                results.add(fromField + " IS NOT NULL AND " + fromField + " != ''");
            }
        });
        return results;
    }

    @Override
    public List<ArrangeResultModel> toUpperCase(List<FieldMappingModel> fromFieldMappings, String fromTable, ComponentTypeEnum fromType) {
        List<ArrangeResultModel> results = Lists.newArrayList();
        fromFieldMappings.forEach(fromMapping -> {
            String fromField = getFromField(fromMapping, fromType);
            String segment = "UPPER(" + fromField + ") AS " + fromMapping.getTempFieldName();
            results.add(new ArrangeResultModel(fromMapping.getTempFieldName(), segment, false, fromMapping));
        });
        return results;
    }

    @Override
    public List<ArrangeResultModel> toLowerCase(List<FieldMappingModel> fromFieldMappings, String fromTable, ComponentTypeEnum fromType) {
        List<ArrangeResultModel> results = Lists.newArrayList();
        fromFieldMappings.forEach(fromMapping -> {
            String fromField = getFromField(fromMapping, fromType);
            String segment = "LOWER(" + fromField + ") AS " + fromMapping.getTempFieldName();
            results.add(new ArrangeResultModel(fromMapping.getTempFieldName(), segment, false, fromMapping));
        });
        return results;
    }

    @Override
    public List<ArrangeResultModel> trim(List<FieldMappingModel> fromFieldMappings, String fromTable, ComponentTypeEnum fromType) {
        List<ArrangeResultModel> results = Lists.newArrayList();
        fromFieldMappings.forEach(fromMapping -> {
            String fromField = getFromField(fromMapping, fromType);
            String segment = "TRIM(" + fromField + ") AS " + fromMapping.getTempFieldName();
            results.add(new ArrangeResultModel(fromMapping.getTempFieldName(), segment, false, fromMapping));
        });
        return results;
    }

    @Override
    public ArrangeResultModel blank(FieldMappingModel fromMapping, ArrangeBlankModel blankModel, String fromTable, ComponentTypeEnum fromType) {
        // sql片段
        String segment = null;
        // 去除空格的类型：left, right, all
        String type = blankModel.getType();
        // 去除空格长度
        Integer length = blankModel.getLength();
        String fromField = getFromField(fromMapping, fromType);
        if (ComponentCons.ARRANGE_PARAM_KEY_SPACE_LEFT.equals(type) && length != null && length != 0) {
            // 从左侧开始，去除在长度为length的范围内的空字符
            segment = "CONCAT(REPLACE(SUBSTR(" + fromField + ", 1, " + length + "), ' ', ''), SUBSTR(" + fromField + ", " + length + " + 1, LENGTH(" + fromField + "))) AS " + fromMapping.getTempFieldName();
        } else if (ComponentCons.ARRANGE_PARAM_KEY_SPACE_RIGHT.equals(type) && length != null && length != 0) {
            // 从右侧开始，去除在长度为length的范围内的空字符
            segment = "CONCAT(SUBSTR(" + fromField + ", 1, LENGTH(" + fromField + ")-" + length + "), REPLACE(SUBSTR(" + fromField + ", LENGTH(" + fromField + ") -" + length + "+1, LENGTH(" + fromField + ")), ' ', '')) AS " + fromMapping.getTempFieldName();
        } else {
            // 去除字段内的全部空格
            segment = "REPLACE(" + fromField + ", ' ', '') AS " + fromMapping.getTempFieldName();
        }
        return new ArrangeResultModel(fromMapping.getTempFieldName(), segment, false, fromMapping);
    }

    @Override
    public ArrangeResultModel enumGroup(FieldMappingModel fromFieldMapping, ArrangeGroupEnumModel groupModel, String fromTable, ComponentTypeEnum fromType) {
        List<ArrangeGroupEnumFieldModel> groups = groupModel.getGroups();
        String otherValue = groupModel.getOther();
        StringBuilder fieldBuilder = new StringBuilder();
        fieldBuilder.append("CASE ");
        // 原字段（要进行分组的字段）
        if (ComponentTypeEnum.DATASOURCE.equals(fromType)) {
            fieldBuilder.append(fromFieldMapping.getOriginalFieldName());
        } else {
            fieldBuilder.append(fromFieldMapping.getTempFieldName());
        }
        fieldBuilder.append(sql_key_blank);

        // 初始化分组后的字段信息
        String newField = fromFieldMapping.getFinalFieldName() + "_group";
        String newFieldTemp = getColumnAlias(fromFieldMapping.getOriginalTableName() + sql_key_separator + newField);
        FieldMappingModel newMapping = fromFieldMapping.clone();
        newMapping.setFinalFieldType(DataTypeEnum.Text.getType());
        newMapping.setFinalFieldName(newField);
        newMapping.setTempFieldName(newFieldTemp);
        String desc = fromFieldMapping.getTableField().getDesc();
        if (StringUtils.isBlank(desc)) {
            newMapping.setFinalFieldDesc(newField);
        } else {
            newMapping.setFinalFieldDesc(desc + "(group)");
        }
        newMapping.getTableField().setType(DataTypeEnum.Text.getType());
        newMapping.getTableField().setName(newField);
        newMapping.getTableField().setColumnType("varchar(255)");
        newMapping.getTableField().setDataType("varchar");
        newMapping.getTableField().setDesc(newMapping.getFinalFieldDesc());

        // 遍历生成条件
        groups.forEach(group -> {
            String target = group.getTarget();
            List<String> source = group.getSources();
            source.forEach(s -> {
                fieldBuilder.append("WHEN ");
                if (NumberUtils.isDigits(s)) {
                    fieldBuilder.append(s);
                } else {
                    fieldBuilder.append("'" + s + "'");
                }
                fieldBuilder.append(sql_key_blank);
                fieldBuilder.append("THEN ");
                if (NumberUtils.isDigits(target)) {
                    fieldBuilder.append(target);
                } else {
                    fieldBuilder.append("'" + target + "'");
                }
                fieldBuilder.append(sql_key_blank);
            });
        });

        fieldBuilder.append("ELSE '").append(otherValue).append("'");
        fieldBuilder.append(sql_key_blank);
        fieldBuilder.append("END AS ");
        fieldBuilder.append(newFieldTemp);
        fieldBuilder.append(sql_key_blank);
        return new ArrangeResultModel(newMapping.getTempFieldName(), fieldBuilder.toString(), true, newMapping);
    }

    @Override
    public ArrangeResultModel sectGroup(FieldMappingModel fromFieldMapping, ArrangeGroupSectModel groupModel, String fromTable, ComponentTypeEnum fromType) {
        List<ArrangeGroupSectFieldModel> groups = groupModel.getGroups();
        String otherValue = groupModel.getOther();
        // 原字段（要进行分组的字段）
        String sourceField;
        if (ComponentTypeEnum.DATASOURCE.equals(fromType)) {
            sourceField = fromFieldMapping.getOriginalFieldName();
        } else {
            sourceField = fromFieldMapping.getTempFieldName();
        }

        // 初始化分组后的字段信息
        String newField = fromFieldMapping.getFinalFieldName() + "_group";
        String newFieldTemp = getColumnAlias(fromFieldMapping.getOriginalTableName() + sql_key_separator + newField);
        FieldMappingModel newMapping = fromFieldMapping.clone();
        newMapping.setFinalFieldName(newField);
        newMapping.setFinalFieldType(DataTypeEnum.Text.getType());
        newMapping.setTempFieldName(newFieldTemp);
        String desc = fromFieldMapping.getTableField().getDesc();
        if (StringUtils.isBlank(desc)) {
            newMapping.setFinalFieldDesc(newField);
        } else {
            newMapping.setFinalFieldDesc(desc + "(group)");
        }
        newMapping.getTableField().setType(DataTypeEnum.Text.getType());
        newMapping.getTableField().setName(newField);
        newMapping.getTableField().setColumnType("varchar(255)");
        newMapping.getTableField().setDataType("varchar");
        newMapping.getTableField().setDesc(newMapping.getFinalFieldDesc());

        StringBuilder fieldBuilder = new StringBuilder();
        fieldBuilder.append("CASE ");
        // 遍历生成条件
        groups.forEach(group -> {
            String minSource = group.getMinSource();
            String maxSource = group.getMaxSource();
            String target = group.getTarget();

            fieldBuilder.append("WHEN ");
            fieldBuilder.append(sourceField);
            fieldBuilder.append(sql_key_blank);
            fieldBuilder.append(sql_key_between);
            if (NumberUtils.isDigits(minSource)) {
                fieldBuilder.append(minSource);
            } else {
                fieldBuilder.append("'" + minSource + "'");
            }

            fieldBuilder.append(sql_key_blank);
            fieldBuilder.append(sql_key_and);
            if (NumberUtils.isDigits(maxSource)) {
                fieldBuilder.append(maxSource);
            } else {
                fieldBuilder.append("'" + maxSource + "'");
            }

            fieldBuilder.append(sql_key_blank);
            fieldBuilder.append("THEN ");
            if (NumberUtils.isDigits(target)) {
                fieldBuilder.append(target);
            } else {
                fieldBuilder.append("'" + target + "'");
            }
            fieldBuilder.append(sql_key_blank);
        });

        fieldBuilder.append("ELSE '").append(otherValue).append("'");
        fieldBuilder.append("END AS ");
        fieldBuilder.append(newFieldTemp);
        fieldBuilder.append(sql_key_blank);
        return new ArrangeResultModel(newMapping.getTempFieldName(), fieldBuilder.toString(), true, newMapping);
    }

    @Override
    public ArrangeResultModel modify(FieldMappingModel fromFieldMapping, String targetDesc, DataTypeEnum targetType, String fromTable, ComponentTypeEnum fromType) {
        String fromField = fromFieldMapping.getOriginalFieldName();
        String tempSegment = fromField + " AS " + fromFieldMapping.getTempFieldName();
        if (!ComponentTypeEnum.DATASOURCE.equals(fromType)) {
            fromField = fromFieldMapping.getTempFieldName();
            tempSegment = fromField;
        }

        FieldMappingModel mapping = fromFieldMapping.clone();
        TableField field = mapping.getTableField();
        // 修改字段
        if (StringUtils.isNotBlank(targetDesc)) {
            mapping.setFinalFieldDesc(targetDesc);
            field.setDesc(targetDesc);
        }

        // 原始字段类型
        String type = field.getType();
        // 前后字段类型一致，不转换
        if (targetType.getType().equals(type)) {
            return new ArrangeResultModel(mapping.getTempFieldName(), tempSegment, false, mapping);
        }
        ArrangeResultModel result = null;
        switch (targetType) {
            case Integer:
                result = toInteger(fromFieldMapping, fromType);
                break;
            case Float:
                result = toFloat(fromFieldMapping, fromType);
                break;
            case Date:
                result = toDate(fromFieldMapping, fromType);
                break;
            case DateTime:
                result = toDateTime(fromFieldMapping, fromType);
                break;
            case Text:
                result = toText(fromFieldMapping, fromType);
                break;
            default:
                throw new BizException("转换类型失败，暂不支持的类型！");
        }
        return result;
    }

    /**
     * 转换字段为整数类型
     *
     * @param fromMapping 从字段映射对象
     * @param fromType    从组件类型
     * @return ArrangeResultModel
     */
    private ArrangeResultModel toInteger(FieldMappingModel fromMapping, ComponentTypeEnum fromType) {
        String fromField = getFromField(fromMapping, fromType);
        String type = fromMapping.getTableField().getDataType();
        DataTypeEnum sourceType = DataTypeEnum.valueOf(type);
        StringBuilder segmentBuilder = new StringBuilder();
        switch (sourceType) {
            case Float:
                segmentBuilder.append("CAST(").append(fromField).append(" AS DECIMAL (32))");
                break;
            case Text:
                // CASE WHEN REGEXP_LIKE('123','(^[+-]?\d{0,}\.?\d{0,}$)') THEN CAST('123' AS DECIMAL (10)) ELSE NULL END
                segmentBuilder.append("CASE WHEN REGEXP_LIKE(");
                segmentBuilder.append(fromField);
                segmentBuilder.append(",");
                // 使用正则匹配数字类型字符串
                segmentBuilder.append("'^(\\-|\\+)?\\d+(\\.\\d+)?$'");
                segmentBuilder.append(") THEN CAST(");
                segmentBuilder.append(fromField);
                segmentBuilder.append(" AS DECIMAL (32)) ELSE NULL END");
                break;
            default:
                return defaultModify(fromMapping, fromType);
        }

        FieldMappingModel mapping = fromMapping.clone();
        mapping.setFinalFieldType(DataTypeEnum.Integer.getType());
        mapping.getTableField().setType(DataTypeEnum.Integer.getType());
        mapping.getTableField().setColumnType("bigint(32)");
        mapping.getTableField().setDataType("bigint");
        mapping.getTableField().setDataScope("32");
        segmentBuilder.append(" AS ");
        segmentBuilder.append(mapping.getTempFieldName());
        return new ArrangeResultModel(mapping.getTempFieldName(), segmentBuilder.toString(), false, mapping);
    }

    /**
     * 转换字段为浮点数类型
     *
     * @param fromMapping 从字段映射对象
     * @param fromType    从组件类型
     * @return ArrangeResultModel
     */
    private ArrangeResultModel toFloat(FieldMappingModel fromMapping, ComponentTypeEnum fromType) {
        String fromField = getFromField(fromMapping, fromType);
        String type = fromMapping.getTableField().getDataType();
        DataTypeEnum sourceType = DataTypeEnum.valueOf(type);
        StringBuilder segmentBuilder = new StringBuilder();
        switch (sourceType) {
            case Integer:
                segmentBuilder.append("CAST(").append(fromField).append(" AS DECIMAL (32,8))");
                break;
            case Text:
                // CASE WHEN REGEXP_LIKE('123','(^[+-]?\d{0,}\.?\d{0,}$)') THEN CAST('123' AS DECIMAL (32,8)) ELSE NULL END
                segmentBuilder.append("CASE WHEN REGEXP_LIKE(");
                segmentBuilder.append(fromField);
                segmentBuilder.append(",");
                // 使用正则匹配数字类型字符串
                segmentBuilder.append("'^(\\-|\\+)?\\d+(\\.\\d+)?$'");
                segmentBuilder.append(") THEN CAST(");
                segmentBuilder.append(fromField);
                segmentBuilder.append(" AS DECIMAL (32,8)) ELSE NULL END");
                break;
            default:
                return defaultModify(fromMapping, fromType);
        }

        FieldMappingModel mapping = fromMapping.clone();
        mapping.setFinalFieldType(DataTypeEnum.Float.getType());
        mapping.getTableField().setType(DataTypeEnum.Float.getType());
        mapping.getTableField().setColumnType("DECIMAL(32,8)");
        mapping.getTableField().setDataType("DECIMAL");
        mapping.getTableField().setDataScope("32,8");
        segmentBuilder.append(" AS ");
        segmentBuilder.append(mapping.getTempFieldName());
        return new ArrangeResultModel(mapping.getTempFieldName(), segmentBuilder.toString(), false, mapping);
    }

    /**
     * 转换字段为日期类型
     *
     * @param fromMapping 从字段映射对象
     * @param fromType    从组件类型
     * @return ArrangeResultModel
     */
    private ArrangeResultModel toDate(FieldMappingModel fromMapping, ComponentTypeEnum fromType) {
        String fromField = getFromField(fromMapping, fromType);
        String type = fromMapping.getTableField().getDataType();
        FieldMappingModel mapping = fromMapping.clone();
        DataTypeEnum sourceType = DataTypeEnum.valueOf(type);
        StringBuilder segmentBuilder = new StringBuilder();
        switch (sourceType) {
            case DateTime:
                segmentBuilder.append(fromField);
                break;
            case Text:
                // CASE WHEN REGEXP_LIKE('2020-02-21', '^[1-2]\d{3}-((0[1-9])|(1[0-2]))-(((0[1-9])|([1-2][0-9])|(3[0-1])))$') THEN TO_DATE('2020-02-21', 'yyyy-mm-dd') ELSE NULL END
                segmentBuilder.append("CASE WHEN REGEXP_LIKE(");
                segmentBuilder.append(fromField);
                segmentBuilder.append(",");
                // 使用正则匹配yyyy-MM-dd格式日期字符串（暂不支持校验时间有效性）
                segmentBuilder.append("'^[1-2]\\d{3}-((0[1-9])|(1[0-2]))-(((0[1-9])|([1-2][0-9])|(3[0-1])))$'");
                segmentBuilder.append(") THEN TO_DATE(");
                segmentBuilder.append(fromField);
                segmentBuilder.append(",'yyyy-mm-dd') ELSE NULL END");
                break;
            default:
                return defaultModify(fromMapping, fromType);
        }

        mapping.setFinalFieldType(DataTypeEnum.Date.getType());
        mapping.getTableField().setType(DataTypeEnum.Date.getType());
        mapping.getTableField().setColumnType("date");
        mapping.getTableField().setDataType("date");
        mapping.getTableField().setDataScope("");
        segmentBuilder.append(" AS ");
        segmentBuilder.append(mapping.getTempFieldName());
        return new ArrangeResultModel(mapping.getTempFieldName(), segmentBuilder.toString(), false, mapping);
    }

    /**
     * 转换字段为日期时间类型
     *
     * @param fromMapping 从字段映射对象
     * @param fromType    从组件类型
     * @return ArrangeResultModel
     */
    private ArrangeResultModel toDateTime(FieldMappingModel fromMapping, ComponentTypeEnum fromType) {
        String fromField = getFromField(fromMapping, fromType);
        String type = fromMapping.getTableField().getDataType();
        FieldMappingModel mapping = fromMapping.clone();
        DataTypeEnum sourceType = DataTypeEnum.valueOf(type);
        StringBuilder segmentBuilder = new StringBuilder();
        switch (sourceType) {
            case DateTime:
                segmentBuilder.append(fromField);
                segmentBuilder.append(" ");
                break;
            case Text:
                // CASE WHEN REGEXP_LIKE('2020-02-21', '^[1-2]\d{3}-((0[1-9])|(1[0-2]))-(((0[1-9])|([1-2][0-9])|(3[0-1])))$') THEN TO_DATE('2020-02-21', 'yyyy-mm-dd') ELSE NULL END
                segmentBuilder.append("CASE WHEN REGEXP_LIKE(");
                segmentBuilder.append(fromField);
                segmentBuilder.append(",");
                // 使用正则匹配yyyy-MM-dd HH:mm:ss格式时间字符串（暂不支持校验时间有效性）
                segmentBuilder.append("'^[1-2]\\d{3}-((0[1-9])|(1[0-2]))-(((0[1-9])|([1-2][0-9])|(3[0-1])))\\s(20|21|22|23|[0-1]\\d):[0-5]\\d:[0-5]\\d$'");
                segmentBuilder.append(") THEN TO_DATE(");
                segmentBuilder.append(fromField);
                segmentBuilder.append(",'yyyy-mm-dd hh24:mi:ss') ELSE NULL END ");
                break;
            default:
                return defaultModify(fromMapping, fromType);
        }

        mapping.setFinalFieldType(DataTypeEnum.DateTime.getType());
        mapping.getTableField().setType(DataTypeEnum.DateTime.getType());
        mapping.getTableField().setColumnType("datetime");
        mapping.getTableField().setDataType("datetime");
        mapping.getTableField().setDataScope("");
        segmentBuilder.append("AS ");
        segmentBuilder.append(mapping.getTempFieldName());
        return new ArrangeResultModel(mapping.getTempFieldName(), segmentBuilder.toString(), false, mapping);
    }

    /**
     * 转换字段为文本类型
     *
     * @param fromMapping 从字段映射对象
     * @param fromType    从组件类型
     * @return ArrangeResultModel
     */
    private ArrangeResultModel toText(FieldMappingModel fromMapping, ComponentTypeEnum fromType) {
        String fromField = getFromField(fromMapping, fromType);
        String type = fromMapping.getTableField().getDataType();
        FieldMappingModel mapping = fromMapping.clone();
        DataTypeEnum sourceType = DataTypeEnum.valueOf(type);
        StringBuilder segmentBuilder = new StringBuilder();
        switch (sourceType) {
            case Date:
                segmentBuilder.append("TO_CHAR(");
                segmentBuilder.append(fromField);
                segmentBuilder.append(",'yyyy-mm-dd')");
                break;
            case DateTime:
                segmentBuilder.append("TO_CHAR(");
                segmentBuilder.append(fromField);
                segmentBuilder.append(",'yyyy-mm-dd hh24:mi:ss')");
                break;
            default:
                segmentBuilder.append("TO_CHAR(");
                segmentBuilder.append(fromField);
                segmentBuilder.append(")");
        }

        mapping.setFinalFieldType(DataTypeEnum.Text.getType());
        mapping.getTableField().setType(DataTypeEnum.Text.getType());
        mapping.getTableField().setColumnType("varchar(255)");
        mapping.getTableField().setDataType("varchar");
        mapping.getTableField().setDataScope("255");
        segmentBuilder.append(" AS ");
        segmentBuilder.append(mapping.getTempFieldName());
        return new ArrangeResultModel(mapping.getTempFieldName(), segmentBuilder.toString(), false, mapping);
    }

    /**
     * 默认不做任何处理
     *
     * @param fromMapping 从组件字段映射
     * @param fromType    从组件类型
     * @return ArrangeResultModel
     */
    private ArrangeResultModel defaultModify(FieldMappingModel fromMapping, ComponentTypeEnum fromType) {
        String fromField = fromMapping.getOriginalFieldName();
        String tempSegment = fromField + " AS " + fromMapping.getTempFieldName();
        if (!ComponentTypeEnum.DATASOURCE.equals(fromType)) {
            fromField = fromMapping.getTempFieldName();
            tempSegment = fromField;
        }

        FieldMappingModel mapping = fromMapping.clone();
        return new ArrangeResultModel(mapping.getTempFieldName(), tempSegment, false, mapping);
    }
}
