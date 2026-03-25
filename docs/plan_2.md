# Plan 2: 병목 감지 로그 (duration, StructuredArguments, slow request)

> **인프라 단계**: 단일 서버
> **선행 조건**: Plan 1 완료 (requestId, 응답 로깅)
> **핵심 질문**: "전체 요청이 느린 건 알겠는데, 내부 어디서 느린 건가?"

---

## 이 단계의 목표

요청의 **어느 단계에서 병목이 발생하는지** 수치로 파악할 수 있게 만든다.
또한 로그 데이터를 **검색/필터링 가능한 구조화된 형태**로 전환하여, 향후 ELK/Loki에서 활용할 기반을 마련한다.

---

## 왜 이 단계가 필요한가

### Plan 1 이후의 한계

Plan 1에서 응답 로깅을 추가하여 "POST /shortenUrl → 200 (850ms)"처럼 전체 처리 시간을 알 수 있게 되었다. 하지만:

1. **내부 어디가 느린지 모른다**: 850ms가 키 생성에서 걸린 건지, DB 저장에서 걸린 건지, 직렬화에서 걸린 건지 알 수 없다
2. **느린 요청을 찾으려면 모든 로그를 뒤져야 한다**: INFO 레벨 로그 수만 줄에서 duration 값을 일일이 비교해야 한다
3. **로그 데이터가 문자열에 묻혀 있다**: `originalUrl=https://naver.com`이 메시지 안에 포함되어, 프로그래밍 방식으로 필드를 추출하려면 정규표현식이 필요하다

### 실제 시나리오

> "오후 2시부터 URL 단축 응답이 느려졌다는 신고가 들어왔다. 키 공간이 고갈되어 재시도가 많아진 건지, DB가 느려진 건지 확인해야 한다."

**Plan 1만으로**: 전체 요청 시간이 느려진 건 확인 가능. 하지만 원인이 키 생성인지 DB인지 구분 불가.

---

## AS-IS (현재 상태 — Plan 1 완료 후)

### 로그 메시지 형태

```java
// ShortenUrlRestController.java
log.info("URL 단축 요청, originalUrl={}", shortenUrlCreateRequestDto.getOriginalUrl());
log.info("URL 단축 완료, shortenUrlKey={}", shortenUrlCreateResponseDto.getShortenUrlKey());
log.info("리다이렉트 요청, shortenUrlKey={}", shortenUrlKey);

// SimpleShortenUrlService.java
log.info("shortenUrl 생성: {}", shortenUrl);
log.warn("키 생성 재시도 {}회째, 키 공간 고갈 가능성 주의", count);

// GlobalExceptionHandler.java
log.error("단축 URL 키 생성 한도 초과", ex);
log.info(ex.getMessage());
log.error("예상치 못한 오류 발생", ex);
log.debug("잘못된 요청: {}", errorMessage);
```

### 출력되는 JSON (LogstashEncoder 경유)

```json
{
  "@timestamp": "2026-03-24T12:00:01.123Z",
  "message": "URL 단축 요청, originalUrl=https://naver.com",
  "logger_name": "k.c.s.p.ShortenUrlRestController",
  "level": "INFO",
  "requestId": "a1b2c3d4"
}
```

→ `originalUrl`이 `message` 문자열 안에 묻혀 있다. JSON 필드로 독립되어 있지 않아서, Kibana에서 `originalUrl` 필드로 필터링할 수 없다.

### 한계 정리

| 문제 | 상세 |
|------|------|
| 내부 단계별 시간 측정 없음 | 키 생성, DB 저장 각각 몇 ms인지 모름 |
| 느린 요청 식별 어려움 | 모든 INFO 로그에서 duration을 일일이 비교해야 함 |
| 로그 필드가 구조화되지 않음 | `originalUrl`, `shortenUrlKey`가 문자열에 묻힘 → 검색/집계 불가 |

---

## TO-BE (목표 상태)

### 1. StructuredArguments 적용

```java
import static net.logstash.logback.argument.StructuredArguments.kv;

// ShortenUrlRestController.java — before
log.info("URL 단축 요청, originalUrl={}", shortenUrlCreateRequestDto.getOriginalUrl());

// ShortenUrlRestController.java — after
log.info("URL 단축 요청", kv("originalUrl", shortenUrlCreateRequestDto.getOriginalUrl()));
```

**콘솔 출력** (사람이 읽는 용도):
```
2026-03-24 12:00:01 INFO [http-nio-8080-exec-1] [a1b2c3d4] ShortenUrlRestController - URL 단축 요청 originalUrl=https://naver.com
```

**JSON 출력** (기계가 읽는 용도 — LogstashEncoder):
```json
{
  "@timestamp": "2026-03-24T12:00:01.123Z",
  "message": "URL 단축 요청 originalUrl=https://naver.com",
  "originalUrl": "https://naver.com",
  "requestId": "a1b2c3d4",
  "level": "INFO"
}
```

→ `"originalUrl"`이 **독립된 JSON 필드**로 존재. Kibana에서 `originalUrl: "naver.com"` 필터링 가능.

### 2. 서비스 레이어 duration 측정

```java
// SimpleShortenUrlService.java — before
public ShortenUrlCreateResponseDto generateShortenUrl(...) {
    String originalUrl = shortenUrlCreateRequestDto.getOriginalUrl();
    String shortenUrlKey = getUniqueShortenUrlKey();

    ShortenUrl shortenUrl = new ShortenUrl(originalUrl, shortenUrlKey);
    shortenUrlRepository.saveShortenUrl(shortenUrl);
    log.info("shortenUrl 생성: {}", shortenUrl);
    ...
}

// SimpleShortenUrlService.java — after
public ShortenUrlCreateResponseDto generateShortenUrl(...) {
    String originalUrl = shortenUrlCreateRequestDto.getOriginalUrl();

    long keyGenStart = System.nanoTime();
    String shortenUrlKey = getUniqueShortenUrlKey();
    long keyGenDurationMs = (System.nanoTime() - keyGenStart) / 1_000_000;

    ShortenUrl shortenUrl = new ShortenUrl(originalUrl, shortenUrlKey);

    long saveStart = System.nanoTime();
    shortenUrlRepository.saveShortenUrl(shortenUrl);
    long saveDurationMs = (System.nanoTime() - saveStart) / 1_000_000;

    log.info("shortenUrl 생성",
            kv("shortenUrlKey", shortenUrlKey),
            kv("originalUrl", originalUrl),
            kv("keyGenDurationMs", keyGenDurationMs),
            kv("saveDurationMs", saveDurationMs));
    ...
}
```

### 3. Slow Request 감지 (LoggingFilter.java)

```java
// LoggingFilter.java — 응답 로깅 부분 변경
long durationMs = (System.nanoTime() - startTime) / 1_000_000;
int statusCode = ((HttpServletResponse) response).getStatus();

if (durationMs > 500) {
    log.warn("느린 요청 감지",
            kv("statusCode", statusCode),
            kv("durationMs", durationMs),
            kv("uri", wrappedRequest.getRequestURI()),
            kv("method", wrappedRequest.getMethod()));
} else {
    log.info("요청 완료",
            kv("statusCode", statusCode),
            kv("durationMs", durationMs));
}
```

### 4. ExceptionHandler 구조화

```java
// GlobalExceptionHandler.java — before
log.info(ex.getMessage());

// GlobalExceptionHandler.java — after
log.info("단축 URL 미발견", kv("shortenUrlKey", extractKeyFromMessage(ex.getMessage())));
```

```java
// GlobalExceptionHandler.java — before
log.debug("잘못된 요청: {}", errorMessage);

// GlobalExceptionHandler.java — after
log.debug("유효성 검증 실패", kv("validationErrors", errorMessage.toString()));
```

### 변경 후 로그 출력 예시 (전체 흐름)

```
12:00:01 DEBUG [exec-1] [a1b2c3d4] LoggingFilter - 요청 수신: POST /shortenUrl body={"originalUrl":"https://naver.com"}
12:00:01 INFO  [exec-1] [a1b2c3d4] ShortenUrlRestController - URL 단축 요청 originalUrl=https://naver.com
12:00:01 INFO  [exec-1] [a1b2c3d4] SimpleShortenUrlService - shortenUrl 생성 shortenUrlKey=abc123 originalUrl=https://naver.com keyGenDurationMs=320 saveDurationMs=15
12:00:01 INFO  [exec-1] [a1b2c3d4] ShortenUrlRestController - URL 단축 완료 shortenUrlKey=abc123
12:00:01 WARN  [exec-1] [a1b2c3d4] LoggingFilter - 느린 요청 감지 statusCode=200 durationMs=850 uri=/shortenUrl method=POST
```

→ `keyGenDurationMs=320`이 보인다. 키 생성에 320ms가 걸렸고, DB 저장은 15ms → **병목은 키 생성**이라는 걸 즉시 파악.

---

## 변경 항목별 상세 설명

### 항목 1: Slow Request 감지

#### 무엇을
응답 시간이 500ms를 초과하는 요청을 **자동으로 WARN 레벨로 승격**시킨다.

#### 왜 필요한가

INFO 로그가 하루에 수만 줄 쌓인다고 하자. 그 중 느린 요청만 찾으려면:

```bash
# AS-IS: 모든 로그에서 duration을 추출하여 비교해야 함
grep "요청 완료" application.log | awk -F'[()]' '{print $2}' | sort -n
```

이 방법은:
- 로그 포맷이 바뀌면 awk 패턴도 바꿔야 한다
- 실시간 모니터링이 불가능하다 (사후 분석만 가능)

```bash
# TO-BE: WARN 레벨만 필터링하면 된다
grep "WARN" application.log
```

WARN으로 승격하면:
- **Kibana/Grafana에서 레벨 필터 한 번**으로 느린 요청만 표시
- **알림 설정 가능**: WARN 로그 발생 시 Slack 알림 (추후 단계)
- **실시간 대시보드**: WARN 건수 시계열 그래프로 추세 파악

#### 왜 500ms인가

- 일반적인 웹 API SLA 기준: 200ms 이내 (좋음), 500ms 이내 (허용), 500ms 초과 (느림)
- 첫 단계에서는 하드코딩으로 시작. 추후 `application.yaml`의 설정값으로 외부화하면 재시작 없이 조절 가능
- 너무 낮으면(예: 100ms) WARN이 과다 발생, 너무 높으면(예: 2000ms) 실질적 의미 없음

### 항목 2: StructuredArguments 적용

#### 무엇을
`log.info("메시지, key={}", value)` 패턴을 `log.info("메시지", kv("key", value))` 패턴으로 변경한다.

#### 왜 필요한가

**현재 방식의 문제** — `originalUrl`이 메시지 문자열에 묻혀 있다:

```json
{"message": "URL 단축 요청, originalUrl=https://naver.com"}
```

이 상태에서 "naver.com으로 향하는 단축 URL이 몇 건 생성되었는가?"를 알려면:
1. 전체 로그를 텍스트 검색해야 한다
2. 정규표현식으로 `originalUrl=(.*)` 를 추출해야 한다
3. 집계하려면 별도 스크립트가 필요하다

**kv() 방식** — `originalUrl`이 독립 필드가 된다:

```json
{
  "message": "URL 단축 요청 originalUrl=https://naver.com",
  "originalUrl": "https://naver.com"
}
```

이제 Kibana에서:
- `originalUrl: "naver.com"` → 필터 한 번으로 해당 도메인 요청만 표시
- Terms Aggregation → 도메인별 요청 건수 파이 차트

#### 왜 이 방식인가 (대안 비교)

| 방식 | 콘솔 출력 | JSON 출력 | 코드 변경량 |
|------|----------|-----------|------------|
| `log.info("msg, key={}", value)` (현재) | key=value 포함 | message에 묻힘 | - |
| `kv("key", value)` (선택) | key=value 포함 | **독립 필드** | import 1줄 + 호출 변경 |
| `Markers.append("key", value)` | 콘솔에 안 나옴 | 독립 필드 | 별도 Marker 생성 필요 |
| 별도 JSON 로깅 라이브러리 | 라이브러리 의존 | 독립 필드 | 신규 의존성 추가 |

`StructuredArguments.kv()`가 최선인 이유:
1. **이미 의존성이 있다**: `logstash-logback-encoder`가 pom.xml에 있으므로 추가 의존성 불필요
2. **콘솔에서도 읽힌다**: `kv("originalUrl", "https://naver.com")`는 콘솔에서 `originalUrl=https://naver.com`으로 출력
3. **JSON에서 필드가 된다**: LogstashEncoder가 자동으로 독립 JSON 필드로 변환

### 항목 3: 서비스 레이어 duration 측정

#### 무엇을
`getUniqueShortenUrlKey()`(키 생성)와 `shortenUrlRepository.saveShortenUrl()`(DB 저장)의 실행 시간을 개별 측정하여 로그에 포함한다.

#### 왜 필요한가

Plan 1의 LoggingFilter에서 전체 요청 시간은 알 수 있다. 하지만 내부 어디가 느린지는 모른다:

```
POST /shortenUrl → 200 (850ms)
```

850ms가 어디서 소비되었는지 가능한 원인:
1. **키 생성 재시도**: `getUniqueShortenUrlKey()`가 최대 5회 재시도 → 각 시도마다 DB 조회
2. **DB 저장 지연**: DB 커넥션 풀 고갈이나 락 대기
3. **네트워크 지연**: DB 서버와의 네트워크
4. **GC 일시정지**: JVM Garbage Collection

각 단계의 duration을 기록하면:
```
keyGenDurationMs=320, saveDurationMs=15
```
→ 총 850ms 중 키 생성이 320ms, DB 저장이 15ms → **나머지 515ms는 직렬화/네트워크/기타** → 각 단계를 좁혀갈 수 있다.

#### 왜 이 메서드들인가

| 메서드 | 병목 가능성 | 이유 |
|--------|------------|------|
| `getUniqueShortenUrlKey()` | **높음** | 키 충돌 시 최대 5회 재시도, 각 시도마다 DB 조회. 데이터가 쌓일수록 충돌 확률 증가 |
| `saveShortenUrl()` | 중간 | DB 쓰기 작업. 커넥션 풀 고갈 시 대기 발생 가능 |
| `getOriginalUrlByShortenUrlKey()` | 낮음 | 단순 DB 읽기. 하지만 트래픽이 많으면 읽기도 병목 가능 |

모든 메서드에 duration을 넣으면 노이즈가 되므로, **병목 가능성이 높은 곳부터** 시작한다.

---

## 이점

### 시나리오: "오후 2시부터 URL 단축이 느려졌다"

**AS-IS 대응 (Plan 1까지)**:
```
12:00:01 INFO [a1b2c3d4] LoggingFilter - 요청 완료: POST /shortenUrl → 200 (850ms)
```
→ 850ms인 건 알겠는데, 왜 느린지 모름. DB 문제? 키 생성 문제? 추측만 가능.

**TO-BE 대응 (Plan 2)**:
```
12:00:01 INFO  [a1b2c3d4] SimpleShortenUrlService - shortenUrl 생성 shortenUrlKey=abc123 keyGenDurationMs=320 saveDurationMs=15
12:00:01 WARN  [a1b2c3d4] SimpleShortenUrlService - 키 생성 재시도 retryCount=3
12:00:01 WARN  [a1b2c3d4] LoggingFilter - 느린 요청 감지 durationMs=850
```
→ `keyGenDurationMs=320` + 재시도 3회 → **키 공간 고갈로 재시도가 많아진 것**이 원인. DB는 정상(15ms).

### 시나리오: "특정 도메인으로 향하는 단축 URL만 실패한다"

**AS-IS**: `grep "naver.com" application.log` → 관련 없는 로그도 잡힘
**TO-BE**: Kibana에서 `originalUrl: "naver.com" AND level: "ERROR"` → 정확한 필터링

---

## 수정 대상 파일

| 파일 | 변경 내용 |
|------|----------|
| `src/main/java/.../presentation/LoggingFilter.java` | slow request WARN 승격 |
| `src/main/java/.../presentation/ShortenUrlRestController.java` | kv() 적용 |
| `src/main/java/.../application/SimpleShortenUrlService.java` | kv() 적용 + duration 측정 |
| `src/main/java/.../presentation/GlobalExceptionHandler.java` | kv() 적용 |

---

## 검증 방법

### 1. StructuredArguments 확인
앱을 기동하고 POST /shortenUrl 요청을 보낸 후:
- **콘솔**: `originalUrl=https://...`이 메시지에 나타나는지 확인
- **JSON** (LogstashEncoder 출력): `"originalUrl": "https://..."`이 독립 필드인지 확인
  - 확인 방법: logback.xml에서 일시적으로 CONSOLE appender의 encoder를 LogstashEncoder로 변경

### 2. Duration 측정 확인
```
shortenUrl 생성 shortenUrlKey=... keyGenDurationMs=... saveDurationMs=...
```
위 형태로 각 단계의 시간이 출력되는지 확인한다.

### 3. Slow Request 감지 확인
의도적으로 느린 요청을 만들어 테스트:
- `getUniqueShortenUrlKey()`에 `Thread.sleep(600)`을 임시 추가
- 요청 후 WARN 레벨의 "느린 요청 감지" 로그가 나타나는지 확인
- 테스트 후 `Thread.sleep` 제거
