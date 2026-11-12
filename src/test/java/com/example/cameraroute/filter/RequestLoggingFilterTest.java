package com.example.cameraroute.filter;

import javax.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();

    @Test
    void restoresPreviousMdcContextAfterRequest() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/camera/route/list");
        request.addHeader("X-Request-Id", "request-123");
        request.addHeader("X-Trace-Id", "trace-456");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MDC.put("unrelated_stale_key", "stale-value");
        MDC.put("request_id", "outer-request");
        MDC.put("trace_id", "outer-trace");

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals("request-123", response.getHeader("X-Request-Id"));
        assertEquals("trace-456", response.getHeader("X-Trace-Id"));
        assertEquals("outer-request", MDC.get("request_id"));
        assertEquals("outer-trace", MDC.get("trace_id"));
        assertEquals("stale-value", MDC.get("unrelated_stale_key"));
        MDC.clear();
    }

    @Test
    void clearsOnlyFilterContextWhenNoPreviousMdcExists() throws Exception {
        MDC.clear();
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

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
        UUID.fromString(requestId);
        UUID.fromString(traceId);
        assertEquals(36, requestId.length());
        assertEquals(36, traceId.length());
    }

    @Test
    void restoresPreviousMdcContextWhenDownstreamFilterThrows() {
        MDC.put("request_id", "outer-request");
        MDC.put("custom", "outer-value");
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/camera/route/list");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThrows(
                ServletException.class,
                () -> filter.doFilter(request, response, (servletRequest, servletResponse) -> {
                    throw new ServletException("downstream failure");
                })
        );

        assertEquals("outer-request", MDC.get("request_id"));
        assertEquals("outer-value", MDC.get("custom"));
        MDC.clear();
    }
}
