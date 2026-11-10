package com.example.cameraroute.service;

import com.example.cameraroute.dto.CameraResponse;
import com.example.cameraroute.dto.RouteCameraRequest;
import com.example.cameraroute.geo.CoordinateConverter;
import com.example.cameraroute.geo.RouteSegmenter;
import com.example.cameraroute.repository.CameraRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CameraRouteService {

    private final CameraRepository repository;

    @Value("${camera.search.default-buffer-meters:100}")
    private int defaultBufferMeters;

    @Value("${camera.search.max-buffer-meters:10000}")
    private int maxBufferMeters;

    @Value("${camera.search.segment-length-meters:10000}")
    private double segmentLengthMeters;

    public List<CameraResponse> findCameras(RouteCameraRequest request) {
        PreparedRoute prepared = prepare(request);
        return repository.findCamerasNearRoute(
                prepared.segmentWkts,
                prepared.bufferMeters
        );
    }

    public double getRouteLengthMeters(RouteCameraRequest request) {
        return prepare(request).lengthMeters;
    }

    private PreparedRoute prepare(RouteCameraRequest request) {
        validateCoordinates(request);

        String type = request.getType() == null || request.getType().trim().isEmpty()
                ? "WGS84"
                : request.getType().trim().toUpperCase(Locale.ROOT);

        if ("GCJ_02".equals(type) || "GCJ02".equals(type)) {
            type = "GCJ-02";
        }

        if (!"WGS84".equals(type) && !"GCJ-02".equals(type)) {
            throw new IllegalArgumentException("type 仅支持 WGS84 或 GCJ-02");
        }

        int buffer = request.getBufferDistance() == null
                ? defaultBufferMeters
                : request.getBufferDistance();

        if (buffer < 0 || buffer > maxBufferMeters) {
            throw new IllegalArgumentException(
                    "bufferDistance 必须在 0 至 " + maxBufferMeters + " 米之间"
            );
        }

        List<CoordinateConverter.Point> wgs84 =
                new ArrayList<CoordinateConverter.Point>();

        for (RouteCameraRequest.CoordinatePoint point : request.getCoordinates()) {
            wgs84.add(
                    CoordinateConverter.toWgs84(
                            point.getLongitude(),
                            point.getLatitude(),
                            type
                    )
            );
        }

        double length = RouteSegmenter.routeLengthMeters(wgs84);

        if (length > 5000000) {
            throw new IllegalArgumentException(
                    "路线长度超过 5000 公里，请拆分路线后查询"
            );
        }

        List<String> segments =
                RouteSegmenter.toWktSegments(wgs84, segmentLengthMeters);

        if (segments.size() > 500) {
            throw new IllegalArgumentException(
                    "路线分段数量超过限制，请拆分路线后查询"
            );
        }

        return new PreparedRoute(segments, buffer, length);
    }

    private void validateCoordinates(RouteCameraRequest request) {
        if (request == null
                || request.getCoordinates() == null
                || request.getCoordinates().size() < 2) {
            throw new IllegalArgumentException("路线至少需要两个坐标点");
        }

        for (RouteCameraRequest.CoordinatePoint point : request.getCoordinates()) {
            if (point == null
                    || point.getLongitude() == null
                    || point.getLatitude() == null) {
                throw new IllegalArgumentException("路线坐标不能为 null");
            }

            double longitude = point.getLongitude();
            double latitude = point.getLatitude();

            if (!Double.isFinite(longitude)
                    || !Double.isFinite(latitude)
                    || longitude < -180
                    || longitude > 180
                    || latitude < -90
                    || latitude > 90) {
                throw new IllegalArgumentException("路线坐标超出经纬度有效范围");
            }
        }
    }

    private static final class PreparedRoute {

        private final List<String> segmentWkts;
        private final int bufferMeters;
        private final double lengthMeters;

        private PreparedRoute(
                List<String> segmentWkts,
                int bufferMeters,
                double lengthMeters
        ) {
            this.segmentWkts = segmentWkts;
            this.bufferMeters = bufferMeters;
            this.lengthMeters = lengthMeters;
        }
    }
}
