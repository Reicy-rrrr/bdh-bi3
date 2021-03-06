package com.deloitte.bdh.data.collation.model.request;


import com.deloitte.bdh.common.util.NifiProcessUtil;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 整理组件请求参数
 *
 * @author chenghzhang
 * @date 2020-11-03
 */
@ApiModel(description = "整理组件请求参数")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ArrangeComponentDto {

    @ApiModelProperty(value = "modelId", example = "0", required = true)
    @NotNull(message = " 模板id 不能为空")
    protected String modelId;

    @ApiModelProperty(value = "字段列表", example = "")
    protected Object fields;

    @ApiModelProperty(value = "坐标", example = "1")
    protected String position = NifiProcessUtil.randPosition();
}
