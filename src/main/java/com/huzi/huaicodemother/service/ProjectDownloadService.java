package com.huzi.huaicodemother.service;

import jakarta.servlet.http.HttpServletResponse;

public interface ProjectDownloadService {
    /**
     * 下载项目为压缩包
     * @param projectName 项目名
     * @param downloadFileName 下载文件名
     * @param response 响应
     */
    void downloadProjectAsZip(String projectName, String downloadFileName, HttpServletResponse response);
}
