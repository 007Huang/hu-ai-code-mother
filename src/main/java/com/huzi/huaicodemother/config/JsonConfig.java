package com.huzi.huaicodemother.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.jackson.JsonComponent;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * Spring MVC Json 配置 。@JsonComponent 注解：这是 Spring Boot 提供的便捷注解，它相当于 @Component + @JsonSerializer / @JsonDeserializer 的组合。加上这个注解后，Spring Boot 会自动将这个配置类注册到全局的 Jackson 消息转换器中，无需再手动配置 WebMvcConfigurer
 */
@JsonComponent
public class JsonConfig {

    /**
     * 添加 Long 转 json 精度丢失的配置
     * 在前端（特别是 JavaScript 环境）中，数字类型是基于 IEEE 754 标准的双精度浮点数，其安全整数范围最大为 2^53 - 1（即 9007199254740991）。
     * 而 Java 中的 Long 类型最大值为 2^63 - 1（即 9223372036854775807）。
     * 当后端返回的雪花算法 ID 等 Long 型数值超过了 JS 的安全范围时，前端接收到的数据末尾几位就会变成 0，导致精度丢失。
     *这段代码的解决方案：将所有的 Long 类型在序列化为 JSON 时，强制转换为 String 字符串类型。这样前端接收到的就是字符串，完美避开了 JS 的数字精度限制。
     */
    @Bean
    public ObjectMapper jacksonObjectMapper(Jackson2ObjectMapperBuilder builder) {
        ObjectMapper objectMapper = builder.createXmlMapper(false).build();
        SimpleModule module = new SimpleModule();
        module.addSerializer(Long.class, ToStringSerializer.instance);//这两行确保了无论实体类中使用的是 Long 还是 long，都会被序列化为字符串。针对包装类 Long 生效。
        module.addSerializer(Long.TYPE, ToStringSerializer.instance);//针对基本数据类型 long 生
        objectMapper.registerModule(module);
        return objectMapper;
    }
}
