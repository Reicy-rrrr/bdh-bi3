package com.deloitte.bdh.data.analyse.sql.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beust.jcommander.internal.Lists;
import com.deloitte.bdh.common.util.ThreadLocalHolder;
import com.deloitte.bdh.data.analyse.dao.bi.BiUiDemoMapper;
import com.deloitte.bdh.data.analyse.enums.DataModelTypeEnum;
import com.deloitte.bdh.data.analyse.enums.WildcardEnum;
import com.deloitte.bdh.data.analyse.model.BiUiAnalyseUserData;
import com.deloitte.bdh.data.analyse.model.datamodel.DataCondition;
import com.deloitte.bdh.data.analyse.model.datamodel.DataModel;
import com.deloitte.bdh.data.analyse.model.datamodel.DataModelField;
import com.deloitte.bdh.data.analyse.sql.AbstractRela;
import com.deloitte.bdh.data.analyse.sql.dto.SqlContext;
import com.deloitte.bdh.data.analyse.utils.AnalyseUtil;
import com.deloitte.bdh.data.analyse.sql.utils.MysqlBuildUtil;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

@Service("analyseLocal")
public class AnalyseLocal extends AbstractRela {
    @Resource
    protected BiUiDemoMapper biUiDemoMapper;

    @Override
    protected String assemblyQuerySql(SqlContext context) {
        DataModel model = context.getModel();
        String select = this.select(model);
        String from = this.from(model);
        String where = this.where(model);
        String groupBy = this.groupBy(model);
        String having = this.having(model);
        String orderBy = this.orderBy(model);
        String limit = this.page(context);
        return StringUtils.join(select, from, where, groupBy, having, orderBy, limit);
    }

    @Override
    protected String select(DataModel model) {
        List<String> list = Lists.newArrayList();
        if (CollectionUtils.isNotEmpty(model.getX())) {
            for (DataModelField s : model.getX()) {
                String express = MysqlBuildUtil.select(model.getTableName(), s.getId(), s.getQuota(), s.getAggregateType(),
                        s.getFormatType(), s.getDataType(), s.getDataUnit(), s.getPrecision(), s.getAlias(), s.getDefaultValue());
                if (StringUtils.isNotBlank(express)) {
                    list.add(express);
                }
            }
        }
        if (CollectionUtils.isEmpty(list)) {
            return "";
        }
        return "SELECT " + AnalyseUtil.join(",", list.toArray(new String[0]));
    }

    @Override
    protected String from(DataModel model) {
        return " FROM " + MysqlBuildUtil.from(model.getTableName(), null);
    }

    @Override
    protected String where(DataModel model) {
        List<String> list = Lists.newArrayList();
        list.add(" 1=1 ");
        for (DataModelField s : model.getX()) {
            String express = MysqlBuildUtil.where(model.getTableName(), s.getId(), s.getQuota(), s.getSymbol(), s.getValue());
            if (StringUtils.isNotBlank(express)) {
                list.add(express);
            }
        }
        if (CollectionUtils.isNotEmpty(model.getConditions())) {
            for (DataCondition condition : model.getConditions()) {
                String express = "";
                String value = convertValue(condition.getSymbol(), condition.getValue());
                String symbol = WildcardEnum.get(condition.getSymbol()).getCode();
                express = MysqlBuildUtil.where(model.getTableName(), condition.getId().get(0), condition.getQuota(), condition.getFormatType(), symbol, value);
                list.add(express);
            }
        }
        //????????????
        if (StringUtils.isNotBlank(model.getPageId())) {
            LambdaQueryWrapper<BiUiAnalyseUserData> lambdaQueryWrapper = new LambdaQueryWrapper<>();
            lambdaQueryWrapper.eq(BiUiAnalyseUserData::getUserId, ThreadLocalHolder.getOperator());
            lambdaQueryWrapper.eq(BiUiAnalyseUserData::getPageId, model.getPageId());
            lambdaQueryWrapper.eq(BiUiAnalyseUserData::getComponentId, model.getComponentId());
            lambdaQueryWrapper.eq(BiUiAnalyseUserData::getTenantId, ThreadLocalHolder.getTenantId());
            List<BiUiAnalyseUserData> userDataList = userDataService.list(lambdaQueryWrapper);
            if (CollectionUtils.isNotEmpty(userDataList)) {
                for (BiUiAnalyseUserData userData : userDataList) {
                    String value = convertValue(WildcardEnum.EQ.getKey(), Lists.newArrayList(userData.getFieldValue()));
                    String express = MysqlBuildUtil.where(userData.getTableName(), userData.getTableField(), DataModelTypeEnum.WD.getCode(), WildcardEnum.EQ.getCode(), value);
                    if (StringUtils.isNotBlank(express)) {
                        list.add(express);
                    }
                }
            }
        }

        return " WHERE " + AnalyseUtil.join(" AND ", list.toArray(new String[0]));
    }

    @Override
    protected String groupBy(DataModel model) {
        List<String> list = Lists.newArrayList();
        if (CollectionUtils.isNotEmpty(model.getX())) {
            boolean needGroup = needGroup(model);
            for (DataModelField s : model.getX()) {
                String express = MysqlBuildUtil.groupBy(model.getTableName(), s.getId(), s.getQuota(), s.getFormatType()
                        , s.getDataType(), needGroup || s.isNeedGroup());
                if (StringUtils.isNotBlank(express)) {
                    list.add(express);
                }
            }
        }
        if (CollectionUtils.isEmpty(list)) {
            return "";
        }
        return " GROUP BY " + AnalyseUtil.join(" , ", list.toArray(new String[0]));
    }

    @Override
    protected String having(DataModel model) {
        List<String> list = Lists.newArrayList();
        for (DataModelField s : model.getX()) {
            String express = MysqlBuildUtil.having(model.getTableName(), s.getId(), s.getQuota()
                    , s.getAggregateType(), s.getSymbol(), s.getValue());
            if (StringUtils.isNotBlank(express)) {
                list.add(express);
            }
        }
        if (CollectionUtils.isNotEmpty(model.getConditions())) {
            for (DataCondition condition : model.getConditions()) {
                String value = convertValue(condition.getSymbol(), condition.getValue());
                String symbol = WildcardEnum.get(condition.getSymbol()).getCode();
                if (StringUtils.equals(condition.getQuota(), DataModelTypeEnum.DL.getCode()) &&
                        StringUtils.isNotBlank(condition.getAggregateType())) {
                    String express = MysqlBuildUtil.having(model.getTableName(), condition.getId().get(0), condition.getQuota(),
                            condition.getAggregateType(), symbol, value);
                    list.add(express);
                }

            }
        }
        if (CollectionUtils.isEmpty(list)) {
            return "";
        }
        return " HAVING " + AnalyseUtil.join(" AND ", list.toArray(new String[0]));
    }

    @Override
    protected String orderBy(DataModel model) {
        List<String> list = Lists.newArrayList();
        for (DataModelField s : model.getX()) {
            String express = MysqlBuildUtil.orderBy(model.getTableName(), s.getId(), s.getQuota()
                    , s.getAggregateType(), s.getFormatType(), s.getOrderType());
            if (StringUtils.isNotBlank(express)) {
                list.add(express);
            }
        }
        if (CollectionUtils.isEmpty(list)) {
            return "";
        }
        return " ORDER BY " + AnalyseUtil.join(" , ", list.toArray(new String[0]));
    }

    @Override
    protected String page(SqlContext context) {
        DataModel model = context.getModel();
        if (null == model.getPage()) {
            return "";
        }
        return " LIMIT " + (model.getPage() - 1) * model.getPageSize() + "," + model.getPageSize();
    }

    @Override
    protected Long count(SqlContext context) {
        DataModel model = context.getModel();
        if (null != model.getPage()) {
            String select = this.select(model);
            String from = this.from(model);
            String where = this.where(model);
            String groupBy = this.groupBy(model);
            String having = this.having(model);
            String countSql = StringUtils.join(select, from, where, groupBy, having);

            if (StringUtils.isNotBlank(countSql)) {
                countSql = "SELECT count(1) AS TOTAL FROM (" + countSql + ") TABLE_COUNT";
                return biUiDemoMapper.selectCount(countSql);
            }
        }
        return null;
    }

    @Override
    protected List<Map<String, Object>> execute(SqlContext context) {
        return biUiDemoMapper.selectDemoList(context.getQuerySql());
    }

    private String convertValue(String symbol, List<String> valueList) {
        List<String> convertValueList = Lists.newArrayList();
        for (String value : valueList) {
            for (String escape : MysqlBuildUtil.ESCAPE_CHARACTER) {
                if (value.contains(escape)) {
                    if ((WildcardEnum.EQ.getCode().equalsIgnoreCase(symbol)
                            || WildcardEnum.IN.getCode().equalsIgnoreCase(symbol)
                            || WildcardEnum.NOT_IN.getCode().equalsIgnoreCase(symbol)
                    ) && (value.contains("%") || value.contains("\""))) {
                        continue;
                    }
                    value = value.replace(escape, "\\" + escape);
                }
            }
            convertValueList.add(value);
        }
        return WildcardEnum.get(symbol).expression(convertValueList);
    }
}
