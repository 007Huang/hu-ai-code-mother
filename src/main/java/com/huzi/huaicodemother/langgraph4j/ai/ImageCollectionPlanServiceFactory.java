package com.huzi.huaicodemother.langgraph4j.ai;

import com.huzi.huaicodemother.langgraph4j.tools.ImageSearchTool;
import com.huzi.huaicodemother.langgraph4j.tools.LogoGeneratorTool;
import com.huzi.huaicodemother.langgraph4j.tools.MermaidDiagramTool;
import com.huzi.huaicodemother.langgraph4j.tools.UndrawIllustrationTool;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
/**
 * 图片收集规划服务工厂
 */
@Configuration
public class ImageCollectionPlanServiceFactory {

    @Resource(name = "openAiChatModel")
    private ChatModel chatModel;

    @Bean
    public ImageCollectionPlanService createImageCollectionPlanService() {
        return AiServices.builder(ImageCollectionPlanService.class)
                .chatModel(chatModel)
                .build();
    }
}