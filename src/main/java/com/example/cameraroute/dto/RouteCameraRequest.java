package com.example.cameraroute.dto;

import lombok.Data;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

@Data
public class RouteCameraRequest {
    private String type = "WGS84";
    private Integer bufferDistance;

    @NotEmpty(message = "coordinates 不能为空")
    @Size(min = 2, max = 100000, message = "路线坐标点数量必须在 2 至 100000 之间")
    @Valid
    private List<CoordinatePoint> coordinates;

    @Data
    public static class CoordinatePoint {
        @NotNull(message = "经度 longitude 不能为空")
        private Double longitude;
        @NotNull(message = "纬度 latitude 不能为空")
        private Double latitude;
    }
}
