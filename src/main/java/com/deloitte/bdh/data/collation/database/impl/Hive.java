package com.deloitte.bdh.data.collation.database.impl;

import com.deloitte.bdh.data.collation.database.DbSelector;
import com.deloitte.bdh.data.collation.database.dto.DbContext;
import com.deloitte.bdh.data.collation.database.po.TableData;
import com.deloitte.bdh.data.collation.database.po.TableField;
import com.deloitte.bdh.data.collation.database.po.TableSchema;
import com.github.pagehelper.PageInfo;
import com.google.common.collect.Lists;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

@Service("hive")
public class Hive extends AbstractProcess implements DbSelector {

    @Override
    public String test(DbContext context) throws Exception {
        Connection con = super.connection(context);
        super.close(con);
        return "θΏζ₯ζε";
    }

    @Override
    public List<String> getTables(DbContext context) throws Exception {
        Connection con = super.connection(context);
        PreparedStatement statement = con.prepareStatement(tableSql(context));
        ResultSet result = statement.executeQuery();
        List<String> list = Lists.newArrayList();
        while (result.next()) {
            String tabName = result.getString("tab_name");
            if ("telescope_entries".equals(tabName)) {
                continue;
            }
            list.add(tabName);
        }
        super.close(con);
        return list;
    }

    @Override
    public List<String> getFields(DbContext context) throws Exception {
        Connection con = super.connection(context);
        PreparedStatement statement = con.prepareStatement(fieldSql(context));
        ResultSet result = statement.executeQuery();
        List<String> list = Lists.newArrayList();
        String tableName = context.getTableName();
        while (result.next()) {
            list.add(result.getString("col_name").replace(tableName + ".", ""));
        }
        super.close(con);
        return list;
    }

    @Override
    public TableSchema getTableSchema(DbContext context) throws Exception {
        Connection con = super.connection(context);
        PreparedStatement statement = con.prepareStatement(fieldSql(context));
        ResultSet result = statement.executeQuery();
        TableSchema schema = new TableSchema();
        List<TableField> columns = Lists.newArrayList();
        String tableName = context.getTableName();
        while (result.next()) {
            TableField field = new TableField();
            field.setName(result.getString("col_name").replace(tableName + ".", ""));
            field.setType("String");
            field.setDesc(field.getName());
            field.setDataType(result.getString("data_type"));
            columns.add(field);
        }
        super.close(con);
        schema.setColumns(columns);
        return schema;
    }

    @Override
    public TableData getTableData(DbContext context) throws Exception {
        return super.getTableData(context);
    }

    @Override
    public long getTableCount(DbContext context) throws Exception {
        return super.getTableCount(context);
    }

    @Override
    public List<Map<String, Object>> executeQuery(DbContext context) throws Exception {
        return super.executeQuery(context);
    }

    @Override
    public PageInfo<Map<String, Object>> executePageQuery(DbContext context) throws Exception {
        return super.executePageQuery(context);
    }

    @Override
    public String tableSql(DbContext context) {
        return "show tables";
    }

    @Override
    public String fieldSql(DbContext context) {
        return "desc " + context.getTableName() + "";
    }

    @Override
    protected String selectSql(DbContext context) {
        Integer size = context.getSize();
        return "select * from " + context.getTableName() + " limit " + size;
    }

    @Override
    protected String buildQueryLimit(DbContext context) {
        return context.getQuerySql() + " limit 10";
    }
}
