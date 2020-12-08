package com.deloitte.bdh.data.analyse.service.impl.datamodel;

import com.deloitte.bdh.data.analyse.enums.DataImplEnum;
import com.deloitte.bdh.data.analyse.enums.DataModelTypeEnum;
import com.deloitte.bdh.data.analyse.model.datamodel.DataConfig;
import com.deloitte.bdh.data.analyse.model.datamodel.DataModel;
import com.deloitte.bdh.data.analyse.model.datamodel.DataModelField;
import com.deloitte.bdh.data.analyse.model.datamodel.request.BaseComponentDataRequest;
import com.deloitte.bdh.data.analyse.model.datamodel.response.BaseComponentDataResponse;
import com.deloitte.bdh.data.analyse.service.AnalyseDataService;
import com.deloitte.bdh.data.collation.enums.DataTypeEnum;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;


@Service("mapDataImpl")
public class MapDataImpl extends AbstractDataService implements AnalyseDataService {

    @Override
    public BaseComponentDataResponse handle(BaseComponentDataRequest request) {
        DataModel dataModel = request.getDataConfig().getDataModel();
        DataConfig dataConfig = request.getDataConfig();
        //符号地图增加经纬度
        if (dataConfig.getTableType().equals(DataImplEnum.MAP_SYMBOL.getTableType())) {
            //经度
            DataModelField longitude = new DataModelField();
            longitude.setType(DataTypeEnum.Text.getType());
            longitude.setQuota(DataModelTypeEnum.WD.getCode());
            longitude.setDataType(DataTypeEnum.Text.getValue());
            longitude.setId("");
            longitude.setAlias("经度");
            //dataModel.getX().add(longitude);
            //纬度
            DataModelField lantitude = new DataModelField();
            lantitude.setType(DataTypeEnum.Text.getType());
            lantitude.setQuota(DataModelTypeEnum.WD.getCode());
            lantitude.setDataType(DataTypeEnum.Text.getValue());
            lantitude.setId("");
            lantitude.setAlias("纬度");
            //dataModel.getX().add(lantitude);
        }
        List<DataModelField> originalX = Lists.newArrayList(dataModel.getX());
        if (CollectionUtils.isNotEmpty(dataModel.getX()) && CollectionUtils.isNotEmpty(dataModel.getY())) {
            dataModel.getY().forEach(field -> dataModel.getX().add(field));
        }
        if (CollectionUtils.isNotEmpty(dataModel.getX()) && CollectionUtils.isNotEmpty(dataModel.getCategory())) {
            dataModel.getCategory().forEach(field -> dataModel.getX().add(field));
        }
        BaseComponentDataResponse response = execute(buildSql(request.getDataConfig().getDataModel()));
        request.getDataConfig().getDataModel().setX(originalX);
        response.setRows(buildCategory(request, response.getRows(), dataModel.getY()));
        return response;
    }

    private List<Map<String, Object>> buildCategory(BaseComponentDataRequest request, List<Map<String, Object>> rows, List<DataModelField> yList) {

        List<Map<String, Object>> newRows = Lists.newArrayList();
        DataModel dataModel = request.getDataConfig().getDataModel();
        for (Map<String, Object> row : rows) {

            //x轴名称
            List<String> xList = Lists.newArrayList();
            for (DataModelField x : dataModel.getX()) {
                String colName = x.getId();
                if (StringUtils.isNotBlank(x.getAlias())) {
                    colName = x.getAlias();
                }
                xList.add(MapUtils.getString(row, colName));
            }
            //其他参数
            List<String> categoryPrefix = Lists.newArrayList();
            for (DataModelField category : dataModel.getCategory()) {
                String colName = category.getId();
                if (StringUtils.isNotBlank(category.getAlias())) {
                    colName = category.getAlias();
                }
                String categoryName = StringUtils.join(colName,": ",MapUtils.getString(row, colName));
                categoryPrefix.add(categoryName);
            }
            //重新赋值
            for (DataModelField y : yList) {
                String colName = y.getId();
                if (StringUtils.isNotBlank(y.getAlias())) {
                    colName = y.getAlias();
                }
                Map<String, Object> newRow = Maps.newHashMap();
                newRow.put("name", StringUtils.join(xList, "-"));
                if (CollectionUtils.isNotEmpty(categoryPrefix)) {
                    newRow.put("category", categoryPrefix);
                }

                if (request.getDataConfig().getTableType().equals(DataImplEnum.MAP_SYMBOL.getTableType())){
                    List<Object> valueList = Lists.newArrayList();
                    valueList.add("");//经度
                    valueList.add("");//纬度
                    valueList.add(MapUtils.getObject(row, colName));
                    newRow.put("value", valueList);
                }
                else{
                    newRow.put("value", MapUtils.getObject(row, colName));
                }
                newRows.add(newRow);
            }
        }
        return newRows;
    }

    @Override
    protected void validate(DataModel dataModel) {

    }
}