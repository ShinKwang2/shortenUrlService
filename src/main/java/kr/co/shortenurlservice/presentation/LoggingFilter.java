package kr.co.shortenurlservice.presentation;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import net.logstash.logback.argument.StructuredArguments;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static net.logstash.logback.argument.StructuredArguments.*;

@Slf4j
@Component
public class LoggingFilter implements Filter {

    private static final long SLOW_THRESHOLD_MS = 1000;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest httpServletRequest) ||
                !(response instanceof HttpServletResponse httpServletResponse)) {
            chain.doFilter(request, response);
            return;
        }

        // 요청을 CacheBodyHttpServletRequest로 래핑
        CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(httpServletRequest);

        // 1. requestId 생성 (X-Request-Id 헤더가 있으면 사용, 없으면 생성)
        String requestId = Optional.ofNullable(wrappedRequest.getHeader("X-Request-Id"))
                .orElse(generateRequestId());

        String method = wrappedRequest.getMethod();
        String uri = wrappedRequest.getRequestURI();

        // 2. MDC에 요청 컨텍스트 주입
        MDC.put("requestId", requestId);
        MDC.put("method", method);
        MDC.put("uri", uri);
        httpServletResponse.setHeader("X-Request-Id", requestId);

        String body = wrappedRequest.getReader().lines().collect(Collectors.joining());
        log.debug("Request: Method={}, URL={}, Body={}", method, uri, body);

        long startTime = System.nanoTime();

        try {
            chain.doFilter(wrappedRequest, response);
        } finally {
            long durationMs = (System.nanoTime() - startTime) / 1_000_000;
            int statusCode = httpServletResponse.getStatus();
            logTraffic(method, uri, statusCode, durationMs);
            MDC.clear();
        }
    }

    private void logTraffic(String method, String uri, int statusCode, long durationMs) {
        String msg = "HTTP {} {} → {} ({}ms)";
        Object[] args = {
                value("method", method),
                value("uri", uri),
                value("statusCode", statusCode),
                value("durationMs", durationMs),
                kv("statusFamily", statusCode / 100 + "xx")
        };

        if (durationMs > SLOW_THRESHOLD_MS) {
            log.warn(msg, args);
        } else {
            log.info(msg, args);
        }
    }

    private String generateRequestId() {
        return Long.toString(System.currentTimeMillis(), 36)
                + "-"
                + UUID.randomUUID().toString().substring(0, 4);
    }
}
