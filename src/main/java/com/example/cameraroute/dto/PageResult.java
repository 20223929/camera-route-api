package com.example.cameraroute.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

/** 通用分页响应结果。 */
@Getter
@AllArgsConstructor
public class PageResult<T> {
    private final long total;
    private final int pageNum;
    private final int pageSize;
    private final int currentCount;
    private final long totalPages;
    private final List<T> data;

    public static <T> PageResult<T> of(long total, int pageNum, int pageSize, List<T> data) {
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize 必须大于等于 1");
        }
        long totalPages = total / pageSize + (total % pageSize == 0 ? 0 : 1);
        int currentCount = data == null ? 0 : data.size();
        return new PageResult<T>(total, pageNum, pageSize, currentCount, totalPages, data);
    }

    public static <T> PageResult<T> empty(int pageNum, int pageSize) {
        return of(0L, pageNum, pageSize, Collections.<T>emptyList());
    }
}
