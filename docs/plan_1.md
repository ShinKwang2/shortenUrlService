# Plan 1: 요청 추적 가능한 로그 (MDC requestId)

> **인프라 단계**: 단일 서버
> **핵심 질문**: "이 에러가 어떤 요청에서 발생한 건가?"

---

## 이 단계의 목표

로그 한 줄만 봐도 **어떤 요청의 로그인지** 즉시 식별할 수 있게 만든다.
동시 요청이 100개 들어와도, 특정 요청의 전체 흐름을 한 번에 추적할 수 있는 기반을 마련한다.

---

## 왜 이 단계가 필요한가

### 현재 겪는 고통

새벽 3시에 "URL 단축이 안 된다"는 신고가 들어왔다고 가정하자.

로그 파일을 열면 이런 상태다:

```
2026-03-24 03:00:01 INFO [http-nio-8080-exec-1] ShortenUrlRestController - URL 단축 요청, originalUrl=https://naver.com
2026-03-24 03:00:01 INFO [http-nio-8080-exec-3] ShortenUrlRestController - URL 단축 요청, originalUrl=https://google.com
2026-03-24 03:00:01 INFO [http-nio-8080-exec-2] ShortenUrlRestController - 리다이렉트 요청, shortenUrlKey=abc123
2026-03-24 03:00:01 ERROR [http-nio-8080-exec-1] GlobalExceptionHandler - 예상치 못한 오류 발생
java.lang.NullPointerException: ...
2026-03-24 03:00:01 INFO [http-nio-8080-exec-3] SimpleShortenUrlService - shortenUrl 생성: ShortenUrl{...}
2026-03-24 03:00:01 INFO [http-nio-8080-exec-1] ShortenUrlRestController - URL 단축 완료, shortenUrlKey=xyz789
```

**문제**: exec-1 스레드에서 ERROR가 났다. 그런데 exec-1이 처리한 요청이 `naver.com`인지 `google.com`인지 확신할 수 없다. 스레드 이름으로 추측은 가능하지만, **스레드 풀은 스레드를 재사용**하기 때문에 exec-1이 한 요청을 끝내고 다른 요청을 처리할 수 있다. 동시 요청이 많아지면 스레드 이름만으로는 요청을 추적할 수 없다.

### 이 단계 없이는 왜 안 되는가

- **스레드 이름은 요청 ID가 아니다**: 톰캣 스레드 풀이 스레드를 재사용하므로, 같은 스레드 이름이 시간대에 따라 다른 요청을 처리한다
- **로그 순서는 보장되지 않는다**: 멀티스레드 환경에서 로그는 시간 순서대로 뒤섞인다. 한 요청의 시작 → 처리 → 완료 로그가 연속되지 않는다
- **grep으로 찾을 키가 없다**: `originalUrl`로 검색하면 시작 로그는 찾지만, 그 요청의 서비스 레이어 로그나 에러 로그를 찾을 방법이 없다

---

## AS-IS (현재 상태)

### LoggingFilter.java (현재 코드)

```java
@Slf4j
@Component
public class LoggingFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest httpServletRequest) {
            CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(httpServletRequest);

            String url = wrappedRequest.getRequestURI();
            String method = wrappedRequest.getMethod();
            String body = wrappedRequest.getReader().lines().reduce("", String::concat);

            log.trace("Incoming Request: URL={}, Method={}, Body={}", url, method, body);

            chain.doFilter(wrappedRequest, response);
        } else {
            chain.doFilter(request, response);
        }
    }
}
```

### logback.xml 콘솔 패턴 (현재)

```xml
<pattern>%d{yyyy-MM-dd HH:mm:ss} %-5level [%thread] %logger{36} - %msg%n</pattern>
```

### 현재 상태의 한계

1. **로그 간 상관관계 없음**: Controller 로그와 Service 로그, ExceptionHandler 로그를 연결할 수단이 없다
2. **TRACE 레벨 로그가 출력되지 않음**: root 로거가 INFO인데 `log.trace()`를 사용 → 해당 로그가 아예 보이지 않는다
3. **응답 정보 없음**: 요청이 들어왔다는 건 알지만, 어떤 status code로 응답했는지, 얼마나 걸렸는지 모른다

### 현재 로그 출력 예시

```
2026-03-24 12:00:01 INFO  [http-nio-8080-exec-1] k.c.s.p.ShortenUrlRestController - URL 단축 요청, originalUrl=https://naver.com
2026-03-24 12:00:01 INFO  [http-nio-8080-exec-1] k.c.s.a.SimpleShortenUrlService - shortenUrl 생성: ShortenUrl{originalUrl='https://naver.com', shortenUrlKey='abc123', redirectCount=0}
2026-03-24 12:00:01 INFO  [http-nio-8080-exec-1] k.c.s.p.ShortenUrlRestController - URL 단축 완료, shortenUrlKey=abc123
```

→ 지금은 요청이 1건이라 스레드명으로 추적 가능하지만, 동시 요청이 늘어나면 불가능하다.

---

## TO-BE (목표 상태)

### LoggingFilter.java (변경 후)

```java
@Slf4j
@Component
public class LoggingFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest httpServletRequest) {
            CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(httpServletRequest);

            // 1. requestId 생성 (X-Request-Id 헤더가 있으면 사용, 없으면 UUID 8자리)
            String requestId = Optional.ofNullable(httpServletRequest.getHeader("X-Request-Id"))
                    .orElse(UUID.randomUUID().toString().substring(0, 8));

            // 2. MDC에 요청 컨텍스트 주입
            MDC.put("requestId", requestId);
            MDC.put("method", wrappedRequest.getMethod());
            MDC.put("uri", wrappedRequest.getRequestURI());

            String body = wrappedRequest.getReader().lines().reduce("", String::concat);

            log.debug("요청 수신: {} {} body={}", wrappedRequest.getMethod(),
                    wrappedRequest.getRequestURI(), body);

            try {
                long startTime = System.nanoTime();

                chain.doFilter(wrappedRequest, response);

                // 3. 응답 로깅
                long durationMs = (System.nanoTime() - startTime) / 1_000_000;
                int statusCode = ((HttpServletResponse) response).getStatus();
                log.info("요청 완료: {} {} → {} ({}ms)",
                        wrappedRequest.getMethod(), wrappedRequest.getRequestURI(),
                        statusCode, durationMs);
            } finally {
                // 4. MDC 정리 (스레드 풀 재사용 시 오염 방지)
                MDC.clear();
            }
        } else {
            chain.doFilter(request, response);
        }
    }
}
```

### logback.xml 콘솔 패턴 (변경 후)

```xml
<pattern>%d{yyyy-MM-dd HH:mm:ss} %-5level [%thread] [%X{requestId}] %logger{36} - %msg%n</pattern>
```

### LoggingFilter 전용 로거 (추가)

```xml
<logger name="kr.co.shortenurlservice.presentation.LoggingFilter" level="DEBUG" />
```

### 변경 후 로그 출력 예시

```
2026-03-24 12:00:01 DEBUG [http-nio-8080-exec-1] [a1b2c3d4] k.c.s.p.LoggingFilter - 요청 수신: POST /shortenUrl body={"originalUrl":"https://naver.com"}
2026-03-24 12:00:01 INFO  [http-nio-8080-exec-1] [a1b2c3d4] k.c.s.p.ShortenUrlRestController - URL 단축 요청, originalUrl=https://naver.com
2026-03-24 12:00:01 INFO  [http-nio-8080-exec-3] [f5e6d7c8] k.c.s.p.ShortenUrlRestController - URL 단축 요청, originalUrl=https://google.com
2026-03-24 12:00:01 INFO  [http-nio-8080-exec-1] [a1b2c3d4] k.c.s.a.SimpleShortenUrlService - shortenUrl 생성: ShortenUrl{...}
2026-03-24 12:00:01 ERROR [http-nio-8080-exec-3] [f5e6d7c8] k.c.s.p.GlobalExceptionHandler - 예상치 못한 오류 발생
2026-03-24 12:00:01 INFO  [http-nio-8080-exec-1] [a1b2c3d4] k.c.s.p.ShortenUrlRestController - URL 단축 완료, shortenUrlKey=abc123
2026-03-24 12:00:01 INFO  [http-nio-8080-exec-1] [a1b2c3d4] k.c.s.p.LoggingFilter - 요청 완료: POST /shortenUrl → 200 (45ms)
2026-03-24 12:00:01 INFO  [http-nio-8080-exec-3] [f5e6d7c8] k.c.s.p.LoggingFilter - 요청 완료: POST /shortenUrl → 500 (12ms)
```

→ `grep "f5e6d7c8" application.log` 한 번이면 에러 요청의 전체 흐름이 보인다.

---

## 변경 항목별 상세 설명

### 항목 1: MDC에 requestId 주입

#### 무엇을
모든 HTTP 요청에 고유 ID(8자리 문자열)를 부여하고, 해당 요청이 처리되는 동안 발생하는 **모든 로그에 자동으로 포함**시킨다.

#### 왜 필요한가

동시에 POST /shortenUrl 요청이 10개 들어왔다고 하자:

```
12:00:01 INFO - URL 단축 요청, originalUrl=https://a.com
12:00:01 INFO - URL 단축 요청, originalUrl=https://b.com
12:00:01 INFO - shortenUrl 생성: ShortenUrl{...}
12:00:01 ERROR - 예상치 못한 오류 발생
12:00:01 INFO - shortenUrl 생성: ShortenUrl{...}
12:00:01 INFO - URL 단축 완료, shortenUrlKey=abc
12:00:01 INFO - URL 단축 완료, shortenUrlKey=def
```

ERROR가 `a.com`에서 난 건지 `b.com`에서 난 건지 **알 수 없다**. requestId가 있으면:

```
12:00:01 INFO  [req-001] - URL 단축 요청, originalUrl=https://a.com
12:00:01 INFO  [req-002] - URL 단축 요청, originalUrl=https://b.com
12:00:01 INFO  [req-001] - shortenUrl 생성: ShortenUrl{...}
12:00:01 ERROR [req-002] - 예상치 못한 오류 발생
```

→ `req-002`가 `b.com` 요청이고, 거기서 에러가 났음을 **즉시** 알 수 있다.

#### 왜 이 방식인가 (대안 비교)

| 방식 | 장점 | 단점 |
|------|------|------|
| **메서드 파라미터로 전달** | 명시적 | 모든 메서드 시그니처 변경 필요, Service → Repository까지 전파해야 함 |
| **ThreadLocal 직접 사용** | 간단 | SLF4J MDC도 ThreadLocal 기반이므로 중복 구현 |
| **MDC (선택)** | SLF4J 표준, logback이 자동 읽음 | 비동기 처리 시 별도 전파 필요 (현재 비동기 미사용이므로 문제 없음) |

MDC는 SLF4J의 표준 메커니즘이다. `MDC.put("key", "value")`만 하면 같은 스레드에서 발생하는 모든 로그에 자동 포함된다. 코드를 전혀 수정하지 않아도 Controller, Service, Repository의 모든 로그에 requestId가 나타난다.

#### 왜 UUID 8자리인가

- 풀 UUID(36자): `550e8400-e29b-41d4-a716-446655440000` → 로그 한 줄이 길어져 가독성 저하
- 8자리: `a1b2c3d4` → 16^8 = **약 42억 가지 조합**. 동시 요청 수천 건에서도 충돌 확률이 극히 낮음
- 로그 파일에서 `grep`할 때도 8자리가 적당한 검색어 길이

#### 왜 X-Request-Id 헤더를 우선하는가

- API Gateway(nginx, Kong 등)나 프론트엔드가 이미 요청 ID를 생성해서 헤더에 넣는 경우가 있다
- 이때 서버가 별도 ID를 생성하면, Gateway 로그와 서버 로그를 연결할 수 없다
- 헤더가 있으면 그 값을 사용하여 **요청의 전체 경로를 일관된 ID로 추적** 가능

### 항목 2: 응답 로깅 (status code + duration)

#### 무엇을
`chain.doFilter()` 전후로 시간을 측정하여, 요청이 완료된 후 **HTTP 상태 코드**와 **처리 시간(ms)**을 기록한다.

#### 왜 필요한가

현재 로그로 알 수 있는 것:
- "요청이 들어왔다" (Controller의 `log.info`)

현재 로그로 알 수 **없는** 것:
- 그 요청이 **성공했는지 실패했는지** (200? 404? 500?)
- 그 요청이 **얼마나 걸렸는지** (10ms? 5000ms?)

"사이트가 느려요"라는 신고에 대응할 때:
- AS-IS: "느린 건 알겠는데 어떤 API가 얼마나 걸렸는지 모르겠음" → APM 도구 없이는 추적 불가
- TO-BE: `grep "→ 200" application.log | sort -t'(' -k2 -n` → 느린 요청이 바로 보인다

#### 왜 doFilter 전후에서 측정하는가

- `System.nanoTime()`: 나노초 단위 정밀도, `System.currentTimeMillis()`보다 정확
- Filter에서 측정하면 **Controller + Service + Repository 전체 처리 시간**을 포함
- 만약 Controller에서만 측정하면 직렬화/역직렬화, 인터셉터 시간이 빠진다

### 항목 3: logback.xml 패턴에 requestId 추가

#### 무엇을
콘솔과 파일 appender의 패턴에 `[%X{requestId}]`를 추가한다.

#### 왜 필요한가
MDC에 값을 넣는 것과 로그에 출력하는 것은 **별개**다. MDC는 저장소일 뿐이고, logback 패턴에 `%X{key}`를 명시해야 실제 로그에 나타난다.

패턴이 없으면:
```
2026-03-24 12:00:01 INFO [http-nio-8080-exec-1] ShortenUrlRestController - URL 단축 요청
```

패턴을 추가하면:
```
2026-03-24 12:00:01 INFO [http-nio-8080-exec-1] [a1b2c3d4] ShortenUrlRestController - URL 단축 요청
```

참고: LogstashEncoder(JSON 출력)는 MDC 값을 **자동으로 JSON 필드로 포함**한다. 별도 패턴 수정 불필요.

### 항목 4: LoggingFilter 전용 로거 레벨

#### 무엇을
`logback.xml`에 LoggingFilter 클래스 전용 로거를 추가하여 DEBUG 레벨을 설정한다.

#### 왜 필요한가

현재 상태:
- `LoggingFilter`에서 `log.trace()` 사용
- root 로거 레벨: `INFO`
- 결과: **TRACE < INFO 이므로 로그가 출력되지 않는다**

해결 방법 비교:

| 방법 | 결과 |
|------|------|
| root 레벨을 TRACE로 변경 | Spring 프레임워크 내부 로그(Hibernate, Tomcat 등)가 **수천 줄** 출력 → 사용 불가 |
| LoggingFilter 전용 로거를 DEBUG로 설정 | LoggingFilter의 DEBUG 이상만 출력, 다른 클래스는 INFO 유지 |

TO-BE에서는 `log.trace()` → `log.debug()`로 변경하고, 전용 로거로 DEBUG를 허용한다.

---

## 이점

### 시나리오: "특정 사용자의 URL 단축이 실패했다"

**AS-IS 대응 과정:**
1. 에러 시각을 확인한다
2. 로그 파일에서 해당 시각의 ERROR를 찾는다
3. ERROR 로그 위아래를 눈으로 훑으며 같은 스레드의 로그를 찾는다
4. 동시 요청이 많으면 스레드가 재사용되어 **잘못된 로그를 연결**할 위험이 있다
5. 소요 시간: **10~30분** (동시 요청이 적으면 빠르지만, 많으면 확신할 수 없다)

**TO-BE 대응 과정:**
1. 에러 시각을 확인한다
2. `grep "ERROR" application.log` → requestId 확인 (예: `f5e6d7c8`)
3. `grep "f5e6d7c8" application.log` → 해당 요청의 전체 흐름이 나온다
4. 소요 시간: **30초**

### 시나리오: "사이트가 전반적으로 느리다"

**AS-IS**: 느린지조차 확인할 방법이 없다 (응답 시간이 로그에 없으므로)

**TO-BE**: `grep "요청 완료" application.log | awk -F'[()]' '{print $2}'` → 각 요청의 처리 시간 목록 확인 가능

---

## 수정 대상 파일

| 파일 | 변경 내용 |
|------|----------|
| `src/main/java/.../presentation/LoggingFilter.java` | MDC requestId 주입, 응답 로깅, TRACE→DEBUG 변경 |
| `src/main/resources/logback.xml` | 콘솔/파일 패턴에 `[%X{requestId}]` 추가, LoggingFilter 전용 로거 추가 |

---

## 검증 방법

### 1. requestId 출력 확인
앱을 기동하고 POST /shortenUrl 요청을 보낸다. 콘솔 로그에 `[xxxxxxxx]` 형태의 requestId가 모든 로그 라인에 나타나는지 확인한다.

### 2. 동시 요청 시 구분 확인
두 개의 터미널에서 **동시에** 요청을 보낸다:
```bash
curl -X POST http://localhost:8080/shortenUrl -H "Content-Type: application/json" -d '{"originalUrl":"https://a.com"}' &
curl -X POST http://localhost:8080/shortenUrl -H "Content-Type: application/json" -d '{"originalUrl":"https://b.com"}' &
```
각 요청의 requestId가 **서로 다른 값**이고, 같은 requestId의 로그를 grep하면 하나의 요청 흐름만 나오는지 확인한다.

### 3. 응답 로깅 확인
"요청 완료" 로그에 status code(200, 404 등)와 처리 시간(ms)이 나타나는지 확인한다.

### 4. X-Request-Id 헤더 테스트
```bash
curl -X POST http://localhost:8080/shortenUrl -H "X-Request-Id: my-custom-id" -H "Content-Type: application/json" -d '{"originalUrl":"https://test.com"}'
```
로그에 `[my-custom-id]`가 나타나는지 확인한다.
