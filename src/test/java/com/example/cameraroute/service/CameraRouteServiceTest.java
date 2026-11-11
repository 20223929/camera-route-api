package com.example.cameraroute.service;

import com.example.cameraroute.dto.CameraPage;
import com.example.cameraroute.dto.RouteCameraRequest;
import com.example.cameraroute.repository.CameraRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    void synchronouslyQueriesSegmentedRouteAndReturnsRequestedPage() {
        RouteCameraRequest request = request("WGS84", 100);
        CameraPage expected = new CameraPage(3, Collections.emptyList());
        when(repository.findCamerasNearRoute(anyList(), eq(100), eq(2), eq(20)))
                .thenReturn(expected);

        CameraPage actual = service.findCameras(request, 2, 20);

        assertSame(expected, actual);
        verify(repository).findCamerasNearRoute(anyList(), eq(100), eq(2), eq(20));
    }

    @Test
    void rejectsInvalidPaginationBeforeQueryingRepository() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.findCameras(request("WGS84", 100), 0, 20)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> service.findCameras(request("WGS84", 100), 1, 0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> service.findCameras(request("WGS84", 100), 1, 1001)
        );
        verify(repository, never()).findCamerasNearRoute(
                anyList(), eq(100), eq(1), eq(20)
        );
    }

    @Test
    void rejectsRouteLongerThanConfiguredSynchronousLimit() {
        RouteCameraRequest request = new RouteCameraRequest();
        request.setType("WGS84");
        request.setBufferDistance(100);
        request.setCoordinates(Arrays.asList(point(100.0d, 20.0d), point(150.0d, 20.0d)));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.findCameras(request, 1, 100)
        );
        verify(repository, never()).findCamerasNearRoute(
                anyList(), eq(100), eq(1), eq(100)
        );
    }

    @Test
    void acceptsGcj02AliasesAndUsesDefaultBuffer() {
        RouteCameraRequest request = request("gcj_02", null);
        when(repository.findCamerasNearRoute(anyList(), eq(100), eq(1), eq(100)))
                .thenReturn(new CameraPage(0, Collections.emptyList()));

        CameraPage page = service.findCameras(request, 1, 100);

        assertEquals(0, page.getTotal());
        verify(repository).findCamerasNearRoute(anyList(), eq(100), eq(1), eq(100));
    }

    @Test
    void rejectsUnsupportedCoordinateSystem() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.findCameras(request("BD-09", 100), 1, 20)
        );
        verify(repository, never()).findCamerasNearRoute(
                anyList(), eq(100), eq(1), eq(20)
        );
    }

    @Test
    void rejectsBufferOutsideConfiguredRange() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.findCameras(request("WGS84", -1), 1, 20)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> service.findCameras(request("WGS84", 10001), 1, 20)
        );
        verify(repository, never()).findCamerasNearRoute(
                anyList(), eq(100), eq(1), eq(20)
        );
    }

    @Test
    void rejectsMissingOrInvalidCoordinates() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.findCameras(null, 1, 20)
        );

        RouteCameraRequest missing = request("WGS84", 100);
        missing.setCoordinates(null);
        assertThrows(
                IllegalArgumentException.class,
                () -> service.findCameras(missing, 1, 20)
        );

        RouteCameraRequest nullPoint = request("WGS84", 100);
        nullPoint.setCoordinates(Arrays.asList(point(113.0d, 23.0d), null));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.findCameras(nullPoint, 1, 20)
        );

        RouteCameraRequest missingLongitude = request("WGS84", 100);
        RouteCameraRequest.CoordinatePoint incomplete = new RouteCameraRequest.CoordinatePoint();
        incomplete.setLatitude(23.0d);
        missingLongitude.setCoordinates(Arrays.asList(point(113.0d, 23.0d), incomplete));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.findCameras(missingLongitude, 1, 20)
        );
    }

    @Test
    void rejectsNonFiniteAndOutOfRangeCoordinates() {
        assertInvalidCoordinate(Double.NaN, 23.0d);
        assertInvalidCoordinate(Double.POSITIVE_INFINITY, 23.0d);
        assertInvalidCoordinate(181.0d, 23.0d);
        assertInvalidCoordinate(113.0d, 91.0d);
        assertInvalidCoordinate(113.0d, -91.0d);
    }

    private void assertInvalidCoordinate(double longitude, double latitude) {
        RouteCameraRequest request = request("WGS84", 100);
        request.setCoordinates(Arrays.asList(
                point(113.0d, 23.0d),
                point(longitude, latitude)
        ));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.findCameras(request, 1, 20)
        );
    }

    private RouteCameraRequest request(String type, Integer buffer) {
        RouteCameraRequest request = new RouteCameraRequest();
        request.setType(type);
        request.setBufferDistance(buffer);
        request.setCoordinates(Arrays.asList(
                point(113.264385d, 23.129112d),
                point(113.270000d, 23.132000d)
        ));
        return request;
    }

    private RouteCameraRequest.CoordinatePoint point(double longitude, double latitude) {
        RouteCameraRequest.CoordinatePoint point = new RouteCameraRequest.CoordinatePoint();
        point.setLongitude(longitude);
        point.setLatitude(latitude);
        return point;
    }
}
