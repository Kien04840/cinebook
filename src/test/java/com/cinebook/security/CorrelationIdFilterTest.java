package com.cinebook.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CorrelationIdFilterTest {

    private CorrelationIdFilter filter;

    @BeforeEach
    void setUp() {
        filter = new CorrelationIdFilter();
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("When request has no correlation ID header, generate UUID and clear MDC after filter")
    void doFilter_WithoutHeader_GeneratesUuidAndCleansUp() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcDuringChain = new AtomicReference<>();

        FilterChain filterChain = (req, res) -> {
            mdcDuringChain.set(MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID_KEY));
        };

        filter.doFilter(request, response, filterChain);

        String generatedId = response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
        assertNotNull(generatedId, "Response should have correlation ID header");
        assertFalse(generatedId.isBlank());
        assertEquals(generatedId, mdcDuringChain.get(), "MDC during chain should match response header");
        assertNull(MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID_KEY), "MDC should be cleared after filter execution");
    }

    @Test
    @DisplayName("When request provides valid correlation ID, reuse it in MDC and response header")
    void doFilter_WithValidHeader_ReusesCorrelationId() throws ServletException, IOException {
        String clientTraceId = "trace-frontend-abc-123_xyz";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, clientTraceId);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcDuringChain = new AtomicReference<>();

        FilterChain filterChain = (req, res) -> {
            mdcDuringChain.set(MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID_KEY));
        };

        filter.doFilter(request, response, filterChain);

        assertEquals(clientTraceId, response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER));
        assertEquals(clientTraceId, mdcDuringChain.get());
        assertNull(MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID_KEY));
    }

    @Test
    @DisplayName("When request provides invalid header (CRLF, special characters, too long), replace with safe UUID")
    void doFilter_WithMaliciousOrInvalidHeader_GeneratesNewUuid() throws ServletException, IOException {
        // CRLF injection attempt
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "invalid\r\nSet-Cookie: evil=1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcDuringChain = new AtomicReference<>();

        FilterChain filterChain = (req, res) -> {
            mdcDuringChain.set(MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID_KEY));
        };

        filter.doFilter(request, response, filterChain);

        String safeId = response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
        assertNotNull(safeId);
        assertFalse(safeId.contains("\r"));
        assertFalse(safeId.contains("\n"));
        assertEquals(safeId, mdcDuringChain.get());
        assertNull(MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID_KEY));
    }

    @Test
    @DisplayName("When filter chain throws exception, MDC is still cleanly removed in finally block")
    void doFilter_WhenExceptionThrown_MdcIsStillCleared() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain filterChain = (req, res) -> {
            assertNotNull(MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID_KEY));
            throw new RuntimeException("Downstream filter failed");
        };

        assertThrows(RuntimeException.class, () -> filter.doFilter(request, response, filterChain));
        assertNull(MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID_KEY), "MDC must be cleared even if downstream throws");
    }
}

