# 프로덕션 수준 로깅/모니터링을 위한 기능 추가 가이드

> **목적**: 현재 URL 단축 프로젝트에 기능을 추가하여 `logging-strategy.md`의 11개 항목을 모두 연습할 수 있는 환경을 만든다.
> 각 Step은 독립적으로 구현 가능하며, 순서대로 진행하는 것을 권장한다.

---

## 현재 커버리지와 목표

| # | logging-strategy.md 항목 | 현재 | 추가할 기능 |
|---|--------------------------|------|------------|
| 1 | HTTP 경계 로그 | O | - |
| 2 | 예외/에러 로그 | O | - |
| 3 | 비즈니스 상태 변경 | 부분 | Step 3 (만료) |
| 4 | 외부 시스템 호출 | **X** | Step 4 |
| 5 | 인증/인가 실패 | **X** | Step 5 |
| 6 | 검증 실패 로그 | O | - |
| 7 | 성능/지연 이상 | 부분 | Step 4 |
| 8 | 재시도/서킷브레이커 | 부분 | Step 4 |
| 9 | 기동 설정 로그 | **X** | Step 1 |
| 10 | 배치/스케줄 로그 | **X** | Step 3 |
| 11 | 분석/메트릭 | **X** | Step 2 |

---

## Step 1: Startup 설정 로그

**커버**: 항목 9 (기동 설정 로그)
**난이도**: 매우 낮음 (30분)

### 왜 필요한가

"내 로컬에서는 되는데" 문제의 원인은 대부분 **설정 차이**다.
기동 시 어떤 프로파일, 어떤 설정으로 떴는지 로그가 있으면, 장애 배포와 정상 배포를 비교해서 바뀐 설정을 즉시 찾을 수 있다.

### 구현 방법

**새 파일 생성**: `infrastructure/StartupLogger.java`

Spring의 `ApplicationRunner` 인터페이스를 구현한다.
앱이 완전히 기동된 후 `run()` 메서드가 한 번 호출된다.

```java
@Slf4j
@Component
public class StartupLogger implements ApplicationRunner {

    private final Environment environment;

    // Environment를 주입받는다. 활성 프로파일, 프로퍼티를 읽을 수 있다.

    @Override
    public void run(ApplicationArguments args) {
        // environment.getActiveProfiles()로 활성 프로파일 조회
        // environment.getProperty("server.port")로 개별 설정값 조회
        // 하나의 log.info로 출력
    }
}
```

### 출력해야 할 정보

```
INFO [STARTUP] Application started
  profiles: [dev]
  server.port: 8080
  database.address: no-database
```

나중에 Step 3~5를 구현하면 여기에 설정값을 추가한다:
```
  url.ttl: 30d
  rateLimit.requests-per-minute: 10
  resilience4j.circuitbreaker.failure-rate-threshold: 50
```

### 주의사항

- **절대 남기면 안 되는 것**: DB 비밀번호, API 키, JWT 시크릿
- `database.address`는 OK, `database.password`는 절대 안 됨
- URL에 인증정보가 포함되면 마스킹: `jdbc:mysql://***:***@prod-db:3306/db`

### 학습 포인트

- `ApplicationRunner` vs `CommandLineRunner` vs `@EventListener(ApplicationReadyEvent.class)` — 세 가지 방법의 차이를 알아두면 좋다
- `Environment` 인터페이스로 프로퍼티에 접근하는 방법

---

## Step 2: Actuator + Micrometer 커스텀 메트릭

**커버**: 항목 11 (분석/메트릭)
**난이도**: 낮음 (1시간)

### 왜 필요한가

로그는 **개별 이벤트**를 기록하고, 메트릭은 **집계된 수치**를 제공한다.
"지난 5분간 URL 생성이 몇 건인가?", "리다이렉트 p99 지연은?" 같은 질문은 로그가 아니라 메트릭이 답해야 한다.

Grafana에서 메트릭 이상 감지 → Loki에서 해당 시점 로그 검색 → Tempo에서 트레이스 추적.
이 워크플로우의 시작점이 메트릭이다.

### 구현 방법

#### 2-1. 의존성 추가 (`pom.xml`)

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

#### 2-2. Actuator 설정 (`application.yaml`)

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, info, prometheus, metrics
  endpoint:
    health:
      show-details: always
  metrics:
    tags:
      application: ${spring.application.name}
```

이것만으로 `/actuator/prometheus` 엔드포인트에서 JVM, HTTP 기본 메트릭이 자동 수집된다.

#### 2-3. 커스텀 메트릭 등록 (`SimpleShortenUrlService.java`)

`MeterRegistry`를 주입받아 비즈니스 메트릭을 등록한다.

```java
@Slf4j
@Service
public class SimpleShortenUrlService {

    private final ShortenUrlRepository shortenUrlRepository;
    private final Counter createCounter;
    private final Counter redirectCounter;
    private final Timer keyGenTimer;

    public SimpleShortenUrlService(
            ShortenUrlRepository shortenUrlRepository,
            MeterRegistry meterRegistry    // Micrometer가 자동 주입
    ) {
        this.shortenUrlRepository = shortenUrlRepository;

        // Counter — 누적 횟수. 단조 증가(monotonically increasing)한다.
        this.createCounter = Counter.builder("shorturl.created")
                .description("Number of shortened URLs created")
                .register(meterRegistry);

        this.redirectCounter = Counter.builder("shorturl.redirected")
                .description("Number of redirects performed")
                .register(meterRegistry);

        // Timer — 소요 시간 분포(histogram). p50, p95, p99 등을 자동 계산한다.
        this.keyGenTimer = Timer.builder("shorturl.keygen.duration")
                .description("Time to generate a unique shorten URL key")
                .register(meterRegistry);

        // Gauge — 현재 값. 올라가기도 내려가기도 한다.
        Gauge.builder("shorturl.active.count",
                        shortenUrlRepository, repo -> repo.findAll().size())
                .description("Current number of active shortened URLs")
                .register(meterRegistry);
    }
}
```

#### 2-4. 메트릭 기록 (서비스 메서드 내)

```java
public ShortenUrlCreateResponseDto generateShortenUrl(...) {
    // Timer로 키 생성 시간 측정
    String shortenUrlKey = keyGenTimer.record(this::getUniqueShortenUrlKey);

    // ... 기존 로직 ...

    createCounter.increment();  // 생성 카운터 증가
    return response;
}

public String getOriginalUrlByShortenUrlKey(String shortenUrlKey) {
    // ... 기존 로직 ...
    redirectCounter.increment();  // 리다이렉트 카운터 증가
    return originalUrl;
}
```

### 확인 방법

```bash
# 앱 기동 후
curl http://localhost:8080/actuator/prometheus | grep shorturl

# 출력 예시:
# shorturl_created_total 3.0
# shorturl_redirected_total 7.0
# shorturl_active_count 3.0
# shorturl_keygen_duration_seconds_bucket{le="0.001"} 2.0
```

### 학습 포인트

- **Counter vs Gauge vs Timer** — 각각 언제 쓰는지 이해하는 것이 핵심
  - Counter: "지금까지 총 몇 번?" (절대 줄지 않음) → URL 생성 수
  - Gauge: "지금 현재 몇 개?" (오르내림) → 활성 URL 수
  - Timer: "얼마나 걸렸나?" (히스토그램) → 키 생성 시간
- Prometheus가 이 엔드포인트를 주기적으로 스크래핑(pull)하는 구조
- `Timer.record()`를 사용하면 기존 코드의 수동 `System.nanoTime()` 측정을 대체할 수 있다

---

## Step 3: URL 만료 배치 작업

**커버**: 항목 10 (배치/스케줄 로그), 항목 3 보강 (새 상태 전이)
**난이도**: 낮음 (1~2시간)

### 왜 필요한가

배치 작업은 HTTP 요청 없이 실행되므로, LoggingFilter가 잡지 못한다.
MDC에 requestId도 없다. 배치 로그를 별도로 설계해야 한다는 것 자체가 학습 포인트다.

또한 "active → expired" 상태 전이가 추가되면서, 비즈니스 상태 변경 로그의 다양한 패턴을 연습할 수 있다.

### 구현 방법

#### 3-1. 도메인 모델 수정 (`ShortenUrl.java`)

```java
public class ShortenUrl {
    private String originalUrl;
    private String shortenUrlKey;
    private Long redirectCount;
    private LocalDateTime createdAt;   // 추가
    private boolean expired;           // 추가

    public ShortenUrl(String originalUrl, String shortenUrlKey) {
        // 기존 생성자에 추가:
        this.createdAt = LocalDateTime.now();
        this.expired = false;
    }

    public void expire() {
        this.expired = true;
    }

    public boolean isExpired() {
        return expired;
    }

    // createdAt getter 추가
}
```

#### 3-2. 스케줄링 활성화 (`ShortenurlserviceApplication.java`)

```java
@EnableScheduling   // 추가
@SpringBootApplication
public class ShortenurlserviceApplication {
    // ...
}
```

#### 3-3. 설정값 추가 (`application.yaml`)

```yaml
url:
  ttl-days: 30                    # URL 만료 기한 (일)
  expiration-cron: "0 0 * * * *"  # 매 시간 정각 실행 (학습 중에는 짧게 조절)
```

#### 3-4. 배치 작업 구현 — 새 파일: `application/UrlExpirationScheduler.java`

```java
@Slf4j
@Component
public class UrlExpirationScheduler {

    private final ShortenUrlRepository shortenUrlRepository;
    private final long ttlDays;

    // @Value("${url.ttl-days}") 로 TTL 주입
    // ShortenUrlRepository 주입

    @Scheduled(cron = "${url.expiration-cron}")
    public void cleanupExpiredUrls() {
        // 1. 배치 시작 로그
        log.info("[BATCH] ExpiredUrlCleanup started");
        long startTime = System.nanoTime();

        // 2. 전체 URL 조회 → createdAt 기준 만료 대상 필터링
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
                    log.info("URL 만료 처리: {}", kv("shortenUrlKey", url.getShortenUrlKey()));
                }
            } catch (Exception e) {
                errors++;
                log.error("[BATCH] URL 만료 처리 실패, shortenUrlKey={}", url.getShortenUrlKey(), e);
            }
        }

        // 3. 배치 완료 로그 — 요약 정보 포함
        long durationMs = (System.nanoTime() - startTime) / 1_000_000;
        log.info("[BATCH] ExpiredUrlCleanup completed, processed={}, expired={}, errors={}, duration={}ms",
                processed, expired, errors, durationMs);

        // 4. 느린 배치 경고
        if (durationMs > 10_000) {
            log.warn("[BATCH] ExpiredUrlCleanup 느린 실행, duration={}ms, threshold=10000ms", durationMs);
        }
    }
}
```

#### 3-5. 서비스 수정 (`SimpleShortenUrlService.java`)

리다이렉트 시 만료 여부를 확인한다.

```java
public String getOriginalUrlByShortenUrlKey(String shortenUrlKey) {
    ShortenUrl shortenUrl = shortenUrlRepository.findShortenUrlByShortenUrlKey(shortenUrlKey);

    if (null == shortenUrl) {
        throw new NotFoundShortenUrlException("존재하지 않는 단축 URL: " + shortenUrlKey);
    }

    // 만료 URL 접근 시
    if (shortenUrl.isExpired()) {
        log.info("만료된 URL 접근 시도: {}", kv("shortenUrlKey", shortenUrlKey));
        throw new ExpiredShortenUrlException("만료된 단축 URL: " + shortenUrlKey);
    }

    // ... 기존 로직 ...
}
```

#### 3-6. 새 예외 클래스와 핸들러

`domain/ExpiredShortenUrlException.java`를 만들고,
`GlobalExceptionHandler`에 핸들러 추가:

```java
@ExceptionHandler(ExpiredShortenUrlException.class)
public ResponseEntity<String> handleExpiredShortenUrlException(ExpiredShortenUrlException ex) {
    log.info("만료된 URL 조회: {}", ex.getMessage());
    return new ResponseEntity<>("해당 단축 URL은 만료되었습니다.", HttpStatus.GONE);  // 410 Gone
}
```

### 학습 포인트

- **배치에서의 MDC**: HTTP 요청이 아니므로 requestId가 없다. 배치 시작 시 `MDC.put("batchId", UUID.randomUUID())`를 직접 넣고 `finally`에서 `MDC.clear()`하는 패턴을 고려해볼 것
- **배치 로그의 3요소**: 시작 로그 + 완료 요약(건수, 시간) + 실패 시 에러 로그
- `@Scheduled(cron = ...)` — cron 표현식으로 실행 주기 제어
- HTTP 상태 코드 `410 Gone`은 "이전에 존재했지만 더 이상 없다"를 의미 — 404와의 차이

---

## Step 4: URL 유효성 검증 + Resilience4j 서킷브레이커

**커버**: 항목 4 (외부 시스템 호출), 항목 8 보강 (서킷브레이커), 항목 7 보강 (외부 호출 지연)
**난이도**: 중간 (2~3시간)

### 왜 필요한가

프로덕션 장애의 대부분은 **외부 시스템 문제**다.
URL 단축 생성 시 원본 URL이 실제로 접근 가능한지 HTTP HEAD 요청으로 확인하면,
외부 호출 → 타임아웃 → 재시도 → 서킷브레이커 열림 → 복구라는 전체 시나리오를 연습할 수 있다.

**테스트가 쉽다**: 존재하지 않는 URL을 입력하면 자연스럽게 실패 시나리오가 만들어진다.

### 구현 방법

#### 4-1. 의존성 추가 (`pom.xml`)

```xml
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
    <version>2.2.0</version>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

> Resilience4j의 `@CircuitBreaker`, `@Retry` 어노테이션은 AOP 기반이므로 `spring-boot-starter-aop`가 필요하다.

#### 4-2. Resilience4j 설정 (`application.yaml`)

```yaml
resilience4j:
  circuitbreaker:
    instances:
      urlValidator:
        failure-rate-threshold: 50          # 실패율 50% 넘으면 OPEN
        slow-call-rate-threshold: 80        # 느린 호출 80% 넘으면 OPEN
        slow-call-duration-threshold: 2s    # 2초 이상이면 "느린 호출"
        sliding-window-size: 10             # 최근 10건 기준
        wait-duration-in-open-state: 30s    # OPEN 상태 30초 유지 후 HALF_OPEN
        permitted-number-of-calls-in-half-open-state: 3
  retry:
    instances:
      urlValidator:
        max-attempts: 3
        wait-duration: 500ms
        retry-exceptions:
          - java.net.SocketTimeoutException
          - java.net.ConnectException
```

#### 4-3. URL 유효성 검증 서비스 — 새 파일: `application/UrlValidationService.java`

```java
@Slf4j
@Service
public class UrlValidationService {

    private final RestClient restClient;

    public UrlValidationService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
                .connectTimeout(Duration.ofSeconds(3))
                .readTimeout(Duration.ofSeconds(3))
                .build();
    }

    @CircuitBreaker(name = "urlValidator", fallbackMethod = "fallback")
    @Retry(name = "urlValidator")
    public boolean validateUrl(String url) {
        long startTime = System.nanoTime();
        try {
            restClient.head()
                    .uri(url)
                    .retrieve()
                    .toBodilessEntity();

            long durationMs = (System.nanoTime() - startTime) / 1_000_000;
            log.info("[EXTERNAL] HEAD {} → OK ({}ms)", url, durationMs);
            return true;

        } catch (Exception e) {
            long durationMs = (System.nanoTime() - startTime) / 1_000_000;
            log.warn("[EXTERNAL] HEAD {} → FAILED ({}, {}ms)", url, e.getClass().getSimpleName(), durationMs);
            throw e;  // Resilience4j가 재시도/서킷브레이커 판단
        }
    }

    // 서킷브레이커가 OPEN일 때 호출되는 폴백
    private boolean fallback(String url, Exception e) {
        log.warn("[CIRCUIT] URL 검증 스킵 (서킷 OPEN), url={}, reason={}", url, e.getMessage());
        return true;  // 서킷 열리면 검증 건너뜀 (URL 생성은 허용)
    }
}
```

#### 4-4. 서킷브레이커 상태 변경 이벤트 로깅

Resilience4j는 상태 변경 이벤트를 발행한다. 이를 로그로 남기는 리스너를 등록한다.

```java
@Slf4j
@Component
public class CircuitBreakerEventLogger {

    public CircuitBreakerEventLogger(CircuitBreakerRegistry registry) {
        registry.circuitBreaker("urlValidator")
                .getEventPublisher()
                .onStateTransition(event ->
                    log.warn("[CIRCUIT] urlValidator: {} → {}",
                            event.getStateTransition().getFromState(),
                            event.getStateTransition().getToState())
                );
    }
}
```

이 클래스는 `infrastructure/CircuitBreakerEventLogger.java`에 둔다.

#### 4-5. 서비스 연동 (`SimpleShortenUrlService.java`)

```java
public ShortenUrlCreateResponseDto generateShortenUrl(...) {
    String originalUrl = shortenUrlCreateRequestDto.getOriginalUrl();

    // URL 유효성 검증 (외부 HTTP 호출)
    urlValidationService.validateUrl(originalUrl);

    // ... 기존 키 생성, 저장 로직 ...
}
```

#### 4-6. RestClient 빈 설정

Spring Boot 3.2+ 부터 `RestClient.Builder`가 자동 주입된다.
별도 설정 없이 `UrlValidationService` 생성자에서 `RestClient.Builder`를 받으면 된다.

### 테스트 시나리오

```bash
# 1. 정상 URL → 성공 로그
curl -X POST http://localhost:8080/shortenUrl \
  -H "Content-Type: application/json" \
  -d '{"originalUrl": "https://www.google.com"}'
# → INFO [EXTERNAL] HEAD https://www.google.com → OK (234ms)

# 2. 존재하지 않는 URL → 실패 + 재시도 로그
curl -X POST http://localhost:8080/shortenUrl \
  -H "Content-Type: application/json" \
  -d '{"originalUrl": "https://this-does-not-exist-12345.com"}'
# → WARN [EXTERNAL] HEAD https://this-does-not-exist-12345.com → FAILED (ConnectException, 3001ms)
# → WARN [EXTERNAL] HEAD ... → FAILED ... (재시도 2회)
# → WARN [EXTERNAL] HEAD ... → FAILED ... (재시도 3회)

# 3. 실패 반복 → 서킷브레이커 상태 변화
# 존재하지 않는 URL로 10번 이상 요청하면:
# → WARN [CIRCUIT] urlValidator: CLOSED → OPEN
# 이후 정상 URL로 요청해도:
# → WARN [CIRCUIT] URL 검증 스킵 (서킷 OPEN)
# 30초 후:
# → WARN [CIRCUIT] urlValidator: OPEN → HALF_OPEN
```

### 학습 포인트

- **서킷브레이커 3가지 상태**: CLOSED(정상) → OPEN(차단) → HALF_OPEN(시험) → CLOSED(복구)
- **폴백 전략**: 서킷이 열렸을 때 어떻게 할 것인가? (여기서는 검증 건너뛰기)
- **재시도 vs 서킷브레이커 순서**: `@Retry`가 먼저 시도하고, 재시도 포함해서 모두 실패하면 서킷브레이커가 카운트
- RestClient (Spring Boot 3.2+의 동기 HTTP 클라이언트) 사용법
- 외부 호출 로그의 기본 형식: `[대상] 메서드 URL → 결과 (소요시간)`

---

## Step 5: IP 기반 Rate Limiter

**커버**: 항목 5 (인증/인가 실패 — 접근 제어의 가장 간단한 형태)
**난이도**: 낮음~중간 (1~2시간)

### 왜 필요한가

Spring Security 없이도 접근 제어 로그를 연습할 수 있는 가장 간단한 방법이다.
같은 IP에서 비정상적으로 많은 요청이 오는 패턴을 탐지하고 로그를 남기는 것은,
인증/인가 로그의 핵심 시나리오(무차별 대입 공격 탐지)와 동일한 구조다.

### 구현 방법 — 두 가지 선택지

#### 선택지 A: 직접 구현 (학습 목적으로 권장)

`ConcurrentHashMap`으로 IP별 요청 수를 추적하고, 주기적으로 리셋한다.
가장 단순하지만 Rate Limiter의 원리를 이해하기에 좋다.

#### 선택지 B: Bucket4j 사용 (프로덕션에 가까운 방식)

토큰 버킷 알고리즘 기반. 의존성 추가 필요:
```xml
<dependency>
    <groupId>com.bucket4j</groupId>
    <artifactId>bucket4j-core</artifactId>
    <version>8.10.1</version>
</dependency>
```

### 선택지 A 상세 구현

#### 5-1. 설정 (`application.yaml`)

```yaml
rate-limit:
  requests-per-minute: 10
```

#### 5-2. Rate Limit 필터 — 새 파일: `presentation/RateLimitFilter.java`

```java
@Slf4j
@Component
public class RateLimitFilter implements Filter {

    private final int requestsPerMinute;
    private final ConcurrentHashMap<String, AtomicInteger> requestCounts = new ConcurrentHashMap<>();

    // @Value("${rate-limit.requests-per-minute}") 로 설정값 주입

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String clientIp = httpRequest.getRemoteAddr();

        AtomicInteger count = requestCounts.computeIfAbsent(clientIp, k -> new AtomicInteger(0));
        int currentCount = count.incrementAndGet();

        if (currentCount > requestsPerMinute) {
            log.warn("[RATE_LIMIT] 요청 한도 초과, ip={}, endpoint={} {}, limit={}/min, current={}",
                    clientIp,
                    httpRequest.getMethod(),
                    httpRequest.getRequestURI(),
                    requestsPerMinute,
                    currentCount);

            httpResponse.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            httpResponse.getWriter().write("요청 한도를 초과했습니다. 잠시 후 다시 시도해주세요.");
            return;  // chain.doFilter 호출하지 않음 → 요청 차단
        }

        chain.doFilter(request, response);
    }
}
```

#### 5-3. 카운터 초기화 스케줄러

분당 카운터를 리셋하는 스케줄 작업을 추가한다.
Step 3에서 `@EnableScheduling`을 이미 추가했으므로 바로 사용 가능하다.

```java
@Scheduled(fixedRate = 60_000)  // 1분마다
public void resetCounts() {
    if (!requestCounts.isEmpty()) {
        log.debug("[RATE_LIMIT] 카운터 초기화, trackedIps={}", requestCounts.size());
        requestCounts.clear();
    }
}
```

> 이 방식은 정확한 sliding window가 아니라 fixed window다.
> 프로덕션에서는 Bucket4j나 Redis 기반 sliding window를 쓰지만, 로그 연습에는 충분하다.

#### 5-4. 필터 순서 조정

`RateLimitFilter`는 `LoggingFilter`보다 먼저 실행되어야 한다 (차단된 요청도 로그에 남기려면 반대).
순서를 제어하려면 `@Order` 어노테이션 또는 `FilterRegistrationBean`을 사용한다.

```java
@Component
@Order(1)  // 숫자가 낮을수록 먼저 실행
public class RateLimitFilter implements Filter {
```

LoggingFilter에도 `@Order(2)`를 추가한다.

단, 여기서 설계 판단이 필요하다:
- Rate Limit 먼저 → 차단된 요청은 LoggingFilter를 거치지 않아 HTTP 경계 로그 없음
- LoggingFilter 먼저 → 차단된 요청도 HTTP 경계 로그에 429로 기록됨

보통 **LoggingFilter를 먼저** 두는 것이 좋다 (모든 요청을 빠짐없이 기록).

### 테스트 시나리오

```bash
# 빠르게 11번 요청
for i in $(seq 1 11); do
  curl -s -o /dev/null -w "%{http_code}\n" \
    -X POST http://localhost:8080/shortenUrl \
    -H "Content-Type: application/json" \
    -d '{"originalUrl": "https://example.com"}'
done

# 1~10번: 200 OK
# 11번: 429 Too Many Requests
# 로그: WARN [RATE_LIMIT] 요청 한도 초과, ip=127.0.0.1, ...
```

### 학습 포인트

- **Fixed Window vs Sliding Window** — 현재 구현의 한계와 프로덕션 방식의 차이
- **Filter 실행 순서** — `@Order`로 제어하는 방법과 순서에 따른 로그 차이
- 429 응답이 Prometheus 메트릭에 어떻게 집계되는지 (Step 2와 연결)
- Grafana에서 "분당 429 비율" 대시보드를 만들면 실시간 남용 탐지가 가능

---

## 전체 검증 체크리스트

모든 Step 완료 후, 아래 시나리오로 logging-strategy.md 11개 항목이 모두 커버되는지 확인한다.

```
□ 앱 기동 → [STARTUP] 로그 출력 (항목 9)
□ POST /shortenUrl (정상 URL) → HTTP 경계 + 외부 호출 + 비즈니스 로그 (항목 1, 3, 4)
□ POST /shortenUrl (존재하지 않는 URL) → 외부 호출 실패 + 재시도 로그 (항목 4, 8)
□ POST /shortenUrl (잘못된 형식) → 검증 실패 로그 (항목 6)
□ GET /{key} (정상) → 리다이렉트 + 비즈니스 로그 (항목 1, 3)
□ GET /{key} (없는 키) → 404 로그 (항목 2)
□ GET /{key} (만료된 키) → 410 Gone 로그 (항목 2, 3)
□ 동일 IP 11번 요청 → Rate Limit 로그 (항목 5)
□ 존재하지 않는 URL 10번+ → 서킷브레이커 OPEN 로그 (항목 8)
□ 배치 실행 대기 → [BATCH] 로그 (항목 10)
□ GET /actuator/prometheus → 커스텀 메트릭 노출 (항목 11)
□ LoggingFilter의 느린 요청 WARN (항목 7)
```
