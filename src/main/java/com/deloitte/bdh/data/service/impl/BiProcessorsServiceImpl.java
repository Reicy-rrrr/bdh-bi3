package com.deloitte.bdh.data.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.deloitte.bdh.common.constant.DSConstant;
import com.deloitte.bdh.common.exception.BizException;
import com.deloitte.bdh.common.util.StringUtil;
import com.deloitte.bdh.data.enums.RunStatusEnum;
import com.deloitte.bdh.data.integration.NifiProcessService;
import com.deloitte.bdh.data.model.BiConnections;
import com.deloitte.bdh.data.model.BiEtlConnection;
import com.deloitte.bdh.data.model.BiEtlProcessor;
import com.deloitte.bdh.data.model.BiProcessors;
import com.deloitte.bdh.data.dao.bi.BiProcessorsMapper;
import com.deloitte.bdh.data.nifi.dto.RunContext;
import com.deloitte.bdh.data.service.BiConnectionsService;
import com.deloitte.bdh.data.service.BiEtlConnectionService;
import com.deloitte.bdh.data.service.BiEtlProcessorService;
import com.deloitte.bdh.data.service.BiProcessorsService;
import com.deloitte.bdh.common.base.AbstractService;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author lw
 * @since 2020-09-27
 */
@Service
@DS(DSConstant.BI_DB)
public class BiProcessorsServiceImpl extends AbstractService<BiProcessorsMapper, BiProcessors> implements BiProcessorsService {
    private static final Logger logger = LoggerFactory.getLogger(BiProcessorsServiceImpl.class);
    @Resource
    private BiProcessorsMapper processorsMapper;
    @Autowired
    private BiConnectionsService connectionsService;
    @Autowired
    private BiEtlProcessorService processorService;
    @Autowired
    private BiEtlConnectionService biEtlConnectionService;
    @Autowired
    private NifiProcessService nifiProcessService;

    @Override
    public List<BiProcessors> getPreChain(String processorsCode) {
        if (StringUtil.isEmpty(processorsCode)) {
            throw new BizException("BiProcessorsServiceImpl.getPreChain error : processorsCode 不能为空");
        }
        BiProcessors biProcessors = processorsMapper.selectOne(
                new LambdaQueryWrapper<BiProcessors>().eq(BiProcessors::getCode, processorsCode)
                        .orderByAsc(BiProcessors::getCode));
        //所有的连接关系
        List<BiConnections> connectionsList = connectionsService.list(
                new LambdaQueryWrapper<BiConnections>().eq(BiConnections::getRelModelCode, biProcessors.getRelModelCode())
        );

        //找出当前processors 的所有上级 processors
        List<String> processorsCodeList = Lists.newArrayList(preProcessorChain(connectionsList, null, processorsCode));
        List<BiProcessors> processorsList = processorsMapper.selectList(
                new LambdaQueryWrapper<BiProcessors>().eq(BiProcessors::getRelModelCode, biProcessors.getRelModelCode())
        );

        List<BiProcessors> preChain = Lists.newLinkedList();
        processorsCodeList.forEach(outer -> processorsList.forEach(inner -> {
                    if (outer.equals(inner.getCode())) {
                        preChain.add(inner);
                    }
                })
        );
        preChain.stream().sorted(Comparator.comparing(BiProcessors::getCode));
        return preChain;
    }

    @Override
    public void preview(RunContext context) throws Exception {
        String result;
        //获取所有的processors 集合
        List<BiProcessors> processorsList = this.getPreChain(context.getPreviewCode());

        //获取processors 下面所有processor 以及需要查询的 connection
        List<BiEtlProcessor> processorList = Lists.newLinkedList();
        List<BiEtlConnection> connectionList = Lists.newLinkedList();
        processorsList.forEach(s -> {
            List<BiEtlProcessor> var = processorService.list(
                    new LambdaQueryWrapper<BiEtlProcessor>().eq(BiEtlProcessor::getRelProcessorsCode, s.getCode())
                            .orderByAsc(BiEtlProcessor::getSequence)
            );

            if (s.getCode().equals(context.getPreviewCode())) {
                //移除最后一个 processor
                BiEtlProcessor lastProcessor = var.get(var.size() - 1);
                var.remove(var.size() - 1);
                //获取最后一个 processor 上的 connection
                BiEtlConnection etlConnection = biEtlConnectionService.getOne(
                        new LambdaQueryWrapper<BiEtlConnection>()
                                .eq(BiEtlConnection::getToProcessorCode, lastProcessor.getCode())
                                .ne(BiEtlConnection::getFromProcessorCode, lastProcessor.getCode())
                );
                connectionList.add(etlConnection);
            }
            processorList.addAll(var);
        });

        //启动
        for (BiEtlProcessor var : processorList) {
            nifiProcessService.runState(var.getProcessId(), RunStatusEnum.RUNNING.getKey(), false);
        }

        //让数据生成目前设置3秒
        Thread.sleep(3000);
        result = nifiProcessService.preview(connectionList.get(0).getConnectionId());
        context.setResult(result);
    }

    @Override
    public void stopAndClear(String processGroupId, String modelCode) throws Exception {
        //停止
        this.runState(processGroupId, RunStatusEnum.STOP.getKey(), true);

        //清空所有
        List<BiEtlConnection> connectionList = biEtlConnectionService.list(
                new LambdaQueryWrapper<BiEtlConnection>().eq(BiEtlConnection::getRelModelCode, modelCode)
        );
        for (BiEtlConnection var : connectionList) {
            nifiProcessService.dropConnections(var.getConnectionId());
        }
    }

    @Override
    public void runState(String id, String state, boolean isGroup) throws Exception {
        nifiProcessService.runState(id, state, isGroup);
    }


    private Set<String> preProcessorChain(List<BiConnections> list, Set<String> set, String processorsCode) {
        if (null == set) {
            set = Sets.newHashSet();
            set.add(processorsCode);
        }
        for (BiConnections var : list) {
            if (var.getToProcessorsCode().equals(processorsCode)) {
                set.add(var.getFromProcessorsCode());
                preProcessorChain(list, set, var.getFromProcessorsCode());
            }
        }
        return set;
    }


}
