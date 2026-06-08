# 책BTI — 독서 성향 분석 ("책장 기반 MBTI") · 설계 문서 (확정)

> **이름**: **책BTI** = **책 + MBTI**. "내 책장이 말하는 나의 독서 성향"을 MBTI 설명문처럼 가볍게 보여주는 기능(사용자 명명 2026-06-07). 기능 코드/도메인 용어로는 *독서 성향(reading personality)* 을, 사용자 노출 브랜드로는 *책BTI* 를 쓴다.
>
> **상태**: **설계 확정 ✅ 2026-06-07.** v1 범위·장르 적재·LLM 공급자·저장 모델·프라이버시 경계까지 합의 완료 → 이제 TDD 착수 가능.
> (이전: 방향 합의 초안 ⏳ 2026-06-05 → 2026-06-07 코드 현황 확인 후 미결 🟡 전부 확정.)
> **왜 설계 먼저였나**: 결과가 (후속 단계에서) **타인에게 노출**되고 **외부 LLM 호출 비용**이 붙으므로, 노출 경계·캐시 정책을 코드에 굳히기 전에 합의가 필요했다.
>
> 관련: [plan.md](../plan.md) §독서 성향 분석 · [sns-design.md](sns-design.md) §3.5(가시성 경계) · [learning-notes.md](learning-notes.md) **N-037**(저장 대상=기존 데이터 해석).

---

## 0. 한눈에 — 확정된 방향

| 항목 | 결정 | 확정 | 근거 |
|---|---|---|---|
| 출력 형태 | **자유 서술문**("이 사람은 ~한 독자다", MBTI 설명문체) | ✅ | 사용자 정의 — 고정 16유형 아님 |
| AI 사용 여부 | **쓴다 (서술 생성에)** | ✅ | 책→성향은 퍼지한 해석, 자유 자연어 = LLM 강점 |
| **AI의 역할 경계** | **해석·서술만**. 사실 집계(숫자 세기)는 코드가 | ✅ | 환각·비용↓, 품질↑ |
| 입력 | **압축된 "독서 프로필"**(코드가 책장에서 집계) | ✅ | raw 통째로 던지지 않음 |
| **장르/출간연도** | **적재 추가**(알라딘 `categoryName`·`pubDate` 매핑 + Book 컬럼 + 백필) | ✅ 2026-06-07 | 장르는 성향의 핵심 축인데 현재 DB·파서 모두 없음(§1.5) |
| 출력 2겹 | 설명문 **+ 짧은 태그/점수** — **둘 다 생성·저장, v1은 태그 비노출** | ✅ 2026-06-07 | ②매칭 대비 싼 보험(나중에 안 갈아엎기) |
| **v1 범위** | **본인용·비노출 먼저**(전체 책 기반, 나만 봄) | ✅ 2026-06-07 | 밀도 의존 0·누출 0·PRIVATE 기본이어도 유용(§1.5) |
| LLM 공급자 | **Gemini Flash**(무료 티어 검증) + **포트 추상화**로 교체 가능 | ✅ 2026-06-07 | 호출당 1원 미만, 무료로 검증, 락인 회피 |
| 키 관리 | **ECS env 주입**(`BOOKTIMER_LLM_API_KEY`, repo 미커밋) | ✅ 2026-06-07 | 기존 `BOOKTIMER_ADMIN_LOGIN_IDS` 패턴 |
| 폴백 | LLM 실패/지연 시 **사실(독서 프로필)만 표시 + "잠시 후 다시 분석"** | ✅ 2026-06-07 | 외부 의존 격리(알라딘 폴백과 동일 정신) |
| 갱신 | **결과 저장·캐시**, 책장 의미변화 or "다시 분석"시만 재생성 | ✅ | 비용↓ + 결과 일관성(공유 캡처) |
| 저장 모델 | **전용 테이블 신규**(Flyway, 번호는 머지 직전) | ✅ 2026-06-07 | 파생 캐시, 일관성·공유 캡처 필요 |
| 프라이버시 | v1 본인용(전체 책, 비노출) / **공개·매칭은 후속**(PUBLIC 책 기반 재생성) | ✅ 2026-06-07 | sns §3.5 — 비공개 간접 누출 차단 |
| 정확도 기대치 | **"MBTI처럼 가볍게" 고지문** 명시 | ✅ | 과신 금지 포지셔닝 |

---

## 1. 사용자 정의 컨셉

> 사용자가 직접 정의(2026-06-05). 이후 변경 시 이 절 갱신.

- 누군가가 **타인의 책장을 보고**(후속 단계) "이 사람은 이런 성향의 독자다~" 하는 **설명문**을 얻는다. MBTI 설명문처럼 **읽는 재미**가 핵심. **v1은 우선 본인이 자기 성향을 본다.**
- 단, MBTI식 **고정 유형(16종)으로 정형화하지는 않는다** — 책으로 사람 성향을 깎기엔 퍼지하므로, **자유 서술**이 더 맞다고 판단.
- 그래서 **AI(LLM)로 서술문을 생성**한다.
- 🎯 **이 기능의 본심은 "분석 정확도"가 아니라 §전략 엔진 B(사람 잇는 재미)의 연료** — 정체성 배지·대화 물꼬·매칭 씨앗(plan.md §독서 성향 분석).

---

## 1.5. 코드 현황 확인 (2026-06-07) — 설계를 바꾼 두 발견

> 착수 전 실제 `Book`/`AladinBookSearchClient`/`visibility`를 확인한 결과, 초안 §3 가정과 어긋나는 점이 둘 나와 v1 범위를 이걸로 확정했다.

### ① 장르·출간연도가 우리 데이터에 없다 → **적재를 새로 깐다**
- `Book` 컬럼: `title·author·isbn13·coverUrl·publisher·purchaseLink·status·clickCount·visibility` — **장르(category)도 `pubDate`도 없음.**
- `AladinBookSearchClient.parse()`는 알라딘 응답에서 6필드(title/author/isbn13/cover/publisher/link)만 매핑 — 알라딘이 주는 **`categoryName`·`pubDate`를 버리고 있다.**
- **결정**: 적재 추가(아래 §10 Phase 1). 장르는 "책장 MBTI"의 심장이라 빼면 v1이 빈약. 신규 책은 파서 매핑으로, 기존 책은 ISBN으로 알라딘 ItemLookUp 백필.

### ② 책 공개는 기본 PRIVATE opt-in → **"공개용 분석"은 대부분 텅 빈다**
- `Book.visibility` 기본값 `PRIVATE`. 대다수 책이 비공개라, "타인 노출 결과는 PUBLIC 책만"(§7) 원칙대로 만들면 **공개용 분석 입력이 거의 0** → 쓸모없는 성향.
- **결정**: v1은 **본인용(전체 책 기반)·비노출**. 나만 보므로 누출 위험 0, PRIVATE 기본이어도 유용, 밀도 의존 0. 공개 노출·매칭은 PUBLIC-기반으로 따로 생성하는 **후속 단계**.

---

## 2. 핵심 설계 결정 — "AI를 쓰냐"가 아니라 "AI한테 뭘 시키냐"

성향 도출을 두 역할로 쪼갠다. 이 분리가 비용·환각·품질을 동시에 잡는다.

| 단계 | 담당 | 내용 | 성격 |
|---|---|---|---|
| ① **사실 집계** | **코드/DB** | 장르 분포·완독률·자주 읽은 저자·책별 누적 시간·다독/정독 경향 등 | 결정적·$0·테스트 가능 |
| ② **해석 + 서술** | **LLM** | ①의 사실을 받아 자연어 성향 설명문 + 태그 생성 | 퍼지 종합·자연어 = AI 강점 |

```
책장(book + reading_session)
   → [코드가 "독서 프로필"(사실)로 압축]
   → [LLM(Gemini Flash)이 그 사실을 해석해 MBTI식 설명문 + 태그 생성]
   → 결과 저장(전용 테이블, 캐시)
   → v1: 본인 화면에 노출
```

> 요지: **고정 축 판정을 건너뛰는 것이지 AI를 빼는 게 아니다.** 반대로, **AI한테 숫자를 세게 하지 않는다**(비싸고 부정확). 코드가 센 사실을 AI가 해석·서술한다.

---

## 3. 입력 — "독서 프로필"(코드가 집계하는 사실)

LLM에 raw 행을 통째로 던지지 않는다. 토큰↓·환각↓·신호 선명. **확정된 사실 목록**:

### 지금 데이터로 즉시 집계 가능 (적재 변경 0)
- **권수·완독**: 보유 N권, 완독 M권, 완독률 (`book.status` 분포)
- **상태 분포**: WANT_TO_READ / READING / FINISHED 비중
- **저자 편향**: 최다독 저자, 저자 다양성 (`book.author`)
- **시간 분포**: 책별 누적 시간, 평균 세션 길이(정독↔다독), 총 독서 시간 (`reading_session.duration_seconds`)
- **다양성 vs 집중**: (저자 기준 편식도 — 장르 적재 전까지 저자로 근사)

### 장르 적재(§10 Phase 1) 후 추가
- **장르/카테고리 비중**: 알라딘 `categoryName` → 상위 장르 분포·편식도(잡식↔편식)
- **신/구**: 알라딘 `pubDate` → 출간연도 분포(최신작↔고전)

> 콜드스타트(책 2~3권)면 신호 부족 → §6 처리.

---

## 4. LLM 호출 — Gemini Flash (포트 추상화)

- **추상화**: `BookSearchClient`처럼 **포트 인터페이스**(예 `ReadingPersonalityNarrator`/`LlmClient`)를 두고 Gemini 어댑터를 구현 — 공급자 락인 회피, 가짜 구현으로 테스트.
- **프롬프트 골격**: "다음은 한 독자의 책장 요약 사실이다. **주어진 사실만 근거로**, 책을 지어내지 말고, MBTI 설명문처럼 이 사람의 독서 성향을 한 문단으로 서술하라. 더불어 비교용 태그 3~5개를 함께 내라." + 독서 프로필(JSON) 주입.
- **그라운딩**: "주어진 사실만" 명시 → 환각(없는 책·없는 장르 발명) 억제.
- **모델/비용**: Gemini Flash(무료 티어로 검증). 입력 압축 프로필 + 한 문단 + 태그 → **호출당 1원 미만**.
- **키 관리**: `BOOKTIMER_LLM_API_KEY` ECS env 주입(repo 미커밋, 기존 ADMIN 시드 패턴).
- **일관성**: temperature 낮게 + **결과 저장**(매 새로고침마다 글 바뀌면 안 됨 — 캡처·공유).
- **폴백**: 호출 실패/지연 → 설명문 없이 **사실(독서 프로필)만 표시 + "잠시 후 다시 분석"**. 외부 API 장애가 화면을 깨지 않게 격리(알라딘 검색 폴백과 동일).

---

## 5. 출력 — 2겹 (설명문 + 비교 핸들), 둘 다 저장

LLM이 **한 번의 호출로 JSON 구조화 출력**을 낸다:

1. **설명문(narrative)** — 사람이 읽는 MBTI식 문단. (v1 본인 노출)
2. **태그/점수(tags)** — 예 `잡식·완독러·정독형` 또는 축별 점수. **기계 비교·매칭용.** (v1은 **저장만, 비노출** — ②매칭 단계에서 사용)

> **결정**: ②까지 갈 거라(로드맵 §추천), 태그를 v1부터 같이 뽑아 저장한다 — 나중에 스키마·프롬프트 안 갈아엎기 위한 싼 보험. 단 v1 UI엔 설명문만 보인다.

---

## 6. 저장·갱신 / 콜드스타트

- **저장**: **전용 테이블 신규**(Flyway, 번호는 머지 직전 부여 — 다중 세션 충돌 회피). 파생 결과(설명문+태그+생성시각+입력 시그니처)를 저장. 매번 LLM 호출 안 함.
  - 스키마 스케치(착수 시 확정): `reading_personality(id, user_id FK unique, narrative TEXT, tags VARCHAR, generated_at, input_signature)`. `input_signature` = 책장 상태 해시(권수·완독·장르 요약) → 의미변화 감지로 재생성 트리거.
- **갱신 시점**: 책장이 **의미있게 변할 때**(시그니처 변동) 또는 사용자 **"다시 분석"** 버튼. → 호출 빈도(=비용 핵심 변수)를 바닥으로.
- **콜드스타트**: 완독 책이 **임계(`COLD_START_MIN_BOOKS`) 미만**이면 분석 보류 + "조금 더 읽으면 성향이 보여요" 안내. **임계=1로 운영(2026-06-08)** — 완독 0권만 보류하고 1권부터는 (정확도 낮아도) 결과를 보여준다(아래 §10 화해 노트).

---

## 7. 프라이버시 경계 (sns §3.5와 동일 원칙 — 1순위)

- **v1 = 본인용·비노출**: 본인 책장 전체(PRIVATE 포함)로 생성하되 **결과는 본인만 본다** → 누출 없음.
- **공개·매칭(후속)**: 타인에게 노출되는 결과는 **PUBLIC 책 기반만** 따로 생성 — 안 그러면 비공개 책이 성향으로 **간접 누출**. 입력 책 범위가 다르므로 본인용과 **별도 생성·저장**.
- 한 번 새면 회수 불가. 노출 경계는 사후 패치 아니라 설계에서 못 박는다.

---

## 8. 남은 열린 질문 (착수하며 확정 — v1 블로킹 아님)

- 장르 분류 **정규화**: 알라딘 `categoryName`은 `"국내도서>소설/시/희곡>한국소설"` 식 경로 — 어느 깊이까지 쓸지(대분류만? 2단계까지?).
- 출력 **태그 체계**(축·라벨 네이밍) — ②매칭 본격화 때 확정. v1은 LLM 자유 태그로 저장만.
- ~~콜드스타트 **임계 권수**(잠정 5권) 튜닝.~~ → **1권으로 확정(2026-06-08)**: 정확도보다 "어떤 결과라도 보여주는 재미 + 책 쌓일수록 결과가 바뀌는 재미"를 우선(사용자 결정). 완독 0권만 보류.
- 호출 실패율·지연 실측 후 폴백 UX 다듬기.
- (후속) 공개용 결과 생성 트리거 — 책 공개 토글 시 재생성? "공개 성향 보기" opt-in?

---

## 9. 정확도 기대치 고지 (필수 UX)

- "**MBTI처럼 가볍게 즐기는 재미**"임을 결과 화면에 명시 — 과신 금지. 책장이 작거나 장르 편향이면 부정확할 수 있음을 알린다.

---

## 10. 구현 단계 (TDD, 단계별 PR)

> 데이터 토대(책장·SNS)는 이미 있음. 프론트 독립으로 진행 가능. 각 단계 Red→Green 가시화(프로젝트 규칙).

1. **Phase 1 — 장르/출간연도 적재** — 적재 사슬과 백필은 위험 프로파일이 달라(백필은 외부 API 호출) 두 PR로 쪼갠다:
   - ✅ **Phase 1a (완료, PR #205 예정) — 전방 적재**: `parse()`에 `categoryName`·`pubDate` 매핑 + `BookSearchResult`(8-인자, 6-인자 편의 생성자로 기존 호출 보존)·`Book`(필드+게터+register 오버로드, blank→null) + Flyway **V18**(`category varchar(300)`, `pub_date varchar(20)`) + 검색폼 hidden 필드 + `addFromSearch` 배선. 이제 검색으로 등록하는 새 책은 장르·출간일이 적재된다. TDD: 파서 매핑·누락 null / Book 적재·8-인자 null 불변식 / addFromSearch 전달 / 컨트롤러 사슬.
   - ✅ **Phase 1b (완료, PR #206 예정) — 백필**: 기존 책(`category IS NULL` + isbn13 있음)을 알라딘 **ItemLookUp**으로 채운다. `BookSearchClient.lookupByIsbn(isbn)` 포트(알라딘 어댑터는 ItemLookUp 호출 + 기존 `parse()` 재사용) + `Book.applyCatalogMetadata` + `BookCatalogBackfillService.backfill(limit)`(외부 HTTP는 트랜잭션 밖, 채운 책만 `saveAll`) + 관리자 트리거 `POST /admin/books/backfill-catalog`(ADMIN·CSRF·결과 플래시). **새 Flyway 없음**(V18 컬럼 재사용). TDD: ItemLookUp URL·lookup 가드 / `applyCatalogMetadata` / 백필 멱등(이미 채워진 책 제외)·null-isbn 제외·미발견 notFound·disabled no-op·limit cap / 관리자 인가(ADMIN 실행·USER 403).
2. ✅ **Phase 2 (완료) — 독서 프로필 집계(코드, 결정적)** — `book`+`reading_session`에서 §3 사실을 뽑는 순수 집계기. `com.booktimer.personality` 패키지 신설: 출력 레코드 `ReadingProfile`(+분포 항목 `LabeledCount`) · 순수 함수 `ReadingProfileAggregator.aggregate(books, sessions)`(영속성 무의존 → DB 없이 경계값 전수) · 얇은 `ReadingProfileService`(레포 배선, **전체 책 기반=본인용** §7). 집계 사실: 권수·상태분포·완독률 / 총시간·평균세션(정독↔다독, 진행중 세션 제외) / 저자편향·다양성 / **장르편식·잡식**(장르 = categoryName **대분류=2번째 세그먼트**; 1번째 "국내도서/외국도서/eBook"는 판매구분이라 제외 — §8 깊이 질문은 대분류로 확정) / **출간 신·구**(pubDate 앞 4자리→연대, 최신 먼저). **결정적**: 분포 동률 시 라벨 오름차순(Phase 4 input_signature·단언 안정). **null-state 제외**(N-055): 저자·장르·출간일 없는 책은 해당 분포에서 빠진다. 저장/LLM 없음(순수 read, Flyway 무변경). TDD 경계값: 0권/null입력/완독률 0나눗셈/정독↔다독/진행중 세션 제외/저자 편향·동률·상한/장르 대분류·단일세그먼트 폴백/연대 파싱·못읽음 제외/교차 사용자 누출 없음. 단위 11 + 배선 2.
3. ✅ **Phase 3 (완료) — LLM 포트 + Gemini 어댑터** — 포트 `ReadingPersonalityNarrator`(가짜 구현으로 테스트·공급자 교체) + Gemini Flash 어댑터 `GeminiReadingPersonalityNarrator`(알라딘 어댑터와 같은 패턴: `@Value` 키 주입·`isEnabled()` 게이트·`RestClient`·자체 ObjectMapper·예외→empty 격리). **키는 `x-goog-api-key` 헤더로**(민감정보 URL·로그 노출 회피, `BOOKTIMER_LLM_API_KEY` ECS env). 출력 레코드 `PersonalityNarration`(narrative+tags) / 결합 `ReadingPersonality`(profile+narration, narration=null이면 폴백). 오케스트레이션 `ReadingPersonalityService.analyze(user)` = 프로필 집계(Phase 2) → 서술 → **폴백 결합**(서술 비면 사실만). **HTTP/JSON 분리**: `buildPrompt`(그라운딩 "지어내지 마라"+사실 JSON 주입)·`buildRequestBody`(Jackson 직렬화로 이스케이프 보장·temperature 0.4·responseMimeType=application/json)·`parseNarration`(Gemini 봉투 candidates&gt;content&gt;parts&gt;text 2단 파싱 + ```json 펜스 제거 + narrative 비면 폴백) 모두 정적·네트워크 무관 단위테스트. TDD Red→Green 3 increment: parseNarration / 게이트·프롬프트·요청본문·narrate 가드 / 서비스 배선·폴백. 단위 7 + 배선 2. **새 저장 없음**(Flyway 무변경 — 캐시는 Phase 4). 실제 Gemini 호출은 키 주입 시 런타임에만(테스트는 가짜·정적 파싱).
4. ✅ **Phase 4 (완료) — 저장·캐시·갱신** — 캐시 엔티티 `ReadingPersonalityCache`(user_id unique·narrative·tags(개행 join)·input_signature·generated_at, BaseTimeEntity) + **Flyway V19** `reading_personality` + 레포 `ReadingPersonalityCacheRepository`. 입력 시그니처 `ProfileSignature.of(profile)` = 프로필 구조(권수·상태·저자/장르/연대 분포) **+ 시간 hour 버킷**의 SHA-256 — **초 단위 raw는 빼서 측정 세션마다 재생성(thrash) 회피**. 진입점 `ReadingPersonalityService.analyzeCached(user, force)`: (1) **콜드스타트**(`COLD_START_MIN_BOOKS=5` 미만)면 LLM·캐시 없이 사실만 보류, (2) **캐시 히트**(force 아니고 시그니처 일치)면 LLM 건너뜀, (3) **재생성**(force·캐시 없음·시그니처 변동)면 narrate→캐시 upsert(같은 행 갱신=unique 보존, generated_at은 주입 `Clock`), (4) **LLM 실패면 사실만 폴백·캐시 안 건드림**. 회원 탈퇴 정리(`AccountService.purge`)에 캐시 삭제 추가(user_id FK). TDD Red→Green 3 increment: ProfileSignature(결정적·구조 민감·hour 버킷) / 캐시 6경로(첫 생성·히트·시그니처 변동·force·콜드스타트·LLM 실패) / 계정 삭제 정리. 단위 5 + 통합 6 + 계정삭제 업데이트. FlywayMigrationTest(validate)가 엔티티↔V19 동기화 검증.
5. ✅ **Phase 5 (완료) — 본인 화면 노출 → v1 완료** — 전용 페이지 `GET /personality`(대시보드 바로가기 타일에서 진입). 컨트롤러 `PersonalityController`가 `analyzeCached(user, false)` 결과를 표시 모델 `PersonalityView`로 매핑(3상태: **READY** 서술 / **COLD_START** 책<5 안내 / **FALLBACK** LLM 실패 안내), 템플릿 `personality.html`이 상태별 카드 + **책장 사실 요약**(콜드스타트·폴백에서도) + **정확도 고지**("MBTI처럼 가볍게") 렌더. **"다시 분석"** = `POST /personality/refresh`(CSRF) → `analyzeCached(force=true)` → PRG 리다이렉트. 태그는 v1 비노출(저장만). 누출 없음(본인만, `anyRequest().authenticated()`). TDD: PersonalityView 3상태 분류(경계: 정확히 임계는 콜드스타트 아님) 단위 4 + 컨트롤러 인증 게이트·3상태 라우팅·force·CSRF 통합 6.

> **v1 완료(2026-06-07)**: 책장 → 사실 집계(2) → LLM 서술(3) → 캐시·갱신(4) → 본인 화면(5) 전 구간 출하. 남은 §8 잔여(장르 깊이 튜닝·태그 체계·콜드스타트 임계 튜닝·폴백 UX 실측)와 후속(공개용·매칭)은 별도.
6. **(후속) 공개용 생성 + 사람 추천(매칭)** — PUBLIC 책 기반 결과 + 태그로 비슷/정반대 추천. §전략 밀도 신호 뒤.
   - ✅ **Phase 6a 책방 공개 노출(완료, PR #240)** — 사용자 요청으로 §7 "공개·매칭은 PUBLIC 책 기반 재생성" 원칙을 그대로 구현. 본인용(전체 책)과 **별도 생성·저장**: `User.personalityPublic` opt-in 플래그(기본 false, V25) + `ReadingProfileService.publicProfileOf`(공개+완독 책 **+ 그 책 세션만** — 집계기가 세션을 책과 대조 안 하므로 비공개 책 독서시간 누출을 서비스가 차단) + **별도 캐시 테이블** `reading_personality_public`(V26)·`analyzePublicCached`(본인용과 캐시 규칙을 `CacheStore` 추상화로 공유) + 책방(`/u/{loginId}`) 노출은 opt-in일 때만, **방문자 조회는 캐시 읽기 전용(LLM 미호출)**·생성은 소유자 행동(토글 ON·"다시 분석")에서만. 별도 테이블 이유: V19 user_id unique를 (user_id,scope) 복합으로 바꾸는 제약 드롭이 MySQL/H2에서 이식 위험 → CREATE만 하는 별도 테이블이 안전+본인용 경로 무손상. UX: 본인용과 결과가 다를 수 있음을 카드에 명시("공개 책 기준"), 공개를 더 켤수록 본인 것에 수렴(통제권은 사용자). §8 열린 질문 중 "공개용 생성 트리거"는 **opt-in 토글 + 켤 때 생성**으로 확정.
   - 🔁 **공개/비공개 분기 폐지 → 공개 단일·항상 노출(2026-06-08, PR #241)** — 사용자 재결정: "책BTI는 사용자끼리 즐기는 재미 요소다. 나만 보는 비공개 성향은 재미가 아니다. 공개 책으로만 뽑고 무조건 책방에 공개하자." 그래서 6a의 **이원화(본인용 전체 책 + 공개용 공개 책 + opt-in 토글)를 일원화**: 성향은 **공개(PUBLIC)+완독 책만**으로 단일 생성(`analyze`·`analyzeCached`가 `publicProfileOf` 사용)해 **단일 캐시 `reading_personality`**에 저장하고 **항상** 책방에 노출(opt-in 게이트 제거). 본인 `/personality`와 책방 `/u/{loginId}`가 같은 결과를 본다. 제거: 별도 테이블 `reading_personality_public`·엔티티/레포·`analyzePublicCached`·`CacheStore` 추상화(저장소 하나라 인라인)·`User.personalityPublic` 플래그·`POST /personality/visibility` 토글 — 모두 **V28**(테이블 drop + 컬럼 drop)로 정리. **프라이버시는 오히려 강화**: 유일한 계산 입력이 공개 책뿐이라 §7 누출 차단이 분기 없이 자명해짐. 콜드스타트(공개 완독 5권 미만)면 캐시가 없어 책방 카드가 숨는다(방문자에겐 LLM 미트리거 그대로). 6a의 근거(별도 생성으로 누출 차단)는 보존하되, 더 단순한 "공개 입력 단일화"가 같은 불변식을 달성하므로 분기를 접었다.
   - ⏳ **Phase 6b 사람 추천(매칭)** — 공개 태그로 비슷/정반대 독자 추천. §전략 밀도 신호 뒤(미착수).

> 이 문서는 **확정본**이다. §8 잔여는 해당 Phase 착수 시 코드와 함께 확정한다.
