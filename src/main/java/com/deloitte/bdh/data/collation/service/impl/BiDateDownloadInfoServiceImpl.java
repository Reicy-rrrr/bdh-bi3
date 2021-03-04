package com.deloitte.bdh.data.collation.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.deloitte.bdh.common.constant.DSConstant;
import com.deloitte.bdh.common.date.DateUtils;
import com.deloitte.bdh.common.util.AliyunOssUtil;
import com.deloitte.bdh.common.util.ExcelUtils;
import com.deloitte.bdh.common.util.ThreadLocalHolder;
import com.deloitte.bdh.common.util.ZipUtil;
import com.deloitte.bdh.data.analyse.constants.AnalyseConstants;
import com.deloitte.bdh.data.collation.database.po.TableColumn;
import com.deloitte.bdh.data.collation.database.po.TableData;
import com.deloitte.bdh.data.collation.enums.DownLoadTStatusEnum;
import com.deloitte.bdh.data.collation.model.BiDataSet;
import com.deloitte.bdh.data.collation.model.BiDateDownloadInfo;
import com.deloitte.bdh.data.collation.dao.bi.BiDateDownloadInfoMapper;
import com.deloitte.bdh.data.collation.service.BiDataSetService;
import com.deloitte.bdh.data.collation.service.BiDateDownloadInfoService;
import com.deloitte.bdh.common.base.AbstractService;
import com.google.common.collect.Lists;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author lw
 * @since 2021-03-02
 */
@Service
@DS(DSConstant.BI_DB)
public class BiDateDownloadInfoServiceImpl extends AbstractService<BiDateDownloadInfoMapper, BiDateDownloadInfo> implements BiDateDownloadInfoService {
    @Resource
    private BiDateDownloadInfoMapper dateDownloadInfoMapper;
    @Autowired
    private AliyunOssUtil aliyunOss;
    @Resource
    private BiDataSetService dataSetService;

    @Override
    public void export(BiDateDownloadInfo info, BiDataSet dataSet) throws Exception {
        String zipFilePath = "/usr/local/t_" + ThreadLocalHolder.getTenantCode()
                + "/" + DateUtils.formatShortDate(new Date()) + "/" + dataSet.getCode() + "/";
        List<TableColumn> columns = dataSetService.getColumns(dataSet.getCode());
        Integer page = 1;
        Integer pageSize = 60000;
        boolean more;
        List<String> zipFiles = Lists.newArrayList();
        try {
            do {
                String fileName = dataSet.getTableDesc() + "_" + System.currentTimeMillis() + ".xls";
                TableData data = dataSetService.getDataInfoPage(dataSet, page, pageSize);
                more = data.isMore();
                page++;
                List<Map<String, Object>> list = data.getRows();
                InputStream inputStream = ExcelUtils.export(list, columns);
                ExcelUtils.create(zipFilePath, fileName, inputStream);
                zipFiles.add(zipFilePath + fileName);
            } while (more);
        } catch (Exception e) {
            log.error("压缩文件错误：", e);
        }

        //压缩
        if (CollectionUtils.isEmpty(zipFiles)) {
            info.setStatus(DownLoadTStatusEnum.FAIL.getKey());
        } else {
            String zipFileName = info.getName() + System.currentTimeMillis() + ".zip";
            boolean success = ZipUtil.toZip(zipFilePath + zipFileName, zipFiles);
            if (!success) {
                info.setStatus(DownLoadTStatusEnum.FAIL.getKey());
            } else {
                String filePath = AnalyseConstants.DOCUMENT_DIR + ThreadLocalHolder.getTenantCode() + "/bi/dataset/";
                String storedFileKey = aliyunOss.uploadFile2OSS(new FileInputStream(new File(zipFilePath + zipFileName)), filePath, zipFileName);
                info.setFileName(zipFileName);
                info.setPath(filePath);
                info.setStoreFileKey(storedFileKey);
                info.setStatus(DownLoadTStatusEnum.SUCCESS.getKey());
            }
        }

        //生成excel 再更新状态
        dateDownloadInfoMapper.updateById(info);

        //删除压缩的文件夹
        File file = new File(zipFilePath);
        ZipUtil.deleteFolder(file);
    }

    @Override
    public String downLoad(String id) {
        BiDateDownloadInfo info = dateDownloadInfoMapper.selectById(id);
        if (null == info) {
            throw new RuntimeException("下载失败:未找到该条导出记录");
        }
        if (DownLoadTStatusEnum.ING.getKey().equalsIgnoreCase(info.getStatus())) {
            throw new RuntimeException("下载失败:当前数据正在生成种");
        }
        if (DownLoadTStatusEnum.FAIL.getKey().equalsIgnoreCase(info.getStatus())) {
            throw new RuntimeException("下载失败:文件生成失败,请重新生成");
        }
        String url = aliyunOss.getImgUrl(info.getPath(), info.getFileName());
        return url;
    }


}
