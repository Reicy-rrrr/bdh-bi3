package com.deloitte.bdh.data.collation.component.impl;

import com.alibaba.fastjson.JSON;
import com.deloitte.bdh.common.exception.BizException;
import com.deloitte.bdh.data.collation.component.ComponentHandler;
import com.deloitte.bdh.data.collation.component.model.ComponentModel;
import com.deloitte.bdh.data.collation.component.model.JoinModel;
import com.deloitte.bdh.data.collation.component.model.JoinTable;
import com.deloitte.bdh.data.collation.enums.ComponentTypeEnum;
import com.deloitte.bdh.data.collation.enums.JoinTypeEnum;
import com.deloitte.bdh.data.collation.model.BiComponentParams;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 关联组件实现
 *
 * @author chenghzhang
 * @date 2020/10/26
 */
@Slf4j
@Service("joinComponent")
public class JoinComponent implements ComponentHandler {

    private static final String param_key_tables = "tables";

    @Override
    public void handle(ComponentModel component) {
        String componentCode = component.getCode();
        // 转换组件参数为map结构
        List<BiComponentParams> params = component.getParams();
        Map<String, BiComponentParams> paramsMap = params.stream()
                .collect(Collectors.toMap(BiComponentParams::getParamKey, param -> param));

        JoinModel joinModel = buildJoinModel(componentCode, paramsMap);
        buildSql(component, joinModel);
    }

    /**
     * 组装关联模型（树形结构）
     *
     * @param componentCode
     * @param paramsMap
     * @return
     */
    private JoinModel buildJoinModel(String componentCode,
                                     Map<String, BiComponentParams> paramsMap) {
        BiComponentParams tablesParam =
                (BiComponentParams) MapUtils.getObject(paramsMap, param_key_tables);
        if (tablesParam == null) {
            log.error("关联组件[{}]未查询到[tables]参数，处理组件失败！", componentCode);
            throw new BizException("关联组件的tables参数不能为空！");
        }

        String tablesStr = tablesParam.getParamValue();
        if (StringUtils.isBlank(tablesStr)) {
            log.error("关联组件[{}]查询到[tables]参数为空，处理组件失败！", componentCode);
            throw new BizException("关联组件的tables参数不能为空！");
        }

        List<JoinModel> tables = JSON.parseArray(tablesStr, JoinModel.class);
        JoinModel joinModel = null;
        for (JoinModel table : tables) {
            // 找到连接查询最左表
            if (StringUtils.isBlank(table.getLeftTableName())) {
                joinModel = table;
                break;
            }
        }

        buildModelTree(joinModel, tables);
        return joinModel;
    }

    /**
     * 组装sql
     *
     * @param component 组件模型
     * @param joinModel 关联模型
     */
    private void buildSql(ComponentModel component, JoinModel joinModel) {
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append(sql_key_select).append(sql_key_blank);
        List<ComponentModel> fromComponents = component.getFrom();
        Map<String, ComponentModel> fromCompMap = fromComponents.stream()
                .collect(Collectors.toMap(ComponentModel::getTableName, fromModel -> fromModel));

        List<Triple> currMappings = Lists.newArrayList();
        for (ComponentModel fromComponent : fromComponents) {
            if (fromComponent == null) {
                continue;
            }

            String fromTableName = fromComponent.getTableName();
            ComponentTypeEnum fromType = fromComponent.getTypeEnum();
            List<Triple> fromMappings = fromComponent.getFieldMappings();
            if (CollectionUtils.isEmpty(fromMappings)) {
                continue;
            }
            for (Triple<String, String, String> fieldMapping : fromMappings) {
                // 将从组件的字段添加到连接组件中
                currMappings.add(fieldMapping);
                String fieldName = fieldMapping.getLeft();
                String fullName = fieldMapping.getRight();
                if (ComponentTypeEnum.DATASOURCE.equals(fromType)) {
                    sqlBuilder.append(sql_key_blank);
                    sqlBuilder.append(fullName);
                    sqlBuilder.append(sql_key_blank);
                    sqlBuilder.append(sql_key_as);
                    sqlBuilder.append(sql_key_blank);
                    sqlBuilder.append(fieldName);
                    sqlBuilder.append(sql_key_comma);
                } else {
                    sqlBuilder.append(sql_key_blank);
                    sqlBuilder.append(fromTableName);
                    sqlBuilder.append(sql_key_separator);
                    sqlBuilder.append(fieldName);
                    sqlBuilder.append(sql_key_comma);
                }
            }
        }
        // 删除SELECT中最后多余的“,”
        sqlBuilder.deleteCharAt(sqlBuilder.lastIndexOf(sql_key_comma));
        sqlBuilder.append(sql_key_blank);
        sqlBuilder.append(sql_key_from);
        sqlBuilder.append(sql_key_blank);
        buildTableSql(sqlBuilder, joinModel, fromCompMap);
        component.setTableName(component.getCode());
        component.setSql(sqlBuilder.toString());
        component.setFieldMappings(currMappings);
        // 组装连接组件的字段
        List<String> fields = currMappings.stream().map(Triple<String, String, String>::getLeft)
                .collect(Collectors.toList());
        component.setFields(fields);
    }

    /**
     * 组装查询表部分sql语句（from 与 where 之间部分）
     *
     * @param sqlBuilder
     * @param joinModel
     * @param componentModels
     */
    private void buildTableSql(StringBuilder sqlBuilder, JoinModel joinModel,
                               Map<String, ComponentModel> componentModels) {
        if (StringUtils.isBlank(joinModel.getLeftTableName())) {
            String leftTableName = joinModel.getTableName();
            ComponentModel leftComp = componentModels.get(leftTableName);
            // 如果为数据源组件，直接使用表名查询；其他组件使用子查询
            if (ComponentTypeEnum.DATASOURCE.equals(leftComp.getTypeEnum())) {
                sqlBuilder.append(joinModel.getTableName());
                sqlBuilder.append(sql_key_blank);
            } else {
                sqlBuilder.append(sql_key_bracket_left);
                sqlBuilder.append(leftComp.getSql());
                // 子查询使用组件code作为别名
                sqlBuilder.append(sql_key_bracket_right);
                sqlBuilder.append(leftComp.getCode());
                sqlBuilder.append(sql_key_blank);
            }
        }
        List<JoinModel> rightModels = joinModel.getRight();
        if (CollectionUtils.isEmpty(rightModels)) {
            return;
        }

        for (JoinModel rightModel : rightModels) {
            String rightTableName = rightModel.getTableName();
            ComponentModel rightComp = componentModels.get(rightTableName);

            JoinTypeEnum joinType = JoinTypeEnum.values(rightModel.getJoinType());
            sqlBuilder.append(joinType.getValue());
            sqlBuilder.append(sql_key_blank);
            if (ComponentTypeEnum.DATASOURCE.equals(rightComp.getTypeEnum())) {
                sqlBuilder.append(rightModel.getTableName());
                sqlBuilder.append(sql_key_blank);
            } else {
                sqlBuilder.append(sql_key_bracket_left);
                sqlBuilder.append(sql_key_blank);
                sqlBuilder.append(rightComp.getSql());
                // 子查询使用组件code作为别名
                sqlBuilder.append(sql_key_bracket_right);
                sqlBuilder.append(sql_key_blank);
                sqlBuilder.append(sql_key_as);
                sqlBuilder.append(sql_key_blank);
                sqlBuilder.append(rightComp.getTableName());
                sqlBuilder.append(sql_key_blank);
            }

            sqlBuilder.append(sql_key_on);
            sqlBuilder.append(sql_key_blank);
            sqlBuilder.append(joinModel.getTableName());
            sqlBuilder.append(sql_key_separator);
            sqlBuilder.append(rightModel.getLeftField());
            sqlBuilder.append(sql_key_equal);
            sqlBuilder.append(rightModel.getTableName());
            sqlBuilder.append(sql_key_separator);
            sqlBuilder.append(rightModel.getRightField());
            sqlBuilder.append(sql_key_blank);

            if (!CollectionUtils.isEmpty(rightModel.getRight())) {
                buildTableSql(sqlBuilder, rightModel, componentModels);
            }
        }
    }

    private void buildModelTree(JoinModel joinModel, List<JoinModel> tables) {
        List<JoinModel> rights = Lists.newArrayList();
        // 为每一个父节点增加子树（List形式，没有则为空的list）
        for (JoinModel table : tables) {
            if (table.getLeftTableName().equals(joinModel.getTableName())) {
                JoinModel rightModel = new JoinModel();
                BeanUtils.copyProperties(table, rightModel, "right");
                rights.add(rightModel);
                buildModelTree(rightModel, tables);
            }
        }
        joinModel.setRight(rights);
    }
}