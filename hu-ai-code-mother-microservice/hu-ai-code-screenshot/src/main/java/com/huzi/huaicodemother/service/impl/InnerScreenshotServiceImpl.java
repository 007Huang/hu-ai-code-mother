package com.huzi.huaicodemother.service.impl;

import com.huzi.huaicodemother.innerservice.InnerScreenshotService;
import com.huzi.huaicodemother.service.ScreenshotService;
import jakarta.annotation.Resource;
//import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
/**
 * 内部服务实现类，做一个中转
 */
@DubboService
//@Slf4j
public class InnerScreenshotServiceImpl implements InnerScreenshotService {

    @Resource
    private ScreenshotService screenshotService;

    @Override
    public String generateAndUploadScreenshot(String webUrl) {
        return screenshotService.generateAndUploadScreenshot(webUrl);
    }
}
