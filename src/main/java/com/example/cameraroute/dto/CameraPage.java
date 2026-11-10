package com.example.cameraroute.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class CameraPage {

    private final long total;
    private final List<CameraResponse> data;
}
