package com.example.cameraroute.dto;

import lombok.Getter;

import java.util.Collections;
import java.util.List;

/** 通用分页响应结果。 */
@Getter
public class PageResult<T> {
    private final long total;
    private final int pageNum;
    private final int pageSize;
    private final int currentCount;
    private final long totalPages;
    private final List<T> data;

    private PageResult(
            long total,
            int pageNum,
            int pageSize,
            int currentCount,
            long totalPages,
            List<T> data
    ) {
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.currentCount = currentCount;
        this.totalPages = totalPages;
        this.data = data;
    }

    public static <T> PageResult<T> of(long total, int pageNum, int pageSize, List<T> data) {
        if (total < 0) {
            throw new IllegalArgumentException("total 不能小于 0");
        }
        if (pageNum < 1) {
            throw new IllegalArgumentException("pageNum 必须大于等于 1");
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize 必须大于等于 1");
        }

        List<T> safeData = data == null ? Collections.<T>emptyList() : data;
        long totalPages = total / pageSize + (total % pageSize == 0 ? 0 : 1);
        return new PageResult<T>(
                total, pageNum, pageSize, safeData.size(), totalPages, safeData
        );
    }

    public static <T> PageResult<T> empty(int pageNum, int pageSize) {
        return of(0L, pageNum, pageSize, Collections.<T>emptyList());
    }
}
