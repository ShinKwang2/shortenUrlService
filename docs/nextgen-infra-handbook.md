# 🚀 차세대 로그 인프라 완벽 가이드

### Grafana Loki · OpenTelemetry Collector · Kubernetes 로깅 · Kafka 클러스터

> **"Elasticsearch 비용을 80% 줄이고, Logstash를 없애고, 컨테이너 환경까지 정복하는 기술"**
> 이 핸드북을 마치면 차세대 Observability 인프라를 설계하고 운영할 수 있습니다.

---

# 목차

## PART 1. Grafana Loki — ES 대체, 비용 80% 절감
- 1장. Elasticsearch의 비용 문제를 직시하자
- 2장. Loki는 어떻게 비용을 80% 줄이는가? — 인덱싱 철학의 차이
- 3장. Loki의 핵심 개념 — Stream, Label, Chunk
- 4장. 라벨 설계 — Loki 성능의 90%를 결정하는 것
- 5장. [실습] Loki + Promtail + Grafana 설치
- 6장. [실습] Spring Boot 로그를 Loki로 수집하기
- 7장. [실습] Loki4j — 앱에서 Loki로 직접 전송
- 8장. LogQL 완전 정복 — 로그 검색 쿼리
- 9장. [실습] LogQL로 실전 장애 분석하기
- 10장. [실습] Grafana 대시보드에서 로그 메트릭 만들기
- 11장. Loki 운영 설정 완전 해부
- 12장. [퀴즈] Loki 설계 및 운영 문제

## PART 2. OpenTelemetry Collector — Logstash 대체
- 13장. 왜 Logstash를 대체해야 하는가?
- 14장. OTel Collector의 설계 철학 — Vendor-Neutral 파이프라인
- 15장. Receiver · Processor · Exporter 완전 해부
- 16장. [실습] OTel Collector 설치 및 기본 설정
- 17장. [실습] Spring Boot → OTel Collector → Loki 파이프라인
- 18장. [실습] 하나의 Collector로 로그 + 메트릭 + 트레이스 통합
- 19장. Tail-based Sampling — 에러만 100% 저장하는 기술
- 20장. OTel Collector 운영 — 이중화, 메모리 관리, 모니터링
- 21장. [퀴즈] OTel Collector 설계 문제

## PART 3. Kubernetes 환경 로깅
- 22장. 컨테이너 로깅은 왜 다른가? — VM과의 근본적 차이
- 23장. K8s 로깅 3가지 패턴 — Sidecar, DaemonSet, Direct
- 24장. [실습] Minikube로 K8s 클러스터 구축
- 25장. [실습] DaemonSet으로 Promtail 배포
- 26장. [실습] K8s 메타데이터 자동 수집 (pod, namespace, node)
- 27장. [실습] Helm Chart로 Loki Stack 한 번에 배포
- 28장. K8s 로깅 운영 — 로그 로테이션, 리소스 제한
- 29장. [퀴즈] K8s 로깅 설계 문제

## PART 4. Kafka 클러스터 (3 Broker) — 프로덕션 운영
- 30장. 단일 Kafka Broker의 한계
- 31장. Kafka 클러스터의 핵심 — Replication과 Leader Election
- 32장. [실습] 3 Broker Kafka 클러스터 구성
- 33장. [실습] Topic Replication Factor와 min.insync.replicas
- 34장. [실습] Broker 장애 시뮬레이션 — 데이터 유실 없음 확인
- 35장. Kafka 클러스터 모니터링 — 핵심 지표
- 36장. KRaft 모드 — Zookeeper 없는 Kafka
- 37장. [퀴즈] Kafka 클러스터 운영 문제

## PART 5. 최종 아키텍처와 마무리
- 38장. 전체 통합 아키텍처 — docker-compose 한 방에 띄우기
- 39장. 규모별 기술 선택 가이드
- 40장. 운영 체크리스트와 자주 하는 실수

---

# PART 1. Grafana Loki — ES 대체, 비용 80% 절감

---

## 1장. Elasticsearch의 비용 문제를 직시하자

### 1.1 ES는 왜 비싼가?

```
Elasticsearch에 로그 한 줄이 저장되는 과정:

원본: "2024-01-15 ERROR 웨이팅 등록 실패 userId: 1001 restaurantId: 500"

ES가 하는 일 (역인덱싱):
  "2024"       → [doc1, doc45, doc892, ...]
  "01"         → [doc1, doc45, doc892, ...]
  "15"         → [doc1, doc200, doc892, ...]
  "ERROR"      → [doc1, doc33, doc567, ...]
  "웨이팅"     → [doc1, doc12, doc892, ...]
  "등록"       → [doc1, doc12, doc345, ...]
  "실패"       → [doc1, doc33, doc567, ...]
  "userId"     → [doc1, doc2, doc3, ...]
  "1001"       → [doc1, doc456, ...]
  "restaurantId" → [doc1, doc2, doc3, ...]
  "500"        → [doc1, doc789, ...]

→ 원본 1줄에서 11개의 토큰을 추출하여 각각 인덱싱
→ 원본 크기의 1.5~2배 스토리지를 사용
→ "어떤 단어로든 빠르게 검색 가능"하지만 그 대가가 비싸다
```

### 1.2 실제 비용 비교 (일 100GB 로그 기준)

```
Elasticsearch (30일 보관):
  데이터량: 100GB × 30일 × 1.8 (인덱스 오버헤드) = 5.4TB
  스토리지: SSD 필수 (Hot Node)
  서버: ES 3노드 (각 2TB SSD, 16GB RAM)
  월 비용 (AWS): ~$1,500

Grafana Loki (30일 보관):
  데이터량: 100GB × 30일 × 0.4 (압축) = 1.2TB
  스토리지: S3 (Object Storage)
  서버: Loki 1~2 인스턴스 (4GB RAM)
  월 비용 (AWS): ~$200

절감: 약 87% ($1,300/월 절약)
```

### 1.3 그런데 검색 속도는?

```
"그럼 Loki는 느린 거 아닌가요?"

정답: "검색 패턴에 따라 다르다"

패턴 1: "에러 로그 중 결제 관련만 보여줘" (가장 흔한 검색)
  ES:   {level: "ERROR"} AND message: "결제"     → 50ms
  Loki: {level="ERROR"} |= "결제"                → 200ms
  → ES가 빠르지만, 200ms도 충분히 빠름

패턴 2: "특정 서비스의 최근 1시간 에러 로그"
  ES:   인덱스 전체를 뒤짐 (30일치)              → 100ms
  Loki: 해당 서비스 스트림만 스캔 (1시간)         → 80ms
  → Loki가 오히려 빠를 수 있음 (스트림 범위가 좁으면)

패턴 3: "모든 서비스에서 'NullPointer'가 포함된 로그" (전문 검색)
  ES:   역인덱스로 즉시 검색                       → 30ms
  Loki: 모든 스트림을 grep처럼 스캔               → 2,000ms
  → ES가 압도적으로 빠름

결론: 
  대부분의 로그 검색은 "특정 서비스 + 특정 레벨 + 시간 범위"
  → 이 패턴에서는 Loki도 충분히 빠르고, 비용은 1/5
```

---

## 2장. Loki는 어떻게 비용을 80% 줄이는가?

### 2.1 핵심 철학: "로그 본문을 인덱싱하지 않는다"

```
Elasticsearch 방식:
  원본 → 모든 단어를 역인덱싱 → 어떤 단어로든 빠른 검색
  대가: 원본의 1.5~2배 스토리지

Loki 방식:
  메타데이터(라벨)만 인덱싱 + 로그 본문은 압축 저장
  검색: 라벨로 범위를 좁히고 → 그 안에서 grep
  대가: 원본의 0.3~0.5배 스토리지 (압축 효과!)

비유:
  ES = 도서관의 모든 책의 모든 단어에 색인을 만드는 것
       → 아무 단어로든 즉시 찾을 수 있지만, 색인이 책보다 두꺼움
  
  Loki = 도서관의 책을 "장르별, 저자별" 선반에 정리하고
         원하는 선반에서 직접 훑어보는 것
         → 선반 찾기(라벨 검색)는 즉시, 책 훑기(본문 검색)는 조금 걸림
```

### 2.2 Loki의 저장 구조

```
┌─────────────────────────────────────────────────────────┐
│                   Loki Storage                           │
│                                                          │
│  인덱스 (매우 작음):                                      │
│  ┌───────────────────────────────────────────────────┐  │
│  │ Stream: {service="waiting", level="ERROR"}        │  │
│  │   → Chunk ID: chunk_abc123 (1시간 분량)           │  │
│  │   → Chunk ID: chunk_def456 (1시간 분량)           │  │
│  │                                                   │  │
│  │ Stream: {service="waiting", level="INFO"}         │  │
│  │   → Chunk ID: chunk_ghi789 (1시간 분량)           │  │
│  └───────────────────────────────────────────────────┘  │
│                                                          │
│  청크 (로그 본문, 압축 저장):                              │
│  ┌─────────────────────────────┐                        │
│  │ chunk_abc123.gz             │ ← S3/GCS에 저장        │
│  │   14:00:01 ERROR 결제 실패.. │    (매우 저렴)          │
│  │   14:00:05 ERROR DB 연결..  │                        │
│  │   14:01:22 ERROR 타임아웃.. │                        │
│  └─────────────────────────────┘                        │
└─────────────────────────────────────────────────────────┘

인덱스 크기: 전체 데이터의 1~3%
청크 크기:   원본의 30~50% (gzip/snappy 압축)
```

---

## 3장. Loki의 핵심 개념

### 3.1 Log Stream (로그 스트림)

```
Stream = 동일한 라벨 조합을 가진 로그의 흐름

예시:
  Stream A: {service="waiting", level="INFO", env="prod"}
    → 운영 환경 waiting-service의 INFO 로그들의 흐름
  
  Stream B: {service="waiting", level="ERROR", env="prod"}
    → 운영 환경 waiting-service의 ERROR 로그들의 흐름

  Stream C: {service="notification", level="INFO", env="prod"}
    → 운영 환경 notification-service의 INFO 로그들의 흐름

각 Stream은 독립적인 "로그 파일"과 비슷한 개념입니다.
검색 시 원하는 Stream만 선택하면 됩니다.
```

### 3.2 Label (라벨)

```
라벨 = 로그를 분류하는 키-값 쌍 = Prometheus 라벨과 동일한 개념

라벨이 곧 인덱스입니다!
→ 라벨 조합으로 Stream이 결정되고
→ Stream 단위로 Chunk가 생성되고
→ 검색 시 라벨로 Stream을 선택합니다
```

### 3.3 Chunk (청크)

```
Chunk = 일정 시간 동안의 로그를 압축하여 묶은 블록

특성:
  - 기본적으로 1시간 또는 1MB 단위
  - gzip 또는 snappy로 압축
  - Object Storage(S3)에 저장
  - 한 번 닫힌 Chunk는 수정 불가 (Immutable)
```

---

## 4장. 라벨 설계 — Loki 성능의 90%를 결정하는 것

### 4.1 카디널리티(Cardinality)란?

```
카디널리티 = 고유 값의 수

저 카디널리티 (좋음):
  level:   "INFO", "WARN", "ERROR" → 3가지 ✅
  env:     "dev", "staging", "prod" → 3가지 ✅
  service: "waiting", "notification", "payment" → 10가지 ✅

고 카디널리티 (나쁨, 절대 라벨로 쓰면 안 됨!):
  userId:    1, 2, 3, ... 1,000,000 → 100만 가지 🚫
  requestId: UUID 값 → 무한대 🚫
  traceId:   UUID 값 → 무한대 🚫
  timestamp: 초 단위 값 → 무한대 🚫
  ip:        IP 주소 → 수십만 가지 🚫
```

### 4.2 왜 고 카디널리티 라벨이 위험한가?

```
라벨 조합 수 = 각 라벨의 카디널리티 곱

안전한 예:
  service(10) × level(5) × env(3) = 150개의 Stream
  → 150개의 인덱스 엔트리 = 문제 없음

위험한 예:
  service(10) × level(5) × userId(100,000) = 5,000,000개의 Stream
  → 500만 개의 인덱스 엔트리!
  → 인덱스가 폭발 → Loki 메모리 부족 → 크래시

★ 철칙: 라벨의 총 Stream 수가 10,000개를 넘지 않도록 설계
```

### 4.3 라벨 설계 가이드

```
✅ 라벨로 써야 하는 것:
  service:    서비스 이름 (waiting-service)
  level:      로그 레벨 (INFO, ERROR)
  env:        환경 (dev, prod)
  namespace:  K8s 네임스페이스
  cluster:    클러스터 이름
  job:        수집 작업 이름

⚠️ 주의하여 쓸 수 있는 것:
  pod:        K8s Pod 이름 (스케일에 따라 카디널리티 증가)
  host:       호스트명 (서버 수에 따라)

🚫 절대 라벨로 쓰면 안 되는 것:
  userId, requestId, traceId, sessionId
  IP 주소, URL 경로 파라미터
  타임스탬프, 랜덤 값

"그러면 userId로 검색하고 싶을 땐 어떻게?"
  → 라벨이 아니라 로그 본문에서 grep으로 검색
  {service="waiting"} |= "userId: 1001"
  
  → 또는 Structured Metadata (Loki 2.9+)로 저장
  → 인덱싱은 안 되지만 필터링은 가능
```

---

## 5장. [실습] Loki + Promtail + Grafana 설치

### 5.1 docker-compose.yml

```yaml
version: '3.8'

services:
  # ═══ Grafana Loki ═══
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
      - loki-network

  # ═══ Promtail (로그 수집 Agent) ═══
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
      - loki-network

  # ═══ Grafana (UI) ═══
  grafana:
    image: grafana/grafana:10.3.0
    container_name: grafana
    environment:
      - GF_SECURITY_ADMIN_USER=admin
      - GF_SECURITY_ADMIN_PASSWORD=admin
    ports:
      - "3000:3000"
    volumes:
      - grafana-data:/var/lib/grafana
      - ./grafana/provisioning:/etc/grafana/provisioning:ro
    depends_on:
      - loki
    networks:
      - loki-network

volumes:
  loki-data:
  grafana-data:

networks:
  loki-network:
    driver: bridge
```

### 5.2 loki-config.yml (상세 주석)

```yaml
# docker/loki/loki-config.yml

auth_enabled: false          # 단일 테넌트 (멀티 테넌트 시 true)

server:
  http_listen_port: 3100
  log_level: info

# ── Ingester: 로그 수신 및 청크 생성 ──
ingester:
  wal:
    enabled: true            # Write-Ahead Log (크래시 시 데이터 보호)
    dir: /loki/wal
  lifecycler:
    ring:
      kvstore:
        store: inmemory      # 단일 인스턴스 (클러스터: consul/etcd)
      replication_factor: 1
  chunk_idle_period: 1h      # 1시간 동안 새 로그 없으면 Chunk 플러시
  max_chunk_age: 2h          # 최대 Chunk 수명
  chunk_target_size: 1048576 # 목표 1MB
  chunk_retain_period: 30s

# ── Schema: 인덱스 + 청크 저장 방식 ──
schema_config:
  configs:
    - from: 2024-01-01
      store: tsdb             # ★ 최신 인덱스 엔진 (v12보다 40% 빠름)
      object_store: filesystem
      schema: v13
      index:
        prefix: index_
        period: 24h           # 인덱스 로테이션 주기

# ── Storage: 어디에 저장할 것인가 ──
storage_config:
  tsdb_shipper:
    active_index_directory: /loki/tsdb-index
    cache_location: /loki/tsdb-cache
  filesystem:
    directory: /loki/chunks
  # ── 운영 환경: S3 ──
  # aws:
  #   s3: s3://ap-northeast-2/your-loki-bucket
  #   bucketnames: your-loki-bucket
  #   region: ap-northeast-2

# ── Compactor: 인덱스 압축 + 보관 정책 ──
compactor:
  working_directory: /loki/compactor
  compaction_interval: 10m
  retention_enabled: true     # ★ 보관 정책 활성화

# ── Limits: 속도 제한 + 보관 기간 ──
limits_config:
  retention_period: 744h      # 31일 보관
  ingestion_rate_mb: 10       # 초당 최대 수집량 10MB
  ingestion_burst_size_mb: 20 # 버스트 허용량 20MB
  per_stream_rate_limit: 3MB  # 스트림당 초당 3MB
  max_entries_limit_per_query: 5000
  max_query_parallelism: 16

analytics:
  reporting_enabled: false
```

### 5.3 promtail-config.yml

```yaml
# docker/promtail/promtail-config.yml

server:
  http_listen_port: 9080

clients:
  - url: http://loki:3100/loki/api/v1/push
    backoff_config:
      min_period: 1s
      max_period: 5m
      max_retries: 10

scrape_configs:
  - job_name: app-logs
    static_configs:
      - targets: [localhost]
        labels:
          service: waiting-service
          env: local
          __path__: /var/log/app/*.log

    pipeline_stages:
      # 1. 멀티라인 (스택트레이스 합치기)
      - multiline:
          firstline: '^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}'
          max_wait_time: 3s

      # 2. 정규식으로 필드 추출
      - regex:
          expression: '^(?P<timestamp>\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}) \[(?P<thread>[^\]]+)\] \[traceId=(?P<traceId>[^\s]*)\s*spanId=(?P<spanId>[^\]]*)\] (?P<level>\w+)\s+(?P<logger>\S+) - (?P<msg>.*)'

      # 3. level을 Loki 라벨로 (저 카디널리티이므로 OK)
      - labels:
          level:

      # 4. 타임스탬프 파싱 (Go 시간 포맷!)
      - timestamp:
          source: timestamp
          format: '2006-01-02 15:04:05.000'
          location: Asia/Seoul

      # 5. traceId는 Structured Metadata로 (라벨 아님!)
      - structured_metadata:
          traceId:
          spanId:

      # 6. 최종 출력 메시지
      - output:
          source: msg
```

### 5.4 Grafana 데이터소스 자동 프로비저닝

```yaml
# docker/grafana/provisioning/datasources/datasources.yml
apiVersion: 1
datasources:
  - name: Loki
    type: loki
    access: proxy
    url: http://loki:3100
    isDefault: true
```

### 5.5 실행 및 확인

```bash
cd docker && docker compose up -d

# Loki 상태 확인
curl http://localhost:3100/ready     # → "ready"
curl http://localhost:3100/metrics   # → Prometheus 메트릭

# Grafana 접속: http://localhost:3000 (admin/admin)
# → Explore → Loki 데이터소스 선택 → {service="waiting-service"} 입력
```

---

## 6장. [실습] Spring Boot 로그를 Loki로 수집하기

```bash
# Spring Boot 앱 실행 (logs/ 폴더에 로그 생성)
cd waiting-service && ./gradlew bootRun

# 테스트 요청
curl -X POST http://localhost:8080/api/waiting \
  -H "Content-Type: application/json" \
  -d '{"userId": 1, "restaurantId": 100}'

# Grafana → Explore → Loki
# 쿼리: {service="waiting-service"}
# → 로그가 보이면 성공!

# 에러 로그만 보기:
# {service="waiting-service", level="ERROR"}
```

---

## 7장. [실습] Loki4j — 앱에서 Loki로 직접 전송

Promtail 없이 앱에서 직접 Loki로 보내는 방법입니다.

### 7.1 의존성 추가

```groovy
// build.gradle
implementation 'com.github.loki4j:loki-logback-appender:1.5.1'
```

### 7.2 logback-spring.xml

```xml
<appender name="LOKI" class="com.github.loki4j.logback.Loki4jAppender">
    <http>
        <url>http://localhost:3100/loki/api/v1/push</url>
    </http>
    <format>
        <label>
            <!-- ★ 저 카디널리티 값만! -->
            <pattern>service=waiting-service,level=%level,env=${ENV:-local},host=${HOSTNAME}</pattern>
        </label>
        <message>
            <pattern>{"ts":"%d{yyyy-MM-dd'T'HH:mm:ss.SSS}","thread":"%thread","traceId":"%X{traceId:-}","logger":"%logger{36}","msg":"%message","ex":"%ex{short}"}</pattern>
        </message>
        <sortByTime>true</sortByTime>
    </format>
</appender>
```

### 7.3 Promtail vs Loki4j 비교

| 항목 | Promtail (파일 기반) | Loki4j (직접 전송) |
|------|--------------------|--------------------|
| 중간 단계 | 앱→파일→Promtail→Loki | 앱→Loki (직접) |
| 유실 방지 | 파일이 버퍼 역할 | 앱 내부 버퍼에 의존 |
| 운영 복잡도 | Promtail 프로세스 관리 | 없음 (앱에 내장) |
| K8s 환경 | DaemonSet으로 배포 | 불필요 |
| 권장 상황 | 프로덕션 (안정성 우선) | 소규모, 개발 환경 |

---

## 8장. LogQL 완전 정복

### 8.1 기본 문법

```logql
# ═══ 라벨 셀렉터 (Stream 선택) ═══
{service="waiting-service"}                    # 정확히 일치
{service=~"waiting.*"}                          # 정규식 매칭
{service="waiting-service", level="ERROR"}     # AND 조건
{service!="notification"}                       # 제외

# ═══ 라인 필터 (로그 본문 검색) ═══
{service="waiting-service"} |= "웨이팅 등록"    # 포함
{service="waiting-service"} != "health"         # 미포함
{service="waiting-service"} |~ "userId: (1001|1002)"  # 정규식 일치
{service="waiting-service"} !~ "DEBUG|TRACE"    # 정규식 미포함

# ═══ 파서 (필드 추출) ═══
{service="waiting-service"} | json              # JSON 파싱
{service="waiting-service"} | logfmt            # key=value 파싱
{service="waiting-service"} | pattern "<ts> [<thread>] <level> <logger> - <msg>"

# ═══ 필드 필터 (파싱 후 필터) ═══
{service="waiting-service"} | json | msg =~ ".*등록.*"
{service="waiting-service"} | json | level = "ERROR"
{service="waiting-service"} | json | traceId = "abc123"

# ═══ 포맷 변경 ═══
{service="waiting-service"} | json | line_format "{{.level}} | {{.msg}}"
```

### 8.2 메트릭 쿼리 (로그에서 지표 추출)

```logql
# 초당 로그 발생률
rate({service="waiting-service"}[5m])

# 초당 에러 발생률
rate({service="waiting-service", level="ERROR"}[5m])

# 1시간 에러 총 수
count_over_time({service="waiting-service", level="ERROR"}[1h])

# 서비스별 에러 비율
sum(rate({level="ERROR"}[5m])) by (service)
  / sum(rate({}[5m])) by (service) * 100

# 에러 메시지별 TOP 10
topk(10, sum(rate({level="ERROR"}[1h])) by (msg))

# ★ Loki의 강력한 점:
# 별도의 메트릭 시스템 없이 로그만으로 대시보드를 만들 수 있음!
```

---

## 9장. [실습] LogQL로 실전 장애 분석

### 시나리오: 에러율이 갑자기 증가했다

```logql
# 1단계: 최근 1시간 에러 발생 추이 확인
rate({service="waiting-service", level="ERROR"}[5m])

# 2단계: 에러 로그 내용 확인
{service="waiting-service", level="ERROR"} | json | line_format "{{.ts}} {{.msg}}"

# 3단계: 특정 에러 메시지가 급증했는지 확인
{service="waiting-service", level="ERROR"} |= "DB 연결 실패"

# 4단계: 해당 시간대의 traceId 확인 (Structured Metadata)
{service="waiting-service", level="ERROR"} | traceId != ""

# 5단계: 특정 traceId의 전체 흐름 추적
{} | traceId = "abc123def456"
# → 모든 서비스의 해당 요청 로그가 나옴
```

---

## 10장. [실습] Grafana 대시보드에서 로그 메트릭 만들기

```
Grafana → Dashboard → New Dashboard → Add Visualization

패널 1: 시간대별 로그 레벨 분포 (Stacked Bar)
  Data Source: Loki
  Query: sum(rate({service="waiting-service"}[5m])) by (level)

패널 2: 에러율 게이지
  Query: sum(rate({level="ERROR"}[5m])) / sum(rate({}[5m])) * 100

패널 3: 최근 에러 로그 (Logs 패널)
  Query: {service="waiting-service", level="ERROR"} | json

패널 4: 서비스별 로그 발생량
  Query: sum(rate({}[5m])) by (service)
```

---

## 11장. Loki 운영 설정 완전 해부

### 11.1 운영 환경 loki-config.yml (S3 스토리지)

```yaml
# ═══ 운영 환경 설정 ═══
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
        store: memberlist    # 클러스터: memberlist 사용
      replication_factor: 1  # 클러스터: 3
  chunk_idle_period: 30m     # 운영: 30분 (메모리 절약)
  max_chunk_age: 1h
  chunk_target_size: 1572864 # 1.5MB (S3 PUT 효율)
  chunk_encoding: snappy     # snappy: 빠른 압축/해제

schema_config:
  configs:
    - from: 2024-01-01
      store: tsdb
      object_store: s3       # ★ S3 사용
      schema: v13
      index:
        prefix: loki_index_
        period: 24h

storage_config:
  tsdb_shipper:
    active_index_directory: /loki/tsdb-index
    cache_location: /loki/tsdb-cache
    shared_store: s3
  aws:
    s3: s3://ap-northeast-2/your-loki-bucket
    bucketnames: your-loki-bucket
    region: ap-northeast-2

compactor:
  working_directory: /loki/compactor
  shared_store: s3
  retention_enabled: true

limits_config:
  retention_period: 2160h     # 90일 보관
  ingestion_rate_mb: 20
  ingestion_burst_size_mb: 40
  per_stream_rate_limit: 5MB
  max_query_parallelism: 32
  max_entries_limit_per_query: 10000

# 쿼리 캐시 (성능 향상)
query_range:
  cache_results: true
  results_cache:
    cache:
      embedded_cache:
        enabled: true
        max_size_mb: 100
```

---

## 12장. [퀴즈] Loki 설계 및 운영 문제

### 문제 1

```yaml
# Promtail 라벨 설정:
labels:
  service: waiting-service
  level: "%level"
  userId: "%userId"
  requestId: "%requestId"
```

```
이 설정의 문제점은 무엇인가요?
```

<details>
<summary>정답 보기</summary>

**userId와 requestId가 라벨로 설정되어 있습니다.** 이것은 고 카디널리티 라벨로, 사용자가 100만 명이면 100만 × 레벨 수 × 서비스 수만큼의 Stream이 생성되어 Loki가 크래시합니다. userId와 requestId는 라벨 대신 로그 본문에 포함하고 `|=` 또는 `| json | userId = "1001"`로 검색해야 합니다. 또는 `structured_metadata`로 저장하면 인덱싱 없이 필터링이 가능합니다.

</details>

### 문제 2

```
일 100GB 로그, 90일 보관. S3 Standard 기준.
Loki의 월간 S3 비용을 추정해보세요. (S3 Standard: $0.025/GB/월)
```

<details>
<summary>정답 보기</summary>

원본: 100GB × 90일 = 9TB. Loki 압축률 약 60%: 9TB × 0.4 = 약 3.6TB. S3 비용: 3,600GB × $0.025 = **$90/월**. 여기에 S3 PUT/GET 비용(약 $10~20/월)을 추가하면 총 약 **$100~110/월**입니다. 동일 조건의 Elasticsearch는 약 $1,500/월이므로 93% 절감입니다.

</details>

---

# PART 2. OpenTelemetry Collector — Logstash 대체

---

## 13장. 왜 Logstash를 대체해야 하는가?

```
Logstash의 한계:
  1. JVM 기반 → 최소 512MB 메모리
  2. 로그 전용 → 메트릭/트레이스는 별도 파이프라인
  3. Elastic 생태계에 묶임 → ES 외 출력이 제한적
  4. 설정이 Ruby DSL → 러닝 커브

OTel Collector의 장점:
  1. Go 기반 → 100~200MB 메모리
  2. 로그 + 메트릭 + 트레이스 통합
  3. Vendor-Neutral → Loki, Tempo, ES, Datadog 어디든 전송
  4. 설정이 YAML → 직관적
```

---

## 14장. OTel Collector의 설계 철학

```
"데이터를 만드는 쪽(앱)과 저장하는 쪽(백엔드)을 완전히 분리한다"

앱은 OTLP 프로토콜로 Collector에 전송하고,
Collector가 백엔드로 라우팅합니다.
→ 백엔드를 Jaeger에서 Tempo로 교체해도 앱 변경 불필요
→ Loki를 ES로 교체해도 앱 변경 불필요

┌─────────┐                    ┌───────────────────────────────┐
│ App     │── OTLP (4317) ──→ │ OTel Collector                │
│         │                    │                               │
└─────────┘                    │  Receivers → Processors → Exporters
                               │                               │
                               │  Traces  → batch → Tempo      │
                               │  Metrics → batch → Mimir      │
                               │  Logs    → batch → Loki       │
                               └───────────────────────────────┘
```

---

## 15장. Receiver · Processor · Exporter 완전 해부

### 15.1 주요 Receivers

```yaml
receivers:
  # 앱에서 OTLP로 전송받기
  otlp:
    protocols:
      grpc:
        endpoint: 0.0.0.0:4317
      http:
        endpoint: 0.0.0.0:4318

  # Prometheus 스크래핑 (Spring Boot Actuator)
  prometheus:
    config:
      scrape_configs:
        - job_name: 'spring-boot'
          metrics_path: '/actuator/prometheus'
          scrape_interval: 15s
          static_configs:
            - targets: ['host.docker.internal:8080']

  # 로그 파일 읽기 (Filebeat 대체)
  filelog:
    include: [/var/log/app/*.log]
    start_at: end
    multiline:
      line_start_pattern: '^\d{4}-\d{2}-\d{2}'
```

### 15.2 주요 Processors

```yaml
processors:
  # 배치 처리 (네트워크 효율)
  batch:
    timeout: 5s
    send_batch_size: 1000

  # 메모리 제한 (OOM 방지)
  memory_limiter:
    check_interval: 1s
    limit_mib: 512
    spike_limit_mib: 128

  # 속성 추가/삭제
  attributes:
    actions:
      - key: environment
        value: production
        action: upsert
      - key: internal.secret
        action: delete

  # 특정 데이터 제외
  filter:
    logs:
      exclude:
        match_type: strict
        bodies:
          - "GET /actuator/health"

  # Tail-based 샘플링 (에러만 100% 저장)
  tail_sampling:
    decision_wait: 10s
    policies:
      - name: errors-always
        type: status_code
        status_code: {status_codes: [ERROR]}
      - name: slow-always
        type: latency
        latency: {threshold_ms: 3000}
      - name: sample-rest
        type: probabilistic
        probabilistic: {sampling_percentage: 5}
```

### 15.3 주요 Exporters

```yaml
exporters:
  # Loki로 로그 전송
  loki:
    endpoint: http://loki:3100/loki/api/v1/push

  # Tempo로 트레이스 전송
  otlp/tempo:
    endpoint: http://tempo:4317
    tls:
      insecure: true

  # Mimir로 메트릭 전송
  prometheusremotewrite:
    endpoint: http://mimir:9009/api/v1/push

  # 디버깅
  debug:
    verbosity: basic
```

---

## 16장. [실습] OTel Collector 설치 및 기본 설정

### 16.1 docker-compose.yml에 추가

```yaml
  otel-collector:
    image: otel/opentelemetry-collector-contrib:0.94.0
    container_name: otel-collector
    command: ["--config=/etc/otel/config.yml"]
    volumes:
      - ./otel-collector/config.yml:/etc/otel/config.yml:ro
    ports:
      - "4317:4317"    # OTLP gRPC
      - "4318:4318"    # OTLP HTTP
      - "8888:8888"    # Collector 자체 메트릭
    networks:
      - loki-network
```

### 16.2 config.yml

```yaml
# docker/otel-collector/config.yml

receivers:
  otlp:
    protocols:
      grpc:
        endpoint: 0.0.0.0:4317
      http:
        endpoint: 0.0.0.0:4318

processors:
  batch:
    timeout: 5s
    send_batch_size: 1000

  memory_limiter:
    check_interval: 1s
    limit_mib: 512

exporters:
  loki:
    endpoint: http://loki:3100/loki/api/v1/push

  debug:
    verbosity: basic

service:
  pipelines:
    logs:
      receivers: [otlp]
      processors: [memory_limiter, batch]
      exporters: [loki, debug]

  telemetry:
    logs:
      level: info
```

---

## 17장. [실습] Spring Boot → OTel Collector → Loki

### 17.1 Spring Boot에서 OTel Collector로 로그 전송

```groovy
// build.gradle 추가
implementation 'io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0:2.1.0-alpha'
```

이 방식은 아직 성숙 단계이므로, 실무에서는 **Promtail 또는 Loki4j**를 통한 수집이 더 안정적입니다. OTel Collector는 주로 **트레이스와 메트릭 수집**에 사용하고, 로그는 Promtail/Filebeat로 수집하는 하이브리드 구성이 가장 일반적입니다.

```
권장 하이브리드 아키텍처:

  [App] ── OTLP ──→ [OTel Collector] ──→ Tempo (트레이스)
        ── OTLP ──→ [OTel Collector] ──→ Mimir (메트릭)
        ── 파일 ──→ [Promtail] ──→ Loki (로그)
```

---

## 18장. [실습] 하나의 Collector로 로그 + 메트릭 + 트레이스 통합

```yaml
# OTel Collector config — 3개 파이프라인 통합

receivers:
  otlp:
    protocols:
      grpc: { endpoint: 0.0.0.0:4317 }
  prometheus:
    config:
      scrape_configs:
        - job_name: spring-boot
          metrics_path: /actuator/prometheus
          static_configs:
            - targets: ['host.docker.internal:8080']

processors:
  batch: { timeout: 5s }
  memory_limiter: { limit_mib: 512 }

exporters:
  loki: { endpoint: http://loki:3100/loki/api/v1/push }
  otlp/tempo: { endpoint: http://tempo:4317, tls: { insecure: true } }
  prometheusremotewrite: { endpoint: http://mimir:9009/api/v1/push }

service:
  pipelines:
    traces:
      receivers: [otlp]
      processors: [memory_limiter, batch]
      exporters: [otlp/tempo]
    metrics:
      receivers: [otlp, prometheus]
      processors: [memory_limiter, batch]
      exporters: [prometheusremotewrite]
    logs:
      receivers: [otlp]
      processors: [memory_limiter, batch]
      exporters: [loki]
```

---

## 19장. Tail-based Sampling

```
일반 샘플링 (Head-based):
  요청 시작 시 5% 확률로 결정 → 에러 요청도 95% 확률로 버림 😱

Tail-based 샘플링:
  요청이 끝난 후 결정 → 에러/느린 요청은 100% 저장 ✅

OTel Collector의 tail_sampling processor:
  - ERROR 응답 → 무조건 저장
  - 3초 이상 소요 → 무조건 저장
  - 나머지 → 5%만 저장

주의: Collector에 10초간 모든 Span을 메모리에 보관해야 함
→ 메모리 사용량 증가 → 적절한 리소스 할당 필요
```

---

## 20장. OTel Collector 운영

```
이중화:
  OTel Collector를 2대 이상 운영
  앞에 로드밸런서(ALB) 배치
  → 한 대가 죽어도 데이터 유실 없음

메모리 관리:
  memory_limiter 필수 설정!
  limit_mib: 컨테이너 메모리의 80%
  → 한도 도달 시 데이터를 거부하여 OOM 방지

자체 모니터링:
  service.telemetry.metrics.address: 0.0.0.0:8888
  → Prometheus로 Collector 자체 메트릭 수집
  → 처리량, 에러율, 큐 크기 등 모니터링
```

---

## 21장. [퀴즈] OTel Collector 설계 문제

### 문제 1

```
OTel Collector가 Loki, Tempo, Mimir 모두에 데이터를 전송합니다.
Loki가 5분간 다운되면 로그 데이터는 유실되나요?
```

<details>
<summary>정답 보기</summary>

**기본 설정에서는 유실될 수 있습니다.** OTel Collector의 기본 내부 큐는 메모리 기반이며 제한적입니다. 하지만 `sending_queue` 설정으로 큐 크기를 늘리고, `persistent_queue`를 활성화하면 디스크에 버퍼링하여 유실을 방지할 수 있습니다. 또는 Collector 앞에 Kafka를 두는 것이 가장 안전합니다.

</details>

---

# PART 3. Kubernetes 환경 로깅

---

## 22장. 컨테이너 로깅은 왜 다른가?

```
VM(전통적 서버):
  - 로그 파일이 서버 디스크에 영구 보존
  - 서버가 재시작되어도 로그 파일 유지
  - Filebeat가 파일을 읽어서 전송

컨테이너 (K8s Pod):
  - Pod이 죽으면 내부 파일시스템이 사라짐 🚨
  - Pod이 재시작되면 이전 로그가 없음
  - Pod이 다른 노드로 이동할 수 있음 (스케줄링)
  - 컨테이너는 stdout/stderr로 로그를 출력하는 것이 표준

K8s의 로그 저장 방식:
  컨테이너 stdout/stderr
    → Docker/containerd가 캡처
    → 노드의 /var/log/pods/<namespace>_<pod>_<uid>/<container>/0.log에 저장
    → 로그 로테이션 정책에 따라 자동 관리
    → 하지만 노드 디스크에만 있음! 중앙 수집이 필요
```

---

## 23장. K8s 로깅 3가지 패턴

```
패턴 1: DaemonSet ★ (가장 일반적)
─────────────────
  각 노드에 로그 수집 Agent를 하나씩 배포
  Agent가 해당 노드의 모든 Pod 로그를 수집

  ┌───────────────────── Node 1 ─────────────────────┐
  │  [Pod A] → stdout → /var/log/pods/...            │
  │  [Pod B] → stdout → /var/log/pods/...            │
  │                                                   │
  │  [Promtail DaemonSet] ← 모든 Pod 로그 읽기       │
  │                          → Loki로 전송            │
  └───────────────────────────────────────────────────┘

  장점: 리소스 효율적 (노드당 1개), 설정 간단
  단점: 모든 Pod의 로그 형식이 다를 수 있음

패턴 2: Sidecar
─────────────────
  각 Pod에 로그 수집 컨테이너를 함께 배포

  ┌──────────────── Pod ────────────────┐
  │  [App Container] → 로그 파일       │
  │  [Promtail Sidecar] ← 파일 읽기   │
  │                       → Loki 전송  │
  └────────────────────────────────────┘

  장점: Pod별 맞춤 설정, 멀티라인 처리 쉬움
  단점: 리소스 낭비 (Pod마다 Agent), 관리 복잡

패턴 3: Direct (앱에서 직접 전송)
─────────────────
  앱이 Loki4j 등으로 직접 Loki에 전송

  장점: 가장 간단, 추가 인프라 불필요
  단점: 앱이 Loki 주소를 알아야 함, 유실 위험

★ 권장:
  대부분의 환경: DaemonSet
  특수한 로그 형식이 필요한 Pod: Sidecar
  소규모/개발: Direct
```

---

## 24장. [실습] Minikube로 K8s 클러스터 구축

```bash
# Minikube 설치 (macOS)
brew install minikube

# Minikube 설치 (Windows)
choco install minikube

# 클러스터 시작 (4GB 메모리, 2 CPU)
minikube start --memory=4096 --cpus=2

# 상태 확인
kubectl cluster-info
kubectl get nodes
```

---

## 25장. [실습] DaemonSet으로 Promtail 배포

### 25.1 promtail-daemonset.yaml

```yaml
apiVersion: apps/v1
kind: DaemonSet
metadata:
  name: promtail
  namespace: monitoring
spec:
  selector:
    matchLabels:
      app: promtail
  template:
    metadata:
      labels:
        app: promtail
    spec:
      serviceAccountName: promtail
      containers:
        - name: promtail
          image: grafana/promtail:2.9.4
          args:
            - -config.file=/etc/promtail/promtail.yml
          volumeMounts:
            - name: config
              mountPath: /etc/promtail
            - name: varlog
              mountPath: /var/log
              readOnly: true
            - name: varlibdockercontainers
              mountPath: /var/lib/docker/containers
              readOnly: true
          resources:
            requests:
              cpu: 50m
              memory: 64Mi
            limits:
              cpu: 200m
              memory: 128Mi
      volumes:
        - name: config
          configMap:
            name: promtail-config
        - name: varlog
          hostPath:
            path: /var/log
        - name: varlibdockercontainers
          hostPath:
            path: /var/lib/docker/containers
```

### 25.2 Promtail ConfigMap — K8s 자동 발견

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: promtail-config
  namespace: monitoring
data:
  promtail.yml: |
    server:
      http_listen_port: 9080
    
    clients:
      - url: http://loki:3100/loki/api/v1/push
    
    positions:
      filename: /tmp/positions.yaml
    
    scrape_configs:
      # ★ K8s Pod 로그 자동 발견
      - job_name: kubernetes-pods
        kubernetes_sd_configs:
          - role: pod
        
        relabel_configs:
          # Pod 이름을 라벨로
          - source_labels: [__meta_kubernetes_pod_name]
            target_label: pod
          # Namespace를 라벨로
          - source_labels: [__meta_kubernetes_namespace]
            target_label: namespace
          # 컨테이너 이름을 라벨로
          - source_labels: [__meta_kubernetes_pod_container_name]
            target_label: container
          # 앱 이름 (Deployment 라벨)
          - source_labels: [__meta_kubernetes_pod_label_app]
            target_label: app
        
        pipeline_stages:
          - cri: {}     # Container Runtime Interface 파서
          - multiline:
              firstline: '^\d{4}-\d{2}-\d{2}'
              max_wait_time: 3s
```

---

## 26장. [실습] K8s 메타데이터 자동 수집

```
K8s SD(Service Discovery)가 자동으로 제공하는 메타데이터:

__meta_kubernetes_pod_name         → Pod 이름
__meta_kubernetes_namespace        → 네임스페이스
__meta_kubernetes_pod_node_name    → 노드 이름
__meta_kubernetes_pod_container_name → 컨테이너 이름
__meta_kubernetes_pod_label_*      → Pod 라벨
__meta_kubernetes_pod_annotation_* → Pod 어노테이션

활용 예:
  Grafana에서 {namespace="production", app="waiting-service"} 검색
  → 프로덕션 네임스페이스의 waiting-service 로그만 즉시 필터링
```

---

## 27장. [실습] Helm Chart로 Loki Stack 한 번에 배포

```bash
# Helm 리포지토리 추가
helm repo add grafana https://grafana.github.io/helm-charts
helm repo update

# Loki Stack 설치 (Loki + Promtail + Grafana)
helm install loki-stack grafana/loki-stack \
  --namespace monitoring \
  --create-namespace \
  --set grafana.enabled=true \
  --set prometheus.enabled=false \
  --set loki.persistence.enabled=true \
  --set loki.persistence.size=10Gi

# 설치 확인
kubectl get pods -n monitoring
# NAME                              READY   STATUS
# loki-stack-0                      1/1     Running
# loki-stack-promtail-xxxxx         1/1     Running  (DaemonSet)
# loki-stack-grafana-xxxxx          1/1     Running

# Grafana 접속 (포트 포워딩)
kubectl port-forward svc/loki-stack-grafana 3000:80 -n monitoring
# → http://localhost:3000 (admin / kubectl get secret으로 비밀번호 확인)
```

---

## 28장. K8s 로깅 운영

```
로그 로테이션:
  K8s는 kubelet이 자동으로 로그 로테이션
  기본: 10MB × 5파일 (kubelet --container-log-max-size, --container-log-max-files)

리소스 제한 (DaemonSet):
  Promtail에 반드시 resources.limits 설정
  → 설정 안 하면 로그 폭증 시 Promtail이 노드 메모리를 다 먹음
  권장: CPU 200m, Memory 128Mi

네임스페이스 필터링:
  kube-system 등 시스템 네임스페이스 로그는 수집하지 않는 것이 일반적
  → Promtail relabel_configs에서 drop 처리
```

---

## 29장. [퀴즈] K8s 로깅 설계 문제

### 문제 1

```
10노드 K8s 클러스터에 Pod이 200개 동작 중입니다.
DaemonSet으로 Promtail을 배포하면 Promtail은 몇 개가 동작하나요?
Sidecar 패턴을 사용하면 몇 개가 필요한가요?
```

<details>
<summary>정답 보기</summary>

**DaemonSet: 10개** (노드당 1개). **Sidecar: 200개** (Pod당 1개). DaemonSet이 리소스 면에서 95% 효율적입니다. Sidecar는 특수한 로그 형식이 필요하거나 Pod별 맞춤 파이프라인이 필요한 경우에만 사용합니다.

</details>

---

# PART 4. Kafka 클러스터 (3 Broker) — 프로덕션 운영

---

## 30장. 단일 Kafka Broker의 한계

```
단일 Broker:
  ❌ Broker가 죽으면 → 모든 로그 전송 실패
  ❌ 디스크 고장 → 아직 소비 안 된 로그 유실
  ❌ Replication 불가 → 데이터 안전성 0
  ❌ 처리량 한계 → 수평 확장 불가

3 Broker 클러스터:
  ✅ 1대가 죽어도 나머지 2대가 서비스 지속
  ✅ Replication Factor=3 → 3곳에 데이터 복제
  ✅ 처리량 3배 (파티션 분산)
  ✅ 롤링 업데이트 가능 (1대씩 재시작)
```

---

## 31장. Kafka 클러스터의 핵심 — Replication과 Leader Election

### 31.1 Replication 동작 원리

```
Topic: app-logs, Partitions: 3, Replication Factor: 3

┌──────────── Broker 1 ──────────┐
│  Partition 0: Leader  ★        │
│  Partition 1: Follower         │
│  Partition 2: Follower         │
└────────────────────────────────┘

┌──────────── Broker 2 ──────────┐
│  Partition 0: Follower         │
│  Partition 1: Leader  ★        │
│  Partition 2: Follower         │
└────────────────────────────────┘

┌──────────── Broker 3 ──────────┐
│  Partition 0: Follower         │
│  Partition 1: Follower         │
│  Partition 2: Leader  ★        │
└────────────────────────────────┘

규칙:
  - 각 Partition의 Leader가 쓰기/읽기 처리
  - Follower는 Leader를 복제 (실시간)
  - Leader가 죽으면 Follower 중 하나가 새 Leader로 승격
  - Leader는 Broker별로 분산 (부하 분산)
```

### 31.2 ISR (In-Sync Replicas)

```
ISR = Leader와 동기화된 Replica 목록

Partition 0의 ISR: [Broker1(Leader), Broker2, Broker3]
  → 3개 모두 동기화 완료 ✅

Broker3 네트워크 지연 발생 시:
  ISR: [Broker1(Leader), Broker2]
  → Broker3이 ISR에서 제외됨
  → Broker3이 복구되면 다시 ISR에 합류

min.insync.replicas = 2:
  → ISR이 2개 이상이어야 쓰기 허용
  → ISR이 1개만 남으면 쓰기 거부 (데이터 안전성 보장)
```

---

## 32장. [실습] 3 Broker Kafka 클러스터 구성

```yaml
# docker/docker-compose-kafka-cluster.yml
version: '3.8'

services:
  zookeeper:
    image: confluentinc/cp-zookeeper:7.6.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
    networks: [kafka-cluster]

  kafka-1:
    image: confluentinc/cp-kafka:7.6.0
    depends_on: [zookeeper]
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka-1:29092
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 3
      KAFKA_DEFAULT_REPLICATION_FACTOR: 3
      KAFKA_MIN_INSYNC_REPLICAS: 2
      KAFKA_NUM_PARTITIONS: 6
      KAFKA_LOG_RETENTION_HOURS: 72
    networks: [kafka-cluster]

  kafka-2:
    image: confluentinc/cp-kafka:7.6.0
    depends_on: [zookeeper]
    environment:
      KAFKA_BROKER_ID: 2
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka-2:29092
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 3
      KAFKA_DEFAULT_REPLICATION_FACTOR: 3
      KAFKA_MIN_INSYNC_REPLICAS: 2
      KAFKA_NUM_PARTITIONS: 6
      KAFKA_LOG_RETENTION_HOURS: 72
    networks: [kafka-cluster]

  kafka-3:
    image: confluentinc/cp-kafka:7.6.0
    depends_on: [zookeeper]
    environment:
      KAFKA_BROKER_ID: 3
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka-3:29092
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 3
      KAFKA_DEFAULT_REPLICATION_FACTOR: 3
      KAFKA_MIN_INSYNC_REPLICAS: 2
      KAFKA_NUM_PARTITIONS: 6
      KAFKA_LOG_RETENTION_HOURS: 72
    networks: [kafka-cluster]

  kafka-ui:
    image: provectuslabs/kafka-ui:v0.7.1
    environment:
      KAFKA_CLUSTERS_0_NAME: log-cluster
      KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: kafka-1:29092,kafka-2:29092,kafka-3:29092
    ports: ["9093:8080"]
    depends_on: [kafka-1, kafka-2, kafka-3]
    networks: [kafka-cluster]

networks:
  kafka-cluster:
    driver: bridge
```

### 32.2 핵심 설정 설명

```yaml
KAFKA_DEFAULT_REPLICATION_FACTOR: 3
# → 새 Topic 생성 시 기본 복제 수
# → 3 = 데이터가 3개 Broker 모두에 복제됨

KAFKA_MIN_INSYNC_REPLICAS: 2
# → 쓰기 성공으로 인정하려면 최소 2개 Replica가 동기화되어야 함
# → acks=all + min.insync.replicas=2:
#     Broker 1대가 죽어도 데이터가 2곳에 있으므로 안전
#     Broker 2대가 동시에 죽으면 쓰기 불가 (안전 우선)

KAFKA_NUM_PARTITIONS: 6
# → 3 Broker × 파티션 2개씩 = 부하 균등 분산
# → Logstash를 최대 6대까지 병렬 소비 가능
```

---

## 33장. [실습] Topic Replication 확인

```bash
# Topic 생성
docker exec kafka-1 kafka-topics --create \
  --bootstrap-server kafka-1:29092 \
  --topic app-logs \
  --partitions 6 \
  --replication-factor 3

# Topic 상세 정보 확인
docker exec kafka-1 kafka-topics --describe \
  --bootstrap-server kafka-1:29092 \
  --topic app-logs

# 결과:
# Topic: app-logs  Partitions: 6  ReplicationFactor: 3
# Partition: 0  Leader: 1  Replicas: 1,2,3  ISR: 1,2,3
# Partition: 1  Leader: 2  Replicas: 2,3,1  ISR: 2,3,1
# Partition: 2  Leader: 3  Replicas: 3,1,2  ISR: 3,1,2
# ...

# → 모든 Partition이 3개의 Broker에 복제됨 ✅
# → ISR에 3개 모두 포함 = 완벽하게 동기화됨 ✅
```

---

## 34장. [실습] Broker 장애 시뮬레이션

```bash
# 1. 메시지 전송 (Producer)
docker exec kafka-1 kafka-console-producer \
  --bootstrap-server kafka-1:29092 \
  --topic app-logs \
  --property "acks=all"
> 메시지 1
> 메시지 2
> 메시지 3

# 2. Broker 2를 강제 종료
docker stop kafka-2

# 3. Topic 상태 확인
docker exec kafka-1 kafka-topics --describe \
  --bootstrap-server kafka-1:29092 \
  --topic app-logs
# → ISR에서 Broker 2가 빠짐
# → 하지만 Leader가 자동으로 재선출됨!

# 4. 메시지 계속 전송 (Broker 2 없이도 동작)
docker exec kafka-1 kafka-console-producer \
  --bootstrap-server kafka-1:29092 \
  --topic app-logs \
  --property "acks=all"
> 메시지 4 (Broker 2 없이도 전송 성공!)

# 5. 소비자에서 모든 메시지 확인
docker exec kafka-1 kafka-console-consumer \
  --bootstrap-server kafka-1:29092 \
  --topic app-logs \
  --from-beginning
# → 메시지 1, 2, 3, 4 모두 확인됨! 유실 없음! ✅

# 6. Broker 2 복구
docker start kafka-2
# → 자동으로 클러스터에 재합류 + ISR 복원
```

---

## 35장. Kafka 클러스터 모니터링

```bash
# 핵심 모니터링 지표:

# 1. Consumer Lag (가장 중요!)
docker exec kafka-1 kafka-consumer-groups \
  --bootstrap-server kafka-1:29092 \
  --group logstash-group \
  --describe
# → LAG 열이 계속 증가하면 소비자가 못 따라감

# 2. Under-Replicated Partitions (복제 지연)
# Kafka UI → Brokers → Under-replicated: 0이어야 정상

# 3. ISR Shrink/Expand (ISR 축소/확장 빈도)
# 자주 발생하면 네트워크 또는 디스크 문제

# 4. Active Controller Count
# 항상 1이어야 함 (0이면 클러스터 리더 없음 = 위험!)
```

---

## 36장. KRaft 모드 — Zookeeper 없는 Kafka

```
Kafka 3.4+ (2023):
  KRaft 모드 = Zookeeper 없이 Kafka 자체적으로 메타데이터 관리
  
  기존: Kafka + Zookeeper (2개 시스템)
  KRaft: Kafka만 (1개 시스템)
  
  장점:
    - 운영 복잡도 감소 (Zookeeper 관리 불필요)
    - 파티션 수 확장성 향상 (수백만 파티션 가능)
    - 장애 복구 속도 향상
  
  현재 상태:
    - Kafka 3.7+ 에서 프로덕션 레디
    - 새 클러스터는 KRaft 권장
    - 기존 Zookeeper 클러스터도 마이그레이션 가능

KRaft docker-compose (참고):
  environment:
    KAFKA_PROCESS_ROLES: broker,controller
    KAFKA_NODE_ID: 1
    KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka-1:9093,2@kafka-2:9093,3@kafka-3:9093
    KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
    # Zookeeper 설정 불필요!
```

---

## 37장. [퀴즈] Kafka 클러스터 운영 문제

### 문제 1

```
3 Broker 클러스터, Replication Factor=3, min.insync.replicas=2
acks=all로 설정된 Producer가 로그를 전송 중.

Broker 2대가 동시에 죽었습니다. 무슨 일이 일어나나요?
```

<details>
<summary>정답 보기</summary>

**Producer의 쓰기가 실패합니다.** `acks=all`은 모든 ISR에 쓰기가 완료되어야 성공으로 인정합니다. ISR에 1개만 남으면 `min.insync.replicas=2` 조건을 만족하지 못하므로 `NotEnoughReplicasException`이 발생합니다. 이것은 의도된 동작으로, 데이터 안전성을 위해 쓰기를 거부하는 것입니다. Filebeat는 Kafka 전송 실패 시 내부 큐에 보관하고 재시도하므로, Broker가 복구되면 밀린 로그가 전송됩니다.

</details>

### 문제 2

```
운영 중인 Kafka 클러스터의 특정 Consumer Group에서
Consumer Lag가 지속적으로 증가하고 있습니다.
3가지 원인과 해결 방법을 제시하세요.
```

<details>
<summary>정답 보기</summary>

**원인 1:** Logstash 처리 속도가 로그 생성 속도보다 느림. 해결: Logstash worker 수 증가 또는 인스턴스 추가. **원인 2:** Logstash의 filter가 너무 복잡 (grok 등). 해결: JSON 로그를 사용하여 grok 제거. **원인 3:** Elasticsearch 인덱싱이 느려서 Logstash output이 막힘. 해결: ES 성능 튜닝 또는 ES 노드 추가. 추가로, 파티션 수보다 Consumer 수가 적으면 병렬도가 제한되므로 Consumer 수 ≤ 파티션 수인지 확인합니다.

</details>

---

# PART 5. 최종 아키텍처와 마무리

---

## 38장. 전체 통합 아키텍처

```
┌─────────────────────────────────────────────────────────────────────┐
│                 차세대 Observability 아키텍처                         │
│                                                                      │
│  ┌──────────┐                                                        │
│  │App (K8s) │── stdout ──→ [Promtail DaemonSet] ──→ [Loki]          │
│  │+ OTel SDK│── OTLP ───→ [OTel Collector] ──→ [Tempo] (트레이스)   │
│  │+Micrometer│── OTLP ──→ [OTel Collector] ──→ [Mimir] (메트릭)     │
│  └──────────┘                                                        │
│                                                                      │
│  대용량 로그 파이프라인 (선택):                                        │
│  [App] → 파일 → [Filebeat] → [Kafka 3 Broker] → [Logstash] → [ES]  │
│                                                                      │
│  시각화:                                                              │
│  [Grafana] ←── Loki (로그) + Tempo (트레이스) + Mimir (메트릭)       │
│      │                                                               │
│      └──→ [Discord/Slack Alert]                                      │
│                                                                      │
│  ILM (ES 사용 시): Hot(7일) → Warm(30일) → Delete(90일)             │
│  Loki Retention: 90일 (S3에 저장, 비용 최소)                          │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 39장. 규모별 기술 선택 가이드

| 상황 | 로그 저장 | 수집 | 버퍼 | 트레이싱 | 메트릭 |
|------|---------|------|------|---------|--------|
| **소규모 (서버 1~10)** | Loki | Promtail/Loki4j | 불필요 | Jaeger | Prometheus |
| **중규모 (서버 10~50)** | Loki | Promtail | Kafka (선택) | Tempo | Prometheus |
| **대규모 (서버 50+)** | Loki + ES(하이브리드) | Filebeat + Promtail | Kafka 3 Broker | Tempo | Mimir |
| **K8s 환경** | Loki | Promtail DaemonSet | 불필요 | Tempo | Prometheus |
| **비용 최소화** | Loki (S3) | Promtail | 불필요 | Tempo (S3) | Mimir (S3) |
| **검색 속도 최우선** | ES | Filebeat | Kafka | Jaeger (ES) | Prometheus |

---

## 40장. 운영 체크리스트와 자주 하는 실수

### 운영 체크리스트

```
Loki:
  □ 라벨 카디널리티 확인 (총 Stream < 10,000)
  □ retention_period 설정
  □ S3 스토리지 구성 (운영 환경)
  □ chunk_idle_period / max_chunk_age 튜닝

OTel Collector:
  □ memory_limiter 필수 설정
  □ batch processor 설정
  □ 이중화 (2대 이상)
  □ 자체 메트릭 모니터링 (8888 포트)

K8s:
  □ Promtail DaemonSet resources.limits 설정
  □ kube-system 네임스페이스 로그 제외
  □ 로그 로테이션 정책 확인

Kafka 클러스터:
  □ Replication Factor ≥ 3
  □ min.insync.replicas = 2
  □ acks = all (Producer)
  □ Consumer Lag 모니터링
  □ 디스크 사용량 모니터링 (70% 미만)
```

### 자주 하는 실수 TOP 5

```
1위: Loki 라벨에 userId/requestId 넣기
    → Stream 폭발 → Loki 크래시
    → 해결: 로그 본문에만 포함, |= 로 검색

2위: OTel Collector에 memory_limiter 안 넣기
    → 트래픽 폭증 시 OOM 크래시
    → 해결: 반드시 memory_limiter 설정

3위: K8s DaemonSet에 resource limits 안 넣기
    → 로그 폭증 시 Promtail이 노드 메모리 독점
    → 해결: limits 설정 (CPU 200m, Memory 128Mi)

4위: Kafka 단일 Broker로 운영
    → Broker 장애 시 로그 유실
    → 해결: 최소 3 Broker + RF=3

5위: Loki와 ES를 동시에 운영하면서 비용 절감 안 됨
    → ES 없이 Loki만으로 충분한지 평가 필요
    → 전문 검색이 꼭 필요한 로그만 ES에 저장
```

---

> **끝.**
> 이 핸드북을 통해 차세대 로그 인프라의 4가지 핵심 기술을 모두 다루었습니다.
>
> Grafana Loki: ES 비용 80% 절감
> OTel Collector: Logstash 대체, 통합 파이프라인
> K8s 로깅: DaemonSet으로 컨테이너 환경 정복
> Kafka 클러스터: 3 Broker로 절대 유실 없는 파이프라인
>
> 이전 핸드북들과 합치면 Level 0(println)에서 Level 5(Full Observability)까지
> 완전한 로드맵을 갖추게 됩니다. 🚀
