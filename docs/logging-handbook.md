# 🪵 LOGGING HANDBOOK

### 주니어 개발자를 위한 로그 완벽 가이드

> **From Zero to Production-Grade Observability**
> 30년 실무 경험을 녹여낸 핸드북 — Spring Boot · Logback · ELK Stack · Prometheus · Grafana

---

# 섹션 2. 로그의 기본

---

## 4. 로그는 왜 필요할까?

### 📝 30년 경험담

> 1995년, 제가 처음 운영 환경에 배포한 시스템에서 장애가 발생했습니다.
> 아무런 로그도 없었죠. 무엇이 잘못된 것인지 파악하는 데 꼬박 3일이 걸렸습니다.
> 그날 이후로 저는 모든 시스템에 로그를 남기기 시작했습니다.

### 4.1 로그의 태생 배경

로그(Log)는 원래 **'항해 일지'**에서 유래한 단어입니다. 배가 항해하면서 매일의 상황을 기록한 것처럼, 컴퓨터 시스템도 자신에게 일어나는 모든 사건을 기록합니다.

**로그 기술의 진화 타임라인:**

| 시대 | 방식 | 특징 |
|------|------|------|
| 1960~70년대 | 프린터 출력 | 물리적 종이에 기록, 실시간 확인 불가 |
| 1980년대 | syslog 탄생 (UNIX) | 최초의 체계적 로깅 시스템, 중앙 집중식 |
| 1990년대 | 파일 기반 로깅 | Log4j 등 로깅 프레임워크 등장 |
| 2000년대 | 구조화된 로깅 | JSON 로그, 로그 레벨 체계화 |
| 2010년대 | 중앙 집중 로그 수집 | ELK Stack, Fluentd 등장 |
| 2020년대~ | Observability (관측 가능성) | 로그 + 메트릭 + 트레이싱 통합, OpenTelemetry |

초창기 컴퓨팅에서는 프린터로 출력하는 것이 유일한 로깅 방법이었습니다. 그러다 UNIX 시스템이 등장하면서 **syslog**가 태어났고, 이것이 현대 로깅의 시작이 되었습니다. 이후 Java 진영에서 **Log4j**(1999)가 등장하면서 "로그 레벨"이라는 개념이 대중화되었고, 이것이 오늘날 우리가 사용하는 SLF4J, Logback의 뿌리가 됩니다.

### 4.2 로그가 필요한 5가지 이유

**첫째, 장애 진단(Troubleshooting).**
운영 환경에서 버그가 발생하면 디버거를 붙일 수 없습니다. 로그만이 유일한 단서입니다. 특히 간헐적으로 발생하는 버그는 로그 없이는 원인을 찾는 것이 불가능에 가깝습니다.

**둘째, 보안 감사(Security Audit).**
누가 언제 어떤 데이터에 접근했는지 추적할 수 있어야 합니다. 금융, 의료 등 규제 산업에서는 법적 의무이기도 합니다.

**셋째, 성능 분석(Performance Analysis).**
응답 시간, 처리량, 병목 현상 등을 로그로 분석하면 시스템 최적화 포인트를 찾을 수 있습니다.

**넷째, 비즈니스 인사이트(Business Intelligence).**
사용자 행동 패턴, 기능 사용 빈도 등을 분석하여 제품 개선에 활용할 수 있습니다.

**다섯째, 법적 증거(Legal Evidence).**
분쟁 발생 시 로그는 법적 증거로 활용될 수 있습니다. 특히 API 호출 기록, 결제 처리 로그는 거래 분쟁의 핵심 증거가 됩니다.

### 4.3 로그가 없으면 벌어지는 일

로그가 없는 서버는 **사이드 미러가 없는 차**와 같습니다.

| 상황 | 로그가 있을 때 | 로그가 없을 때 |
|------|---------------|---------------|
| 장애 발생 | 즉시 원인 파악 가능 | 코드를 한 줄씩 디버깅 |
| 성능 저하 | 병목 구간 즉시 확인 | 재현이 안 되면 미궁 |
| 보안 사고 | 침입 경로 추적 가능 | 흔적조차 파악 불가 |
| CS 문의 | 사용자 행동 재현 가능 | "저희도 확인이 어렵습니다" |

---

## 5. 로그는 대체 어떤 걸 기록해야 할까?

### 📝 30년 경험담

> 초기에는 모든 것을 로그로 남겼습니다. 결과는? 디스크가 3일 만에 가득 찼습니다.
> 그 후로 '무엇을 기록할 것인가'만큼이나 '무엇을 기록하지 않을 것인가'도 중요하다는 것을 배웠습니다.

### 5.1 반드시 기록해야 하는 것

```
✅ 애플리케이션 시작/종료 이벤트
✅ 사용자 인증/인가 시도 (성공과 실패 모두)
✅ 핵심 비즈니스 로직의 시작과 종료
✅ 외부 API 호출 (요청/응답/소요시간)
✅ 데이터베이스 쿼리 실패
✅ 예외(Exception) 발생 — 반드시 스택트레이스 포함
✅ 결제, 주문 등 중요 트랜잭션
✅ 설정 변경 이벤트
```

### 5.2 절대 기록하면 안 되는 것

```
🚫 비밀번호 (평문이든 해시든)
🚫 주민등록번호, 카드번호 등 개인정보
🚫 API Key, Secret Token
🚫 세션 토큰 전체 (마지막 4자리만 남기기)
🚫 의료 정보, 금융 상세 정보
```

> ⚠️ **WARNING:** 개인정보를 로그에 남기면 개인정보보호법 위반으로 과태료를 물 수 있습니다. 실제로 2019년 국내 한 기업이 로그 파일에 고객 카드번호를 평문으로 저장하여 과징금을 부과받은 사례가 있습니다.

### 5.3 로그 기록의 황금률 (Golden Rule)

```
"6개월 뒤 새벽 3시에 장애가 발생했을 때,
이 로그만 보고 무슨 일이 일어났는지 파악할 수 있는가?"
```

이 질문에 **"Yes"**라고 답할 수 있다면, 그 로그는 잘 작성된 것입니다.

### 5.4 좋은 로그 vs 나쁜 로그

```java
// ❌ 나쁜 로그 — 아무 정보도 없음
log.error("에러 발생");
log.info("처리 완료");

// ✅ 좋은 로그 — 누가, 무엇을, 왜
log.error("웨이팅 등록 실패 - userId: {}, restaurantId: {}, reason: {}",
          userId, restaurantId, e.getMessage(), e);
log.info("웨이팅 등록 성공 - userId: {}, restaurantId: {}, waitingNumber: {}, estimatedTime: {}분",
         userId, restaurantId, waitingNumber, estimatedMinutes);
```

---

## 6. [실습] 맛집 웨이팅 API를 통한 로그 실습

### 실습 목표

실제 서비스와 유사한 **맛집 웨이팅 시스템**을 만들면서 로그의 기본기를 익힙니다.

### 6.1 프로젝트 생성

**Spring Initializr** (https://start.spring.io) 에서 아래 설정으로 프로젝트를 생성합니다.

```
Project: Gradle - Groovy
Language: Java
Spring Boot: 3.2.x 이상
Group: com.example
Artifact: waiting-log
Dependencies:
  - Spring Web
  - Spring Boot DevTools
  - Lombok
```

### 6.2 도메인 모델 생성

```java
// src/main/java/com/example/waitinglog/domain/Waiting.java
package com.example.waitinglog.domain;

import java.time.LocalDateTime;

public class Waiting {
    private Long id;
    private Long userId;
    private Long restaurantId;
    private int waitingNumber;
    private WaitingStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public enum WaitingStatus {
        WAITING,    // 대기 중
        CALLED,     // 호출됨
        SEATED,     // 착석
        CANCELLED,  // 취소
        NO_SHOW     // 노쇼
    }

    // 생성자
    public Waiting(Long id, Long userId, Long restaurantId,
                   int waitingNumber, WaitingStatus status) {
        this.id = id;
        this.userId = userId;
        this.restaurantId = restaurantId;
        this.waitingNumber = waitingNumber;
        this.status = status;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // Getter / Setter 생략 (Lombok @Data 사용 권장)
    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getRestaurantId() { return restaurantId; }
    public int getWaitingNumber() { return waitingNumber; }
    public WaitingStatus getStatus() { return status; }
    public void setStatus(WaitingStatus status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
```

### 6.3 서비스 계층 — 로그를 남기는 핵심 위치

```java
// src/main/java/com/example/waitinglog/service/WaitingService.java
package com.example.waitinglog.service;

import com.example.waitinglog.domain.Waiting;
import com.example.waitinglog.domain.Waiting.WaitingStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class WaitingService {

    // ✅ SLF4J Logger 선언 — 클래스마다 하나씩 만드는 것이 관례
    private static final Logger log = LoggerFactory.getLogger(WaitingService.class);

    private final Map<Long, Waiting> waitingStore = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(1);
    private final AtomicLong waitingNumberSeq = new AtomicLong(1);

    /**
     * 웨이팅 등록
     */
    public Waiting register(Long userId, Long restaurantId) {
        log.info("웨이팅 등록 요청 - userId: {}, restaurantId: {}", userId, restaurantId);

        // 비즈니스 검증: 이미 대기 중인지 확인
        boolean alreadyWaiting = waitingStore.values().stream()
                .anyMatch(w -> w.getUserId().equals(userId)
                        && w.getRestaurantId().equals(restaurantId)
                        && w.getStatus() == WaitingStatus.WAITING);

        if (alreadyWaiting) {
            log.warn("중복 웨이팅 시도 - userId: {}, restaurantId: {} (이미 대기 중)",
                     userId, restaurantId);
            throw new IllegalStateException("이미 해당 식당에 대기 중입니다.");
        }

        Long id = sequence.getAndIncrement();
        int waitingNumber = (int) waitingNumberSeq.getAndIncrement();

        Waiting waiting = new Waiting(id, userId, restaurantId,
                                      waitingNumber, WaitingStatus.WAITING);
        waitingStore.put(id, waiting);

        log.info("웨이팅 등록 완료 - id: {}, userId: {}, restaurantId: {}, " +
                 "waitingNumber: {}, currentQueueSize: {}",
                 id, userId, restaurantId, waitingNumber, waitingStore.size());

        return waiting;
    }

    /**
     * 웨이팅 취소
     */
    public Waiting cancel(Long waitingId, Long userId) {
        log.info("웨이팅 취소 요청 - waitingId: {}, userId: {}", waitingId, userId);

        Waiting waiting = waitingStore.get(waitingId);

        if (waiting == null) {
            log.error("웨이팅 취소 실패 - waitingId: {} (존재하지 않음)", waitingId);
            throw new IllegalArgumentException("존재하지 않는 웨이팅입니다.");
        }

        if (!waiting.getUserId().equals(userId)) {
            log.error("웨이팅 취소 실패 - waitingId: {}, requestUserId: {}, " +
                      "ownerUserId: {} (권한 없음)",
                      waitingId, userId, waiting.getUserId());
            throw new SecurityException("본인의 웨이팅만 취소할 수 있습니다.");
        }

        if (waiting.getStatus() != WaitingStatus.WAITING) {
            log.warn("웨이팅 취소 불가 - waitingId: {}, currentStatus: {} " +
                     "(WAITING 상태만 취소 가능)",
                     waitingId, waiting.getStatus());
            throw new IllegalStateException("대기 중인 상태에서만 취소할 수 있습니다.");
        }

        waiting.setStatus(WaitingStatus.CANCELLED);

        log.info("웨이팅 취소 완료 - waitingId: {}, userId: {}, restaurantId: {}",
                 waitingId, userId, waiting.getRestaurantId());

        return waiting;
    }

    /**
     * 웨이팅 조회
     */
    public Waiting getWaiting(Long waitingId) {
        log.debug("웨이팅 조회 - waitingId: {}", waitingId);

        Waiting waiting = waitingStore.get(waitingId);
        if (waiting == null) {
            log.debug("웨이팅 조회 결과 없음 - waitingId: {}", waitingId);
        }
        return waiting;
    }
}
```

### 6.4 컨트롤러 계층

```java
// src/main/java/com/example/waitinglog/controller/WaitingController.java
package com.example.waitinglog.controller;

import com.example.waitinglog.domain.Waiting;
import com.example.waitinglog.service.WaitingService;
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
    public ResponseEntity<?> register(@RequestBody Map<String, Long> request) {
        Long userId = request.get("userId");
        Long restaurantId = request.get("restaurantId");

        log.info("[API] POST /api/waiting - userId: {}, restaurantId: {}",
                 userId, restaurantId);

        try {
            Waiting waiting = waitingService.register(userId, restaurantId);
            return ResponseEntity.ok(Map.of(
                "id", waiting.getId(),
                "waitingNumber", waiting.getWaitingNumber(),
                "status", waiting.getStatus().name()
            ));
        } catch (IllegalStateException e) {
            log.warn("[API] 웨이팅 등록 거부 - {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{waitingId}")
    public ResponseEntity<?> cancel(@PathVariable Long waitingId,
                                    @RequestParam Long userId) {
        log.info("[API] DELETE /api/waiting/{} - userId: {}", waitingId, userId);

        try {
            Waiting waiting = waitingService.cancel(waitingId, userId);
            return ResponseEntity.ok(Map.of(
                "id", waiting.getId(),
                "status", waiting.getStatus().name()
            ));
        } catch (Exception e) {
            log.error("[API] 웨이팅 취소 오류 - waitingId: {}, error: {}",
                      waitingId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{waitingId}")
    public ResponseEntity<?> getWaiting(@PathVariable Long waitingId) {
        log.debug("[API] GET /api/waiting/{}", waitingId);

        Waiting waiting = waitingService.getWaiting(waitingId);
        if (waiting == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
            "id", waiting.getId(),
            "waitingNumber", waiting.getWaitingNumber(),
            "status", waiting.getStatus().name()
        ));
    }
}
```

### 6.5 실습 실행 및 확인

```bash
# 1. 프로젝트 실행
./gradlew bootRun

# 2. 웨이팅 등록
curl -X POST http://localhost:8080/api/waiting \
  -H "Content-Type: application/json" \
  -d '{"userId": 1, "restaurantId": 100}'

# 3. 중복 등록 시도 (WARN 로그 발생 확인)
curl -X POST http://localhost:8080/api/waiting \
  -H "Content-Type: application/json" \
  -d '{"userId": 1, "restaurantId": 100}'

# 4. 웨이팅 취소
curl -X DELETE "http://localhost:8080/api/waiting/1?userId=1"

# 5. 존재하지 않는 웨이팅 취소 (ERROR 로그 발생 확인)
curl -X DELETE "http://localhost:8080/api/waiting/999?userId=1"
```

### 6.6 콘솔에서 확인할 로그 예시

```
2024-01-15 14:30:01.123  INFO  --- [nio-8080-exec-1] c.e.w.controller.WaitingController  : [API] POST /api/waiting - userId: 1, restaurantId: 100
2024-01-15 14:30:01.125  INFO  --- [nio-8080-exec-1] c.e.w.service.WaitingService         : 웨이팅 등록 요청 - userId: 1, restaurantId: 100
2024-01-15 14:30:01.130  INFO  --- [nio-8080-exec-1] c.e.w.service.WaitingService         : 웨이팅 등록 완료 - id: 1, userId: 1, restaurantId: 100, waitingNumber: 1, currentQueueSize: 1
2024-01-15 14:30:05.200  WARN  --- [nio-8080-exec-2] c.e.w.service.WaitingService         : 중복 웨이팅 시도 - userId: 1, restaurantId: 100 (이미 대기 중)
2024-01-15 14:30:10.300  ERROR --- [nio-8080-exec-3] c.e.w.service.WaitingService         : 웨이팅 취소 실패 - waitingId: 999 (존재하지 않음)
```

> ✨ **TIP:** 로그를 보면서 각 로그 레벨(INFO, WARN, ERROR)이 어떤 상황에서 쓰이는지 눈에 익혀두세요. 다음 섹션에서 자세히 다룹니다.

---

# 섹션 3. 로그의 Level과 Filter

---

## 7. 로그의 레벨 — TRACE, DEBUG, INFO, WARN, ERROR, FATAL

### 7.1 로그 레벨이 왜 필요하게 됐을까?

초기 로깅 시대에는 **모든 로그를 동일하게** 취급했습니다. `System.out.println()`으로 전부 출력했죠. 하지만 이 방식에는 심각한 문제가 있었습니다.

```
문제 1: 운영 환경에서 디버깅 로그가 쏟아져 나와 디스크가 터짐
문제 2: 정말 중요한 에러 로그가 수만 줄의 디버그 로그에 묻혀 보이지 않음
문제 3: 코드에서 println을 지우면 나중에 다시 디버깅할 때 또 추가해야 함
```

이 문제를 해결하기 위해 **"로그에 중요도(Level)를 부여하자"**라는 아이디어가 탄생했습니다. 1999년 **Log4j**가 이 개념을 체계화했고, 이후 모든 로깅 프레임워크의 표준이 되었습니다.

### 7.2 6단계 로그 레벨 상세 해설

```
TRACE ← 가장 상세 (개발 환경에서도 거의 사용 안 함)
  │
DEBUG ← 개발 중 디버깅 (운영 환경에서는 OFF)
  │
 INFO ← 정상 동작 기록 (운영 환경 기본 레벨) ★
  │
 WARN ← 잠재적 문제 (당장은 아니지만 주의 필요)
  │
ERROR ← 실제 오류 발생 (즉시 확인 필요) ★★
  │
FATAL ← 시스템 중단급 심각한 오류 ★★★
```

| 레벨 | 용도 | 실제 사용 예시 | 운영 환경 |
|------|------|--------------|----------|
| **TRACE** | 코드 흐름 추적 | 메서드 진입/종료, 변수 값 변화 | ❌ OFF |
| **DEBUG** | 개발 중 디버깅 | SQL 쿼리, 요청/응답 상세 | ❌ OFF |
| **INFO** | 정상 동작 기록 | 사용자 로그인, 주문 생성, 서버 시작 | ✅ ON |
| **WARN** | 잠재적 문제 | 재시도 발생, 데이터 누락(비필수), 느린 응답 | ✅ ON |
| **ERROR** | 실제 오류 | DB 연결 실패, API 호출 실패, 결제 오류 | ✅ ON |
| **FATAL** | 시스템 중단 | OOM, 필수 설정 누락, 데이터 손상 | ✅ ON |

### 7.3 레벨 설정의 핵심 원리: 임계값(Threshold)

로그 레벨을 `INFO`로 설정하면 **INFO 이상**(INFO, WARN, ERROR, FATAL)만 출력됩니다. 이것이 로그 레벨의 핵심 원리인 **임계값(Threshold)** 개념입니다.

```
설정: logging.level.root=INFO

TRACE → ❌ 출력 안 됨
DEBUG → ❌ 출력 안 됨
INFO  → ✅ 출력됨
WARN  → ✅ 출력됨
ERROR → ✅ 출력됨
FATAL → ✅ 출력됨
```

### 7.4 각 레벨을 언제 써야 하는지 — 판단 기준표

```
Q: 이 문제로 사용자가 서비스를 이용하지 못하는가?
├── Yes → ERROR 또는 FATAL
│   ├── 시스템 전체가 멈추는가? → FATAL
│   └── 특정 기능만 영향받는가? → ERROR
└── No
    ├── 나중에 문제가 될 수 있는가?
    │   ├── Yes → WARN
    │   └── No → 정상 동작인가?
    │       ├── Yes → INFO
    │       └── 디버깅용인가? → DEBUG / TRACE
```

---

## 8. [실습] 실습으로 Log Level 이해하기

### 8.1 application.yml 설정

```yaml
# src/main/resources/application.yml
logging:
  level:
    root: INFO                                    # 기본 레벨
    com.example.waitinglog: DEBUG                  # 우리 패키지는 DEBUG까지 출력
    org.springframework.web: INFO                  # Spring Web은 INFO만
    org.hibernate.SQL: DEBUG                       # Hibernate SQL 쿼리 보기 (JPA 사용 시)
```

### 8.2 로그 레벨 테스트 코드 작성

```java
// src/main/java/com/example/waitinglog/controller/LogLevelTestController.java
package com.example.waitinglog.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LogLevelTestController {

    private static final Logger log = LoggerFactory.getLogger(LogLevelTestController.class);

    @GetMapping("/api/log-test")
    public String testLogLevels() {
        log.trace("TRACE - 가장 상세한 레벨. 메서드 진입 추적 등에 사용");
        log.debug("DEBUG - 개발 중 디버깅용. 변수 값, SQL 쿼리 등");
        log.info("INFO  - 정상 동작 기록. 서비스 흐름 파악에 사용");
        log.warn("WARN  - 잠재적 문제 경고. 당장은 아니지만 주의 필요");
        log.error("ERROR - 실제 오류 발생. 즉시 확인 필요");

        return "콘솔에서 로그를 확인하세요!";
    }
}
```

### 8.3 실행 및 결과 확인

```bash
# 서버 실행 후 호출
curl http://localhost:8080/api/log-test
```

**application.yml에서 `com.example.waitinglog: DEBUG`로 설정했으므로:**

```
# 출력 결과 (TRACE는 출력되지 않음!)
DEBUG - 개발 중 디버깅용. 변수 값, SQL 쿼리 등
INFO  - 정상 동작 기록. 서비스 흐름 파악에 사용
WARN  - 잠재적 문제 경고. 당장은 아니지만 주의 필요
ERROR - 실제 오류 발생. 즉시 확인 필요
```

### 8.4 실습 과제: 레벨 바꿔보기

아래처럼 레벨을 바꿔가며 어떤 로그가 출력되는지 확인해보세요.

```yaml
# 실험 1: TRACE로 변경 → 모든 로그 출력
logging.level.com.example.waitinglog: TRACE

# 실험 2: WARN으로 변경 → WARN, ERROR만 출력
logging.level.com.example.waitinglog: WARN

# 실험 3: ERROR로 변경 → ERROR만 출력
logging.level.com.example.waitinglog: ERROR
```

---

## 9. 로그는 어떻게 구성돼있을까? — 8가지 구성요소

### 9.1 로그 한 줄 해부하기

실제 운영 환경의 로그 한 줄을 분해해봅시다.

```
2024-01-15 14:30:01.123 [http-nio-8080-exec-1] INFO  c.e.w.service.WaitingService - 웨이팅 등록 완료 - id: 42, userId: 1, restaurantId: 100
│                       │                      │     │                              │
①타임스탬프              ②스레드명               ③레벨  ④로거명(클래스)                 ⑤메시지
```

### 9.2 8가지 구성요소 상세

| 번호 | 구성요소 | 설명 | 예시 |
|------|---------|------|------|
| ① | **타임스탬프(Timestamp)** | 로그 발생 시각 (밀리초 단위 권장) | `2024-01-15 14:30:01.123` |
| ② | **스레드명(Thread)** | 로그를 발생시킨 스레드 | `http-nio-8080-exec-1` |
| ③ | **로그 레벨(Level)** | 로그의 중요도 | `INFO`, `ERROR` |
| ④ | **로거명(Logger)** | 로그를 발생시킨 클래스 | `c.e.w.service.WaitingService` |
| ⑤ | **메시지(Message)** | 실제 로그 내용 | `웨이팅 등록 완료 - id: 42` |
| ⑥ | **MDC(Mapped Diagnostic Context)** | 요청별 고유 ID 등 컨텍스트 | `requestId: abc-123` |
| ⑦ | **예외 정보(Exception)** | 스택트레이스 | `java.lang.NullPointerException...` |
| ⑧ | **호출 위치(Caller)** | 메서드명, 라인 번호 | `WaitingService.java:45` |

### 9.3 왜 이 8가지가 필요한가?

**타임스탬프:** 장애 발생 시간을 정확히 파악하고, 여러 서버 간 로그를 시간순으로 정렬하기 위함입니다.

**스레드명:** 동시에 여러 요청이 처리될 때 어느 요청의 로그인지 구분하기 위함입니다. Spring Boot는 톰캣 스레드 풀에서 스레드를 할당하므로, 스레드명으로 같은 요청의 로그를 묶어볼 수 있습니다.

**MDC:** 같은 요청에 속하는 모든 로그를 하나의 ID로 묶어줍니다. 이것이 11장에서 다룰 핵심 기술입니다.

---

## 10. [실습] 로그 읽기 실습으로 CS 대응하기

### 시나리오

> 고객센터에 전화가 왔습니다.
> "저 오후 2시 30분쯤에 웨이팅 등록했는데 갑자기 취소됐어요! 저는 취소한 적 없는데요!"
> 고객의 userId는 `1001`입니다.

### 10.1 아래 로그에서 원인을 찾아보세요

```log
2024-01-15 14:28:15.100 [exec-1] INFO  c.e.w.s.WaitingService - 웨이팅 등록 요청 - userId: 1001, restaurantId: 500
2024-01-15 14:28:15.150 [exec-1] INFO  c.e.w.s.WaitingService - 웨이팅 등록 완료 - id: 42, userId: 1001, restaurantId: 500, waitingNumber: 7
2024-01-15 14:29:00.200 [exec-3] INFO  c.e.w.s.WaitingService - 웨이팅 조회 - waitingId: 42
2024-01-15 14:31:22.300 [exec-5] INFO  c.e.w.s.WaitingService - 웨이팅 취소 요청 - waitingId: 42, userId: 2050
2024-01-15 14:31:22.305 [exec-5] ERROR c.e.w.s.WaitingService - 웨이팅 취소 실패 - waitingId: 42, requestUserId: 2050, ownerUserId: 1001 (권한 없음)
2024-01-15 14:32:10.400 [exec-7] INFO  c.e.w.s.WaitingService - 웨이팅 취소 요청 - waitingId: 42, userId: 1001
2024-01-15 14:32:10.410 [exec-7] INFO  c.e.w.s.WaitingService - 웨이팅 취소 완료 - waitingId: 42, userId: 1001, restaurantId: 500
```

### 10.2 분석 과정

```
1단계: userId: 1001로 필터링
2단계: 시간순으로 정렬 (이미 정렬되어 있음)
3단계: 흐름 파악

14:28:15 → 웨이팅 등록 (waitingNumber: 7) ✅ 정상
14:31:22 → userId: 2050이 취소 시도 → 권한 없음으로 거부 ✅ 보안 작동
14:32:10 → userId: 1001이 직접 취소 → 완료

💡 결론: 고객(1001) 본인이 14시 32분에 직접 취소했습니다.
    → 고객이 실수로 취소 버튼을 누른 것으로 보입니다.
    → 중간에 다른 사용자(2050)가 취소를 시도했지만 권한 검증에서 차단되었습니다.
```

> ✨ **TIP:** 이것이 로그의 힘입니다. 로그가 없었다면 "확인이 어렵습니다"라고 답할 수밖에 없었을 겁니다.

---

## 11. [실습] MdcLoggingFilter로 UUID 부여하기

### 11.1 MDC란 무엇이고 왜 필요한가?

**MDC(Mapped Diagnostic Context)**는 스레드 로컬(ThreadLocal)에 키-값 쌍을 저장하여, 해당 스레드에서 발생하는 **모든 로그에 자동으로 특정 정보를 추가**해주는 기술입니다.

**MDC가 없던 시절의 문제:**

```log
14:30:01 [exec-1] INFO  - 웨이팅 등록 요청 - userId: 1
14:30:01 [exec-2] INFO  - 웨이팅 등록 요청 - userId: 2
14:30:01 [exec-1] INFO  - DB 조회 시작
14:30:01 [exec-2] INFO  - DB 조회 시작
14:30:02 [exec-1] INFO  - 웨이팅 등록 완료
14:30:02 [exec-2] ERROR - DB 연결 실패
```

스레드명으로 구분할 수 있지만, 스레드는 재사용되기 때문에 **같은 스레드명이 다른 요청에 사용**될 수 있습니다. MDC로 요청마다 고유한 UUID를 부여하면 이 문제가 해결됩니다.

### 11.2 MdcLoggingFilter 구현

```java
// src/main/java/com/example/waitinglog/filter/MdcLoggingFilter.java
package com.example.waitinglog.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)  // 가장 먼저 실행되어야 함
public class MdcLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(MdcLoggingFilter.class);
    private static final String REQUEST_ID = "requestId";
    private static final String CLIENT_IP = "clientIp";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        long startTime = System.currentTimeMillis();

        try {
            // ① 요청별 고유 UUID 생성 및 MDC에 저장
            String requestId = UUID.randomUUID().toString().substring(0, 8);
            MDC.put(REQUEST_ID, requestId);
            MDC.put(CLIENT_IP, getClientIp(request));

            // ② 응답 헤더에도 requestId 추가 (프론트엔드 디버깅용)
            response.setHeader("X-Request-Id", requestId);

            log.info("[REQUEST START] {} {} (requestId: {})",
                     request.getMethod(), request.getRequestURI(), requestId);

            // ③ 다음 필터/컨트롤러로 전달
            filterChain.doFilter(request, response);

        } finally {
            // ④ 요청 처리 시간 로깅
            long duration = System.currentTimeMillis() - startTime;
            log.info("[REQUEST END] {} {} - status: {}, duration: {}ms",
                     request.getMethod(), request.getRequestURI(),
                     response.getStatus(), duration);

            // ⑤ MDC 정리 (메모리 누수 방지 — 반드시 해야 함!)
            MDC.clear();
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
```

### 11.3 Logback 패턴에 MDC 필드 추가

```yaml
# src/main/resources/application.yml
logging:
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [%X{requestId}] [%X{clientIp}] %-5level %logger{36} - %msg%n"
  level:
    root: INFO
    com.example.waitinglog: DEBUG
```

`%X{requestId}` — MDC에서 `requestId` 값을 꺼내 로그에 포함시킵니다.

### 11.4 MDC 적용 후 로그 비교

**적용 전:**
```
14:30:01 [exec-1] INFO  c.e.w.s.WaitingService - 웨이팅 등록 요청 - userId: 1
14:30:01 [exec-2] INFO  c.e.w.s.WaitingService - 웨이팅 등록 요청 - userId: 2
14:30:01 [exec-1] INFO  c.e.w.s.WaitingService - DB 조회 시작
14:30:02 [exec-2] ERROR c.e.w.s.WaitingService - DB 연결 실패
```

**적용 후:**
```
14:30:01 [exec-1] [a1b2c3d4] [192.168.1.10] INFO  c.e.w.s.WaitingService - 웨이팅 등록 요청 - userId: 1
14:30:01 [exec-2] [e5f6g7h8] [192.168.1.20] INFO  c.e.w.s.WaitingService - 웨이팅 등록 요청 - userId: 2
14:30:01 [exec-1] [a1b2c3d4] [192.168.1.10] INFO  c.e.w.s.WaitingService - DB 조회 시작
14:30:02 [exec-2] [e5f6g7h8] [192.168.1.20] ERROR c.e.w.s.WaitingService - DB 연결 실패
```

이제 `requestId: a1b2c3d4`로 검색하면 해당 요청의 모든 로그를 한눈에 볼 수 있습니다!

---

# 섹션 4. 로깅 프레임워크 Logback

---

## 12. 로그를 파일로 보관하는 방법

### 12.1 왜 파일로 보관해야 하는가?

콘솔 출력만으로는 서버가 재시작되면 모든 로그가 사라집니다. 또한 수천 줄의 로그가 실시간으로 쏟아지면 콘솔에서는 원하는 로그를 찾을 수 없습니다.

**로그 보관 방식의 진화:**

| 시대 | 방식 | 장점 | 단점 |
|------|------|------|------|
| 1세대 | `> app.log` (리다이렉션) | 간단 | 파일이 무한 증가 |
| 2세대 | 로그 로테이션 (logrotate) | 파일 크기 관리 | 설정 복잡 |
| 3세대 | 로깅 프레임워크 내장 관리 | 앱 레벨에서 관리 | ← **현재 표준** |
| 4세대 | 중앙 로그 수집 (ELK 등) | 검색/분석 가능 | 인프라 비용 |

### 12.2 Java 로깅 프레임워크의 역사

```
1996 — System.out.println() 시대
  ↓
1999 — Log4j 탄생 (Apache) ← 로그 레벨 개념 탄생
  ↓
2002 — JUL (java.util.logging) ← Java 표준 라이브러리에 포함
  ↓
2004 — SLF4J 탄생 ← 로깅 프레임워크 추상화 (Facade 패턴)
  ↓
2006 — Logback 탄생 (Log4j 창시자가 다시 만듦) ← Spring Boot 기본 채택
  ↓
2014 — Log4j2 (Apache) ← 비동기 로깅 성능 우수
  ↓
현재 — SLF4J + Logback이 Spring Boot의 표준
```

**왜 SLF4J + Logback이 표준이 되었는가?**

SLF4J(Simple Logging Facade for Java)는 **Facade 패턴**을 적용한 로깅 API입니다. 실제 로깅 구현체(Logback, Log4j2 등)를 언제든 교체할 수 있게 해줍니다. Logback은 Log4j를 만든 Ceki Gülcü가 Log4j의 단점을 보완하여 새로 만든 프레임워크로, Spring Boot가 기본 로깅 프레임워크로 채택했습니다.

---

## 13. Logback의 Appender란 무엇일까?

### 13.1 Logback의 3대 핵심 구성요소

```
┌─────────────────────────────────────────────────┐
│                   Logback                        │
│                                                  │
│  ┌──────────┐   ┌──────────┐   ┌──────────────┐ │
│  │  Logger   │──→│ Appender │──→│   Encoder/   │ │
│  │ (어디서)  │   │ (어디로) │   │   Layout     │ │
│  │          │   │          │   │  (어떤 형식)  │ │
│  └──────────┘   └──────────┘   └──────────────┘ │
└─────────────────────────────────────────────────┘
```

| 구성요소 | 역할 | 비유 |
|---------|------|------|
| **Logger** | 로그 메시지를 수집하는 주체 | 기자(취재) |
| **Appender** | 로그를 출력할 목적지 | 출판사(어디에 발행?) |
| **Encoder/Layout** | 로그의 출력 형식 | 편집부(어떤 형태로?) |

### 13.2 주요 Appender 종류

| Appender | 역할 | 사용 시기 |
|----------|------|----------|
| **ConsoleAppender** | 콘솔(표준출력)에 출력 | 개발 환경 |
| **FileAppender** | 단일 파일에 기록 | 단순 로깅 |
| **RollingFileAppender** | 조건에 따라 파일 분할 | **운영 환경 필수** |
| **AsyncAppender** | 비동기로 로그 기록 | 고성능 시스템 |
| **LogstashTcpSocketAppender** | Logstash로 전송 | ELK 연동 시 |

### 13.3 RollingFileAppender가 핵심인 이유

운영 환경에서는 반드시 **RollingFileAppender**를 사용해야 합니다. 이유는 단순합니다. **단일 파일에 계속 쓰면 파일이 수 GB가 되어 열 수조차 없게 됩니다.**

RollingFileAppender는 두 가지 정책으로 파일을 관리합니다.

```
1. TimeBasedRollingPolicy — 시간 기준 (매일, 매시간 등)
   예: app-2024-01-15.log, app-2024-01-16.log

2. SizeAndTimeBasedRollingPolicy — 시간 + 크기 기준
   예: app-2024-01-15.0.log (100MB), app-2024-01-15.1.log (100MB)
```

---

## 14. [실습] Logback 설정하고 로그 파일로 만들기

### 14.1 logback-spring.xml 생성

```xml
<!-- src/main/resources/logback-spring.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<configuration>

    <!-- ═══════════════════════════════════════════ -->
    <!-- 공통 변수 정의                               -->
    <!-- ═══════════════════════════════════════════ -->
    <property name="LOG_DIR" value="./logs" />
    <property name="APP_NAME" value="waiting-log" />
    <property name="LOG_PATTERN"
              value="%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [%X{requestId:-NO_ID}] [%X{clientIp:-0.0.0.0}] %-5level %logger{36} - %msg%n" />

    <!-- ═══════════════════════════════════════════ -->
    <!-- 1. 콘솔 Appender (개발 환경용)                -->
    <!-- ═══════════════════════════════════════════ -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>${LOG_PATTERN}</pattern>
            <charset>UTF-8</charset>
        </encoder>
    </appender>

    <!-- ═══════════════════════════════════════════ -->
    <!-- 2. 파일 Appender (전체 로그)                  -->
    <!-- ═══════════════════════════════════════════ -->
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <!-- 현재 기록 중인 파일 -->
        <file>${LOG_DIR}/${APP_NAME}.log</file>

        <!-- 롤링 정책: 날짜 + 크기 기반 -->
        <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
            <!-- 파일명 패턴: 날짜별 + 인덱스 -->
            <fileNamePattern>${LOG_DIR}/${APP_NAME}.%d{yyyy-MM-dd}.%i.log</fileNamePattern>
            <!-- 단일 파일 최대 크기 -->
            <maxFileSize>100MB</maxFileSize>
            <!-- 전체 로그 보관 기간 -->
            <maxHistory>30</maxHistory>
            <!-- 전체 로그 최대 용량 -->
            <totalSizeCap>3GB</totalSizeCap>
        </rollingPolicy>

        <encoder>
            <pattern>${LOG_PATTERN}</pattern>
            <charset>UTF-8</charset>
        </encoder>
    </appender>

    <!-- ═══════════════════════════════════════════ -->
    <!-- 3. 에러 전용 Appender                        -->
    <!-- ═══════════════════════════════════════════ -->
    <appender name="ERROR_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>${LOG_DIR}/${APP_NAME}-error.log</file>

        <!-- ERROR 레벨 이상만 기록 -->
        <filter class="ch.qos.logback.classic.filter.ThresholdFilter">
            <level>ERROR</level>
        </filter>

        <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
            <fileNamePattern>${LOG_DIR}/${APP_NAME}-error.%d{yyyy-MM-dd}.%i.log</fileNamePattern>
            <maxFileSize>50MB</maxFileSize>
            <maxHistory>90</maxHistory>
            <totalSizeCap>1GB</totalSizeCap>
        </rollingPolicy>

        <encoder>
            <pattern>${LOG_PATTERN}</pattern>
            <charset>UTF-8</charset>
        </encoder>
    </appender>

    <!-- ═══════════════════════════════════════════ -->
    <!-- Profile별 설정                               -->
    <!-- ═══════════════════════════════════════════ -->

    <!-- 개발 환경: 콘솔 + 파일 (DEBUG) -->
    <springProfile name="local,dev">
        <root level="DEBUG">
            <appender-ref ref="CONSOLE" />
            <appender-ref ref="FILE" />
        </root>
    </springProfile>

    <!-- 운영 환경: 파일만 (INFO) -->
    <springProfile name="prod">
        <root level="INFO">
            <appender-ref ref="FILE" />
            <appender-ref ref="ERROR_FILE" />
        </root>
    </springProfile>

    <!-- 기본값 (profile 미지정 시) -->
    <springProfile name="default">
        <root level="INFO">
            <appender-ref ref="CONSOLE" />
            <appender-ref ref="FILE" />
            <appender-ref ref="ERROR_FILE" />
        </root>
    </springProfile>

</configuration>
```

### 14.2 실행 및 확인

```bash
# 서버 실행
./gradlew bootRun

# 테스트 요청 보내기
curl -X POST http://localhost:8080/api/waiting \
  -H "Content-Type: application/json" \
  -d '{"userId": 1, "restaurantId": 100}'

# 로그 파일 확인
cat logs/waiting-log.log
cat logs/waiting-log-error.log

# 실시간 로그 모니터링
tail -f logs/waiting-log.log
```

---

## 15. [실습] 로그 압축(.gz)으로 서버 용량 확보하기

### 15.1 왜 압축이 필요한가?

텍스트 기반 로그 파일은 압축률이 매우 높습니다 (보통 **90% 이상** 압축). 하루 100MB 로그가 쌓인다면 30일이면 3GB, 압축하면 300MB 이하로 줄어듭니다.

### 15.2 Logback에서 자동 압축 설정

`fileNamePattern`에 `.gz`만 추가하면 됩니다. 정말 간단합니다.

```xml
<!-- 변경 전 -->
<fileNamePattern>${LOG_DIR}/${APP_NAME}.%d{yyyy-MM-dd}.%i.log</fileNamePattern>

<!-- 변경 후 — .gz만 추가! -->
<fileNamePattern>${LOG_DIR}/${APP_NAME}.%d{yyyy-MM-dd}.%i.log.gz</fileNamePattern>
```

### 15.3 전체 설정 (압축 포함)

```xml
<appender name="FILE_GZ" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <file>${LOG_DIR}/${APP_NAME}.log</file>

    <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
        <!-- ✅ .gz 확장자 → 자동 GZIP 압축 -->
        <fileNamePattern>${LOG_DIR}/archived/${APP_NAME}.%d{yyyy-MM-dd}.%i.log.gz</fileNamePattern>
        <maxFileSize>100MB</maxFileSize>
        <maxHistory>30</maxHistory>
        <totalSizeCap>3GB</totalSizeCap>
    </rollingPolicy>

    <encoder>
        <pattern>${LOG_PATTERN}</pattern>
        <charset>UTF-8</charset>
    </encoder>
</appender>
```

### 15.4 압축 전후 비교

```bash
# 압축 전 (30일 보관 가정)
ls -lh logs/
# waiting-log.2024-01-01.0.log    100MB
# waiting-log.2024-01-02.0.log     95MB
# ... (30일 × ~100MB = ~3GB)

# 압축 후
ls -lh logs/archived/
# waiting-log.2024-01-01.0.log.gz    8MB
# waiting-log.2024-01-02.0.log.gz    7MB
# ... (30일 × ~8MB = ~240MB)

# 압축된 로그 읽기
zcat logs/archived/waiting-log.2024-01-01.0.log.gz | grep "ERROR"
zless logs/archived/waiting-log.2024-01-01.0.log.gz
```

---

# 섹션 5. Elasticsearch와 Logstash를 활용한 로그 수집

---

## 16. ELK 스택이란 무엇일까?

### 16.1 ELK 스택이 탄생한 배경

서버가 1대일 때는 `tail -f`로 충분했습니다. 하지만 서버가 10대, 100대가 되면 어떨까요?

```
문제 1: 10대 서버에 SSH 접속해서 각각 로그 파일을 찾아봐야 함
문제 2: 특정 에러가 어느 서버에서 발생했는지 모름
문제 3: 로그를 시간순으로 합쳐서 보고 싶은데 불가능
문제 4: "지난주 ERROR 로그 중 결제 관련만 보여줘" 같은 검색 불가
```

이 문제를 해결하기 위해 **"로그를 한 곳에 모아서 검색하자"**라는 아이디어가 탄생했고, 그 결과물이 **ELK 스택**입니다.

### 16.2 ELK 스택의 구성

```
ELK = Elasticsearch + Logstash + Kibana

┌──────────┐     ┌───────────┐     ┌───────────────┐     ┌──────────┐
│ 애플리케이션│────→│ Logstash   │────→│ Elasticsearch │────→│  Kibana  │
│ (로그 발생)│     │ (수집/변환) │     │ (저장/검색)    │     │ (시각화)  │
└──────────┘     └───────────┘     └───────────────┘     └──────────┘
```

| 구성 요소 | 역할 | 비유 |
|----------|------|------|
| **Elasticsearch** | 로그 저장 및 전문 검색 엔진 | 도서관 (저장 + 색인) |
| **Logstash** | 로그 수집, 변환, 전달 파이프라인 | 우체국 (수거 + 분류 + 배달) |
| **Kibana** | 로그 시각화 및 대시보드 | 열람실 (검색 + 분석) |

### 16.3 기존 방식과의 비교

| 항목 | 파일 기반 (기존) | ELK 스택 (현재) |
|------|----------------|----------------|
| 로그 검색 | `grep`으로 텍스트 검색 | 전문 검색 엔진 (밀리초) |
| 다중 서버 | 서버마다 SSH 접속 | 한 곳에서 모든 서버 로그 |
| 시각화 | 없음 (텍스트만) | 그래프, 차트, 대시보드 |
| 알림 | 없음 (수동 확인) | 조건 기반 자동 알림 |
| 보관 | 서버 디스크에 의존 | 별도 스토리지, 확장 가능 |

---

## 17. Logstash의 핵심 기능은 무엇일까?

### 17.1 Logstash의 3단계 파이프라인

```
┌─────────┐     ┌────────────┐     ┌──────────┐
│  INPUT   │────→│   FILTER   │────→│  OUTPUT  │
│ (수집)   │     │ (변환/가공) │     │ (전달)   │
└─────────┘     └────────────┘     └──────────┘
```

**INPUT (수집):** 어디에서 로그를 받아올 것인가?
- `tcp` / `udp` — 네트워크를 통한 수신
- `beats` — Filebeat 등 경량 수집기로부터
- `file` — 로그 파일 직접 읽기
- `kafka` — Kafka 토픽에서 소비

**FILTER (변환):** 받아온 로그를 어떻게 가공할 것인가?
- `grok` — 비정형 텍스트를 구조화된 데이터로 변환
- `date` — 타임스탬프 파싱
- `mutate` — 필드 추가/삭제/변환
- `geoip` — IP에서 지역 정보 추출

**OUTPUT (전달):** 가공한 로그를 어디로 보낼 것인가?
- `elasticsearch` — Elasticsearch에 저장
- `file` — 파일로 출력
- `kafka` — Kafka 토픽으로 전달

### 17.2 실제 Logstash 설정 예시

```ruby
# logstash.conf
input {
  tcp {
    port => 5044
    codec => json_lines
  }
}

filter {
  # JSON 로그에서 타임스탬프 파싱
  date {
    match => [ "timestamp", "yyyy-MM-dd HH:mm:ss.SSS" ]
    target => "@timestamp"
  }

  # 로그 레벨별 태그 추가
  if [level] == "ERROR" {
    mutate { add_tag => ["error"] }
  }
}

output {
  elasticsearch {
    hosts => ["http://elasticsearch:9200"]
    index => "app-logs-%{+YYYY.MM.dd}"
  }
}
```

---

## 18. Elasticsearch를 로그 수집에 사용하는 이유

### 18.1 왜 RDB가 아닌 Elasticsearch인가?

```
MySQL에 로그를 저장하면?

INSERT INTO logs (timestamp, level, message) VALUES (...);
→ 하루 수백만 건 INSERT → DB 부하 폭증
→ LIKE '%결제 오류%' → 풀 스캔 → 수십 초

Elasticsearch에 로그를 저장하면?

POST /logs/_doc { "message": "결제 오류" }
→ 역인덱스(Inverted Index) 덕분에 밀리초 단위 검색
→ 수십 대로 수평 확장(Horizontal Scaling) 가능
```

### 18.2 Elasticsearch의 핵심 개념

| ES 개념 | RDB 비교 | 설명 |
|---------|---------|------|
| **Index** | Database | 로그 데이터의 논리적 모음 |
| **Document** | Row | 개별 로그 한 건 |
| **Field** | Column | 로그의 각 속성 |
| **Shard** | Partition | 데이터 분산 저장 단위 |
| **Replica** | Slave | 복제본 (장애 대비) |

### 18.3 역인덱스(Inverted Index)란?

일반적인 인덱스가 "문서 → 단어"를 매핑한다면, 역인덱스는 **"단어 → 문서"**를 매핑합니다.

```
일반 인덱스 (RDB):
문서1 → "웨이팅 등록 실패"
문서2 → "웨이팅 등록 성공"
문서3 → "결제 처리 실패"

역인덱스 (Elasticsearch):
"웨이팅" → [문서1, 문서2]
"등록"   → [문서1, 문서2]
"실패"   → [문서1, 문서3]
"성공"   → [문서2]
"결제"   → [문서3]

→ "실패"로 검색하면? 즉시 문서1, 문서3을 반환 (O(1))
```

---

## 19. [실습] Docker 설치

### 19.1 Docker가 필요한 이유

ELK 스택(Elasticsearch, Logstash, Kibana)을 로컬에 직접 설치하면 환경 설정이 복잡하고, 버전 충돌 문제가 생깁니다. Docker를 사용하면 **한 줄의 명령어**로 동일한 환경을 만들 수 있습니다.

### 19.2 Docker Desktop 설치 (Windows)

```
1. https://www.docker.com/products/docker-desktop/ 접속
2. "Download for Windows" 클릭
3. 설치 파일 실행
4. WSL 2 기반 엔진 사용 체크 (권장)
5. 설치 완료 후 재부팅
```

### 19.3 설치 확인

```bash
# Docker 버전 확인
docker --version
# Docker version 24.x.x

# Docker Compose 버전 확인
docker compose version
# Docker Compose version v2.x.x

# 테스트 실행
docker run hello-world
```

---

## 20. [실습] Elasticsearch, Logstash 설치 및 설정

### 20.1 프로젝트 구조

```
waiting-log/
├── src/
├── docker/
│   ├── docker-compose.yml
│   └── logstash/
│       └── pipeline/
│           └── logstash.conf
├── logs/
├── build.gradle
└── ...
```

### 20.2 docker-compose.yml 작성

```yaml
# docker/docker-compose.yml
version: '3.8'

services:
  # ═══════════════════════════════════════
  # Elasticsearch
  # ═══════════════════════════════════════
  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.12.0
    container_name: elasticsearch
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false    # 실습용 (운영 시 true)
      - xpack.security.http.ssl.enabled=false
      - "ES_JAVA_OPTS=-Xms512m -Xmx512m"
    ports:
      - "9200:9200"
    volumes:
      - es-data:/usr/share/elasticsearch/data
    networks:
      - elk-network
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:9200/_cluster/health || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 10

  # ═══════════════════════════════════════
  # Logstash
  # ═══════════════════════════════════════
  logstash:
    image: docker.elastic.co/logstash/logstash:8.12.0
    container_name: logstash
    volumes:
      - ./logstash/pipeline/logstash.conf:/usr/share/logstash/pipeline/logstash.conf:ro
    ports:
      - "5044:5044"   # TCP input
      - "9600:9600"   # Logstash API
    environment:
      - "LS_JAVA_OPTS=-Xms256m -Xmx256m"
    depends_on:
      elasticsearch:
        condition: service_healthy
    networks:
      - elk-network

volumes:
  es-data:
    driver: local

networks:
  elk-network:
    driver: bridge
```

### 20.3 Logstash 파이프라인 설정

```ruby
# docker/logstash/pipeline/logstash.conf
input {
  tcp {
    port => 5044
    codec => json_lines
  }
}

filter {
  # 타임스탬프 파싱
  if [timestamp] {
    date {
      match => [ "timestamp", "yyyy-MM-dd HH:mm:ss.SSS" ]
      target => "@timestamp"
      timezone => "Asia/Seoul"
    }
  }

  # 로그 레벨이 없으면 기본값 설정
  if ![level] {
    mutate { add_field => { "level" => "INFO" } }
  }

  # 불필요한 필드 제거
  mutate {
    remove_field => ["@version", "host", "port"]
  }
}

output {
  elasticsearch {
    hosts => ["http://elasticsearch:9200"]
    index => "waiting-logs-%{+YYYY.MM.dd}"
  }

  # 디버깅용 콘솔 출력 (운영 시 제거)
  stdout {
    codec => rubydebug
  }
}
```

### 20.4 Spring Boot에서 Logstash 연동 설정

**build.gradle에 의존성 추가:**

```groovy
dependencies {
    // ... 기존 의존성 ...
    implementation 'net.logstash.logback:logstash-logback-encoder:7.4'
}
```

**logback-spring.xml에 Logstash Appender 추가:**

```xml
<!-- Logstash TCP Appender -->
<appender name="LOGSTASH" class="net.logstash.logback.appender.LogstashTcpSocketAppender">
    <destination>localhost:5044</destination>
    <encoder class="net.logstash.logback.encoder.LogstashEncoder">
        <customFields>{"service":"waiting-log","env":"local"}</customFields>
        <includeMdcKeyName>requestId</includeMdcKeyName>
        <includeMdcKeyName>clientIp</includeMdcKeyName>
    </encoder>
    <!-- 연결 실패 시 재시도 설정 -->
    <reconnectionDelay>5 seconds</reconnectionDelay>
</appender>

<!-- root에 LOGSTASH appender 추가 -->
<root level="INFO">
    <appender-ref ref="CONSOLE" />
    <appender-ref ref="FILE" />
    <appender-ref ref="LOGSTASH" />
</root>
```

### 20.5 실행 및 확인

```bash
# 1. Docker 컨테이너 시작
cd docker
docker compose up -d

# 2. Elasticsearch 동작 확인
curl http://localhost:9200
# → 클러스터 정보 JSON 응답

# 3. Spring Boot 앱 시작
cd ..
./gradlew bootRun

# 4. 테스트 요청 보내기
curl -X POST http://localhost:8080/api/waiting \
  -H "Content-Type: application/json" \
  -d '{"userId": 1, "restaurantId": 100}'

# 5. Elasticsearch에 로그가 저장되었는지 확인
curl "http://localhost:9200/waiting-logs-*/_search?pretty&q=*"
```

---

## 21. Logback, Logstash, Elasticsearch를 활용한 아키텍처 구성

### 21.1 전체 아키텍처

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Production Architecture                       │
│                                                                      │
│  ┌──────────────┐   Logstash    ┌───────────┐    ┌──────────────┐   │
│  │ Spring Boot  │──TCP/JSON───→│ Logstash   │──→│Elasticsearch │   │
│  │  Server #1   │   (5044)     │            │   │              │   │
│  └──────────────┘              │  - parse   │   │  - index     │   │
│                                │  - filter  │   │  - search    │   │
│  ┌──────────────┐              │  - enrich  │   │  - aggregate │   │
│  │ Spring Boot  │──TCP/JSON───→│            │   │              │   │
│  │  Server #2   │              └───────────┘   └──────┬───────┘   │
│  └──────────────┘                                      │           │
│                                                        ▼           │
│                                                  ┌──────────┐     │
│  ┌──────────────┐                                │  Kibana   │     │
│  │ Spring Boot  │──TCP/JSON───→ (Logstash)──→   │  (시각화)  │     │
│  │  Server #N   │                                └──────────┘     │
│  └──────────────┘                                                  │
└─────────────────────────────────────────────────────────────────────┘
```

### 21.2 데이터 흐름

```
1. Spring Boot 앱에서 log.info("...") 호출
   ↓
2. Logback이 LogstashEncoder로 JSON 변환
   {
     "@timestamp": "2024-01-15T14:30:01.123+09:00",
     "level": "INFO",
     "logger_name": "c.e.w.s.WaitingService",
     "message": "웨이팅 등록 완료",
     "requestId": "a1b2c3d4",
     "service": "waiting-log"
   }
   ↓
3. TCP 소켓을 통해 Logstash(5044)로 전송
   ↓
4. Logstash가 타임스탬프 파싱, 필드 보강
   ↓
5. Elasticsearch의 인덱스(waiting-logs-2024.01.15)에 저장
   ↓
6. Kibana에서 검색 및 시각화 가능
```

### 21.3 대용량 환경을 위한 개선된 아키텍처

실제 대용량 트래픽 환경에서는 Logstash 앞에 **Kafka**를 두는 것이 일반적입니다. 이유는 Logstash가 일시적으로 다운되어도 **로그 유실이 없도록 버퍼** 역할을 해주기 때문입니다.

```
[App] → [Kafka] → [Logstash] → [Elasticsearch] → [Kibana]
```

이 아키텍처는 섹션 9에서 최종 아키텍처와 함께 더 자세히 다룹니다.

---

# 섹션 6. Kibana를 통한 로그 모니터링

---

## 22. Kibana의 핵심 기능은 무엇일까?

### 22.1 Kibana란?

Kibana는 Elasticsearch에 저장된 데이터를 **시각화**하고 **검색**할 수 있는 웹 기반 인터페이스입니다. ELK 스택의 "눈" 역할을 합니다.

### 22.2 Kibana의 핵심 기능 4가지

| 기능 | 설명 | 사용 시기 |
|------|------|----------|
| **Discover** | 로그 전문 검색 및 필터링 | 특정 에러 로그 찾기, CS 대응 |
| **Dashboard** | 차트/그래프로 로그 시각화 | 실시간 모니터링, 트렌드 분석 |
| **Lens** | 드래그앤드롭으로 차트 생성 | 비개발자도 쉽게 분석 |
| **Alerting** | 조건 기반 알림 | ERROR 급증 시 알림 |

### 22.3 Kibana가 없던 시절 vs 있는 시절

```
Kibana 이전:
  $ ssh server1
  $ grep "ERROR" /var/log/app.log | wc -l
  $ ssh server2
  $ grep "ERROR" /var/log/app.log | wc -l
  → 10대면 10번 반복...

Kibana 이후:
  Discover 탭 → level: ERROR 필터 → 모든 서버의 에러를 한 화면에
  Dashboard → 에러 발생 추이를 실시간 그래프로 확인
```

---

## 23. [실습] Kibana 설치 및 설정

### 23.1 docker-compose.yml에 Kibana 추가

```yaml
# docker/docker-compose.yml에 추가
  kibana:
    image: docker.elastic.co/kibana/kibana:8.12.0
    container_name: kibana
    environment:
      - ELASTICSEARCH_HOSTS=http://elasticsearch:9200
      - xpack.security.enabled=false
    ports:
      - "5601:5601"
    depends_on:
      elasticsearch:
        condition: service_healthy
    networks:
      - elk-network
```

### 23.2 실행 및 접속

```bash
# 전체 ELK 스택 시작
cd docker
docker compose up -d

# Kibana 로딩 대기 (1~2분 소요)
# 접속: http://localhost:5601
```

---

## 24. [실습] Kibana로 Data View 생성하기

### 24.1 Data View란?

Data View는 Kibana가 Elasticsearch의 어떤 인덱스를 바라볼지 정의하는 설정입니다. 로그를 검색하려면 먼저 Data View를 만들어야 합니다.

### 24.2 생성 순서

```
1. Kibana 접속 (http://localhost:5601)
2. 좌측 메뉴 → Management → Stack Management
3. Kibana → Data Views 클릭
4. "Create data view" 버튼 클릭
5. 아래와 같이 입력:

   Name: waiting-logs
   Index pattern: waiting-logs-*
   Timestamp field: @timestamp

6. "Save data view to Kibana" 클릭
```

> ✨ **TIP:** `waiting-logs-*`의 `*`는 와일드카드입니다. `waiting-logs-2024.01.15`, `waiting-logs-2024.01.16` 등 모든 날짜의 인덱스를 포함합니다.

---

## 25. [실습] Discover 탭을 통한 로그 검색하기

### 25.1 Discover 기본 사용법

```
1. 좌측 메뉴 → Analytics → Discover
2. 좌측 상단에서 Data View를 "waiting-logs"로 선택
3. 우측 상단에서 시간 범위 설정 (Last 15 minutes 등)
```

### 25.2 KQL(Kibana Query Language)로 로그 검색하기

```
# 기본 텍스트 검색
결제 오류

# 특정 필드로 검색
level: "ERROR"
requestId: "a1b2c3d4"
message: "웨이팅 등록"

# AND 조건
level: "ERROR" and message: "결제"

# OR 조건
level: "ERROR" or level: "WARN"

# NOT 조건
level: "ERROR" and not message: "timeout"

# 와일드카드
message: "웨이팅*"

# 범위 검색 (숫자 필드)
response_time > 1000
```

### 25.3 실습 시나리오: CS 대응

```
시나리오: "userId 1001 고객이 웨이팅 등록이 안 된다고 합니다"

검색어: message: "1001" and message: "웨이팅"
시간범위: Today

결과에서 확인할 것:
1. 등록 요청 로그가 있는가? → 있다면 서버에 요청은 도달한 것
2. ERROR 레벨 로그가 있는가? → 있다면 서버에서 오류 발생
3. requestId를 찾아서 해당 요청의 전체 흐름 추적
```

---

## 26. [실습] 대시보드를 통해 로그 시각화하기

### 26.1 대시보드에 추가할 시각화 패널

```
1. 📊 시간대별 로그 발생량 (Line Chart)
   → 언제 트래픽이 몰리는지 파악

2. 🥧 로그 레벨 비율 (Pie Chart)
   → ERROR 비율이 높으면 문제

3. 📋 에러 로그 TOP 10 (Table)
   → 가장 많이 발생하는 에러 순위

4. 📈 시간대별 ERROR 발생 추이 (Area Chart)
   → 에러 급증 시점 파악
```

### 26.2 대시보드 생성 순서

```
1. 좌측 메뉴 → Analytics → Dashboard
2. "Create dashboard" 클릭
3. "Create visualization" 클릭

[시간대별 로그 발생량 Line Chart]
  → Lens 에디터에서:
  - Horizontal axis: @timestamp (Date histogram)
  - Vertical axis: Count
  - Break down by: level.keyword

[로그 레벨 비율 Pie Chart]
  → Lens 에디터에서:
  - Slice by: level.keyword
  - Size by: Count

4. 각 시각화를 만든 후 "Save and return"
5. 대시보드 이름 지정: "웨이팅 서비스 로그 모니터링"
6. 저장
```

### 26.3 완성된 대시보드 활용법

```
✅ 매일 아침 대시보드를 열어 전날 밤 에러 현황 확인
✅ ERROR 비율이 갑자기 올라가면 즉시 Discover로 이동하여 상세 확인
✅ 배포 직후 대시보드를 모니터링하여 새 버전의 안정성 확인
✅ CS 대응 시 해당 시간대 로그를 대시보드에서 먼저 확인
```

---

# 섹션 7. Actuator를 활용한 모니터링 시스템 구축

---

## 27. [실습] 실습을 위한 웨이팅 API 코드 수정

### 27.1 왜 코드를 수정하는가?

모니터링 실습을 위해 **의도적으로 다양한 상황**을 만들 수 있는 코드가 필요합니다.

```java
// src/main/java/com/example/waitinglog/controller/WaitingController.java
// 기존 코드에 아래 엔드포인트 추가

@GetMapping("/api/waiting/stress")
public ResponseEntity<?> stressTest(@RequestParam(defaultValue = "100") int delay) {
    log.info("[STRESS TEST] delay: {}ms", delay);

    try {
        // 의도적 지연 — 모니터링에서 응답시간 확인용
        Thread.sleep(delay);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }

    // 10% 확률로 에러 발생 — 에러율 모니터링 확인용
    if (Math.random() < 0.1) {
        log.error("[STRESS TEST] 랜덤 에러 발생!");
        return ResponseEntity.internalServerError()
               .body(Map.of("error", "Internal Server Error"));
    }

    return ResponseEntity.ok(Map.of("status", "ok", "delay", delay));
}

@GetMapping("/api/waiting/oom-test")
public ResponseEntity<?> oomTest() {
    log.warn("[OOM TEST] 메모리 부하 테스트 시작");
    // 주의: 실제 운영 환경에서는 절대 사용하지 마세요!
    // 이 코드는 학습 목적으로만 사용합니다.
    return ResponseEntity.ok(Map.of("message", "메모리 모니터링을 위한 엔드포인트"));
}
```

---

## 28. 모니터링의 필요성과 모니터링 도구

### 28.1 로그와 모니터링의 차이

```
로그(Logging):
  "무슨 일이 일어났는가?" (사건 기록)
  → 과거 시점의 사건을 기록하고 추적

모니터링(Monitoring):
  "지금 시스템 상태가 어떤가?" (상태 측정)
  → 현재 시점의 시스템 건강 상태를 실시간 확인

두 가지는 보완 관계입니다. 둘 다 있어야 완전한 Observability를 달성합니다.
```

### 28.2 모니터링 도구의 진화

```
1세대 (2000년대): Nagios, Zabbix
  → 서버 상태 체크 (UP/DOWN), 임계치 알림

2세대 (2010년대): Graphite, StatsD
  → 시계열 메트릭 수집, 그래프 시각화

3세대 (2015년~): Prometheus + Grafana ← 현재 표준
  → Pull 기반 메트릭 수집, 강력한 쿼리 언어, 풍부한 시각화

4세대 (2020년~): OpenTelemetry
  → 로그 + 메트릭 + 트레이싱 통합 표준
```

### 28.3 우리가 구축할 모니터링 스택

```
Spring Boot App
  │
  ├── Actuator (앱 상태 정보 노출)
  │     └── Micrometer (메트릭 수집 라이브러리)
  │
  ├── Prometheus (메트릭 저장 + 쿼리)
  │
  └── Grafana (시각화 + 알림)
        └── Discord Webhook (장애 알림)
```

---

## 29. 스프링 부트 모니터링 도구 Actuator, Micrometer

### 29.1 Actuator란?

Spring Boot Actuator는 애플리케이션의 **내부 상태를 HTTP 엔드포인트로 노출**해주는 라이브러리입니다.

```
/actuator/health    → 앱 건강 상태 (UP/DOWN)
/actuator/info      → 앱 정보
/actuator/metrics   → 메트릭 목록
/actuator/prometheus → Prometheus 형식 메트릭 (핵심!)
```

### 29.2 Micrometer란?

Micrometer는 **메트릭 수집의 추상화 계층(Facade)**입니다. SLF4J가 로깅의 Facade인 것처럼, Micrometer는 메트릭의 Facade입니다.

```
SLF4J    → Logback, Log4j2    (로깅)
Micrometer → Prometheus, Datadog, New Relic  (메트릭)
```

Micrometer를 사용하면 코드 변경 없이 메트릭 백엔드(Prometheus, Datadog 등)를 교체할 수 있습니다.

### 29.3 주요 메트릭 종류

| 메트릭 유형 | 설명 | 예시 |
|------------|------|------|
| **Counter** | 증가만 하는 값 | HTTP 요청 수, 에러 발생 수 |
| **Gauge** | 증감하는 현재 값 | 현재 메모리 사용량, 활성 스레드 수 |
| **Timer** | 작업 소요 시간 | HTTP 응답 시간, DB 쿼리 시간 |
| **Distribution Summary** | 값의 분포 | 요청 크기, 응답 크기 |

---

## 30. [실습] Actuator와 Micrometer로 서버 모니터링 하기

### 30.1 의존성 추가

```groovy
// build.gradle
dependencies {
    // ... 기존 의존성 ...
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'io.micrometer:micrometer-registry-prometheus'
}
```

### 30.2 application.yml 설정

```yaml
# src/main/resources/application.yml
management:
  endpoints:
    web:
      exposure:
        include: health, info, metrics, prometheus  # 노출할 엔드포인트
  endpoint:
    health:
      show-details: always  # 상세 건강 상태 표시
  metrics:
    tags:
      application: waiting-log  # 모든 메트릭에 앱 이름 태그 추가
```

### 30.3 커스텀 메트릭 추가

```java
// src/main/java/com/example/waitinglog/service/WaitingService.java
// 기존 WaitingService에 Micrometer 메트릭 추가

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@Service
public class WaitingService {

    private static final Logger log = LoggerFactory.getLogger(WaitingService.class);

    private final Map<Long, Waiting> waitingStore = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(1);
    private final AtomicLong waitingNumberSeq = new AtomicLong(1);

    // ✅ 커스텀 메트릭 선언
    private final Counter waitingRegisteredCounter;
    private final Counter waitingCancelledCounter;
    private final Counter waitingErrorCounter;
    private final Timer waitingRegisterTimer;

    public WaitingService(MeterRegistry registry) {
        // 웨이팅 등록 수 카운터
        this.waitingRegisteredCounter = Counter.builder("waiting.registered.count")
                .description("Total number of waitings registered")
                .tag("service", "waiting")
                .register(registry);

        // 웨이팅 취소 수 카운터
        this.waitingCancelledCounter = Counter.builder("waiting.cancelled.count")
                .description("Total number of waitings cancelled")
                .tag("service", "waiting")
                .register(registry);

        // 에러 발생 수 카운터
        this.waitingErrorCounter = Counter.builder("waiting.error.count")
                .description("Total number of waiting errors")
                .tag("service", "waiting")
                .register(registry);

        // 등록 소요 시간 타이머
        this.waitingRegisterTimer = Timer.builder("waiting.register.duration")
                .description("Time taken to register a waiting")
                .tag("service", "waiting")
                .register(registry);
    }

    public Waiting register(Long userId, Long restaurantId) {
        return waitingRegisterTimer.record(() -> {
            log.info("웨이팅 등록 요청 - userId: {}, restaurantId: {}",
                     userId, restaurantId);

            try {
                boolean alreadyWaiting = waitingStore.values().stream()
                        .anyMatch(w -> w.getUserId().equals(userId)
                                && w.getRestaurantId().equals(restaurantId)
                                && w.getStatus() == Waiting.WaitingStatus.WAITING);

                if (alreadyWaiting) {
                    waitingErrorCounter.increment();
                    throw new IllegalStateException("이미 해당 식당에 대기 중입니다.");
                }

                Long id = sequence.getAndIncrement();
                int waitingNumber = (int) waitingNumberSeq.getAndIncrement();
                Waiting waiting = new Waiting(id, userId, restaurantId,
                                              waitingNumber, Waiting.WaitingStatus.WAITING);
                waitingStore.put(id, waiting);

                waitingRegisteredCounter.increment();  // ✅ 등록 성공 카운트

                log.info("웨이팅 등록 완료 - id: {}, waitingNumber: {}",
                         id, waitingNumber);
                return waiting;

            } catch (Exception e) {
                waitingErrorCounter.increment();  // ✅ 에러 카운트
                throw e;
            }
        });
    }

    // cancel() 메서드에서도 waitingCancelledCounter.increment() 추가
    // ... (기존 코드 동일, 취소 성공 시 카운터 증가)
}
```

### 30.4 실행 및 확인

```bash
# 서버 실행 후 Actuator 엔드포인트 확인
curl http://localhost:8080/actuator/health
# {"status":"UP","components":{...}}

curl http://localhost:8080/actuator/metrics
# 사용 가능한 메트릭 목록

# ✅ Prometheus 형식 메트릭 확인 (핵심!)
curl http://localhost:8080/actuator/prometheus
# ... (수백 줄의 메트릭)

# 커스텀 메트릭 확인
curl http://localhost:8080/actuator/prometheus | grep "waiting"
# waiting_registered_count_total 5.0
# waiting_cancelled_count_total 1.0
# waiting_error_count_total 0.0
# waiting_register_duration_seconds_sum 0.123
```

---

# 섹션 8. Prometheus, Grafana를 통한 서버 모니터링과 Discord 알림

---

## 31. [실습] Prometheus 설치 및 메트릭 수집

### 31.1 Prometheus란?

Prometheus는 **Pull 기반 시계열 데이터베이스**입니다. 주기적으로(기본 15초) 대상 서버의 `/actuator/prometheus` 엔드포인트를 호출하여 메트릭을 수집(scrape)합니다.

```
기존 (Push 방식):  앱 → 모니터링 서버 (앱이 직접 보냄)
Prometheus (Pull): 앱 ← Prometheus (Prometheus가 가져감)

Pull 방식의 장점:
- 앱이 모니터링 서버 주소를 몰라도 됨
- 모니터링 서버가 죽어도 앱에 영향 없음
- 새 서버 추가 시 Prometheus 설정만 변경
```

### 31.2 docker-compose.yml에 Prometheus 추가

```yaml
  # docker/docker-compose.yml에 추가
  prometheus:
    image: prom/prometheus:v2.49.0
    container_name: prometheus
    volumes:
      - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro
    ports:
      - "9090:9090"
    networks:
      - elk-network
    extra_hosts:
      - "host.docker.internal:host-gateway"  # Docker에서 호스트 접근용
```

### 31.3 Prometheus 설정 파일

```yaml
# docker/prometheus/prometheus.yml
global:
  scrape_interval: 15s      # 15초마다 메트릭 수집
  evaluation_interval: 15s  # 15초마다 규칙 평가

scrape_configs:
  # Spring Boot 앱 메트릭 수집
  - job_name: 'waiting-log-app'
    metrics_path: '/actuator/prometheus'
    scrape_interval: 10s
    static_configs:
      - targets: ['host.docker.internal:8080']
        labels:
          application: 'waiting-log'
          environment: 'local'
```

### 31.4 실행 및 확인

```bash
# Docker 재시작
cd docker
docker compose up -d

# Prometheus 웹 UI 접속
# http://localhost:9090

# Targets 메뉴에서 Spring Boot 앱이 UP 상태인지 확인
# Status → Targets → waiting-log-app: UP
```

---

## 32. [실습] PromQL 프로메테우스 쿼리 언어 맛보기

### 32.1 PromQL이란?

PromQL(Prometheus Query Language)은 Prometheus에 저장된 시계열 데이터를 조회하는 쿼리 언어입니다.

### 32.2 자주 사용하는 PromQL 예시

```promql
# 1. HTTP 요청 총 수
http_server_requests_seconds_count

# 2. 최근 5분간 초당 요청 수 (Rate)
rate(http_server_requests_seconds_count[5m])

# 3. ERROR 응답만 필터링
http_server_requests_seconds_count{status=~"5.."}

# 4. 평균 응답 시간 (초)
rate(http_server_requests_seconds_sum[5m]) / rate(http_server_requests_seconds_count[5m])

# 5. 95번째 백분위 응답 시간
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))

# 6. JVM 힙 메모리 사용량 (MB)
jvm_memory_used_bytes{area="heap"} / 1024 / 1024

# 7. 커스텀 메트릭: 웨이팅 등록 수 증가율
rate(waiting_registered_count_total[5m])

# 8. CPU 사용률
system_cpu_usage * 100
```

### 32.3 실습: Prometheus UI에서 직접 쿼리하기

```
1. http://localhost:9090 접속
2. 상단 입력창에 쿼리 입력
3. "Execute" 클릭

실습 쿼리들:

① jvm_memory_used_bytes{area="heap"}
   → 현재 힙 메모리 사용량 확인

② rate(http_server_requests_seconds_count{uri="/api/waiting"}[5m])
   → 웨이팅 API 초당 요청 수

③ http_server_requests_seconds_count{status="500"}
   → 500 에러 발생 횟수

④ "Graph" 탭 클릭 → 시간에 따른 변화를 그래프로 확인
```

---

## 33. [실습] Grafana 설치 및 Prometheus 연결

### 33.1 docker-compose.yml에 Grafana 추가

```yaml
  # docker/docker-compose.yml에 추가
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
    depends_on:
      - prometheus
    networks:
      - elk-network

# volumes 섹션에 추가
volumes:
  es-data:
    driver: local
  grafana-data:
    driver: local
```

### 33.2 Prometheus 데이터소스 연결

```
1. Grafana 접속: http://localhost:3000 (admin/admin)
2. 좌측 메뉴 → Connections → Data Sources
3. "Add data source" → "Prometheus" 선택
4. 설정:
   - URL: http://prometheus:9090
   - (나머지 기본값 유지)
5. "Save & test" → "Data source is working" 확인
```

---

## 34. [실습] Grafana 대시보드 생성하기

### 34.1 대시보드 생성

```
1. 좌측 메뉴 → Dashboards → "New" → "New Dashboard"
2. "Add visualization" 클릭
```

### 34.2 패널 1: HTTP 요청률 (Rate)

```
Data Source: Prometheus
Query:
  rate(http_server_requests_seconds_count{application="waiting-log"}[5m])

Legend: {{method}} {{uri}} {{status}}
Visualization: Time series
Panel Title: HTTP 요청률 (req/sec)
```

### 34.3 패널 2: 응답 시간 (95th percentile)

```
Query:
  histogram_quantile(0.95,
    rate(http_server_requests_seconds_bucket{application="waiting-log"}[5m])
  )

Legend: {{uri}}
Visualization: Time series
Panel Title: 응답 시간 P95 (초)
```

### 34.4 패널 3: JVM 메모리 사용량

```
Query A (Heap Used):
  jvm_memory_used_bytes{application="waiting-log", area="heap"} / 1024 / 1024

Query B (Heap Max):
  jvm_memory_max_bytes{application="waiting-log", area="heap"} / 1024 / 1024

Legend: Used / Max
Visualization: Time series
Unit: megabytes (MB)
Panel Title: JVM Heap 메모리 (MB)
```

### 34.5 패널 4: 에러율

```
Query:
  sum(rate(http_server_requests_seconds_count{application="waiting-log", status=~"5.."}[5m]))
  /
  sum(rate(http_server_requests_seconds_count{application="waiting-log"}[5m]))
  * 100

Visualization: Gauge
Unit: percent (0-100)
Thresholds: 0=green, 1=yellow, 5=red
Panel Title: 에러율 (%)
```

### 34.6 패널 5: 커스텀 비즈니스 메트릭

```
Query A (등록):
  rate(waiting_registered_count_total[5m]) * 60

Query B (취소):
  rate(waiting_cancelled_count_total[5m]) * 60

Legend: 등록/분, 취소/분
Visualization: Time series
Panel Title: 웨이팅 등록/취소 추이 (건/분)
```

### 34.7 부하 테스트로 대시보드 확인

```bash
# 간단한 부하 테스트 (10초간 요청 반복)
for i in $(seq 1 100); do
  curl -s -X POST http://localhost:8080/api/waiting \
    -H "Content-Type: application/json" \
    -d "{\"userId\": $i, \"restaurantId\": 100}" > /dev/null &
done
wait

# 스트레스 테스트 (응답시간 변화 관찰)
for i in $(seq 1 50); do
  curl -s "http://localhost:8080/api/waiting/stress?delay=$((RANDOM % 500))" > /dev/null &
done
wait

# → Grafana 대시보드에서 실시간으로 변화하는 그래프 관찰!
```

---

## 35. [실습] Grafana Alert를 연동한 서버 에러 발생 시 Discord로 알림 보내주기

### 35.1 Discord Webhook 설정

```
1. Discord 서버에서 알림 받을 채널 선택
2. 채널 설정(⚙️) → 연동 → 웹훅
3. "새 웹훅" 클릭
4. 이름: "Grafana Alert"
5. "웹훅 URL 복사" → 이 URL을 Grafana에 설정합니다

   예: https://discord.com/api/webhooks/1234567890/abcdefg...
```

### 35.2 Grafana에 Discord Contact Point 추가

```
1. Grafana → Alerting → Contact points
2. "Add contact point"
3. 설정:
   - Name: Discord-Alert
   - Integration: Discord
   - Webhook URL: (위에서 복사한 URL 붙여넣기)
4. "Test" → Discord에 테스트 메시지 수신 확인
5. "Save contact point"
```

### 35.3 Notification Policy 설정

```
1. Alerting → Notification policies
2. Default policy의 Contact point를 "Discord-Alert"로 변경
3. Save
```

### 35.4 Alert Rule 생성 — 에러율 기반 알림

```
1. Alerting → Alert rules → "New alert rule"
2. 설정:

   Rule name: High Error Rate Alert

   [A] Query:
   sum(rate(http_server_requests_seconds_count{application="waiting-log", status=~"5.."}[5m]))
   /
   sum(rate(http_server_requests_seconds_count{application="waiting-log"}[5m]))
   * 100

   [B] Threshold:
   IS ABOVE 5  (에러율 5% 초과 시 알림)

   Evaluation behavior:
   - Evaluate every: 1m (1분마다 평가)
   - For: 3m (3분간 지속되면 알림 발생)

   Labels:
   - severity: critical

   Summary:
   "[ALERT] 웨이팅 서비스 에러율 {{ $values.A }}% 초과!"

   Description:
   "에러율이 5%를 초과했습니다. 즉시 확인이 필요합니다.
   현재 에러율: {{ $values.A }}%
   확인: http://localhost:3000"

3. "Save rule and exit"
```

### 35.5 Alert Rule 생성 — 응답 시간 기반 알림

```
Rule name: Slow Response Time Alert

[A] Query:
histogram_quantile(0.95,
  rate(http_server_requests_seconds_bucket{application="waiting-log"}[5m])
)

[B] Threshold:
IS ABOVE 3  (P95 응답시간 3초 초과 시 알림)

Summary:
"[ALERT] 웨이팅 서비스 응답 지연 - P95: {{ $values.A }}초"
```

### 35.6 알림 테스트

```bash
# 의도적으로 에러를 대량 발생시켜 알림 트리거
for i in $(seq 1 200); do
  curl -s "http://localhost:8080/api/waiting/stress?delay=10" > /dev/null &
done
wait

# → 3분 후 Discord에 알림 메시지 수신!
```

### 35.7 Discord에 수신되는 알림 메시지 예시

```
🔴 [FIRING] High Error Rate Alert

[ALERT] 웨이팅 서비스 에러율 12.5% 초과!

에러율이 5%를 초과했습니다. 즉시 확인이 필요합니다.
현재 에러율: 12.5%
확인: http://localhost:3000

Labels:
  severity: critical
  application: waiting-log
```

---

# 섹션 9. 완강을 축하합니다!

---

## 36. 앞으로 공부해야 할 방향

### 36.1 우리가 여기까지 구축한 것

```
┌───────────────────────────────────────────────────────────────────────┐
│                    최종 완성 아키텍처                                   │
│                                                                       │
│  ┌──────────────┐                                                     │
│  │ Spring Boot  │──── SLF4J + Logback ────┐                           │
│  │  + Actuator  │                          │                           │
│  │  + Micrometer│──── /actuator/prometheus  │                           │
│  └──────┬───────┘         │                │                           │
│         │                 │                ▼                           │
│         │           ┌─────┴──────┐  ┌───────────┐  ┌──────────────┐  │
│         │           │ Prometheus  │  │  Logstash  │  │ 로그 파일     │  │
│         │           │ (메트릭 DB) │  │ (수집/변환) │  │ (.log, .gz)  │  │
│         │           └─────┬──────┘  └─────┬─────┘  └──────────────┘  │
│         │                 │               │                           │
│         │                 ▼               ▼                           │
│         │           ┌──────────┐  ┌───────────────┐                   │
│         │           │ Grafana   │  │ Elasticsearch │                   │
│         │           │ (시각화)  │  │ (로그 저장)    │                   │
│         │           └────┬─────┘  └───────┬───────┘                   │
│         │                │                │                           │
│         │                ▼                ▼                           │
│         │           ┌──────────┐  ┌──────────┐                       │
│         │           │ Discord   │  │  Kibana   │                       │
│         │           │ (알림)    │  │  (로그 UI) │                       │
│         │           └──────────┘  └──────────┘                       │
│         │                                                             │
│         └── 로그: 무슨 일이 일어났는가? (사건 추적)                      │
│             메트릭: 지금 상태가 어떤가? (건강 측정)                      │
│             알림: 문제가 생기면 즉시 알려줘! (자동 대응)                  │
└───────────────────────────────────────────────────────────────────────┘
```

### 36.2 대용량 트래픽을 위한 최종 프로덕션 아키텍처

실제 대규모 서비스(일 수천만 요청)에서 사용하는 아키텍처입니다.

```
┌──────────────────────────────────────────────────────────────────────────────┐
│              Production-Grade Observability Architecture                      │
│                                                                              │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐                                        │
│  │ App #1  │ │ App #2  │ │ App #N  │   Spring Boot + Logback + Micrometer   │
│  └────┬────┘ └────┬────┘ └────┬────┘                                        │
│       │           │           │                                              │
│       ▼           ▼           ▼                                              │
│  ┌───────────────────────────────────┐                                       │
│  │          Kafka Cluster            │  ← 로그 버퍼 (유실 방지, 백프레셔)     │
│  │  (3 brokers, replication=2)       │                                       │
│  └──────────────┬────────────────────┘                                       │
│                 │                                                            │
│       ┌─────────┴─────────┐                                                  │
│       ▼                   ▼                                                  │
│  ┌──────────┐      ┌──────────────┐                                         │
│  │ Logstash │      │ Logstash     │    ← 수평 확장 가능한 로그 파이프라인     │
│  │ Node #1  │      │ Node #2      │                                         │
│  └────┬─────┘      └──────┬───────┘                                         │
│       │                   │                                                  │
│       ▼                   ▼                                                  │
│  ┌──────────────────────────────────┐                                        │
│  │    Elasticsearch Cluster         │  ← Hot-Warm-Cold 아키텍처              │
│  │  ┌──────┐ ┌──────┐ ┌──────┐     │     Hot: 최근 7일 (SSD)               │
│  │  │ Hot  │ │ Warm │ │ Cold │     │     Warm: 30일 (HDD)                  │
│  │  │ (SSD)│ │ (HDD)│ │(S3)  │     │     Cold: 90일+ (Object Storage)      │
│  │  └──────┘ └──────┘ └──────┘     │                                        │
│  └──────────────┬───────────────────┘                                        │
│                 │                                                            │
│                 ▼                                                            │
│          ┌──────────┐                                                        │
│          │  Kibana   │   ← 로그 검색 및 시각화                                │
│          └──────────┘                                                        │
│                                                                              │
│  ───── 메트릭 파이프라인 ─────                                                │
│                                                                              │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐                                        │
│  │ App #1  │ │ App #2  │ │ App #N  │                                        │
│  │/metrics │ │/metrics │ │/metrics │                                        │
│  └────┬────┘ └────┬────┘ └────┬────┘                                        │
│       │           │           │       ← Prometheus가 Pull (scrape)           │
│       ▼           ▼           ▼                                              │
│  ┌──────────────────────────────────┐                                        │
│  │    Prometheus (HA with Thanos)    │  ← 장기 저장은 Thanos/Mimir           │
│  └──────────────┬───────────────────┘                                        │
│                 │                                                            │
│                 ▼                                                            │
│          ┌──────────┐                                                        │
│          │ Grafana   │   ← 메트릭 시각화 + Alert                              │
│          │ + Alerts  │──→ Discord / Slack / PagerDuty                        │
│          └──────────┘                                                        │
│                                                                              │
│  ───── 미래: 분산 트레이싱 (다음 단계) ─────                                   │
│                                                                              │
│  ┌─────────┐                                                                 │
│  │ App #1  │──── OpenTelemetry SDK ────→ ┌───────┐ ──→ ┌────────┐           │
│  │ App #2  │──── (로그+메트릭+트레이스) ──→│Jaeger │     │Tempo   │           │
│  │ App #N  │                             └───────┘     └────────┘           │
│  └─────────┘                                                                 │
└──────────────────────────────────────────────────────────────────────────────┘
```

### 36.3 아키텍처 핵심 설계 원칙

**1. Kafka를 버퍼로 사용하는 이유**

```
문제: Logstash가 죽으면 그 동안의 로그가 유실됨
해결: App → Kafka → Logstash

Kafka의 역할:
- 로그 유실 방지 (디스크에 영속화)
- 백프레셔 처리 (Logstash가 느려도 OK)
- 여러 Consumer가 같은 로그를 소비 가능
  (로그 분석용 + 실시간 알림용 + 장기 보관용)
```

**2. Elasticsearch Hot-Warm-Cold 아키텍처**

```
비용 최적화를 위한 계층형 스토리지:

Hot (최근 7일):
  - 빠른 SSD 디스크
  - 실시간 검색에 최적화
  - 가장 비쌈

Warm (8~30일):
  - HDD 디스크
  - 가끔 검색하는 로그
  - 중간 비용

Cold (31~90일):
  - Object Storage (S3)
  - 거의 검색하지 않는 로그
  - 가장 저렴

→ Index Lifecycle Management (ILM)으로 자동 이동
```

**3. Prometheus 고가용성 (Thanos/Mimir)**

```
Prometheus 단일 인스턴스의 한계:
- 장기 보관이 어려움 (로컬 디스크 의존)
- HA 구성이 복잡

해결: Thanos 또는 Grafana Mimir
- 장기 메트릭 저장 (Object Storage)
- 여러 Prometheus 인스턴스의 메트릭 통합 조회
- 다운샘플링으로 스토리지 절약
```

### 36.4 앞으로 공부해야 할 로드맵

```
현재 수준 (이 핸드북 완료 후):
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ ← 여기까지 왔습니다!

다음 단계 1: 분산 트레이싱
├── OpenTelemetry 기본 개념
├── Jaeger 또는 Zipkin 설치 및 연동
├── Trace ID로 마이크로서비스 간 요청 추적
└── Spring Cloud Sleuth / Micrometer Tracing

다음 단계 2: 로그 파이프라인 고도화
├── Filebeat (경량 로그 수집기)
├── Kafka를 이용한 로그 버퍼링
├── Elasticsearch ILM (인덱스 생명주기 관리)
└── Elasticsearch 클러스터 운영

다음 단계 3: 통합 Observability
├── OpenTelemetry Collector
├── Grafana Loki (로그 전용, ES 대체)
├── Grafana Tempo (트레이싱)
└── Grafana Mimir (메트릭 장기 저장)

다음 단계 4: 클라우드 네이티브
├── Kubernetes 환경 로깅 (sidecar 패턴)
├── AWS CloudWatch / GCP Stackdriver
├── Datadog / New Relic (SaaS 모니터링)
└── SLO/SLI 기반 모니터링
```

### 36.5 기술 선택 가이드

| 상황 | 추천 스택 | 이유 |
|------|----------|------|
| **소규모 (서버 1~5대)** | Logback 파일 + Loki + Grafana | 가볍고 비용 저렴 |
| **중규모 (서버 5~50대)** | ELK + Prometheus + Grafana | 이 핸드북의 구성 |
| **대규모 (서버 50대+)** | Kafka + ES + Prometheus HA | 유실 방지 + 확장성 |
| **클라우드 네이티브** | OpenTelemetry + Grafana Stack | 로그+메트릭+트레이스 통합 |
| **비용 최소화** | Loki + Tempo + Mimir (Grafana Stack) | ES 대비 스토리지 비용 절감 |

### 36.6 마지막 당부

> 📝 **30년 경험담: 마지막 이야기**
>
> 30년간 수백 개의 시스템을 경험하면서 깨달은 것이 있습니다.
>
> **좋은 로그는 좋은 코드만큼 중요합니다.**
>
> 로그는 "귀찮은 부가 작업"이 아니라, 시스템의 **생명선**입니다.
> 새벽 3시에 장애가 발생했을 때, 여러분을 구해주는 것은
> 화려한 아키텍처가 아니라 **잘 남겨진 로그 한 줄**입니다.
>
> 이 핸드북에서 배운 것들을 실무에 하나씩 적용해보세요.
> 처음부터 완벽할 필요는 없습니다. 하지만 시작은 해야 합니다.
>
> 여러분의 성장을 응원합니다. 🚀

---

## 부록: 최종 docker-compose.yml (전체 스택)

```yaml
# docker/docker-compose.yml — 전체 ELK + Prometheus + Grafana
version: '3.8'

services:
  # ═══ Elasticsearch ═══
  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.12.0
    container_name: elasticsearch
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
      - xpack.security.http.ssl.enabled=false
      - "ES_JAVA_OPTS=-Xms512m -Xmx512m"
    ports:
      - "9200:9200"
    volumes:
      - es-data:/usr/share/elasticsearch/data
    networks:
      - monitoring-network
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:9200/_cluster/health || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 10

  # ═══ Logstash ═══
  logstash:
    image: docker.elastic.co/logstash/logstash:8.12.0
    container_name: logstash
    volumes:
      - ./logstash/pipeline/logstash.conf:/usr/share/logstash/pipeline/logstash.conf:ro
    ports:
      - "5044:5044"
      - "9600:9600"
    environment:
      - "LS_JAVA_OPTS=-Xms256m -Xmx256m"
    depends_on:
      elasticsearch:
        condition: service_healthy
    networks:
      - monitoring-network

  # ═══ Kibana ═══
  kibana:
    image: docker.elastic.co/kibana/kibana:8.12.0
    container_name: kibana
    environment:
      - ELASTICSEARCH_HOSTS=http://elasticsearch:9200
      - xpack.security.enabled=false
    ports:
      - "5601:5601"
    depends_on:
      elasticsearch:
        condition: service_healthy
    networks:
      - monitoring-network

  # ═══ Prometheus ═══
  prometheus:
    image: prom/prometheus:v2.49.0
    container_name: prometheus
    volumes:
      - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro
    ports:
      - "9090:9090"
    networks:
      - monitoring-network
    extra_hosts:
      - "host.docker.internal:host-gateway"

  # ═══ Grafana ═══
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
    depends_on:
      - prometheus
    networks:
      - monitoring-network

volumes:
  es-data:
    driver: local
  grafana-data:
    driver: local

networks:
  monitoring-network:
    driver: bridge
```

### 전체 스택 한 번에 시작하기

```bash
cd docker

# 전체 스택 시작
docker compose up -d

# 상태 확인
docker compose ps

# 접속 URL 정리
# Elasticsearch: http://localhost:9200
# Kibana:        http://localhost:5601
# Prometheus:    http://localhost:9090
# Grafana:       http://localhost:3000 (admin/admin)

# 전체 스택 중지
docker compose down

# 전체 스택 + 데이터 삭제
docker compose down -v
```

---

> **끝.**
> 이 핸드북이 여러분의 로깅 & 모니터링 여정에 든든한 나침반이 되길 바랍니다.
