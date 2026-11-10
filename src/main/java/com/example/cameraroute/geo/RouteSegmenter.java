package com.example.cameraroute.geo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Splits a route into overlapping-endpoint LineStrings no longer than the configured length.
 */
public final class RouteSegmenter {

    private static final double EARTH_RADIUS_METERS = 6371008.8;

    private RouteSegmenter() {
    }

    public static double routeLengthMeters(List<CoordinateConverter.Point> points) {
        double total = 0;

        for (int i = 1; i < points.size(); i++) {
            total += distance(points.get(i - 1), points.get(i));
        }

        return total;
    }

    public static List<String> toWktSegments(
            List<CoordinateConverter.Point> points,
            double maxMeters
    ) {
        if (points == null || points.size() < 2) {
            throw new IllegalArgumentException("路线至少需要两个坐标点");
        }

        if (maxMeters <= 0) {
            throw new IllegalArgumentException("分段长度必须大于 0");
        }

        List<String> result = new ArrayList<String>();
        List<CoordinateConverter.Point> current =
                new ArrayList<CoordinateConverter.Point>();

        current.add(points.get(0));
        double currentLength = 0;

        for (int i = 1; i < points.size(); i++) {
            CoordinateConverter.Point start = points.get(i - 1);
            CoordinateConverter.Point end = points.get(i);
            double edgeLength = distance(start, end);

            if (edgeLength == 0) {
                continue;
            }

            if (currentLength >= maxMeters - 1e-6 && current.size() >= 2) {
                result.add(toWkt(current));
                current = new ArrayList<CoordinateConverter.Point>();
                current.add(start);
                currentLength = 0;
            }

            double consumed = 0;

            while (consumed < edgeLength - 1e-6) {
                double remainingCapacity = maxMeters - currentLength;
                double remainingEdge = edgeLength - consumed;

                if (remainingEdge <= remainingCapacity + 1e-6) {
                    current.add(end);
                    currentLength += remainingEdge;
                    consumed = edgeLength;
                } else {
                    double nextConsumed = consumed + remainingCapacity;
                    CoordinateConverter.Point split =
                            interpolate(start, end, nextConsumed / edgeLength);

                    current.add(split);
                    result.add(toWkt(current));
                    current = new ArrayList<CoordinateConverter.Point>();
                    current.add(split);
                    currentLength = 0;
                    consumed = nextConsumed;
                }
            }
        }

        if (current.size() >= 2) {
            result.add(toWkt(current));
        }

        if (result.isEmpty()) {
            throw new IllegalArgumentException("路线坐标不能全部重合");
        }

        return result;
    }

    private static String toWkt(List<CoordinateConverter.Point> points) {
        StringBuilder wkt = new StringBuilder("LINESTRING(");

        for (int i = 0; i < points.size(); i++) {
            if (i > 0) {
                wkt.append(',');
            }

            CoordinateConverter.Point point = points.get(i);
            wkt.append(String.format(
                    Locale.US,
                    "%.8f %.8f",
                    point.getLongitude(),
                    point.getLatitude()
            ));
        }

        return wkt.append(')').toString();
    }

    private static CoordinateConverter.Point interpolate(
            CoordinateConverter.Point a,
            CoordinateConverter.Point b,
            double ratio
    ) {
        double lat1 = Math.toRadians(a.getLatitude());
        double lon1 = Math.toRadians(a.getLongitude());
        double lat2 = Math.toRadians(b.getLatitude());
        double lon2 = Math.toRadians(b.getLongitude());
        double angular = distance(a, b) / EARTH_RADIUS_METERS;

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
            CoordinateConverter.Point a,
            CoordinateConverter.Point b
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
}
