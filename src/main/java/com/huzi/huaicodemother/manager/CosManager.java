package com.huzi.huaicodemother.manager;

import com.huzi.huaicodemother.config.CosClientConfig;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
/**
 * 腾讯云COS对象存储管理器，上传工具类，
 *
 */
@Component
@Slf4j
public class CosManager {
    @Resource
    private CosClientConfig cosClientConfig;
    @Resource
    private COSClient cosClient;

    /**
     * 上传对象
     *
     * @param key  唯一键
     * @param file 文件
     * @return 上传结果
     */
    public PutObjectResult putObject(String key, File  file) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key, file);
        return cosClient.putObject(putObjectRequest);
    }
    public String uploadFile(String key, File file) {
        PutObjectResult result = putObject(key, file);
        if(result != null){
            String url =String.format("%s%s",cosClientConfig.getHost(),key);
            log.info("文件上传到COS成功：{} -> {}",file.getName(), url);
            return url;
        }else {
            log.error("文件上传到COS失败：{},返回结果为空",file.getName());
            return null;
        }
    }
}
