package kr.co.shortenurlservice.presentation;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.co.shortenurlservice.infrastructure.RateLimitCounter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

import static net.logstash.logback.argument.StructuredArguments.kv;

@RequiredArgsConstructor
@Slf4j
@Order(2)
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String RATE_LIMIT_EXCEEDED_EVENT = "rate_limit_exceeded";


    private final RateLimitCounter rateLimitCounter;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String clientIp = extractClientIp(request);
        String method = request.getMethod();
        String uri = request.getRequestURI();

        if (!rateLimitCounter.isAllowed(clientIp)) {
            log.warn("[RATE_LIMIT] 요청 한도 초과: {}, {}, {}, {}, {}",
                    kv("event", RATE_LIMIT_EXCEEDED_EVENT),
                    kv("clientIp", clientIp),
                    kv("method", method),
                    kv("uri", uri),
                    kv("statusCode", HttpStatus.TOO_MANY_REQUESTS.value())
            );

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("text/plain; charset=UTF-8");
            response.getWriter().write("요청 한도를 초과했습니다. 잠시 후 다시 시도해주세요.");
            return; // chain.doFilter 요청하지 않음 -> 요청 차단
        }
        filterChain.doFilter(request, response);
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
