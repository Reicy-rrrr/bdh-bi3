package com.deloitte.bdh.data.collation.model.request;


import com.deloitte.bdh.data.collation.component.model.ArrangeModifyModel;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 整理组件（修改字段）请求参数
 *
 * @author chenghzhang
 * @date 2020-11-09
 */
@ApiModel(description = "整理组件（修改字段）请求参数")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ArrangeModifyDto extends ArrangeComponentDto {
    @ApiModelProperty(value = "修改字段", example = "")
    @NotNull(message = " 修改字段 不能为空")
    private List<ArrangeModifyModel> fields;
}
