package com.cinebook.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Filter thiết lập Correlation ID cho từng HTTP request nhằm phục vụ truy vết log phân tán (Distributed Tracing).
 * 
 * Cơ chế hoạt động:
 * 1. Đọc header "X-Correlation-Id" từ request.
 * 2. Xác thực định dạng (chỉ cho phép ký tự an toàn alphanumeric, gạch ngang, gạch dưới, tối đa 64 ký tự).
 *    Nếu không hợp lệ hoặc không có, tự sinh một chuỗi UUID v4 ngẫu nhiên.
 * 3. Nạp vào SLF4J MDC (Mapped Diagnostic Context) với key "correlationId".
 * 4. Đính kèm lại vào Response Header "X-Correlation-Id" để client/frontend có thể tham chiếu khi tra cứu lỗi.
 * 5. Luôn dọn dẹp MDC trong khối finally nhằm tránh rò rỉ context giữa các thread trong thread pool.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String MDC_CORRELATION_ID_KEY = "correlationId";
    private static final Pattern CORRELATION_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,64}$");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String correlationId = resolveCorrelationId(request);
        MDC.put(MDC_CORRELATION_ID_KEY, correlationId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_CORRELATION_ID_KEY);
        }
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        String headerValue = request.getHeader(CORRELATION_ID_HEADER);
        if (StringUtils.hasText(headerValue)) {
            String trimmed = headerValue.trim();
            if (CORRELATION_ID_PATTERN.matcher(trimmed).matches()) {
                return trimmed;
            }
        }
        return UUID.randomUUID().toString();
    }
}

