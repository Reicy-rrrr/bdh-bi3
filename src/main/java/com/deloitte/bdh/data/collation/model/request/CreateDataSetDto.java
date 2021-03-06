package com.deloitte.bdh.data.collation.model.request;


import com.deloitte.bdh.data.analyse.model.request.SaveResourcePermissionDto;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import javax.validation.constraints.NotNull;

@ApiModel(description = "新增数据集")
@Setter
@Getter
@ToString
public class CreateDataSetDto {

    @ApiModelProperty(value = "文件夹ID", example = "0", required = true)
    @NotNull(message = "文件夹ID 不能为空")
    private String folderId;

    @ApiModelProperty(value = "数据源ID", example = "1", required = true)
    @NotNull(message = "数据源ID 不能为空")
    private String refSourceId;

    @ApiModelProperty(value = "表名称", example = "表名称", required = true)
    @NotNull(message = "表名称 不能为空")
    private String tableName;

    @ApiModelProperty(value = "表别名", example = "表别名", required = true)
    @NotNull(message = "表别名 不能为空")
    private String tableNameDesc;

    @ApiModelProperty(value = "保存资源权限", example = "保存资源权限", required = true)
    private SaveResourcePermissionDto permissionDto;

    @ApiModelProperty(value = "comments", example = "comments", required = true)
    private String comments;
}
