package com.deloitte.bdh.data.service;

import com.deloitte.bdh.common.base.PageResult;
import com.deloitte.bdh.data.model.BiEtlDatabaseInf;
import com.deloitte.bdh.common.base.Service;
import com.deloitte.bdh.data.model.request.CreateResourcesDto;
import com.deloitte.bdh.data.model.request.GetResourcesDto;
import com.deloitte.bdh.data.model.request.RunResourcesDto;
import com.deloitte.bdh.data.model.request.UpdateResourcesDto;

import java.util.List;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author lw
 * @since 2020-09-24
 */
public interface BiEtlDatabaseInfService extends Service<BiEtlDatabaseInf> {

    /**
     * 基于租户获取数据源列表
     *
     * @param dto
     * @return
     */
    PageResult<List<BiEtlDatabaseInf>> getResources(GetResourcesDto dto);

    /**
     * 创建数据源
     *
     * @param dto
     * @return
     */
    BiEtlDatabaseInf createResource(CreateResourcesDto dto) throws Exception;

    /**
     * 启用/禁用数据源
     *
     * @param dto
     * @return
     */
    BiEtlDatabaseInf runResource(RunResourcesDto dto) throws Exception;

    /**
     * del数据源
     *
     * @param dto
     * @return
     */
    void delResource(String id) throws Exception;

    /**
     * 修改数据源
     *
     * @param dto
     * @return
     */
    BiEtlDatabaseInf updateResource(UpdateResourcesDto dto) throws Exception;
}
