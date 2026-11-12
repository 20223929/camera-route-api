package com.example.cameraroute.repository;

import com.example.cameraroute.dto.PageResult;
import com.example.cameraroute.dto.CameraResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CameraRepositoryTest {

    private JdbcTemplate jdbcTemplate;
    private CameraRepository repository;
    private Connection connection;
    private Array sqlArray;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate = mock(JdbcTemplate.class);
        repository = new CameraRepository(jdbcTemplate);
        ReflectionTestUtils.setField(repository, "queryTimeoutSeconds", 17);

        connection = mock(Connection.class);
        sqlArray = mock(Array.class);
        when(connection.createArrayOf(eq("text"), any(String[].class))).thenReturn(sqlArray);
    }

    @Test
    void bindsRouteSegmentsBufferPaginationAndQueryTimeout() throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        doAnswer(invocation -> {
            PreparedStatementCreator creator = invocation.getArgument(0);
            creator.createPreparedStatement(connection);
            return PageResult.of(12L, 3, 25, Collections.singletonList(mock(CameraResponse.class)));
        }).when(jdbcTemplate).query(
                any(PreparedStatementCreator.class),
                org.mockito.ArgumentMatchers.<ResultSetExtractor<PageResult<CameraResponse>>>any()
        );

        PageResult<CameraResponse> page = repository.findCamerasNearRoute(
                Arrays.asList("LINESTRING(113 23,113.01 23)"),
                250,
                3,
                25
        );

        assertEquals(12L, page.getTotal());
        assertEquals(1, page.getData().size());
        verify(connection).prepareStatement(org.mockito.ArgumentMatchers.argThat(sql ->
                sql.contains("ST_DWithin(c.geom::geography, r.geog, ?)")
                        && sql.contains("LEFT JOIN paged")
                        && !sql.contains("search_bbox")
                        && !sql.contains("ST_Buffer")
        ));
        verify(connection).createArrayOf(eq("text"), any(String[].class));
        verify(statement).setArray(1, sqlArray);
        verify(statement).setInt(2, 250);
        verify(statement).setInt(3, 25);
        verify(statement).setLong(4, 50L);
        verify(statement).setQueryTimeout(17);
    }

    @Test
    void skipsDatabaseWhenNoRouteSegmentsAreProvided() {
        PageResult<CameraResponse> page = repository.findCamerasNearRoute(
                Collections.<String>emptyList(),
                100,
                1,
                20
        );

        assertEquals(0L, page.getTotal());
        assertEquals(0, page.getData().size());
        verify(jdbcTemplate, never()).query(
                any(PreparedStatementCreator.class),
                any(ResultSetExtractor.class)
        );
    }

    @Test
    void returnsTotalForAnEmptyOrOutOfRangePageInOneQuery() throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);

        doAnswer(invocation -> {
            PreparedStatementCreator creator = invocation.getArgument(0);
            creator.createPreparedStatement(connection);
            return PageResult.of(42L, 99, 20, Collections.<CameraResponse>emptyList());
        }).when(jdbcTemplate).query(
                any(PreparedStatementCreator.class),
                org.mockito.ArgumentMatchers.<ResultSetExtractor<PageResult<CameraResponse>>>any()
        );

        PageResult<CameraResponse> page = repository.findCamerasNearRoute(
                Arrays.asList("LINESTRING(113 23,113.01 23)"),
                100,
                99,
                20
        );

        assertEquals(42L, page.getTotal());
        assertEquals(0, page.getData().size());
        verify(connection).prepareStatement(anyString());
        verify(statement).setInt(3, 20);
        verify(statement).setLong(4, 1960L);
        verify(statement).setQueryTimeout(17);
        verify(jdbcTemplate, org.mockito.Mockito.times(1)).query(
                any(PreparedStatementCreator.class),
                any(ResultSetExtractor.class)
        );
    }
}
