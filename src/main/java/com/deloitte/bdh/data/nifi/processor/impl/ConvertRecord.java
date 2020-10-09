package com.deloitte.bdh.data.nifi.processor.impl;


import com.deloitte.bdh.data.enums.ProcessorTypeEnum;
import com.deloitte.bdh.data.model.BiEtlDbRef;
import com.deloitte.bdh.data.model.BiEtlDbService;
import com.deloitte.bdh.data.model.BiEtlParams;
import com.deloitte.bdh.data.model.BiEtlProcessor;
import com.deloitte.bdh.data.nifi.dto.Processor;
import com.deloitte.bdh.data.nifi.dto.ProcessorContext;
import com.deloitte.bdh.data.nifi.processor.AbstractProcessor;
import com.deloitte.bdh.data.service.BiEtlDbCSService;
import com.google.common.collect.Maps;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service("ConvertRecord")
public class ConvertRecord extends AbstractProcessor {

    @Autowired
    private BiEtlDbCSService biEtlDbCSService;

    @Override
    public Map<String, Object> save(ProcessorContext context) throws Exception {
        String dbId = context.getBiEtlDatabaseInf().getId();
        BiEtlDbService query = new BiEtlDbService();
        query.setDbId(dbId);
        List<BiEtlDbService> controllerServices = biEtlDbCSService.list(query);

        // 配置ConvertRecord的属性
        Map<String, Object> properties = Maps.newHashMap();
        if (!CollectionUtils.isEmpty(controllerServices)) {
            for (BiEtlDbService controllerService : controllerServices) {
                if (controllerService == null) {
                    continue;
                }
                if ("record-reader".equals(controllerService.getPropertyName())) {
                    properties.put("record-reader", controllerService.getServiceId());
                    continue;
                }

                if ("record-writer".equals(controllerService.getPropertyName())) {
                    properties.put("record-writer", controllerService.getServiceId());
                    continue;
                }
            }
        }

        // 调度相关的默认值
        Map<String, Object> config = Maps.newHashMap();
        config.put("schedulingPeriod", "1 * * * * ?");
        config.put("schedulingStrategy", "CRON_DRIVEN");
        config.put("properties", properties);

        // processor 公共的
        Map<String, Object> component = Maps.newHashMap();
        component.put("name", MapUtils.getString(context.getReq(), "name"));
        component.put("type", ProcessorTypeEnum.ConvertRecord.getvalue());
        component.put("config", config);

        //新建 processor
        BiEtlProcessor biEtlProcessor = createProcessor(context, component);
        // 新建 processor param
        List<BiEtlParams> paramsList = createParams(biEtlProcessor, context, component);
        //该组件有关联表的信息
        BiEtlDbRef dbRef = createDbRef(biEtlProcessor, context);

        Processor processor = new Processor();
        BeanUtils.copyProperties(biEtlProcessor, processor);
        processor.setList(paramsList);
        processor.setDbRef(dbRef);
        context.addProcessorList(processor);
        return null;
    }

    @Override
    protected Map<String, Object> rSave(ProcessorContext context) throws Exception {
        Processor processor = context.getTempProcessor();
        processorService.delProcessor(processor.getId());

        List<BiEtlParams> paramsList = processor.getList();
        if (CollectionUtils.isNotEmpty(paramsList)) {
            List<String> list = paramsList
                    .stream()
                    .map(BiEtlParams::getId)
                    .collect(Collectors.toList());
            paramsService.removeByIds(list);
        }

        //删除该组件有关联表的信息
        etlDbRefService.removeById(processor.getDbRef().getId());
        return null;
    }

    @Override
    protected Map<String, Object> delete(ProcessorContext context) throws Exception {
        Processor processor = context.getTempProcessor();
        processorService.delProcessor(processor.getId());
        List<BiEtlParams> paramsList = processor.getList();
        if (CollectionUtils.isNotEmpty(paramsList)) {
            List<String> list = paramsList
                    .stream()
                    .map(BiEtlParams::getId)
                    .collect(Collectors.toList());
            paramsService.removeByIds(list);
        }

        //若有关联表的信息，该组件有则删除
        etlDbRefService.removeById(processor.getDbRef().getId());
        return null;
    }

    @Override
    protected Map<String, Object> rDelete(ProcessorContext context) throws Exception {
        List<BiEtlParams> sourceParamList = context.getTempProcessor().getList();
        //获取删除前的参数 map
        Map<String, Object> sourceParam = transferToMap(sourceParamList);
        //新建 删除的 processor
        BiEtlProcessor biEtlProcessor = createProcessor(context, sourceParam);
        //新建 processor param
        createParams(biEtlProcessor, context, sourceParam);
        //该组件有关联表的信息
        createDbRef(biEtlProcessor, context);

        //补偿删除必须要调用该方法
        setTempForRdelete(biEtlProcessor, context);
        return null;
    }

    @Override
    public Map<String, Object> update(ProcessorContext context) throws Exception {
        return null;
    }

    @Override
    protected Map<String, Object> rUpdate(ProcessorContext context) throws Exception {
        return null;
    }

    @Override
    public Map<String, Object> validate(ProcessorContext context) throws Exception {
        return null;

    }

    @Override
    protected ProcessorTypeEnum processorType() {
        return ProcessorTypeEnum.ConvertRecord;
    }
}