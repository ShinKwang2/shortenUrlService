package kr.co.shortenurlservice.presentation;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static net.logstash.logback.argument.StructuredArguments.*;

@Slf4j
@Order(2)
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String RATE_LIMIT_EXCEEDED_EVENT = "rate_limit_exceeded";
    private static final String RATE_LIMIT_COUNTER_RESET_EVENT = "rate_limit_counter_reset";

    private final int limitPerMinute;
    private final ConcurrentHashMap<String, AtomicInteger> requestCounts;


    public RateLimitFilter(@Value("${rate-limit.requests-per-minute}") int limitPerMinute) {
        this.limitPerMinute = limitPerMinute;
        this.requestCounts = new ConcurrentHashMap<>();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String clientIp = extractClientIp(request);
        String method = request.getMethod();
        String uri = request.getRequestURI();

        AtomicInteger count = requestCounts.computeIfAbsent(clientIp, k -> new AtomicInteger(0));
        int currentCount = count.incrementAndGet();

        if (currentCount > limitPerMinute) {
            log.warn("[RATE_LIMIT] 요청 한도 초과: {}, {}, {}, {}, {}, {}",
                    kv("event", RATE_LIMIT_EXCEEDED_EVENT),
                    kv("clientIp", clientIp),
                    kv("method", method),
                    kv("uri", uri),
                    kv("limitPerMinute", limitPerMinute),
                    kv("currentCount", currentCount),
                    kv("statusCode", HttpStatus.TOO_MANY_REQUESTS.value())
            );

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("text/plain; charset=UTF-8");
            response.getWriter().write("요청 한도를 초과했습니다. 잠시 후 다시 시도해주세요.");
            return; // chain.doFilter 요청하지 않음 -> 요청 차단
        }

        filterChain.doFilter(request, response);
    }

    @Scheduled(fixedRate = 60_000)
    public void resetRateLimitCounts() {
        int trackedIpCount = requestCounts.size();
        if (trackedIpCount == 0) {
            return;
        }
        log.debug("[RATE_LIMIT] 카운터 초기화, {}",
                kv("trackedIpCount", trackedIpCount),
                kv("event", RATE_LIMIT_COUNTER_RESET_EVENT)
        );

        requestCounts.clear();
    }

    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }

        return request.getRemoteAddr();
    }
}
