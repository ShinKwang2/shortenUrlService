# Observability 개념 가이드

## 목표
구현에 앞서 OpenTelemetry, 로깅 전략, 모니터링 지표에 대한 개념을 정리한다.

---

<thinking>

## 사고 과정

### 1. "로그를 얼마나 남겨야 하는가"라는 질문의 본질

이 질문은 사실 **"무엇을 관찰해야 하는가"**라는 질문과 같다.
로그를 남기는 이유는 딱 3가지다:

1. **문제가 생겼을 때 원인을 찾기 위해** (디버깅)
2. **문제가 생기기 전에 징후를 감지하기 위해** (모니터링)
3. **시스템이 어떻게 사용되는지 이해하기 위해** (분석)

그래서 "얼마나"의 답은: **이 3가지 목적을 달성할 수 있을 만큼**이다.
너무 적으면 문제 원인을 못 찾고, 너무 많으면 비용이 폭발하고 정작 중요한 로그를 노이즈에서 찾기 어렵다.

### 2. 업계가 왜 OpenTelemetry로 수렴하고 있는가

예전에는 로그, 메트릭, 트레이스가 각각 다른 라이브러리/포맷으로 존재했다:
- 로그: Log4j, Logback, Fluentd...
- 메트릭: StatsD, Prometheus client, Datadog agent...
- 트레이스: Zipkin, Jaeger, X-Ray...

각각 다른 SDK, 다른 포맷, 다른 백엔드. 벤더 종속(vendor lock-in)이 심했다.
OpenTelemetry는 이걸 **하나의 표준**으로 통일하자는 CNCF 프로젝트다.

핵심 사고: "데이터를 생성하는 부분(계측)"과 "데이터를 저장/분석하는 부분(백엔드)"을 분리하자.
→ 앱은 OTel 표준으로 데이터를 내보내고, 백엔드는 자유롭게 선택할 수 있다.

### 3. Observability의 3대 축 (Three Pillars)

업계에서 말하는 Observability는 3가지 신호(Signal)로 구성된다:

| 신호 | 질문 | 예시 |
|------|------|------|
| **Logs** (로그) | "그 순간에 무슨 일이 일어났는가?" | `ERROR: URL not found for key=abc123` |
| **Metrics** (메트릭) | "지금 시스템 상태가 어떤가?" | `HTTP 요청 수: 1500/min, 에러율: 2.3%` |
| **Traces** (트레이스) | "하나의 요청이 어떤 경로로 흘러갔는가?" | `POST /shortenUrl → Service → Repository → Response (45ms)` |

이 3가지가 연결(correlation)되어야 진짜 Observability다.
예: 메트릭에서 에러율 급증 감지 → 해당 시간대 로그 검색 → 특정 traceId의 전체 흐름 추적

</thinking>

---

<plan>

## OpenTelemetry Spec이란

### 한 줄 정의
**앱에서 Logs, Metrics, Traces 데이터를 수집하고 내보내는 방법에 대한 업계 표준 규격.**

### 구성 요소

```
┌─────────────────────────────────────────────┐
│              OpenTelemetry Spec              │
├─────────────┬──────────────┬────────────────┤
│    API      │     SDK      │   Protocol     │
│ (인터페이스) │ (구현체)      │ (전송 규격)     │
├─────────────┼──────────────┼────────────────┤
│ "무엇을     │ "어떻게      │ "어떤 포맷으로  │
│  계측할까"  │  처리할까"   │  내보낼까"      │
└─────────────┴──────────────┴────────────────┘
```

- **API**: 코드에서 사용하는 인터페이스. `tracer.startSpan()`, `meter.createCounter()` 같은 것
- **SDK**: API의 실제 구현. 샘플링, 배치 처리, 내보내기 담당
- **OTLP (OpenTelemetry Protocol)**: 데이터 전송 포맷. gRPC 또는 HTTP로 전송

### Spring Boot에서의 OTel

Spring Boot 3.x는 **Micrometer**를 통해 OTel을 지원한다:
- Micrometer = Spring 진영의 메트릭/트레이싱 추상화 레이어
- Micrometer가 내부적으로 OTel SDK를 사용하여 OTLP 포맷으로 데이터를 내보냄
- 즉, **Spring Boot 개발자는 Micrometer API를 쓰면 자연스럽게 OTel Spec을 따르게 됨**

### OTel Spec을 "맞춘다"는 것의 의미

1. **데이터 모델 준수**: 로그/메트릭/트레이스에 OTel이 정의한 속성(Attributes)을 포함
   - `service.name`, `service.version`, `trace_id`, `span_id` 등
2. **OTLP 프로토콜 사용**: 데이터를 OTLP 포맷으로 내보냄
3. **Semantic Conventions 준수**: OTel이 정의한 표준 속성명 사용
   - 예: HTTP 요청의 경우 `http.request.method`, `http.response.status_code`, `url.path`

---

## 로그를 어디에, 얼마나 남겨야 하는가

### Log Level 가이드

| 레벨 | 언제 사용 | ShortenUrl 예시 | 프로덕션에서 |
|------|----------|----------------|-------------|
| **ERROR** | 시스템이 정상 동작하지 못하는 상황. 즉시 알림이 필요한 수준 | 알 수 없는 예외 발생, 외부 서비스 연결 실패 | 항상 ON |
| **WARN** | 잠재적 문제. 당장은 아니지만 곧 문제가 될 수 있음 | URL 키 생성 재시도 횟수 3회 초과 | 항상 ON |
| **INFO** | 비즈니스 관점에서 의미 있는 이벤트. 시스템의 정상 흐름 | URL 단축 생성 완료, 리다이렉트 수행 | 항상 ON |
| **DEBUG** | 개발/디버깅에 필요한 상세 정보 | 입력 파라미터 값, 중간 처리 결과 | 보통 OFF |
| **TRACE** | 매우 상세한 흐름 추적 | 메서드 진입/종료, 변수 상태 | 보통 OFF |

### 핵심 원칙: "프로덕션에서 INFO만 켜져 있어도 문제를 추적할 수 있어야 한다"

이 말은:
- ERROR/WARN은 **"무엇이 잘못되었는가"**를 충분히 설명해야 함
- INFO는 **"시스템이 무엇을 했는가"**의 핵심 흐름을 보여줘야 함
- DEBUG는 INFO만으로 부족할 때 **임시로 켤 수 있는 상세 정보**

### ShortenUrl 프로젝트에 적용하면

```
[요청 진입] ─── INFO: 어떤 요청이 들어왔는가
     │
[비즈니스 로직] ─── INFO: 핵심 결과 (URL 생성됨, 리다이렉트됨)
     │              DEBUG: 중간 과정 (키 생성 시도, 조회 결과)
     │
[예외 발생] ─── ERROR: 예상치 못한 오류 + 스택트레이스
     │          WARN: 예상된 실패 (URL 미존재 등)
     │
[요청 종료] ─── (자동: HTTP 상태코드, 응답시간은 메트릭으로)
```

### 로그에 반드시 포함해야 하는 정보 (컨텍스트)

```
하나의 로그 라인에 담아야 할 것:

1. 시간 (timestamp)        → 언제?
2. 로그 레벨 (level)        → 얼마나 심각한가?
3. traceId                  → 어떤 요청에서? (로그↔트레이스 연결의 핵심!)
4. spanId                   → 요청 흐름 중 어디에서?
5. 로거 이름 (logger)       → 어떤 컴포넌트에서?
6. 메시지 (message)         → 무엇이 일어났는가?
7. 비즈니스 컨텍스트         → 관련 데이터 (shortenUrlKey, originalUrl 등)
```

**traceId가 가장 중요한 이유:**
하나의 HTTP 요청이 Controller → Service → Repository를 거치며 여러 로그를 남기는데,
traceId가 있으면 이 로그들을 하나의 요청으로 묶어서 볼 수 있다.

---

## 모니터링에 필요한 지표 (Metrics)

### RED Method (요청 기반 서비스의 황금 지표)

ShortenUrl 같은 요청-응답 서비스에는 **RED Method**가 기본이다:

| 지표 | 의미 | ShortenUrl에서 | 왜 중요한가 |
|------|------|---------------|------------|
| **R**ate | 초당 요청 수 | `/shortenUrl` POST 요청 수/초, `/{key}` GET 요청 수/초 | 트래픽 규모 파악 |
| **E**rror | 에러율 | 4xx, 5xx 응답 비율 | 서비스 품질 |
| **D**uration | 응답 시간 | p50, p95, p99 응답 시간 | 사용자 경험 |

→ Spring Boot Actuator + Micrometer는 이 3가지를 **자동으로** 수집한다.
`http.server.requests` 메트릭 하나에 요청수, 에러수, 응답시간 히스토그램이 모두 포함됨.

### USE Method (인프라 리소스 지표)

| 지표 | 의미 | 예시 |
|------|------|------|
| **U**tilization | 리소스 사용률 | CPU 사용률, JVM 힙 메모리 사용률 |
| **S**aturation | 포화도 | 스레드 풀 대기 큐 길이 |
| **E**rrors | 리소스 에러 | GC pause 시간, OOM 발생 횟수 |

→ 이것도 Actuator가 JVM 메트릭(`jvm.memory.*`, `jvm.gc.*`, `jvm.threads.*`)으로 자동 수집.

### ShortenUrl 전용 비즈니스 메트릭 (선택)

위 두 가지(RED, USE)는 프레임워크가 자동 수집하지만,
비즈니스 관점의 메트릭은 직접 정의해야 한다:

| 메트릭 | 타입 | 의미 |
|--------|------|------|
| `shortenurl.created.total` | Counter | 생성된 단축 URL 총 개수 |
| `shortenurl.redirected.total` | Counter | 리다이렉트 총 횟수 |
| `shortenurl.key_generation.retries` | Histogram | 키 생성 시 재시도 횟수 분포 |

→ 이건 Phase 2(나중)에 코드에 Micrometer Counter/Histogram을 추가해서 구현.

---

## Traces (분산 트레이싱)

### 단일 서비스인데 트레이싱이 왜 필요한가?

1. **요청 하나의 전체 소요 시간을 구간별로 분해**할 수 있다
   - Controller에서 얼마, Service에서 얼마, Repository에서 얼마
   - 어디가 병목인지 한눈에 파악
2. **로그와 연결** — traceId로 해당 요청의 모든 로그를 필터링
3. **나중에 서비스가 늘어나면** DB, 캐시, 외부 API 호출까지 하나의 트레이스로 추적

### Trace 구조

```
Trace (traceId: abc123)
└── Span: POST /shortenUrl (45ms)
    ├── Span: SimpleShortenUrlService.generateShortenUrl (12ms)
    │   └── Span: MapShortenUrlRepository.saveShortenUrl (2ms)
    └── Span: HTTP Response (1ms)
```

Spring Boot + Micrometer Tracing이 **자동 생성**하는 Span의 범위:
- ✅ **자동**: HTTP 인바운드 요청 전체 (Controller 진입~응답)
- ✅ **자동**: WebClient/RestTemplate 외부 호출 Span
- ❌ **수동 필요**: Service/Repository 계층 내부 메서드 → `@Observed` 어노테이션 또는 직접 `Observation.start()` 호출 필요

즉, 아무것도 안 해도 요청 단위 최상위 Span은 생기지만, 내부 구간별 분해는 직접 계측해야 한다.

</plan>

---

<recommend>

## 추천 학습 & 구현 순서

### 1단계: 개념 이해 (지금)
- ✅ OpenTelemetry Spec이 무엇인지
- ✅ Logs, Metrics, Traces 3대 축
- ✅ 로그 레벨 전략과 컨텍스트
- ✅ RED/USE Method로 어떤 지표를 봐야 하는지

### 2단계: 메트릭부터 시작 (가장 쉬움)
- Spring Boot Actuator + Micrometer Prometheus 추가
- `/actuator/prometheus` 엔드포인트 확인
- Prometheus + Grafana로 RED 메트릭 대시보드 구성
- **이유**: 코드 변경 없이 의존성+설정만으로 완성. 즉시 시각적 결과를 볼 수 있어 동기부여됨

### 3단계: 구조화 로깅 + Loki
- logback-spring.xml로 JSON 구조화 로깅 설정
- Loki4j 어펜더로 Loki에 로그 push
- Grafana에서 로그 검색
- **이유**: 로그 포맷을 정하고 나면, "어디에 어떤 로그를 남길지" 기준이 명확해짐

### 4단계: 트레이싱 + 연동
- Micrometer Tracing + OTel 브릿지로 트레이스 생성
- Tempo에 전송, Grafana에서 조회
- Loki 로그의 traceId 클릭 → Tempo 트레이스로 이동 (로그↔트레이스 연동)
- **이유**: 3대 축이 모두 연결되는 순간이 Observability의 핵심 가치를 체감하는 순간

### 5단계 (선택): 비즈니스 메트릭 추가
- Micrometer Counter/Histogram으로 `shortenurl.created.total` 등 직접 계측
- Custom Grafana 대시보드 구성

### 추천 참고 자료
- OpenTelemetry 공식 문서: https://opentelemetry.io/docs/
- Spring Boot Observability 공식 가이드: Spring Boot Reference → Production-ready Features → Observability
- Grafana Labs 블로그의 "Intro to Observability" 시리즈

</recommend>

---

# 심화편: 더 깊이 파고들기

---

<thinking>

## OTel Semantic Conventions를 왜 알아야 하는가

로그나 메트릭에 속성(attribute)을 붙일 때, 팀마다 이름을 다르게 지으면 혼란이 생긴다:
- 어떤 팀: `http_status`, 다른 팀: `status_code`, 또 다른 팀: `response.code`
- 같은 의미인데 이름이 달라서 대시보드/알림 규칙을 공유할 수 없음

OTel Semantic Conventions는 **"이 속성은 이 이름으로 부르자"**라는 전 세계적 약속이다.
이걸 따르면:
1. Grafana 대시보드 템플릿을 그대로 가져다 쓸 수 있음
2. 팀/회사가 바뀌어도 동일한 언어로 대화 가능
3. 라이브러리/프레임워크가 자동 계측한 데이터와 내가 직접 남긴 데이터가 일관됨

## 로그 설계에서 "구조화"가 왜 중요한가

전통적 로그:
```
2024-01-15 10:23:45 INFO URL shortened: abc123 -> https://example.com
```

구조화된 로그 (JSON):
```json
{"timestamp":"2024-01-15T10:23:45","level":"INFO","traceId":"abc...","logger":"ShortenUrlService","message":"URL shortened","shortenUrlKey":"abc123","originalUrl":"https://example.com"}
```

차이점:
- 전통적 로그는 **사람이 읽기 좋지만** 기계가 파싱하기 어렵다 (정규식 필요)
- 구조화 로그는 **기계가 바로 인덱싱/검색**할 수 있다 (JSON 필드로 필터링)
- Loki에서 `| json | shortenUrlKey="abc123"` 으로 바로 검색 가능

핵심: **로그는 결국 기계가 처리하는 데이터**다. 사람이 읽는 건 Grafana UI가 해준다.

</thinking>

---

<plan>

## OTel Semantic Conventions 상세

### HTTP 서버 관련 (ShortenUrl에 직접 해당)

Spring Boot + Micrometer가 **자동으로** 아래 속성들을 메트릭/트레이스에 포함시킨다:

| Semantic Convention 속성 | 의미 | ShortenUrl 예시 값 |
|--------------------------|------|-------------------|
| `http.request.method` | HTTP 메서드 | `GET`, `POST` |
| `http.response.status_code` | HTTP 상태 코드 | `200`, `301`, `404` |
| `url.path` | 요청 경로 | `/shortenUrl`, `/abc123` |
| `http.route` | 라우팅 패턴 | `/shortenUrl`, `/{shortenUrlKey}` |
| `server.address` | 서버 호스트 | `localhost` |
| `server.port` | 서버 포트 | `8080` |
| `network.protocol.version` | HTTP 버전 | `1.1`, `2` |

### 서비스 리소스 속성

| 속성 | 의미 | 설정 방법 |
|------|------|----------|
| `service.name` | 서비스 이름 | `spring.application.name`에서 자동 |
| `service.version` | 서비스 버전 | `info.app.version`이나 빌드 정보에서 |
| `service.namespace` | 서비스 그룹 | 직접 설정 (예: `shorturl-platform`) |
| `deployment.environment` | 배포 환경 | `dev`, `staging`, `prod` |

### 직접 로그를 남길 때의 Naming Convention

OTel Semantic Conventions에서 배워야 할 **패턴**:

```
1. 소문자 + 점(.) 구분자 사용
   ✅ http.request.method
   ❌ httpRequestMethod, HTTP_REQUEST_METHOD

2. 네임스페이스 → 엔티티 → 속성 순서
   ✅ url.path         (url 네임스페이스 → path 속성)
   ✅ db.query.text    (db 네임스페이스 → query 엔티티 → text 속성)

3. 단수형 사용
   ✅ http.request.header
   ❌ http.request.headers

4. boolean은 긍정형
   ✅ http.request.resend  (true/false)
   ❌ http.request.not_first
```

ShortenUrl 비즈니스 속성에 적용하면:
```
shortenurl.key              → 단축 URL 키
shortenurl.original_url     → 원본 URL
shortenurl.redirect.count   → 리다이렉트 횟수
shortenurl.key.retries      → 키 생성 재시도 횟수
```

---

## 로그 설계 패턴: 어디에 무엇을 남길 것인가

### 패턴 1: 경계(Boundary)에서 로그를 남겨라

```
외부 세계 ──→ [경계] ──→ 내부 로직 ──→ [경계] ──→ 외부 세계
              ↑ 여기서 INFO                ↑ 여기서 INFO
```

**경계란?**
- HTTP 요청 수신/응답 반환 (Controller 진입/종료)
- 외부 API 호출 전/후
- DB 쿼리 전/후
- 메시지 큐 발행/소비

**이유:** 경계에서의 로그는 **시스템 간 상호작용을 기록**한다.
내부 로직은 트레이스의 Span으로 충분히 추적되므로, 모든 메서드에 로그를 남길 필요 없다.

### ShortenUrl에 적용

```java
// Controller - 요청 경계 (INFO)
@PostMapping("/shortenUrl")
public ResponseEntity<...> createShortenUrl(...) {
    log.info("URL 단축 요청 수신, originalUrl={}", request.getOriginalUrl());
    // ... 처리 ...
    log.info("URL 단축 완료, shortenUrlKey={}", response.getShortenUrlKey());
    return ResponseEntity.ok(response);
}

// Service - 비즈니스 로직 (DEBUG)
public ShortenUrl generateShortenUrl(String originalUrl) {
    log.debug("단축 URL 생성 시작, originalUrl={}", originalUrl);
    String key = getUniqueShortenUrlKey();
    log.debug("고유 키 생성 완료, key={}", key);
    // ...
}

// Exception Handler - 에러 경계 (ERROR/WARN)
@ExceptionHandler(NotFoundShortenUrlException.class)
public ResponseEntity<...> handleNotFound(NotFoundShortenUrlException e) {
    log.warn("URL 조회 실패: {}", e.getMessage());  // WARN: 클라이언트 에러(4xx)
    // ...
}

@ExceptionHandler(Exception.class)
public ResponseEntity<...> handleUnexpected(Exception e) {
    log.error("예상치 못한 오류 발생", e);  // ERROR: 서버 에러(5xx) + 스택트레이스
    // ...
}
```

### 패턴 2: 4xx는 WARN, 5xx는 ERROR

| HTTP 상태 | 로그 레벨 | 이유 |
|-----------|----------|------|
| 2xx | INFO | 정상 흐름 |
| 3xx (리다이렉트) | INFO | 정상 동작 |
| 4xx (클라이언트 에러) | WARN | 서버 잘못이 아님. 하지만 비정상 접근 패턴 감지용으로 기록 |
| 5xx (서버 에러) | ERROR | 서버 문제. 즉시 확인 필요 |

**흔한 실수:** 404를 ERROR로 찍으면 에러 알림이 폭발한다.
존재하지 않는 URL 접근은 서버 잘못이 아니다 → WARN이 적절.

### 패턴 3: 로그 메시지는 "무엇을 했는가" + "관련 데이터"

```
❌ log.info("success");                          → 뭐가 성공?
❌ log.info("processing request");               → 무슨 요청?
❌ log.info(request.toString());                  → 민감정보 노출 위험, 구조 없음

✅ log.info("URL 단축 완료, key={}", key);        → 행위 + 식별자
✅ log.warn("URL 조회 실패, key={}", key);        → 결과 + 식별자
✅ log.error("키 생성 한도 초과, retries={}", 5);  → 문제 + 수치
```

### 패턴 4: 민감 정보는 절대 로그에 남기지 않는다

로그에 남기면 안 되는 것:
- 비밀번호, API 키, 토큰
- 주민등록번호, 카드번호 등 개인정보
- 전체 요청/응답 body (민감 정보 포함 가능성)

ShortenUrl에서는 originalUrl이 민감할 수 있는지 고려:
- 공개 URL이면 OK
- 인증 토큰이 URL에 포함된 경우? → 마스킹 필요

---

## 메트릭 설계 패턴

### 메트릭 타입 4가지

| 타입 | 용도 | 예시 |
|------|------|------|
| **Counter** | 단조 증가하는 누적값 | 총 요청 수, 총 에러 수 |
| **Gauge** | 올라갔다 내려갔다 하는 현재값 | 현재 메모리 사용량, 활성 스레드 수 |
| **Histogram** | 값의 분포 | 응답 시간 분포 (p50, p95, p99) |
| **Summary** | Histogram과 유사, 클라이언트에서 계산 | (Prometheus에서는 Histogram 권장) |

### ShortenUrl에서 자동 수집되는 메트릭 (Actuator)

| 메트릭 이름 | 타입 | 의미 |
|-------------|------|------|
| `http.server.requests` | Timer(Histogram) | 모든 HTTP 요청의 횟수 + 응답시간 분포 |
| `jvm.memory.used` | Gauge | JVM 힙/논힙 메모리 사용량 |
| `jvm.gc.pause` | Timer | GC 일시정지 시간 |
| `jvm.threads.live` | Gauge | 활성 스레드 수 |
| `process.cpu.usage` | Gauge | CPU 사용률 |
| `logback.events` | Counter | 로그 레벨별 로그 발생 횟수 |

→ 이것만으로 RED Method + USE Method의 80%가 커버된다.

### 커스텀 메트릭을 추가할 때의 원칙

1. **자동 수집으로 부족할 때만** 추가 (프레임워크가 해주는 걸 중복하지 말 것)
2. **비즈니스 의미가 있는 것만** 추가
3. **카디널리티를 낮게 유지** (라벨 값의 종류가 너무 많으면 Prometheus가 느려짐)
   - ✅ `method="POST"` (값 종류: GET, POST, PUT, DELETE 정도)
   - ❌ `shortenUrlKey="abc123"` (값 종류: 무한대 → 절대 라벨로 쓰면 안 됨)

</plan>

---

<recommend>

## 실행 계획 (업데이트)

### 마크다운 파일 저장
현재 정리된 모든 내용을 프로젝트 내 `docs/02-observability-concepts.md`로 저장

### 구현 순서 (변경 없음)
1단계 개념 이해 ✅ 완료
2단계 메트릭 (Actuator + Prometheus + Grafana) → 다음 진행
3단계 구조화 로깅 + Loki
4단계 트레이싱 + 연동
5단계 비즈니스 메트릭

</recommend>

---

# 심화편 2: Loki 쿼리, Grafana 대시보드, 알림 전략

---

<thinking>

## Loki를 이해하기 위한 사고

Loki는 Elasticsearch와 **철학이 다르다**.

Elasticsearch: 로그 전체를 인덱싱 → 풀텍스트 검색 빠름 → **저장 비용 비쌈**
Loki: 라벨(label)만 인덱싱, 로그 본문은 압축 저장 → 라벨로 필터 후 본문 grep → **저장 비용 저렴**

Loki의 슬로건: **"Like Prometheus, but for logs"**
- Prometheus가 메트릭을 라벨로 구분하듯, Loki도 로그를 라벨로 구분한다
- 라벨이 잘 설계되어야 Loki가 빠르다
- 라벨이 너무 많으면(고카디널리티) Loki 성능이 급격히 떨어진다

## Grafana 대시보드 사고

대시보드는 **질문에 답하는 도구**다. "뭘 보여줄까"가 아니라 "어떤 질문에 답해야 하는가"로 접근:
- "서비스가 살아있는가?" → Health 패널
- "지금 트래픽이 평소와 다른가?" → Rate 패널
- "에러가 나고 있는가?" → Error rate 패널
- "응답이 느린가?" → Latency 패널
- "리소스가 부족한가?" → JVM 메모리/CPU 패널

## 알림 전략 사고

알림의 가장 큰 적: **Alert Fatigue (알림 피로)**
- 알림이 너무 많으면 → 무시하게 됨 → 진짜 중요한 알림도 놓침
- "이 알림이 울렸을 때, 내가 당장 무언가를 해야 하는가?" → No이면 알림이 아니라 대시보드에 표시
- 핵심: **Actionable한 알림만** 보내라

</thinking>

---

<plan>

## 1. Loki 쿼리 (LogQL) 완전 가이드

### LogQL 기본 구조

```
{라벨 셀렉터} | 파이프라인
```

모든 LogQL 쿼리는 이 2단계다:
1. **라벨 셀렉터**: 어떤 로그 스트림을 가져올까 (Loki 인덱스 사용, 빠름)
2. **파이프라인**: 가져온 로그를 어떻게 필터/변환할까 (본문 스캔, 상대적으로 느림)

### 라벨 셀렉터 (Label Selector)

logback-spring.xml에서 설정한 라벨: `application`, `host`, `level`

```logql
# 정확히 일치
{application="shortenurlservice"}

# 정규식 매칭
{application=~"shorten.*"}

# 부정 (이것이 아닌)
{application="shortenurlservice", level!="DEBUG"}

# 여러 라벨 조합 (AND)
{application="shortenurlservice", level="ERROR"}
```

**주의: 최소 하나의 라벨 셀렉터는 반드시 필요하다. `{}`만으로는 쿼리 불가.**

### 파이프라인 (Pipeline) — 로그 필터링

```logql
# 텍스트 포함 필터 (grep과 동일)
{application="shortenurlservice"} |= "URL 단축 완료"

# 텍스트 미포함 필터
{application="shortenurlservice"} != "health"

# 정규식 필터
{application="shortenurlservice"} |~ "key=\\w{8}"

# 정규식 부정
{application="shortenurlservice"} !~ "actuator|health"
```

### 파이프라인 — JSON 파싱

구조화 로그(JSON)를 보내므로, 필드별로 필터링할 수 있다:

```logql
# JSON 파싱 후 필드로 필터
{application="shortenurlservice"} | json | level="ERROR"

# 특정 필드 값으로 검색
{application="shortenurlservice"} | json | shortenUrlKey="abc123"

# traceId로 특정 요청의 모든 로그 추적
{application="shortenurlservice"} | json | traceId="64a8f3b2c1d4e5f6"

# 여러 조건 조합
{application="shortenurlservice"} | json | level="ERROR" | message=~".*키 생성.*"
```

### 파이프라인 — 포맷팅

```logql
# 특정 필드만 표시 (Grafana에서 보기 좋게)
{application="shortenurlservice"} | json | line_format "{{.timestamp}} [{{.level}}] {{.logger}} - {{.message}}"

# 라벨 추출 (파이프라인 내에서 새 라벨 생성)
{application="shortenurlservice"} | json | label_format traceId="{{.traceId}}"
```

### 메트릭 쿼리 (Logs → Metrics 변환)

Loki의 강력한 기능: 로그로부터 메트릭을 만들 수 있다.

```logql
# 초당 로그 발생률
rate({application="shortenurlservice"}[5m])

# 초당 에러 로그 발생률
rate({application="shortenurlservice", level="ERROR"}[5m])

# 에러 비율 (에러 로그 수 / 전체 로그 수)
sum(rate({application="shortenurlservice", level="ERROR"}[5m]))
/
sum(rate({application="shortenurlservice"}[5m]))

# 특정 키워드 포함 로그의 발생 횟수 (최근 1시간)
count_over_time({application="shortenurlservice"} |= "URL 단축 완료"[1h])

# 상위 5개 에러 메시지
topk(5, sum by (message) (count_over_time(
  {application="shortenurlservice", level="ERROR"} | json [1h]
)))
```

### 실전 쿼리 시나리오

```logql
# 시나리오 1: "최근 30분간 에러가 있었나?"
{application="shortenurlservice", level=~"ERROR|WARN"}
  | json
  | line_format "{{.timestamp}} [{{.level}}] {{.message}}"

# 시나리오 2: "특정 사용자 요청의 전체 흐름 추적"
{application="shortenurlservice"}
  | json
  | traceId="64a8f3b2c1d4e5f6"

# 시나리오 3: "어떤 에러가 가장 많이 발생하는가?"
topk(10, sum by (message) (
  count_over_time({application="shortenurlservice", level="ERROR"} | json [24h])
))

# 시나리오 4: "특정 시간대의 로그만 보기"
# → Grafana UI에서 시간 범위 선택이 더 편함. LogQL 자체에는 시간 필터 없음.

# 시나리오 5: "5분간 에러가 10건 이상이면 위험"
count_over_time({application="shortenurlservice", level="ERROR"}[5m]) > 10
```

---

## 2. Grafana 대시보드 설계

### 대시보드 구조 원칙

**하나의 대시보드 = 하나의 관점**

| 대시보드 | 대상 | 질문 |
|----------|------|------|
| **Service Overview** | 서비스 전체 상태 | "지금 서비스가 괜찮은가?" |
| **JVM Internals** | JVM 리소스 | "리소스가 부족한가?" |
| **Logs Explorer** | 로그 상세 | "무슨 일이 일어났는가?" (Grafana 내장 Explore로 대체 가능) |

### Service Overview 대시보드 상세 레이아웃

```
┌─────────────────────────────────────────────────────────────┐
│                    Service Overview                          │
│                 shortenurlservice                           │
├─────────────┬──────────────┬──────────────┬─────────────────┤
│ Row 1: 핵심 지표 (Stat 패널, 한눈에 상태 파악)                │
├─────────────┬──────────────┬──────────────┬─────────────────┤
│  현재 RPS   │ 에러율 (%)   │ p95 응답시간  │  Uptime        │
│  "12.3/s"   │  "0.5%"     │  "45ms"      │  "3d 2h"       │
│  (초록/빨강) │  (초록/빨강)  │ (초록/노랑/빨강)│               │
├─────────────┴──────────────┴──────────────┴─────────────────┤
│ Row 2: Rate — 시간에 따른 요청량 변화                         │
├─────────────────────────────────────────────────────────────┤
│  ┌─ Time Series 패널 ──────────────────────────────────┐    │
│  │  요청 수/초 (전체, 성공, 에러를 색상으로 구분)         │    │
│  │  ████████████████████████░░░░ ← 정상 (초록)          │    │
│  │  ░░░░░░░░░░░░░░░░░░░░░░████ ← 에러 (빨강)           │    │
│  └──────────────────────────────────────────────────────┘    │
├─────────────────────────────────────────────────────────────┤
│ Row 3: Errors — 에러 상세                                   │
├──────────────────────────┬──────────────────────────────────┤
│  ┌─ Time Series ───────┐ │ ┌─ Table ─────────────────────┐ │
│  │ HTTP 상태코드별      │ │ │ 최근 에러 로그 (Loki 연동)   │ │
│  │ 에러 발생률          │ │ │ 시간 | 레벨 | 메시지        │ │
│  │ ── 404              │ │ │ 10:23 ERROR  키 생성 실패   │ │
│  │ ── 500              │ │ │ 10:21 WARN   URL 미존재    │ │
│  └─────────────────────┘ │ └─────────────────────────────┘ │
├─────────────────────────────────────────────────────────────┤
│ Row 4: Duration — 응답 시간 분포                             │
├─────────────────────────────────────────────────────────────┤
│  ┌─ Time Series 패널 ──────────────────────────────────┐    │
│  │  응답 시간 백분위수                                    │    │
│  │  ── p99 (빨강)                                       │    │
│  │  ── p95 (노랑)                                       │    │
│  │  ── p50 (초록)                                       │    │
│  └──────────────────────────────────────────────────────┘    │
├─────────────────────────────────────────────────────────────┤
│ Row 5: 엔드포인트별 분석                                     │
├──────────────────────────┬──────────────────────────────────┤
│  ┌─ Table ─────────────┐ │ ┌─ Pie Chart ────────────────┐ │
│  │ 엔드포인트 | RPS |   │ │ │ 트래픽 비율                  │ │
│  │ p95 | 에러율         │ │ │ POST /shortenUrl: 30%      │ │
│  │ POST /shortenUrl    │ │ │ GET /{key}: 65%            │ │
│  │  2.1  15ms  0.1%   │ │ │ GET /shortenUrl/{k}: 5%    │ │
│  │ GET /{key}          │ │ │                            │ │
│  │  8.5  8ms   0.8%   │ │ │                            │ │
│  └─────────────────────┘ │ └────────────────────────────┘ │
└──────────────────────────┴──────────────────────────────────┘
```

### 각 패널의 PromQL 쿼리

#### Row 1: 핵심 지표 (Stat 패널)

```promql
# 현재 RPS (초당 요청 수)
sum(rate(http_server_requests_seconds_count{application="shortenurlservice"}[5m]))

# 에러율 (%)
sum(rate(http_server_requests_seconds_count{application="shortenurlservice", status=~"5.."}[5m]))
/
sum(rate(http_server_requests_seconds_count{application="shortenurlservice"}[5m]))
* 100

# p95 응답 시간 (초 → ms 변환은 Grafana 단위 설정으로)
histogram_quantile(0.95,
  sum(rate(http_server_requests_seconds_bucket{application="shortenurlservice"}[5m])) by (le)
)
```

#### Row 2: Rate (Time Series)

```promql
# 성공 요청 rate
sum(rate(http_server_requests_seconds_count{application="shortenurlservice", status=~"2..|3.."}[5m]))

# 4xx 에러 rate
sum(rate(http_server_requests_seconds_count{application="shortenurlservice", status=~"4.."}[5m]))

# 5xx 에러 rate
sum(rate(http_server_requests_seconds_count{application="shortenurlservice", status=~"5.."}[5m]))
```

#### Row 3: Errors

```promql
# HTTP 상태코드별 에러 rate
sum by (status) (rate(http_server_requests_seconds_count{application="shortenurlservice", status=~"[45].."}[5m]))
```

에러 로그 테이블 (Loki 쿼리):
```logql
{application="shortenurlservice", level=~"ERROR|WARN"} | json | line_format "{{.message}}"
```

#### Row 4: Duration (Time Series)

```promql
# p50 (중앙값)
histogram_quantile(0.50, sum(rate(http_server_requests_seconds_bucket{application="shortenurlservice"}[5m])) by (le))

# p95
histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{application="shortenurlservice"}[5m])) by (le))

# p99
histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket{application="shortenurlservice"}[5m])) by (le))
```

#### Row 5: 엔드포인트별

```promql
# 엔드포인트별 RPS
sum by (uri) (rate(http_server_requests_seconds_count{application="shortenurlservice"}[5m]))

# 엔드포인트별 p95
histogram_quantile(0.95, sum by (le, uri) (rate(http_server_requests_seconds_bucket{application="shortenurlservice"}[5m])))

# 엔드포인트별 에러율
sum by (uri) (rate(http_server_requests_seconds_count{application="shortenurlservice", status=~"[45].."}[5m]))
/
sum by (uri) (rate(http_server_requests_seconds_count{application="shortenurlservice"}[5m]))
```

### JVM Internals 대시보드

```
┌─────────────────────────────────────────────────────────────┐
│                     JVM Internals                           │
├─────────────────────────────────────────────────────────────┤
│ Row 1: Memory                                               │
├──────────────────────────┬──────────────────────────────────┤
│  ┌─ Gauge 패널 ────────┐ │ ┌─ Time Series ────────────────┐│
│  │  Heap 사용률         │ │ │  메모리 영역별 사용량 추이     ││
│  │   ████████░░ 78%    │ │ │  ── Heap Used               ││
│  │                     │ │ │  ── Heap Committed           ││
│  │                     │ │ │  ── Heap Max                 ││
│  └─────────────────────┘ │ └──────────────────────────────┘│
├─────────────────────────────────────────────────────────────┤
│ Row 2: GC (Garbage Collection)                              │
├──────────────────────────┬──────────────────────────────────┤
│  ┌─ Time Series ───────┐ │ ┌─ Time Series ────────────────┐│
│  │  GC 일시정지 시간     │ │ │  GC 발생 횟수/분             ││
│  └─────────────────────┘ │ └──────────────────────────────┘│
├─────────────────────────────────────────────────────────────┤
│ Row 3: Threads & CPU                                        │
├──────────────────────────┬──────────────────────────────────┤
│  ┌─ Time Series ───────┐ │ ┌─ Time Series ────────────────┐│
│  │  활성 스레드 수       │ │ │  Process CPU 사용률          ││
│  └─────────────────────┘ │ └──────────────────────────────┘│
└──────────────────────────┴──────────────────────────────────┘
```

JVM 패널 PromQL:

```promql
# Heap 사용률
jvm_memory_used_bytes{application="shortenurlservice", area="heap"}
/ jvm_memory_max_bytes{application="shortenurlservice", area="heap"} * 100

# Heap 영역별 사용량
jvm_memory_used_bytes{application="shortenurlservice", area="heap"}
jvm_memory_committed_bytes{application="shortenurlservice", area="heap"}
jvm_memory_max_bytes{application="shortenurlservice", area="heap"}

# GC pause time (초)
rate(jvm_gc_pause_seconds_sum{application="shortenurlservice"}[5m])

# GC 횟수/분
rate(jvm_gc_pause_seconds_count{application="shortenurlservice"}[1m]) * 60

# 활성 스레드
jvm_threads_live_threads{application="shortenurlservice"}

# CPU 사용률
process_cpu_usage{application="shortenurlservice"}
```

---

## 3. 알림(Alerting) 전략 상세

### 알림 설계 철학

```
                    알림의 피라미드

                  ╱ PagerDuty/전화 ╲         ← 즉시 행동 필요 (서비스 다운)
                 ╱   Slack 긴급채널  ╲        ← 30분 내 확인 필요
                ╱    Slack 일반채널    ╲       ← 업무시간 내 확인
               ╱      대시보드 표시     ╲      ← 참고용, 알림 불필요
              ╱   로그에만 기록 (기본)    ╲     ← 나중에 필요하면 검색
```

**원칙 1: 알림은 심각도에 따라 다른 채널로 보낸다**
**원칙 2: 모든 알림에는 즉각적인 행동(Action)이 연결되어야 한다**
**원칙 3: "흥미롭지만 긴급하지 않은 것"은 알림이 아니라 대시보드에 표시**

### 알림 분류 체계

| 심각도 | 기준 | 액션 | 예시 |
|--------|------|------|------|
| **Critical (P1)** | 서비스가 사용 불가 | 즉시 대응. 전화/PagerDuty | 서비스 다운, 100% 에러율 |
| **Warning (P2)** | 서비스 저하, 곧 장애 가능 | 30분 내 확인. Slack 긴급 | 에러율 5% 초과, p99 > 2초 |
| **Info (P3)** | 비정상이지만 서비스 영향 없음 | 업무시간 확인. Slack 일반 | GC 빈번, 디스크 80% |

### ShortenUrl에 적용할 알림 규칙

#### Critical Alerts (즉시 대응)

```yaml
# Alert 1: 서비스 다운
- alert: ServiceDown
  expr: up{application="shortenurlservice"} == 0
  for: 1m   # 1분간 지속되면 발동 (일시적 재시작 제외)
  labels:
    severity: critical
  annotations:
    summary: "ShortenUrl 서비스가 다운되었습니다"
    action: "서버 상태 확인, 로그 확인, 재시작 필요"

# Alert 2: 5xx 에러율 급증
- alert: HighErrorRate
  expr: |
    sum(rate(http_server_requests_seconds_count{application="shortenurlservice", status=~"5.."}[5m]))
    /
    sum(rate(http_server_requests_seconds_count{application="shortenurlservice"}[5m]))
    > 0.1
  for: 2m   # 2분간 10% 이상이면 발동
  labels:
    severity: critical
  annotations:
    summary: "5xx 에러율이 {{ $value | humanizePercentage }} 입니다"
    action: "Grafana 에러 패널 확인 → Loki에서 ERROR 로그 검색 → 트레이스 추적"
```

#### Warning Alerts (30분 내 확인)

```yaml
# Alert 3: 응답 시간 저하
- alert: HighLatency
  expr: |
    histogram_quantile(0.95,
      sum(rate(http_server_requests_seconds_bucket{application="shortenurlservice"}[5m])) by (le)
    ) > 1
  for: 5m   # 5분간 p95 > 1초이면 발동
  labels:
    severity: warning
  annotations:
    summary: "p95 응답 시간이 {{ $value }}초입니다"
    action: "엔드포인트별 응답시간 확인, GC/CPU 패널 확인"

# Alert 4: JVM 힙 메모리 부족
- alert: HighHeapUsage
  expr: |
    jvm_memory_used_bytes{application="shortenurlservice", area="heap"}
    / jvm_memory_max_bytes{application="shortenurlservice", area="heap"}
    > 0.85
  for: 5m   # 5분간 85% 이상
  labels:
    severity: warning
  annotations:
    summary: "JVM 힙 메모리 사용률이 {{ $value | humanizePercentage }}입니다"
    action: "메모리 누수 의심, 힙 덤프 분석 고려"

# Alert 5: 4xx 에러 급증 (비정상 접근 패턴)
- alert: High4xxRate
  expr: |
    sum(rate(http_server_requests_seconds_count{application="shortenurlservice", status=~"4.."}[5m]))
    > 10
  for: 5m   # 5분간 초당 10건 이상
  labels:
    severity: warning
  annotations:
    summary: "4xx 에러가 비정상적으로 많습니다 ({{ $value }}/s)"
    action: "악의적 접근 여부 확인, 404 로그에서 패턴 분석"
```

### 알림을 Prometheus에서 설정하는 방법

#### 파일 구조

```
monitoring/
  prometheus/
    prometheus.yml      ← 스크래핑 설정
    alert-rules.yml     ← 알림 규칙 정의 (NEW)
  alertmanager/
    alertmanager.yml    ← 알림 라우팅/수신자 설정 (NEW)
```

#### prometheus.yml에 알림 규칙 연결

```yaml
global:
  scrape_interval: 15s

# 알림 규칙 파일 경로
rule_files:
  - /etc/prometheus/alert-rules.yml

# Alertmanager 연결
alerting:
  alertmanagers:
    - static_configs:
        - targets: ["alertmanager:9093"]

scrape_configs:
  - job_name: "shortenurlservice"
    metrics_path: "/actuator/prometheus"
    static_configs:
      - targets: ["host.docker.internal:8080"]
```

#### alert-rules.yml (전체)

```yaml
groups:
  - name: shortenurlservice
    rules:
      # 위에서 정의한 5개 알림 규칙을 여기에 배치
      - alert: ServiceDown
        expr: up{application="shortenurlservice"} == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "ShortenUrl 서비스 다운"

      - alert: HighErrorRate
        # ... (위 내용 그대로)

      # 나머지 규칙들...
```

#### alertmanager.yml (Slack 연동 예시)

```yaml
global:
  resolve_timeout: 5m

route:
  group_by: ['alertname', 'severity']
  group_wait: 10s        # 같은 그룹의 알림을 10초간 모아서 한번에
  group_interval: 5m     # 같은 알림 재발송 간격
  repeat_interval: 4h    # 해결 안 된 알림 재알림 간격
  receiver: 'default'

  routes:
    # Critical → 긴급 채널
    - match:
        severity: critical
      receiver: 'slack-critical'
      repeat_interval: 30m

    # Warning → 일반 채널
    - match:
        severity: warning
      receiver: 'slack-warning'
      repeat_interval: 4h

receivers:
  - name: 'default'
    # 기본: 아무데도 안 보냄 (개발 환경)

  - name: 'slack-critical'
    slack_configs:
      - api_url: 'https://hooks.slack.com/services/YOUR/WEBHOOK/URL'
        channel: '#alerts-critical'
        title: '🚨 {{ .GroupLabels.alertname }}'
        text: '{{ .CommonAnnotations.summary }}\n행동: {{ .CommonAnnotations.action }}'

  - name: 'slack-warning'
    slack_configs:
      - api_url: 'https://hooks.slack.com/services/YOUR/WEBHOOK/URL'
        channel: '#alerts-warning'
        title: '⚠️ {{ .GroupLabels.alertname }}'
        text: '{{ .CommonAnnotations.summary }}'
```

### Alert Fatigue 방지 체크리스트

```
알림을 추가하기 전에 스스로에게 물어볼 것:

□ 이 알림이 울리면 내가 즉시 무언가를 해야 하는가?
  → No이면 알림이 아니라 대시보드에 표시

□ 이 알림의 임계값은 실제 문제 상황에서만 초과되는가?
  → 정상 상황에서도 자주 초과되면 임계값 조정 필요

□ for 절이 충분히 길어서 일시적 스파이크를 무시하는가?
  → for: 0s → 노이즈 폭발. 최소 1~2분은 줘라

□ 같은 문제에 대해 여러 알림이 동시에 울리지 않는가?
  → group_by로 묶거나, 하위 알림은 상위가 firing일 때 억제(inhibit)

□ 해결 방법(action)이 annotation에 적혀 있는가?
  → 알림을 받은 사람이 "그래서 뭘 해야 하지?"라고 고민하면 안 됨
```

### 개발 환경에서의 알림

처음부터 Slack/PagerDuty를 연동할 필요 없다. 단계적으로:

1. **1단계 (지금)**: Prometheus Alert Rules만 정의 → Grafana에서 "Firing Alerts" 패널로 확인
2. **2단계**: Alertmanager 추가 → 로컬에서 알림 흐름 테스트
3. **3단계 (프로덕션)**: Slack/PagerDuty 실제 연동

### docker-compose.yml에 Alertmanager 추가 (나중에)

```yaml
  alertmanager:
    image: prom/alertmanager:v0.27.0
    container_name: alertmanager
    ports:
      - "9093:9093"
    volumes:
      - ./monitoring/alertmanager/alertmanager.yml:/etc/alertmanager/alertmanager.yml:ro
```

</plan>

---

<recommend>

## 최종 실행 로드맵 (전체 업데이트)

### Phase 1: 개념 학습 ✅ 완료
- Observability 3대 축 (Logs, Metrics, Traces)
- OpenTelemetry Spec & Semantic Conventions
- 로그 레벨 전략 & 설계 패턴
- RED/USE Method
- LogQL 쿼리 문법
- Grafana 대시보드 설계
- 알림 전략 & Alert Fatigue 방지

### Phase 2: 메트릭 구현 (코드 직접 작성)
1. pom.xml에 Actuator + Micrometer Prometheus 추가
2. application.yaml에 Actuator 설정
3. docker-compose.yml로 Prometheus + Grafana 기동
4. Service Overview 대시보드 구성 (위 PromQL 참고)
5. JVM Internals 대시보드 구성

### Phase 3: 로그 구현
1. pom.xml에 loki-logback-appender 추가
2. logback-spring.xml 작성 (JSON 구조화 + Loki push)
3. docker-compose.yml에 Loki 추가
4. Grafana에 Loki 데이터소스 추가
5. LogQL로 로그 검색 실습

### Phase 4: 트레이싱 구현
1. pom.xml에 micrometer-tracing-bridge-otel + otlp-exporter 추가
2. application.yaml에 tracing 설정
3. docker-compose.yml에 Tempo 추가
4. Loki ↔ Tempo 연동 (derivedFields)
5. 로그에서 traceId 클릭 → 트레이스 확인

### Phase 5: 알림 구현
1. alert-rules.yml 작성 (5개 핵심 규칙)
2. Grafana에서 Firing Alerts 패널 확인
3. (선택) Alertmanager + Slack 연동

### 이 문서를 프로젝트에 저장
`docs/02-observability-concepts.md`로 저장하여 참고 자료로 활용

</recommend>
