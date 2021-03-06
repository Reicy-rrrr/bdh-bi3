package com.deloitte.bdh.data.analyse.model.request;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.hibernate.validator.constraints.NotBlank;

import java.io.Serializable;

/**
 * Author:LIJUN
 * Date:12/11/2020
 * Description:
 */
@Data
public class AnalysePageIdDto implements Serializable {

    @NotBlank
    @ApiModelProperty(value = "pageId")
    String pageId;

    @ApiModelProperty(value = "配置ID")
    String configId;

    @NotBlank
    @ApiModelProperty(value = "配置内容")
    String content;

}
