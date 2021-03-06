package com.deloitte.bdh.data.collation.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Resource;

import com.deloitte.bdh.common.exception.BizException;
import com.deloitte.bdh.common.mq.MessageProducer;
import com.deloitte.bdh.common.util.ThreadLocalHolder;
import com.deloitte.bdh.data.analyse.enums.ResourceMessageEnum;
import com.deloitte.bdh.data.analyse.service.impl.LocaleMessageService;
import org.apache.commons.lang.StringUtils;
import org.codehaus.jackson.type.TypeReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.deloitte.bdh.common.constant.DSConstant;
import com.deloitte.bdh.common.util.JsonUtil;
import com.deloitte.bdh.data.collation.component.model.ComponentModel;
import com.deloitte.bdh.data.collation.database.DbHandler;
import com.deloitte.bdh.data.collation.enums.EffectEnum;
import com.deloitte.bdh.data.collation.enums.MqTypeEnum;
import com.deloitte.bdh.data.collation.enums.PlanResultEnum;
import com.deloitte.bdh.data.collation.enums.PlanStageEnum;
import com.deloitte.bdh.data.collation.enums.SyncTypeEnum;
import com.deloitte.bdh.data.collation.enums.YesOrNoEnum;
import com.deloitte.bdh.data.collation.model.BiComponent;
import com.deloitte.bdh.data.collation.model.BiEtlMappingConfig;
import com.deloitte.bdh.data.collation.model.BiEtlModel;
import com.deloitte.bdh.data.collation.model.BiEtlSyncPlan;
import com.deloitte.bdh.data.collation.model.BiProcessors;
import com.deloitte.bdh.data.collation.mq.KafkaMessage;
import com.deloitte.bdh.data.collation.mq.KafkaSyncDto;
import com.deloitte.bdh.data.collation.nifi.template.servie.Transfer;
import com.deloitte.bdh.data.collation.service.BiComponentService;
import com.deloitte.bdh.data.collation.service.BiEtlMappingConfigService;
import com.deloitte.bdh.data.collation.service.BiEtlModelHandleService;
import com.deloitte.bdh.data.collation.service.BiEtlModelService;
import com.deloitte.bdh.data.collation.service.BiEtlSyncPlanService;
import com.deloitte.bdh.data.collation.service.BiProcessorsService;
import com.deloitte.bdh.data.collation.service.MqBiPlanService;

import lombok.extern.slf4j.Slf4j;


@Service
@Slf4j
@SuppressWarnings("rawtypes")
@DS(DSConstant.BI_DB)
public class MqBiPlanServiceImpl implements MqBiPlanService {


    @Resource
    private BiEtlSyncPlanService syncPlanService;
    @Autowired
    private BiEtlMappingConfigService configService;
    @Autowired
    private BiProcessorsService processorsService;
    @Autowired
    private DbHandler dbHandler;
    @Autowired
    private BiComponentService componentService;
    @Autowired
    private BiEtlModelService modelService;
    @Autowired
    private BiEtlModelHandleService modelHandleService;
    @Autowired
    private Transfer transfer;

    @Resource
    private MessageProducer messageProducer;

    @Resource
    private LocaleMessageService localeMessageService;

    @Override
    public void biEtlSyncPlan(KafkaMessage message) {
        log.info("uuid:" + message.getUuid() + "kafka Plan_start ?????????????????????????????????????????????++++++++++++++++++++++++++++++++");
        //??????????????????????????????????????????
        String body = message.getBody();
        log.info("uuid:" + message.getUuid() + "kafka Plan_start body :" + body);
        List<KafkaSyncDto> list = JsonUtil.string2Obj(body, new TypeReference<List<KafkaSyncDto>>() {
        });
        log.info("uuid:" + message.getUuid() + "kafka Plan_start list:" + list.toString());
        if (!CollectionUtils.isEmpty(list)) {
            syncToExecute(list, message);
            //??????Mq topic ?????? ?????????????????????????????????
            message.setBeanName(MqTypeEnum.Plan_check_end.getType());
            messageProducer.sendSyncMessage(message, 1);
        }

    }

    @Override
    public void biEtlSyncManyPlan(KafkaMessage message) {
        log.info("uuid:" + message.getUuid() + " kafka ?????????????????????????????????????????????????????????????????? ???????????????");
        String body = message.getBody();
        List<KafkaSyncDto> list = JsonUtil.string2Obj(body, new TypeReference<List<KafkaSyncDto>>() {
        });
        if (!CollectionUtils.isEmpty(list)) {
            //???????????????????????????????????? ????????????????????????????????????????????????  ????????????????????????????????????topic ????????????????????????
            syncExecuting(message, list);
        }

    }

    @Override
    public void biEtlSyncManyEndPlan(KafkaMessage message) {
        log.info("uuid:" + message.getUuid() + " kafka ?????????????????????????????????????????????????????????????????????tyep ???1 ??????????????????????????? ???????????????????????????");
        String body = message.getBody();
        List<KafkaSyncDto> planList = JsonUtil.string2Obj(body, new TypeReference<List<KafkaSyncDto>>() {
        });
        if (org.apache.commons.collections4.CollectionUtils.isEmpty(planList)) {
            return;
        }
        try {
            etlToExecute(planList, message);
            etlExecuting(planList, message);
        } catch (Exception e) {
            // TODO: handle exception
        }


    }

    //??????
    private void syncToExecute(List<KafkaSyncDto> list2, KafkaMessage message) {
        //???????????????????????????????????????????????????
        for (KafkaSyncDto plan : list2) {
            List<BiEtlSyncPlan> list = syncPlanService.list(new LambdaQueryWrapper<BiEtlSyncPlan>()
                    .eq(BiEtlSyncPlan::getPlanType, "0")
                    .eq(BiEtlSyncPlan::getPlanStage, PlanStageEnum.TO_EXECUTE.getKey())
                    .eq(BiEtlSyncPlan::getGroupCode, plan.getGroupCode())
                    .eq(BiEtlSyncPlan::getCode, plan.getCode())
                    .eq(BiEtlSyncPlan::getTenantId, message.getTenantId())
                    .isNull(BiEtlSyncPlan::getPlanResult)
                    .orderByAsc(BiEtlSyncPlan::getCreateDate)
            );
            log.info("uuid:" + message.getUuid() + " kafka Plan_start syncToExecute  " + list.toString());
            list.forEach(s -> {
                if (YesOrNoEnum.YES.getKey().equals(s.getIsFirst())) {
                    log.info("uuid:" + message.getUuid() + " kafka Plan_start syncToExecute  YesOrNoEnum.YES.getKey() ++++++++++++++++++++++++++++++++");
                    syncToExecuteNonTask(s);
                } else {
                    log.info("uuid:" + message.getUuid() + " kafka Plan_start syncToExecute  YesOrNoEnum.no.getKey() ++++++++++++++++++++++++++++++++");
                    syncToExecuteTask(s);
                }
            });
        }

    }

    private void syncToExecuteNonTask(BiEtlSyncPlan plan) {
        int count = Integer.parseInt(plan.getProcessCount());
        try {
            //???????????? ??????nifi ??????????????????
            BiEtlMappingConfig config = configService.getOne(new LambdaQueryWrapper<BiEtlMappingConfig>()
                    .eq(BiEtlMappingConfig::getCode, plan.getRefMappingCode())
            );

            //?????????????????????????????????
            if (0 == count) {
                //???????????????
                String result = configService.validateSource(config);
                if (null != result) {
                    throw new RuntimeException(result);
                }
                dbHandler.truncateTable(config.getToTableName());
            }
            //????????????????????????
            String processorsGroupId = componentService.getProcessorsGroupId(config.getRefComponentCode());
            //??????NIFI
            transfer.run(processorsGroupId);
            //??????plan ????????????
            plan.setPlanStage(PlanStageEnum.EXECUTING.getKey());
            //??????
            plan.setProcessCount("0");
            plan.setResultDesc(null);
        } catch (Exception e) {
            log.error("sync.syncToExecuteNonTask:++++++++++++++++++++++++++++++", e);
            count++;
            plan.setPlanStage(PlanStageEnum.EXECUTED.getKey());
            plan.setPlanResult(PlanResultEnum.FAIL.getKey());
            plan.setResultDesc(e.getMessage());
            plan.setProcessCount(String.valueOf(count));
        } finally {
            syncPlanService.updateById(plan);
        }
    }

    private void syncToExecuteTask(BiEtlSyncPlan plan) {
        int count = Integer.parseInt(plan.getProcessCount());
        try {
            //???????????? ??????nifi ??????????????????
            BiEtlMappingConfig config = configService.getOne(new LambdaQueryWrapper<BiEtlMappingConfig>()
                    .eq(BiEtlMappingConfig::getCode, plan.getRefMappingCode())
            );
            String processorsGroupId = componentService.getProcessorsGroupId(config.getRefComponentCode());
            SyncTypeEnum typeEnum = SyncTypeEnum.getEnumByKey(config.getType());
            //????????????????????????????????????????????????????????????
            if (0 == count && SyncTypeEnum.FULL == typeEnum) {
                dbHandler.truncateTable(config.getToTableName());
                //#10002???????????????????????????????????????
                transfer.stop(processorsGroupId);
                //??????
                transfer.clear(processorsGroupId);
            }
            //??????NIFI
            transfer.run(processorsGroupId);
            //??????plan ????????????
            plan.setPlanStage(PlanStageEnum.EXECUTING.getKey());
            //??????
            plan.setProcessCount("0");
            plan.setResultDesc(null);
        } catch (Exception e) {
            log.error("sync.syncToExecuteTask:++++++++++++++++++++++++++++++++++++=", e);
            count++;
            plan.setPlanStage(PlanStageEnum.EXECUTED.getKey());
            plan.setPlanResult(PlanResultEnum.FAIL.getKey());
            plan.setResultDesc(e.getMessage());
            plan.setProcessCount(String.valueOf(count));
        } finally {
            syncPlanService.updateById(plan);
        }
    }

    private void syncExecuting(KafkaMessage message, List<KafkaSyncDto> list2) {

        KafkaSyncDto plan = list2.get(0);
        //???????????????????????????????????????????????????
        List<BiEtlSyncPlan> list = new ArrayList<>();
        if (list2.size() == 1) {
            list = syncPlanService.list(new LambdaQueryWrapper<BiEtlSyncPlan>()
                    .eq(BiEtlSyncPlan::getPlanType, "0")
                    .eq(BiEtlSyncPlan::getPlanStage, PlanStageEnum.EXECUTING.getKey())
                    .eq(BiEtlSyncPlan::getGroupCode, plan.getGroupCode())
                    .eq(BiEtlSyncPlan::getCode, plan.getCode())
                    .eq(BiEtlSyncPlan::getTenantId, message.getTenantId())
                    .isNull(BiEtlSyncPlan::getPlanResult)
                    .orderByAsc(BiEtlSyncPlan::getCreateDate)

            );
        } else {
            list = syncPlanService.list(new LambdaQueryWrapper<BiEtlSyncPlan>()
                    .eq(BiEtlSyncPlan::getPlanType, "0")
                    .eq(BiEtlSyncPlan::getPlanStage, PlanStageEnum.EXECUTING.getKey())
                    .eq(BiEtlSyncPlan::getGroupCode, plan.getGroupCode())
                    .eq(BiEtlSyncPlan::getTenantId, message.getTenantId())
                    .isNull(BiEtlSyncPlan::getPlanResult)
                    .orderByAsc(BiEtlSyncPlan::getCreateDate)

            );
        }

        if (!CollectionUtils.isEmpty(list)) {
            if (list2.size() == 1) {
                if (!syncExecutingTask(list.get(0), message)) {
                    message.setBeanName(MqTypeEnum.Plan_check_end.getType());
                    messageProducer.sendSyncMessage(message);
                }
            } else {
                int num = 0;
                for (BiEtlSyncPlan s : list) {
                    if (syncExecutingTask(s, message)) {
                        num = num + 1;
                    }
                }
                if (list.size() == num) {
                    message.setBeanName(MqTypeEnum.Plan_checkMany_end.getType());
                    messageProducer.sendSyncMessage(message);
                } else {
                    message.setBeanName(MqTypeEnum.Plan_check_end.getType());
                    messageProducer.sendSyncMessage(message);
                }
            }
        }


    }


    private boolean syncExecutingTask(BiEtlSyncPlan plan, KafkaMessage message) {
        int count = Integer.parseInt(plan.getProcessCount());
        BiEtlMappingConfig config = configService.getOne(new LambdaQueryWrapper<BiEtlMappingConfig>()
                .eq(BiEtlMappingConfig::getCode, plan.getRefMappingCode())
        );
        String processorsGroupId = componentService.getProcessorsGroupId(config.getRefComponentCode());
        boolean retry = false;
        try {
            if (10 < count) {
                //?????????????????????,??????10?????????????????????
                throw new RuntimeException("??????????????????");
                //???????????????????????????????????????????????? nifi????????????todo
            }
            count++;
            //???????????????????????? localCount
//            String condition = assemblyCondition(plan.getIsFirst(), config);
            long nowCount = dbHandler.getCount(config.getToTableName(), null);

            //??????????????????????????????????????????count
            String sqlCount = plan.getSqlCount();
            String localCount = String.valueOf(nowCount);
            if (Long.parseLong(localCount) < Long.parseLong(sqlCount)) {
                retry = true;
                // ?????????????????????
                plan.setSqlLocalCount(localCount);

                return false;
            } else {
                //???????????????
                plan.setPlanResult(PlanResultEnum.SUCCESS.getKey());

                //??????plan ????????????
                plan.setPlanStage(PlanStageEnum.EXECUTED.getKey());
                plan.setPlanResult(PlanResultEnum.SUCCESS.getKey());
                plan.setResultDesc(PlanResultEnum.SUCCESS.getValue());

                //????????????nifi???????????????????????????count
                nowCount = dbHandler.getCount(config.getToTableName(), null);
                plan.setSqlLocalCount(String.valueOf(nowCount));
                // ??????MappingConfig ??? LOCAL_COUNT??? OFFSET_VALUE todo
                config.setLocalCount(String.valueOf(nowCount));
//                    config.setOffsetValue();
                configService.updateById(config);

                //??????Component ???????????????
                BiComponent component = componentService.getOne(new LambdaQueryWrapper<BiComponent>()
                        .eq(BiComponent::getCode, config.getRefComponentCode())
                );
                component.setEffect(EffectEnum.ENABLE.getKey());
                componentService.updateById(component);
                return true;
            }
        } catch (Exception e) {
            log.error("sync.syncExecutingTask:", e);
            plan.setPlanStage(PlanStageEnum.EXECUTED.getKey());
            plan.setPlanResult(PlanResultEnum.FAIL.getKey());
            plan.setResultDesc(e.getMessage());
        } finally {
            plan.setProcessCount(String.valueOf(count));
            syncPlanService.updateById(plan);

            //????????????
            if (!retry) {
                try {
                    //#10002 ??????????????????
                    transfer.stop(processorsGroupId);
                } catch (Exception e1) {
                    log.error("sync.syncExecutingTask.stop NIFI:", e1);
                }
            }
        }
        return retry;
    }

    @Deprecated
    private String assemblyCondition(String isFirst, BiEtlMappingConfig config) {
        String condition = null;
        //????????????????????????
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


    private void etlToExecute(List<KafkaSyncDto> planList, KafkaMessage message) throws Exception {
        KafkaSyncDto plan = planList.get(0);
        //???????????????????????????????????????????????????s
        List<BiEtlSyncPlan> list = syncPlanService.list(new LambdaQueryWrapper<BiEtlSyncPlan>()
                .eq(BiEtlSyncPlan::getPlanType, "1")
                .eq(BiEtlSyncPlan::getPlanStage, PlanStageEnum.TO_EXECUTE.getKey())
                .eq(BiEtlSyncPlan::getGroupCode, plan.getGroupCode())
                .eq(BiEtlSyncPlan::getTenantId, message.getTenantId())
                .isNull(BiEtlSyncPlan::getPlanResult)
                .orderByAsc(BiEtlSyncPlan::getCreateDate)

        );
        for (BiEtlSyncPlan syncPlan : list) {
            etlToExecuteTask(syncPlan);
        }
    }


    private void etlToExecuteTask(BiEtlSyncPlan plan) {
        try {
            //??????????????????????????????????????????
            List<BiEtlSyncPlan> synclist = syncPlanService.list(new LambdaQueryWrapper<BiEtlSyncPlan>()
                    .eq(BiEtlSyncPlan::getPlanType, "0")
                    .eq(BiEtlSyncPlan::getGroupCode, plan.getGroupCode())
            );

            //?????????????????????????????????????????????
            if (!CollectionUtils.isEmpty(synclist)) {
                for (BiEtlSyncPlan syncPlan : synclist) {
                    if (PlanResultEnum.FAIL.getKey().equals(syncPlan.getPlanResult())) {
                        throw new BizException(ResourceMessageEnum.KAFKA_1.getCode(),
                                localeMessageService.getMessage(ResourceMessageEnum.KAFKA_1.getMessage(), ThreadLocalHolder.getLang()), syncPlan.getName());
                    }
                    //??????????????????????????????????????????????????????
                    if (null == syncPlan.getPlanResult()) {
                        return;
                    }
                }
            }

            //???????????????????????????????????????etl
            ComponentModel componentModel = modelHandleService.handleModel(plan.getRefModelCode());

            //etl?????????????????????processorsCode
            String processorsCode = plan.getRefMappingCode();
            String tableName = componentModel.getTableName();
            String query = componentModel.getQuerySql();
            String count = String.valueOf(dbHandler.getCountLocal(query));
            //??????
            dbHandler.truncateTable(tableName);
            //??????NIFI
            BiProcessors processors = processorsService.getOne(new LambdaQueryWrapper<BiProcessors>()
                    .eq(BiProcessors::getCode, processorsCode)
            );
            transfer.run(processors.getProcessGroupId());
            //??????plan ????????????
            plan.setPlanStage(PlanStageEnum.EXECUTING.getKey());
            plan.setSqlCount(count);
            //??????
            plan.setProcessCount("0");
            plan.setResultDesc(null);
        } catch (Exception e) {
            log.error("etl.etlToExecuteTask:", e);
            plan.setPlanResult(PlanResultEnum.FAIL.getKey());
            plan.setPlanStage(PlanStageEnum.EXECUTED.getKey());
            plan.setResultDesc(e.getMessage());

            //??????model???????????????
            BiEtlModel model = modelService.getOne(new LambdaQueryWrapper<BiEtlModel>().eq(BiEtlModel::getCode, plan.getRefModelCode()));
            model.setSyncStatus(YesOrNoEnum.NO.getKey());
            modelService.updateById(model);
        } finally {
            syncPlanService.updateById(plan);
        }

    }

    private void etlExecuting(List<KafkaSyncDto> planList, KafkaMessage message) {
        KafkaSyncDto plan = planList.get(0);
        //???????????????????????????????????????????????????
        List<BiEtlSyncPlan> list = syncPlanService.list(new LambdaQueryWrapper<BiEtlSyncPlan>()
                .eq(BiEtlSyncPlan::getPlanType, "1")
                .eq(BiEtlSyncPlan::getPlanStage, PlanStageEnum.EXECUTING.getKey())
                .eq(BiEtlSyncPlan::getGroupCode, plan.getGroupCode())
                .eq(BiEtlSyncPlan::getTenantId, message.getTenantId())
                .isNull(BiEtlSyncPlan::getPlanResult)
                .orderByAsc(BiEtlSyncPlan::getCreateDate)
        );
        list.forEach(s -> {
            etlExecutingTask(s, message);
        });

    }

    private void etlExecutingTask(BiEtlSyncPlan plan, KafkaMessage message) {
        int count = Integer.parseInt(plan.getProcessCount());
        ComponentModel componentModel = modelHandleService.handleModel(plan.getRefModelCode());
        String processorsCode = plan.getRefMappingCode();
        String tableName = componentModel.getTableName();

        BiEtlModel model = modelService.getOne(new LambdaQueryWrapper<BiEtlModel>()
                .eq(BiEtlModel::getCode, plan.getRefModelCode())
        );

        BiProcessors processors = processorsService.getOne(new LambdaQueryWrapper<BiProcessors>()
                .eq(BiProcessors::getCode, processorsCode)
        );

        boolean retry = false;
        try {
            count++;
            //???????????????????????? localCount
//                String condition = assemblyCondition(plan.getIsFirst(), config);
            long nowCount = dbHandler.getCount(tableName, null);

            //??????????????????????????????????????????count
            String sqlCount = plan.getSqlCount();
            String localCount = String.valueOf(nowCount);
            if (Long.parseLong(localCount) < Long.parseLong(sqlCount)) {
                retry = true;
                // ?????????????????????
                plan.setSqlLocalCount(localCount);
                message.setBeanName(MqTypeEnum.Plan_checkMany_end.getType());
                messageProducer.sendSyncMessage(message);
            } else {
                //???????????????
                plan.setPlanResult(PlanResultEnum.SUCCESS.getKey());

                //??????plan ????????????
                plan.setPlanStage(PlanStageEnum.EXECUTED.getKey());
                plan.setPlanResult(PlanResultEnum.SUCCESS.getKey());
                plan.setResultDesc(PlanResultEnum.SUCCESS.getValue());

                //????????????nifi???????????????????????????count
                nowCount = dbHandler.getCount(tableName, null);
                plan.setSqlLocalCount(String.valueOf(nowCount));

                //??????model??????????????????
                model.setSyncStatus(YesOrNoEnum.NO.getKey());
                model.setLastExecuteDate(LocalDateTime.now());
                modelService.updateById(model);
            }
        } catch (Exception e) {
            log.error("etl.etlExecutingTask:", e);
            plan.setPlanStage(PlanStageEnum.EXECUTED.getKey());
            plan.setPlanResult(PlanResultEnum.FAIL.getKey());
            plan.setResultDesc(e.getMessage());

            //??????model??????????????????
            model.setSyncStatus(YesOrNoEnum.NO.getKey());
            modelService.updateById(model);
        } finally {
            plan.setProcessCount(String.valueOf(count));
            syncPlanService.updateById(plan);

            //????????????
            if (!retry) {
                try {
                    //#10002 ??????????????????
                    transfer.stop(processors.getProcessGroupId());
                } catch (Exception e1) {
                    log.error("sync.syncExecutingTask.stop NIFI:", e1);
                }
            }
        }
    }

}
