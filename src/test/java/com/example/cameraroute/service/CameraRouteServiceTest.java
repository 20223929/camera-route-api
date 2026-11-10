package com.example.cameraroute.service;

import com.example.cameraroute.dto.RouteCameraRequest;
import com.example.cameraroute.repository.CameraRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;

class CameraRouteServiceTest {

    private CameraRouteService service;

    @BeforeEach
    void setUp() {
        service = new CameraRouteService(mock(CameraRepository.class));
        ReflectionTestUtils.setField(service, "defaultBufferMeters", 100);
        ReflectionTestUtils.setField(service, "maxBufferMeters", 10000);
        ReflectionTestUtils.setField(service, "segmentLengthMeters", 10000.0d);
    }

    @Test
    void equivalentNormalizedRequestsHaveSameFingerprint() {
        RouteCameraRequest first = request("WGS84", 100);
        RouteCameraRequest second = request("wgs84", 100);

        assertEquals(service.getRequestHash(first), service.getRequestHash(second));
    }

    @Test
    void differentSearchBuffersHaveDifferentFingerprints() {
        assertNotEquals(
                service.getRequestHash(request("WGS84", 100)),
                service.getRequestHash(request("WGS84", 200))
        );
    }

    private RouteCameraRequest request(String type, int buffer) {
        RouteCameraRequest request = new RouteCameraRequest();
        request.setType(type);
        request.setBufferDistance(buffer);

        RouteCameraRequest.CoordinatePoint start = new RouteCameraRequest.CoordinatePoint();
        start.setLongitude(113.264385d);
        start.setLatitude(23.129112d);
        RouteCameraRequest.CoordinatePoint end = new RouteCameraRequest.CoordinatePoint();
        end.setLongitude(113.270000d);
        end.setLatitude(23.132000d);

        request.setCoordinates(java.util.Arrays.asList(start, end));
        return request;
    }
}
