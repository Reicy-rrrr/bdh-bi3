package com.deloitte.bdh.data.collation.database.impl;

import com.deloitte.bdh.data.collation.database.DbSelector;
import com.deloitte.bdh.data.collation.database.dto.DbContext;
import com.deloitte.bdh.data.collation.database.po.TableData;
import com.deloitte.bdh.data.collation.database.po.TableField;
import com.deloitte.bdh.data.collation.database.po.TableSchema;
import com.deloitte.bdh.data.collation.enums.SQLServerDataTypeEnum;
import com.github.pagehelper.PageInfo;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

@Service("sqlserver")
public class Sqlserver extends AbstractProcess implements DbSelector {

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
            list.add(result.getString("name"));
        }

        //get views
        statement = con.prepareStatement(" SELECT Name FROM sys.sql_modules AS m INNER JOIN sys.all_objects AS o ON m.object_id = o.object_id WHERE o.[type] = 'v' ");
        result = statement.executeQuery();
        while (result.next()) {
            list.add(result.getString("Name"));
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
        while (result.next()) {
            list.add(result.getString("COLUMN_NAME"));
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
        while (result.next()) {
            // εε
            String name = result.getString("COLUMN_NAME");
            // ζθ?Ύη½?δΈΊε­ζ?΅εη§°
            String comments = name;
            // ζ°ζ?η±»ε
            String dataType = result.getString("DATA_TYPE");
            // ε­η¬¦δΈ²ζε€§ιΏεΊ¦
            String characterLength = result.getString("CHARACTER_MAXIMUM_LENGTH");
            // ζ°ε­η²ΎεΊ¦
            String numericPrecision = result.getString("NUMERIC_PRECISION");
            String length = "0";
            if (StringUtils.isNotBlank(characterLength) || StringUtils.isNotBlank(numericPrecision)) {
                length = StringUtils.isBlank(characterLength) ? numericPrecision : characterLength;
            }
            // ζ°ε­ζ εΊ¦
            String numericScale = result.getString("NUMERIC_SCALE");
            String scale = "0";
            if (StringUtils.isNotBlank(numericScale)) {
                scale = numericScale;
            }
            // ε­ζ?΅η±»ε
            StringBuilder columnType = new StringBuilder(dataType);
            if (!StringUtils.equals("0", length)) {
                columnType.append("(").append(length);
                if (!StringUtils.equals("0", scale)) {
                    columnType.append(",").append(scale);
                }
                columnType.append(")");
            }
            TableField field = new TableField(SQLServerDataTypeEnum.values(dataType.toLowerCase()).getValue().getType(), name, comments, columnType.toString(), dataType, length, scale);
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
        return "SELECT * FROM sysobjects WHERE XTYPE='U'";
    }

    @Override
    public String fieldSql(DbContext context) {
        return "SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME='" + context.getTableName() + "'";
    }

    @Override
    protected String selectSql(DbContext context) {
        Integer page = context.getPage();
        Integer size = context.getSize();
        return "SELECT * FROM (SELECT * , (ROW_NUMBER() OVER(ORDER BY @@SERVERNAME)-1)/" + size + " AS TEMP_NUM FROM " + context.getTableName() + ") temp WHERE TEMP_NUM = " + (page - 1);
    }

    @Override
    protected String buildQueryLimit(DbContext context) {
        return "SELECT * FROM (SELECT * , (ROW_NUMBER() OVER(ORDER BY @@SERVERNAME)-1)/10 AS TEMP_NUM FROM (" + context.getQuerySql() + ") temp1) temp2 WHERE TEMP_NUM = 1";
    }
}
