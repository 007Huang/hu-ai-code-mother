package com.huzi.huaicodemother.model.dto.app;

import lombok.Data;

import java.io.Serializable;

/**
 * 应用更新请求（用户修改自己的应用）
 *
 * @author <a href="https://github.com/007Huang">hzh</a>
 * @since 2026-07-19
 */


@Data
public class AppUpdateRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 应用名称
     */
    private String appName;

    private static final long serialVersionUID = 1L;
}

//@Data
//public class AppUpdateRequest implements Serializable {
//
//    /**
//     * 应用 id
//     */
//    private Long id;
//
//    /**
//     * 应用名称
//     */
//    private String appName;
//
//    private static final long serialVersionUID = 1L;
//}
