# 통합 Observability 가이드

### Grafana Tempo · Grafana Mimir · LGTM 스택 통합

> **"로그를 클릭하면 트레이스가, 트레이스를 클릭하면 메트릭이 보이는 세계"**
> 이 핸드북을 마치면 로그 · 메트릭 · 트레이스를 하나의 화면에서 통합 분석할 수 있습니다.

---

# 목차

## PART 1. 왜 통합 Observability인가?
- 1장. Observability의 3대 신호 — Logs, Metrics, Traces
- 2장. 도구를 따로 쓰면 벌어지는 일 — "컨텍스트 스위칭 지옥"
- 3장. LGTM 스택이란 무엇인가?
- 4장. 3대 신호의 연결 — 이것이 핵심이다
- 5장. [퀴즈] 어떤 신호를 먼저 확인해야 하는가?

## PART 2. Grafana Tempo — 분산 트레이싱 저장소
- 6장. 왜 Jaeger 대신 Tempo인가?
- 7장. Tempo의 설계 철학 — "인덱싱 없이 Trace ID로만 검색"
- 8장. Tempo의 내부 구조 — Distributor, Ingester, Compactor, Querier
- 9장. [실습] Tempo 설치 (Docker)
- 10장. Tempo 설정 완전 해부 — tempo-config.yml
- 11장. [실습] Spring Boot 트레이스를 Tempo에 저장하기
- 12장. [실습] Grafana에서 트레이스 검색 및 분석
- 13장. Metrics Generator — 트레이스에서 메트릭 자동 생성
- 14장. Tempo 운영 — 스토리지, 보관, 성능 튜닝
- 15장. [퀴즈] Tempo 설계 문제

## PART 3. Grafana Mimir — 메트릭 장기 저장소
- 16장. Prometheus의 한계 — 왜 장기 저장이 필요한가?
- 17장. Mimir vs Thanos — 어떤 것을 선택해야 하는가?
- 18장. Mimir의 내부 구조 — Write Path, Read Path, Compaction
- 19장. [실습] Mimir 설치 (Docker)
- 20장. Mimir 설정 완전 해부 — mimir-config.yml
- 21장. [실습] Prometheus → Mimir Remote Write 연동
- 22장. [실습] Grafana에서 Mimir 메트릭 조회
- 23장. Mimir 운영 — 스토리지, 보관, 멀티 테넌시
- 24장. [퀴즈] Mimir 설계 문제

## PART 4. LGTM 스택 통합 — 로그 ↔ 트레이스 ↔ 메트릭 연결
- 25장. 통합의 핵심 — Derived Fields, Exemplars, Trace to Logs
- 26장. [실습] 전체 LGTM 스택 docker-compose 한 방에 띄우기
- 27장. [실습] Grafana 데이터소스 통합 프로비저닝
- 28장. [실습] Loki → Tempo 연결 — 로그에서 트레이스로 점프
- 29장. [실습] Tempo → Loki 연결 — 트레이스에서 로그로 점프
- 30장. [실습] Mimir → Tempo 연결 — 메트릭에서 트레이스로 (Exemplars)
- 31장. [실습] 통합 대시보드 만들기 — 한 화면에 3개 신호
- 32장. [실습] 실전 장애 분석 시나리오 — 메트릭→트레이스→로그 순서로

## PART 5. 운영 환경 구성 가이드
- 33장. LGTM 스택 운영 아키텍처 — Object Storage 중심
- 34장. 규모별 리소스 가이드
- 35장. 비용 비교 — ELK+Jaeger+Prometheus vs LGTM
- 36장. 보안 — 멀티 테넌시와 인증
- 37장. [퀴즈] 운영 환경 종합 문제

## PART 6. 마무리
- 38장. Observability 성숙도 자가 진단
- 39장. 전체 핸드북 시리즈 총정리
- 40장. 자주 하는 실수 TOP 10과 체크리스트

> 📌 **다음 단계 — 4번째 신호: Continuous Profiling**
> LGTM(Logs/Metrics/Traces)을 완성한 팀의 다음 목표는 **Continuous Profiling**.
> CPU 사용률이 높은데 어떤 함수가 원인인지 Metrics/Traces로는 알기 어렵다.
> Profiling은 코드 레벨 CPU/메모리 병목을 지속적으로 샘플링해 시각화한다.
> 도구: **Grafana Pyroscope** (Grafana 생태계), **Google Cloud Profiler**, **Parca** (CNCF).

---

# PART 1. 왜 통합 Observability인가?

---

## 1장. Observability의 3대 신호

### 1.1 각 신호의 역할

```
┌─────────────────────────────────────────────────────────────┐
│              Observability의 3대 신호                         │
│                                                              │
│  Metrics (메트릭)        Traces (트레이스)      Logs (로그)   │
│  ──────────────         ──────────────        ────────────   │
│  "얼마나 바쁜가?"        "어디서 느린가?"       "무슨 일이    │
│  "정상인가?"             "어디서 실패했나?"      일어났나?"    │
│                                                              │
│  수치로 측정             요청 흐름 추적          이벤트 기록   │
│  시계열 데이터           Span 타임라인           텍스트 기록   │
│                                                              │
│  예:                     예:                    예:           │
│  - CPU 80%              - 결제서비스 3초       - "결제 실패   │
│  - 에러율 5%            - DB쿼리 2초            orderId: 42  │
│  - 초당 요청 1000       - 전체 5초              reason: 잔액  │
│                                                  부족"        │
│  도구: Prometheus        도구: Jaeger/Tempo     도구: ELK/Loki│
│        Mimir                                                  │
└─────────────────────────────────────────────────────────────┘
```

### 1.2 세 가지 모두 필요한 이유

```
장애 상황: "사용자가 결제가 안 된다고 합니다"

① 메트릭으로 전체 상황 파악 (거시적)
   → 에러율 그래프: 14:25부터 에러율 40% 급증
   → 응답 시간 P95: 14:25부터 5초 → 15초로 증가
   → "14:25에 뭔가 터졌구나"

② 트레이스로 요청 흐름 추적 (미시적)
   → 에러 트레이스 클릭: 결제 서비스에서 DB 쿼리 Span이 10초
   → "DB가 병목이네!"
   → 하지만 왜 DB가 느려졌는지는 트레이스에 안 나옴

③ 로그로 상세 원인 확인 (상세)
   → 해당 Trace ID로 로그 검색
   → "ERROR: Connection pool exhausted. Active: 50/50, Waiting: 200"
   → "DB 커넥션 풀이 고갈되었구나!"
   → 원인: 14:25에 배포된 신규 코드에 커넥션 누수 버그

메트릭만 있으면: "에러가 늘었다" (어디서? 왜? 모름)
트레이스만 있으면: "DB가 느리다" (왜? 모름)
로그만 있으면: "커넥션 풀 에러" (언제부터? 영향 범위는? 모름)

세 가지를 합치면: "14:25 배포 후 커넥션 누수로 DB 커넥션 풀 고갈 → 에러율 40%"
  → 정확한 원인 + 시점 + 영향 범위를 5분 안에 파악
```

---

## 2장. 도구를 따로 쓰면 벌어지는 일

```
기존 방식 (도구 3개 따로 운영):

  메트릭: Prometheus + Grafana    ← 탭 1
  트레이스: Jaeger UI              ← 탭 2
  로그: Kibana                     ← 탭 3

장애 분석 과정:
  1. Grafana에서 에러율 급증 확인
  2. "이 시간대의 트레이스를 보자" → Jaeger UI 탭으로 이동
  3. Jaeger에서 에러 트레이스 발견 → Trace ID 복사
  4. Kibana 탭으로 이동 → Trace ID 붙여넣기 → 검색
  5. 로그 확인... 하지만 시간대가 안 맞아서 다시 필터 조정

문제:
  ❌ 도구 간 컨텍스트 스위칭 → 집중력 저하
  ❌ 수동으로 ID 복사/붙여넣기 → 실수 가능
  ❌ 각 도구의 시간 필터가 다름 → 혼란
  ❌ 3개 시스템을 각각 운영 → 운영 부담 3배
  ❌ 각각 다른 스토리지 → 비용 비효율
```

```
LGTM 방식 (하나의 Grafana에서 모두):

  Grafana 하나에서:
    메트릭 그래프 위의 점(Exemplar) 클릭 → 트레이스로 점프
    트레이스의 Span 클릭 → 해당 시점의 로그로 점프
    로그의 traceId 클릭 → 트레이스로 점프

  ✅ 원클릭 전환 → 분석 속도 10배 향상
  ✅ 컨텍스트 유지 → 시간 필터 자동 동기화
  ✅ 하나의 UI → 러닝 커브 최소화
  ✅ Object Storage 공유 → 비용 절감
```

---

## 3장. LGTM 스택이란 무엇인가?

```
LGTM = Loki + Grafana + Tempo + Mimir

  L = Loki    → 로그 저장/검색
  G = Grafana → 통합 시각화 UI (모든 데이터를 하나의 화면에서)
  T = Tempo   → 트레이스 저장/검색
  M = Mimir   → 메트릭 장기 저장 (Prometheus 호환)

공통 설계 철학:
  1. Object Storage(S3) 기반 → 스토리지 비용 최소화
  2. 수평 확장 가능 → 마이크로서비스 아키텍처
  3. Grafana와 네이티브 통합 → 원클릭 연결
  4. Prometheus 생태계 호환 → 기존 PromQL, AlertManager 재사용
```

---

## 4장. 3대 신호의 연결

```
┌─────────────────────────────────────────────────────────────────┐
│                 3대 신호의 연결 관계                               │
│                                                                   │
│  [Mimir 메트릭]                                                   │
│  에러율 그래프에서                                                 │
│  Exemplar(★) 클릭 ──→ Trace ID를 통해 ──→ [Tempo 트레이스]       │
│                                                                   │
│  [Tempo 트레이스]                                                 │
│  Span 클릭 ──────────→ 해당 시간대/서비스로 ──→ [Loki 로그]      │
│                                                                   │
│  [Loki 로그]                                                      │
│  traceId 필드 클릭 ──→ Trace ID를 통해 ──→ [Tempo 트레이스]      │
│                                                                   │
│  연결의 열쇠:                                                      │
│    Trace ID = 세 신호를 하나로 묶는 공통 식별자                     │
│    Exemplar  = 메트릭 데이터 포인트에 Trace ID를 첨부               │
│    Derived Fields = 로그 텍스트에서 Trace ID를 추출                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 5장. [퀴즈] 어떤 신호를 먼저 확인해야 하는가?

### 문제 1

```
상황: 새벽 3시에 Discord 알림이 울렸습니다.
      "에러율 5% 초과"
      가장 먼저 무엇을 확인해야 하나요?
```

<details>
<summary>정답 보기</summary>

**메트릭 → 트레이스 → 로그 순서로 확인합니다.**
1. Grafana 메트릭 대시보드: 에러율이 언제부터 올랐는지, 어떤 서비스인지 파악 (30초)
2. Exemplar 클릭 → Tempo: 에러 트레이스에서 어느 Span이 실패했는지 확인 (1분)
3. Trace → Logs 클릭 → Loki: 해당 Span의 스택트레이스, 에러 메시지 확인 (1분)
→ 총 2~3분 안에 원인 파악 가능

</details>

### 문제 2

```
상황: 특정 고객이 "내 주문이 안 된다"고 CS 문의.
      X-Trace-Id: abc123def456 을 알려줌.
      가장 먼저 무엇을 확인해야 하나요?
```

<details>
<summary>정답 보기</summary>

**트레이스 → 로그 순서로 확인합니다.** Trace ID를 이미 알고 있으므로 Grafana에서 Tempo로 직접 검색합니다. 트레이스에서 전체 요청 흐름과 실패 지점을 확인하고, 해당 Span의 로그를 Loki에서 확인합니다.

</details>

---

# PART 2. Grafana Tempo — 분산 트레이싱 저장소

---

## 6장. 왜 Jaeger 대신 Tempo인가?

### 6.1 Jaeger의 비용 문제

```
Jaeger의 스토리지 옵션:
  1. Memory → 재시작 시 데이터 삭제 (개발용)
  2. Badger → 로컬 디스크 (소규모)
  3. Elasticsearch → 안정적이지만 비쌈 ★
  4. Cassandra → 대규모에 적합하지만 운영 복잡

실제 비용 (Jaeger + ES, 일 1000만 트레이스):
  Span 데이터: ~50GB/일
  ES 인덱스 포함: ~100GB/일
  30일 보관: 3TB (SSD)
  월 비용: ~$500~800
```

### 6.2 Tempo의 접근

```
Tempo의 스토리지:
  Object Storage(S3, GCS, MinIO) ← 오직 이것만!

Tempo 비용 (동일 조건):
  Span 데이터: ~50GB/일
  압축 저장: ~20GB/일
  30일 보관: 600GB (S3)
  월 비용: ~$15 (S3 스토리지) + ~$50 (Tempo 컴퓨팅)
  총: ~$65/월

비교:
  Jaeger + ES: ~$650/월
  Tempo + S3:  ~$65/월
  → 약 90% 절감!
```

### 6.3 Jaeger vs Tempo 비교표

| 항목 | Jaeger | Tempo |
|------|--------|-------|
| 스토리지 | ES, Cassandra, Badger | S3, GCS, Azure Blob |
| 인덱싱 | 서비스명, 태그 등으로 검색 | Trace ID로만 검색 |
| 비용 (일 50GB) | ~$650/월 | ~$65/월 |
| 검색 방식 | 서비스/태그/시간으로 필터 | Trace ID 직접 검색 |
| Grafana 통합 | 별도 UI (Jaeger UI) | Grafana 네이티브 |
| 트레이스 발견 | Jaeger UI에서 직접 | Loki/Mimir에서 Trace ID로 연결 |
| Metrics Generator | 없음 | 트레이스에서 RED 메트릭 자동 생성 |

```
★ 핵심 차이: "트레이스를 어떻게 찾는가?"

Jaeger: Jaeger UI에서 서비스/태그/시간으로 직접 검색
  → 인덱싱 필요 → 스토리지 비용 증가

Tempo: Trace ID로만 검색 (인덱싱 없음)
  → "그러면 Trace ID를 어떻게 알아?"
  → Loki 로그에서 traceId 필드 클릭 → Tempo로 점프
  → Mimir 메트릭의 Exemplar 클릭 → Tempo로 점프
  → "트레이스를 직접 검색하지 않고, 로그/메트릭에서 연결한다"
  → 이것이 LGTM 통합의 핵심 철학!
```

---

## 7장. Tempo의 설계 철학

```
"Like Prometheus, but for traces"

Prometheus: 메트릭을 라벨로 인덱싱, 시계열 데이터
Loki:       로그를 라벨로만 인덱싱, 본문은 grep
Tempo:      트레이스를 인덱싱하지 않음, Trace ID로만 접근

Tempo의 3가지 원칙:
  1. 인덱싱 없음 → 스토리지 비용 최소화
  2. Trace ID가 유일한 검색 키 → 단순한 설계
  3. Object Storage → 무한 확장, 저비용

"그런데 Trace ID 없이는 어떻게 찾나요?"
  → TraceQL 검색 (Tempo 2.0+): 서비스명, Span 속성으로 검색 가능!
  → Metrics Generator: 트레이스에서 자동으로 RED 메트릭 생성
  → 즉, "인덱싱 없이도 찾을 수 있는 방법"이 점점 추가되고 있음
```

---

## 8장. Tempo의 내부 구조

```
┌─────────────────────────────────────────────────────────────┐
│                    Tempo Architecture                         │
│                                                               │
│  [App] ── OTLP ──→ ┌─────────────┐                          │
│                     │ Distributor │ ← 트레이스 수신 + 검증    │
│                     └──────┬──────┘                          │
│                            │                                  │
│                            ▼                                  │
│                     ┌─────────────┐                          │
│                     │  Ingester   │ ← 메모리에 버퍼링         │
│                     │             │   WAL에 기록              │
│                     └──────┬──────┘                          │
│                            │ (블록 생성)                      │
│                            ▼                                  │
│                     ┌─────────────┐                          │
│                     │  Object     │ ← S3/GCS에 블록 저장     │
│                     │  Storage    │                          │
│                     └──────┬──────┘                          │
│                            │                                  │
│                            ▼                                  │
│                     ┌─────────────┐                          │
│                     │ Compactor   │ ← 작은 블록을 큰 블록으로 │
│                     │             │   합쳐서 검색 효율 향상   │
│                     └─────────────┘                          │
│                                                               │
│  [Grafana] ─ 검색 ─→ ┌─────────────┐                        │
│                       │  Querier    │ ← Trace ID로 블록 검색  │
│                       └─────────────┘                        │
│                                                               │
│  [선택] Metrics Generator:                                    │
│  Ingester가 받은 트레이스에서 자동으로 메트릭 생성              │
│  → span_duration, request_count 등                            │
│  → Prometheus Remote Write로 Mimir에 저장                     │
└─────────────────────────────────────────────────────────────┘
```

---

## 9장. [실습] Tempo 설치

```yaml
# docker-compose.yml
  tempo:
    image: grafana/tempo:2.3.1
    container_name: tempo
    command: ["-config.file=/etc/tempo/tempo-config.yml"]
    volumes:
      - ./tempo/tempo-config.yml:/etc/tempo/tempo-config.yml:ro
      - tempo-data:/tmp/tempo
    ports:
      - "3200:3200"     # Tempo API (Grafana가 여기로 쿼리)
      - "4317:4317"     # OTLP gRPC (앱/Collector가 여기로 전송)
      - "4318:4318"     # OTLP HTTP
    networks:
      - observability
```

---

## 10장. Tempo 설정 완전 해부

```yaml
# docker/tempo/tempo-config.yml

# ═══ 서버 ═══
server:
  http_listen_port: 3200

# ═══ Distributor: 트레이스 수신 ═══
distributor:
  receivers:
    otlp:
      protocols:
        grpc:
          endpoint: "0.0.0.0:4317"    # 앱에서 OTLP gRPC로 전송
        http:
          endpoint: "0.0.0.0:4318"    # 또는 OTLP HTTP로 전송

# ═══ Ingester: 메모리 버퍼링 + WAL ═══
ingester:
  max_block_duration: 5m              # 5분마다 블록 생성 → Object Storage로 플러시
  max_block_bytes: 524288000          # 블록 최대 500MB
  trace_idle_period: 30s              # 30초간 새 Span이 없으면 트레이스 완료 간주

# ═══ Storage: 어디에 저장할 것인가 ═══
storage:
  trace:
    backend: local                     # 개발: local / 운영: s3, gcs, azure
    local:
      path: /tmp/tempo/blocks
    wal:
      path: /tmp/tempo/wal

    # ── 운영 환경: S3 ──
    # backend: s3
    # s3:
    #   bucket: your-tempo-bucket
    #   endpoint: s3.ap-northeast-2.amazonaws.com
    #   region: ap-northeast-2
    #   access_key: ${AWS_ACCESS_KEY_ID}
    #   secret_key: ${AWS_SECRET_ACCESS_KEY}

    # ── 블록 풀 설정 ──
    pool:
      max_workers: 100                 # 병렬 검색 워커 수
      queue_depth: 10000

# ═══ Compactor: 블록 압축 + 보관 ═══
compactor:
  compaction:
    block_retention: 744h              # ★ 31일 보관 (이후 자동 삭제)
    compacted_block_retention: 1h      # 압축된 원본 블록 임시 보관
    compaction_window: 1h              # 1시간 단위로 블록 합치기

# ═══ Metrics Generator: 트레이스에서 메트릭 자동 생성 ═══
metrics_generator:
  registry:
    external_labels:
      source: tempo
      cluster: local
  storage:
    path: /tmp/tempo/generator/wal
    remote_write:
      - url: http://mimir:9009/api/v1/push    # Mimir로 메트릭 전송
        send_exemplars: true                    # ★ Exemplar 포함!

# ═══ Overrides: 리소스 제한 ═══
overrides:
  defaults:
    metrics_generator:
      processors: [service-graphs, span-metrics]  # 활성화할 메트릭 종류
      # service-graphs: 서비스 간 호출 관계 메트릭
      # span-metrics:   Span 기반 RED 메트릭

# ═══ TraceQL 검색 활성화 ═══
query_frontend:
  search:
    duration_slo: 5s                   # 검색 SLO: 5초 이내 응답
    max_duration: 0                    # 0 = 무제한
```

### 10.2 핵심 설정 해설

```
Q: max_block_duration: 5m 은 뭔가요?
→ Ingester가 5분마다 메모리의 트레이스를 Object Storage에 블록으로 저장합니다.
  짧으면: 저장 빈번 → S3 PUT 비용 증가, 하지만 메모리 사용 적음
  길면:   저장 드묾 → S3 비용 절감, 하지만 메모리 사용 증가

Q: trace_idle_period: 30s 는?
→ 30초간 새 Span이 안 오면 "이 트레이스는 끝났다"고 간주합니다.
  마이크로서비스 호출이 30초 이상 걸리면 값을 늘려야 합니다.

Q: metrics_generator의 processors는?
→ service-graphs: 어떤 서비스가 어떤 서비스를 호출하는지 자동 시각화
→ span-metrics: 서비스별 요청 수(Rate), 에러 수(Error), 응답 시간(Duration)
   = RED 메트릭을 트레이스에서 자동 생성
   = Prometheus/Mimir에 저장되어 Grafana에서 바로 사용 가능
```

---

## 11장. [실습] Spring Boot 트레이스를 Tempo에 저장하기

### 11.1 Spring Boot 설정

```yaml
# application.yml
management:
  tracing:
    sampling:
      probability: 1.0
  otlp:
    tracing:
      endpoint: http://localhost:4317    # Tempo의 OTLP gRPC
```

### 11.2 테스트

```bash
# Tempo 시작
docker compose up -d

# Spring Boot 앱 시작 후 요청
curl -X POST http://localhost:8080/api/waiting \
  -H "Content-Type: application/json" \
  -d '{"userId": 1, "restaurantId": 100}'

# Tempo API로 직접 확인
curl "http://localhost:3200/api/traces/{TRACE_ID}" | python3 -m json.tool

# Grafana → Explore → Tempo 데이터소스
# → TraceQL: {} 입력 → 최근 트레이스 목록 확인
```

---

## 12장. [실습] Grafana에서 트레이스 검색

### 12.1 TraceQL 쿼리 문법

```
TraceQL = Tempo 2.0+에서 지원하는 트레이스 검색 언어

# 기본 검색 (최근 트레이스 목록)
{}

# 특정 서비스의 트레이스
{ resource.service.name = "waiting-service" }

# 에러가 포함된 트레이스
{ status = error }

# 3초 이상 걸린 Span이 있는 트레이스
{ duration > 3s }

# 특정 HTTP 상태 코드
{ span.http.status_code = 500 }

# 조합
{ resource.service.name = "waiting-service" && status = error && duration > 1s }

# 특정 Trace ID로 직접 검색 (Grafana UI에서)
# Search 탭 → Trace ID 입력
```

### 12.2 Grafana에서 확인할 것

```
Grafana → Explore → Tempo

1. TraceQL로 검색: { resource.service.name = "waiting-service" }
2. 트레이스 목록에서 하나 클릭
3. Span 타임라인 확인:
   waiting-service: POST /api/waiting        ──────── 250ms
     ├── register-waiting                      ────── 220ms
     │     └── HTTP POST /notification            ── 150ms
     │           └── notification: POST /notify     ── 120ms
     └── 완료

4. 각 Span 클릭 → Tags 확인:
   http.method: POST
   http.status_code: 200
   http.url: /api/waiting

5. Node Graph 탭 → 서비스 간 호출 관계 시각화
```

---

## 13장. Metrics Generator — 트레이스에서 메트릭 자동 생성

```
★ Tempo의 킬러 기능!

기존 방식:
  메트릭을 별도로 코드에서 생성 (Micrometer Counter, Timer 등)
  → 개발자가 직접 코드를 작성해야 함

Metrics Generator:
  트레이스 데이터에서 자동으로 메트릭 생성
  → 코드 변경 없이 RED 메트릭을 얻을 수 있음!

자동 생성되는 메트릭:
  traces_spanmetrics_calls_total
    → 서비스별/엔드포인트별 요청 수
  traces_spanmetrics_latency_bucket  
    → 서비스별/엔드포인트별 응답 시간 분포
  traces_service_graph_request_total
    → 서비스 A → 서비스 B 호출 수

Grafana에서 활용:
  이 메트릭으로 대시보드를 만들면
  → 별도 코드 없이 서비스별 성능 대시보드 완성!
  → Exemplar가 포함되어 있으므로 메트릭 → 트레이스 점프 가능!
```

---

## 14장. Tempo 운영

```
스토리지 계산 (일 1000만 트레이스, 평균 5 Span):
  원본: ~50GB/일
  Tempo 압축 후: ~20GB/일
  30일 보관: ~600GB (S3)
  S3 비용: ~$15/월

보관 기간:
  compactor.compaction.block_retention: 744h (31일)
  → 31일이 지난 블록은 자동 삭제

성능 튜닝:
  ingester.max_block_duration: 5m   → 메모리 vs I/O 트레이드오프
  compactor.compaction_window: 1h   → 블록 크기 최적화
  query_frontend.search.max_duration → 검색 타임아웃

이중화:
  Tempo를 2+ 인스턴스로 운영
  memberlist 또는 consul로 클러스터링
  ingester replication_factor: 3
```

---

## 15장. [퀴즈] Tempo 설계 문제

### 문제 1

```
"Tempo에서 서비스명으로 트레이스를 검색하고 싶은데,
 인덱싱이 없다고 했잖아요. 어떻게 하나요?"
```

<details>
<summary>정답 보기</summary>

3가지 방법이 있습니다. 첫째, **TraceQL** (Tempo 2.0+)로 `{ resource.service.name = "waiting-service" }`를 검색합니다. 인덱싱 없이 블록을 스캔하므로 ES보다 느리지만 실용적입니다. 둘째, **Loki 로그에서 연결**합니다. `{service="waiting-service", level="ERROR"}`로 로그를 검색하고 traceId 필드를 클릭하면 Tempo로 점프합니다. 셋째, **Metrics Generator**가 만든 메트릭에서 Exemplar를 클릭합니다.

</details>

---

# PART 3. Grafana Mimir — 메트릭 장기 저장소

---

## 16장. Prometheus의 한계

```
Prometheus의 장점:
  ✅ Pull 기반 수집 → 간단
  ✅ PromQL → 강력한 쿼리
  ✅ AlertManager → 알림
  ✅ 생태계가 거대

Prometheus의 한계:
  ❌ 로컬 디스크에만 저장
     → 디스크 용량 = 보관 기간 (보통 15일~1개월)
     → "3개월 전 메트릭과 비교하고 싶다" → 불가능
  
  ❌ 단일 인스턴스
     → Prometheus 서버가 죽으면 수집 중단
     → HA 구성이 복잡 (2대 운영 시 데이터 중복)
  
  ❌ 수평 확장 불가
     → 시계열 수가 수백만 개면 메모리 부족
     → 서비스가 많아지면 한 대로 감당 불가

해결: Remote Write로 장기 저장소에 메트릭을 전송
  Prometheus ── remote_write ──→ Mimir (또는 Thanos)
```

---

## 17장. Mimir vs Thanos

```
Thanos (2018):
  - Prometheus HA + 장기 저장을 위해 탄생
  - Prometheus 옆에 Sidecar를 붙이는 방식
  - 기존 Prometheus를 최소한으로 변경
  
Mimir (2022):
  - Grafana Labs가 Cortex를 포크하여 만듦
  - Prometheus를 완전히 대체하는 중앙 집중식 저장소
  - Remote Write로 메트릭을 수신
  - 더 높은 성능과 멀티 테넌시 지원

비교:
| 항목 | Thanos | Mimir |
|------|--------|-------|
| 접근 방식 | Prometheus Sidecar | 독립 서비스 |
| 설치 방식 | 기존 Prometheus에 추가 | Prometheus와 별도 |
| 멀티 테넌시 | 제한적 | 네이티브 지원 |
| 성능 | 좋음 | 매우 좋음 (10배+) |
| Grafana 통합 | 좋음 | 네이티브 |
| 운영 복잡도 | 중간 (컴포넌트 많음) | 낮음 (monolithic 모드) |

선택 가이드:
  이미 Thanos를 쓰고 있다면 → Thanos 유지
  새로 구축한다면 → Mimir 권장 (더 간단, 더 빠름, LGTM 통합)
```

---

## 18장. Mimir의 내부 구조

```
┌─────────────────────────────────────────────────────────────┐
│                     Mimir Architecture                        │
│                                                               │
│  [Prometheus]                                                 │
│   │ remote_write                                              │
│   ▼                                                           │
│  ┌─────────────┐                                             │
│  │ Distributor │ ← 메트릭 수신, 샤딩, Ingester로 분배        │
│  └──────┬──────┘                                             │
│         ▼                                                     │
│  ┌─────────────┐                                             │
│  │  Ingester   │ ← 메모리에 최근 메트릭 보관 (2시간)          │
│  │             │   WAL에 기록 (크래시 보호)                   │
│  └──────┬──────┘                                             │
│         │ (2시간마다 블록 생성)                                │
│         ▼                                                     │
│  ┌─────────────┐                                             │
│  │  Object     │ ← S3에 TSDB 블록 저장 (장기 보관)           │
│  │  Storage    │                                             │
│  └──────┬──────┘                                             │
│         ▼                                                     │
│  ┌─────────────┐                                             │
│  │ Compactor   │ ← 블록 합치기 + 다운샘플링                  │
│  └─────────────┘                                             │
│                                                               │
│  [Grafana]                                                    │
│   │ PromQL 쿼리                                               │
│   ▼                                                           │
│  ┌──────────────┐     ┌──────────────┐                       │
│  │Query Frontend│ ──→ │   Querier    │ ← Ingester + Storage  │
│  │ (쿼리 분할)  │     │ (쿼리 실행)  │   에서 데이터 조합     │
│  └──────────────┘     └──────────────┘                       │
└─────────────────────────────────────────────────────────────┘

★ 핵심: Mimir는 PromQL을 그대로 사용!
  기존 Prometheus 대시보드를 변경 없이 Mimir에서 실행 가능
```

---

## 19장. [실습] Mimir 설치

```yaml
# docker-compose.yml
  mimir:
    image: grafana/mimir:2.11.0
    container_name: mimir
    command: ["-config.file=/etc/mimir/mimir-config.yml"]
    volumes:
      - ./mimir/mimir-config.yml:/etc/mimir/mimir-config.yml:ro
      - mimir-data:/data
    ports:
      - "9009:9009"     # Mimir API
    networks:
      - observability
```

---

## 20장. Mimir 설정 완전 해부

```yaml
# docker/mimir/mimir-config.yml

# 단일 인스턴스 모드 (개발/소규모)
# 운영 환경에서는 마이크로서비스 모드 사용
target: all

server:
  http_listen_port: 9009
  grpc_listen_port: 9095
  log_level: warn

# ═══ 멀티 테넌시 ═══
multitenancy_enabled: false        # 단일 테넌트 (true면 X-Scope-OrgID 헤더 필요)

# ═══ Distributor ═══
distributor:
  ring:
    instance_addr: 127.0.0.1
    kvstore:
      store: memberlist

# ═══ Ingester ═══
ingester:
  ring:
    instance_addr: 127.0.0.1
    kvstore:
      store: memberlist
    replication_factor: 1          # 개발: 1 / 운영: 3

# ═══ Blocks Storage (메트릭 장기 저장) ═══
blocks_storage:
  backend: filesystem              # 개발: filesystem / 운영: s3, gcs
  filesystem:
    dir: /data/blocks
  tsdb:
    dir: /data/tsdb
    block_ranges_period: [2h]      # 2시간마다 블록 생성
    retention_period: 0            # 0 = limits_config에서 관리
  bucket_store:
    sync_dir: /data/tsdb-sync

  # ── 운영 환경: S3 ──
  # backend: s3
  # s3:
  #   bucket_name: your-mimir-bucket
  #   endpoint: s3.ap-northeast-2.amazonaws.com
  #   region: ap-northeast-2

# ═══ Compactor ═══
compactor:
  data_dir: /data/compactor
  sharding_ring:
    kvstore:
      store: memberlist

# ═══ Limits ═══
limits:
  # ★ 메트릭 보관 기간
  compactor_blocks_retention_period: 8760h   # 365일 (1년)
  
  # 시계열 제한 (카디널리티 폭발 방지)
  max_global_series_per_user: 1500000        # 최대 시계열 150만 개
  max_global_series_per_metric: 100000       # 메트릭당 최대 10만 시계열
  
  # 수집 속도 제한
  ingestion_rate: 50000                       # 초당 최대 5만 샘플
  ingestion_burst_size: 500000                # 버스트 허용량

# ═══ Memberlist (단일 인스턴스에서는 형식적) ═══
memberlist:
  join_members: []

# ═══ Activity Tracker ═══
activity_tracker:
  filepath: /data/activity.log
```

### 20.2 핵심 설정 해설

```
Q: compactor_blocks_retention_period는?
→ 메트릭 보관 기간. 8760h = 1년.
  Prometheus 기본 보관(15일)보다 훨씬 길게 보관 가능.
  S3에 저장하므로 비용 부담이 적음.

Q: max_global_series_per_user는?
→ 시계열(time series) 수 제한. 카디널리티 폭발 방지.
  시계열 = 메트릭명 + 라벨 조합의 고유 수
  예: http_requests_total{method="GET", uri="/api/waiting", status="200"}
      = 1개의 시계열
  서비스 10개 × 엔드포인트 50개 × 상태코드 5개 = 2,500 시계열 (안전)
  + userId 라벨 추가 → 100만 유저 × 2,500 = 25억 시계열 (폭발!)

Q: ingestion_rate는?
→ 초당 최대 수집 샘플 수. 초과하면 429 Too Many Requests.
  Prometheus scrape_interval=15s, 시계열 10만 개
  → 15초마다 10만 샘플 = 초당 ~6,700 샘플
  → ingestion_rate: 50000이면 넉넉
```

---

## 21장. [실습] Prometheus → Mimir Remote Write 연동

```yaml
# docker/prometheus/prometheus.yml

global:
  scrape_interval: 15s

# ★ Remote Write: Mimir로 메트릭 장기 저장
remote_write:
  - url: http://mimir:9009/api/v1/push
    queue_config:
      max_samples_per_send: 1000
      max_shards: 200
      capacity: 2500

scrape_configs:
  - job_name: 'spring-boot-apps'
    metrics_path: '/actuator/prometheus'
    scrape_interval: 15s
    static_configs:
      - targets: ['host.docker.internal:8080']
        labels:
          application: 'waiting-service'
```

```bash
# 확인
curl "http://localhost:9009/prometheus/api/v1/query?query=up"
# → Prometheus와 동일한 응답 형식!
```

---

## 22장. [실습] Grafana에서 Mimir 메트릭 조회

```
Grafana → Connections → Data Sources → Add → Prometheus

Name: Mimir
URL: http://mimir:9009/prometheus     # ★ /prometheus 경로 필수!
Save & Test → "Data source is working"

Explore에서:
  PromQL: rate(http_server_requests_seconds_count[5m])
  → Prometheus와 완전히 동일한 PromQL!
  → 기존 대시보드를 그대로 Mimir에서 사용 가능
```

---

## 23장. Mimir 운영

```
스토리지 계산:
  시계열 10만 개, scrape_interval 15s
  → 초당 ~6,700 샘플
  → 일 ~580M 샘플
  → TSDB 블록 크기: ~5GB/일
  → 1년: ~1.8TB (S3)
  → S3 비용: ~$45/월

성능:
  Query Frontend가 긴 쿼리를 자동으로 분할
  예: 1년치 쿼리 → 24시간 단위로 분할 → 병렬 실행 → 결과 합산
  → 사용자는 느끼지 못함

HA (고가용성):
  Ingester replication_factor: 3
  → 3개의 Ingester에 동일한 메트릭 복제
  → 1대가 죽어도 데이터 손실 없음
```

---

## 24장. [퀴즈] Mimir 설계 문제

### 문제 1

```
Prometheus에서 remote_write로 Mimir에 메트릭을 전송 중입니다.
Mimir가 5분간 다운되면 메트릭이 유실되나요?
```

<details>
<summary>정답 보기</summary>

**유실되지 않습니다.** Prometheus는 remote_write 실패 시 내부 WAL(Write-Ahead Log)에 메트릭을 보관하고, Mimir가 복구되면 밀린 메트릭을 재전송합니다. Prometheus의 WAL은 기본적으로 2시간 분량을 보관하므로, 5분 정도의 다운타임은 문제 없습니다.

</details>

---

# PART 4. LGTM 스택 통합

---

## 25장. 통합의 핵심 — Derived Fields, Exemplars, Trace to Logs

```
연결 메커니즘 3가지:

1. Derived Fields (Loki → Tempo)
   Loki 로그에서 traceId 패턴을 자동 인식
   → 클릭하면 Tempo에서 해당 트레이스 조회
   설정 위치: Grafana의 Loki 데이터소스 설정

2. Trace to Logs (Tempo → Loki)
   Tempo 트레이스의 Span을 클릭하면
   → 해당 서비스, 해당 시간대의 Loki 로그로 점프
   설정 위치: Grafana의 Tempo 데이터소스 설정

3. Exemplars (Mimir → Tempo)
   Mimir 메트릭 그래프 위의 점(★)을 클릭하면
   → 해당 시점의 트레이스로 점프
   설정 위치: Grafana의 Prometheus/Mimir 데이터소스 설정
   조건: Tempo의 Metrics Generator가 활성화되어 있어야 함
```

---

## 26장. [실습] 전체 LGTM 스택 docker-compose

```yaml
# docker/docker-compose-lgtm.yml
# version: '3.8'  # Docker Compose V2 이후 deprecated — 생략 가능

services:
  # ═══ Grafana Loki (로그) ═══
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
      - observability

  # ═══ Grafana Tempo (트레이스) ═══
  tempo:
    image: grafana/tempo:2.3.1
    container_name: tempo
    command: ["-config.file=/etc/tempo/tempo-config.yml"]
    volumes:
      - ./tempo/tempo-config.yml:/etc/tempo/tempo-config.yml:ro
      - tempo-data:/tmp/tempo
    ports:
      - "3200:3200"
      - "4317:4317"
      - "4318:4318"
    depends_on:
      - mimir
    networks:
      - observability

  # ═══ Grafana Mimir (메트릭) ═══
  mimir:
    image: grafana/mimir:2.11.0
    container_name: mimir
    command: ["-config.file=/etc/mimir/mimir-config.yml"]
    volumes:
      - ./mimir/mimir-config.yml:/etc/mimir/mimir-config.yml:ro
      - mimir-data:/data
    ports:
      - "9009:9009"
    networks:
      - observability

  # ═══ Prometheus (수집 + Mimir 전송) ═══
  prometheus:
    image: prom/prometheus:v2.49.0
    container_name: prometheus
    volumes:
      - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro
    ports:
      - "9090:9090"
    extra_hosts:
      - "host.docker.internal:host-gateway"
    networks:
      - observability

  # ═══ Promtail (로그 수집) ═══
  promtail:
    image: grafana/promtail:2.9.4
    container_name: promtail
    command: -config.file=/etc/promtail/promtail-config.yml
    volumes:
      - ./promtail/promtail-config.yml:/etc/promtail/promtail-config.yml:ro
      - ../logs:/var/log/app:ro
    depends_on:
      - loki
    networks:
      - observability

  # ═══ Grafana (통합 UI) ═══
  grafana:
    image: grafana/grafana:10.3.0
    container_name: grafana
    environment:
      - GF_SECURITY_ADMIN_USER=admin
      - GF_SECURITY_ADMIN_PASSWORD=admin
      - GF_FEATURE_TOGGLES_ENABLE=traceQLStreaming,traceToMetrics
    ports:
      - "3000:3000"
    volumes:
      - grafana-data:/var/lib/grafana
      - ./grafana/provisioning:/etc/grafana/provisioning:ro
    depends_on:
      - loki
      - tempo
      - mimir
    networks:
      - observability

volumes:
  loki-data:
  tempo-data:
  mimir-data:
  grafana-data:

networks:
  observability:
    driver: bridge
```

---

## 27장. [실습] Grafana 데이터소스 통합 프로비저닝

```yaml
# docker/grafana/provisioning/datasources/datasources.yml
apiVersion: 1

datasources:
  # ═══ Loki (로그) ═══
  - name: Loki
    type: loki
    uid: loki
    access: proxy
    url: http://loki:3100
    isDefault: false
    jsonData:
      # ★ Derived Fields: 로그의 traceId를 Tempo로 연결
      derivedFields:
        - datasourceUid: tempo
          # JSON 로그에서 traceId 추출
          matcherRegex: '"traceId"\s*:\s*"(\w+)"'
          name: TraceID
          url: '$${__value.raw}'
          urlDisplayLabel: 'View Trace in Tempo'
        - datasourceUid: tempo
          # Plain text 로그에서 traceId 추출
          matcherRegex: 'traceId=(\w+)'
          name: TraceID
          url: '$${__value.raw}'
          urlDisplayLabel: 'View Trace in Tempo'

  # ═══ Tempo (트레이스) ═══
  - name: Tempo
    type: tempo
    uid: tempo
    access: proxy
    url: http://tempo:3200
    isDefault: false
    jsonData:
      # ★ Trace to Logs: 트레이스에서 Loki 로그로 점프
      tracesToLogsV2:
        datasourceUid: loki
        spanStartTimeShift: '-5m'       # Span 시작 5분 전부터
        spanEndTimeShift: '5m'          # Span 종료 5분 후까지
        filterByTraceID: true           # traceId로 필터링
        filterBySpanID: false
        tags:
          - key: service.name
            value: service              # Loki 라벨 service로 매핑

      # ★ Trace to Metrics: 트레이스에서 메트릭으로 점프
      tracesToMetrics:
        datasourceUid: mimir
        spanStartTimeShift: '-5m'
        spanEndTimeShift: '5m'
        tags:
          - key: service.name
            value: service

      # Node Graph (서비스 호출 관계 시각화)
      nodeGraph:
        enabled: true

      # TraceQL 검색 활성화
      search:
        hide: false

      # Service Graph (Metrics Generator 기반)
      serviceMap:
        datasourceUid: mimir

  # ═══ Mimir (메트릭) ═══
  - name: Mimir
    type: prometheus
    uid: mimir
    access: proxy
    url: http://mimir:9009/prometheus
    isDefault: true
    jsonData:
      # ★ Exemplars: 메트릭에서 트레이스로 점프
      exemplarTraceIdDestinations:
        - datasourceUid: tempo
          name: traceID
      httpMethod: POST

  # ═══ Prometheus (직접 조회용, 단기 데이터) ═══
  - name: Prometheus
    type: prometheus
    uid: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: false
```

### 27.2 프로비저닝 설정 핵심 설명

```
Loki → Tempo (Derived Fields):
  matcherRegex: '"traceId"\s*:\s*"(\w+)"'
  → JSON 로그에서 "traceId": "abc123" 패턴을 인식
  → 괄호 안의 값(abc123)을 추출
  → Tempo 링크 생성: 클릭하면 Tempo에서 abc123 조회

Tempo → Loki (tracesToLogsV2):
  spanStartTimeShift: '-5m'
  → Span 시작 시간 5분 전부터 로그 검색
  → 왜? Span이 시작되기 직전의 로그도 보고 싶을 수 있으므로
  
  filterByTraceID: true
  → Loki에서 traceId 필터를 자동 적용
  → {service="waiting-service"} | traceId = "abc123"

Mimir → Tempo (Exemplars):
  exemplarTraceIdDestinations:
    → 메트릭 그래프의 Exemplar(★) 점에서 traceID를 추출
    → 클릭하면 Tempo에서 해당 트레이스 조회
```

---

## 28장. [실습] Loki → Tempo 연결 확인

```
1. Grafana → Explore → Loki 선택
2. 쿼리: {service="waiting-service", level="ERROR"}
3. 로그 결과에서 traceId가 포함된 줄 확인
4. traceId 옆에 파란색 링크 "View Trace in Tempo" 클릭 ★
5. → 자동으로 Tempo Explore로 전환됨!
6. → 해당 Trace의 전체 Span 타임라인이 표시됨

동작 원리:
  Loki 로그: {"traceId": "abc123", "message": "결제 실패"}
                         ↑
                 Derived Fields가 이 값을 인식
                         ↓
                 Tempo 링크 생성: tempo/trace/abc123
```

---

## 29장. [실습] Tempo → Loki 연결 확인

```
1. Grafana → Explore → Tempo 선택
2. TraceQL: { resource.service.name = "waiting-service" && status = error }
3. 에러 트레이스 클릭 → Span 타임라인 표시
4. 특정 Span 클릭 → "Logs for this span" 링크 클릭 ★
5. → 자동으로 Loki Explore로 전환됨!
6. → 해당 서비스, 해당 시간대, 해당 traceId의 로그가 표시됨

쿼리가 자동 생성됨:
  {service="waiting-service"} | traceId = "abc123"
  시간 범위: Span 시작 -5분 ~ Span 종료 +5분
```

---

## 30장. [실습] Mimir → Tempo 연결 확인 (Exemplars)

```
전제: Tempo의 Metrics Generator가 활성화되어 있어야 함

1. Grafana → Explore → Mimir 선택
2. PromQL: histogram_quantile(0.95, sum(rate(traces_spanmetrics_latency_bucket[5m])) by (le))
3. 그래프에 작은 점(★ Exemplar)이 표시됨
4. Exemplar 점 클릭 ★
5. → 자동으로 Tempo로 전환!
6. → 해당 시점의 실제 트레이스가 표시됨

의미:
  "P95 응답 시간이 3초였다"는 메트릭에서
  "실제로 3초 걸린 그 요청"의 트레이스를 바로 확인 가능
  → 통계(메트릭)에서 개별 사례(트레이스)로 점프
```

---

## 31장. [실습] 통합 대시보드 만들기

```
Grafana → Dashboard → New Dashboard

━━━ 상단: 메트릭 개요 (Mimir) ━━━

패널 1: 에러율 게이지 (Mimir)
  sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m]))
  / sum(rate(http_server_requests_seconds_count[5m])) * 100

패널 2: P95 응답시간 (Mimir)
  histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))

패널 3: 초당 요청 수 (Mimir)
  sum(rate(http_server_requests_seconds_count[5m])) by (uri)

━━━ 중단: 트레이스 개요 (Tempo) ━━━

패널 4: 서비스 맵 (Tempo - Node Graph)
  → Tempo 데이터소스, Service Graph 유형 선택
  → 서비스 간 호출 관계가 자동으로 시각화됨

━━━ 하단: 로그 (Loki) ━━━

패널 5: 최근 에러 로그 (Loki)
  {service=~".+", level="ERROR"} | json
  Visualization: Logs

패널 6: 시간대별 로그 레벨 분포 (Loki)
  sum(rate({service=~".+"}[5m])) by (level)
```

---

## 32장. [실습] 실전 장애 분석 시나리오

```
시나리오: "오후 2시부터 결제 성공률이 60%로 떨어졌습니다"

━━━ 1단계: 메트릭으로 전체 상황 파악 (30초) ━━━

Grafana 대시보드 확인:
  에러율: 14:00에 5% → 14:02부터 40%로 급증
  P95 응답시간: 14:02부터 200ms → 5초로 증가
  서비스: payment-service에서 에러 집중

→ "14:02에 payment-service에 문제 발생!"

━━━ 2단계: 트레이스로 병목 파악 (1분) ━━━

대시보드에서 에러율 그래프의 Exemplar(★) 클릭
→ Tempo에서 에러 트레이스 표시:

  waiting-service: POST /api/waiting         ─── 5200ms
    ├── register                               ── 50ms
    └── HTTP POST /payment                        ── 5100ms (★ 병목!)
          └── payment-service: processPayment        ── 5050ms
                └── HTTP POST https://pg.example.com    ── 5000ms (★ 타임아웃!)

→ "PG사 API 호출이 5초 타임아웃!"

━━━ 3단계: 로그로 정확한 원인 확인 (1분) ━━━

Tempo에서 payment-service Span 클릭 → "Logs for this span"
→ Loki로 자동 전환:

  14:02:05 ERROR payment-service - PG 결제 요청 실패
    java.net.SocketTimeoutException: Read timed out
    target: https://pg.example.com/v1/pay
    connectionTimeout: 3s, readTimeout: 5s

  14:02:10 WARN payment-service - PG 재시도 1/3
  14:02:15 ERROR payment-service - PG 재시도 실패 2/3

→ "PG사 서버가 응답하지 않아 타임아웃 발생!"

━━━ 결론 (총 2분 30초) ━━━

원인: 14:02부터 PG사(pg.example.com) 서버 장애
영향: payment-service 타임아웃 → 결제 성공률 60%
조치: PG사에 연락 + 대체 PG 전환 검토

━━━ 이전 방식이었다면? ━━━
Grafana → Jaeger UI(탭 이동) → Trace ID 복사 → Kibana(탭 이동)
→ 시간 필터 수동 조정 → 로그 검색
→ 총 10~15분 소요 + 컨텍스트 스위칭으로 집중력 저하
```

---

# PART 5. 운영 환경 구성 가이드

---

## 33장. LGTM 스택 운영 아키텍처

```
┌───────────────────────────────────────────────────────────────┐
│                 프로덕션 LGTM 아키텍처                         │
│                                                                │
│  [App Cluster]                                                 │
│  ┌──────────┐                                                  │
│  │ App #1~N │── OTLP ──→ [OTel Collector] ──→ Tempo (트레이스)│
│  │+Micrometer│                              ──→ Mimir (메트릭) │
│  └──────────┘                                                  │
│       │ stdout                                                 │
│       ▼                                                        │
│  [Promtail / Filebeat] ──────────────────→ Loki (로그)         │
│                                                                │
│  [Prometheus] ── remote_write ──→ Mimir (메트릭 장기 저장)     │
│                                                                │
│  공통 스토리지:                                                 │
│  ┌─────────────────────────────────────────────┐               │
│  │              S3 (Object Storage)             │               │
│  │  loki-bucket/  tempo-bucket/  mimir-bucket/  │               │
│  └─────────────────────────────────────────────┘               │
│                                                                │
│  [Grafana] ←── Loki + Tempo + Mimir 통합 조회                  │
│      │                                                         │
│      └──→ AlertManager ──→ Discord / Slack / PagerDuty         │
└───────────────────────────────────────────────────────────────┘
```

---

## 34장. 규모별 리소스 가이드

```
소규모 (서버 1~10대, 일 ~5GB 로그):
  Loki:  1 인스턴스, 2GB RAM, filesystem 스토리지
  Tempo: 1 인스턴스, 1GB RAM, filesystem 스토리지
  Mimir: 1 인스턴스, 1GB RAM, filesystem 스토리지
  총: 4~8GB RAM

중규모 (서버 10~50대, 일 5~50GB 로그):
  Loki:  2 인스턴스, 4GB RAM each, S3 스토리지
  Tempo: 2 인스턴스, 2GB RAM each, S3 스토리지
  Mimir: 2 인스턴스, 4GB RAM each, S3 스토리지
  총: 20~32GB RAM

대규모 (서버 50~200대, 일 50~500GB 로그):
  Loki:  마이크로서비스 모드, Read 3 + Write 3 + Backend 3
  Tempo: 마이크로서비스 모드, Distributor 3 + Ingester 3 + Querier 3
  Mimir: 마이크로서비스 모드, 유사 구성
  총: 100GB+ RAM, 전담 운영 인력 필요
```

---

## 35장. 비용 비교

```
일 100GB 로그, 1000만 트레이스, 시계열 10만 개
30일 보관 기준, AWS 환경

┌─────────────────┬─────────────────┬─────────────────┐
│                 │ ELK+Jaeger+Prom │   LGTM Stack    │
├─────────────────┼─────────────────┼─────────────────┤
│ 로그 스토리지   │ ES SSD 5.4TB    │ S3 1.2TB        │
│                 │ $900/월          │ $30/월           │
├─────────────────┼─────────────────┼─────────────────┤
│ 트레이스 저장   │ ES SSD 3TB      │ S3 600GB        │
│                 │ $500/월          │ $15/월           │
├─────────────────┼─────────────────┼─────────────────┤
│ 메트릭 저장     │ Prometheus local│ S3 150GB        │
│                 │ $0 (로컬)        │ $4/월            │
├─────────────────┼─────────────────┼─────────────────┤
│ 컴퓨팅          │ ES 3노드        │ Loki+Tempo+Mimir│
│                 │ + Jaeger        │                  │
│                 │ + Prometheus    │                  │
│                 │ $800/월          │ $300/월           │
├─────────────────┼─────────────────┼─────────────────┤
│ UI              │ Kibana+Jaeger UI│ Grafana (통합)  │
│                 │ + Grafana       │                  │
├─────────────────┼─────────────────┼─────────────────┤
│ 총 비용         │ ~$2,200/월       │ ~$350/월         │
│ 절감            │                  │ 약 84% 절감      │
└─────────────────┴─────────────────┴─────────────────┘
```

---

## 36장. 보안 — 멀티 테넌시와 인증

```
멀티 테넌시:
  Loki, Tempo, Mimir 모두 멀티 테넌시 지원
  → 팀별/서비스별 데이터 격리
  → HTTP 헤더 X-Scope-OrgID로 테넌트 구분

인증:
  Grafana에서 LDAP/OIDC 연동
  → 팀별 대시보드 접근 권한 관리
  
네트워크:
  Loki, Tempo, Mimir는 내부 네트워크에서만 접근
  Grafana만 외부 노출 (Reverse Proxy + SSL)
```

---

## 37장. [퀴즈] 운영 환경 종합 문제

### 문제 1

```
LGTM 스택에서 Tempo가 2시간 다운되었습니다.
이 기간 동안 발생한 트레이스는 복구 가능한가요?
```

<details>
<summary>정답 보기</summary>

**앱의 트레이스 전송 방식에 따라 다릅니다.** 앱이 OTel Collector를 경유하여 Tempo로 전송하는 경우, Collector의 sending_queue에 일시적으로 버퍼링되지만 2시간은 감당하기 어렵습니다. Kafka를 중간에 두면 Kafka에 트레이스가 보관되어 Tempo 복구 후 소비 가능합니다. 가장 안전한 구성: App → OTel Collector → Kafka → OTel Collector → Tempo.

</details>

### 문제 2

```
Loki에서 로그의 traceId를 클릭했는데 Tempo에서 "trace not found"가 뜹니다.
3가지 가능한 원인은?
```

<details>
<summary>정답 보기</summary>

**1. 샘플링:** Trace가 샘플링에서 제외되어 Tempo에 저장되지 않았습니다 (probability < 1.0). **2. 보관 기간:** Tempo의 block_retention이 만료되어 해당 트레이스가 삭제되었습니다. **3. 전송 지연:** 트레이스가 아직 Ingester에서 Object Storage로 플러시되지 않았거나, Compactor가 아직 처리하지 않았을 수 있습니다 (최대 수 분 지연).

</details>

---

# PART 6. 마무리

---

## 38장. Observability 성숙도 자가 진단

```
아래 체크리스트로 현재 수준을 확인하세요:

Level 0: 없음 (0~1개 체크)
  □ System.out.println으로 디버깅

Level 1: 기본 로깅 (2~3개 체크)
  □ SLF4J + Logback 사용
  □ 로그 레벨 구분 (INFO, WARN, ERROR)
  □ MDC로 requestId 추적

Level 2: 중앙 집중 로그 (4~5개 체크)
  □ ELK 또는 Loki로 로그 중앙 수집
  □ Kibana/Grafana에서 로그 검색
  □ 로그 파일 로테이션 + 압축

Level 3: 메트릭 + 알림 (6~7개 체크)
  □ Prometheus + Grafana 메트릭 수집
  □ 커스텀 비즈니스 메트릭 정의
  □ Discord/Slack 알림 설정

Level 4: 분산 트레이싱 (8~9개 체크)
  □ OpenTelemetry / Micrometer Tracing 적용
  □ 서비스 간 Trace ID 전파
  □ 로그에 Trace ID 자동 삽입
  □ Jaeger 또는 Tempo에서 트레이스 분석

Level 5: 통합 Observability (10~12개 체크)
  □ LGTM 스택 운영 (Loki + Grafana + Tempo + Mimir)
  □ 로그 → 트레이스 원클릭 점프 (Derived Fields)
  □ 트레이스 → 로그 원클릭 점프 (Trace to Logs)
  □ 메트릭 → 트레이스 원클릭 점프 (Exemplars)
  □ Object Storage 기반 비용 최적화
  □ 장애 분석: 메트릭→트레이스→로그 3분 이내 완료
```

---

## 39장. 전체 핸드북 시리즈 총정리

```
핸드북 1: 로그의 기본 (기본 핸드북)
  SLF4J, Logback, MDC, ELK, Prometheus, Grafana, Discord 알림
  → Level 0 → Level 3

핸드북 2: 분산 트레이싱 (이 시리즈 1편)
  OpenTelemetry, Jaeger, Micrometer Tracing, Trace ID 전파
  → Level 3 → Level 4

핸드북 3: 로그 파이프라인 고도화 (이 시리즈 2편)
  Filebeat, Kafka, ES ILM, ES 클러스터
  → Level 2~3 인프라 강화

핸드북 4: 차세대 인프라 (이 시리즈 3편)
  Loki, OTel Collector, K8s 로깅, Kafka 클러스터
  → Level 3~4 인프라 현대화

핸드북 5: 통합 Observability (이 핸드북) ★
  Tempo, Mimir, LGTM 통합, Derived Fields, Exemplars
  → Level 4 → Level 5 (최종)
```

---

## 40장. 자주 하는 실수 TOP 10과 체크리스트

### 자주 하는 실수

```
1위: Grafana 데이터소스에 Derived Fields를 설정하지 않음
    → Loki 로그에서 Tempo로 점프가 안 됨
    → 해결: Loki 데이터소스 설정에서 derivedFields 추가

2위: Tempo의 Metrics Generator를 활성화하지 않음
    → Exemplar가 생성되지 않음 → 메트릭→트레이스 연결 불가
    → 해결: tempo-config.yml에서 metrics_generator 활성화

3위: Mimir URL에 /prometheus 경로를 빠트림
    → Grafana에서 "Data source is not working" 에러
    → 해결: URL을 http://mimir:9009/prometheus 로 설정

4위: Tempo의 trace_idle_period를 너무 짧게 설정
    → 긴 요청의 Span이 잘림
    → 해결: 서비스 최대 응답 시간보다 길게 설정

5위: Loki 라벨에 고 카디널리티 값 사용
    → Stream 폭발 → Loki 크래시
    → 해결: userId, traceId를 라벨이 아닌 본문/메타데이터에

6위: 모든 것을 filesystem에 저장 (운영 환경)
    → 디스크 부족, 확장 불가
    → 해결: S3/GCS Object Storage 사용

7위: Prometheus remote_write 미설정
    → Mimir에 메트릭이 없음
    → 해결: prometheus.yml에 remote_write 추가

8위: Spring Boot의 tracing.sampling.probability를 운영에서 1.0
    → 트레이스 데이터 폭발
    → 해결: 운영에서 0.01~0.1

9위: Promtail의 pipeline_stages에서 타임스탬프 파싱 누락
    → Loki에서 시간이 수집 시간으로 기록 (실제 로그 시간이 아님)
    → 해결: timestamp stage 추가 (Go 시간 포맷 주의!)

10위: Grafana Feature Toggles를 활성화하지 않음
     → TraceQL, Trace to Metrics 등이 동작 안 함
     → 해결: GF_FEATURE_TOGGLES_ENABLE 환경변수 설정
```

### 최종 운영 체크리스트

```
LGTM 스택:
  □ Loki: S3 스토리지, retention_period 설정, 라벨 카디널리티 < 10K
  □ Tempo: S3 스토리지, block_retention 설정, metrics_generator 활성화
  □ Mimir: S3 스토리지, blocks_retention_period 설정
  □ Grafana: 데이터소스 3개 연결 + Derived Fields + Trace to Logs + Exemplars

연결 확인:
  □ Loki 로그에서 traceId 클릭 → Tempo 트레이스 표시
  □ Tempo Span 클릭 → Loki 로그 표시
  □ Mimir 그래프 Exemplar 클릭 → Tempo 트레이스 표시

알림:
  □ Grafana Alert 설정 (에러율, 응답 시간 기반)
  □ Discord/Slack 연동 확인
```

---

> **끝.**
> 5개의 핸드북을 모두 마치셨습니다!
>
> 기본 핸드북 → 분산 트레이싱 → 로그 파이프라인 → 차세대 인프라 → 통합 Observability
>
> Level 0(println)에서 Level 5(Full LGTM Observability)까지
> 완전한 여정을 마쳤습니다.
>
> 이제 여러분은 어떤 규모의 서비스에서든
> Observability를 설계하고 운영할 수 있는 역량을 갖추었습니다.
>
> 여러분의 시스템이 항상 건강하기를 바랍니다. 🚀
