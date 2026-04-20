package kr.co.shortenurlservice.application;

import kr.co.shortenurlservice.domain.ShortenUrl;
import kr.co.shortenurlservice.domain.ShortenUrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class UrlExpirationSchedulerTest {

    @Mock
    private ShortenUrlRepository shortenUrlRepository;

    private Clock fixedClock;
    private UrlExpirationScheduler scheduler;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(
                Instant.parse("2026-04-15T01:00:01Z"),
                ZoneId.of("UTC")
        );
        scheduler = new UrlExpirationScheduler(shortenUrlRepository, 30L, fixedClock);
    }

    @Test
    void shouldExpireOnlyOldActiveUrls_thenSaveOnlyExpiredTarget() {
        // given
        LocalDateTime now = LocalDateTime.now(fixedClock);
        LocalDateTime threshold = now.minusDays(30L);

        ShortenUrl alreadyExpired = new ShortenUrl("https://a.com", "key1", threshold.minusDays(1L));
        alreadyExpired.expire();

        ShortenUrl oldActive = new ShortenUrl("https://b.com", "key2", threshold.minusDays(1L));
        ShortenUrl beforeOneSecond = new ShortenUrl("https://b.com", "key3", threshold.minusSeconds(1));
        ShortenUrl recentActive = new ShortenUrl("https://c.com", "key4", threshold.plusDays(1L));

        given(shortenUrlRepository.findAll())
                .willReturn(List.of(alreadyExpired, oldActive, beforeOneSecond, recentActive));

        // when
        scheduler.cleanupExpirationUrls();

        // then
        ArgumentCaptor<ShortenUrl> captor = ArgumentCaptor.forClass(ShortenUrl.class);
        verify(shortenUrlRepository, times(2)).saveShortenUrl(captor.capture());

        List<ShortenUrl> savedUrls = captor.getAllValues();
        assertThat(savedUrls).hasSize(2);
        assertThat(savedUrls)
                .extracting(ShortenUrl::getShortenUrlKey)
                .containsExactlyInAnyOrder("key2", "key3");

        assertThat(savedUrls)
                .allMatch(ShortenUrl::isExpired);

        assertThat(alreadyExpired.isExpired()).isTrue();
        assertThat(recentActive.isExpired()).isFalse();
    }

    @Test
    void shouldNotExpireCreatedExactlyAtThreshold_thenDoNotSave() {
        // given
        LocalDateTime now = LocalDateTime.now(fixedClock);
        LocalDateTime threshold = now.minusDays(30L);

        ShortenUrl createdAtThreshold = new ShortenUrl("https://a.com", "key1", threshold);


        given(shortenUrlRepository.findAll())
                .willReturn(List.of(createdAtThreshold));

        // when
        scheduler.cleanupExpirationUrls();

        // then
        verify(shortenUrlRepository, times(0)).saveShortenUrl(any());
        assertThat(createdAtThreshold.isExpired()).isFalse();
    }

    @Test
    void shouldExpireUrlCreatedBeforeThreshold_thenSaveExpiredUrl() {
        LocalDateTime now = LocalDateTime.now(fixedClock);
        LocalDateTime threshold = now.minusDays(30);

        ShortenUrl target = new ShortenUrl("https://a.com", "key1", threshold.minusSeconds(1));
        given(shortenUrlRepository.findAll())
                .willReturn(List.of(target));

        scheduler.cleanupExpirationUrls();

        verify(shortenUrlRepository, times(1)).saveShortenUrl(target);
        assertThat(target.isExpired()).isTrue();
    }
}