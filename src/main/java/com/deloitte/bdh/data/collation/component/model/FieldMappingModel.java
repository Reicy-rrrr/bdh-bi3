package com.deloitte.bdh.data.collation.component.model;

import com.deloitte.bdh.data.collation.database.po.TableField;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 字段映射模型
 *
 * @author chenghzhang
 * @date 2020/11/02
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FieldMappingModel implements Cloneable {
    /** 临时字段名称：重命名后的字段名称（example:BI570E5A0E84C5E171） */
    @ApiModelProperty(value = "临时字段名称", example = "BI570E5A0E84C5E171")
    private String tempFieldName;
    /** 最终字段名称：最终字段名称（最终表中的字段） */
    @ApiModelProperty(value = "最终字段名称", example = "user_name")
    private String finalFieldName;
    /** 最终字段类型：最终字段类型（系统中的五种类型） */
    @ApiModelProperty(value = "最终字段类型", example = "最终字段类型")
    private String finalFieldType;
    /** 最终字段描述：最终字段名称描述（最终表字段的描述） */
    @ApiModelProperty(value = "最终字段描述", example = "用户名称")
    private String finalFieldDesc;
    /** 原始字段名称 */
    @ApiModelProperty(value = "原始字段名称", example = "user_name")
    private String originalFieldName;
    /** 原始表名称 */
    @ApiModelProperty(value = "原始表名称", example = "tb_user_info")
    private String originalTableName;
    /** 原始字段类型 */
    @ApiModelProperty(value = "原始字段类型", example = "decimal(10,4)")
    private String originalColumnType;
    /** 原始字段是否为索引 */
    @ApiModelProperty(value = "原始字段是否为索引", example = "true")
    private boolean isIndex = false;
    /** 表字段 */
    @ApiModelProperty(value = "表字段", example = "")
    private TableField tableField;

    public FieldMappingModel(String tempFieldName, String finalFieldName, String finalFieldType, String finalFieldDesc, String originalFieldName, String originalTableName, String originalColumnType) {
        this.tempFieldName = tempFieldName;
        this.finalFieldName = finalFieldName;
        this.finalFieldDesc = finalFieldDesc;
        this.finalFieldType = finalFieldType;
        this.originalFieldName = originalFieldName;
        this.originalTableName = originalTableName;
        this.originalColumnType = originalColumnType;
    }

    public FieldMappingModel(String tempFieldName, String finalFieldName, String finalFieldType, String finalFieldDesc, String originalFieldName, String originalTableName, String originalColumnType, TableField tableField) {
        this.tempFieldName = tempFieldName;
        this.finalFieldName = finalFieldName;
        this.finalFieldDesc = finalFieldDesc;
        this.finalFieldType = finalFieldType;
        this.originalFieldName = originalFieldName;
        this.originalTableName = originalTableName;
        this.originalColumnType = originalColumnType;
        this.tableField = tableField;
    }

    @Override
    public FieldMappingModel clone(){
        FieldMappingModel model = null;
        try {
            model = (FieldMappingModel) super.clone();
            model.setTableField(this.tableField.clone());
        } catch (CloneNotSupportedException e) {

        }
        return model;
    }
}
