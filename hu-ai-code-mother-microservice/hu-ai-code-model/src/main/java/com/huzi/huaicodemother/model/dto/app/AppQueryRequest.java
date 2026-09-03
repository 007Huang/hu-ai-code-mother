package com.huzi.huaicodemother.model.dto.app;

import com.huzi.huaicodemother.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 应用查询请求（用户用）
 *
 * @author <a href="https://github.com/007Huang">hzh</a>
 * @since 2026-07-19
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class AppQueryRequest extends PageRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 应用名称
     */
    private String appName;

    /**
     * 应用封面
     */
    private String cover;

    /**
     * 应用初始化的 prompt
     */
    private String initPrompt;

    /**
     * 代码生成类型（枚举）
     */
    private String codeGenType;

    /**
     * 部署标识
     */
    private String deployKey;

    /**
     * 优先级
     */
    private Integer priority;

    /**
     * 创建用户id
     */
    private Long userId;

    private static final long serialVersionUID = 1L;
}

//@EqualsAndHashCode(callSuper = true)
//@Data
//public class AppQueryRequest extends PageRequest implements Serializable {
//
//    /**
//     * 应用名称（模糊查询）
//     */
//    private String appName;
//
//    private static final long serialVersionUID = 1L;
//}
