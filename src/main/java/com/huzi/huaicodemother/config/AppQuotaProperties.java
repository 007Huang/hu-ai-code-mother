package com.huzi.huaicodemother.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 用户应用配额默认值，在启动时检查配置范围。 */
@Component
@ConfigurationProperties(prefix = "app.quota")
@Getter
@Setter
public class AppQuotaProperties {

    public static final int MAX_LIMIT = 10000;

    private int defaultLimit = 10;

    @PostConstruct
    public void validate() {
        if (defaultLimit < 0 || defaultLimit > MAX_LIMIT) {
            throw new IllegalArgumentException("app.quota.default-limit 必须在 0 到 10000 之间");
        }
    }
}
