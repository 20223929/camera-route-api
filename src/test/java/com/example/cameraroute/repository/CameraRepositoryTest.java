package com.example.cameraroute.repository;

import com.example.cameraroute.dto.CameraPage;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
        when(jdbcTemplate.query(
                any(PreparedStatementCreator.class),
                any(ResultSetExtractor.class)
        )).thenAnswer(invocation -> {
            PreparedStatementCreator creator = invocation.getArgument(0);
            creator.createPreparedStatement(connection);
            return new CameraPage(12L, Collections.emptyList());
        });

        CameraPage page = repository.findCamerasNearRoute(
                Arrays.asList("LINESTRING(113 23,113.01 23)"),
                250,
                3,
                25
        );

        assertEquals(12L, page.getTotal());
        verify(connection).createArrayOf(eq("text"), any(String[].class));
        verify(statement).setArray(1, sqlArray);
        verify(statement).setInt(2, 250);
        verify(statement).setInt(3, 25);
        verify(statement).setLong(4, 50L);
        verify(statement).setQueryTimeout(17);
    }

    @Test
    void skipsDatabaseWhenNoRouteSegmentsAreProvided() {
        CameraPage page = repository.findCamerasNearRoute(
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
    void runsCountFallbackWhenRequestedPageHasNoRows() throws Exception {
        PreparedStatement pageStatement = mock(PreparedStatement.class);
        PreparedStatement countStatement = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString()))
                .thenReturn(pageStatement, countStatement);

        when(jdbcTemplate.query(
                any(PreparedStatementCreator.class),
                any(ResultSetExtractor.class)
        )).thenAnswer(new org.mockito.stubbing.Answer<Object>() {
            private int invocationCount;

            @Override
            public Object answer(org.mockito.invocation.InvocationOnMock invocation)
                    throws Throwable {
                PreparedStatementCreator creator = invocation.getArgument(0);
                creator.createPreparedStatement(connection);
                invocationCount++;
                return invocationCount == 1
                        ? new CameraPage(0L, Collections.emptyList())
                        : Long.valueOf(42L);
            }
        });

        CameraPage page = repository.findCamerasNearRoute(
                Arrays.asList("LINESTRING(113 23,113.01 23)"),
                100,
                99,
                20
        );

        assertEquals(42L, page.getTotal());
        assertEquals(0, page.getData().size());
        verify(pageStatement).setInt(3, 20);
        verify(pageStatement).setLong(4, 1960L);
        verify(countStatement, never()).setInt(eq(3), anyInt());
        verify(countStatement, never()).setLong(eq(4), anyLong());
        verify(pageStatement).setQueryTimeout(17);
        verify(countStatement).setQueryTimeout(17);
    }
}
