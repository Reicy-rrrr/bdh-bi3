package com.deloitte.bdh.data.collation.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.deloitte.bdh.common.constant.DSConstant;
import com.deloitte.bdh.data.collation.model.BiReportOut;
import com.deloitte.bdh.data.collation.dao.bi.BiReportOutMapper;
import com.deloitte.bdh.data.collation.service.BiReportOutService;
import com.deloitte.bdh.common.base.AbstractService;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author lw
 * @since 2021-01-28
 */
@Service
@DS(DSConstant.BI_DB)
public class BiReportOutServiceImpl extends AbstractService<BiReportOutMapper, BiReportOut> implements BiReportOutService {

}
