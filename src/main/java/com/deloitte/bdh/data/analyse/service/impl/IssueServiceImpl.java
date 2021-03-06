package com.deloitte.bdh.data.analyse.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beust.jcommander.internal.Lists;
import com.beust.jcommander.internal.Sets;
import com.deloitte.bdh.common.constant.DSConstant;
import com.deloitte.bdh.common.properties.BiProperties;
import com.deloitte.bdh.common.util.ThreadLocalHolder;
import com.deloitte.bdh.data.analyse.enums.CategoryTypeEnum;
import com.deloitte.bdh.data.analyse.model.BiUiAnalyseCategory;
import com.deloitte.bdh.data.analyse.model.BiUiAnalysePage;
import com.deloitte.bdh.data.analyse.model.request.CopyDeloittePageDto;
import com.deloitte.bdh.data.analyse.model.request.CopySourceDto;
import com.deloitte.bdh.data.analyse.model.request.CreateAnalyseCategoryDto;
import com.deloitte.bdh.data.analyse.model.request.IssueDeloitteDto;
import com.deloitte.bdh.data.analyse.model.resp.AnalyseCategoryDto;
import com.deloitte.bdh.data.analyse.model.resp.AnalysePageDto;
import com.deloitte.bdh.data.analyse.service.AnalyseCategoryService;
import com.deloitte.bdh.data.analyse.service.AnalysePageService;
import com.deloitte.bdh.data.analyse.service.IssueService;
import com.deloitte.bdh.data.collation.controller.BiTenantConfigController;
import com.deloitte.bdh.data.collation.database.DbHandler;
import com.deloitte.bdh.data.collation.model.BiDataSet;
import com.deloitte.bdh.data.collation.service.BiDataSetService;
import com.google.common.collect.Maps;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@DS(DSConstant.BI_DB)
public class IssueServiceImpl implements IssueService {
    @Resource
    private BiProperties biProperties;
    @Resource
    private BiDataSetService dataSetService;
    @Resource
    private AnalyseCategoryService categoryService;
    @Resource
    AnalysePageService analysePageService;
    @Resource
    private DbHandler dbHandler;


    @Override
    @Transactional
    public Map<String, String> copyDeloittePage(CopyDeloittePageDto dto) {
        String beginTenantCode = ThreadLocalHolder.getTenantCode();
        //??????????????????
        ThreadLocalHolder.set("tenantCode", biProperties.getInnerTenantCode());
        //???????????????????????????pageId
        List<AnalysePageDto> analysePageDtoList = analysePageService.getPageWithChildren(dto.getFromPageId());
        List<CopySourceDto> copySourceDtoList = Lists.newLinkedList();
        Set<String> uniqueCodeAll = Sets.newHashSet();
        for (AnalysePageDto analysePageDto : analysePageDtoList) {
            CopySourceDto copySourceDto = analysePageService.getCopySourceData(analysePageDto.getId());
            if (CollectionUtils.isNotEmpty(copySourceDto.getOriginCodeList())) {
                Set<String> uniqueCodeList = new HashSet<>(copySourceDto.getOriginCodeList());
                uniqueCodeAll.addAll(uniqueCodeList);
            }
            copySourceDtoList.add(copySourceDto);
        }

        //????????????????????????????????????
        Map<String, String> newCodeMap = Maps.newHashMap();
        for (String code : uniqueCodeAll) {
            //??????????????????
            ThreadLocalHolder.set("tenantCode", biProperties.getInnerTenantCode());
            Map<String, Object> map = analysePageService.buildNewDataSet(dto.getDataSetName(), dto.getDataSetCategoryId(), code);

            //????????????????????????
            ThreadLocalHolder.set("tenantCode", beginTenantCode);
            analysePageService.saveNewTable(map);
            newCodeMap.put(code, MapUtils.getString(map, "newCode"));
        }
        //????????????????????????
        ThreadLocalHolder.set("tenantCode", beginTenantCode);
        String groupId = null;
        for (CopySourceDto copySourceDto : copySourceDtoList) {
            groupId = analysePageService.saveNewPage(groupId, dto.getName() + "(" + copySourceDto.getPageName() + ")", dto.getCategoryId(), copySourceDto.getPageId(),
                    copySourceDto.getLinkPageId(), copySourceDto.getContent(), copySourceDto.getChildrenArr(), newCodeMap);
        }
        return null;
    }

    @Override
    public Map<String, String> issueDeloittePage(IssueDeloitteDto dto) {
        //??????????????????
        if (!ThreadLocalHolder.getTenantCode().equals(biProperties.getInnerTenantCode())) {
            throw new RuntimeException("??????????????????????????????");
        }

        if (dto.isWithData()) {
            return issueWithData(dto);
        }
        return issueNoData(dto);
    }

    private Map<String, String> issueWithData(IssueDeloitteDto dto) {
        List<AnalysePageDto> analysePageDtoList = analysePageService.getPageWithChildren(dto.getFromPageId());
        List<CopySourceDto> copySourceDtoList = Lists.newLinkedList();
        Set<String> uniqueCodeAll = Sets.newHashSet();
        for (AnalysePageDto analysePageDto : analysePageDtoList) {
            CopySourceDto copySourceDto = analysePageService.getCopySourceData(analysePageDto.getId());
            if (CollectionUtils.isNotEmpty(copySourceDto.getOriginCodeList())) {
                Set<String> uniqueCodeList = new HashSet<>(copySourceDto.getOriginCodeList());
                uniqueCodeAll.addAll(uniqueCodeList);
            }
            copySourceDtoList.add(copySourceDto);
        }

        //???????????????
        List<BiDataSet> dataSetList = dataSetService.list(new LambdaQueryWrapper<BiDataSet>().in(BiDataSet::getCode, uniqueCodeAll));

        //??????????????????
        ThreadLocalHolder.set("operator", BiTenantConfigController.OPERATOR);
        Map<String, String> result = Maps.newHashMap();
        for (String tenantCode : dto.getTenantCodes().split(",")) {

            try {
                Map<String, String> codeMap = Maps.newHashMap();
                for (BiDataSet dataSet : dataSetList) {
                    //?????????????????????????????????
                    ThreadLocalHolder.set("tenantCode", biProperties.getInnerTenantCode());
                    Map<String, Object> map = getTableInfo(dataSet.getTableName());

                    //????????????????????????
                    ThreadLocalHolder.set("tenantCode", tenantCode);
                    buildTable(map);
                    String setCode = buildDataSetIssue(dto.getCategoryName(), dataSet.getTableDesc(), dataSet.getTableName());
                    codeMap.put(dataSet.getCode(), setCode);
                }

                //????????????????????????,????????????????????????
                ThreadLocalHolder.set("tenantCode", tenantCode);
                AnalyseCategoryDto analyseCategory = buildCategoryIssue(dto.getCategoryName());
                String groupId = null;
                for (CopySourceDto copySourceDto : copySourceDtoList) {
                    //?????????????????????????????????
                    int hasIssue = analysePageService.count(new LambdaQueryWrapper<BiUiAnalysePage>().eq(BiUiAnalysePage::getName, copySourceDto.getPageName()));
                    if (hasIssue >= 1) {
                        continue;
                    }
                    groupId = analysePageService.saveNewPage(groupId, copySourceDto.getPageName(), analyseCategory.getId(), copySourceDto.getPageId(),
                            copySourceDto.getLinkPageId(), copySourceDto.getContent(), copySourceDto.getChildrenArr(), codeMap);
                }
                result.put(tenantCode, "success");
            } catch (Exception e) {
                result.put(tenantCode, e.getMessage());
            }
            ThreadLocalHolder.remove("linkTempMap");
        }
        return result;
    }

    private Map<String, String> issueNoData(IssueDeloitteDto dto) {
        List<AnalysePageDto> analysePageDtoList = analysePageService.getPageWithChildren(dto.getFromPageId());
        List<CopySourceDto> copySourceDtoList = Lists.newLinkedList();
        Set<String> uniqueCodeAll = Sets.newHashSet();
        for (AnalysePageDto analysePageDto : analysePageDtoList) {
            CopySourceDto copySourceDto = analysePageService.getCopySourceData(analysePageDto.getId());
            if (CollectionUtils.isNotEmpty(copySourceDto.getOriginCodeList())) {
                Set<String> uniqueCodeList = new HashSet<>(copySourceDto.getOriginCodeList());
                uniqueCodeAll.addAll(uniqueCodeList);
            }
            copySourceDtoList.add(copySourceDto);
        }

        List<BiDataSet> dataSetList = dataSetService.list(new LambdaQueryWrapper<BiDataSet>().in(BiDataSet::getCode, uniqueCodeAll));

        //??????????????????
        Map<String, String> result = Maps.newHashMap();
        for (String tenantCode : dto.getTenantCodes().split(",")) {
            ThreadLocalHolder.set("tenantCode", tenantCode);
            ThreadLocalHolder.set("operator", BiTenantConfigController.OPERATOR);

            try {
                Map<String, String> codeMap = Maps.newHashMap();
                for (BiDataSet dataSet : dataSetList) {
                    //????????????????????????
                    String setCode = buildDataSetIssue(dto.getCategoryName(), dataSet.getTableDesc(), dataSet.getTableName());
                    codeMap.put(dataSet.getCode(), setCode);
                }
                //????????????????????????
                AnalyseCategoryDto analyseCategory = buildCategoryIssue(dto.getCategoryName());
                String groupId = null;
                for (CopySourceDto copySourceDto : copySourceDtoList) {
                    //?????????????????????????????????
                    int hasIssue = analysePageService.count(new LambdaQueryWrapper<BiUiAnalysePage>().eq(BiUiAnalysePage::getName, copySourceDto.getPageName()));
                    if (hasIssue >= 1) {
                        continue;
                    }
                    groupId = analysePageService.saveNewPage(groupId, copySourceDto.getPageName(), analyseCategory.getId(), copySourceDto.getPageId(),
                            copySourceDto.getLinkPageId(), copySourceDto.getContent(), copySourceDto.getChildrenArr(), codeMap);
                }
                result.put(tenantCode, "success");
            } catch (Exception e) {
                result.put(tenantCode, e.getMessage());
            }
            ThreadLocalHolder.remove("linkTempMap");
        }
        return result;
    }

    private String buildDataSetIssue(String folderName, String dataSetName, String tableName) {
        String folderId = dataSetService.folderCreate(folderName);
        String dataSetCode = dataSetService.createCopy(folderId, tableName, dataSetName);
        return dataSetCode;
    }

    private AnalyseCategoryDto buildCategoryIssue(String categoryName) {
        AnalyseCategoryDto analyseCategory = new AnalyseCategoryDto();
        BiUiAnalyseCategory categoryServiceOne = categoryService.getOne(new LambdaQueryWrapper<BiUiAnalyseCategory>().eq(BiUiAnalyseCategory::getName, categoryName));
        if (null == categoryServiceOne) {
            CreateAnalyseCategoryDto categoryDto = new CreateAnalyseCategoryDto();
            categoryDto.setName(categoryName);
            categoryDto.setDes(categoryName);
            categoryDto.setParentId("0");
            categoryDto.setType(CategoryTypeEnum.CUSTOMER.getCode());
            analyseCategory = categoryService.createAnalyseCategory(categoryDto);
        } else {
            BeanUtils.copyProperties(categoryServiceOne, analyseCategory);
        }
        return analyseCategory;
    }


    private Map<String, Object> getTableInfo(String tableName) {
        Map<String, Object> map = new HashMap<>();
        map.put("tableName", tableName);
        //??????????????????
        String createSql = dbHandler.getCreateSql(tableName).toUpperCase();
        map.put("createSql", createSql);
        //??????????????????
        List<LinkedHashMap<String, Object>> data = dbHandler.executeQueryLinked("select * from " + tableName + ";");
        map.put("data", data);
        return map;
    }

    private void buildTable(Map<String, Object> map) {
        if (!dbHandler.isTableExists(MapUtils.getString(map, "tableName"))) {
            dbHandler.executeQuery(MapUtils.getString(map, "createSql"));
            dbHandler.executeInsert(MapUtils.getString(map, "tableName"), (List<LinkedHashMap<String, Object>>) MapUtils.getObject(map, "data"));
        }
    }
}
