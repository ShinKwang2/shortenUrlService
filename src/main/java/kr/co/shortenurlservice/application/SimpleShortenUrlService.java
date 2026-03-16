package kr.co.shortenurlservice.application;

import kr.co.shortenurlservice.domain.LackOfShortenUrlKeyException;
import kr.co.shortenurlservice.domain.NotFoundShortenUrlException;
import kr.co.shortenurlservice.domain.ShortenUrl;
import kr.co.shortenurlservice.domain.ShortenUrlRepository;
import kr.co.shortenurlservice.presentation.ShortenUrlCreateRequestDto;
import kr.co.shortenurlservice.presentation.ShortenUrlCreateResponseDto;
import kr.co.shortenurlservice.presentation.ShortenUrlInformationDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SimpleShortenUrlService {

    private ShortenUrlRepository shortenUrlRepository;

    @Autowired
    public SimpleShortenUrlService(ShortenUrlRepository shortenUrlRepository) {
        this.shortenUrlRepository = shortenUrlRepository;
    }

    public ShortenUrlCreateResponseDto generateShortenUrl(
            ShortenUrlCreateRequestDto shortenUrlCreateRequestDto
    ) {
        // 1. 단축 URL 키(key) 생성
        // 2. 원래의 URL과 단축 키를 통해 ShortenUrl 도메인 객체 생성
        // 3. 생성된 ShortenUrl을 레포지토리를 통해 저장
        // 4. ShortenUrl을 ShortenUrlCreateResponseDto로 변환하여 반환

        String originalUrl = shortenUrlCreateRequestDto.getOriginalUrl();
        String shortenUrlKey = getUniqueShortenUrlKey();
        log.debug("getUniqueShortenUrlKey {}", shortenUrlKey);

        ShortenUrl shortenUrl = new ShortenUrl(originalUrl, shortenUrlKey);
        shortenUrlRepository.saveShortenUrl(shortenUrl);
        log.info("shortenUrl 생성: {}", shortenUrl);

        ShortenUrlCreateResponseDto shortenUrlCreateResponseDto
                = new ShortenUrlCreateResponseDto(shortenUrl);

        return shortenUrlCreateResponseDto;
    }

    public String getOriginalUrlByShortenUrlKey(String shortenUrlKey) {
        ShortenUrl shortenUrl = shortenUrlRepository.findShortenUrlByShortenUrlKey(shortenUrlKey);

        if (null == shortenUrl) {
            throw new NotFoundShortenUrlException("단축 URL을 생성하지 못했습니다. shortenUrlKey=" + shortenUrlKey);
        }

        shortenUrl.increaseRedirectCount();
        shortenUrlRepository.saveShortenUrl(shortenUrl);

        String originalUrl = shortenUrl.getOriginalUrl();

        return originalUrl;
    }

    public ShortenUrlInformationDto getShortenUrlInformationByShortenUrlKey(String shortenUrlKey) {
        ShortenUrl shortenUrl = shortenUrlRepository.findShortenUrlByShortenUrlKey(shortenUrlKey);

        if (null == shortenUrl) {
            throw new NotFoundShortenUrlException("단축 URL을 생성하지 못했습니다. shortenUrlKey=" + shortenUrlKey);
        }

        ShortenUrlInformationDto shortenUrlInformationDto = new ShortenUrlInformationDto(shortenUrl);
        return shortenUrlInformationDto;
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
        }

        throw new LackOfShortenUrlKeyException();
    }
}
