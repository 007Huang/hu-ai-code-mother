package com.huzi.huaicodemother.ai.config;

//import com.huzi.huaicodemother.monitor.AiModelMonitorListener;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import jakarta.annotation.Resource;
import lombok.Data;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;

/**
 * 通用流式聊天模型配置
 */
@Configuration
@ConfigurationProperties(prefix = "langchain4j.open-ai.streaming-chat-model")
@Data
public class StreamingChatModelConfig {

//    @Resource
//    private AiModelMonitorListener aiModelMonitorListener;
    private String baseUrl;

    private String apiKey;

    private String modelName;

    private Integer maxTokens;

    private Double temperature;

    private boolean logRequests;

    private boolean logResponses;

    @Bean(destroyMethod = "shutdown")
    public ThreadPoolTaskExecutor openAiStreamingChatModelTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(6);
        executor.setMaxPoolSize(6);
        executor.setThreadNamePrefix("my-LangChain4j-");
        executor.initialize();
        return executor;
    }

    @Bean
    @Scope("prototype")
    public StreamingChatModel streamingChatModelPrototype(
            @Qualifier("openAiStreamingChatModelHttpClientBuilder") HttpClientBuilder httpClientBuilder) {
        return OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .maxTokens(maxTokens)
                .temperature(temperature)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .httpClientBuilder(httpClientBuilder)
//                .listeners(List.of(aiModelMonitorListener))
                .build();
    }
}
