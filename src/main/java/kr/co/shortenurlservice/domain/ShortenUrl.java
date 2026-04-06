package kr.co.shortenurlservice.domain;

import lombok.ToString;

import java.time.LocalDateTime;
import java.util.Random;

@ToString
public class ShortenUrl {

    private String originalUrl;

    private String shortenUrlKey;

    private Long redirectCount;

    private LocalDateTime createdAt;
    private boolean expired;

    public ShortenUrl(String originalUrl, String shortenUrlKey, LocalDateTime createdAt) {
        this.originalUrl = originalUrl;
        this.shortenUrlKey = shortenUrlKey;
        this.redirectCount = 0L;
        this.createdAt = createdAt;
        this.expired = false;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public String getShortenUrlKey() {
        return shortenUrlKey;
    }

    public Long getRedirectCount() {
        return redirectCount;
    }

    public void increaseRedirectCount() {
        this.redirectCount = this.redirectCount + 1;
    }

    public void expire() {
        this.expired = true;
    }

    public boolean isExpired() {
        return expired;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public static String generateShortenUrlKey() {
        String base56Characters = "23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz";
        Random random = new Random();
        StringBuilder shortenUrlKey = new StringBuilder();

        for (int count = 0; count < 8; count++) {
            int base56CharacterIndex = random.nextInt(0, base56Characters.length());
            char base56Character = base56Characters.charAt(base56CharacterIndex);

            shortenUrlKey.append(base56Character);
        }

        return shortenUrlKey.toString();
    }
}
