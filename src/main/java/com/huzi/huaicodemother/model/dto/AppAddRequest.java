package com.huzi.huaicodemother.model.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 应用创建请求
 *
 * @author <a href="https://github.com/007Huang">hzh</a>
 * @since 2026-07-19
 */


@Data
public class AppAddRequest implements Serializable {

    /**
     * 应用初始化的 prompt
     */
    private String initPrompt;

    private static final long serialVersionUID = 1L;
}
//@Data
//public class AppAddRequest implements Serializable {
//
//    /**
//     * 应用名称
//     */
//    private String appName;
//
//    /**
//     * 应用封面
//     */
//    private String cover;
//
//    /**
//     * 应用初始化的 prompt（必填）
//     */
//    private String initPrompt;
//
//    /**
//     * 代码生成类型（枚举）
//     */
//    private String codeGenType;
//
//    private static final long serialVersionUID = 1L;
//}
