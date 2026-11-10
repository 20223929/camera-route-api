package com.example.cameraroute.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ApiResponse<T> {

    private int code;
    private String message;
    private long total;
    private T data;

    public static <T> ApiResponse<T> success(T data, long total) {
        return new ApiResponse<T>(200, "success", total, data);
    }
}
