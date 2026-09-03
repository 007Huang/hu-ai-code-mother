package com.huzi.huaicodemother.common;

import lombok.Data;

import java.io.Serializable;
/**
 * 删除请求
 * @date 2026/7/9
 */
@Data
public class DeleteRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    private static final long serialVersionUID = 1L;
}
