package com.deloitte.bdh.data.analyse.controller;


import com.deloitte.bdh.common.base.PageResult;
import com.deloitte.bdh.common.base.RetRequest;
import com.deloitte.bdh.common.base.RetResponse;
import com.deloitte.bdh.common.base.RetResult;
import com.deloitte.bdh.data.analyse.model.BiUiAnalyseCategory;
import com.deloitte.bdh.data.analyse.model.request.AnalyseCategoryReq;
import com.deloitte.bdh.data.analyse.model.request.CreateAnalyseCategoryDto;
import com.deloitte.bdh.data.analyse.model.request.InitTenantReq;
import com.deloitte.bdh.data.analyse.model.request.UpdateAnalyseCategoryDto;
import com.deloitte.bdh.data.analyse.model.resp.AnalyseCategoryTree;
import com.deloitte.bdh.data.analyse.service.BiUiAnalyseCategoryService;
import com.github.pagehelper.PageHelper;
import io.swagger.annotations.ApiOperation;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author bo.wang
 * @since 2020-10-19
 */
@RestController
@RequestMapping("/ui/analyse/category")
public class BiUiAnalyseCategoryController {
    @Resource
    BiUiAnalyseCategoryService biUiAnalyseCategoryService;

    @ApiOperation(value = "基于租户查询报表的树状结构", notes = "基于租户查询报表的树状结构")
    @PostMapping("/getCategoryTree")
    public RetResult<List<AnalyseCategoryTree>> getCategoryTree(@RequestBody @Validated RetRequest<AnalyseCategoryReq> request) {
        return RetResponse.makeOKRsp(biUiAnalyseCategoryService.getTree(request.getData()));
    }

    @ApiOperation(value = "基于租户获取页面列表", notes = "基于租户获取页面列表")
    @PostMapping("/getAnalyseCategorys")
    public RetResult<PageResult> getAnalyseCategorys(@RequestBody @Validated RetRequest<AnalyseCategoryReq> request) {
        PageHelper.startPage(request.getData().getPage(), request.getData().getSize());
        return RetResponse.makeOKRsp(biUiAnalyseCategoryService.getAnalyseCategorys(request.getData()));
    }

    @ApiOperation(value = "查看单个页面详情", notes = "查看单个页面详情")
    @PostMapping("/getAnalyseCategory")
    public RetResult<BiUiAnalyseCategory> getAnalyseCategory(@RequestBody @Validated RetRequest<String> request) {
        return RetResponse.makeOKRsp(biUiAnalyseCategoryService.getAnalyseCategory(request.getData()));
    }

    @ApiOperation(value = "新增页面", notes = "新增页面")
    @PostMapping("/createAnalyseCategory")
    public RetResult<BiUiAnalyseCategory> createAnalyseCategory(@RequestBody @Validated RetRequest<CreateAnalyseCategoryDto> request) throws Exception {
        return RetResponse.makeOKRsp(biUiAnalyseCategoryService.createAnalyseCategory(request.getData()));
    }

    @ApiOperation(value = "删除页面", notes = "删除页面")
    @PostMapping("/delAnalyseCategory")
    public RetResult<Void> delAnalyseCategory(@RequestBody @Validated RetRequest<String> request) throws Exception {
        biUiAnalyseCategoryService.delAnalyseCategory(request.getData());
        return RetResponse.makeOKRsp();
    }

    @ApiOperation(value = "修改页面", notes = "修改页面")
    @PostMapping("/updateAnalyseCategory")
    public RetResult<BiUiAnalyseCategory> updateAnalyseCategory(@RequestBody @Validated RetRequest<UpdateAnalyseCategoryDto> request) throws Exception {
        return RetResponse.makeOKRsp(biUiAnalyseCategoryService.updateAnalyseCategory(request.getData()));
    }

    @ApiOperation(value = "修改页面", notes = "修改页面")
    @PostMapping("/updateAnalyseCategory")
    public RetResult<BiUiAnalyseCategory> initTenantAnalyse(@RequestBody @Validated RetRequest<InitTenantReq> request) throws Exception {
        biUiAnalyseCategoryService.initTenantAnalyse(request.getData());
        return RetResponse.makeOKRsp();
    }
}
