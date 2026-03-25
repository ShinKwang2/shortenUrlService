# Plan 3: 로그 시각화 — ELK 연동

> **인프라 단계**: 단일 서버
> **선행 조건**: Plan 1, 2 완료 (requestId, StructuredArguments, duration)
> **핵심 질문**: "로그 파일을 grep하지 않고, 시각적으로 병목을 찾을 수 있는가?"

---

## 이 단계의 목표

Plan 1~2에서 만든 구조화된 로그를 **Elasticsearch에 저장**하고, **Kibana에서 검색/필터/시각화**할 수 있게 만든다.
로그 파일을 직접 열지 않고도 병목을 찾는 환경을 구축한다.

---

## 왜 이 단계가 필요한가

### Plan 1~2 이후의 한계

Plan 2에서 `kv()`로 구조화된 필드를 만들고, duration을 측정했다. 하지만:

1. **검색이 여전히 grep**: `grep "WARN" application.log` → 파일이 1GB면 수 초~수십 초 소요
2. **시계열 분석 불가**: "3시부터 4시 사이에 WARN이 몇 건이었나?"를 알려면 `awk`로 시간 파싱 + 집계 필요
3. **필드 기반 집계 불가**: "어떤 originalUrl에서 에러가 가장 많은가?" → grep으로는 불가능
4. **실시간 모니터링 불가**: 로그 파일은 tail -f로 따라가는 게 최선. 대시보드 없음

### 실제 시나리오

> "오전 10시부터 간헐적으로 500 에러가 발생한다. 패턴이 있는지 (특정 시간대? 특정 URL?) 확인하고 싶다."

**Plan 2만으로**: `grep "ERROR" application.log | grep "10:" | ...` → 시간 필터링은 되지만, "10시~11시에 에러 50건, 11시~12시에 에러 200건"같은 추세는 눈으로 세야 한다.

**ELK가 있으면**: Kibana에서 시간 범위 선택 → 에러 건수 히스토그램이 즉시 나온다. `originalUrl` 필드로 Terms Aggregation → 에러가 집중된 도메인이 바로 보인다.

---

## AS-IS (현재 상태)

### logback.xml (현재 설정)

```xml
<configuration>
    <property name="LOG_FILE" value="application.log" />

    <!-- LOGSTASH 로 전송할 Appender -->
    <appender name="LOGSTASH" class="net.logstash.logback.appender.LogstashTcpSocketAppender">
        <destination>localhost:5044</destination>
        <encoder class="net.logstash.logback.encoder.LogstashEncoder" />
    </appender>

    <!-- 콘솔 출력 -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss} %-5level [%thread] %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <!-- 파일 출력 -->
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>${LOG_FILE}</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>application.%d{yyyy-MM-dd}.log.gz</fileNamePattern>
            <maxHistory>5</maxHistory>
        </rollingPolicy>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss} %-5level [%thread] %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <!-- Logger 설정 -->
    <root level="info">
        <appender-ref ref="CONSOLE" />
        <appender-ref ref="FILE" />
        <appender-ref ref="LOGSTASH" />
    </root>
</configuration>
```

### 현재 상태의 문제점

| 문제 | 상세 | 영향 |
|------|------|------|
| **LOGSTASH가 항상 활성화** | dev 환경에서도 localhost:5044에 연결 시도 | Logstash가 없으면 연결 에러 로그 반복 출력 → 실제 로그를 가림 |
| **파일 이름이 logback.xml** | Spring Profile(`<springProfile>`) 태그를 사용할 수 없음 | 환경별 설정 분리 불가 |
| **reconnection 설정 없음** | Logstash가 재시작되면 연결이 끊어진 채 복구 안 됨 | 운영 중 Logstash 재시작 시 로그 유실 |
| **queueSize 미설정** | Logstash가 일시적으로 느리면 로그가 바로 유실 | 피크 트래픽 시 로그 유실 가능 |
| **service 식별 필드 없음** | 어떤 서비스에서 보낸 로그인지 구분 불가 | 추후 다중 서비스 시 혼란 |

---

## TO-BE (목표 상태)

### 1. logback.xml → logback-spring.xml 전환

파일 이름을 `logback-spring.xml`로 변경한다.

#### 왜 이름을 바꿔야 하는가

- `logback.xml`: Logback이 직접 로딩. Spring 컨텍스트 초기화 **이전**에 로딩됨
- `logback-spring.xml`: Spring Boot가 로딩. Spring 컨텍스트 초기화 **이후**에 로딩됨

차이점:
- `logback.xml`에서는 `<springProfile>` 태그를 **사용할 수 없다** (Spring이 아직 초기화되지 않았으므로)
- `logback-spring.xml`에서는 `<springProfile>` 태그로 **프로파일별 설정**이 가능

### 2. logback-spring.xml (변경 후)

```xml
<configuration>
    <property name="LOG_FILE" value="application.log" />

    <!-- 콘솔 출력 -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss} %-5level [%thread] [%X{requestId}] %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <!-- 파일 출력 -->
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>${LOG_FILE}</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>application.%d{yyyy-MM-dd}.log.gz</fileNamePattern>
            <maxHistory>5</maxHistory>
        </rollingPolicy>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss} %-5level [%thread] [%X{requestId}] %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <!-- LOGSTASH 로 전송할 Appender (프로파일별 활성화) -->
    <appender name="LOGSTASH" class="net.logstash.logback.appender.LogstashTcpSocketAppender">
        <destination>localhost:5044</destination>
        <reconnectionDelay>5 seconds</reconnectionDelay>
        <queueSize>512</queueSize>
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <customFields>{"service":"shortenurlservice"}</customFields>
        </encoder>
    </appender>

    <!-- LoggingFilter 전용 로거 -->
    <logger name="kr.co.shortenurlservice.presentation.LoggingFilter" level="DEBUG" />

    <!-- dev 프로파일: CONSOLE + FILE만 -->
    <springProfile name="dev">
        <root level="INFO">
            <appender-ref ref="CONSOLE" />
            <appender-ref ref="FILE" />
        </root>
    </springProfile>

    <!-- prod 프로파일: CONSOLE + FILE + LOGSTASH -->
    <springProfile name="prod">
        <root level="INFO">
            <appender-ref ref="CONSOLE" />
            <appender-ref ref="FILE" />
            <appender-ref ref="LOGSTASH" />
        </root>
    </springProfile>
</configuration>
```

### 3. docker-compose-elk.yml (신규)

```yaml
version: '3.8'
services:
  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.12.0
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
      - "ES_JAVA_OPTS=-Xms512m -Xmx512m"
    ports:
      - "9200:9200"
    volumes:
      - es-data:/usr/share/elasticsearch/data

  logstash:
    image: docker.elastic.co/logstash/logstash:8.12.0
    ports:
      - "5044:5044"
    volumes:
      - ./logstash/pipeline:/usr/share/logstash/pipeline
    depends_on:
      - elasticsearch

  kibana:
    image: docker.elastic.co/kibana/kibana:8.12.0
    ports:
      - "5601:5601"
    environment:
      - ELASTICSEARCH_HOSTS=http://elasticsearch:9200
    depends_on:
      - elasticsearch

volumes:
  es-data:
```

### 4. logstash/pipeline/logback.conf (신규)

```
input {
  tcp {
    port => 5044
    codec => json_lines
  }
}

output {
  elasticsearch {
    hosts => ["elasticsearch:9200"]
    index => "shortenurlservice-%{+YYYY.MM.dd}"
  }
}
```

---

## 변경 항목별 상세 설명

### 항목 1: logback.xml → logback-spring.xml 전환

#### 무엇을
파일 이름을 변경하고, `<springProfile>` 태그로 dev/prod 설정을 분리한다.

#### 왜 필요한가

현재 `logback.xml`에서는 LOGSTASH appender가 **항상 활성화**되어 있다. 개발할 때 Logstash를 띄우지 않으면:

```
12:00:01 WARN  c.q.l.core.net.SyslogOutputStream - Connection refused: localhost:5044
12:00:06 WARN  c.q.l.core.net.SyslogOutputStream - Connection refused: localhost:5044
12:00:11 WARN  c.q.l.core.net.SyslogOutputStream - Connection refused: localhost:5044
```

5초마다 연결 에러가 출력되면서 **실제 로그를 가린다**. 개발 효율이 떨어진다.

`<springProfile>`로 분리하면:
- `dev`: CONSOLE + FILE만 → 연결 에러 없음
- `prod`: CONSOLE + FILE + LOGSTASH → ELK로 전송

#### 왜 이 방식인가

| 방법 | 장점 | 단점 |
|------|------|------|
| if-else로 환경변수 분기 | 단순 | logback.xml에서 Spring Profile 미지원 |
| 별도 파일 (logback-dev.xml, logback-prod.xml) | 완전 분리 | 공통 설정(appender 정의)이 중복됨 |
| **logback-spring.xml + `<springProfile>`** (선택) | 공통 설정은 한 번 정의, 프로파일별 root만 다르게 | Spring Boot 전용 (순수 logback에서는 미지원) |

### 항목 2: LOGSTASH appender 안정화

#### reconnectionDelay — 왜 필요한가

Logstash가 업데이트 등으로 재시작되면 TCP 연결이 끊긴다. `reconnectionDelay` 없이는:
- 연결 끊김 → 이후 모든 로그 유실
- 앱을 재시작해야 연결 복구

`reconnectionDelay: 5 seconds` 설정 시:
- 연결 끊김 → 5초 후 자동 재연결 시도 → Logstash가 올라오면 자동 복구
- 재연결 동안의 로그는 `queueSize`만큼 버퍼링

#### queueSize — 왜 필요한가

Logstash가 느려지거나 재시작 중일 때:
- `queueSize=0` (기본값): 전송 못하면 **즉시 로그 유실**
- `queueSize=512`: 최대 512개의 로그 이벤트를 메모리에 보관. Logstash 복구 후 전송

512의 근거:
- 로그 이벤트 1개 ≈ 1KB → 512개 ≈ 512KB → 메모리 부담 거의 없음
- 짧은 장애(수 초~수십 초)의 로그 유실 방지에 충분

#### customFields — 왜 필요한가

```json
{"service":"shortenurlservice"}
```

모든 로그에 `"service": "shortenurlservice"` 필드가 추가된다.

현재는 서비스가 1개이므로 불필요해 보이지만:
- Plan 4(다중 서버)에서 **같은 서비스의 여러 인스턴스**를 구분하는 기반
- Plan 5(MSA)에서 **다른 서비스의 로그**와 구분하는 기반
- **지금 추가해도 비용이 없고**, 나중에 추가하면 과거 로그에는 필드가 없어서 혼란

### 항목 3: docker-compose-elk.yml

#### 왜 Docker Compose인가

ELK 스택은 3개의 서비스(Elasticsearch, Logstash, Kibana)로 구성된다.

| 방식 | 장점 | 단점 |
|------|------|------|
| 각각 수동 설치 | 없음 | 설치/설정/버전 관리 복잡 |
| 각각 docker run | Docker 사용 | 3개 컨테이너 네트워크 설정 필요 |
| **docker-compose** (선택) | `docker compose up` 한 명령어 | Docker Desktop 필요 |

Docker Compose를 사용하면:
- `docker compose -f docker-compose-elk.yml up`: 전체 스택 기동
- `docker compose -f docker-compose-elk.yml down`: 전체 스택 중지
- `logstash/pipeline/logback.conf`를 git으로 버전 관리

#### 왜 xpack.security.enabled=false인가

Elasticsearch 8.x부터 보안이 기본 활성화되어 있다. 학습 환경에서는:
- HTTPS 인증서 설정 불필요
- API 키/비밀번호 없이 접근 가능
- **프로덕션에서는 반드시 보안을 활성화해야 한다**

#### Logstash 파이프라인 — codec: json_lines인 이유

LogstashTcpSocketAppender는 로그를 **JSON 한 줄** 형태로 TCP로 전송한다. Logstash 쪽에서 이를 파싱하려면 `json_lines` 코덱이 필요하다. 다른 코덱(예: `plain`)을 사용하면 JSON이 파싱되지 않고 통째로 `message` 필드에 들어간다.

---

## 이점

### 시나리오: "오전 10시부터 간헐적 500 에러, 패턴 확인"

**AS-IS (Plan 2까지)**:
```bash
grep "ERROR" application.log | grep "10:" | wc -l  # 10시대 에러 건수
grep "ERROR" application.log | grep "11:" | wc -l  # 11시대 에러 건수
# ... 시간대별로 반복
```
→ 수작업, 느림, 시각화 불가

**TO-BE (Plan 3)**:
1. Kibana 접속 → Discover → 시간 범위: 오전 10시~현재
2. 필터: `level: "ERROR"`
3. 히스토그램: 시간대별 에러 건수가 막대 그래프로 나타남
4. 하위 차트: `originalUrl` Terms Aggregation → 에러가 특정 도메인에 집중되었는지 확인
5. 소요 시간: **1분 이내**

### 시나리오: "Slow request가 언제부터 늘어났는가"

**AS-IS**: `grep "WARN.*느린 요청" application.log` → 시간별 추세 파악 불가
**TO-BE**: Kibana에서 `level: "WARN" AND message: "느린 요청 감지"` → 시계열 히스토그램으로 추세 즉시 확인

---

## 수정 대상 파일

| 파일 | 변경 내용 |
|------|----------|
| `src/main/resources/logback.xml` | `logback-spring.xml`로 이름 변경 + springProfile 분리 + LOGSTASH 안정화 |
| `src/main/resources/application.yaml` | 변경 없음 (이미 `profiles.active: dev` 설정됨) |
| 신규: `docker-compose-elk.yml` | ELK 스택 정의 |
| 신규: `logstash/pipeline/logback.conf` | Logstash 파이프라인 |

---

## 검증 방법

### 1. dev 프로파일에서 LOGSTASH 에러 없음 확인
```bash
# dev 프로파일로 기동 (기본값)
./mvnw spring-boot:run
```
콘솔에 `Connection refused: localhost:5044` 에러가 나타나지 **않는지** 확인한다.

### 2. ELK 스택 기동 + 로그 수집 확인
```bash
# ELK 기동
docker compose -f docker-compose-elk.yml up -d

# prod 프로파일로 앱 기동
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod

# 요청 발생
curl -X POST http://localhost:8080/shortenUrl \
  -H "Content-Type: application/json" \
  -d '{"originalUrl":"https://naver.com"}'
```

### 3. Kibana에서 확인
1. `http://localhost:5601` 접속
2. Management → Index Patterns → `shortenurlservice-*` 생성
3. Discover → 로그가 나타나는지 확인
4. 좌측 필드 목록에 `requestId`, `originalUrl`, `shortenUrlKey`, `service` 등이 **독립 필드**로 나타나는지 확인
5. `requestId`로 필터링 → 해당 요청의 전체 흐름이 보이는지 확인

### 4. reconnection 테스트
1. Logstash 컨테이너를 중지: `docker compose -f docker-compose-elk.yml stop logstash`
2. 앱에서 요청을 몇 건 보낸다 (이 로그는 큐에 버퍼링됨)
3. Logstash 컨테이너를 재시작: `docker compose -f docker-compose-elk.yml start logstash`
4. Kibana에서 버퍼링되었던 로그가 나타나는지 확인
