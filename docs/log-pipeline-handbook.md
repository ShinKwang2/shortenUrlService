# 🔧 로그 파이프라인 고도화 완벽 가이드

### Filebeat · Kafka · Elasticsearch ILM · ES 클러스터 운영

> **"서버 100대의 로그를, 한 건의 유실 없이, 비용 효율적으로 관리하는 기술"**
> 이 핸드북을 마치면 프로덕션 로그 파이프라인을 설계하고 운영할 수 있습니다.

---

# 목차

## PART 1. 왜 로그 파이프라인을 고도화해야 하는가?
- 1장. 기본 핸드북의 아키텍처를 돌아보자
- 2장. 서버 1대에서 100대로 — 무엇이 달라지는가
- 3장. 실제 장애 사례 — 로그 유실이 일으킨 참사
- 4장. 고도화된 파이프라인의 전체 그림
- 5장. [퀴즈] 현재 아키텍처의 문제점 찾기

## PART 2. Filebeat — 경량 로그 수집기
- 6장. Logstash만으로는 왜 부족한가?
- 7장. Filebeat의 탄생 배경과 설계 철학
- 8장. Filebeat의 내부 동작 원리 — Harvester, Registry, Backpressure
- 9장. Filebeat 설정 완전 해부 — 모든 옵션 설명
- 10장. [실습] Filebeat 설치 및 Elasticsearch 직접 전송
- 11장. [실습] 멀티라인 로그 처리 — 스택트레이스 합치기
- 12장. [실습] JSON 로그 파싱
- 13장. Filebeat Modules — 사전 정의된 수집 설정
- 14장. [퀴즈] Filebeat 설정 문제

## PART 3. Kafka를 이용한 로그 버퍼링
- 15장. 왜 로그 파이프라인에 Kafka가 필요한가?
- 16장. Kafka의 핵심 개념 — Topic, Partition, Consumer Group
- 17장. Kafka 설정 완전 해부 — 로그 파이프라인에 최적화된 설정
- 18장. [실습] Kafka + Zookeeper 설치
- 19장. [실습] Filebeat → Kafka 연동
- 20장. [실습] Kafka → Logstash → Elasticsearch 파이프라인
- 21장. [실습] 전체 파이프라인 통합 테스트 — 장애 시뮬레이션
- 22장. Kafka 운영 — 모니터링, Consumer Lag, 파티션 리밸런싱
- 23장. [퀴즈] Kafka 설계 문제

## PART 4. Elasticsearch ILM (인덱스 생명주기 관리)
- 24장. 로그 인덱스를 관리하지 않으면 벌어지는 일
- 25장. ILM의 4단계 — Hot, Warm, Cold, Delete
- 26장. ILM 정책 설계 — 비즈니스 요구사항에 맞추기
- 27장. [실습] ILM 정책 생성 및 적용
- 28장. [실습] 인덱스 템플릿과 ILM 연결
- 29장. [실습] Rollover — 인덱스 자동 분할
- 30장. [실습] ILM 상태 모니터링 및 문제 해결
- 31장. [퀴즈] ILM 설계 문제

## PART 5. Elasticsearch 클러스터 운영
- 32장. 왜 클러스터가 필요한가? — 단일 노드의 한계
- 33장. ES 클러스터의 핵심 개념 — Node, Shard, Replica
- 34장. 노드 역할 — Master, Data, Coordinating, Ingest
- 35장. 샤드 전략 — 몇 개로 나누고, 몇 개를 복제할 것인가
- 36장. [실습] 3노드 클러스터 구성 (Docker)
- 37장. [실습] Hot-Warm-Cold 노드 역할 분리
- 38장. 클러스터 모니터링 — _cat API와 핵심 지표
- 39장. 클러스터 장애 대응 — Yellow/Red 상태 복구
- 40장. [퀴즈] 클러스터 운영 문제

## PART 6. 운영 환경 최종 아키텍처
- 41장. 규모별 아키텍처 설계 가이드
- 42장. 전체 docker-compose.yml — 프로덕션 파이프라인
- 43장. 운영 체크리스트와 자주 하는 실수
- 44장. 비용 최적화 전략
- 45장. 다음 단계 — Grafana Loki, OpenTelemetry Collector

---

# PART 1. 왜 로그 파이프라인을 고도화해야 하는가?

---

## 1장. 기본 핸드북의 아키텍처를 돌아보자

기본 핸드북에서 우리는 이런 아키텍처를 구축했습니다.

```
기본 핸드북의 아키텍처:

┌──────────────┐                ┌───────────┐              ┌────────────────┐
│ Spring Boot  │── TCP/JSON ──→│ Logstash   │── HTTP ───→ │ Elasticsearch  │
│  + Logback   │   (5044)      │            │  (9200)     │                │
│              │               │ - parse    │             │ - index        │
│ LogstashTcp  │               │ - filter   │             │ - search       │
│ Appender     │               │ - enrich   │             │                │
└──────────────┘               └───────────┘              └───────┬────────┘
                                                                   │
                                                                   ▼
                                                            ┌──────────┐
                                                            │  Kibana  │
                                                            └──────────┘

이 아키텍처는 서버 1~3대, 일 로그 1GB 이하에서는 충분합니다.
하지만 서비스가 성장하면 심각한 문제가 발생합니다.
```

---

## 2장. 서버 1대에서 100대로 — 무엇이 달라지는가

### 2.1 규모별 달라지는 것

| 항목 | 서버 1~3대 | 서버 10~30대 | 서버 50~100대 |
|------|-----------|-------------|-------------|
| 일일 로그량 | ~1GB | 10~50GB | 100GB~1TB |
| Logstash 부하 | 여유 | 조금 빠듯 | 감당 불가 |
| TCP 연결 수 | 3개 | 30개 | 100개 (위험) |
| 장애 영향 범위 | 작음 | 보통 | 로그 대량 유실 |
| ES 디스크 (1년) | ~365GB | ~18TB | ~365TB |
| 월 비용 (AWS) | ~$50 | ~$500 | ~$5,000+ |

### 2.2 구체적으로 무엇이 문제인가

```
문제 1: Logstash가 SPOF(단일 장애 지점)
─────────────────────────────────
  100대의 서버가 모두 하나의 Logstash에 TCP로 연결
  → Logstash가 1분만 죽어도 100대 서버의 로그가 유실
  → Logstash 업데이트/설정 변경 시마다 로그 유실 위험

문제 2: Logstash에 100개의 TCP 연결
─────────────────────────────────
  각 앱이 LogstashTcpSocketAppender로 직접 연결
  → 100개의 상시 TCP 연결 = Logstash의 메모리/CPU 부담
  → JVM 기반 Logstash의 GC 압력 증가

문제 3: 앱 성능에 영향
─────────────────────────────────
  앱이 Logstash에 직접 TCP 전송 → Logstash가 느려지면?
  → 앱의 로그 전송 스레드가 블로킹
  → 앱의 응답 시간까지 느려질 수 있음 (backpressure)

문제 4: Elasticsearch 디스크 무한 증가
─────────────────────────────────
  로그를 날짜별로 인덱스(app-logs-2024.01.15)를 생성
  → 삭제 정책이 없으면 디스크가 무한히 증가
  → 6개월 전 로그를 SSD에 보관할 필요가 있는가? → 비용 낭비

문제 5: Elasticsearch 단일 노드 한계
─────────────────────────────────
  하나의 ES 노드가 죽으면 → 모든 로그 검색 불가
  디스크 용량 한계 → 단일 서버 기준
  검색 성능 한계 → CPU/메모리 한 대 기준
```

### 2.3 문제와 해결 매핑

| 문제 | 해결 기술 | 핸드북 위치 |
|------|----------|-----------|
| Logstash SPOF + 앱 영향 | **Filebeat** (경량 에이전트) | PART 2 |
| 로그 유실 + 버퍼링 | **Kafka** (메시지 버퍼) | PART 3 |
| 디스크 무한 증가 + 비용 | **ES ILM** (생명주기 관리) | PART 4 |
| 단일 노드 한계 | **ES 클러스터** (분산 저장) | PART 5 |

---

## 3장. 실제 장애 사례 — 로그 유실이 일으킨 참사

```
📝 경험담:

2018년, 제가 운영하던 이커머스 서비스에서 블랙 프라이데이 이벤트가 있었습니다.
평소 대비 트래픽이 10배로 증가했고, 동시에 결제 오류가 발생하기 시작했습니다.

문제 상황:
  - 트래픽 10배 → 로그량도 10배로 증가
  - Logstash가 처리량 한계에 도달 → 입력 큐가 가득 참
  - 앱들이 Logstash에 TCP 전송 실패 → 재시도 반복
  - 앱의 로그 전송 스레드가 블로킹 → 앱 응답 지연 발생
  - 결제 서비스까지 느려짐 → 타임아웃 → 결제 실패

결과:
  - 30분간 결제 실패율 40%
  - 매출 손실 약 2억원 (추정)
  - 사후 분석 시 Logstash 재시작 전후의 로그가 30분 분량 유실
  - 장애 원인 분석에 3일 소요 (로그가 없으니까!)

교훈:
  1. 로그 파이프라인이 앱 성능에 영향을 주면 안 된다 → Filebeat
  2. 로그는 절대 유실되면 안 된다 → Kafka 버퍼
  3. 트래픽 폭증에도 파이프라인이 견뎌야 한다 → 확장 가능한 아키텍처
```

---

## 4장. 고도화된 파이프라인의 전체 그림

```
기본 핸드북 (Before):
  [App] ──TCP──→ [Logstash] ──→ [ES] ──→ [Kibana]

고도화 (After):
  [App]                                              
    │ (로그 파일 출력)                                  
    ▼                                                
  [Filebeat]        ← 각 서버에 설치 (15MB 메모리)      
    │ (Kafka 전송)                                     
    ▼                                                 
  [Kafka]           ← 로그 버퍼 (유실 방지, 10배 트래픽도 흡수)
    │ (Consumer)                                      
    ▼                                                 
  [Logstash]        ← 중앙 1~2대 (파싱/변환 전담)       
    │ (Output)                                        
    ▼                                                 
  [ES Cluster]      ← 3노드 이상 (HA + ILM)           
    │ Hot→Warm→Cold→Delete (자동 생명주기 관리)          
    ▼                                                 
  [Kibana]          ← 시각화                           

각 기술이 해결하는 문제:
  Filebeat:    앱에 영향 없는 경량 수집 + 파일 기반 안전성
  Kafka:       유실 방지 + 트래픽 흡수 + 다중 소비
  ES ILM:      비용 최적화 + 자동 보관/삭제
  ES Cluster:  고가용성 + 분산 저장/검색
```

---

## 5장. [퀴즈] 현재 아키텍처의 문제점 찾기

### 문제 1

```
현재 구성: [App] ── LogstashTcpAppender ──→ [Logstash] ──→ [ES]

Logstash 설정을 변경하기 위해 재시작해야 합니다.
재시작에 약 30초가 소요됩니다. 이때 발생하는 문제는?
```

<details>
<summary>정답 보기</summary>

30초 동안 앱에서 Logstash로의 TCP 연결이 끊깁니다. 이 기간 동안 앱이 전송하려는 로그는 유실되거나, 앱 내부 버퍼에 쌓여 메모리 압력이 발생합니다. LogstashTcpSocketAppender의 reconnectionDelay 설정에 따라 재연결을 시도하지만, 그 사이의 로그는 유실됩니다. Kafka를 중간에 두면 Logstash가 재시작되어도 Kafka에 로그가 보관되므로 유실이 없습니다.

</details>

### 문제 2

```
ES에 매일 5GB의 로그가 쌓입니다. 인덱스 삭제 정책이 없습니다.
1년 후 상태를 예측해보세요.
```

<details>
<summary>정답 보기</summary>

5GB × 365일 = 약 1.8TB의 데이터가 쌓입니다. ES의 역인덱스 오버헤드를 포함하면 실제로는 약 2.7~3.6TB의 디스크를 사용합니다. 인덱스 수는 365개이며, 각 인덱스의 샤드 수까지 합치면 수천 개의 샤드가 생깁니다. ES는 샤드가 많을수록 Master 노드의 클러스터 상태 관리 부담이 커지며, 검색 성능도 저하됩니다. ILM으로 30일 후 Warm, 90일 후 Cold, 180일 후 Delete 정책을 적용하면 디스크 사용량을 크게 줄일 수 있습니다.

</details>

---

# PART 2. Filebeat — 경량 로그 수집기

---

## 6장. Logstash만으로는 왜 부족한가?

### 6.1 Logstash의 장점과 단점

```
Logstash의 장점:
  ✅ 강력한 파싱 (grok, dissect, json)
  ✅ 다양한 Input/Output 플러그인 (200개+)
  ✅ 복잡한 데이터 변환 가능

Logstash의 단점:
  ❌ JVM 기반 → 최소 256MB, 보통 512MB~1GB 메모리
  ❌ 시작 시간이 느림 (10~30초)
  ❌ 각 서버에 설치하기에는 너무 무거움
  ❌ 파일 읽기 기능이 Filebeat보다 불안정
```

### 6.2 역할 분리의 필요성

```
잘못된 구성: 각 서버에 Logstash 설치
  서버 100대 × Logstash 512MB = 50GB 메모리 낭비!

올바른 구성: 역할 분리
  각 서버: Filebeat (15MB) ← 수집만 담당
  중앙 서버: Logstash (1~2대) ← 파싱/변환 담당

결과:
  서버 100대 × Filebeat 15MB = 1.5GB 메모리 (97% 절감!)
  Logstash는 중앙에 2대만 운영
```

---

## 7장. Filebeat의 탄생 배경과 설계 철학

### 7.1 Beats 패밀리

```
Elastic에서는 Logstash의 무거움을 해결하기 위해
"Beats"라는 경량 수집기 시리즈를 만들었습니다.

Filebeat:    로그 파일 수집 ← 가장 많이 사용
Metricbeat:  시스템/앱 메트릭 수집
Packetbeat:  네트워크 패킷 분석
Heartbeat:   서비스 가용성 모니터링
Auditbeat:   보안 감사 데이터 수집

모든 Beats의 공통점:
  - Go 언어로 작성 → JVM 불필요
  - 경량 (10~30MB 메모리)
  - 설정이 간단 (YAML)
  - Elasticsearch, Logstash, Kafka로 출력 가능
```

### 7.2 Filebeat의 설계 철학

```
철학 1: "한 가지 일을 잘 하자"
  → 파일을 읽어서 전송하는 것에만 집중
  → 복잡한 파싱은 Logstash에 위임

철학 2: "절대 로그를 잃어버리지 않는다"
  → Registry로 읽은 위치를 기록
  → 재시작해도 마지막 위치부터 이어서 읽음
  → Backpressure 메커니즘으로 전송 실패 시 재시도

철학 3: "호스트에 최소한의 영향을 준다"
  → 15~30MB 메모리, CPU 거의 사용 안 함
  → 앱 성능에 영향 제로
```

---

## 8장. Filebeat의 내부 동작 원리

### 8.1 아키텍처

```
┌─────────────────────────────────────────────────────┐
│                    Filebeat 내부                       │
│                                                       │
│  ┌──────────────────────────────────────────────────┐ │
│  │                Input Manager                      │ │
│  │                                                   │ │
│  │  ┌───────────┐  ┌───────────┐  ┌───────────┐    │ │
│  │  │Harvester 1│  │Harvester 2│  │Harvester 3│    │ │
│  │  │(app.log)  │  │(error.log)│  │(access.log│    │ │
│  │  └─────┬─────┘  └─────┬─────┘  └─────┬─────┘    │ │
│  │        │               │               │          │ │
│  │        └───────────────┼───────────────┘          │ │
│  │                        ▼                          │ │
│  │                 ┌────────────┐                    │ │
│  │                 │   Spooler  │                    │ │
│  │                 │  (내부 큐)  │                    │ │
│  │                 └──────┬─────┘                    │ │
│  └────────────────────────┼──────────────────────────┘ │
│                           ▼                            │
│  ┌──────────────────────────────────────────────────┐ │
│  │              Output (전송)                         │ │
│  │  → Elasticsearch / Kafka / Logstash              │ │
│  └──────────────────────────────────────────────────┘ │
│                                                       │
│  ┌──────────────────────────────────────────────────┐ │
│  │              Registry (위치 기록)                   │ │
│  │  app.log: offset=154823                           │ │
│  │  error.log: offset=8891                           │ │
│  └──────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────┘
```

### 8.2 핵심 컴포넌트 설명

```
Harvester (수확기):
  - 파일 하나당 하나의 Harvester가 할당됨
  - 파일을 한 줄씩 읽음 (tail -f와 비슷)
  - 새 줄이 추가되면 즉시 읽음
  - 파일이 로테이션(이름 변경)되어도 추적

Registry (레지스트리):
  - 각 파일을 "어디까지 읽었는지" offset으로 기록
  - 디스크에 영속화됨 (data/registry/filebeat 디렉토리)
  - Filebeat 재시작 시 Registry에서 마지막 offset을 읽어 이어서 수집
  - ★ 이것이 "로그 유실 방지"의 핵심 메커니즘!

Spooler (내부 큐):
  - Harvester가 읽은 이벤트를 모아서 배치 전송
  - 메모리 기반 (기본) 또는 디스크 기반 큐 선택 가능
  
Backpressure (배압):
  - Output(Kafka/ES)이 느려지면 → Spooler가 가득 참
  - Spooler가 가득 차면 → Harvester가 읽기를 멈춤
  - Output이 복구되면 → 다시 읽기 시작
  - → 앱에는 전혀 영향 없음! (앱은 그냥 파일에 계속 씀)
```

### 8.3 Logstash TCP 방식과의 근본적 차이

```
LogstashTcpSocketAppender 방식:
  앱 → (TCP 소켓) → Logstash
  
  앱이 직접 TCP 전송 → 전송 실패 시 앱이 영향을 받음
  Logstash가 느려지면 → 앱의 스레드가 블로킹
  Logstash가 죽으면 → 로그 유실

Filebeat 방식:
  앱 → (파일에 쓰기) → [로그 파일] ← Filebeat가 읽기 → Kafka/ES
  
  앱은 파일에 쓰기만 함 → Filebeat 상태와 무관
  Filebeat가 느려지면 → 파일에 로그가 계속 쌓이기만 함 (앱 무관)
  Filebeat가 죽으면 → 파일에 로그 보존 + Registry로 이어서 읽기
  → 앱 성능에 절대 영향 없음!
```

---

## 9장. Filebeat 설정 완전 해부

### 9.1 filebeat.yml 전체 구조

```yaml
# ═══ INPUT: 어디에서 읽을 것인가 ═══
filebeat.inputs:
  - type: filestream
    ...

# ═══ PROCESSORS: 간단한 가공 ═══
processors:
  - add_host_metadata: ~
  ...

# ═══ OUTPUT: 어디로 보낼 것인가 ═══
output.elasticsearch:   # 또는 output.kafka, output.logstash
  ...

# ═══ LOGGING: Filebeat 자체 로깅 ═══
logging.level: info

# ═══ SETUP: 초기 설정 ═══
setup.ilm.enabled: false
```

### 9.2 Input 설정 — 모든 옵션 상세 설명

```yaml
filebeat.inputs:

  - type: filestream          # ★ 8.x 권장 타입 (구 'log' 타입 대체)
    id: waiting-app-logs      # 고유 ID (필수! 없으면 Registry 충돌)
    enabled: true             # 이 Input 활성화 여부
    
    # ── 파일 경로 ──
    paths:
      - /var/log/app/*.log              # 와일드카드 지원
      - /var/log/app/**/*.log           # 재귀 디렉토리 탐색
      # ※ 심볼릭 링크도 따라감 (기본값)

    # ── 파일 발견 설정 ──
    prospector.scanner:
      check_interval: 10s     # 새 파일 스캔 주기 (기본 10초)
                              # 줄이면 반응 빠르지만 CPU 약간 증가
      # symlinks: true        # 심볼릭 링크 추적 (기본 true)
    
    # ── 파일 나이 설정 ──
    ignore_older: 24h         # 24시간 이상 된 파일은 무시
                              # Filebeat 재시작 시 오래된 파일 재수집 방지
                              # ★ 운영 환경에서 반드시 설정!
    
    # ── 파일 닫기 설정 ──
    close.on_state_change:
      inactive: 5m            # 5분간 새 로그 없으면 파일 핸들 닫기
                              # 서버의 fd(file descriptor) 부족 방지
                              # ★ 로그 파일이 많은 서버에서 중요
    
    close.reader:
      after_interval: 15m     # 15분 후 강제 닫기 (로테이션된 파일용)
    
    # ── 읽기 설정 ──
    # file_identity.native: ~   # inode 기반 파일 식별 (Linux 기본)
    
    # ── 멀티라인 (스택트레이스) ──
    parsers:
      - multiline:
          type: pattern
          pattern: '^\d{4}-\d{2}-\d{2}'  # 날짜로 시작하면 새 이벤트
          negate: true                    # 패턴 불일치 시 이전에 합침
          match: after                    # 이전 이벤트 '뒤에' 합침
          max_lines: 100                  # 최대 합칠 줄 수
          timeout: 5s                     # 이 시간 후 합치기 종료
    
    # ── 필드 추가 ──
    fields:
      service: waiting-service
      env: prod
      team: backend
    fields_under_root: true   # fields를 최상위 레벨로 올림
                              # false면: fields.service, fields.env
                              # true면:  service, env (Root에 바로)
    
    # ── 태그 추가 ──
    tags: ["app-log", "waiting"]
```

### 9.3 Output 설정 — 3가지 방식 비교

```yaml
# ═══ 방식 1: Elasticsearch 직접 전송 ═══
# 용도: 소규모, 간단한 구성, 파싱이 필요 없을 때
output.elasticsearch:
  hosts: ["http://elasticsearch:9200"]
  index: "filebeat-waiting-%{+yyyy.MM.dd}"    # 날짜별 인덱스
  username: "elastic"                          # 운영 시 인증
  password: "${ES_PASSWORD}"                   # 환경변수 참조
  # ── 성능 설정 ──
  bulk_max_size: 50           # 한 번에 보낼 최대 이벤트 수
  worker: 1                   # 전송 워커 수 (병렬 전송)
  # ── 재시도 설정 ──
  max_retries: 3              # 최대 재시도 횟수

# ═══ 방식 2: Logstash로 전송 ═══
# 용도: 복잡한 파싱/변환이 필요할 때
output.logstash:
  hosts: ["logstash:5044"]
  # ── 로드밸런싱 ──
  loadbalance: true           # 여러 Logstash에 분산
  # ── 재시도 설정 ──
  pipelining: 0               # 파이프라이닝 비활성화 (안정성 우선)

# ═══ 방식 3: Kafka로 전송 ★ (권장, 대규모) ═══
# 용도: 유실 방지, 대규모 트래픽, 다중 소비자
output.kafka:
  hosts: ["kafka-1:9092", "kafka-2:9092", "kafka-3:9092"]  # 브로커 목록
  topic: "app-logs"                     # Kafka Topic 이름
  
  # ── 파티셔닝 ──
  partition.round_robin:                # 라운드 로빈 분배
    reachable_only: true                # 도달 가능한 파티션에만 전송
  # partition.hash:                     # 특정 필드 기준으로 파티셔닝
  #   hash: ["fields.service"]          # 같은 서비스의 로그는 같은 파티션
  
  # ── 안정성 ──
  required_acks: 1              # 0: fire-and-forget (유실 가능)
                                # 1: 리더에게만 ACK ★ (권장)
                                # -1: 모든 replica ACK (가장 안전, 느림)
  
  # ── 성능 ──
  compression: gzip             # 압축 (네트워크 대역폭 절약)
  max_message_bytes: 1048576    # 메시지 최대 크기 1MB
  
  # ── 재시도 ──
  max_retries: 3
  backoff:
    init: 1s                    # 초기 재시도 대기
    max: 60s                    # 최대 재시도 대기
```

### 9.4 Processors — 간단한 가공

```yaml
processors:
  # 호스트 메타데이터 추가 (호스트명, IP, OS 등)
  - add_host_metadata:
      when.not.contains.tags: forwarded
  
  # Docker 메타데이터 추가 (컨테이너 환경에서)
  # - add_docker_metadata: ~
  
  # Kubernetes 메타데이터 추가 (K8s 환경에서)
  # - add_kubernetes_metadata: ~
  
  # 불필요한 필드 제거 (전송 데이터량 감소)
  - drop_fields:
      fields: ["agent.ephemeral_id", "agent.id", "agent.version",
               "agent.name", "ecs.version", "input.type", "log.offset"]
      ignore_missing: true
  
  # 특정 로그 제외 (health check 등)
  - drop_event:
      when:
        contains:
          message: "GET /actuator/health"
  
  # 조건부 필드 추가
  - add_fields:
      when:
        contains:
          message: "ERROR"
      target: ""
      fields:
        alert_level: high
```

---

## 10장. [실습] Filebeat 설치 및 Elasticsearch 직접 전송

### 10.1 프로젝트 구조

```
log-pipeline/
├── docker/
│   ├── docker-compose.yml
│   ├── filebeat/
│   │   └── filebeat.yml
│   └── logstash/
│       └── pipeline/
│           └── logstash.conf
├── waiting-service/
│   ├── src/
│   └── build.gradle
└── logs/                    ← 앱 로그가 여기에 생성됨
```

### 10.2 docker-compose.yml

```yaml
version: '3.8'

services:
  # ═══ Elasticsearch ═══
  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.12.0
    container_name: elasticsearch
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
      - "ES_JAVA_OPTS=-Xms512m -Xmx512m"
    ports:
      - "9200:9200"
    volumes:
      - es-data:/usr/share/elasticsearch/data
    networks:
      - pipeline-network
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:9200/_cluster/health || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 10

  # ═══ Kibana ═══
  kibana:
    image: docker.elastic.co/kibana/kibana:8.12.0
    container_name: kibana
    environment:
      - ELASTICSEARCH_HOSTS=http://elasticsearch:9200
    ports:
      - "5601:5601"
    depends_on:
      elasticsearch:
        condition: service_healthy
    networks:
      - pipeline-network

  # ═══ Filebeat ═══
  filebeat:
    image: docker.elastic.co/beats/filebeat:8.12.0
    container_name: filebeat
    user: root                 # 호스트 로그 파일 읽기 권한
    command: filebeat -e -strict.perms=false  # -e: 콘솔 로그 출력
    volumes:
      - ./filebeat/filebeat.yml:/usr/share/filebeat/filebeat.yml:ro
      - ../logs:/var/log/app:ro              # 앱 로그 디렉토리
      - filebeat-data:/usr/share/filebeat/data  # Registry 영속화
    depends_on:
      elasticsearch:
        condition: service_healthy
    networks:
      - pipeline-network

volumes:
  es-data:
  filebeat-data:

networks:
  pipeline-network:
    driver: bridge
```

### 10.3 filebeat.yml (최소 구성)

```yaml
# docker/filebeat/filebeat.yml

filebeat.inputs:
  - type: filestream
    id: app-logs
    enabled: true
    paths:
      - /var/log/app/*.log
    fields:
      service: waiting-service
      env: local
    fields_under_root: true
    parsers:
      - multiline:
          type: pattern
          pattern: '^\d{4}-\d{2}-\d{2}'
          negate: true
          match: after

output.elasticsearch:
  hosts: ["http://elasticsearch:9200"]
  index: "filebeat-waiting-%{+yyyy.MM.dd}"

# ES 기본 인덱스 템플릿/ILM 비활성화 (직접 관리)
setup.ilm.enabled: false
setup.template.enabled: false

logging.level: info
```

### 10.4 앱의 로그가 파일로 출력되는지 확인

```yaml
# waiting-service/src/main/resources/application.yml
logging:
  file:
    path: ./logs                    # 로그 파일 경로
    name: ./logs/waiting-service.log  # 로그 파일명
  pattern:
    file: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [traceId=%X{traceId:-none}] %-5level %logger{36} - %msg%n"
    console: "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{20} - %msg%n"
```

### 10.5 실행 및 확인

```bash
# 1. 인프라 시작
cd docker && docker compose up -d

# 2. Spring Boot 앱 실행 (로그가 logs/ 폴더에 생성됨)
cd ../waiting-service && ./gradlew bootRun

# 3. 테스트 요청
curl -X POST http://localhost:8080/api/waiting \
  -H "Content-Type: application/json" \
  -d '{"userId": 1, "restaurantId": 100}'

# 4. Filebeat가 로그를 읽었는지 확인
docker logs filebeat 2>&1 | tail -5
# → "Harvester started for file: /var/log/app/waiting-service.log"

# 5. Elasticsearch에 저장되었는지 확인
curl "http://localhost:9200/filebeat-waiting-*/_count"
# → {"count": N}

# 6. 실제 문서 확인
curl "http://localhost:9200/filebeat-waiting-*/_search?pretty&size=1"
```

---

## 11장. [실습] 멀티라인 로그 처리

### 11.1 문제 상황 만들기

```java
// WaitingService에 에러 발생 코드 추가
@GetMapping("/api/waiting/error-test")
public void errorTest() {
    try {
        String s = null;
        s.length();  // NullPointerException 발생!
    } catch (Exception e) {
        log.error("의도적 에러 발생", e);
        throw new RuntimeException("테스트 에러", e);
    }
}
```

### 11.2 에러 발생 후 로그 확인

```bash
curl http://localhost:8080/api/waiting/error-test

# logs/waiting-service.log에 이런 로그가 남음:
# 2024-01-15 14:30:01.123 [exec-1] ERROR c.e.w.c.WaitingController - 의도적 에러 발생
# java.lang.NullPointerException: null
#     at c.e.w.c.WaitingController.errorTest(WaitingController.java:52)
#     at sun.reflect.NativeMethodAccessorImpl.invoke(Unknown Source)
#     ... 30 more
```

### 11.3 멀티라인 설정이 없으면?

```
ES에 저장된 결과 (잘못됨):
  문서 1: "2024-01-15 14:30:01.123 ... 의도적 에러 발생"
  문서 2: "java.lang.NullPointerException: null"     ← 별도 문서!
  문서 3: "    at c.e.w.c.WaitingController..."       ← 별도 문서!
  
→ 스택트레이스가 5개의 별도 문서로 쪼개져서 의미 없음
```

### 11.4 멀티라인 설정이 있으면?

```yaml
parsers:
  - multiline:
      type: pattern
      pattern: '^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}'  # 타임스탬프 패턴
      negate: true       # 이 패턴과 "일치하지 않는" 줄을
      match: after       # 이전 이벤트 "뒤에" 합침
      max_lines: 100     # 최대 100줄까지 합침
      timeout: 5s        # 5초간 새 줄이 없으면 합치기 종료
```

```
ES에 저장된 결과 (올바름):
  문서 1:
    message: "2024-01-15 14:30:01.123 ... 의도적 에러 발생\n
              java.lang.NullPointerException: null\n
              at c.e.w.c.WaitingController.errorTest(...)\n
              ... 30 more"
    
→ 하나의 문서에 전체 스택트레이스가 포함됨 ✅
```

---

## 12장. [실습] JSON 로그 파싱

### 12.1 앱에서 JSON 로그 출력 (LogstashEncoder 사용)

```xml
<!-- logback-spring.xml -->
<appender name="JSON_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <file>logs/waiting-service.json</file>
    <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
        <fileNamePattern>logs/archived/waiting-service.%d{yyyy-MM-dd}.%i.json.gz</fileNamePattern>
        <maxFileSize>100MB</maxFileSize>
        <maxHistory>30</maxHistory>
    </rollingPolicy>
    <encoder class="net.logstash.logback.encoder.LogstashEncoder">
        <customFields>{"service":"waiting-service"}</customFields>
        <includeMdcKeyName>traceId</includeMdcKeyName>
        <includeMdcKeyName>spanId</includeMdcKeyName>
    </encoder>
</appender>
```

### 12.2 Filebeat JSON 파싱 설정

```yaml
filebeat.inputs:
  - type: filestream
    id: app-json-logs
    paths:
      - /var/log/app/*.json
    
    parsers:
      # JSON 라인 파싱 (각 줄이 하나의 JSON 객체)
      - ndjson:
          target: ""              # JSON 필드를 최상위로 올림
          add_error_key: true     # 파싱 실패 시 error.* 필드 추가
          overwrite_keys: true    # 기존 필드 덮어쓰기
          message_key: message    # message 필드를 메인 메시지로
    
    # ★ JSON 로그는 멀티라인이 필요 없음!
    # JSON은 한 줄에 하나의 이벤트가 완전히 들어있으므로.
    
    fields:
      log_format: json
    fields_under_root: true
```

### 12.3 JSON 로그의 장점

```
Plain Text 로그:
  2024-01-15 14:30:01.123 [exec-1] INFO c.e.w.s.WaitingService - 웨이팅 등록 완료 - id: 1
  → Logstash에서 grok으로 파싱 필요
  → grok 패턴이 복잡하고, 패턴 불일치 시 파싱 실패

JSON 로그:
  {"@timestamp":"2024-01-15T14:30:01.123","level":"INFO","message":"웨이팅 등록 완료 - id: 1"}
  → 파싱 불필요! 이미 구조화되어 있음
  → Filebeat가 바로 ES에 저장 가능
  → Logstash 없이도 검색/필터링 가능

★ 권장: 운영 환경에서는 JSON 로그를 사용하세요.
   Logstash의 grok 파싱을 제거할 수 있어 파이프라인이 단순해집니다.
```

---

## 13장. Filebeat Modules

```
Filebeat Modules = 사전 정의된 수집 설정 + 대시보드

자주 사용하는 Modules:
  system:   /var/log/syslog, /var/log/auth.log
  nginx:    access.log, error.log
  apache:   access.log, error.log
  mysql:    slowquery.log, error.log
  docker:   컨테이너 로그

사용법:
  filebeat modules enable nginx
  filebeat modules enable system

→ Input 설정, grok 패턴, Kibana 대시보드가 자동 설정됨
→ 앱 로그가 아닌 인프라 로그 수집에 유용
```

---

## 14장. [퀴즈] Filebeat 설정 문제

### 문제 1

```yaml
filebeat.inputs:
  - type: filestream
    paths:
      - /var/log/app/*.log
    ignore_older: 1h
```

```
서버 점검으로 Filebeat가 2시간 동안 꺼져있었습니다.
다시 시작하면 2시간 동안의 로그를 수집할 수 있을까요?
```

<details>
<summary>정답 보기</summary>

**일부만 수집됩니다.** `ignore_older: 1h` 설정 때문에 1시간 이상 된 로그 파일은 무시합니다. 하지만 이미 Registry에 기록된 파일(이전에 읽기 시작한 파일)은 `ignore_older`와 무관하게 마지막 offset부터 이어서 읽습니다. 새로 생성된 파일 중 1시간 이상 된 것만 무시됩니다. 운영 환경에서는 `ignore_older: 24h` 이상으로 설정하는 것이 안전합니다.

</details>

### 문제 2

```yaml
output.kafka:
  hosts: ["kafka:9092"]
  topic: "app-logs"
  required_acks: 0
```

```
이 설정의 문제점은 무엇인가요?
```

<details>
<summary>정답 보기</summary>

`required_acks: 0`은 "fire-and-forget" 모드입니다. Kafka 브로커에 메시지를 보내고 ACK를 기다리지 않으므로, 네트워크 문제나 브로커 장애 시 로그가 유실될 수 있습니다. 로그 유실을 방지하려면 `required_acks: 1` (리더 ACK)로 설정해야 합니다. 가장 안전한 `-1`(모든 replica ACK)은 성능 저하가 있으므로, 일반적으로 `1`이 권장됩니다.

</details>

---

# PART 3. Kafka를 이용한 로그 버퍼링

---

## 15장. 왜 로그 파이프라인에 Kafka가 필요한가?

### 15.1 Kafka가 없는 파이프라인의 약점

```
[Filebeat] → [Logstash] → [Elasticsearch]

시나리오 1: Logstash 재시작
  → Filebeat가 Logstash에 전송 실패
  → Filebeat 내부 큐에 쌓임 (메모리 제한)
  → 큐가 가득 차면 Filebeat가 파일 읽기를 중단
  → Logstash 복구 후 이어서 전송하지만, 순서 보장이 안 될 수 있음

시나리오 2: Elasticsearch 장애 (5분간)
  → Logstash 출력 실패 → Logstash 내부 큐에 쌓임
  → Logstash 큐도 가득 차면 → Filebeat로 backpressure 전달
  → 전체 파이프라인이 정체

시나리오 3: 이벤트 트래픽 10배 폭증
  → Logstash가 감당 불가
  → Filebeat backpressure 발생 → 로그 수집 지연
```

### 15.2 Kafka를 넣으면

```
[Filebeat] → [Kafka] → [Logstash] → [Elasticsearch]

시나리오 1: Logstash 재시작
  → Filebeat → Kafka에 정상 전송 (영향 없음!)
  → Kafka에 로그가 보관됨
  → Logstash 복구 후 Kafka에서 이어서 소비 → 유실 0

시나리오 2: Elasticsearch 장애 (5분간)
  → Filebeat → Kafka → 정상 (영향 없음!)
  → Logstash → ES 전송 실패 → 소비를 멈춤
  → Kafka에 로그가 계속 쌓임 (디스크에 영속화)
  → ES 복구 후 → Logstash가 밀린 로그를 소비 → 유실 0

시나리오 3: 이벤트 트래픽 10배 폭증
  → Kafka가 초당 수백만 메시지 처리 가능 → 흡수
  → Logstash를 여러 대로 스케일 아웃 → 병렬 소비
  → 파이프라인이 견딤
```

### 15.3 Kafka의 로그 파이프라인에서의 역할 정리

```
역할 1: 버퍼 (Buffer)
  → 생산자와 소비자 사이의 속도 차이를 흡수
  
역할 2: 안전 저장소 (Durability)
  → 디스크에 영속화 → 재시작해도 데이터 보존
  
역할 3: 병렬 처리 (Parallelism)
  → 파티션으로 데이터를 분산 → 여러 소비자가 병렬 처리

역할 4: 디커플링 (Decoupling)
  → 생산자(Filebeat)와 소비자(Logstash)가 서로 독립
  → 한쪽이 죽어도 다른 쪽에 영향 없음

역할 5: 다중 소비 (Multiple Consumers)
  → 같은 로그를 여러 용도로 사용 가능
  → Consumer Group A: Logstash → ES (검색용)
  → Consumer Group B: Spark → 분석 (ML용)
  → Consumer Group C: Alert → 알림 (실시간 모니터링용)
```

---

## 16장. Kafka의 핵심 개념

### 16.1 Topic과 Partition

```
Topic = 로그의 카테고리 (RDBMS의 테이블과 비슷)
  예: "app-logs", "error-logs", "access-logs"

Partition = Topic 내 병렬 처리 단위
  하나의 Topic은 여러 개의 Partition으로 나뉨

┌─────────────────────────────────────────────────────────┐
│  Topic: app-logs                                         │
│                                                          │
│  Partition 0: [msg0][msg3][msg6][msg9] [msg12]...       │
│  Partition 1: [msg1][msg4][msg7][msg10][msg13]...       │
│  Partition 2: [msg2][msg5][msg8][msg11][msg14]...       │
│                                                          │
│  각 Partition 안에서는 순서가 보장됨 (FIFO)               │
│  Partition 간에는 순서가 보장되지 않음                     │
└─────────────────────────────────────────────────────────┘

파티션 수 결정 기준:
  파티션 수 = 최대 병렬 소비자 수
  
  예: 파티션 3개 → Logstash 최대 3대로 병렬 소비 가능
      파티션 6개 → Logstash 최대 6대로 병렬 소비 가능
      
  ★ 파티션은 나중에 늘릴 수 있지만 줄일 수 없음!
  ★ 처음에 넉넉하게 잡되, 너무 많으면 오버헤드 발생
  ★ 일반적으로 6~12개가 적당
```

### 16.2 Consumer Group

```
Consumer Group = 같은 Topic을 소비하는 소비자 그룹

그룹 내 소비자들은 파티션을 "나눠서" 소비:
  Partition 0 → Logstash #1
  Partition 1 → Logstash #2
  Partition 2 → Logstash #3
  → 같은 메시지를 중복 처리하지 않음

다른 그룹은 같은 메시지를 "각각" 소비:
  Group "logstash-group": Logstash #1, #2, #3 → ES 저장
  Group "alert-group": Alert Service → 에러 알림
  → 같은 로그를 서로 다른 용도로 사용
```

### 16.3 Offset

```
Offset = 각 Consumer가 파티션에서 읽은 위치

Partition 0: [msg0][msg1][msg2][msg3][msg4][msg5]
                                      ↑
                              Consumer의 현재 Offset = 3
                              (msg0~2는 읽음, msg3부터 읽을 차례)

Kafka가 Offset을 관리 → Consumer 재시작 시 마지막 위치부터 이어서 읽음
(Filebeat의 Registry와 같은 역할!)
```

---

## 17장. Kafka 설정 완전 해부

### 17.1 로그 파이프라인에 최적화된 Kafka 설정

```yaml
# docker-compose.yml 환경변수로 설정

KAFKA_LOG_RETENTION_HOURS: 72        # ★ 로그 보관 기간: 3일
# → Logstash가 3일 안에 소비하지 못하면 삭제됨
# → 일반 메시지 큐에서는 짧게(1시간) 잡지만,
#    로그 파이프라인에서는 장애 복구 시간을 고려해 길게 잡음
# → 운영 환경: 24~72시간 권장

KAFKA_LOG_RETENTION_BYTES: 5368709120  # 파티션당 최대 5GB
# → 보관 기간 + 크기 중 먼저 도달한 조건으로 삭제
# → -1이면 크기 제한 없음 (기간으로만 관리)

KAFKA_NUM_PARTITIONS: 6              # ★ 기본 파티션 수
# → 새 Topic 자동 생성 시 적용
# → Logstash를 최대 6대까지 병렬 운영 가능
# → 운영 환경: 6~12 권장

KAFKA_LOG_SEGMENT_BYTES: 1073741824  # 세그먼트 크기 1GB
# → Kafka는 로그를 세그먼트 단위로 관리
# → 세그먼트가 닫혀야 retention 정책이 적용됨

KAFKA_MESSAGE_MAX_BYTES: 10485760    # 메시지 최대 10MB
# → 스택트레이스가 포함된 큰 로그 메시지를 위해

KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1  # 개발: 1, 운영: 3
# → Consumer offset 저장 토픽의 복제 수
# → 운영 환경에서는 반드시 3 (브로커 수와 동일)

KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"    # Topic 자동 생성
# → Filebeat가 전송할 때 Topic이 없으면 자동 생성
# → 운영 환경에서는 false로 두고 수동 생성이 안전
```

---

## 18장. [실습] Kafka + Zookeeper 설치

### 18.1 docker-compose.yml에 Kafka 추가

```yaml
  # ═══ Zookeeper (Kafka 메타데이터 관리) ═══
  zookeeper:
    image: confluentinc/cp-zookeeper:7.6.0
    container_name: zookeeper
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000
    ports:
      - "2181:2181"
    volumes:
      - zk-data:/var/lib/zookeeper/data
    networks:
      - pipeline-network

  # ═══ Kafka Broker ═══
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
      KAFKA_LOG_RETENTION_HOURS: 72
      KAFKA_NUM_PARTITIONS: 3
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
    ports:
      - "9092:9092"
    volumes:
      - kafka-data:/var/lib/kafka/data
    networks:
      - pipeline-network

  # ═══ Kafka UI (모니터링) ═══
  kafka-ui:
    image: provectuslabs/kafka-ui:v0.7.1
    container_name: kafka-ui
    environment:
      KAFKA_CLUSTERS_0_NAME: local
      KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: kafka:29092
    ports:
      - "9093:8080"       # http://localhost:9093
    depends_on:
      - kafka
    networks:
      - pipeline-network
```

### 18.2 설치 확인

```bash
docker compose up -d

# Kafka 동작 확인
docker exec kafka kafka-topics --list --bootstrap-server kafka:29092

# Topic 수동 생성 (권장)
docker exec kafka kafka-topics --create \
  --bootstrap-server kafka:29092 \
  --topic app-logs \
  --partitions 3 \
  --replication-factor 1

# Topic 상세 정보
docker exec kafka kafka-topics --describe \
  --bootstrap-server kafka:29092 \
  --topic app-logs

# Kafka UI 접속: http://localhost:9093
```

---

## 19장. [실습] Filebeat → Kafka 연동

### 19.1 filebeat.yml 변경 — Output을 Kafka로

```yaml
# docker/filebeat/filebeat.yml

filebeat.inputs:
  - type: filestream
    id: app-logs
    enabled: true
    paths:
      - /var/log/app/*.log
    fields:
      service: waiting-service
      env: local
    fields_under_root: true
    parsers:
      - multiline:
          type: pattern
          pattern: '^\d{4}-\d{2}-\d{2}'
          negate: true
          match: after

processors:
  - drop_fields:
      fields: ["agent", "ecs", "input.type", "log.offset"]
      ignore_missing: true
  - drop_event:
      when:
        contains:
          message: "GET /actuator/health"

# ★ Output: Kafka로 변경!
output.kafka:
  hosts: ["kafka:29092"]
  topic: "app-logs"
  partition.round_robin:
    reachable_only: true
  required_acks: 1
  compression: gzip

logging.level: info
setup.ilm.enabled: false
setup.template.enabled: false
```

### 19.2 동작 확인

```bash
# Filebeat 재시작
docker compose restart filebeat

# 앱에 요청 전송
curl -X POST http://localhost:8080/api/waiting \
  -H "Content-Type: application/json" \
  -d '{"userId": 1, "restaurantId": 100}'

# Kafka에 메시지 도착 확인
docker exec kafka kafka-console-consumer \
  --bootstrap-server kafka:29092 \
  --topic app-logs \
  --from-beginning \
  --max-messages 3

# Kafka UI에서 확인: http://localhost:9093
# → Topics → app-logs → Messages 탭
```

---

## 20장. [실습] Kafka → Logstash → Elasticsearch 파이프라인

### 20.1 Logstash 설정 — Kafka Input

```ruby
# docker/logstash/pipeline/logstash.conf

input {
  kafka {
    bootstrap_servers => "kafka:29092"
    topics => ["app-logs"]
    group_id => "logstash-consumer-group"
    codec => json
    consumer_threads => 3         # ★ 파티션 수와 동일하게!
    auto_offset_reset => "earliest"
    decorate_events => "basic"    # Kafka 메타데이터 추가
  }
}

filter {
  # ── 타임스탬프 파싱 ──
  if [message] =~ /^\d{4}-\d{2}-\d{2}/ {
    grok {
      match => {
        "message" => "^%{TIMESTAMP_ISO8601:log_timestamp}"
      }
      tag_on_failure => []
    }
    if [log_timestamp] {
      date {
        match => ["log_timestamp", "yyyy-MM-dd HH:mm:ss.SSS"]
        target => "@timestamp"
        timezone => "Asia/Seoul"
      }
      mutate { remove_field => ["log_timestamp"] }
    }
  }

  # ── 불필요한 필드 정리 ──
  mutate {
    remove_field => ["@version", "[event]"]
  }
}

output {
  elasticsearch {
    hosts => ["http://elasticsearch:9200"]
    index => "app-logs-%{+YYYY.MM.dd}"
  }
}
```

### 20.2 docker-compose.yml에 Logstash 추가

```yaml
  logstash:
    image: docker.elastic.co/logstash/logstash:8.12.0
    container_name: logstash
    volumes:
      - ./logstash/pipeline/logstash.conf:/usr/share/logstash/pipeline/logstash.conf:ro
    environment:
      - "LS_JAVA_OPTS=-Xms256m -Xmx256m"
    depends_on:
      - kafka
      - elasticsearch
    networks:
      - pipeline-network
```

---

## 21장. [실습] 전체 파이프라인 통합 테스트 — 장애 시뮬레이션

### 21.1 정상 동작 확인

```bash
docker compose up -d

# 요청 전송
for i in $(seq 1 20); do
  curl -s -X POST http://localhost:8080/api/waiting \
    -H "Content-Type: application/json" \
    -d "{\"userId\": $i, \"restaurantId\": 100}" > /dev/null
done

# ES에서 확인
curl "http://localhost:9200/app-logs-*/_count"
# → {"count": 약 40~60}  (요청당 2~3개의 로그)
```

### 21.2 장애 시뮬레이션: Logstash 중단

```bash
# ★ 핵심 테스트: Logstash를 죽여도 로그가 유실되지 않는지 확인

# 1. 현재 ES 문서 수 확인
curl "http://localhost:9200/app-logs-*/_count"
# → {"count": 60}

# 2. Logstash 중단
docker compose stop logstash

# 3. Logstash가 없는 동안 요청 계속 전송 (10건)
for i in $(seq 21 30); do
  curl -s -X POST http://localhost:8080/api/waiting \
    -H "Content-Type: application/json" \
    -d "{\"userId\": $i, \"restaurantId\": 200}" > /dev/null
done

# 4. Kafka에 메시지가 쌓여있는지 확인
# Kafka UI (http://localhost:9093) → Topics → app-logs → Consumer Lag 확인
# → Consumer Lag가 증가하고 있음 = Logstash가 안 읽고 있음 = Kafka에 쌓여있음

# 5. Logstash 재시작
docker compose start logstash

# 6. 30초 대기 후 ES 문서 수 확인
sleep 30
curl "http://localhost:9200/app-logs-*/_count"
# → {"count": 약 90~100}  ← Logstash 중단 동안의 로그도 모두 처리됨!

# ★ 결론: Kafka 덕분에 Logstash가 죽어도 로그 유실 없음!
```

---

## 22장. Kafka 운영 — 모니터링, Consumer Lag

### 22.1 Consumer Lag — 가장 중요한 모니터링 지표

```
Consumer Lag = (가장 최신 메시지 Offset) - (Consumer가 읽은 Offset)

  Lag = 0:      모든 메시지를 실시간 처리 중 ✅
  Lag = 100:    100개 밀려있음 (약간의 지연, 보통 OK)
  Lag = 10,000: 10,000개 밀려있음 ⚠️ (Logstash가 느림)
  Lag 계속 증가: Logstash 처리 속도 < 로그 생성 속도 🚨

모니터링 방법:
  # CLI
  docker exec kafka kafka-consumer-groups \
    --bootstrap-server kafka:29092 \
    --group logstash-consumer-group \
    --describe

  # Kafka UI: http://localhost:9093
  # → Consumer Groups → logstash-consumer-group → Lag 확인
```

### 22.2 Lag가 계속 증가할 때의 대응

```
1. Logstash 성능 확인
   → 파이프라인 병목 찾기 (filter가 너무 복잡?)
   → worker 수 늘리기 (logstash.yml: pipeline.workers)

2. Logstash 수평 확장
   → Logstash를 2대 → 3대로 증가
   → 같은 Consumer Group이면 파티션을 자동 분배
   → ★ 파티션 수 ≥ Logstash 수 여야 함!

3. 파티션 수 증가 (최후의 수단)
   docker exec kafka kafka-topics --alter \
     --bootstrap-server kafka:29092 \
     --topic app-logs \
     --partitions 6
```

---

## 23장. [퀴즈] Kafka 설계 문제

### 문제 1

```
서비스 50대에서 하루 50GB의 로그가 발생합니다.
Kafka의 retention을 72시간(3일)으로 설정했습니다.
Kafka에 필요한 최소 디스크 공간은?
```

<details>
<summary>정답 보기</summary>

50GB × 3일 = 150GB. 여기에 Kafka 오버헤드(메타데이터, 세그먼트 관리)를 포함하면 약 180~200GB가 필요합니다. Replication Factor가 2라면 이의 2배인 360~400GB가 필요합니다. 안전 마진(디스크 사용률 70% 이하 유지)을 포함하면 약 500~600GB를 권장합니다.

</details>

### 문제 2

```
Logstash를 4대로 운영하려고 합니다.
Kafka Topic의 파티션은 최소 몇 개여야 하나요?
```

<details>
<summary>정답 보기</summary>

**최소 4개**입니다. 하나의 Consumer Group에서 하나의 파티션은 하나의 Consumer만 소비할 수 있습니다. 파티션이 3개인데 Consumer가 4개면, 1개의 Consumer는 놀게 됩니다. 파티션 수 ≥ Consumer 수가 되어야 모든 Consumer가 일을 합니다. 실무에서는 향후 확장을 고려하여 6~8개로 설정하는 것이 좋습니다.

</details>

---

# PART 4. Elasticsearch ILM (인덱스 생명주기 관리)

---

## 24장. 로그 인덱스를 관리하지 않으면 벌어지는 일

```
관리하지 않은 ES (6개월 후):

$ curl "http://localhost:9200/_cat/indices?v&s=index"

index                    docs.count  store.size
app-logs-2024.01.01        2,345,678     4.8gb
app-logs-2024.01.02        2,401,234     4.9gb
...
app-logs-2024.06.30        3,012,456     5.2gb

인덱스 수: 181개
총 데이터: 약 900GB
총 샤드 수: 181 × 2 (primary + replica) = 362개

문제점:
1. SSD 디스크 90% 사용 → 곧 디스크 풀
2. 6개월 전 로그를 SSD에 보관할 필요가 있는가? → 비용 낭비
3. 샤드 362개 → Master 노드 클러스터 상태 관리 부하
4. 인덱스가 많으면 → 검색 시 모든 인덱스를 스캔 → 성능 저하
```

---

## 25장. ILM의 4단계

```
┌──────┐         ┌──────┐         ┌──────┐         ┌────────┐
│ Hot  │── 7일 →│ Warm │── 30일 →│ Cold │── 90일 →│ Delete │
│      │         │      │         │      │         │        │
│ SSD  │         │ HDD  │         │ S3   │         │ 삭제   │
│ 읽기/ │         │ 읽기  │         │ 거의  │         │        │
│ 쓰기  │         │ 전용  │         │ 안 읽음│         │        │
└──────┘         └──────┘         └──────┘         └────────┘

Hot Phase (0~7일):
  - 현재 로그가 쓰이는 인덱스
  - SSD에서 최고 성능으로 읽기/쓰기
  - Rollover: 1일 또는 50GB 도달 시 새 인덱스 생성

Warm Phase (8~30일):
  - 더 이상 쓰기 없음 (읽기 전용)
  - HDD로 이동 → 스토리지 비용 절감
  - Force Merge: 세그먼트를 1개로 합침 → 검색 효율 향상
  - Shrink: 샤드 수를 줄임 → 리소스 절약

Cold Phase (31~90일):
  - 거의 검색하지 않는 로그
  - Object Storage(S3)로 이동 → 비용 최소화
  - Frozen 인덱스 전환 가능

Delete Phase (91일~):
  - 보관 기간이 끝난 인덱스 자동 삭제
  - 법적 요구사항에 맞게 설정
```

---

## 26장. ILM 정책 설계 — 비즈니스 요구사항에 맞추기

| 요구사항 | Hot | Warm | Cold | Delete |
|---------|-----|------|------|--------|
| 일반 서비스 로그 | 7일 | 30일 | - | 30일 |
| 결제/주문 로그 | 7일 | 30일 | 90일 | 365일 |
| 보안 감사 로그 | 30일 | 90일 | 365일 | 3년 |
| 접근 로그 (access) | 3일 | 14일 | - | 14일 |
| 디버그 로그 | 1일 | - | - | 3일 |

---

## 27장. [실습] ILM 정책 생성 및 적용

### 27.1 ILM 정책 생성

```bash
curl -X PUT "http://localhost:9200/_ilm/policy/app-logs-policy" \
  -H "Content-Type: application/json" \
  -d '{
  "policy": {
    "phases": {
      "hot": {
        "min_age": "0ms",
        "actions": {
          "rollover": {
            "max_age": "1d",
            "max_primary_shard_size": "50gb"
          },
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
      "delete": {
        "min_age": "30d",
        "actions": { "delete": {} }
      }
    }
  }
}'

# 확인
curl "http://localhost:9200/_ilm/policy/app-logs-policy?pretty"
```

### 27.2 각 Action 상세 설명

```
rollover:
  max_age: "1d"                  → 1일마다 새 인덱스 생성
  max_primary_shard_size: "50gb" → 또는 50GB 도달 시 새 인덱스
  → 둘 중 먼저 도달한 조건에 발동

shrink:
  number_of_shards: 1            → 샤드를 1개로 합침
  → Warm 단계에서는 쓰기가 없으므로 샤드가 많을 필요 없음
  → 리소스 절약

forcemerge:
  max_num_segments: 1            → Lucene 세그먼트를 1개로 합침
  → 검색 성능 향상 (세그먼트가 적을수록 빠름)
  → ★ 쓰기가 없는 인덱스에서만 사용! (쓰기 중이면 성능 저하)

set_priority:
  → 노드 재시작 시 복구 우선순위
  → Hot(100) > Warm(50) > Cold(0)
```

---

## 28장. [실습] 인덱스 템플릿과 ILM 연결

### 28.1 인덱스 템플릿 생성

```bash
curl -X PUT "http://localhost:9200/_index_template/app-logs-template" \
  -H "Content-Type: application/json" \
  -d '{
  "index_patterns": ["app-logs-*"],
  "template": {
    "settings": {
      "number_of_shards": 1,
      "number_of_replicas": 1,
      "index.lifecycle.name": "app-logs-policy",
      "index.lifecycle.rollover_alias": "app-logs-write"
    },
    "mappings": {
      "properties": {
        "@timestamp":  { "type": "date" },
        "level":       { "type": "keyword" },
        "message":     { "type": "text", "analyzer": "standard" },
        "service":     { "type": "keyword" },
        "traceId":     { "type": "keyword" },
        "thread":      { "type": "keyword" },
        "logger_name": { "type": "keyword" }
      }
    }
  },
  "priority": 200
}'
```

### 28.2 매핑 필드 타입 설명

```
keyword:  정확한 값으로 검색/집계 (level:"ERROR", service:"waiting")
          → 로그 레벨, 서비스명, traceId 등에 사용
          
text:     전문 검색 (토큰화 후 부분 일치 검색)
          → 로그 메시지 본문에 사용
          
date:     날짜/시간 값, 범위 검색 가능
          → @timestamp에 사용

★ 자주 하는 실수:
  traceId를 text로 설정하면 → "abc123"이 "abc"와 "123"으로 토큰화됨
  → traceId:"abc123"으로 정확히 검색이 안 됨!
  → traceId는 반드시 keyword 타입!
```

---

## 29장. [실습] Rollover — 인덱스 자동 분할

### 29.1 Bootstrap Index 생성 (최초 1회)

```bash
# Rollover를 사용하려면 첫 번째 인덱스를 수동으로 생성해야 합니다
curl -X PUT "http://localhost:9200/app-logs-000001" \
  -H "Content-Type: application/json" \
  -d '{
  "aliases": {
    "app-logs-write": {
      "is_write_index": true
    }
  }
}'

# 확인
curl "http://localhost:9200/_cat/aliases/app-logs-write?v"
# alias           index              is_write_index
# app-logs-write  app-logs-000001    true
```

### 29.2 Logstash Output을 Alias로 변경

```ruby
output {
  elasticsearch {
    hosts => ["http://elasticsearch:9200"]
    # ★ 날짜 인덱스 대신 Alias 사용
    index => "app-logs-write"
    # ilm_enabled => true    # 8.x에서는 자동
  }
}
```

### 29.3 Rollover 동작 확인

```bash
# ILM이 Rollover를 실행하면:
# app-logs-000001 → 읽기 전용
# app-logs-000002 → 새 Write Index

curl "http://localhost:9200/_cat/indices/app-logs-*?v&s=index"
# index             status  docs.count  store.size
# app-logs-000001   open    1,234,567   4.8gb      ← 읽기 전용
# app-logs-000002   open      123,456   0.5gb      ← 현재 쓰기
```

---

## 30장. [실습] ILM 상태 모니터링

```bash
# 인덱스별 ILM 상태 확인
curl "http://localhost:9200/app-logs-*/_ilm/explain?pretty"

# 결과 예시:
# {
#   "indices": {
#     "app-logs-000001": {
#       "managed": true,
#       "policy": "app-logs-policy",
#       "phase": "warm",          ← 현재 Warm 단계
#       "age": "10d",
#       "action": "complete"
#     },
#     "app-logs-000002": {
#       "managed": true,
#       "policy": "app-logs-policy",
#       "phase": "hot",           ← 현재 Hot 단계
#       "age": "1d"
#     }
#   }
# }

# ILM 실행 상태 확인
curl "http://localhost:9200/_ilm/status?pretty"
# → "operation_mode": "RUNNING" 이면 정상
```

---

## 31장. [퀴즈] ILM 설계 문제

### 문제 1

```
결제 로그는 금융 규제상 1년간 보관해야 합니다.
하루 평균 2GB의 결제 로그가 발생합니다.
ILM 정책을 설계하고, 1년간 필요한 스토리지를 계산하세요.
```

<details>
<summary>정답 보기</summary>

정책: Hot(7일, SSD) → Warm(30일, HDD) → Cold(365일, S3) → Delete(366일)

스토리지 계산:
- Hot: 2GB × 7일 = 14GB (SSD)
- Warm: 2GB × 23일 = 46GB (HDD)
- Cold: 2GB × 335일 = 670GB (S3, 약 $15/월)
- 총: 약 730GB

ES 역인덱스 오버헤드(×1.5)를 포함하면 약 1.1TB. 하지만 Cold 단계에서 S3를 사용하면 SSD/HDD에 필요한 공간은 약 90GB에 불과합니다. 비용의 대부분은 S3에 있으며, S3는 매우 저렴합니다.

</details>

---

# PART 5. Elasticsearch 클러스터 운영

---

## 32장. 왜 클러스터가 필요한가?

```
단일 노드의 위험:
  ✅ 간단한 설정
  ❌ 서버 1대가 죽으면 → 모든 로그 검색/저장 불가
  ❌ 디스크 한계 → 확장 불가
  ❌ 데이터 복제 없음 → 디스크 고장 시 데이터 손실

클러스터의 장점:
  ✅ 노드 하나가 죽어도 서비스 지속 (Replica)
  ✅ 데이터를 여러 노드에 분산 (수평 확장)
  ✅ 검색을 여러 노드가 병렬 처리 (성능 향상)
```

---

## 33장. ES 클러스터의 핵심 개념

### 33.1 Shard와 Replica

```
Index: app-logs-2024.01.15
  설정: number_of_shards: 2, number_of_replicas: 1

┌─────────────────────────────────────────────────────────┐
│                   Elasticsearch Cluster                   │
│                                                          │
│  Node 1                Node 2                Node 3      │
│  ┌──────────────┐     ┌──────────────┐     ┌──────────┐ │
│  │ P0 (Primary) │     │ P1 (Primary) │     │ R0       │ │
│  │ R1 (Replica) │     │ R0 (Replica) │     │ R1       │ │
│  └──────────────┘     └──────────────┘     └──────────┘ │
│                                                          │
│  P0: 원본 데이터의 절반                                   │
│  P1: 원본 데이터의 나머지 절반                             │
│  R0: P0의 복제본 (다른 노드에 배치)                       │
│  R1: P1의 복제본 (다른 노드에 배치)                       │
│                                                          │
│  → Node 1이 죽어도 Node 2에 R0, Node 3에 R1이 있음       │
│  → 데이터 손실 없이 서비스 지속!                           │
└─────────────────────────────────────────────────────────┘
```

### 33.2 Primary Shard vs Replica Shard

```
Primary Shard:
  - 원본 데이터
  - 쓰기(인덱싱) 요청을 처리
  - 인덱스 생성 시 결정 → 이후 변경 불가!

Replica Shard:
  - Primary의 복제본
  - 읽기(검색) 요청을 분산 처리
  - 언제든 변경 가능
  - Primary가 있는 노드와 다른 노드에 배치됨

★ 핵심 규칙:
  "같은 샤드의 Primary와 Replica는 절대 같은 노드에 배치되지 않음"
  → 노드가 죽어도 다른 노드에 복제본이 있으므로 데이터 손실 없음
```

---

## 34장. 노드 역할

```
Master Node (마스터 노드):
  역할: 클러스터 상태 관리
  - 인덱스 생성/삭제 결정
  - 샤드 할당 (어떤 노드에 어떤 샤드를 배치할지)
  - 노드 참여/이탈 관리
  수량: 최소 3대 (과반수 투표를 위해 반드시 홀수)
  리소스: CPU, 메모리 적당, 디스크 적음

Data Node (데이터 노드):
  역할: 실제 데이터 저장 및 검색 처리
  - CRUD 작업 수행
  - 검색 쿼리 실행
  세부 역할: data_hot, data_warm, data_cold (ILM과 연계)
  수량: 데이터량에 비례
  리소스: CPU, 메모리, 디스크 모두 중요

Coordinating Node (조정 노드):
  역할: 클라이언트 요청을 받아서 Data Node에 분배
  - 검색 결과를 모아서 응답
  - 로드밸런서 역할
  수량: 1~2대 (선택 사항)
  리소스: CPU, 메모리 중요, 디스크 적음
  
Ingest Node (수집 노드):
  역할: 인덱싱 전 데이터 전처리
  - Logstash 역할 일부 대체 가능
  수량: 필요 시 (보통 Data Node가 겸함)
```

---

## 35장. 샤드 전략

### 35.1 샤드 크기 가이드

```
권장 샤드 크기: 10GB ~ 50GB

  너무 작으면 (1GB):
    → 인덱스 10개 × 샤드 5개 = 50개의 작은 샤드
    → Master 노드 관리 오버헤드 증가
    → 검색 시 50개 샤드를 모두 스캔 → 비효율

  너무 크면 (100GB+):
    → 장애 복구 시 100GB 데이터를 다른 노드로 복제
    → 복구에 오랜 시간 소요
    → Force merge 등 관리 작업이 느림

규칙:
  Primary Shard 수 = 예상 인덱스 크기 ÷ 50GB (올림)
  
  예: 하루 5GB 로그 → Primary Shard 1개
  예: 하루 30GB 로그 → Primary Shard 1개
  예: 하루 100GB 로그 → Primary Shard 2~3개
  예: 하루 500GB 로그 → Primary Shard 10개
```

### 35.2 Replica 수 가이드

```
Replica 수: 1 (기본, 대부분의 경우)
  → 노드 1대 장애까지 견딤
  → 검색 성능 약 2배 향상 (Primary + Replica 모두 검색 처리)

Replica 수: 2 (중요 데이터)
  → 노드 2대 동시 장애까지 견딤
  → 디스크 사용량 3배 (Primary 1 + Replica 2)
  → 결제/금융 로그 등 중요 데이터에 사용

Replica 수: 0 (개발 환경)
  → 복제 없음 → 디스크 절약
  → 노드 죽으면 데이터 손실!
  → ★ 절대 운영 환경에서 사용하지 마세요!
```

---

## 36장. [실습] 3노드 클러스터 구성

```yaml
# docker/docker-compose-cluster.yml
version: '3.8'

services:
  es-node1:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.12.0
    container_name: es-node1
    environment:
      - node.name=es-node1
      - cluster.name=log-cluster
      - discovery.seed_hosts=es-node2,es-node3
      - cluster.initial_master_nodes=es-node1,es-node2,es-node3
      - node.roles=master,data_hot,data_content
      - xpack.security.enabled=false
      - "ES_JAVA_OPTS=-Xms512m -Xmx512m"
    ports:
      - "9200:9200"
    volumes:
      - es-node1-data:/usr/share/elasticsearch/data
    networks:
      - cluster-network

  es-node2:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.12.0
    container_name: es-node2
    environment:
      - node.name=es-node2
      - cluster.name=log-cluster
      - discovery.seed_hosts=es-node1,es-node3
      - cluster.initial_master_nodes=es-node1,es-node2,es-node3
      - node.roles=master,data_warm
      - xpack.security.enabled=false
      - "ES_JAVA_OPTS=-Xms512m -Xmx512m"
    volumes:
      - es-node2-data:/usr/share/elasticsearch/data
    networks:
      - cluster-network

  es-node3:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.12.0
    container_name: es-node3
    environment:
      - node.name=es-node3
      - cluster.name=log-cluster
      - discovery.seed_hosts=es-node1,es-node2
      - cluster.initial_master_nodes=es-node1,es-node2,es-node3
      - node.roles=master,data_cold
      - xpack.security.enabled=false
      - "ES_JAVA_OPTS=-Xms256m -Xmx256m"
    volumes:
      - es-node3-data:/usr/share/elasticsearch/data
    networks:
      - cluster-network

volumes:
  es-node1-data:
  es-node2-data:
  es-node3-data:

networks:
  cluster-network:
    driver: bridge
```

### 36.2 클러스터 상태 확인

```bash
docker compose -f docker-compose-cluster.yml up -d

# 클러스터 건강 상태
curl "http://localhost:9200/_cluster/health?pretty"
# → "status": "green" (모든 샤드 정상), "number_of_nodes": 3

# 노드 목록
curl "http://localhost:9200/_cat/nodes?v&h=name,node.role,heap.percent,disk.used_percent"
# name      node.role         heap.percent  disk.used_percent
# es-node1  data_hot,master          35               22
# es-node2  data_warm,master         28               15
# es-node3  data_cold,master         20               10

# 샤드 분배 확인
curl "http://localhost:9200/_cat/shards?v&s=index"
```

---

## 37장. [실습] Hot-Warm-Cold 노드 역할 분리

### 37.1 ILM 정책에서 노드 할당 설정

```bash
curl -X PUT "http://localhost:9200/_ilm/policy/app-logs-tiered-policy" \
  -H "Content-Type: application/json" \
  -d '{
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
          "allocate": {
            "require": { "data": "warm" }
          },
          "shrink": { "number_of_shards": 1 },
          "forcemerge": { "max_num_segments": 1 },
          "set_priority": { "priority": 50 }
        }
      },
      "cold": {
        "min_age": "30d",
        "actions": {
          "allocate": {
            "require": { "data": "cold" }
          },
          "set_priority": { "priority": 0 }
        }
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

## 38장. 클러스터 모니터링 — _cat API

```bash
# ═══ 가장 자주 사용하는 _cat API ═══

# 클러스터 건강 상태 (가장 먼저 확인!)
curl "http://localhost:9200/_cat/health?v"
# status: green=정상, yellow=Replica미할당, red=Primary미할당

# 노드 상태
curl "http://localhost:9200/_cat/nodes?v&h=name,node.role,heap.percent,ram.percent,cpu,disk.used_percent"

# 인덱스 목록 (크기순 정렬)
curl "http://localhost:9200/_cat/indices?v&s=store.size:desc&h=index,status,docs.count,store.size"

# 샤드 분배 (Unassigned 샤드 찾기)
curl "http://localhost:9200/_cat/shards?v&s=state"

# 미할당 샤드 원인 확인
curl "http://localhost:9200/_cluster/allocation/explain?pretty"
```

---

## 39장. 클러스터 장애 대응

### 39.1 Yellow 상태

```
원인: Replica 샤드가 할당되지 않음
  → 노드 수가 부족하여 Replica를 배치할 곳이 없음
  → 또는 노드가 다운되어 Replica가 미할당

대응:
  1. curl "http://localhost:9200/_cat/shards?v&h=index,shard,prirep,state,node"
     → state=UNASSIGNED인 샤드 찾기
  
  2. 노드 수 확인: 최소 2대 이상이어야 Replica 할당 가능
  
  3. 개발 환경이라면 Replica 0으로 변경:
     curl -X PUT "http://localhost:9200/app-logs-*/_settings" \
       -H "Content-Type: application/json" \
       -d '{"index": {"number_of_replicas": 0}}'
```

### 39.2 Red 상태

```
원인: Primary 샤드가 할당되지 않음 → 데이터 손실 위험!

대응:
  1. 즉시 어떤 인덱스가 Red인지 확인:
     curl "http://localhost:9200/_cat/indices?v&health=red"
  
  2. 해당 노드가 다운되었는지 확인:
     curl "http://localhost:9200/_cat/nodes?v"
  
  3. 노드가 복구되면 자동으로 Green으로 돌아옴
  
  4. 노드가 영구 손실된 경우:
     → Replica가 있었다면 자동으로 Replica가 Primary로 승격
     → Replica가 없었다면 데이터 손실 (★ 그래서 Replica가 중요!)
```

---

## 40장. [퀴즈] 클러스터 운영 문제

### 문제 1

```
3노드 클러스터에서 number_of_replicas=1로 설정했습니다.
Node 2가 영구적으로 죽었습니다.
데이터가 손실되나요?
```

<details>
<summary>정답 보기</summary>

**손실되지 않습니다.** Replica가 1이므로 모든 Primary 샤드의 복제본이 다른 노드에 있습니다. Node 2에 있던 Primary 샤드의 복제본은 Node 1 또는 Node 3에 있고, ES가 자동으로 해당 Replica를 Primary로 승격합니다. 클러스터는 일시적으로 Yellow 상태가 되지만(새 Replica를 만들 공간이 부족할 수 있으므로), 데이터는 안전합니다.

</details>

### 문제 2

```
하루 50GB 로그, 30일 보관, Replica 1.
3노드 클러스터에서 각 노드에 필요한 최소 디스크는?
```

<details>
<summary>정답 보기</summary>

총 데이터: 50GB × 30일 = 1,500GB (Primary)
Replica 포함: 1,500GB × 2 = 3,000GB (3TB)
3노드 분산: 3,000GB ÷ 3 = 1,000GB/노드
안전 마진(70% 이하): 1,000GB ÷ 0.7 = 약 1,430GB ≈ **1.5TB/노드**

ES는 디스크 사용률이 85%를 넘으면 새 샤드 할당을 거부하고(watermark.low), 90%를 넘으면 샤드를 다른 노드로 이동시키기 시작합니다(watermark.high). 70% 이하를 유지하는 것이 안전합니다.

</details>

---

# PART 6. 운영 환경 최종 아키텍처

---

## 41장. 규모별 아키텍처 설계

```
소규모 (서버 1~10대, 일 ~5GB):
  [App] → 로그 파일 → [Filebeat] → [ES 단일 노드]
  Kafka: 불필요
  ILM: 30일 보관 후 삭제

중규모 (서버 10~50대, 일 5~50GB):
  [App] → 로그 파일 → [Filebeat] → [Kafka] → [Logstash] → [ES 3노드 클러스터]
  ILM: Hot(7일) → Warm(30일) → Delete(90일)

대규모 (서버 50~200대, 일 50~500GB):
  [App] → 로그 파일 → [Filebeat] → [Kafka 클러스터] → [Logstash ×3] → [ES 5+ 노드 클러스터]
  ILM: Hot(7일, SSD) → Warm(30일, HDD) → Cold(90일, S3) → Delete(365일)
```

---

## 42장. 전체 docker-compose.yml

```yaml
# docker/docker-compose-production.yml
version: '3.8'

services:
  # ═══ Zookeeper ═══
  zookeeper:
    image: confluentinc/cp-zookeeper:7.6.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
    volumes:
      - zk-data:/var/lib/zookeeper/data
    networks: [pipeline]

  # ═══ Kafka ═══
  kafka:
    image: confluentinc/cp-kafka:7.6.0
    depends_on: [zookeeper]
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:29092,PLAINTEXT_HOST://localhost:9092
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_LOG_RETENTION_HOURS: 72
      KAFKA_NUM_PARTITIONS: 3
    ports: ["9092:9092"]
    volumes:
      - kafka-data:/var/lib/kafka/data
    networks: [pipeline]

  # ═══ Elasticsearch ═══
  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.12.0
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
      - "ES_JAVA_OPTS=-Xms1g -Xmx1g"
    ports: ["9200:9200"]
    volumes:
      - es-data:/usr/share/elasticsearch/data
    networks: [pipeline]
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:9200/_cluster/health || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 10

  # ═══ Logstash ═══
  logstash:
    image: docker.elastic.co/logstash/logstash:8.12.0
    volumes:
      - ./logstash/pipeline:/usr/share/logstash/pipeline:ro
    environment:
      - "LS_JAVA_OPTS=-Xms256m -Xmx256m"
    depends_on: [kafka, elasticsearch]
    networks: [pipeline]

  # ═══ Filebeat ═══
  filebeat:
    image: docker.elastic.co/beats/filebeat:8.12.0
    user: root
    command: filebeat -e -strict.perms=false
    volumes:
      - ./filebeat/filebeat.yml:/usr/share/filebeat/filebeat.yml:ro
      - ../logs:/var/log/app:ro
      - filebeat-data:/usr/share/filebeat/data
    depends_on: [kafka]
    networks: [pipeline]

  # ═══ Kibana ═══
  kibana:
    image: docker.elastic.co/kibana/kibana:8.12.0
    environment:
      - ELASTICSEARCH_HOSTS=http://elasticsearch:9200
    ports: ["5601:5601"]
    depends_on:
      elasticsearch:
        condition: service_healthy
    networks: [pipeline]

  # ═══ Kafka UI ═══
  kafka-ui:
    image: provectuslabs/kafka-ui:v0.7.1
    environment:
      KAFKA_CLUSTERS_0_NAME: local
      KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: kafka:29092
    ports: ["9093:8080"]
    depends_on: [kafka]
    networks: [pipeline]

volumes:
  zk-data:
  kafka-data:
  es-data:
  filebeat-data:

networks:
  pipeline:
    driver: bridge
```

### 실행

```bash
docker compose -f docker-compose-production.yml up -d

# 접속 URL
# Elasticsearch: http://localhost:9200
# Kibana:        http://localhost:5601
# Kafka UI:      http://localhost:9093
```

---

## 43장. 운영 체크리스트와 자주 하는 실수

### 운영 체크리스트

```
□ Filebeat: ignore_older 설정 (24h 이상)
□ Filebeat: close.on_state_change.inactive 설정 (5m)
□ Filebeat: output.kafka.required_acks: 1
□ Kafka: retention.hours 설정 (24~72h)
□ Kafka: 파티션 수 ≥ Logstash 인스턴스 수
□ Logstash: consumer_threads = 파티션 수
□ ES: ILM 정책 설정 및 적용
□ ES: number_of_replicas ≥ 1 (운영 환경)
□ ES: 디스크 watermark 모니터링 (85% 미만 유지)
□ Kafka: Consumer Lag 모니터링
□ ES: 클러스터 health 모니터링 (green 유지)
```

### 자주 하는 실수 TOP 5

```
1위: ES ILM을 설정하지 않음
    → 디스크가 가득 차서 로그 저장 불가
    → 해결: 반드시 ILM + Delete phase 설정

2위: Kafka 파티션 수 < Logstash 수
    → 일부 Logstash가 놀게 됨
    → 해결: 파티션 수 ≥ Logstash 수

3위: Filebeat에서 health check 로그를 필터링하지 않음
    → 10초마다 health check × 100대 = 엄청난 양의 무의미한 로그
    → 해결: drop_event processor로 필터링

4위: ES Replica를 0으로 운영
    → 노드 장애 시 데이터 손실
    → 해결: 운영 환경에서는 최소 1

5위: Kafka retention을 너무 짧게 설정 (1시간 등)
    → Logstash 장애 시 1시간 분량만 복구 가능
    → 해결: 최소 24시간, 권장 72시간
```

---

## 44장. 비용 최적화 전략

```
1. JSON 로그 사용 → Logstash grok 파싱 제거
   → Logstash CPU 사용량 감소 → 인스턴스 축소 가능

2. ILM Hot-Warm-Cold → 스토리지 계층화
   → SSD는 최근 7일만, 나머지는 HDD/S3

3. 불필요한 로그 필터링
   → health check, DEBUG 로그를 Filebeat 단계에서 drop
   → ES에 저장되는 양 30~50% 감소 가능

4. 인덱스 매핑 최적화
   → 필요 없는 필드 비활성화 (enabled: false)
   → text 대신 keyword (인덱싱 비용 감소)

5. Kafka 압축
   → output.kafka.compression: gzip
   → 네트워크 대역폭 50~70% 절약
```

---

## 45장. 다음 단계

```
이 핸드북을 마치면 할 수 있는 것:
  ✅ Filebeat로 경량 로그 수집
  ✅ Kafka로 로그 버퍼링 (유실 방지)
  ✅ ES ILM으로 인덱스 생명주기 관리
  ✅ ES 클러스터 구성 및 운영
  ✅ 전체 파이프라인 장애 시뮬레이션 및 복구

다음으로 배울 것:
  1. Grafana Loki — ES 대체, 비용 80% 절감
  2. OpenTelemetry Collector — Logstash 대체, 통합 파이프라인
  3. Kubernetes 환경 — DaemonSet으로 Filebeat/Promtail 배포
  4. Kafka 클러스터 (3 broker) — 프로덕션 Kafka 운영
```

---

> **끝.**
> 이 핸드북을 통해 로그 파이프라인을 "서버 1대용"에서
> "서버 100대 프로덕션용"으로 업그레이드하는 방법을 모두 다루었습니다.
> Filebeat → Kafka → Logstash → ES Cluster + ILM
> 이 파이프라인을 실습하고 운영에 적용해보세요. 🚀
