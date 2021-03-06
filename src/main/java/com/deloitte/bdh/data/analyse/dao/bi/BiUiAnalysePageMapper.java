package com.deloitte.bdh.data.analyse.dao.bi;

import com.deloitte.bdh.common.base.Mapper;
import com.deloitte.bdh.data.analyse.model.BiUiAnalysePage;
import com.deloitte.bdh.data.analyse.model.request.SelectPublishedPageDto;
import com.deloitte.bdh.data.analyse.model.resp.AnalysePageDto;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author lw
 * @since 2021-04-01
 */
public interface BiUiAnalysePageMapper extends Mapper<BiUiAnalysePage> {

    List<AnalysePageDto> selectPublishedPage(@Param("queryDto") SelectPublishedPageDto queryDto);

    List<AnalysePageDto> getPageWithChildren(@Param("rootPageId") String pageId);

}
