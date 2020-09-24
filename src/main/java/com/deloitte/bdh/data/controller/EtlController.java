package com.deloitte.bdh.data.controller;


import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.deloitte.bdh.common.base.PageResult;
import com.deloitte.bdh.common.base.RetRequest;
import com.deloitte.bdh.common.base.RetResponse;
import com.deloitte.bdh.common.base.RetResult;
import com.deloitte.bdh.common.util.StringUtil;
import com.deloitte.bdh.data.model.BiEtlDatabaseInf;
import com.deloitte.bdh.data.model.request.CreateResourcesDto;
import com.deloitte.bdh.data.model.request.GetResourcesDto;
import com.deloitte.bdh.data.service.BiEtlDatabaseInfService;
import com.github.pagehelper.PageHelper;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;


/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author lw
 * @since 2020-09-23
 */
@RestController
@RequestMapping("/etl")
public class EtlController {
    @Autowired
    private BiEtlDatabaseInfService biEtlDatabaseInfService;

    @ApiOperation(value = "基于租户获取数据源列表", notes = "基于租户获取数据源列表")
    @PostMapping("/getResources")
    public RetResult<PageResult> getResources(@RequestBody @Validated RetRequest<GetResourcesDto> request) {
        PageHelper.startPage(request.getData().getPage(), request.getData().getSize());
        return RetResponse.makeOKRsp(biEtlDatabaseInfService.getResources(request.getData()));
    }

    @ApiOperation(value = "新增数据源", notes = "新增数据源")
    @PostMapping("/createResource")
    public RetResult<BiEtlDatabaseInf> createResource(@RequestBody @Validated RetRequest<CreateResourcesDto> request) throws Exception {
        return RetResponse.makeOKRsp(biEtlDatabaseInfService.createResource(request.getData()));
    }

}
