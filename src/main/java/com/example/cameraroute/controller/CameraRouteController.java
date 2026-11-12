package com.example.cameraroute.controller;

import com.example.cameraroute.dto.PageResult;
import com.example.cameraroute.dto.CameraResponse;
import com.example.cameraroute.dto.RouteCameraRequest;
import com.example.cameraroute.service.CameraRouteService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/camera")
@Validated
@RequiredArgsConstructor
public class CameraRouteController {

    private final CameraRouteService service;

    @PostMapping("/route/list")
    public PageResult<CameraResponse> getRouteCameras(
            @Valid @RequestBody RouteCameraRequest request,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "500") int pageSize
    ) {
        return service.findCameras(request, pageNum, pageSize);
    }
}
