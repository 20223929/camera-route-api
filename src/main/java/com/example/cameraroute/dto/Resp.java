package com.example.cameraroute.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 统一 API 响应结构。
 *
 * @param <T> 业务数据类型
 */
@Getter
@AllArgsConstructor
public class Resp<T> {

    private final int code;
    private final String msg;
    private final T data;

    public static <T> Resp<T> success(T data) {
        return new Resp<T>(200, "success", data);
    }

    public static <T> Resp<T> success(String msg, T data) {
        return new Resp<T>(200, msg, data);
    }

    public static <T> Resp<T> failure(int code, String msg) {
        return new Resp<T>(code, msg, null);
    }
}
