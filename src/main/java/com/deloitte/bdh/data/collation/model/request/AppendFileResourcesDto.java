package com.deloitte.bdh.data.collation.model.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import javax.validation.constraints.NotNull;
import java.util.Map;


/**
 * @author chenghzhang
 */
@ApiModel(description = "追加上传文件数据源DTO")
@Setter
@Getter
@ToString
public class AppendFileResourcesDto {
    @ApiModelProperty(value = "数据源信息id", example = "10", required = true)
    @NotNull(message = "数据源信息id 不能为空")
    private String dbId;

    @ApiModelProperty(value = "文件信息id", example = "11", required = true)
    @NotNull(message = "文件信息id 不能为空")
    private String fileId;

    @ApiModelProperty(value = "字段类型", example = "id:1, code:2, value:3", required = true)
    @NotNull(message = "字段类型 不能为空")
    private Map<String, String> columns;

    @ApiModelProperty(value = "数据源名称", example = "数据源名称", required = true)
    private String name;
}
