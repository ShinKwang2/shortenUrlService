package kr.co.shortenurlservice.application;

import kr.co.shortenurlservice.domain.ShortenUrl;
import kr.co.shortenurlservice.domain.ShortenUrlRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Slf4j
@Component
public class UrlExpirationScheduler {

    private static final String JOB_NAME = "expired-url-cleanup";
    private static final long SLOW_EXECUTION_THRESHOLD_MS = 10_000L;

    private final ShortenUrlRepository shortenUrlRepository;
    private final long ttlDays;
    private final Clock clock;

    public UrlExpirationScheduler(ShortenUrlRepository shortenUrlRepository,
                                  @Value("${url.ttl-days}") long ttlDays,
                                  Clock clock) {
        this.shortenUrlRepository = shortenUrlRepository;
        this.ttlDays = ttlDays;
        this.clock = clock;
    }

    @Scheduled(cron = "${url.expiration-cron}")
    public void cleanupExpirationUrls() {
        long startTime = System.nanoTime();
        LocalDateTime startedAt = LocalDateTime.now(clock);

        int processed = 0;
        int expired = 0;
        int errors = 0;

        log.info("[BATCH] {} 시작",
                kv("jobName", JOB_NAME),
                kv("event", "batch_started"),
                kv("startedAt", startedAt),
                kv("ttlDays", ttlDays)
        );
        try {
            List<ShortenUrl> allUrls = shortenUrlRepository.findAll();
            LocalDateTime threshold = LocalDateTime.now().minusDays(ttlDays);

            for (ShortenUrl url : allUrls) {
                processed++;
                try {
                    if (!url.isExpired() && url.getCreatedAt().isBefore(threshold)) {
                        url.expire();
                        shortenUrlRepository.saveShortenUrl(url);
                        expired++;
                        log.debug("URL 만료 처리: {}",
                                kv("shortenUrlKey", url.getShortenUrlKey()),
                                kv("event", "url_expired"),
                                kv("jobName", JOB_NAME),
                                kv("createdAt", url.getCreatedAt()),
                                kv("threshold", threshold)
                        );
                    }
                } catch (Exception e) {
                    errors++;
                    log.error("[BATCH] URL 만료 처리 실패: {}",
                            kv("shortenUrlKey", url.getShortenUrlKey()),
                            kv("event", "url_expire_failed"),
                            kv("createdAt", url.getCreatedAt()),
                            e
                    );
                }

                // 3. 배치 완료 로그 - 요약 정보 포함
                long durationMs = (System.nanoTime() - startTime) / 1_000_000;
                log.info("[BATCH] {} 완료, {}, {}, {}, {}ms",
                        kv("jobName", JOB_NAME),
                        kv("processed", processed),
                        kv("expired", expired),
                        kv("errors", errors),
                        kv("durationMs", durationMs),
                        kv("event", "batch_completed"),
                        kv("ttlDays", ttlDays)
                );

                // 4. 느린 배치 경고
                if (durationMs > SLOW_EXECUTION_THRESHOLD_MS) {
                    log.warn("[BATCH] {} 느린 완료, {}ms, {}ms",
                            kv("jobName", JOB_NAME),
                            kv("durationMs", durationMs),
                            kv("thresholdMs", SLOW_EXECUTION_THRESHOLD_MS),
                            kv("event", "batch_slow"),
                            kv("processed", processed),
                            kv("expired", expired),
                            kv("errors", errors)
                    );
                }
            }
        } catch (Exception e) {
            long durationMs = (System.nanoTime() - startTime) / 1_000_000;

            log.error("[BATCH] {} 실패",
                    kv("jobName", JOB_NAME),
                    kv("event", "batch_failed"),
                    kv("processed", processed),
                    kv("expired", expired),
                    kv("errors", errors),
                    kv("durationMs", durationMs),
                    e);

            throw e;
        }
    }
}
