package com.deloitte.bdh.data.collation.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.deloitte.bdh.common.base.AbstractService;
import com.deloitte.bdh.common.base.PageResult;
import com.deloitte.bdh.common.constant.DSConstant;
import com.deloitte.bdh.common.date.DateUtils;
import com.deloitte.bdh.common.exception.BizException;
import com.deloitte.bdh.common.properties.BiProperties;
import com.deloitte.bdh.common.util.AliyunOssUtil;
import com.deloitte.bdh.common.util.JsonUtil;
import com.deloitte.bdh.common.util.NifiProcessUtil;
import com.deloitte.bdh.common.util.ThreadLocalHolder;
import com.deloitte.bdh.data.analyse.enums.ResourceMessageEnum;
import com.deloitte.bdh.data.collation.controller.BiTenantConfigController;
import com.deloitte.bdh.data.collation.dao.bi.BiEtlDatabaseInfMapper;
import com.deloitte.bdh.data.collation.database.DbHandler;
import com.deloitte.bdh.data.collation.database.DbSelector;
import com.deloitte.bdh.data.collation.database.dto.DbContext;
import com.deloitte.bdh.data.collation.database.po.TableData;
import com.deloitte.bdh.data.collation.database.po.TableField;
import com.deloitte.bdh.data.collation.database.po.TableSchema;
import com.deloitte.bdh.data.collation.enums.*;
import com.deloitte.bdh.data.collation.service.NifiProcessService;
import com.deloitte.bdh.data.collation.model.*;
import com.deloitte.bdh.data.collation.model.request.*;
import com.deloitte.bdh.data.collation.service.*;
import com.github.pagehelper.PageInfo;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.io.InputStream;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * ???????????????
 * </p>
 *
 * @author lw
 * @since 2020-09-24
 */
@Service
@DS(DSConstant.BI_DB)
public class BiEtlDatabaseInfServiceImpl extends AbstractService<BiEtlDatabaseInfMapper, BiEtlDatabaseInf> implements BiEtlDatabaseInfService {
    private static final Logger logger = LoggerFactory.getLogger(BiEtlDatabaseInfServiceImpl.class);

    @Resource
    private BiEtlDatabaseInfMapper biEtlDatabaseInfMapper;
    @Autowired
    private NifiProcessService nifiProcessService;
    @Autowired
    private AliyunOssUtil aliyunOss;
    @Autowired
    private FileReadService fileReadService;
    @Autowired
    private BiEtlDbFileService biEtlDbFileService;
    @Autowired
    private BiEtlMappingConfigService configService;
    @Autowired
    private DbSelector dbSelector;
    @Autowired
    private DbHandler dbHandler;
    @Autowired
    private BiEtlSyncPlanService syncPlanService;
    @Autowired
    private BiEtlModelService modelService;
    @Autowired
    private BiTenantConfigService biTenantConfigService;
    @Resource
    private BiProperties biProperties;
    @Resource
    private BiDataSetService dataSetService;

    @Override
    public BiEtlDatabaseInf initDatabaseInfo() {
        if (0 == this.count()) {
            //????????????????????????
            CreateResourcesDto resourcesDto = new CreateResourcesDto();
            resourcesDto.setName("????????????????????????");
            resourcesDto.setComments("???????????????");
            resourcesDto.setType(SourceTypeEnum.File_Excel.getType());
            resourcesDto.setDbName("ORDERS_USCA_BI");
            return this.createResource(resourcesDto, true);
        }
        return this.getOne(new LambdaQueryWrapper<BiEtlDatabaseInf>().orderByAsc(BiEtlDatabaseInf::getId).last("limit 1"));
    }

    @Override
    public PageResult<BiEtlDatabaseInf> getResources(GetResourcesDto dto) {
        LambdaQueryWrapper<BiEtlDatabaseInf> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        // ?????????????????????????????????
        if (StringUtils.isNotBlank(dto.getName())) {
            lambdaQueryWrapper.like(BiEtlDatabaseInf::getName, dto.getName());
        }
        if (StringUtils.isNotBlank(dto.getEffect())) {
            lambdaQueryWrapper.eq(BiEtlDatabaseInf::getEffect, dto.getEffect());
        }
        if (!StringUtils.equals(dto.getSuperUserFlag(), YesOrNoEnum.YES.getKey())) {
            lambdaQueryWrapper.in(BiEtlDatabaseInf::getCreateUser, ThreadLocalHolder.getOperator(), BiTenantConfigController.OPERATOR);
        }
        lambdaQueryWrapper.orderByDesc(BiEtlDatabaseInf::getCreateDate);
        PageInfo<BiEtlDatabaseInf> pageInfo = new PageInfo<>(this.list(lambdaQueryWrapper));
        return new PageResult<>(pageInfo);
    }

    @Override
    @Transactional
    public BiEtlDatabaseInf createResource(CreateResourcesDto dto, boolean init) {
        BiEtlDatabaseInf inf = null;
        SourceTypeEnum typeEnum = SourceTypeEnum.values(dto.getType());
        switch (typeEnum) {
            case File_Csv:
            case File_Excel:
                inf = createResourceFromFile(dto);
                break;
            case Hive2:
            case Hive:
                inf = createResourceFromHive(dto);
                break;
            case Hana:
            case SQLServer:
            case Oracle:
            case Mysql:
                inf = createResourceFromDB(dto);
                break;
            default:
                throw new RuntimeException("???????????????????????????????????????!");
        }
        runResource(inf.getId(), EffectEnum.ENABLE.getKey(), init);
        return inf;
    }

    @Override
    public BiEtlDatabaseInf createFileResource(CreateFileResourcesDto dto) {
        // ????????????id
        String fileId = dto.getFileId();
        if (StringUtils.isBlank(fileId)) {
            logger.error("?????????????????????????????????????????????????????????id?????????");
            throw new BizException(ResourceMessageEnum.SAVE_FILE_SOURCE_FAIL_1.getCode(),
                    localeMessageService.getMessage(ResourceMessageEnum.SAVE_FILE_SOURCE_FAIL_1.getMessage(), ThreadLocalHolder.getLang()));
        }

        BiEtlDbFile dbFile = biEtlDbFileService.getById(fileId);
        if (dbFile == null) {
            logger.error("???????????????????????????????????????id[{}]???????????????????????????", fileId);
            throw new BizException(ResourceMessageEnum.SAVE_FILE_SOURCE_FAIL_2.getCode(),
                    localeMessageService.getMessage(ResourceMessageEnum.SAVE_FILE_SOURCE_FAIL_2.getMessage(), ThreadLocalHolder.getLang()));
        }
        // ?????????????????????????????????????????????????????????
        if (dbFile.getReadFlag() == 0) {
            logger.error("???????????????????????????????????????id[{}]?????????????????????????????????", fileId);
            throw new BizException(ResourceMessageEnum.SAVE_FILE_SOURCE_FAIL_3.getCode(),
                    localeMessageService.getMessage(ResourceMessageEnum.SAVE_FILE_SOURCE_FAIL_3.getMessage(), ThreadLocalHolder.getLang()));
        }

        // ???????????????dto??????????????????????????????
        CreateResourcesDto createDto = new CreateResourcesDto();
        String fileType = dbFile.getFileType();
        BeanUtils.copyProperties(dto, createDto);
        if (fileType.endsWith(FileTypeEnum.Csv.getValue())) {
            createDto.setType(SourceTypeEnum.File_Csv.getType());
        } else {
            createDto.setType(SourceTypeEnum.File_Excel.getType());
        }
        BiEtlDatabaseInf inf = createResource(createDto, false);

        // ???????????????????????????????????????
        String tableName = initImportTableName(inf);

        // ???????????????????????????
        List<TableField> tableFields = initTableSchema(dto.getColumns());
        dbHandler.createTable(inf.getId(), tableName, tableFields);
        // ?????????????????????????????????
        Map<String, TableField> tableFieldMap = tableFields.stream().collect(Collectors.toMap(TableField::getDesc, tableField -> tableField));

        String fileName = dbFile.getStoredFileName();
        String filePath = dbFile.getFilePath();
        // ???oss?????????????????????
        InputStream fileStream = aliyunOss.getFileStream(filePath, fileName);
        // ????????????
        fileReadService.readIntoDB(fileStream, fileType, tableFieldMap, tableName);
        // ??????????????????????????????id
        dbFile.setDbId(inf.getId());
        // ???????????????????????????
        dbFile.setReadFlag(0);
        biEtlDbFileService.updateById(dbFile);

        // ???????????????????????????????????????????????????????????????
        inf.setDbName(tableName);
        inf.setEffect(EffectEnum.ENABLE.getKey());
        this.updateById(inf);
        return inf;
    }

    @Override
    public BiEtlDatabaseInf appendFileResource(AppendFileResourcesDto dto) throws Exception {
        // ?????????id
        String dbId = dto.getDbId();
        if (StringUtils.isBlank(dbId)) {
            logger.error("??????????????????????????????????????????????????????id?????????");
            throw new BizException(ResourceMessageEnum.SAVE_FILE_SOURCE_FAIL_4.getCode(),
                    localeMessageService.getMessage(ResourceMessageEnum.SAVE_FILE_SOURCE_FAIL_4.getMessage(), ThreadLocalHolder.getLang()));
        }
        // ????????????id
        String fileId = dto.getFileId();
        if (StringUtils.isBlank(fileId)) {
            logger.error("?????????????????????????????????????????????????????????id?????????");
            throw new BizException(ResourceMessageEnum.SAVE_FILE_SOURCE_FAIL_5.getCode(),
                    localeMessageService.getMessage(ResourceMessageEnum.SAVE_FILE_SOURCE_FAIL_5.getMessage(), ThreadLocalHolder.getLang()));
        }

        BiEtlDatabaseInf database = this.getById(dbId);
        if (database == null) {
            logger.error("?????????????????????????????????id[{}]", dbId);
            throw new BizException(ResourceMessageEnum.SAVE_FILE_SOURCE_FAIL_6.getCode(),
                    localeMessageService.getMessage(ResourceMessageEnum.SAVE_FILE_SOURCE_FAIL_6.getMessage(), ThreadLocalHolder.getLang()), dbId);
        }
        SourceTypeEnum sourceType = SourceTypeEnum.values(database.getType());
        if (SourceTypeEnum.File_Excel != sourceType && SourceTypeEnum.File_Csv != sourceType) {
            logger.error("?????????????????????????????????????????????[{}]", sourceType.getTypeName());
            throw new BizException(ResourceMessageEnum.SAVE_FILE_SOURCE_FAIL_7.getCode(),
                    localeMessageService.getMessage(ResourceMessageEnum.SAVE_FILE_SOURCE_FAIL_7.getMessage(), ThreadLocalHolder.getLang()), dbId);
        }

        BiEtlDbFile dbFile = biEtlDbFileService.getById(fileId);
        if (dbFile == null) {
            logger.error("???????????????????????????????????????id[{}]???????????????????????????", fileId);
            throw new BizException(ResourceMessageEnum.SAVE_FILE_SOURCE_FAIL_8.getCode(),
                    localeMessageService.getMessage(ResourceMessageEnum.SAVE_FILE_SOURCE_FAIL_8.getMessage(), ThreadLocalHolder.getLang()), dbId);
        }
        // ?????????????????????????????????????????????????????????
        if (dbFile.getReadFlag() == 0) {
            logger.error("???????????????????????????????????????id[{}]?????????????????????????????????", fileId);
            throw new BizException(ResourceMessageEnum.SAVE_FILE_SOURCE_FAIL_3.getCode(),
                    localeMessageService.getMessage(ResourceMessageEnum.SAVE_FILE_SOURCE_FAIL_3.getMessage(), ThreadLocalHolder.getLang()), dbId);
        }

        // ?????????????????????
        String tableName = database.getDbName();
        // ????????????
        String fileName = dbFile.getStoredFileName();
        String filePath = dbFile.getFilePath();
        // ???oss?????????????????????
        InputStream fileStream = aliyunOss.getFileStream(filePath, fileName);
        // ????????????
        String fileType = dbFile.getFileType();

        // ????????????????????????????????????????????????
        List<TableField> historyFields = dbHandler.getTableFields(tableName);
        // ?????????????????????????????????
        Map<String, TableField> tableFieldMap = historyFields.stream().collect(Collectors.toMap(TableField::getDesc, tableField -> tableField));

        // ?????????????????????????????????
        Map<String, TableField> importFields = Maps.newHashMap();
        // ???????????????????????????????????????????????????????????????
        boolean validateFlag = true;
        StringBuilder errorMsg = new StringBuilder(localeMessageService.getMessage(ResourceMessageEnum.SAVE_FILE_SOURCE_FAIL_9.getMessage(), ThreadLocalHolder.getLang()));
        for (String importColumn : dto.getColumns().keySet()) {
            if (!tableFieldMap.containsKey(importColumn)) {
                validateFlag = false;
                errorMsg.append(importColumn).append("|");
                continue;
            }
            importFields.put(importColumn, MapUtils.getObject(tableFieldMap, importColumn));
        }
        // ????????????????????????|???
        if (errorMsg.lastIndexOf("|") >= 0) {
            errorMsg.deleteCharAt(errorMsg.lastIndexOf("|"));
        }

        errorMsg.append(localeMessageService.getMessage(ResourceMessageEnum.SAVE_FILE_SOURCE_FAIL_10.getMessage(), ThreadLocalHolder.getLang()));
        if (!validateFlag) {
            logger.error(errorMsg.toString());
            throw new BizException(errorMsg.toString());
        }

        fileReadService.readIntoDB(fileStream, fileType, importFields, tableName);
        // ??????????????????????????????id
        dbFile.setDbId(database.getId());
        // ???????????????????????????
        dbFile.setReadFlag(0);
        biEtlDbFileService.updateById(dbFile);
        if (StringUtils.isNotBlank(dto.getName())) {
            database.setName(dto.getName());
            this.updateById(database);
        }
        return database;
    }

    @Override
    public BiEtlDatabaseInf resetFileResource(ResetFileResourcesDto dto) throws Exception {
        // ?????????id
        String dbId = dto.getDbId();
        if (StringUtils.isBlank(dbId)) {
            logger.error("??????????????????????????????????????????????????????id?????????");
            throw new BizException(ResourceMessageEnum.RESET_FILE_SOURCE_FAIL_1.getCode(),
                    localeMessageService.getMessage(ResourceMessageEnum.RESET_FILE_SOURCE_FAIL_1.getMessage(), ThreadLocalHolder.getLang()));
        }
        // ????????????id
        String fileId = dto.getFileId();
        if (StringUtils.isBlank(fileId)) {
            logger.error("?????????????????????????????????????????????????????????id?????????");
            throw new BizException(ResourceMessageEnum.RESET_FILE_SOURCE_FAIL_2.getCode(),
                    localeMessageService.getMessage(ResourceMessageEnum.RESET_FILE_SOURCE_FAIL_2.getMessage(), ThreadLocalHolder.getLang()));
        }

        BiEtlDatabaseInf database = this.getById(dbId);
        if (database == null) {
            logger.error("?????????????????????????????????id[{}]", dbId);
            throw new BizException(ResourceMessageEnum.DATA_SOURCE_NOT_EXIST.getCode(),
                    localeMessageService.getMessage(ResourceMessageEnum.DATA_SOURCE_NOT_EXIST.getMessage(), ThreadLocalHolder.getLang()));
        }
        SourceTypeEnum sourceType = SourceTypeEnum.values(database.getType());
        if (SourceTypeEnum.File_Excel != sourceType && SourceTypeEnum.File_Csv != sourceType) {
            logger.error("?????????????????????????????????????????????[{}]", sourceType.getTypeName());
            throw new BizException(ResourceMessageEnum.DATA_SOURCE_NOT_EXIST.getCode(),
                    localeMessageService.getMessage(ResourceMessageEnum.DATA_SOURCE_NOT_EXIST.getMessage(), ThreadLocalHolder.getLang()));
        }

        BiEtlDbFile dbFile = biEtlDbFileService.getById(fileId);
        if (dbFile == null) {
            logger.error("???????????????????????????????????????id[{}]???????????????????????????", fileId);
            throw new BizException(ResourceMessageEnum.RESET_FILE_SOURCE_FAIL_3.getCode(),
                    localeMessageService.getMessage(ResourceMessageEnum.RESET_FILE_SOURCE_FAIL_3.getMessage(), ThreadLocalHolder.getLang()));
        }
        // ?????????????????????????????????????????????????????????
        if (dbFile.getReadFlag() == 0) {
            logger.error("???????????????????????????????????????id[{}]?????????????????????????????????", fileId);
            throw new BizException(ResourceMessageEnum.RESET_FILE_SOURCE_FAIL_4.getCode(),
                    localeMessageService.getMessage(ResourceMessageEnum.RESET_FILE_SOURCE_FAIL_4.getMessage(), ThreadLocalHolder.getLang()));
        }

        // ???????????????
        String tableName = database.getDbName();
        dbHandler.drop(tableName);
        // ??????????????????????????????ftp??????????????????
        biEtlDbFileService.deleteByDbId(dbId);

        // ???????????????
        String fileName = dbFile.getStoredFileName();
        String filePath = dbFile.getFilePath();
        // ???oss?????????????????????
        InputStream fileStream = aliyunOss.getFileStream(filePath, fileName);
        // ????????????
        String fileType = dbFile.getFileType();
        // ???????????????????????????
        List<TableField> tableFields = initTableSchema(dto.getColumns());
        dbHandler.createTable(dbId, tableName, tableFields);
        // ?????????????????????????????????
        Map<String, TableField> tableFieldMap = tableFields.stream().collect(Collectors.toMap(TableField::getDesc, tableField -> tableField));
        fileReadService.readIntoDB(fileStream, fileType, tableFieldMap, tableName);
        // ??????????????????????????????id
        dbFile.setDbId(database.getId());
        // ???????????????????????????
        dbFile.setReadFlag(0);
        biEtlDbFileService.updateById(dbFile);

        //?????????????????????
        if (StringUtils.isNotBlank(dto.getName())) {
            database.setName(dto.getName());
            this.updateById(database);
        }
        //?????????????????????????????????
        ThreadLocalHolder.async(() -> dataSetService.delRelationByDbId(database.getId()));
        return database;
    }

    @Override
    public BiEtlDatabaseInf runResource(String id, String effect, boolean init) {
        BiEtlDatabaseInf inf = biEtlDatabaseInfMapper.selectById(id);
        if (!effect.equals(inf.getEffect())) {
            if (!init && inf.getCreateUser().equals(BiTenantConfigController.OPERATOR)) {
                throw new BizException(ResourceMessageEnum.DEFAULT_DATA_LOCK.getCode(),
                        localeMessageService.getMessage(ResourceMessageEnum.DEFAULT_DATA_LOCK.getMessage(), ThreadLocalHolder.getLang()));
            }

            if (!SourceTypeEnum.File_Csv.getType().equals(inf.getType()) && !SourceTypeEnum.File_Excel.getType().equals(inf.getType())) {
                //????????????????????????
                if (EffectEnum.DISABLE.getKey().equals(effect)) {
                    //?????????????????????????????????????????????
                    List<BiEtlMappingConfig> configList = configService.list(new LambdaQueryWrapper<BiEtlMappingConfig>()
                            .eq(BiEtlMappingConfig::getRefSourceId, inf.getId()));
                    if (CollectionUtils.isNotEmpty(configList)) {
                        Set<String> modelCodes = configList.stream().map(BiEtlMappingConfig::getRefModelCode).collect(Collectors.toSet());
                        if (CollectionUtils.isNotEmpty(modelCodes)) {
                            List<BiEtlModel> models = modelService.list(new LambdaQueryWrapper<BiEtlModel>()
                                    .eq(BiEtlModel::getSyncStatus, YesOrNoEnum.YES.getKey())
                                    .in(BiEtlModel::getCode, modelCodes));
                            if (CollectionUtils.isNotEmpty(models)) {
                                List<String> names = models.stream().map(BiEtlModel::getName).collect(Collectors.toList());
                                throw new BizException(ResourceMessageEnum.TEMPLATE_IN_USE.getCode(),
                                        localeMessageService.getMessage(ResourceMessageEnum.TEMPLATE_IN_USE.getMessage(), ThreadLocalHolder.getLang()),
                                        StringUtils.join(names, ","));
                            }
                        }
                    }

                    Set<String> configCodeSet = configList.stream().map(BiEtlMappingConfig::getCode).collect(Collectors.toSet());
                    if (CollectionUtils.isNotEmpty(configCodeSet)) {
                        List<BiEtlSyncPlan> runningPlan = syncPlanService.list(new LambdaQueryWrapper<BiEtlSyncPlan>()
                                .in(BiEtlSyncPlan::getRefMappingCode, configCodeSet)
                                .isNull(BiEtlSyncPlan::getPlanResult));
                        if (CollectionUtils.isNotEmpty(runningPlan)) {
                            List<String> modelCodes = runningPlan.stream().map(BiEtlSyncPlan::getRefModelCode).collect(Collectors.toList());
                            List<BiEtlModel> models = modelService.list(new LambdaQueryWrapper<BiEtlModel>()
                                    .in(BiEtlModel::getCode, modelCodes));
                            List<String> names = models.stream().map(BiEtlModel::getName).collect(Collectors.toList());
                            throw new BizException(ResourceMessageEnum.SOURCE_IN_USE.getCode(),
                                    localeMessageService.getMessage(ResourceMessageEnum.SOURCE_IN_USE.getMessage(), ThreadLocalHolder.getLang()),
                                    StringUtils.join(names, ","));
                        }
                    }
                }
                String controllerServiceId = inf.getControllerServiceId();
                Map<String, Object> sourceMap = nifiProcessService.runControllerService(controllerServiceId, effect);
                inf.setVersion(NifiProcessUtil.getVersion(sourceMap));
            }
            inf.setEffect(effect);
            biEtlDatabaseInfMapper.updateById(inf);
        }
        return inf;
    }

    @Override
    public void delResource(String id) throws Exception {
        BiEtlDatabaseInf inf = biEtlDatabaseInfMapper.selectById(id);
        if (inf.getCreateUser().equals(BiTenantConfigController.OPERATOR)) {
            throw new BizException(ResourceMessageEnum.DEFAULT_DATA_LOCK.getCode(),
                    localeMessageService.getMessage(ResourceMessageEnum.DEFAULT_DATA_LOCK.getMessage(), ThreadLocalHolder.getLang()));
        }

        if (EffectEnum.ENABLE.getKey().equals(inf.getEffect())) {
            throw new BizException(ResourceMessageEnum.DELETE_LOCK_ENABLE.getCode(),
                    localeMessageService.getMessage(ResourceMessageEnum.DELETE_LOCK_ENABLE.getMessage(), ThreadLocalHolder.getLang()));
        }

        // ???????????? ?????????????????????????????????
        List<BiEtlMappingConfig> configList = configService.list(new LambdaQueryWrapper<BiEtlMappingConfig>()
                .eq(BiEtlMappingConfig::getRefSourceId, inf.getId())
        );
        if (CollectionUtils.isNotEmpty(configList)) {
            throw new BizException(ResourceMessageEnum.DELETE_LOCK_SOURCE_IN_USE.getCode(),
                    localeMessageService.getMessage(ResourceMessageEnum.DELETE_LOCK_SOURCE_IN_USE.getMessage(), ThreadLocalHolder.getLang()));
        }

        if (!SourceTypeEnum.File_Csv.getType().equals(inf.getType()) && !SourceTypeEnum.File_Excel.getType().equals(inf.getType())) {
            String controllerServiceId = inf.getControllerServiceId();

            nifiProcessService.delControllerService(controllerServiceId);
        } else {
            // ??????????????????????????????????????????????????????ftp?????????
            biEtlDbFileService.deleteByDbId(id);
            // ?????????????????????(?????????????????????dbName???????????????????????????)
            String tableName = inf.getDbName();
            if (StringUtils.isNotBlank(tableName)) {
                dbHandler.drop(tableName);
            }
        }
        biEtlDatabaseInfMapper.deleteById(id);
    }

    @Override
    public BiEtlDatabaseInf updateResource(UpdateResourcesDto dto) throws Exception {
        BiEtlDatabaseInf inf = biEtlDatabaseInfMapper.selectById(dto.getId());
        if (EffectEnum.ENABLE.getKey().equals(inf.getEffect())) {
            throw new BizException(ResourceMessageEnum.UPDATE_LOCK_SOURCE_IN_USE.getCode(),
                    localeMessageService.getMessage(ResourceMessageEnum.UPDATE_LOCK_SOURCE_IN_USE.getMessage(), ThreadLocalHolder.getLang()));

        }
        if (inf.getCreateUser().equals(BiTenantConfigController.OPERATOR)) {
            throw new BizException(ResourceMessageEnum.DEFAULT_DATA_LOCK.getCode(),
                    localeMessageService.getMessage(ResourceMessageEnum.DEFAULT_DATA_LOCK.getMessage(), ThreadLocalHolder.getLang()));
        }
        if (!SourceTypeEnum.File_Csv.getType().equals(inf.getType()) && !SourceTypeEnum.File_Excel.getType().equals(inf.getType())) {
            return updateResourceFromMysql(dto);
        } else {
            return updateResourceFromFile(dto);

        }
    }

    @Override
    public String testConnection(TestConnectionDto dto) {
        return testConnection(dto.getDbType(), dto.getIp(), dto.getPort(), dto.getDbName(), dto.getDbUserName(), dto.getDbPassword());
    }

    private String testConnection(String dbType, String ip, String port, String dbName, String userName, String pwd) {
        String result = "????????????";
        try {
            DbContext context = new DbContext();
            context.setSourceTypeEnum(SourceTypeEnum.values(dbType));
            context.setDbUrl(NifiProcessUtil.getDbUrl(dbType, ip, port, dbName));
            context.setDbUserName(userName);
            context.setDbPassword(pwd);
            context.setDriverName(SourceTypeEnum.getDriverNameByType(dbType));
            dbSelector.test(context);
        } catch (Exception e) {
            log.error("????????????????????????", e);
            throw new BizException(ResourceMessageEnum.CONNECT_FAIL.getCode(),
                    localeMessageService.getMessage(ResourceMessageEnum.CONNECT_FAIL.getMessage(), ThreadLocalHolder.getLang()));

        }
        return result;
    }

    @Override
    public List<String> getTables(String dbId) throws Exception {
        DbContext context = new DbContext();
        context.setDbId(dbId);
        return dbSelector.getTables(context);
    }

    @Override
    public List<String> getFields(String dbId, String tableName) throws Exception {
        DbContext context = new DbContext();
        context.setDbId(dbId);
        context.setTableName(tableName);
        return dbSelector.getFields(context);
    }

    @Override
    public TableSchema getTableSchema(GetTableSchemaDto dto) throws Exception {
        DbContext context = new DbContext();
        context.setDbId(dto.getDbId());
        context.setTableName(dto.getTableName());
        TableSchema schema = dbSelector.getTableSchema(context);
        return schema;
    }

    @Override
    public TableData getTableData(GetTableDataDto dto) throws Exception {
        DbContext context = new DbContext();
        context.setDbId(dto.getDbId());
        context.setTableName(dto.getTableName());
        context.setPage(dto.getPage());
        context.setSize(dto.getSize());
        TableData data = dbSelector.getTableData(context);
        return data;
    }

    private BiEtlDatabaseInf updateResourceFromMysql(UpdateResourcesDto dto) throws Exception {
        if (StringUtils.isAllBlank(dto.getDbName(), dto.getDbPassword(), dto.getDbUser(), dto.getPort())) {
            throw new BizException(ResourceMessageEnum.CONFIG_SOURCE_PARAM_ERROR.getCode(),
                    localeMessageService.getMessage(ResourceMessageEnum.CONFIG_SOURCE_PARAM_ERROR.getMessage(), ThreadLocalHolder.getLang()),
                    JsonUtil.obj2String(dto));
        }

        testConnection(dto.getType(), dto.getAddress(), dto.getPort(), dto.getDbName(), dto.getDbUser(), dto.getDbPassword());
        BiEtlDatabaseInf biEtlDatabaseInf = biEtlDatabaseInfMapper.selectById(dto.getId());
        biEtlDatabaseInf.setName(dto.getName());
        biEtlDatabaseInf.setComments(dto.getComments());
        biEtlDatabaseInf.setType(dto.getType());
        if (StringUtils.isNotBlank(dto.getAddress())) {
            biEtlDatabaseInf.setAddress(dto.getAddress());
        }
        if (StringUtils.isNotBlank(dto.getDbName())) {
            biEtlDatabaseInf.setDbName(dto.getDbName());
        }
        if (StringUtils.isNotBlank(dto.getDbUser())) {
            biEtlDatabaseInf.setDbUser(dto.getDbUser());
        }
        if (StringUtils.isNotBlank(dto.getDbPassword())) {
            biEtlDatabaseInf.setDbPassword(dto.getDbPassword());
        }
        if (StringUtils.isNotBlank(dto.getPort())) {
            biEtlDatabaseInf.setPort(dto.getPort());
        }

        //??????type ??????
        biEtlDatabaseInf.setDriverName(SourceTypeEnum.getDriverNameByType(biEtlDatabaseInf.getType()));
        biEtlDatabaseInf.setTypeName(SourceTypeEnum.getNameByType(biEtlDatabaseInf.getType()));
        biEtlDatabaseInf.setEffect(EffectEnum.DISABLE.getKey());

        //??????nifi
        Map<String, Object> properties = Maps.newHashMap();
        properties.put("Database User", biEtlDatabaseInf.getDbUser());
        properties.put("Password", biEtlDatabaseInf.getDbPassword());
        properties.put("Database Connection URL", NifiProcessUtil.getDbUrl(biEtlDatabaseInf.getType(),
                biEtlDatabaseInf.getAddress(), biEtlDatabaseInf.getPort(), biEtlDatabaseInf.getDbName()));
        properties.put("Database Driver Class Name", biEtlDatabaseInf.getDriverName());
        properties.put("database-driver-locations", biEtlDatabaseInf.getDriverLocations());

        Map<String, Object> request = Maps.newHashMap();
        request.put("id", biEtlDatabaseInf.getControllerServiceId());
        request.put("name", biEtlDatabaseInf.getName());
        request.put("comments", biEtlDatabaseInf.getComments());
        request.put("properties", properties);

        Map<String, Object> sourceMap = nifiProcessService.updControllerService(request);
        biEtlDatabaseInf.setVersion(NifiProcessUtil.getVersion(sourceMap));
        biEtlDatabaseInfMapper.updateById(biEtlDatabaseInf);
        return biEtlDatabaseInf;
    }

    @Deprecated
    private BiEtlDatabaseInf updateResourceFromFile(UpdateResourcesDto dto) throws Exception {
        BiEtlDatabaseInf source = biEtlDatabaseInfMapper.selectById(dto.getId());
        if (source == null) {
            throw new BizException(ResourceMessageEnum.DATA_SOURCE_NOT_EXIST.getCode(),
                    localeMessageService.getMessage(ResourceMessageEnum.DATA_SOURCE_NOT_EXIST.getMessage(), ThreadLocalHolder.getLang()));

        }
        // ?????????????????????????????????????????????????????????
        BiEtlDatabaseInf updateObj = new BiEtlDatabaseInf();
        BeanUtils.copyProperties(dto, updateObj);
        if (StringUtils.isNotBlank(dto.getName())) {
            updateObj.setName(dto.getName());
        }
        if (StringUtils.isNotBlank(dto.getComments())) {
            updateObj.setName(dto.getComments());
        }
        biEtlDatabaseInfMapper.updateById(updateObj);
        return updateObj;
    }

    private BiEtlDatabaseInf createResourceFromFile(CreateResourcesDto dto) {
        BiEtlDatabaseInf inf = new BiEtlDatabaseInf();
        BeanUtils.copyProperties(dto, inf);
        inf.setTypeName(SourceTypeEnum.getNameByType(inf.getType()));
        inf.setEffect(EffectEnum.DISABLE.getKey());
        inf.setTenantId(ThreadLocalHolder.getTenantId());

        // ??????nifi ?????? ??????rootgroupid
        inf.setVersion("1");
        inf.setControllerServiceId(null);
        inf.setRootGroupId(biTenantConfigService.getGroupId());
        biEtlDatabaseInfMapper.insert(inf);
        return inf;
    }

    private BiEtlDatabaseInf createResourceFromDB(CreateResourcesDto dto) {
        if (StringUtils.isAnyBlank(dto.getDbName(), dto.getDbPassword(), dto.getDbUser(), dto.getPort())) {
            throw new BizException(ResourceMessageEnum.CONFIG_SOURCE_PARAM_ERROR.getCode(),
                    localeMessageService.getMessage(ResourceMessageEnum.CONFIG_SOURCE_PARAM_ERROR.getMessage(), ThreadLocalHolder.getLang()),
                    JsonUtil.obj2String(dto));
        }
        BiEtlDatabaseInf exitDb = biEtlDatabaseInfMapper.selectOne(new LambdaQueryWrapper<BiEtlDatabaseInf>()
                .eq(BiEtlDatabaseInf::getName, dto.getName())
        );
        if (null != exitDb) {
            throw new BizException(ResourceMessageEnum.SOURCE_NAME_EXIST.getCode(),
                    localeMessageService.getMessage(ResourceMessageEnum.SOURCE_NAME_EXIST.getMessage(), ThreadLocalHolder.getLang()));
        }

        testConnection(dto.getType(), dto.getAddress(), dto.getPort(), dto.getDbName(), dto.getDbUser(), dto.getDbPassword());
        BiEtlDatabaseInf inf = new BiEtlDatabaseInf();
        BeanUtils.copyProperties(dto, inf);
        inf.setTenantId(ThreadLocalHolder.getTenantId());
        inf.setPoolType(PoolTypeEnum.DBCPConnectionPool.getKey());
        inf.setDriverName(SourceTypeEnum.getDriverNameByType(inf.getType()));
        inf.setTypeName(SourceTypeEnum.getNameByType(inf.getType()));

        //todo ??????????????????
        if (SourceTypeEnum.Mysql.getType().equals(dto.getType())) {
            inf.setDriverLocations(biProperties.getMysqlDriver());
        } else if (SourceTypeEnum.Oracle.getType().equals(dto.getType())) {
            inf.setDriverLocations(biProperties.getOracleDriver());
        } else if (SourceTypeEnum.SQLServer.getType().equals(dto.getType())) {
            inf.setDriverLocations(biProperties.getSqlServerDriver());
        } else if (SourceTypeEnum.Hana.getType().equals(dto.getType())) {
            inf.setDriverLocations(biProperties.getHanaDriver());
        }
        inf.setEffect(EffectEnum.DISABLE.getKey());

        //??????nifi ?????? controllerService
        Map<String, Object> createParams = Maps.newHashMap();
        createParams.put("id", biTenantConfigService.getGroupId());
        //???????????????
        createParams.put("type", inf.getPoolType());
        createParams.put("name", inf.getName());
        createParams.put("dbUser", inf.getDbUser());
        createParams.put("passWord", inf.getDbPassword());
        createParams.put("dbUrl", NifiProcessUtil.getDbUrl(inf.getType(), inf.getAddress(), inf.getPort(), inf.getDbName()));
        createParams.put("driverName", inf.getDriverName());
        createParams.put("driverLocations", inf.getDriverLocations());
        createParams.put("comments", inf.getComments());
        Map<String, Object> sourceMap = nifiProcessService.createControllerService(createParams);

        //nifi ?????????????????????dto
        inf.setVersion(NifiProcessUtil.getVersion(sourceMap));
        inf.setControllerServiceId(MapUtils.getString(sourceMap, "id"));
        inf.setRootGroupId(MapUtils.getString(sourceMap, "parentGroupId"));
        biEtlDatabaseInfMapper.insert(inf);
        return inf;
    }

    private BiEtlDatabaseInf createResourceFromHive(CreateResourcesDto dto) {
        if (StringUtils.isAnyBlank(dto.getDbName(), dto.getDbPassword(), dto.getDbUser(), dto.getPort())) {
            throw new BizException(ResourceMessageEnum.CONFIG_SOURCE_PARAM_ERROR.getCode(),
                    localeMessageService.getMessage(ResourceMessageEnum.CONFIG_SOURCE_PARAM_ERROR.getMessage(), ThreadLocalHolder.getLang()),
                    JsonUtil.obj2String(dto));
        }

        BiEtlDatabaseInf inf = new BiEtlDatabaseInf();
        BeanUtils.copyProperties(dto, inf);
        inf.setPoolType(PoolTypeEnum.HiveConnectionPool.getKey());
        inf.setDriverName(SourceTypeEnum.getDriverNameByType(inf.getType()));
        inf.setTypeName(SourceTypeEnum.getNameByType(inf.getType()));

        inf.setEffect(EffectEnum.DISABLE.getKey());
        inf.setTenantId(ThreadLocalHolder.getTenantId());

        // ??????nifi ?????? controllerService
        Map<String, Object> createParams = Maps.newHashMap();
        createParams.put("id", biTenantConfigService.getGroupId());
        // ???????????????
        createParams.put("type", inf.getPoolType());
        createParams.put("name", inf.getName());
        createParams.put("comments", inf.getComments());

        createParams.put("hive-db-connect-url", NifiProcessUtil.getDbUrl(inf.getType(), inf.getAddress(), inf.getPort(), inf.getDbName()));
        createParams.put("hive-config-resources", biProperties.getHiveSet());
        createParams.put("hive-db-user", inf.getDbUser());
        createParams.put("hive-db-password", inf.getDbPassword());
        Map<String, Object> sourceMap = nifiProcessService.createOtherControllerService(createParams);

        //nifi ?????????????????????dto
        inf.setVersion(NifiProcessUtil.getVersion(sourceMap));
        inf.setControllerServiceId(MapUtils.getString(sourceMap, "id"));
        inf.setRootGroupId(MapUtils.getString(sourceMap, "parentGroupId"));
        biEtlDatabaseInfMapper.insert(inf);
        return inf;
    }

    /**
     * ????????????????????????????????????
     * ???????????????id + "_" + yyyyMMdd + "_" + dbId
     *
     * @param inf
     * @return
     */
    private String initImportTableName(BiEtlDatabaseInf inf) {
        if (inf == null) {
            return null;
        }

        SourceTypeEnum sourceType = SourceTypeEnum.values(inf.getType());

        StringBuilder collectionName = new StringBuilder(32);
        String now = DateUtils.formatShortDate(new Date());
        collectionName.append(sourceType.getTypeName()).append("_").append(inf.getId()).append("_").append(now);
        return collectionName.toString();
    }

    /**
     * ????????????????????????
     * ???????????????????????????????????????????????????????????????
     *
     * @param columns
     * @return
     */
    private List<TableField> initTableSchema(Map<String, String> columns) {
        List<TableField> tableFields = Lists.newArrayList();
        int columnNum = 1;
        for (Map.Entry<String, String> entry : columns.entrySet()) {
            String name = entry.getKey();
            String type = entry.getValue();

            DataTypeEnum dataType = DataTypeEnum.get(type);
            String columnName = "column" + columnNum;
            TableField tableField = null;
            switch (dataType) {
                case Integer:
                    tableField = new TableField(type, columnName, name, "bigint(32)", "bigint", "32", "0");
                    break;
                case Float:
                    tableField = new TableField(type, columnName, name, "decimal(32,8)", "decimal", "32", "8");
                    break;
                case Text:
                    tableField = new TableField(type, columnName, name, "varchar(255)", "varchar", "255", "0");
                    break;
                case Date:
                    tableField = new TableField(type, columnName, name, "date", "date", "0", "0");
                    break;
                case DateTime:
                    tableField = new TableField(type, columnName, name, "datetime", "datetime", "0", "0");
                    break;
                default:
                    tableField = new TableField(type, columnName, name, "varchar(255)", "varchar", "255", "0");
            }
            tableFields.add(tableField);
            columnNum++;
        }
        return tableFields;
    }
}
