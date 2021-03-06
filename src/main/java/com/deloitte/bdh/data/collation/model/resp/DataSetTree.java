package com.deloitte.bdh.data.collation.model.resp;

import com.deloitte.bdh.data.collation.model.BiDataSet;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;


@ApiModel(description = "数据集")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataSetTree extends BiDataSet {

    @ApiModelProperty(value = "模型名称")
    private String modelName;

    @ApiModelProperty(value = "描述")
    private String comments;

    @ApiModelProperty(value = "上次刷新时间")
    private String lastExecuteDate;

    @ApiModelProperty(value = "权限")
    private List<String> permittedAction;

    @ApiModelProperty(value = "下级")
    private List<DataSetTree> children;
}
