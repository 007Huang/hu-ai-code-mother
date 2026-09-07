package com.huzi.huaicodemother.service;
/**
 * 截图服务
 *
 */
public interface ScreenshotService {
    /**
     * 通用的截图服务，可以得到访问网址
     *
     * @param webUrl 网页地址
     * @return 截图上传结果
     */
    String generateAndUploadScreenshot(String webUrl);
}
