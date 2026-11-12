package com.example.cameraroute.dto;

import lombok.Data;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

@Data
public class RouteCameraRequest {
    private String type = "WGS84";
    private Integer bufferDistance;

    @NotNull(message = "geometry 不能为空")
    @Valid
    private Geometry geometry;

    @Data
    public static class Geometry {
        @NotBlank(message = "geometry.type 不能为空")
        private String type;

        @NotEmpty(message = "geometry.coordinates 不能为空")
        @Size(min = 2, max = 100000, message = "路线坐标点数量必须在 2 至 100000 之间")
        private List<List<Double>> coordinates;
    }
}
