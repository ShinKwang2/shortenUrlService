package kr.co.shortenurlservice.domain;

import org.springframework.stereotype.Repository;

import java.util.List;

public interface ShortenUrlRepository {

    void saveShortenUrl(ShortenUrl shortenUrl);

    ShortenUrl findShortenUrlByShortenUrlKey(String shortenUrlKey);

    List<ShortenUrl> findAll();
}
