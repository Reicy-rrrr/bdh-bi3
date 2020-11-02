package com.deloitte.bdh.data.collation.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.deloitte.bdh.common.constant.DSConstant;
import com.deloitte.bdh.data.collation.component.constant.ComponentCons;
import com.deloitte.bdh.data.collation.database.DbHandler;
import com.deloitte.bdh.data.collation.enums.*;
import com.deloitte.bdh.data.collation.model.BiComponent;
import com.deloitte.bdh.data.collation.model.BiComponentParams;
import com.deloitte.bdh.data.collation.model.BiEtlMappingConfig;
import com.deloitte.bdh.data.collation.model.BiEtlSyncPlan;
import com.deloitte.bdh.data.collation.dao.bi.BiEtlSyncPlanMapper;
import com.deloitte.bdh.data.collation.service.*;
import com.deloitte.bdh.common.base.AbstractService;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author lw
 * @since 2020-10-26
 */
@Service
@DS(DSConstant.BI_DB)
public class BiEtlSyncPlanServiceImpl extends AbstractService<BiEtlSyncPlanMapper, BiEtlSyncPlan> implements BiEtlSyncPlanService {

    @Resource
    private BiEtlSyncPlanMapper syncPlanMapper;
    @Autowired
    private BiEtlMappingConfigService configService;
    @Autowired
    private BiProcessorsService processorsService;
    @Autowired
    private DbHandler dbHandler;
    @Resource
    private AsyncTaskExecutor executor;
    @Autowired
    private BiComponentService componentService;
    @Autowired
    private BiComponentParamsService componentParamsService;


    @Override
    public void sync() throws Exception {
        syncToExecute();
        syncExecuting();
    }

    private void syncToExecute() {
        //寻找类型为同步，状态为待执行的计划
        List<BiEtlSyncPlan> list = syncPlanMapper.selectList(new LambdaQueryWrapper<BiEtlSyncPlan>()
                .eq(BiEtlSyncPlan::getPlanType, "0")
                .eq(BiEtlSyncPlan::getPlanStage, PlanStageEnum.TO_EXECUTE.getKey())
                .isNull(BiEtlSyncPlan::getPlanResult)
                .orderByAsc(BiEtlSyncPlan::getCreateDate)
                .last("limit 50")
        );

        list.forEach(s -> {
            if (YesOrNoEnum.YES.getKey().equals(s.getIsFirst())) {
                syncToExecuteNonTask(s);
            } else {
                syncToExecuteTask(s);
            }
        });

    }

    private void syncExecuting() {
        //寻找类型为同步，状态为待执行的计划
        List<BiEtlSyncPlan> list = syncPlanMapper.selectList(new LambdaQueryWrapper<BiEtlSyncPlan>()
                .eq(BiEtlSyncPlan::getPlanType, "0")
                .eq(BiEtlSyncPlan::getPlanStage, PlanStageEnum.EXECUTING.getKey())
                .isNull(BiEtlSyncPlan::getPlanResult)
                .orderByAsc(BiEtlSyncPlan::getCreateDate)
                .last("limit 50")
        );
        list.forEach(this::syncExecutingTask);

    }

    private void syncToExecuteNonTask(BiEtlSyncPlan plan) {
        int count = Integer.parseInt(plan.getProcessCount());
        try {
            //判断已处理次数,超过3次则动作完成。
            if (3 < count) {
                plan.setPlanStage(PlanStageEnum.EXECUTED.getKey());
                plan.setPlanResult(PlanResultEnum.FAIL.getKey());
            } else {
                //组装数据 启动nifi 改变执行状态
                BiEtlMappingConfig config = configService.getOne(new LambdaQueryWrapper<BiEtlMappingConfig>()
                        .eq(BiEtlMappingConfig::getCode, plan.getRefMappingCode())
                );

                //非调度发起的同步第一次
                if (0 == count) {
                    dbHandler.truncateTable(config.getToTableName());
                }
                //获取归属组件信息
                String processorsCode = getProcessorsCode(config);
                //启动NIFI
                processorsService.runState(processorsCode, RunStatusEnum.RUNNING, true);
                //修改plan 执行状态
                plan.setPlanStage(PlanStageEnum.EXECUTING.getKey());
                //重置
                plan.setProcessCount("0");
                plan.setResultDesc(" ");
            }
        } catch (Exception e1) {
            e1.printStackTrace();
            count++;
            plan.setResultDesc(e1.getMessage());
            plan.setProcessCount(String.valueOf(count));
        } finally {
            syncPlanMapper.updateById(plan);
        }
    }

    private void syncToExecuteTask(BiEtlSyncPlan plan) {
        int count = Integer.parseInt(plan.getProcessCount());
        try {
            //判断已处理次数,超过3次则动作完成。
            if (3 < count) {
                plan.setPlanStage(PlanStageEnum.EXECUTED.getKey());
                plan.setPlanResult(PlanResultEnum.FAIL.getKey());
            } else {
                //组装数据 启动nifi 改变执行状态
                BiEtlMappingConfig config = configService.getOne(new LambdaQueryWrapper<BiEtlMappingConfig>()
                        .eq(BiEtlMappingConfig::getCode, plan.getRefMappingCode())
                );

                SyncTypeEnum typeEnum = SyncTypeEnum.getEnumByKey(config.getType());
                if (0 == count && SyncTypeEnum.FULL == typeEnum) {
                    //全量则清空
                    dbHandler.truncateTable(config.getToTableName());
                }
                //启动NIFI
                processorsService.runState(getProcessorsCode(config), RunStatusEnum.RUNNING, true);
                //修改plan 执行状态
                plan.setPlanStage(PlanStageEnum.EXECUTING.getKey());
                //重置
                plan.setProcessCount("0");
                plan.setResultDesc(" ");
            }
        } catch (Exception e1) {
            e1.printStackTrace();
            count++;
            plan.setResultDesc(e1.getMessage());
            plan.setProcessCount(String.valueOf(count));
        } finally {
            syncPlanMapper.updateById(plan);
        }
    }

    private void syncExecutingTask(BiEtlSyncPlan plan) {
        int count = Integer.parseInt(plan.getProcessCount());
        BiEtlMappingConfig config = configService.getOne(new LambdaQueryWrapper<BiEtlMappingConfig>()
                .eq(BiEtlMappingConfig::getCode, plan.getRefMappingCode())
        );

        try {
            //判断已处理次数,超过10次则动作完成。
            if (10 < count) {
                plan.setPlanStage(PlanStageEnum.EXECUTED.getKey());
                plan.setPlanResult(PlanResultEnum.FAIL.getKey());
                //调用nifi 停止与清空
                String tenantCode = doHeader();
                String processorsCode = getProcessorsCode(config);
                executor.execute(() -> {
                    try {
                        processorsService.runStateAsync(tenantCode, processorsCode, RunStatusEnum.STOP, true);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });

            } else {
                count++;
                //基于条件实时查询 localCount
                String condition = assemblyCondition(plan.getIsFirst(), config);
                long nowCount = dbHandler.getCount(config.getToTableName(), condition);

                //判断目标数据库与源数据库的表count
                String sqlCount = plan.getSqlCount();
                String localCount = String.valueOf(nowCount);
                if (Long.parseLong(localCount) < Long.parseLong(sqlCount)) {
                    // 等待下次再查询
                    plan.setSqlLocalCount(localCount);
                } else {
                    //已同步完成
                    plan.setSqlLocalCount(localCount);
                    plan.setPlanResult(PlanResultEnum.SUCCESS.getKey());

                    //调用nifi 停止与清空
                    String tenantCode = doHeader();
                    String processorsCode = getProcessorsCode(config);
                    executor.execute(() -> {
                        try {
                            processorsService.runStateAsync(tenantCode, processorsCode, RunStatusEnum.STOP, true);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });

                    //修改plan 执行状态
                    plan.setPlanStage(PlanStageEnum.EXECUTED.getKey());
                    plan.setPlanResult(PlanResultEnum.SUCCESS.getKey());
                    plan.setResultDesc(PlanResultEnum.SUCCESS.getValue());
                    //todo 设置MappingConfig 的 LOCAL_COUNT和 OFFSET_VALUE

                    //设置Component 状态为可用
                    BiComponent component = componentService.getOne(new LambdaQueryWrapper<BiComponent>()
                            .eq(BiComponent::getCode, config.getRefComponentCode())
                    );
                    component.setEffect(EffectEnum.ENABLE.getKey());
                    component.setModifiedDate(LocalDateTime.now());
                    componentService.updateById(component);
                }
            }
        } catch (Exception e1) {
            e1.printStackTrace();
            plan.setResultDesc(e1.getMessage());
        } finally {
            plan.setProcessCount(String.valueOf(count));
            syncPlanMapper.updateById(plan);
        }
    }

    private String assemblyCondition(String isFirst, BiEtlMappingConfig config) {
        String condition = null;
        //非第一次且是增量
        if (YesOrNoEnum.NO.getKey().equals(isFirst)) {
            SyncTypeEnum typeEnum = SyncTypeEnum.getEnumByKey(config.getType());
            if (SyncTypeEnum.INCREMENT == typeEnum) {
                String offsetField = config.getOffsetField();
                String offsetValue = config.getOffsetValue();
                if (StringUtils.isNotBlank(offsetValue)) {
                    condition = "'" + offsetField + "' > =" + "'" + offsetValue + "'";
                }
            }
        }
        return condition;
    }

    private String getProcessorsCode(BiEtlMappingConfig config) {
        BiComponent component = componentService.getOne(new LambdaQueryWrapper<BiComponent>()
                .eq(BiComponent::getCode, config.getRefComponentCode())
        );
        if (null == component) {
            throw new RuntimeException("EtlServiceImpl.getProcessorsCode.error : 未找到目标 组件");
        }
        BiComponentParams componentParams = componentParamsService.getOne(new LambdaQueryWrapper<BiComponentParams>()
                .eq(BiComponentParams::getRefComponentCode, component.getCode())
                .eq(BiComponentParams::getParamKey, ComponentCons.REF_PROCESSORS_CDOE)
        );
        if (null == componentParams) {
            throw new RuntimeException("EtlServiceImpl.getProcessorsCode.error : 未找到目标组件 参数");
        }
        return componentParams.getParamValue();
    }

    @Override
    public void etl() {
        //todo 生成调度计划
    }
}
