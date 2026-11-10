package com.example.cameraroute.dto;

public class ApiResponse<T> {

    private int code;
    private String message;
    private long total;
    private T data;

    public ApiResponse(int code, String message, long total, T data) {
        this.code = code;
        this.message = message;
        this.total = total;
        this.data = data;
    }

    public static <T> ApiResponse<T> success(T data, long total) {
        return new ApiResponse<T>(200, "success", total, data);
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public long getTotal() {
        return total;
    }

    public T getData() {
        return data;
    }
}
