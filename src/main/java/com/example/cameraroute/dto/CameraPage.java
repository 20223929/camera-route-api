package com.example.cameraroute.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class CameraPage {

    private final long total;
    private final int pageNum;
    private final int pageSize;
    private final List<CameraResponse> data;
}
