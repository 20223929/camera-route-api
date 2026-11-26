package com.example.cameraroute.geo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CoordinateConverterTest {

    @Test
    void convertsWgs84ToGcj02AndBackWithinTolerance() {
        CoordinateConverter.Point gcj02 = CoordinateConverter.toGcj02(
                121.473700d, 31.230400d
        );
        assertEquals(121.478223d, gcj02.getLongitude(), 0.00001d);
        assertEquals(31.228457d, gcj02.getLatitude(), 0.00001d);

        CoordinateConverter.Point restored = CoordinateConverter.toWgs84(
                gcj02.getLongitude(), gcj02.getLatitude(), CoordinateType.GCJ_02
        );
        assertEquals(121.473700d, restored.getLongitude(), 0.000001d);
        assertEquals(31.230400d, restored.getLatitude(), 0.000001d);
    }

    @Test
    void leavesCoordinatesOutsideChinaUnchanged() {
        CoordinateConverter.Point point = CoordinateConverter.toGcj02(
                103.8198d, 1.3521d
        );
        assertEquals(103.8198d, point.getLongitude(), 0.0d);
        assertEquals(1.3521d, point.getLatitude(), 0.0d);

        CoordinateConverter.Point restored = CoordinateConverter.toWgs84(
                103.8198d, 1.3521d, CoordinateType.GCJ_02
        );
        assertEquals(103.8198d, restored.getLongitude(), 0.0d);
        assertEquals(1.3521d, restored.getLatitude(), 0.0d);
    }
}
