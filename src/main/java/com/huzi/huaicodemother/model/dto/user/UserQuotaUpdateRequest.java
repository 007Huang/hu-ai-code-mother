package com.huzi.huaicodemother.model.dto.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/** 管理员设置用户配额；显式 null 表示恢复默认配额。 */
@Getter
public class UserQuotaUpdateRequest implements Serializable {

    @Setter
    @Schema(type = "string", example = "1234567890123456789")
    private Long id;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, minimum = "0", maximum = "10000")
    @JsonDeserialize(using = AppLimitDeserializer.class)
    private Integer appLimit;

    @JsonIgnore
    @Schema(hidden = true)
    private boolean appLimitPresent;

    @JsonSetter("appLimit")
    public void setAppLimit(Integer appLimit) {
        this.appLimit = appLimit;
        this.appLimitPresent = true;
    }

    private static final long serialVersionUID = 1L;
}
