package com.deloitte.bdh.data.analyse.service;

import com.deloitte.bdh.common.base.RetRequest;
import com.deloitte.bdh.data.analyse.model.datamodel.request.BaseComponentDataRequest;
import com.deloitte.bdh.data.analyse.model.datamodel.response.BaseComponentDataResponse;
import com.deloitte.bdh.data.analyse.model.request.GetAnalyseDataTreeDto;
import com.deloitte.bdh.data.analyse.model.resp.AnalyseFolderTree;
import com.deloitte.bdh.data.collation.database.po.TableInfo;

import java.util.List;

/**
 * Author:LIJUN
 * Date:12/11/2020
 * Description:
 */
public interface AnalyseModelService {

    /**
     * 根据数据源获取表
     * @return
     */
    List<TableInfo> getAllTable();

    /**
     * 获取数据模型树
     * @param request
     * @return
     */
    List<AnalyseFolderTree> getDataTree(RetRequest<GetAnalyseDataTreeDto> request);

    /**
     * 保存数据模型树
     * @param request
     * @return
     */
    void saveDataTree(RetRequest<List<AnalyseFolderTree>> request);

    /**
     * 获取组件数据
     * @param data
     * @return
     */
    BaseComponentDataResponse getComponentData(BaseComponentDataRequest data) throws Exception;

}
