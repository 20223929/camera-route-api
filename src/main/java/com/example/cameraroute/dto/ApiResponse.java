package com.example.cameraroute.dto;

import lombok.Getter;

import java.util.Collection;

@Getter
public class ApiResponse<T> {

    private int code;
    private String message;
    private long total;
    private int pageNum;
    private int pageSize;
    private int currentCount;
    private long totalPages;
    private T data;

    /**
     * Creates a response without pagination metadata, for validation and server errors.
     */
    public ApiResponse(int code, String message, long total, T data) {
        this(code, message, total, 0, 0, 0, 0, data);
    }

    private ApiResponse(
            int code,
            String message,
            long total,
            int pageNum,
            int pageSize,
            int currentCount,
            long totalPages,
            T data
    ) {
        this.code = code;
        this.message = message;
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.currentCount = currentCount;
        this.totalPages = totalPages;
        this.data = data;
    }

    public static <T> ApiResponse<T> success(
            T data,
            long total,
            int pageNum,
            int pageSize
    ) {
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize 必须大于等于 1");
        }
        long totalPages = total / pageSize
                + (total % pageSize == 0 ? 0 : 1);
        int currentCount = data instanceof Collection
                ? ((Collection<?>) data).size() : 0;
        return new ApiResponse<T>(
                200, "success", total, pageNum, pageSize,
                currentCount, totalPages, data
        );
    }
}
