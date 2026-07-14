package com.huzi.huaicodemother;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.huzi.huaicodemother.mapper")
public class HuAiCodeMotherApplication {

    public static void main(String[] args) {
        SpringApplication.run(HuAiCodeMotherApplication.class, args);
    }

}
//hu-ai-code-mother-frontend