package com.deloitte.bdh.data.analyse.model.request;

import com.google.common.collect.Lists;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * Author:LIJUN
 * Date:24/02p/2020
 * Description:
 */
@Data
public class OrganizationPermissionDto implements Serializable {

    @ApiModelProperty(value = "用户列表")
    private List<String> userList = Lists.newArrayList();

    @ApiModelProperty(value = "组织id")
    private List<String> organizationList = Lists.newArrayList();

}
