package kr.co.shortenurlservice.application;

import kr.co.shortenurlservice.domain.ShortenUrl;
import kr.co.shortenurlservice.domain.ShortenUrlRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Slf4j
@Component
public class UrlExpirationScheduler {

    private static final String JOB_NAME = "expired-url-cleanup";

    private final ShortenUrlRepository shortenUrlRepository;
    private final long ttlDays;

    public UrlExpirationScheduler(ShortenUrlRepository shortenUrlRepository,
                                  @Value("${url.ttl-days}") long ttlDays) {
        this.shortenUrlRepository = shortenUrlRepository;
        this.ttlDays = ttlDays;
    }

    @Scheduled(cron = "${url.expiration-cron}")
    public void cleanupExpirationUrls() {
        // 1. 배치 시작 로그
        log.info("[BATCH] {} started", JOB_NAME);
        long startTime = System.nanoTime();

        // 2. 전체 URL 조회 -> createdAt 기준 만료 대상 필터링
        List<ShortenUrl> allUrls = shortenUrlRepository.findAll();
        LocalDateTime threshold = LocalDateTime.now().minusDays(ttlDays);

        int processed = 0;
        int expired = 0;
        int errors = 0;

        for (ShortenUrl url : allUrls) {
            processed++;
            try {
                if (!url.isExpired() && url.getCreatedAt().isBefore(threshold)) {
                    url.expire();
                    shortenUrlRepository.saveShortenUrl(url);
                    expired++;
                    log.info("URL 만료 처리: shortenUrlKy={}", url.getShortenUrlKey());
                }
            } catch (Exception e) {
                errors++;
                log.error("[BATCH] URL 만료 처리 실패, shortenUrlKey={}", url.getShortenUrlKey(), e);
            }
        }

        // 3. 배치 완료 로그 - 요약 정보 포함
        long durationMs = (System.nanoTime() - startTime) / 1_000_000;
        log.info("[BATCH] ExpiredUrlCleanup completed, processed={}, expired={}, errors={}, duration={}ms",
                processed, expired, errors, durationMs);

        // 4. 느린 배치 경고
        if (durationMs > 10_000) {
            log.warn("[BATCH] {} 느린 실행, duration={}ms, threshold={}days", JOB_NAME, durationMs, ttlDays);
        }
    }
}
