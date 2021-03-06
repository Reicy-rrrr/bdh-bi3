package com.deloitte.bdh.data.collation.model.resp;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件预读取结果
 *
 * @author chenghzhang
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BiEtlDbFileUploadResp {
    @ApiModelProperty(value = "文件信息id", example = "25", required = true)
    private String fileId;

    @ApiModelProperty(value = "字段类型", example = "id:1, code:2, value:3", required = true)
    private LinkedHashMap<String, String> columns;

    @ApiModelProperty(value = "表头信息", example = "id,code,value", required = true)
    private List<String> headers;

    @ApiModelProperty(value = "导入预览", example = "id:1, code:01, value:01", required = true)
    private List<Map<String, Object>> lines;
}
