# Plan 5: 분산 추적 — traceId / spanId

> **인프라 단계**: MSA (마이크로서비스 아키텍처)
> **선행 조건**: Plan 1~4 완료
> **핵심 질문**: "여러 서비스를 거치는 요청에서, 어느 서비스가 병목인가?"

---

## 이 단계의 목표

하나의 사용자 요청이 **여러 서비스를 거칠 때**, 요청의 전체 경로를 추적하고 **어느 서비스의 어느 작업에서 시간이 소비되는지** 정확히 파악할 수 있게 만든다.

---

## 왜 이 단계가 필요한가

### requestId의 한계

Plan 1에서 도입한 `requestId`는 **단일 서비스 내에서만 유효**하다.

MSA 환경에서 URL 단축 서비스가 다른 서비스를 호출한다고 하자:

```
[클라이언트] → [API Gateway] → [shortenUrlService] → [analyticsService] → [notificationService]
                                     ↓
                               [urlValidationService]
```

사용자가 URL 단축을 요청하면:
1. `shortenUrlService`가 URL을 단축하고
2. `urlValidationService`에 원본 URL의 유효성 검사를 요청하고
3. `analyticsService`에 생성 이벤트를 기록하고
4. `notificationService`에 알림을 보낸다

이때 각 서비스의 로그:

```
# shortenUrlService (requestId: a1b2c3d4)
12:00:01 INFO [a1b2c3d4] - URL 단축 요청
12:00:01 INFO [a1b2c3d4] - urlValidationService 호출
12:00:02 INFO [a1b2c3d4] - URL 단축 완료 (1200ms)

# urlValidationService (requestId: x9y8z7w6) ← 별도 requestId 생성!
12:00:01 INFO [x9y8z7w6] - URL 유효성 검사 요청
12:00:02 INFO [x9y8z7w6] - URL 유효성 검사 완료 (800ms)

# analyticsService (requestId: m3n4o5p6) ← 또 별도 requestId!
12:00:02 INFO [m3n4o5p6] - 이벤트 기록
```

**문제**: `a1b2c3d4`, `x9y8z7w6`, `m3n4o5p6`는 **서로 연결되지 않는다**. 전체 요청이 1200ms 걸렸는데, urlValidationService의 800ms가 원인인지 알려면 **시각을 눈으로 대조**해야 한다.

### traceId가 해결하는 것

traceId는 **서비스 간에 전파**된다. HTTP 요청 시 `traceparent` 헤더로 자동 전달:

```
# shortenUrlService
12:00:01 INFO [traceId=abc123/spanId=001] - URL 단축 요청
12:00:01 INFO [traceId=abc123/spanId=002] - urlValidationService 호출

# urlValidationService (같은 traceId!)
12:00:01 INFO [traceId=abc123/spanId=003] - URL 유효성 검사 요청
12:00:02 INFO [traceId=abc123/spanId=003] - URL 유효성 검사 완료 (800ms)

# analyticsService (같은 traceId!)
12:00:02 INFO [traceId=abc123/spanId=004] - 이벤트 기록

# shortenUrlService
12:00:02 INFO [traceId=abc123/spanId=001] - URL 단축 완료 (1200ms)
```

→ `traceId=abc123`으로 **모든 서비스의 로그를 한 번에 조회** 가능

### spanId의 역할 — "어느 작업이 느린가"

하나의 trace 안에서 각 **작업 단위**가 span이다:

```
trace: abc123
├── span 001: shortenUrlService (전체 1200ms)
│   ├── span 002: urlValidationService 호출 (800ms)  ← 병목!
│   ├── span 003: DB 저장 (15ms)
│   └── span 004: analyticsService 호출 (50ms)
```

이것이 Grafana Tempo에서 **워터폴(waterfall) 뷰**로 시각화된다. 한 눈에 span 002(urlValidationService)가 병목임을 알 수 있다.

---

## AS-IS (현재 상태 — Plan 4 완료 후)

### 로그에 포함된 식별자

```
2026-03-24 12:00:01 INFO [http-nio-8080-exec-1] [a1b2c3d4] ShortenUrlRestController - URL 단축 요청 originalUrl=https://naver.com
```

- `requestId`: 단일 서비스 내에서만 유효
- `traceId`: **없음**
- `spanId`: **없음**

### 아키텍처

```
[App] --HTTP--> [Loki] <-- [Grafana]
```

→ 로그만 수집. 트레이스 수집 없음.

### 한계

| 한계 | 상세 |
|------|------|
| 서비스 간 추적 불가 | requestId가 서비스 경계를 넘지 못함 |
| 작업 단위 구분 불가 | 하나의 요청 안에서 "DB 조회 15ms, 외부 API 호출 800ms"를 구분할 수 없음 |
| 워터폴 뷰 불가 | 시간 소비를 시각적으로 볼 수 없음 |

---

## TO-BE (목표 상태)

### 아키텍처

```
[App] --HTTP--> [Loki]  <── logs
[App] --OTLP--> [Tempo] <── traces     ← 신규
                  ↑
              [Grafana] (Loki + Tempo 통합)
```

### 1. pom.xml 의존성 추가

```xml
<!-- Micrometer Tracing (Spring Boot 3.x 표준 트레이싱) -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>

<!-- OpenTelemetry OTLP 내보내기 (Tempo로 전송) -->
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>
```

#### 왜 이 의존성들인가

**micrometer-tracing-bridge-otel**:
- Spring Boot 3.x의 **공식 트레이싱 추상화**
- Spring Boot 2.x에서는 Spring Cloud Sleuth가 이 역할을 했지만, 3.x에서 Micrometer Tracing으로 대체됨
- "bridge-otel"은 Micrometer의 트레이싱 API를 **OpenTelemetry 구현체에 연결**하는 브릿지
- 왜 OpenTelemetry인가: 벤더 중립 표준. Jaeger, Tempo, Zipkin 어디든 전환 가능

**opentelemetry-exporter-otlp**:
- 트레이스 데이터를 **OTLP(OpenTelemetry Protocol)** 형식으로 Tempo에 전송
- 왜 OTLP인가: OpenTelemetry의 표준 프로토콜. Tempo, Jaeger 등 대부분의 트레이스 백엔드가 지원

### 2. application.yaml 트레이싱 설정

```yaml
management:
  tracing:
    sampling:
      probability: 1.0  # 모든 요청의 트레이스를 수집 (개발환경)
  otlp:
    tracing:
      endpoint: http://localhost:4318/v1/traces
```

#### 각 설정의 의미

**sampling.probability: 1.0**
- 1.0 = 100%의 요청에 대해 트레이스를 생성
- **왜 100%인가**: 학습/개발 환경에서는 모든 요청을 추적해야 동작을 확인할 수 있음
- **프로덕션에서는**: 0.1 (10%) ~ 0.01 (1%)로 설정. 모든 요청을 추적하면 Tempo 저장 비용 급증
- 중요한 요청만 추적하고 싶으면 코드에서 `@Observed` 어노테이션으로 선별 가능

**otlp.tracing.endpoint**
- 트레이스를 어디로 보낼지
- Tempo가 `localhost:4318`에서 OTLP HTTP를 수신

### 3. logback-spring.xml 패턴 변경

```xml
<!-- 변경 전 (Plan 1~4) -->
<pattern>%d{yyyy-MM-dd HH:mm:ss} %-5level [%thread] [%X{requestId}] %logger{36} - %msg%n</pattern>

<!-- 변경 후 -->
<pattern>%d{yyyy-MM-dd HH:mm:ss} %-5level [%thread] [%X{traceId}/%X{spanId}] [%X{requestId}] %logger{36} - %msg%n</pattern>
```

#### 왜 traceId와 requestId를 둘 다 유지하는가

| | traceId | requestId |
|---|---|---|
| 생성 주체 | Micrometer Tracing (자동) | LoggingFilter (직접 생성) |
| 형태 | 32자리 hex | 8자리 UUID |
| 서비스 간 전파 | **됨** (HTTP 헤더 자동 전파) | 안 됨 |
| 가독성 | 길어서 눈으로 읽기 어려움 | 짧아서 읽기 쉬움 |
| 용도 | 분산 추적, Tempo 연동 | 단일 서비스 내 빠른 식별 |

두 가지를 모두 유지하면:
- **로그 파일에서 빠르게 검색**: 짧은 requestId 사용
- **서비스 간 추적**: 긴 traceId 사용
- **Grafana에서 로그 → 트레이스 연결**: traceId 클릭 → Tempo 워터폴

### 4. traceId/spanId 자동 MDC 주입 — 왜 코드 변경이 없는가

Spring Boot 3.x + Micrometer Tracing이 클래스패스에 있으면:

1. Spring Boot가 자동으로 `ObservationAutoConfiguration`을 활성화
2. 모든 HTTP 요청 진입 시 `traceId`와 `spanId`를 생성
3. 생성된 값을 **자동으로 MDC에 삽입** (`MDC.put("traceId", ...)`)
4. HTTP 클라이언트(RestTemplate, WebClient) 호출 시 `traceparent` 헤더를 **자동으로 추가**
5. 요청 완료 후 MDC에서 자동 제거

**코드를 한 줄도 작성하지 않아도** traceId가 모든 로그에 나타난다. 이것이 Micrometer Tracing의 핵심 가치다.

### 5. docker-compose에 Tempo 추가

```yaml
# docker-compose-loki.yml에 추가
  tempo:
    image: grafana/tempo:2.3.1
    ports:
      - "4318:4318"    # OTLP HTTP
      - "3200:3200"    # Tempo query API
    command: [ "-config.file=/etc/tempo.yaml" ]
    volumes:
      - ./tempo/tempo.yaml:/etc/tempo.yaml
```

`tempo/tempo.yaml`:
```yaml
server:
  http_listen_port: 3200

distributor:
  receivers:
    otlp:
      protocols:
        http:

storage:
  trace:
    backend: local
    local:
      path: /tmp/tempo/blocks
```

Grafana 데이터소스에 Tempo 추가:
```yaml
# grafana/provisioning/datasources/tempo.yml
apiVersion: 1
datasources:
  - name: Tempo
    type: tempo
    access: proxy
    url: http://tempo:3200
```

---

## 변경 항목별 상세 설명

### 항목 1: Micrometer Tracing — 왜 Spring Cloud Sleuth가 아닌가

Spring Cloud Sleuth는 Spring Boot **2.x**의 트레이싱 솔루션이었다. Spring Boot 3.x에서는:

- Sleuth가 **더 이상 유지보수되지 않음** (deprecated)
- Micrometer Tracing이 공식 후속
- Micrometer는 메트릭(Prometheus) + 트레이싱(OTel)을 **하나의 API**로 통합

### 항목 2: W3C Trace Context — traceId가 어떻게 서비스 간에 전달되는가

서비스 A가 서비스 B를 HTTP로 호출할 때:

```
GET /api/validate HTTP/1.1
Host: urlValidationService
traceparent: 00-abc123def456...-001-01    ← 자동 추가!
```

`traceparent` 헤더는 **W3C Trace Context** 표준이다:
- `00`: 버전
- `abc123def456...`: traceId (32자리 hex)
- `001`: spanId (16자리 hex)
- `01`: 플래그 (sampled)

서비스 B는 이 헤더를 받아서:
1. 같은 traceId를 사용 (새로 생성하지 않음)
2. 새로운 spanId를 생성 (자신의 작업 단위)
3. 자신의 MDC에 traceId/spanId를 넣음 → 로그에 자동 포함

**Micrometer Tracing이 이 모든 과정을 자동으로 처리**한다. RestTemplate이나 WebClient를 사용하면 `traceparent` 헤더가 자동 추가된다.

### 항목 3: Tempo — 왜 Jaeger가 아닌가

| | Jaeger | Tempo |
|---|---|---|
| 인덱싱 | traceId를 인덱싱 | **인덱싱 안 함** (Loki 방식과 동일) |
| 저장 비용 | 상대적으로 높음 | 매우 낮음 |
| Grafana 통합 | 가능하지만 별도 데이터소스 | **네이티브 통합** (Loki에서 traceId 클릭 → Tempo) |
| 검색 방식 | traceId로 직접 검색 | Loki에서 traceId 찾고 → Tempo로 이동 |

Tempo를 선택하는 이유:
1. **Loki와 같은 철학**: 인덱싱 최소화 → 비용 절감
2. **Grafana 통합**: Loki 로그에서 traceId 클릭 → Tempo 워터폴 뷰로 원클릭 전환
3. **이미 Grafana를 사용 중** (Plan 4에서 설정)

### 항목 4: span의 구조 — 자동 생성 vs 수동 생성

Micrometer Tracing이 **자동으로 생성하는 span**:
- HTTP 요청 수신 (Controller 진입~응답)
- HTTP 요청 발신 (RestTemplate/WebClient 호출)
- 데이터베이스 쿼리 (JDBC)

**수동으로 생성해야 하는 span** (이 단계에서는 선택):
- 비즈니스 로직의 특정 구간 (예: 키 생성 재시도 루프)

자동 span만으로도 "어느 서비스 호출이 느린가"는 파악 가능하다. 내부 로직의 세밀한 구간 측정은 Plan 6에서 메트릭(Timer)으로 보완한다.

---

## 이점

### 시나리오: "URL 단축 API가 1.2초 걸린다, 원인을 찾아라" (MSA 환경)

**AS-IS (Plan 4까지)**:
1. Grafana에서 `durationMs > 1000`인 로그를 찾음
2. requestId로 shortenUrlService의 로그를 추적 → "urlValidationService 호출" 로그 발견
3. 하지만 urlValidationService에서 실제로 얼마나 걸렸는지는 **urlValidationService의 로그를 별도로 검색**해야 함
4. 두 서비스의 로그를 **시간대로 눈으로 대조**하면서 추정
5. 소요 시간: **10~20분**

**TO-BE (Plan 5)**:
1. Grafana에서 `durationMs > 1000`인 로그를 찾음
2. traceId를 클릭 → Tempo 워터폴 뷰가 나타남:
   ```
   [shortenUrlService]     ████████████████████ 1200ms
     [urlValidationService]  █████████████████ 800ms   ← 병목!
     [DB save]               ██ 15ms
     [analyticsService]      ███ 50ms
   ```
3. urlValidationService가 800ms → **병목 원인 즉시 확인**
4. 소요 시간: **30초**

### 시나리오: "간헐적으로 타임아웃, 어느 서비스가 원인인지 모른다"

**AS-IS**: 각 서비스의 로그를 따로 검색, 시간대 대조 → 원인 서비스를 찾는 데 수십 분
**TO-BE**: 타임아웃 에러 로그의 traceId → Tempo 워터폴 → 타임아웃을 유발한 span이 어느 서비스의 어느 호출인지 즉시 확인

---

## TO-BE 로그 출력 예시

```
2026-03-24 12:00:01 INFO [exec-1] [abc123def456.../001a] [a1b2c3d4] ShortenUrlRestController - URL 단축 요청 originalUrl=https://naver.com
2026-03-24 12:00:01 INFO [exec-1] [abc123def456.../002b] [a1b2c3d4] SimpleShortenUrlService - shortenUrl 생성 shortenUrlKey=xyz keyGenDurationMs=15 saveDurationMs=10
2026-03-24 12:00:01 INFO [exec-1] [abc123def456.../001a] [a1b2c3d4] ShortenUrlRestController - URL 단축 완료 shortenUrlKey=xyz
2026-03-24 12:00:01 INFO [exec-1] [abc123def456.../001a] [a1b2c3d4] LoggingFilter - 요청 완료 statusCode=200 durationMs=45
```

→ `traceId=abc123def456...`가 모든 로그에 자동 포함
→ `spanId`가 작업 단위별로 다름 (Controller: 001a, Service: 002b)

---

## 수정 대상 파일

| 파일 | 변경 내용 |
|------|----------|
| `pom.xml` | `micrometer-tracing-bridge-otel`, `opentelemetry-exporter-otlp` 추가 |
| `src/main/resources/application.yaml` | tracing 설정 (sampling, endpoint) |
| `src/main/resources/logback-spring.xml` | 패턴에 `[%X{traceId}/%X{spanId}]` 추가 |
| `docker-compose-loki.yml` 또는 신규 파일 | Tempo 서비스 추가 |
| 신규: `tempo/tempo.yaml` | Tempo 설정 |
| 신규: `grafana/provisioning/datasources/tempo.yml` | Tempo 데이터소스 |

---

## 검증 방법

### 1. traceId 자동 출력 확인
앱을 기동하고 요청을 보낸 후, 콘솔 로그에 `[traceId/spanId]`가 나타나는지 확인한다.
traceId가 빈 값(`[/]`)이 아닌 32자리 hex인지 확인한다.

### 2. 같은 요청의 모든 로그가 같은 traceId인지 확인
requestId로 grep한 결과와 traceId로 grep한 결과가 동일한 로그 세트인지 확인한다.

### 3. Tempo 워터폴 확인
1. Grafana → Explore → Tempo 데이터소스 선택
2. traceId를 입력하여 검색
3. 워터폴 뷰에서 span들이 나타나는지 확인

### 4. Loki → Tempo 연동 확인
1. Grafana → Explore → Loki 데이터소스
2. 로그 검색 후 traceId 필드 클릭
3. "View trace in Tempo" 링크가 나타나고, 클릭하면 Tempo 워터폴로 이동하는지 확인

### 5. (선택) 서비스 간 traceId 전파 확인
두 번째 서비스가 있다면, RestTemplate으로 호출 시 `traceparent` 헤더가 자동 추가되는지 확인:
```java
// shortenUrlService에서 다른 서비스 호출
restTemplate.getForObject("http://other-service/api", String.class);
```
→ other-service의 로그에 **같은 traceId**가 나타나는지 확인
