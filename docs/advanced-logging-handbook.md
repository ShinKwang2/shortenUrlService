# 🔭 ADVANCED LOGGING HANDBOOK

### 분산 트레이싱 · 로그 파이프라인 고도화 · 통합 Observability 완벽 가이드

> **From ELK to Full Observability**
> 기본 핸드북을 마친 개발자를 위한 심화 가이드
> OpenTelemetry · Jaeger · Filebeat · Kafka · Loki · Tempo · Mimir

---

# 목차

## PART 1. 분산 트레이싱 (Distributed Tracing)
- 1장. 분산 트레이싱은 왜 탄생했는가?
- 2장. 분산 트레이싱의 핵심 개념 — Trace, Span, Context Propagation
- 3장. OpenTelemetry란 무엇인가? — 표준의 탄생
- 4장. Jaeger와 Zipkin — 트레이싱 백엔드 비교
- 5장. [실습] Jaeger 설치 및 Spring Boot 연동
- 6장. [실습] 마이크로서비스 간 Trace ID 전파 실습
- 7장. Spring Cloud Sleuth에서 Micrometer Tracing으로의 전환
- 8장. [실습] Micrometer Tracing + OpenTelemetry 설정
- 9장. [실습] 로그에 Trace ID 자동 삽입하기
- 10장. 분산 트레이싱 실전 패턴과 안티패턴

## PART 2. 로그 파이프라인 고도화
- 11장. 왜 Logstash만으로는 부족한가?
- 12장. Filebeat — 경량 로그 수집기의 모든 것
- 13장. [실습] Filebeat 설치 및 설정
- 14장. [실습] Filebeat 멀티라인 로그 처리 (스택트레이스)
- 15장. Kafka를 로그 파이프라인에 도입하는 이유
- 16장. [실습] Kafka 클러스터 구성 및 로그 버퍼링
- 17장. [실습] Filebeat → Kafka → Logstash → Elasticsearch 파이프라인
- 18장. Elasticsearch ILM(인덱스 생명주기 관리)
- 19장. [실습] ILM 정책 설정 — Hot/Warm/Cold/Delete
- 20장. Elasticsearch 클러스터 운영 기초
- 21장. [실습] ES 클러스터 3노드 구성 및 샤드 전략

## PART 3. 통합 Observability — Grafana Stack
- 22장. 왜 Grafana Stack인가? — ELK에서의 전환
- 23장. OpenTelemetry Collector — 통합 수집의 중심
- 24장. [실습] OpenTelemetry Collector 설치 및 설정
- 25장. Grafana Loki — 로그 전용 저장소
- 26장. [실습] Loki 설치 및 Spring Boot 연동
- 27장. [실습] Promtail 설정과 로그 수집
- 28장. [실습] Loki LogQL 쿼리 마스터하기
- 29장. Grafana Tempo — 분산 트레이싱 저장소
- 30장. [실습] Tempo 설치 및 Trace 연동
- 31장. Grafana Mimir — 메트릭 장기 저장소
- 32장. [실습] Mimir 설치 및 Prometheus 원격 저장
- 33장. [실습] Grafana에서 로그-메트릭-트레이스 통합 조회
- 34장. 최종 프로덕션 아키텍처와 비용 최적화
- 35장. Observability 성숙도 모델과 앞으로의 방향

---

# PART 1. 분산 트레이싱 (Distributed Tracing)

---

## 1장. 분산 트레이싱은 왜 탄생했는가?

### 1.1 모놀리스에서 마이크로서비스로

2010년대 중반, 넷플릭스와 아마존이 수백 개의 마이크로서비스로 전환하면서 새로운 문제가 등장했습니다.

```
모놀리스 시대 (서버 1대):
  사용자 요청 → [주문 로직] → [결제 로직] → [재고 로직] → 응답
  → 한 서버 안에서 모든 로그가 나옴
  → 디버깅: grep 한 번이면 됨

마이크로서비스 시대 (서버 N대):
  사용자 요청 → [API Gateway] → [주문 서비스] → [결제 서비스] → [재고 서비스]
                     ↓                ↓               ↓
                  Server A         Server B         Server C
  
  → 로그가 3대의 서버에 흩어져 있음
  → "이 요청이 어디서 느려진 거지?" → 찾을 수 없음
```

### 1.2 기존 방식의 한계

```
시도 1: 각 서버에 SSH 접속해서 로그 검색
  → 서버가 50대면 50번 반복? 불가능

시도 2: ELK로 중앙 수집 후 검색
  → "결제 실패" 로그를 찾았는데, 어느 사용자의 어느 요청인지 모름
  → 같은 시간에 1000명이 결제했으면?

시도 3: MDC의 requestId로 추적
  → 한 서비스 안에서는 OK
  → 하지만 주문 서비스의 requestId와 결제 서비스의 requestId가 다름!
  → 서비스 간 연결 고리가 없음
```

### 1.3 분산 트레이싱의 탄생

2010년 구글이 내부 분산 트레이싱 시스템 **Dapper**에 관한 논문을 발표했습니다. 이 논문의 핵심 아이디어는 단순했습니다.

```
"하나의 사용자 요청이 여러 서비스를 거칠 때,
 모든 서비스가 같은 ID를 공유하면 추적할 수 있지 않을까?"
```

이 아이디어에서 **Trace ID**라는 개념이 탄생했고, 이후 Twitter의 Zipkin(2012), Uber의 Jaeger(2017), 그리고 표준화 프로젝트인 OpenTelemetry(2019)로 발전했습니다.

```
Google Dapper 논문 (2010)
  ↓
Twitter → Zipkin (2012) — 최초의 오픈소스 분산 트레이싱
  ↓
Uber → Jaeger (2017) — CNCF 프로젝트, 현재 가장 대중적
  ↓
OpenTracing + OpenCensus → OpenTelemetry (2019) — 업계 표준 통합
```

---

## 2장. 분산 트레이싱의 핵심 개념 — Trace, Span, Context Propagation

### 2.1 Trace와 Span

분산 트레이싱을 이해하려면 딱 두 가지 개념만 알면 됩니다.

```
Trace (트레이스):
  = 하나의 사용자 요청이 시스템을 통과하는 전체 여정
  = 하나의 고유한 Trace ID를 가짐

Span (스팬):
  = 하나의 작업 단위 (서비스 호출, DB 쿼리, HTTP 요청 등)
  = 하나의 Trace 안에 여러 개의 Span이 존재
  = 각 Span은 고유한 Span ID를 가짐
  = 부모-자식 관계를 가질 수 있음 (Parent Span ID)
```

실제 예시로 보겠습니다. 사용자가 웨이팅을 등록하는 요청 하나가 만드는 트레이스입니다.

```
Trace ID: abc-123-def-456

[Span A] API Gateway (전체 요청)          ──────────────────────────────────
  ├── [Span B] 주문 서비스: POST /waiting   ──────────────────────
  │     ├── [Span C] DB 조회: 중복 확인         ─────────
  │     ├── [Span D] DB 삽입: 웨이팅 등록              ──────
  │     └── [Span E] 알림 서비스 호출                        ────────────
  │           └── [Span F] SMS 발송                              ──────
  └── 완료

시간축 →→→→→→→→→→→→→→→→→→→→→→→→→→→→→→→→→→→→→→→→→→→→→→→

각 Span이 기록하는 정보:
  - Trace ID     : abc-123-def-456 (모든 Span이 동일)
  - Span ID      : 각자 고유
  - Parent Span ID: 부모 Span의 ID
  - Operation Name: "POST /waiting", "SELECT waiting", 등
  - Start Time   : 시작 시각
  - Duration     : 소요 시간
  - Tags/Attributes: HTTP 메서드, 상태 코드, 에러 여부 등
  - Events/Logs  : Span 내에서 발생한 이벤트
```

### 2.2 Span의 상세 구조

```json
{
  "traceId": "abc123def456",
  "spanId": "span-001",
  "parentSpanId": null,
  "operationName": "POST /api/waiting",
  "serviceName": "waiting-service",
  "startTime": "2024-01-15T14:30:01.000Z",
  "duration": 245,
  "tags": {
    "http.method": "POST",
    "http.url": "/api/waiting",
    "http.status_code": 200,
    "user.id": "1001"
  },
  "logs": [
    {
      "timestamp": "2024-01-15T14:30:01.050Z",
      "message": "중복 확인 완료"
    },
    {
      "timestamp": "2024-01-15T14:30:01.150Z",
      "message": "웨이팅 등록 완료 - waitingNumber: 7"
    }
  ]
}
```

### 2.3 Context Propagation (컨텍스트 전파)

분산 트레이싱의 **가장 핵심적인 메커니즘**입니다. 서비스 A가 서비스 B를 호출할 때, **Trace ID와 Span ID를 HTTP 헤더에 실어서 전달**합니다.

```
[주문 서비스]                              [결제 서비스]
     │                                        │
     │  HTTP 요청                              │
     │  ──────────────────────────────→       │
     │  헤더:                                  │
     │    traceparent: 00-abc123-span01-01    │
     │    tracestate: waiting=true            │
     │                                        │
     │  결제 서비스가 헤더에서 추출:             │
     │                                        │
     │    Trace ID: abc123 (동일하게 유지!)     │
     │    Parent Span ID: span01              │
     │    새 Span ID: span02 (새로 생성)       │
     │                                        │
```

**W3C Trace Context 표준 헤더:**

```
traceparent: {version}-{trace-id}-{parent-span-id}-{trace-flags}
예: traceparent: 00-abc123def456789-span001234-01

  00       : 버전 (현재 00 고정)
  abc123.. : Trace ID (32자 hex)
  span00.. : Parent Span ID (16자 hex)  
  01       : Trace Flags (01 = 샘플링됨)
```

### 2.4 샘플링(Sampling) — 모든 요청을 추적하면 안 되는 이유

```
일일 요청 1억 건 × Span 평균 5개 = 5억 개의 Span 데이터
  → 저장 비용 폭발
  → 네트워크 부하
  → 트레이싱 백엔드 과부하

해결: 샘플링 — 전체의 일부만 트레이싱

샘플링 전략:
1. Probabilistic (확률 기반): 전체의 1%만 추적
   → 간단하지만 중요한 에러 요청을 놓칠 수 있음

2. Rate Limiting (속도 제한): 초당 최대 10건만 추적
   → 일정한 데이터량 유지

3. Tail-based (꼬리 기반): 일단 모두 추적하고, 에러/느린 요청만 저장
   → 가장 이상적이지만 구현이 복잡 (OTel Collector에서 지원)
```

---

## 3장. OpenTelemetry란 무엇인가? — 표준의 탄생

### 3.1 OpenTelemetry 이전의 혼란

```
2015~2018년의 상황:

트레이싱 라이브러리:
  - OpenTracing (CNCF) ← Jaeger가 사용
  - OpenCensus (Google) ← Stackdriver가 사용

문제:
  - 두 라이브러리가 호환되지 않음
  - 앱에서 라이브러리를 바꾸려면 코드 전체 수정 필요
  - "SLF4J 같은 표준이 왜 트레이싱에는 없지?"

해결:
  2019년, 두 프로젝트가 합쳐서 OpenTelemetry (OTel) 탄생
```

### 3.2 OpenTelemetry의 3대 신호 (Three Pillars of Observability)

```
┌─────────────────────────────────────────────────────┐
│                OpenTelemetry (OTel)                   │
│                                                       │
│  ┌───────────┐  ┌───────────┐  ┌───────────────┐    │
│  │   Traces   │  │  Metrics   │  │    Logs       │    │
│  │  (추적)    │  │  (지표)    │  │   (로그)      │    │
│  │           │  │           │  │              │    │
│  │ 요청 흐름  │  │ 수치 측정  │  │ 이벤트 기록   │    │
│  │ 어디서     │  │ 얼마나    │  │ 무슨 일이     │    │
│  │ 느려졌나?  │  │ 바쁜가?   │  │ 일어났나?    │    │
│  └───────────┘  └───────────┘  └───────────────┘    │
│                                                       │
│  → 이 세 가지를 하나의 SDK로 통합 수집                   │
│  → 백엔드(Jaeger, Prometheus, Loki 등)는 자유롭게 교체   │
└─────────────────────────────────────────────────────┘
```

### 3.3 OpenTelemetry의 구성 요소

| 구성 요소 | 역할 | 비유 |
|----------|------|------|
| **OTel SDK** | 앱에서 데이터 생성 | 기자 (취재) |
| **OTel API** | 표준 인터페이스 (Facade) | 기사 작성 양식 |
| **OTel Collector** | 데이터 수집/가공/전달 | 편집국 (수거+편집+배포) |
| **Exporters** | 백엔드로 데이터 전송 | 배달부 |

### 3.4 OTLP(OpenTelemetry Protocol)란?

```
OTLP = OpenTelemetry가 정의한 데이터 전송 표준 프로토콜

특징:
  - gRPC (기본, 포트 4317) 또는 HTTP (포트 4318) 지원
  - Trace, Metric, Log 모두 동일한 프로토콜로 전송
  - 모든 OTel 호환 백엔드가 지원

이전에는:
  Jaeger → Jaeger 전용 프로토콜 (Thrift)
  Zipkin → Zipkin 전용 JSON 포맷
  → 백엔드마다 다른 프로토콜을 써야 했음

OTLP 이후:
  모든 백엔드가 OTLP를 지원
  → 백엔드를 교체해도 앱 코드 변경 불필요
```

---

## 4장. Jaeger와 Zipkin — 트레이싱 백엔드 비교

| 항목 | Jaeger | Zipkin |
|------|--------|--------|
| 개발사 | Uber → CNCF | Twitter |
| 언어 | Go | Java |
| UI | 풍부하고 직관적 | 심플하고 가벼움 |
| 스토리지 | Cassandra, ES, Kafka, Badger | MySQL, ES, Cassandra |
| 샘플링 | 다양한 전략 지원 | 기본 확률 샘플링 |
| OTLP 지원 | 네이티브 지원 | 어댑터 필요 |
| 추천 상황 | 프로덕션, 대규모 | 학습, 소규모 |

---

## 5장. [실습] Jaeger 설치 및 Spring Boot 연동

### 5.1 프로젝트 구조

```
waiting-tracing/
├── waiting-service/          ← 웨이팅 서비스 (포트 8080)
├── notification-service/     ← 알림 서비스 (포트 8081)
├── docker/
│   └── docker-compose.yml
└── ...
```

### 5.2 docker-compose.yml — Jaeger 설치

```yaml
# docker/docker-compose.yml
version: '3.8'

services:
  jaeger:
    image: jaegertracing/all-in-one:1.54
    container_name: jaeger
    environment:
      - SPAN_STORAGE_TYPE=memory
      - COLLECTOR_OTLP_ENABLED=true
    ports:
      - "4317:4317"     # OTLP gRPC (앱 → Jaeger)
      - "4318:4318"     # OTLP HTTP
      - "16686:16686"   # Jaeger UI ★
    networks:
      - tracing-network

networks:
  tracing-network:
    driver: bridge
```

### 5.3 Spring Boot 의존성 설정

```groovy
// waiting-service/build.gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'

    // Micrometer Tracing — Bridge: Micrometer → OpenTelemetry
    implementation 'io.micrometer:micrometer-tracing-bridge-otel'

    // OpenTelemetry Exporter: OTLP 프로토콜로 Jaeger에 전송
    implementation 'io.opentelemetry:opentelemetry-exporter-otlp'

    // 메트릭 (Prometheus)
    implementation 'io.micrometer:micrometer-registry-prometheus'

    // HTTP 클라이언트 (서비스 간 호출, 자동 Trace 전파)
    implementation 'org.springframework.boot:spring-boot-starter-webflux'

    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
}
```

### 5.4 application.yml — 트레이싱 설정

```yaml
# waiting-service/src/main/resources/application.yml
server:
  port: 8080

spring:
  application:
    name: waiting-service   # ★ Jaeger UI에 표시될 서비스 이름

management:
  tracing:
    sampling:
      probability: 1.0    # 1.0=100% (개발), 운영은 0.01~0.1 권장
  otlp:
    tracing:
      endpoint: http://localhost:4317   # Jaeger OTLP gRPC
  endpoints:
    web:
      exposure:
        include: health, prometheus, info

# ★ 로그 패턴에 Trace ID 자동 삽입
logging:
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [traceId=%X{traceId} spanId=%X{spanId}] %-5level %logger{36} - %msg%n"
  level:
    root: INFO
    com.example: DEBUG
```

### 5.5 ObservationConfig — @Observed 활성화에 필수

```java
package com.example.waiting.config;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObservationConfig {
    // 이 Bean이 없으면 @Observed를 붙여도 Span이 생성되지 않습니다
    @Bean
    ObservedAspect observedAspect(ObservationRegistry registry) {
        return new ObservedAspect(registry);
    }
}
```

### 5.6 WaitingService — @Observed로 자동 Span 생성

```java
package com.example.waiting.service;

import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class WaitingService {

    private static final Logger log = LoggerFactory.getLogger(WaitingService.class);
    private final WebClient webClient;
    private final Map<Long, Map<String, Object>> store = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong(1);

    // WebClient.Builder에 Spring Boot가 자동으로 트레이싱 헤더 주입을 설정해줌
    public WaitingService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .baseUrl("http://localhost:8081")  // 알림 서비스
                .build();
    }

    @Observed(name = "waiting.register", contextualName = "register-waiting")
    public Map<String, Object> register(Long userId, Long restaurantId) {
        log.info("웨이팅 등록 시작 - userId: {}, restaurantId: {}", userId, restaurantId);

        Long id = seq.getAndIncrement();
        Map<String, Object> waiting = Map.of(
            "id", id, "userId", userId,
            "restaurantId", restaurantId,
            "waitingNumber", id.intValue(), "status", "WAITING"
        );
        store.put(id, waiting);
        log.info("웨이팅 등록 완료 - id: {}, waitingNumber: {}", id, id.intValue());

        // ★ 알림 서비스 호출 — Trace ID가 HTTP 헤더로 자동 전파됨
        try {
            webClient.post()
                .uri("/api/notification/waiting")
                .bodyValue(Map.of("userId", userId,
                    "message", "웨이팅 " + id.intValue() + "번 등록"))
                .retrieve().bodyToMono(String.class).block();
            log.info("알림 전송 완료");
        } catch (Exception e) {
            log.warn("알림 전송 실패 (무시) - error: {}", e.getMessage());
        }

        return waiting;
    }
}
```

### 5.7 WaitingController

```java
package com.example.waiting.controller;

import com.example.waiting.service.WaitingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/waiting")
public class WaitingController {
    private static final Logger log = LoggerFactory.getLogger(WaitingController.class);
    private final WaitingService waitingService;

    public WaitingController(WaitingService waitingService) {
        this.waitingService = waitingService;
    }

    @PostMapping
    public ResponseEntity<?> register(@RequestBody Map<String, Long> req) {
        log.info("[API] POST /api/waiting - userId: {}, restaurantId: {}",
                 req.get("userId"), req.get("restaurantId"));
        return ResponseEntity.ok(
            waitingService.register(req.get("userId"), req.get("restaurantId")));
    }
}
```

### 5.8 실행 및 Jaeger UI 확인

```bash
# 1. Jaeger 시작
cd docker && docker compose up -d

# 2. Spring Boot 앱 시작
cd waiting-service && ./gradlew bootRun

# 3. 테스트 요청
curl -X POST http://localhost:8080/api/waiting \
  -H "Content-Type: application/json" \
  -d '{"userId": 1, "restaurantId": 100}'

# 4. Jaeger UI 확인: http://localhost:16686
#    → Service: "waiting-service" 선택 → Find Traces
#    → 트레이스 클릭 → Span 타임라인 확인
```

---

## 6장. [실습] 마이크로서비스 간 Trace ID 전파 실습

### 6.1 notification-service 생성

```yaml
# notification-service/src/main/resources/application.yml
server:
  port: 8081
spring:
  application:
    name: notification-service
management:
  tracing:
    sampling:
      probability: 1.0
  otlp:
    tracing:
      endpoint: http://localhost:4317
logging:
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [traceId=%X{traceId} spanId=%X{spanId}] %-5level %logger{36} - %msg%n"
```

```java
// NotificationController.java
@RestController
@RequestMapping("/api/notification")
public class NotificationController {
    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);

    @PostMapping("/waiting")
    public ResponseEntity<?> send(@RequestBody Map<String, Object> req) {
        // ★ 이 로그에 waiting-service와 동일한 traceId가 자동으로 찍힘!
        log.info("[알림] 웨이팅 알림 수신 - userId: {}, message: {}",
                 req.get("userId"), req.get("message"));
        try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        log.info("[알림] SMS 발송 완료 - userId: {}", req.get("userId"));
        return ResponseEntity.ok(Map.of("status", "sent"));
    }
}
```

### 6.2 Jaeger UI에서 확인할 것

```
http://localhost:16686 → Service: waiting-service → Find Traces

트레이스 클릭 시 Span 타임라인:
  waiting-service: POST /api/waiting          ────────────────────────
    ├── waiting-service: register-waiting        ──────────────────
    │     └── waiting-service: HTTP POST            ────────────
    │           └── notification-service: POST /api/notification/waiting  ──────
    └── 완료

→ 두 서비스가 하나의 Trace로 연결됨!
→ 각 Span의 소요 시간으로 병목 즉시 파악 가능
```

### 6.3 Context Propagation 내부 동작

```
[waiting-service]
WebClient.post() 호출 시:
  → Spring Boot 자동 설정이 WebClient에 트레이싱 필터 주입
  → HTTP 요청 헤더에 자동 추가:
      traceparent: 00-abc123def456-span001-01

[notification-service]  
HTTP 요청 수신 시:
  → 서블릿 필터가 traceparent 헤더를 자동 파싱
  → MDC에 traceId, spanId 자동 설정
  → 이후 모든 로그에 동일한 traceId 출력

★ 개발자가 할 일: 아무것도 없음!
  의존성만 추가하면 WebClient, RestTemplate, Feign에서 자동 동작
```

---

## 7장. Spring Cloud Sleuth에서 Micrometer Tracing으로의 전환

### 7.1 왜 Sleuth는 사라졌는가?

```
Spring Cloud Sleuth (2015~2022): Spring Boot 2.x 시대의 트레이싱 표준
  → Zipkin/Brave 기반, spring-cloud-starter-sleuth 의존성

Spring Boot 3.0 (2022.11)과 함께 Sleuth 지원 중단
이유:
  1. Sleuth가 Brave(Zipkin)에 강하게 결합
  2. OpenTelemetry가 업계 표준으로 자리잡음
  3. Micrometer 팀이 더 범용적인 트레이싱 추상화 개발

전환 요약:
  기존: spring-cloud-starter-sleuth + spring-cloud-sleuth-zipkin
  현재: micrometer-tracing-bridge-otel + opentelemetry-exporter-otlp
```

### 7.2 마이그레이션 체크리스트

```
1. 의존성 교체
   제거: spring-cloud-starter-sleuth
   추가: micrometer-tracing-bridge-otel + opentelemetry-exporter-otlp

2. 설정 변경
   제거: spring.sleuth.*
   추가: management.tracing.* , management.otlp.*

3. MDC 키: %X{traceId} %X{spanId} ← 동일! 변경 불필요

4. @NewSpan → @Observed 변경
   Sleuth:    @NewSpan("operation-name")
   Micrometer: @Observed(name = "operation.name")
```

---

## 8장. [실습] Micrometer Tracing + OpenTelemetry 설정

### 8.1 수동 Span 생성 (@Observed 대신 직접 제어)

```java
package com.example.waiting.service;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private final ObservationRegistry observationRegistry;

    public PaymentService(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    public void processPayment(Long orderId, int amount) {
        Observation observation = Observation.createNotStarted(
                "payment.process", observationRegistry);

        // 커스텀 태그 (Jaeger UI에서 검색 가능)
        observation.lowCardinalityKeyValue("payment.method", "CARD");
        observation.highCardinalityKeyValue("order.id", orderId.toString());

        observation.observe(() -> {
            log.info("결제 처리 시작 - orderId: {}, amount: {}원", orderId, amount);
            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            log.info("결제 처리 완료 - orderId: {}", orderId);
        });
    }
}
```

### 8.2 lowCardinalityKeyValue vs highCardinalityKeyValue

```
lowCardinalityKeyValue (저 카디널리티):
  → 값의 종류가 적은 태그 (예: "CARD", "CASH", "TRANSFER")
  → 메트릭의 차원(dimension)으로도 사용됨

highCardinalityKeyValue (고 카디널리티):
  → 값의 종류가 많은 태그 (예: orderId, userId)
  → 트레이스에만 기록됨, 메트릭에는 포함 안 됨

★ 왜 구분하는가?
  고 카디널리티 값을 메트릭에 넣으면 → 메트릭 폭발 (cardinality explosion)
  user.id를 Prometheus 라벨로 넣으면 100만 유저 = 100만 시계열
  → Prometheus가 OOM으로 죽음
```

---

## 9장. [실습] 로그에 Trace ID 자동 삽입하기

### 9.1 ELK 연동 시 JSON 로그에 Trace ID 포함

```xml
<!-- logback-spring.xml -->
<appender name="LOGSTASH" class="net.logstash.logback.appender.LogstashTcpSocketAppender">
    <destination>localhost:5044</destination>
    <encoder class="net.logstash.logback.encoder.LogstashEncoder">
        <customFields>{"service":"waiting-service"}</customFields>
        <includeMdcKeyName>traceId</includeMdcKeyName>
        <includeMdcKeyName>spanId</includeMdcKeyName>
    </encoder>
</appender>
```

### 9.2 Kibana에서 Trace ID로 검색

```
KQL: traceId: "abc123def456"
→ 이 요청과 관련된 모든 서비스의 로그가 한 번에 검색됨
```

---

## 10장. 분산 트레이싱 실전 패턴과 안티패턴

### 10.1 운영 환경 샘플링 전략

```
소규모 (일 10만 요청 이하): 1.0 (100%)
중규모 (일 100만 요청):     0.1 (10%)
대규모 (일 1000만 요청):    0.01~0.05 (1~5%)
초대규모 (일 1억 요청+):    0.001 (0.1%) + Tail-based sampling
```

### 10.2 패턴: Trace ID를 API 응답 헤더에 포함

```java
@RestControllerAdvice
public class TraceIdResponseAdvice implements ResponseBodyAdvice<Object> {
    private final io.micrometer.tracing.Tracer tracer;

    public TraceIdResponseAdvice(io.micrometer.tracing.Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public boolean supports(MethodParameter returnType,
                           Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request, ServerHttpResponse response) {
        if (tracer.currentSpan() != null) {
            response.getHeaders().add("X-Trace-Id",
                tracer.currentSpan().context().traceId());
        }
        return body;
    }
}
```

### 10.3 안티패턴 모음

```
❌ 운영 환경에서 100% 트레이싱 → 스토리지/네트워크 비용 폭발
❌ Span에 비밀번호 태깅 → 보안 위험
❌ 모든 메서드에 @Observed → Trace가 수백 개 Span으로 가득
❌ Trace ID를 로그에 안 남기기 → Jaeger에서 찾아도 상세 로그 볼 수 없음

✅ 서비스 경계, DB 호출, 외부 API 호출에만 Span 생성
✅ Trace ID를 응답 헤더와 에러 응답에 포함
✅ 에러 발생 시 Span에 에러 이벤트 기록
```

---

# PART 2. 로그 파이프라인 고도화

---

## 11장. 왜 Logstash만으로는 부족한가?

```
기본 핸드북 아키텍처:
  [Spring Boot] ──TCP──→ [Logstash] ──→ [Elasticsearch]

문제점:
1. Logstash가 죽으면 → 로그 유실
2. Logstash가 느려지면 → 앱 스레드가 블로킹 → 앱도 느려짐
3. 서버 50대가 모두 Logstash에 직접 TCP 연결 → 리소스 부담
4. Logstash 자체가 무거움 (JVM, 최소 256MB 메모리)

해결:
  [App] → 로그 파일 → [Filebeat] → [Kafka] → [Logstash] → [ES]
  Filebeat: 경량 수집기 (15MB 메모리)
  Kafka:    버퍼 (유실 방지)
```

---

## 12장. Filebeat — 경량 로그 수집기의 모든 것

### 12.1 Filebeat vs Logstash

| 항목 | Filebeat | Logstash |
|------|----------|----------|
| 언어 | Go | Java (JVM) |
| 메모리 | 15~30MB | 256MB~1GB |
| 역할 | 로그 수집 + 간단한 가공 | 복잡한 파싱/변환/가공 |
| 설치 위치 | 각 서버마다 (Agent) | 중앙 (1~2대) |
| grok 파싱 | ❌ | ✅ |

### 12.2 Filebeat의 동작 원리

```
┌──────────────────────────────────────────┐
│              Filebeat 내부                 │
│                                           │
│  Harvester (파일을 한 줄씩 읽음)            │
│       ↓                                   │
│  Spooler (모아서 전달)                     │
│       ↓                                   │
│  Output (Kafka/Logstash/ES로 전송)         │
│                                           │
│  Registry: 각 파일을 어디까지 읽었는지 기록  │
│  → 재시작해도 마지막 위치부터 이어서 읽음    │
└──────────────────────────────────────────┘
```

---

## 13장. [실습] Filebeat 설치 및 설정

### 13.1 docker-compose.yml

```yaml
  filebeat:
    image: docker.elastic.co/beats/filebeat:8.12.0
    container_name: filebeat
    user: root
    volumes:
      - ./filebeat/filebeat.yml:/usr/share/filebeat/filebeat.yml:ro
      - ../logs:/var/log/app:ro
      - filebeat-data:/usr/share/filebeat/data
    depends_on:
      - elasticsearch
    networks:
      - monitoring-network

volumes:
  filebeat-data:
    driver: local
```

### 13.2 filebeat.yml — 상세 주석 포함

```yaml
# docker/filebeat/filebeat.yml

# ═══ INPUT: 어디에서 로그를 읽을 것인가? ═══
filebeat.inputs:
  - type: filestream         # filestream: 8.x 권장 (log 타입의 후속)
    id: app-logs             # 고유 ID (필수)
    enabled: true
    paths:
      - /var/log/app/*.log

    # 24시간 이상 된 파일은 무시 (재시작 시 오래된 파일 재읽기 방지)
    ignore_older: 24h

    # 5분간 새 로그 없으면 파일 핸들 닫기 (fd 부족 방지)
    close_inactive: 5m

    # 필드 추가
    fields:
      service: waiting-service
      env: local
    fields_under_root: true

    # 멀티라인 (스택트레이스 합치기)
    parsers:
      - multiline:
          type: pattern
          pattern: '^\d{4}-\d{2}-\d{2}'   # 날짜로 시작하면 새 로그
          negate: true
          match: after
          max_lines: 100
          timeout: 5s

# ═══ PROCESSORS: 간단한 가공 ═══
processors:
  - add_host_metadata:
      when.not.contains.tags: forwarded
  - drop_fields:
      fields: ["agent.ephemeral_id", "agent.id", "ecs.version"]
      ignore_missing: true

# ═══ OUTPUT ═══
# 방법 1: ES 직접 전송
output.elasticsearch:
  hosts: ["http://elasticsearch:9200"]
  index: "filebeat-waiting-%{+yyyy.MM.dd}"

# 방법 2: Kafka (대용량)
# output.kafka:
#   hosts: ["kafka:29092"]
#   topic: "app-logs"
#   compression: gzip
#   required_acks: 1

logging.level: info
setup.ilm.enabled: false
setup.template.enabled: false
```

---

## 14장. [실습] Filebeat 멀티라인 로그 처리

### 14.1 문제

```log
2024-01-15 14:30:01 ERROR c.e.w.s.WaitingService - 웨이팅 등록 실패
java.lang.NullPointerException: userId는 null일 수 없습니다
    at com.example.waiting.service.WaitingService.register(WaitingService.java:42)
    at com.example.waiting.controller.WaitingController.register(WaitingController.java:28)
```

Filebeat 기본: 한 줄 = 하나의 이벤트 → 스택트레이스가 4개의 별도 이벤트로 분리됨

### 14.2 해결

```yaml
parsers:
  - multiline:
      type: pattern
      pattern: '^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}'
      negate: true     # 패턴과 불일치하는 줄을
      match: after     # 이전 로그 뒤에 합침

# 결과: 줄1~4가 하나의 이벤트로 합쳐짐 ✅
```

### 14.3 JSON 로그라면 멀티라인 불필요

```yaml
# JSON 로그 파싱 설정
parsers:
  - ndjson:
      target: ""
      add_error_key: true
      message_key: message
```

---

## 15장. Kafka를 로그 파이프라인에 도입하는 이유

```
Kafka 없이:
  Logstash 재시작 → 그 동안 로그 유실
  ES 느려짐 → Logstash 큐 가득 → Filebeat 전송 실패
  트래픽 폭증 → Logstash 감당 불가 → 유실

Kafka 도입 후:
  Logstash 재시작 → Kafka에 로그 보관 → 재시작 후 이어서 소비 → 유실 0
  ES 느려짐 → Kafka에 쌓임 → ES 복구 후 처리
  트래픽 폭증 → Kafka가 초당 수백만 건 처리 → 버퍼로 흡수
```

### 핵심 개념

```
Topic     : 로그 카테고리 (예: app-logs)
Partition : 병렬 처리 단위 (파티션 수 = 최대 병렬 소비자 수)
Producer  : 로그를 보내는 쪽 (Filebeat)
Consumer  : 로그를 가져가는 쪽 (Logstash)
Retention : 보관 기간 (기본 7일)
```

---

## 16장. [실습] Kafka 클러스터 구성

### 16.1 docker-compose.yml

```yaml
  zookeeper:
    image: confluentinc/cp-zookeeper:7.6.0
    container_name: zookeeper
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
    ports:
      - "2181:2181"
    networks:
      - monitoring-network

  kafka:
    image: confluentinc/cp-kafka:7.6.0
    container_name: kafka
    depends_on:
      - zookeeper
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:29092,PLAINTEXT_HOST://localhost:9092
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_LOG_RETENTION_HOURS: 72          # 3일 보관
      KAFKA_NUM_PARTITIONS: 3               # 기본 파티션 수
    ports:
      - "9092:9092"
    volumes:
      - kafka-data:/var/lib/kafka/data
    networks:
      - monitoring-network

  kafka-ui:
    image: provectuslabs/kafka-ui:v0.7.1
    container_name: kafka-ui
    environment:
      KAFKA_CLUSTERS_0_NAME: local
      KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: kafka:29092
    ports:
      - "9093:8080"
    depends_on:
      - kafka
    networks:
      - monitoring-network
```

---

## 17장. [실습] Filebeat → Kafka → Logstash → ES 파이프라인

### 17.1 Filebeat → Kafka 설정

```yaml
# filebeat.yml — output을 Kafka로
output.kafka:
  hosts: ["kafka:29092"]
  topic: "app-logs"
  partition.round_robin:
    reachable_only: true
  compression: gzip
  required_acks: 1
```

### 17.2 Logstash ← Kafka 설정

```ruby
# logstash.conf
input {
  kafka {
    bootstrap_servers => "kafka:29092"
    topics => ["app-logs"]
    group_id => "logstash-consumer-group"
    codec => json
    consumer_threads => 3          # 파티션 수와 동일하게
    auto_offset_reset => "earliest"
  }
}

filter {
  if [message] {
    grok {
      match => {
        "message" => "%{TIMESTAMP_ISO8601:timestamp} \[%{DATA:thread}\] \[traceId=%{DATA:traceId} spanId=%{DATA:spanId}\] %{LOGLEVEL:level}\s+%{DATA:logger} - %{GREEDYDATA:log_message}"
      }
    }
  }

  if [timestamp] {
    date {
      match => ["timestamp", "yyyy-MM-dd HH:mm:ss.SSS"]
      target => "@timestamp"
      timezone => "Asia/Seoul"
    }
    mutate { remove_field => ["timestamp"] }
  }

  mutate { remove_field => ["@version", "[event]", "[agent]"] }
}

output {
  elasticsearch {
    hosts => ["http://elasticsearch:9200"]
    index => "app-logs-%{+YYYY.MM.dd}"
  }
}
```

---

## 18장. Elasticsearch ILM (인덱스 생명주기 관리)

```
문제: 로그 인덱스가 무한 증가 → 디스크 폭발

해결: ILM — 시간에 따라 자동 관리

┌──────┐      ┌──────┐      ┌──────┐      ┌────────┐
│ Hot  │─7일─→│ Warm │─30일→│ Cold │─90일→│ Delete │
│ (SSD)│      │ (HDD)│      │ (S3) │      │ (삭제) │
└──────┘      └──────┘      └──────┘      └────────┘
```

---

## 19장. [실습] ILM 정책 설정

```bash
curl -X PUT "http://localhost:9200/_ilm/policy/app-logs-policy" \
  -H "Content-Type: application/json" -d '{
  "policy": {
    "phases": {
      "hot": {
        "min_age": "0ms",
        "actions": {
          "rollover": { "max_age": "1d", "max_primary_shard_size": "50gb" },
          "set_priority": { "priority": 100 }
        }
      },
      "warm": {
        "min_age": "7d",
        "actions": {
          "shrink": { "number_of_shards": 1 },
          "forcemerge": { "max_num_segments": 1 },
          "set_priority": { "priority": 50 }
        }
      },
      "cold": {
        "min_age": "30d",
        "actions": { "set_priority": { "priority": 0 } }
      },
      "delete": {
        "min_age": "90d",
        "actions": { "delete": {} }
      }
    }
  }
}'
```

---

## 20장. Elasticsearch 클러스터 운영 기초

### 노드 역할

```
Master Node: 클러스터 상태 관리 (최소 3대, 홀수)
Data Node:   실제 데이터 저장 (Hot/Warm/Cold 구분)
Coordinating Node: 요청 분배 + 결과 취합 (로드밸런서)
```

### 샤드 전략

```
Primary Shard: 원본 (한 번 정하면 변경 불가)
Replica Shard: 복제본 (언제든 변경 가능)

규칙: 샤드 하나당 20~50GB
  하루 10GB 로그 → Primary Shard 1개
  하루 200GB 로그 → Primary Shard 4~10개
```

---

## 21장. [실습] ES 클러스터 3노드 구성

```yaml
# docker-compose-es-cluster.yml
services:
  es-node1:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.12.0
    environment:
      - node.name=es-node1
      - cluster.name=log-cluster
      - discovery.seed_hosts=es-node2,es-node3
      - cluster.initial_master_nodes=es-node1,es-node2,es-node3
      - node.roles=master,data_hot
      - xpack.security.enabled=false
      - "ES_JAVA_OPTS=-Xms512m -Xmx512m"
    ports: ["9200:9200"]

  es-node2:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.12.0
    environment:
      - node.name=es-node2
      - cluster.name=log-cluster
      - discovery.seed_hosts=es-node1,es-node3
      - cluster.initial_master_nodes=es-node1,es-node2,es-node3
      - node.roles=master,data_warm
      - xpack.security.enabled=false
      - "ES_JAVA_OPTS=-Xms512m -Xmx512m"

  es-node3:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.12.0
    environment:
      - node.name=es-node3
      - cluster.name=log-cluster
      - discovery.seed_hosts=es-node1,es-node2
      - cluster.initial_master_nodes=es-node1,es-node2,es-node3
      - node.roles=master,data_cold
      - xpack.security.enabled=false
      - "ES_JAVA_OPTS=-Xms256m -Xmx256m"
```

---

# PART 3. 통합 Observability — Grafana Stack

---

## 22장. 왜 Grafana Stack인가?

### ELK의 비용 문제

```
Elasticsearch: 모든 로그를 역인덱싱 → 원본의 1.5~2배 스토리지
Loki:          라벨만 인덱싱 → 원본 크기 (압축하면 더 작음)

일 100GB 로그 기준 월간 비용 (AWS):
  ELK: ~$1,500/월 (ES SSD + 3노드)
  LGTM: ~$300/월 (S3 + Loki 1~2노드)
  → 약 80% 절감
```

### Loki의 인덱싱 철학

```
Elasticsearch: "모든 단어를 인덱싱 → 어떤 단어로든 즉시 검색"
Loki:          "라벨로 범위를 좁히고, 그 안에서 grep"

대부분의 로그 검색은 "특정 서비스의 특정 레벨"로 시작하므로 Loki로 충분
```

---

## 23장. OpenTelemetry Collector — 통합 수집의 중심

```
기존: [App] → Logstash(로그) + Prometheus(메트릭) + Jaeger(트레이스)
OTel: [App] ── OTLP ──→ [OTel Collector] ──→ Loki + Mimir + Tempo
→ 하나의 파이프라인으로 통합
```

### 3단계 파이프라인

```
Receivers (수신): otlp, prometheus, filelog, kafka
     ↓
Processors (가공): batch, memory_limiter, attributes, filter
     ↓
Exporters (전달): loki, prometheusremotewrite, otlp/tempo
```

---

## 24장. [실습] OpenTelemetry Collector 설치 및 설정

### 24.1 docker-compose.yml

```yaml
  otel-collector:
    image: otel/opentelemetry-collector-contrib:0.94.0
    container_name: otel-collector
    command: ["--config=/etc/otel-collector/config.yml"]
    volumes:
      - ./otel-collector/config.yml:/etc/otel-collector/config.yml:ro
    ports:
      - "4317:4317"   # OTLP gRPC
      - "4318:4318"   # OTLP HTTP
      - "8888:8888"   # Collector 자체 메트릭
    networks:
      - monitoring-network
```

### 24.2 OTel Collector config.yml (★ 핵심)

```yaml
# docker/otel-collector/config.yml

receivers:
  otlp:
    protocols:
      grpc:
        endpoint: 0.0.0.0:4317
      http:
        endpoint: 0.0.0.0:4318

  prometheus:
    config:
      scrape_configs:
        - job_name: 'spring-boot'
          scrape_interval: 15s
          metrics_path: '/actuator/prometheus'
          static_configs:
            - targets: ['host.docker.internal:8080']

processors:
  batch:
    timeout: 5s              # 5초마다 또는
    send_batch_size: 1000    # 1000개 모이면 전송

  memory_limiter:
    check_interval: 1s
    limit_mib: 512           # 컨테이너 메모리의 ~80%
    spike_limit_mib: 128

  resource:
    attributes:
      - key: environment
        value: local
        action: upsert

exporters:
  debug:
    verbosity: basic

  loki:
    endpoint: http://loki:3100/loki/api/v1/push

  otlp/tempo:
    endpoint: http://tempo:4317
    tls:
      insecure: true

  prometheusremotewrite:
    endpoint: http://mimir:9009/api/v1/push
    tls:
      insecure: true

service:
  pipelines:
    traces:
      receivers: [otlp]
      processors: [memory_limiter, batch]
      exporters: [otlp/tempo, debug]

    metrics:
      receivers: [otlp, prometheus]
      processors: [memory_limiter, batch]
      exporters: [prometheusremotewrite]

    logs:
      receivers: [otlp]
      processors: [memory_limiter, batch, resource]
      exporters: [loki]

  telemetry:
    logs:
      level: info
    metrics:
      address: 0.0.0.0:8888
```

---

## 25장. Grafana Loki — 로그 전용 저장소

### 핵심 개념

```
Log Stream = 동일한 라벨 조합의 로그 흐름
  {service="waiting", level="INFO", env="prod"}

좋은 라벨 (저 카디널리티): service, level, env, namespace
나쁜 라벨 (절대 금지):     userId, requestId, traceId, timestamp
→ 고유 값 수백만 개 → 인덱스 폭발 → Loki 성능 저하
```

---

## 26장. [실습] Loki 설치 및 Spring Boot 연동

### 26.1 docker-compose.yml

```yaml
  loki:
    image: grafana/loki:2.9.4
    container_name: loki
    command: -config.file=/etc/loki/loki-config.yml
    volumes:
      - ./loki/loki-config.yml:/etc/loki/loki-config.yml:ro
      - loki-data:/loki
    ports:
      - "3100:3100"
    networks:
      - monitoring-network
```

### 26.2 Loki 설정 파일 (★ 상세 주석)

```yaml
# docker/loki/loki-config.yml
auth_enabled: false

server:
  http_listen_port: 3100

ingester:
  wal:
    enabled: true
    dir: /loki/wal
  lifecycler:
    ring:
      kvstore:
        store: inmemory
      replication_factor: 1
  chunk_idle_period: 1h       # 1시간 동안 새 로그 없으면 플러시
  max_chunk_age: 2h           # 최대 청크 수명
  chunk_target_size: 1048576  # 목표 1MB

schema_config:
  configs:
    - from: 2024-01-01
      store: tsdb              # 최신 인덱스 엔진
      object_store: filesystem # 운영: s3, gcs
      schema: v13
      index:
        prefix: index_
        period: 24h

storage_config:
  tsdb_shipper:
    active_index_directory: /loki/tsdb-index
    cache_location: /loki/tsdb-cache
  filesystem:
    directory: /loki/chunks

compactor:
  working_directory: /loki/compactor
  retention_enabled: true

limits_config:
  retention_period: 744h         # 31일 보관
  ingestion_rate_mb: 10          # 초당 최대 10MB
  per_stream_rate_limit: 3MB
  max_entries_limit_per_query: 5000

analytics:
  reporting_enabled: false
```

### 26.3 Spring Boot → Loki 직접 전송 (Loki4j)

```groovy
// build.gradle
implementation 'com.github.loki4j:loki-logback-appender:1.5.1'
```

```xml
<!-- logback-spring.xml -->
<appender name="LOKI" class="com.github.loki4j.logback.Loki4jAppender">
    <http>
        <url>http://localhost:3100/loki/api/v1/push</url>
    </http>
    <format>
        <label>
            <!-- ★ 저 카디널리티 값만! userId, requestId 절대 안 됨 -->
            <pattern>service=waiting-service,level=%level,env=local</pattern>
        </label>
        <message>
            <pattern>{"timestamp":"%d{yyyy-MM-dd HH:mm:ss.SSS}","thread":"%thread","traceId":"%X{traceId:-}","spanId":"%X{spanId:-}","logger":"%logger{36}","message":"%message","stackTrace":"%ex"}</pattern>
        </message>
        <sortByTime>true</sortByTime>
    </format>
</appender>
```

---

## 27장. [실습] Promtail 설정과 로그 수집

```yaml
# docker/promtail/promtail-config.yml
server:
  http_listen_port: 9080

clients:
  - url: http://loki:3100/loki/api/v1/push

scrape_configs:
  - job_name: app-logs
    static_configs:
      - targets: [localhost]
        labels:
          service: waiting-service
          env: local
          __path__: /var/log/app/*.log

    pipeline_stages:
      - multiline:
          firstline: '^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}'
          max_wait_time: 3s

      - regex:
          expression: '^(?P<timestamp>\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}) \[(?P<thread>[^\]]+)\] \[traceId=(?P<traceId>[^\s]*) spanId=(?P<spanId>[^\]]*)\] (?P<level>\w+)\s+(?P<logger>\S+) - (?P<message>.*)$'

      - labels:
          level:      # level을 Loki 라벨로

      - timestamp:
          source: timestamp
          format: '2006-01-02 15:04:05.000'  # Go 시간 포맷
          location: Asia/Seoul

      - structured_metadata:
          traceId:    # traceId는 라벨이 아닌 메타데이터로
          spanId:

      - output:
          source: message
```

---

## 28장. [실습] Loki LogQL 쿼리 마스터하기

```logql
# ── 기본 ──
{service="waiting-service"}                           # 전체 로그
{service="waiting-service", level="ERROR"}            # 에러만
{service="waiting-service"} |= "웨이팅 등록"           # 텍스트 검색
{service="waiting-service"} != "health"               # 제외
{service="waiting-service"} |~ "userId: (1001|1002)"  # 정규식

# ── JSON 파싱 ──
{service="waiting-service"} | json
{service="waiting-service"} | json | message =~ ".*등록.*"

# ── 메트릭 쿼리 (로그에서 지표 추출) ──
rate({service="waiting-service"}[5m])                 # 초당 로그 발생률
rate({service="waiting-service", level="ERROR"}[5m])  # 초당 에러 발생률
count_over_time({level="ERROR"}[1h])                  # 1시간 에러 총 수

# ── 실전 ──
# CS 대응: 특정 사용자 추적
{service="waiting-service"} |= "userId: 1001" | json

# traceId로 전체 요청 흐름 추적
{service=~".*"} | traceId="abc123def456"
```

---

## 29장. Grafana Tempo — 분산 트레이싱 저장소

```
Jaeger: Cassandra/ES 저장 → 비용 높음, 별도 스토리지 운영
Tempo:  Object Storage(S3) 저장 → 비용 매우 낮음, 인덱싱 없음

핵심: Trace ID로만 조회
  Grafana에서 Loki 로그의 traceId 클릭 → Tempo로 자동 이동
```

---

## 30장. [실습] Tempo 설치 및 Trace 연동

### docker-compose.yml

```yaml
  tempo:
    image: grafana/tempo:2.3.1
    container_name: tempo
    command: ["-config.file=/etc/tempo/tempo-config.yml"]
    volumes:
      - ./tempo/tempo-config.yml:/etc/tempo/tempo-config.yml:ro
      - tempo-data:/tmp/tempo
    ports:
      - "3200:3200"
      - "4317"
```

### tempo-config.yml

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

ingester:
  max_block_duration: 5m

compactor:
  compaction:
    block_retention: 744h      # 31일 보관

storage:
  trace:
    backend: local             # 운영: s3, gcs
    local:
      path: /tmp/tempo/blocks
    wal:
      path: /tmp/tempo/wal

metrics_generator:
  registry:
    external_labels:
      source: tempo
  storage:
    path: /tmp/tempo/generator/wal
    remote_write:
      - url: http://mimir:9009/api/v1/push
        send_exemplars: true

overrides:
  defaults:
    metrics_generator:
      processors: [service-graphs, span-metrics]
```

---

## 31장. Grafana Mimir — 메트릭 장기 저장소

```
Prometheus 한계:
  - 로컬 디스크만 → 장기 보관 어려움
  - 단일 인스턴스 → HA 복잡

Mimir:
  - Object Storage(S3)에 저장 → 무제한 보관
  - Prometheus Remote Write로 연동
  - 수평 확장 가능
```

---

## 32장. [실습] Mimir 설치 및 Prometheus 원격 저장

### docker-compose.yml

```yaml
  mimir:
    image: grafana/mimir:2.11.0
    container_name: mimir
    command: ["-config.file=/etc/mimir/mimir-config.yml"]
    volumes:
      - ./mimir/mimir-config.yml:/etc/mimir/mimir-config.yml:ro
      - mimir-data:/data
    ports:
      - "9009:9009"
```

### mimir-config.yml

```yaml
target: all   # 단일 인스턴스 모드

server:
  http_listen_port: 9009

multitenancy_enabled: false

ingester:
  ring:
    instance_addr: 127.0.0.1
    kvstore:
      store: memberlist
    replication_factor: 1

blocks_storage:
  backend: filesystem          # 운영: s3, gcs
  filesystem:
    dir: /data/blocks
  tsdb:
    dir: /data/tsdb
  bucket_store:
    sync_dir: /data/tsdb-sync

compactor:
  data_dir: /data/compactor
  sharding_ring:
    kvstore:
      store: memberlist

limits:
  compactor_blocks_retention_period: 8760h  # 1년 보관
  max_global_series_per_user: 1000000

memberlist:
  join_members: []

activity_tracker:
  filepath: /data/activity.log
```

### Prometheus → Mimir Remote Write

```yaml
# prometheus.yml
remote_write:
  - url: http://mimir:9009/api/v1/push
    queue_config:
      max_samples_per_send: 1000
```

---

## 33장. [실습] Grafana에서 로그-메트릭-트레이스 통합 조회

### 33.1 데이터소스 자동 프로비저닝

```yaml
# docker/grafana/provisioning/datasources/datasources.yml
apiVersion: 1
datasources:
  - name: Loki
    type: loki
    url: http://loki:3100
    jsonData:
      derivedFields:
        - datasourceUid: tempo
          matcherRegex: '"traceId":"(\w+)"'
          name: TraceID
          url: '$${__value.raw}'

  - name: Tempo
    type: tempo
    uid: tempo
    url: http://tempo:3200
    jsonData:
      tracesToLogsV2:
        datasourceUid: loki
        filterByTraceID: true
      nodeGraph:
        enabled: true

  - name: Mimir
    type: prometheus
    url: http://mimir:9009/prometheus
    isDefault: true
    jsonData:
      exemplarTraceIdDestinations:
        - datasourceUid: tempo
          name: traceId
```

### 33.2 통합 조회 시나리오

```
1단계: Grafana 대시보드에서 에러율 급증 확인 (Mimir 메트릭)
2단계: Exemplar 링크로 Tempo에서 에러 트레이스 확인
3단계: Trace → Logs 링크로 Loki에서 상세 로그 확인

메트릭(전체 조감도) → 트레이스(요청 흐름) → 로그(상세 기록)
한 화면에서 끊김 없이 전환 = Observability의 완성
```

---

## 34장. 최종 프로덕션 아키텍처와 비용 최적화

### 전체 LGTM 스택 docker-compose.yml

```yaml
version: '3.8'
services:
  loki:
    image: grafana/loki:2.9.4
    command: -config.file=/etc/loki/loki-config.yml
    volumes: [./loki/loki-config.yml:/etc/loki/loki-config.yml:ro, loki-data:/loki]
    ports: ["3100:3100"]
    networks: [observability]

  tempo:
    image: grafana/tempo:2.3.1
    command: ["-config.file=/etc/tempo/tempo-config.yml"]
    volumes: [./tempo/tempo-config.yml:/etc/tempo/tempo-config.yml:ro, tempo-data:/tmp/tempo]
    ports: ["3200:3200", "4317:4317", "4318:4318"]
    networks: [observability]

  mimir:
    image: grafana/mimir:2.11.0
    command: ["-config.file=/etc/mimir/mimir-config.yml"]
    volumes: [./mimir/mimir-config.yml:/etc/mimir/mimir-config.yml:ro, mimir-data:/data]
    ports: ["9009:9009"]
    networks: [observability]

  otel-collector:
    image: otel/opentelemetry-collector-contrib:0.94.0
    command: ["--config=/etc/otel-collector/config.yml"]
    volumes: [./otel-collector/config.yml:/etc/otel-collector/config.yml:ro]
    ports: ["4317:4317", "4318:4318"]
    depends_on: [loki, tempo, mimir]
    networks: [observability]

  prometheus:
    image: prom/prometheus:v2.49.0
    volumes: [./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro]
    ports: ["9090:9090"]
    extra_hosts: ["host.docker.internal:host-gateway"]
    networks: [observability]

  promtail:
    image: grafana/promtail:2.9.4
    command: -config.file=/etc/promtail/promtail-config.yml
    volumes: [./promtail/promtail-config.yml:/etc/promtail/promtail-config.yml:ro, ../logs:/var/log/app:ro]
    depends_on: [loki]
    networks: [observability]

  grafana:
    image: grafana/grafana:10.3.0
    environment: [GF_SECURITY_ADMIN_USER=admin, GF_SECURITY_ADMIN_PASSWORD=admin]
    ports: ["3000:3000"]
    volumes: [grafana-data:/var/lib/grafana, ./grafana/provisioning:/etc/grafana/provisioning:ro]
    depends_on: [loki, tempo, mimir, prometheus]
    networks: [observability]

volumes:
  loki-data:
  tempo-data:
  mimir-data:
  grafana-data:

networks:
  observability:
    driver: bridge
```

### 실행

```bash
docker compose up -d

# 접속 URL
# Grafana:    http://localhost:3000 (admin/admin)
# Loki:       http://localhost:3100
# Tempo:      http://localhost:3200
# Mimir:      http://localhost:9009
# Prometheus: http://localhost:9090
```

---

## 35장. Observability 성숙도 모델과 앞으로의 방향

### 성숙도 5단계

```
Level 0: System.out.println ← 장애 대응 불가
Level 1: SLF4J + Logback 파일 로그 ← 기본 핸드북
Level 2: ELK 중앙 집중 로그 ← 기본 핸드북
Level 3: Prometheus + Grafana 메트릭/알림 ← 기본 핸드북
Level 4: 분산 트레이싱 + Trace-Log 연결 ← 이 핸드북 PART 1
Level 5: LGTM 통합 Observability ← 이 핸드북 PART 2, 3
```

### 기술 선택 가이드

```
서버 1~5대 + 소규모: Loki + Grafana + Prometheus (비용 ~0)
서버 5~50대 + 중규모: LGTM 스택 or ELK + Prometheus
서버 50대+ + 대규모: LGTM + Kafka 버퍼 + S3
클라우드 네이티브: Grafana Cloud or 셀프 LGTM + Helm
```

### 다음 로드맵

```
1. Kubernetes Observability (DaemonSet, Helm)
2. OTel Auto-instrumentation (Java Agent)
3. SLO/SLI 기반 모니터링 (Error Budget)
4. eBPF 기반 관측 (Grafana Beyla)
5. AIOps (이상 탐지, 자동 복구)
```

---

> **끝.**
> 기본 핸드북 + 이 심화 핸드북을 모두 마쳤다면,
> 대부분의 회사에서 Observability를 구축할 수 있는 충분한 지식을 갖추게 되었습니다.
> Level 1부터 시작해서 점진적으로 높여가세요. 🚀
