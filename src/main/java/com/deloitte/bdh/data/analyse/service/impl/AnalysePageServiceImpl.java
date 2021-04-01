package com.deloitte.bdh.data.analyse.service.impl;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import com.deloitte.bdh.data.analyse.enums.*;
import com.deloitte.bdh.data.analyse.model.request.*;
import com.deloitte.bdh.data.collation.database.DbHandler;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.deloitte.bdh.common.base.AbstractService;
import com.deloitte.bdh.common.base.PageRequest;
import com.deloitte.bdh.common.base.PageResult;
import com.deloitte.bdh.common.base.RetRequest;
import com.deloitte.bdh.common.constant.DSConstant;
import com.deloitte.bdh.common.exception.BizException;
import com.deloitte.bdh.common.json.JsonUtil;
import com.deloitte.bdh.common.util.AesUtil;
import com.deloitte.bdh.common.util.GenerateCodeUtil;
import com.deloitte.bdh.common.util.Md5Util;
import com.deloitte.bdh.common.util.StringUtil;
import com.deloitte.bdh.common.util.ThreadLocalHolder;
import com.deloitte.bdh.data.analyse.constants.AnalyseConstants;
import com.deloitte.bdh.data.analyse.dao.bi.BiUiAnalysePageMapper;
import com.deloitte.bdh.data.analyse.model.BiUiAnalysePage;
import com.deloitte.bdh.data.analyse.model.BiUiAnalysePageConfig;
import com.deloitte.bdh.data.analyse.model.BiUiAnalysePageLink;
import com.deloitte.bdh.data.analyse.model.BiUiAnalysePublicShare;
import com.deloitte.bdh.data.analyse.model.BiUiAnalyseUserResource;
import com.deloitte.bdh.data.analyse.model.resp.AnalysePageConfigDto;
import com.deloitte.bdh.data.analyse.model.resp.AnalysePageDto;
import com.deloitte.bdh.data.analyse.service.AnalysePageConfigService;
import com.deloitte.bdh.data.analyse.service.AnalysePageHomepageService;
import com.deloitte.bdh.data.analyse.service.AnalysePageLinkService;
import com.deloitte.bdh.data.analyse.service.AnalysePageService;
import com.deloitte.bdh.data.analyse.service.AnalyseUserDataService;
import com.deloitte.bdh.data.analyse.service.AnalyseUserResourceService;
import com.deloitte.bdh.data.analyse.service.BiUiAnalysePublicShareService;
import com.deloitte.bdh.data.collation.database.po.TableColumn;
import com.deloitte.bdh.data.collation.enums.DataSetTypeEnum;
import com.deloitte.bdh.data.collation.enums.YesOrNoEnum;
import com.deloitte.bdh.data.collation.model.BiDataSet;
import com.deloitte.bdh.data.collation.service.BiDataSetService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author bo.wang
 * @since 2020-10-19
 */
@Service
@DS(DSConstant.BI_DB)
public class AnalysePageServiceImpl extends AbstractService<BiUiAnalysePageMapper, BiUiAnalysePage> implements AnalysePageService {


    @Value("${bi.analyse.view.address}")
    private String viewAddress;

    @Value("${bi.analyse.public.address}")
    private String publicAddress;

    @Value("${bi.analyse.encryptPass}")
    private String encryptPass;

    @Resource
    private BiUiAnalysePublicShareService shareService;

    @Resource
    private AnalysePageConfigService configService;

    @Resource
    private AnalyseUserResourceService userResourceService;

    @Resource
    private AnalyseUserDataService userDataService;

    @Resource
    private BiUiAnalysePageMapper analysePageMapper;

    @Resource
    private AnalysePageHomepageService homepageService;

    @Resource
    private BiDataSetService dataSetService;

    @Resource
    private AnalysePageLinkService linkService;

    @Resource
    private DbHandler dbHandler;

    @Override
    public PageResult<AnalysePageDto> getChildAnalysePageList(PageRequest<GetAnalysePageListDto> request) {
        if (0 == request.getSize()) {
            PageHelper.startPage(request.getPage(), request.getSize(), true, false, true);
        } else {
            PageHelper.startPage(request.getPage(), request.getSize());
        }
        List<AnalysePageDto> pageList = Lists.newArrayList();
        PageInfo pageInfo;
        if (StringUtils.equals(request.getData().getSuperUserFlag(), YesOrNoEnum.YES.getKey())) {
            LambdaQueryWrapper<BiUiAnalysePage> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(BiUiAnalysePage::getParentId, request.getData().getCategoryId());
            if (StringUtils.isNotBlank(request.getData().getName())) {
                queryWrapper.like(BiUiAnalysePage::getName, request.getData().getName());
            }
            queryWrapper.isNotNull(BiUiAnalysePage::getPublishId);
            queryWrapper.eq(BiUiAnalysePage::getIsEdit, YnTypeEnum.NO.getCode());
            queryWrapper.orderByDesc(BiUiAnalysePage::getCreateDate);
            List<BiUiAnalysePage> list = list(queryWrapper);
            pageInfo = PageInfo.of(list);
            for (BiUiAnalysePage page : list) {
                AnalysePageDto dto = new AnalysePageDto();
                BeanUtils.copyProperties(page, dto);
                pageList.add(dto);
            }
        } else {
            SelectPublishedPageDto selectPublishedPageDto = new SelectPublishedPageDto();
            selectPublishedPageDto.setUserId(ThreadLocalHolder.getOperator());
            selectPublishedPageDto.setResourceType(ResourcesTypeEnum.PAGE.getCode());
            selectPublishedPageDto.setPermittedAction(PermittedActionEnum.VIEW.getCode());
            selectPublishedPageDto.setTenantId(ThreadLocalHolder.getTenantId());
            selectPublishedPageDto.setName(request.getData().getName());
            selectPublishedPageDto.setResourcesIds(Lists.newArrayList(request.getData().getCategoryId()));
            selectPublishedPageDto.setIsEdit(YnTypeEnum.NO.getCode());
            pageList = analysePageMapper.selectPublishedPage(selectPublishedPageDto);
            pageInfo = PageInfo.of(pageList);
        }

        //处理查询之后做操作返回total不正确
        List<AnalysePageDto> pageDtoList = Lists.newArrayList();
        pageList.forEach(page -> {
            AnalysePageDto dto = new AnalysePageDto();
            BeanUtils.copyProperties(page, dto);
            List<BiUiAnalysePage> childList = list(new LambdaQueryWrapper<BiUiAnalysePage>()
                    .eq(BiUiAnalysePage::getParentId, dto.getId())
                    .eq(BiUiAnalysePage::getIsEdit, YnTypeEnum.NO.getCode()));
            if (CollectionUtils.isNotEmpty(childList)) {
                dto.setHasChild(YesOrNoEnum.YES.getKey());
            } else {
                dto.setHasChild(YesOrNoEnum.NO.getKey());
            }
            pageDtoList.add(dto);
        });
        userResourceService.setPagePermission(pageDtoList, request.getData().getSuperUserFlag());
        homepageService.fillHomePage(pageDtoList);
        pageInfo.setList(pageDtoList);
        return new PageResult<>(pageInfo);
    }

    @Override
    public AnalysePageDto getAnalysePage(String pageId) {
        if (StringUtils.isNotBlank(pageId)) {
            BiUiAnalysePage page = this.getById(pageId);
            if (null != page) {
                AnalysePageDto dto = new AnalysePageDto();
                BeanUtils.copyProperties(page, dto);
                return dto;
            }
        }
        return null;
    }

    @Override
    public AnalysePageDto createAnalysePage(RetRequest<CreateAnalysePageDto> request) {
//        checkBiUiAnalysePageByName(request.getData().getCode(), request.getData().getName(), ThreadLocalHolder.getTenantId(), null);
        BiUiAnalysePage entity = new BiUiAnalysePage();
        BeanUtils.copyProperties(request.getData(), entity);
        entity.setCode(GenerateCodeUtil.generate());
        entity.setTenantId(ThreadLocalHolder.getTenantId());
        entity.setIsEdit(YnTypeEnum.YES.getCode());
        this.save(entity);
        AnalysePageDto dto = new AnalysePageDto();
        BeanUtils.copyProperties(entity, dto);
        return dto;
    }

    @Override
    public CopySourceDto getCopySourceData(String pageId) {
        CopySourceDto dto = new CopySourceDto();
        BiUiAnalysePage fromPage = this.getById(pageId);
        BiUiAnalysePageConfig fromPageConfig = configService.getById(fromPage.getEditId());
        if (null == fromPageConfig) {
            throw new BizException(ResourceMessageEnum.PAGE_CONFIG_NOT_EXIST.getCode(),
                    localeMessageService.getMessage(ResourceMessageEnum.PAGE_CONFIG_NOT_EXIST.getMessage(), ThreadLocalHolder.getLang()));
        }
        JSONObject content = (JSONObject) JSONObject.parse(fromPageConfig.getContent());
        JSONArray childrenArr = content.getJSONArray("children");
        if (null == childrenArr) {
            throw new BizException(ResourceMessageEnum.PAGE_NO_COMPONENT.getCode(),
                    localeMessageService.getMessage(ResourceMessageEnum.PAGE_NO_COMPONENT.getMessage(), ThreadLocalHolder.getLang()));
        }
        List<String> originCodeList = Lists.newArrayList();
        List<String> linkPageId = Lists.newArrayList();
        for (int i = 0; i < childrenArr.size(); i++) {
            JSONObject data = childrenArr.getJSONObject(i).getJSONObject("data");
            String type = childrenArr.getJSONObject(i).getString("type");

            JSONObject mutual = childrenArr.getJSONObject(i).getJSONObject("mutual");
            if (!"Text".equalsIgnoreCase(type) && !"Image".equalsIgnoreCase(type) && data.size() != 0) {
                originCodeList.add(data.getString("tableCode"));
            }
            if (mutual.size() != 0) {
                JSONArray jumpReport = mutual.getJSONArray("jumpReport");
                if (null != jumpReport && jumpReport.size() != 0 && StringUtils.isNotBlank(jumpReport.getString(1))) {
                    linkPageId.add(jumpReport.getString(1));
                }
            }
        }
        dto.setContent(content);
        dto.setChildrenArr(childrenArr);
        dto.setLinkPageId(linkPageId);
        dto.setOriginCodeList(originCodeList);
        dto.setPageName(fromPage.getName());
        return dto;
    }

    @Override
    public Map<String, Object> buildNewDataSet(String dataSetName, String dataSetCategoryId, String code) {
        Map<String, Object> map = new HashMap<>();
        LambdaQueryWrapper<BiDataSet> dataSetQueryWrapper = new LambdaQueryWrapper<>();
        dataSetQueryWrapper.eq(BiDataSet::getCode, code);
        BiDataSet dataSet = dataSetService.getOne(dataSetQueryWrapper);
        map.put("dataSet", dataSet);
        BiDataSet newDataSet = new BiDataSet();
        String newCode = GenerateCodeUtil.generate();
        map.put("newCode", newCode);
        String tableName = dataSet.getTableName().toUpperCase() + "_" + newCode;
        map.put("tableName", tableName);
        newDataSet.setCode(newCode);
        newDataSet.setType(DataSetTypeEnum.COPY.getKey());
        newDataSet.setTableName(tableName);
        newDataSet.setTableDesc(dataSet.getTableDesc());
        newDataSet.setRefModelCode(dataSet.getRefModelCode());
        newDataSet.setParentId(dataSetCategoryId);
        newDataSet.setComments(dataSetName);
        newDataSet.setIsFile(YesOrNoEnum.NO.getKey());
        newDataSet.setTenantId(ThreadLocalHolder.getTenantId());
        map.put("newDataSet", newDataSet);
        //获取建表语句
        String createSql = dbHandler.getCreateSql(dataSet.getTableName()).toUpperCase();
        createSql = createSql.replace(dataSet.getTableName().toUpperCase(), tableName);
        map.put("createSql", createSql);
        //获取原始数据
        List<LinkedHashMap<String, Object>> data = dbHandler.executeQueryLinked("select * from " + dataSet.getTableName() + ";");
        map.put("data", data);
        return map;
    }

    @Override
    public void saveNewTable(Map<String, Object> map) {
        dataSetService.save((BiDataSet) MapUtils.getObject(map, "newDataSet"));
        dbHandler.executeQuery(MapUtils.getString(map, "createSql"));
        dbHandler.executeInsert(MapUtils.getString(map, "tableName"), (List<LinkedHashMap<String, Object>>) MapUtils.getObject(map, "data"));
    }

    @Override
    @Transactional
    public AnalysePageDto saveNewPage(String name, String categoryId, String fromPageId, List<String> linkPageId,
                                      JSONObject content, JSONArray childrenArr, Map<String, String> codeMap) {
        //复制page
        BiUiAnalysePage insertPage = new BiUiAnalysePage();
        insertPage.setType("dashboard");
        insertPage.setName(name);
        insertPage.setCode(GenerateCodeUtil.generate());
        insertPage.setParentId(categoryId);
        insertPage.setIsPublic(YesOrNoEnum.YES.getKey());
        insertPage.setIsEdit(YnTypeEnum.NO.getCode());
        insertPage.setTenantId(ThreadLocalHolder.getTenantId());
        insertPage.setDeloitteFlag(YesOrNoEnum.NO.getKey());
        insertPage.setOriginPageId(null);
        this.save(insertPage);

        //添加跳转关系
        if (CollectionUtils.isNotEmpty(linkPageId)) {
            linkPageId = linkPageId.stream().distinct().collect(Collectors.toList());
            List<BiUiAnalysePageLink> linkList = Lists.newArrayList();
            for (String refPageId : linkPageId) {
                BiUiAnalysePageLink link = new BiUiAnalysePageLink();
                link.setPageId(insertPage.getId());
                link.setRefPageId(refPageId);
                link.setTenantId(ThreadLocalHolder.getTenantId());
                linkList.add(link);
            }
            linkService.saveBatch(linkList);
        }

        //保存关联关系
        Map<String, Object> map = Maps.newHashMap();
        map.put("newPageId", insertPage.getId());
        map.put("oldLinkPageId", linkPageId);
        Map<String, Map<String, Object>> linkTempMap = ThreadLocalHolder.get("linkTempMap");
        if (null == linkTempMap) {
            Map<String, Map<String, Object>> linkRela = Maps.newHashMap();
            linkRela.put(fromPageId, map);
            ThreadLocalHolder.set("linkTempMap", linkRela);
        } else {
            linkTempMap.put(fromPageId, map);
            ThreadLocalHolder.set("linkTempMap", linkTempMap);
        }

        //替换content
        JSONObject page = content.getJSONObject("page");
        replacePage(page, insertPage);
        for (int i = 0; i < childrenArr.size(); i++) {
            JSONObject data = childrenArr.getJSONObject(i).getJSONObject("data");
            if (data.size() != 0) {
                data.put("tableCode", codeMap.get(data.getString("tableCode")));
            }
        }

        //复制config
        BiUiAnalysePageConfig editConfig = new BiUiAnalysePageConfig();
        editConfig.setPageId(insertPage.getId());
        editConfig.setContent(content.toJSONString());
        editConfig.setTenantId(ThreadLocalHolder.getTenantId());
        configService.save(editConfig);
        BiUiAnalysePageConfig publishConfig = new BiUiAnalysePageConfig();
        publishConfig.setPageId(insertPage.getId());
        publishConfig.setContent(content.toJSONString());
        publishConfig.setTenantId(ThreadLocalHolder.getTenantId());
        configService.save(publishConfig);
        insertPage.setEditId(editConfig.getId());
        insertPage.setPublishId(publishConfig.getId());
        this.updateById(insertPage);

        //替换跳转到此报表的链接
        replaceLinkId(categoryId, fromPageId, insertPage.getId());

        AnalysePageDto dto = new AnalysePageDto();
        BeanUtils.copyProperties(insertPage, dto);
        return dto;
    }

    private void replacePage(JSONObject page, BiUiAnalysePage newPage) {
        page.put("id", newPage.getId());
        page.put("code", newPage.getCode());
        page.put("name", newPage.getName());
        page.put("type", newPage.getType());
        page.put("parentId", newPage.getParentId());
        page.put("originPageId", newPage.getOriginPageId());
        page.put("editId", newPage.getEditId());
        page.put("publishId", newPage.getPublishId());
        page.put("des", newPage.getDes());
        page.put("isEdit", newPage.getIsEdit());
        page.put("isPublic", newPage.getIsPublic());
        page.put("deloitteFlag", newPage.getDeloitteFlag());
        page.put("tenantId", newPage.getTenantId());
    }

    @Override
    @Transactional
    public void batchDelAnalysePage(BatchDeleteAnalyseDto request) {
        List<String> pageIds = request.getIds();
        if (CollectionUtils.isEmpty(pageIds)) {
            throw new BizException(ResourceMessageEnum.DELETE_PAGE_SELECT.getCode(),
                    localeMessageService.getMessage(ResourceMessageEnum.DELETE_PAGE_SELECT.getMessage(), ThreadLocalHolder.getLang()));
        }
        //如果删除草稿箱的报表
        if (request.getType().equals(AnalyseConstants.PAGE_CONFIG_EDIT)) {
            List<BiUiAnalysePage> pages = this.listByIds(pageIds);
            for (BiUiAnalysePage page : pages) {
                //如果发布过
                if (StringUtils.isNotBlank(page.getPublishId())) {
                    BiUiAnalysePageConfig publishConfig = configService.getById(page.getPublishId());
                    BiUiAnalysePageConfig editConfig = configService.getById(page.getEditId());
                    editConfig.setContent(publishConfig.getContent());
                    configService.updateById(editConfig);
                    page.setIsEdit(YnTypeEnum.NO.getCode());
                    updateById(page);
                } else {
                    removeById(page.getId());
                    //删除config
                    configService.removeById(page.getEditId());
                }
            }
            return;
        }

        //删除config
        LambdaQueryWrapper<BiUiAnalysePageConfig> configQueryWrapper = new LambdaQueryWrapper<>();
        configQueryWrapper.in(BiUiAnalysePageConfig::getPageId, pageIds);
        configService.remove(configQueryWrapper);

        //删除可见编辑权限
        LambdaQueryWrapper<BiUiAnalyseUserResource> resourceQueryWrapper = new LambdaQueryWrapper<>();
        resourceQueryWrapper.in(BiUiAnalyseUserResource::getResourceId, pageIds);
        userResourceService.remove(resourceQueryWrapper);
        //删除page
        this.removeByIds(pageIds);
    }

    @Override
    public AnalysePageDto updateAnalysePage(RetRequest<UpdateAnalysePageDto> request) {
        BiUiAnalysePage entity = this.getById(request.getData().getId());
        if (null == entity) {
            throw new BizException(ResourceMessageEnum.PAGE_NOT_EXIST.getCode(),
                    localeMessageService.getMessage(ResourceMessageEnum.PAGE_NOT_EXIST.getMessage(), ThreadLocalHolder.getLang()));
        }
        checkBiUiAnalysePageByName(request.getData().getCode(), request.getData().getName(), entity.getTenantId(), entity.getId());
        entity.setName(request.getData().getName());
        entity.setDes(request.getData().getDes());
        this.updateById(entity);
        AnalysePageDto dto = new AnalysePageDto();
        BeanUtils.copyProperties(entity, dto);
        return dto;
    }

    private void setAccessUrl(String pageId, String password, String isPublic) {

        //获取访问地址
        LambdaQueryWrapper<BiUiAnalysePublicShare> shareLambdaQueryWrapper = new LambdaQueryWrapper<>();
        shareLambdaQueryWrapper.eq(BiUiAnalysePublicShare::getRefPageId, pageId);
        //公开状态
        List<String> typeList = Lists.newArrayList(ShareTypeEnum.ZERO.getKey(), ShareTypeEnum.ONE.getKey(), ShareTypeEnum.TWO.getKey());
        shareLambdaQueryWrapper.in(BiUiAnalysePublicShare::getType, typeList);
        BiUiAnalysePublicShare share = shareService.getOne(shareLambdaQueryWrapper);
        if (null == share) {
            share = new BiUiAnalysePublicShare();
            share.setRefPageId(pageId);
            share.setTenantId(ThreadLocalHolder.getTenantId());
            Map<String, Object> params = Maps.newHashMap();
            params.put("tenantCode", ThreadLocalHolder.getTenantCode());
            params.put("refPageId", pageId);
            share.setCode(AesUtil.encryptNoSymbol(JsonUtil.readObjToJson(params), encryptPass));

        }
        if (StringUtils.equals(isPublic, ShareTypeEnum.FALSE.getKey())) {
            share.setType(ShareTypeEnum.ZERO.getKey());
            share.setAddress(viewAddress);
        } else {
            share.setAddress(publicAddress);
            if (StringUtils.isNotEmpty(password)) {
                share.setType(ShareTypeEnum.TWO.getKey());
            } else {
                share.setType(ShareTypeEnum.ONE.getKey());
            }
        }
        if (StringUtils.isNotEmpty(password)) {
            share.setPassword(Md5Util.getMD5(password, encryptPass + ThreadLocalHolder.getTenantCode()));
        } else {
            share.setPassword(null);
        }
        shareService.saveOrUpdate(share);
    }

    @Transactional
    @Override
    public AnalysePageConfigDto publishAnalysePage(PublishAnalysePageDto request) {
        String pageId = request.getPageId();
        String categoryId = request.getCategoryId();
        SaveResourcePermissionDto permissionDto = request.getSaveResourcePermissionDto();
        BiUiAnalysePageConfig originConfig = configService.getById(request.getConfigId());
        BiUiAnalysePage originPage = getById(request.getPageId());
        if (originPage == null) {
            throw new BizException(ResourceMessageEnum.PAGE_NOT_EXIST.getCode(),
                    localeMessageService.getMessage(ResourceMessageEnum.PAGE_NOT_EXIST.getMessage(), ThreadLocalHolder.getLang()));
        }

        String isPublic = request.getIsPublic();
        if (StringUtils.equals(request.getDeloitteFlag(), YesOrNoEnum.YES.getKey())) {
            updatePage(request, originPage, originConfig, ShareTypeEnum.TRUE.getKey());
        } else {
            //获取公开状态s
            if (isPublic.equals(ShareTypeEnum.TRUE.getKey())) {
                updatePage(request, originPage, originConfig, isPublic);
            } else {
                List<BiUiAnalysePage> allPageList = list(new LambdaQueryWrapper<BiUiAnalysePage>()
                        .eq(BiUiAnalysePage::getParentId, categoryId)
                        .eq(BiUiAnalysePage::getOriginPageId, originPage.getOriginPageId()));
                if (CollectionUtils.isEmpty(allPageList) && StringUtils.isNotBlank(originPage.getParentId())) {
                    //新建config
                    BiUiAnalysePageConfig newConfig = new BiUiAnalysePageConfig();
                    if (originConfig != null) {
                        BeanUtils.copyProperties(originConfig, newConfig);
                        originConfig.setContent(request.getContent());
                        configService.updateById(originConfig);
                    }
                    newConfig.setId(null);
                    newConfig.setPageId(null);
                    newConfig.setContent(request.getContent());
                    newConfig.setTenantId(ThreadLocalHolder.getTenantId());
                    configService.save(newConfig);
                    //新建page
                    BiUiAnalysePage newPage = new BiUiAnalysePage();
                    BeanUtils.copyProperties(originPage, newPage);
                    newPage.setId(null);
                    newPage.setPublishId(newConfig.getId());
                    newPage.setParentId(categoryId);
                    newPage.setIsEdit(YnTypeEnum.NO.getCode());
                    newPage.setOriginPageId(originPage.getId());
                    //便于在发布切换文件夹时找到不同版本报表的权限，不同版本code必须设置成一样
                    newPage.setCode(originPage.getCode());
                    if (StringUtils.isNotBlank(isPublic)) {
                        if (isPublic.equals(ShareTypeEnum.TRUE.getKey())) {
                            newPage.setIsPublic(YesOrNoEnum.YES.getKey());
                        } else {
                            newPage.setIsPublic(YesOrNoEnum.NO.getKey());
                        }
                    }
                    save(newPage);
                    String newPageId = newPage.getId();
                    //保存pageId到config
                    newConfig.setPageId(newPageId);
                    configService.updateById(newConfig);
                    //把新的pageId传给权限操作
                    if (null != permissionDto) {
                        pageId = newPageId;
                        permissionDto.setId(newPageId);
                    }
                } else {
                    updatePage(request, originPage, originConfig, isPublic);
                }
            }

            if (isPublic.equals(ShareTypeEnum.FALSE.getKey())) {
                //可见编辑权限
                userResourceService.saveResourcePermission(permissionDto);
                //数据权限
                userDataService.saveDataPermission(request.getPermissionItemDtoList(), request.getPageId());
            } else {
                //删除之前的配置
                userResourceService.delResourcePermission(permissionDto);
                userDataService.delDataPermission(request.getPermissionItemDtoList(), request.getPageId());
            }
        }
        //生成链接
        setAccessUrl(pageId, request.getPassword(), isPublic);
        return null;
    }


    private void updatePage(PublishAnalysePageDto dto, BiUiAnalysePage originPage, BiUiAnalysePageConfig originConfig, String isPublic) {

        //新建config
        BiUiAnalysePageConfig newConfig = new BiUiAnalysePageConfig();
        if (originConfig != null) {
            BeanUtils.copyProperties(originConfig, newConfig);
            originConfig.setContent(dto.getContent());
            configService.updateById(originConfig);
        }
        newConfig.setId(null);
        newConfig.setPageId(dto.getPageId());
        newConfig.setContent(dto.getContent());
        newConfig.setTenantId(ThreadLocalHolder.getTenantId());
        configService.save(newConfig);
        //更新page
        if (StringUtils.isNotBlank(isPublic)) {
            if (isPublic.equals(ShareTypeEnum.TRUE.getKey())) {
                originPage.setIsPublic(YesOrNoEnum.YES.getKey());
            } else {
                originPage.setIsPublic(YesOrNoEnum.NO.getKey());
            }
        }
        originPage.setPublishId(newConfig.getId());
        if (StringUtils.isEmpty(originPage.getOriginPageId())) {
            originPage.setOriginPageId(originPage.getId());
        }
        originPage.setParentId(dto.getCategoryId());
        originPage.setIsEdit(YnTypeEnum.NO.getCode());
        updateById(originPage);
    }

    @Override
    public PageResult<AnalysePageDto> getAnalysePageDrafts(PageRequest<AnalyseNameDto> request) {
        PageHelper.startPage(request.getPage(), request.getSize());
        LambdaQueryWrapper<BiUiAnalysePage> pageLambdaQueryWrapper = new LambdaQueryWrapper<>();
        if (!StringUtil.isEmpty(ThreadLocalHolder.getTenantId())) {
            pageLambdaQueryWrapper.eq(BiUiAnalysePage::getTenantId, ThreadLocalHolder.getTenantId());
        }
        if (StringUtils.isNotBlank(request.getData().getName())) {
            pageLambdaQueryWrapper.like(BiUiAnalysePage::getName, request.getData().getName());
        }
        pageLambdaQueryWrapper.eq(BiUiAnalysePage::getIsEdit, YnTypeEnum.YES.getCode());
        if (!StringUtils.equals(YesOrNoEnum.YES.getKey(), request.getData().getSuperUserFlag())) {
            pageLambdaQueryWrapper.eq(BiUiAnalysePage::getCreateUser, ThreadLocalHolder.getOperator());
        }
        pageLambdaQueryWrapper.orderByDesc(BiUiAnalysePage::getModifiedDate);
        List<BiUiAnalysePage> pageList = this.list(pageLambdaQueryWrapper);
        return getAnalysePageDtoPageResult(pageList, request);
    }

    @Override
    public void replaceDataSet(ReplaceDataSetDto dto) throws Exception {
        BiUiAnalysePage page = getById(dto.getPageId());
        if (null == page) {
            throw new BizException(ResourceMessageEnum.PAGE_NOT_EXIST.getCode(),
                    localeMessageService.getMessage(ResourceMessageEnum.PAGE_NOT_EXIST.getMessage(), ThreadLocalHolder.getLang()));
        }
        List<String> configIdList = Lists.newArrayList();
        configIdList.add(page.getEditId());
        if (StringUtils.isNotBlank(page.getPublishId())) {
            configIdList.add(page.getPublishId());
        }
        //校验数据集字段类型
        List<BiUiAnalysePageConfig> configList = configService.listByIds(configIdList);
        for (ReplaceItemDto itemDto : dto.getReplaceItemDtoList()) {
            BiDataSet fromDataSet = dataSetService.getOne(new LambdaQueryWrapper<BiDataSet>()
                    .eq(BiDataSet::getCode, itemDto.getFromDataSetCode()));
            if (null == fromDataSet) {
                throw new BizException(ResourceMessageEnum.DATA_SET_NOT_EXIST.getCode(),
                        localeMessageService.getMessage(ResourceMessageEnum.DATA_SET_NOT_EXIST.getMessage(), ThreadLocalHolder.getLang()));
            }
            BiDataSet toDataSet = dataSetService.getOne(new LambdaQueryWrapper<BiDataSet>()
                    .eq(BiDataSet::getCode, itemDto.getToDataSetCode()));
            if (null == toDataSet) {
                throw new BizException(ResourceMessageEnum.DATA_SET_NOT_EXIST.getCode(),
                        localeMessageService.getMessage(ResourceMessageEnum.DATA_SET_NOT_EXIST.getMessage(), ThreadLocalHolder.getLang()));
            }
            List<TableColumn> fromFieldList = dataSetService.getColumns(fromDataSet.getCode());
            List<TableColumn> toFieldList = dataSetService.getColumns(toDataSet.getCode());
            validReplaceField(fromFieldList, toFieldList);
        }
        Map<String, ReplaceItemDto> itemDtoMap = dto.getReplaceItemDtoList().stream().collect(
                Collectors.toMap(ReplaceItemDto::getFromDataSetCode, a -> a, (k1, k2) -> k1));
        //替换数据
        for (BiUiAnalysePageConfig config : configList) {
            JSONObject content = (JSONObject) JSONObject.parse(config.getContent());
            JSONArray childrenArr = content.getJSONArray("children");
            for (int i = 0; i < childrenArr.size(); i++) {
                JSONObject data = childrenArr.getJSONObject(i).getJSONObject("data");
                if (data.size() != 0 && null != MapUtils.getObject(itemDtoMap, data.getString("tableCode"))) {
                    ReplaceItemDto itemDto = MapUtils.getObject(itemDtoMap, data.getString("tableCode"));
                    data.put("tableCode", itemDto.getToDataSetCode());
                }
            }
            config.setContent(content.toJSONString());
            configService.updateById(config);
        }
    }

    @Override
    public List<String> getUsedTableName(String pageId) {
        List<String> result = Lists.newArrayList();
        BiUiAnalysePage page = this.getById(pageId);
        if (null == page) {
            throw new BizException(ResourceMessageEnum.PAGE_NOT_EXIST.getCode(),
                    localeMessageService.getMessage(ResourceMessageEnum.PAGE_NOT_EXIST.getMessage(), ThreadLocalHolder.getLang()));
        }
        BiUiAnalysePageConfig config = configService.getById(page.getEditId());
        if (null == config) {
            return result;
        }
        JSONObject content = (JSONObject) JSONObject.parse(config.getContent());
        JSONArray childrenArr = content.getJSONArray("children");
        if (null == childrenArr) {
            return result;
        }
        List<String> codeList = Lists.newArrayList();
        for (int i = 0; i < childrenArr.size(); i++) {
            JSONObject data = childrenArr.getJSONObject(i).getJSONObject("data");
            if (data.size() != 0) {
                codeList.add(data.getString("tableCode"));
            }
        }
        if (CollectionUtils.isNotEmpty(codeList)) {
            LambdaQueryWrapper<BiDataSet> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.in(BiDataSet::getCode, codeList);
            List<BiDataSet> dataSetList = dataSetService.list(queryWrapper);
            dataSetList.forEach(dataSet -> result.add(dataSet.getTableName()));
        }
        return result;
    }

    @Override
    public List<AnalysePageDto> getPageWithChildren(String pageId) {
        BiUiAnalysePage fromPage = this.getById(pageId);
        if (null == fromPage) {
            throw new BizException(ResourceMessageEnum.PAGE_NOT_EXIST.getCode(),
                    localeMessageService.getMessage(ResourceMessageEnum.PAGE_NOT_EXIST.getMessage(), ThreadLocalHolder.getLang()));
        }
        return this.analysePageMapper.getPageWithChildren(pageId);
    }

    private void validReplaceField(List<TableColumn> fromFieldList, List<TableColumn> toFieldList) {
        Map<String, TableColumn> toMap = Maps.newHashMap();
        if (CollectionUtils.isNotEmpty(toFieldList)) {
            toMap = toFieldList.stream().collect(Collectors.toMap(TableColumn::getName,
                    a -> a, (k1, k2) -> k1));
        }
        for (TableColumn column : fromFieldList) {
            if (null == MapUtils.getObject(toMap, column.getName())) {
                throw new BizException(ResourceMessageEnum.FIELD_NOT_FOUND.getCode(),
                        localeMessageService.getMessage(ResourceMessageEnum.FIELD_NOT_FOUND.getMessage(), ThreadLocalHolder.getLang()), column.getName());
            }
            TableColumn fromColumn = MapUtils.getObject(toMap, column.getName());
            if (!org.apache.commons.lang.StringUtils.equals(column.getDataType(), fromColumn.getDataType())) {
                throw new BizException(ResourceMessageEnum.FIELD_NOT_FOUND.getCode(),
                        localeMessageService.getMessage(ResourceMessageEnum.FIELD_NOT_FOUND.getMessage(), ThreadLocalHolder.getLang()), column.getName());
            }
        }
    }

    private void checkBiUiAnalysePageByName(String code, String name, String tenantId, String currentId) {
        if (StringUtils.isNotBlank(code)) {
            //字母和数字
            String regEx = "[A-Z,a-z,0-9,-]*";
            Pattern pattern = Pattern.compile(regEx);
            if (!pattern.matcher(code).matches()) {
                throw new BizException("编码只能由字母和数字组成");
            }
        }
        LambdaQueryWrapper<BiUiAnalysePage> query = new LambdaQueryWrapper<>();
        query.eq(BiUiAnalysePage::getTenantId, tenantId);
        query.eq(BiUiAnalysePage::getCode, code);
        if (currentId != null) {
            query.ne(BiUiAnalysePage::getId, currentId);
        }
        List<BiUiAnalysePage> codeList = list(query);
        if (CollectionUtils.isNotEmpty(codeList)) {
            throw new BizException("已存在相同报表编码");
        }
    }

    private PageResult<AnalysePageDto> getAnalysePageDtoPageResult(List<BiUiAnalysePage> pageList, PageRequest<AnalyseNameDto> request) {
        //处理查询之后做操作返回total不正确
        PageInfo pageInfo = PageInfo.of(pageList);
        List<AnalysePageDto> pageDtoList = Lists.newArrayList();
        pageList.forEach(page -> {
            AnalysePageDto dto = new AnalysePageDto();
            BeanUtils.copyProperties(page, dto);
            BeanUtils.copyProperties(page, dto);
            pageDtoList.add(dto);
        });
        pageInfo.setList(pageDtoList);
        return new PageResult<>(pageInfo);
    }

    private void replaceLinkId(String categoryId, String oldPageId, String newPageId) {
        //替换上游
        List<Pair<String, String>> pairList = getLinkTempFather(oldPageId);
        if (CollectionUtils.isNotEmpty(pairList)) {
            for (Pair<String, String> pair : pairList) {
                BiUiAnalysePageLink pageLink = linkService.getOne(new LambdaQueryWrapper<BiUiAnalysePageLink>()
                        .eq(BiUiAnalysePageLink::getRefPageId, oldPageId)
                        .eq(BiUiAnalysePageLink::getPageId, pair.getRight()));
                if (null != pageLink) {
                    BiUiAnalysePage uiAnalysePage = this.getById(pageLink.getPageId());
                    List<String> configIdList = Lists.newArrayList();
                    configIdList.add(uiAnalysePage.getEditId());
                    configIdList.add(uiAnalysePage.getPublishId());
                    List<BiUiAnalysePageConfig> configList = configService.listByIds(configIdList);
                    if (CollectionUtils.isNotEmpty(configList)) {
                        for (BiUiAnalysePageConfig config : configList) {
                            JSONObject content = (JSONObject) JSONObject.parse(config.getContent());
                            JSONArray childrenArr = content.getJSONArray("children");
                            for (int i = 0; i < childrenArr.size(); i++) {
                                JSONObject mutual = childrenArr.getJSONObject(i).getJSONObject("mutual");
                                if (mutual.size() != 0) {
                                    JSONArray jumpReport = mutual.getJSONArray("jumpReport");
                                    if (jumpReport.size() != 0 && StringUtils.isNotBlank(jumpReport.getString(1))) {
                                        if (StringUtils.equals(jumpReport.getString(1), oldPageId)) {
                                            jumpReport.set(0, categoryId);
                                            jumpReport.set(1, newPageId);
                                        }
                                    }
                                }
                            }
                            config.setContent(content.toJSONString());
                            configService.saveOrUpdate(config);
                        }
                    }
                    //替换之后更新关联关系
                    pageLink.setRefPageId(newPageId);
                    linkService.saveOrUpdate(pageLink);
                }
            }
        }

        //替换自己
        List<BiUiAnalysePageLink> linkList = linkService.list(new LambdaQueryWrapper<BiUiAnalysePageLink>()
                .eq(BiUiAnalysePageLink::getPageId, newPageId));
        if (CollectionUtils.isNotEmpty(linkList)) {
            for (BiUiAnalysePageLink pageLink : linkList) {
                String newlinkedPageId = getLinkTempChild(newPageId);
                if (null == newlinkedPageId) {
                    continue;
                }
                BiUiAnalysePage newLinkedPage = this.getById(newlinkedPageId);
                BiUiAnalysePage uiAnalysePage = this.getById(pageLink.getPageId());
                List<String> configIdList = Lists.newArrayList();
                configIdList.add(uiAnalysePage.getEditId());
                configIdList.add(uiAnalysePage.getPublishId());
                List<BiUiAnalysePageConfig> configList = configService.listByIds(configIdList);
                if (CollectionUtils.isNotEmpty(configList)) {
                    for (BiUiAnalysePageConfig config : configList) {
                        JSONObject content = (JSONObject) JSONObject.parse(config.getContent());
                        JSONArray childrenArr = content.getJSONArray("children");
                        for (int i = 0; i < childrenArr.size(); i++) {
                            JSONObject mutual = childrenArr.getJSONObject(i).getJSONObject("mutual");
                            if (mutual.size() != 0) {
                                JSONArray jumpReport = mutual.getJSONArray("jumpReport");
                                if (jumpReport.size() != 0 && StringUtils.isNotBlank(jumpReport.getString(1))) {
                                    if (StringUtils.equals(jumpReport.getString(1), oldPageId)) {
                                        jumpReport.set(0, newLinkedPage.getParentId());
                                        jumpReport.set(1, newlinkedPageId);
                                    }
                                }
                            }
                        }
                        config.setContent(content.toJSONString());
                        configService.saveOrUpdate(config);
                    }
                }
                //替换之后更新关联关系
                pageLink.setRefPageId(newlinkedPageId);
                linkService.saveOrUpdate(pageLink);
            }
        }
    }

    private List<Pair<String, String>> getLinkTempFather(String linkedPageId) {
        Map<String, Map<String, Object>> linkTempMap = ThreadLocalHolder.get("linkTempMap");
        List<Pair<String, String>> list = Lists.newArrayList();
        for (Map.Entry<String, Map<String, Object>> outMap : linkTempMap.entrySet()) {
            String oldFromPageId = outMap.getKey();
            String newFromPageId = (String) outMap.getValue().get("newPageId");
            List<String> linkList = (List<String>) outMap.getValue().get("oldLinkPageId");
            if (CollectionUtils.isNotEmpty(linkList) && linkList.contains(linkedPageId)) {
                Pair<String, String> pair = new ImmutablePair(oldFromPageId, newFromPageId);
                list.add(pair);
            }
        }
        return list;
    }

    private String getLinkTempChild(String linkPageId) {
        Map<String, Map<String, Object>> linkTempMap = ThreadLocalHolder.get("linkTempMap");
        if (linkTempMap.containsKey(linkPageId)) {
            return (String) linkTempMap.get(linkPageId).get("newPageId");
        }
        return null;
    }
}
