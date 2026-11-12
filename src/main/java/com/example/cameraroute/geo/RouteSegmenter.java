package com.example.cameraroute.geo;

import lombok.Value;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Splits a route into overlapping-endpoint LineStrings no longer than the configured length. */
public final class RouteSegmenter {
    private static final double EARTH_RADIUS_METERS = 6371008.8;
    private static final double EPSILON_METERS = 1e-6;
    private static final int MAX_INITIAL_WKT_CAPACITY = 1_048_576;

    private RouteSegmenter() {
    }

    /** Compatibility helper. Prefer segment() when both length and WKT are required. */
    public static double routeLengthMeters(List<CoordinateConverter.Point> points) {
        validatePoints(points);
        double total = 0;
        for (int i = 1; i < points.size(); i++) {
            total += distance(points.get(i - 1), points.get(i));
        }
        return total;
    }

    public static List<String> toWktSegments(
            List<CoordinateConverter.Point> points, double maxMeters
    ) {
        return segment(points, maxMeters, Double.POSITIVE_INFINITY, Integer.MAX_VALUE)
                .getSegmentWkts();
    }

    /** Computes route length and segments in one pass, enforcing both limits immediately. */
    public static SegmentationResult segment(
            List<CoordinateConverter.Point> points,
            double maxMeters,
            double maxRouteLengthMeters,
            int maxSegments
    ) {
        validatePoints(points);
        if (!Double.isFinite(maxMeters) || maxMeters <= 0) {
            throw new IllegalArgumentException("分段长度必须是有限的正数");
        }
        if (Double.isNaN(maxRouteLengthMeters) || maxRouteLengthMeters <= 0) {
            throw new IllegalArgumentException("路线最大长度必须是正数");
        }
        if (maxSegments < 1) {
            throw new IllegalArgumentException("最大分段数量必须大于 0");
        }

        List<String> result = new ArrayList<String>(Math.min(maxSegments, 16));
        List<CoordinateConverter.Point> current = new ArrayList<CoordinateConverter.Point>();
        current.add(points.get(0));
        double currentLength = 0;
        double totalLength = 0;

        for (int i = 1; i < points.size(); i++) {
            CoordinateConverter.Point start = points.get(i - 1);
            CoordinateConverter.Point end = points.get(i);
            double edgeLength = distance(start, end);
            totalLength += edgeLength;
            if (totalLength > maxRouteLengthMeters) {
                throw new IllegalArgumentException("路线长度超过 " + Math.round(maxRouteLengthMeters / 1000.0)
                        + " 公里，请拆分路线后查询");
            }
            if (edgeLength == 0) continue;

            if (currentLength >= maxMeters - EPSILON_METERS && current.size() >= 2) {
                addSegment(result, current, maxSegments);
                current = new ArrayList<CoordinateConverter.Point>();
                current.add(start);
                currentLength = 0;
            }

            double consumed = 0;
            while (consumed < edgeLength - EPSILON_METERS) {
                double remainingCapacity = maxMeters - currentLength;
                double remainingEdge = edgeLength - consumed;
                if (remainingEdge <= remainingCapacity + EPSILON_METERS) {
                    current.add(end);
                    currentLength += remainingEdge;
                    consumed = edgeLength;
                } else {
                    double nextConsumed = consumed + remainingCapacity;
                    CoordinateConverter.Point split = interpolate(
                            start, end, nextConsumed / edgeLength, edgeLength
                    );
                    current.add(split);
                    addSegment(result, current, maxSegments);
                    current = new ArrayList<CoordinateConverter.Point>();
                    current.add(split);
                    currentLength = 0;
                    consumed = nextConsumed;
                }
            }
        }

        if (current.size() >= 2) addSegment(result, current, maxSegments);
        if (result.isEmpty()) throw new IllegalArgumentException("路线坐标不能全部重合");
        return new SegmentationResult(totalLength, result);
    }

    private static void validatePoints(List<CoordinateConverter.Point> points) {
        if (points == null || points.size() < 2) {
            throw new IllegalArgumentException("路线至少需要两个坐标点");
        }
    }

    private static void addSegment(
            List<String> result, List<CoordinateConverter.Point> points, int maxSegments
    ) {
        if (result.size() >= maxSegments) {
            throw new IllegalArgumentException("路线分段数量超过 " + maxSegments
                    + "，请拆分路线后查询");
        }
        result.add(toWkt(points));
    }

    private static String toWkt(List<CoordinateConverter.Point> points) {
        long estimate = 16L + points.size() * 48L;
        int capacity = (int) Math.min(estimate, MAX_INITIAL_WKT_CAPACITY);
        StringBuilder wkt = new StringBuilder(capacity).append("LINESTRING(");
        for (int i = 0; i < points.size(); i++) {
            if (i > 0) wkt.append(',');
            CoordinateConverter.Point point = points.get(i);
            wkt.append(formatCoordinate(point.getLongitude())).append(' ')
                    .append(formatCoordinate(point.getLatitude()));
        }
        return wkt.append(')').toString();
    }

    private static String formatCoordinate(double value) {
        return BigDecimal.valueOf(value).setScale(8, RoundingMode.HALF_UP).toPlainString();
    }

    private static CoordinateConverter.Point interpolate(
            CoordinateConverter.Point a, CoordinateConverter.Point b,
            double ratio, double edgeLength
    ) {
        double lat1 = Math.toRadians(a.getLatitude());
        double lon1 = Math.toRadians(a.getLongitude());
        double lat2 = Math.toRadians(b.getLatitude());
        double lon2 = Math.toRadians(b.getLongitude());
        double angular = edgeLength / EARTH_RADIUS_METERS;
        if (angular < 1e-12) {
            return new CoordinateConverter.Point(a.getLongitude(), a.getLatitude());
        }
        double sinAngular = Math.sin(angular);
        double wa = Math.sin((1 - ratio) * angular) / sinAngular;
        double wb = Math.sin(ratio * angular) / sinAngular;
        double x = wa * Math.cos(lat1) * Math.cos(lon1)
                + wb * Math.cos(lat2) * Math.cos(lon2);
        double y = wa * Math.cos(lat1) * Math.sin(lon1)
                + wb * Math.cos(lat2) * Math.sin(lon2);
        double z = wa * Math.sin(lat1) + wb * Math.sin(lat2);
        return new CoordinateConverter.Point(
                Math.toDegrees(Math.atan2(y, x)),
                Math.toDegrees(Math.atan2(z, Math.sqrt(x * x + y * y)))
        );
    }

    private static double distance(
            CoordinateConverter.Point a, CoordinateConverter.Point b
    ) {
        double lat1 = Math.toRadians(a.getLatitude());
        double lat2 = Math.toRadians(b.getLatitude());
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(b.getLongitude() - a.getLongitude());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1, Math.sqrt(h)));
    }

    @Value
    public static class SegmentationResult {
        double routeLengthMeters;
        List<String> segmentWkts;
    }
}
