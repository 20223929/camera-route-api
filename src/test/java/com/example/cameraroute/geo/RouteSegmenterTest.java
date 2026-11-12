package com.example.cameraroute.geo;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteSegmenterTest {

    @Test
    void keepsShortRouteAsOneSegment() {
        List<CoordinateConverter.Point> points = Arrays.asList(
                point(113.264385, 23.129112),
                point(113.270000, 23.132000)
        );

        List<String> segments = RouteSegmenter.toWktSegments(points, 10000);

        assertEquals(1, segments.size());
        assertTrue(segments.get(0).startsWith("LINESTRING("));
        assertTrue(segments.get(0).endsWith(")"));
    }

    @Test
    void splitsLongRouteAndKeepsSegmentEndpointsConnected() {
        List<CoordinateConverter.Point> points = Arrays.asList(
                point(113.0, 23.0),
                point(113.3, 23.0)
        );

        List<String> segments = RouteSegmenter.toWktSegments(points, 10000);

        assertTrue(segments.size() > 1);
        for (String segment : segments) {
            assertTrue(segment.startsWith("LINESTRING("));
            assertTrue(segment.endsWith(")"));
            assertTrue(segment.split(",").length >= 2);
        }
        assertAdjacentSegmentsConnected(segments);
    }

    @Test
    void splitsAnOversizedSingleEdgeIntoMultipleConnectedSegments() {
        List<CoordinateConverter.Point> points = Arrays.asList(
                point(0.0, 0.0),
                point(0.02, 0.0)
        );

        List<String> segments = RouteSegmenter.toWktSegments(points, 1000.0d);

        assertEquals(3, segments.size());
        assertAdjacentSegmentsConnected(segments);
    }

    @Test
    void ignoresRepeatedAdjacentPointsAndRejectsCollapsedRoute() {
        List<CoordinateConverter.Point> points = Arrays.asList(
                point(0.0, 0.0),
                point(0.0, 0.0),
                point(0.01, 0.0),
                point(0.01, 0.0)
        );

        assertEquals(1, RouteSegmenter.toWktSegments(points, 2000.0d).size());
        assertThrows(
                IllegalArgumentException.class,
                () -> RouteSegmenter.toWktSegments(
                        Arrays.asList(point(1.0, 1.0), point(1.0, 1.0)),
                        1000.0d
                )
        );
    }

    @Test
    void rejectsMissingOrTooShortRoutesAndInvalidSegmentLength() {
        assertThrows(
                IllegalArgumentException.class,
                () -> RouteSegmenter.toWktSegments(null, 1000.0d)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> RouteSegmenter.toWktSegments(
                        Collections.singletonList(point(0.0, 0.0)),
                        1000.0d
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> RouteSegmenter.toWktSegments(
                        Arrays.asList(point(0.0, 0.0), point(0.01, 0.0)),
                        0.0d
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> RouteSegmenter.toWktSegments(
                        Arrays.asList(point(0.0, 0.0), point(0.01, 0.0)),
                        -1.0d
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> RouteSegmenter.toWktSegments(
                        Arrays.asList(point(0.0, 0.0), point(0.01, 0.0)),
                        Double.NaN
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> RouteSegmenter.toWktSegments(
                        Arrays.asList(point(0.0, 0.0), point(0.01, 0.0)),
                        Double.POSITIVE_INFINITY
                )
        );
    }

    @Test
    void calculatesLengthAcrossAllRouteEdges() {
        List<CoordinateConverter.Point> points = Arrays.asList(
                point(0.0, 0.0),
                point(0.01, 0.0),
                point(0.02, 0.0)
        );

        double length = RouteSegmenter.routeLengthMeters(points);

        assertTrue(length > 2200.0d);
        assertTrue(length < 2300.0d);
    }

    @Test
    void convertsGcj02ToWgs84() {
        CoordinateConverter.Point converted =
                CoordinateConverter.toWgs84(116.404, 39.915, CoordinateType.GCJ_02);

        assertNotEquals(116.404, converted.getLongitude(), 0.000001);
        assertNotEquals(39.915, converted.getLatitude(), 0.000001);
    }

    @Test
    void leavesWgs84AndGcj02CoordinatesOutsideChinaUnchanged() {
        CoordinateConverter.Point wgs84 =
                CoordinateConverter.toWgs84(116.404, 39.915, CoordinateType.WGS84);
        CoordinateConverter.Point outsideChina =
                CoordinateConverter.toWgs84(-73.9857, 40.7484, CoordinateType.GCJ_02);

        assertEquals(116.404, wgs84.getLongitude(), 0.0);
        assertEquals(39.915, wgs84.getLatitude(), 0.0);
        assertEquals(-73.9857, outsideChina.getLongitude(), 0.0);
        assertEquals(40.7484, outsideChina.getLatitude(), 0.0);
    }

    @Test
    void rejectsUnsupportedCoordinateSystem() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CoordinateType.fromValue("BD-09")
        );
    }

    @Test
    void segmentResultCalculatesLengthAndEnforcesLimitsDuringSegmentation() {
        List<CoordinateConverter.Point> points = Arrays.asList(
                point(0.0, 0.0), point(0.3, 0.0)
        );
        RouteSegmenter.SegmentationResult result =
                RouteSegmenter.segment(points, 10000.0d, 5000000.0d, 500);
        assertTrue(result.getRouteLengthMeters() > 30000.0d);
        assertEquals(RouteSegmenter.toWktSegments(points, 10000.0d),
                result.getSegmentWkts());
        assertThrows(IllegalArgumentException.class,
                () -> RouteSegmenter.segment(points, 1000.0d, 5000000.0d, 2));
        assertThrows(IllegalArgumentException.class,
                () -> RouteSegmenter.segment(points, 10000.0d, 1000.0d, 500));
    }

    @Test
    void preservesEightDecimalWktPrecision() {
        List<String> segments = RouteSegmenter.toWktSegments(
                Arrays.asList(point(113.123456789, 23.123456789),
                        point(113.124, 23.124)), 10000.0d);
        assertTrue(segments.get(0).contains("113.12345679 23.12345679"));
    }

    private static CoordinateConverter.Point point(double longitude, double latitude) {
        return new CoordinateConverter.Point(longitude, latitude);
    }

    private static void assertAdjacentSegmentsConnected(List<String> segments) {
        for (int i = 1; i < segments.size(); i++) {
            assertEquals(lastCoordinate(segments.get(i - 1)), firstCoordinate(segments.get(i)));
        }
    }

    private static String firstCoordinate(String wkt) {
        return wkt.substring("LINESTRING(".length(), wkt.indexOf(','));
    }

    private static String lastCoordinate(String wkt) {
        return wkt.substring(wkt.lastIndexOf(',') + 1, wkt.length() - 1);
    }
}
