package kr.co.shortenurlservice.application;

import kr.co.shortenurlservice.infrastructure.RateLimitCounter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import static net.logstash.logback.argument.StructuredArguments.kv;

@RequiredArgsConstructor
@Slf4j
@Component
public class RateLimitResetScheduler {

    private static final String RATE_LIMIT_COUNTER_RESET_EVENT = "rate_limit_counter_reset";

    private final RateLimitCounter rateLimitCounter;

    @Scheduled(fixedRate = 60_000)
    public void reset() {
        int cleared = rateLimitCounter.reset();
        log.debug("[RATE_LIMIT] 카운터 초기화, {}, {}",
                kv("event", RATE_LIMIT_COUNTER_RESET_EVENT),
                kv("trackedIpCount", cleared)
        );
    }
}
