package com.huzi.huaicodemother.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/** 当前有效应用计数及创建配额，不包含其他用户信息。 */
@Data
public class AppQuotaVO implements Serializable {

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer usedCount;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer limitCount;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer remainingCount;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean canCreate;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean overLimit;
    @Schema(nullable = true)
    private Integer appLimit;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer defaultLimit;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean usesDefault;

    private static final long serialVersionUID = 1L;
}
