# ShortenUrl 프로젝트: 개발자가 직접 해야 하는 Observability 5가지

> 이 문서는 `docs/observability-guide.md`(개념편)의 **구현 동반 문서**다.
> Spring Boot가 자동으로 해주지 않는 5가지를 **왜 해야 하고**, **어떤 선택지가 있으며**, **이 프로젝트에서는 어떻게 적용하는지** 다룬다.

---

## 0. 자동 vs 수동 — 경계를 먼저 이해하자

Spring Boot Actuator + Micrometer 의존성을 추가하고 설정하면 **많은 것이 공짜**다.
하지만 "공짜가 아닌 것"을 모르면, 프로덕션 장애 때 아무것도 추적할 수 없다.

| 영역 | Spring Boot가 자동 제공 | 개발자가 직접 해야 함 |
|------|----------------------|-------------------|
| HTTP 메트릭 (요청수, 에러율, 응답시간) | O — `http.server.requests` | |
| JVM 메트릭 (힙, GC, 스레드, CPU) | O — `jvm.*`, `process.*` | |
| HTTP Span (요청 단위 트레이스) | O — Controller 진입/종료 | |
| Semantic Conventions 속성 | O — `http.request.method`, `url.path` 등 | |
| traceId/spanId 생성·전파 | O — Micrometer Tracing | |
| **비즈니스 이벤트 로그** | | **Section 1** |
| **비즈니스 메트릭** | | **Section 2** |
| **내부 로직 Span** | | **Section 3** |
| **구조화 로그 포맷 + Loki 전송** | | **Section 4** |
| **의존성·설정·인프라 구성** | | **Section 5** |

핵심: 프레임워크는 **"시스템이 어떻게 동작하는가"**를 알려주고,
개발자는 **"비즈니스적으로 무슨 일이 일어나고 있는가"**와 **"문제 발생 시 맥락(로그)"**을 심어야 한다.

---

## 1. 로그를 적절한 위치에 적절한 레벨로 남기기

### Why

Spring Boot가 자동으로 남기는 로그는 기동 로그, 빈 등록, 요청 매핑 정도다.
"URL abc123이 생성되었다", "존재하지 않는 키로 접근 시도가 있었다" 같은 **비즈니스 이벤트**는 개발자가 직접 남겨야 한다.

로그가 없으면 메트릭에서 "에러율 급증"을 감지해도 **왜 에러가 나는지** 알 수 없다.

### 현재 코드의 문제점

현재 프로젝트의 로그 상태를 파일별로 점검하면:

#### `GlobalExceptionHandler.java`

```java
// 문제 1: 500 에러인데 로그가 전혀 없다
@ExceptionHandler(LackOfShortenUrlKeyException.class)
public ResponseEntity<String> handleLackOfShortenUrlKeyException(
        LackOfShortenUrlKeyException ex
) {
    // 개발자에게 알려줄 수 있는 수단 필요  ← 주석만 있고 로그가 없다!
    return new ResponseEntity<>("단축 URL 자원이 부족합니다.", HttpStatus.INTERNAL_SERVER_ERROR);
}

// 문제 2: 4xx(클라이언트 에러)인데 INFO로 기록하고 있다
@ExceptionHandler(NotFoundShortenUrlException.class)
public ResponseEntity<String> handleNotFoundShortenUrlException(
        NotFoundShortenUrlException ex
) {
    log.info(ex.getMessage());  // ← 4xx는 WARN이 적절
    return new ResponseEntity<>("단축 URL을 찾지 못했습니다.", HttpStatus.NOT_FOUND);
}
```

- **문제 1**: `LackOfShortenUrlKeyException`은 서버 내부 에러(500)다. 키 공간이 고갈된 심각한 상황인데 **로그를 전혀 남기지 않는다**. 프로덕션에서 이 에러가 나면 아무 흔적이 없다.
- **문제 2**: `NotFoundShortenUrlException`은 클라이언트가 존재하지 않는 URL에 접근한 것(404)이다. 서버 잘못이 아니므로 ERROR는 과하지만, 비정상 접근 패턴 감지를 위해 **WARN**이 적절하다.
- **문제 3**: `Exception.class`에 대한 catch-all 핸들러가 없다. 예상치 못한 예외가 발생하면 Spring 기본 에러 페이지가 나가고, 개발자는 스택트레이스를 로그에서 볼 수 없다.

#### `ShortenUrlRestController.java`

```java
// 문제 4: TRACE 레벨 — 프로덕션에서 보이지 않는다
@RequestMapping(value = "/shortenUrl", method = RequestMethod.POST)
public ResponseEntity<ShortenUrlCreateResponseDto> createShortenUrl(...) {
    log.trace("shortenUrlCreateRequestDto {}", shortenUrlCreateRequestDto);  // ← TRACE는 거의 안 켠다
    // ...
}

// 문제 5: 가장 많이 호출될 엔드포인트인데 로그가 전혀 없다
@RequestMapping(value = "/{shortenUrlKey}", method = RequestMethod.GET)
public ResponseEntity<?> redirectShortenUrl(@PathVariable String shortenUrlKey)
        throws URISyntaxException {
    // ← 로그 없음. 리다이렉트가 정상적으로 수행되었는지 알 수 없다
    String originalUrl = simpleShortenUrlService.getOriginalUrlByShortenUrlKey(shortenUrlKey);
    // ...
}
```

#### `SimpleShortenUrlService.java`

```java
// 문제 6: 키 생성 재시도가 3회를 초과하면 WARN을 남겨야 한다
private String getUniqueShortenUrlKey() {
    final int MAX_RETRY_COUNT = 5;
    int count = 0;
    while (count++ < MAX_RETRY_COUNT) {
        // ← 재시도 횟수가 높아지면 키 공간 고갈 징조. 3회 초과 시 WARN이 적절
        String shortenUrlKey = ShortenUrl.generateShortenUrlKey();
        ShortenUrl shortenUrl = shortenUrlRepository.findShortenUrlByShortenUrlKey(shortenUrlKey);
        if (null == shortenUrl) return shortenUrlKey;
    }
    throw new LackOfShortenUrlKeyException();
}
```

### 로그 설계 원칙 요약

| 원칙 | 설명 |
|------|------|
| **경계(Boundary)에서 남겨라** | Controller 진입/종료, 예외 핸들러. 내부 로직은 트레이스가 커버 |
| **4xx는 WARN, 5xx는 ERROR** | 서버 잘못(ERROR)과 클라이언트 에러(WARN)를 구분 |
| **행위 + 식별자** | `"URL 단축 완료, key={}"` — 무엇을 했는지 + 추적할 수 있는 값 |
| **프로덕션 INFO만으로 추적 가능해야** | DEBUG/TRACE 없이도 핵심 흐름이 보여야 한다 |

### 개선 코드

#### `GlobalExceptionHandler.java` — 로그 추가

```java
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(LackOfShortenUrlKeyException.class)
    public ResponseEntity<String> handleLackOfShortenUrlKeyException(
            LackOfShortenUrlKeyException ex
    ) {
        log.error("단축 URL 키 생성 한도 초과", ex);  // ERROR + 스택트레이스
        return new ResponseEntity<>("단축 URL 자원이 부족합니다.", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(NotFoundShortenUrlException.class)
    public ResponseEntity<String> handleNotFoundShortenUrlException(
            NotFoundShortenUrlException ex
    ) {
        log.warn("URL 조회 실패: {}", ex.getMessage());  // INFO → WARN으로 변경
        return new ResponseEntity<>("단축 URL을 찾지 못했습니다.", HttpStatus.NOT_FOUND);
    }

    // 예상치 못한 예외를 위한 catch-all 핸들러 추가
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleUnexpectedException(Exception ex) {
        log.error("예상치 못한 오류 발생", ex);  // ERROR + 전체 스택트레이스
        return new ResponseEntity<>("서버 내부 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
```

#### `ShortenUrlRestController.java` — 경계 로깅

```java
@RequestMapping(value = "/shortenUrl", method = RequestMethod.POST)
public ResponseEntity<ShortenUrlCreateResponseDto> createShortenUrl(
        @Valid @RequestBody ShortenUrlCreateRequestDto shortenUrlCreateRequestDto
) {
    log.info("URL 단축 요청, originalUrl={}", shortenUrlCreateRequestDto.getOriginalUrl());

    ShortenUrlCreateResponseDto shortenUrlCreateResponseDto
            = simpleShortenUrlService.generateShortenUrl(shortenUrlCreateRequestDto);

    log.info("URL 단축 완료, shortenUrlKey={}", shortenUrlCreateResponseDto.getShortenUrlKey());
    return ResponseEntity.ok().body(shortenUrlCreateResponseDto);
}

@RequestMapping(value = "/{shortenUrlKey}", method = RequestMethod.GET)
public ResponseEntity<?> redirectShortenUrl(
        @PathVariable String shortenUrlKey
) throws URISyntaxException {
    log.info("리다이렉트 요청, shortenUrlKey={}", shortenUrlKey);

    String originalUrl = simpleShortenUrlService.getOriginalUrlByShortenUrlKey(shortenUrlKey);

    URI redirectUri = new URI(originalUrl);
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.setLocation(redirectUri);
    return new ResponseEntity<>(httpHeaders, HttpStatus.MOVED_PERMANENTLY);
}
```

#### `SimpleShortenUrlService.java` — 재시도 WARN

```java
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
```

### Repository 레이어에는 왜 로그를 안 남기는가?

`MapShortenUrlRepository`는 인메모리 구현체이므로 로그를 남기면 노이즈만 늘어난다.
실제 DB를 사용하게 되면 JDBC/JPA가 쿼리 로그를 자동으로 남기므로, Repository 레이어에 수동 로그는 보통 불필요하다.
다만, 외부 API를 호출하는 Repository라면 **호출 전/후에 INFO 로그**를 남기는 것이 적절하다.

---

## 2. 비즈니스 메트릭 정의

### Why

Actuator가 자동 수집하는 `http.server.requests`는 **HTTP 수준**만 커버한다:
- "POST /shortenUrl이 초당 10번 호출됐다" — O
- "오늘 단축 URL이 몇 개 생성됐는가?" — X
- "키 생성 시 재시도가 평균 몇 번 발생하는가?" — X

비즈니스 관점의 질문에 답하려면 **커스텀 메트릭**을 직접 정의해야 한다.

### 옵션 비교

Spring Boot 3.x에서 커스텀 메트릭을 추가하는 방법은 4가지다:

| 방식 | 메커니즘 | 수집 내용 | 장점 | 단점 |
|------|---------|----------|------|------|
| **MeterRegistry** (프로그래밍) | `Counter.builder(...).register(registry)` | 개발자가 결정 | 완전한 제어, 조건부 기록, 값 기록 가능 | 비즈니스 코드에 Micrometer API 결합 |
| **@Counted** | 메서드에 어노테이션 | 호출 횟수만 | 가장 간단, 코드 침투 최소 | 횟수만 셈, `CountedAspect` 빈 필요 |
| **@Timed** | 메서드에 어노테이션 | 호출 횟수 + 소요 시간 | 어노테이션 하나로 Timer 생성 | `TimedAspect` 빈 필요 |
| **@Observed** | 메서드에 어노테이션 | 호출 횟수 + 소요 시간 + **Span 생성** | 메트릭과 트레이스를 동시에 얻음 | `ObservedAspect` 빈 필요, 가장 무거움 |

#### 어떤 걸 써야 하는가?

```
"이 메서드의 호출 횟수만 알면 된다"           → @Counted
"이 메서드의 소요 시간도 알고 싶다"           → @Timed
"트레이스에서 이 메서드도 Span으로 보고 싶다"  → @Observed
"특정 값(재시도 횟수 등)을 기록해야 한다"      → MeterRegistry (프로그래밍)
```

### 이 프로젝트에 추가할 메트릭

| 메트릭 이름 | 타입 | 위치 | 의미 | 추천 방식 |
|------------|------|------|------|----------|
| `shortenurl.created` | Counter/Timer | `generateShortenUrl()` | 단축 URL 생성 횟수 + 소요시간 | `@Observed` |
| `shortenurl.redirected` | Counter/Timer | `getOriginalUrlByShortenUrlKey()` | 리다이렉트 횟수 + 소요시간 | `@Observed` |
| `shortenurl.key_generation.retries` | DistributionSummary | `getUniqueShortenUrlKey()` | 키 생성 재시도 횟수 분포 | MeterRegistry |

### 구현 예시 — @Observed 방식 (추천)

```java
@Slf4j
@Service
public class SimpleShortenUrlService {

    // @Observed를 사용하면 이 메서드가 호출될 때마다:
    // 1. 메트릭 생성: shortenurl.created (횟수 + 소요시간)
    // 2. Span 생성: 트레이스에서 이 메서드가 하나의 구간으로 보임
    @Observed(name = "shortenurl.created",
              contextualName = "generate-shorten-url")
    public ShortenUrlCreateResponseDto generateShortenUrl(...) {
        // 기존 로직 그대로
    }

    @Observed(name = "shortenurl.redirected",
              contextualName = "redirect-shorten-url")
    public String getOriginalUrlByShortenUrlKey(String shortenUrlKey) {
        // 기존 로직 그대로
    }
}
```

### 구현 예시 — MeterRegistry 방식 (값 기록이 필요할 때)

`@Observed`로는 "재시도가 몇 번 발생했는지"를 **값으로** 기록할 수 없다.
이런 경우에만 MeterRegistry를 직접 사용한다:

```java
@Slf4j
@Service
public class SimpleShortenUrlService {

    private final ShortenUrlRepository shortenUrlRepository;
    private final MeterRegistry meterRegistry;  // 추가

    @Autowired
    public SimpleShortenUrlService(
            ShortenUrlRepository shortenUrlRepository,
            MeterRegistry meterRegistry               // 추가
    ) {
        this.shortenUrlRepository = shortenUrlRepository;
        this.meterRegistry = meterRegistry;
    }

    private String getUniqueShortenUrlKey() {
        final int MAX_RETRY_COUNT = 5;
        int count = 0;
        while (count++ < MAX_RETRY_COUNT) {
            String shortenUrlKey = ShortenUrl.generateShortenUrlKey();
            ShortenUrl shortenUrl = shortenUrlRepository.findShortenUrlByShortenUrlKey(shortenUrlKey);

            if (null == shortenUrl) {
                // 재시도 횟수를 분포 메트릭으로 기록
                DistributionSummary.builder("shortenurl.key_generation.retries")
                        .description("키 생성 시 재시도 횟수 분포")
                        .register(meterRegistry)
                        .record(count);
                return shortenUrlKey;
            }

            if (count >= 3) {
                log.warn("키 생성 재시도 {}회째, 키 공간 고갈 가능성 주의", count);
            }
        }

        throw new LackOfShortenUrlKeyException();
    }
}
```

### Aspect 빈 등록 (필수)

`@Observed`, `@Counted`, `@Timed` 어노테이션은 **AOP 프록시**가 처리한다.
해당 Aspect 빈을 등록하지 않으면 어노테이션이 완전히 무시된다.

```java
package kr.co.shortenurlservice;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObservabilityConfig {

    // @Observed 어노테이션을 처리하는 AOP Aspect
    @Bean
    ObservedAspect observedAspect(ObservationRegistry observationRegistry) {
        return new ObservedAspect(observationRegistry);
    }

    // @Counted를 쓸 경우 (이 프로젝트에서는 @Observed를 쓰므로 불필요)
    // @Bean
    // CountedAspect countedAspect(MeterRegistry registry) {
    //     return new CountedAspect(registry);
    // }

    // @Timed를 쓸 경우 (이 프로젝트에서는 @Observed를 쓰므로 불필요)
    // @Bean
    // TimedAspect timedAspect(MeterRegistry registry) {
    //     return new TimedAspect(registry);
    // }
}
```

### 주의: 카디널리티

메트릭에 태그(label)를 붙일 때, **값의 종류가 무한한 태그는 절대 사용하면 안 된다**.
Prometheus가 시계열 폭발(cardinality explosion)을 일으켜 성능이 급격히 저하된다.

```
✅ .tag("method", "POST")              → 값 종류: GET, POST, PUT, DELETE (4개)
✅ .tag("status", "200")               → 값 종류: 200, 301, 404, 500 (수십 개)
❌ .tag("shortenUrlKey", "abc123")      → 값 종류: 무한대 → 절대 태그로 쓰면 안 됨
❌ .tag("originalUrl", "https://...")   → 값 종류: 무한대 → 절대 태그로 쓰면 안 됨
```

---

## 3. 커스텀 Span 추가

### Why

Spring Boot + Micrometer Tracing은 **HTTP 요청 단위**로 Span을 자동 생성한다:

```
Trace (traceId: abc123)
└── Span: POST /shortenUrl (45ms)     ← 이것만 자동으로 생긴다
```

하지만 "45ms 중에서 키 생성에 몇 ms, DB 저장에 몇 ms 걸렸는가?"는 알 수 없다.
내부 로직의 소요시간을 구간별로 보려면 **커스텀 Span**이 필요하다:

```
Trace (traceId: abc123)
└── Span: POST /shortenUrl (45ms)
    ├── Span: generate-shorten-url (40ms)     ← 커스텀 Span
    │   └── Span: key-generation (12ms)       ← 커스텀 Span
    └── Span: HTTP Response (1ms)
```

### 옵션 비교

| 방식 | 장점 | 단점 | 추천 용도 |
|------|------|------|----------|
| **@Observed** | 가장 간단. 메서드에 어노테이션 하나면 끝. 메트릭도 동시에 얻음 | 메서드 전체가 하나의 Span. 중간에 속성(attribute) 추가 어려움 | 대부분의 서비스 메서드 |
| **Observation API** | 벤더 중립(Micrometer). 속성 추가 가능. 코드 블록 단위로 Span 범위 지정 | 코드가 장황해짐 | 커스텀 속성 필요할 때, 메서드 일부만 Span으로 감쌀 때 |
| **직접 OTel API** | OTel의 모든 기능 사용 가능 (Span Events, Links, 상태 코드 등) | OTel SDK에 직접 종속. Micrometer 추상화를 우회 | 이 프로젝트 규모에서는 불필요 |

#### 핵심: @Observed = 메트릭 + Span 동시

Section 2에서 `@Observed`를 메트릭 수집용으로 소개했다.
사실 `@Observed`는 **메트릭과 Span을 동시에 생성**한다. 즉, Section 2에서 `@Observed`를 적용했다면 Section 3의 커스텀 Span도 이미 해결된 것이다.

이것이 Spring Boot 3.x에서 `@Observed`를 권장하는 이유다:
- `@Counted` = 메트릭만
- `@Timed` = 메트릭만
- `@Observed` = **메트릭 + Span** (하나로 두 가지를 얻음)

### 구현 예시 — Observation API (속성이 필요할 때)

`@Observed`로는 Span에 커스텀 속성(예: 재시도 횟수)을 넣을 수 없다.
이런 경우에 Observation API를 사용한다:

```java
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

@Slf4j
@Service
public class SimpleShortenUrlService {

    private final ShortenUrlRepository shortenUrlRepository;
    private final MeterRegistry meterRegistry;
    private final ObservationRegistry observationRegistry;  // 추가

    @Autowired
    public SimpleShortenUrlService(
            ShortenUrlRepository shortenUrlRepository,
            MeterRegistry meterRegistry,
            ObservationRegistry observationRegistry          // 추가
    ) {
        this.shortenUrlRepository = shortenUrlRepository;
        this.meterRegistry = meterRegistry;
        this.observationRegistry = observationRegistry;
    }

    private String getUniqueShortenUrlKey() {
        // Observation을 수동으로 생성 — Span + 메트릭이 함께 만들어짐
        return Observation.createNotStarted("shortenurl.key-generation", observationRegistry)
                .observe(() -> {
                    final int MAX_RETRY_COUNT = 5;
                    int count = 0;
                    while (count++ < MAX_RETRY_COUNT) {
                        String shortenUrlKey = ShortenUrl.generateShortenUrlKey();
                        ShortenUrl shortenUrl = shortenUrlRepository
                                .findShortenUrlByShortenUrlKey(shortenUrlKey);

                        if (null == shortenUrl) {
                            // 재시도 횟수를 분포 메트릭으로 기록
                            DistributionSummary.builder("shortenurl.key_generation.retries")
                                    .register(meterRegistry)
                                    .record(count);
                            return shortenUrlKey;
                        }

                        if (count >= 3) {
                            log.warn("키 생성 재시도 {}회째", count);
                        }
                    }
                    throw new LackOfShortenUrlKeyException();
                });
    }
}
```

### Observation API vs @Observed — 언제 뭘 쓰는가

```
일반적인 서비스 메서드?
  → @Observed 어노테이션이면 충분

메서드 전체가 아니라 일부 코드 블록만 Span으로 감싸고 싶다?
  → Observation.createNotStarted(...).observe(() -> { ... })

Span에 커스텀 속성(key-value)을 동적으로 추가하고 싶다?
  → Observation API + .lowCardinalityKeyValue("key", "value")

OTel 고유 기능(Span Events, Links)이 필요하다?
  → 직접 OTel API (이 프로젝트에서는 불필요)
```

---

## 4. 로그 포맷/설정 (logback-spring.xml)

### Why

Spring Boot의 기본 로그 출력:
```
2024-01-15 10:23:45.123  INFO 12345 --- [nio-8080-exec-1] k.c.s.a.SimpleShortenUrlService : shortenUrl 생성: ShortenUrl@abc
```

이 형태는 **사람이 읽기엔 좋지만**:
- Loki/Elasticsearch에서 **필드별 검색이 불가능**하다 (정규식으로 파싱해야 함)
- `traceId`, `spanId`가 **포함되어 있지 않다** (로그↔트레이스 연결 불가)

구조화 로그(JSON):
```json
{
  "timestamp": "2024-01-15T10:23:45.123",
  "level": "INFO",
  "traceId": "64a8f3b2c1d4e5f6",
  "spanId": "a1b2c3d4",
  "logger": "k.c.s.a.SimpleShortenUrlService",
  "message": "shortenUrl 생성",
  "shortenUrlKey": "abc123"
}
```

이 형태면:
- Loki에서 `| json | shortenUrlKey="abc123"` 으로 바로 검색
- `traceId`로 로그 → 트레이스 연결

### 포맷 옵션 비교

| 방식 | 라이브러리 | 장점 | 단점 | 추천 |
|------|----------|------|------|------|
| **logstash-logback-encoder** | `net.logstash.logback:logstash-logback-encoder` | 업계 표준. MDC 필드(traceId, spanId) 자동 포함. 커스텀 필드 추가 쉬움. Spring Boot 공식 문서에서도 추천 | 추가 의존성 1개 | **O** |
| **Logback 내장 JsonLayout** | Logback 1.5+ 내장 | 추가 의존성 없음 | MDC 필드를 수동 매핑해야 함. 커스텀 필드 추가 불편. 문서/예제 부족 | |

**추천: logstash-logback-encoder** — traceId/spanId가 MDC에 들어있으면 자동으로 JSON에 포함된다.
Micrometer Tracing이 MDC에 traceId/spanId를 자동 주입하므로, 개발자가 별도 코드를 작성할 필요가 없다.

### Loki 전송 옵션 비교

| 방식 | 메커니즘 | 장점 | 단점 | 추천 |
|------|---------|------|------|------|
| **loki4j appender** | 앱에서 Loki HTTP API로 직접 push | 가장 간단. 사이드카 불필요. logback-spring.xml 설정만으로 완료 | 앱이 Loki에 네트워크 의존 | **O** (단일 서비스) |
| **Promtail sidecar** | 앱은 파일에 로그 기록 → Promtail이 파일을 읽어서 Loki로 전송 | 앱과 Loki가 디커플링. 앱 재시작 시 로그 유실 없음 | Docker Compose에 서비스 추가 필요. 파일 로테이션 설정 필요 | 멀티 서비스 환경 |
| **OTel Collector** | 앱이 OTLP로 로그 전송 → Collector가 Loki로 라우팅 | 로그/메트릭/트레이스 모두 하나의 파이프라인으로 통합 | 가장 복잡. Collector 설정 학습 필요 | 대규모 마이크로서비스 |

**추천: loki4j appender** — 단일 서비스 학습 프로젝트에서 가장 빠르게 시작할 수 있다.
나중에 서비스가 여러 개로 늘어나면 Promtail이나 OTel Collector로 전환을 고려한다.

### traceId/spanId가 로그에 들어가는 원리

```
[자동] Micrometer Tracing
  → HTTP 요청이 들어오면 traceId, spanId 생성
  → SLF4J MDC에 자동 주입: MDC.put("traceId", "..."), MDC.put("spanId", "...")

[자동] logstash-logback-encoder
  → MDC의 모든 키-값을 JSON 필드로 자동 포함

결과: 개발자가 작성하는 코드 = 0줄
```

### 프로파일별 설정 전략

| 프로파일 | 콘솔 출력 | Loki 전송 | 로그 레벨 |
|----------|----------|-----------|----------|
| **dev** (기본) | 사람이 읽기 좋은 패턴 레이아웃 (색상 포함) | 없음 (Loki 없어도 개발 가능) | DEBUG |
| **prod** | 없음 또는 최소한 | JSON → Loki push | INFO |

**왜 분리하는가?**
- 개발 중에는 JSON 로그를 콘솔에서 읽기 어렵다
- 프로덕션에서는 사람이 읽는 게 아니라 Grafana UI가 렌더링하므로 JSON이 적절하다

### 전체 logback-spring.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>

    <springProperty scope="context" name="APP_NAME"
                    source="spring.application.name"
                    defaultValue="shortenurlservice"/>

    <!-- ===== 개발 환경 (dev) ===== -->
    <springProfile name="default | dev">
        <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder>
                <!-- traceId, spanId를 패턴에 포함 -->
                <pattern>
                    %d{HH:mm:ss.SSS} %highlight(%-5level) [%thread] [traceId=%X{traceId:-}] [spanId=%X{spanId:-}] %cyan(%logger{36}) - %msg%n
                </pattern>
            </encoder>
        </appender>

        <root level="DEBUG">
            <appender-ref ref="CONSOLE"/>
        </root>

        <!-- 프레임워크 로그는 INFO로 제한 (너무 시끄러움) -->
        <logger name="org.springframework" level="INFO"/>
        <logger name="org.apache" level="INFO"/>
    </springProfile>

    <!-- ===== 프로덕션 환경 (prod) ===== -->
    <springProfile name="prod">
        <!-- JSON 구조화 로그 → Loki로 직접 push -->
        <appender name="LOKI" class="com.github.loki4j.logback.Loki4jAppender">
            <!-- Loki 서버 주소 -->
            <http>
                <url>http://localhost:3100/loki/api/v1/push</url>
            </http>
            <format>
                <!-- Loki 라벨: 인덱싱에 사용. 저카디널리티만! -->
                <label>
                    <pattern>application=${APP_NAME},host=${HOSTNAME},level=%level</pattern>
                </label>
                <!-- 로그 본문: JSON 형태 -->
                <message>
                    <pattern>
                        {"timestamp":"%d{yyyy-MM-dd'T'HH:mm:ss.SSS}","level":"%level","traceId":"%X{traceId:-}","spanId":"%X{spanId:-}","logger":"%logger{36}","message":"%message","stackTrace":"%exception{short}"}
                    </pattern>
                </message>
            </format>
        </appender>

        <root level="INFO">
            <appender-ref ref="LOKI"/>
        </root>
    </springProfile>

</configuration>
```

> **Loki 라벨 주의사항**:
> - `application`, `host`, `level` 정도만 라벨로 설정 (저카디널리티)
> - `traceId`, `shortenUrlKey` 같은 고카디널리티 값은 **절대 라벨에 넣지 않는다** → 로그 본문에만 포함
> - Loki는 라벨로 인덱싱하므로, 라벨 종류가 많으면 성능이 급격히 저하된다

### logstash-logback-encoder를 사용하는 대안 (더 풍부한 JSON)

위 예시는 loki4j의 내장 패턴으로 JSON을 만들었다.
logstash-logback-encoder를 쓰면 **MDC 필드 자동 포함, 스택트레이스 구조화, 커스텀 필드 추가**가 더 쉬워진다:

```xml
<!-- prod 프로파일에서 logstash-logback-encoder 사용 시 -->
<appender name="LOKI" class="com.github.loki4j.logback.Loki4jAppender">
    <http>
        <url>http://localhost:3100/loki/api/v1/push</url>
    </http>
    <format>
        <label>
            <pattern>application=${APP_NAME},host=${HOSTNAME},level=%level</pattern>
        </label>
        <message class="com.github.loki4j.logback.JsonLayout"/>
    </format>
</appender>
```

`JsonLayout`을 사용하면 MDC의 traceId, spanId가 **자동으로** JSON 필드에 포함된다.

---

## 5. 인프라 설정

### Why

Section 1~4의 코드 변경은 **인프라가 없으면 동작하지 않는다**:
- Micrometer가 메트릭을 만들어도 → **Prometheus**가 수집하지 않으면 사라진다
- 로그를 JSON으로 만들어도 → **Loki**가 없으면 저장/검색할 수 없다
- Span을 생성해도 → **Tempo**가 없으면 트레이스를 볼 수 없다
- 이 모든 것은 → **Grafana**에서 통합 조회한다

### Part A: pom.xml 의존성

| 의존성 | groupId:artifactId | 용도 | Spring Boot BOM 관리 | 버전 명시 필요 |
|--------|-------------------|------|---------------------|--------------|
| **Actuator** | `spring-boot-starter-actuator` | `/actuator/*` 엔드포인트 활성화 | O | 불필요 |
| **Prometheus 레지스트리** | `micrometer-registry-prometheus` | 메트릭을 Prometheus 포맷으로 노출 | O | 불필요 |
| **OTel 트레이싱 브릿지** | `micrometer-tracing-bridge-otel` | Micrometer Tracing → OTel SDK 변환 | O | 불필요 |
| **OTLP Exporter** | `opentelemetry-exporter-otlp` | 트레이스를 OTLP 프로토콜로 내보냄 | O | 불필요 |
| **JSON 로그 인코더** | `logstash-logback-encoder` | 로그를 JSON 구조화 | X | **8.0** |
| **Loki Appender** | `loki-logback-appender` | 로그를 Loki로 직접 push | X | **1.5.2** |

> **Spring Boot BOM이란?**
> `spring-boot-starter-parent`를 상속하면 Spring Boot가 관리하는 라이브러리들의 버전이 자동으로 결정된다.
> BOM이 관리하는 의존성은 `<version>` 태그를 생략해도 된다.
> BOM이 관리하지 않는 의존성(logstash, loki4j)은 반드시 버전을 명시해야 한다.

```xml
<dependencies>
    <!-- 기존 의존성 -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <scope>annotationProcessor</scope>
    </dependency>

    <!-- ===== Observability 의존성 (신규) ===== -->

    <!-- 1. Actuator: /actuator/health, /actuator/prometheus 등 엔드포인트 -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>

    <!-- 2. Prometheus 레지스트리: 메트릭을 Prometheus 포맷으로 변환 -->
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-registry-prometheus</artifactId>
    </dependency>

    <!-- 3. Micrometer → OTel 브릿지: Span을 OTel 형식으로 변환 -->
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-tracing-bridge-otel</artifactId>
    </dependency>

    <!-- 4. OTLP Exporter: 트레이스를 Tempo로 전송 -->
    <dependency>
        <groupId>io.opentelemetry</groupId>
        <artifactId>opentelemetry-exporter-otlp</artifactId>
    </dependency>

    <!-- 5. JSON 구조화 로깅 -->
    <dependency>
        <groupId>net.logstash.logback</groupId>
        <artifactId>logstash-logback-encoder</artifactId>
        <version>8.0</version>
    </dependency>

    <!-- 6. Loki 로그 전송 -->
    <dependency>
        <groupId>com.github.loki4j</groupId>
        <artifactId>loki-logback-appender</artifactId>
        <version>1.5.2</version>
    </dependency>

    <!-- 테스트 -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

### Part B: application.yaml

```yaml
spring:
  application:
    name: shortenurlservice

# ===== Observability 설정 =====
management:
  endpoints:
    web:
      exposure:
        # 외부에 노출할 Actuator 엔드포인트
        # health: 헬스 체크, prometheus: 메트릭, info: 앱 정보
        include: health, info, prometheus
  metrics:
    distribution:
      percentiles-histogram:
        # http.server.requests 메트릭에 히스토그램 버킷 활성화
        # 이게 있어야 Prometheus에서 p50, p95, p99를 계산할 수 있다
        http.server.requests: true
    tags:
      # 모든 메트릭에 application 태그를 추가
      # Prometheus 쿼리에서 {application="shortenurlservice"}로 필터링 가능
      application: ${spring.application.name}
  tracing:
    sampling:
      # 트레이스 샘플링 비율
      # 1.0 = 100% (개발환경). 프로덕션에서는 0.1 (10%) 정도로 낮춘다
      # 100% 샘플링은 트래픽이 많으면 Tempo 저장 비용이 폭발한다
      probability: 1.0
  otlp:
    tracing:
      # 트레이스를 보낼 OTLP 엔드포인트 (Tempo)
      endpoint: http://localhost:4318/v1/traces
```

#### 설정 항목 해설

| 설정 | 왜 필요한가 | 기본값 | 변경한 값 |
|------|-----------|--------|----------|
| `endpoints.web.exposure.include` | Actuator 엔드포인트는 기본적으로 `health`만 노출. `prometheus`를 추가해야 메트릭 수집 가능 | `health` | `health, info, prometheus` |
| `percentiles-histogram` | 히스토그램 없으면 Prometheus에서 p95/p99 계산 불가. `rate`/`count`만 가능 | `false` | `true` |
| `metrics.tags.application` | 메트릭에 서비스 이름 태그가 없으면, 여러 서비스의 메트릭이 섞여서 구분 불가 | 없음 | `${spring.application.name}` |
| `tracing.sampling.probability` | 0이면 트레이스를 아예 생성하지 않음. 1.0이면 모든 요청을 트레이싱 | `0.1` (10%) | `1.0` (개발 중이므로 100%) |
| `otlp.tracing.endpoint` | Tempo의 OTLP HTTP 수신 포트. 이 설정이 없으면 트레이스가 전송되지 않음 | 없음 | `http://localhost:4318/v1/traces` |

### Part C: docker-compose.yml

```yaml
version: "3.8"

services:
  # ===== Prometheus: 메트릭 수집·저장 =====
  prometheus:
    image: prom/prometheus:v3.2.1
    container_name: prometheus
    ports:
      - "9090:9090"
    volumes:
      - ./monitoring/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro
    # extra_hosts는 컨테이너에서 호스트 머신의 앱에 접근하기 위함
    extra_hosts:
      - "host.docker.internal:host-gateway"

  # ===== Grafana: 시각화·대시보드 =====
  grafana:
    image: grafana/grafana:11.5.2
    container_name: grafana
    ports:
      - "3000:3000"
    environment:
      # 초기 관리자 비밀번호 (개발용)
      - GF_SECURITY_ADMIN_PASSWORD=admin
    volumes:
      # 데이터소스 자동 프로비저닝 (Prometheus, Loki, Tempo)
      - ./monitoring/grafana/provisioning:/etc/grafana/provisioning:ro

  # ===== Loki: 로그 저장·검색 =====
  loki:
    image: grafana/loki:3.4.2
    container_name: loki
    ports:
      - "3100:3100"
    command: -config.file=/etc/loki/local-config.yaml

  # ===== Tempo: 트레이스 저장·검색 =====
  tempo:
    image: grafana/tempo:2.7.1
    container_name: tempo
    ports:
      - "3200:3200"   # Tempo query API (Grafana가 사용)
      - "4317:4317"   # OTLP gRPC 수신
      - "4318:4318"   # OTLP HTTP 수신 (Spring Boot가 여기로 전송)
    command: -config.file=/etc/tempo/config.yaml
    volumes:
      - ./monitoring/tempo/config.yaml:/etc/tempo/config.yaml:ro
```

### Part D: 지원 설정 파일들

#### `monitoring/prometheus/prometheus.yml`

```yaml
global:
  scrape_interval: 15s    # 15초마다 메트릭 수집
  evaluation_interval: 15s

scrape_configs:
  - job_name: "shortenurlservice"
    metrics_path: "/actuator/prometheus"    # Actuator 프로메테우스 엔드포인트
    static_configs:
      - targets: ["host.docker.internal:8080"]  # 호스트에서 실행 중인 Spring Boot 앱
```

#### `monitoring/grafana/provisioning/datasources/datasources.yml`

```yaml
apiVersion: 1

datasources:
  # Prometheus — 메트릭 조회
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true

  # Loki — 로그 조회
  - name: Loki
    type: loki
    access: proxy
    url: http://loki:3100
    jsonData:
      # traceId 필드를 클릭하면 Tempo로 이동 (로그↔트레이스 연동의 핵심!)
      derivedFields:
        - name: traceId
          datasourceUid: tempo
          matcherRegex: '"traceId":"([a-f0-9]+)"'
          url: '$${__value.raw}'
          urlDisplayLabel: 'View Trace'

  # Tempo — 트레이스 조회
  - name: Tempo
    type: tempo
    access: proxy
    uid: tempo
    url: http://tempo:3200
```

#### `monitoring/tempo/config.yaml`

```yaml
server:
  http_listen_port: 3200

distributor:
  receivers:
    otlp:
      protocols:
        grpc:
          endpoint: "0.0.0.0:4317"
        http:
          endpoint: "0.0.0.0:4318"

storage:
  trace:
    backend: local
    local:
      path: /var/tempo/traces
    wal:
      path: /var/tempo/wal

metrics_generator:
  storage:
    path: /var/tempo/generator/wal
```

### 단계적 구성 — 한번에 다 하지 말 것

위 설정을 한번에 다 추가하면 문제 발생 시 원인 파악이 어렵다.
기존 `observability-guide.md`의 Phase와 맞춰서 단계적으로 추가한다:

| Phase | 추가하는 것 | docker-compose 서비스 |
|-------|-----------|---------------------|
| **Phase 2: 메트릭** | actuator, micrometer-prometheus, prometheus.yml | Prometheus + Grafana |
| **Phase 3: 로그** | logstash-logback-encoder, loki4j, logback-spring.xml | + Loki |
| **Phase 4: 트레이싱** | micrometer-tracing-bridge-otel, otlp-exporter, application.yaml tracing 설정 | + Tempo |

---

## 6. 구현 순서 체크리스트

### Phase 2: 메트릭 (코드 변경 없이 가장 빠르게 결과를 볼 수 있다)

- [ ] `pom.xml`에 `spring-boot-starter-actuator` + `micrometer-registry-prometheus` 추가
- [ ] `application.yaml`에 `management.endpoints`, `metrics.distribution`, `metrics.tags` 설정
- [ ] `monitoring/prometheus/prometheus.yml` 생성
- [ ] `monitoring/grafana/provisioning/datasources/datasources.yml` 생성 (Prometheus만)
- [ ] `docker-compose.yml` 생성 (Prometheus + Grafana만)
- [ ] `docker compose up -d` 후 `http://localhost:8080/actuator/prometheus` 확인
- [ ] Grafana(`http://localhost:3000`)에서 PromQL 쿼리 테스트

### Phase 3: 구조화 로깅 + Loki

- [ ] `pom.xml`에 `logstash-logback-encoder` + `loki-logback-appender` 추가
- [ ] `src/main/resources/logback-spring.xml` 생성 (Section 4 참고)
- [ ] `docker-compose.yml`에 Loki 서비스 추가
- [ ] Grafana datasources에 Loki 추가
- [ ] 앱 재시작 후 Grafana Explore에서 LogQL 쿼리 테스트

### Phase 4: 트레이싱

- [ ] `pom.xml`에 `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp` 추가
- [ ] `application.yaml`에 `management.tracing`, `management.otlp` 설정
- [ ] `monitoring/tempo/config.yaml` 생성
- [ ] `docker-compose.yml`에 Tempo 서비스 추가
- [ ] Grafana datasources에 Tempo 추가 (derivedFields 포함)
- [ ] 앱 재시작 후 Grafana에서 트레이스 조회
- [ ] Loki 로그의 traceId 클릭 → Tempo 트레이스로 이동 확인

### Phase 5: 비즈니스 로직 계측

- [ ] `ObservabilityConfig.java` 생성 (ObservedAspect 빈 등록)
- [ ] `SimpleShortenUrlService`에 `@Observed` 어노테이션 추가
- [ ] `SimpleShortenUrlService`에 MeterRegistry 주입 + `getUniqueShortenUrlKey()` 재시도 메트릭 추가
- [ ] `GlobalExceptionHandler` 로그 수정 (Section 1 참고)
- [ ] `ShortenUrlRestController` 경계 로깅 추가 (Section 1 참고)
- [ ] Grafana에서 커스텀 메트릭/Span 확인
