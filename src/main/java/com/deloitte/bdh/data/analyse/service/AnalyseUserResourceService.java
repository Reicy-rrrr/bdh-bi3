package com.deloitte.bdh.data.analyse.service;

import com.deloitte.bdh.common.base.Service;
import com.deloitte.bdh.data.analyse.model.BiUiAnalyseUserResource;
import com.deloitte.bdh.data.analyse.model.request.SaveResourcePermissionDto;


/**
 * Author:LIJUN
 * Date:08/12/2020
 * Description:
 */
public interface AnalyseUserResourceService extends Service<BiUiAnalyseUserResource> {

    void saveResourcePermission(SaveResourcePermissionDto dto);
}