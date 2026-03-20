# Observability 코드와 비즈니스 코드 분리: 실무 패턴 가이드

> 이 문서는 `docs/observability-developer-setup.md`의 **후속 문서**다.
> 이전 가이드에서 `@Observed`, `MeterRegistry`, `log.info(...)` 등을 비즈니스 코드에 직접 넣는 방식을 제안했다.
> 이 문서는 **"비즈니스 로직에 모니터링 코드가 섞이면 코드가 난잡해지는 문제"**를 실무에서 어떻게 해결하는지 다룬다.

---

## 1. 왜 이 고민이 생기는가

### Before: 현재 SimpleShortenUrlService (순수한 상태)

```java
@Slf4j
@Service
public class SimpleShortenUrlService {

    private ShortenUrlRepository shortenUrlRepository;

    public ShortenUrlCreateResponseDto generateShortenUrl(
            ShortenUrlCreateRequestDto shortenUrlCreateRequestDto
    ) {
        String originalUrl = shortenUrlCreateRequestDto.getOriginalUrl();
        String shortenUrlKey = getUniqueShortenUrlKey();

        ShortenUrl shortenUrl = new ShortenUrl(originalUrl, shortenUrlKey);
        shortenUrlRepository.saveShortenUrl(shortenUrl);

        return new ShortenUrlCreateResponseDto(shortenUrl);
    }
}
```

비즈니스 로직이 **5줄**이다. 읽기 쉽고, 의도가 명확하다.

### After: observability-developer-setup.md를 모두 적용하면

```java
@Slf4j
@Service
public class SimpleShortenUrlService {

    private final ShortenUrlRepository shortenUrlRepository;
    private final MeterRegistry meterRegistry;              // 모니터링 의존성 1
    private final ObservationRegistry observationRegistry;  // 모니터링 의존성 2

    @Observed(name = "shortenurl.created",                  // 메트릭+트레이스
              contextualName = "generate-shorten-url")
    public ShortenUrlCreateResponseDto generateShortenUrl(
            ShortenUrlCreateRequestDto shortenUrlCreateRequestDto
    ) {
        log.info("URL 단축 요청, originalUrl={}",                        // 로그 1
                 shortenUrlCreateRequestDto.getOriginalUrl());

        String originalUrl = shortenUrlCreateRequestDto.getOriginalUrl();
        String shortenUrlKey = getUniqueShortenUrlKey();

        ShortenUrl shortenUrl = new ShortenUrl(originalUrl, shortenUrlKey);
        shortenUrlRepository.saveShortenUrl(shortenUrl);

        log.info("URL 단축 완료, shortenUrlKey={}", shortenUrlKey);      // 로그 2

        return new ShortenUrlCreateResponseDto(shortenUrl);
    }

    private String getUniqueShortenUrlKey() {
        return Observation.createNotStarted("shortenurl.key-generation", // Span 생성
                observationRegistry).observe(() -> {
            final int MAX_RETRY_COUNT = 5;
            int count = 0;
            while (count++ < MAX_RETRY_COUNT) {
                String shortenUrlKey = ShortenUrl.generateShortenUrlKey();
                ShortenUrl shortenUrl = shortenUrlRepository
                        .findShortenUrlByShortenUrlKey(shortenUrlKey);
                if (null == shortenUrl) {
                    DistributionSummary.builder("shortenurl.key_generation.retries")  // 메트릭
                            .register(meterRegistry)
                            .record(count);
                    return shortenUrlKey;
                }
                if (count >= 3) {
                    log.warn("키 생성 재시도 {}회째", count);               // 로그 3
                }
            }
            throw new LackOfShortenUrlKeyException();
        });
    }
}
```

비즈니스 로직은 여전히 5줄인데, **모니터링 코드가 10줄 이상**이다.
의존성도 `MeterRegistry`, `ObservationRegistry`가 추가되어 생성자가 복잡해졌다.

**이게 문제인가?** — 실무에서는 의견이 갈린다. 하지만 "더 나은 방법이 있는가?"라는 질문에는 여러 답이 존재한다.

---

## 2. 실무에서 사용하는 5가지 분리 패턴

### 패턴 1: Spring AOP 커스텀 Aspect

**메커니즘**: `@Aspect` + `@Around`로 메서드 실행 전후에 로그/메트릭을 자동 처리한다.
비즈니스 코드에는 아무것도 추가하지 않는다.

**적합한 곳**: 반복적이고 구조적인 관심사 — 모든 서비스 메서드의 진입/종료 로그, 실행 시간 측정 등

#### 이 프로젝트 적용 코드

```java
package kr.co.shortenurlservice.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
public class LoggingAspect {

    // application 패키지 하위의 모든 public 메서드에 적용
    @Around("execution(* kr.co.shortenurlservice.application..*(..))")
    public Object logMethodExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().toShortString();

        log.info("[진입] {}", methodName);
        long startTime = System.currentTimeMillis();

        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - startTime;
            log.info("[종료] {} ({}ms)", methodName, duration);
            return result;
        } catch (Throwable e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[실패] {} ({}ms) - {}", methodName, duration, e.getMessage());
            throw e;
        }
    }
}
```

```java
package kr.co.shortenurlservice.infrastructure;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class MetricsAspect {

    private final MeterRegistry meterRegistry;

    public MetricsAspect(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Around("execution(* kr.co.shortenurlservice.application..*(..))")
    public Object recordMetrics(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();

        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            return joinPoint.proceed();
        } finally {
            sample.stop(Timer.builder("service.method.duration")
                    .tag("method", methodName)
                    .register(meterRegistry));
        }
    }
}
```

이렇게 하면 `SimpleShortenUrlService`는 **비즈니스 코드만** 남는다:

```java
@Service
public class SimpleShortenUrlService {

    private ShortenUrlRepository shortenUrlRepository;

    public ShortenUrlCreateResponseDto generateShortenUrl(
            ShortenUrlCreateRequestDto shortenUrlCreateRequestDto
    ) {
        String originalUrl = shortenUrlCreateRequestDto.getOriginalUrl();
        String shortenUrlKey = getUniqueShortenUrlKey();

        ShortenUrl shortenUrl = new ShortenUrl(originalUrl, shortenUrlKey);
        shortenUrlRepository.saveShortenUrl(shortenUrl);

        return new ShortenUrlCreateResponseDto(shortenUrl);
    }
    // ... 나머지 메서드도 순수한 비즈니스 로직만
}
```

#### 장점
- 비즈니스 코드 **0% 오염** — `@Slf4j`조차 필요 없다
- 새 메서드를 추가해도 자동으로 로그/메트릭이 적용된다

#### 단점
- **비즈니스 맥락을 꺼내기 어렵다** — `shortenUrlKey`가 뭔지 Aspect에서 알려면 인자를 파싱해야 한다
- **디버깅 시 흐름 추적이 어렵다** — 로그가 어디서 나오는지 코드만 봐서는 모른다
- Pointcut 표현식 실수 시 의도치 않은 메서드에 적용될 수 있다

---

### 패턴 2: HandlerInterceptor (Controller 레이어)

**메커니즘**: Spring MVC의 `preHandle` / `postHandle` / `afterCompletion`을 사용하여 HTTP 요청/응답 경계에서 로깅한다. Controller에 `log.info()`를 넣는 대신 Interceptor가 처리한다.

**적합한 곳**: HTTP 요청/응답 경계 로깅 — "어떤 요청이 들어왔고, 어떤 응답을 줬는가"

#### 이 프로젝트 적용 코드

```java
package kr.co.shortenurlservice.infrastructure;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
public class RequestLoggingInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) {
        request.setAttribute("startTime", System.currentTimeMillis());
        log.info("[요청] {} {}", request.getMethod(), request.getRequestURI());
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex
    ) {
        long startTime = (long) request.getAttribute("startTime");
        long duration = System.currentTimeMillis() - startTime;
        log.info("[응답] {} {} → {} ({}ms)",
                request.getMethod(),
                request.getRequestURI(),
                response.getStatus(),
                duration);
    }
}
```

```java
package kr.co.shortenurlservice.infrastructure;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final RequestLoggingInterceptor requestLoggingInterceptor;

    public WebConfig(RequestLoggingInterceptor requestLoggingInterceptor) {
        this.requestLoggingInterceptor = requestLoggingInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requestLoggingInterceptor)
                .addPathPatterns("/**");
    }
}
```

이렇게 하면 `ShortenUrlRestController`에서 `log.trace()`나 `log.info()`를 제거할 수 있다:

```java
@RestController
public class ShortenUrlRestController {

    private final SimpleShortenUrlService simpleShortenUrlService;

    // log 문이 전혀 없다 — Interceptor가 처리
    @RequestMapping(value = "/shortenUrl", method = RequestMethod.POST)
    public ResponseEntity<ShortenUrlCreateResponseDto> createShortenUrl(
            @Valid @RequestBody ShortenUrlCreateRequestDto shortenUrlCreateRequestDto
    ) {
        ShortenUrlCreateResponseDto shortenUrlCreateResponseDto
                = simpleShortenUrlService.generateShortenUrl(shortenUrlCreateRequestDto);
        return ResponseEntity.ok().body(shortenUrlCreateResponseDto);
    }

    @RequestMapping(value = "/{shortenUrlKey}", method = RequestMethod.GET)
    public ResponseEntity<?> redirectShortenUrl(
            @PathVariable String shortenUrlKey
    ) throws URISyntaxException {
        String originalUrl = simpleShortenUrlService.getOriginalUrlByShortenUrlKey(shortenUrlKey);
        URI redirectUri = new URI(originalUrl);
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setLocation(redirectUri);
        return new ResponseEntity<>(httpHeaders, HttpStatus.MOVED_PERMANENTLY);
    }
}
```

#### 장점
- Controller가 깨끗해진다 — 비즈니스 흐름만 남는다
- 모든 HTTP 엔드포인트에 일관된 로깅이 적용된다

#### 단점
- **HTTP 요청/응답 수준만 접근 가능** — 비즈니스 맥락(예: 생성된 `shortenUrlKey`)을 알 수 없다
- Controller 레이어에만 적용된다 (Service 레이어에는 별도 방법 필요)

---

### 패턴 3: Event-Driven (ApplicationEventPublisher)

**메커니즘**: 비즈니스 코드가 도메인 이벤트를 발행하면, 별도 리스너가 로그/메트릭을 처리한다.
비즈니스 코드는 "무슨 일이 일어났다"만 알리고, "그걸 어떻게 기록할지"는 모른다.

**적합한 곳**: 비즈니스 의미가 있는 이벤트 — "URL이 생성되었다", "리다이렉트가 수행되었다"

#### 이 프로젝트 적용 코드

**이벤트 클래스 정의:**

```java
package kr.co.shortenurlservice.domain;

public record ShortenUrlCreatedEvent(
        String originalUrl,
        String shortenUrlKey
) {}
```

```java
package kr.co.shortenurlservice.domain;

public record ShortenUrlRedirectedEvent(
        String shortenUrlKey,
        String originalUrl
) {}
```

**비즈니스 코드 — 이벤트만 발행:**

```java
@Service
public class SimpleShortenUrlService {

    private final ShortenUrlRepository shortenUrlRepository;
    private final ApplicationEventPublisher eventPublisher;  // 이벤트 발행자만 의존

    @Autowired
    public SimpleShortenUrlService(
            ShortenUrlRepository shortenUrlRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.shortenUrlRepository = shortenUrlRepository;
        this.eventPublisher = eventPublisher;
    }

    public ShortenUrlCreateResponseDto generateShortenUrl(
            ShortenUrlCreateRequestDto shortenUrlCreateRequestDto
    ) {
        String originalUrl = shortenUrlCreateRequestDto.getOriginalUrl();
        String shortenUrlKey = getUniqueShortenUrlKey();

        ShortenUrl shortenUrl = new ShortenUrl(originalUrl, shortenUrlKey);
        shortenUrlRepository.saveShortenUrl(shortenUrl);

        // 비즈니스 이벤트 발행 — 로그/메트릭은 리스너가 처리
        eventPublisher.publishEvent(new ShortenUrlCreatedEvent(originalUrl, shortenUrlKey));

        return new ShortenUrlCreateResponseDto(shortenUrl);
    }

    public String getOriginalUrlByShortenUrlKey(String shortenUrlKey) {
        ShortenUrl shortenUrl = shortenUrlRepository.findShortenUrlByShortenUrlKey(shortenUrlKey);

        if (null == shortenUrl) {
            throw new NotFoundShortenUrlException(
                    "단축 URL을 찾지 못했습니다. shortenUrlKey=" + shortenUrlKey);
        }

        shortenUrl.increaseRedirectCount();
        shortenUrlRepository.saveShortenUrl(shortenUrl);

        eventPublisher.publishEvent(
                new ShortenUrlRedirectedEvent(shortenUrlKey, shortenUrl.getOriginalUrl()));

        return shortenUrl.getOriginalUrl();
    }

    // getUniqueShortenUrlKey()는 그대로 — 내부 로직이므로 이벤트 발행 불필요
}
```

**Observability 리스너 — 로그와 메트릭을 여기에 집중:**

```java
package kr.co.shortenurlservice.infrastructure;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import kr.co.shortenurlservice.domain.ShortenUrlCreatedEvent;
import kr.co.shortenurlservice.domain.ShortenUrlRedirectedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ObservabilityEventListener {

    private final Counter urlCreatedCounter;
    private final Counter urlRedirectedCounter;

    public ObservabilityEventListener(MeterRegistry meterRegistry) {
        this.urlCreatedCounter = Counter.builder("shortenurl.created.count")
                .description("단축 URL 생성 횟수")
                .register(meterRegistry);
        this.urlRedirectedCounter = Counter.builder("shortenurl.redirected.count")
                .description("리다이렉트 횟수")
                .register(meterRegistry);
    }

    @EventListener
    public void onShortenUrlCreated(ShortenUrlCreatedEvent event) {
        log.info("URL 단축 완료, shortenUrlKey={}, originalUrl={}",
                event.shortenUrlKey(), event.originalUrl());
        urlCreatedCounter.increment();
    }

    @EventListener
    public void onShortenUrlRedirected(ShortenUrlRedirectedEvent event) {
        log.info("리다이렉트 수행, shortenUrlKey={}, originalUrl={}",
                event.shortenUrlKey(), event.originalUrl());
        urlRedirectedCounter.increment();
    }
}
```

#### 장점
- 비즈니스 코드는 이벤트만 발행 — **관심사 완전 분리**
- 이벤트에 비즈니스 맥락(`shortenUrlKey`, `originalUrl`)이 자연스럽게 포함된다
- 리스너를 교체하거나 추가해도 비즈니스 코드 변경 없음
- 테스트 시 이벤트 발행 여부만 검증하면 된다

#### 단점
- 이벤트 클래스 + 리스너 클래스가 추가로 필요하다
- 간접 레이어가 증가하여 "이 로그가 어디서 나오는지" 추적이 한 단계 더 필요하다
- 이벤트 발행 코드 1줄은 여전히 비즈니스 코드에 존재한다

---

### 패턴 4: Decorator 패턴

**메커니즘**: Service 인터페이스를 정의하고, 실제 비즈니스 구현체를 감싸는 Observability Decorator를 만든다.
Spring의 `@Primary`를 이용해 Controller는 Decorator를 주입받고, Decorator가 내부적으로 실제 구현체를 호출한다.

**적합한 곳**: 인터페이스가 이미 있고, 관측 로직이 복잡할 때

#### 이 프로젝트 적용 코드

**인터페이스 정의:**

```java
package kr.co.shortenurlservice.application;

import kr.co.shortenurlservice.presentation.ShortenUrlCreateRequestDto;
import kr.co.shortenurlservice.presentation.ShortenUrlCreateResponseDto;
import kr.co.shortenurlservice.presentation.ShortenUrlInformationDto;

public interface ShortenUrlService {
    ShortenUrlCreateResponseDto generateShortenUrl(
            ShortenUrlCreateRequestDto shortenUrlCreateRequestDto);

    String getOriginalUrlByShortenUrlKey(String shortenUrlKey);

    ShortenUrlInformationDto getShortenUrlInformationByShortenUrlKey(String shortenUrlKey);
}
```

**순수한 비즈니스 구현체:**

```java
@Service
public class SimpleShortenUrlService implements ShortenUrlService {
    // 현재 코드 그대로 — 모니터링 코드 0줄
    // @Slf4j도 제거 가능
}
```

**Observability Decorator:**

```java
package kr.co.shortenurlservice.infrastructure;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import kr.co.shortenurlservice.application.ShortenUrlService;
import kr.co.shortenurlservice.presentation.ShortenUrlCreateRequestDto;
import kr.co.shortenurlservice.presentation.ShortenUrlCreateResponseDto;
import kr.co.shortenurlservice.presentation.ShortenUrlInformationDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Primary  // Controller가 이 Decorator를 주입받도록
public class ObservableShortenUrlService implements ShortenUrlService {

    private final ShortenUrlService delegate;  // 실제 비즈니스 구현체
    private final Counter urlCreatedCounter;
    private final Timer urlCreationTimer;

    public ObservableShortenUrlService(
            @Qualifier("simpleShortenUrlService") ShortenUrlService delegate,
            MeterRegistry meterRegistry
    ) {
        this.delegate = delegate;
        this.urlCreatedCounter = Counter.builder("shortenurl.created.count")
                .register(meterRegistry);
        this.urlCreationTimer = Timer.builder("shortenurl.created.duration")
                .register(meterRegistry);
    }

    @Override
    public ShortenUrlCreateResponseDto generateShortenUrl(
            ShortenUrlCreateRequestDto shortenUrlCreateRequestDto
    ) {
        log.info("URL 단축 요청, originalUrl={}",
                shortenUrlCreateRequestDto.getOriginalUrl());

        return urlCreationTimer.record(() -> {
            ShortenUrlCreateResponseDto response =
                    delegate.generateShortenUrl(shortenUrlCreateRequestDto);

            log.info("URL 단축 완료, shortenUrlKey={}",
                    response.getShortenUrlKey());
            urlCreatedCounter.increment();

            return response;
        });
    }

    @Override
    public String getOriginalUrlByShortenUrlKey(String shortenUrlKey) {
        log.info("리다이렉트 요청, shortenUrlKey={}", shortenUrlKey);
        return delegate.getOriginalUrlByShortenUrlKey(shortenUrlKey);
    }

    @Override
    public ShortenUrlInformationDto getShortenUrlInformationByShortenUrlKey(String shortenUrlKey) {
        return delegate.getShortenUrlInformationByShortenUrlKey(shortenUrlKey);
    }
}
```

#### 장점
- 비즈니스 구현체가 **100% 순수** — 테스트 시 Decorator 없이 테스트 가능
- 관측 로직이 복잡해져도 비즈니스 코드는 건드리지 않는다

#### 단점
- **인터페이스 + 2개 구현체**가 필요 — 작은 프로젝트에서는 과도한 구조
- 메서드마다 위임(delegate) 코드를 작성해야 한다
- 인터페이스에 메서드를 추가할 때마다 Decorator도 업데이트해야 한다

---

### 패턴 5: 하이브리드 (실무에서 가장 많이 쓰는 방식)

위 패턴들을 **역할에 따라 조합**한다. "완벽한 분리"보다 "적절한 균형"이 목표다.

| 관심사 | 처리 방식 | 이유 |
|--------|----------|------|
| 진입/종료 로그, 실행 시간 | AOP 또는 `@Observed` | 구조적이고 반복적 — 자동화 적합 |
| HTTP 요청/응답 로깅 | HandlerInterceptor | Controller를 깨끗하게 유지 |
| 비즈니스 이벤트 로그 | **비즈니스 코드에 직접** (1~2줄) | 맥락이 필요한 로그는 그 자리에 있어야 디버깅이 쉬움 |
| 비즈니스 메트릭 | `@Observed` 어노테이션 | 코드 1줄로 메트릭+Span 동시 확보 |
| 예외 로깅 | GlobalExceptionHandler | 예외 처리가 이미 집중되어 있으므로 로그도 여기서 |

핵심 원칙: **"1~2줄의 비즈니스 로그는 비즈니스 코드에 있는 게 맞다."**
`log.info("URL 단축 완료, shortenUrlKey={}", shortenUrlKey)` 이 한 줄이 코드를 오염시킨다고 보기는 어렵다.
오히려 이 로그가 다른 파일에 있으면, 디버깅할 때 "이 로그가 대체 어디서 나오는 거지?"를 찾느라 시간을 쓴다.

---

## 3. 시니어 엔지니어들의 관점 비교

같은 팀의 시니어 엔지니어 3명에게 물어보면, 3개의 다른 답이 나올 수 있다:

| 관점 | 주장 | 근거 |
|------|------|------|
| **분리 파** | 비즈니스 코드에 `log` 한 줄도 넣지 말라 | SRP(단일 책임 원칙) 위반. 비즈니스 로직 변경과 모니터링 변경이 같은 파일에서 일어나면 안 된다. 테스트할 때 로그 검증이 불필요하게 끼어든다 |
| **실용주의 파** | 비즈니스 맥락을 담은 로그는 비즈니스 코드에 속한다 | 과도한 추상화는 디버깅을 더 어렵게 만든다. "로그가 어디서 나오는지 모르겠다"는 불만이 더 크다. 코드 리뷰 시 비즈니스 흐름과 로그를 함께 볼 수 있어야 한다 |
| **업계 주류** | 하이브리드 — 구조적 관심사만 분리하고, 비즈니스 로그는 인라인 | SRP는 중요하지만, 가독성과 디버깅 용이성도 중요하다. 구조적 로그(진입/종료)는 AOP로, 비즈니스 이벤트 로그는 그 자리에 |

**핵심 논쟁: SRP vs 실용성**

SRP(단일 책임 원칙)를 엄격하게 적용하면, `SimpleShortenUrlService`는 "URL 단축"만 책임져야 한다. 로깅은 다른 책임이므로 다른 곳에 있어야 한다.

하지만 실무에서 SRP를 **문자 그대로** 적용하면:
- 로그를 보려면 Aspect/Listener/Decorator를 찾아가야 한다
- 새 팀원이 코드를 이해하는 데 시간이 더 걸린다
- 장애 대응 시 "이 로그가 어디서 나오는지" 찾는 시간이 추가된다

**결론**: SRP의 목적은 "변경 이유를 분리하는 것"이다.
`log.info()` 한 줄이 비즈니스 로직의 변경 이유를 늘리지는 않는다.
`MeterRegistry`를 주입받아서 10줄의 메트릭 코드를 작성하는 것은 변경 이유를 늘린다.

---

## 4. 프로젝트 규모별 추천

| 규모 | 추천 패턴 | 이유 |
|------|----------|------|
| **소규모** (이 프로젝트처럼 서비스 1~2개) | **하이브리드 (AOP + 인라인)** | 과도한 추상화가 오히려 복잡도를 증가시킨다. 코드 전체를 한 사람이 파악할 수 있으므로, 인라인 로그가 디버깅에 더 효과적이다 |
| **중규모** (서비스 5~10개, 팀원 3~5명) | **AOP + Event-Driven** | 팀원 간 코드 스타일 통일이 필요하다. "로그를 어디에 넣어야 하는가"에 대한 규칙이 명확해야 PR 리뷰가 원활하다 |
| **대규모 마이크로서비스** (서비스 10개+) | **Decorator + Event-Driven** | 서비스 간 일관성이 중요하다. 각 서비스의 Observability 요구사항이 다를 수 있으므로 Decorator로 커스터마이징한다 |

---

## 5. 이 프로젝트에 하이브리드 적용한 최종 코드

이 프로젝트의 규모와 학습 목적을 고려하여, **하이브리드 패턴**을 적용한다:

- **구조적 관심사** → `LoggingAspect`, `RequestLoggingInterceptor`
- **비즈니스 로그** → `SimpleShortenUrlService`에 인라인 (최소한만)
- **예외 로그** → `GlobalExceptionHandler`에 집중

### LoggingAspect.java — 진입/종료 로그 자동화

```java
package kr.co.shortenurlservice.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
public class LoggingAspect {

    @Around("execution(* kr.co.shortenurlservice.application..*(..))")
    public Object logMethodExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().toShortString();

        log.debug("[진입] {}", methodName);
        long startTime = System.currentTimeMillis();

        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - startTime;
            log.debug("[종료] {} ({}ms)", methodName, duration);
            return result;
        } catch (Throwable e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[실패] {} ({}ms) - {}", methodName, duration, e.getMessage());
            throw e;
        }
    }
}
```

> **왜 DEBUG인가?**
> 진입/종료 로그는 구조적이고 반복적이다. 프로덕션에서 항상 보일 필요는 없다.
> 문제가 의심될 때 특정 패키지의 로그 레벨을 DEBUG로 낮추면 보인다.
> 비즈니스 이벤트 로그(`log.info`)와 역할을 분리하기 위함이다.

### RequestLoggingInterceptor.java — HTTP 요청 로그 자동화

```java
package kr.co.shortenurlservice.infrastructure;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
public class RequestLoggingInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) {
        request.setAttribute("startTime", System.currentTimeMillis());
        log.info("[HTTP] {} {}", request.getMethod(), request.getRequestURI());
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex
    ) {
        long startTime = (long) request.getAttribute("startTime");
        long duration = System.currentTimeMillis() - startTime;
        log.info("[HTTP] {} {} → {} ({}ms)",
                request.getMethod(),
                request.getRequestURI(),
                response.getStatus(),
                duration);
    }
}
```

### SimpleShortenUrlService.java — 비즈니스 로그만 남김

```java
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
        String originalUrl = shortenUrlCreateRequestDto.getOriginalUrl();
        String shortenUrlKey = getUniqueShortenUrlKey();

        ShortenUrl shortenUrl = new ShortenUrl(originalUrl, shortenUrlKey);
        shortenUrlRepository.saveShortenUrl(shortenUrl);

        // 비즈니스 이벤트 로그 — 이 한 줄은 여기 있는 게 맞다
        log.info("URL 단축 완료, shortenUrlKey={}", shortenUrlKey);

        return new ShortenUrlCreateResponseDto(shortenUrl);
    }

    public String getOriginalUrlByShortenUrlKey(String shortenUrlKey) {
        ShortenUrl shortenUrl = shortenUrlRepository.findShortenUrlByShortenUrlKey(shortenUrlKey);

        if (null == shortenUrl) {
            throw new NotFoundShortenUrlException(
                    "단축 URL을 찾지 못했습니다. shortenUrlKey=" + shortenUrlKey);
        }

        shortenUrl.increaseRedirectCount();
        shortenUrlRepository.saveShortenUrl(shortenUrl);

        return shortenUrl.getOriginalUrl();
    }

    public ShortenUrlInformationDto getShortenUrlInformationByShortenUrlKey(String shortenUrlKey) {
        ShortenUrl shortenUrl = shortenUrlRepository.findShortenUrlByShortenUrlKey(shortenUrlKey);

        if (null == shortenUrl) {
            throw new NotFoundShortenUrlException(
                    "단축 URL을 찾지 못했습니다. shortenUrlKey=" + shortenUrlKey);
        }

        return new ShortenUrlInformationDto(shortenUrl);
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
```

> **Before와 비교**: 모니터링 의존성(`MeterRegistry`, `ObservationRegistry`)이 없다.
> `log.info` 1줄과 `log.warn` 1줄만 추가되었다.
> 진입/종료 로그는 `LoggingAspect`가, HTTP 로그는 `RequestLoggingInterceptor`가 처리한다.

### GlobalExceptionHandler.java — 예외 로그 집중

```java
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(LackOfShortenUrlKeyException.class)
    public ResponseEntity<String> handleLackOfShortenUrlKeyException(
            LackOfShortenUrlKeyException ex
    ) {
        log.error("단축 URL 키 생성 한도 초과", ex);
        return new ResponseEntity<>("단축 URL 자원이 부족합니다.", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(NotFoundShortenUrlException.class)
    public ResponseEntity<String> handleNotFoundShortenUrlException(
            NotFoundShortenUrlException ex
    ) {
        log.warn("URL 조회 실패: {}", ex.getMessage());
        return new ResponseEntity<>("단축 URL을 찾지 못했습니다.", HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleUnexpectedException(Exception ex) {
        log.error("예상치 못한 오류 발생", ex);
        return new ResponseEntity<>("서버 내부 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
```

### 최종 구조: 누가 무엇을 담당하는가

```
요청 흐름과 로깅 책임:

HTTP 요청
  │
  ├─ RequestLoggingInterceptor  → [HTTP] POST /shortenUrl
  │
  ├─ ShortenUrlRestController   → (로그 없음 — Interceptor가 처리)
  │
  ├─ LoggingAspect              → [진입] SimpleShortenUrlService.generateShortenUrl(..)
  │
  ├─ SimpleShortenUrlService    → URL 단축 완료, shortenUrlKey=abc123  ← 비즈니스 로그
  │
  ├─ LoggingAspect              → [종료] SimpleShortenUrlService.generateShortenUrl(..) (12ms)
  │
  ├─ RequestLoggingInterceptor  → [HTTP] POST /shortenUrl → 200 (15ms)
  │
  └─ (예외 발생 시)
     └─ GlobalExceptionHandler  → ERROR/WARN 로그 + 스택트레이스
```

이 구조에서 `SimpleShortenUrlService`에는 비즈니스 로그 **딱 2줄**만 있다:
1. `log.info("URL 단축 완료, ...")` — 핵심 비즈니스 이벤트
2. `log.warn("키 생성 재시도 ...")` — 시스템 건강 경고

나머지 모든 구조적 로그는 Aspect와 Interceptor가 자동으로 처리한다.

---

## 6. 정리: 언제 어떤 패턴을 쓸 것인가

```
"이 로그/메트릭은 모든 메서드에 공통적인가?"
  → Yes → AOP 또는 HandlerInterceptor

"이 로그는 비즈니스적 의미가 있는가?" (URL 생성됨, 결제 완료됨 등)
  → Yes, 그리고 1~2줄이면 → 인라인 (비즈니스 코드에 직접)
  → Yes, 그리고 후속 처리가 필요하면 → Event-Driven

"관측 로직이 복잡하고, 인터페이스가 이미 있는가?"
  → Yes → Decorator

"위 판단이 어렵다면?"
  → 하이브리드: 구조적 → AOP, 비즈니스 → 인라인
```

> **다음 단계**: `observability-developer-setup.md`의 Phase 5 체크리스트를 진행할 때,
> 이 문서의 하이브리드 패턴을 참고하여 `LoggingAspect`와 `RequestLoggingInterceptor`를
> 먼저 만들고, 비즈니스 코드에는 최소한의 로그만 추가한다.
