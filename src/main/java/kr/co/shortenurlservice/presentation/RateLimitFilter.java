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

    private final int requestsPerMinute;
    private final ConcurrentHashMap<String, AtomicInteger> requestCounts;

    public RateLimitFilter(@Value("${rate-limit.requests-per-minute}") int requestPerMinute) {
        this.requestsPerMinute = requestPerMinute;
        this.requestCounts = new ConcurrentHashMap<>();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String clientIp = request.getRemoteAddr();

        AtomicInteger count = requestCounts.computeIfAbsent(clientIp, k -> new AtomicInteger(0));
        int currentCount = count.incrementAndGet();

        if (currentCount > requestsPerMinute) {
            log.warn("[RATE_LIMIT] 요청 한도 초과, ip={}, endpoint={} {}, limit={}/min, current={}",
                    clientIp,
                    request.getMethod(),
                    request.getRequestURI(),
                    requestsPerMinute,
                    currentCount);

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("text/plain; charset=UTF-8");
            response.getWriter().write("요청 한도를 초과했습니다. 잠시 후 다시 시도해주세요.");
            return; // chain.doFilter 요청하지 않음 -> 요청 차단
        }

        filterChain.doFilter(request, response);
    }

    @Scheduled(fixedRate = 60_000)
    public void resetLateLimitCounts() {
        if (!requestCounts.isEmpty()) {
            log.debug("[RATE_LIMIT] 카운터 초기화, trackedIp Count={}", requestCounts.size());
            requestCounts.clear();
        }
    }
}
