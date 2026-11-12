package com.example.cameraroute.service;

import com.example.cameraroute.dto.CameraPage;
import com.example.cameraroute.dto.RouteCameraRequest;
import com.example.cameraroute.repository.CameraRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CameraRouteServiceTest {
    private CameraRepository repository;
    private CameraRouteService service;

    @BeforeEach
    void setUp() {
        repository = mock(CameraRepository.class);
        service = new CameraRouteService(repository);
        ReflectionTestUtils.setField(service, "defaultBufferMeters", 100);
        ReflectionTestUtils.setField(service, "maxBufferMeters", 10000);
        ReflectionTestUtils.setField(service, "segmentLengthMeters", 10000.0d);
        ReflectionTestUtils.setField(service, "maxRouteLengthMeters", 5000000.0d);
    }

    @Test
    void synchronouslyQueriesGeoJsonLineStringAndReturnsRequestedPage() {
        RouteCameraRequest request = request("WGS84", 100);
        CameraPage expected = new CameraPage(3, Collections.emptyList());
        when(repository.findCamerasNearRoute(anyList(), eq(100), eq(2), eq(20))).thenReturn(expected);
        assertSame(expected, service.findCameras(request, 2, 20));
        verify(repository).findCamerasNearRoute(anyList(), eq(100), eq(2), eq(20));
    }

    @Test
    void rejectsInvalidPaginationBeforeQueryingRepository() {
        assertThrows(IllegalArgumentException.class, () -> service.findCameras(request("WGS84", 100), 0, 20));
        assertThrows(IllegalArgumentException.class, () -> service.findCameras(request("WGS84", 100), 1, 0));
        assertThrows(IllegalArgumentException.class, () -> service.findCameras(request("WGS84", 100), 1, 1001));
        verify(repository, never()).findCamerasNearRoute(anyList(), eq(100), eq(1), eq(20));
    }

    @Test
    void rejectsRouteLongerThanConfiguredSynchronousLimit() {
        RouteCameraRequest request = request("WGS84", 100);
        request.setGeometry(geometry("LineString", coordinate(100.0, 20.0), coordinate(150.0, 20.0)));
        assertThrows(IllegalArgumentException.class, () -> service.findCameras(request, 1, 100));
        verify(repository, never()).findCamerasNearRoute(anyList(), eq(100), eq(1), eq(100));
    }

    @Test
    void acceptsGcj02AliasesAndUsesDefaultBuffer() {
        RouteCameraRequest request = request("gcj_02", null);
        when(repository.findCamerasNearRoute(anyList(), eq(100), eq(1), eq(100)))
                .thenReturn(new CameraPage(0, Collections.emptyList()));
        assertEquals(0, service.findCameras(request, 1, 100).getTotal());
        verify(repository).findCamerasNearRoute(anyList(), eq(100), eq(1), eq(100));
    }

    @Test
    void rejectsUnsupportedCoordinateSystem() {
        assertThrows(IllegalArgumentException.class, () -> service.findCameras(request("BD-09", 100), 1, 20));
        verify(repository, never()).findCamerasNearRoute(anyList(), eq(100), eq(1), eq(20));
    }

    @Test
    void rejectsBufferOutsideConfiguredRange() {
        assertThrows(IllegalArgumentException.class, () -> service.findCameras(request("WGS84", -1), 1, 20));
        assertThrows(IllegalArgumentException.class, () -> service.findCameras(request("WGS84", 10001), 1, 20));
        verify(repository, never()).findCamerasNearRoute(anyList(), eq(100), eq(1), eq(20));
    }

    @Test
    void rejectsMissingOrInvalidGeoJsonGeometry() {
        assertThrows(IllegalArgumentException.class, () -> service.findCameras(null, 1, 20));
        RouteCameraRequest missing = request("WGS84", 100);
        missing.setGeometry(null);
        assertThrows(IllegalArgumentException.class, () -> service.findCameras(missing, 1, 20));

        RouteCameraRequest badType = request("WGS84", 100);
        badType.getGeometry().setType("MultiLineString");
        assertThrows(IllegalArgumentException.class, () -> service.findCameras(badType, 1, 20));

        RouteCameraRequest badPosition = request("WGS84", 100);
        badPosition.setGeometry(geometry("LineString", coordinate(113.0, 23.0), Collections.singletonList(113.1)));
        assertThrows(IllegalArgumentException.class, () -> service.findCameras(badPosition, 1, 20));

        RouteCameraRequest nullPosition = request("WGS84", 100);
        nullPosition.setGeometry(geometry("LineString", coordinate(113.0, 23.0), null));
        assertThrows(IllegalArgumentException.class, () -> service.findCameras(nullPosition, 1, 20));
    }

    @Test
    void rejectsNonFiniteAndOutOfRangeCoordinates() {
        assertInvalidCoordinate(Double.NaN, 23.0);
        assertInvalidCoordinate(Double.POSITIVE_INFINITY, 23.0);
        assertInvalidCoordinate(181.0, 23.0);
        assertInvalidCoordinate(113.0, 91.0);
        assertInvalidCoordinate(113.0, -91.0);
    }

    private void assertInvalidCoordinate(double longitude, double latitude) {
        RouteCameraRequest request = request("WGS84", 100);
        request.setGeometry(geometry("LineString", coordinate(113.0, 23.0), coordinate(longitude, latitude)));
        assertThrows(IllegalArgumentException.class, () -> service.findCameras(request, 1, 20));
    }

    private RouteCameraRequest request(String type, Integer buffer) {
        RouteCameraRequest request = new RouteCameraRequest();
        request.setType(type);
        request.setBufferDistance(buffer);
        request.setGeometry(geometry("LineString",
                coordinate(113.264385, 23.129112), coordinate(113.270000, 23.132000)));
        return request;
    }

    private RouteCameraRequest.Geometry geometry(String type, List<Double>... positions) {
        RouteCameraRequest.Geometry geometry = new RouteCameraRequest.Geometry();
        geometry.setType(type);
        geometry.setCoordinates(Arrays.asList(positions));
        return geometry;
    }

    private List<Double> coordinate(Double longitude, Double latitude) {
        return Arrays.asList(longitude, latitude);
    }
}
