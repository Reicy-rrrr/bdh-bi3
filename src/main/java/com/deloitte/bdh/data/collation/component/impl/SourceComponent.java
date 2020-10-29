package com.deloitte.bdh.data.collation.component.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.deloitte.bdh.common.exception.BizException;
import com.deloitte.bdh.data.collation.component.ComponentHandler;
import com.deloitte.bdh.data.collation.component.model.ComponentModel;
import com.deloitte.bdh.data.collation.database.DbHandler;
import com.deloitte.bdh.data.collation.database.po.TableColumn;
import com.deloitte.bdh.data.collation.model.BiEtlMappingConfig;
import com.deloitte.bdh.data.collation.model.BiEtlMappingField;
import com.deloitte.bdh.data.collation.service.BiEtlMappingConfigService;
import com.deloitte.bdh.data.collation.service.BiEtlMappingFieldService;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 数据源组件实现
 *
 * @author chenghzhang
 * @date 2020/10/26
 */
@Slf4j
@Service("sourceComponent")
public class SourceComponent implements ComponentHandler {

    @Autowired
    private BiEtlMappingConfigService biEtlMappingConfigService;

    @Autowired
    private BiEtlMappingFieldService biEtlMappingFieldService;

    @Autowired
    private DbHandler dbHandler;

    @Override
    public void handle(ComponentModel component) {
        String componentCode = component.getCode();
        // 查询配置映射（表名）
        LambdaQueryWrapper<BiEtlMappingConfig> configWrapper = new LambdaQueryWrapper();
        configWrapper.eq(BiEtlMappingConfig::getRefCode, componentCode);
        List<BiEtlMappingConfig> configs = biEtlMappingConfigService.list(configWrapper);
        if (CollectionUtils.isEmpty(configs)) {
            throw new BizException("源表组件配置映射信息不能为空！");
        }
        BiEtlMappingConfig config = configs.get(0);
        // 使用目标表名（落库后的表）
        String tableName = config.getToTableName();
        component.setTableName(tableName);

        // 查询映射字段
        LambdaQueryWrapper<BiEtlMappingField> fieldWrapper = new LambdaQueryWrapper();
        fieldWrapper.eq(BiEtlMappingField::getRefCode, componentCode);
        List<BiEtlMappingField> fields = biEtlMappingFieldService.list(fieldWrapper);
        List<String> fieldNames = null;
        // 如果映射字段为空，直接查询表结构中的所有字段
        if (!CollectionUtils.isEmpty(fields)) {
            fieldNames = fields.stream().map(BiEtlMappingField::getFieldName).collect(Collectors.toList());
        } else {
            List<TableColumn> columns = dbHandler.getColumns(tableName);
            fieldNames = columns.stream().map(TableColumn::getName).collect(Collectors.toList());
        }
        component.setFields(fieldNames);
        List<Triple> fieldMappings = Lists.newArrayList();
        fieldNames.forEach(fieldName -> {
            // fullName: table.column
            String fullName = tableName + sql_key_separator + fieldName;
            // 使用全名进行编码获取到字段别名（全名可以避免重复）
            String newName = renameColumn(fullName);
            Triple<String, String, String> mapping = new ImmutableTriple(newName, fieldName, fullName);
            fieldMappings.add(mapping);
        });
        component.setFieldMappings(fieldMappings);
        initSql(component);
    }

    /**
     * 初始化sql
     *
     * @param component
     */
    private void initSql(ComponentModel component) {
        String tableName = component.getTableName();
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append(sql_key_select);
        sqlBuilder.append(sql_key_blank);
        List<Triple> fieldMappings = component.getFieldMappings();
        fieldMappings.forEach(fieldMapping -> {
            sqlBuilder.append(fieldMapping.getRight());
            sqlBuilder.append(sql_key_blank);
            sqlBuilder.append(sql_key_as);
            sqlBuilder.append(sql_key_blank);
            sqlBuilder.append(fieldMapping.getLeft());
            sqlBuilder.append(sql_key_comma);
        });
        // 删除SELECT中最后多余的“,”
        sqlBuilder.deleteCharAt(sqlBuilder.lastIndexOf(sql_key_comma));
        sqlBuilder.append(sql_key_blank);
        sqlBuilder.append(sql_key_from);
        sqlBuilder.append(sql_key_blank);
        sqlBuilder.append(tableName);
        component.setSql(sqlBuilder.toString());
    }
}