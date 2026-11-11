package com.example.cameraroute.filter;

import javax.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();

    @Test
    void propagatesValidCorrelationIdsAndClearsMdcAfterRequest() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/camera/route/list");
        request.addHeader("X-Request-Id", "request-123");
        request.addHeader("X-Trace-Id", "trace-456");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals("request-123", response.getHeader("X-Request-Id"));
        assertEquals("trace-456", response.getHeader("X-Trace-Id"));
        assertNull(MDC.get("request_id"));
        assertNull(MDC.get("trace_id"));
    }

    @Test
    void replacesInvalidCorrelationIds() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/actuator/health");
        request.addHeader("X-Request-Id", "invalid request id");
        request.addHeader("X-Trace-Id", new String(new char[65]).replace('\0', 'x'));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String requestId = (String) response.getHeader("X-Request-Id");
        String traceId = (String) response.getHeader("X-Trace-Id");
        assertEquals(36, requestId.length());
        assertEquals(36, traceId.length());
    }

    @Test
    void clearsMdcWhenDownstreamFilterThrows() {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/camera/route/list");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThrows(
                ServletException.class,
                () -> filter.doFilter(request, response, (servletRequest, servletResponse) -> {
                    throw new ServletException("downstream failure");
                })
        );

        assertNull(MDC.get("request_id"));
        assertNull(MDC.get("trace_id"));
    }
}
