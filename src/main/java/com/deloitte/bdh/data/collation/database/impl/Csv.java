package com.deloitte.bdh.data.collation.database.impl;

import com.deloitte.bdh.common.base.MongoHelper;
import com.deloitte.bdh.common.base.MongoPageResult;
import com.deloitte.bdh.data.collation.database.DbSelector;
import com.deloitte.bdh.data.collation.database.dto.DbContext;
import com.deloitte.bdh.data.collation.database.po.TableData;
import com.deloitte.bdh.data.collation.database.po.TableField;
import com.deloitte.bdh.data.collation.database.po.TableSchema;
import com.google.common.collect.Lists;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;

@Service("csv")
public class Csv extends AbstractProcess implements DbSelector {

    @Autowired
    private MongoHelper<LinkedHashMap> mongoHelper;

    @Override
    public String test(DbContext context) throws Exception {
        return "连接成功";
    }

    @Override
    public List<String> getTables(DbContext context) throws Exception {
        List<String> list = Lists.newArrayList();
        list.add(context.getDbName());
        return list;
    }

    @Override
    public List<String> getFields(DbContext context) throws Exception {
        MongoPageResult<LinkedHashMap> result = mongoHelper.selectListByPage(context.getDbName(), LinkedHashMap.class, 1, 1);
        LinkedHashMap rowMap = result.getRows().get(0);
        List<String> list = Lists.newArrayList(rowMap.keySet());
        return list;
    }

    @Override
    public TableSchema getTableSchema(DbContext context) throws Exception {
        MongoPageResult<LinkedHashMap> result = mongoHelper.selectListByPage(context.getDbName(), LinkedHashMap.class, 1, 1);
        LinkedHashMap rowMap = result.getRows().get(0);
        TableSchema schema = new TableSchema();
        List<TableField> columns = Lists.newArrayList();
        rowMap.forEach((key, value) -> {
            TableField field = new TableField();
            field.setName((String) key);
            field.setType("String");
            field.setDesc("");
            columns.add(field);
        });

        schema.setColumns(columns);
        return schema;
    }

    @Override
    public TableData getTableData(DbContext context) throws Exception {
        MongoPageResult<LinkedHashMap> result = mongoHelper.selectListByPage(
                context.getDbName(), LinkedHashMap.class, context.getPage(), context.getSize());
        TableData data = new TableData();
        data.setTotal(result.getTotal());
        data.setMore(result.isMore());
        List<LinkedHashMap<String, Object>> rows = Lists.newArrayList();
        result.getRows().forEach(rowData -> {
            rows.add(rowData);
        });
        data.setRows(rows);
        return data;
    }

    @Override
    public long getTableCount(DbContext context) throws Exception {
        return super.getTableCount(context);
    }

    @Override
    public String tableSql(DbContext context) {
        return null;
    }

    @Override
    public String fieldSql(DbContext context) {
        return null;
    }

    @Override
    protected String selectSql(DbContext context) {
        return null;
    }
}