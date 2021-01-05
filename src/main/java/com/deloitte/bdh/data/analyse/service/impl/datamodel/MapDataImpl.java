package com.deloitte.bdh.data.analyse.service.impl.datamodel;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.deloitte.bdh.data.analyse.constants.CustomParamsConstants;
import com.deloitte.bdh.data.analyse.enums.DataImplEnum;
import com.deloitte.bdh.data.analyse.enums.DataModelTypeEnum;
import com.deloitte.bdh.data.analyse.enums.MapEnum;
import com.deloitte.bdh.data.analyse.model.datamodel.DataConfig;
import com.deloitte.bdh.data.analyse.model.datamodel.DataModel;
import com.deloitte.bdh.data.analyse.model.datamodel.DataModelField;
import com.deloitte.bdh.data.analyse.model.datamodel.request.ComponentDataRequest;
import com.deloitte.bdh.data.analyse.model.datamodel.response.BaseComponentDataResponse;
import com.deloitte.bdh.data.analyse.service.AnalyseDataService;
import com.deloitte.bdh.data.collation.enums.DataTypeEnum;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;


@Service("mapDataImpl")
public class MapDataImpl extends AbstractDataService implements AnalyseDataService {

    @Override
    public BaseComponentDataResponse handle(ComponentDataRequest request) {
        DataConfig dataConfig = request.getDataConfig();
        DataModel dataModel = dataConfig.getDataModel();
        String tableName = dataModel.getTableName();
        //如果直接List<DataModelField> originalX = dataModel.getX();
        //会发生深拷贝现象，导致下方赋值的时候也会跟着变化
        List<DataModelField> originalX = Lists.newArrayList();
        originalX.addAll(dataModel.getX());
        //符号地图增加经纬度
        if (dataConfig.getTableType().equals(DataImplEnum.MAP_SYMBOL.getTableType())) {
            //查询出地方CODE,方便查出经纬度
            DataModelField placeCode = new DataModelField();
            placeCode.setType(DataTypeEnum.Text.getType());
            placeCode.setQuota(DataModelTypeEnum.WD.getCode());
            placeCode.setDataType(DataTypeEnum.Text.getValue());
            placeCode.setId(MapEnum.PLACECODE.getCode());
            placeCode.setAlias(MapEnum.PLACECODE.getDesc());
            dataModel.getX().add(placeCode);
        }
        if (CollectionUtils.isNotEmpty(dataModel.getX()) && CollectionUtils.isNotEmpty(dataModel.getY())) {
            dataModel.getY().forEach(field -> dataModel.getX().add(field));
        }
        if (CollectionUtils.isNotEmpty(dataModel.getX()) && CollectionUtils.isNotEmpty(dataModel.getCategory())) {
            dataModel.getCategory().forEach(field -> dataModel.getX().add(field));
        }
        Map<String, Object> customParams = dataModel.getCustomParams();
        if (MapUtils.isNotEmpty(customParams)) {
            Object symbolS = MapUtils.getObject(customParams, CustomParamsConstants.SYMBOL_SIZE);
            if (Objects.nonNull(symbolS)) {
                DataModelField symbolSize = JSONObject.parseObject(JSON.toJSONString(symbolS), DataModelField.class);
                if (CollectionUtils.isNotEmpty(dataModel.getX()) && Objects.nonNull(symbolSize)) {
                    dataModel.getX().add(symbolSize);
                }
            }
        }
        BaseComponentDataResponse response = execute(dataModel, buildSql(request.getDataConfig().getDataModel()));
        if (MapUtils.isNotEmpty(dataModel.getCustomParams())) {
            String viewDetail = MapUtils.getString(dataModel.getCustomParams(), CustomParamsConstants.VIEW_DETAIL);
            if (StringUtils.equals(viewDetail, "true")) {
                return response;
            }
        }
        request.getDataConfig().getDataModel().setX(originalX);
        request.getDataConfig().getDataModel().setTableName(tableName);
        response.setExtra(getMinMax(customParams, response.getRows()));
        response.setRows(buildCategory(request, response.getRows(), dataModel.getY()));
        return response;
    }

    private List<Map<String, Object>> buildCategory(ComponentDataRequest request, List<Map<String, Object>> rows, List<DataModelField> yList) {

        List<Map<String, Object>> newRows = Lists.newArrayList();
        DataModel dataModel = request.getDataConfig().getDataModel();
        //查询出所有的经纬度数据
        Map<String, Map<String, String>> queryLongitudeLantitude = queryLongitudeLantitude(dataModel);

        Map<String, Object> customParams = dataModel.getCustomParams();
        DataModelField symbolSizeField = null;
        if (MapUtils.isNotEmpty(customParams)) {
            Object symbolS = MapUtils.getObject(customParams, CustomParamsConstants.SYMBOL_SIZE);
            if (Objects.nonNull(symbolS)) {
                symbolSizeField = JSONObject.parseObject(JSON.toJSONString(symbolS), DataModelField.class);
            }
        }

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
            String symbolSizeName = getColName(symbolSizeField);

            //图例参数
            List<String> categoryPrefix = Lists.newArrayList();
            for (DataModelField category : dataModel.getCategory()) {
                String colName = category.getId();
                if (StringUtils.isNotBlank(category.getAlias())) {
                    colName = category.getAlias();
                }
                String categoryName = StringUtils.join(colName, ": ", MapUtils.getString(row, colName));
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

                if (request.getDataConfig().getTableType().equals(DataImplEnum.MAP_SYMBOL.getTableType())) {
                    List<Object> valueList = Lists.newArrayList();
                    //获取当前地方CODE
                    String placeCode = MapUtils.getString(row, MapEnum.PLACECODE.getDesc());
                    if (StringUtils.isNotEmpty(placeCode)) {
                        //获取当前地方的经纬度数据
                        Map<String, String> longLanMap = queryLongitudeLantitude.get(placeCode);
                        if (Objects.nonNull(longLanMap)) {
                            valueList.add(longLanMap.get(MapEnum.LONGITUDE.getCode()));//经度
                            valueList.add(longLanMap.get(MapEnum.LANTITUDE.getCode()));//纬度
                        } else {
                            valueList.add("");//经度
                            valueList.add("");//纬度
                        }
                    } else {
                        valueList.add("");//经度
                        valueList.add("");//纬度
                    }
                    valueList.add(MapUtils.getObject(row, colName));
                    newRow.put("value", valueList);
                } else {
                    newRow.put("value", MapUtils.getObject(row, colName));
                }
                if (StringUtils.isNotEmpty(symbolSizeName)) {
                    double symbolSize = MapUtils.getDouble(row, symbolSizeName);
                    if (!Double.isNaN(symbolSize)) {
                        newRow.put("symbolSize", StringUtils.join(symbolSizeName, ": ", symbolSize));
                    }
                }
                newRows.add(newRow);
            }
        }
        return newRows;
    }

    private Map<String, Map<String, String>> queryLongitudeLantitude(DataModel dataModel) {
        String sql = "select * from LONGITUDE_LANTITUDE";
        List<Map<String, Object>> rows = execute(dataModel, sql).getRows();
        Map<String, Map<String, String>> returnMap = Maps.newHashMap();
        for (Map<String, Object> row : rows) {
            Map<String, String> longLantitude = Maps.newHashMap();
            longLantitude.put(MapEnum.LONGITUDE.getCode(), MapUtils.getString(row, MapEnum.LONGITUDE.getCode()));
            longLantitude.put(MapEnum.LANTITUDE.getCode(), MapUtils.getString(row, MapEnum.LANTITUDE.getCode()));
            returnMap.put(MapUtils.getString(row, MapEnum.PLACECODE.getCode()), longLantitude);
        }
        return returnMap;
    }

    private Map<String, Object> getMinMax(Map<String, Object> customParams, List<Map<String, Object>> rows) {

        Map<String, Object> result = Maps.newHashMap();
        DataModelField symbolSizeField = null;
        if (MapUtils.isNotEmpty(customParams)) {
            Object symbolS = MapUtils.getObject(customParams, CustomParamsConstants.SYMBOL_SIZE);
            if (Objects.nonNull(symbolS)) {
                symbolSizeField = JSONObject.parseObject(JSON.toJSONString(symbolS), DataModelField.class);
            }
        }
        if (Objects.isNull(symbolSizeField)){
            return result;
        }
        String symbolSizeName = getColName(symbolSizeField);
        List<Double> symbolSizeList = Lists.newArrayList();
        for (Map<String, Object> row : rows) {
            double value = MapUtils.getDouble(row, symbolSizeName);
            symbolSizeList.add(value);
        }
        Map<String, Object> minMaxMap = Maps.newHashMap();
        minMaxMap.put("min", Collections.min(symbolSizeList));
        minMaxMap.put("max", Collections.max(symbolSizeList));
        result.put("minMax",minMaxMap);
        return result;
    }

    @Override
    protected void validate(DataModel dataModel) {

    }
}
