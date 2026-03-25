# Plan 4: 중앙 집중 로그 — Loki + Grafana

> **인프라 단계**: 다중 서버
> **선행 조건**: Plan 1~3 완료 (requestId, StructuredArguments, ELK 경험)
> **핵심 질문**: "서버가 여러 대일 때, 어느 서버에서 병목이 발생하고 있는가?"

---

## 이 단계의 목표

서버가 2대 이상으로 늘어난 환경에서, **모든 서버의 로그를 한 곳에서 조회**하고 **어느 서버가 문제인지 식별**할 수 있게 만든다.
동시에 ELK의 높은 리소스 비용을 해결하기 위해 **Loki + Grafana**로 전환한다.

---

## 왜 이 단계가 필요한가

### 다중 서버 환경의 근본 문제

단일 서버에서는 `application.log` 하나만 보면 된다. 하지만 서버가 3대가 되면:

```
서버 A: /app/logs/application.log
서버 B: /app/logs/application.log
서버 C: /app/logs/application.log
```

장애가 발생하면:
1. SSH로 서버 A에 접속 → `grep "ERROR" application.log` → 에러 없음
2. SSH로 서버 B에 접속 → `grep "ERROR" application.log` → 에러 발견!
3. 하지만 같은 시간에 서버 C에서도 에러가 났는지? → SSH로 서버 C 접속 → 확인

**3대면 3번, 10대면 10번 SSH 접속**. 이것이 반복되면 장애 대응이 느려진다.

### ELK로 해결되지 않는가?

ELK(Plan 3)는 중앙 집중 로그를 제공한다. 하지만:

| 문제 | 상세 |
|------|------|
| **Elasticsearch의 리소스 소비** | ES는 모든 로그의 **전문(full-text)을 인덱싱**한다. 로그가 하루 10GB면 인덱스도 수 GB → 메모리/디스크 부담 |
| **운영 복잡도** | ES 클러스터 관리(샤드 배분, ILM, JVM 튜닝)가 필요. 학습/소규모 서비스에는 과도 |
| **비용** | AWS Elasticsearch Service 기준, 소규모에서도 월 수만~수십만원 |

### Loki가 해결하는 것

Loki는 로그 내용을 **인덱싱하지 않는다**. 라벨(service, level, hostname)만 인덱싱하고, 로그 본문은 압축 저장한다.

| | Elasticsearch | Loki |
|---|---|---|
| 인덱싱 대상 | 모든 필드의 모든 값 | 라벨(key-value)만 |
| 저장 비용 | 높음 (인덱스 + 원본) | 낮음 (압축 원본만) |
| 검색 속도 | 빠름 (인덱스 사용) | 라벨 필터 후 본문 스캔 |
| 적합한 규모 | 대규모 (하루 수백 GB) | 소~중규모 (하루 수 GB~수십 GB) |
| Grafana 통합 | 별도 (Kibana 사용) | 네이티브 (같은 UI에서 로그 + 메트릭 + 트레이스) |

핵심: Loki는 "로그를 보관하고 라벨로 빠르게 필터링"하는 데 특화되어 있다. "모든 텍스트를 전문 검색"이 필요하면 ES가 낫지만, 대부분의 병목 추적은 라벨 필터링 + grep으로 충분하다.

---

## AS-IS (현재 상태 — Plan 3 완료 후)

### 아키텍처

```
[App (prod profile)] --TCP:5044--> [Logstash] --HTTP:9200--> [Elasticsearch] <--HTTP:5601-- [Kibana]
```

### Logstash appender (logback-spring.xml)

```xml
<appender name="LOGSTASH" class="net.logstash.logback.appender.LogstashTcpSocketAppender">
    <destination>localhost:5044</destination>
    <reconnectionDelay>5 seconds</reconnectionDelay>
    <queueSize>512</queueSize>
    <encoder class="net.logstash.logback.encoder.LogstashEncoder">
        <customFields>{"service":"shortenurlservice"}</customFields>
    </encoder>
</appender>
```

### 한계

| 한계 | 상세 |
|------|------|
| **서버 식별 불가** | `service` 필드는 있지만 **어느 서버 인스턴스**에서 보낸 로그인지 구분 안 됨 |
| **ES 리소스 부담** | 서버가 늘어나면 로그량도 비례 증가 → ES 메모리/디스크 빠르게 소진 |
| **Kibana ≠ Grafana** | 추후 메트릭(Prometheus)과 트레이스(Tempo)를 추가하면 UI가 3개로 분산됨 |

---

## TO-BE (목표 상태)

### 아키텍처

```
[App 인스턴스 1] --HTTP:3100--> [Loki] <--HTTP:3000-- [Grafana]
[App 인스턴스 2] --HTTP:3100-->    ↑
[App 인스턴스 3] --HTTP:3100-->    |
```

- 앱에서 Loki로 **직접 HTTP push** (Logstash 불필요)
- Grafana에서 Loki를 데이터소스로 연결

### 1. pom.xml 의존성 추가

```xml
<dependency>
    <groupId>com.github.loki4j</groupId>
    <artifactId>loki-logback-appender</artifactId>
    <version>1.5.2</version>
</dependency>
```

### 2. logback-spring.xml LOKI appender 추가

```xml
<!-- Loki로 전송할 Appender -->
<appender name="LOKI" class="com.github.loki4j.logback.Loki4jAppender">
    <http>
        <url>http://localhost:3100/loki/api/v1/push</url>
    </http>
    <format>
        <label>
            <pattern>service=shortenurlservice,level=%level,hostname=${HOSTNAME}</pattern>
        </label>
        <message>
            <pattern>
                {
                  "timestamp": "%d{yyyy-MM-dd'T'HH:mm:ss.SSS}",
                  "level": "%level",
                  "logger": "%logger",
                  "thread": "%thread",
                  "requestId": "%X{requestId}",
                  "message": "%message"
                }
            </pattern>
        </message>
    </format>
</appender>

<!-- loki 프로파일 -->
<springProfile name="loki">
    <root level="INFO">
        <appender-ref ref="CONSOLE" />
        <appender-ref ref="LOKI" />
    </root>
</springProfile>
```

### 3. docker-compose-loki.yml (신규)

```yaml
version: '3.8'
services:
  loki:
    image: grafana/loki:2.9.4
    ports:
      - "3100:3100"
    command: -config.file=/etc/loki/local-config.yaml

  grafana:
    image: grafana/grafana:10.3.1
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
    volumes:
      - ./grafana/provisioning:/etc/grafana/provisioning
    depends_on:
      - loki
```

### 4. Grafana 데이터소스 프로비저닝

`grafana/provisioning/datasources/loki.yml`:
```yaml
apiVersion: 1
datasources:
  - name: Loki
    type: loki
    access: proxy
    url: http://loki:3100
    isDefault: true
```

---

## 변경 항목별 상세 설명

### 항목 1: loki-logback-appender 의존성

#### 무엇을
앱에서 Loki로 직접 로그를 HTTP로 push하는 Logback appender를 추가한다.

#### 왜 필요한가

ELK에서는 앱 → Logstash → Elasticsearch 경로였다. Logstash는 중간에서 로그를 가공/전달하는 역할이다.

Loki는 앱에서 **직접 HTTP API로 push**할 수 있다:
- 중간 컴포넌트(Logstash) 불필요 → 구조 단순화
- TCP 연결 관리 불필요 → HTTP는 상태가 없으므로 더 안정적
- 실패 시 재시도가 HTTP 레벨에서 처리됨

#### 왜 Promtail이 아닌 직접 push인가

로그를 Loki에 보내는 방법은 크게 2가지:

| 방법 | 설명 | 장점 | 단점 |
|------|------|------|------|
| **Promtail** (에이전트) | 서버에 설치, 로그 파일을 읽어서 Loki로 전송 | 앱 코드 변경 없음, 기존 파일 로그 활용 | 에이전트 설치/관리 필요 |
| **loki-logback-appender** (직접 push) | 앱에서 Loki HTTP API로 직접 전송 | 에이전트 불필요, 즉시 전송 | 앱에 의존성 추가 |

현재 단계에서는 **학습 환경**이므로 에이전트 없이 직접 push가 간단하다. 프로덕션 환경(쿠버네티스 등)에서는 DaemonSet으로 Promtail을 배포하는 것이 일반적이다.

### 항목 2: Loki 라벨(label) 설계

#### 무엇을
모든 로그에 `service`, `level`, `hostname` 라벨을 붙인다.

#### 왜 라벨이 중요한가

Loki는 **라벨만 인덱싱**한다. 라벨로 필터링해야 빠르고, 라벨이 없는 필드는 본문 스캔(느림)으로 검색해야 한다.

```
# 빠름 (라벨 인덱스 사용)
{service="shortenurlservice", level="ERROR"}

# 느림 (본문 전체 스캔)
{service="shortenurlservice"} |= "NullPointerException"
```

#### 각 라벨의 역할

| 라벨 | 왜 필요한가 | 예시 쿼리 |
|------|------------|----------|
| `service` | 어떤 서비스의 로그인지 구분 (MSA 대비) | `{service="shortenurlservice"}` |
| `level` | 로그 레벨로 필터링 (에러만 보기) | `{level="ERROR"}` |
| `hostname` | **어느 서버 인스턴스**에서 발생했는지 구분 | `{hostname="server-01"}` |

#### 왜 hostname이 핵심인가

서버 3대에서 같은 에러가 나면:
- hostname 없이: "에러가 발생했다" → 어느 서버인지 모름
- hostname 있으면: "server-02에서만 에러 발생" → server-02의 디스크/네트워크/메모리 문제 확인

```
# server-02에서만 에러가 나는지 확인
{service="shortenurlservice", level="ERROR"} | hostname="server-02"

# 서버별 에러 건수 비교
sum by(hostname) (rate({service="shortenurlservice", level="ERROR"}[5m]))
```

#### 라벨 카디널리티 주의

Loki에서 라벨의 **고유 값 수(카디널리티)**가 너무 높으면 성능이 저하된다:

| 라벨 후보 | 카디널리티 | 적합 여부 |
|-----------|-----------|----------|
| `service` | 수 개~수십 개 | 적합 |
| `level` | 5개 (TRACE~ERROR) | 적합 |
| `hostname` | 서버 대수 (수 대~수십 대) | 적합 |
| `requestId` | **요청 건수 (수백만)** | **부적합** — 라벨로 만들면 안 됨! |
| `userId` | **사용자 수 (수백만)** | **부적합** |

`requestId`는 라벨이 아닌 **로그 본문에 JSON 필드**로 포함시킨다. 검색할 때는:
```
{service="shortenurlservice"} | json | requestId="a1b2c3d4"
```

### 항목 3: Grafana 대시보드

#### 무엇을
Grafana에서 다음을 시각화하는 대시보드를 만든다:
- 로그 볼륨 (레벨별 시계열)
- 에러율 추세
- 느린 요청(WARN) 빈도
- 서버별 로그 건수

#### 왜 대시보드가 필요한가

Plan 3의 Kibana Discover에서도 로그를 볼 수 있었다. 하지만 Grafana 대시보드는 다른 가치를 제공한다:

| Kibana Discover | Grafana Dashboard |
|-----------------|-------------------|
| 사후 분석 (문제가 생긴 후 검색) | **실시간 모니터링** (대시보드를 띄워놓고 추세 감시) |
| 매번 쿼리 입력 필요 | 한 번 설정하면 **자동 갱신** |
| 로그만 표시 | 로그 + 메트릭 + 트레이스를 **같은 UI**에서 표시 (Plan 5~6 대비) |

#### 핵심 패널 설계

**패널 1: 로그 볼륨 (레벨별)**
```
# LogQL
sum by(level) (rate({service="shortenurlservice"}[5m]))
```
→ INFO, WARN, ERROR의 시계열 그래프. ERROR가 갑자기 늘어나면 이상 징후.

**패널 2: 서버별 에러 건수**
```
sum by(hostname) (rate({service="shortenurlservice", level="ERROR"}[5m]))
```
→ 특정 서버에서만 에러가 집중되면 해당 서버의 인프라 문제.

**패널 3: 느린 요청 감지**
```
{service="shortenurlservice", level="WARN"} |= "느린 요청 감지"
```
→ 느린 요청의 목록. durationMs 값으로 심각도 판단.

---

## 이점

### 시나리오: "서버 3대 중 하나에서만 간헐적 에러 발생"

**AS-IS (ELK — Plan 3)**:
1. Kibana에서 `level: "ERROR"` 필터링 → 에러 목록 확인
2. 하지만 **어느 서버에서 발생한 에러인지 구분하는 필드가 없다** (service는 있지만 hostname이 없음)
3. 로그 메시지를 일일이 읽으면서 추측

**TO-BE (Loki + Grafana — Plan 4)**:
1. Grafana 대시보드의 "서버별 에러 건수" 패널을 본다
2. `server-02`에서만 에러율이 높은 게 보인다
3. `{hostname="server-02", level="ERROR"}` → 해당 서버의 에러만 필터링
4. 소요 시간: **30초**

### 시나리오: "전체 시스템의 건강 상태를 한눈에 파악"

**AS-IS**: Kibana Discover에서 매번 쿼리를 입력해야 함
**TO-BE**: Grafana 대시보드를 모니터에 띄워놓으면 실시간으로 추세 확인. 이상 징후 시 Grafana 알림(추후 설정)

---

## 수정 대상 파일

| 파일 | 변경 내용 |
|------|----------|
| `pom.xml` | `loki-logback-appender` 의존성 추가 |
| `src/main/resources/logback-spring.xml` | LOKI appender + `loki` 프로파일 추가 |
| 신규: `docker-compose-loki.yml` | Loki + Grafana 스택 |
| 신규: `grafana/provisioning/datasources/loki.yml` | Loki 데이터소스 자동 설정 |

---

## 검증 방법

### 1. Loki 스택 기동 + 로그 수집 확인
```bash
# Loki + Grafana 기동
docker compose -f docker-compose-loki.yml up -d

# loki 프로파일로 앱 기동
./mvnw spring-boot:run -Dspring-boot.run.profiles=loki

# 요청 발생
curl -X POST http://localhost:8080/shortenUrl \
  -H "Content-Type: application/json" \
  -d '{"originalUrl":"https://naver.com"}'
```

### 2. Grafana에서 로그 확인
1. `http://localhost:3000` 접속 (admin/admin)
2. 좌측 메뉴 → Explore
3. 데이터소스: Loki 선택
4. 쿼리: `{service="shortenurlservice"}`
5. 로그가 나타나는지 확인

### 3. 라벨 필터링 확인
```
{service="shortenurlservice", level="ERROR"}
```
→ 에러 로그만 나타나는지 확인

### 4. hostname 라벨 확인
```
{service="shortenurlservice"} | hostname
```
→ hostname이 현재 머신의 이름으로 나타나는지 확인

### 5. LogQL 파이프라인 확인
```
{service="shortenurlservice"} | json | requestId="a1b2c3d4"
```
→ 특정 requestId의 로그만 필터링되는지 확인
