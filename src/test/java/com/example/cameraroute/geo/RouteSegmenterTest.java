package com.example.cameraroute.geo;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RouteSegmenterTest {
    @Test
    void keepsShortRouteAsOneSegment() {
        List<CoordinateConverter.Point> points = Arrays.asList(
                new CoordinateConverter.Point(113.264385, 23.129112),
                new CoordinateConverter.Point(113.270000, 23.132000));
        List<String> segments = RouteSegmenter.toWktSegments(points, 10000);
        assertEquals(1, segments.size());
        assertTrue(segments.get(0).startsWith("LINESTRING("));
    }

    @Test
    void splitsLongRouteAndKeepsSegmentEndpointsConnected() {
        List<CoordinateConverter.Point> points = Arrays.asList(
                new CoordinateConverter.Point(113.0, 23.0),
                new CoordinateConverter.Point(113.3, 23.0));
        List<String> segments = RouteSegmenter.toWktSegments(points, 10000);
        assertTrue(segments.size() > 1);
        for (String segment : segments) assertTrue(segment.startsWith("LINESTRING("));
    }

    @Test
    void gcj02InputIsConvertedToWgs84() {
        CoordinateConverter.Point converted = CoordinateConverter.toWgs84(116.404, 39.915, "GCJ-02");
        assertNotEquals(116.404, converted.longitude, 0.000001);
        assertNotEquals(39.915, converted.latitude, 0.000001);
    }

    @Test
    void wgs84InputIsUnchanged() {
        CoordinateConverter.Point converted = CoordinateConverter.toWgs84(116.404, 39.915, "WGS84");
        assertEquals(116.404, converted.longitude, 0.0);
        assertEquals(39.915, converted.latitude, 0.0);
    }
}
