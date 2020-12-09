package com.deloitte.bdh.data.analyse.service.impl;


import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.deloitte.bdh.common.base.AbstractService;
import com.deloitte.bdh.common.base.RetRequest;
import com.deloitte.bdh.common.constant.DSConstant;
import com.deloitte.bdh.common.util.ThreadLocalHolder;
import com.deloitte.bdh.data.analyse.dao.bi.BiUiAnalyseUserResourceMapper;
import com.deloitte.bdh.data.analyse.enums.PermittedActionEnum;
import com.deloitte.bdh.data.analyse.enums.ResourcesTypeEnum;
import com.deloitte.bdh.data.analyse.model.BiUiAnalyseUserResource;
import com.deloitte.bdh.data.analyse.model.request.SaveResourcePermissionDto;
import com.deloitte.bdh.data.analyse.service.AnalysePageService;
import com.deloitte.bdh.data.analyse.service.AnalyseUserResourceService;
import com.google.common.collect.Lists;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Author:LIJUN
 * Date:08/12/2020
 * Description:
 */
@Service
@DS(DSConstant.BI_DB)
public class AnalyseUserResourceServiceImpl extends AbstractService<BiUiAnalyseUserResourceMapper, BiUiAnalyseUserResource> implements AnalyseUserResourceService {

    @Override
    public void saveResourcePermission(SaveResourcePermissionDto dto) {
        if (null != dto) {
            if (StringUtils.equals(dto.getResourceType(), ResourcesTypeEnum.PAGE.getCode())) {
                process(dto, ResourcesTypeEnum.PAGE.getCode(), ThreadLocalHolder.getTenantId());
            }
            if (StringUtils.equals(dto.getResourceType(), ResourcesTypeEnum.CATEGORY.getCode())) {
                process(dto, ResourcesTypeEnum.CATEGORY.getCode(), ThreadLocalHolder.getTenantId());
            }
        }
    }

    public void process(SaveResourcePermissionDto dto, String resourceType, String tenantId) {
        //删除之前的配置
        LambdaQueryWrapper<BiUiAnalyseUserResource> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(BiUiAnalyseUserResource::getResourceId, dto.getId());
        this.remove(queryWrapper);

        //交集（查看和编辑）
        List<String> bothPermission = dto.getViewUserList().stream().filter(num -> dto.getEditUserList().contains(num))
                .collect(Collectors.toList());

        //差集（查看）
        List<String> viewPermission = dto.getViewUserList().stream().filter(num -> !dto.getEditUserList().contains(num))
                .collect(Collectors.toList());

        //差集（编辑）
        List<String> editPermission = dto.getEditUserList().stream().filter(num -> !dto.getViewUserList().contains(num))
                .collect(Collectors.toList());
        List<BiUiAnalyseUserResource> resourceList = Lists.newArrayList();
        for (String userId : bothPermission) {
            BiUiAnalyseUserResource resource = new BiUiAnalyseUserResource();
            resource.setResourceId(dto.getId());
            resource.setResourceType(resourceType);
            resource.setPermittedAction(PermittedActionEnum.VIEW.getCode() + "," + PermittedActionEnum.EDIT.getCode());
            resource.setTenantId(tenantId);
            resource.setUserId(userId);
            resourceList.add(resource);
        }
        for (String userId : viewPermission) {
            BiUiAnalyseUserResource resource = new BiUiAnalyseUserResource();
            resource.setResourceId(dto.getId());
            resource.setResourceType(resourceType);
            resource.setPermittedAction(PermittedActionEnum.VIEW.getCode());
            resource.setTenantId(tenantId);
            resource.setUserId(userId);
            resourceList.add(resource);
        }
        for (String userId : editPermission) {
            BiUiAnalyseUserResource resource = new BiUiAnalyseUserResource();
            resource.setResourceId(dto.getId());
            resource.setResourceType(resourceType);
            resource.setPermittedAction(PermittedActionEnum.EDIT.getCode());
            resource.setTenantId(tenantId);
            resource.setUserId(userId);
            resourceList.add(resource);
        }
        if (CollectionUtils.isNotEmpty(resourceList)) {
            this.saveBatch(resourceList);
        }
    }
}