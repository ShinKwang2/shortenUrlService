package kr.co.shortenurlservice.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import kr.co.shortenurlservice.domain.LackOfShortenUrlKeyException;
import kr.co.shortenurlservice.domain.NotFoundShortenUrlException;
import kr.co.shortenurlservice.domain.ShortenUrl;
import kr.co.shortenurlservice.domain.ShortenUrlRepository;
import kr.co.shortenurlservice.presentation.ShortenUrlCreateRequestDto;
import kr.co.shortenurlservice.presentation.ShortenUrlCreateResponseDto;
import kr.co.shortenurlservice.presentation.ShortenUrlInformationDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Slf4j
@Service
public class SimpleShortenUrlService {

    private final ShortenUrlRepository shortenUrlRepository;
    private final Counter createCount;
    private final Counter redirectCount;
    private final Timer keyGenTimer;

    public SimpleShortenUrlService(ShortenUrlRepository shortenUrlRepository, MeterRegistry meterRegistry) {
        this.shortenUrlRepository = shortenUrlRepository;

        // Counter - 누적 횟수, 단조 증가(monotonically increasing)한다.
        this.createCount = Counter.builder("shorturl.created")
                .description("Number of shortened URLs created")
                .register(meterRegistry);

        this.redirectCount = Counter.builder("shorturl.redirected")
                .description("Number of redirects performed")
                .register(meterRegistry);

        // Timer - 소요 시간 분포(histogram). p50, p95, p99 등을 자동 계산한다.
        this.keyGenTimer = Timer.builder("shorturl.keygen.duration")
                .description("Time to generate a unique shorten URL key")
                .register(meterRegistry);

        // Gauge - 현재 값, 올라가기도 내려가기도 한다.
        Gauge.builder("shorturl.active.count",
                        shortenUrlRepository, repo -> repo.findAll().size())
                .description("Current number of active shortened URLs")
                .register(meterRegistry);
    }

    public ShortenUrlCreateResponseDto generateShortenUrl(
            ShortenUrlCreateRequestDto shortenUrlCreateRequestDto
    ) {

        String originalUrl = shortenUrlCreateRequestDto.getOriginalUrl();
        // long keyGenStart = System.nanoTime();
        // String shortenUrlKey = getUniqueShortenUrlKey();
        // long keyGenDurationMs = (System.nanoTime() - keyGenStart) / 1_000_000;

        // Timer로 키 생성 시간 측정
        String shortenUrlKey = keyGenTimer.record(this::getUniqueShortenUrlKey);

        ShortenUrl shortenUrl = new ShortenUrl(originalUrl, shortenUrlKey);

        long saveStart = System.nanoTime();
        shortenUrlRepository.saveShortenUrl(shortenUrl);
        long saveDurationMs = (System.nanoTime() - saveStart) / 1_000_000;

        createCount.increment(); // 생성 카운트 증가
        log.info("shortenUrl 생성: {}",
                kv("shortenUrlKey", shortenUrlKey),
                kv("originalUrl", originalUrl),
                kv("saveDurationMs", saveDurationMs));

        ShortenUrlCreateResponseDto shortenUrlCreateResponseDto
                = new ShortenUrlCreateResponseDto(shortenUrl);

        return shortenUrlCreateResponseDto;
    }

    public String getOriginalUrlByShortenUrlKey(String shortenUrlKey) {
        ShortenUrl shortenUrl = shortenUrlRepository.findShortenUrlByShortenUrlKey(shortenUrlKey);

        if (null == shortenUrl) {
            throw new NotFoundShortenUrlException("존재하지 않는 단축 URL: " + shortenUrlKey);
        }

        shortenUrl.increaseRedirectCount();
        shortenUrlRepository.saveShortenUrl(shortenUrl);

        String originalUrl = shortenUrl.getOriginalUrl();

        redirectCount.increment(); // 리다이렉트 카운터 증가

        log.info("리다이렉트 수행: {}, {}",
                kv("shortenUrlKey", shortenUrlKey),
                kv("originalUrl", originalUrl));

        return originalUrl;
    }

    public ShortenUrlInformationDto getShortenUrlInformationByShortenUrlKey(String shortenUrlKey) {
        ShortenUrl shortenUrl = shortenUrlRepository.findShortenUrlByShortenUrlKey(shortenUrlKey);

        if (null == shortenUrl) {
            throw new NotFoundShortenUrlException("존재하지 않는 단축 URL: " + shortenUrlKey);
        }

        ShortenUrlInformationDto shortenUrlInformationDto = new ShortenUrlInformationDto(shortenUrl);
        return shortenUrlInformationDto;
    }

    public List<ShortenUrlInformationDto> getAllShortenUrlInformationDto() {
        List<ShortenUrl> shortenUrls = shortenUrlRepository.findAll();

        return shortenUrls.stream()
                .map(ShortenUrlInformationDto::new)
                .toList();
    }

    private String getUniqueShortenUrlKey() {
        final int MAX_RETRY_COUNT = 5;
        int count = 0;

        while (count++ < MAX_RETRY_COUNT) {
            String shortenUrlKey = ShortenUrl.generateShortenUrlKey();
            ShortenUrl shortenUrl = shortenUrlRepository.findShortenUrlByShortenUrlKey(shortenUrlKey);

            if (null == shortenUrl) {
                return shortenUrlKey;
            }

            if (count >= 3) {
                log.warn("키 생성 재시도 {}회째, 키 공간 고갈 가능성 주의", count);
            }
        }

        throw new LackOfShortenUrlKeyException();
    }
}
