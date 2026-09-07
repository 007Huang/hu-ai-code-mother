package com.huzi.huaicodeuser;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
//@SpringBootApplication(scanBasePackages = {"com.huzi"})
@SpringBootApplication
@EnableDubbo
@MapperScan("com.huzi.huaicodeuser.mapper")
@ComponentScan("com.huzi")
//@EnableDubbo
public class HuAiCodeUserApplication {
    public static void main(String[] args) {
        SpringApplication.run(HuAiCodeUserApplication.class, args);
    }
}

//@SpringBootApplication
//@MapperScan("com.yupi.yuaicodeuser.mapper")
//@ComponentScan("com.yupi")
//@EnableDubbo
//public class YuAiCodeUserApplication {
//    public static void main(String[] args) {
//        SpringApplication.run(YuAiCodeUserApplication.class, args);
//    }
//}
