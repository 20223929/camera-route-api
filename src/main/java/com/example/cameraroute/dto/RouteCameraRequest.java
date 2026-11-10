package com.example.cameraroute.dto;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

public class RouteCameraRequest {
    private String routeId;

    /** WGS84 or GCJ-02. Defaults to WGS84 for backward compatibility. */
    private String type = "WGS84";
    private Integer bufferDistance;

    @NotEmpty(message = "coordinates 不能为空")
    @Size(min = 2, max = 100000, message = "路线坐标点数量必须在 2 至 100000 之间")
    @Valid
    private List<CoordinatePoint> coordinates;

    public String getRouteId() { return routeId; }
    public void setRouteId(String routeId) { this.routeId = routeId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Integer getBufferDistance() { return bufferDistance; }
    public void setBufferDistance(Integer bufferDistance) { this.bufferDistance = bufferDistance; }
    public List<CoordinatePoint> getCoordinates() { return coordinates; }
    public void setCoordinates(List<CoordinatePoint> coordinates) { this.coordinates = coordinates; }

    public static class CoordinatePoint {
        @NotNull(message = "经度 longitude 不能为空")
        private Double longitude;
        @NotNull(message = "纬度 latitude 不能为空")
        private Double latitude;
        public Double getLongitude() { return longitude; }
        public void setLongitude(Double longitude) { this.longitude = longitude; }
        public Double getLatitude() { return latitude; }
        public void setLatitude(Double latitude) { this.latitude = latitude; }
    }
}
