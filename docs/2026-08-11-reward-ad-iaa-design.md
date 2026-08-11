# 앱인토스 리워드 광고(IAA) 도입 — 설계

> 🧭 세션 메타: model=claude-fable-5 · effort=high(에이전트 정의 frontmatter)
>
> 작성일 2026-08-11. 대상: **토스 미니앱 다음 릴리스**(v2 심사 제출됨 2026-08-11, 결과 대기 — 이 기능은 심사 통과 후 별도 릴리스).
> 이 문서는 콜드 구현 세션용 핸드오프다 — 필요한 맥락은 전부 이 안에 있고, 시그니처·구조는 전부 repo 실측이다.

## 0. 결론 요약

- **보상 = "밀린 하루 지우개"(부채 용서권)** — 7일 윈도우의 빠뜨린 날 하나의 잔여 부채를 광고 시청 1회로 0 처리. 서재 꾸미기 재화(먹이)는 **주지 않는다**(§2에서 기각 근거).
- **진입점 = 홈 탭 부채 문구 옆** — `carriedDebtSeconds > 0`일 때만 "광고 보고 하루 지우기" 버튼 노출. 리워드 광고 가이드라인의 "보상이 필요한 순간" 요건에 정확히 맞는다.
- **어뷰징 방어 = 서버측 상한(DB unique 제약)** — SDK에 서버사이드 보상 검증(SSV)이 **없음을 실측**했으므로 클라 콜백은 신뢰하지 않는 전제로 설계: 보상을 비금전·저가치로 잡고, 하루 1회·같은 날 중복 불가를 DB 제약으로 강제한다.
- **서버 변경 = 테이블 1개 + 기존 부채 계산에 4줄** — 부채가 유도값(무저장)이라 "용서한 날짜" 마킹 하나면 웹·미니앱 표시가 자동으로 함께 맞는다.
- PR 2개: **PR-A 서버**(마이그레이션 V62 + 서비스 + API + 부채 반영), **PR-B 미니앱 클라**(SDK 배선 + 홈 버튼).

---

## 1. 현황 — 실측 결과

### 1.1 SDK: 리워드 광고 API (공식 문서 실측, 2026-08-11)

출처: `developers-apps-in-toss.toss.im/documentation/common/monetization/iaa/interstitial-rewarded-ad.md`

- 전면형·리워드형이 **동일 API**를 쓰고, 타입은 콘솔에서 만든 **광고 그룹 ID(adGroupId)로 자동 결정**된다.
- `@apps-in-toss/web-framework`(미니앱은 `^3.0.2` 사용 중 — `miniapp/package.json` 실측)에서 제공:

```typescript
function loadFullScreenAd(params: {
  options: { adGroupId: string };
  onEvent: (data: { type: 'loaded' }) => void;
  onError: (err: unknown) => void;
}): () => void;

function showFullScreenAd(params: {
  options: { adGroupId: string };
  onEvent: (data: ShowFullScreenAdEvent) => void;
  onError: (err: unknown) => void;
}): () => void;

type ShowFullScreenAdEvent =
  | { type: 'requested' } | { type: 'show' } | { type: 'impression' }
  | { type: 'clicked' } | { type: 'dismissed' } | { type: 'failedToShow' }
  | { type: 'userEarnedReward'; data: { unitType: string; unitAmount: number } };
```

- 보상 시점 신호 = **`userEarnedReward` 클라이언트 이벤트뿐**. 문서 전체에 **서버사이드 보상 검증(SSV/S2S 콜백) 언급 없음** → 어뷰징 방어의 신뢰 경계가 여기서 갈린다(§4).
- 같은 adGroupId로는 **한 번에 하나만 미리 로드** 가능. 테스트용 리워드 adGroupId: `ait-ad-test-rewarded-id`(콘솔 등록 전 개발·검증 가능).
- 콘솔에서 광고 그룹 생성 시 **보상 이름·수량을 정확히 입력**해야 하고, 생성 후 구글 등록까지 최대 2시간.
- 가이드라인(수익화 가이드 실측): 인트로·로딩·팝업 모달 삽입 금지, 광고 위장·오클릭 유도 금지, 결제 흐름 내 삽입 금지, 동일 화면 동일 포맷 2개 금지, "광고 클릭 즉시 리워드" 구조 금지(시청 완료 보상은 OK — `userEarnedReward`가 그 시점), dead-end 금지. 위반 제재는 AdMob 정책 준용.

### 1.2 서재 꾸미기 재화 — 저장 없는 유도값 (핵심 실측)

`garden/FeedingService.java`가 단일 출처다. 재화(먹이)는 **테이블이 없다**:

```
EARN(무저장): 달성일 수(DailyQuotaCalculator.metDayCount) — 독서에서 유도, 위조 불가
BALANCE:      foodBalance = earned - spent(≥0) — 매 요청마다 유도
SPEND(저장):  AuthorAffection.feedCount++ 만 영속
```

- `foodBalance(user) = metDayCount(그날 읽은 초 ≥ 그날 목표인 날 수) − sum(feedCount)`.
- 즉 **"독서에서만 유도되고 위조 불가"가 이 재화의 설계 불변식**이다(javadoc에 명시). 광고 보상으로 먹이를 지급하려면 저장형 보너스 원장을 신설해 이 불변식을 깨야 한다.
- 캐릭터 해금(`AuthorCharacterUnlockCalculator`)·잔디·스트릭도 전부 독서 세션에서 유도 — 구매·지급형 재화는 코드베이스 어디에도 없다.
- **미니앱에는 정원(꾸미기) UI가 없다** — `miniapp/src/screens/Home.tsx` 주석 실측: "서재 관리·검색·정원은 웹이 본진". 미니앱 서재 탭(`Library.tsx`)은 책장 CRUD이지 꾸미기가 아니다.

### 1.3 부채(밀린 시간) — 역시 유도값, 용서 장치는 "7일 자동 소멸"뿐

`session/ReadingDebtService.java` + `WeeklyDebtCalculator.java` 실측:

- 부채는 저장하지 않고 완료 세션에서 매번 유도. 하루 부채 = `max(0, 그날 목표 − 그날 읽은 초)`, 활성 윈도우는 **오늘 포함 최근 7일**(그 이전은 자동 용서).
- 초과분은 backward-only 재분배(나중 날의 초과가 오래된 날 부채를 갚음, 선납 불가). 1분 미만 부채는 `forgivenSubMinute`로 이미 "용서" 메커니즘이 존재한다 — **이번 설계는 이 기존 메커니즘에 한 갈래를 더하는 것**이다.
- 소비처: `web/DashboardModel.computeLive()` → `carriedDebtSeconds`(빠뜨린 날 부채 합) — **웹 SSR과 미니앱 `/api/dashboard`가 같은 경로**를 쓴다. 여기 반영하면 두 채널 정합성은 자동.
- 미니앱 홈(`Home.tsx:114`)이 `carriedDebtSeconds > 0`일 때 "어제까지 밀린 시간 X 포함"을 표시 — 죄책감이 화면에 뜨는 바로 그 지점이다.
- 진단용 `WeeklyDebtTrace` / `DayDebtTrace`(record, 9필드)가 관리자 관찰성을 담당 — 계산 변경 시 trace 정직성을 유지해야 한다.

### 1.4 미니앱 구조

- 탭(App.tsx `TABS` 실측): **홈 · 서재 · 소셜 · 기록**(스토리는 소셜 탭 내). 라우터 없이 `view × tab` 상태.
- API 클라이언트 `miniapp/src/api.ts`: `request<T>(path, {method, body, query})` + Bearer 토큰(localStorage), 401 → `UnauthorizedError` → 재로그인. 서버 record DTO가 타입 단일 출처.
- SDK 호출은 `miniapp/src/toss.ts`에 한 겹 감싸는 패턴(현재 `tossLogin` 하나) — 광고도 같은 자리에 감싼다.
- 서버 인증: `/api/**` + Bearer는 미니앱 전용 stateless 체인(SecurityConfig)이 401을 처리 — 새 엔드포인트에 추가 인증 작업 없음(`MiniappGoalApiController` 패턴 그대로).
- 마이그레이션 최신 번호: **V61**(`V61__toss_miniapp_auth.sql`) → 이번 작업은 **V62**.

### 1.5 문제 정의

이미 알려진 thesis 미비점: **부채가 밀리면 죄책감 → 이탈**(스트릭 프리즈류 용서 장치 부재 — 7일 자동 소멸은 "7일간 죄책감 노출"이기도 하다). 타깃(인트린식 동기 약한 책 입문자)은 이 압박에 가장 취약하다. 리워드 광고는 eCPM이 가장 높은 포맷이면서 **자발적 시청**이라 압박이 없다 — "용서 장치"와 "수익화"를 한 기능으로 겸하는 것이 이 설계의 골자다.

---

## 2. 결정 1 — 보상: 무엇을 줄 것인가

| | A. 부채 용서권 (추천) | B. 서재 꾸미기 재화(먹이) | C. 병행(A+B) |
|---|---|---|---|
| thesis 정합 | **알려진 미비점(죄책감→이탈)을 직접 해소.** "습관 유지"를 돕는 보상 | 꾸미기 동기를 광고로 우회 — "독서해야 먹이를 얻는다"는 루프를 희석 | A의 가치 + B의 부작용 |
| 기존 코드 정합 | 부채 계산에 "용서" 메커니즘이 이미 있음(1분 미만 용서) — 한 갈래 추가 | **`FeedingService`의 "독서에서 유도, 위조 불가" 불변식을 깨야 함**(저장형 보너스 원장 신설) | 둘 다 |
| 보상 체감 위치 | 미니앱 홈에 즉시 보임(밀린 시간 감소) | **미니앱에 정원 UI가 없어** 보상이 안 보임(웹에서만 확인 가능) | — |
| 변경 규모 | 테이블 1 + 계산기 4줄 | 테이블 1 + FeedingService EARN 이원화 + (체감시키려면) 미니앱 정원 UI 신설 | 최대 |
| 어뷰징 시 피해 | 밀린 하루 표시가 지워질 뿐 — 잔디·스트릭·먹이 불변 | 먹이 무한 파밍 → 정 레벨 인플레 | — |

**추천: A(부채 용서권) 단독.** B는 세 겹으로 어긋난다(불변식 파괴·보상 비가시·thesis 희석). C는 첫 릴리스에 YAGNI — A 출시 후 시청률 데이터를 보고 재검토한다.

### 보상 수치·상한

- **1회 시청 = 빠뜨린 날 1일의 잔여 부채 전액 용서.** 대상 날짜는 서버가 고른다: 윈도우 내 과거 빠뜨린 날 중 **잔여 부채(`remainingSeconds`)가 가장 큰 날, 동률이면 최신 날**. 근거: 한 번의 시청으로 체감 감소(죄책감 해소)가 최대가 되게 — 가장 오래된 날은 어차피 곧 윈도우 밖으로 자동 소멸하므로 그걸 고르면 보상 가치가 하루 만에 증발한다.
- **일일 시청 상한 = 1회**(유저 타임존 기준). 근거: 빠뜨린 날은 최대 6개(오늘 제외)이고 하루 1회면 "광고 몰아보기로 전부 세탁"이 불가능하면서, 매일 챙겨 볼 유인은 남는다. 상한을 늘리는 건 수치 한 곳 수정이므로 데이터 보고 조정.
- **오늘의 부채는 용서 대상이 아니다** — 오늘은 아직 진행 중이고, "오늘 할 일을 광고로 지운다"는 습관 형성과 정면 충돌한다. 과거 빠뜨린 날만.
- 콘솔 광고 그룹의 보상 표기(필수 입력): 이름 "밀린 하루 지우개", 수량 1.

### 파생 효과의 경계 (파밍 차단 — 명시적 비연동)

용서는 **부채 표시에만** 작용한다. 아래는 전부 독서 세션에서 별도 유도되므로 자동으로 불변이며, 테스트로 못 박는다(§6):

- **먹이 EARN 불변**: `FeedingService.computeMetDayCount`는 waiver를 반영하지 않는다 → 광고로 먹이 파밍 불가.
- **잔디·스트릭·성장 단계 불변**: 세션 기반 유도 — 용서한 날이 "읽은 날"로 둔갑하지 않는다.
- 용서는 "그날을 달성 처리"가 아니라 "그날의 빚 독촉을 멈춤"이다 — UX 문구도 이 결을 유지한다.

---

## 3. 결정 2 — 진입점·배치

**홈 탭, 부채 문구("어제까지 밀린 시간 X 포함") 바로 아래 버튼 1개.** `Home.tsx:114`의 `carriedDebtSeconds > 0` 조건 블록 안이다.

- 노출 조건(모두 AND): ① `carriedDebtSeconds > 0` ② 서버가 준 `debtWaiverAvailable === true`(오늘 미사용 + 빠뜨린 날 존재) ③ 클라 빌드에 `adGroupId` 설정됨(§5.2 — 미설정이면 기능 전체가 조용히 꺼지는 config-gate, 웹 `AdsProperties`의 클라판).
- 문구: 버튼 "광고 보고 밀린 하루 지우기" — "광고"를 명시해 광고 위장 금지 조항을 지킨다. 시청 완료 후: "어제 밀린 N분을 지웠어요" 한 줄.
- 대안 비교:
  - **전용 섹션/탭**: 부채 없는 유저에게도 광고 진입점이 상시 보임 — 입문자에게 "광고 보는 앱" 인상, 기각.
  - **측정 종료 직후(stop 응답 화면)**: 방금 목표를 향해 읽은 직후에 광고를 들이밀면 성취 순간을 광고가 가로챈다, 기각.
  - **홈 부채 문구 옆(채택)**: 보상이 필요한 순간(죄책감이 뜨는 지점)에만 나타나고, 부채가 없으면 광고의 존재 자체가 안 보인다 — 입문자 압박 0.
- 가이드라인 대조: 인트로·로딩·팝업 아님 ✓ / 결제 흐름 아님(미니앱에 결제 없음) ✓ / 동일 화면 동일 포맷 1개 ✓ / dead-end 아님(버튼 무시하고 모든 기능 사용 가능) ✓ / 클릭 즉시 보상 아님(`userEarnedReward` = 시청 완료 시점) ✓.
- **배너·전면형은 이번 범위 밖**(비목표 §8) — 강제 노출 포맷은 압박에 취약한 타깃과 충돌하고, 배너는 eCPM 최저라 thesis 리스크 대비 수익이 안 나온다.

---

## 4. 결정 3 — 어뷰징 방어: 신뢰 경계

**전제(실측): SSV가 없다.** 서버는 "광고를 실제로 봤는지"를 검증할 수단이 없고, `POST /api/miniapp/debt-waiver` 호출은 클라이언트 주장일 뿐이다. 따라서:

1. **클라 콜백을 신뢰 경계 밖에 둔다** — 서버는 "광고 시청"이 아니라 "지급 요청"만 안다고 가정하고 설계한다.
2. **최악 시나리오를 상한으로 캡한다**: 유저가 광고 없이 API를 직접 때려도 얻는 것은 "하루 1회, 밀린 하루 표시 지우기"뿐이다. 금전 가치가 없고(광고 수익 정산은 토스 애즈의 impression 집계 기준 — 우리 지급 기록과 무관), 잔디·스트릭·먹이·랭킹에 영향이 없다(§2). **피해 상한이 낮으므로 nonce·서명 토큰 같은 추가 장치는 과잉이다** — 넣지 않는다.
3. **상한은 DB 제약으로 강제한다**(애플리케이션 검사 + 제약 이중화, 동시 요청 race도 제약이 잡는다):
   - `UNIQUE(user_id, granted_on)` — 하루(유저 TZ) 1회.
   - `UNIQUE(user_id, waived_date)` — 같은 날짜 중복 용서 방지(용서된 날은 빠뜨린 날 목록에서 빠지므로 자연 성립하지만, 제약으로도 박는다 — 공짜다).
4. **대상 날짜는 서버가 고른다** — 클라는 날짜를 보내지 않는다(요청 body 없음). 조작 표면 자체를 제거.
5. 향후 앱인토스가 SSV를 제공하면 지급 API 앞단에 검증 한 단계를 끼우면 된다 — 현 구조에서 교체점은 컨트롤러 한 곳.

---

## 5. 변경 범위

### 5.1 서버 (PR-A)

**① 마이그레이션 `src/main/resources/db/migration/V62__reading_goal_waiver.sql`**

V61 관례를 따른다(소문자 SQL · `uk_`/`fk_` 접두 · `datetime(6)`):

```sql
-- V62 — 리워드 광고 보상: 밀린 하루 용서(waiver). 부채는 유도값이라 "용서한 날짜" 마킹만 저장한다.
-- uk_goal_waiver_grant(user_id, granted_on)가 일일 1회 상한, uk_goal_waiver_date가 같은 날 중복 용서 방지 —
-- 상한을 애플리케이션 검사가 아니라 DB 제약으로 강제한다(동시 요청 race 포함, 설계 §4).

create table reading_goal_waiver (
    id          bigint not null auto_increment,
    user_id     bigint not null,
    waived_date date   not null,  -- 용서된 날(유저 TZ 일자)
    granted_on  date   not null,  -- 지급된 날(유저 TZ 오늘) — 일일 상한의 키
    created_at  datetime(6) not null,
    updated_at  datetime(6) not null,
    primary key (id),
    constraint uk_goal_waiver_date  unique (user_id, waived_date),
    constraint uk_goal_waiver_grant unique (user_id, granted_on),
    constraint fk_goal_waiver_user  foreign key (user_id) references users (id)
);
```

⚠️ 유저 삭제 purge에 FK 자식 정리 추가 필수(T-029 재발 방지): `user/AccountService.java`의 purge 절차(167행 부근, `apiTokenRepository.deleteByUser` 옆)에 `goalWaiverRepository.deleteByUser(user)`를 추가한다. H2 통합 테스트로 못 박는다(§6).

**② 엔티티·리포지토리 `session/ReadingGoalWaiver.java` · `ReadingGoalWaiverRepository.java`**

```java
@Entity @Table(name = "reading_goal_waiver")
public class ReadingGoalWaiver extends BaseTimeEntity {
    // user(FK), waivedDate, grantedOn — 생성 후 불변, 정적 팩토리 create(user, waivedDate, grantedOn)
}

public interface ReadingGoalWaiverRepository extends JpaRepository<ReadingGoalWaiver, Long> {
    boolean existsByUserAndGrantedOn(User user, LocalDate grantedOn);
    List<ReadingGoalWaiver> findByUserAndWaivedDateGreaterThanEqual(User user, LocalDate from); // 윈도우 조회
    void deleteByUser(User user); // purge용
}
```

**③ 부채 계산 반영 — `WeeklyDebtCalculator` + `DayDebtTrace` + `ReadingDebtService`**

계산기는 순수 함수 유지: `Set<LocalDate> waivedDates` 파라미터를 받는 trace 오버로드 하나 추가, 기존 public 시그니처는 `Set.of()` 위임으로 전부 보존(호출부 무변경).

```java
// WeeklyDebtCalculator — 새 오버로드
public static WeeklyDebtTrace computeTrace(Map<LocalDate, Long> secondsByDate,
                                           Map<LocalDate, Long> goalByDate,
                                           Set<LocalDate> waivedDates,
                                           LocalDate today) { ... }

// computeTraceInternal 1단계 루프 끝에 4줄 — 기존 forgivenSubMinute 처리와 같은 자리·같은 결:
if (i < todayIdx && waivedDates.contains(d)) {
    waived[i] = true;
    remaining[i] = 0;   // 부채만 소거. rawSurplus는 그대로 → 그날 초과분 뱅킹 불변
}
```

- 오늘(`i == todayIdx`)은 대상 아님 — waived set에 오늘이 섞여 들어와도 무시된다(방어).
- `DayDebtTrace`에 `boolean waived` 필드 1개 추가(9→10필드) — 진단 뷰에서 "부채가 있었는데 0"이 버그로 보이지 않게 정직하게 남긴다. 기존 생성 지점(계산기 1곳 + 테스트) 수정.
- `ReadingDebtService.weeklyDebtTrace(user, asOf)`에서 리포지토리로 윈도우 시작일 이후 waiver를 조회해 넘긴다(생성자 주입 1개 추가):

```java
Set<LocalDate> waived = waiverRepository
        .findByUserAndWaivedDateGreaterThanEqual(user, effectiveAsOf.minusDays(WeeklyDebtCalculator.WINDOW_DAYS - 1))
        .stream().map(ReadingGoalWaiver::getWaivedDate).collect(Collectors.toSet());
```

이 한 곳이 유일한 배선점이다 — `DashboardModel.computeLive`(웹 SSR + 미니앱 `/api/dashboard` + start/stop 응답)가 전부 `ReadingDebtService.weeklyDebt`를 경유하므로 **웹·미니앱 표시가 자동으로 함께 줄어든다**(§8 웹 채널 정합성의 근거).

**④ 지급 유스케이스 `session/GoalWaiverService.java`**

```java
@Service
public class GoalWaiverService {

    /** @return 용서된 날짜와 소거된 부채(초) */
    @Transactional
    public WaiveResult waive(User user) {
        LocalDate today = debtService.today(user);
        if (waiverRepository.existsByUserAndGrantedOn(user, today)) {
            throw new IllegalStateException("오늘은 이미 사용했어요. 내일 다시 지울 수 있어요.");   // → 409
        }
        WeeklyDebtTrace trace = debtService.weeklyDebtTrace(user, today);
        DayDebtTrace target = trace.days().stream()
                .filter(d -> !d.isToday() && d.remainingSeconds() >= WeeklyDebtCalculator.MIN_MISSED_DEBT_SECONDS)
                .max(Comparator.comparingLong(DayDebtTrace::remainingSeconds)
                        .thenComparing(DayDebtTrace::date))                      // 최대 부채, 동률이면 최신
                .orElseThrow(() -> new IllegalArgumentException("지울 밀린 날이 없어요."));  // → 400
        waiverRepository.save(ReadingGoalWaiver.create(user, target.date(), today));
        return new WaiveResult(target.date(), target.remainingSeconds());
    }

    public record WaiveResult(LocalDate waivedDate, long waivedSeconds) {}
}
```

동시 요청(두 탭)은 `uq_goal_waiver_grant` 위반 → `DataIntegrityViolationException` → 컨트롤러에서 409로 매핑.

**⑤ API `web/api/` — 엔드포인트 `POST /api/miniapp/debt-waiver` (Bearer, body 없음)**

`TimerState` record와 `buildTimerState(user)`가 있는 `DashboardApiController`에 추가한다(응답 조립 재사용 — 별도 컨트롤러를 만들면 private `buildTimerState`를 추출해야 해서 diff가 커진다).

```java
/** @return 200 {waivedDate, waivedSeconds, timer} / 400 지울 날 없음 / 401 미인증 / 409 오늘 이미 사용 */
@PostMapping("/api/miniapp/debt-waiver")
public WaiveResponse waiveDebt(Principal principal) {
    User user = currentUserService.resolve(principal);
    GoalWaiverService.WaiveResult result = goalWaiverService.waive(user);
    return new WaiveResponse(result.waivedDate(), result.waivedSeconds(), buildTimerState(user));
}
public record WaiveResponse(LocalDate waivedDate, long waivedSeconds, TimerState timer) {}
```

예외 매핑: `IllegalStateException`·`DataIntegrityViolationException` → 409(평문 메시지 — 미니앱 `ApiError`가 그대로 표시), `IllegalArgumentException` → 400. `/api/**` Bearer 체인이 401을 이미 처리(추가 작업 없음).

**⑥ `TimerState`·`DashboardResponse`에 필드 1개 추가 — `boolean debtWaiverAvailable`**

계산 주체는 `GoalWaiverService`에 조회 메서드로 둔다(컨트롤러에 리포지토리를 직접 주입하지 않는다):

```java
/** 오늘 지급 가능 여부 — 미니앱 버튼 노출 조건. 밀린 날 존재 + 오늘 미사용. */
public boolean availableFor(User user) {
    // WeeklyDebt의 빠뜨린 날 목록(toWeeklyDebt 세 번째 인자로 조립되는 missed 리스트)이 비어 있지 않고,
    return !debtService.weeklyDebt(user).missedDays().isEmpty()   // 접근자명은 WeeklyDebt record 실물 기준
            && !waiverRepository.existsByUserAndGrantedOn(user, debtService.today(user));
}
```

`DashboardApiController.buildTimerState(user)`와 대시보드 응답 조립에서 이 값을 채운다. JSON 필드 추가는 구버전 미니앱에 하위호환(무시됨). start/stop/waive 응답에 같이 실려 버튼 노출/숨김이 재조회 없이 갱신된다. 웹 SSR(`DashboardModel`)에는 넣지 않는다 — 웹에 버튼이 없다.

### 5.2 미니앱 클라 (PR-B)

**① `miniapp/src/toss.ts` — SDK 래핑(기존 `tossLogin` 패턴)**

```typescript
import { loadFullScreenAd, showFullScreenAd, TossAuth } from '@apps-in-toss/web-framework';

/** 콘솔 발급 리워드 광고 그룹 ID. 빈 값이면 광고 기능 전체가 꺼진다(config-gate). */
export const REWARD_AD_GROUP_ID: string = import.meta.env.VITE_REWARD_AD_GROUP_ID ?? '';

/**
 * 리워드 광고 1회 시청 — resolve(true)=보상 조건 충족, resolve(false)=끝까지 안 봄.
 * userEarnedReward가 dismissed보다 먼저 올 수 있어 플래그로 들고 있다가 dismissed에 확정한다.
 */
export function watchRewardAd(adGroupId: string): Promise<boolean> {
  return new Promise((resolve, reject) => {
    let rewarded = false;
    loadFullScreenAd({
      options: { adGroupId },
      onError: reject,
      onEvent: (e) => {
        if (e.type !== 'loaded') return;
        showFullScreenAd({
          options: { adGroupId },
          onError: reject,
          onEvent: (se) => {
            if (se.type === 'userEarnedReward') rewarded = true;
            else if (se.type === 'dismissed') resolve(rewarded);
            else if (se.type === 'failedToShow') reject(new Error('광고를 표시하지 못했어요'));
          },
        });
      },
    });
  });
}
```

⚠️ 이벤트 순서(`userEarnedReward` ↔ `dismissed`)는 문서에 보장이 없다 — **샌드박스에서 테스트 adGroupId(`ait-ad-test-rewarded-id`)로 실측**이 머지 게이트다(§6 검증 계획). 사전 로드 최적화(버튼 노출 시 미리 load)는 YAGNI — 클릭 시 로드로 시작하고, 로드 지연이 실측으로 거슬리면 후속.

**② `miniapp/src/api.ts` — 호출 함수 + 타입 필드**

```typescript
export interface TimerState { /* 기존 필드… */ debtWaiverAvailable: boolean; }

export interface WaiveResponse { waivedDate: string; waivedSeconds: number; timer: TimerState; }
export const waiveDebt = (): Promise<WaiveResponse> => request('/api/miniapp/debt-waiver', { body: {} });
```

**③ `miniapp/src/screens/Home.tsx` — 부채 블록에 버튼**

`carriedDebtSeconds > 0` 블록(114행) 안에 조건 노출. 기존 `busy`·`fail` 재사용:

```tsx
{dashboard.debtWaiverAvailable && REWARD_AD_GROUP_ID !== '' && (
  <Button variant="weak" size="small" style={{ marginTop: 8 }} disabled={busy}
    onClick={() => {
      setBusy(true); setError(null);
      watchRewardAd(REWARD_AD_GROUP_ID)
        .then((rewarded) => (rewarded ? waiveDebt().then((r) => { onTimerChange(r.timer); setWaived(r.waivedSeconds); }) : undefined))
        .catch(() => setError('광고를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.'))
        .finally(() => setBusy(false));
    }}>
    광고 보고 밀린 하루 지우기
  </Button>
)}
```

- `rewarded === false`(중간 이탈)면 **지급 API를 호출하지 않는다** — 조용히 원상태.
- 성공 시 `onTimerChange(r.timer)`로 부채·버튼이 즉시 갱신되고, "N분을 지웠어요" 한 줄(`waived` 로컬 상태)을 잠깐 보여준다.
- 광고 로드 실패(`onError`)는 이 화면의 에러 문구로 — 재시도는 유저가 버튼을 다시 누르는 것으로 충분.

### 5.3 웹 채널 — 정합성만 (변경 없음)

리워드 광고는 토스 애즈 SDK가 미니앱 WebView에서만 동작하므로 **미니앱 전용**이다. 웹에는 아무 UI도 넣지 않는다. 정합성은 §5.1-③에서 구조적으로 해결된다: 용서가 `ReadingDebtService` 단일 경로에 반영되므로 웹 대시보드의 밀린 시간 표시도 같은 값으로 줄어든다. 웹 유저 입장에서는 "미니앱에서 하루를 지우면 웹에서도 지워져 있다" — 별도 동기화 코드 없음. 웹의 AdSense 배너(`AdsProperties`)와는 완전 별개 시스템으로 상호 간섭 없음.

---

## 6. 엣지케이스 · TDD 계획

**RED → GREEN 순서 필수** — 각 단계에서 ① 실패 테스트 먼저 작성 → ② 실행해 Red 확인(구현 부재로 인한 실패인지 눈으로) → ③ 최소 구현 → ④ 재실행해 Green 확인. 테스트는 H2(기존 `src/test/resources/application.properties`), FK·unique 제약 검증은 flush 강제.

### PR-A 서버

**RED 1 — 계산기·부채 반영** (`WeeklyDebtCalculatorTest` 추가 케이스 + `ReadingDebtServiceTest`):
- waived 날짜의 잔여 부채가 0, `DayDebtTrace.waived == true`.
- **waived 날의 초과분 뱅킹 불변**: 목표 초과 달성한 날을 waive해도 그 초과분이 다른 날 부채를 갚는 동작 유지(경계: waive는 deficit만 소거).
- waived set에 오늘 날짜가 들어와도 오늘 부채는 불변.
- waived set에 윈도우 밖 날짜가 들어와도 무해(no-op).
- 기존 오버로드(waiver 없는 호출) 전부 기존 결과 불변 — 회귀 앵커.

**RED 2 — 지급 유스케이스** (`GoalWaiverServiceTest`, H2 통합):
- 빠뜨린 날 2개(부채 30분·60분) → waive가 **60분 날**을 고르고 `carriedDebtSeconds`가 정확히 그만큼 감소.
- 부채 동률이면 최신 날 선택.
- 빠뜨린 날 없음(전부 달성) → `IllegalArgumentException`.
- **일일 상한**: 같은 날 2회째 waive → `IllegalStateException`. 서비스 검사를 우회해 직접 2행 insert + flush → unique 제약 위반 실측(돌연변이 방어 — 애플리케이션 검사만 지워도 DB가 막는지).
- **중복 용서**: 같은 waived_date 2행 insert + flush → 제약 위반.
- **파밍 차단**: waive 전후 `FeedingService.foodBalance` 불변 + `ContributionGraph` 스트릭 불변.
- **purge**: waiver 가진 유저 삭제가 FK 위반 없이 성공(flush 강제 — T-029 계열 필수 테스트).

**RED 3 — API** (기존 `MiniappGoalApiController` 테스트 패턴):
- 미인증(Bearer 없음) → 401.
- 성공 → 200 + `{waivedDate, waivedSeconds, timer}`, `timer.debtWaiverAvailable == false`(방금 썼으므로).
- 오늘 이미 사용 → 409 평문 메시지 / 지울 날 없음 → 400.
- `/api/dashboard` 응답에 `debtWaiverAvailable` — 부채 있음+미사용=true, 부채 0=false, 오늘 사용=false 3분기.

**GREEN**: V62 + 엔티티 + 계산기 오버로드 + 서비스 + 컨트롤러 최소 구현 → 전체 `./gradlew test` GREEN 확인.

### PR-B 미니앱

**RED 1** (vitest, 기존 `app.test.tsx`의 SDK 모듈 mock 패턴 — `vi.mock('@apps-in-toss/web-framework')`):
- `watchRewardAd` 단위: `loaded → show → userEarnedReward → dismissed` 시퀀스 → `true` / `dismissed`만 → `false` / `failedToShow`·`onError` → reject. (이벤트 순서 변형 `dismissed` 직전 `userEarnedReward` 케이스 포함 — 플래그 방식 검증.)
- Home 렌더: `debtWaiverAvailable: false` 또는 adGroupId 빈 값 → 버튼 미노출 / true+설정됨 → 노출.
- 클릭 흐름: rewarded → `waiveDebt` 호출 + 타이머 갱신 / not rewarded → **API 미호출** / 광고 에러 → 에러 문구 + API 미호출.

**GREEN**: toss.ts·api.ts·Home.tsx 구현 → vitest GREEN.

**머지 전 실기기 게이트** (프론트 검증 규칙 — 헤드리스만으로 끝내지 않는다):
- 샌드박스에서 `VITE_REWARD_AD_GROUP_ID=ait-ad-test-rewarded-id`로 실제 광고 로드→시청→보상 이벤트 순서 실측(§5.2-① 가정 검증). 순서가 다르면 `watchRewardAd` 한 함수만 수정.
- 시청 완료→서버 지급→홈 부채 감소까지 왕복 1회 육안 확인.

---

## 7. 규모 · 리스크 · PR 분할

**규모**: 서버 — 신규 파일 4(마이그레이션·엔티티·리포지토리·서비스) + 수정 4(계산기·trace record·ReadingDebtService·DashboardApiController), 테스트 ~20건. 클라 — 수정 3(toss.ts·api.ts·Home.tsx), 테스트 ~10건. 합계 소~중형.

**PR 분할** (연쇄 — PR-A 머지 확인 후 PR-B 분기, T-096):
1. **PR-A `feat/reward-debt-waiver-server`**: V62 + 도메인 + API + 부채 반영 + 테스트. 클라 없이도 무해(엔드포인트만 잠들어 있음).
2. **PR-B `feat/miniapp-reward-ad`**: SDK 배선 + 홈 버튼 + vitest + 샌드박스 실측 결과 첨부.

**리스크**:
- **SDK 이벤트 순서 미보장**(중): 문서에 순서 계약이 없다 — 샌드박스 실측이 머지 게이트(§6). 교체점은 `watchRewardAd` 1곳.
- **SSV 부재**(수용): API 직접 호출로 광고 없이 지급받을 수 있으나 피해 상한이 "하루 1회 부채 표시 소거"로 캡됨(§4). 수익 정산과 무관하므로 금전 리스크 0.
- **회귀 — 부채 계산**(중): `WeeklyDebtCalculator`는 홈 카운트다운·웹 대시보드·admin 진단이 모두 걸린 코어다. 완화: 기존 시그니처 무변경 + waiver 없는 경로 결과 불변을 회귀 테스트로 앵커(§6 RED 1 마지막 항목).
- **용서와 스트릭의 개념 혼동**(저): 용서해도 잔디는 비어 있다 — "지우개는 빚만 지운다"를 완료 문구에 반영해 기대 불일치를 막는다.
- **미니앱 릴리스 타이밍**(외부): v2 심사 결과 대기 중 — PR-B의 스토어 반영은 심사 통과 후 다음 릴리스 사이클. 서버 PR-A는 먼저 머지·배포해도 무해.
- **광고 그룹 등록 지연**(외부): 콘솔 생성 후 구글 등록까지 최대 2시간 — 운영 점등은 등록 완료 후 env 주입으로(§9). 그 전까지 버튼은 config-gate로 미노출.

---

## 8. 비목표 (YAGNI — 이번에 안 하는 것)

- **배너·전면형 광고**: 강제 노출은 압박 취약 타깃과 충돌. 리워드 데이터를 본 뒤에도 도입하려면 별도 설계.
- **먹이·꾸미기 재화 지급**(옵션 B): §2 기각 근거. "위조 불가" 불변식은 지킨다.
- **사전 로드 최적화**: 클릭 시 로드로 시작. 실측으로 지연이 거슬리면 후속.
- **하루 상한 2회+·용서 대상 날짜 선택 UI**: 상한 1회·서버 자동 선택으로 시작 — 조작 표면 최소.
- **웹 채널 리워드**: 토스 애즈는 미니앱 전용 — 기술적으로 불가.
- **nonce/서명 지급 토큰**: SSV 없는 상황에서 클라 발 nonce는 보안 연극이다 — 피해 캡(§4)으로 충분.
- **admin 진단 뷰의 waiver 별도 표기**: `DayDebtTrace.waived` 필드까지만. 뷰 개선은 필요가 생기면.

---

## 9. 운영 체크리스트 (코드 밖 — 릴리스 전)

1. 앱인토스 콘솔에서 **리워드 광고 그룹 생성** — 보상 이름 "밀린 하루 지우개" / 수량 1 정확히 입력(가이드라인 필수). 생성 후 구글 등록 최대 2시간 대기.
2. 발급된 adGroupId를 미니앱 빌드 env `VITE_REWARD_AD_GROUP_ID`로 주입(빌드타임 — 미니앱 배포 파이프라인의 env 설정 위치에 등록). 그 전 빌드는 버튼 미노출로 안전.
3. 파트너 정산 정보는 등록 완료 상태(설계 범위 밖). **간이과세자 현금영수증 자체 발행 의무** — 월 정산 지급 시 처리(정산: 사업자 단위 월 정산·익월 말일 지급·5,000원 이하 이월).
4. 릴리스 후 1주: 시청률(버튼 노출 대비 완료)·`reading_goal_waiver` 행 증가 추이 관찰 — 상한 조정·옵션 C 재검토의 입력 데이터.
