package kr.co.shortenurlservice.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimitCounter {

    private final int limitPerMinute;
    private final ConcurrentHashMap<String, AtomicInteger> rateLimitCounts;


    public RateLimitCounter(@Value("${rate-limit.requests-per-minute}") int limitPerMinute) {
        this.limitPerMinute = limitPerMinute;
        this.rateLimitCounts = new ConcurrentHashMap<>();
    }

    public boolean isAllowed(String ip) {
        return rateLimitCounts.computeIfAbsent(ip, k -> new AtomicInteger(0))
                .incrementAndGet() <= limitPerMinute;
    }

    public int reset() {
        int size = rateLimitCounts.size();
        rateLimitCounts.clear();
        return size;
    }
}
