package com.example.cameraroute.service;

import com.example.cameraroute.dto.CameraPage;
import com.example.cameraroute.dto.RouteCameraRequest;
import com.example.cameraroute.geo.CoordinateConverter;
import com.example.cameraroute.geo.RouteSegmenter;
import com.example.cameraroute.repository.CameraRepository;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class CameraRouteService {

    private final CameraRepository repository;

    @org.springframework.beans.factory.annotation.Value("${camera.search.default-buffer-meters:100}")
    private int defaultBufferMeters;

    @org.springframework.beans.factory.annotation.Value("${camera.search.max-buffer-meters:10000}")
    private int maxBufferMeters;

    @org.springframework.beans.factory.annotation.Value("${camera.search.segment-length-meters:10000}")
    private double segmentLengthMeters;

    @org.springframework.beans.factory.annotation.Value("${camera.search.max-route-length-meters:5000000}")
    private double maxRouteLengthMeters;

    public CameraPage findCameras(
            RouteCameraRequest request,
            int pageNum,
            int pageSize
    ) {
        long startedAt = System.nanoTime();
        int coordinateCount = request == null || request.getCoordinates() == null
                ? 0 : request.getCoordinates().size();
        String coordinateType = request == null ? null : request.getType();

        log.info(
                "event=route.camera.query.started coordinateType={} coordinateCount={} pageNum={} pageSize={}",
                coordinateType, coordinateCount, pageNum, pageSize
        );

        validatePage(pageNum, pageSize);
        PreparedRoute prepared = prepare(request);

        log.info(
                "event=route.camera.query.prepared coordinateType={} coordinateCount={} "
                        + "routeLengthMeters={} segmentCount={} bufferMeters={} pageNum={} pageSize={}",
                coordinateType, coordinateCount, prepared.getRouteLengthMeters(),
                prepared.getSegmentWkts().size(), prepared.getBufferMeters(), pageNum, pageSize
        );

        CameraPage page = repository.findCamerasNearRoute(
                prepared.getSegmentWkts(),
                prepared.getBufferMeters(),
                pageNum,
                pageSize
        );

        log.info(
                "event=route.camera.query.completed resultCount={} total={} pageNum={} pageSize={} durationMs={}",
                page.getData().size(), page.getTotal(), pageNum, pageSize,
                (System.nanoTime() - startedAt) / 1_000_000L
        );
        return page;
    }

    private PreparedRoute prepare(RouteCameraRequest request) {
        validateCoordinates(request);

        String type = request.getType() == null || request.getType().trim().isEmpty()
                ? "WGS84" : request.getType().trim().toUpperCase(Locale.ROOT);
        if ("GCJ_02".equals(type) || "GCJ02".equals(type)) {
            type = "GCJ-02";
        }
        if (!"WGS84".equals(type) && !"GCJ-02".equals(type)) {
            throw new IllegalArgumentException("type 仅支持 WGS84 或 GCJ-02");
        }

        int buffer = request.getBufferDistance() == null
                ? defaultBufferMeters : request.getBufferDistance();
        if (buffer < 0 || buffer > maxBufferMeters) {
            throw new IllegalArgumentException(
                    "bufferDistance 必须在 0 至 " + maxBufferMeters + " 米之间"
            );
        }

        List<CoordinateConverter.Point> wgs84 =
                new ArrayList<CoordinateConverter.Point>();
        for (RouteCameraRequest.CoordinatePoint point : request.getCoordinates()) {
            wgs84.add(CoordinateConverter.toWgs84(
                    point.getLongitude(), point.getLatitude(), type
            ));
        }

        double length = RouteSegmenter.routeLengthMeters(wgs84);
        if (length > maxRouteLengthMeters) {
            throw new IllegalArgumentException(
                    "路线长度超过 " + Math.round(maxRouteLengthMeters / 1000.0)
                            + " 公里，请拆分路线后查询"
            );
        }

        List<String> segments = RouteSegmenter.toWktSegments(
                wgs84, segmentLengthMeters
        );
        if (segments.size() > 500) {
            throw new IllegalArgumentException(
                    "路线分段数量超过 500，请拆分路线后查询"
            );
        }

        return new PreparedRoute(segments, buffer, length);
    }

    private void validatePage(int pageNum, int pageSize) {
        if (pageNum < 1) {
            throw new IllegalArgumentException("pageNum 必须大于等于 1");
        }
        if (pageSize < 1 || pageSize > 1000) {
            throw new IllegalArgumentException("pageSize 必须在 1 至 1000 之间");
        }
    }

    private void validateCoordinates(RouteCameraRequest request) {
        if (request == null || request.getCoordinates() == null
                || request.getCoordinates().size() < 2) {
            throw new IllegalArgumentException("路线至少需要两个坐标点");
        }

        for (RouteCameraRequest.CoordinatePoint point : request.getCoordinates()) {
            if (point == null || point.getLongitude() == null
                    || point.getLatitude() == null) {
                throw new IllegalArgumentException("路线坐标不能为 null");
            }

            double longitude = point.getLongitude();
            double latitude = point.getLatitude();
            if (!Double.isFinite(longitude) || !Double.isFinite(latitude)
                    || longitude < -180 || longitude > 180
                    || latitude < -90 || latitude > 90) {
                throw new IllegalArgumentException("路线坐标超出经纬度有效范围");
            }
        }
    }

    @Value
    private static class PreparedRoute {
        List<String> segmentWkts;
        int bufferMeters;
        double routeLengthMeters;
    }
}
