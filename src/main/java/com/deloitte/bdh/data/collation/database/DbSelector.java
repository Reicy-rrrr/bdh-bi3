package com.deloitte.bdh.data.collation.database;

import com.deloitte.bdh.data.collation.database.dto.DbContext;
import com.deloitte.bdh.data.collation.database.po.TableData;
import com.deloitte.bdh.data.collation.database.po.TableSchema;

import java.util.List;

public interface DbSelector {
    String test(DbContext context) throws Exception;

    List<String> getTables(DbContext context) throws Exception;

    List<String> getFields(DbContext context) throws Exception;

    TableSchema getTableSchema(DbContext context) throws Exception;

    TableData getTableData(DbContext context) throws Exception;

    long getTableCount(DbContext context) throws Exception;
}
