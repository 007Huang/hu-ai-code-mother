package com.huzi.huaicodemother.common;

import com.huzi.huaicodemother.exception.ErrorCode;
import lombok.Data;

import java.io.Serializable;
/**
 * 通用返回类
 *
 * @param <T>
 * @Date: 2026/7/9
 */
@Data
public class BaseResponse<T> implements Serializable {

    private int code;

    private T data;//范型

    private String message;

    public BaseResponse(int code, T data, String message) {
        this.code = code;
        this.data = data;
        this.message = message;
    }

    public BaseResponse(int code, T data) {
        this(code, data, "");
    }

    public BaseResponse(ErrorCode errorCode) {
        this(errorCode.getCode(), null, errorCode.getMessage());
    }
}
