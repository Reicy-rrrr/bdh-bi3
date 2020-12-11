package com.deloitte.bdh.data.collation.model.request;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import javax.validation.constraints.NotNull;

@ApiModel(description = "新增数据集文件夹")
@Setter
@Getter
@ToString
public class CreateDataSetFileDto {

    @ApiModelProperty(value = "上级文件夹ID", example = "0", required = true)
    private String fileId;

    @ApiModelProperty(value = "文件名称", example = "文件名称", required = true)
    @NotNull(message = "文件名称 不能为空")
    private String fileName;

}
