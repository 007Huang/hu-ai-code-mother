package com.huzi.huaicodemother;

import dev.langchain4j.community.store.embedding.redis.spring.RedisEmbeddingStoreAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude =  {RedisEmbeddingStoreAutoConfiguration.class})
@MapperScan("com.huzi.huaicodemother.mapper")
public class HuAiCodeMotherApplication {

    public static void main(String[] args) {
        SpringApplication.run(HuAiCodeMotherApplication.class, args);
    }

}
//hu-ai-code-mother-frontend