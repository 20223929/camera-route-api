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
    void rejectsInvalidPagination() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.findCameras(request("WGS84", 100), 0, 20)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> service.findCameras(request("WGS84", 100), 1, 1001)
        );
    }

    @Test
    void rejectsRouteLongerThanConfiguredSynchronousLimit() {
        RouteCameraRequest request = new RouteCameraRequest();
        request.setType("WGS84");
        request.setBufferDistance(100);

        RouteCameraRequest.CoordinatePoint start = new RouteCameraRequest.CoordinatePoint();
        start.setLongitude(100.0d);
        start.setLatitude(20.0d);
        RouteCameraRequest.CoordinatePoint end = new RouteCameraRequest.CoordinatePoint();
        end.setLongitude(150.0d);
        end.setLatitude(20.0d);
        request.setCoordinates(Arrays.asList(start, end));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.findCameras(request, 1, 100)
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

    private RouteCameraRequest request(String type, Integer buffer) {
        RouteCameraRequest request = new RouteCameraRequest();
        request.setType(type);
        request.setBufferDistance(buffer);

        RouteCameraRequest.CoordinatePoint start = new RouteCameraRequest.CoordinatePoint();
        start.setLongitude(113.264385d);
        start.setLatitude(23.129112d);
        RouteCameraRequest.CoordinatePoint end = new RouteCameraRequest.CoordinatePoint();
        end.setLongitude(113.270000d);
        end.setLatitude(23.132000d);

        request.setCoordinates(Arrays.asList(start, end));
        return request;
    }
}
