# 🔭 분산 트레이싱 완벽 가이드

### OpenTelemetry · Jaeger · Micrometer Tracing · Spring Boot 3.x

> **"서버 100대에서 발생한 장애를, 단 하나의 ID로 추적하는 기술"**
> 이 핸드북을 마치면 분산 트레이싱을 설계하고 운영할 수 있습니다.

---

# 목차

## PART 1. 분산 트레이싱을 왜 배워야 하는가?
- 1장. 모놀리스에서 마이크로서비스로 — 무엇이 달라졌는가
- 2장. 기존 로깅만으로는 절대 해결할 수 없는 문제들
- 3장. 분산 트레이싱의 탄생 — Google Dapper에서 OpenTelemetry까지
- 4장. [퀴즈] 분산 트레이싱이 필요한 상황 판단하기

## PART 2. 분산 트레이싱의 핵심 개념
- 5장. Trace, Span, Context — 세 가지만 알면 된다
- 6장. Span의 내부 구조 — 무엇이 기록되는가
- 7장. Context Propagation — 서비스 간 ID가 전달되는 원리
- 8장. W3C Trace Context 표준 — traceparent 헤더 해부
- 9장. 샘플링 전략 — 모든 요청을 추적하면 안 되는 이유
- 10장. [퀴즈] Trace와 Span 구조 이해하기

## PART 3. 기술 스택 선택
- 11장. 분산 트레이싱 기술 생태계 전체 지도
- 12장. OpenTelemetry — 왜 이것이 표준이 되었는가
- 13장. Jaeger vs Zipkin — 어떤 것을 선택해야 하는가
- 14장. Spring Cloud Sleuth는 왜 사라졌는가
- 15장. Micrometer Tracing — Spring Boot 3.x의 새로운 표준
- 16장. Sleuth → Micrometer 마이그레이션 완전 가이드

## PART 4. [실습] 분산 트레이싱 처음부터 끝까지
- 17장. 실습 프로젝트 설계 — 맛집 웨이팅 마이크로서비스
- 18장. [실습] Jaeger 설치 (Docker)
- 19장. [실습] waiting-service 생성 및 트레이싱 설정
- 20장. [실습] notification-service 생성 및 Trace 전파
- 21장. [실습] Jaeger UI에서 트레이스 분석하기
- 22장. [실습] 수동 Span 생성과 커스텀 태그
- 23장. [실습] 로그에 Trace ID 자동 삽입하기
- 24장. [실습] ELK와 연동 — Kibana에서 Trace ID로 검색
- 25장. [퀴즈] 실습 종합 문제

## PART 5. 로그를 어떻게 구성해야 하는가
- 26장. 트레이싱 시대의 로그 설계 원칙
- 27장. 로그 포맷 — Plain Text vs JSON 구조화 로그
- 28장. MDC + Trace ID + Span ID 통합 전략
- 29장. 로그 레벨 전략 — 트레이싱과 함께 쓸 때
- 30장. [실습] 최적의 로그 포맷 설정하기

## PART 6. 운영 환경 구성 가이드
- 31장. 운영 환경에서의 샘플링 전략 설계
- 32장. Jaeger 프로덕션 아키텍처 — Collector + Kafka + ES
- 33장. 운영 환경 application.yml 완전 가이드
- 34장. 성능 영향 분석 — 트레이싱이 앱에 미치는 부하
- 35장. 장애 대응 시나리오별 트레이싱 활용법
- 36장. 보안 — 트레이스에 남기면 안 되는 것들
- 37장. [퀴즈] 운영 환경 설계 문제

## PART 7. 정리와 다음 단계
- 38장. 분산 트레이싱 체크리스트
- 39장. 자주 하는 실수 TOP 10
- 40장. 다음 단계 — OpenTelemetry Collector, Grafana Tempo

---

# PART 1. 분산 트레이싱을 왜 배워야 하는가?

---

## 1장. 모놀리스에서 마이크로서비스로 — 무엇이 달라졌는가

### 1.1 모놀리스 시대의 디버깅

모놀리스 아키텍처에서는 하나의 애플리케이션 안에 모든 로직이 들어있습니다. 장애가 발생하면 한 서버의 로그 파일 하나만 보면 됩니다.

```
[모놀리스: 서버 1대]

사용자 요청 ──→ ┌──────────────────────────────────────┐
                │            Spring Boot App             │
                │                                        │
                │  Controller → Service → Repository     │
                │  (주문)       (결제)     (재고)          │
                │                                        │
                │  모든 로그가 하나의 파일에 기록됨          │
                └──────────────────────────────────────┘

디버깅:
  $ grep "ERROR" application.log
  → 끝. 한 파일에 모든 것이 있음.
```

### 1.2 마이크로서비스 시대의 디버깅

마이크로서비스로 전환하면 하나의 사용자 요청이 여러 서비스를 거칩니다. 각 서비스는 별도의 서버에서 동작하고, 각각의 로그 파일을 가집니다.

```
[마이크로서비스: 서버 N대]

사용자 요청 ──→ ┌────────────┐    ┌────────────┐    ┌────────────┐
                │ API Gateway │───→│ 주문 서비스  │───→│ 결제 서비스  │
                │ (Server A)  │    │ (Server B)  │    │ (Server C)  │
                └────────────┘    └──────┬─────┘    └────────────┘
                                         │
                                         ▼
                                  ┌────────────┐    ┌────────────┐
                                  │ 재고 서비스  │───→│ 알림 서비스  │
                                  │ (Server D)  │    │ (Server E)  │
                                  └────────────┘    └────────────┘

디버깅:
  $ ssh server-a && grep "ERROR" gateway.log      → 없음
  $ ssh server-b && grep "ERROR" order.log        → 없음
  $ ssh server-c && grep "ERROR" payment.log      → 에러 발견!
  
  하지만... 이 에러가 어느 사용자의 어느 요청 때문인지 모름.
  같은 시간에 1000명이 결제하고 있었으니까.
```

### 1.3 실제 장애 시나리오

```
상황: 사용자가 "웨이팅 등록이 안 된다"고 CS 문의

마이크로서비스 환경에서의 요청 흐름:
  1. API Gateway에서 요청 수신 ✅
  2. waiting-service에서 중복 확인 ✅
  3. waiting-service에서 DB 저장 ✅
  4. notification-service로 알림 요청 ← 여기서 타임아웃!
  5. waiting-service가 알림 실패를 에러로 처리 → 전체 롤백

문제: 
  - waiting-service 로그에는 "알림 서비스 호출 실패"만 기록
  - notification-service 로그에는 "요청 수신" 로그조차 없음
  - 네트워크 문제? notification-service가 죽어있었나? 타임아웃?
  - 로그만으로는 두 서비스 사이에서 무슨 일이 일어났는지 알 수 없음
```

### 1.4 왜 이것이 해결하기 어려운가

마이크로서비스 환경에서 디버깅이 어려운 근본적인 이유는 **"연결 고리의 부재"**입니다.

```
모놀리스:
  하나의 스레드가 처음부터 끝까지 처리
  → 스레드명으로 같은 요청의 로그를 묶을 수 있음

마이크로서비스:
  서비스 A의 스레드 exec-1이 서비스 B를 호출하면
  서비스 B에서는 새로운 스레드 exec-7이 처리
  → 스레드명이 달라짐
  → "서비스 A의 exec-1 요청"과 "서비스 B의 exec-7 요청"이
    같은 사용자 요청이라는 것을 알 방법이 없음
```

이 문제를 해결하는 것이 바로 **분산 트레이싱**입니다.

---

## 2장. 기존 로깅만으로는 절대 해결할 수 없는 문제들

### 2.1 기존 방식으로 시도해본 것들과 한계

우리는 이미 기본 핸드북에서 여러 기술을 배웠습니다. 각각이 마이크로서비스 환경에서 왜 부족한지 정리해봅시다.

```
시도 1: 타임스탬프로 로그 연결하기
─────────────────────────────────
  "같은 시간에 발생한 로그를 묶으면 되지 않나?"
  
  waiting-service  14:30:01.123  웨이팅 등록 요청
  notification     14:30:01.150  알림 수신  ← 이게 위 요청의 알림인가?
  notification     14:30:01.155  알림 수신  ← 아니면 이게?
  
  → 밀리초 단위로도 수십 개의 요청이 동시에 들어옴
  → 시간만으로는 매칭 불가능

시도 2: MDC requestId 사용하기
─────────────────────────────────
  기본 핸드북에서 배운 MdcLoggingFilter로 UUID를 부여했었죠.
  
  waiting-service  [req-abc123]  웨이팅 등록 요청
  waiting-service  [req-abc123]  DB 저장 완료
  waiting-service  [req-abc123]  알림 서비스 호출
  
  → 한 서비스 안에서는 완벽하게 동작!
  
  하지만...
  notification     [req-xyz789]  알림 수신   ← 다른 requestId!
  
  → notification-service는 자기만의 requestId를 새로 생성
  → 서비스 간에 requestId가 공유되지 않음
  → "req-abc123"과 "req-xyz789"가 같은 사용자 요청이라는 것을 모름

시도 3: ELK로 중앙 수집 후 검색하기
─────────────────────────────────
  모든 서비스의 로그를 Elasticsearch에 모았다고 합시다.
  
  Kibana에서 검색: "웨이팅 등록 실패"
  → 100개의 결과가 나옴
  → 이 중 CS 문의한 사용자의 요청은 어느 것?
  → userId로 필터링해도, 그 사용자의 요청이 다른 서비스에서
    어떻게 처리되었는지는 알 수 없음
```

### 2.2 분산 트레이싱이 해결하는 것

```
분산 트레이싱이 제공하는 것:

하나의 사용자 요청에 대해:
  ✅ 어떤 서비스를 거쳤는가? (호출 경로)
  ✅ 각 서비스에서 얼마나 걸렸는가? (소요 시간)
  ✅ 어디서 에러가 발생했는가? (장애 지점)
  ✅ 어디가 병목인가? (성능 병목)
  ✅ 모든 서비스의 로그를 하나의 ID로 검색 가능 (로그 연결)

한 마디로:
  "하나의 ID(Trace ID)로 모든 서비스를 꿰뚫는다"
```

### 2.3 분산 트레이싱의 특장점 정리

| 특장점 | 기존 (로그만) | 분산 트레이싱 적용 후 |
|-------|-------------|-------------------|
| 서비스 간 요청 추적 | 불가능 | Trace ID 하나로 전체 추적 |
| 병목 구간 파악 | 추측에 의존 | Span별 소요 시간으로 정확히 파악 |
| 장애 원인 분석 | SSH로 각 서버 접속 | Jaeger UI에서 즉시 확인 |
| CS 대응 | "확인 어렵습니다" | Trace ID로 정확한 경위 설명 |
| 서비스 의존성 파악 | 문서에 의존 | 실제 호출 관계 자동 시각화 |
| 성능 최적화 | 개별 서비스 프로파일링 | 전체 요청 흐름에서 최적화 포인트 발견 |

---

## 3장. 분산 트레이싱의 탄생 — Google Dapper에서 OpenTelemetry까지

### 3.1 역사 타임라인

```
2010  Google Dapper 논문 발표
      └── "대규모 분산 시스템에서 요청을 추적하는 방법"
      └── Trace ID, Span, 샘플링 개념을 처음 체계화
      └── Google 내부에서만 사용 (비공개)

2012  Twitter → Zipkin 오픈소스 공개
      └── Dapper 논문에 영감을 받아 만듦
      └── 최초의 오픈소스 분산 트레이싱 시스템
      └── Java 기반, 가볍고 심플한 설계
      └── Spring Cloud Sleuth가 Zipkin을 기반으로 발전

2015  CNCF → OpenTracing 프로젝트 시작
      └── 트레이싱 API 표준화 시도
      └── 벤더 중립적 API 제공
      └── Jaeger, Zipkin 등이 OpenTracing을 지원

2015  Google → OpenCensus 프로젝트 공개
      └── 트레이싱 + 메트릭을 통합 수집
      └── Google Cloud에 최적화

2017  Uber → Jaeger 오픈소스 공개
      └── Go 언어로 작성, 고성능
      └── CNCF에 기부, Graduated 프로젝트로 승격
      └── 현재 가장 널리 사용되는 트레이싱 백엔드

2019  OpenTracing + OpenCensus → OpenTelemetry (OTel) 합병
      └── "트레이싱 표준이 두 개면 표준이 아니다"
      └── 로그 + 메트릭 + 트레이스를 하나의 SDK로 통합
      └── CNCF 주도, 모든 클라우드 벤더가 지원

2022  Spring Boot 3.0 출시
      └── Spring Cloud Sleuth 지원 중단
      └── Micrometer Tracing으로 대체
      └── OpenTelemetry를 First-class로 지원

현재  OpenTelemetry = 업계 표준
      └── AWS, GCP, Azure 모두 OTel 지원
      └── Datadog, New Relic 등 상용 APM도 OTel 호환
      └── Spring Boot 3.x + Micrometer Tracing이 기본 스택
```

### 3.2 왜 이 역사를 알아야 하는가

인터넷에서 분산 트레이싱을 검색하면 **시대별로 다른 기술**이 나옵니다. 역사를 모르면 혼란에 빠집니다.

```
혼란스러운 검색 결과:

"Spring Boot 트레이싱 설정하기"
  → 2020년 글: spring-cloud-starter-sleuth 사용 (지금은 deprecated)
  → 2022년 글: spring-cloud-starter-sleuth + spring-cloud-sleuth-zipkin
  → 2023년 글: micrometer-tracing-bridge-brave (Zipkin 방식)
  → 2024년 글: micrometer-tracing-bridge-otel (OpenTelemetry 방식) ✅

정답:
  Spring Boot 3.x를 사용한다면
  → micrometer-tracing-bridge-otel + opentelemetry-exporter-otlp
  → 이것이 현재 표준이며, 이 핸드북에서 사용하는 방식입니다.
```

---

## 4장. [퀴즈] 분산 트레이싱이 필요한 상황 판단하기

아래 각 상황에서 분산 트레이싱이 필요한지 판단해보세요.

### 문제 1

```
상황: Spring Boot 모놀리스 앱이 하나의 서버에서 동작 중.
      가끔 특정 API가 느려진다는 CS가 들어옴.

분산 트레이싱이 필요한가?
```

<details>
<summary>정답 보기</summary>

**아니오.** 모놀리스이고 서버 1대이므로 분산 트레이싱까지는 불필요합니다. 기본 핸드북에서 배운 MDC + requestId + Logback으로 충분합니다. 다만, 향후 마이크로서비스 전환 계획이 있다면 미리 적용해두면 좋습니다.

</details>

### 문제 2

```
상황: 주문 서비스, 결제 서비스, 재고 서비스가 각각 별도 서버에서 동작.
      "주문 생성 시 간헐적으로 5초 이상 걸린다"는 리포트.
      어떤 서비스가 병목인지 파악 불가.

분산 트레이싱이 필요한가?
```

<details>
<summary>정답 보기</summary>

**예.** 전형적인 분산 트레이싱이 필요한 상황입니다. Trace를 통해 주문 → 결제 → 재고 각 구간의 소요 시간을 확인하면 병목 서비스를 즉시 파악할 수 있습니다.

</details>

### 문제 3

```
상황: 마이크로서비스 5개가 동작 중. ELK 스택으로 로그를 중앙 수집하고 있음.
      MDC에 requestId를 넣어서 서비스 내 로그는 추적 가능.
      하지만 서비스 A의 requestId와 서비스 B의 requestId가 달라서
      서비스 간 연결이 안 됨.

분산 트레이싱이 필요한가?
```

<details>
<summary>정답 보기</summary>

**예.** 이미 로깅 인프라는 갖추어져 있으므로, 분산 트레이싱을 추가하면 큰 시너지가 납니다. Trace ID가 모든 서비스에 자동 전파되면 Kibana에서 `traceId: "abc123"`으로 검색하여 모든 서비스의 로그를 한 번에 볼 수 있습니다.

</details>

### 문제 4

```
상황: API Gateway 뒤에 50개의 마이크로서비스가 동작.
      하루 요청 수 1억 건. 모든 요청을 트레이싱하고 싶음.

모든 요청을 트레이싱해야 하는가?
```

<details>
<summary>정답 보기</summary>

**절대 아니오.** 1억 건 × Span 평균 5개 = 5억 개의 Span 데이터가 매일 생성됩니다. 저장 비용과 네트워크 부하가 폭발합니다. 샘플링(보통 1~5%)을 적용해야 하며, Tail-based 샘플링으로 에러/느린 요청만 100% 저장하는 전략이 필요합니다.

</details>

---

# PART 2. 분산 트레이싱의 핵심 개념

---

## 5장. Trace, Span, Context — 세 가지만 알면 된다

### 5.1 현실 세계 비유

분산 트레이싱의 개념을 택배 추적 시스템에 비유하면 이해가 쉽습니다.

```
택배 추적 시스템:

  송장번호 (Trace ID):
    → 하나의 택배에 부여되는 고유 번호
    → 이 번호로 택배가 어디에 있는지 추적

  배송 구간 (Span):
    → "서울 물류센터 → 대전 허브" = Span 1 (소요: 2시간)
    → "대전 허브 → 부산 허브" = Span 2 (소요: 3시간)
    → "부산 허브 → 부산 OO동" = Span 3 (소요: 1시간)
    → 각 구간이 얼마나 걸렸는지 기록

  송장 전달 (Context Propagation):
    → 서울에서 택배를 보낼 때 송장번호를 붙임
    → 대전 허브에 도착해도 같은 송장번호가 유지됨
    → 부산까지 같은 송장번호로 추적 가능

분산 트레이싱:

  Trace ID = 송장번호
    → 하나의 사용자 요청에 부여되는 고유 ID

  Span = 배송 구간
    → 각 서비스에서의 작업 단위
    → 소요 시간, 성공/실패 여부 기록

  Context Propagation = 송장 전달
    → HTTP 헤더를 통해 Trace ID를 다음 서비스로 전달
```

### 5.2 Trace (트레이스)

```
Trace = 하나의 사용자 요청이 시스템을 관통하는 전체 여정

특징:
  - 32자리 hex 문자열 (128비트)
  - 예: 4bf92f3577b34da6a3ce929d0e0e4736
  - 하나의 Trace에는 1개 이상의 Span이 포함
  - 요청이 시작될 때 생성되고, 응답이 완료되면 끝남
```

### 5.3 Span (스팬)

```
Span = 하나의 작업 단위

특징:
  - 16자리 hex 문자열 (64비트)
  - 예: 00f067aa0ba902b7
  - 시작 시각, 종료 시각(또는 소요 시간)이 기록됨
  - 부모 Span을 가질 수 있음 (Parent Span ID)
  - 태그(Tags/Attributes)와 이벤트(Events/Logs)를 가짐

Span의 종류:
  Root Span:  요청의 시작점. Parent가 없는 최상위 Span.
  Child Span: 다른 Span의 하위 작업. Parent Span ID를 가짐.
```

실제 예시로 그려보겠습니다.

```
Trace ID: 4bf92f3577b34da6

시간축 →→→→→→→→→→→→→→→→→→→→→→→→→→→→→→→→→→→→→→

[Root Span] waiting-service: POST /api/waiting        ─────────────────── 250ms
  │
  ├─[Child Span] waiting-service: checkDuplicate         ────── 30ms
  │   └─[Child Span] MariaDB: SELECT * FROM waiting        ── 15ms
  │
  ├─[Child Span] waiting-service: saveWaiting               ────── 40ms
  │   └─[Child Span] MariaDB: INSERT INTO waiting              ── 20ms
  │
  └─[Child Span] waiting-service: HTTP POST /notification            ─────────── 150ms
      └─[Child Span] notification-service: POST /api/notify              ──────── 120ms
          └─[Child Span] notification-service: sendSMS                      ── 80ms

이 그래프에서 알 수 있는 것:
  1. 전체 요청은 250ms 소요
  2. 알림 서비스 호출이 150ms로 가장 오래 걸림 (60%)
  3. SMS 발송이 80ms → 병목은 SMS 발송!
  4. DB 쿼리는 각각 15ms, 20ms → 문제 없음
```

### 5.4 Context (컨텍스트)

```
Context = Trace ID + Span ID + 추가 정보를 담은 "바톤"

서비스 A가 서비스 B를 호출할 때:
  1. 현재의 Trace ID와 Span ID를 Context에 담음
  2. HTTP 헤더에 실어서 서비스 B로 전달 (바톤 전달)
  3. 서비스 B가 헤더에서 Context를 꺼냄
  4. 같은 Trace ID를 유지하면서 새 Span ID를 생성
  
  → 이것이 "Context Propagation"
```

---

## 6장. Span의 내부 구조 — 무엇이 기록되는가

### 6.1 Span 데이터 구조

```json
{
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "spanId": "00f067aa0ba902b7",
  "parentSpanId": "a1b2c3d4e5f6a7b8",
  "operationName": "POST /api/waiting",
  "serviceName": "waiting-service",
  
  "startTime": "2024-01-15T14:30:01.000Z",
  "endTime":   "2024-01-15T14:30:01.250Z",
  "duration":  250,
  
  "status": {
    "code": "OK"
  },
  
  "attributes": {
    "http.method": "POST",
    "http.url": "/api/waiting",
    "http.status_code": 200,
    "http.request_content_length": 48,
    "net.peer.name": "localhost",
    "net.peer.port": 8080,
    "custom.user_id": "1001",
    "custom.restaurant_id": "500"
  },
  
  "events": [
    {
      "name": "중복 확인 완료",
      "timestamp": "2024-01-15T14:30:01.030Z",
      "attributes": { "duplicate": false }
    },
    {
      "name": "DB 저장 완료",
      "timestamp": "2024-01-15T14:30:01.070Z",
      "attributes": { "waiting_number": 7 }
    }
  ],
  
  "links": []
}
```

### 6.2 각 필드 상세 설명

| 필드 | 설명 | 예시 |
|------|------|------|
| **traceId** | 요청 전체의 고유 ID (32자 hex) | `4bf92f3577b34da6a3ce929d0e0e4736` |
| **spanId** | 이 Span의 고유 ID (16자 hex) | `00f067aa0ba902b7` |
| **parentSpanId** | 부모 Span ID (Root면 null/비어있음) | `a1b2c3d4e5f6a7b8` |
| **operationName** | 이 Span이 무슨 작업을 하는지 | `POST /api/waiting` |
| **serviceName** | 이 Span을 생성한 서비스 이름 | `waiting-service` |
| **startTime** | Span 시작 시각 | ISO 8601 형식 |
| **duration** | 소요 시간 (밀리초) | `250` |
| **status** | 성공/실패 여부 | `OK`, `ERROR` |
| **attributes** | 키-값 쌍의 메타데이터 (태그) | `http.method=POST` |
| **events** | Span 내에서 발생한 이벤트들 | 로그와 비슷한 개념 |

### 6.3 Attributes — 무엇을 태깅해야 하는가

```
✅ 태깅해야 하는 것 (검색/필터링에 유용):
  http.method          → GET, POST, PUT, DELETE
  http.status_code     → 200, 400, 500
  http.url             → /api/waiting
  service.name         → waiting-service
  service.version      → 1.2.3
  deployment.environment → prod, staging, dev
  
  비즈니스 태그:
  custom.user_id       → 사용자 식별 (CS 대응)
  custom.restaurant_id → 비즈니스 엔티티 식별

🚫 절대 태깅하면 안 되는 것:
  비밀번호, 토큰, API Key
  주민등록번호, 카드번호
  전체 요청/응답 Body (크기 문제 + 개인정보)
```

---

## 7장. Context Propagation — 서비스 간 ID가 전달되는 원리

### 7.1 동작 원리 (아주 상세하게)

```
① 사용자 요청이 waiting-service에 도착

  HTTP 요청 헤더에 traceparent가 없음
  → "새로운 요청이구나" → 새 Trace ID 생성
  
  생성된 값:
    Trace ID: 4bf92f3577b34da6a3ce929d0e0e4736
    Span ID:  00f067aa0ba902b7  (Root Span)
  
  MDC에 자동 설정:
    MDC.put("traceId", "4bf92f3577b34da6a3ce929d0e0e4736")
    MDC.put("spanId", "00f067aa0ba902b7")

② waiting-service가 notification-service를 HTTP 호출

  WebClient(또는 RestTemplate)가 요청을 보낼 때:
  Spring Boot의 자동 설정이 HTTP 헤더에 Context를 추가:
  
  GET /api/notification/waiting HTTP/1.1
  Host: notification-service:8081
  Content-Type: application/json
  traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01
  ─────────────────────────────────────────────────────────────────
  버전─┘  Trace ID (동일하게 유지)──────────┘  Parent Span ID───┘  └─샘플링

③ notification-service가 요청 수신

  서블릿 필터가 traceparent 헤더를 파싱:
    Trace ID: 4bf92f3577b34da6a3ce929d0e0e4736  ← 동일!
    Parent Span ID: 00f067aa0ba902b7
    새 Span ID: 11a2b3c4d5e6f7a8  ← 새로 생성
  
  MDC에 자동 설정:
    MDC.put("traceId", "4bf92f3577b34da6a3ce929d0e0e4736")
    MDC.put("spanId", "11a2b3c4d5e6f7a8")
  
  → 이제 notification-service의 모든 로그에도
    동일한 traceId가 찍힘!

④ 결과

  waiting-service 로그:
    [traceId=4bf92f3577b34da6] 웨이팅 등록 요청
    [traceId=4bf92f3577b34da6] 알림 서비스 호출

  notification-service 로그:
    [traceId=4bf92f3577b34da6] 알림 수신     ← 같은 traceId!
    [traceId=4bf92f3577b34da6] SMS 발송 완료  ← 같은 traceId!

  → traceId=4bf92f3577b34da6 로 검색하면
    두 서비스의 모든 로그를 한 번에 볼 수 있음!
```

### 7.2 자동 전파가 지원되는 HTTP 클라이언트

```
Spring Boot 3.x + micrometer-tracing-bridge-otel에서
자동으로 traceparent 헤더를 주입해주는 HTTP 클라이언트:

  ✅ WebClient           ← 리액티브 (권장)
  ✅ RestTemplate         ← 전통적 동기 방식
  ✅ RestClient           ← Spring Boot 3.2+ 새 클라이언트
  ✅ OpenFeign            ← 선언적 HTTP 클라이언트
  ✅ Spring Cloud Gateway ← API Gateway

수동 설정이 필요한 것:
  ⚠️ HttpURLConnection   ← 기본 Java. 수동으로 헤더 추가 필요
  ⚠️ OkHttp              ← Interceptor 추가 필요
  ⚠️ Apache HttpClient   ← Interceptor 추가 필요
  
  Kafka, RabbitMQ 등 메시징:
  ⚠️ 별도 설정 필요 (메시지 헤더에 Context를 실어야 함)
```

---

## 8장. W3C Trace Context 표준 — traceparent 헤더 해부

### 8.1 traceparent 헤더

```
traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01
             ──  ────────────────────────────────  ────────────────  ──
             │              │                            │           │
          version      trace-id                    parent-id    trace-flags
          (2자리)      (32자리 hex)               (16자리 hex)   (2자리)

version:      00 (현재 고정)
trace-id:     요청의 고유 ID. 모든 서비스에서 동일하게 유지.
parent-id:    이 요청을 보낸 쪽의 Span ID.
trace-flags:  01 = 샘플링됨 (이 트레이스를 기록해야 함)
              00 = 샘플링 안 됨 (기록하지 않아도 됨)
```

### 8.2 왜 W3C 표준이 중요한가

```
W3C 표준 이전:
  Zipkin:  헤더 이름이 X-B3-TraceId, X-B3-SpanId, X-B3-Sampled
  Jaeger:  헤더 이름이 uber-trace-id
  AWS:     헤더 이름이 X-Amzn-Trace-Id
  → 서비스마다 다른 트레이싱 도구를 쓰면 헤더가 안 맞음

W3C 표준 이후:
  모든 도구가 traceparent 헤더를 지원
  → 서비스 A가 Jaeger, 서비스 B가 Zipkin을 써도 Trace가 연결됨
  → OpenTelemetry는 기본적으로 W3C 표준 사용
```

---

## 9장. 샘플링 전략 — 모든 요청을 추적하면 안 되는 이유

### 9.1 샘플링을 하지 않으면

```
하루 요청 1000만 건, 서비스 5개, 평균 Span 5개/요청

Span 데이터량:
  1000만 × 5 (Span/요청) = 5000만 Span/일
  Span 하나 평균 크기: ~1KB
  5000만 × 1KB = 약 50GB/일

저장 비용 (1년):
  50GB × 365일 = 18.25TB
  Elasticsearch SSD 기준: 약 $5,000~$10,000/년

네트워크 부하:
  50GB/일 = 약 600KB/초 지속 전송
  + 전송 실패 시 재시도 부하

앱 성능 영향:
  Span 생성 + 직렬화 + 전송 = 요청당 ~1ms 추가
  하지만 100% 트레이싱 시 GC 압력 증가 → 간헐적 지연 발생 가능
```

### 9.2 세 가지 샘플링 전략

```
1. Head-based Sampling (확률 기반) ← 가장 간단, 가장 일반적
─────────────────────────────────
  요청이 들어올 때 확률적으로 결정: "이 요청을 추적할까 말까?"
  
  설정: probability: 0.05  (5%)
  
  장점: 구현이 매우 간단 (application.yml 한 줄)
  단점: 중요한 에러 요청이 95%의 확률로 누락될 수 있음
  
  적합한 경우:
    - 트래픽이 많아서 5%만으로도 충분한 샘플이 확보될 때
    - 간헐적 에러보다 전체적 트렌드가 중요할 때

2. Rate-limiting Sampling (속도 제한)
─────────────────────────────────
  초당 최대 N개의 트레이스만 기록
  
  설정: rate_limit: 10  (초당 10개)
  
  장점: 데이터량을 정확히 예측 가능
  단점: 트래픽이 몰릴 때 정작 중요한 요청을 놓칠 수 있음

3. Tail-based Sampling (꼬리 기반) ← 가장 이상적, 가장 복잡
─────────────────────────────────
  일단 모든 요청을 추적하고, 완료된 후에 "이 트레이스를 저장할까?" 결정
  
  저장 조건 예시:
    - status == ERROR → 무조건 저장
    - duration > 3초 → 무조건 저장
    - 나머지 → 5%만 저장
  
  장점: 에러와 느린 요청을 절대 놓치지 않음
  단점: 
    - OTel Collector가 필요 (앱에서는 불가능)
    - Collector에 임시 메모리가 필요 (모든 Span을 잠시 보관)
    - 구현이 복잡
```

### 9.3 운영 규모별 권장 샘플링

| 규모 | 일 요청 수 | 샘플링 전략 | 설정값 |
|------|-----------|-----------|--------|
| 소규모 | ~10만 | 100% | probability: 1.0 |
| 중규모 | ~100만 | 10% | probability: 0.1 |
| 대규모 | ~1000만 | 1~5% | probability: 0.01~0.05 |
| 초대규모 | 1억+ | Tail-based | 에러/느린 요청=100%, 나머지=0.1% |

---

## 10장. [퀴즈] Trace와 Span 구조 이해하기

### 문제 1: Span 관계 파악

```
아래 Span 정보가 있습니다. 부모-자식 관계를 그려보세요.

Span A: { spanId: "aaa", parentSpanId: null,  operationName: "POST /api/order" }
Span B: { spanId: "bbb", parentSpanId: "aaa", operationName: "checkInventory" }
Span C: { spanId: "ccc", parentSpanId: "aaa", operationName: "processPayment" }
Span D: { spanId: "ddd", parentSpanId: "ccc", operationName: "callPG" }
Span E: { spanId: "eee", parentSpanId: "aaa", operationName: "sendNotification" }
```

<details>
<summary>정답 보기</summary>

```
[Span A] POST /api/order (Root)
  ├── [Span B] checkInventory
  ├── [Span C] processPayment
  │     └── [Span D] callPG
  └── [Span E] sendNotification
```

Span A가 Root Span(parentSpanId가 null)이고, B, C, E는 A의 자식, D는 C의 자식입니다.

</details>

### 문제 2: traceparent 해석

```
traceparent: 00-abcdef1234567890abcdef1234567890-1234567890abcdef-01

이 헤더에서 아래를 찾으세요:
1. Trace ID는?
2. Parent Span ID는?
3. 이 요청은 샘플링되었는가?
```

<details>
<summary>정답 보기</summary>

1. Trace ID: `abcdef1234567890abcdef1234567890` (32자리)
2. Parent Span ID: `1234567890abcdef` (16자리)
3. 샘플링 여부: `01` = 샘플링됨 (기록해야 함)

</details>

### 문제 3: 병목 식별

```
아래 트레이스에서 가장 큰 병목은 어디인가요?

[Root] POST /api/waiting                    총 820ms
  ├── [Span] checkDuplicate                   35ms
  ├── [Span] saveToDatabase                   45ms
  ├── [Span] HTTP POST /notification          ──── 700ms
  │     └── [Span] sendSMS                         650ms
  └── [Span] updateCache                      20ms
```

<details>
<summary>정답 보기</summary>

가장 큰 병목은 **sendSMS** (650ms)입니다. 전체 820ms 중 79%를 차지합니다. notification 서비스 내부의 SMS 발송이 원인입니다. 해결 방안: SMS 발송을 비동기(Async)로 전환하거나, 메시지 큐(Kafka)를 통해 비동기 처리하면 전체 응답 시간을 크게 줄일 수 있습니다.

</details>

---

# PART 3. 기술 스택 선택

---

## 11장. 분산 트레이싱 기술 생태계 전체 지도

```
┌──────────────────────────────────────────────────────────────────────┐
│                    분산 트레이싱 기술 생태계                            │
│                                                                       │
│  [계측 라이브러리 (앱에 설치)]                                         │
│  ┌─────────────────────────────────────────────────────────────────┐ │
│  │ OpenTelemetry SDK ← 현재 표준                                   │ │
│  │ Micrometer Tracing ← Spring Boot 3.x 추상화 (OTel 위에서 동작)  │ │
│  │ Brave ← Zipkin용 계측 라이브러리 (레거시)                        │ │
│  └─────────────────────────────────────────────────────────────────┘ │
│                              │                                       │
│                              │ OTLP 프로토콜                          │
│                              ▼                                       │
│  [수집기 (선택 사항)]                                                  │
│  ┌─────────────────────────┐                                         │
│  │ OpenTelemetry Collector │  ← 데이터 가공, 라우팅, 샘플링            │
│  └────────────┬────────────┘                                         │
│               │                                                      │
│               ▼                                                      │
│  [백엔드 (저장 + 검색)]                                               │
│  ┌──────────┐ ┌──────────┐ ┌──────────────┐ ┌─────────────────┐    │
│  │  Jaeger  │ │  Zipkin  │ │ Grafana Tempo │ │ 상용 APM        │    │
│  │  (CNCF)  │ │ (Twitter)│ │  (Grafana)   │ │ (Datadog, NR..) │    │
│  └──────────┘ └──────────┘ └──────────────┘ └─────────────────┘    │
│               │                                                      │
│               ▼                                                      │
│  [UI (시각화)]                                                        │
│  Jaeger UI / Zipkin UI / Grafana                                     │
└──────────────────────────────────────────────────────────────────────┘

우리가 선택할 스택:
  Micrometer Tracing (Spring Boot 추상화)
    → OpenTelemetry SDK (계측)
    → OTLP 프로토콜 (전송)
    → Jaeger (백엔드 + UI)
```

---

## 12장. OpenTelemetry — 왜 이것이 표준이 되었는가

### 12.1 SLF4J와의 비유

```
로깅 세계:
  SLF4J (추상화) → Logback, Log4j2 (구현체)
  → 구현체를 바꿔도 코드 변경 불필요

트레이싱 세계:
  OpenTelemetry API (추상화) → Jaeger, Zipkin, Tempo (백엔드)
  → 백엔드를 바꿔도 코드 변경 불필요
  
  처음에 Jaeger를 쓰다가 나중에 Grafana Tempo로 바꾸고 싶다면?
  → 앱 코드 변경 없이 exporter 설정만 변경하면 됨
```

### 12.2 OpenTelemetry가 제공하는 것

```
1. API: 계측 인터페이스 (코드에서 사용하는 것)
   → Span 생성, Attribute 추가, Event 기록 등

2. SDK: API의 실제 구현
   → Span을 만들고, 배치 처리하고, 전송 준비

3. Exporters: 백엔드로 데이터 전송
   → OTLP Exporter (표준), Jaeger Exporter, Zipkin Exporter 등

4. Auto-instrumentation: 코드 변경 없이 자동 계측
   → Java Agent를 붙이면 HTTP, DB, 메시징 등 자동 추적
   → 매우 편리하지만, 이 핸드북에서는 원리를 이해하기 위해 수동 방식도 함께 실습

5. OTLP 프로토콜: 데이터 전송 표준
   → gRPC (포트 4317) 또는 HTTP (포트 4318)
   → 모든 OTel 호환 백엔드가 지원
```

---

## 13장. Jaeger vs Zipkin — 어떤 것을 선택해야 하는가

| 항목 | Jaeger | Zipkin |
|------|--------|--------|
| 개발사/역사 | Uber (2017) → CNCF Graduated | Twitter (2012) |
| 구현 언어 | Go | Java |
| OTLP 지원 | 네이티브 (기본 지원) | 어댑터 필요 |
| UI 기능 | 풍부 (DAG, 비교, 검색 필터) | 심플 |
| 스토리지 | ES, Cassandra, Kafka, Badger, Memory | ES, MySQL, Cassandra, Memory |
| 성능 | 대규모 트래픽에 최적화 | 중소규모에 적합 |
| 배포 모드 | All-in-One / 분산 (Collector, Query, Ingester) | All-in-One / 분산 |
| 커뮤니티 | CNCF, 매우 활발 | 안정적이지만 Jaeger보다 작음 |

```
선택 가이드:

"Spring Boot 3.x + 운영 환경에서 사용할 거라면"
  → Jaeger ✅ (OTLP 네이티브, CNCF, 활발한 커뮤니티)

"학습 목적 또는 매우 가볍게 시작하고 싶다면"
  → Zipkin도 OK (더 심플, Spring 생태계와 역사적으로 친밀)

"나중에 Grafana Tempo로 전환할 수도 있다면"
  → Jaeger ✅ (둘 다 OTLP를 지원하므로 전환이 쉬움)

이 핸드북의 선택: Jaeger
  이유: OTLP 네이티브 + CNCF 표준 + 풍부한 UI + 실무에서 가장 많이 사용
```

---

## 14장. Spring Cloud Sleuth는 왜 사라졌는가

```
Spring Cloud Sleuth의 역사:

2015~2022: Spring Boot 2.x의 트레이싱 표준
  → spring-cloud-starter-sleuth 의존성 하나로 자동 설정
  → Brave 라이브러리 기반 (Zipkin 전용)
  → 매우 편리했지만...

문제점:
  1. Brave(Zipkin)에 강하게 결합
     → OpenTelemetry 지원이 어려움
  
  2. Spring Boot 3.0에서 Jakarta EE로 전환
     → Sleuth가 이 전환을 따라가지 못함
  
  3. OpenTelemetry가 업계 표준으로 자리잡음
     → Brave 전용 라이브러리보다 범용적인 추상화가 필요

결정:
  Spring팀이 Sleuth를 deprecated하고
  Micrometer Tracing을 새로운 표준으로 결정

영향:
  Spring Boot 2.x → 3.x로 업그레이드하는 모든 팀이
  Sleuth → Micrometer Tracing으로 마이그레이션 필요
```

---

## 15장. Micrometer Tracing — Spring Boot 3.x의 새로운 표준

### 15.1 Micrometer Tracing이란

```
Micrometer Tracing = 트레이싱의 SLF4J

  SLF4J   → Logback 또는 Log4j2    (로깅)
  Micrometer → Prometheus 또는 Datadog (메트릭)
  Micrometer Tracing → OTel 또는 Brave (트레이싱) ← 이것!

어떤 백엔드를 사용하든 코드는 동일:
  @Observed, ObservationRegistry, Tracer
  → Bridge 의존성만 바꾸면 백엔드 교체 가능
```

### 15.2 의존성 선택 가이드

```
OTel 방식 (권장):
  micrometer-tracing-bridge-otel
  + opentelemetry-exporter-otlp
  → Jaeger, Tempo, Datadog 등 OTLP 지원 백엔드로 전송

Brave 방식 (레거시/Zipkin 전용):
  micrometer-tracing-bridge-brave
  + zipkin-reporter-brave
  → Zipkin으로만 전송

이 핸드북의 선택: OTel 방식
  이유: 업계 표준, 백엔드 교체 용이, 더 넓은 생태계
```

---

## 16장. Sleuth → Micrometer 마이그레이션 완전 가이드

### 16.1 의존성 변경

```groovy
// ❌ 제거 (Spring Boot 2.x / Sleuth)
implementation 'org.springframework.cloud:spring-cloud-starter-sleuth'
implementation 'org.springframework.cloud:spring-cloud-sleuth-zipkin'

// ✅ 추가 (Spring Boot 3.x / Micrometer Tracing)
implementation 'io.micrometer:micrometer-tracing-bridge-otel'
implementation 'io.opentelemetry:opentelemetry-exporter-otlp'
```

### 16.2 설정 변경

```yaml
# ❌ 제거 (Sleuth)
spring:
  sleuth:
    sampler:
      probability: 1.0
  zipkin:
    base-url: http://localhost:9411

# ✅ 추가 (Micrometer Tracing)
management:
  tracing:
    sampling:
      probability: 1.0
  otlp:
    tracing:
      endpoint: http://localhost:4317
```

### 16.3 어노테이션 변경

```java
// ❌ Sleuth
@NewSpan("register-waiting")
public void register() { ... }

// ✅ Micrometer Tracing
@Observed(name = "waiting.register", contextualName = "register-waiting")
public void register() { ... }
```

### 16.4 변경되지 않는 것

```
✅ 로그 패턴에서 MDC 키: %X{traceId} %X{spanId}  ← 동일!
✅ WebClient, RestTemplate 자동 전파 ← 동일하게 동작
✅ 로그에 Trace ID가 자동 삽입되는 동작 ← 동일
```

---

# PART 4. [실습] 분산 트레이싱 처음부터 끝까지

---

## 17장. 실습 프로젝트 설계

```
맛집 웨이팅 마이크로서비스 구성:

┌──────────────┐         ┌─────────────────────┐
│ Client (curl)│────────→│ waiting-service      │
│              │         │ (포트 8080)           │
└──────────────┘         │                      │
                         │ - 웨이팅 등록/취소/조회 │
                         │ - 중복 확인            │
                         └──────────┬───────────┘
                                    │ HTTP 호출
                                    ▼
                         ┌─────────────────────┐
                         │ notification-service │
                         │ (포트 8081)           │
                         │                      │
                         │ - SMS 알림 발송       │
                         │ - 카카오톡 알림       │
                         └──────────────────────┘

트레이스 저장:
                         ┌─────────────────────┐
                         │ Jaeger (All-in-One)  │
                         │ UI: localhost:16686  │
                         │ OTLP: localhost:4317 │
                         └─────────────────────┘
```

---

## 18장. [실습] Jaeger 설치 (Docker)

### 18.1 docker-compose.yml

```yaml
# docker/docker-compose.yml
version: '3.8'

services:
  jaeger:
    image: jaegertracing/all-in-one:1.54
    container_name: jaeger
    environment:
      - SPAN_STORAGE_TYPE=memory       # 메모리 저장 (개발용)
      - COLLECTOR_OTLP_ENABLED=true    # OTLP 수신 활성화
      - LOG_LEVEL=info
    ports:
      # ── 데이터 수신 ──
      - "4317:4317"       # OTLP gRPC ★ (앱에서 이 포트로 전송)
      - "4318:4318"       # OTLP HTTP
      - "14250:14250"     # Jaeger gRPC (레거시)
      - "14268:14268"     # Jaeger HTTP (레거시)
      - "6831:6831/udp"   # Jaeger Thrift compact (레거시)
      # ── UI ──
      - "16686:16686"     # Jaeger UI ★ (이 주소로 접속)
    restart: unless-stopped
```

### 18.2 실행 및 확인

```bash
cd docker
docker compose up -d

# Jaeger UI 접속 확인
# 브라우저: http://localhost:16686
# → "Service" 드롭다운이 보이면 성공 (아직 서비스가 없으므로 비어있음)

# OTLP gRPC 포트 확인
curl -v http://localhost:4318/v1/traces 2>&1 | head -5
# → 연결이 되면 성공 (실제 데이터가 없으므로 에러 응답이 와도 정상)
```

---

## 19장. [실습] waiting-service 생성 및 트레이싱 설정

### 19.1 build.gradle

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.2.2'
    id 'io.spring.dependency-management' version '1.1.4'
}

group = 'com.example'
version = '0.0.1-SNAPSHOT'

java {
    sourceCompatibility = '17'
}

repositories {
    mavenCentral()
}

dependencies {
    // ── 웹 ──
    implementation 'org.springframework.boot:spring-boot-starter-web'
    
    // ── Actuator (health check + metrics endpoint) ──
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    
    // ── 트레이싱 핵심 의존성 (총 2개) ──
    // 1. Micrometer → OpenTelemetry 브릿지
    implementation 'io.micrometer:micrometer-tracing-bridge-otel'
    // 2. OTLP Exporter (Jaeger로 전송)
    implementation 'io.opentelemetry:opentelemetry-exporter-otlp'
    
    // ── HTTP 클라이언트 (서비스 간 호출, Trace 자동 전파) ──
    implementation 'org.springframework.boot:spring-boot-starter-webflux'
    
    // ── Lombok ──
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
    
    // ── 테스트 ──
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}
```

### 19.2 application.yml

```yaml
server:
  port: 8080

spring:
  application:
    name: waiting-service    # ★ Jaeger UI에 이 이름으로 표시됨

# ═══════════════════════════════════════
# 트레이싱 설정
# ═══════════════════════════════════════
management:
  tracing:
    sampling:
      probability: 1.0       # 100% 샘플링 (개발 환경)

  otlp:
    tracing:
      endpoint: http://localhost:4317   # Jaeger OTLP gRPC 엔드포인트

  endpoints:
    web:
      exposure:
        include: health, info

# ═══════════════════════════════════════
# 로그 패턴 — Trace ID 자동 삽입 ★
# ═══════════════════════════════════════
logging:
  pattern:
    # %X{traceId} : MDC에서 traceId를 가져와 출력
    # %X{spanId}  : MDC에서 spanId를 가져와 출력
    console: >-
      %d{yyyy-MM-dd HH:mm:ss.SSS}
      [%thread]
      [traceId=%X{traceId:-none} spanId=%X{spanId:-none}]
      %-5level %logger{36} - %msg%n
  level:
    root: INFO
    com.example: DEBUG
```

### 19.3 ObservationConfig.java (★ 필수)

```java
package com.example.waiting.config;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObservationConfig {

    /**
     * ★ 이 Bean이 없으면 @Observed 어노테이션이 동작하지 않습니다!
     * 
     * ObservedAspect는 AOP를 사용하여 @Observed가 붙은 메서드를
     * 자동으로 Span으로 감싸줍니다.
     */
    @Bean
    ObservedAspect observedAspect(ObservationRegistry registry) {
        return new ObservedAspect(registry);
    }
}
```

### 19.4 WaitingService.java

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

    public WaitingService(WebClient.Builder webClientBuilder) {
        // ★ WebClient.Builder를 주입받으면 Spring Boot가
        //    자동으로 트레이싱 필터를 설정해줍니다.
        //    직접 WebClient.create()를 하면 자동 전파가 안 됩니다!
        this.webClient = webClientBuilder
                .baseUrl("http://localhost:8081")
                .build();
    }

    /**
     * @Observed 어노테이션:
     *   name: 메트릭 이름 (Prometheus에서 사용)
     *   contextualName: Span 이름 (Jaeger에서 표시)
     *   → 이 메서드 호출 시 자동으로 Span이 생성됨
     */
    @Observed(
        name = "waiting.register",
        contextualName = "register-waiting",
        lowCardinalityKeyValues = {"operation", "register"}
    )
    public Map<String, Object> register(Long userId, Long restaurantId) {
        log.info("웨이팅 등록 시작 - userId: {}, restaurantId: {}", userId, restaurantId);

        // ── 중복 확인 ──
        boolean duplicate = store.values().stream()
                .anyMatch(w -> w.get("userId").equals(userId)
                        && w.get("restaurantId").equals(restaurantId)
                        && "WAITING".equals(w.get("status")));

        if (duplicate) {
            log.warn("중복 웨이팅 시도 - userId: {}, restaurantId: {}", userId, restaurantId);
            throw new IllegalStateException("이미 대기 중입니다.");
        }

        // ── DB 저장 (시뮬레이션) ──
        Long id = seq.getAndIncrement();
        Map<String, Object> waiting = new ConcurrentHashMap<>(Map.of(
            "id", id,
            "userId", userId,
            "restaurantId", restaurantId,
            "waitingNumber", id.intValue(),
            "status", "WAITING"
        ));
        store.put(id, waiting);
        log.info("웨이팅 저장 완료 - id: {}, waitingNumber: {}", id, id.intValue());

        // ── 알림 서비스 호출 ──
        // ★ 이 HTTP 호출에서 traceparent 헤더가 자동으로 추가됩니다
        try {
            String result = webClient.post()
                    .uri("/api/notification/waiting")
                    .bodyValue(Map.of(
                        "userId", userId,
                        "waitingNumber", id.intValue(),
                        "message", "웨이팅 " + id.intValue() + "번으로 등록되었습니다."
                    ))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            log.info("알림 전송 완료 - response: {}", result);
        } catch (Exception e) {
            log.warn("알림 전송 실패 (무시) - error: {}", e.getMessage());
        }

        return waiting;
    }

    public Map<String, Object> cancel(Long waitingId) {
        log.info("웨이팅 취소 요청 - waitingId: {}", waitingId);
        Map<String, Object> waiting = store.get(waitingId);
        if (waiting == null) {
            throw new IllegalArgumentException("존재하지 않는 웨이팅");
        }
        waiting.put("status", "CANCELLED");
        log.info("웨이팅 취소 완료 - waitingId: {}", waitingId);
        return waiting;
    }
}
```

### 19.5 WaitingController.java

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
        try {
            Map<String, Object> result = waitingService.register(
                    req.get("userId"), req.get("restaurantId"));
            return ResponseEntity.ok(result);
        } catch (IllegalStateException e) {
            log.warn("[API] 등록 거부 - {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> cancel(@PathVariable Long id) {
        log.info("[API] DELETE /api/waiting/{}", id);
        try {
            return ResponseEntity.ok(waitingService.cancel(id));
        } catch (Exception e) {
            log.error("[API] 취소 오류 - {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
```

---

## 20장. [실습] notification-service 생성 및 Trace 전파

### 20.1 application.yml

```yaml
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
    console: >-
      %d{yyyy-MM-dd HH:mm:ss.SSS}
      [%thread]
      [traceId=%X{traceId:-none} spanId=%X{spanId:-none}]
      %-5level %logger{36} - %msg%n
```

### 20.2 NotificationController.java

```java
package com.example.notification.controller;

import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/notification")
public class NotificationController {

    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);

    @PostMapping("/waiting")
    @Observed(name = "notification.send", contextualName = "send-waiting-notification")
    public ResponseEntity<?> sendWaitingNotification(@RequestBody Map<String, Object> req) {
        // ★ 이 로그의 traceId가 waiting-service와 동일한지 확인하세요!
        log.info("[알림] 웨이팅 알림 수신 - userId: {}, waitingNumber: {}, message: {}",
                 req.get("userId"), req.get("waitingNumber"), req.get("message"));

        // SMS 발송 시뮬레이션
        sendSMS(req.get("userId").toString(), req.get("message").toString());

        log.info("[알림] 처리 완료 - userId: {}", req.get("userId"));
        return ResponseEntity.ok(Map.of("status", "sent", "channel", "SMS"));
    }

    private void sendSMS(String userId, String message) {
        log.debug("[SMS] 발송 시작 - userId: {}", userId);
        try {
            Thread.sleep(80);  // PG사 API 호출 시뮬레이션
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.debug("[SMS] 발송 완료 - userId: {}", userId);
    }
}
```

### 20.3 실행 및 검증

```bash
# 터미널 1: Jaeger
cd docker && docker compose up -d

# 터미널 2: waiting-service (8080)
cd waiting-service && ./gradlew bootRun

# 터미널 3: notification-service (8081)
cd notification-service && ./gradlew bootRun

# 터미널 4: 요청 전송
curl -X POST http://localhost:8080/api/waiting \
  -H "Content-Type: application/json" \
  -d '{"userId": 1, "restaurantId": 100}'
```

### 20.4 콘솔 로그 확인 — 핵심!

```
── waiting-service 콘솔 ──
14:30:01.100 [exec-1] [traceId=4bf92f35 spanId=00f067aa] INFO  c.e.w.c.WaitingController - [API] POST /api/waiting
14:30:01.105 [exec-1] [traceId=4bf92f35 spanId=a1b2c3d4] INFO  c.e.w.s.WaitingService - 웨이팅 등록 시작 - userId: 1
14:30:01.150 [exec-1] [traceId=4bf92f35 spanId=a1b2c3d4] INFO  c.e.w.s.WaitingService - 웨이팅 저장 완료 - id: 1
14:30:01.350 [exec-1] [traceId=4bf92f35 spanId=a1b2c3d4] INFO  c.e.w.s.WaitingService - 알림 전송 완료

── notification-service 콘솔 ──
14:30:01.200 [exec-3] [traceId=4bf92f35 spanId=e5f6a7b8] INFO  c.e.n.c.NotificationController - [알림] 웨이팅 알림 수신
14:30:01.280 [exec-3] [traceId=4bf92f35 spanId=e5f6a7b8] DEBUG c.e.n.c.NotificationController - [SMS] 발송 완료

★ 두 서비스의 traceId가 모두 4bf92f35로 동일합니다!
★ spanId는 각 서비스마다 다릅니다 (각자의 작업 단위이므로).
```

---

## 21장. [실습] Jaeger UI에서 트레이스 분석하기

### 21.1 접속 및 검색

```
1. 브라우저: http://localhost:16686

2. 좌측 "Service" 드롭다운: waiting-service 선택

3. "Find Traces" 클릭

4. 트레이스 목록에서 방금 전송한 트레이스 클릭
```

### 21.2 트레이스 상세 화면에서 확인할 것

```
트레이스 타임라인 (Jaeger UI):

waiting-service: POST /api/waiting                    ──────────────── 250ms
  ├── waiting-service: register-waiting                  ──────────── 230ms
  │     └── waiting-service: HTTP POST                      ─────── 150ms
  │           └── notification-service: POST /notification     ──── 120ms
  └── 완료

확인 포인트:
  ✅ 두 서비스(waiting + notification)가 하나의 트레이스로 연결
  ✅ 각 Span의 소요 시간이 바 형태로 시각화
  ✅ notification-service 호출이 전체 시간의 60% 차지 → 병목 지점
  ✅ Span을 클릭하면 Tags(http.method, http.status_code 등) 확인 가능
```

### 21.3 Jaeger UI 핵심 기능

```
1. Service & Operation 필터
   → 특정 서비스의 특정 엔드포인트만 검색

2. Tags 검색
   → http.status_code=500 으로 에러 트레이스만 필터링

3. Duration 필터
   → Min: 1s (1초 이상 걸린 느린 요청만 검색)

4. Compare 기능
   → 두 트레이스를 나란히 비교
   → "어제는 빨랐는데 오늘은 느리다" 분석에 유용

5. DAG (Directed Acyclic Graph)
   → 서비스 간 호출 관계를 그래프로 시각화
   → "어떤 서비스가 어떤 서비스를 호출하는지" 한눈에 파악
```

---

## 22장. [실습] 수동 Span 생성과 커스텀 태그

### 22.1 왜 수동 Span이 필요한가

```
@Observed는 메서드 단위로 Span을 만듭니다.
하지만 메서드 안에서 여러 단계를 구분하고 싶을 때가 있습니다.

예: register() 메서드 안에서
  1단계: 중복 확인 ← 얼마나 걸리는지 따로 측정하고 싶음
  2단계: DB 저장   ← 얼마나 걸리는지 따로 측정하고 싶음
  3단계: 알림 호출 ← 이건 이미 HTTP 호출이라 자동 Span 생성됨

이럴 때 수동으로 Span을 만듭니다.
```

### 22.2 ObservationRegistry로 수동 Span 생성

```java
package com.example.waiting.service;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class WaitingServiceV2 {

    private static final Logger log = LoggerFactory.getLogger(WaitingServiceV2.class);
    private final ObservationRegistry registry;

    public WaitingServiceV2(ObservationRegistry registry) {
        this.registry = registry;
    }

    public void registerWithDetailedSpans(Long userId, Long restaurantId) {

        // ── 1단계: 중복 확인 (수동 Span) ──
        Observation checkObs = Observation.createNotStarted("waiting.check-duplicate", registry);
        checkObs.lowCardinalityKeyValue("step", "duplicate-check");
        checkObs.highCardinalityKeyValue("user.id", userId.toString());
        
        boolean isDuplicate = checkObs.observe(() -> {
            log.info("중복 확인 시작 - userId: {}", userId);
            // ... DB 조회 로직 ...
            try { Thread.sleep(30); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            log.info("중복 확인 완료 - 결과: false");
            return false;
        });

        // ── 2단계: DB 저장 (수동 Span) ──
        Observation saveObs = Observation.createNotStarted("waiting.save-to-db", registry);
        saveObs.lowCardinalityKeyValue("step", "db-save");
        saveObs.lowCardinalityKeyValue("db.type", "MariaDB");
        
        saveObs.observe(() -> {
            log.info("DB 저장 시작 - userId: {}, restaurantId: {}", userId, restaurantId);
            // ... INSERT 로직 ...
            try { Thread.sleep(40); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            log.info("DB 저장 완료");
        });

        // ── 3단계: 알림 호출은 HTTP 자동 Span ──
        // webClient.post()... → 자동으로 Span 생성됨
    }
}
```

### 22.3 Jaeger에서 확인

```
수동 Span 적용 전:
  [Span] register-waiting                    ──────────────── 250ms
    └── [Span] HTTP POST /notification          ─────── 150ms

수동 Span 적용 후:
  [Span] register-waiting                    ──────────────── 250ms
    ├── [Span] waiting.check-duplicate          ── 30ms
    ├── [Span] waiting.save-to-db                  ── 40ms
    └── [Span] HTTP POST /notification                ─────── 150ms

→ 각 단계별 소요 시간이 Jaeger에서 개별 바로 표시됨
→ 어디가 느린지 더 정밀하게 파악 가능
```

---

## 23장. [실습] 로그에 Trace ID 자동 삽입하기

이미 19장에서 `%X{traceId}`를 설정했습니다. 여기서는 **왜 이것이 동작하는지** 원리를 깊이 설명합니다.

### 23.1 동작 원리

```
1. 요청이 들어오면 Micrometer Tracing이 Trace ID 생성
2. OpenTelemetry SDK가 MDC에 traceId, spanId를 자동 설정
   → 내부적으로 MDC.put("traceId", "4bf92f35...")가 실행됨
3. Logback의 %X{traceId}가 MDC에서 값을 가져와 로그에 출력
4. 요청이 끝나면 MDC에서 자동 제거

코드로 표현하면:
  // Spring Boot가 자동으로 하는 일 (개발자가 할 일 아님)
  MDC.put("traceId", currentSpan.context().traceId());
  MDC.put("spanId", currentSpan.context().spanId());
  try {
      filterChain.doFilter(request, response);
  } finally {
      MDC.remove("traceId");
      MDC.remove("spanId");
  }
```

### 23.2 기본 핸드북의 MDC requestId와의 관계

```
기본 핸드북에서 만든 MdcLoggingFilter:
  MDC.put("requestId", UUID.randomUUID().toString().substring(0, 8));

분산 트레이싱의 traceId:
  MDC.put("traceId", "4bf92f3577b34da6...");

Q: 둘 다 써야 하나요?
A: traceId가 requestId를 완전히 대체합니다.
   traceId가 더 강력합니다 (서비스 간 전파 + Jaeger 연동).
   
   기존 MdcLoggingFilter는 제거하거나,
   traceId가 없는 비HTTP 요청(스케줄러 등)에서만 사용하면 됩니다.
```

---

## 24장. [실습] ELK와 연동 — Kibana에서 Trace ID로 검색

### 24.1 Logstash Encoder로 JSON 로그에 Trace ID 포함

```groovy
// build.gradle에 추가
implementation 'net.logstash.logback:logstash-logback-encoder:7.4'
```

```xml
<!-- logback-spring.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <property name="LOG_PATTERN"
              value="%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [traceId=%X{traceId:-none} spanId=%X{spanId:-none}] %-5level %logger{36} - %msg%n" />

    <!-- 콘솔 (Plain Text) -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>${LOG_PATTERN}</pattern>
        </encoder>
    </appender>

    <!-- Logstash TCP (JSON) -->
    <appender name="LOGSTASH" class="net.logstash.logback.appender.LogstashTcpSocketAppender">
        <destination>localhost:5044</destination>
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <customFields>{"service":"waiting-service","env":"local"}</customFields>
            <!-- ★ MDC의 traceId, spanId를 JSON 필드로 자동 포함 -->
            <includeMdcKeyName>traceId</includeMdcKeyName>
            <includeMdcKeyName>spanId</includeMdcKeyName>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="CONSOLE" />
        <appender-ref ref="LOGSTASH" />
    </root>
</configuration>
```

### 24.2 Elasticsearch에 저장되는 JSON 구조

```json
{
  "@timestamp": "2024-01-15T14:30:01.123+09:00",
  "level": "INFO",
  "logger_name": "c.e.w.s.WaitingService",
  "thread_name": "http-nio-8080-exec-1",
  "message": "웨이팅 등록 완료 - id: 1, waitingNumber: 1",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "spanId": "a1b2c3d4e5f6a7b8",
  "service": "waiting-service",
  "env": "local"
}
```

### 24.3 Kibana에서 검색

```
KQL 검색어:

# 특정 트레이스의 모든 로그
traceId: "4bf92f3577b34da6a3ce929d0e0e4736"

# 특정 트레이스의 에러 로그만
traceId: "4bf92f3577b34da6a3ce929d0e0e4736" and level: "ERROR"

# 워크플로우:
# 1. Jaeger에서 느린/에러 트레이스 발견
# 2. Trace ID 복사
# 3. Kibana에서 traceId로 검색
# 4. 해당 요청의 상세 로그 확인 (모든 서비스)
```

---

## 25장. [퀴즈] 실습 종합 문제

### 문제 1: 설정 오류 찾기

```yaml
# 아래 설정에서 잘못된 부분을 찾으세요.
management:
  tracing:
    sampling:
      probability: 1.0
  otlp:
    tracing:
      endpoint: http://localhost:16686   # ← 여기!
```

<details>
<summary>정답 보기</summary>

`endpoint`가 Jaeger UI 포트(16686)로 되어 있습니다. **OTLP gRPC 포트(4317)**로 변경해야 합니다.

```yaml
endpoint: http://localhost:4317   # OTLP gRPC
```

16686은 Jaeger UI(웹 브라우저용)이고, 4317이 실제 트레이스 데이터를 수신하는 포트입니다.

</details>

### 문제 2: WebClient 자동 전파 안 되는 이유

```java
// 아래 코드에서 Trace가 전파되지 않습니다. 왜일까요?

@Service
public class WaitingService {
    private final WebClient webClient = WebClient.create("http://localhost:8081");
    // ...
}
```

<details>
<summary>정답 보기</summary>

`WebClient.create()`로 직접 생성하면 Spring Boot의 자동 설정(트레이싱 필터 주입)이 적용되지 않습니다.

**`WebClient.Builder`를 주입받아야 합니다:**

```java
public WaitingService(WebClient.Builder builder) {
    this.webClient = builder.baseUrl("http://localhost:8081").build();
}
```

Spring Boot가 `WebClient.Builder`에 `ExchangeFilterFunction`을 자동으로 추가하여 HTTP 요청 시 `traceparent` 헤더를 주입해줍니다.

</details>

### 문제 3: @Observed가 동작하지 않는 이유

```java
@Service
public class WaitingService {
    @Observed(name = "waiting.register")
    public void register() { ... }
}
// → Jaeger에서 "waiting.register" Span이 보이지 않음
```

<details>
<summary>정답 보기</summary>

`ObservedAspect` Bean이 등록되지 않았기 때문입니다. `@Observed`는 AOP 기반이므로 아래 설정이 반드시 필요합니다:

```java
@Configuration
public class ObservationConfig {
    @Bean
    ObservedAspect observedAspect(ObservationRegistry registry) {
        return new ObservedAspect(registry);
    }
}
```

</details>

---

# PART 5. 로그를 어떻게 구성해야 하는가

---

## 26장. 트레이싱 시대의 로그 설계 원칙

### 26.1 로그의 역할 변화

```
트레이싱 도입 전:
  로그 = 유일한 디버깅 수단
  → 모든 정보를 로그에 담아야 했음
  → 로그가 매우 장황해짐

트레이싱 도입 후:
  트레이스 = 요청 흐름 + 소요 시간 + 서비스 간 관계
  로그 = 상세한 비즈니스 이벤트 기록
  
  → 역할이 분리됨!
  → 로그는 더 간결하고 핵심적인 정보에 집중할 수 있음
```

### 26.2 트레이싱 시대의 로그 5원칙

```
원칙 1: "흐름은 트레이스에, 내용은 로그에"
  ❌ log.info("주문서비스에서 결제서비스 호출 시작")  ← 트레이스가 해줌
  ✅ log.info("결제 금액: {}원, 수단: {}", amount, method)  ← 비즈니스 내용

원칙 2: "모든 로그에 traceId가 포함되어야 한다"
  → Logback 패턴에 %X{traceId} 필수
  → JSON 로그라면 traceId 필드 포함

원칙 3: "로그 레벨은 더 엄격하게"
  트레이싱이 있으므로 DEBUG 로그를 줄일 수 있음
  → 메서드 진입/종료 로그 불필요 (트레이스가 Span으로 기록)
  → 비즈니스 결정 포인트만 INFO로 기록

원칙 4: "에러 로그에는 반드시 컨텍스트 포함"
  ❌ log.error("에러 발생", e)
  ✅ log.error("결제 실패 - orderId: {}, amount: {}, reason: {}",
              orderId, amount, e.getMessage(), e)

원칙 5: "민감 정보는 로그에도 트레이스에도 남기지 않는다"
  ❌ log.info("카드번호: {}", cardNumber)
  ❌ span.setAttribute("card.number", cardNumber)
  ✅ log.info("카드: ****-****-****-{}", last4Digits)
```

---

## 27장. 로그 포맷 — Plain Text vs JSON 구조화 로그

### 27.1 Plain Text 로그

```
2024-01-15 14:30:01.123 [exec-1] [traceId=4bf92f35 spanId=00f067aa] INFO c.e.w.s.WaitingService - 웨이팅 등록 완료 - id: 1

장점: 사람이 읽기 쉬움, 콘솔에서 바로 확인 가능
단점: 파싱이 어려움, grok 정규식 필요
적합: 개발 환경 콘솔 출력
```

### 27.2 JSON 구조화 로그

```json
{"@timestamp":"2024-01-15T14:30:01.123","level":"INFO","logger":"c.e.w.s.WaitingService","thread":"exec-1","traceId":"4bf92f35","spanId":"00f067aa","message":"웨이팅 등록 완료","waitingId":1}

장점: 파싱 불필요, 필드별 검색 가능, ES와 완벽 호환
단점: 사람이 읽기 어려움
적합: 운영 환경, ELK/Loki 연동 시
```

### 27.3 권장: 환경별 분리

```xml
<!-- logback-spring.xml -->
<!-- 개발: 콘솔에 Plain Text -->
<springProfile name="local,dev">
    <root level="DEBUG">
        <appender-ref ref="CONSOLE" />  <!-- Plain Text -->
    </root>
</springProfile>

<!-- 운영: 파일에 JSON + Logstash 전송 -->
<springProfile name="prod">
    <root level="INFO">
        <appender-ref ref="JSON_FILE" />   <!-- JSON 파일 -->
        <appender-ref ref="LOGSTASH" />    <!-- Logstash TCP -->
    </root>
</springProfile>
```

---

## 28장. MDC + Trace ID + Span ID 통합 전략

```
기본 핸드북에서 만든 MDC 필드:
  requestId:  UUID (서비스 내 요청 식별)
  clientIp:   클라이언트 IP

분산 트레이싱이 자동으로 추가하는 MDC 필드:
  traceId:    Trace ID (서비스 간 요청 추적)
  spanId:     Span ID (현재 작업 단위)

통합 후 로그 패턴:
  %d [%thread] [traceId=%X{traceId} spanId=%X{spanId}] [ip=%X{clientIp}] %-5level %logger{36} - %msg%n

clientIp는 여전히 유용 (같은 traceId라도 어느 클라이언트인지 알아야 하므로)
requestId는 traceId로 대체 가능 (제거하거나 비HTTP 요청용으로만 유지)
```

---

## 29장. 로그 레벨 전략 — 트레이싱과 함께 쓸 때

```
트레이싱 도입 전 (기본 핸드북):
  DEBUG: 메서드 진입/종료, 변수 값, SQL 쿼리
  INFO:  비즈니스 이벤트, 요청 시작/완료
  
트레이싱 도입 후:
  DEBUG: 줄여도 됨 (Span이 메서드 소요 시간을 기록하므로)
  INFO:  비즈니스 결정 포인트에 집중

구체적 가이드:
  ❌ 불필요해진 로그 (트레이스가 대신함):
    log.debug("메서드 진입: register()")    → Span이 기록
    log.debug("메서드 종료: register()")    → Span이 기록
    log.info("알림 서비스 호출 시작")        → HTTP Span이 기록
    log.info("알림 서비스 응답 수신")        → HTTP Span이 기록
    log.debug("소요 시간: {}ms", duration)  → Span duration이 기록

  ✅ 여전히 필요한 로그 (트레이스가 못하는 것):
    log.info("웨이팅 등록 - userId: {}, waitingNumber: {}", ...)  → 비즈니스 데이터
    log.warn("중복 등록 시도 - userId: {}", ...)                  → 비즈니스 판단
    log.error("결제 실패 - reason: {}", ..., exception)           → 에러 상세 + 스택트레이스
```

---

## 30장. [실습] 최적의 로그 포맷 설정하기

```xml
<!-- logback-spring.xml — 분산 트레이싱 최적화 버전 -->
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <property name="APP_NAME" value="waiting-service" />

    <!-- ── 개발용 콘솔 (Plain Text, 읽기 쉬움) ── -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] [%X{traceId:-NO_TRACE}] %-5level %logger{20} - %msg%n</pattern>
        </encoder>
    </appender>

    <!-- ── 운영용 JSON 파일 (ES/Loki 연동에 최적) ── -->
    <appender name="JSON_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/${APP_NAME}.json</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
            <fileNamePattern>logs/archived/${APP_NAME}.%d{yyyy-MM-dd}.%i.json.gz</fileNamePattern>
            <maxFileSize>100MB</maxFileSize>
            <maxHistory>30</maxHistory>
            <totalSizeCap>3GB</totalSizeCap>
        </rollingPolicy>
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <customFields>{"service":"${APP_NAME}"}</customFields>
            <includeMdcKeyName>traceId</includeMdcKeyName>
            <includeMdcKeyName>spanId</includeMdcKeyName>
            <includeMdcKeyName>clientIp</includeMdcKeyName>
        </encoder>
    </appender>

    <springProfile name="local">
        <root level="DEBUG">
            <appender-ref ref="CONSOLE" />
        </root>
    </springProfile>

    <springProfile name="prod">
        <root level="INFO">
            <appender-ref ref="JSON_FILE" />
        </root>
    </springProfile>
</configuration>
```

---

# PART 6. 운영 환경 구성 가이드

---

## 31장. 운영 환경에서의 샘플링 전략 설계

### 31.1 운영 환경 application.yml

```yaml
# ═══ 운영 환경 (application-prod.yml) ═══
management:
  tracing:
    sampling:
      # ★ 운영 환경에서는 절대 1.0(100%)을 사용하지 마세요!
      probability: 0.05    # 5% 샘플링
      
  otlp:
    tracing:
      # 운영 환경에서는 OTel Collector를 경유하는 것이 권장됨
      endpoint: http://otel-collector:4317
      # 직접 Jaeger로 보내는 것도 가능하지만 Collector 경유가 더 유연
```

### 31.2 중요한 요청은 100% 트레이싱하는 방법

```java
// 특정 조건에서 샘플링을 강제하는 커스텀 설정
// 예: 에러 응답일 때는 항상 트레이싱

@Component
public class CustomSampler implements io.opentelemetry.sdk.trace.samplers.Sampler {
    
    private final Sampler defaultSampler = Sampler.traceIdRatioBased(0.05); // 5%

    @Override
    public SamplingResult shouldSample(/* ... */) {
        // 에러 관련 헤더가 있으면 무조건 샘플링
        // (실제로는 Tail-based 샘플링을 OTel Collector에서 하는 것이 더 좋음)
        return defaultSampler.shouldSample(/* ... */);
    }
}
```

---

## 32장. Jaeger 프로덕션 아키텍처

### 32.1 개발 vs 운영 아키텍처

```
개발 (All-in-One):
  [App] ── OTLP ──→ [Jaeger All-in-One (Memory)]
  → 간단하지만 재시작 시 데이터 소실

운영 (분산 모드):
  [App] ── OTLP ──→ [OTel Collector]
                         │
                         ▼
                    [Kafka] (버퍼)
                         │
                         ▼
                    [Jaeger Collector]
                         │
                         ▼
                    [Elasticsearch] (영구 저장)
                         │
                         ▼
                    [Jaeger Query + UI] (검색/시각화)
```

### 32.2 운영 환경 docker-compose.yml

```yaml
version: '3.8'

services:
  # ── Elasticsearch (Jaeger 스토리지) ──
  jaeger-elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.12.0
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
      - "ES_JAVA_OPTS=-Xms512m -Xmx512m"
    ports:
      - "9200:9200"
    volumes:
      - jaeger-es-data:/usr/share/elasticsearch/data

  # ── Jaeger Collector (트레이스 수신 + ES 저장) ──
  jaeger-collector:
    image: jaegertracing/jaeger-collector:1.54
    environment:
      - SPAN_STORAGE_TYPE=elasticsearch
      - ES_SERVER_URLS=http://jaeger-elasticsearch:9200
      - COLLECTOR_OTLP_ENABLED=true
    ports:
      - "4317:4317"     # OTLP gRPC
      - "4318:4318"     # OTLP HTTP
      - "14250:14250"   # Jaeger gRPC
    depends_on:
      - jaeger-elasticsearch

  # ── Jaeger Query + UI (검색/시각화) ──
  jaeger-query:
    image: jaegertracing/jaeger-query:1.54
    environment:
      - SPAN_STORAGE_TYPE=elasticsearch
      - ES_SERVER_URLS=http://jaeger-elasticsearch:9200
    ports:
      - "16686:16686"   # Jaeger UI
    depends_on:
      - jaeger-elasticsearch

volumes:
  jaeger-es-data:
```

---

## 33장. 운영 환경 application.yml 완전 가이드

```yaml
# ═══════════════════════════════════════════════════
# application-prod.yml — 운영 환경 전체 설정
# ═══════════════════════════════════════════════════

spring:
  application:
    name: waiting-service

server:
  port: 8080

# ── 트레이싱 ──
management:
  tracing:
    sampling:
      probability: 0.05                          # 5% 샘플링

  otlp:
    tracing:
      endpoint: http://otel-collector:4317       # OTel Collector 경유
      # 직접 Jaeger: http://jaeger-collector:4317

  # ── Actuator ──
  endpoints:
    web:
      exposure:
        include: health, prometheus, info
  endpoint:
    health:
      show-details: when-authorized              # 인증된 사용자만 상세 정보
  metrics:
    tags:
      application: waiting-service
      environment: prod

# ── 로깅 ──
logging:
  level:
    root: INFO
    com.example: INFO                             # 운영에서는 DEBUG 금지
    org.springframework.web: WARN
    org.hibernate: WARN
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [traceId=%X{traceId:-}] %-5level %logger{36} - %msg%n"
```

---

## 34장. 성능 영향 분석 — 트레이싱이 앱에 미치는 부하

```
트레이싱 오버헤드 측정 결과 (일반적인 수치):

                   트레이싱 OFF    100% 트레이싱    5% 트레이싱
─────────────────────────────────────────────────────────────
평균 응답 시간       50ms           52ms (+4%)     50.1ms (+0.2%)
P99 응답 시간       200ms          210ms (+5%)    201ms (+0.5%)
CPU 사용률           30%            33% (+3%p)     30.5% (+0.5%p)
메모리 사용           512MB          530MB (+18MB)  514MB (+2MB)
네트워크 (out)       100Mbps        110Mbps        100.5Mbps

결론:
  - 5% 샘플링 시: 성능 영향 거의 없음 (0.5% 미만)
  - 100% 샘플링 시: 3~5% 오버헤드 (개발 환경에서만)
  - 가장 큰 영향: 네트워크 I/O (Span 데이터 전송)
  - GC 영향: 100% 트레이싱 시 Span 객체 생성으로 Minor GC 빈도 약간 증가
```

---

## 35장. 장애 대응 시나리오별 트레이싱 활용법

### 시나리오 1: 특정 API가 간헐적으로 느림

```
1. Jaeger UI에서 Duration 필터: > 3s (3초 초과)
2. 느린 트레이스들의 공통점 파악
   → 특정 서비스의 Span만 느린가?
   → DB 쿼리 Span이 느린가?
   → 외부 API 호출이 느린가?
3. 병목 Span의 Tags 확인
   → db.statement, http.url 등으로 원인 특정
```

### 시나리오 2: 에러율 급증

```
1. Jaeger UI에서 Tags 검색: error=true
2. 에러 트레이스들의 Span 확인
   → 어느 서비스에서 에러가 시작되었는가?
   → 에러 Span의 Events/Logs에서 에러 메시지 확인
3. 해당 Trace ID를 Kibana에서 검색
   → 스택트레이스 등 상세 로그 확인
```

### 시나리오 3: CS 문의 "내 요청이 안 됩니다"

```
1. 프론트엔드에서 X-Trace-Id 응답 헤더를 고객에게 안내
   → "에러 화면의 Trace ID를 알려주세요: abc123..."
2. Jaeger에서 Trace ID로 직접 검색
3. 전체 요청 흐름과 에러 지점을 즉시 파악
4. 고객에게 정확한 원인 설명 가능
```

---

## 36장. 보안 — 트레이스에 남기면 안 되는 것들

```
🚫 절대 Span Attribute에 넣으면 안 되는 것:
  - 비밀번호, 토큰, API Key
  - 주민등록번호, 카드번호
  - 의료 정보
  - 전체 HTTP Request/Response Body

⚠️ 주의해서 넣어야 하는 것:
  - userId: 허용하되, 내부 ID만 (이메일, 전화번호 X)
  - 요청 파라미터: 핵심만 선별
  - URL: 경로 파라미터에 개인정보가 포함되지 않는지 확인

✅ 넣어야 하는 것:
  - http.method, http.status_code, http.url (경로)
  - service.name, service.version
  - deployment.environment
  - 비즈니스 엔티티 ID (orderId, waitingId 등)
```

---

## 37장. [퀴즈] 운영 환경 설계 문제

### 문제 1

```
하루 요청 500만 건, 서비스 10개인 환경.
Jaeger에 Elasticsearch를 스토리지로 사용 중.
100% 샘플링을 하면 하루에 ES에 얼마나 쌓일까요?
(Span 하나 = 1KB, 요청당 평균 Span 8개)
```

<details>
<summary>정답 보기</summary>

500만 × 8 Span = 4000만 Span/일
4000만 × 1KB = 약 40GB/일
ES 역인덱싱 오버헤드 × 1.5 = 약 60GB/일

한 달이면 1.8TB, 1년이면 21.6TB → 100% 샘플링은 비현실적입니다.
5% 샘플링 시: 약 3GB/일 → 한 달 90GB → 관리 가능합니다.

</details>

### 문제 2

```
운영 환경에서 Trace ID를 고객에게 노출하는 것은 보안 위험인가요?
```

<details>
<summary>정답 보기</summary>

**Trace ID 자체는 보안 위험이 아닙니다.** Trace ID는 단순한 128비트 랜덤 값이며, 이것만으로는 어떤 데이터에도 접근할 수 없습니다. 다만 Jaeger UI가 공개 인터넷에 노출되어 있다면 Trace ID로 내부 시스템 정보를 조회할 수 있으므로, Jaeger UI는 반드시 내부 네트워크에서만 접근 가능하게 해야 합니다.

API 응답 헤더에 `X-Trace-Id`를 포함하는 것은 널리 사용되는 패턴이며, CS 대응 시 매우 유용합니다.

</details>

---

# PART 7. 정리와 다음 단계

---

## 38장. 분산 트레이싱 체크리스트

```
도입 전 체크리스트:

□ 마이크로서비스가 2개 이상인가?
□ 서비스 간 HTTP/gRPC 호출이 있는가?
□ 장애 발생 시 어느 서비스가 원인인지 파악이 어려운가?
□ CS 대응 시 "확인이 어렵습니다"라고 답하고 있는가?
→ 하나라도 Yes면 분산 트레이싱 도입을 권장합니다.

기술 스택 체크리스트:

□ Spring Boot 3.x 사용 (2.x면 먼저 업그레이드)
□ micrometer-tracing-bridge-otel 의존성 추가
□ opentelemetry-exporter-otlp 의존성 추가
□ ObservedAspect Bean 등록
□ application.yml에 management.tracing 설정
□ application.yml에 management.otlp.tracing.endpoint 설정
□ 로그 패턴에 %X{traceId} %X{spanId} 추가
□ Jaeger (또는 Tempo) 설치 및 동작 확인
□ WebClient.Builder를 주입받아 사용 (자동 전파)
□ 운영 환경 샘플링 비율 설정 (1.0이 아닌 값)

운영 체크리스트:

□ 샘플링 비율이 적절한가? (5% 이하 권장)
□ Jaeger UI가 내부 네트워크에서만 접근 가능한가?
□ Span에 민감 정보가 포함되어 있지 않은가?
□ Jaeger 스토리지 보관 기간 설정 (기본 7일)
□ 트레이싱 오버헤드 모니터링 (CPU, 메모리, 네트워크)
```

---

## 39장. 자주 하는 실수 TOP 10

```
1위: WebClient.create()로 직접 생성
    → Trace가 전파되지 않음
    → WebClient.Builder를 주입받아야 함

2위: ObservedAspect Bean 미등록
    → @Observed가 동작하지 않음

3위: 운영 환경에서 100% 샘플링
    → 스토리지/네트워크 비용 폭발

4위: OTLP 포트를 Jaeger UI 포트(16686)로 설정
    → 4317(gRPC) 또는 4318(HTTP)이 올바른 포트

5위: Span에 요청 Body 전체를 태깅
    → 데이터량 폭발 + 개인정보 유출 위험

6위: spring-cloud-starter-sleuth를 Spring Boot 3.x에서 사용
    → 호환되지 않음. micrometer-tracing으로 마이그레이션 필요

7위: Trace ID를 로그 패턴에 포함하지 않음
    → Jaeger에서 문제를 찾아도 상세 로그를 볼 수 없음

8위: 모든 메서드에 @Observed 붙이기
    → Span이 수십 개 생성 → 트레이스가 너무 복잡해짐
    → 의미 있는 작업 단위에만 적용

9위: Kafka/RabbitMQ 메시지에 Context 전파를 안 함
    → 비동기 메시징은 자동 전파가 안 됨. 수동으로 헤더에 추가 필요

10위: Jaeger 스토리지 보관 기간을 설정하지 않음
     → 디스크가 가득 참. 반드시 retention 설정 필요
```

---

## 40장. 다음 단계 — OpenTelemetry Collector, Grafana Tempo

```
이 핸드북을 마치면 할 수 있는 것:
  ✅ Spring Boot 마이크로서비스에 분산 트레이싱 적용
  ✅ Jaeger로 트레이스 저장 및 분석
  ✅ 로그에 Trace ID 자동 삽입
  ✅ 서비스 간 Trace ID 전파
  ✅ 운영 환경 샘플링 설계

다음으로 배울 것:

1. OpenTelemetry Collector
   → 앱과 백엔드 사이의 중간 계층
   → Tail-based 샘플링 (에러=100%, 나머지=1%)
   → 데이터 가공, 필터링, 라우팅
   → 백엔드 교체 시 앱 변경 불필요

2. Grafana Tempo
   → Jaeger 대안, Object Storage(S3) 기반
   → 스토리지 비용 대폭 절감
   → Grafana에서 로그(Loki) ↔ 트레이스(Tempo) ↔ 메트릭(Mimir) 원클릭 전환

3. OTel Java Agent (자동 계측)
   → java -javaagent:opentelemetry-javaagent.jar -jar app.jar
   → 코드 변경 없이 HTTP, DB, 메시징 자동 트레이싱
   → @Observed 없이도 모든 인바운드/아웃바운드 호출 추적

4. Kafka/RabbitMQ 메시지 트레이싱
   → 비동기 메시징에서의 Context 전파
   → Producer → Broker → Consumer 전체 추적
```

---

> **끝.**
> 이 핸드북을 통해 분산 트레이싱의 "왜?"부터 "어떻게?"까지,
> 그리고 "운영 환경에서는?"까지 모두 다루었습니다.
> 실습 코드를 직접 따라하고, 퀴즈를 풀어보면서
> 분산 트레이싱을 자기 것으로 만들어보세요. 🚀
