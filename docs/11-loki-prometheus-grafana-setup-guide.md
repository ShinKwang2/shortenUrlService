# Shorten URL Service용 Loki · Prometheus · Grafana 가이드

> 이 문서는 "로그를 보고 싶다" 수준에서 출발해도 이해할 수 있도록 작성했다.
> 목표는 세 가지다.
> 1. Loki, Prometheus, Grafana가 각각 무엇인지 이해한다.
> 2. 이 프로젝트에 맞게 어떻게 붙이는지 이해한다.
> 3. 실무에서는 어떤 식으로 운영하는지 감을 잡는다.

---

## 1. 먼저 큰 그림부터

Observability에서 가장 많이 보는 세 가지 신호는 다음이다.

| 신호 | 질문 | 대표 도구 |
|------|------|-----------|
| Logs | "무슨 일이 있었나?" | Loki |
| Metrics | "전체적으로 얼마나 느리고, 얼마나 실패하나?" | Prometheus |
| Visualization | "한 화면에서 어떻게 볼까?" | Grafana |

짧게 말하면:

- `Loki`는 로그 저장소다.
- `Prometheus`는 메트릭 저장소이자 수집기다.
- `Grafana`는 이 둘을 사람이 보기 쉽게 연결해 주는 화면이다.

이 세 개를 함께 두는 이유는 역할이 명확하게 다르기 때문이다.

예를 들어 장애가 났다고 가정하자.

1. Prometheus에서 `5xx 증가`, `응답시간 증가`를 본다.
2. Grafana 대시보드에서 어느 API가 문제인지 좁힌다.
3. Loki에서 해당 시간대 로그를 검색해 정확한 예외 메시지를 찾는다.

즉:

- Prometheus는 "이상 징후 탐지"에 강하다.
- Loki는 "원인 파악"에 강하다.
- Grafana는 "탐지와 원인 파악을 한 화면에서 연결"해 준다.

---

## 2. 각 도구를 아주 정확하게 이해하기

### 2.1 Loki란?

Loki는 Grafana Labs가 만든 로그 저장소다.

많은 팀이 로그 저장소로 Elasticsearch를 떠올리는데, Loki는 철학이 다르다.

- Elasticsearch는 로그 본문을 강하게 인덱싱한다.
- Loki는 로그 본문 전체를 비싸게 인덱싱하지 않고, `label` 중심으로 저장한다.

이 차이 때문에 Loki는 다음 장점이 있다.

- 운영비가 상대적으로 저렴하다.
- Grafana와 붙이기 쉽다.
- Kubernetes, 컨테이너 환경과 잘 맞는다.

대신 주의할 점도 있다.

- 아무 문자열이나 초고속 전문 검색하는 도구는 아니다.
- 라벨 설계를 잘못하면 성능이 망가진다.

Loki의 핵심 개념은 세 개만 잡으면 된다.

#### 1. Log line

로그 한 줄이다.

예:

```text
2026-04-11 10:00:10 INFO [req-1234] ShortenUrlRestController - shorten url created
```

#### 2. Label

로그를 분류하는 메타데이터다.

예:

```text
app=shorten-url-service
env=dev
level=info
```

Loki는 이 라벨을 기준으로 검색 범위를 먼저 줄인다.

#### 3. Stream

동일한 label 조합을 가진 로그 흐름이다.

예를 들어:

- `{app="shorten-url-service", env="dev", level="info"}`
- `{app="shorten-url-service", env="dev", level="error"}`

이 두 개는 서로 다른 stream이다.

실무에서 매우 중요한 원칙:

- `service`, `env`, `job`, `instance`, `level` 정도는 라벨로 쓰기 좋다.
- `requestId`, `userId`, `url`, `traceId`는 라벨로 올리면 안 된다.

이유는 high cardinality 때문이다.

즉 값 종류가 너무 많으면:

- stream 수가 폭증하고
- 인덱스 비용이 커지고
- Loki 성능이 나빠진다

따라서 `requestId` 같은 값은 라벨이 아니라 로그 본문 또는 JSON 필드로 저장하고, 검색할 때 본문 필터로 찾는다.

### 2.2 Prometheus란?

Prometheus는 메트릭 시스템이다.

메트릭은 로그처럼 사건 하나하나를 저장하는 것이 아니라, 일정 시점의 숫자를 저장한다.

예:

- 현재 HTTP 요청 수
- 에러 응답 수
- 응답시간 히스토그램
- JVM 메모리 사용량
- CPU 사용량

Prometheus의 동작은 기본적으로 `pull` 방식이다.

즉 애플리케이션이 `/actuator/prometheus` 같은 메트릭 endpoint를 노출하면,
Prometheus 서버가 주기적으로 와서 긁어 간다.

Prometheus의 강점:

- 시계열 데이터 처리에 매우 강하다.
- Alert 규칙 만들기 좋다.
- Spring Boot + Micrometer 조합이 매우 쉽다.

Prometheus가 잘하는 질문:

- "5분 동안 5xx 비율이 얼마나 올랐나?"
- "P95 응답시간이 얼마인가?"
- "현재 JVM heap 사용률이 얼마인가?"
- "RPS가 평소 대비 몇 배 증가했나?"

Prometheus가 못 하는 질문:

- "왜 500이 났는가?"
- "어떤 예외 메시지였는가?"
- "어떤 requestId에서 실패했는가?"

그건 로그의 영역이다.

### 2.3 Grafana란?

Grafana는 저장소가 아니다.
화면이다.

정확히는:

- Loki, Prometheus, Tempo, Elasticsearch, MySQL 같은 여러 데이터소스를 연결하고
- 대시보드, 탐색, 알람, 링크를 제공하는 시각화 도구다.

Grafana를 쓰는 이유는 두 가지다.

1. Prometheus 메트릭 그래프를 보기 쉽다.
2. Loki 로그와 Prometheus 메트릭을 같은 시간 축에서 함께 볼 수 있다.

실무에서는 "Grafana가 메인 관제 UI"가 되는 경우가 많다.

---

## 3. 이 프로젝트의 현재 상태

현재 코드베이스를 기준으로 보면 이미 좋은 출발점이 있다.

### 3.1 이미 들어가 있는 것

`[application.yaml](/Users/shin/Documents/60_monitoring/90_shortenUrlService/src/main/resources/application.yaml:1)` 기준:

- `spring-boot-starter-actuator` 사용 가능
- `/actuator/prometheus` 노출 설정 존재
- `health`, `info`, `metrics`, `prometheus` endpoint 공개
- `management.metrics.tags.application` 설정 존재

`[pom.xml](/Users/shin/Documents/60_monitoring/90_shortenUrlService/pom.xml:1)` 기준:

- `spring-boot-starter-actuator`
- `micrometer-registry-prometheus`
- `logstash-logback-encoder`

`[logback.xml](/Users/shin/Documents/60_monitoring/90_shortenUrlService/src/main/resources/logback.xml:1)` 기준:

- 콘솔 출력
- 파일 출력
- Logstash TCP appender 출력

`[LoggingFilter.java](/Users/shin/Documents/60_monitoring/90_shortenUrlService/src/main/java/kr/co/shortenurlservice/presentation/LoggingFilter.java:1)` 기준:

- `requestId`, `method`, `uri`를 MDC에 넣고 있음
- 요청 처리 시간과 상태코드를 기록하고 있음

즉:

- 메트릭은 거의 Prometheus-ready 상태다.
- 로그는 Loki로 보내기 전에 구조를 조금 정리하면 된다.

### 3.2 아직 부족한 것

현재 상태에서 운영 관점으로 보면 보완 포인트는 다음이다.

1. `logback.xml`이 Loki 직접 연동형이 아니라 `Logstash TCP` 기준이다.
2. 로그 포맷이 완전한 JSON 중심 운영형이라고 보기는 어렵다.
3. Prometheus, Loki, Grafana를 띄우는 인프라 파일이 아직 없다.
4. Grafana datasource/provisioning 문서가 없다.
5. 알람 규칙, 실무용 대시보드 기준이 아직 없다.

그래서 이 문서에서는 "최소 실행 가능한 로컬 세팅"과 "실무형 운영 방식"을 분리해서 설명한다.

---

## 4. 이 프로젝트에서 추천하는 구조

로컬/학습용으로는 다음 구조가 가장 단순하다.

```text
Spring Boot App
  ├─ 로그 → 파일 또는 stdout
  ├─ 메트릭 → /actuator/prometheus
  │
  ├─ Promtail → Loki
  ├─ Prometheus → Spring Boot scrape
  └─ Grafana → Loki + Prometheus 조회
```

구성 요소별 역할:

- Spring Boot 앱: 로그와 메트릭을 만든다.
- Promtail: 앱 로그를 읽어서 Loki로 보낸다.
- Loki: 로그를 저장하고 검색한다.
- Prometheus: 앱 메트릭을 수집한다.
- Grafana: 둘 다 시각화한다.

왜 Promtail을 추천하느냐:

- 지금 프로젝트는 이미 파일 로그와 콘솔 로그가 있다.
- 학습 단계에서는 "애플리케이션에서 직접 Loki로 보내는 방식"보다 "에이전트가 로그를 수집하는 방식"이 더 일반적이다.
- 운영에서도 sidecar/daemonset/agent 방식이 더 흔하다.

참고:

- Promtail은 여전히 많이 쓰이지만, 장기적으로는 Grafana Alloy 또는 OpenTelemetry Collector를 함께 검토하는 팀이 많다.
- 다만 입문과 로컬 실습에서는 Promtail이 이해하기 제일 쉽다.

---

## 5. 세팅 순서 요약

이 프로젝트에서 가장 현실적인 도입 순서는 다음이다.

1. Spring Boot 메트릭 endpoint 확인
2. 로그 포맷 정리
3. Loki 실행
4. Promtail 실행
5. Prometheus 실행
6. Grafana 실행
7. Grafana datasource 연결
8. 로그/메트릭 대시보드 구성
9. 알람 추가

아래부터는 실제 설정 예시를 순서대로 설명한다.

---

## 6. Spring Boot 쪽 준비

### 6.1 메트릭은 이미 거의 준비 완료

현재 `application.yaml`에 아래 설정이 있다.

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, info, prometheus, metrics
  endpoint:
    health:
      show-details: always
  metrics:
    tags:
      application: ${spring.application.name}
```

이 설정이면 앱 실행 후 다음 endpoint를 확인할 수 있다.

```text
http://localhost:8080/actuator/prometheus
```

여기서 Prometheus 포맷의 메트릭이 내려오면 준비가 된 것이다.

대표적으로 보게 될 메트릭:

- `http_server_requests_seconds`
- `jvm_memory_used_bytes`
- `jvm_gc_pause_seconds`
- `system_cpu_usage`
- `process_uptime_seconds`

Spring Boot 3 + Micrometer에서는 버전에 따라 이름이 조금씩 다를 수 있다.
핵심은 "HTTP 요청, JVM, 프로세스, 시스템 메트릭이 노출된다"는 점이다.

### 6.2 로그는 JSON 중심으로 정리하는 것이 좋다

지금 `logback.xml`은 사람이 읽는 패턴 로그와 Logstash appender가 섞여 있다.

로컬 실습 단계에서는 그대로 가도 되지만, Loki와 잘 맞추려면 실무적으로는 다음 방향이 좋다.

#### 권장 원칙

1. 운영 환경에서는 `stdout JSON` 또는 `file JSON` 중 하나로 통일
2. `requestId`, `method`, `uri`, `statusCode`, `durationMs`를 구조화 필드로 남김
3. 예외는 stacktrace 포함
4. 라벨로 올릴 값과 본문에 둘 값을 분리

#### 라벨 후보

- `app`
- `env`
- `job`
- `instance`
- 필요시 `level`

#### 로그 본문/JSON 필드 후보

- `requestId`
- `traceId`
- `method`
- `uri`
- `statusCode`
- `durationMs`
- `exception.class`
- `exception.message`

실무에서는 로그를 JSON 한 줄로 남기는 경우가 많다.

예:

```json
{
  "timestamp": "2026-04-11T10:15:23.120+09:00",
  "level": "INFO",
  "service": "shorten-url-service",
  "requestId": "ma1ab-12cd",
  "method": "POST",
  "uri": "/shortenUrl",
  "statusCode": 200,
  "durationMs": 18,
  "message": "shorten url created"
}
```

이렇게 해 두면 Grafana Loki에서 필드 파싱과 검색이 쉬워진다.

---

## 7. 로컬에서 실제로 붙이는 방법

이 섹션은 "내 로컬에서 한 번 띄워서 보고 싶다"를 기준으로 한다.

### 7.1 필요한 파일들

일반적으로 아래 파일들을 추가해서 사용한다.

```text
monitoring/
  docker-compose.yml
  loki-config.yml
  promtail-config.yml
  prometheus.yml
  grafana/
    provisioning/
      datasources/
        datasources.yml
```

이번 문서에서는 설정 내용을 먼저 설명하고, 실제 파일 추가는 필요할 때 별도 작업으로 진행하면 된다.

### 7.2 Docker Compose 예시

```yaml
services:
  loki:
    image: grafana/loki:3.0.0
    ports:
      - "3100:3100"
    command: -config.file=/etc/loki/local-config.yaml
    volumes:
      - ./loki-config.yml:/etc/loki/local-config.yaml

  promtail:
    image: grafana/promtail:3.0.0
    volumes:
      - ./promtail-config.yml:/etc/promtail/config.yml
      - ../application.log:/var/log/shorten-url/application.log
    command: -config.file=/etc/promtail/config.yml
    depends_on:
      - loki

  prometheus:
    image: prom/prometheus:v3.2.1
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml
    command:
      - --config.file=/etc/prometheus/prometheus.yml

  grafana:
    image: grafana/grafana:11.5.2
    ports:
      - "3000:3000"
    volumes:
      - ./grafana/provisioning:/etc/grafana/provisioning
    depends_on:
      - loki
      - prometheus
```

주의:

- 버전은 예시다.
- 실제 도입 시점에는 이미지 태그를 팀에서 고정해서 쓰는 것이 좋다.
- 로컬에서는 `application.log` 파일 수집이 단순하다.
- 컨테이너 환경에서는 대개 `stdout` 수집을 더 선호한다.

### 7.3 Loki 설정 예시

```yaml
auth_enabled: false

server:
  http_listen_port: 3100

common:
  path_prefix: /loki
  storage:
    filesystem:
      chunks_directory: /loki/chunks
      rules_directory: /loki/rules
  replication_factor: 1
  ring:
    kvstore:
      store: inmemory

schema_config:
  configs:
    - from: 2024-01-01
      store: tsdb
      object_store: filesystem
      schema: v13
      index:
        prefix: index_
        period: 24h

limits_config:
  allow_structured_metadata: true
  volume_enabled: true

pattern_ingester:
  enabled: true

ruler:
  alertmanager_url: http://localhost:9093
```

로컬에서는 filesystem 기반으로 충분하다.
운영에서는 보통 S3 같은 object storage를 쓴다.

### 7.4 Promtail 설정 예시

```yaml
server:
  http_listen_port: 9080
  grpc_listen_port: 0

positions:
  filename: /tmp/positions.yaml

clients:
  - url: http://loki:3100/loki/api/v1/push

scrape_configs:
  - job_name: shorten-url-service
    static_configs:
      - targets:
          - localhost
        labels:
          job: shorten-url-service
          app: shorten-url-service
          env: dev
          __path__: /var/log/shorten-url/application.log
```

핵심 포인트:

- `job`, `app`, `env`는 라벨이다.
- `__path__`는 실제 로그 파일 경로다.

만약 로그가 JSON이라면 pipeline stage를 추가해서 필드를 파싱할 수 있다.

예:

```yaml
pipeline_stages:
  - json:
      expressions:
        level: level
        requestId: requestId
        method: method
        uri: uri
        statusCode: statusCode
        durationMs: durationMs
  - labels:
      level:
```

여기서도 주의:

- `level` 정도는 라벨로 올려도 괜찮다.
- `requestId`는 라벨로 올리지 않는 편이 좋다.

### 7.5 Prometheus 설정 예시

```yaml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: shorten-url-service
    metrics_path: /actuator/prometheus
    static_configs:
      - targets:
          - host.docker.internal:8080
```

macOS Docker Desktop 기준 로컬 Spring Boot를 컨테이너 Prometheus가 보려면 `host.docker.internal`가 편하다.

만약 앱도 Compose 안에서 띄우면 서비스명으로 바꾸면 된다.

예:

```yaml
targets:
  - app:8080
```

### 7.6 Grafana datasource provisioning 예시

```yaml
apiVersion: 1

datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true

  - name: Loki
    type: loki
    access: proxy
    url: http://loki:3100
```

이렇게 두면 Grafana를 처음 띄울 때 datasource를 수동으로 넣지 않아도 된다.

---

## 8. Grafana에서 실제로 뭘 보게 되나

### 8.1 Prometheus로 보는 대표 그래프

처음엔 아래 네 가지만 있으면 충분하다.

1. 전체 요청 수
2. 5xx 에러 비율
3. 응답시간 P95
4. JVM Heap 사용량

예시 쿼리:

#### 초당 요청 수

```promql
sum(rate(http_server_requests_seconds_count[1m]))
```

#### 5xx 응답 수

```promql
sum(rate(http_server_requests_seconds_count{status=~"5.."}[1m]))
```

#### 5xx 비율

```promql
sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m]))
/
sum(rate(http_server_requests_seconds_count[5m]))
```

#### P95 응답시간

```promql
histogram_quantile(
  0.95,
  sum(rate(http_server_requests_seconds_bucket[5m])) by (le)
)
```

실제 meter 이름은 Spring/Micrometer 버전에 따라 조금 다를 수 있으니, Grafana Explore나 Prometheus UI에서 자동완성을 보면서 맞추면 된다.

### 8.2 Loki에서 보는 대표 로그 검색

#### 앱 전체 로그

```logql
{app="shorten-url-service"}
```

#### ERROR/WARN만

```logql
{app="shorten-url-service", level=~"WARN|ERROR"}
```

#### 특정 URI 포함

```logql
{app="shorten-url-service"} |= "/shortenUrl"
```

#### 특정 requestId 검색

```logql
{app="shorten-url-service"} |= "ma1ab-12cd"
```

#### 500 응답 로그 찾기

```logql
{app="shorten-url-service"} |= "statusCode" |= "500"
```

만약 JSON 로그가 잘 파싱되면 더 좋다.

예:

```logql
{app="shorten-url-service"} | json | statusCode=500
```

---

## 9. 실무에서는 보통 어떻게 하는가

여기가 가장 중요하다.

학습용과 운영용은 다르다.

### 9.1 로컬/스터디용

가장 단순한 방식:

- 애플리케이션
- Loki
- Promtail
- Prometheus
- Grafana

파일 로그 기반으로 실습하기 좋다.

### 9.2 소규모 운영

보통 다음 방식이 많다.

- 애플리케이션은 `stdout JSON`으로 로그 출력
- 컨테이너 런타임 또는 노드 에이전트가 로그 수집
- Prometheus가 `/actuator/prometheus` scrape
- Grafana가 대시보드/알람 제공

환경 예:

- Docker Compose
- ECS
- 작은 Kubernetes 클러스터

이 단계에서 중요한 포인트:

- 로그를 애플리케이션에서 직접 Loki로 밀어 넣기보다, 외부 에이전트가 수집하게 둔다.
- 앱은 관찰 가능성 데이터를 "생산"만 하고, "전송 책임"은 에이전트나 collector에게 맡긴다.

이유:

- 앱이 Loki 장애에 덜 영향을 받는다.
- 전송 재시도/버퍼링/배치를 collector가 담당할 수 있다.
- 운영 구조가 더 유연해진다.

### 9.3 중대형 운영

보통 아래 방향으로 간다.

- 로그: Grafana Alloy 또는 OpenTelemetry Collector 또는 Fluent Bit
- 메트릭: Prometheus + remote_write + 장기 저장소(Mimir/Thanos 등)
- 시각화: Grafana
- 알람: Alertmanager 또는 Grafana Alerting

Kubernetes에서는:

- 로그 수집기: DaemonSet
- 메트릭 수집: Prometheus Operator
- 대시보드: Grafana

로그 저장은:

- Loki의 object storage backend 사용
- S3/GCS/Azure Blob 기반

### 9.4 실무에서 자주 하는 선택

#### 선택 1. 로그는 파일보다 stdout

컨테이너 기반 운영에서는 거의 기본값이다.

이유:

- 컨테이너 표준 방식이다.
- sidecar/daemonset 수집이 쉽다.
- 파일 회전 이슈를 줄인다.

#### 선택 2. JSON 로그

사람이 읽는 예쁜 문자열 로그보다 JSON을 더 많이 쓴다.

이유:

- 파싱이 안정적이다.
- 필드 검색이 쉽다.
- Loki, Elasticsearch, SIEM 도구와 궁합이 좋다.

#### 선택 3. requestId, traceId는 로그 필드로 유지

하지만 라벨로는 잘 안 올린다.

이유:

- 검색은 가능해야 한다.
- 그러나 cardinality 폭발은 막아야 한다.

#### 선택 4. 메트릭은 Prometheus, 로그는 Loki, 알람은 공통화

실무에서는 메트릭 알람과 로그 기반 알람을 혼합한다.

예:

- 메트릭 알람: 5xx 비율 5분 평균 3% 초과
- 로그 알람: 특정 예외 메시지 1분 동안 20건 초과

#### 선택 5. 대시보드는 "운영용"과 "개발용"을 분리

운영용 대시보드:

- 요청 수
- 에러율
- 지연시간
- 인프라 상태

개발용 대시보드:

- 특정 endpoint latency
- 특정 예외 패턴
- 배포 이후 변화

---

## 10. 이 프로젝트에 맞는 실무 권장안

Shorten URL Service 기준으로는 다음 구성이 적절하다.

### 10.1 1단계: 지금 바로 할 수 있는 최소 구성

1. 현재 `Actuator + Prometheus` 구성 유지
2. `application.log`를 Promtail이 읽게 구성
3. Grafana에서 Loki/Prometheus datasource 연결
4. 최소 대시보드 4개 생성

이 단계만 해도 아래가 가능하다.

- `/shortenUrl` 요청량 확인
- 4xx/5xx 추세 확인
- 느린 요청 확인
- requestId 기반 로그 추적

### 10.2 2단계: 로그 포맷 개선

권장:

- `logback.xml`을 JSON 중심으로 재정비
- 예외 로그를 구조화
- `requestId`, `method`, `uri`, `statusCode`, `durationMs`를 고정 필드화

이 단계가 끝나면:

- Loki 검색성이 좋아지고
- Grafana Explore에서 필드 기반 분석이 쉬워지고
- 이후 OTel/Tempo 연계도 쉬워진다

### 10.3 3단계: 알람 추가

최소 알람:

1. 5xx 비율 증가
2. P95 latency 증가
3. 앱 다운
4. JVM heap 급증

필요하면 로그 알람:

5. `LackOfShortenUrlKeyException`
6. 특정 validation failure 급증

### 10.4 4단계: 장기적으로는 trace까지 연결

현재 사용자가 요청한 범위는 Loki/Prometheus/Grafana지만,
실무에서는 결국 trace까지 붙인다.

즉 최종적으로는:

- Logs: Loki
- Metrics: Prometheus
- Traces: Tempo
- UI: Grafana

이 구성이 장애 분석 효율이 가장 좋다.

---

## 11. 운영 대시보드에 꼭 있어야 하는 항목

Shorten URL 서비스라면 아래 정도가 기본이다.

### 애플리케이션 개요

- 전체 RPS
- 전체 4xx 비율
- 전체 5xx 비율
- P50/P95/P99 응답시간
- 애플리케이션 up/down

### 엔드포인트별 분석

- `POST /shortenUrl` 호출 수
- `POST /shortenUrl` 실패율
- `GET /{shortKey}` 호출 수
- `GET /{shortKey}` 404 비율

### JVM/프로세스

- heap used
- GC pause
- thread count
- CPU usage

### 로그 패널

- 최근 ERROR 로그
- 최근 WARN 로그
- requestId 검색용 Explore 링크

---

## 12. 알람은 어떻게 잡는가

초기 운영에서는 과도한 알람보다 "적은 수의 신뢰 가능한 알람"이 중요하다.

추천:

### 1. 앱 다운

```promql
up{job="shorten-url-service"} == 0
```

### 2. 5xx 비율 상승

```promql
(
  sum(rate(http_server_requests_seconds_count{job="shorten-url-service",status=~"5.."}[5m]))
/
  sum(rate(http_server_requests_seconds_count{job="shorten-url-service"}[5m]))
) > 0.03
```

### 3. P95 응답시간 증가

```promql
histogram_quantile(
  0.95,
  sum(rate(http_server_requests_seconds_bucket{job="shorten-url-service"}[5m])) by (le)
) > 0.5
```

여기서 `0.5`는 0.5초다.

### 4. 특정 예외 로그 급증

Grafana/Loki alert 또는 recording rule 기반으로:

```logql
sum(count_over_time({app="shorten-url-service"} |= "LackOfShortenUrlKeyException" [5m])) > 5
```

---

## 13. 실무에서 자주 하는 실수

### 1. requestId를 라벨로 올림

절대 피하는 편이 좋다.
카디널리티가 터진다.

### 2. 로그를 예쁜 문자열로만 남김

사람은 보기 좋지만 기계는 파싱하기 어렵다.
운영에서는 JSON이 낫다.

### 3. Prometheus에 너무 많은 고카디널리티 태그를 붙임

예:

- userId
- full URL
- requestId

이런 태그는 메트릭 cardinality도 터뜨린다.

### 4. 알람을 너무 많이 만듦

알람 피로가 생긴다.
초기엔 적고 강한 알람부터 시작해야 한다.

### 5. 대시보드를 너무 화려하게 만듦

운영 대시보드는 예쁜 것보다 빨리 읽히는 것이 중요하다.

### 6. 로그 보관 기간과 비용을 안 계산함

로그는 계속 쌓인다.
운영 들어가면 retention과 비용 정책을 먼저 잡아야 한다.

---

## 14. 이 프로젝트 기준 권장 로그/메트릭 설계

### 로그에 꼭 남길 것

- `requestId`
- `method`
- `uri`
- `statusCode`
- `durationMs`
- 예외 클래스
- 예외 메시지
- stacktrace
- 주요 비즈니스 이벤트

예:

- 단축 URL 생성 성공
- 존재하지 않는 키 조회
- 만료 URL 접근
- 키 생성 실패

### 메트릭에 꼭 볼 것

- 전체 HTTP 요청 수
- 상태코드별 요청 수
- latency histogram
- JVM heap
- uptime

### 라벨/태그는 낮은 카디널리티만

좋은 예:

- `application`
- `job`
- `instance`
- `env`
- `method`
- `status`

나쁜 예:

- `requestId`
- `userId`
- `originalUrl`
- `shortenUrlKey`

`shortenUrlKey`도 메트릭 태그로는 대개 부적절하다.
종류가 계속 늘어나기 때문이다.

---

## 15. 추천 도입 순서

당장 실무적으로 가장 무리 없는 순서는 이렇다.

1. 현재 앱에 대해 `/actuator/prometheus` 확인
2. 로컬에 Prometheus 붙이기
3. Grafana 붙여서 메트릭 대시보드 만들기
4. Promtail + Loki 붙여 로그 보기
5. 로그를 JSON 구조화로 개선
6. 알람 규칙 추가
7. 이후 필요하면 Tempo까지 확장

이 순서가 좋은 이유:

- 가장 빠르게 성과가 보인다.
- 장애 대응 능력이 즉시 올라간다.
- 이후 trace 도입도 자연스럽다.

---

## 16. 결론

이 프로젝트에 Loki, Prometheus, Grafana를 붙인다는 것은 단순히 도구 3개를 설치하는 일이 아니다.
정확히는 다음 체계를 만드는 일이다.

- Prometheus로 "문제가 생겼는지"를 빠르게 감지하고
- Loki로 "왜 문제가 생겼는지"를 추적하고
- Grafana로 "같은 시간축에서 함께" 본다

이 프로젝트는 이미:

- Spring Boot Actuator
- Prometheus registry
- requestId 기반 로깅

이 들어가 있으므로 시작점은 좋다.

가장 현실적인 다음 단계는 이것이다.

1. 로컬 Docker Compose로 Loki/Promtail/Prometheus/Grafana를 띄운다.
2. 앱의 `/actuator/prometheus`를 Prometheus가 scrape하게 만든다.
3. `application.log`를 Promtail이 Loki로 보내게 한다.
4. Grafana에서 메트릭과 로그를 함께 본다.
5. 이후 로그를 JSON 구조화하고 알람을 붙인다.

---

## 17. 다음 작업으로 추천하는 것

원하면 다음 단계도 바로 이어서 진행할 수 있다.

1. `monitoring/` 디렉터리에 실제 `docker-compose.yml`, `prometheus.yml`, `loki-config.yml`, `promtail-config.yml`, `grafana provisioning` 파일 생성
2. `logback.xml`을 Loki 친화적인 JSON 로그 형태로 개편
3. Grafana 기본 대시보드 JSON 또는 provisioning까지 추가
4. Prometheus/Grafana alert rule 예시 추가

개인적으로는 다음 순서를 추천한다.

1. 먼저 `monitoring/` 실행 파일들 추가
2. 그 다음 `logback.xml` 정리
3. 마지막으로 대시보드/알람 추가

이렇게 가면 문서가 설명으로만 끝나지 않고, 이 저장소에서 바로 실행 가능한 상태가 된다.
