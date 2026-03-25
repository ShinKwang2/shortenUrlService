# Plan 6: 통합 옵저버빌리티 — Logs + Metrics + Traces

> **인프라 단계**: MSA (마이크로서비스 아키텍처)
> **선행 조건**: Plan 1~5 완료
> **핵심 질문**: "에러율이 올라갔다 → 어떤 에러? → 근본 원인은? 을 하나의 흐름으로 추적할 수 있는가?"

---

## 이 단계의 목표

옵저버빌리티의 **세 가지 신호(Logs, Metrics, Traces)**를 통합하여, **"무엇이 일어나고 있는가(메트릭) → 왜 일어났는가(로그) → 어디서 일어났는가(트레이스)"** 를 하나의 대시보드에서 연결하여 추적할 수 있게 만든다.

---

## 왜 이 단계가 필요한가

### 세 가지 신호의 역할

| 신호 | 역할 | 답하는 질문 | Plan |
|------|------|------------|------|
| **Logs** | 무엇이 일어났는지 상세 기록 | "어떤 에러가 났는가?" | Plan 1~4에서 구축 |
| **Traces** | 요청의 서비스 간 경로 추적 | "어느 서비스에서 느린가?" | Plan 5에서 구축 |
| **Metrics** | 수치화된 집계 (건수, 비율, 지연시간) | "**얼마나** 자주? **얼마나** 심각한가?" | **이번 Plan에서 구축** |

### Plan 5까지의 한계

Plan 5까지 로그와 트레이스가 있다. 하지만:

1. **"얼마나"를 모른다**: 에러가 났다는 건 알지만, "에러율이 5%인지 0.1%인지" 모른다. 1건의 에러와 1000건의 에러는 대응 방식이 다르다
2. **추세를 모른다**: "어제보다 느려졌는가?" → 로그를 일일이 세서 비교해야 한다
3. **알림이 없다**: 에러율이 급증해도 누군가 로그를 보지 않으면 모른다
4. **용량 계획이 안 된다**: "초당 요청 수가 얼마나 되는가?" → 로그 건수를 세는 것은 정확하지 않다

### 메트릭이 해결하는 것

```
# "현재 에러율이 얼마인가?" — 즉시 답변 가능
rate(shortenurl_created_total{status="error"}[5m]) / rate(shortenurl_created_total[5m])

# "키 생성 p99 지연시간이 얼마인가?" — 즉시 답변 가능
histogram_quantile(0.99, rate(shortenurl_key_generation_seconds_bucket[5m]))

# "에러율이 5%를 넘으면 알림" — 자동 감지
ALERT: rate(http_server_requests_seconds_count{status=~"5.."}[5m]) > 0.05
```

---

## AS-IS (현재 상태 — Plan 5 완료 후)

### 가지고 있는 것

```
[App] → [Loki]  ← Logs (구조화된 필드, requestId, traceId)
[App] → [Tempo] ← Traces (워터폴 뷰, 서비스 간 추적)
         ↑
     [Grafana]
```

### 가지고 있지 않은 것

| 부재 | 영향 |
|------|------|
| **메트릭 없음** | "초당 URL 생성 건수", "에러율", "p99 지연시간" 같은 수치 집계 불가 |
| **Actuator 미노출** | 런타임 로그 레벨 변경 불가, 앱 상태(health) 확인 불가 |
| **비즈니스 메트릭 없음** | 비즈니스 KPI(단축 URL 생성수, 리다이렉트 수)를 실시간 모니터링 불가 |
| **알림 없음** | 이상 징후를 사람이 직접 발견해야 함 |

### "메트릭 없이" 병목을 찾는 현재 방법

"시스템이 느려졌다"는 신고가 들어오면:
1. Grafana Loki에서 최근 WARN 로그("느린 요청 감지")를 검색
2. 건수를 눈으로 센다 (또는 LogQL `count_over_time`으로 집계)
3. 시계열 추세? → LogQL로 가능하지만, 쿼리할 때마다 **전체 로그를 스캔** → 느리고 비효율적

메트릭은 **사전에 집계된 숫자**다. 쿼리 시점에 계산하는 게 아니라, 이벤트 발생 시 카운터/타이머에 **즉시 반영**된다. 그래서 메트릭 쿼리는 밀리초 단위로 응답한다.

---

## TO-BE (목표 상태)

### 아키텍처

```
[App] → [Loki]       ← Logs
[App] → [Tempo]      ← Traces
[App] → [Prometheus] ← Metrics (/actuator/prometheus)  ← 신규
         ↑
     [Grafana] (Loki + Tempo + Prometheus 통합 대시보드)
```

### 1. pom.xml 의존성 추가

```xml
<!-- Spring Boot Actuator -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>

<!-- Prometheus 메트릭 레지스트리 -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

#### 왜 이 의존성들인가

**spring-boot-starter-actuator**:
- `/actuator/health`: 앱이 살아있는지 확인 (로드밸런서/쿠버네티스가 사용)
- `/actuator/loggers`: 런타임에 로그 레벨 변경 (재시작 없이 DEBUG 전환)
- `/actuator/prometheus`: Prometheus가 스크랩할 수 있는 메트릭 엔드포인트
- `/actuator/info`: 앱 버전/빌드 정보

**micrometer-registry-prometheus**:
- Micrometer의 메트릭(Counter, Timer, Gauge 등)을 **Prometheus 형식으로 노출**
- 왜 Prometheus인가:
  - Grafana와 네이티브 통합
  - **Pull 모델**: Prometheus가 앱에서 메트릭을 가져감 → 앱에 부담 최소
  - 업계 사실상 표준 (CNCF 프로젝트)

### 2. application.yaml 설정

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, info, loggers, prometheus, metrics
  tracing:
    sampling:
      probability: 1.0
  otlp:
    tracing:
      endpoint: http://localhost:4318/v1/traces
```

#### 각 엔드포인트를 왜 노출하는가

| 엔드포인트 | 왜 필요한가 |
|-----------|------------|
| `health` | 로드밸런서가 주기적으로 호출하여 앱 상태 확인. DOWN이면 트래픽 차단 |
| `info` | 어떤 버전의 앱이 배포되어 있는지 확인. 롤백 판단에 사용 |
| `loggers` | **프로덕션에서 재시작 없이** 특정 클래스의 로그 레벨을 변경. 예: `POST /actuator/loggers/kr.co.shortenurlservice.application -d '{"configuredLevel":"DEBUG"}'` → SimpleShortenUrlService의 DEBUG 로그 활성화 |
| `prometheus` | Prometheus가 주기적으로 스크랩하는 메트릭 엔드포인트 |
| `metrics` | 앱 내부에서 메트릭 값을 직접 확인 (디버깅용) |

#### 왜 모든 엔드포인트(`*`)가 아닌 특정 엔드포인트만 노출하는가

- `env`: 환경변수 노출 → **비밀번호, API 키 유출 위험**
- `heapdump`: 힙 덤프 → 메모리에 있는 민감 데이터 유출 가능
- `shutdown`: 앱 종료 → 악의적 호출 시 서비스 중단

필요한 것만 명시적으로 나열하는 것이 안전하다.

### 3. 비즈니스 메트릭 추가 (SimpleShortenUrlService.java)

```java
// before
@Slf4j
@Service
public class SimpleShortenUrlService {

    private ShortenUrlRepository shortenUrlRepository;

    @Autowired
    public SimpleShortenUrlService(ShortenUrlRepository shortenUrlRepository) {
        this.shortenUrlRepository = shortenUrlRepository;
    }

    // ... 메서드들
}

// after
@Slf4j
@Service
public class SimpleShortenUrlService {

    private final ShortenUrlRepository shortenUrlRepository;
    private final Counter urlCreatedCounter;
    private final Counter urlRedirectedCounter;
    private final Timer keyGenerationTimer;

    @Autowired
    public SimpleShortenUrlService(ShortenUrlRepository shortenUrlRepository,
                                    MeterRegistry meterRegistry) {
        this.shortenUrlRepository = shortenUrlRepository;

        this.urlCreatedCounter = Counter.builder("shortenurl.created")
                .description("단축 URL 생성 건수")
                .register(meterRegistry);

        this.urlRedirectedCounter = Counter.builder("shortenurl.redirected")
                .description("단축 URL 리다이렉트 건수")
                .register(meterRegistry);

        this.keyGenerationTimer = Timer.builder("shortenurl.key.generation")
                .description("단축 URL 키 생성 소요 시간")
                .register(meterRegistry);
    }

    public ShortenUrlCreateResponseDto generateShortenUrl(...) {
        String originalUrl = shortenUrlCreateRequestDto.getOriginalUrl();

        // Timer로 키 생성 시간 측정
        String shortenUrlKey = keyGenerationTimer.record(() -> getUniqueShortenUrlKey());

        ShortenUrl shortenUrl = new ShortenUrl(originalUrl, shortenUrlKey);
        shortenUrlRepository.saveShortenUrl(shortenUrl);

        urlCreatedCounter.increment();  // 생성 카운터 증가

        log.info("shortenUrl 생성",
                kv("shortenUrlKey", shortenUrlKey),
                kv("originalUrl", originalUrl));

        return new ShortenUrlCreateResponseDto(shortenUrl);
    }

    public String getOriginalUrlByShortenUrlKey(String shortenUrlKey) {
        // ... 기존 로직

        urlRedirectedCounter.increment();  // 리다이렉트 카운터 증가

        return originalUrl;
    }
}
```

---

## 변경 항목별 상세 설명

### 항목 1: Counter — 왜 필요한가

#### 무엇을
`shortenurl.created`와 `shortenurl.redirected` Counter를 추가한다.

#### 왜 이 메트릭들인가

**RED Method** (Rate, Errors, Duration)로 판단:

| RED | 메트릭 | 왜 필요한가 |
|-----|--------|------------|
| **Rate** | `shortenurl.created` | "초당 URL 생성 건수"를 실시간 모니터링. 트래픽 급증/급감 감지 |
| **Rate** | `shortenurl.redirected` | "초당 리다이렉트 건수". 단축 URL의 실제 사용량 파악 |
| **Error** | Spring Boot 자동 제공 (`http_server_requests` with status=5xx) | 에러율 자동 집계 |
| **Duration** | `shortenurl.key.generation` | 키 생성 지연 시간. 키 공간 고갈 시 조기 경보 |

#### 왜 로그의 duration과 별개로 메트릭이 필요한가

| | 로그의 duration | 메트릭의 Timer |
|---|---|---|
| 저장 | 로그 파일/Loki에 텍스트로 저장 | Prometheus에 **사전 집계된 숫자**로 저장 |
| 쿼리 속도 | LogQL로 스캔 → 수 초 | PromQL → **밀리초** |
| p99 계산 | 전체 로그를 읽어서 계산 | histogram_quantile → **즉시** |
| 알림 | 불가능 (로그 기반 알림은 비효율적) | Grafana Alert Rule로 **자동 알림** |

예시:
```
# "키 생성 p99 지연시간이 200ms를 넘으면 알림"
histogram_quantile(0.99, rate(shortenurl_key_generation_seconds_bucket[5m])) > 0.2
```
→ 이 쿼리는 Prometheus에서 밀리초 만에 응답하고, 알림 규칙으로 설정 가능.
→ 같은 질문을 로그로 답하려면 전체 로그를 스캔해야 한다.

### 항목 2: Timer — 왜 필요한가

#### 무엇을
`shortenurl.key.generation` Timer를 추가하여 키 생성 소요 시간을 측정한다.

#### 왜 이 작업인가

`getUniqueShortenUrlKey()`는 이 서비스에서 **가장 병목 가능성이 높은 작업**이다:
- 랜덤 키를 생성하고 DB에서 중복 확인
- 중복이면 **최대 5회 재시도**
- 데이터가 쌓일수록 충돌 확률 증가 → 재시도 증가 → 지연 증가

Timer는 다음을 자동으로 기록한다:
- **count**: 호출 횟수
- **sum**: 총 소요 시간
- **max**: 최대 소요 시간
- **histogram**: 분포 (p50, p90, p99 계산 가능)

이를 통해:
```
# 평균 키 생성 시간
rate(shortenurl_key_generation_seconds_sum[5m]) / rate(shortenurl_key_generation_seconds_count[5m])

# p99 키 생성 시간 (100번 중 99번째로 느린 요청)
histogram_quantile(0.99, rate(shortenurl_key_generation_seconds_bucket[5m]))
```

→ p99가 급증하면 **키 공간 고갈의 조기 경보** → 데이터 정리나 키 길이 확장 등의 대응

#### Timer.record()의 장점

```java
// 방법 1: System.nanoTime() 직접 사용 (Plan 2에서 했던 방식)
long start = System.nanoTime();
String key = getUniqueShortenUrlKey();
long duration = (System.nanoTime() - start) / 1_000_000;

// 방법 2: Timer.record() 사용
String key = keyGenerationTimer.record(() -> getUniqueShortenUrlKey());
```

Timer.record()의 장점:
- 예외가 발생해도 시간이 기록됨 (try-finally 불필요)
- Prometheus에 자동으로 histogram bucket이 생성됨
- 코드가 간결

### 항목 3: Actuator — 왜 필요한가

#### /actuator/loggers — 런타임 로그 레벨 변경

**시나리오**: 프로덕션에서 특정 에러가 발생하고 있다. 원인을 파악하려면 SimpleShortenUrlService의 DEBUG 로그가 필요하다.

**Actuator 없이**:
1. application.yaml에서 로그 레벨 변경
2. 앱 재시작 (서비스 중단!)
3. 디버깅
4. 로그 레벨 복원
5. 앱 재시작 (또 서비스 중단!)

**Actuator 있으면**:
```bash
# DEBUG 활성화 (재시작 없음)
curl -X POST http://localhost:8080/actuator/loggers/kr.co.shortenurlservice.application \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel":"DEBUG"}'

# 디버깅 완료 후 INFO로 복원 (재시작 없음)
curl -X POST http://localhost:8080/actuator/loggers/kr.co.shortenurlservice.application \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel":"INFO"}'
```

→ **서비스 중단 없이** 실시간 디버깅 가능

#### /actuator/health — 왜 필요한가

로드밸런서(Nginx, ALB 등)가 주기적으로 호출하여 앱 상태를 확인한다:
- `UP` → 트래픽 전달
- `DOWN` → 트래픽 차단, 다른 서버로 라우팅

쿠버네티스에서는:
- `livenessProbe`: 앱이 살아있는지 → 죽으면 Pod 재시작
- `readinessProbe`: 트래픽을 받을 준비가 되었는지 → 아니면 서비스에서 제외

### 항목 4: Grafana 통합 대시보드 — 세 신호의 연결

#### 무엇을
하나의 Grafana 대시보드에서 메트릭, 로그, 트레이스를 연결하여 표시한다.

#### 왜 통합이 필요한가

세 신호를 **따로 보면**:
1. Prometheus: "에러율 5%" → 어떤 에러?
2. Loki로 이동: "NullPointerException" → 어디서?
3. Tempo로 이동: "urlValidationService에서 발생" → 왜?

세 신호를 **연결하면**:
1. 대시보드의 에러율 그래프에서 스파이크 발견
2. 같은 시간대의 에러 로그 패널이 바로 아래에 → "NullPointerException" 확인
3. 로그의 traceId 클릭 → Tempo 워터폴 → 근본 원인 서비스 확인
4. **하나의 화면에서 3단계를 30초 이내에 완료**

#### 연결의 핵심: traceId

세 신호를 연결하는 공통 키가 `traceId`이다:
- **로그**: `traceId` MDC 필드
- **트레이스**: traceId로 워터폴 조회
- **메트릭**: exemplar에 traceId 포함 (Prometheus exemplar 기능)

```
[메트릭 스파이크] --traceId--> [에러 로그] --traceId--> [트레이스 워터폴]
```

### 대시보드 패널 설계

```
┌─────────────────────────────────────────────────────────┐
│ 패널 1: 요청률 (Prometheus)                               │
│ rate(http_server_requests_seconds_count[5m])            │
│ [=====|======|========|==========]  ← 시계열 그래프      │
├─────────────────────────────────────────────────────────┤
│ 패널 2: 에러율 (Prometheus)                               │
│ rate(http_server_requests_seconds_count{status=~"5.."}  │
│ [__|__|_____|████████|_____]        ← 스파이크 발견!      │
├─────────────────────────────────────────────────────────┤
│ 패널 3: p99 지연시간 (Prometheus)                         │
│ histogram_quantile(0.99, ...)                            │
│ [__|__|__|███████|__|__]            ← 지연 증가 확인      │
├─────────────────────────────────────────────────────────┤
│ 패널 4: 에러 로그 스트림 (Loki)                            │
│ {service="shortenurlservice", level="ERROR"}             │
│ 12:00 NullPointerException at... [traceId: abc123] ←클릭│
├─────────────────────────────────────────────────────────┤
│ 패널 5: 비즈니스 메트릭 (Prometheus)                       │
│ rate(shortenurl_created_total[5m])                       │
│ rate(shortenurl_redirected_total[5m])                    │
└─────────────────────────────────────────────────────────┘
```

---

## docker-compose-observability.yml (신규)

```yaml
version: '3.8'
services:
  loki:
    image: grafana/loki:2.9.4
    ports:
      - "3100:3100"

  tempo:
    image: grafana/tempo:2.3.1
    ports:
      - "4318:4318"
      - "3200:3200"
    volumes:
      - ./tempo/tempo.yaml:/etc/tempo.yaml
    command: [ "-config.file=/etc/tempo.yaml" ]

  prometheus:
    image: prom/prometheus:v2.49.1
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml

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
      - tempo
      - prometheus
```

`prometheus/prometheus.yml`:
```yaml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'shortenurlservice'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['host.docker.internal:8080']
```

#### 왜 scrape_interval이 15초인가

- Prometheus는 **Pull 모델**: 15초마다 앱의 `/actuator/prometheus`를 호출하여 메트릭 수집
- 15초가 너무 길면(실시간성 부족), 5초로 줄일 수 있지만 앱/Prometheus 부하 증가
- 15초가 너무 짧으면, 30초~1분으로 늘릴 수 있지만 세밀한 스파이크를 놓칠 수 있음
- 15초는 **업계 표준 기본값**

---

## 이점

### 시나리오: "새벽 3시, 에러율 급증 알림이 왔다"

**AS-IS (Plan 5까지 — 메트릭 없음)**:
1. 알림 자체가 오지 않음 (알림 시스템이 없으므로). 아침에 출근해서 사용자 불만으로 발견
2. Loki에서 ERROR 로그 검색 → 언제부터 에러가 났는지 로그를 시간대별로 세야 함
3. 원인 파악에 **30분~1시간**

**TO-BE (Plan 6)**:
1. Grafana Alert: `에러율 > 5%` → Slack/이메일 알림 → **즉시 인지**
2. 대시보드 접속: 에러율 그래프에서 2:47부터 스파이크 확인
3. 같은 시간대의 에러 로그 패널: `LackOfShortenUrlKeyException` 반복
4. 비즈니스 메트릭: `shortenurl.key.generation` p99가 2:47부터 급증
5. 결론: **키 공간 고갈로 재시도 실패** → 키 길이 확장 또는 데이터 정리 필요
6. 원인 파악에 **5분**

### Plan 1~6 전체 여정 요약

| Plan | 답할 수 있는 질문 | 없으면? |
|------|-----------------|---------|
| 1 | "이 에러가 어떤 요청에서 났는가?" | 로그가 뒤섞여 추적 불가 |
| 2 | "요청의 어느 단계에서 느린가?" | 전체 시간만 알고 내부 분해 불가 |
| 3 | "시간대별 에러 추세는?" | grep으로 수작업 집계 |
| 4 | "어느 서버가 문제인가?" | SSH 반복 접속 |
| 5 | "어느 서비스가 병목인가?" | 서비스 로그를 눈으로 대조 |
| **6** | **"얼마나 자주, 얼마나 심각한가?"** | **로그를 일일이 세야 함** |

---

## 수정 대상 파일

| 파일 | 변경 내용 |
|------|----------|
| `pom.xml` | `spring-boot-starter-actuator`, `micrometer-registry-prometheus` 추가 |
| `src/main/resources/application.yaml` | Actuator 엔드포인트 노출 설정 |
| `src/main/java/.../application/SimpleShortenUrlService.java` | Counter, Timer 추가 |
| 신규: `docker-compose-observability.yml` | Loki + Tempo + Prometheus + Grafana 통합 |
| 신규: `prometheus/prometheus.yml` | Prometheus 스크랩 설정 |

---

## 검증 방법

### 1. Actuator 엔드포인트 확인
```bash
# 앱 기동 후
curl http://localhost:8080/actuator/health
# → {"status":"UP"}

curl http://localhost:8080/actuator/prometheus | head -20
# → # HELP jvm_memory_used_bytes ...
# → shortenurl_created_total 0.0
```

### 2. 비즈니스 메트릭 확인
```bash
# URL 생성 요청
curl -X POST http://localhost:8080/shortenUrl \
  -H "Content-Type: application/json" \
  -d '{"originalUrl":"https://naver.com"}'

# 메트릭 확인
curl http://localhost:8080/actuator/prometheus | grep shortenurl
# → shortenurl_created_total 1.0
# → shortenurl_key_generation_seconds_count 1.0
# → shortenurl_key_generation_seconds_sum 0.015
```

### 3. 전체 스택 통합 테스트
```bash
# 전체 관측 스택 기동
docker compose -f docker-compose-observability.yml up -d

# 앱 기동
./mvnw spring-boot:run -Dspring-boot.run.profiles=loki
```

1. 여러 요청 발생 (정상 + 에러)
2. Grafana 접속 (`http://localhost:3000`)
3. **Prometheus**: `rate(shortenurl_created_total[5m])` → 생성률 그래프
4. **Loki**: `{service="shortenurlservice", level="ERROR"}` → 에러 로그
5. **Tempo**: 로그에서 traceId 클릭 → 워터폴 뷰

### 4. 런타임 로그 레벨 변경 테스트
```bash
# DEBUG 활성화
curl -X POST http://localhost:8080/actuator/loggers/kr.co.shortenurlservice.application \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel":"DEBUG"}'

# 요청 발생 → DEBUG 로그가 나오는지 확인

# INFO로 복원
curl -X POST http://localhost:8080/actuator/loggers/kr.co.shortenurlservice.application \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel":"INFO"}'
```

### 5. 세 신호 연결 확인
1. 에러를 발생시킨다 (존재하지 않는 shortenUrlKey로 리다이렉트 요청)
2. Prometheus에서 에러 카운트 증가 확인
3. Loki에서 해당 에러 로그의 traceId 확인
4. Tempo에서 해당 traceId의 워터폴 확인
5. **세 곳의 데이터가 같은 이벤트를 가리키는지** 확인
