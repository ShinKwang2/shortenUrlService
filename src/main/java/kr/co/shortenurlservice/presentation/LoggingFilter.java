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

        try {
            long startTime = System.nanoTime();

            chain.doFilter(wrappedRequest, response);

            // 3. 응답 로깅
            int statusCode = httpServletResponse.getStatus();
            long durationMs = (System.nanoTime() - startTime) / 1_000_000;

            // 상태코드 계열별 분류
            String statusFamily = statusCode / 100 + "xx";

            // durationMs가 500이 넘어갈 경우 Slow Request이므로 WARN으로 로깅한다.
            if (durationMs > 500) {
                log.warn("HTTP {} {} → {} ({}ms) [SLOW]",
                        value("method", method),
                        value("uri", uri),
                        value("statusCode", statusCode),
                        value("durationMs", durationMs),
                        kv("statusFamily", statusFamily),
                        kv("threshold", "warning")
                );
            } else {
                log.info("HTTP {} {} → {} ({}ms)",
                        value("method", method),
                        value("uri", uri),
                        value("statusCode", statusCode),
                        value("durationMs", durationMs),
                        kv("statusFamily", statusFamily)
                );
            }


        } finally {
            // 4. MDC 정리 (스레드 풀 재사용 시 오염 방지)
            MDC.clear();
        }
    }

    private String generateRequestId() {
        return Long.toString(System.currentTimeMillis(), 36)
                + "-"
                + UUID.randomUUID().toString().substring(0, 4);
    }
}
