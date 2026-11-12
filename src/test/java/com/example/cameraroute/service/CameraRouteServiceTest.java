package com.example.cameraroute.service;

import com.example.cameraroute.dto.PageResult;
import com.example.cameraroute.dto.CameraResponse;
import com.example.cameraroute.dto.RouteCameraRequest;
import com.example.cameraroute.repository.CameraRepository;
import com.example.cameraroute.geo.CoordinateType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
    void synchronouslyQueriesGeoJsonLineStringAndReturnsRequestedPage() {
        RouteCameraRequest request = request("WGS84", 100);
        PageResult<CameraResponse> expected = PageResult.of(3, 2, 20, Collections.<CameraResponse>emptyList());
        when(repository.findCamerasNearRoute(anyList(), eq(100), eq(2), eq(20)))
                .thenReturn(expected);

        PageResult<CameraResponse> actual = service.findCameras(request, 2, 20);

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
        RouteCameraRequest request = request("WGS84", 100);
        request.setGeometry(geometry(
                "LineString",
                coordinate(100.0d, 20.0d),
                coordinate(150.0d, 20.0d)
        ));

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
                .thenReturn(PageResult.empty(1, 100));

        PageResult<CameraResponse> page = service.findCameras(request, 1, 100);

        assertEquals(0, page.getTotal());
        verify(repository).findCamerasNearRoute(anyList(), eq(100), eq(1), eq(100));
    }

    @Test
    void acceptsOnlyDeclaredCoordinateTypeValues() {
        assertEquals(CoordinateType.WGS84, CoordinateType.fromValue("WGS84"));
        assertEquals(CoordinateType.GCJ_02, CoordinateType.fromValue("GCJ-02"));
        assertEquals(CoordinateType.GCJ_02, CoordinateType.fromValue("gcj_02"));
        assertThrows(IllegalArgumentException.class,
                () -> CoordinateType.fromValue("BD-09"));
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
    void rejectsMissingOrInvalidGeoJsonGeometry() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.findCameras(null, 1, 20)
        );

        RouteCameraRequest missing = request("WGS84", 100);
        missing.setGeometry(null);
        assertThrows(
                IllegalArgumentException.class,
                () -> service.findCameras(missing, 1, 20)
        );

        RouteCameraRequest badType = request("WGS84", 100);
        badType.getGeometry().setType("MultiLineString");
        assertThrows(
                IllegalArgumentException.class,
                () -> service.findCameras(badType, 1, 20)
        );

        RouteCameraRequest badPosition = request("WGS84", 100);
        badPosition.setGeometry(geometry(
                "LineString",
                coordinate(113.0d, 23.0d),
                Collections.singletonList(113.1d)
        ));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.findCameras(badPosition, 1, 20)
        );

        RouteCameraRequest nullPosition = request("WGS84", 100);
        nullPosition.setGeometry(geometry(
                "LineString", coordinate(113.0d, 23.0d), null
        ));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.findCameras(nullPosition, 1, 20)
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
        request.setGeometry(geometry(
                "LineString",
                coordinate(113.0d, 23.0d),
                coordinate(longitude, latitude)
        ));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.findCameras(request, 1, 20)
        );
    }

    private RouteCameraRequest request(String type, Integer buffer) {
        RouteCameraRequest request = new RouteCameraRequest();
        request.setType(CoordinateType.fromValue(type));
        request.setBufferDistance(buffer);
        request.setGeometry(geometry(
                "LineString",
                coordinate(113.264385d, 23.129112d),
                coordinate(113.270000d, 23.132000d)
        ));
        return request;
    }

    private RouteCameraRequest.Geometry geometry(
            String type,
            List<Double>... positions
    ) {
        RouteCameraRequest.Geometry geometry = new RouteCameraRequest.Geometry();
        geometry.setType(type);
        geometry.setCoordinates(Arrays.asList(positions));
        return geometry;
    }

    private List<Double> coordinate(Double longitude, Double latitude) {
        return Arrays.asList(longitude, latitude);
    }
}
