package com.huzi.huaicodemother.ai;

import com.huzi.huaicodemother.ai.model.HtmlCodeResult;
import com.huzi.huaicodemother.ai.model.MultiFileCodeResult;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
class AiCodeGeneratorServiceTest {
    @Resource
    private AiCodeGeneratorService aiCodeGeneratorService;
    @Test
    void generateHtmlCode() {
         HtmlCodeResult result = aiCodeGeneratorService.generateHtmlCode("做个个人博客，不超过50行");
        Assertions.assertNotNull( result);
    }

    @Test
    void generateMultiFileCode() {
        MultiFileCodeResult result = aiCodeGeneratorService.generateMultiFileCode("做个个人留言板，不超过50行");
        Assertions.assertNotNull( result);
    }
}