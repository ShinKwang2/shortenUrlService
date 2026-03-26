# 무엇을 로그로 남겨야 하는가

> **이 문서의 위치**
>
> | 문서 | 다루는 질문 |
> |------|-----------|
> | `02-observability-concepts.md` | **WHY** — 왜 Observability가 필요한가, 3대 축 개념 |
> | `04-observability-in-practice.md` | **HOW** — Spring Boot에서 어떻게 설정하고 구현하는가 |
> | `05-observability-clean-code.md` | **WHERE** — 로그/메트릭 코드를 어디에 배치하는가 (분리 패턴) |
> | **이 문서 (logging-strategy.md)** | **WHAT** — 무엇을 로그로 남겨야 하는가 (항목 선정 기준) |
>
> 기존 3개 문서가 "어떻게 설정하고", "어디에 넣고", "왜 필요한지"를 다뤘다면,
> 이 문서는 **"무엇을 로그로 남겨야 하는가"** — 로그 항목 선정 기준 자체에 집중한다.

---

## 우선순위 기준 정의

모든 로그 항목을 3단계 우선순위로 분류한다.
프로덕션 장애 대응 관점에서 "이 로그가 없으면 어떤 일이 벌어지는가?"로 판단한다.

| 우선순위 | 기준 | 비유 |
|---------|------|------|
| **1순위 (Must-Have)** | 이것 없으면 새벽 3시 장애 때 원인 파악 불가능. **비협상 대상** | 소방차의 사이렌 — 없으면 출동 자체가 안 된다 |
| **2순위 (Should-Have)** | 없으면 디버깅에 10배 시간 소요. 원인은 찾지만 몇 시간 걸림 | 소방차의 GPS — 없어도 도착은 하지만, 한참 헤맨다 |
| **3순위 (Nice-to-Have)** | 최적화, 용량 계획, 심층 분석용. 없어도 장애 대응은 가능 | 소방차의 블랙박스 — 현장 대응에는 필요 없지만, 사후 분석에 가치 있다 |

---

## Part 1: 공통 로그 요소 (도메인 무관)

어떤 서비스를 만들든 공통으로 적용되는 로그 항목이다.
ShortenUrl 프로젝트 코드를 예시로 사용하지만, 다른 어떤 프로젝트에도 동일하게 적용된다.

---

### 1순위 (Must-Have) — 6개

#### 1. HTTP 요청/응답 경계 로그

**What**: 요청 진입·종료 시점의 method, URI, status, duration

**Why**: 장애 시 첫 번째 질문은 항상 이것이다 — "서비스가 요청을 받고 있는가? 어떤 응답을 주는가?"
메트릭(Prometheus)이 "에러율이 올라갔다"를 알려줘도, 로그가 없으면 **어떤 요청이 어떤 응답을 받았는지** 확인할 수 없다.

**시나리오**:
새벽 3시 알림 수신. Grafana에서 에러율 급증 확인.
- 로그가 있으면: Loki에서 `[HTTP] POST /shortenUrl → 500 (12ms)` 즉시 확인 → 500 에러의 패턴 분석 → 원인 범위 좁힘
- 로그가 없으면: "서비스가 요청을 받고 있긴 한 건가?" → "어떤 엔드포인트에서 에러가 나는 거지?" → 메트릭의 label만으로 추측 → 확신 없이 코드를 뒤짐

**레벨**: INFO
**필드**: `http.method`, `url.path`, `http.status_code`, `duration_ms`

```
[HTTP] POST /shortenUrl → 200 (15ms)
[HTTP] GET /abc123 → 301 (3ms)
[HTTP] GET /nonexistent → 404 (2ms)
```

> 참고: `05-observability-clean-code.md`의 `RequestLoggingInterceptor` 패턴이 이 로그를 담당한다.

---

#### 2. 예외/에러 로그 (스택트레이스 포함)

**What**: ExceptionHandler에 도달한 모든 예외 — 메시지 + 예외 클래스 + 스택트레이스(5xx)

**Why**: 메트릭은 "뭔가 깨졌다"를 알려주고, 에러 로그는 "왜 깨졌는지"를 알려준다.
`http.server.requests` 메트릭에서 `status=500`이 보여도, **어떤 예외가 발생했는지, 코드 어느 줄에서 터졌는지**는 로그만이 알려줄 수 있다.

**시나리오**:
현재 프로젝트의 `GlobalExceptionHandler`를 보자:

```java
// 현재 코드 — LackOfShortenUrlKeyException 핸들러에 로그가 없다!
@ExceptionHandler(LackOfShortenUrlKeyException.class)
public ResponseEntity<String> handleLackOfShortenUrlKeyException(
        LackOfShortenUrlKeyException ex
) {
    // 500이 나가도 흔적이 없다
    return new ResponseEntity<>("단축 URL 자원이 부족합니다.", HttpStatus.INTERNAL_SERVER_ERROR);
}
```

키 공간이 고갈되어 500 에러가 반환되는 심각한 상황인데, 프로덕션에서 **아무 흔적도 남지 않는다**.
Prometheus에서 500 에러가 보여도, Loki에서 원인을 찾을 수 없다.

**레벨**: ERROR(5xx), WARN(4xx)
**필드**: `exception.type`, `exception.message`, `exception.stacktrace`

```
ERROR 단축 URL 키 생성 한도 초과
  kr.co.shortenurlservice.domain.LackOfShortenUrlKeyException
    at SimpleShortenUrlService.getUniqueShortenUrlKey(SimpleShortenUrlService.java:85)
    at SimpleShortenUrlService.generateShortenUrl(SimpleShortenUrlService.java:34)
    ...

WARN  URL 조회 실패: 단축 URL을 찾지 못했습니다. shortenUrlKey=nonexistent
```

> 핵심: 5xx는 반드시 `log.error("메시지", ex)` — 예외 객체를 두 번째 인자로 전달해야 스택트레이스가 출력된다.
> `log.error("실패: " + ex.getMessage())`는 스택트레이스가 없어서 **어디서 터졌는지 알 수 없다**.

---

#### 3. 비즈니스 상태 변경 로그

**What**: 핵심 엔티티의 생성/수정/삭제/상태전환

**Why**: HTTP 로그는 "요청이 왔다"를 알려주고, 비즈니스 로그는 "그 결과 무엇이 발생했는가"를 알려준다.
`POST /shortenUrl → 200`이라는 HTTP 로그만으로는 **어떤 키가 생성되었고, 어떤 URL에 매핑되었는지** 알 수 없다.

**시나리오**:
고객 문의: "abc123 단축 URL이 잘못된 곳으로 이동합니다."
- 비즈니스 로그가 있으면: Loki에서 `shortenUrlKey=abc123` 검색 → 생성 시점의 `originalUrl` 확인 → 잘못된 URL로 생성된 건지, 나중에 변경된 건지 판별
- 비즈니스 로그가 없으면: DB에서 현재 상태만 확인 가능 → 언제 생성되었는지, 원래 무엇이었는지 알 수 없다 → 원인 파악 불가

**레벨**: INFO
**필드**: entity identifier, action, 관련 비즈니스 데이터

```
INFO  URL 단축 완료, shortenUrlKey=abc123
INFO  리다이렉트 수행, shortenUrlKey=abc123, originalUrl=https://example.com
```

---

#### 4. 외부 시스템 호출 로그

**What**: DB, 외부 API, 메시지 큐, 캐시 호출 전/후

**Why**: 프로덕션 장애의 대부분은 **외부 시스템 문제**다.
DB 타임아웃, 외부 API 레이트 리밋, Redis 연결 거부, 메시지 큐 가득 참 — 이 모든 것이 외부 시스템 호출 경계에서 발생한다.
자신의 코드는 정상인데 외부 시스템이 문제일 때, 호출 로그가 없으면 **어디서 막혔는지** 알 수 없다.

**시나리오**:
ShortenUrl 프로젝트에 Redis 캐시를 추가한 후, Redis 서버가 다운되었다고 가정하자.
- 외부 호출 로그가 있으면: `Redis GET shortenUrl:abc123 → FAILED (ConnectionRefused, 2003ms)` → 즉시 Redis 문제 파악
- 외부 호출 로그가 없으면: 500 에러만 보임 → "코드 버그인가? DB 문제인가? Redis 문제인가?" → 하나씩 확인하며 시간 소모

**레벨**: INFO(성공), WARN/ERROR(실패)
**필드**: `target_system`, `operation`, `duration_ms`, `success/failure`

```
INFO  [DB] findShortenUrlByShortenUrlKey → OK (3ms)
WARN  [Redis] GET shortenUrl:abc123 → FAILED (ConnectionRefused, 2003ms)
ERROR [ExternalAPI] POST https://analytics.example.com/event → 503 (5012ms)
```

> 현재 프로젝트는 인메모리 Repository를 사용하므로 이 로그가 아직 필요 없다.
> 하지만 실제 DB나 외부 API를 연동하는 순간 **가장 먼저 추가해야 할 로그**다.

---

#### 5. 인증/인가 실패 로그

**What**: 거부된 인증/인가 시도 — 누가, 뭘, 왜 거부됐는지

**Why**: 보안. **무차별 대입 공격(brute force) 탐지의 유일한 수단**이다.
메트릭에서 "404가 많다"는 볼 수 있지만, **같은 IP에서 분당 10,000개의 랜덤 키를 시도하고 있다**는 로그 없이는 알 수 없다.
또한 컴플라이언스(보안 감사) 요구사항에서 "인증 실패 이력을 보관하라"는 항목은 거의 필수다.

**시나리오**:
ShortenUrl 서비스에 관리자 API가 추가된 후, 공격자가 관리자 엔드포인트에 무차별 대입을 시도한다.
- 인증 실패 로그가 있으면: `WARN [AUTH] Failed login, ip=203.0.113.42, user=admin, reason=InvalidPassword` → 단일 IP에서 분당 수백 건 → 즉시 IP 차단
- 인증 실패 로그가 없으면: 높은 401 비율만 보임 → "인증 서버 문제인가? 클라이언트 버그인가?" → 공격 패턴인지 구분 불가

**레벨**: WARN
**필드**: `user_id`(또는 IP), `resource`, `failure_reason`

```
WARN  [AUTH] 인증 실패, ip=203.0.113.42, endpoint=/admin/urls, reason=InvalidToken
WARN  [AUTH] 접근 거부, userId=user123, resource=/admin/delete, reason=InsufficientPermission
```

> 현재 프로젝트에는 인증이 없다. 하지만 Spring Security를 추가하는 순간 이 로그가 필수가 된다.

---

#### 6. 데이터 검증 실패 로그

**What**: 입력 검증 실패 시 어떤 필드가 어떤 제약을 위반했는지

**Why**: 높은 검증 실패율은 3가지를 의미할 수 있다:
1. **깨진 클라이언트** — 프론트엔드 배포 후 API 호출 형식이 바뀜
2. **API 계약 오해** — 외부 파트너가 문서와 다르게 호출
3. **공격 시도** — SQL 인젝션, XSS 등 악의적 입력

어떤 경우든, **어떤 필드가 어떤 제약을 위반했는지** 알아야 원인을 파악할 수 있다.

**시나리오**:
프론트엔드 팀이 새 버전을 배포한 직후 400 에러가 급증했다.
- 검증 로그가 있으면: `WARN [VALIDATION] field=originalUrl, constraint=URL형식아님, value=not-a-url***` → 프론트엔드의 URL 인코딩 버그 즉시 파악
- 검증 로그가 없으면: 400 에러가 많다는 것만 앎 → "어떤 필드가 문제인지, 어떤 값을 보냈는지" 프론트엔드 팀에게 역으로 물어야 함

**레벨**: WARN
**필드**: `field_name`, `constraint_violated`, `rejected_value`(민감정보 마스킹)

```
WARN  [VALIDATION] field=originalUrl, constraint=URL형식아님, rejectedValue=not-a-url
WARN  [VALIDATION] field=originalUrl, constraint=NotBlank, rejectedValue=(empty)
```

> `rejected_value`는 민감정보가 포함될 수 있으므로 반드시 마스킹한다.
> 예: 이메일 → `u***@example.com`, 전화번호 → `010-****-5678`

---

### 2순위 (Should-Have) — 3개

#### 7. 성능/지연 이상 로그

**What**: 예상보다 현저히 느린 작업 (예: 평균의 3배 초과)

**Why**: Prometheus 메트릭의 p95/p99는 **집계된 숫자**다.
"p99가 2초"라는 것은 알지만, **어떤 특정 요청이 왜 2초나 걸렸는지**는 알 수 없다.
느린 요청이 발생한 시점에 로그를 남기면, traceId로 해당 요청의 전체 흐름을 추적할 수 있다.

**시나리오**:
ShortenUrl 서비스의 리다이렉트 API가 평소 5ms인데, 간헐적으로 500ms가 걸린다.
- 지연 이상 로그가 있으면: `WARN [SLOW] getOriginalUrlByShortenUrlKey, duration=523ms, threshold=50ms, shortenUrlKey=xyz789` → 해당 traceId로 Tempo에서 구간별 시간 확인 → DB 쿼리가 느린 것 발견
- 지연 이상 로그가 없으면: p99가 높다는 건 알지만, 어떤 요청이 왜 느린지 추적할 단서가 없다

**레벨**: WARN
**필드**: `operation`, `duration_ms`, `threshold_ms`, 비즈니스 식별자

```
WARN  [SLOW] getOriginalUrlByShortenUrlKey, duration=523ms, threshold=50ms, shortenUrlKey=xyz789
```

---

#### 8. 재시도/서킷브레이커 로그

**What**: 재시도 횟수, 서킷브레이커 상태 변경 (CLOSED→OPEN→HALF_OPEN)

**Why**: 재시도는 실패를 숨긴다.
3번 재시도 후 성공하면 메트릭은 "성공"으로 기록되지만, 시스템은 이미 **열화(degradation) 중**이다.
재시도 횟수가 증가하는 추세는 곧 다가올 장애의 **조기 경고 신호**다.

**시나리오**:
현재 프로젝트의 `getUniqueShortenUrlKey()`는 최대 5회 재시도한다:

```java
private String getUniqueShortenUrlKey() {
    final int MAX_RETRY_COUNT = 5;
    int count = 0;
    while (count++ < MAX_RETRY_COUNT) {
        String shortenUrlKey = ShortenUrl.generateShortenUrlKey();
        ShortenUrl shortenUrl = shortenUrlRepository.findShortenUrlByShortenUrlKey(shortenUrlKey);
        if (null == shortenUrl) return shortenUrlKey;
    }
    throw new LackOfShortenUrlKeyException();
}
```

키 공간이 점점 차면 재시도 횟수가 증가한다:
- 재시도 로그가 있으면: 어제까지 평균 1회 → 오늘 평균 3회 → `WARN 키 생성 재시도 4회째` → 키 공간 고갈 **며칠 전 조기 경고**
- 재시도 로그가 없으면: `LackOfShortenUrlKeyException`이 터질 때까지 아무도 모른다 → 이미 장애

서킷브레이커도 마찬가지다:
```
WARN  [CIRCUIT] Redis circuit breaker: CLOSED → OPEN (failures=5/5 in 30s)
INFO  [CIRCUIT] Redis circuit breaker: OPEN → HALF_OPEN (cooldown=60s elapsed)
INFO  [CIRCUIT] Redis circuit breaker: HALF_OPEN → CLOSED (probe succeeded)
```

**레벨**: WARN(임계치 초과), INFO(서킷브레이커 상태변경)

---

#### 9. 설정/기동 로그

**What**: 기동 시 활성 설정값 — 프로파일, 주요 설정, 피처 플래그, DB URL(인증정보 제외)

**Why**: "내 로컬에서는 되는데" = **설정 차이**.
장애 배포와 정상 배포의 기동 로그를 비교하면, **어떤 설정이 바뀌었는지** 즉시 발견할 수 있다.
특히 프로파일(dev/prod)이 잘못 적용되면 완전히 다른 동작을 하는데, 기동 로그 없이는 이를 확인할 방법이 없다.

**시나리오**:
배포 후 서비스가 비정상 동작한다.
- 기동 로그가 있으면: `INFO [STARTUP] profiles=prod, db.url=jdbc:mysql://prod-db:3306/shortenurl, cache.enabled=true, tracing.sampling=0.1` → 이전 배포와 비교 → `cache.enabled`가 `false`→`true`로 변경되었음을 발견 → 캐시 관련 코드 버그 확인
- 기동 로그가 없으면: "뭐가 바뀐 거지?" → 환경 변수 확인, 설정 파일 비교, 배포 히스토리 뒤짐 → 시간 소모

**레벨**: INFO
**필드**: `active_profiles`, 주요 설정값(**시크릿 제외**)

```
INFO  [STARTUP] Application started
  profiles: [prod]
  db.url: jdbc:mysql://prod-db:3306/shortenurl
  cache.enabled: true
  tracing.sampling.probability: 0.1
  server.port: 8080
```

> **절대 남기면 안 되는 것**: DB 비밀번호, API 키, JWT 시크릿 등.
> URL에서 인증정보 부분은 마스킹한다: `jdbc:mysql://***:***@prod-db:3306/shortenurl`

---

### 3순위 (Nice-to-Have) — 2개

#### 10. 스케줄 작업/배치 로그

**What**: 예약 작업의 시작/종료/처리건수/에러

**Why**: 스케줄 작업은 HTTP 요청 없이 실행되므로, 자동 경계 로그가 생성되지 않는다.
HandlerInterceptor도 잡지 못하고, Actuator 메트릭도 자동 수집하지 않는다.
조용히 실패하면 **하류 시스템에 영향이 나타날 때까지 수시간~수일 동안 발견되지 않는다**.

**시나리오**:
ShortenUrl 서비스에 "30일 지난 URL을 만료 처리하는 배치"를 추가한다고 가정하자.
- 배치 로그가 있으면: `INFO [BATCH] ExpiredUrlCleanup started`, `INFO [BATCH] ExpiredUrlCleanup completed, processed=1523, deleted=89, errors=0, duration=3.2s` → 정상 동작 확인
- 배치 로그가 없으면: 배치가 OOM으로 중단 → 만료된 URL이 계속 리다이렉트됨 → CS 문의 → 며칠 후 발견

**레벨**: INFO(시작/종료), ERROR(실패)

```
INFO  [BATCH] ExpiredUrlCleanup started
INFO  [BATCH] ExpiredUrlCleanup completed, processed=1523, deleted=89, errors=0, duration=3.2s
ERROR [BATCH] ExpiredUrlCleanup failed at offset=456, error=DataAccessException: Connection refused
```

---

#### 11. 사용자 행동/분석 로그

**What**: 기능 사용 패턴, 접근 패턴, 피크 시간대

**Why**: 이 로그는 디버깅용이 아니다. **제품 의사결정**을 위한 것이다.
- 어떤 시간대에 트래픽이 집중되는가? → 용량 계획
- 어떤 기능이 많이 쓰이는가? → 기능 우선순위 결정
- 특정 URL 도메인의 단축 요청이 많은가? → 비즈니스 인사이트

장애 대응에는 필요 없으므로 3순위다. 별도의 분석 파이프라인으로 분리하는 것이 일반적이다.

**레벨**: INFO 또는 별도 분석 파이프라인

```
INFO  [ANALYTICS] shortenUrl.created, domain=example.com, hour=14, dayOfWeek=MON
INFO  [ANALYTICS] shortenUrl.redirected, shortenUrlKey=abc123, referrer=twitter.com
```

> 이 데이터는 Prometheus Counter나 별도 분석 시스템(BigQuery, Amplitude 등)으로 보내는 것이 더 적절하다.
> 로그보다 메트릭이 비용 효율적인 영역이다.

---

## Part 2: 도메인별 필수 로그

공통 로그 요소 외에, 각 도메인에는 해당 도메인 특유의 필수 로그 항목이 있다.
3개 도메인을 예시로 들어, **도메인 지식이 로그 설계에 어떻게 영향을 미치는지** 보여준다.

---

### 금융 도메인

금융 도메인은 **법적 요구사항과 감사(audit)가 로그 설계를 지배**한다.
일반 서비스에서 3순위인 로그가 금융에서는 1순위가 되기도 한다.

#### 1순위 — 3개

**거래(Transaction) 로그**
- What: 모든 금전 거래의 금액, 통화, 출금/입금 계좌, 거래 ID, 타임스탬프
- Why: 금융감독원 감사, 고객 분쟁 해결, 이중 결제 탐지. **법적으로 N년간 보관 의무**가 있다
- 시나리오: 고객 "100만원을 이체했는데 상대방에게 안 갔다" → 거래 로그에서 `txId=TX-20260315-001, amount=1000000, currency=KRW, from=1234-5678, to=9876-5432, status=COMPLETED` → 실제로 완료됨을 확인 → 수신 은행 측 문제로 범위 좁힘
- `INFO [TX] txId=TX-20260315-001, type=TRANSFER, amount=1000000, currency=KRW, fromAccount=****5678, toAccount=****5432, status=COMPLETED`

**감사 추적(Audit Trail) 로그**
- What: 누가(who), 무엇을(what), 언제(when), 어디서(where) 변경했는지
- Why: 내부 사기(fraud) 탐지, 규제 준수. "이 계좌의 한도를 누가 올렸나?" 질문에 답해야 한다
- 시나리오: 내부 감사에서 "특정 계좌의 일일 이체 한도가 비정상적으로 높다" 발견 → 감사 로그에서 `userId=admin_kim, action=UPDATE_LIMIT, account=****5678, oldLimit=5000000, newLimit=500000000, ip=10.0.1.42` → 내부 직원이 무단 변경한 사실 확인
- `INFO [AUDIT] userId=admin_kim, action=UPDATE_LIMIT, target=account:****5678, before=5000000, after=500000000, ip=10.0.1.42`

**잔액 변경 로그**
- What: 잔액 변경 전/후 값, 변경 사유
- Why: 잔액 불일치(대사 오류) 발생 시 변경 이력을 역추적해야 한다. 어느 시점에서 틀어졌는지 **변경 전/후 값**이 있어야 찾을 수 있다
- 시나리오: 일일 대사(reconciliation)에서 계좌 잔액이 원장과 10만원 불일치 → 잔액 변경 로그를 시간순 추적 → 14:23에 `before=500000, after=600000, reason=DEPOSIT` → 14:25에 `before=500000, after=400000, reason=WITHDRAWAL` → 14:23의 입금이 반영되지 않은 시점에서 출금이 처리됨 (동시성 버그)
- `INFO [BALANCE] account=****5678, before=500000, after=600000, reason=DEPOSIT, txId=TX-001`

#### 2순위 — 2개

**대사(Reconciliation) 상태 로그**
- What: 대사 작업의 시작/종료, 불일치 건수, 불일치 상세
- Why: 대사는 금융 시스템의 "헬스 체크"다. 불일치가 누적되면 큰 문제로 번진다
- 시나리오: 대사 로그 없이 일일 보고서만 확인 → 3일 전부터 불일치가 생겼지만, 오늘에야 발견 → 수백 건의 거래를 수동 검토해야 함

**적용 환율/이율 로그**
- What: 거래 시점에 적용된 환율, 이율, 수수료율
- Why: "어제 이체할 때 환율이 왜 이렇게 높았나?" 같은 고객 문의 대응. 환율/이율은 시시각각 변하므로, 거래 시점의 적용값을 기록해둬야 한다
- 시나리오: 고객 "환율이 1200원인데 왜 1250원이 적용됐나?" → 로그에서 해당 거래 시점의 적용 환율 확인 → 실시간 환율과 적용 환율의 차이가 스프레드임을 설명

#### 3순위 — 2개

**리스크 스코어링 로그**
- What: 거래별 리스크 점수, 판정 사유
- Why: 사후 분석. "이 의심 거래가 왜 통과됐는가?" → 당시 리스크 점수와 임계값을 비교
- 장애 대응에는 불필요하지만, 리스크 모델 개선과 감사 대응에 가치 있다

**규제 보고서 생성 로그**
- What: 규제 보고서 생성 시작/완료, 포함 건수, 전송 상태
- Why: "이번 분기 보고서가 금감원에 제출됐는가?" 확인용
- 실패 시 법적 문제가 생기므로 보고서 생성 자체는 2순위에 가깝지만, 상세 로그는 3순위다

---

### 이메일 도메인

이메일 도메인은 **발송 상태 추적과 도메인 평판 관리**가 핵심이다.
이메일은 "보냈다"고 끝이 아니라, 상대방 메일 서버가 어떻게 처리했는지까지 추적해야 한다.

#### 1순위 — 2개

**발송 상태 로그**
- What: 각 이메일의 상태 전이 — QUEUED → SENT → DELIVERED / BOUNCED / DEFERRED
- Why: "이메일을 보냈는데 안 왔다"는 CS 문의 대응의 유일한 수단. 이메일은 비동기이므로, 발송 후 어떻게 되었는지 추적하지 않으면 아무것도 알 수 없다
- 시나리오: 마케팅팀 "프로모션 메일이 고객에게 안 간다" → 발송 로그에서 `recipient=user@company.com, status=DEFERRED, reason=MailboxFull` → 수신자 메일함이 가득 참 → 재시도 예정 시간 확인
- `INFO [MAIL] messageId=MSG-001, recipient=u***@company.com, status=SENT, smtpResponse=250 OK`
- `WARN [MAIL] messageId=MSG-001, recipient=u***@company.com, status=BOUNCED, reason=UserUnknown, bounceType=HARD`

**반송/스팸 분류 로그**
- What: 하드 바운스(주소 없음) vs 소프트 바운스(일시적), 스팸 분류 통보
- Why: **도메인 블랙리스트 방지**. 하드 바운스율이 높으면 Gmail/Yahoo 등이 우리 도메인을 스팸으로 분류한다. 한번 블랙리스트에 올라가면 복구에 수주~수개월이 걸린다
- 시나리오: 하드 바운스율이 3%를 넘어감 → 로그에서 bounceType=HARD인 수신자 목록 추출 → 잘못된 이메일 주소 일괄 제거 → 바운스율 정상화
- `ERROR [MAIL] bounceRate=3.2%, threshold=2%, period=24h — 도메인 블랙리스트 위험`

#### 2순위 — 2개

**템플릿 렌더링 로그**
- What: 어떤 템플릿이 어떤 변수로 렌더링되었는지
- Why: "이메일 내용이 깨져 보인다" → 렌더링 시점의 변수값과 템플릿 버전을 확인
- 시나리오: 고객 "주문 확인 메일에 {{orderNumber}}라고 적혀 있다" → 템플릿 변수 바인딩 실패 확인

**레이트 리밋 로그**
- What: 수신 서버별/시간당 발송량, 리밋 도달 시 경고
- Why: Gmail은 시간당 발송 한도가 있다. 한도를 초과하면 일시 차단 → 전체 발송 지연
- 시나리오: 대량 프로모션 발송 중 Gmail 수신 서버가 `452 Too many recipients` 반환 → 레이트 리밋 로그에서 시간당 발송량 확인 → 발송 속도 조절

#### 3순위 — 2개

**오픈/클릭 추적 로그**
- What: 이메일 열람 여부, 링크 클릭 여부
- Why: 마케팅 효과 분석, A/B 테스트. 장애 대응과 무관하지만 비즈니스 의사결정에 가치 있다
- 이 데이터는 보통 별도 분석 시스템(Mixpanel, Amplitude 등)으로 보낸다

**수신자 설정 변경 로그**
- What: 수신 거부(unsubscribe), 수신 채널 변경, 수신 빈도 변경
- Why: 법적 요구사항(스팸메일법) — 수신 거부 요청은 기록해야 하며, 이후 발송하면 안 된다
- 장애 대응에는 불필요하지만 컴플라이언스 관점에서 필요하다

---

### 이커머스 도메인

이커머스 도메인은 **주문 생명주기, 재고 정합성, CS 대응**이 핵심이다.
고객의 돈이 관련되므로 금융 도메인과 유사한 면이 있지만, 재고/배송이라는 물리적 차원이 추가된다.

#### 1순위 — 3개

**주문 생명주기 로그**
- What: 주문의 상태 전이 — CREATED → PAID → PREPARING → SHIPPED → DELIVERED / CANCELLED / REFUNDED
- Why: CS 대응의 생명줄. "제 주문이 어떻게 됐나요?"에 답하려면 상태 전이 이력이 필수다. 또한 "결제는 됐는데 배송이 안 시작됐다"같은 상태 불일치를 발견하는 수단이다
- 시나리오: 고객 "어제 주문했는데 아직 배송 준비 중이다" → 주문 로그에서 `orderId=ORD-001, status=PAID→PREPARING, timestamp=2026-03-19T14:23` 확인 → PREPARING 상태에서 24시간째 멈춤 → 물류 시스템 연동 장애 발견
- `INFO [ORDER] orderId=ORD-001, status=PAID→PREPARING, userId=user123`
- `WARN [ORDER] orderId=ORD-001, stuckInStatus=PREPARING, duration=24h, threshold=4h`

**결제 이벤트 로그**
- What: 결제 시도/성공/실패, PG(결제 게이트웨이) 응답, 환불 처리
- Why: "결제했는데 주문이 안 됐다" / "환불 처리가 안 됐다" — 고객의 돈이 걸린 문제이므로 매우 민감하다. PG 응답까지 기록해야 분쟁 시 증거가 된다
- 시나리오: 고객 "카드 결제됐는데 주문이 안 생겼다" → 결제 로그에서 `pgTxId=PG-123, status=APPROVED` 확인 → 주문 생성 로그 부재 → 결제 승인 후 주문 생성 사이에서 에러 발생 → PG 취소 API 호출 또는 수동 주문 생성
- `INFO [PAYMENT] orderId=ORD-001, pgTxId=PG-123, amount=59000, method=CARD, status=APPROVED`
- `ERROR [PAYMENT] orderId=ORD-001, pgTxId=PG-124, amount=59000, status=FAILED, reason=InsufficientBalance`

**재고 변경 로그**
- What: 재고 증감 — 변경 전/후 수량, 변경 사유(판매, 입고, 반품, 수동 조정)
- Why: **과초과판매(overselling) 방지**. 재고가 0인데 주문이 들어가면 고객에게 취소 연락을 해야 하고, 신뢰를 잃는다. 재고 불일치 원인을 추적하려면 변경 이력이 필수다
- 시나리오: 상품 A가 재고 0인데 5건이 더 팔림 → 재고 변경 로그를 시간순 추적 → 동시에 10명이 마지막 1개를 구매 → 동시성 제어(낙관적 잠금 등) 미비 발견
- `INFO [INVENTORY] productId=PROD-001, before=10, after=9, reason=SOLD, orderId=ORD-001`
- `WARN [INVENTORY] productId=PROD-001, stock=0, attemptedDecrease=1, reason=OVERSELLING_DETECTED`

#### 2순위 — 2개

**장바구니 이벤트 로그**
- What: 장바구니 추가/삭제/수량변경
- Why: "왜 장바구니 전환율이 떨어졌는가?" 분석. 장바구니에 담았다가 삭제하는 패턴 → 가격/배송비 문제 추정
- 시나리오: 장바구니→주문 전환율이 급락 → 장바구니 이벤트 로그에서 "배송비 표시 단계에서 삭제 급증" 패턴 발견 → 배송비 정책 변경 효과 확인

**배송 상태 로그**
- What: 물류사 API 응답, 배송 상태 전이, 배송 지연 알림
- Why: "배송이 어디까지 왔나요?" CS 대응. 물류사 API 장애 시 배송 추적 불가 상황 감지
- 시나리오: 물류사 API에서 `status=IN_TRANSIT`인 건이 3일간 업데이트 없음 → 물류사 측 문제인지, 우리 시스템 연동 문제인지 판별

#### 3순위 — 2개

**검색/탐색 로그**
- What: 검색어, 필터 조건, 검색 결과 수, 결과 없음(zero result) 비율
- Why: 상품 검색 최적화, "이 검색어로 검색했는데 원하는 상품이 안 나온다" 개선
- 장애 대응과 무관하지만, zero result 비율이 급증하면 검색 인덱스 문제일 수 있다

**추천 엔진 로그**
- What: 추천 알고리즘 응답, 추천 결과, 추천 클릭률
- Why: 추천 품질 모니터링. 추천 알고리즘 변경 후 A/B 테스트 결과 분석
- 완전히 분석 영역이므로 별도 파이프라인으로 보내는 것이 일반적이다

---

## Part 3: 신입 개발자를 위한 로그 결정 프레임워크

Part 1~2의 지식을 실무에서 빠르게 적용할 수 있는 체크리스트와 가이드라인이다.

---

### 3-1. 의사결정 체크리스트 (5단계 질문)

새 코드를 작성하거나 기존 코드를 리뷰할 때, 이 5단계 질문으로 "여기에 로그가 필요한가?"를 판단한다.

```
1. 에러/예외인가?
   → YES → 무조건 로그 (ERROR 또는 WARN)
   → NO  → 다음 질문으로

2. 시스템 경계를 넘는가? (HTTP 요청/응답, DB 호출, 외부 API, 메시지 큐)
   → YES → 로그 (INFO)
   → NO  → 다음 질문으로

3. 비즈니스 상태가 변하는가? (엔티티 생성/수정/삭제/상태전환)
   → YES → 로그 (INFO)
   → NO  → 다음 질문으로

4. 보안 관련 이벤트인가? (인증 실패, 권한 거부, 의심스러운 접근)
   → YES → 로그 (WARN 또는 INFO)
   → NO  → 다음 질문으로

5. 위 모두 아니다?
   → 아마 로그 불필요
   → 개발 중 디버깅에 필요하면 DEBUG 레벨로
```

이 체크리스트는 Part 1의 1순위 6개 항목과 정확히 대응된다:
- 질문 1 → 항목 2 (예외/에러 로그)
- 질문 2 → 항목 1, 4 (HTTP 경계, 외부 시스템 호출)
- 질문 3 → 항목 3 (비즈니스 상태 변경)
- 질문 4 → 항목 5, 6 (인증/인가 실패, 데이터 검증 실패)

---

### 3-2. 흔한 실수 6가지

신입 개발자가 가장 자주 범하는 로깅 실수와 그 이유.

#### 실수 1: 모든 것을 INFO로 남긴다

```java
// Bad
log.info("메서드 진입");
log.info("파라미터: {}", param);
log.info("중간 결과: {}", result);
log.info("메서드 종료");
```

**문제**: 노이즈가 올라가고, 진짜 중요한 INFO 로그가 묻힌다.
프로덕션에서 "INFO만으로 문제를 추적할 수 있어야 한다"는 원칙에서, INFO가 너무 많으면 오히려 추적이 어려워진다.

**해결**: 진입/종료는 DEBUG, 비즈니스 결과는 INFO.
AOP를 쓰면 진입/종료를 자동으로 DEBUG 처리할 수 있다 (`05-observability-clean-code.md` 참고).

#### 실수 2: ERROR에 스택트레이스를 누락한다

```java
// Bad — 스택트레이스 없음
log.error("처리 실패: " + ex.getMessage());

// Good — 예외 객체를 두 번째 인자로 전달
log.error("처리 실패", ex);
```

**문제**: `ex.getMessage()`만으로는 **어디서 터졌는지** 알 수 없다.
"NullPointerException"이라는 메시지만 보이고, 코드 어느 줄인지 모른다.

**해결**: SLF4J에서 예외 객체는 **마지막 인자**로 전달하면 자동으로 스택트레이스가 출력된다.

#### 실수 3: 민감정보를 로깅한다

```java
// Bad — 절대 하면 안 됨
log.info("로그인 시도, userId={}, password={}", userId, password);
log.info("결제 요청, cardNumber={}", cardNumber);

// Good — 민감정보 마스킹
log.info("로그인 시도, userId={}", userId);  // password 로깅하지 않음
log.info("결제 요청, cardLast4={}", cardNumber.substring(cardNumber.length() - 4));
```

**문제**: 로그는 장기간 보관되고, 여러 시스템을 거치며, 다수의 사람이 접근할 수 있다.
비밀번호, 카드번호, 주민번호, 토큰이 로그에 들어가면 **보안 사고**이자 **법률 위반**이다.

**해결**: 로그에는 식별자(ID)만 남기고, 민감한 값은 절대 남기지 않는다.
`toString()` 오버라이드에서 민감 필드를 제외하는 것도 좋은 습관이다.

#### 실수 4: 문자열 연결로 로그 메시지를 만든다

```java
// Bad — 로그 레벨이 비활성이어도 문자열 연결이 실행됨
log.debug("key=" + key + ", url=" + originalUrl);

// Good — 플레이스홀더 사용, 로그 레벨 비활성 시 연결 미실행
log.debug("key={}, url={}", key, originalUrl);
```

**문제**: `log.debug("key=" + key)`에서, DEBUG 레벨이 OFF여도 `"key=" + key` 문자열 연결은 **매번 실행**된다.
트래픽이 높으면 불필요한 문자열 생성으로 GC 부담이 증가한다.

**해결**: SLF4J의 `{}` 플레이스홀더를 사용하면, 해당 레벨이 활성화되었을 때만 문자열이 생성된다.

#### 실수 5: 4xx 에러를 ERROR로 남긴다

```java
// Bad — 404는 서버 잘못이 아님
log.error("URL not found: {}", shortenUrlKey);

// Good — 클라이언트 에러는 WARN
log.warn("URL 조회 실패: shortenUrlKey={}", shortenUrlKey);
```

**문제**: 404는 클라이언트가 존재하지 않는 리소스에 접근한 것이다. 서버는 정상 동작하고 있다.
ERROR로 남기면 에러 알림이 폭발하고, **진짜 서버 에러(5xx)가 노이즈에 묻힌다**.

**해결**: `02-observability-concepts.md`의 패턴 2 참고 — 4xx는 WARN, 5xx는 ERROR.
"이 에러가 발생했을 때 개발자가 새벽에 깨어나야 하는가?"로 판단하면 쉽다.

#### 실수 6: 식별자를 포함하지 않는다

```java
// Bad — "뭘?"이라는 질문에 답할 수 없다
log.info("처리 완료");
log.info("조회 성공");
log.warn("재시도");

// Good — 무엇이 완료/성공/재시도되었는지 알 수 있다
log.info("URL 단축 완료, shortenUrlKey={}", key);
log.info("URL 조회 성공, shortenUrlKey={}, originalUrl={}", key, url);
log.warn("키 생성 재시도 {}회째", count);
```

**문제**: 식별자 없는 로그는 Loki에서 검색할 수 없다.
"처리 완료"가 초당 100건 찍히면, **어떤 요청의 "처리 완료"인지** 특정할 방법이 없다.

**해결**: 모든 로그에 최소 하나의 **비즈니스 식별자**를 포함한다.
ShortenUrl에서는 `shortenUrlKey`가 대부분의 로그에서 핵심 식별자다.

---

### 3-3. 단계적 로깅 적용 전략

새 프로젝트나 기존 프로젝트에 로깅을 추가할 때, 한번에 다 하지 말고 4단계로 나눈다.
각 단계는 이전 단계가 완료되어야 다음으로 넘어갈 수 있다.

#### Phase 1: ExceptionHandler 로그 + HTTP 경계 로그 (30분이면 가능)

가장 적은 노력으로 가장 큰 효과를 얻는다.

- `GlobalExceptionHandler`에 적절한 레벨의 로그 추가 (5xx → ERROR, 4xx → WARN)
- catch-all `Exception.class` 핸들러 추가
- `RequestLoggingInterceptor` 또는 유사한 HTTP 경계 로그 추가

이것만으로 **"서비스가 어떤 요청을 받고, 어떤 에러가 나고 있는가"**를 알 수 있다.
Part 1의 항목 1, 2가 커버된다.

#### Phase 2: 비즈니스 상태 변경 로그 (비즈니스 메서드당 log.info 1줄)

핵심 비즈니스 메서드에 결과 로그를 추가한다.

- `generateShortenUrl()` → `log.info("URL 단축 완료, shortenUrlKey={}", key)`
- 상태 전이가 있는 메서드 → 전이 전/후 상태 기록

과하게 넣지 않는다. **메서드당 1줄**이 원칙이다.
Part 1의 항목 3이 커버된다.

#### Phase 3: 외부 시스템 호출 로그 (실제 DB/API 연동 시)

인메모리에서 실제 DB/캐시/외부 API로 전환할 때 추가한다.

- DB 호출 전/후 로그 (또는 JPA의 쿼리 로그 설정)
- 외부 API 호출 전/후 로그 (RestTemplate/WebClient Interceptor)
- 캐시 hit/miss 로그

Part 1의 항목 4가 커버된다.

#### Phase 4: 성능/재시도 로그 (프로덕션 트래픽 기준선 확보 후)

프로덕션에서 정상 트래픽의 기준선(baseline)이 확보된 후에 추가한다.
기준선이 없으면 "이 응답 시간이 느린 건가 정상인 건가?" 판단할 수 없다.

- 지연 이상 감지 로그 (평균의 3배 초과 시 WARN)
- 재시도 횟수 경고 로그
- 서킷브레이커 상태 변경 로그

Part 1의 항목 7, 8이 커버된다.

---

### 3-4. PR 리뷰 체크리스트

PR(Pull Request)을 리뷰할 때 로그 관점에서 확인해야 할 4가지 질문.
이 체크리스트를 팀의 PR 리뷰 템플릿에 추가하면 로그 품질이 일관되게 유지된다.

```
□ 새 ExceptionHandler에 적절한 로그가 있는가?
  - 5xx → ERROR + 스택트레이스(예외 객체 전달)
  - 4xx → WARN + 메시지
  - catch-all 핸들러가 존재하는가?

□ INFO 로그만으로 요청의 여정을 진입→종료까지 추적할 수 있는가?
  - HTTP 경계(진입/응답)가 기록되는가?
  - 핵심 비즈니스 결과가 기록되는가?
  - 식별자(shortenUrlKey 등)로 특정 요청을 필터링할 수 있는가?

□ toString()이 민감정보를 노출할 가능성은?
  - DTO/Entity의 toString()에 비밀번호, 토큰, 카드번호가 포함되지 않는가?
  - log.info("요청: {}", requestDto)에서 requestDto.toString()이 무엇을 출력하는지 확인했는가?

□ 로그 레벨이 적절한가?
  - ERROR = 새벽에 깨워야 한다 (5xx, 시스템 장애)
  - WARN = 내일 출근해서 확인한다 (4xx, 재시도 초과, 지연 이상)
  - INFO = 정상 운영 기록 (비즈니스 이벤트, HTTP 경계)
  - DEBUG = 개발/디버깅용. 프로덕션에서 보통 OFF
```

> 로그 레벨의 핵심 판단 기준: **"이 로그가 찍히면, 누군가가 무엇을 해야 하는가?"**
> - 즉시 행동 필요 → ERROR
> - 조만간 확인 필요 → WARN
> - 아무 행동 불필요, 기록용 → INFO
> - 평소엔 보지 않음 → DEBUG
