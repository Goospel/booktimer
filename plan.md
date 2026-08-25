# BookTimer — 작업 계획 / 추후 할 일 (plan.md)

> 지금 당장 안 하지만 **놓치면 안 되는 할 일**을 모아두는 곳.
> 개요·도메인 규칙은 [README.md](README.md), 학습 개념은 [claude-docs/learning-notes.md](claude-docs/learning-notes.md),
> 함정·해결은 [claude-docs/troubleshooting.md](claude-docs/troubleshooting.md).

MVP(누적 타이머 + 인증 + 설정 + 일자별 기록 + 계정 보안)는 구현·배포 완료 상태.
아래는 그 이후 로드맵과 미뤄둔 보강 항목.

> 📱 **폰으로 확인할 것은 [claude-docs/device-check.md](claude-docs/device-check.md)에 한 장으로 모아 뒀다.**
> 미완 항목의 **대부분이 실기기 확인**이고 이 문서 곳곳에 흩어져 있어, 화면 순서로 다시 묶은 목록이다.
>
> 🧭 **미완 마커는 세 가지 어휘를 쓴다 — `⬜` · `🔜` · 마커 없는 `- [ ]`.** 감사할 땐 **셋 다** 세어야 한다.
> 2026-08-25 감사에서 이걸 놓쳐 한 번 헛걸음했다: 앞선 스윕이 `⬜`만 봤는데, 그때 이미 끝나 있던 두 항목이
> 나머지 두 어휘에 숨어 있어 다음 세션이 「할 일」로 집어 들었다.
>
> ⚠️ **세기 전에 읽을 것 — 이 범례 줄(바로 위)이 `⬜`와 `🔜`를 하나씩 품는다. 집계에서 각각 1을 빼라.**
> 범례는 마커를 **실물로 보여 줘야** 쓸모가 있어서 뺄 수 없다. 그러니 지우는 대신 **빼야 할 개수를 여기 적어 둔다**
> — 계측 대상 문서 안에 범례를 두면 범례가 계측에 잡히고, 그건 결함이 아니라 **명시해야 할 상수**다.
> 2026-08-25 기준 실제 미완 = **`🔜` 8 · `⬜` 15**(범례 제외). 같은 이유로 **닫은 항목의 옛 마커를 산문에서
> 인용할 땐 이모지를 쓰지 말고 말로 풀어라** — 그건 범례와 달리 실물이 필요 없는데도 유령을 만든다(이 감사 중 2건 발생). 상세는 T-215.

---

## 🎯 전략 우선순위 — retention 우선 / 2-audience (2026-06-06)

> 기능을 더 쌓기 전에 **무엇을 먼저 할지의 판단 기준**. 이 절은 아래 모든 로드맵 항목 위에 얹히는 렌즈다.
> (배경 대화: 2026-06-06. 출발점 thesis는 메모리 `project-thesis` 참고.)

### 출발점(thesis)과 두 엔진

- **thesis**: BookTimer는 **"책을 좋아하진 않지만 읽고는 싶은 입문/포기형"**을 위해 *하루 최소 시간만 정해 딱 그만큼 읽자*는 서비스다(사용자 본인 경험에서 출발).
- 그래서 이 제품엔 **서로 다른 두 사용자를 위한 두 엔진**이 있다 — 헷갈리면 우선순위가 꼬인다:

  | | 엔진 A — 습관 타이머 | 엔진 B — 사람 잇는 재미 |
  |---|---|---|
  | 타깃 | **입문/포기형**(책 어려워함) | **헤비 리더**(생각·읽을거리 많음) |
  | 가치 | 혼자·작게·부담 없이 습관 | 대화·생각 공유·연결의 재미 |
  | retention 원천 | 습관 루프(알림·잔디·축하) | 사람(네트워크) |
  | 대표 기능 | 타이머·잔디·목표 알람·상한 | 공개 책방·팔로우·도서 성향·추천·채팅 |

- **모델 = Strava**: *혼자 쓰는 도구로 들어와서, 사람 때문에 남는다.* A가 입구(콜드스타트 면역)·입문자 가치, B가 끈끈함·헤비 리더 가치. **도서 성향(MBTI)은 "분석 정확도"가 아니라 "소셜 연료"(정체성 배지·대화 물꼬·매칭 씨앗)로 평가**한다.

### 순서(생명) — 솔로로 모으고, 밀도 차면 소셜, 채팅은 맨 끝

1. **A의 retention부터** — 입문자는 약한 사용자라 *다시 안 부르면 무조건 잊힌다*. 아래 「retention 레버」가 **현재 최우선**.
2. **밀도가 차야 B가 0원어치를 벗어난다** — 소셜·성향·추천은 사람이 있어야 작동. 텅 빈 채팅방은 오히려 마이너스. 그래서 §SNS·§도서 성향·§구독은 *밀도 신호가 온 뒤*.
3. **채팅은 맨 마지막** — 가장 무겁고(모더레이션·법적) 위험. 가벼운 소셜 재미(성향 배지·비교·"같은 책 읽는 사람")부터, 채팅은 그 후.

### retention 레버 (입문자 친화 튜닝 — 현재 최우선 백로그)

> 현 메커니즘 중 thesis와 *충돌*하는 것을 다정하게 고치고, 떠난 사용자를 *다시 부르는* 장치를 추가한다.
> 마케팅(소규모 홍보)은 최소 ①을 붙인 뒤 시작하는 것을 권장 — 입문자는 재참여 트리거 없이는 재방문율이 안 나온다.

- [x] **① 재참여 알림** ✅ **토스 채널로 충족 (2026-08-18 점등 · 08-25 사용자 확인)** / ⏸ **웹 이메일 갈래는 의도적 보류** — ⚠️ **이 줄이 오래 「최우선 미완」으로 보였다**: 아래 하위 불릿엔 미니앱 푸시 점등이 이미 ✅로 적혀 있었는데 **머리글만 미체크**라, 로드맵을 훑는 눈에는 최우선 레버가 통째로 비어 있는 것처럼 읽혔다(2026-08-25 마커 감사). 지금 상태 = **토스 연동 사용자에게는 7일 비활동 넛지가 실제로 나간다**. 웹 전용 사용자는 SES 프로덕션 액세스 2차 거부로 여전히 잠겨 있고, **사용자 지시(2026-08-25)로 그쪽은 쫓지 않는다** — 되살아나는 조건은 SES가 열리거나 대체 발송 경로가 생길 때다. 이하 원문은 이메일 채널을 전제로 쓰인 2026-06 합의라 **역사 기록으로 남긴다**. 떠난 사용자를 *다시 부르는* 장치. 습관 앱 retention의 핵심 레버인데 현재 전무(목표 달성 알람은 *탭 열림 전제*라 이미 온 사람만 대상 — 게다가 모바일에서 책 읽는 동안 화면 꺼지면 백그라운드 throttle로 전이 감지조차 못 함. N-### 후보: 웹 인앱 알림의 한계).
  - ⏸️ **보류 (2026-06-24) — SES 프로덕션 액세스 2차 거부**: 1차 거부(6/17, [[N-091]]) 후 상세 사용 사례를 담아 재요청했으나 **또 거부됨**. "상세로 재요청하면 뚫린다"(N-091 초안)는 가설이 반증 — 근본은 정보 부족이 아니라 **신규·저트래픽 서비스의 발송 실적·평판 0**일 가능성(닭-달걀: 실발송 실적이 없어 거부되고, 거부라 실적을 못 쌓음. AdSense "콘텐츠 부족" 거부 §수익과 같은 뿌리). → **이메일 의존 기능(재참여 넛지·가입 인증·비번 재설정)은 실사용·트래픽이 쌓이거나 대체 발송 경로를 찾기 전까지 보류**, 다른 작업(마을 게임화 등 retention A엔진·공개 콘텐츠 확충) 우선. **같은 판단을 또 내리지 않게 여기 박음.**
  - ✅ **토스 미니앱 푸시로 채널 우회 — 점등 완료 (2026-08-18)**: 위 ⏸ 보류는 **채널이 이메일 하나뿐이라서** 걸린 것이었다. 그 사이 미니앱 트랙에서 푸시 인프라(`TossMessengerClient`·알림 동의 UI·푸시 2종)가 완성돼, **SES를 기다리지 않고 토스 연동 사용자에게 먼저 재참여 넛지를 보낸다**(웹 전용 사용자는 여전히 이메일 대기 — 그쪽 ⏸는 위 그대로 유효하다). 설계·구현은 §미니앱 「재참여 넛지 푸시」 항목 참조. **트리거는 기존 이메일 구현과 같은 「7일 비활동 win-back」**이고, 아래 2026-06-06 합의의 「그날 세션 0 → 저녁 1통」은 **의도적으로 채택하지 않았다** — 그 합의는 *목표 달성 축하 푸시가 없던 시절*의 이메일 전제였고, 지금 그대로 하면 「읽으면 축하 · 안 읽으면 리마인더 = 전원 매일 1푸시」가 돼 스팸 체감·동의 철회를 부른다. 반응(동의 철회율·재방문)을 보고 데일리 리마인더를 별도 설계로 재검토한다.
  - **✅ 결정 (사용자 합의 2026-06-06) — 채널 = 이메일, 용도 = 재참여 넛지.**
    - **왜 이메일(웹푸시 아님)**: 모든 사용자가 가입 시 이메일을 **필수로** 제공(로컬 가입+구글 OAuth 모두) → 기기별 권한 동의 불필요, **아이폰 사파리 Web 알림 제약을 통째로 우회**, 크로스 플랫폼.
    - **역할 분담 (중요)**: 이메일은 *실시간이 아님* → "목표 달성 **순간**" 알람엔 부적합(도착 지연·받은함 안 봄). 원래 그건 인앱 알람의 몫으로 뒀으나, **인앱 알람은 탭이 죽으면·모바일 화면 꺼지면 동작 안 해 실효가 없어 제거**(PR #185). 달성 순간 시각 피드백은 **배지(`goalMet` "오늘 목표 달성! 🎉")·초록색만** 남기고, 본격 "순간 알림"은 서버발(푸시/이메일) 인프라가 붙을 때 재검토. 이메일은 **"오늘 안 읽었어요" 재참여**에만 쓴다(이게 retention 가치도 더 큼).
    - **✅ 트리거 (사용자 합의 2026-06-06)**: **"안 읽은 날 저녁 넛지"** — 그날(유저 타임존 기준) 독서 세션이 0이면 정해진 저녁 시각에 1통. 매일 읽는 사람에겐 안 감(스팸 아님).
  - **구현 선결 / 설계 메모**:
    - ✅ **안전 순서 결정 (2026-06-10) — "추후 문제 없는 방향"**: 발송 인프라를 **법적 부담 기준 2단계로 분리**해 간다. ① 먼저 인프라+transactional(가입 인증·비번 재설정·열거 통지)을 깐다 — *서비스 이행 안내*라 정보통신망법 광고성 규제 무관, 보안 '높음' 갭도 여기서 닫힌다. ② 그 위에 재참여 넛지를 **컴플라이언스(옵트인·(광고)표시·수신거부·야간회피·방침 고지)를 갖춰** 얹는다. 정본·체크리스트는 §하드닝 「이메일 발송 인프라」의 "📐 안전한 착수 순서".
    - ⚠️ **발송 인프라가 없음 = 이 결정의 8할은 그 인프라를 짓는 것** — SES/SMTP·발신 도메인 검증·SPF/DKIM/DMARC. §하드닝 「이메일 발송 인프라」와 **같은 작업**이므로 거기서 함께(가입 인증·열거 통지·비번 재설정·이 넛지가 공통 소비자). 메일 단가는 ≈0, 비용은 셋업·딜리버러빌리티.
    - **딜리버러빌리티 — 가입 이메일 인증과 묶기**: 미검증 주소로 보내면 반송→발신 평판 하락(N-053). 인증된 주소로만 넛지 보내는 게 안전.
    - **발송 시각 = 유저 타임존 저녁** — 자정 경계·"오늘 읽었나" 판정은 기존 Clock+유저 TZ(N-010) 재사용. 매 저녁 스케줄 잡(서버 cron/스케줄러)이 "오늘 세션 0인 사용자"를 조회해 발송.
    - **⚠️ 한국 정보통신망법 제50조 (넛지에만 적용)**: 넛지는 *영리목적 광고성 정보*로 보는 게 안전(AdSense·제휴 수익=영리목적, 이용 유도=광고성) → **사전 수신동의(opt-in, 기본 OFF)·제목 `(광고)` 표시·발신자 정보·무료 수신거부**, **야간(21~08시) 발송 별도 동의**(그래서 20시 등 21시 이전), **수신동의 2년마다 재확인**. + **개인정보보호법**: 개인정보처리방침에 발송목적 고지·마케팅 수신은 선택동의로 분리. ⚠️ transactional(가입 인증·비번 재설정 등)은 광고성이 아니라 이 규제 **무관** — 그래서 위 2단계 분리가 안전하다. (법률자문 아님 — 발송 전 방통위/KISA 「불법스팸 방지 가이드」 확인 권장.)
    - **dedup/빈도**: 하루 1통 상한, 이미 읽은 날엔 미발송(트리거 자체가 그럼), 수신거부 시 영구 제외.
- [x] **② 마찰 감소 — 사후 수동 입력 (완료 ✅ 2026-06-07)** — 측정 시작을 깜빡한 독서를 *나중에 수동으로 기록*(시간·책 입력). 지금은 타이머를 안 켜면 그 독서가 통째로 유실 → "어차피 기록 안 됐네" 이탈. 약한 사용자일수록 마찰 내성 0.
  - **구현**: 전용 페이지 `GET/POST /sessions/manual`(`ReadingSessionController`) + 템플릿 `manual-session.html` + **"이번 주 빠뜨린 날" 목록의 "채우기" 링크**(`?date=` 프리필)로 진입. (초기엔 대시보드 퀵액션 "✍️ 빠뜨린 기록"/"직접 채우기" 타일도 진입점이었으나, 진입을 "빠뜨린 날" 링크 하나로 일원화하며 대시보드 타일은 제거 — 2026-06-24. 페이지·라우트·`V22`는 유지.) 서비스 `ReadingSessionService.recordManual` = `start`의 **책 필수** 규칙을 따른 완료 세션(진행 중 세션과 무관). **차감 로직 없음** — 부채는 완료 세션에서 유도되므로([[N-058]] 7일 윈도우) 세션 저장이 곧 그 날 부채 감소. 날짜는 **최근 7일 윈도우 안만** 허용(그 이전은 자동 용서). **DB 무변경**(기존 `reading_session` 재사용 — Flyway 없음).
  - **날짜 안착**: 잔디·일자별 기록·부채는 `startedAt`을 유저 TZ로 묶으므로(N-010) 고른 날짜에 안착하게 시각을 잡음 — 오늘이면 `endedAt=now`(미래 시각 회피), 과거면 그 날 정오 종료, `startedAt=endedAt-시간`.
  - **직접 채운 날 잔디 구분 (PR #225, 완료 ✅)**: 수동 입력으로 채운 날을 잔디에서 **테두리 색**(앰버 인셋)으로 구분해 측정값과 시각적으로 구별. `ReadingSession.manualEntry` 플래그(V22 `reading_session.manual_entry`)를 수동 입력 시점에 박고(`ReadingSession.manual` 팩토리), 일자별 집계(`DailyReadingRecord.manuallyFilled`)→`ContributionGraphBuilder`(manualDates 오버로드)→`ContributionDay.manual`로 흘려 템플릿에서 `.grass-cell.manual` 테두리로 렌더. 본인 뷰(history·dashboard·book-detail)에만 표시하고 **공개 프로필(profile)엔 미표시**(백필 사실을 타인에게 노출하지 않음). 기존 행 백필은 휴리스틱(`date(started_at)<>date(created_at)`=백데이트). 같은 날 수동 입력은 백필로는 못 가리나 이후 신규 기록은 정확.
  - **차감 → 부채 모델 전환 (2026-06-07, PR #217)**: 초기엔 "수동 기록도 측정과 같이 단일 누적 잔여에서 무조건 차감"으로 출하 → 한 달 전 1시간을 적으면 *오늘* 잔여가 줄어 "오늘 목표 채움"으로 오인(실사용 버그). *오늘만 차감* 임시 픽스를 거쳐, 근본적으로 **부채 모델을 단일 롤링 카운터(N-001) → 7일 윈도우 per-day 부채로 전환**(아래 §부채 모델 참조). 이제 부채 = 날짜별 독립 = `max(0, 하루목표 − 그날 읽은 초)`, 활성 범위 최근 7일(그 이전 자동 용서). 백데이트 입력이 *그 날* 부채를 정확히 채우고 오늘을 오염시키지 않는다. 개념·일반 교훈: [[N-058]].
- [x] **③ 부채 용서 장치 (streak freeze) — 7일 윈도우로 흡수 ✅ (2026-06-07, PR #217)** — 누적 부채가 밀리면 *죄책감→이탈*을 만든다는 문제를, 부채 모델을 **최근 7일 윈도우**로 바꿔 구조적으로 해결했다: 7일보다 오래된 빚은 표시·집계·충전 대상이 아니라 **자동 용서**되고, 최대 부채도 7×목표로 자연 제한된다(옛 cap의 과몰입 방지 역할 흡수). 별도 면제 카운터/리셋 UI 없이 윈도우 자체가 용서 장치. (더 정교한 면제가 필요하면 후속에서.)
- **thesis에 이미 잘 맞는 기존 장치**(유지·강화): **7일 윈도우**(옛 cap의 과몰입 방지+죄책감 상한을 흡수 — 최대 부채 7×목표, 오래된 빚 자동 용서), 목표 달성 배지(`goalMet` "오늘 목표 달성! 🎉"·초록색 — 인앱 알람은 실효 없어 제거 PR #185, 배지만 유지), 잔디(습관 시각 증거).

### 📐 부채 모델 — 7일 윈도우 per-day (2026-06-07 전환, PR #217)

> 옛 모델(N-001): **단일 누적 카운터**(`remainingSeconds`) + 상한(cap) + 매일 증가하는 Lazy accrual. "지금 시점의 단일 잔액"만 알아서 **백데이트(과거) 기록을 올바른 날에 못 꽂는** 한계(레버 ② 버그의 뿌리). 사용자 결정으로 per-day 모델로 전환.

- **하루 부채 = `max(0, 하루목표 − 그날 읽은 초)`** — 날짜별로 발생하되, **오늘 초과분이 과거 부채를 소급해 갚는 backward-only catch-up(과거 메우기)**이 적용된다(PR #379). 하루목표 = `dailyIncrementSeconds`.
- ~~**활성 범위 = 최근 7일**(오늘 포함). 그보다 오래된 날은 표시·집계·충전 대상 아님 = **자동 용서**(레버 ③ 흡수, cap 대체). 최대 부채 7×목표로 자연 제한.~~ → **7일 자동 소멸 폐지, 무제한 누적으로 전환(2026-08-14, 완료 ✅)**: 창은 `max(부채 누적 시작일, 첫 목표일)`부터 오늘까지로 계산 범위가 **매일 하루씩 자란다**(`ReadingDebtService.debtWindowStart`, 창 길이는 `goalByDate` 맵이 계산기에 알려준다). 부채는 이제 기다려서 사라지지 않고 **두 수단으로만** 줄어든다 — ① 목표를 초과해 읽어 backward 재분배로 갚기 ② **리워드 광고 용서(횟수 제한 없음)**. **오늘 몫은 광고로 지울 수 없다**(계산기가 오늘을 용서 대상에서 제외 — 진행 중인 하루를 광고로 무르면 습관 형성과 정면 충돌). 전환 충격 차단 장치는 **누적 시작일 하한**(`booktimer.debt.epoch`=2026-08-07 = 배포 1주 전)이다 — 없으면 옛 사용자에게 가입 이후 수백 시간이 한꺼번에 살아난다. 하한을 배포보다 1주 이르게 잡아 **전환 순간 보이는 부채가 옛 7일 창과 같고** 그 뒤로 하루씩 누적된다. 목표 이력이 없는 레거시(미온보딩) 계정은 근거 있는 시작점이 없어 옛 7일 창으로 폴백한다(폴백 목표를 무한한 과거에 소급 적용하면 없던 빚을 만든다).
- **부채는 저장 안 하고 완료 세션에서 유도**(`ReadingDebtService`/`WeeklyDebtCalculator`, 유저 TZ+Clock로 오늘·윈도우 산정 N-010). 롤링 카운터·cap·accrual 불필요.
- **대시보드** = 헤드라인 카운트다운(JS). **기본은 "오늘 부채 + 윈도우 미상환 부채 합"**(밀린 부채 합산 표시, PR #257) — 설정에서 끄면 "오늘 부채"만. **"이번 주 빠뜨린 날" 목록**(최근 6일 부채>0, "채우기"=수동입력 `?date=` 프리필)은 **독서 기록 화면(`/history`)으로 이전**(PR #219) — "채워 넣기=과거 기록 보정"이라 기록 화면이 자연스러운 자리. 대시보드의 별도 "빠뜨린 기록" 바로가기 버튼도 같이 제거(채우기 동선이 빠뜨린 날 목록으로 일원화).
- **밀린 부채 합산 표시 토글(PR #257)**: 헤드라인에 윈도우 미상환 부채(어제까지 밀린 빚)를 합산해 "갚을 시간"으로 쌓아 보여줄지를 설정에서 on/off(`ReadingTimer.debtCarryover`, V30, 기본 ON). ~~표시 전용 — 어제 빚은 측정으로 안 줄고 '빠뜨린 날 채우기'로만 갚는다~~ → **PR #379 뱅킹 도입으로 갱신**: 오늘 할당량 채우고 더 읽으면 그 초과분이 과거 부채를 직접 갚는다(backward-only catch-up). 수동 채우기는 그 날 읽은 양으로 부채를 0으로 만드는 별개 동선으로 유지. 헤드라인=`WeeklyDebt.totalDebtSeconds()`(오늘+빠뜨린 날 합), `carriedDebtSeconds`=빠뜨린 날 합. **라이브 카운트다운은 전체 빚이 0이 될 때까지 매초 줄어든다** — 뱅킹(오늘 초과분이 과거 빚을 직접 갚음)과 일관되게, 오늘 할당량을 채운 뒤 과거 빚을 갚는 구간에서도 라이브 잔여가 계속 감소한다(초기 출하의 floor-clamp는 "어제 빚은 못 갚는다"는 가정으로 floor에서 멈췄으나, 이는 backward-only 뱅킹과 어긋나 측정 종료 시에만 갱신되던 버그여서 `max(0,…)`로 정정). `carriedDebtSeconds`는 **오늘 목표 진행바** 계산용으로 남는다(라이브 잔여에서 빼 "오늘 부채분"을 산출해 진행률·달성 판정). 끄면 0이라 현행(오늘 부채만)과 동일. 배경: '50분 남기고 자정 지났는데 타이머가 70분으로 리셋'된 실사용 피드백 — #217의 이월 없음이 의도대로였으나 사용자가 누적 부채를 원해 합산 표시를 더했다([[N-058]]).
- **수동 입력/측정 stop**: 차감 로직 제거 — 세션 저장이 곧 그 날 부채 감소. 수동 입력 날짜의 하한은 **부채 창 시작일과 동일**(2026-08-14 — 옛 고정 7일 폐지): 부채로 세는 날은 채울 수 있어야 하고, 세지 않는 날은 채울 이유가 없다. 두 규칙이 갈리면 "부채는 있는데 채울 수 없는 날"이 생긴다.
- **그날 목표로 판정 — per-day 목표 스냅샷(PR #222, 완료 ✅)**: 부채는 `max(0, 하루목표 − 그날 읽은 초)`인데, 하루 목표를 **현재 평면값 하나**(`ReadingTimer.dailyIncrementSeconds`)로만 잡으면 목표를 **올렸을 때** 옛 목표를 채운 과거 날이 새 목표로 재판정돼 빠뜨린 날로 둔갑한다(소급 함정 — 표시 상태를 가변 파라미터에서 매번 재유도하면 파라미터를 건드릴 때 과거 판정까지 다시 쓰임, [[N-059]]). 해결: 목표가 **설정·변경되는 시점**마다 한 행을 남기는 이력(`ReadingGoalChange`, 테이블 `reading_goal_change`, V21)을 두고, 과거 날을 `GoalSchedule.goalFor(date)`(effective_date ≤ date 중 최근=floorEntry)로 **그날 유효했던 목표**로 판정한다. 기록은 온보딩·설정(목표 실제 변경 시)에서, `ReadingTimer`는 현재 목표 캐시로 유지(잔디 등 공유). **두 겹 방어**: ① per-day 스냅샷 = 앞으로의 목표 변경을 정확히 막음 ② 1분 미만 부족 용서(`WeeklyDebtCalculator.MIN_MISSED_DEBT_SECONDS=60`) = 이미 지나간 옛 목표(데이터 복원 불가, 예: 60→61분 변경 전 날들)의 "0분 부족" 잔재를 커버. **baseline 컷오프(PR #223)**: 목표 이력의 첫 행(`GoalSchedule.earliestEffectiveDate()`)보다 이른 윈도우 날은 "사용자가 아직 시작하기 전"이라 빠뜨린 날에서 제외한다(`ReadingDebtService`가 목표를 안 넣어 부채 0) — 가입 전 날짜가 폴백 현재 목표로 "N분 부족"으로 새던 잔재 차단(입문자가 시작 전 날을 "못 지킴"으로 보지 않게). 이력이 비면(레거시) baseline이 없어 옛 동작대로 폴백 전체 적용. **잔디 색 농도도 그날 목표로(PR #224, 완료 ✅)**: 독서 잔디(전체·책별)의 칸 색은 달성 비율(목표 대비)인데 같은 평면-목표 소급 버그가 있어 목표를 올리면 옛 목표를 채운 과거 칸 색이 내려갔다 → `ContributionGraphBuilder`에 날짜별 목표 오버로드(`ToLongFunction<LocalDate>`)를 두고 `ReadingContributionService`·`BookContributionService`가 같은 `GoalSchedule.goalFor`를 넘겨 칸마다 그날 목표로 색칠(빠뜨린 날과 동일 인프라 재사용). 잔디는 baseline 이전 날도 어차피 독서 0이라 색 변화 없음(컷오프 불필요).
- **admin 부채 진단 뷰(PR #380, 완료 ✅)**: `/admin/users/{loginId}/debt?asOf=` — 임의 기준일까지의 누적 창을 **재계산으로 재현**(스냅샷·스케줄러 0). 날짜별 원시부채/초과·1분미만용서·재분배후잔여·초과소모량 추적. `WeeklyDebtCalculator.computeTrace()` + `WeeklyDebtTrace`/`DayDebtTrace`로 단일 출처 보장(진단과 라이브 계산이 같은 경로). backward-only 선납불가·과거메우기·윈도우슬라이드 관찰 가능.
- **PR-1(#217)**: 사용자 대면 전환(엔진+대시보드+수동입력+설정/온보딩 cap·초기값 제거+관리자). 엔티티 `remainingSeconds/capSeconds/lastAccrualDate`·accrual 클래스는 **미사용 vestigial로 잔존**(무파괴, DB 마이그레이션 없음).
- **PR-2(#218, 완료 ✅)**: 죽은 컬럼/클래스 제거 — 엔티티에서 `remainingSeconds/capSeconds/lastAccrualDate` + accrue/deduct/isAtCap/applyInitialSetup 제거, `AccrualCalculator`·`ReadingTimerService` 삭제, **V20 마이그레이션으로 세 컬럼 DROP**. 이제 `ReadingTimer`의 유일한 상태는 하루 목표뿐. 개념·일반 교훈: [[N-058]].

### 🔖 리브랜딩(서비스명 변경) — 엔진 B 완성 시점에 (예약, 2026-06-06)

> **아직 확정 아님 / 지금 바꾸지 않는다.** 더 나은 이름이 떠오르지 않아 보류하되, **"언제 다시 꺼낼지"의 트리거만** 박아둔다.
> (이 항목은 Claude가 적절한 시기에 먼저 리브랜딩을 제안하기 위한 *예약 신호*다 — 트리거 조건이 충족되면 다음 작업 논의 때 Claude가 운을 뗀다.)

- **문제**: 현재 이름 **`BookTimer`** 는 *타이머*만 강조한다 — 그건 **엔진 A(입문/포기형을 위한 습관 타이머)** 의 기능이다. 그런데 §전략에서 타깃이 **엔진 B(헤비 리더)까지 확장**됐고, 헤비 리더에게 "타이머"는 와닿지 않는다(그들은 시간 재려고 오는 게 아니라 *사람·대화·생각 공유*를 위해 온다). 이름이 제품의 절반(소셜·책방·연결)을 가린다.
- **왜 지금은 안 바꾸나**: ① 더 나은 이름 미정. ② **이름은 엔진 B가 실재할 때 바꿔야 의미가 있다** — 아직 SNS가 0원어치면 새 이름도 약속만 앞선 꼴. ③ 리브랜딩 비용(도메인·OAuth 동의화면·리디렉션 URI·privacy·로고·문구 일괄 교체)이 큰데, 어차피 **§도메인 TLD 이전(`.click`→`.com`/`.app`)·§이메일 발송 인프라**와 겹치므로 그때 한 번에 묶는 게 한계비용 최소.
- **✅ 트리거 (이때 Claude가 리브랜딩을 제안한다)**: **헤비 리더를 위한 SNS 기능이 어느 정도 완성되어 출하될 즈음** — 구체적으로 §SNS(공개 책방·팔로우·검색은 이미 있음) 위에 **도서 성향·사람 추천 중 하나 이상이 실제로 켜져 "소셜이 제품의 얼굴이 되는" 시점**. 그 무렵 ①도메인 TLD 이전 ②이메일 인프라와 묶어 리브랜딩을 함께 검토.
- **새 이름 방향(브레인스토밍 씨앗, 미확정)**: 타이머 한 기능이 아니라 **"책 + 사람/공간"** 을 담는 이름. 이미 굳히는 중인 **"책방"** 컨셉(공개 프로필=개인의 책방, PR #182)과 결이 맞는 후보군을 그때 모은다.

---

## 🔒 보안 / 인프라

### HTTPS 적용 — ALB TLS termination (완료 ✅ 2026-06-02)

**한 일**: `booktimer.click` 도메인(Route 53) + ACM 인증서(DNS 검증) + ALB 443 리스너 +
HTTP→HTTPS 301 리다이렉트 + Route 53 alias. 배경 개념 **N-021**.

- [x] 도메인 확보 — Route 53에 `booktimer.click` 등록(무료 플랜은 등록 차단 → 유료 전환)
- [x] ACM 인증서 발급 (ap-northeast-2, DNS 검증, apex + www)
- [x] ALB HTTPS(443) 리스너 + 인증서 연결 (기존 타깃그룹)
- [x] HTTP(80) → HTTPS(443) 301 리다이렉트
- [x] Route 53 alias(apex/www) → ALB
- [x] 프록시 뒤 https 인식 — **`ForwardedHeaderFilter` 명시 빈**(`WebConfig`).
      ※ `server.forward-headers-strategy=framework` 프로퍼티는 Boot 4에서 무동작이라 명시 등록(T-014, N-022)
- [x] 세션 쿠키 `Secure`/`HttpOnly` (prod 전용)
- [x] 보안 그룹: ALB 인바운드 443 허용
- [x] **(후속) HSTS 헤더** ✅ 해소 확인 (2026-08-25 실측) — 운영 응답에 `Strict-Transport-Security: max-age=31536000 ; includeSubDomains`가 실려 있다(붙이는 주체는 Caddy). ⚠️ **옛 근거 문장 「현재 ALB가 일부 적용」은 무효였다** — ALB는 2026-08-03 EC2 이관 정리에서 **삭제됐다**. 즉 이 줄은 「할 일」이 아니라 **이미 없는 리소스를 근거로 든 낡은 문장**이었고, 그래서 다음 세션이 이걸 후보로 집어 들었다(2026-08-25 마커 감사에서 적발). ※ `preload` 지시자는 없다 — 등재는 되돌리기가 어려우니 필요해지면 그때 별도 판단.

### 도메인 TLD 이전 — `.click` → `.app` (완료 ✅ 2026-06-12 — 이메일 점등 선결)

**왜**: 구글 로그인 게시 후 Chrome이 `booktimer.click`을 **"위험한 사이트"로 차단**(Safe Browsing 피싱 오탐)했다.
원인은 우리 코드가 아니라 **`.click` TLD의 낮은 평판 + 신규 도메인 + 로그인/OAuth 콜백** 조합(T-027, N-036).
`.click`은 평판이 근본적으로 낮아 재발 위험 + **마케팅 메일(이메일 2단계 넛지) 딜리버러빌리티**까지 깎여, 점등 전 `.app`으로 이전한다.

**진행 (2026-06-11)** — `.app`(레지스트리가 HTTPS 강제·평판 양호) 선택. 인프라(콘솔): `booktimer.app` 구매 · ACM(서울 apex+www, DNS 검증) · ALB 443 SNI 인증서 · Route53 alias(apex·www) · Google OAuth redirect URI 병행 등록 완료, `https://booktimer.app` 가동(health UP) 확인. 코드 일관성(base-url 주석·README·`ForwardedHeadersHttpsTest` `.app`) PR #310. base-url은 env 주입·OAuth redirect는 `X-Forwarded-Host` 동적 생성이라 **앱 동작 코드 0 변경**.
- [x] **근본 대응 — `.app` 이전 착수**: 도메인 · ACM · ALB · alias · OAuth redirect 완료.
- [x] **OAuth 로그인 검증**: `.app` 구글 로그인 성공(`redirect_uri_mismatch` 없음 — ⑤ redirect URI 병행 등록 검증).
- [x] **base-url `.app` 주입**: `BOOKTIMER_BASE_URL` task def 평문 environment 주입(PR #311 — 그동안 미주입이라 localhost 기본값이던 갭 동시 해소). main push 자동 배포.
- [x] **`.click`·`www.click`·`www.app` → `.app`(apex) 301** (PR #315 cleanup + 2026-06-28 `www.app` 보강): ALB 443 호스트 규칙(우선순위 1) — 경로·쿼리(`/#{path}`·`#{query}`) 보존, 기본값은 `.app`(apex) forward 유지. curl로 apex·www·경로쿼리 301 + `.app` 200 대조 검증. **⚠️ PR #315가 `.click`·`www.click`만 등록하고 신규 `www.booktimer.app`을 빠뜨려, canonical 미설정(rel=canonical·sitemap·www→apex 301 전무)으로 구글이 `www.app`을 독립 색인·검색 1위로 노출 → 검색 유입자가 `www.app`에 닿으면 redirect_uri가 `www`로 동적 생성돼 `redirect_uri_mismatch` + host-only 세션 쿠키 분리로 비로그인 랜딩(2026-06-28 발견·해소: 같은 규칙 호스트 조건에 `www.booktimer.app`을 OR 값으로 추가, T-113·N-138). 잔여(후속): canonical 태그·sitemap으로 구글 재색인 가속.**
- [x] **Google OAuth 동의화면 `.app`** (PR #315): 홈페이지·개인정보처리방침 URL `.click`→`.app`. 승인도메인은 `.click`·`.app` 병행 유지(redirect URI 병행 기간이라 아직 안 뺌).
- [x] **자동갱신 정리** (PR #315, Route53): `.app` ON(만료 2027-06-11) / `.click` OFF(만료 **2027-06-02** 자연 폐기).
- [ ] **잔여 ① 기본 SSL 인증서 교체** — `.click` 만료(2027-06-02) **직전**에 ALB 443 기본 인증서를 `.click`→`.app`으로. 지금 기본 인증서가 `.click`이라 `.click` TLS(→301)가 살아있고, 섣불리 바꾸면 깨지므로 만료 임박 시 교체(그래야 `.click` 인증서 삭제돼도 리스너 기본 인증서가 존재).
- [ ] **잔여 ② (선택) Search Console `.app` 등록** — Safe Browsing 선제 평판.
- [ ] **잔여 ③ `.click` 폐기 시(2027-06)** — Google 승인도메인·OAuth redirect URI에서 `.click` 제거.
- 비용: `.app` 도메인 ≈$20/년(`.click`은 자동갱신 OFF로 2027-06 폐기) · ACM 무료.

### AWS 요금 가드레일 — Budgets 월 $50 알람 (완료 ✅ 2026-06-02)

**한 일**: AWS Budgets에 **비용 예산 `booktimer-monthly-50`**(월 $50, 고정, 기본/반복) 생성.
알림 4개 — 실제 50%($25)·80%($40)·100%($50) + **예측 100%**. 수신: 계정 이메일.
콘솔 수동 설정(로컬에 AWS CLI 없음, GitHub Actions OIDC + 콘솔/CloudShell 사용).

- 예상 baseline 월 $30~50(ALB + Fargate 0.25vCPU + RDS micro + Route53, NAT 없음) 상단에 임계치.
- ⚠️ **실측은 이 추정과 크게 달랐다 (2026-07-28, Cost Explorer)** — 실사용 원가가 **월 ~$100**인데
  6월까지 **크레딧이 100% 덮어 청구가 $0**이라 예산 알람조차 울리지 않았다(알람은 청구액 기준).
  7월 크레딧 고갈로 실체가 드러나 $64 청구. 내역: Fargate 2태스크 $36 · RDS $21 ·
  퍼블릭IPv4 $18(**유휴 EIP $3.6 포함**) · ALB $16 · ECR $2.8(이미지 459개, lifecycle 없었음).
  **교훈**: 크레딧이 있는 동안 Budgets(청구액 기준)는 원가를 못 보여준다 — 크레딧 계정에선
  Cost Explorer의 `RECORD_TYPE=Usage`를 따로 봐야 한다.
  → **§ECS Fargate → EC2 단일 인스턴스 이전**으로 월 ~$30 구조 전환 **완료(2026-08-03)**.
  ⚠️ Budgets 임계치($50)는 아직 옛 원가 기준이라 재설정이 남아 있다.
- Budgets는 비용 데이터 하루 ~3회 갱신 → 알림은 실시간 아님(몇 시간~하루).
- 임계치 기준은 **% (예산 대비)** — 콘솔 한글 라벨 "경우를 기준으로 설정됨"이 곧 % 기준(헷갈림 주의).

### ECS Fargate → EC2 단일 인스턴스 이전 (완료 ✅ 2026-07-28 ~ 2026-08-03)

**왜**: 위 실측대로 원가가 월 ~$100. 수입 0인 개인 서비스가 HA(다중 AZ 이중화)에 월 $50을 쓰는
구조라, **HA를 내려놓고 배포 무중단만 유지**하는 단일 인스턴스로 합친다 → 월 ~$30(연 ~$840 절감).

**목표 구성**: EC2 t3.small 1대에 Caddy(443 TLS·호스트 301) + app-blue/app-green(무중단 전환)
+ MySQL 8.4 + 일일 S3 백업. ALB·Fargate·RDS 제거, 퍼블릭 IPv4 4개 → 1개(유휴 EIP 회수).

- [x] **지혈** ✅ 2026-07-28 — autoscaling min 2→1, ECR lifecycle(최근 10개만) → 월 −$20.5
- [x] **코드/설정** ✅ — `compose.prod.yaml` · `Caddyfile` · blue-green 배포 스크립트(테스트 15건, 돌연변이 2종 사살)
      · `render-env.sh`(SSM→.env 매핑 단일출처) · bootstrap/backup · SecurityConfig 헬스 프로브 공개(TDD RED→GREEN)
- [x] **인프라·컷오버** ✅ 2026-07-28 — EC2 생성 → RDS 바라본 채 검증 → Route53 4개 호스트 EIP 전환,
      Let's Encrypt 자동 발급, 실트래픽 200/TLS1.3, OAuth redirect_uri apex(T-113 무회귀)
- [x] **DB 이관** ✅ 2026-07-28 — mysqldump(77MB·30테이블·flyway history 포함) → EC2 MySQL 8.4,
      행 수 정확 대조로 완전 일치 확인. ⚠️ 계정은 덤프에 안 딸려와 수 분 503(T-137)
- [x] **배포 파이프라인 전환** ✅ 2026-07-28 — `deploy.yml`을 ECS → S3 sync + SSM Send-Command로 교체.
      **리소스 삭제보다 먼저** 했다 — 안 하면 main push가 ECS에 배포돼 "성공"인데 실서비스엔 반영 안 되는
      무성 실패가 된다(desired=0이라 안정화가 즉시 성공). 라이브 헬스체크 게이트도 추가.
- [x] **일일 백업 복구** ✅ 2026-08-03 — 정리 착수 전 확인하니 백업이 **엿새간 0건**이었다(T-138). cron을 `ec2-user`로 돌려
      로그(`/var/log`)·`.env`(root:600) 권한이 동시에 막혀 **스크립트가 실행조차 안 됐고**, `BACKUP_BUCKET`도 없었다.
      `root` 실행 + 버킷 계정ID 유도로 해소. 실백업 1건(6.6MB·30테이블·gzip OK) S3 검증, 회귀 가드 8단언 신설.
- [x] **정리** ✅ 2026-08-03 — ALB·리스너·타깃그룹 / ECS 서비스·클러스터 / RDS(최종 스냅샷
      `booktimer-db-final-20260803` 보존) 삭제, autoscaling 등록 해제, `task-definition.json`·
      `autoscaling-config.yml`·`zero-downtime-config.yml` 제거. 되돌릴 수 없는 작업이라
      **사전 점검 4종**(DNS가 EIP인가 / 실서비스 200인가 / ECS 0인가 / 타깃 등록 없는가)을
      통과시키고 지웠다 → **월 ~$30 확정**. 🛑 이 시점부터 DNS 롤백 경로는 없다.
- [x] **Budgets 임계치 재설정** ✅ (2026-08-25 확인) — 한도는 **이미 $40**이었다(실측 `BudgetLimit.Amount=40.0`).
      이 줄만 안 닫혀 있어 다음 세션이 「할 일」로 집어 들었다 — T-211 군 7회차.
      ⚠️ **그걸 확인하다 진짜 결함이 나왔다 — 경보 수신 주소가 오타였다.** 세 알림이 전부
      `itsforaws0320@gmail.com`으로 가고 있었는데 계정 주소는 `itisforaws0320@gmail.com`이다(`is`가 빠졌다).
      AWS가 개설 때 자동 생성하는 `My Zero-Spend Budget`이 **루트 이메일을 쓰기에** 나란히 놓고서야 드러났다.
      **AWS Budgets 이메일은 SNS와 달리 수신 확인 절차가 없어, 주소가 틀려도 오류 없이 조용히 안 간다** —
      즉 한도를 $40으로 조였느냐와 **무관하게 경보는 애초에 아무 데도 도착하지 않았다**. 세 알림의 수신자를
      교체하고 실측 확인(옛 주소 잔존 0건). 현재 임계치 = 90%·100% ACTUAL + 100% FORECASTED(=$36·$40).
      ⚠️ **이름은 `booktimer-monthly-50`인데 한도는 $40이다** — AWS는 예산 이름을 바꿀 수 없어(삭제 후 재생성뿐)
      그대로 뒀다. 콘솔에서 **이름 끝의 `50`을 한도로 읽지 말 것.**
      ※ `My Zero-Spend Budget`(한도 $1·임계치 $0.01)은 실지출 $25.98이라 **영구 `ALARM`** 상태다 —
      AWS 기본 예산이고 지워서 얻는 게 작아 **그대로 두기로 판단**(2026-08-25).

> 실행 런북: [claude-docs/deploy-ec2.md](claude-docs/deploy-ec2.md) · 구 아키텍처: [deploy-aws.md](claude-docs/deploy-aws.md)(리소스 삭제됨 — 역사 기록)
> ⚠️ 수용한 한계: 단일 장애점(인스턴스 다운 = 서비스 다운) · 배포 시 in-flight 요청 1건 끊김 가능
> (로컬 150요청 프로브 실측) · 백업 입도 24시간(RDS PITR 상실).

### 세션 외부화 — Spring Session JDBC (완료 ✅ 2026-06-02)

**왜**: 배포(태스크 교체) 때마다 **재로그인** 발생. 원인은 세션이 **JVM 메모리**(기본 `HttpSession`)에
저장돼 태스크가 죽으면 통째로 사라지기 때문. 게다가 태스크를 2개 이상으로 늘리면(무중단·스케일아웃)
요청이 분산돼 *평소에도* 세션이 오락가락 → 세션 외부화는 무중단/스케일의 **전제**다.

**한 일**: 세션을 RDS(MySQL)에 저장 — `spring-boot-starter-session-jdbc`(Boot 4는 autoconfig가 별도
모듈이라 raw `spring-session-jdbc`만으론 빈 미생성, N-024 패턴) + Flyway **V2**로 `SPRING_SESSION`·
`SPRING_SESSION_ATTRIBUTES` 생성. 운영은 `spring.session.jdbc.initialize-schema=none(never)`로 Flyway가
스키마 단일 소스, 테스트 H2는 embedded 자동 초기화. 개념 **N-029**, 함정 **T-020**.

- **새 인프라·추가 비용 0** (기존 RDS 재사용). 이 규모엔 성능 충분.
- ⚠️ **이 배포 직후 1회는 전원 재로그인** — 세션 쿠키 이름이 `JSESSIONID`→`SESSION`으로 바뀌고 기존
  인메모리 세션은 어차피 소멸. 이후부터는 배포에도 로그인 유지.
- **(향후) Spring Session + Redis(ElastiCache)로 전환** — 트래픽/세션 쓰기가 늘면 DB 부하·지연 측면에서
  Redis(인메모리, TTL 네이티브)가 유리. JDBC→Redis는 의존성·설정 교체로 비교적 단순. 지금은 비용(예산 $50)
  고려해 JDBC 유지, 전환은 트래픽 신호가 오면.

#### 세션 비활성 타임아웃 30일 — 독서 중 로그아웃 해결 (완료 ✅ 2026-06-07)

**증상(실사용)**: 책 읽다 "측정 종료"를 누르려니 로그아웃됨, ~50분 비웠다 오니 재로그인 요구.
**원인**: 타임아웃이 코드에 없어 **Spring Boot 기본 30분**. 게다가 **독서 타이머는 클라이언트(JS)에서만 1초마다
돌고 읽는 동안 서버 요청이 0**이라 서버가 "노는 사용자"로 보고 30분 뒤 세션을 끊는다 → *오래 읽는 핵심 사용자일수록*
더 잘 터지는 최악 패턴. (측정 중 `ReadingSession`은 DB에 남아 재로그인하면 살아 있으나 끊김 자체가 마찰.)
**해결**: 비활성 타임아웃 **30일** + 쿠키 Max-Age **30일**(브라우저 닫아도 유지). 세션이 MySQL 외부화돼 있어 길게 잡아도 비용 0.
- ⚠️ **Boot 4 함정**: `server.servlet.session.timeout=30d` 프로퍼티는 **Spring Session JDBC 저장소에 안 먹는다**(기본 30분 그대로 — cookieSerializer·ForwardedHeaderFilter와 같은 계열). → `WebConfig#sessionTimeoutCustomizer`(`SessionRepositoryCustomizer`)로 저장소에 직접 `setDefaultMaxInactiveInterval`, 쿠키 Max-Age는 `cookieSerializer.setCookieMaxAge`. 프로퍼티는 의도 문서화용으로 남김.
- TDD: `SessionCookieSameSiteTest`에 ① `createSession().getMaxInactiveInterval()==30일`(프로퍼티 무동작이면 30분이라 Red로 함정 포착) ② 로그인 응답 SESSION 쿠키 `Max-Age=2592000` 추가.
- (옵션·후속) 측정 중 keepalive 핑 — 30일도 넘기는 초장기 비활성까지 막진 못하나 현 요구엔 불필요.

### 무중단 배포 — ECS 롤링 deploymentConfiguration (적용·검증 완료 ✅ 2026-06-02)

- **증상**: 배포 시 홈페이지가 잠깐 먹통(503), 버튼 동작 반영 안 됨. 원인: 단일 태스크(`desiredCount=1`)가
  **죽고→새로 뜨는 공백** 동안 ALB에 healthy 타깃이 없음. (재로그인 문제는 위 세션 외부화로 **별도 해결**됨.)
- **한 일**: ECS 서비스 `deploymentConfiguration`을 멱등 적용하는 워크플로 신설
  (`.github/workflows/zero-downtime-config.yml`, `workflow_dispatch`):
  - `minimumHealthyPercent=100` + `maximumPercent=200` → desiredCount=1이어도 새 태스크를 **추가로** 띄워
    ALB 헬스 통과 후에야 옛 태스크 드레인 → 교체 중 항상 healthy 태스크 ≥1.
  - `deploymentCircuitBreaker{enable=true, rollback=true}` → 새 태스크 안정화 실패 시 **자동 롤백**.
  - 한 번 적용하면 영속(매 배포는 task def만 교체, deploymentConfiguration은 안 건드림 → 드리프트 없음).
  - **코드 변경 없음**(앱), OIDC 역할의 기존 `ecs:UpdateService` 권한으로 충분.
- **검증 완료 ✅**: 워크플로로 설정 적용 후 실배포를 돌리며 `https://booktimer.click/actuator/health`를
  1초 주기로 폴링 — 배포 도중·종료까지 **끊김 없이 200**(503 없음). 재로그인도 없음(#73과 결합).
- **선택적 후속(미적용)**: 타깃그룹 `deregistration_delay`(기본 300s→60s)·헬스체크 간격(30s→15s) 단축은
  교체 *속도* 최적화일 뿐 다운타임 원인은 아님. `elasticloadbalancing:Modify*` 권한이 필요해
  현재 OIDC 역할로는 불가 — 적용 시 deploy-aws.md의 해당 절(IAM 권한 추가 + CloudShell 1회) 참고.

### 관측성(모니터링) — 감시는 켰다 ✅ / 메트릭 대시보드는 보류 ⏸ (2026-08-18)

**발단**: 「Prometheus + Grafana 도입은 어떻게 생각하냐」(성능 측정·모니터링이 백엔드에서 중요하다고 들어서).
답을 코드에서 찾았고, **감시(alerting)와 대시보드(metrics)를 분리해** 앞엣것만 지금 켰다.

- **✅ 켠 것 — 가동 감시**: `.github/workflows/uptime.yml`(10분 cron) → `deploy/health-probe.sh` →
  `https://booktimer.app/actuator/health`. 실패하면 Actions 런이 빨개지고 **GitHub이 메일**을 보낸다.
  서버 메모리 0·요금 0(공개 레포)·새 계정 0. 그 전까지 운영 감시는 **배포 직후 `deploy.yml`이 health를
  한 번 curl하는 것뿐**이어서, 새벽에 죽으면 직접 열어보기 전까지 알 길이 없었다.
  - **판정을 HTTP 코드만으로 하지 않는다** — 도메인이 남의 곳을 가리키거나 앞단이 파킹·에러 페이지를
    200으로 돌려주면 코드만 보는 감시는 장애를 통과시킨다. 본문에 `"status":"UP"`이 있어야 성공이다
    (T-150에서 배운 「성공은 exit code가 아니라 응답 내용으로 판정」의 재적용).
  - **재시도 3회가 계약이다** — 오탐이 감시를 죽인다(멀쩡한데 메일이 오면 두 번째부터 안 읽는다).
    blip 한 번은 침묵, 연속 실패만 쏜다. `deploy/tests/test-health-probe.sh`(7단언·돌연변이 2종 사살)가 지킨다.
  - ⚠️ **감시기 자체의 사각**(알고 쓴다): 스케줄 정시 보장 없음(수 분~수십 분 지연) · GitHub이 죽으면
    감시도 죽음(2026-08-17 API 대장애 실측) · **레포 60일 무활동 시 GitHub이 스케줄을 자동 정지**.
    셋 중 하나가 아파지면 그때가 유료·외부 감시로 올라갈 신호다.

- **⏸ 보류한 것 — Prometheus + Grafana self-host**: 운영은 **EC2 t3.small 1대(2GB)**이고 메모리 예산이
  이미 다 팔렸다 — 평시 `mysql 450 + app 550 + caddy 30 + OS 200 = 1.23GB`, **배포 순간 app이 2개가 되어
  1.78GB**이고 스왑 2GB가 그 순간을 흡수하도록 설계돼 있다(`compose.prod.yaml`·`bootstrap-ec2.sh`).
  Prometheus(200~400MB) + Grafana(150~250MB)를 얹으면 배포 순간에 **OOM killer가 MySQL을 잡는다** —
  `bootstrap-ec2.sh`가 스왑을 만든 이유가 정확히 그 시나리오다. **모니터링을 붙였다가 DB가 죽는 건 최악의 거래.**
  - 게다가 지금 병목은 p99 레이턴시가 아니다 — **신규 코호트 실독서(5분+) 0건**이고, 계측 예산은 이미
    그쪽(전환 이벤트 2종·대표 전환 교체)에 쓰고 있다.
  - **절반은 이미 있다**: `spring-boot-starter-actuator`가 있어 Micrometer가 앱 안에서 이미 돌고, 운영은
    `management.endpoints.web.exposure.include=health`로 health만 열어 뒀다. 즉 없는 건 **저장소와 그림**뿐이라
    나중에 붙일 때 앱 쪽 작업은 사실상 한 줄이다(급할 이유가 하나 더 없다).
  - **재개 트리거**: 인스턴스가 2대 이상 될 때 · 로그로 답할 수 없는 질문이 실제로 생길 때 · 인스턴스를 키울 때.
  - **그때의 경로는 self-host가 아니라 Grafana Cloud 무료 티어** — 저장·UI를 남의 메모리로 넘기고 우리 박스엔
    스크래이프 에이전트(≈50MB)만 둔다. t3.small에서 현실적인 유일한 길이다.
  - **학습 목적이면 로컬이 낫다**: `load-test/booktimer-load.js`(k6)가 이미 있으니 로컬 compose에
    Prometheus·Grafana를 붙여 부하를 걸며 꺾이는 지점을 보는 것이, 트래픽 없는 운영의 평평한 그래프보다 배움이 크다.
  - ⚠️ 곁가지: 그 k6 스크립트의 주석이 **ECS 오토스케일링(태스크 2→3→4) 검증**을 목적으로 적고 있는데
    지금 운영은 EC2 1대 + docker compose다 — 전제가 낡았다(별건으로 정리 대상).

---

## 📖 기능 로드맵

### 독서 잔디 (컨트리뷰션 그래프) — 완료 ✅ 2026-06-02
- GitHub 잔디 스타일 1년치(53주 × 7요일) 히트맵을 `/history` 상단에 렌더. 일자별 독서 시간을
  색 농도 0~4로 표시(0/≤15분/≤30분/≤60분/초과 — 상수라 조정 쉬움), 상단 월 라벨·좌측 요일 라벨·hover 툴팁.
- 데이터는 기존 `ReadingHistoryService.dailyHistory` 재사용. "오늘"은 Clock+유저 타임존(N-010).
- 순수 빌더 `ContributionGraphBuilder`(스프링 무관)로 그리드 계산 → 경계값 단위테스트. 렌더는 Thymeleaf+CSS grid.
- **연속일(streak) → 성장 잔디로 완료 ✅ 2026-06-08 (PR #255)**: 연속 독서 일수에 따라 잔디 카드 우측 상단 식물이 🟫땅→🌱새싹(1~3)→🌷꽃(4~13)→🌳나무(14+)로 자란다(신규 enum `GrowthStage` + 빌더 `currentStreak()`, 오늘 유예·끊기면 리셋). 대시보드·히스토리·프로필 잔디에 노출.
- (남은 후속 아이디어) 색 단계 사용자 데이터 분포 기반 조정.

### 독서 마을 — 잔디를 게임화한 "수집형 마을"로 (아이디어 📝, 2026-06-10 / 사용자 대면 명칭·URL 마을/village로 리네임 ✅ 2026-06-18)

> **상태**: 비전은 캡처·일부 진행 중. **트랙 A 1단계(MVP)는 출하 완료 ✅** (PR #332), **트랙 A 수집 확장(장르 매핑 식물)도 출하 완료 ✅** (아래 「장르 매핑 출하분」), **트랙 B 메커닉(숨은 레시피 발견)도 베타 출하 완료 ✅** (아래 「트랙 B 출하분」 — 레시피 큐레이션은 커스텀 카탈로그 단계로 연기), **도감 전용 페이지(`/garden`)도 출하 완료 ✅** (아래 「도감 전용 페이지 출하분」 — 대시보드는 요약+링크로 경량화, 잔디 흡수는 후속), **작가·출판사 다양성 축도 출하 완료 ✅ — 트랙 A 매핑 3축(장르·작가·출판사) 완성** (아래 「작가·출판사 다양성 출하분」), **식물 배치(꾸미기)도 경량 SSR로 출하 완료 ✅** (PR #345 — 「리치 UI ②」 결정을 전환 묶음에서 경량 SSR 선출하로 갱신, 좌표 API·스키마는 SPA 전환 시 재활용), **무대화(A0)·생명감(A1)·SVG 승격(A2)·인터랙션(A3 드래그+휠 줌)도 출하 완료 ✅ — 미니게임 퀄리티 로드맵 A 단계 진행** (A0 PR #346 이모지 유지·에셋 0 순수 CSS 무대 / A1 PR #349 은은한 상시 모션 / A2 PR #351 시간축 14종 이모지→코드 벡터 SVG·타 축 폴백 / A3 PR #353 Pointer Events 드래그(빈 칸 이동·격자 스냅)+휠 줌(1~2.5배)·점유 칸 무효 복귀·탭 폴백 유지 / A3 후속 PR #355 점유 칸 스왑·캔버스 밖 드롭=거두기(드롭 결정 순수 함수 `resolveDrop` 추출)로 드래그 모델 완성, 아래 「비주얼·기술」 A0→B 로드맵). **A3는 격자 스냅을 지켜 B 전환 트리거(자유 픽셀 `x,y`)를 의도적으로 회피** — part-sway(모션) 등은 후속, 자유 좌표·실시간 협업 등 풀 리치 UI는 여전히 프론트 전환 스코프. **편집 모드 카메라 줌·팬도 출하 완료 ✅** — Phaser 편집 씬에 휠 줌(커서 포컬)·빈 공간 드래그=팬·핀치 줌(2포인터 중점 포컬) 추가, 저장·서버·보기 모드 무변경(PR #376, N-090). "열린 질문"은 *아직 정하지 않은* 것들(트랙 A·B로 일부 해소됨 — 데이터 모델·발견 저장·파밍 가드). **식물 접지 패스(발밑 앵커+접지 그림자)도 출하 완료 ✅** — kind='plant'를 발밑 앵커·shadowLayer 접지 그림자로 땅에 박음, 데코는 현행 중심 앵커 유지(아래 「식물 접지 패스 출하분」).
>
> **컨셉 전환 (2026-06-24) ✅ 완료**: 식물 4축(TIME·GENRE·DIVERSITY·RECIPE)·소품(Decoration) 제거 → **작가(배회 캐릭터)·건물(배치)만** 남기는 마을로 전환. **PR-1(#468, 프론트)**: GardenGame.vue·GardenDex.vue에서 식물·소품 팔레트·섹션 제거, scene.ts BUILDING-only 필터(고아 행 렌더·재저장 차단). **PR-2(#470, 백엔드·대시보드)**: Plant/GenrePlant/DiversityPlant/RecipePlant/Decoration Java 클래스 27개 삭제(DB 보존·소프트 제거), GardenView 20-arg→6-arg(2축), CatalogDto 22→7필드, layoutOf() 명시 BUILDING 필터(defense-in-depth), 대시보드 GardenPanel.vue 작가·건물 카운트로 전환. 1120 tests 100% 통과. 재매핑(읽기 차원→오브젝트) 및 건물 tier 진화는 후속.
>
> **먹이주기 루프 v1 ✅ 출하 완료 (2026-06-24)**: 가지치기로 제거된 TIME(매일 보상)을 **자원 루프**로 복원 — 매일 목표 달성 → 먹이 +1(하루 1상한) → 배회 작가 탭 → 먹이 1 소비 + 정(affection) 축적. **PR-1(#474, 백엔드)**: `DailyQuotaCalculator`(달성일 유도, 부채 모델 정합), `AuthorAffection` 엔티티·`author_affection` 테이블(V52), `FeedingService`(foodBalance·affectionByCharacter·feed), `POST /api/garden/feed`, `GardenApiResponse`에 `foodBalance`·`affection` 추가. TDD 전 통과. **PR-2(프론트)**: VillageApp HUD 🍙 먹이 잔액 표시, scene.ts 보기 모드 배회 작가 `setInteractive` + `gameobjectup` 탭 핸들러(팬 오발 방지 `_panMoved` 재사용), 반응 하트 떠오름 tween(독립 Text 오브젝트 — update() 덮어쓰기 함정 회피), GardenDex 도감 작가 칸 ❤️ N 정 표시. 547 기존 테스트 그린 유지. **PR-3(정 진화 v1)**: `AffectionLevel` record(feedCount → level 0~5·title·nextThreshold 순수 유도, 단일 출처), `FeedResult` 3필드→6필드(+level·title·leveledUp), `FeedingService.feed()` before/after 레벨 비교로 leveledUp 산출, `GardenApiResponse` AuthorCharacterDto/OwnedCharacterDto에 level·title 추가. 프론트: DexCell 배지 "Lv{n} {칭호} ❤️{count}" 형식, scene.ts `playFeedReaction` leveledUp 시 ✨별+칭호 tween(T-084: `this.objs` 제외로 update() 덮어쓰기 회피). TDD: AffectionLevelTest 11개 경계값·FeedingServiceTest 4개·GardenApiControllerTest·FeedApiControllerTest 추가, 161 Java 테스트·547 vitest 전부 GREEN.
>
> **트랙 A 1단계 출하분 (2026-06-13, PR #332)**: 타이머 할당량 기반 식물 수집의 **읽기전용 MVP**. ① **데이터 모델 = 식물 카탈로그(`plant`) 정적 시드 1장(V35·14종)뿐** — 보유 이력 테이블 없음. ② **보유를 저장하지 않고 독서 실적의 함수로 유도** — "누적 목표 달성일 ≥ 식물 임계"면 보유(부채 모델 N-058·Lazy accrual N-001과 같은 철학). 해금일·NEW(최근 7일)도 달성 날짜 정렬로 유도. ③ 해금 기준 = **그날 목표를 채운 날의 누적 수**(잔디 level4 판정·`GoalSchedule` 소급 방어 N-059 재사용). ④ 화면 = **대시보드 잔디 카드 안 접힌 `<details>` 베타 패널**(순수 CSS 토글, 별도 `/garden` 페이지 없음 — *당시 사실*; 이후 도감 전용 페이지로 분리·대시보드는 요약화, 아래 「도감 전용 페이지 출하분」) — 🚧 베타 배너 + 미니 정원(보유 이모지) + 도감(보유=이모지+이름/+NEW, 미보유=🔒+목표일) + 진척·다음 해금. 패키지 `com.booktimer.garden`(`Plant`/`PlantRepository`/`PlantUnlockCalculator`(순수·전수 TDD)/`GardenView`/`PlantState`/`GardenService`) + fragment `fragments/garden.html :: panel`. **잔디와 공존**(잔디 무변경).
>
> **트랙 A 장르 매핑 출하분 (2026-06-13)**: 시간축에 더해 **장르 수집축**을 추가 — "무슨 책을 읽든 같은 14종"이던 동기 부재를 푼다(매핑 차원 3축 중 **장르** 먼저). ① **별도 카탈로그 `genre_plant`(V36·12장르 + 폴백 들꽃 1종)** — 시간축 `plant`/`PlantUnlockCalculator`/V35는 **완전 무변경**(회귀 0, 설계 옵션 B). ② 해금 기준 = **그 장르의 책을 1권 완독(`FINISHED`)** — 완독만 집계해 파밍을 막고 책BTI와 신호 일치(사용자 결정). 장르 대분류는 `ReadingProfileAggregator.primaryGenre` 재사용(garden→personality 의존 신설). ③ `genre_label`이 시드 어디에도 안 맞는 장르를 완독하면 **폴백 식물(들꽃)** 보유 → "읽었는데 0개" 허무 방지. category null(수동 등록) 완독책은 제외(N-055). ④ 화면 = 같은 베타 패널에 "수집 식물(장르)" 섹션 추가(보유=이모지+장르명 / 미보유=🔒+장르명). 신규 `GenrePlant`/`GenrePlantRepository`/`GenrePlantState`/`GenreUnlockCalculator`(순수·전수 TDD) + `GardenService`·`GardenView` 확장. **시간축·잔디와 공존**.
>
> **트랙 B 메커닉 출하분 (베타, 2026-06-13)**: 완독+읽고싶음 책의 **조합**을 발견하면 식물을 얻는 **숨은 레시피**. 트랙 A와 두 가지가 근본적으로 다르다 — ① **발견을 저장한다**(`user_discovered_plant`): 발견은 "처음 만족된 순간"의 이벤트라 박아야 하고(NEW·토스트), 책장이 바뀌어도(읽고싶음 책 이동·삭제) 보유가 유지돼야 하기 때문(트랙 A는 저장 안 하고 유도). ② **조건을 숨긴다**: 어떤 조합인지 안내하지 않고 미발견은 "???" 미스터리 슬롯으로만 노출(발견 자체가 재미). **메커닉**: 조회(`GardenService.view`, 대시보드 진입) 시 책장 스냅샷으로 레시피 평가 → 새 발견 저장(조회가 쓰기 유발이라 view를 읽기-쓰기 트랜잭션으로; `uk(user,plant_code)`+`existsBy`로 멱등). **파밍 가드**: 모든 레시피는 완독 ≥1 필수 — 읽고싶음만으론 해금 불가(WANT_TO_READ는 저비용 행동). 레시피 정의는 코드 상수(`RecipeDefinition`, 임의 술어라 SQL화 부자연), DB(`recipe_plant`/V37)엔 결과 식물 표시 메타만 시드. 신규 `RecipePlant`/`RecipePlantRepository`/`RecipeDefinition`/`RecipeEvaluator`(순수·전수 TDD)/`ShelfSnapshot`/`UserDiscoveredPlant`/`UserDiscoveredPlantRepository`/`DiscoveredPlantState` + `GardenService`·`GardenView` 확장 + 같은 베타 패널에 "숨은 레시피" 섹션(토스트·??? 슬롯). **⚠️ 레시피 큐레이션(8종)은 베타 예시** — 메커닉 검증용일 뿐 확정이 아니며, 식물 카탈로그를 직접 커스텀(관리자 입력)으로 관리하는 단계에서 식물·레시피를 함께 교체한다(사용자 결정, 아래 열린질문 "정적 시드 vs 관리자 입력"). **트랙 A·잔디와 공존**.
>
> **도감 전용 페이지 출하분 (2026-06-13)**: 정원 3트랙 메커닉(시간·장르·레시피)이 다 깔렸으나 담는 그릇이 대시보드 잔디 카드 패널 하나라 (a) 수집의 재미가 시각적으로 안 살고 (b) 패널이 4섹션으로 길어 대시보드가 비대하던 것을, **별도 `/garden` 전용 페이지 = 도감 본진**으로 키우고 대시보드 카드는 **요약+링크로 경량화**. ① 신규 `web.GardenController`(`@GetMapping("/garden")`) — 대시보드와 **같은 `GardenService.view` 재사용**(서비스·뷰모델·계산기·엔티티·마이그레이션 전부 무변경 → 회귀 0), `/garden`은 permitAll 목록에 없어 default-deny가 자동 인증 보호. ② 신규 `templates/garden.html` — brand 헤더 + 전체 진척 한 줄 + **축 탭(전체/시간/장르/숨은 레시피) + 보유/미보유 필터**(데이터가 이미 다 내려와 있어 Alpine `x-show` 클라이언트 토글, 서버 왕복 0; no-JS면 x-show가 무시돼 전부 보이는 **점진 향상** 폴백) + 3축 그리드 + 대시보드 링크. ③ fragment `panel`→`summary` 경량화 — 대시보드 잔디 카드엔 미니정원 + 3축 진척 한 줄 + "정원 도감 →" 링크만(전체 도감 그리드는 페이지로 이전), 잔디 토글 인프라(`.garden-host`/`.garden-toggle`)·발견 토스트 유지(**잔디 회귀 0**). ④ **잔디 흡수(53×7 히트맵 제거)는 이번 범위 밖** — 정원 완성도가 더 오른 뒤 후속(아래 「도입 순서」와 정렬). ⑤ 클릭 리치 상세는 `Plant`/`GenrePlant`에 설명 컬럼이 없어(메타 빈약) 1차 생략·hover title 대체 — 식물 커스텀 카탈로그 단계에서 메타와 함께 보강. TDD: `GardenControllerTest`(MockMvc — 미인증 보호·인증 200+뷰+모델·현재 유저 해소) RED→GREEN, 탭·필터 등 순수 시각은 preview 게이트. **트랙 A/B·잔디와 공존**, 전체 회귀 0.
>
> **작가·출판사 다양성 출하분 (2026-06-14)**: 매핑 3축의 마지막 축. 시간축(누적일)·장르축(라벨 1:1)에 더해 **완독 책의 서로 다른(distinct) 작가/출판사 수 ≥ 임계**로 식물을 유도(완독만 집계 → 파밍 방지, 시간축과 같은 부채 모델 N-058 — 보유 저장 안 함). 작가/출판사는 **열린 집합(수천)**이고 표기가 미정규화라 장르식 1:1 라벨 시드가 불가·불신 → **다양성 카운트**로 설계(사용자 결정). 작가·출판사가 같은 카운트 메커닉이라 **한 카탈로그 `diversity_plant`(V38)에 `kind`로 구분**(작가 7종 임계 1·3·5·10·15·20·30 + 출판사 5종 1·3·5·8·12, 한 출판사가 여러 책을 내 낮은 임계). `Book.author`/`publisher`는 적재 시 정규화 안 됨(`category`와 달리 `blankToNull` 없음)이라 `distinctCount`가 strip+빈/null 제외를 맡음(N-055). 시간축 `plant`·장르축 `genre_plant`·레시피축 `recipe_plant` **전부 무변경**(회귀 0). 신규 `DiversityKind`/`DiversityPlant`/`DiversityPlantRepository`/`DiversityPlantState`/`DiversityUnlockCalculator`(순수·전수 TDD) + `GardenService`·`GardenView`(+diversityPlants·작가/출판사 보유·전체 카운트) 확장. 화면 = `/garden`에 "🖋️ 작가·출판사" 탭 + 작가/출판사 두 소그리드(`kind` 분리)·대시보드 요약 묶음 표기, 기존 `.garden-axis`/`.garden-grid`/`.plant-cell` 재사용(새 CSS 0). **트랙 A 3축(시간·장르·작가출판사) 완성**, 잔디·트랙 B와 공존. (PR #344)
>
> **작가 캐릭터 도감 출하분 (2026-06-16)**: 수집 도감에 **작가 캐릭터 축(`PlacementAxis.AUTHOR`)** 신설 — 완독책의 작가명을 normalize·contains 매칭으로 20종 시드 카탈로그와 연결해 캐릭터 해금. `AuthorCharacterUnlockCalculator.normalize()` = 알라딘 원문 `"한강 (지은이)"` 형태에서 괄호역할군(`지은이`·`옮김`·`엮음`·`저` 등) 제거 + 공백 제거 → `"한강"` → `contains(normalizedMatch)` 부분매칭. 빈 matchName 누수 가드(N-055) 포함. `V45 author_character` 테이블 + 국내 6종(한강·김영하·박경리·조정래·정유정·김초엽) + 해외 14종(무라카미 하루키·베르나르 베르베르·헤르만 헤세 등) 시드. `garden.html`에 "🧑 작가캐릭터 N/M" 진행 바·필터 탭·도감 섹션 추가. TDD 24개(Calculator 18 + Service 3 + LayoutService 3) RED→GREEN. 실 브라우저: 한강 완독 등록 → 1/20 해금 · Phaser 꾸미기 배치→저장→F5 리로드 유지 · 콘솔 에러 없음(CLAUDE.md T-053 게이트). (PR #372)
>
> **격자 좌표계 1차(파생·직교) 출하분 (2026-06-17)**: N-089 CoC 방향 확정 후 첫 격자 슬라이스. **저장 모델 = A(파생 격자·스냅만)** — 0~1 저장 유지, 격자는 편집기 스냅·화면 비주얼에만 존재(**서버·DB·마이그레이션 0, 가역적**). 직교 사각 격자 먼저, 아이소 전환은 2차. `@free-pure-core`에 `GRID_COLS=20·GRID_ROWS=16`(50px 정사각 셀) + `cellOf`/`cellCenter`/`snapToCell` 3 순수 함수 추가. Phaser 편집 씬: `drawGrid()`(옅은 녹색 격자선, depth 0.5) + `drag` 라이브 스냅(`pixelToNorm→snapToCell→normToPixel`) + 스폰 셀 중심 초기화. 저장 계약(0~1 정규화)·서버 무변경. TDD RED→GREEN: `free-pure.test.mjs` 60 단언(격자 22 신규). 실 브라우저 하니스 검증: 격자선 비주얼·콘솔 에러 0. (PR #375)
>
> **아이소메트릭 전환 출하분 (2026-06-18)**: #375(직교 격자) 예고한 "아이소 2차" 완료 — CoC 방향(메모리 `garden-vision-coc-zoo`) 정의적 룩 달성. **저장 모델 옵션 A 계속**(0~1 정규화·서버·DB·마이그레이션 0, 투영 레이어 교체만). `normToIso(x,y,f=0.5)↔isoToNorm(sx,sy,f)` + px 편의 2함수 순수 코어 추가. Java `GardenIsoProjection.screenX/Y/Percent` + `PlacedItem.isoLeftPct/isoTopPct`. Phaser: 배경을 수평 띠→**다이아몬드 잔디 바닥**, 격자선을 축정렬→**아이소 사선 격자**, 투영 호출 6곳 `normToIsoPixel/isoPixelToNorm`으로 교체(스냅·카메라 무변경). SSR `th:style`: `isoLeftPct()/isoTopPct()` 적용으로 **view=edit 투영식 동기화 보장**. `app.css`: `.garden-view::before` CSS `clip-path: polygon(50% 25%, 100% 50%, 50% 75%, 0 50%)` 보기모드 다이아몬드 바닥. TDD RED→GREEN: JS 96단언(전체 `free-pure.test.mjs`) + Java 9단언(`GardenIsoProjectionTest`) + 전체 1009 BUILD SUCCESSFUL. 실 브라우저: 다이아 격자·바닥 ✅·콘솔 0. (PR #380)
>
> **식물 접지 패스 출하분 (2026-06-18)**: kind='plant' 아이템을 **발밑 앵커** 빌보드로 땅에 박아 아이소 접지감 완성 — 이전 중심 앵커(`translate(-50%,-50%)`)는 식물이 "뜬 스티커"처럼 보였음. **4곳 동기화**: ① SSR `th:classappend` gv-plant/gv-decor 분기 + `th:style` plant→`-100%`·decor→`-50%` 발밑/중심 앵커 분기. ② Phaser `spawnObject` `setOrigin(0.5, isPlant ? 1 : 0.5)`. ③ `shadowLayer`(depth 0.6, 격자 0.5 < 그림자 < 식물 1+) `drawShadows()` 접지 타원(`ISO_FLATTEN=0.5`). ④ `app.css` `.gv-plant { transform-origin: bottom center }` + `.gv-plant::after` 발밑 그림자(데코 공통 `::after` 현행 유지, CSS specificity 2-class 덮어쓰기). Java 0 변경·스키마 0·저장 계약 무변경. 실 브라우저: 데코 중심 앵커 유지·CSS 그림자 보존 ✅. (PR #382)
>
> **캐릭터·건물 SVG 승격 — 파일럿 (2026-06-19)**: 작가 캐릭터(🧑 20종)·출판사 건물(🏢 10종)을 식물 A2 패턴(이모지→코드 벡터 SVG)으로 승격 착수. **렌더 파이프라인은 이미 100% 배선됨**(`scene.ts` `spawnCharacter`/`spawnObject`·`DexCell.vue` 모두 `spriteId` 있으면 SVG·없으면 이모지 폴백) → 작업 본질 = **`<symbol>` 그리기 + `sprite_id` 시드**(엔티티·Vue 변경 0; scene.ts는 아래 접지 보정 1줄만). 이번 PR은 **파일럿 2종**(작가 `han_gang`·건물 `minumsa`)만 승격해 **32px 가독성·접지·배회 룩**을 실 게임에서 선검증(N-080 = 대표 소수 고유 + 나머지 제네릭, 30종 전부 강행 금지). 신설 fragment `garden-character-sprites.html`(한강 = 흑발+자주 코트+가슴의 책, 발밑 y≈31·중앙 x16, 발밑 작은 타원 그림자로 접지)·`garden-building-sprites.html`(민음사 = 아이소 코너뷰 석조 건물+남색 지붕·간판). **건물 접지 = footprint 정합 + 그림자 생략(실 게임 피드백 보정)** — 실 게임에서 건물이 떠 보였는데(① 게임 Phaser 발밑 그림자는 얇은 식물용 크기 `plantPx*0.55`라 넓은 박스 바닥보다 좁아 "그림자 위에 뜬" 인상 ② 밑면 footprint 각도가 타일 다이아와 불일치), **건물 밑면 다이아를 타일 각도(기울기≈0.4)·앞코너 발밑 앵커로 정합**하고 `scene.ts drawShadows`가 **건물(axis=BUILDING)만 식물용 캐스트 그림자를 생략**하게 해 CoC식 flush 접지로 바꿈(식물·소품 그림자는 유지). scene.ts 변경이라 번들 재빌드(T-063). `garden.html` `<th:block>` 2줄 주입, V48 `sprite_id` UPDATE(파일럿 2행만 — V47 선점으로 V48 조율). 회귀 가드 = `FlywayMigrationTest`에 **부분 승격 불변식**(파일럿 2종 `sprite_id=code`, 나머지 작가/건물 `sprite_id=null` 폴백 보존, N-055 null-state) RED→GREEN. 실 브라우저 게이트(T-053/054): 배회 작가 SVG(이모지 아님)·발밑 접지·배회 / 건물 배치 SVG·타일 flush 접지 / 도감 보유 셀 SVG·미보유 🔒 / 콘솔 0. **후속 PR = 대표 고유 + 제네릭 확장**. 절차 걷기 애니·프로 아트는 같은 `spriteId` 위 후속. 비전·배경은 메모리 `garden-vision-coc-zoo`.
>
> **캐릭터·건물 SVG 승격 — 나머지 전종 확장 완료 ✅ (2026-06-19)**: 파일럿이 확정한 룩·구조를 복제 확장해 **작가 20종·건물 10종 전부 코드 벡터 SVG로 승격 완료**(이모지 폴백 종 0). 파일럿이 증명한 파이프라인(렌더 배선 완비)을 그대로 따라 **`<symbol>` 그리기 + `sprite_id` 시드**만 했다(엔티티·`scene.ts`·Vue 변경 0 → 번들 재빌드 불요). 아트 = N-080(**대표 고유 + 베이스 변형**): 작가는 발밑 기준 베이스 아바타(머리·옷색·소품)에 대표 작가만 모티프 고유 디테일(무라카미 고양이·카뮈 태양·톨스토이 흰 수염·카프카 갑충·헤세 팔레트·도스토옙스키 촛불 등); 건물은 민음사의 타일 flush footprint 기하를 복제하고 지붕형·벽색·층수로 변형(오피스 유리·성 흉벽·동양 누각·공사장 비계 등). V49 `sprite_id=code` UPDATE(나머지 28행). 회귀 가드 = `FlywayMigrationTest` 불변식을 **부분 승격→전종 승격**으로 전환(`author_character`·`building` 전 행 비null·`=code`, 새 시드 누수 가드 N-055) RED→GREEN. 검증 = 30종 게임 렌더 크기 자가 점검(32px 가독성 게이트 — 사용자 확인) + 실 브라우저(T-053/054). 절차 걷기 애니·프로 아트(저폴리 팩/3D)는 같은 `spriteId` 위 후속.
>
> **마을 여백 장식 — 물 베이스 + void 앰비언트(섬·바위·나무·수련) ✅ (2026-06-24)**: 줌아웃(containZoom) 시 드러나는 마름모 밖 75% void가 단색으로 휑했던 문제 해소. `drawBackground` 베이스색을 `--garden-water`(#6FA8C7, CoC 바다 톤)로 교체해 잔디 섬이 물 위에 떠 있는 룩 완성. 신규 `drawAmbientDecor()`가 `pure.ts`의 결정적 배치표 `AMBIENT_DECOR`(10개 screen-norm 큐레이션)를 순회해 depth 0.1~0.4 전용 밴드에 스폰 — `this.objs` 미포함(격자·저장·배회·드래그와 격리). `garden-ambient-sprites.html` 4종 SVG(amb_island/rock/tree/lily) 신설·주입. CSS `--garden-water` 토큰 + village letterbox(`#village-app`·`.village-shell`·`.village-game-root`) 배경 물색 정합(투명 캔버스 이음새). TDD: `isInsideDiamond` + `AMBIENT_DECOR` 불변식(마름모 밖·inset 안·sizeFactor·kind 등) 20종 vitest RED→GREEN(453 tests). 후속: 물결 애니·앰비언트↔객체 depth interleave(v1은 장식 항상 객체 뒤), 앰비언트 좌표 데이터 연동.
>
> **마을 캐릭터 절차 걷기 애니 — D 폴리시 (통짜 transform) ✅ (2026-06-19)**: SVG 승격(#407·#408)으로 통짜 스프라이트가 깔린 위에 **코드만의 절차 걷기 애니** 적용 — 그림 0, 30종 즉시. 배회 작가가 **걸을 때 통통(bob)·좌우 흔들(tilt)·납작늘임(squash)·진행방향 바라봄(flipX)**, **멈추면 잔잔한 숨쉬기(idle breathing)**. 기법 = **단일 Image transform 변형**(파츠 분리/스프라이트시트 아님 — 사용자 결정 2026-06-19; 파츠·시트는 프로 아트 졸업 후속). `pure.ts` 순수함수 `walkPose(phase,clockMs,dx,prevFlipX)→WalkPose`(θ=2π·clock/`WALK_STEP_MS`, bob=−A·\|sinθ\| **위로만**[착지 0·정점 −A → 발 안 뚫음], tilt=B·sinθ, squash 부피보존 `scaleX·scaleY=1∓WALK_SQUASH·\|sinθ\|`, flip 데드존으로 미세 dx 깜빡임 방지) + 상수 6종(`WALK_BOB_PX`·`WALK_TILT_DEG`·`WALK_SQUASH`·`WALK_STEP_MS`·`IDLE_BREATH*`·`FLIP_DEADZONE`), DOM·Date·random 0(결정성 → 경계 테스트 가능). `scene.ts` 3곳: ① `update()`가 `wanderStep` 뒤 walkPose 적용 — **시각 bob ⊥ 논리 footY 분리**(bob을 `o.y`에만 더하고 depth·접지는 `footY` setData → `restack` 정렬키를 footY 우선으로 = bob이 z-순서를 흔들지 않음) + `setDisplaySize`가 깐 scale을 base로 곱한 squash(크기 파괴 방지) + `setFlipX`. ② `spawnCharacter`가 baseScale 저장 + `animClock` 위상 오프셋 시드(걸음 군무 방지). ③ `restack` 정렬키 footY 우선(식물·건물은 미설정 → `o.y` 폴백 = 무변경). 엔티티·SVG symbol·Vue 도감·백엔드·저장 무변경. TDD: `garden-pure.test.ts` walkPose 경계 11종 RED→GREEN(vitest 429). 번들 재빌드(T-063). 실 브라우저 게이트(T-053/054): walk/idle·접지 유지·발 안 뚫음·depth 겹침·위상 분산·콘솔 0·편집↔뷰·도감 회귀 0. **후속(같은 `spriteId` 위)**: 8방위(현 좌우 flip만)·~~idle 행동 다양화~~(✅ 아래)·속도연동 위상·파츠 분리 정밀 걷기 또는 스프라이트시트(프로 아트 졸업 시). 비전·배경 메모리 `garden-vision-coc-zoo`.
>
> **마을 D 폴리시 PR-A — idle 행동 다양화 ✅ (2026-06-24)**: 멈춘 작가가 숨쉬기만 하던 것에서 **4종 idle 행동(stand·read·stretch·look)**을 확률 분포(60/15/15/10%)로 랜덤 선택 — "살아있음" 강화. `pure.ts` `IdleAction` 타입·`pickIdleAction(rand)`(분포 상수 `IDLE_STAND/READ/STRETCH_WEIGHT`)·`idlePose(action,clockMs,prevFlipX)→WalkPose`(stand=기존 breathing / read=30% 진폭 정지감 / stretch=느린 세로 늘임 `STRETCH_AMP=0.12` / look=`LOOK_TOGGLE_MS=1200` 주기 flipX 토글). `WanderState`에 `idleAction?` 필드, `wanderStep` walk→idle 전환 시 `pickIdleAction(rand)` 자동 부여(idle 동안 재선택 금지). `scene.ts` `update()`: walk=`walkPose`, idle=`idlePose`. read/stretch 진입 순간(walk→idle 한 프레임)에 📖/🙆 이모트 독립 Text tween 1회(T-084 회피: `objs` 미포함·자체 tween+destroy). TDD: `pickIdleAction` 분포 경계 8종·`idlePose` 불변식 11종·`wanderStep` idleAction 연동 4종 RED→GREEN. 568 vitest GREEN. 번들 재빌드(T-063). **후속**: ~~PR-B 근접 상호작용(두 작가 스칠 때 마주보기+인사 이모트)~~(✅ 아래), 8방위·파츠·스프라이트시트(아트 졸업 시).

> **마을 D 폴리시 PR-B — 근접 상호작용 ✅ (2026-06-24)**: 두 작가 캐릭터가 서로 가까이 있을 때 마주보고 인사 이모트. `pure.ts`에 `INTERACT_DIST`(0.13)·`INTERACT_COOLDOWN_MS`(12000ms)·`isNear`·`faceEachOther` 추가(순수함수, T-084 불변식 준수). `scene.ts` `update()` 캐릭터 쌍 순회 — idle 시 flipX 마주보기(메인 루프 **뒤** 배치로 look 토글 우선순위 확보)·쿨다운 보호 👋/❤️ 이모트 독립 Text tween(T-084 회피). TDD: `isNear` 5종·`faceEachOther` 4종 RED→GREEN. 577 vitest GREEN. 번들 재빌드(T-063). **후속**: 8방위·파츠·스프라이트시트(아트 졸업 시).

> **모바일 portrait 강제 가로 뷰 (2026-06-29)**: #605로 구현했으나 실기기 세로(portrait)에서 파란 화면이 나와 방향 전환 — **#605 강제 회전 제거 → 반응형 (2026-06-30)**: CSS `rotate90`·`applyPortraitInputPatch`·`rotateTouchForPortrait` 일체 제거. portrait·landscape 모두 Phaser `Scale.RESIZE`·표준 `transformPointer`·`fitCamera` 반응형으로 동작. vitest 778 GREEN. 번들 재빌드. — **반응형 2모드 PoC (2026-06-30)**: 6컨셉 심사 결론 = 모바일 세로에서 CoC 가로 아이소 무대는 쾌적성 천장(세로는 "도시 부감 와우"를 구조적으로 못 줌, 세로 재미 = 1:1 애착(돌봄)·수집). → **세로(≤899px·portrait) = 경량 돌봄 뷰 `PortraitVillage.vue`**(오늘의 작가 히어로 카드·정 미터·큰 밥 주기 버튼·식구 작가 카드 스택, 순수 Vue·Phaser 미사용), **가로·데스크탑·앱 = 기존 GardenGame(CoC) 그대로**로 `VillageApp.vue` orientation 분기. 백엔드·DB·SVG 0변경, 같은 `/api/garden`·`/feed` 재사용(추가 API 0). `pure.ts` `pickTodayAuthor`(정 레벨 최저 선정)·`affectionProgress`(임계 복제 미터) 순수함수 TDD(vitest 796 GREEN). 실 브라우저 모바일 세로 실측 5게이트 통과(루프 완주 밥주기→Lv↑·식구 탭 전환·가로↔세로·데스크탑 회귀 0·#605 제거 후 파란화면 없음). **PoC(전면화 아님)** — 게임감 소감 = 따뜻한 돌봄/수집 뷰(일반 카드앱과 변별됨), 전면화는 사용자 판단. → **전면화 ✅ (2026-06-30, PR-A)**: 데스크톱·가로에서도 CoC 가로 무대(`GardenGame`·`scene.ts`·Phaser·여백 앰비언트)를 제거하고 **PortraitVillage를 전 화면 단일 돌봄 뷰**로. `pure.ts`는 CoC 순수함수 전부 삭제(좌표·줌·격자·아이소·wander·walkPose) — 돌봄 공유분(`pickTodayAuthor`·`affectionProgress`)만 잔존. 데스크톱은 중앙 카드(액자) 레이아웃. CoC 재유입 정적 가드(`village-care-view`) RED→GREEN. **정원/마을→「서재」 리네임 ✅ (PR-B)**: 사용자 대면 명칭(앱 UI·랜딩 마케팅) 전부 서재로 통일, 랜딩 ④섹션은 다마고치 톤("작가가 찾아오는 나의 서재")으로 재작성. 코드 식별자·라우트(`/village`)·DB·CSS 클래스는 내부값 garden/village 유지(2026-06-18 원칙). `LandingPageTest` `containsString("독서 서재")` RED→GREEN.

> **건물 은퇴 + 작가 꾸미기 피벗 (2026-06-30) ✅ 엔진 제거까지 완료**: CoC 컨셉에서 벗어나는 방향(세로 케어 뷰가 주가 됨)에서 **출판사 건물은 사실상 무용**해진다 — 건물의 유일한 페이오프가 CoC 맵 배치인데, 작가(케어 루프 = 먹이→정→레벨 보유)와 달리 건물은 케어 루프가 없고 세로 돌봄 뷰(`PortraitVillage`)엔 등장조차 안 한다(`ownedPlants()` = BUILDING만 → 배회 작가는 배치 대상도 아님). 그래서 **① 건물(BUILDING)축 은퇴** + **② 작가 캐릭터 꾸미기**(옷 갈아입히기 등 능동 커스터마이징 — 검증된 드레스업 게임 패턴)로 작가 발전 방향을 잡는다(사용자 결정 2026-06-30). 은퇴는 단계적: **PR-1 = 건물 콘텐츠 제거 ✅**(`Building` 엔티티·`BuildingUnlockCalculator`·`BuildingState`·도감 건물 섹션·대시보드 건물 카운트·건물 SVG 스프라이트 제거, DB `publisher_building`·`garden_placement` 테이블은 보존=소프트 제거, 배치 엔진은 `ownedPlants()`→빈목록으로 inert化하되 코드 유지). **PR-2 = 배치/편집 엔진 제거 ✅**(건물이 *유일* 배치 대상이라 좀비가 된 `GardenLayoutService`·`GardenPlacement(Repository)`·`PlacedItem`/`PlacedPlant`/`OwnedPlant`/`PlacementRequest`/`LayoutSaveRequest`/`PlacementAxis`·고아가 된 `GardenIsoProjection`(SSR 좌표 유틸)·`GardenView.ownedPlants()`·scene.ts 편집 경로(드래그·선택·격자·식물 그림자)·`POST /api/garden/layout`·`POST /village/layout`·응답 `placed`/`owned` 필드를 통째 은퇴; `garden_placement`·`publisher_building` 테이블은 보존=소프트 제거; 가로 GardenGame 빈상태 카피를 "마을 식구가 없어요 — 작가의 책을 완독하면 찾아와요"로 정리·HUD ✏️꾸미기 버튼 제거·무효화된 e2e 정원 저장 스펙 제거; 월드 좌표 상수 `WORLD_WIDTH/HEIGHT`는 엔진과 무관한 `GardenWorld` holder로 이전해 `/api/garden` 응답 `world`의 출처 유지). 남는 건 보기 전용 마을(배회·먹이주기) — 실 브라우저 검증(캔버스 마운트·격자 없음·꾸미기 버튼 부재·콘솔 0). CoC 가로 모드는 **PR-A(2026-06-30)에서 제거** — 데스크톱·가로 GardenGame을 없애고 돌봄 뷰(PortraitVillage) 단일 모드로 전환(2모드 PoC 전면화, 별도 흐름). 아래 「단계적 진화(tier)」는 이 피벗으로 **대체**된다(권수 기반 자동 외형 진화 → 사용자 능동 커스터마이징). 꾸미기의 핵심 난제 = 현 작가 스프라이트가 통짜(D 폴리시 단일 transform)라 옷 교체엔 파츠 분리/outfit 변형 결정이 선행(별도 설계 세션).

**명칭 전환 (방향 결정)**: 이 기능은 **이제부터 "잔디"가 아니라 "정원(Garden)"**이다. GitHub 컨트리뷰션을 그대로 빼다 박은 "잔디"에서 출발했지만, 정체성을 *독서로 가꾸는 나만의 정원*으로 재정의한다. (코드·UI의 `잔디`/`contribution` 명칭 실제 리네이밍은 구현 단계의 일 — 지금은 방향만 못 박음.)

**발상 → 게임화 (이어지는 진화)**: 1차 발상은 "큰 네모 **한 칸**에 책을 **할당량만큼 채우면 식물이 심어진다**"였다. 여기서 한 발 더 나아가 **게임 같은 수집·해금(collection / unlock) 경험**을 지향한다 — 식물이 단순 성장 단계를 넘어 **모아서 정원을 채우는 수집 대상**이 된다. 따라서 동작 방식도 지금(연속일 1뱃지)과 **달라진다**.

**핵심 메커닉 (방향 굳힘)**:
- **수집형** — 식물 **종류를 대폭 늘린다**. 다양한 식물로 정원을 채우는 게 동기.
- **매핑 차원 = 출판사 · 작가 · 장르** — 이 세 축에 매칭되는 **수많은 식물 카탈로그**를 준비한다(읽은 책의 메타데이터 → 식물 해금).
- 기존 *연속일(streak) 성장 뱃지*와는 **트리거가 다르다** — streak이 아니라 *독서 실적*(아래 2트랙) 기반 해금.
- **단계적 진화 (향후·사용자 구상 2026-06-18) — ⚠️ 2026-06-30 「건물 은퇴 + 작가 꾸미기」 피벗으로 대체됨**(권수 기반 자동 외형 진화 대신 사용자 능동 커스터마이징; 건물축 자체 은퇴). 아래는 근거 보존용 기록. — 수집 해금을 *이진(보유/미보유)*에서 **누적 권수에 따른 단계적 진화**로 확장한다: **작가 캐릭터**는 *특정 작가 완독 권수↑ → 외형 발전(정교·화려)*, **출판사 건물**은 *특정 출판사 권수↑ → 더 커지거나 화려해짐*. 시간축 식물 `GrowthStage`(🟫→🌱→🌷→🌳) 성장 결이되 *대상(작가/출판사)별 다단계*다. 기술 = 현 단일 임계(`owned` boolean, `AuthorCharacter`·`Building`) → **tier**(권수 구간별 외형·tier별 `spriteId`)로 확장, 해금 계산이 boolean→tier int. 비용 핵심 = tier마다 아트 제작량(N-080) → 대표 소수 + 폴백으로 시작. 비전·배경은 메모리 `garden-vision-coc-zoo`에도 기록.

**식물 획득 방식 — 2트랙 (결정 2026-06-10)**: 식물을 얻는 기준을 둘로 나눈다.
- **트랙 A — 타이머 할당량 + 매핑 3축(장르·작가·출판사)** ✅ **(매핑 3축 완성 — 위 상태 박스 참조)**: 타이머를 채워 *할당량*을 달성하면 식물을 얻는다(시간 기반 — 1차 발상의 연장). 1단계 확정: 할당량 = **그날 목표를 채운 날의 누적 수**, 보유는 저장 안 하고 유도, 읽기전용 도감/미니정원. **장르 매핑 확장**: 시간축에 더해 *장르 1권 완독 → 장르 식물* 수집축 추가(별도 `genre_plant` 카탈로그·완독 기준·폴백 들꽃). **작가·출판사 다양성 확장**: *완독 distinct 작가/출판사 수 ≥ 임계 → 식물*(통합 `diversity_plant`·`kind` 구분) — 매핑 3축 완성.
- **트랙 B — 책 조합 (숨겨진 레시피)** ✅ **(메커닉 베타 출하 — 위 상태 박스 참조)**: **완독(`BookStatus.FINISHED`)** 과 **읽고 싶음(`BookStatus.WANT_TO_READ`)** 책의 *조합*으로 식물을 얻는다(매핑은 위 3축 메타로 판정). **핵심: 어떤 책을 완독하고 어떤 책을 읽고 싶음으로 두면 어떤 식물이 나오는지 사용자에게 안내하지 않는다** — *발견(discovery)* 자체가 재미인 숨은 레시피(요리·연금술 게임의 숨은 조합처럼). **출하: 발견 저장·??? 슬롯·완독≥1 파밍 가드·조회 시 평가** 메커닉 완성, 레시피 8종은 베타 예시(커스텀 카탈로그 단계에서 교체).

**도입 순서 (결정 2026-06-10)**: **초반엔 기존 잔디(53×7 히트맵)를 유지**하며 정원과 **공존**시키고, **정원의 완성도가 올라가면 잔디를 제거**(정원이 흡수)한다 → 앞서 "대체 vs 공존" 열린 질문을 *단계적 대체*로 해소.

**리치 UI 기능 · 프론트 선후 (결정 2026-06-10 → 갱신 2026-06-14)**: 정원에 두 인터랙티브 기능을 둔다 — **① 식물 도감**(보유/미보유 식물 그리드 + 작가·장르 필터 + 클릭 상세, **출하 완료 ✅** `/garden`)과 **② 커스텀 꾸미기**(식물을 캔버스에 배치해 정원 레이아웃을 꾸미고 저장, **경량 SSR로 출하 완료 ✅** — 아래 결정 갱신).
- **도감**은 ~~현 스택(Thymeleaf SSR + htmx 부분 swap + Alpine)으로 충분~~ → **S3(PR #405, 2026-06-19)에서 Vue(`GardenDex.vue` + `DexCell.vue`)로 이전 완료**, Alpine 완전 제거(게임·도감 모두 Vue). 도감 탭·필터·6축 그리드가 `/api/garden` catalog 소비.
- **꾸미기 배치**는 위치 상태 + **레이아웃 영속화(서버 저장)**가 필요. 원래 이를 HTML만 내려주는 현 SSR엔 없는 **JSON API + 본격 프론트(SPA)** 요구로 봐 전환 스코프에 묶었으나(갈림길은 모바일 앱 선행조건 §프론트엔드 전환·N-017과 같음), **재검토 끝에 경량 SSR로 선출하**했다(아래).
- ~~**결정(2026-06-10) = (A) 리치 버전을 프론트 전환 스코프에 묶는다**~~ — "두 번 짜기" 우려로 전환 뒤로 미뤘던 결정. **근거는 보존하되 배치에 한해 뒤집음(2026-06-14, PR #345)**.
- **갱신 결정(2026-06-14) = 꾸미기 배치는 경량 SSR로 먼저 출하**(설계 `claude-docs/plans/2026-06-13-garden-placement.md`). "두 번 짜기" 우려는 SNS 같은 큰 기능 기준이고, **배치 하나는 작은 JSON 저장 엔드포인트(`POST /garden/layout`) + (axis,code,cell) 좌표 스키마면 충분**하며 **그 API·스키마는 SPA 전환 시 그대로 재활용**된다(재작업은 캔버스 UI 레이어뿐 = "절반만 두 번"). 전환 트리거가 미정이라 핵심 재미("나만의 정원 꾸미기")를 무기한 미루느니 일찍 준다. HTML5 drag는 모바일 터치에 빈약해 **탭-투-플레이스(격자 스냅)** 로 출하 — 드래그는 SPA 전환 시 UI 고도화 여지. → 정원은 여전히 §프론트엔드 전환을 정당화하는 **첫 구체 동인**(드래그 고도화·실시간 협업 등 풀 리치 UI는 전환 스코프에 남음).

**기존 자산과의 연결 (재사용 후보 · 실현성)**:
- `GrowthStage` enum이 이미 식물 단계(🟫→🌱→🌷→🌳)와 "SVG 교체 여지"를 열어둠(`com.booktimer.session`) → 식물 비주얼·단계의 토대. 단 수집형으로 가면 단일 enum이 아니라 **식물 카탈로그 + 사용자 보유/해금 상태**를 담는 데이터 모델이 필요(아래 열린 질문).
- **매핑 3축 메타데이터는 이미 `Book` 엔티티에 존재** — `author`(저자)·`publisher`(출판사)·`category`(장르). 검색 등록 시 알라딘 API로 채워짐 → 해금 조건 판정에 그대로 재사용 가능. **데이터 토대는 이미 있다.**
- 단 실현성 단서 두 가지: ① `category`는 깔끔한 장르 enum이 아니라 **알라딘 계층 문자열**(예 `"국내도서>소설/시/희곡>한국소설"`)이라 해금 매핑하려면 파싱/정규화가 필요. ② 수동 등록 책은 메타가 **null일 수 있음**(`BookCatalogBackfillService`로 백필 시도하는 구조) → 해금 판정의 메타 완전성·정규화가 핵심 난제.

**열린 질문 (설계 시 결정 — 지금은 미정)**:
- **식물 카탈로그의 출처·규모** — 출판사/작가/장르별 "수많은 식물"을 누가 어떻게 큐레이션·관리하나(정적 시드 데이터 vs 관리자 입력)? 식물 에셋(이미지/SVG)은 어디서?
- **트랙별 임계·레시피** — 트랙 A 할당량 크기, 트랙 B의 구체 조합 규칙(완독 N권 + 읽고싶음 M권? 작가/장르 매칭 방식), 중복·재해금 규칙.
- **숨긴 레시피의 발견성** — 안내가 없으니 *우연 해금* 시 피드백("새 식물 발견!")·도감(보유 목록)을 어디까지 줄지. 힌트 0이면 발견이 아예 안 일어날 위험 ↔ 너무 친절하면 "숨김"의 재미 소멸. 게다가 **레시피가 숨겨져 메타 신뢰성이 더 치명적** — 작가/장르 표기 불일치로 "왜 안 나오지"를 사용자가 역추적할 수 없다.
- **읽고싶음 입력의 가벼움** — `WANT_TO_READ`는 *읽지 않아도* 누를 수 있는 저비용 행동 → 해금 입력으로 쓰면 파밍(남발) 여지. 의도된 재미로 둘지/제약을 둘지.
- **데이터 모델** — `Plant`(식물 카탈로그) + `사용자 보유 식물`(해금 이력) + 매핑(작가/출판사/장르 ↔ 식물). 책 메타(특히 정규화된 장르)가 부족하면 그 보강이 선행.
- ~~**정원/도감 화면 디테일** — 드래그 배치의 격자 스냅 vs 자유 배치·좌표 저장 모델~~ → **결정·출하 ✅ 2026-06-14 (PR #345)**: **격자 스냅(8×6)** 채택(자유 픽셀은 반응형·터치에서 깨짐), 좌표 저장은 **행별 `garden_placement`((axis,code) 복합 키 + cell_index)**. 남은 미정 → **해소 ✅**: 도감은 **§6.6 리디자인 출하(2026-07-07)** — 건물·식물축 은퇴로 '필터 축 추가'는 폐기되고, 작가 단일축에 **상태 필터칩(전체/보유/미보유)·시각 진행바·클릭 캐릭터 상세시트**를 넣어 도감을 완성('🚧 개발 중' 베타 배너 제거, 미해금=🔒 잠금). 드래그 편집기는 CoC 무대 은퇴(PortraitVillage 돌봄 뷰 단일화)로 폐지.
- **비주얼·기술 — 미니게임 퀄리티 로드맵 A0→B→살아있는 정원 게임 (A0 무대화 ✅ PR #346 · A1 생명감 ✅ 2026-06-14, PR #349 · A2 SVG 승격(시간축 14종) ✅ 2026-06-15, PR #351 · A2 후속 SVG 승격(타 축 33종·도감 4축 완결) ✅ 2026-06-15, PR #354 · A3 인터랙션(드래그+휠 줌) ✅ 2026-06-15, PR #353 · A3 후속 인터랙션 풍부화(스왑·밖-드롭 거두기) ✅ 2026-06-15, PR #355 · 자유 위치 전환=살아있는 정원 게임 Phase 1(격자 폐지·정규화 좌표·Phaser) ✅ 2026-06-15, PR #356)**: "이모지 격자" 인상을 **단계적 고도화 A(현 스택)→B(캔버스 게임 엔진)**로 끌어올린다(사용자 합의 2026-06-14). A 단계(무대·모션·SVG·격자 드래그) 완결 후, 사용자가 **본격 "살아있는 정원 게임"**(성장·물주기·낮밤·파티클)을 최종 비전으로 확정 → **자유 위치 전환(Phase 1)으로 B(Phaser 캔버스)에 진입**(2026-06-15). **A0 무대화 = 출하** — 에셋 0·이모지 유지하되 하늘→잔디 배경·흙 화단·발밑 그림자·울타리·빈 정원 이랑으로 **순수 CSS/HTML 무대 연출**(백엔드/JS 0, 보기/편집 캔버스를 `.garden-stage`로 래핑·좌표계 불변). 2.5D perspective는 탭 좌표를 깨 비채택(깊이는 그라데이션+그림자로 암시). **A1 생명감 = 출하**(사용자 결정 = "은은한 상시 생명감") — `@keyframes` **sway(상시 미세 흔들림·`transform-origin:bottom`)+rise(입장 솟아오름)+pop(심을 때)+옅은 glow**, nth-child stagger(셀별 비동기), `prefers-reduced-motion` 가드(모션 끄면 즉시 최종 상태). **전부 CSS + 선언적 Alpine 1상태(`justPlaced`)·백엔드 0.** 풍부한 JS 파티클·바람은 '화사' 옵션/B로 보류. **A2 SVG 승격 = 출하**(사용자 결정 = **코드 벡터로 직접 그리기**, 외부 에셋 0) — 식물 비주얼을 **이모지 → 인라인 `<symbol>`+`<use href>` 코드 벡터 SVG**로 올림. **범위 = 시간축(`plant`) 14종 완결**(성장 서사 일관성), 타 축 33종은 이모지 폴백 유지(후속 PR에서 같은 파이프라인 복제 — **A2 후속에서 완결, 아래**). 파이프라인 = `plant.sprite_id`(V40 nullable·기존 행 폴백 무중단) → `Plant.spriteId` → `OwnedPlant`/`PlacedPlant`(+spriteId) → `GardenView`(시간축만)·`layoutOf`(보유 메타 결합). **불변식 = `spriteId==null`이 정상(폴백)** → 양쪽 모드 "있으면 SVG·없으면 이모지" 분기·null-state 누수 테스트(N-055). 저장 계약 불변(클라는 axis/code/cell만, spriteId는 서버 결합). 모션은 A1 상속(`.canvas-plant` transform을 자식 `<svg>`가 받음). 도메인 데이터·뷰모델 전파 변경이라 **정식 RED→GREEN TDD**(좌표·색은 비검증→preview 게이트). `Plant.java:19` "SVG 후속" 예고 실현. **A2 후속 = 출하**(사용자 결정 = **한 PR에 33종 완결**, 축별 분리 미채택) — A2가 시간축에 깐 파이프라인을 **장르 13 + 다양성 12 + 레시피 8 = 33종**에 1:1 복제해 **도감 4축이 전부 SVG**가 되며 한 화면 이모지 혼재 해소. 새 설계가 아니라 검증된 파이프라인의 N축 복제(리스크 = 33종 벡터 제작 노동). `genre_plant`/`diversity_plant`/`recipe_plant` 각 `sprite_id` 컬럼(V41 nullable·전 행 `=code`·미래 행 폴백) → 3 엔티티 `spriteId`+`of()` 끝 인자 → **`GardenView.ownedPlants()` 타 축 3줄 `null`→`getSpriteId()`(핵심)**. 캔버스·팔레트는 A2 `OwnedPlant.spriteId` 분기 재사용(추가 마크업 0), 도감 그리드 4곳만 시간축 패턴 복제. 벡터 = `garden-sprites.html` 33 `<symbol>` 추가(14→47, 발밑 줄기로 과일·채소도 "땅에 선 식물"·무대 톤). 47종 code 전역 유니크라 충돌 0. 저장 계약 불변(서버 결합). **정식 RED→GREEN**(타 축 전파 3 테스트 RED→엔티티 3+`GardenView` 3줄 GREEN, null-state 누수 가드 N-055) + 회귀 GREEN, `app.css` 무변경. 이로써 정원 A 단계 비주얼(무대·모션·SVG·인터랙션) 완결. **A3 인터랙션 = 출하**(사용자 결정 = 드래그 + 휠 줌, 핀치·팬·스왑 제외) — 탭-투-플레이스를 **Pointer Events 단일 제스처**로 끌어올림. **이동거리 임계(6px)**로 탭/드래그 분기(임계 미만=기존 `tapCell`/`selectPalette` 폴백 위임 → 회귀 0·키보드 경로 보존), `setPointerCapture`로 셀 밖 추적, **고스트**(화면좌표 `fixed`·정지)가 손가락 따라오고 **드롭 예고 하이라이트**. **스냅 = 셀 `getBoundingClientRect` 순회**(`closestCellIndex` 순수 함수) — `getBoundingClientRect`가 transform 적용 화면좌표를 줘 **줌돼도 scale 보정 0**(좌표계 자동 정합). **줌 = 휠 제자리 확대**(`clampScale` 1~2.5배, `transform:scale`·origin center·무대 overflow가 클립, 팬 없음). **점유 칸 드롭 = 무효 복귀(빈 칸 이동만)** — 교체는 기존 탭이 담당(드래그·탭 역할 분담). **격자 스냅 유지 = 저장 계약(`axis,code,cell`) 불변·백엔드 0** → 자유 픽셀 좌표(B 트리거)를 의도적으로 회피. `touch-action:none`은 편집 캔버스에만(페이지 스크롤 보존). **순수 코어(`closestCellIndex`/`clampScale`) RED→GREEN 자동 단언**(`.preview` 노드 하니스가 garden.html 실제 함수 블록을 추출 실행 — 셀 중앙·경계 반열린·갭 최근접·밖 -1·줌 정합·클램프 전수) + 회귀 `./gradlew test` GREEN + 정적 Alpine 하니스 실 포인터 시뮬로 드래그/이동/무효복귀/줌 검증. 제스처 품질(실기기 멀티터치)은 실 브라우저 수동 게이트. **A3 후속 인터랙션 풍부화 = 출하**(사용자 결정 = 스왑 + 밖-드롭 거두기, 핀치·팬·part-sway 제외) — A3가 깐 드래그를 "빈 칸 이동만"에서 **드래그 모델 완성**으로. **핵심 = 드롭 결정을 순수 함수 `resolveDrop(source, target, placements)`로 추출**(상태 변경과 결정 분리 → 하니스가 결정만 단독 단언, `applyDrop`은 결과 맵 적용만). 4결정: 빈 칸 이동 / **셀→점유칸 스왑**(출발칸↔target, 둘 다 "한 식물 한 번" 유지) / **팔레트→점유칸 교체**(점유자 거둠+중복 떼기, 탭 교체와 정합) / **밖(-1) 거두기**(셀 소스만 fromCell 제거)+제자리 무변경. 오발 방지(R2) = `closestCellIndex` 셀 크기 비례 마진이 "확실히 밖"일 때만 -1(갭·근접 안 거둠) + 셀 소스 밖-드래그 시 `.drag-removing` 붉은 톤 경고. 저장 계약 불변·백엔드 0(맵 조작일 뿐 payload는 `axis,code,cell`). **정식 RED→GREEN**(`a3-pure.test.mjs`가 `@a3-pure-core` 마커 블록 추출 실행 — `resolveDrop` 미구현 RED→추가 GREEN, 4결정+입력 불변+기존 회귀 가드 28단언) + 회귀 GREEN. **B 진입 = 자유 위치 전환(살아있는 정원 게임 Phase 1) ✅ 출하**(2026-06-15, PR #356, 계획 `2026-06-15-garden-living-game-phase1-freeplacement.md`) — A3의 "격자 스냅 유지"가 의도적으로 회피하던 **B 전환 트리거 ②(자유 픽셀 좌표 → `garden_placement` 좌표 컬럼)**가 사용자의 본격 게임 비전 확정으로 실현됐다. **격자(`cell_index INT`)를 폐지**하고 정규화 좌표(`pos_x/pos_y DOUBLE` 0~1)+`z_order`로 풀스택 교체(V42 drop/recreate), 보기 모드는 서버 렌더 좌표 절대배치(no-JS 폴백), 편집은 **Phaser 3 게임 위젯**(garden 한정 `defer` 로드)으로 드래그 자유 이동·월드 밖 드롭=거두기·팔레트 탭 추가. **엔진 = Phaser**(PixiJS 미채택 — 최종 비전의 씬·게임루프·트윈·파티클·타이머·입력이 내장이라 게임이 종착지면 Phaser가 적합, 계획 §2.2). **SVG→Phaser 텍스처 POC 성공**(A2의 `<symbol>`을 Blob URL → `load.image`, 폴백 불변식 계승). 검증 = 서버 정식 RED→GREEN(좌표 범위·겹침 허용(Phase1 당시; #384에서 한 칸 하나 불변식으로 회귀)·zOrder·회귀) + `.preview/free-pure.test.mjs` 순수 코어 23단언 + Phaser 씬 preview MCP eval 실증, 제스처·픽셀 시각은 실 브라우저 수동 게이트. **후속 = 살아있는 정원 게임 Phase 2~5**(별도 계획 세션에서 상세화): **2** 변형·레이어 ✅ 출하(2026-06-15, PR #363 — 회전·크기·앞뒤 정렬: V43 rotation/scale, 편집 변형 툴바(탭 선택)·보기 wrapper 합성(outer 변형/inner sway)·clampRotation/clampScale, Phaser 실조작은 배포 후 실기기 게이트; **크기 UI·렌더는 CoC 고정 크기 정책으로 후속 제거(PR #387) — 회전만 유지·scale DB 잔재 유지**) → **3** 꾸미기 소품(길·연못·울타리 등 데코 카탈로그) **✅ 출하**(2026-06-15, PR #367, 계획 `2026-06-15-garden-phase3-decorations.md`) — 식물만 놓던 정원에 **보유 무관 장식 13종**을 더해 조경을 열었다. 소품은 식물과 근본이 다름(① 보유 무관·해금 없음 ② 같은 소품 여러 개 = `uk(user,code)` 없음)이라 **검증된 식물 배치 도메인을 0줄 안 건드리고** 소품 전용 테이블·검증을 평행 신설(회귀 0, genre/diversity/recipe 분리 패턴). DB(V44) `decoration`(카탈로그 시드)+`garden_decoration_placement`(중복 허용), 저장은 `POST /garden/layout` 바디를 **`LayoutSaveRequest{plants,decorations}` 래퍼**로 원자적 교체(`saveLayout`), z는 식물·소품 **통합 단일 스케일 병합 정렬**(`layoutItemsOf`→`PlacedItem`). 소품 검증=카탈로그 존재·좌표/변형 범위·개수 cap(200)·고아 제외(보유 검증·중복 거부 없음). 프론트=보기 `placedItems` 단일 루프(kind별 `.canvas-plant` sway/`.canvas-decor` 정적)·편집 "소품" 팔레트(항상 활성=다중)·Phaser 통합 `objs`(`spawnObject`/`addDecoration`/`exportPlacements` 종별 분리·텍스처 `tex-`)·소품 SVG 13종(`garden-decor-sprites.html`, code 전역 유니크). TDD RED→GREEN(`GardenDecorationLayoutServiceTest` 11 — 중복 허용 불변식 반전·z 병합·고아·cap·IDOR + 컨트롤러 래퍼/모델/400 + V44 마이그레이션 + 식물 회귀 0) + **실 브라우저 검증**(CLAUDE.md 🖥️·#366 게이트 — `.preview` 충실 하니스 Phaser `defer`·실 CDN: 콘솔 0·2-종 마운트·팔레트 13종·중복(연못 2개)·export `{plants axis, decorations no-axis, 전역 z 교차}` 실증) → **4** 살아있는 연출(성장 단계·물주기·바람 sway·낮밤 사이클·파티클) → **5**(선택) 게임 루프·보상(일일 돌봄·정원 점수·방문/공유). 반응형·접근성(스크린리더 대체텍스트)은 각 단계에서 함께.

**왜 이 방향인가 (retention 관점)**: 엔진 A(습관 타이머)의 retention 레버는 "습관 루프(알림·정원·축하)"다(§전략). 수집·해금 메타는 *읽을 이유*를 하나 더 부여하고(특정 작가/장르를 더 읽게), 정원을 *채우고 싶은 목표*로 만들어 재방문 동기를 강화하는 게임화 축 → thesis(습관 형성)와 정렬. 단 *수집 욕구가 독서 자체를 가리지 않게* 균형이 관건.

**연관**: 위 「독서 잔디」(→ 정원으로 흡수·개명), §프론트엔드 전환(리치 UI=드래그 커스텀이 전환 동인), `GrowthStage`·`ContributionGraph`·`ContributionGraphBuilder`(`com.booktimer.session`), `Book`의 `author`/`publisher`/`category` 메타, §전략 retention 레버.

### 독서 잔디 — 색 농도를 "하루 목표(증가값)" 기준으로 (완료 ✅ 2026-06-03)

> **구현 완료** — `ContributionGraphBuilder.build/levelFor`에 `goalSeconds` 인자 추가, 하드코딩 임계
> (`LEVEL_THRESHOLDS_SECONDS`) 제거. 레벨을 목표 대비 비율로 교차곱(정수) 비교. `ReadingContributionService`·
> `BookContributionService`가 유저 `ReadingTimer.dailyIncrementSeconds`를 조회해 빌더에 전달(없으면 1시간 폴백).
> 범례 "적음/많음" → "목표 미달/목표 달성"으로 보강. TDD: 빌더 경계(0%·25%·50%·100%·초과·목표0 퇴화·목표 추종)
> + 서비스 목표 배선. 아래는 설계 근거 기록(참고용).

**문제**: 현재 잔디 레벨(0~4)은 `ContributionGraphBuilder`에 **하드코딩된 고정 임계**
(`LEVEL_THRESHOLDS_SECONDS = {15분, 30분, 60분}`)로만 정해진다. 유저별 **하루 목표**인
`ReadingTimer.dailyIncrementSeconds`(설정의 "증가값", 기본 1시간)를 **전혀 참조하지 않는다**.
지금 "1시간 읽으면 진초록"으로 보이는 건 *기본 증가값(1시간)과 하드코딩 임계(60분 초과=lv4)가 우연히
일치*하기 때문일 뿐 — 사용자가 증가값을 30분으로 바꾸면 목표는 30분인데 잔디는 여전히 1시간 넘게
읽어야 최고 농도가 된다(목표를 따라가지 않음).

**의미 정리**: `dailyIncrementSeconds`는 부채 누적 모델(N-001)에서 "매일 늘어나는 갚을 양" =
사실상 **그날의 독서 목표**다. 따라서 잔디 농도를 이 목표 대비 **달성 비율**로 칠하는 게 자연스럽다.

**결정 (사용자 합의 2026-06-03)**:
- **목표 기준 = 그날 평면 증가값**(`dailyIncrementSeconds`). 이월된 누적 부채(잔여)가 아니라 그날 증가값으로
  고정 — 잔디는 칸마다 기준이 같아야 직관적이라(이월분을 섞으면 칸마다 기준이 달라짐).
- **비율 4단계 매핑** (목표 100% 달성 시 최고 농도 = 진초록):

  | 그날 독서 / 목표 | 레벨 | 색 |
  |---|---|---|
  | 0% (안 읽음) | 0 | 회색 |
  | 0 초과 ~ 25% | 1 | 연초록 |
  | 25% ~ 50% | 2 | |
  | 50% ~ 100% 미만 | 3 | |
  | **100% 이상 (목표 달성/초과)** | 4 | 진초록 |

  - 경계는 기존 코드 관례대로 "이하 포함"으로 둔다(예: 정확히 25%는 lv1, 정확히 50%는 lv2, 정확히 100%는 lv4).

**구현 메모 (별도 PR에서, TDD)**:
- `ContributionGraphBuilder.build(...)` / `levelFor(...)` 시그니처에 **목표 초(`goalSeconds`) 인자 추가** —
  현재 고정 `LEVEL_THRESHOLDS_SECONDS` 대신 목표 비율로 레벨 계산.
- **부동소수 회피 — 교차곱**으로 비율 비교(정수 long 유지):
  - `seconds <= 0` → lv0
  - `seconds * 4 <= goal` → lv1 (≤25%)
  - `seconds * 2 <= goal` → lv2 (≤50%)
  - `seconds < goal` → lv3 (<100%)
  - 그 외(`seconds >= goal`) → lv4 (목표 달성/초과)
- **목표 0 퇴화 처리는 자동**: `goal=0`이면 위 식에서 읽은 날(`seconds>0`)은 전부 lv4로 떨어진다
  (div-by-zero 없음). "목표 없음 = 읽기만 하면 만점"으로 합리적 — 별도 분기 불필요.
- `ReadingContributionService`가 유저의 `ReadingTimer`(→ `dailyIncrementSeconds`)를 읽어 빌더에 넘겨야 함
  (현재는 `historyService` + `clock`만 의존 → 타이머 조회 의존성 1개 추가). 타이머 미존재 케이스 정책 정하기
  (없으면 기본 1시간 fallback 등).
- **TDD 경계 테스트**: 0% / 0 바로 초과 / 정확히 25·50·100% / 100% 초과 / **목표 0(퇴화)** / 목표보다 큰 잔여 이월.
- (선택) `history.html` 범례·툴팁을 "목표 대비 %"로 보강, 범례 라벨 "적음/많음" → "목표 대비" 뉘앙스 검토.

**참고**: 색 자체(`app.css`의 `.level-0~4`)와 그리드 레이아웃은 그대로 재사용 — 바뀌는 건 *레벨을 정하는 기준*뿐.

### 목표 달성 알람 — 잔여가 0이 되는 순간 알림 (구현 → 제거 ✅ 2026-06-06)

> **구현(PR #181)했다가 제거(PR #185).** 측정 중 잔여 0 전이에 소리(WebAudio)+플래시+Web Notifications로 1회 알리는
> 인앱 알람을 만들었으나, **실사용에서 실효 없음이 드러나 철회**했다 — ① 인앱 알람은 *탭이 살아 있고 측정 타이머가
> 도는 동안*에만 작동하는데, ② 모바일에서 책 읽는 동안 화면이 꺼지면 브라우저가 백그라운드 탭을 throttle해 **잔여 0
> 전이를 감지조차 못 함**(아예 안 울림), ③ OS 알림은 권한 허용+플랫폼(iOS 사파리 제약) 문제로 거의 안 닿음. 게다가
> **못 지킬 약속으로 알림 권한을 요청하는 게 UX 마이너스**였다. 즉 "탭 열어둔 채 측정한다"는 PR #181의 가정이
> 종이책·모바일 현실에서 깨졌다(개념: 웹 인앱 알림의 한계 — N-### 후보).
>
> **제거 범위**: `createGoalAlarm`/소리/OS알림/권한요청/플래시(CSS `.goal-flash`)·전이 로직·테스트(`src/test/js/`). **유지**:
> 달성 배지("오늘 목표 달성! 🎉", `goalMet`)·타이머 초록색(`.met`) — #181 이전부터 있던 단순·항상 동작 시각 피드백.
>
> **대안 = 서버발**: 달성 "순간"을 탭 밖으로 알리려면 Web Push(SW+VAPID) 또는 서버 알림이 필요. 이는 §전략 「retention
> 레버 ①」(서버발=이메일, 단 이메일은 *재참여 넛지*용이고 실시간 순간엔 부적합)에서 다룬다. 아래는 철회된 설계 근거(참고용 보존).

**원하는 것**: 측정 중 누적 잔여(부채)가 0으로 떨어지는 순간(=오늘 목표 달성) 사용자에게 알람을 준다.
지금은 화면 배지("오늘 목표 달성! 🎉", `goalMet`)만 뜨고 따로 알리지 않는다.

**왜 쉬운가 (서버 불필요)**: 타이머가 서버 왕복 없이 JS에서 1초마다 도므로(`dashboard.js`의 `goalMet`),
"잔여 0이 되는 순간"을 **이미 클라이언트가 계산**한다. 거기에 알람을 거는 거라 DB·마이그레이션·서버 push 인프라가 전혀 안 든다.

**범위 결정 — 탭 열림 전제(①+②)로 충분**:
- ① **탭 안 알람**(소리 + 화면 플래시/배지 강조) — 매우 쉬움(JS 몇 줄).
- ② **Web Notifications API**(OS 알림창) — 탭이 떠 있으면 백그라운드(다른 탭/최소화)여도 뜸. 권한 요청 1번, 서버 불필요.
- ③ **Push API + Service Worker**(탭 완전히 닫아도 알림) — **하지 않는다(오버킬)**. VAPID·구독 저장·서버 push 필요.
  독서 측정은 본인이 페이지를 열어둔 채 일어나므로(측정 중이 아니면 알릴 목표 달성 순간도 없음) ①+②면 족하다.

**구현 메모(별도 PR)**:
- 알람은 **상태가 아니라 "전이"에 건다** — `goalMet`이 `false → true`로 넘어가는 **순간 1회**만 울려야 한다
  (현재 `goalMet`은 상태값이라 transition 감지 한 줄 추가 필요). 매 tick마다 울리면 안 됨.
- 소리 자동재생: 사용자가 「측정 시작」을 클릭한 제스처로 오디오가 풀려 있어 `Audio.play()` 차단 문제 없음.
- 점진적 향상: 권한 거부/구형 브라우저면 ②는 건너뛰고 ①(소리+플래시)로 자연 폴백 — htmx 폴백과 같은 결.
- 주로 `dashboard.js` + CSS/사운드 파일. Java 테스트 게이트엔 안 걸리지만 transition 감지 같은 순수 로직은 작게 검증 가능.

### 대시보드 인사말 → 작가 격언 랜덤 노출 (MVP 완료 ✅ PR #169 · DB+admin 관리 ✅ PR #170, 2026-06-05)

> **MVP 구현 완료(PR #169)** — `님, 환영합니다`를 작가 격언으로 교체. `com.booktimer.quote` 패키지 신설:
> `Quote`, `QuoteService`(+ `Random` 주입 이음새로 `random()`), 격언 18개 큐레이션. `DashboardController`가 전체 페이지
> 경로에서만 `quote`를 모델에 싣고(`DashboardModel` 아님 — htmx 라이브 공유 회피, 잔디 graph와 같은 자리), `dashboard.html`
> 인사말을 격언+작가로 교체(`.greeting.quote` CSS, 작가는 작게·흐리게).
>
> **DB 이전 + 운영자 관리(PR #170)** — 정적 JSON → DB(`quote` 테이블, Flyway V17 + 18개 seed)로 옮겨 **재시작 없이
> 추가/삭제**. `Quote` record→`@Entity`(text varchar500/author varchar100, BaseTimeEntity), `QuoteRepository`,
> 선택 로직은 순수 `QuotePicker`로 분리(결정적 단위테스트), `QuoteService`는 repo 백업 + 빈 목록이면 폴백 격언 반환
> (대시보드 깨짐 방지). 운영자 화면 `/admin/quotes`(`AdminQuoteController` — 목록+추가+삭제, POST는 CSRF, `/admin` 랜딩 링크).
> TDD: QuotePickerTest(결정적 선택)·QuoteServiceTest(@DataJpaTest add/all/delete/폴백/공백거부)·AdminQuoteControllerTest
> (USER 403·ADMIN 200·추가/삭제/공백). 생성자 2개(public+테스트용)라 `@Autowired`로 주입 생성자 명시(없으면 no-arg 탐색 실패).
> 아래는 설계·미래 진화 기록.

**원하는 것**: 대시보드 상단 `님, 환영합니다`(`dashboard.html:20`, `.greeting`) 자리에 **작가들의 격언/명언을 랜덤으로** 띄운다.
체크박스·설정 폼 없이 **페이지를 띄울 때마다 자연스럽게 바뀐다**. 보이는 위치·스타일은 지금 그대로.

*(2026-07-02 진화: 독서 스토리 도입으로 홈 본문 카드에서 상단 브랜드 로고 바로 아래 한 줄(Teleport)로 약화 이동 —
6초 로테이션·hover 정지 유지, 모바일 포함 상시 노출, 길면 말줄임 없이 자연 개행.)*

**왜 구조가 맞는가**: 인사말은 htmx 라이브 영역(`#dashboard-live`) **밖**에 있다 → 측정 시작/종료(htmx swap)로는 안 바뀌고
**전체 페이지 로드 때만** 다시 그려진다. "페이지 로드마다 바뀌고 측정 버튼엔 안 바뀜"이 추가 작업 없이 자연히 나온다.

**격언 소스 — 초기 비교(MVP는 ①, 그 뒤 ③ 채택)**:
- ① **내장 목록**(resources JSON) — 네트워크·비용·실패 0, 오프라인, 빠름. 작가명 출처 표기. 짧은 유명 격언은 출처 달면 저작권 일반적으로 무해. → **MVP(PR #169)로 채택**.
- ② 외부 명언 API — **비추**(영어 위주, 대시보드 핫패스마다 네트워크 왕복·지연·레이트리밋·장애, 책/작가 큐레이션 아님). 미채택.
- ③ DB 테이블 — 처음엔 오버킬로 봤으나 **운영자가 런타임에 추가/삭제하려면 필수** → **PR #170에서 채택**(아래 미래 진화 ②③의 토대도 됨).

**구현 메모 (※ 초기 MVP 설계 기준 — 일부는 PR #170에서 진화함, 아래 ⚠️ 참고)**:
- 서버사이드 랜덤 — 목록 전체를 JS로 내려 고르지 말고, **서버에서 하나 뽑아 모델에 실어 Thymeleaf 렌더**(SSR 우선, JS 불필요, 브라우저엔 한 개만). *(유지)*
- **테스트 이음새**: 랜덤은 비결정적 → `Clock` 주입(N-010)처럼 `java.util.Random`을 **주입**해 테스트는 시드/스텁으로 "n번째 선택" 단언. *(유지 — PR #170에선 순수 `QuotePicker`로 분리)*
- **`DashboardController`에만** 모델 추가(전체페이지 경로, 온보딩·admin 게이트 뒤). `DashboardModel.populate`엔 넣지 말 것 — htmx 라이브 경로와 공유라 start/stop마다 헛돌고 어차피 라이브 프래그먼트 밖이라 안 그려짐. *(유지)*
- **캐시 금지**(`@Cacheable` X — 매 렌더 새로 뽑아야 매번 바뀜). *(유지)*
- ⚠️ **PR #170에서 바뀐 것**: 소스가 정적 JSON → **DB(`quote` 테이블)**, `QuoteService`는 불변 목록 → **`QuoteRepository` 백업**(매 호출 `findAll`, 빈 목록이면 폴백). `Quote`는 record → `@Entity`. 템플릿은 `${quote.text}`/`${quote.author}`(getter).

**🔮 미래 진화 (지금 우선순위 아님 — 이 기능이 자라는 방향)**:
1. **MVP + DB/admin 관리 (위, 완료)** — DB 백업 + 운영자 추가/삭제. ③ DB 테이블 인프라(`quote`·`QuoteRepository`)가 이제 깔려 있어 아래 ②③의 토대가 됨.
2. **책장 작가 필터** — 사용자 **책장 속 책의 작가**(`book.author`)가 남긴 격언만 뜨게. 격언에 작가 키를 달고 사용자의 distinct 책장 작가와 매칭. "내가 읽는 작가의 말"이라 개인화·몰입↑.
3. **사용자 저장 문장(하이라이트)** — 책을 읽다 **인상깊은 문장을 저장**하는 기능(신규 엔티티 예: `Highlight{book, text, page}`) 추가 시, 대시보드가 **사용자 본인이 저장한 문장**을 회전 노출(비면 큐레이션으로 폴백). 이게 큰 단계 — 저장 UI·저장소·소스 전환 필요.
- **지금 설계로 길 터두기**: 대시보드가 "이 사용자용 격언 하나 줘"라고만 묻도록 `QuoteSource`(또는 `QuoteProvider`) 이음새를 두면, 구현이 ①내장→②책장필터→③사용자저장으로 **컨트롤러·템플릿 안 건드리고** 갈아끼워진다.

### 책 단위 기록 (Book) — README §2.3
- **1단계 완료 ✅ 2026-06-02**: 책 등록·목록(`/books`). 알라딘 OpenAPI 검색(포트/어댑터 `BookSearchClient`
  → `AladinBookSearchClient`, TTBKey=env) → "책장에 추가", 상태(읽고싶음/읽는중/완독)·삭제, 소유권 검사.
  키 없으면 수동 입력 폴백. 구매링크에 제휴 태그 토대(제휴 고지 푸터 포함). Flyway V3.
  ✅ 외부 완료: **알라딘 TTBKey 발급 + SSM 주입**(검색·제휴 라이브 활성화, 2026-06-03 갱신 이력 참고).
  ⚠️ **제휴 추적 결함 발견·수정 2026-07-04**: TTBKey는 검색 *요청*엔 실렸으나(검색은 정상 작동) *응답 link*에 실리려면 `includeKey=1`이 필요한데(알라딘 기본 0) 이를 안 보내, 저장·302 재생되는 구매링크에 ttbkey가 빠져 **제휴 추적이 실제론 0**이었다(운영 Chrome 실측 2건 확정 — `/buy` 목적지가 `…&partner=openAPI&start=api`로 ttbkey 부재; 쿠팡 N-146/T-129와 동일 계열이나 '합성'이 아니라 'API 옵션 미설정'). `includeKey=1` 추가 + 기존 저장분 백필(`BookCatalogBackfillService.backfillPurchaseLinks` + `/admin/books/backfill-purchase-links`)로 복구. **✅ 운영 백필 실행 완료·실측 확정 2026-07-04**: admin `POST /admin/books/backfill-purchase-links` 1회 실행 = 조회 45·갱신 45·미발견 0·남은 0(등록 전량, limit 50 ≥ 45), 운영 Chrome 재실측으로 `/buy` 302 목적지가 백필 전 `…&partner=openAPI&start=api`(ttbkey 부재) → 후 `…&ttbkey=ttbkimsadol…&partner=openAPI&start=api`(실림)로 전환 확정 — 신규 검색은 `includeKey=1`, 기존 45권은 백필로 전량 추적 복구(남은 0이라 더 돌릴 것 없음). 상세 changelog 2026-07-04, 자동 메모리 `aladin-affiliate-tracking-broken`.
- **2단계 완료 ✅ 2026-06-03**: `ReadingSession`에 nullable `book` 연결(Flyway V4) + 타이머 시작 시 책 선택
  (대시보드 드롭다운, "선택 안 함" 포함, 소유권 검사) + 책별 누적 시간 집계(`BookReadingStatsService`,
  완료·책지정 세션 합) → `/books`에 책별 시간 표시. 측정 중 책은 대시보드에 노출.
  ⚠️ 디버깅: SSR 앱엔 ObjectMapper 빈 없음(T-022)은 1단계에서 발생, 해결됨.
- **3단계** (조각별 PR로 진행):
  - **①-a 책 시작 시 상태 자동 전환 완료 ✅ 2026-06-03**: 읽고싶음 책으로 타이머를 시작하면 자동으로
    읽는중 전환(`Book.startReading()` 멱등 — 읽는중/완독은 불변, 완독 되돌리지 않음). 전환 시에만 저장
    (`ReadingSessionService`). TDD: BookTest·ReadingSessionServiceTest·ReadingSessionControllerTest.
  - **②-b 제휴 클릭 추적 완료 ✅ 2026-06-03**: "구매"를 서버 경유(`GET /books/{id}/buy`)로 카운트한 뒤
    알라딘 제휴링크로 302 리다이렉트(`Book.clickCount`, Flyway V5). 소유권(IDOR)·링크 없으면 미집계.
    수익 데이터 토대(어떤 책이 구매 의향을 내나). 개념: N-033(클릭 추적 GET 리다이렉트·CSRF·오픈 리다이렉트).
    TDD: BookTest·BookServiceTest·BookControllerTest.
    - **확장 ✅ 2026-06-06 (#199)**: 구매 진입점을 **남의 책방(공개 프로필)까지** 확장 — 다른 사람의 책방에서
      바로 구매(`GET /u/{loginId}/books/{id}/buy`). 내 책 전용 `/books/{id}/buy`는 소유권(IDOR) 게이트라 남의
      책엔 못 써, **공개(PUBLIC) 여부**를 게이트로 둔 `recordPublicPurchaseClick` 신설(비공개·없는 책은 임의 id
      탐침 차단). 클릭은 **책 주인 카운트**에 집계 — 근거: 카운트 목적이 *"어떤 책이 구매 의향을 내나"(제휴 수익
      분석)*이고 **행 단위 적립→ISBN 롤업**으로 읽으므로 귀속 주체는 부수적, 신호를 버리지 않는 게 핵심(집계 안 함이
      최대 손실). 보상 정산 모델 도입 시 viewer/owner 분리(재검토). 상세 근거는 `recordPublicPurchaseClick` javadoc. TDD.
      - **본인 책방 숨김 ✅ 2026-06-06 (#202)**: 같은 프로필 템플릿이 본인/남 양쪽을 그리므로, 구매 버튼은
        **`self`(본인 여부) 플래그로 남의 책방에서만** 노출(내 책은 이미 내 것 → 살 이유 없음). 제휴 안내문도 본인 책방엔
        숨김(버튼 없으니 무의미). `${b.purchaseLink and !self}`로 했다 SpringEL boolean 강제로 깨져
        `${!#strings.isEmpty(b.purchaseLink) and !self}`로 정정(T-031 확장). TDD: 본인 책방 음성 렌더 테스트 추가.
    - **쿠팡 파트너스 병행 ✅ 2026-06-11 (#309)**: 알라딘 "구매" 옆에 쿠팡 "구매" 버튼을 나란히 추가(검색·등록은
      계속 알라딘 API). 알라딘은 API가 통째로 준 링크를 DB에 저장하지만, 쿠팡 검색 링크는 `f(ISBN·제목, 추적코드, 템플릿)`로
      결정적이라 **저장 없이 런타임 생성**(`CoupangLinkBuilder`, 백필 0 — 기존 책 전부 즉시 버튼). 추적코드가
      `not-configured`(가입 전 기본)면 `coupangEnabled=false`로 **버튼·공정위 고지문구를 화면에서 숨김** — 쿠팡 파트너스
      가입 후 환경변수(`BOOKTIMER_COUPANG_TRACKING_CODE`/`_SEARCH_URL_TEMPLATE`) 주입만으로 점진 활성화(코드 변경 0).
      엔드포인트는 알라딘과 별도(`/buy/coupang`·`/u/{loginId}/books/{id}/buy/coupang`, 회귀 0), 카운트도 분리
      (`Book.coupangClickCount`, Flyway V34). `coupangEnabled`는 `AdsModelAdvice` 패턴의 전역 `@ControllerAdvice`로 주입.
      TDD: CoupangLinkBuilderTest(순수)·CoupangBookServiceTest(IDOR·비활성·공개 게이트)·
      CoupangBuyControllerTest(엔드포인트 + 뷰 게이트 렌더).
      - **가입·추적 형식 확정 + 배포 env 자리 ✅ 2026-06-12 (#319)**: 쿠팡 파트너스 가입(개인) 완료, 추적코드 `AF…` 발급.
        링크 생성 도구 도착 URL로 추적 형식 실측 — 추적 키는 **`lptag` 파라미터**(쿠팡 URL에 `&channel=user&lptag=AF…`
        부착 시 수익 귀속, 단축 링크는 편의 껍데기일 뿐 실제 귀속은 도착 URL의 `lptag`). 우리 런타임 생성(`{trackingCode}`를
        `lptag`에 치환)과 일치 확인. `task-definition.json` secrets에 두 env 자리 추가(알라딘 ttb-key와 동일 SSM 주입).
        **남은 점등**: SSM에 `COUPANG_TRACKING_CODE=AF…`·`COUPANG_SEARCH_URL_TEMPLATE=…?q={query}&channel=user&lptag={trackingCode}`
        주입 + 재배포(사용자 작업). ⚠️ 잔여: ISBN 검색 품질 재평가·API 딥링크 키는 판매 15만원 실적 게이트 뒤(초기 검색 링크 방식)·
        `lptag` 직접 부착의 약관 허용 범위 확인.
      - **추적 0 발견 → 딥링크 API 연동(dark-launch) ✅ 2026-07-03**: 위 `lptag` 직접 부착 방식이 파트너스 리포트에
        **클릭 0**으로 잡혀 조사 — `lptag`만 붙인 자작 검색 URL은 파트너스가 "생성"한 정식 링크가 아니라 구조적으로
        미집계(수익 링크 판별 조건 `isshortened=Y` 부재, [N-146](claude-docs/learning-notes.md)/[T-129](claude-docs/troubleshooting.md)).
        `CoupangDeeplinkClient`(HMAC-SHA256 서명 딥링크 API 호출)로 정식 추적링크(`shortenUrl`)를 생성해 그걸로 리다이렉트하도록
        교체. **API 키는 여전히 파트너스 '최종 승인'(누적 판매 15만원) 게이트 뒤라 미발급** → 활성 게이트를 기존 `lptag`
        `tracking-code`에서 `CoupangDeeplinkProperties.isEnabled()`(access-key·secret-key 존재)로 재정의 — 키 미설정 시
        API 호출 자체를 시도하지 않고 raw 검색 URL로 즉시 폴백(버튼 작동 유지, 그 클릭만 추적 안 됨). 키 확보 후
        SSM(`BOOKTIMER_COUPANG_ACCESS_KEY`/`SECRET_KEY`/`SUB_ID`) 주입만으로 점등(코드 변경 0). 캐시는 인메모리
        (옵션 A — `computeIfAbsent`로 동시요청도 API 1회만, 재기동 시 초기화되나 단일/소수 인스턴스라 무해). TDD:
        CoupangDeeplinkSignerTest(openssl 독립 HMAC 벡터)·CoupangDeeplinkPropertiesTest(키+subId 게이트)·
        CoupangDeeplinkClientTest(MockRestServiceServer)·CoupangBookServiceTest 확장(딥링크 성공/실패 폴백 2케이스 추가)·
        기존 CoupangBuyControllerTest 4케이스 회귀 유지.
      - **알라딘도 추적 0이었다 → `&amp;` 되돌리기 ✅ 2026-08-22**: 미니앱에 구매 흐름을 붙일 수 있는지
        조사하다 모바일 귀속을 실측했고, 그 김에 **알라딘 제휴가 점등 이래 통째로 무추적이었음**을 발견했다
        (운영 DB 실측: 제휴 링크를 가진 책 **47권 중 47권이 깨짐 · 정상 0권 · 클릭 22회 전부 무추적**,
        최초 2026-06-05 ~ 최근 2026-08-13) — 알라딘 API가 응답 JSON의 **`link` 필드만** `&`를 `&amp;`로 인코딩해 주는데(20개 항목 전부,
        다른 필드는 0건) 파싱이 그대로 저장해, 알라딘이 파라미터를 `amp;ttbkey`·`amp;partner`로 읽어 제휴 식별에
        실패한다. **상품 페이지는 정확히 뜨므로 화면 증상이 0**이고 수수료만 죽는다(위 쿠팡 「추적 0」과 같은 클래스의
        무성 실패지만, 그쪽은 리포트에 클릭 0으로 드러난 반면 이건 리포트를 봐도 트래픽 부족과 구분되지 않는다).
        `AladinBookSearchClient.parse()`에 `unescapeAmp()` 1회 — 검색·ItemList·ItemLookUp·백필이 모두 이 메서드
        하나를 지나 진입점 전체가 덮인다 — 와 기존 저장분 일괄 치환(Flyway V76). ⚠️ **백필은 이걸 원리상 못 걷어낸다**:
        백필 대상 조회가 `'ttbkey='` 포함 여부로 "이미 제휴링크가 실렸다"를 판정하는데 `&amp;ttbkey=`에도 그
        문자열이 있어 전부 건너뛴다. **판정 기준을 「페이지가 뜨는가」에서 「귀속 쿠키가 심기는가」로 바꾼 것이
        발견의 전부다.** 부수 소득: 알라딘은 **모바일 UA에서도 귀속이 살아 있다**(iPhone·Android 둘 다
        `m/mproduct.aspx`의 같은 상품 착지 + `partner` 쿠키) — 목적지를 모바일 메인으로 갈아치우는 Yes24(T-128)와
        정반대라, 미니앱(100% 모바일)에 구매 링크를 붙일 근거가 됐다. TDD: AladinBookSearchClientTest에 엔티티
        복원 + 정상 링크 불변(이중 디코딩 금지) 단언. 상세는 T-202.
        ⚠️ **이 군의 3회차이고, 위 「추적 0 발견」(쿠팡)·T-131(알라딘)에 이어 「이미 닫았다」던 자리에서 다시 샜다** —
        T-131이 `includeKey=1`+백필(#644, 2026-07-04)로 ttbkey를 링크에 싣고 "운영 실측 2건으로 확정"했지만
        그 실측은 **「링크에 ttbkey가 있나」**를 봤고 `&amp;ttbkey=`에도 있어 통과했다(그 뒤 7주 더 무추적).
        그래서 코드뿐 아니라 **재발·승격 트래커의 판정 기준을 「추적자가 실렸나」→「추적이 실제로 먹히나」로
        격상**했다. 새 제휴를 붙이거나 추적을 고칠 때는 링크를 한 건 열어 **귀속 신호**(알라딘=`partner` 쿠키 /
        쿠팡=파트너스 리포트 클릭 / YES24=linkprice 래퍼 통과)를 확인한다 — 문자열 검사로는 이 군을 못 잡는다.
    - **미니앱에 구매 줄 ✅ 2026-08-22**: 위 실측으로 「알라딘은 모바일에서도 귀속된다」가 확인되고 추적 자체가
      살아나자, 미뤄 뒀던 미니앱 구매 흐름을 붙였다. **새로 뚫을 관문이 없었다** — 앱인토스 가이드라인 2-2절이
      「각 제품을 소개·추천한 뒤 최저가 구매 플랫폼으로 이동」을 허용 예시로 명시하고, 외부 링크 열기
      (`Device.openURL`)는 홈 피드 「책 뉴스」·설정 약관에서 이미 심사를 통과해 돌고 있으며, `purchaseLink`도
      서버가 이미 미니앱까지 내려주고 있었다(담을 때 되돌려 저장만 하고 화면에선 안 썼다). 붙인 곳은 시트 둘
      (서재 「관리」 = 삭제 위 / 책 담기 = 담기 3종 뒤)이고 구현은 **공용 `BuyRow` 하나**다 — 고지문구를 같은
      컴포넌트에 묶어 **링크가 없으면 줄과 고지가 함께 사라지게** 했다(살 곳 없는 고지는 거짓말이라 테스트로 못 박음).
      ⚠️ 목 픽스처의 `purchaseLink`가 전부 `null`이라 **이 기능이 목 모드 검증 경로에 아예 없었다**(T-191 계열
      사각) → 짝수 id 책·검색 결과 2권에만 링크를 넣어 **있는 쪽/없는 쪽을 둘 다** 남겼다. 미니앱 944건 그린 +
      목 모드 실측(양쪽 분기·콘솔 0). **Yes24·쿠팡은 안 붙였다** — Yes24는 모바일에서 추적이 소실되고(T-128)
      쿠팡은 딥링크 API 키가 15만원 게이트 뒤라 미니앱에선 둘 다 수수료가 0이다.
        - **⚠️ 다중 에이전트 코드리뷰로 발견 → 버튼 노출을 딥링크 키에서 분리 (같은 날 후속 수정)**: 위 구현이
          `CoupangLinkBuilder.isEnabled()`(버튼·고지문구 노출)를 딥링크 API 키 게이트에 그대로 묶어, 파트너 가입은
          이미 완료(2026-06-12, #319)돼 운영에 떠 있던 버튼이 **키 미발급 상태 그대로 배포하면 사라지는 회귀**가
          됨(dark-launch=무영향이어야 하는데 실제론 후퇴). 8종 파인더+7건 검증 코드리뷰(전부 CONFIRMED)로 발견,
          사용자 확인 후 즉시 분리: `CoupangLinkBuilder.isEnabled()`는 새 독립 플래그
          `booktimer.coupang.partner-enabled`(기본 false, `task-definition.json`에서 운영만 명시적 `true`)로 되돌리고,
          딥링크 API 키 게이트는 `CoupangDeeplinkClient`에서만 독립 판단 — 버튼은 그대로 뜨고(raw 링크는 키 없이도
          유효한 구매 경로) 키가 오면 추적만 조용히 업그레이드. `buildSearchLink`는 운영 SSM에 남을 옛
          `{trackingCode}` 플레이스홀더도 방어적으로 제거. 같은 리뷰에서 확인된 나머지(캐시 동시요청 경쟁 →
          `computeIfAbsent`, RestClient 타임아웃 부재 → 커넥트 3초·리드 5초, 예외 로깅에 클래스명 추가, subId 누락 시
          정산 제외 사각 → `isEnabled()`에 subId 포함, 정적 Javadoc 정정)도 같은 커밋에 반영.
      - **구매처 선택 토글 통합 ✅ 2026-06-12 (#321)**: 점등으로 알라딘·쿠팡 "구매" 버튼이 한 행에 둘 나란히 뜨던 것을,
        구매처가 **2개일 때만 "구매" 토글**(`<details>` 순수 CSS·JS 0)로 묶어 펼쳐 고르게 함. **1개뿐이면 단일 버튼 유지**
        (불필요한 클릭 제거). 분기 조건(`#strings.isEmpty(purchaseLink)`·`coupangEnabled`)이 **매 렌더 그 책 데이터로 평가**돼
        나중에 구매처가 2개로 늘면 자동 토글 전환(코드 변경 0). 뷰 3곳(books/book-detail/profile)+`app.css` `.buy-menu`
        (기존 `manual-add` `<details>` 패턴 재사용), 백엔드 무변경. TDD: "2개면 토글+두 링크 / 1개뿐이면 단일" 경계 2개 추가.
        - **펼침 오버레이 다듬기 ✅ 2026-06-12 (#323)**: 펼친 목록이 흐름에 쌓여 박스가 커지며 형제("삭제")가 밀리던 것을,
          `.buy-menu-items`를 `position:absolute`로 흐름에서 빼내 "구매" 버튼 바로 아래 드롭다운으로 띄움 → 펼쳐도 박스 높이 고정·형제 안 흔들림. `app.css`만 변경.
    - **Yes24 제휴 병행 ✅ 2026-07-02**: 알라딘·쿠팡 옆에 Yes24 "구매"를 추가(쿠팡 `CoupangLinkBuilder` 패턴을 그대로
      미러링). `Yes24LinkBuilder`가 `f(ISBN·제목, 추적코드, 템플릿)`로 **저장 없이 런타임 생성**, 추적코드가 `not-configured`(가입 전
      기본)면 `yes24Enabled=false`로 버튼·고지문구를 숨김 → 가입 후 환경변수(`BOOKTIMER_YES24_TRACKING_CODE`/`_SEARCH_URL_TEMPLATE`)
      주입만으로 점등(코드 변경 0). 엔드포인트 별도(`/buy/yes24`·`/u/{loginId}/books/{id}/buy/yes24`), 카운트 분리
      (`Book.yes24ClickCount`, Flyway V55). 3제공자로 늘며 뷰 3곳(books/book-detail/profile)의 조합 분기(최대 7분기)를
      **`buyOptions` 리스트 렌더로 리팩터**(제공자 추가 시 push 한 줄, 조합 폭발 제거) — 단일 구매처는 제공자명 버튼으로 통일.
      `CoupangModelAdvice`는 두 제휴 플래그를 싣는 `AffiliateModelAdvice`로 리네임. TDD: Yes24LinkBuilderTest(순수)·
      Yes24BookServiceTest(IDOR·비활성·공개 게이트)·Yes24BuyControllerTest(엔드포인트) + 기존 4곳 누수/노출 가드.
      **점등 준비 완료 (2026-07-02)**: 링크프라이스 가입(affiliate `A100705638`)·리다이렉트 래퍼 템플릿(`lpweb.kr/click.php?…&tu=<Yes24 검색 URL>`) 확정,
      고지 문구를 알라딘 라인과 같은 집 문체(`Yes24 "구매" 링크는 제휴 링크로, 구매 시 일부 수수료를 받을 수 있습니다.`)로 3곳 통일, SSM 파라미터 2개 생성(현재 `YES24_TRACKING_CODE=not-configured`로 **꺼둠**).
      **남은 것 = 점등 스위치 하나**: SSM `YES24_TRACKING_CODE`를 실값(`A100705638`)으로 `--overwrite`+재배포하면 버튼·고지 노출(코드 변경 0·외부 작업).
      실 브라우저: books 구매 드롭다운·book-detail SSR `th:each`·Yes24 고지문구 확인(로컬 테스트 추적코드 주입).
      모바일 UA는 Yes24 게이트가 딥링크를 버려(모바일 메인 치환, T-128) 래퍼 없이 `m.yes24.com/search` 직행으로 분기(커미션은 데스크톱만) ✅ 2026-07-02.
      ⚠️ **추적 결함 발견·가드 2026-07-04**: 점등 후 운영 Chrome 실측 결과 `YES24_TRACKING_CODE`는 실값으로 켜졌으나(버튼 노출) **`YES24_SEARCH_URL_TEMPLATE`가 링크프라이스 래퍼가 아닌 순수 검색 URL**(`www.yes24.com/product/search?query=<ISBN>`)이라 추적코드가 링크에 안 실려 **데스크톱도 추적 0**이었다(알라딘 T-131과 같은 무성실패 계열, 2건 실측). 위 "남은 것 = TRACKING_CODE 스위치 하나"는 부정확 — TRACKING_CODE만 켜고 SEARCH_URL_TEMPLATE를 래퍼로 안 채우면 추적이 안 된다. `isEnabled()`가 `trackingCode`만 보던 것을 **템플릿에 `{trackingCode}` 포함까지 검증**하도록 가드 추가(추적 안 될 상황이면 버튼 숨김 — 무성실패를 명시화). **근본 복구**=SSM `YES24_SEARCH_URL_TEMPLATE`를 래퍼로 `--overwrite`. 상세 changelog 2026-07-04, 자동 메모리 `aladin-affiliate-tracking-broken`(YES24 후속 포함).
      ✅ **복구 완료·실측 확정 2026-07-04**: SSM `YES24_SEARCH_URL_TEMPLATE`를 링크프라이스 래퍼(실제 값 `newtip.net/click.php?m=yes24&a={trackingCode}&l=9999&l_cd1=3&l_cd2=0&tu=<Yes24 검색 URL>{query}` — 위 초안의 `lpweb.kr/a_id=`가 아니라 컨버터가 준 `newtip.net/a=` 형식)로 교체 후 재배포(GitHub Actions Deploy to ECS Fargate). 운영 Chrome 재실측: `/buy/yes24` referrer가 `www.yes24.com/Cooperate/Yes24Gateway.aspx?pid=…&ReturnURL=…`(링크프라이스 제휴 게이트, `pid` 실림) 경유 = 데스크톱 추적 복구 확정. (SSM 교체·재배포는 운영자 작업, 실측 검증은 Claude.)
      ✅ **실적 조회 자동화 착수 2026-07-04**: 커미션 확인을 브라우저·세션 없이 무인화하려고 LinkPrice 실적 조회 오픈 API(`api.linkprice.com/affiliate/translist.php`)를 집계·요약하는 로컬 스크립트 `.claude/scripts/affiliate-report.mjs` 신설(즉석 조회 A안). `auth_key` 발급(링크프라이스 문의)만 되면 `a_id=A100705638`로 판매·커미션·정산상태를 당겨 요약. 알라딘은 실적 조회 API 없음(TTB 2022 종료)·쿠팡 OFF라 YES24만 대상. 상세 changelog 2026-07-04.
      ✅ **auth_key 발급·점등 확인 2026-07-06**: 링크프라이스 담당자가 실적조회 오픈 API 인증키 발급(a_id=`A100705638`) → gitignored `.claude/.secrets/linkprice-auth`에 저장, env var 없이 `--test` 라이브 스모크 정상. 저장 시 `readAuthKey()`가 비밀 파일을 `.claude/scripts/.secrets/`(스크립트 폴더)에서 읽던 경로 버그를 발견 — 주석·`.gitignore`가 가리키는 `.claude/.secrets/`와 어긋나 문서대로 저장하면 실패, 코드 경로에 저장하면 gitignore 밖이라 키 커밋 위험(양방향 무성 함정). `secretPathFor()` 순수 함수 추출 + `..` 추가로 수정(TDD RED→GREEN). 이로써 실적 조회 무인화 실사용 가능. 상세 changelog 2026-07-06.
      첫 실 조회는 **0건**(202605~07 전부 `list_count=0` — 제휴 점등 직후라 확정 실적 없음·본인 클릭/구매 제외, 정상). 이 과정에서 `result=101`("정상 page 번호 아님"=실적 0건이면 반환할 page 없음)을 하드 에러로 처리하던 걸 `classifyResult()`로 "실적 없음"(빈 성공)으로 보강 — 실제 오류코드(100·300 등)는 error 유지. 상세 changelog 2026-07-06.
    - **교보문고 제휴 추가 (dark-launch) ✅ 2026-07-05**: 링크프라이스 4번째 머천트로 교보문고 "구매" 추가 — `Yes24LinkBuilder` 패턴을 그대로 미러링(대칭 복제).
      `KyoboLinkBuilder`가 `f(ISBN·제목, 추적코드, 템플릿)`로 저장 없이 런타임 생성(목적지는 교보 통합검색 URL — 상품 상세는 내부ID(S000…) 기반이라 ISBN 조립 불가),
      추적코드 `not-configured` 기본이면 `kyoboEnabled=false`로 버튼·고지문 숨김 → 대시보드에서 교보 링크프라이스 실 URL(m/l/tu) 발급 후 SSM
      `BOOKTIMER_KYOBO_TRACKING_CODE`/`_SEARCH_URL_TEMPLATE` 주입만으로 점등(코드 변경 0, 미점등 secret을 task-def valueFrom으로 안 걸어 T-130 롤백 회피).
      `isEnabled()` 이중 게이트(추적코드 AND 템플릿에 `{trackingCode}` 자리)로 Yes24 T-131 무성실패 방어 상속, 모바일 UA 분기 T-128 대칭(교보 게이트 실측은 점등 후).
      엔드포인트 `/buy/kyobo`·`/u/{loginId}/books/{id}/buy/kyobo`, 카운트 분리(`Book.kyoboClickCount`, Flyway V59). 활성 플래그 3경로(AffiliateModelAdvice SSR
      + BookApiController·ProfileApiController JSON 직접 주입 N-144), 뷰 3곳 buyOptions push + 고지문구 + 번들 재빌드. `application.properties`는 Yes24 대칭으로 키 미기재(@Value 기본값).
      실적 조회는 `affiliate-report.mjs`가 `m_id`로 교보를 자동 집계(코드 변경 0). TDD: KyoboLinkBuilderTest(게이트·인코딩·queryFor)·KyoboBuyControllerTest(302·집계·IDOR·공개/비공개) RED→GREEN.
      **점등 미지값**: 교보 m/l/tu 실값·모바일 게이트·SSM 주입은 대시보드 발급 시점 확정. 상세 changelog 2026-07-05.
  - **③-c 책 상세 페이지 완료 ✅ 2026-06-03**: `GET /books/{id}` — 책 메타 + 월별 일자 기록 + 누적 시간. 소유권 검사(IDOR, 없으면 책장으로).
    `BookContributionService`(세션 패키지, 유저 TZ 일자) + `findByUserAndBook`. 책장에서 제목 클릭 진입.
    TDD: BookContributionServiceTest(단위)·BookControllerTest(렌더·IDOR). **책 3단계 완료.**
    - **개편 ✅ 2026-06-20**: 누적 시간 소수점 제거(Thymeleaf `${}` 범위 → SpEL 정수 나눗셈), **책별 잔디 완전 제거**
      (`BookContributionService`에서 `ContributionGraphBuilder`·목표 스케줄 의존 들어냄 — 잔디는 점차 폐지 방향), 일자별 기록을
      독서 기록(`/history`)과 동일한 **월별 ◀▶ 스크롤**로 통일(`MonthlyReadingSection.groupByMonth` 공유 헬퍼 추출). 전체 잔디·프로필 잔디는 유지.
- SNS 확장의 핵심 컨텐츠 토대

### OAuth 소셜 로그인
- [x] **구글(Google OIDC)** — 완료·배포 (2026-06-02). find-or-create 프로비저닝, principal=email 통일,
      소셜 계정 UX 분기(비번 카드 숨김). Google 동의 화면은 Testing(테스트 사용자만) → 추후 게시(Publish)
- [x] **동의 화면 게시(Production 전환) 완료 ✅ 2026-06-03** — 테스트 사용자 100명 제한 해제, 누구나 Google 로그인 가능.
      스코프가 non-sensitive(`openid`/`email`/`profile`)라 **Google 검증 절차 없이 즉시 게시**, 코드 변경 0.
      체크리스트: [x] ① 개인정보처리방침 페이지 (`GET /privacy`, 공개·permitAll, 로그인/가입에서 링크) →
      [x] ② 동의 화면 브랜딩(앱 이름·지원 이메일) + 처리방침 URL `https://booktimer.click/privacy` 등록 →
      [x] ③ Console에서 Publish app(게시 상태=프로덕션, 사용자 유형=외부).
      ※ "OAuth 사용자 한도 100명"은 **민감/제한 범위 요청 시에만** 적용 — non-sensitive만 쓰므로 표시되어도 미적용(실질 무제한).
      ※ 보안 전제는 이미 충족(하드닝 #1 email_verified·#2 brute-force 완료, 사이트·LOCAL 가입은 이미 공개).
      ※ 게시 과정에서 Chrome "위험한 사이트" 오탐(T-027) 발생 → Search Console 도메인 인증 후 재평가로 **자연 해소**(Safe Browsing 등재 없음 확인).
- [ ] 카카오/네이버 등 추가 provider (선택)
- [ ] **(백로그) 온보딩에서 타임존도 받기** — 현재 온보딩 페이지는 하루 목표만 묻는다(초기값·상한은 7일 윈도우 전환으로 제거).
      구글 가입자는 가입 폼이 없어 타임존이 기본값 `Asia/Seoul`로 생성된다(설정에서 변경 가능).
      잔디 자정 경계·일일 누적이 타임존에 의존(N-010)하므로 해외 사용자 대응 시 온보딩 폼에 타임존
      드롭다운 추가가 자연스럽다. **우선순위 낮음** — 해외 사용자 유입은 아직 먼 얘기. 그때 착수.

### SNS 기능 — README §2.4

> ⚠️ **구현 전 설계 먼저 (필수)** — 코드부터 짜지 말 것. 공유 모델·프라이버시·관계 모델을 먼저 못 박고
> 별도 설계 문서로 합의한 뒤에 TDD로 들어간다. 이 기능은 데이터 노출·권한 경계가 핵심이라
> 설계 없이 시작하면 되돌리기 어려운 결정(공개 범위·스키마)이 코드에 굳어버린다.

> 📐 **설계 문서 → [claude-docs/sns-design.md](claude-docs/sns-design.md)** (사용자 요구사항 1차 확정 반영 2026-06-04).
> **확정 요구사항**: ① 서로 팔로우(단방향) · ② **책 단위 공개/비공개**(책마다 오픈 선택) · ③ 검색 시 **내 팔로우 중**
> 몇 명이 원함/읽음(팔로우 스코프 카운트) · ④⑤ 개인 프로필 페이지(공개 책장+잔디) · ⑥ **닉네임 유니크**(검색·핸들).
> 설계: 책별 `visibility`(PRIVATE 기본 백필)·`follow` 테이블·닉네임 유니크화(기존 중복/NULL 백필 선결)·canViewBook 게이트·
> 잔디 viewer 가시성 필터(비공개 책 세션 간접 누출 차단)·로드맵(①닉네임+책공개→②프로필→③팔로우→④팔로우스코프 카운트→⑤악용).
> **결론: SNS 대부분 SSR로 충분 → API-first big-bang 불필요.** 카운트 status 매핑·k익명은 4단계에서 확정(§11-4·5 해결). 남은 열린 질문은 닉네임 변경 정책(§11-3)·프로필 SEO 개방(§11-8) 정도.

> 🗺️ **로드맵 진행 상태** (정본 상세: [sns-design.md §7](claude-docs/sns-design.md)). 단계별 ✅는 머지·배포 완료 기준.
> - ✅ **1단계** (PR #108·#109) — 닉네임 유니크화(V7) + 책별 공개 토글(V8, BookVisibility PRIVATE 기본)
> - ✅ **2단계** (PR #111) — 개인 공개 프로필 `/u/{nickname}`(SSR, PUBLIC 책장+잔디, 비공개 책 세션 간접 누출 차단 §3.5) *(공개 잔디는 이후 프로필이 Vue 섬으로 전환되며 직렬화·렌더가 빠져 미배선 dead-code로 남아 2026-06-29 제거 — 현재 남의 잔디는 비노출)*
> - ✅ **3단계** (PR #112) — 닉네임 검색(부분일치·상한20) + 팔로우(follow V9, 자기팔로우 금지·멱등·언팔즉시) + 프로필 팔로우 카운트/버튼
> - ✅ **4단계** (PR #118) — 팔로우 스코프 인기 카운트("👥 팔로우 중 N명 원함 · M명 읽음", 책장·검색결과). 원함=WANT_TO_READ·읽음=READING∪FINISHED 확정, k익명 임계 없음(위험 제한적, 확정), isbn 일괄 group by(N+1 회피), Flyway 신규 없음
> - ✅ **4단계+ drill-down** — 카운트 배지를 클릭하면 "그 책을 원함/읽음인 **내 팔로우 명단**"(`GET /books/readers`). 카운트와 **같은 게이트**(팔로우·PUBLIC·distinct)로 신원만 펼침 — 각 팔로우 프로필의 PUBLIC 책장에서 어차피 보이는 것이라 새 노출 0, 임의 isbn에도 내 팔로우 공개책만(IDOR 없음). 행은 기존 `UserRowAssembler` 재사용. 전역 카운트는 채택 안 함(아래 §책 인기 카운트). Flyway 신규 없음. TDD
> - ✅ **5단계 (완료)** — 악용 방지. ✅ **차단(block)** (PR #121, 대칭 — 서로 팔로우·프로필 차단, V10, 차단 시 팔로우 양방향 해제, `/me/blocks` 해제, 탈퇴 정리). ✅ **신고(report)** (PR #127, reporter→reported, V11, 사유+상세, 쌍당 1건 멱등, 탈퇴 정리). ✅ **관리자 신고함**(PR #245, `/admin/reports` — 개발자가 신고자→대상·사유·상세를 최신순 검토 후 삭제, 문의함 패턴, 처음엔 "저장만"이었던 검토 UI 해소). ✅ **레이트리밋 + 열거완화 + 차단 검색숨김** (`RateLimitService` 사용자별 인메모리 — FOLLOW 30/분·SEARCH 20/분·REPORT 10/시간, 초과 시 드롭/안내; 검색 결과 차단 사용자 필터; 인메모리=인스턴스별 한계는 backlog).
> - 부수 픽스: 탈퇴 시 follow·book FK 자식 정리(PR #112·#113), sweep T-029·N-040(PR #114).
> - 보강: 본인 팔로워/팔로잉 목록 `/me/followers`·`/me/following`(PR #119).
> - 검색 UX: 책 검색 기준 제목/저자 분리(PR #123, SNS 외 실사용 픽스 — 아래 "실사용 발견" 섹션).
> - 🔀 **탐색(사용자 검색) IA 흡수 — 진입점 방식 ✅ (2026-06-26)** — 탐색은 SNS 발견 기능인데 대시보드 QuickNav 타일로 핵심 독서기능과 동급 비중을 차지(사용자 제안: 책방 안 검색창으로 합치자). 탐색 결과물(누른 사용자→`/u/<id>`=책방)이 곧 책방이라 같은 흐름 → **대시보드 탐색 타일 제거**. ⚠️ **흡수 1차(A안, #536)는 검색+친구추천 풀패널을 내 책방(self) 상단에 통째로 박아 책방 비대화**(피드백: "검색은 페이지 채울 큰 기능 아닌데 책방이 뚱뚱해졌다") → **보완: 책방엔 검색창처럼 생긴 슬림 진입 링크(`a.shop-search-entry`)만, 누르면 `/search`(검색+친구추천 본체)로 이동**(트위터식 — 책방=연결 장소라 진입점은 두되 무거운 본체는 전용 페이지가 소유). self-only(남의 책방 누수 방지). **B안(보류 — 반응 평가 후 재결정)**: 책방을 소셜 허브로 승격(중립 '책방 둘러보기' 랜딩). 결정·진화·재평가 트리거 = 자동 메모리 `explore-search-into-bookshop`.
> - ✅ **친구 추천 — 하이브리드 계단식 1단계 (2026-06-29)** — `/search`의 "친구 추천"을 **순수 랜덤 10명**에서 **신호 기반 계단식 + "추천 이유" 칩**으로 교체(SNS 추천 = 그래프 엔진 "아는 사람" + 관심사 엔진 "좋아할 사람" 하이브리드. 소규모라 무거운 ML 대신 단순·해석가능 조합 — StoryGraph식 "같은 책 신호" 재사용). 우선순위 생성기 사다리 = **G1 맞팔 후보**(나를 팔로우했는데 내가 안 한 사람 → "나를 팔로우함") → **G2 친구의 친구(FoF)**(내 팔로이가 팔로우 → "공통 친구 N명") → **C 같은 책**(내 읽음/완독 isbn ∩ 후보 PUBLIC 읽음 → "같이 읽은 책 N권") → **F 랜덤 폴백**(콜드스타트 — 항상 10명 채움). userId dedup·이유 합집합. 모든 생성기가 노출 불변식(운영자·본인·차단 양방향·login_id null 제외) + **이미 팔로우한 사람 제외**(동작 변경 — 기존 랜덤은 띄움)를 보존, 같은 책은 후보의 PUBLIC만(비공개 누수 가드). 스키마 변경 0(`follow` self-join 2개 + `book` theta-join 1개, `FollowScopePopularity` 패턴 복제). 추천 행만 `reasons` 칩 DTO(`RecommendedUser`)로 감싸 검색 행(`UserSearchResult`)은 무변경. **TDD RED→GREEN**: 리포지토리(FoF·맞팔·같은책 + 누수/N-055/차단/이미팔로우 가드)·서비스(사다리 순서·dedup·폴백·limit)·API(reasons JSON) 슬라이스+통합 테스트 RED→구현→GREEN(전체 그린), 프론트 vitest 칩 DOM. **2단계(후속, 보류)**: 성향 태그(종족) 영속화+유사도, ~~활동량 폴백(+V53 인덱스) → ✅ 2026-06-29 완료~~, 인기 폴백, 점수 블렌딩, recommend 캐시/레이트리밋.
> - ✅ **둘러보기 — 인스타 탐색의 구조를 빌린 발견 화면** (2026-08-20~, **코드 완료**) / ⬜ 실기기 확인 대기 — 위 추천은 **웹 `/search`에만** 있었고 미니앱은 「아이디를 정확히 아는 것」이 남의 책방에 닿는 유일한 길이라 친구 찾기가 사실상 막혀 있었다. 인스타 탐색에서 **디자인이 아니라 시스템만** 가져온다: 사람과 그 사람의 책을 무작위로 늘어놓고, 화면에 들어올 때마다 얼굴이 바뀐다. **서버 ✅ (PR-1)** — `recommend()`를 **고치지 않고 감싸는** `explore()` 신설(그 결정적 순서는 웹 `UserSearchPanel.vue`가 의존한다), 응답도 `/api/search`가 아니라 **`/api/explore`** 신설이라 웹 무변경. 셔플 = **사다리 3배수 풀 → 무작위 10명**(우선순위는 「누가 후보인가」, 무작위는 「이번에 누가 보이는가」). 책 = **누적 시간 순 4권 + 나와 겹친 책 2권이 앞자리**(이게 「같이 읽은 책 N권」 칩을 대신한다 — 칩은 폐지). ⚠️ **시점 정보 비노출** — 초·시각은 정렬 재료로만 쓰고 DTO에 필드를 두지 않는다. 공개 책 0권은 목록에서 제외(빈 카드 방지). 성능은 배치 2쿼리(후보 전원의 책·초를 각 1회). 둘러보기에서 걷어낸 **「공통 친구 N명」·「나를 팔로우함」은 책방(프로필)으로 이사** — 인스타 프로필의 「○○님 외 N명이 팔로우합니다」 자리(이름 2명 + 나머지는 접기). **미니앱 화면 ✅ (PR-2)** — 책방 탭 검색바를 누르면 열리던 「친구 찾기」 시트를 전체 화면 `screens/Explore.tsx`로 승격했다. 검색어를 넣으면 지금의 아이디 검색이 그 자리에 그대로 나온다(**기존 동작 무변경** — 검색 상태도 그대로 책방 셸이 들고, `SearchSheet`가 지키던 네 가지는 삭제가 아니라 이관). 카드에는 칩이 하나도 없고 시점 문구도 없다. 책방에는 인스타의 그 줄(겹친 아바타 + 「○○님, ○○님 외 N명이 팔로우합니다」)을 팔로우 버튼 아래·책 격자 위에 놓았고, 「나를 팔로우함」은 닉네임 옆 배지다. 새 필드는 전부 **선택 필드**라 서버·미니앱 배포 순서에 화면이 의존하지 않는다. ⚠️ 표지 폭은 실측으로 64 → **56**(64는 360px 기기에서 여유가 0). 375px 뷰포트에서 가로 스크롤 0 · 표지 4권 한 줄 · 책 권수와 무관하게 카드 높이 177px 동일 확인. 미니앱 871/871 + tsc 클린.

> 💡 **헷갈리기 쉬운 점 — "남에게 보여주려면 DB에 저장해야 하나?" → 독서 데이터는 이미 다 저장돼 있다.**
> "누가 어떤 책을 읽었나/읽는 중인가/얼마나 읽었나"는 이미 `book`(user_id 소유, status) +
> `reading_session`(book_id + duration_seconds)에 들어 있다. SNS에서 남의 걸 보여주는 건 **데이터 추가가
> 아니라 조회 주체를 바꾸는 것**(`where user_id = 나` → `= 그 사람`). 따라서 SNS가 **새로 저장해야 하는 건
> 독서 기록이 아니라 두 가지뿐**이다 — ① **관계**(팔로우/친구), ② **공개 범위**(전체/팔로워/비공개).
> 이 둘만 새 테이블/컬럼(`V6__…`)으로 더하고, `book`/`reading_session`은 그대로 둔다.
> 그리고 `where user_id`만 갈아끼우는 순간 **IDOR/공개범위 체크가 보안 경계**가 된다(비공개 기록 누출 방지).
> 개념 상세: learning-notes **N-037**.

- 사용자 간 독서 기록 / 책별 시간 공유 (별도 설계 필요) — **저장 대상은 관계+공개범위, 독서기록 아님(위 💡)**
- **설계에서 먼저 정해야 할 것**(아래는 1~3단계 진행하며 대부분 확정·구현됨 — 정본 [sns-design.md](claude-docs/sns-design.md). 4·5단계 분만 미결):
  - **공유 범위/프라이버시**: 무엇이 공개인가(독서 시간/책 목록/잔디?) · 기본값은 비공개인가 공개인가 · 유저별 토글
  - **관계 모델**: 팔로우(단방향) vs 친구(양방향) vs 전체 공개 피드 — 무엇을 먼저?
  - **권한 경계(IDOR)**: 남의 기록 조회 시 노출 가능 항목 화이트리스트, 비공개 데이터 차단
  - **스키마/마이그레이션**: 새 Flyway 버전(관계·공개설정 테이블), 기존 데이터 기본 공개값
  - **악용/스팸**: 차단·신고, 공개 프로필 열거 완화
  - **SSR 부하·SEO**: 공개 프로필 페이지의 렌더 위치(N-017) — 검색 노출/수익 축과 연계
- 토대: 책 단위 기록(완료)이 핵심 콘텐츠 → 공유 단위 후보는 "책별 누적 시간/잔디".
- **프론트/앱과의 선후**: SNS 완성은 프론트 교체·앱의 선행조건이 *아니다*. 단 SNS UI를 두 번(SSR→SPA) 짜지
  않으려면 프론트 결정과 순서를 맞춰야 한다. 데이터 설계(위)는 프론트와 무관하게 먼저 가능 — §프론트엔드 전환 💡 참고.

#### 책 인기 카운트 — "몇 명이 이 책을 읽는가" (집계 노출)
> 위 💡와 같은 맥락: **새 독서 데이터를 저장하는 게 아니라 기존 `book`을 집계해 숫자만 보여주는 것**이다.
> "누가 이 책을 가졌나/읽는 중인가"는 이미 `book`(`isbn`/제목 식별, `user_id` 소유, `status`)에 있다.
> 책 식별 키(알라딘 ISBN)로 `book`을 `group by` 해 **사람 수만 count**하면 된다 — 추가 테이블 없이 시작 가능.

> 🚫 **전역(전체 사용자) 카운트는 채택 안 함 (사용자 결정 2026-06-04).** 처음엔 "전체에서 N명 읽는 중"을
> 검색결과·책장에 노출하려 했으나, **팔로우 가치를 희석**한다고 판단해 접었다 — 전역 카운트는 *팔로우 없이도*
> 공짜로 사회적 증거를 주므로 "궁금하면 팔로우" 동기가 약해진다. 대신 카운트를 **팔로우 스코프로 가두고**
> (4단계, 이미 구현) 거기에 **drill-down(누가 읽는지, 4단계+)**을 얹어 *팔로우할수록 더 보인다*는 희소성으로
> 핵심 루프를 강화하는 쪽으로 결정. 아래 두 항목의 "전역 distinct count" 구상은 그래서 **보류/철회**한다.

- [x] ~~검색 결과 리스트에 "읽는 중 N · 완독 M" **전역** 표시~~ → **철회**. 팔로우 스코프 카운트(4단계)로 충족.
- [x] ~~내 책장에서 "몇 명이 이 책을 읽는 중인지" **전역** 표시~~ → **철회**. 팔로우 스코프 카운트 + drill-down(4단계·4단계+)으로 충족.

> 아래는 **철회된 전역 카운트 원문 구상**(참고용 보존) — 위 🚫 결정 전 설계라 더는 진행하지 않는다.

- [ ] **(철회·참고용) 검색 결과 리스트에 "읽는 중 N · 완독 M" 작게 표시** — 책을 검색(`/books/search`)해 알라딘에서
      받아온 결과를 리스트로 보여줄 때, 각 책 항목에 **그 책을 읽고 있는/읽은 사람 수를 작은 숫자로만** 노출.
  - 집계: 알라딘 결과의 ISBN을 키로 `book`에서 `status`별 **distinct user 수** count
    (`READING` → "읽는 중 N", `DONE` → "완독 M"). 0명이면 숨기거나 회색 처리.
  - **프라이버시 = 집계 노출**: 개인 식별 없이 **숫자(기수)만** 보여준다 → 공개범위 설계 부담이 작다
    (누가 읽는지는 안 보여주므로 IDOR/공개범위 경계가 개인 기록 노출보다 약함). 단, **소수 인원일 때
    재식별 위험**은 검토(예: 1~2명이면 사실상 특정 가능) — k-익명성 관점에서 최소 노출 임계값 고려.
  - 성능: 검색 페이지당 N개 ISBN을 **한 번의 `group by` 쿼리로 일괄 집계**(N+1 회피). 캐시 후보.
- [ ] **(철회·참고용) 내 책장에서 "몇 명이 이 책을 읽는 중인지" 표시** — `/books`(내 책장) 목록의 각 책에
      **현재 그 책을 `READING` 상태로 가진 사람 수**를 함께 보여준다(나 포함/제외 정책 결정 필요).
  - 검색 결과 카운트와 **같은 집계 로직 재사용**(ISBN 키 distinct user count) — 표시 위치만 책장 목록.
  - "함께 읽는 사람" 동기부여 축 → 추후 "이 책 같이 읽는 사람 보기"(관계/공개범위 도입 후) 진입점 후보.
- **설계 메모**: 이 카운트 기능은 관계(팔로우) 없이도 **집계만으로 선출시 가능**(SNS 풀스택보다 가벼움).
  단 책 동일성 키(ISBN 정규화 — ISBN10/13, 개정판/세트 처리)와 0명·소수 처리, N+1 회피가 선결.

### 📸 여백 (구 「독서 스토리」) — 책에 딸린 자리, 거기 쌓이는 글 (v1 출하 ✅ 2026-07-02 · 만료 폐지·개명 ✅ 2026-08-16 · **책 귀속 재설계 ✅ 2026-08-16**, 정본 [sns-design.md §13](claude-docs/sns-design.md))

> ⚠️ **2026-08-16 전환 — 「스토리」를 버리고 「여백」으로, 24시간 만료를 폐지했다 (사용자 결정).**
> 원래 의도는 "인상 깊은 문장·생각을 남기는 자리"였는데 인스타를 베끼며 딸려 온 **24h 휘발이 그 의도와
> 정면으로 어긋났다** — 남기라고 해 놓고 하루 만에 지우는 셈이라 기존 사용자에게 괴리를 준다. 어휘 후보
> 7종(밑줄·책갈피·문장·한 줄·여백·감상·필사)을 놓고 **여백**을 골랐다: 책 여백에 끄적이는 은유라 문장과
> 생각을 모두 담고, 기존 어휘(책방·서재·마을)와 층위가 안 겹친다.
>
> - **만료 → 표시 상한**: `LIFETIME`(24h) 삭제. 저장은 영구, 스트립·뷰어에 실리는 건 최근 20장.
> - **작성 20장 상한 폐지**: 그 값은 "24h 창 안의 개수"였다 — 만료 없이 두면 21번째부터 영영 못 쓰는
>   잠금이 된다. 도배 방어는 레이트리밋(시간당 10)이 그대로 맡는다.
> - **코드·API·테이블은 `story`로 남는다** — 사용자에게 보이는 어휘만 바꿨다(경로·스키마 개명은
>   마이그레이션을 부르는데 사용자가 보지 않는 값이다).
> - ⏸ 남은 사각(상한 20장 초과분을 볼 길이 없음)은 **같은 날 아래 재설계에서 해소**됐다.

> ⚠️⚠️ **2026-08-16 재설계 — 인스타 UI를 전면 폐기하고 여백을 책에 귀속시켰다 (사용자 결정, 시안 3장 합의).**
> 개명만으로는 괴리가 안 풀렸다: 이름은 「여백」인데 화면은 여전히 **사람 단위 링 → 전체화면 스토리 뷰어**라
> 인스타 문법 그대로였다. 생각은 원래 **책마다** 갈리므로, 사용자가 먼저 **책을 고르고** 그 책에 글을 남기는
> 구조로 뒤집었다.
>
> - **어휘가 한 겹 더 바뀐다**: 「여백」은 이제 *글 한 장*이 아니라 **책에 딸린 자리**이고, 거기 쌓이는 게
>   **「글」**이다(책 여백에 끄적이는 은유의 완성). 「여백 적기」→**「여백에 글 남기기」**, 「여백 3」→
>   **「여백에 남긴 글 3」**, 소식 문장은 **「○○님이 『책』의 여백에 글을 남겼어요」**.
> - **폐기**: 링 스트립·전체화면 뷰어·진행바·좌우 넘김·열람 기록(`story_view` 테이블 드롭)·표시 상한 20장.
>   상한은 진행바가 카드 수만큼 칸을 나눠 생긴 제약이라 **목록이 되면 존재 이유가 사라진다** → 위 ⏸ 해소.
> - **`story.book_id` 필수화 (V71)** — 여백은 책에 귀속된다. ⚠️ 연쇄: 책 삭제 시 글의 책 연결만 풀던
>   `unlinkBook`이 NOT NULL과 정면 충돌 → **글도 함께 삭제**(`deleteByBook`)로 바꿨다. 새 모델에선 자연스러운
>   의미다(자리가 사라지면 거기 쌓인 글도 사라진다). 운영 실측 후 진행: `book_id IS NULL` 2건 삭제(사용자 승인).
> - **24시간이 콘텐츠 수명 → 표시 이펙트로 이동**: 최근 24시간 안에 새 글이 달린 책은 **표지 테두리(정지) +
>   점 배지**. 지나면 이펙트만 꺼지고 글은 남는다. 서버는 원시 사실(`BookSummary.lastStoryAt`)만 주고 24h 판정은
>   클라 순수 함수가 한다(계측 가능 + 시간을 서버·클라가 이중으로 갖지 않게).
>   ⚠️ **처음엔 테두리가 «맥동»했으나 당일 걷어냈다 (2026-08-16, 실기기 회귀)** — `box-shadow` 무한 애니메이션이
>   표지 이미지를 감싼 칸에 걸려 초당 60번 재래스터화했고, 실기기에서 앱 전체가 느려지고 화면이 백지로 떴다(T-176).
>   신호 전달에 움직임은 필요 없다는 판단으로 정지 테두리 + 점만 남겼다.
> - **발견 경로 = 홈 소식**: 스트립을 없애면 남의 글을 볼 길이 사라지므로 `SocialEvent`에 `STORY` 타입을 더했다.
>   ⚠️ **사람+책 단위로 묶는다** — 안 묶으면 한 사람이 하루 4장 적을 때 미리보기 3줄을 통째로 먹어 완독·읽기시작
>   소식이 첫 화면에서 밀려난다. 발췌 1줄을 함께 싣고(없으면 누를 이유가 없다) 그 줄만 눌러 그 책 여백으로 점프.
> - **웹도 같이 이식** (사용자 결정 — 초안의 "웹 은퇴"는 기각). 단 화면 문법은 통일하지 않는다: 웹 책방은 격자가
>   아니라 구매 버튼·드롭다운이 든 **리스트 행**이라 행 전체 클릭이 충돌한다 → 표지 발광 + 「여백」 손잡이 →
>   오버레이 패널. ⚠️ 옛 뷰어에 있던 **타인 글 신고 배선을 패널로 이사**시켰다(그냥 지웠으면 안전장치가 사라진다).
> - ⏸ **웹에는 소식 피드 자체가 없다**(실측) — 새로 만들지 않았다. 웹은 「내 책방 발광 + 책별 글 목록」까지고
>   남의 글 발견은 미니앱 전용이다. 웹 발견 경로는 필요해지면 그때 판단.

> ⚠️ **2026-08-16 결정 2 — 비공개(PRIVATE) 책에도 여백을 허용한다 (사용자 결정, 설계
> [claude-docs/plans/2026-08-16-여백-진입경로-재설계.md](claude-docs/plans/2026-08-16-여백-진입경로-재설계.md)).**
> 아래 「첨부는 본인 소유+PUBLIC 책만」(2026-07-02 설계 요지)은 **여기서 완화됐다** — 비공개 책의 여백은
> *나만 보는 메모*다. 진입 동선도 책방 격자(=공개 책만 진열) 하나뿐이라 비공개 책엔 여백이 아예 없었다.
>
> - **새 불변식**: 글은 책 소유자만 쓴다(불변) · 남에게는 「책 PUBLIC + 팔로워」일 때만 보인다 ·
>   비공개 책의 여백은 소유자 전용. 글에 자체 공개 필드가 없어 **가시성은 언제나 읽기 시점에 책에서 파생**된다.
> - **서버(PR 1) ✅ 2026-08-16**: 쓰기 게이트 2곳 삭제(`Story.of`의 IAE · `StoryService.create`의 400),
>   읽기 게이트는 `marginOf`에서 `!isPublic && !self → 404`로 **소유자만 예외**. 남에게는 오늘과 완전히 동일하다
>   (존재조차 404 — 팔로워여도). 소식 피드의 `b.visibility = PUBLIC` 재검사는 전환 창 백업에서 **주 방어로
>   승격**(쿼리 변경 0줄). 함께 DTO 3필드(`MarginBook.isPublic`·`BookOption.isPublic`·`MyBookSummary.storyCount`)
>   + 글 수 배치 count 쿼리(`StoryRepository.countByBook`).
> - **PRIVATE→PUBLIC 전환이 새 위험면**이다(안 보이던 메모가 한꺼번에 노출) → 2중 고지: 쓰는 순간 캡션 +
>   공개하는 순간 확인 시트.
> - **웹(PR 2) ✅ 2026-08-16**: `BooksApp.vue`의 공개 토글에 고지를 넣었다 — **비공개→공개 방향 + 여백 글이
>   있을 때만**(되돌리는 방향엔 묻지 않는다, `storyCount` 없는 옛 응답은 fail-open). 확인 모달을 새로 만들지 않고
>   같은 파일이 삭제에 이미 쓰는 **네이티브 `confirm()`** 문법을 재사용해 4줄로 끝냈다. ⚠️ **삽입 위치가 곧
>   방어다** — `confirm()`은 동기 블로킹이라 **낙관 갱신보다 앞**에 둬야 「확인 전에 화면이 먼저 공개로 바뀌는
>   창」이 구조적으로 없다. 그 배치를 취소 테스트(취소 시 `isPublic` 불변 + 서버 요청 0)로 못 박았다.
> - **미니앱(PR 3) ✅ 2026-08-16**: 쓰기 동선을 책방에서 **홈·서재로** 옮겼다. 홈은 주 버튼 아래 「여백에 글
>   남기기」로 **1탭 작성 직행**(대상 책 = 측정 중이면 활성 책, 대기 중이면 캐러셀에서 고른 책 —
>   `marginDoorBook`), 서재는 「관리」 옆 「여백」 손잡이로 여백 화면. 핸들(@아이디)이 없으면 두 문 다 그리지
>   않는다(서버가 여백 대상을 loginId로만 찾아 눌러도 안 열린다). ⚠️ **여백을 App 전역 뷰로 올린 것이 이 PR의
>   구조 변경**이다 — 예전엔 홈 소식에서 여백을 열면 **책방 탭으로 점프**해 뒤로 갔을 때 남의 전시장이 나왔다.
>   이제 `goal`·`settings`처럼 탭 위에 전체 화면으로 서서 **뒤로 = 열었던 탭**이고, 그 전달 수단이던
>   `marginTarget` 배선은 통째로 삭제했다(책방 격자→여백은 책방 셸 내부 상태 그대로 — 회귀 없음).
>   캡션은 `visibilityNotice`(비공개면 「나만 봐요」, 필드 없는 옛 서버는 공개로 간주 — 새는 글을 안 샌다고
>   말하는 쪽이 더 위험한 거짓말이다), 확인 시트는 `needsPublishConfirm`(비공개 + 글 1개 이상일 때만).
> - **후속 ✅ 2026-08-16 — 홈 캐러셀 선택 유지**: 여백을 전역 뷰로 올린 대가로 홈이 언마운트돼, 기본값 아닌
>   책을 골라 글을 쓰고 오면 선택이 되돌아갔다(PR 3의 「알려진 한계」). 고른 책 상태를 **App으로 올려** 닫았다 —
>   `undefined`(아직 안 고름)와 `null`(「책 없이」)은 다른 값이다(합치면 「책 없이」 사용자가 매번 끌려간다).
> - **후속 ✅ 2026-08-16 — 서재 인라인 여백 박스**: PR 3의 서재 「여백」 손잡이는 **누르기 전엔 아무것도
>   말하지 않는 문**이었다(글이 있는지조차 목록 화면까지 가야 안다). 고른 책 아래 인라인 박스(헤더 「여백 N」
>   + 최근 2장 + 「전체 보기 ›」)를 세우고, 손잡이 줄은 **「여백에 글쓰기」(작성 직행) + 「관리」 전폭 2:1**로
>   갈랐다 — 들춰보기를 박스가 맡으니 손잡이가 홈 문과 같은 작성 직행이 된다. 카드는 여백 화면 `MarginCard`를
>   `self=false`로 재사용(지우기는 전체 화면의 몫). ⚠️ **캐러셀 훑기의 경합에 가드를 둘 뒀다** — 쓰기 가드
>   (effect cleanup)만으론 「응답 오기 전까지 옛 책 글이 보이는」 구간을 못 막고, 렌더 가드(스냅의 `bookId`
>   태그 ≠ 지금 책 → 로딩)만으론 늦은 응답이 마지막에 덮일 때 **재요청 트리거가 없어 교착**한다. 요청 폭주는
>   2단 debounce(캐러셀 settle 120ms + 박스 300ms)로 누른다(목 모드 실측: 3권 훑어 1건). 응답 캐시는
>   ⏸ **의도적 보류** — 리마운트 재조회가 곧 작성 후 복귀 반영이라, 캐시 무효화를 얹으면 stale 사고가 새로 난다.
> - **후속 ✅ 2026-08-16 — 나가는 길 정리(작성 후 복귀 · 뒤로가기 모양)**: 작성 직행으로 들어가도 여백 상세를
>   **밑에 깔고** 그 위에 서던 구조라, 쓰고 나오면 출발한 탭이 아니라 한 번도 요청하지 않은 여백 상세에 떨어졌다.
>   `MarginState.bookId`를 nullable로 바꿔 **깔린 화면이 없으면 통째로 닫는다**(순수 함수 `closeCompose`).
>   이로써 여백 상세로 가는 문은 **서재 박스의 「전체 보기 ›」 하나**로 좁혀졌다 — 자주 볼 화면이 아니라
>   원할 때만 들어가는 자리라는 사용자 판단. 작성 화면의 헤더 뒤로가기는 「취소」와 중복이라 없앴고, 남긴
>   손잡이는 **「‹ 돌아가기」 알약을 제목 위 줄**로 바꿨다(`Screen` 공용).
> - **후속 ✅ 2026-08-16 — 나가는 길 통일**: 위 변경의 영향 범위를 전수 조사하다 남의 책방 실패 화면에서
>   상단 알약 + 하단 버튼 **중복**을 발견했고, 그 화면 코드에 「제목 옆 ← 는 안 둔다 — 버튼으로 안 읽혔다」는
>   **같은 문제의 옛 회피 주석**이 남아 있었다. 알약으로 회피 이유가 사라졌으므로 남의 책방·책 추가·기존 계정
>   연결의 하단 「돌아가기」를 걷고 **상단 손잡이 하나**로 통일했다. 하단 버튼의 `disabled={busy}`는 버리지 않고
>   `Screen`의 `backDisabled`로 옮겼다(감추면 34px 줄이 사라졌다 나타나 화면이 튄다).
>   ⏸ **목표 화면은 그대로** — 그 하단 버튼은 첫 실행 때 「나중에 정할래요」가 되는 *건너뛰기*라 뒤로가기가 아니다.

> ⚠️ **2026-08-22 책축 개방 — 「함께 걸기」로 여백을 사람축에서 책축으로 연다 (사용자 결정, 설계
> [claude-docs/plans/2026-08-22-여백-책축-개방.md](claude-docs/plans/2026-08-22-여백-책축-개방.md)).**
> 지금까지 여백에 도달하는 좌표는 전부 **사람축**(`loginId + bookId`)이었고 노출 게이트 셋이 전부 **팔로우를
> 전제**했다 — 같은 책을 읽는 낯선 사람의 글은 원리상 닿을 수 없었다. 글 단위 opt-in 「함께 걸기」를 켜면
> 그 글이 **같은 책(isbn13)을 보는 누구에게나** 열린다(읽기·좋아요까지. 답글·댓글은 비목표).
>
> - **불변식(최상위 규칙)**: 노출 = `book.isPublic()` **AND** (팔로워 **OR** `shared`). `shared`는 게이트를
>   **여는** 값이 아니라 **좁히는** 값이다 — `shared=true`여도 책이 PRIVATE면 아무에게도 안 보인다.
>   **두 축을 OR로 잇는 코드는 이 불변식 위반이다.** 판정이 사는 곳은 정확히 둘(`StoryService.assertVisible`
>   단건 · `StoryRepository.sharedByIsbn` 목록)이고 서로 미러라 같이 고친다.
> - **기본 꺼짐 · 소급 노출 0** — V77의 `default false`가 DB 층에서 보장한다. 지금까지 쓴 사람들은
>   「팔로워에게 보여요」를 보고 썼으므로 그 약속을 깨지 않는다.
> - **PR-1 서버 ✅ 2026-08-22**: V77(`story.shared`) + 게이트 확장 + 신규 API 3종(`GET /api/stories/book/{isbn}`
>   · `POST`/`DELETE /api/stories/{id}/share`) + 검색 응답의 「여백 N」 배지 집계(`marginCounts`, 페이지당 1쿼리).
>   차단은 **쿼리가 직접 진다** — 팔로우 스코프 피드가 기대던 "팔로우 존재 → 차단 없음" write-시점 불변식이
>   팔로우 무관 목록엔 통하지 않기 때문이다. 클라 없이도 무해(신규 엔드포인트 미호출·추가 필드는 무시).
> - **PR-2 미니앱 ✅ 2026-08-22**: 작성 화면 「함께 걸기」 체크(기본 꺼짐) · 내 카드 상태 칩·토글 ·
>   「이 책의 여백」 화면 · 내 책이면 「내 여백 / 모두」 탭 · 검색 행 「여백 N」 배지 · dev-mock. 웹(Thymeleaf)은
>   **비목표** — 웹엔 여백 UI 자체가 없어 이식이 확장이 아니라 신축이다.
>   - ⚠️ **칩은 꺼진 글에도 선다** — 설계 §3은 「상태 칩(켜진 글만)」과 「사후에 켜고 끄기」를 함께 적었는데,
>     켜진 글에만 칩을 두면 **사후에 켜는 길이 카드에 없다**. 손잡이가 있을 때(=내 카드)는 꺼진 글에도
>     「함께 걸기」로 서고, 손잡이가 없으면(남의 카드) 켜진 글의 「함께 걸림」만 사실값으로 남는다.
>   - ⚠️ **책축 화면의 「내 여백」 탭은 사람축 화면으로 갈아탄다** — 같은 데이터를 두 컨테이너가 각자 들면
>     어긋나므로, 데이터는 한 곳에서만 들고 화면을 옮긴다(작성·삭제·토글이 사는 자리가 사람축이다).
>   - ⚠️ **검색 배지는 행 버튼 「바깥」의 형제다** — 버튼 안에 버튼을 넣으면 마크업이 깨지고, 담긴 책은
>     행이 `disabled`라 안쪽에 있으면 눌리지도 않는다(담긴 책일수록 그 책의 여백을 보고 싶다).
> - **PR-3 게시판 개편 ✅ 2026-08-22**: 책축을 연 다음 날 **화면이 목록을 못 따라간다**는 것이 드러났다 —
>   색 카드가 줄줄이 쌓이면 「글이 몇 개인가」조차 안 읽히고, 두 좌표계를 오가는 알약 탭은 「필터」로 보였다.
>   여백을 **게시판**으로 다시 짰다(사용자와 시안 3라운드 합의): 밑줄 2탭(`내가 쓴 여백` / `모두의 여백`) ·
>   흰 목록 카드 + 구분선 행 · 글쓰기는 게시판 머리 **우상단** 작은 버튼 · 긴 글 3줄 접기 + 「더보기」 ·
>   행의 동작을 **⋯ 시트**로 통합. 팔레트 6색은 행 왼쪽 3px 막대로 산다.
>   - ⚠️ **어휘를 한 벌로 통일했다** — 명사 「모두의 여백」, 동사 「올리기/내리기」. 옛 「함께 걸기」·
>     「이 책의 여백에 걸기」·「함께 걸림」이 <b>같은 것을 셋으로</b> 부르고 있었고, 사용자가 실제로 되물었다.
>     작성 화면 체크박스 문구까지 같은 말로 맞췄다(쓸 때와 고칠 때가 갈리면 같은 값으로 안 읽힌다).
>   - ⚠️ **주인 이름은 남의 여백에서만 선다** — 내 여백은 탭줄이 그 자리를 대신하지만, 남의 여백은 탭이
>     없어 그 줄이 「누구의 여백인가」의 유일한 좌표다. 지우면 그 화면의 주어가 사라진다.
>   - ⚠️ **접기 임계는 설계 추정이 틀렸다** — 90자로 잡았는데 목 모드 실측이 3줄 ≈ 73자였다(98자·84자
>     모두 4줄). 75로 내렸다. 여유는 위쪽에 둔다: 4줄이 안 접히면 행이 길어질 뿐이지만, 3줄 글의 「더보기」는
>     눌러도 아무것도 안 변하는 가짜 손잡이가 된다.
>   - ⚠️ **돌연변이 실측이 무계측 게이트를 하나 찾았다** — 「남의 여백이면 ⋯ 를 안 그린다」에 테스트가 없어
>     게이트를 지워도 전건 통과했다. 컨테이너가 손잡이를 언제나 넘기므로 그 게이트가 유일한 방어였다.
>   - ⚠️ **목이 「서재에 없는 책」을 만들 수 없었다** — 그 화면(탭·글쓰기 없음 + 담기 안내)을 눈으로 볼 길이
>     없어 dev-mock에 픽스처를 더했다. 목이 서버가 낼 수 있는 상태를 못 내는 것은 이 레포 3회차다(T-175).
>   - 카드 프롭은 여섯이 빠지고(`self`·`busy`·`confirming`·`onConfirmDelete`·`onDelete`·`onToggleShare`) 셋이
>     늘어 **순감**했다 — 동작이 시트로 옮겨 간 덕이다. 서재 인라인 미리보기도 같은 컴포넌트라 함께 행이 됐다.
>   - **후속 ✅ 2026-08-22 — 「모두의 여백」은 조회 전용**: 두 탭 모두에 세웠던 글쓰기를 「내가 쓴 여백」
>     한 곳으로 좁혔다(사용자 결정). 여러 사람의 글이 섞인 목록에 글쓰기가 있으면 **그 목록에 바로 쓴다**로
>     읽히는데, 실제로는 내 여백에 남고 「올리기」를 켜야 거기 실린다 — 문이 둘이면 그 차이를 화면이
>     설명할 자리가 없다. 조건으로 거르지 않고 `BookMarginAllView`의 `onCompose` **프롭 자체를 없앴다**:
>     조건은 다음 사람이 지우고 되살릴 수 있지만 없는 프롭은 타입이 막는다(D-10을 닫은 것과 같은 수법).
> - **PR-4 팔로우 축 제거 ✅ 2026-08-22**: 사용자가 실기기 테스트 중 알아챘다 — 「여백이 팔로워에게만 보이는
>   느낌이었는데 이제 불특정 다수에게 보인다」. 조사해 보니 **게이트가 절반만 작동**하고 있었다: 책축을 연
>   뒤로 낯선 사람의 글을 「모두의 여백」에서 읽을 수 있는데, 그 사람 책방으로 넘어오면 **「팔로우하면 볼 수
>   있어요」**를 만났다. 방금 읽은 그 글이 그 목록 안에 있는 채로. 팔로우를 **열람 권한에서 통째로 걷어**
>   모델을 한 문장으로 줄였다: **공개 책의 여백은 공개, 비공개 책의 여백은 나만.** 팔로우는 홈 소식
>   구독(`feedRecent`)에만 남는다 — 인스타와 같은 의미다(공개 계정은 누구나 읽고, 팔로우는 피드 구독).
>   - 서버 세 곳: `marginOf`(목록) · `assertVisible`(단건 좋아요·명단) · `recencyOf`(격자 발광).
>     `StoryService`의 `FollowService` 의존성과 `MarginResponse.following` 필드도 함께 지웠다 — 권한처럼
>     생긴 죽은 필드를 남기면 다음 사람이 게이트가 아직 있다고 읽는다.
>   - ⚠️ **이중 방어가 사라져 책 가시성이 유일한 방어가 됐다** — 그래서 이 PR에서 제일 중요한 것은 새 코드가
>     아니라 **안 뒤집은 테스트**다(PRIVATE 404 가드 12건 전원 PASS 확인). 「함께 걸었어도 책이 PRIVATE면 404」가
>     이제 최후 방어선이다.
>   - ⚠️ **가장 무거운 발견은 문구가 아니라 고지 결함이었다** — 서재의 공개 전환 확인이 「N개가 팔로워에게
>     보여요」였는데, 「모두의 여백」에 올린 글은 그때도 전체 공개였다(비공개 책에서도 켤 수 있다). 되돌리기
>     어려운 행동 직전에 **실제보다 좁게 고지**하고 있었다 → 「누구에게나」로.
>   - ⚠️ **체크박스의 성격이 바뀌었다** — 「모두의 여백에 올리기」는 권한 스위치가 아니라 **발견 경로 스위치**다
>     (안 올려도 공개 책이면 읽히고, 올려야 그 책을 보는 사람에게 발견된다). 문구도 노출("보여요")에서
>     게재("올라가요")로. 안 그러면 「안 올리면 안 보인다」는 반대 오해가 남는다.
>   - **소급 적용은 사용자 결정** — 기존 글도 함께 열었다. 앞으로 쓰는 글만 여는 안은 화면이 「이 글은
>     팔로워만, 저 글은 전체」 두 종류를 설명해야 해서, 지금 없애려는 혼란이 그대로 돌아온다.
>   - 목(`dev-mock`)도 같이 넓혔다 — 비팔로워를 빈 배열로 두면 「비팔로워도 읽힌다」를 목 모드에서 원리상
>     확인할 수 없다(T-175 계열 4회차).
> - ⏸ **보류**: ISBN10→13 변환·개정판/세트 동일성(같은 책의 다른 판은 여백이 갈라진다 — 아래 기존 보류 항목에
>   귀속. 체감은 「글이 덜 보임」이지 「잘못 보임」이 아니라 안전한 방향의 한계다) · 「내 서재에 담기」 전용
>   엔드포인트(`POST /api/books/by-isbn`, v1은 「검색에서 담을 수 있어요」 안내로 코드 0) · 남의 책방 격자·홈
>   캐러셀에서의 책축 진입(v1 관객은 검색·내 서재만) · 책축 목록 페이지네이션(사람축과 같은 상한 100) ·
>   「이 책을 N명이 읽음」류 사람 수 집계(2026-06-04 결정 유효 — 글 수만 노출).

> **컨셉(원안, 2026-07-02)**: 인스타그램 "스토리"를 차용 — 책을 읽다 인상 깊은 **문장**을 올리면
> **나를 팔로우한 사람들에게 24시간 동안만** 보이고 만료 후 사라진다. UI도 인스타 스토리를 거의 그대로
> 베낀다(상단 동그란 아바타 링 → 탭하면 풀스크린 카드, 진행바·자동 넘김·좌우 탭 이동).
> *(↑ 이 원안은 2026-08-16에 통째로 폐기됐다 — 만료도, 링·뷰어·진행바도. 지금은 책 귀속 + 목록이다.)*

> ⚠️ **원래 §전략 엔진 B(소셜) → "사람 밀도가 찬 뒤" 착수(백로그)였다.** 스토리는 **팔로워가 있어야
> 가치가 나오는 밀도 의존** 기능(팔로워 0 = 빈 스토리 = 0원어치)이라 밀도 선행이 원칙이었으나,
> **사용자 결정(2026-07-02)으로 지금 착수** — 원칙의 근거는 보존(반응이 없으면 이 원칙이 이유였음을 기억).

> ✅ **설계 완료 (2026-07-02)** — SNS 규칙("구현 전 설계 먼저")대로 공개범위·만료·신고·차단 경계를
> [sns-design.md §13](claude-docs/sns-design.md)에 못 박고 사용자 합의를 마쳤다. 노출 경계 요지:
> 팔로워 한정 단일 · 첨부는 본인 소유+PUBLIC 책만(비공개 간접 누출 차단) · 만료는 표시 필터(보존) ·
> 신고는 기존 Report 재사용.
>
> ✅ **v1 출하 (2026-07-02)** — 같은 날 TDD(Red→Green)로 백엔드(스키마 V56·V57 + API 6종 + 게이트
> + FK 정리)·프론트(shared 스토리 컴포넌트 → dashboard·profile 두 섬 삽입) 순차 PR 2개로 구현 완료.
> 상세는 changelog(2026-07-02) 참조. 남은 후속 백로그는 sns-design §13.9(전체공개 옵션·사진·보관함 등).

**1차 방향 확정 (2026-06-29, 사용자):**
- **문장 출처 = 책 연결 + 자유 텍스트 둘 다** — 작성 시 내 책장의 책을 *선택사항*으로 붙일 수 있다.
  붙이면 "《책제목》에서"로 표시 + '이 책 보기'·구매링크·책BTI와 엮여 **발견·제휴 축과 연결**. 안 붙이면 자유 문장.
- **콘텐츠 형식 = 텍스트 카드만 (이미지 업로드 없음)** — 배경색/책 표지 위에 문장 텍스트를 얹는 카드.
  사용자 이미지 업로드는 안 한다 — 현재 이미지 저장소(S3)·업로드·이미지 모더레이션 인프라가 없어
  깔면 비용·무게가 급증. 텍스트만이면 추가 인프라 ≈0. (사진 스토리는 후속 별도 판단.)

**기존 토대로 충분 (새 저장은 스토리 본체뿐 — N-037 정신):**
- **팔로워 노출** = `follow` 테이블 재사용. "팔로워에게 보이기"는 `story ⋈ follow`(viewer가 팔로우한
  사람의 미만료 스토리) 조인 — 4단계 팔로우 스코프 카운트·`followScopeReaders` 쿼리와 동형.
- **만료** = `BaseTimeEntity.createdAt` + `where created_at >= now()-24h` 표시 필터(만료 잡 불필요,
  물리 삭제는 후속 배치). 유저 TZ·Clock(N-010) 그대로.
- **차단·신고·레이트리밋** = `Block`/`Report`/`RateLimitService` 게이트 통과(차단 상대 미노출·스토리
  신고·작성 빈도 제한). 비공개 책 간접 누출 차단(§3.5) 동일 주의.
- **진입점** = 책방 `/u/{loginId}`(Vue 섬 `frontend/src/profile/`)·홈(`/`) 상단. 뷰어·스트립은 별도 섬이
  아니라 **`frontend/src/shared/story/` 공용 컴포넌트**로 dashboard·profile 두 섬에 삽입(설계 §13.7에서 정정 —
  스트립이 기존 섬 내부 상단에 들어가야 해서 별도 마운트 포인트가 오히려 복잡).

**새로 필요한 것 (최소, 상세는 sns-design §13.3~13.5):**
- **`story` + `story_view` 테이블 2개** — 번호는 머지 직전 확정(2026-07-02 현재 최신 V55 → V56·V57 예상).
  `story` = 작성자 FK·책 nullable FK·`text`(≤500자)·배경 팔레트 코드. `story_view` = 열람 멱등 기록
  (미열람 링 + "누가 봤나"). 탈퇴·책삭제·스토리삭제 시 자식 정리(FK — T-023·T-029 계열, 실 H2 테스트 필수).
- **`Story`/`StoryView` 엔티티 + 레포 + `StoryService` + `StoryApiController`** — 피드(`GET /api/stories/feed`)·
  책방(`GET /api/stories/of/{loginId}`)·작성(`POST /api/stories`)·본인 삭제·열람 기록·열람자 목록.
  `RateLimitAction.STORY_CREATE`(10/시간) + 활성 상한 20장.
- **프론트 = 인스타 스토리 UI** — 아바타 링(미열람 강조 — 서버 `story_view` 기준)·풀스크린 카드·진행바·
  자동 넘김·좌우 탭. 머지 전 실 브라우저 1회 게이트(프론트 검증 규칙 — 타이머·오버레이는 가짜 green 위험 클래스).

**~~열린 질문~~ → 전부 해소 (2026-07-02 사용자 확정, 기록은 sns-design §13.9):**
- 공개범위 = **팔로워 한정 단일**(visibility 컬럼 없이 시작, 전체공개는 additive 후속).
- 조회 표시(누가 봤나) = **v1 포함** — 미열람 링이 어차피 `story_view`를 요구, 열람자 목록은 그 위에 거의 공짜.
- 장수 = **여러 장**(작성자별 묶음 순차 재생, 활성 상한 20 + 레이트리밋).
- 만료 데이터 = **보존 + 표시 필터**(물리 삭제 배치는 후속) / 미열람 추적 = `story_view`(서버, 기기 무관).

### 📚🧬 책BTI — 독서 성향 분석 ("책장 기반 MBTI") (v1 ✅ 2026-06-07 · 공개 책 기반·항상 책방 공개로 단일화 ✅ 2026-06-08 · 분석 히스토리 3개+대표 고르기 ✅ 2026-06-08 · 결정적 책BTI 태그(8종족 닫힌 어휘·책방 노출·근거 드릴다운) ✅ 2026-06-10 (Phase 6b))

> 🏷️ **이름 = 책BTI**(책 + MBTI, 사용자 명명 2026-06-07). 사용자 노출 브랜드는 "책BTI", 코드/도메인 용어는 "독서 성향(reading personality)".

> ✅ **설계 확정(2026-06-07) — v1 TDD 착수 가능.** 정본 설계: [claude-docs/reading-personality-design.md](claude-docs/reading-personality-design.md).
> **확정 요지**: v1 = **본인용·비노출**(전체 책 기반, 나만 봄) · 장르/출간연도 **적재 추가**(알라딘 `categoryName`·`pubDate` — 현재 `Book` DB·`AladinBookSearchClient.parse()` 둘 다 안 받아 새로 깐다) · LLM = **Gemini Flash**(무료 검증, 포트 추상화로 교체 가능) · 결과 **전용 테이블 캐시**(Flyway, 번호 머지 직전) · 사실집계=코드/해석·서술=LLM 분리 · 폴백=사실만 표시. 공개·매칭은 후속(밀도 신호 뒤).
> **구현 단계**(정본 설계 §10): Phase1 장르 적재 → 2 독서프로필 집계(코드) → 3 LLM 포트+Gemini → 4 저장·캐시 → 5 본인 화면 노출 → (후속) 공개용+추천.
> **진행**: ✅ **Phase 1a 전방 적재**(검색→Book→DB로 장르·출간일 흐름: `parse()` 매핑 + `BookSearchResult`/`Book` 필드 + V18 + 검색폼 hidden + addFromSearch) — PR #205 머지·배포 완료. ✅ **Phase 1b 백필**(기존 책 ISBN으로 알라딘 ItemLookUp 채우기 — `lookupByIsbn` 포트 + `BookCatalogBackfillService`(멱등·null-isbn 제외·limit cap) + 관리자 버튼 `/admin/books/backfill-catalog`, 새 Flyway 없음) — PR #206 머지. ✅ **Phase 2 독서 프로필 집계**(코드·결정적): 순수 함수 `ReadingProfileAggregator`(권수·완독률·총시간/평균세션=정독↔다독·저자편향/다양성·장르편식/잡식·출간연대=신/구) + 얇은 `ReadingProfileService`(전체 책 기반·사용자 범위). 저장/LLM 없음(순수 read). null-state(저자·장르·출간일 없는 책)는 분포에서 제외(N-055). ✅ **Phase 3 LLM 포트 + Gemini 어댑터**: 포트 `ReadingPersonalityNarrator` + Gemini Flash 어댑터(키 헤더 주입·isEnabled 게이트·예외→폴백, 프롬프트 그라운딩·요청본문·2단 응답파싱 모두 정적 단위테스트) + 결합 레코드 `PersonalityNarration`/`ReadingPersonality`(narration null=폴백) + 오케스트레이션 `ReadingPersonalityService.analyze`(사실+서술 결합, 서술 실패 시 사실만). 저장 없음(Flyway 무변경). ✅ **Phase 4 저장·캐시·갱신**: 캐시 엔티티 `ReadingPersonalityCache`(user_id unique) + **Flyway V19** + 레포 + 입력 시그니처 `ProfileSignature`(구조+시간 hour 버킷 SHA-256, 초 thrash 회피). `analyzeCached(user, force)` = 콜드스타트(<5권) 보류 / 캐시 히트면 LLM 건너뜀 / force·시그니처 변동이면 재생성·upsert / LLM 실패면 사실만 폴백. 회원 탈퇴 정리에 캐시 삭제 추가(FK). ✅ **Phase 5 본인 화면 노출 → v1 완료**: 전용 페이지 `/personality`(대시보드 바로가기 타일) — `PersonalityController` + 표시 모델 `PersonalityView`(3상태: READY 서술 / COLD_START 책<5 안내 / FALLBACK LLM 실패) + 템플릿 `personality.html`(상태별 카드 + 책장 사실 요약 + 정확도 고지) + "다시 분석"(`POST /personality/refresh`, CSRF, force 재생성). 태그 v1 비노출. **🎉 책BTI v1 전 구간 출하**(집계→서술→캐시→화면). 남은 §8 잔여·후속(공개용·매칭)은 별도. ✅ **견고화 — 빈 화면·지연 방어(PR #235, [[N-060]])**: 실사용에서 "한 번씩 분석이 통째로 비고 새로고침하면 다시 보임"이 발생 — 캐시 미스(시그니처 변동) 시점의 라이브 Gemini 호출이 느리거나 빈 응답이면 그대로 빈 화면이 됐다. 네 겹 처방: ① **serve-stale-on-error** — 재생성 실패 시 사실만이 아니라 *직전 캐시(stale)* 를 우선 노출(빈 화면 방지, 캐시를 fresh/stale/absent 3상태로) ② LLM 호출을 트랜잭션 밖으로(`analyzeCached` propagation SUPPORTS — 네트워크 시간 동안 DB 커넥션 점유 회피) ③ `RestClient` connect/read 타임아웃(5s/20s, 무한 대기 차단) ④ Gemini 빈 출력 방어(`maxOutputTokens`=2048 + `thinkingConfig.thinkingBudget=0` — 2.5-flash thinking이 출력 예산을 삼켜 `parts[0].text`가 빈 문자열로 오는 것 차단). TDD Red→Green(stale 폴백 2 + 빈출력 방어 1) + 전체 그린. ✅ **서술 범위 — 책 내용만, 독서 습관 제외(PR #236)**: 실사용 피드백("결과물에 독서 습관은 빼고, 무슨 책을 읽느냐로 성격·가치관·취향만"). 원인은 `buildPrompt`가 `ReadingProfile` 전체를 직렬화해 독서시간·완독률·세션 같은 **습관 신호까지 [사실]로 주입** → 모델이 정독형/완독러를 씀. 처방: ① `bookFactsJson`로 **책 내용 신호만**(장르·저자·출간연대 분포·다양성·총권수) 선별 주입(습관 필드 제외) ② 프롬프트 지시를 "읽은 책이 드러내는 **성격·가치관·취향만**, 독서 습관(시간·완독률·정독/다독·권수)은 언급 금지"로 교체. **능력 제거(입력 차단) + 지시 병행**(지시만으론 새기 쉬움). TDD Red→Green(습관 수치 미노출 단언 + 책 사실 유지). DB·Flyway 무변경. ✅ **성향 입력을 완독 책만으로 + 책장 요약 정리(PR #238)**: 실사용 피드백("읽고싶음·읽는중 말고 완독한 책만으로, 책장 요약에서 보유/완독·총 독서 시간은 빼 달라"). 그동안 `profileOf`가 **전체 책**을 집계해 안 읽은 책의 저자·장르까지 LLM으로 갔다. 처방: ① `profileOf`가 **완독(FINISHED) 책만** 집계(읽고싶음/읽는중은 어떤 분포에도 안 샘) ② 콜드스타트를 `finishedBooks()` 기준으로(읽고싶음만 쌓으면 보류) ③ `bookFactsJson` 표본 키 `finishedBooks` ④ `ProfileSignature`에서 독서시간 제거(시간은 더 이상 성향 입력 아님 → 시간만 쌓여도 재분석되던 낭비 제거) ⑤ `personality.html` 책장 요약에서 보유/완독·총 독서 시간 행 삭제, 콜드스타트 문구 "완독한 책 N권". TDD Red→Green + 전체 그린. ✅ **책방 공개 노출(설계 Phase 6 전반부, PR #240)**: 사용자 요청("내 책BTI를 책방=공개 프로필에서 남들도 보게"). 프라이버시 충돌 — 기존 책BTI는 **전체 책(비공개 포함)** 기반이라 그대로 노출하면 비공개 책 취향이 샌다(설계 §7 "한 번 새면 회수 불가"). 사용자 결정 = **공개(PUBLIC)+완독 책만 재계산**(본인 `/personality`와 달라질 수 있음 — 의도된 경계, "공개를 더 켤수록 본인 것에 수렴", 통제권은 사용자) + **opt-in 토글**(기본 비노출). 5단계: ① `User.personalityPublic` 플래그(기본 false·V25, opt-in이라 기존 사용자 false 유지) ② `ReadingProfileService.publicProfileOf`(공개+완독 책 **+ 그 책 세션만** — `ReadingProfileAggregator`가 세션을 책과 대조 안 해 통째 합산하므로 비공개 책 독서시간 누출을 서비스가 차단) ③ 별도 테이블 `reading_personality_public`(V26)+엔티티/레포+`analyzePublicCached(user,force)` — 본인용과 캐시 규칙(콜드스타트·serve-stale·캐시 비손상)을 `CacheStore` 추상화로 공유해 저장소만 교체(불변식 한 곳, 드리프트 차단)+탈퇴 정리(FK) ④ 책방 노출 — `ProfileService`가 opt-in+캐시 있을 때만 `ProfileView.personality` 채움(**방문자 조회는 캐시 읽기 전용=LLM 미호출**, 비용 안전장치), `profile.html` 책BTI 카드("공개 책 기준" 명시+"MBTI처럼 가볍게" 고지) ⑤ `/personality` 노출 토글 + `POST /personality/visibility`(CSRF, 켤 때 공개 책BTI 즉시 생성=소유자 행동에서만 LLM, "다시 분석"도 공개 동기화). 별도 테이블 선택 이유: V19 user_id unique를 (user_id,scope) 복합으로 바꾸면 제약 드롭 SQL이 MySQL(DROP INDEX)/H2(DROP CONSTRAINT)에서 갈려 이식 위험 → CREATE만 하는 별도 테이블이 안전+기존 본인용 경로/테스트 무손상. TDD Red→Green 각 단계(User 3·publicProfileOf 2·공개 캐시 6·책방 노출 3·토글 3) + 전체 그린. 남은 후속: **사람 추천(매칭)** = Phase 6 후반부. ✅ **"다시 분석" 일일 횟수 제한(PR #241)**: `POST /personality/refresh`는 매번 `force=true`로 Gemini를 강제 호출 → 악의적 반복 클릭이 LLM 남용·서버 부담이 된다(사용자 우려). 처방: **일일 카운터를 `User`에 DB로** 두고(인메모리 X — ECS 인스턴스가 여럿이라도 한도가 일관되고 재기동에도 유지) **사용자 타임존 기준 자정 롤오버**로 리셋 — `User.tryConsumePersonalityRefresh(today)`(날짜 바뀌면 0 리셋 후 셈, 한도 `DAILY_PERSONALITY_REFRESH_LIMIT`=3 내면 +1·true / 초과면 상태 불변·false) + `remainingPersonalityRefreshes(today)`(부수효과 없는 읽기, 표시·버튼 비활성용), **V27** `users.personality_refresh_count`/`_date`(기존 사용자는 첫 클릭 시 채워져 자연히 적용), 컨트롤러가 소비 시도해 초과면 LLM 호출 없이 `redirect:/personality`+flash 안내(`Clock` 주입해 타임존으로 오늘 계산), `personality.html`에 남은 횟수·0이면 버튼 비활성. "오늘"을 도메인이 직접 계산 안 하고 인자로 받는 이유=타임존/시계 결정을 호출자에 둬 테스트에서 자정 경계를 고정. TDD Red→Green(도메인 3·컨트롤러 1) + 전체 그린. 🔁 **공개/비공개 분기 폐지 → 공개 단일·항상 노출(PR #241)**: 사용자 재결정으로 책BTI의 성격을 바꿨다 — "사용자끼리 즐기는 재미 요소이지 나만 보는 비공개 성향이 재미는 아니다 → 공개 책으로만 뽑고 무조건 책방 공개". 위 #240의 이원화(본인용 전체 책 + 공개용 공개 책 + opt-in 토글 + 별도 테이블 + `analyzePublicCached`/`CacheStore`)를 **일원화**: 성향을 **공개(PUBLIC)+완독 책만**으로 단일 생성(`analyze`·`analyzeCached`가 `publicProfileOf` 사용)해 **단일 캐시 `reading_personality`**에 저장하고 **항상** 책방에 노출(opt-in 게이트 제거 — 본인 `/personality`와 책방 `/u/{loginId}`가 같은 결과). 제거 대상은 **V28**(테이블 drop+컬럼 drop)로 정리: `reading_personality_public`·`PublicReadingPersonalityCache`(엔티티/레포)·`analyzePublicCached`·`CacheStore` 추상화(저장소 하나라 `findCached`/`upsertCache` 인라인)·`User.personalityPublic` 플래그·`POST /personality/visibility` 토글·`ProfileService` opt-in 게이트·탈퇴 정리의 공개 캐시 삭제. 콜드스타트(공개 완독 5권 미만)면 캐시가 없어 책방 카드가 숨고(방문자 LLM 미트리거 유지), `/personality`엔 "공개 책만으로 분석돼 항상 책방에 노출됨" 안내. **프라이버시는 오히려 강화**: 유일한 계산 입력이 공개 책뿐이라 §7 누출 차단이 분기 없이 자명해짐(#240의 별도-생성 근거는 보존하되 더 단순한 공개-입력-단일화가 같은 불변식 달성). 테스트 전부 공개 책 기준으로 갱신(픽스처 `makePublic`)·opt-in/토글 테스트 삭제·`ProfileControllerTest`를 노출/숨김 2케이스로·`ReadingPersonalityServicePublicCacheTest` 삭제 + 전체 그린(V28 H2 적용 검증). 설계 §10 Phase 6a에 반전 기록. ✅ **분석 히스토리(최대 3개) + 대표 고르기(PR #246)**: 실사용 피드백("책 추가 후 다시 분석했는데 성향이 안 바뀐 것 같은데 비교가 안 된다. 하루 3번뿐이라 또 못 돌린다 → 과거 문장이 안 사라지고 최대 3개까지 남게, 그중 하나 골라 비교"). 위에서 "단일 캐시 `reading_personality`=1행/유저"라 재분석이 덮어써 과거 서술이 소실된 게 근본 원인 → **모델을 최대 3행/유저 + `selected`(대표) 플래그로 확장**(유저당 selected는 0~1, 대표=공개 책방 노출 + 교체에서 보호). 사용자 결정 3: ①고른 게 **책방 대표+삭제 보호** ②"다시 분석"은 **후보로만 추가**(자동 대표 X, 기존 선택 유지) ③새 행은 **"다시 분석" 버튼에서만** 쌓임(단순 GET 방문은 교체 안 함 — 예상치 못한 교체 방지). 서비스를 3진입점으로 분리: `currentPersonality`(GET — 대표 읽기, 히스토리 비고 책 충분하면 첫 1개 부트스트랩=자동 대표, **책장이 바뀌어도 자동 재생성하지 않음**) · `reanalyze`("다시 분석" — 새 분석을 후보로 추가, 4행이면 *대표 뺀 가장 오래된 후보* 1개 교체, LLM 실패 시 stale 대표 유지·새 행 안 만듦) · `select`(대표 변경, LLM 無, **본인 행만=IDOR 가드**). `history`는 최신순 + 대표 + **stale**(이 분석 이후 책장 바뀜) 표시(사용자의 "안 바뀐 건가?" 혼란을 분석 시각·stale로 해소). 교체 정렬은 `generated_at` + **id tiebreak**(같은 초도 결정적, #245 신고함과 동일 교훈). 공개 책방(`ProfileService`)은 `findByUserAndSelectedTrue`로 대표만 노출. **V29**: user_id unique 드롭이 MySQL(DROP INDEX)/H2(DROP CONSTRAINT)로 갈리는 함정(V26 주석) 회피 위해 **CREATE+INSERT…SELECT+DROP+RENAME**로 테이블 재생성(기존 1행→`selected=true` 이관, id 재부여=자식 FK 없어 안전). `personality.html`은 3카드(대표 뱃지·분석 시각·책장변경 안내·"이걸로 대표 선택" 폼). TDD Red→Green: 도메인 히스토리 10(교체·보호·선택·재생성안함·serve-stale·IDOR·부트스트랩·stale)·GET경로 6·뷰 1·컨트롤러 3 + 전체 그린(FlywayMigrationTest로 V29 H2 검증). ✅ **한도 소진·분석 시각 UX 3종 수정(PR #247)**: 실사용 혼란("다시 분석해도 성향 안 바뀌고 후보도 안 뜸")을 타임스탬프 대조로 진단 — **버그 아님**(오늘 3회를 #246 배포 전 구버전에 소진해 quota 0으로 처음 만난 것, 자정 KST 리셋 전 막힘). 0/3이 설명 안 되던 UX를 셋 고침: ① 비활성 버튼 시각화(`:disabled` 회색·금지 커서) ② 한도 안내를 `refreshRemaining==0` 상시 표시(버튼 disabled라 POST 못 가 플래시가 영영 안 뜨던 catch-22 해소) ③ 분석 시각 사용자 타임존 표기(`PersonalityView.formatTime` — 그동안 서버 UTC라 9시간 어긋남). 템플릿 개발 주석을 파서 수준 `<!--/* */-->`로 바꿔 출력 버퍼 비대화·주석 유출 차단(#245와 같은 'response committed' CSRF 함정 회피). TDD Red→Green(타임존 변환)+전체 그린. ✅ **책BTI 태그(결정적·LLM 무관) — 8종족 닫힌 어휘 → 책방 헤더 노출 → 근거 책 드릴다운 (Phase 6b, PR #281·#282·#283)**: §전략 엔진 B의 **사람 추천(매칭)** 을 향한 토대로 책BTI에 결정적 태그를 얹었다. 자유서술 태그는 "같은 취향인데 다른 라벨"로 매칭이 깨지므로(open-vocabulary drift) **닫힌 어휘**로 고정 — 알라딘 대분류(이미 유한 어휘)를 8 독서 종족(이야기·지식·탐구·실용·감성·수양·학습·동심)으로 큐레이션한 `ReadingTribe` + 3축 결정적 규칙 `ReadingTagger`(①장르 정체성 1·2종족 ②폭=외길/균형/잡식 ③저자 충성=한우물형). 온더플라이 계산이라 Flyway·저장 0, LLM 실패와 무관하게 사실에서 직접 뜨고 규칙이 바뀌면 즉시 재태깅(#281). 본인 `/personality` 외에 **남에게 보이는 책방 `/u/{loginId}` 헤더**로 노출하되 입력은 공개+완독 책만(비공개 취향 누출 차단, #282). 칩을 클릭하면 그 태그를 만든 공개+완독 **근거 책**을 htmx 인라인으로 펼친다(장르 종족·한우물형만 클릭, 폭 태그는 부분집합이 없어 비클릭 — 순수 `PersonalityTagAttribution` + 가드 체인 `resolveVisibleTarget` 공유로 PRIVATE 누출 차단·차단·404 일관, #283). 닫힌 어휘 = 매칭 키라 위 「3기능」 #2 **사람 추천**의 비교 입력으로 직결되고, 정본 설계 §8 "태그 체계" 열린 질문을 이 규칙으로 해소.

> 🎯 **이게 진짜로 뭔가 (2026-06-06 명확화)**: 이 기능의 본심은 "분석"이 아니라 **§전략의 엔진 B(사람 잇는 재미)**다 —
> 사람과 사람을 이어 대화하고 생각을 공유하는 재미를 BookTimer에 넣고 싶었던 것. 그래서 **타깃은 입문자가 아니라
> 헤비 리더**(공유할 생각이 있는 사람)이고, **성향은 정확도가 아니라 "소셜 연료"(정체성 배지·대화 물꼬·매칭 씨앗)로
> 평가**한다(Strava의 배지·세그먼트처럼). 매리트는 분명히 있으나 **밀도(사람 수)가 차야 0원어치를 벗어나고**, thesis의
> 핵심 audience(입문자)와는 다른 사람을 위한 레이어다 → §전략 순서대로 **솔로 retention·밀도 확보 뒤**에 착수가 원칙이었다. (§전략 우선순위 참고.)
>
> 🔀 **순서 화해 (2026-06-07 결정)**: 엔진 B를 지금 시작하되 **밀도 독립 부분부터** 들어간다 — **성향 분석 v1(본인용)은 내 책장 하나로 가치가 나오고(밀도 0 의존) 캡처·공유되는 획득 후크**라 "밀도 먼저" 원칙과 충돌하지 않는다(오히려 밀도를 키운다). 반면 **밀도 의존(②사람 추천)·고위험(③채팅·결제)은 §전략대로 뒤로** 둔다. 즉 *진입은 성향 분석, 밀도 필요한 레이어는 신호 뒤*. (이 결정으로 위 "밀도 확보 뒤" 원칙과 실제 착수가 화해됨 — 근거 보존.)

**컨셉**: 사용자의 **책장(보유/읽는중/완독 책 + 책별 누적 시간)** 을 입력으로, **AI를 돌리든 알고리즘을
짜든** 일정 과정을 거쳐 그 사람의 **독서 성향(=일종의 "독서 MBTI")** 을 도출한다. 이 성향 프로필을
토대로 두 갈래 컨텐츠를 얹는다 — ① **개인화 책 추천**, ② **사용자끼리 상호작용**(성향 비교·매칭·궁합 등).

**왜 BookTimer에 자연스러운가**: 핵심 입력(누가 어떤 책을 가졌나/얼마나 읽었나)이 이미 `book`·
`reading_session`에 다 있다(N-037 — SNS와 같은 맥락, 새 독서데이터 저장이 아니라 **기존 데이터 해석**).
SNS 토대(팔로우·공개범위·프로필)가 깔려 있어 ②의 사용자 상호작용도 얹을 자리가 있다.

**구상 갈래 (택1 아님 — 단계적 가능)**:
- **A. 규칙/알고리즘 기반(가벼움)** — 장르·카테고리·저자·완독률·독서 시간 분포 등에서 축(axis)을 뽑아
  점수화 → 성향 라벨 매핑. 외부 비용 0, 결정적·설명가능. **MVP·검증용으로 적합.**
- **B. AI(LLM) 기반(풍부함)** — 책 목록·메타를 LLM에 넣어 성향 서술/요약을 생성. 표현은 풍부하나
  **비용·지연·프롬프트 일관성·환각** 관리 필요. A로 축을 정하고 B는 "표현 레이어"로 얹는 하이브리드도 후보.

**다운스트림 컨텐츠**:
- **책 추천** — 성향 + 팔로우 스코프 인기(4단계 집계) + 알라딘 검색을 엮어 "당신 성향엔 이 책". 제휴(3%)와 연계.
- **사용자 상호작용** — 성향 라벨로 프로필 배지, "비슷한 성향 독자" 추천, 성향 궁합/비교, 성향별 모임 등.

**미확정·선결(나중에 정할 것)**:
- 성향 축/라벨 체계 설계(몇 축? 라벨 네이밍? 책 부족 시 처리 — 콜드스타트).
- 입력 프라이버시 — **PRIVATE 책을 분석에 쓸지**(타인에게 성향이 노출되면 비공개 책이 간접 누출될 수 있음 → §3.5 가시성 경계와 동일 주의). 분석은 본인 것만/공개 결과는 PUBLIC 책 기반 등.
- 책 동일성 키(ISBN 정규화)·장르 분류 출처(알라딘 카테고리?).
- A vs B vs 하이브리드 / 비용·캐싱 / 결과 갱신 주기(책 추가 시 재계산?).
- 결과 저장 모델(파생 캐시 vs 매번 계산), Flyway 신규 여부.
- 재미/오락 정확도 기대치 — "MBTI처럼 가볍게 즐기는 것" 포지셔닝(과신 금지 고지).

**선후**: 데이터 토대(책장·SNS)는 이미 있음 → **프론트와 독립으로 데이터/알고리즘 설계 선행 가능**.
구현 착수 전 **설계 문서 합의 필수**(SNS와 동일 원칙 — 프라이버시·노출 경계가 핵심).

### 관리자(개발자) 대시보드 — 운영 데이터 확인 (완료 ✅ 2026-06-05, 4단계 전부)

**왜**: 사용자/타이머/세션/책 등 운영 데이터를 확인하려고 **매번 RDS에 직접 접속하는 게 번거롭다**.
가입자 수·활성 사용자·총 독서 시간 같은 통계나 개별 데이터를 **웹에서 바로 볼 수 있는 관리 화면**이 필요하다.

> ⚠️ **접근 제어가 1순위 — 개발자(ADMIN)만 접근 가능해야 한다.** 운영 데이터(이메일·독서 기록 등 개인정보)가
> 걸려 있어, 일반 사용자에게 새면 개인정보 유출이다. 인가 경계를 먼저 못 박고 화면을 짠다.

- [x] **접근 제어 (선결) 완료 ✅ 2026-06-05 (PR #144)** — `/admin/**`를 `hasRole("ADMIN")`로 보호(default-deny 위 역할 매처).
  `AdminController` `GET /admin` + 최소 랜딩(`admin.html`). 인가 경계 TDD: 미인증→`/login` 리다이렉트 / USER→403 / ADMIN→200.
  - **ADMIN 승격 = ENV 부트스트랩 시드 채택**(사용자 결정 2026-06-05) — 운영 DB 수동 update 대신 재현·테스트 가능한 시드.
    `BOOKTIMER_ADMIN_EMAILS`(쉼표 구분, relaxed binding→`booktimer.admin.emails`)의 이메일을 기동 시 `AdminAccountSeeder`(ApplicationRunner)가
    `AdminAccountService.seedAdmins`로 승격. 승격은 `User.promoteToAdmin()`(멱등, USER→ADMIN만). 미존재·공백·이미 ADMIN은 조용히 무시.
    가입은 `Role.USER` 고정 유지(권한 상승 벡터 차단), ADMIN은 이 시드로만. **이메일은 repo에 커밋 안 함**(공개 저장소 — ENV로만 주입).
  - ✅ **운영 적용 완료(2026-06-05)**: prod ECS 태스크에 ADMIN 시드 ENV 설정 + 배포 완료. **단 ENV 이름은 login_id 인증 컷오버(PR #149) 이후 `BOOKTIMER_ADMIN_EMAILS`→`BOOKTIMER_ADMIN_LOGIN_IDS`로 바뀌었다**(아래 §login_id) — 값은 task-definition.json 미커밋(콘솔/시크릿 주입, 노출 방지).
- [x] **통계 요약 완료 ✅ 2026-06-05 (PR #152)** — 가입자 수(USER만·ADMIN 제외), 온보딩 완료자, 최근 7일 활성 사용자, 총 책/세션 수, 총·가입자당 평균 독서 시간 집계 카드.
  - 기존 테이블 **집계 쿼리**(N-037: 새 저장 아님, 읽기 전용) → DB 안 건드림(Flyway 없음). `AdminStatsService`/`AdminStats`(record), `countByRole`·`countActiveUsersSince`(Clock 주입 → 결정적)·`sumAllDurationSeconds`. 무거운 집계는 캐시 후보(현 규모 불필요).
- [x] **데이터 조회 완료 ✅ 2026-06-05 (PR #154)** — 사용자 목록(`/admin/users`, 페이징·login_id/nickname 검색·가입일 내림차순) + 드릴다운(`/admin/users/{loginId}`, 타이머 설정·최근 10세션·책장 상태별 요약).
  - 📐 설계 메모: [claude-docs/admin-data-lookup-design.md](claude-docs/admin-data-lookup-design.md) — 라우팅·email 마스킹·드릴다운 범위·보안 체크리스트.
  - 읽기 전용(GET만 → CSRF 표면 없음). 수정·삭제 운영 액션·감사로그는 보류(별도 단계). **Flyway 없음**.
  - 개인정보 최소 노출: **비밀번호 해시 비노출**(DTO에서 제외), **email 마스킹 기본+클릭 시 전체**(`EmailMask`, `<details>` 토글), email로는 검색 안 함. `AdminUserService`/`AdminUserRow`/`AdminUserDetail`(record), 세션→책 제목은 `left join fetch`로 트랜잭션 안에서 해소. TDD(마스킹 규칙·목록 검색/페이징·드릴다운 조립/타이머 폴백/최근 N 한도·없는 id 404·USER 403 회귀).
- **메모**: SSR(Thymeleaf)로 가볍게 시작 가능(N-017 — 내부 도구라 SEO·인터랙션 요구 없음).
  외부 노출 0이 이상적 → 추후 IP 제한/별도 경로 등 추가 방어 검토. 운영 액션 추가 시 **감사 로그** 동반.

### 🪪 로그인 아이디(login_id) 도입 — 식별/인증 분리 (완료 ✅ 2026-06-05, PR 1~5 + wipe + 운영 적용)

> 📐 설계 메모: [claude-docs/login-id-design.md](claude-docs/login-id-design.md) — 식별 모델·마이그레이션·전환·PR 단계 합의용. **코드 없음.**

**왜**: 현재 로그인·식별 핸들이 **email**인데 운영자 이메일이 홈페이지에 공개돼 있다. *이메일 공개 자체가 admin 시드를 뚫진 않지만*(시드는 ENV 조작+계정 로그인 둘 다 필요), email이 **로그인 식별자**라 공개 = 로그인 표적 노출 → 표적형 무차별 대입에 약하다. 이메일을 로그인·식별에서 빼고 **비공개 `login_id`(아이디)**로 로그인·식별한다.

**합의된 방향** (사용자 결정 2026-06-05, 🔁 모델 전환 포함):
- **🔁 login_id = 공개 @핸들 (인스타/X 모델)** — 검색·프로필 URL·로그인 식별자가 모두 login_id. ~~**불변**(한번 정하면 영원히).~~ → **평생 1회 변경 가능**으로 완화(2026-08-15, 아래 「아이디 평생 1회 변경」 절). email은 계속 비공개(연락/복구). 원목표(공개 이메일을 로그인 식별자에서 분리)는 그대로 달성 — 공개 핸들만 nickname→login_id로 옮김.
- **nickname = 표시 이름** — 중복 허용·자유 변경, 더 이상 핸들/유니크 아님(uk_users_nickname 제거).
- **principal = login_id 전면 전환(B안)** — email은 연락/복구/OAuth 연결용 **속성**으로 강등.
- **기존 사용자 전부 wipe**(출시 전·친구뿐, 스냅샷 생략) → 백필·dual-lookup·임시값·전환 게이트 **전부 제거**(그린필드).
- **login_id는 온보딩 한 곳에서 전원 직접 선택**(local+OAuth, 자동생성 없음). 인증은 `findByLoginId`만(email 폴백 없음).
- **형식**: `a-z0-9_`, 3~20자, 소문자 정규화, 예약어 차단(표준).
- **Admin 시드**: `BOOKTIMER_ADMIN_EMAILS` → `BOOKTIMER_ADMIN_LOGIN_IDS`.
- **PR 단계**: ①스키마+도메인 **✅ #146** → ②불변+닉네임 중복허용+온보딩 캡처 **✅ #147** → ③공개 핸들 컷오버(검색·프로필·관계 핸들 nickname→login_id) **✅ #148** → ④인증 컷오버(wipe 선행·14곳·시드) **✅ #149** → ⑤login_id 무결성 강화(조건부 CHECK) **✅ #156**.
- **wipe 실행 ✅ 2026-06-05** — prod RDS 그린필드 리셋 완료(users 11·session 40·book 6·timer 11·follow 5·SPRING_SESSION 161 → 0). 절차·함정은 사설 런북 `private-docs/rds-access-runbook.md`(git 미추적).
  - ① 한 일: `V13__user_login_id.sql`(`login_id varchar(50)` nullable + `uk_users_login_id`), `User.loginId` 필드 + `assignLoginId`·`getLoginId`. 동작 변화 없음. TDD + FlywayMigrationTest + 전체 그린.
  - ② 한 일: `assignLoginId` **불변 가드**(재설정 시 ISE) + `normalizeLoginId` 정적 추출. **nickname 유니크 제거** — V14(`uk_users_nickname` drop) + register/onboarding/settings/oauth-provisioning 중복검사 제거 + `NicknameAllocator`·`NicknameAlreadyExistsException`·`existsByNickname` 삭제. **온보딩 login_id 캡처** — `OnboardingForm.loginId`(+@Pattern), `OnboardingService.complete`가 `existsByLoginId` 사전확인+`assignLoginId`로 불변 확정, `LoginIdAlreadyExistsException`, `onboarding.html` 아이디 필드(불변 안내)+nickname 라벨 정정. 로그인·검색은 아직 email/nickname. TDD(불변·중복허용·온보딩 캡처/유니크/예약어 Red→Green) + 전체 그린.
  - ③ 한 일: **검색·프로필·관계 식별을 nickname→login_id로 컷오버.** `UserRepository`: `findByLoginId`·`findTop20ByLoginIdContainingIgnoreCaseOrderByLoginIdAsc` 추가, `findByNickname`·`findTop20ByNickname...` 제거. 검색(`UserSearchService`)이 login_id 부분일치, `UserSearchResult`·`ProfileView`에 `loginId` 추가(닉네임은 표시용 유지 — 핸들/표시 분리). 프로필 `/u/{loginId}`(`ProfileController`·`ProfileService.findByLoginId`). 팔로우/차단/신고 컨트롤러 대상 식별 `@RequestParam loginId`+`findByLoginId`(닉네임 중복 시 오식별 정합성 버그 동시 해소). self-프로필 링크용 `loginId` 모델 속성(Dashboard/FollowList/Block). 템플릿 6종 `/u/{loginId}`+`name="loginId"`+`@핸들` 표시. 인증은 아직 email(PR-4). TDD(검색=login_id·닉네임중복→login_id 정확식별 Red→Green) + 전체 그린.
  - ④ 한 일: **인증 식별자 email→login_id 전면 컷오버.** `loadUserByUsername`=`findByLoginId`만(email 폴백 없음 → 이메일 로그인 차단), principal=login_id. **로컬 가입에서 login_id 캡처**(`SignupForm.loginId`·`register` 7-arg) — 로그인이 login_id 기준이라 가입 시 있어야 함(온보딩 캡처는 OAuth 전용으로 이동, `OnboardingService.complete`가 `loginId==null`일 때만 확정). **OAuth**: `BookTimerOidcUser`로 principal=login_id(온보딩 전 첫 세션만 email). **`CurrentUserService` 공유 리졸버**(login_id 우선 → OAuth 첫 세션만 email 브리지)로 13개 컨트롤러 통일. `SettingsController` 4곳은 principal→User→`getEmail()`로 서비스(findByEmail) 시그니처 보존. **admin 시드 `BOOKTIMER_ADMIN_EMAILS`→`BOOKTIMER_ADMIN_LOGIN_IDS`**(findByLoginId). 로그인폼 라벨 이메일→아이디. **wipe 선행 완료.** Flyway 없음(스키마 무변경). TDD(login_id 로그인·이메일 차단·리졸버·login_id principal 컨트롤러 경로 Red→Green) + 전체 그린. **✅ 운영 적용 완료(2026-06-05): prod 배포 + ENV `BOOKTIMER_ADMIN_LOGIN_IDS` 교체 완료(admin 시드 정상 동작 확인).**
  - ⑤ 한 일: **login_id 무결성을 DB 제약으로 강화.** 단순 NOT NULL은 불가 — OAuth 사용자는 프로비저닝(INSERT) 시점엔 login_id가 없고 온보딩에서 정하므로 그 창에선 null이 정상. 그래서 **조건부 불변식 `onboarded ⟹ login_id IS NOT NULL`을 CHECK로** 좁힘(`V15__user_login_id_when_onboarded_check.sql`, `ck_users_login_id_when_onboarded`). 메인 스위트는 Hibernate 생성이라 무영향(V14와 동일) — `FlywayMigrationTest`가 Flyway 스키마에 적용해 검증(온보딩+login_id없음 거부·온보딩전 null 허용·정상 허용). 엔티티 Javadoc을 조건부 CHECK 현실로 정정. **email 로그인 잔재 없음 재확인**(`loadUserByUsername`=findByLoginId만, 남은 findByEmail은 설정 조회·OAuth 첫 세션 브리지 등 정당). TDD(Red→Green) + 전체 그린. (PR #156)

### 🪪 아이디 평생 1회 변경 — 옛 핸들은 영구 잠금 (완료 ✅ 2026-08-15, 웹 설정 화면 + 미니앱 설정 화면)

**왜**: 위 도입 때 login_id를 **완전 불변**으로 못 박았는데, 실제로는 오타·개명·유치한 첫 아이디를 안고 갈 수밖에 없는 마찰이 남았다. 그렇다고 자유 변경을 열면 공개 @핸들이 식별자 구실을 못 한다(팔로우·프로필 URL이 매번 흔들리고, 핸들 세탁으로 평판을 리셋할 수 있다). **평생 1회**가 그 사이의 답이다.

- **옛 핸들은 `previous_login_id`로 옮겨 UNIQUE로 영구 잠금** — 남이 그 아이디로 갈아타는 사칭을 막는다. **컬럼 하나가 잠금과 소진 판정을 겸한다**(값이 있으면 이미 썼다는 뜻) → 카운터·이력 테이블·만료 스케줄러 전부 불필요.
- **되돌리기 특례 없음** — 자기 옛 아이디로 돌아가는 것도 소진 가드에 먼저 걸린다. 열어주면 A↔B 왕복으로 1회 제한이 무의미해진다.
- **세션은 안 끊는다** — `CurrentUserService.resolve`에 3단째 폴백(`findByPreviousLoginId`)을 달아 **전 기기의 열린 세션**이 그대로 산다. 현재 요청의 SecurityContext만 갈아끼우는 방법은 더 비싸면서 다른 기기 세션을 못 살린다. 폴백은 *해석*에만 붙고 *로그인*에는 안 붙어, 옛 아이디로 새로 로그인하는 것은 자동으로 불가(크리덴셜은 즉시 새 아이디로 넘어간다).
- **가드 순서 = 메시지 정확성**: 소진(ISE) → 형식·예약어(IAE) → 현재와 동일(IAE) → 두 컬럼 중복(`LoginIdAlreadyExistsException`) → 교체. 특히 "현재와 동일"이 중복 검사보다 앞서야 본인에게 "이미 사용 중"이라는 오해성 안내가 안 나간다. 중복은 현행·옛 핸들 **양쪽**을 보되 어느 쪽인지는 사용자에게 구분해 알리지 않는다(부수 정보 누출).
- **한 일**: `V69__user_previous_login_id.sql`(컬럼 + `uk_users_previous_login_id`), `User.changeLoginId`(`assignLoginId`의 once-set 규약은 무변경 — 가입·온보딩 3곳이 의존), `AccountService.changeLoginId`, 리포지토리 2메서드, `CurrentUserService` 3단 폴백, `POST /settings/login-id` + 설정 화면 「아이디 변경」 카드(새 아이디 + 되돌릴 수 없음 확인 체크박스, 소진 후엔 정적 표기). TDD RED→GREEN 5단계. UNIQUE·V15 CHECK 단언은 실제 DDL이 도는 `FlywayMigrationTest`에 뒀다(메인 스위트는 Hibernate 생성이라 그 제약이 없어 슬라이스에선 공허해진다).
- ✅ **미니앱 변경 UI (2026-08-15, 같은 날 후속)** — 예고대로 서비스·엔티티·마이그레이션은 한 줄도 안 건드리고 **API 컨트롤러 메서드 1개 + 화면**만 얹었다. `POST /api/miniapp/handle/change`는 소진 409·형식/동일 400을 **메서드 안 try/catch**로 잡고 중복 409만 클래스 핸들러에 떨어뜨린다(클래스 핸들러가 이미 *생성* 의미로 점유돼 있어 그대로 두면 변경 실패 문구가 뭉개진다). 소진 여부의 출처는 `GET /api/dashboard`의 신규 `previousLoginId` 한 필드 — 전용 조회 API를 새로 파지 않았고, **본인 응답에만** 싣는다(프로필·검색에 넣으면 "저 사람이 아이디를 바꿨구나"가 샌다). 미니앱 설정 @아이디 섹션은 핸들 없음/미소진/소진 3분기가 되고, 기존 `HandleSheet`에 `change` 프롭 하나로 바꾸기 변형을 붙였다(구조가 같아 별도 컴포넌트는 중복 60줄). 웹의 `confirmOnce` 체크박스는 **안 옮겼다** — 그건 웹 컨트롤러 로컬의 폼 검증이지 서비스 계약이 아니고, API 파라미터로 만들면 클라가 항상 true로 보내는 죽은 값이 된다(서버 불변식은 소진 가드가 이미 지킨다). 확인은 「진입 버튼 → 경고 시트 + 새 아이디 타이핑」 2단으로 둔다. 곁들여 **이번 변경으로 거짓이 된 문구를 전수 교정**했다(생성 시트·설정 안내·서버 생성 409·목 픽스처).
- ⏸ **탈퇴 계정의 옛 핸들 재사용**은 별개 정책 판단으로 남긴다 — 탈퇴는 users 행을 물리 삭제하므로 잠금도 함께 사라진다(기존 동작 그대로). 막으려면 tombstone 테이블이 필요해 현시점 YAGNI.

### 프론트엔드 전환 (SSR → SPA) · 앱 프론트 — 선후 의존 정리
- 현재 Thymeleaf SSR. API 계약 안정성 + 인터랙션 요구가 커지면 전환 (N-017)

> 💡 **"SNS가 어느 정도 완성돼야 프론트 교체·앱을 할 수 있나?" → 아니다. 순서가 거꾸로다.**
> SNS는 프론트 교체의 *선행조건*이 아니라, 인터랙션 요구를 키워 전환을 **정당화하는 사유**일 뿐이다.
> 무엇이 무엇을 진짜로 막는지로 보면:
>
> | 작업 | 진짜 선행조건 | SNS에 의존? |
> |---|---|---|
> | **앱 프론트(모바일)** | **안정적인 JSON API** | ❌ (API에 의존) |
> | **프론트 교체(SSR→SPA)** | 인터랙션 요구↑ / API 계약 안정(N-017) | ❌ (SNS가 *촉발*은 함) |
> | **SNS** | 데이터 설계(관계+공개범위) 합의 | — |
>
> - **앱 프론트는 SNS가 아니라 API에 막혀 있다.** 지금은 서버가 HTML을 그려 내려주는 SSR이라, 모바일 앱이
>   먹을 **JSON API가 없다.** 앱의 하드 선행조건은 "SNS 완성"이 아니라 "API 레이어 존재" — SNS가 0%여도
>   API만 있으면 앱은 가능하고, SNS가 100%여도 API가 없으면 앱은 불가능.
> - **프론트 교체의 트리거는 SNS가 아니라 인터랙션 요구(N-017).** SNS(피드·팔로우)는 그 요구를 *키우는*
>   대표 기능 → 선행조건이 아니라 정당화 사유.
> - **인터랙션 동인은 이제 SNS만이 아니다 — 「독서 정원」의 커스텀 꾸미기가 구체 동인으로 합류**(§기능 로드맵 「독서 정원」, 결정 2026-06-10 → 갱신 2026-06-14). 원래 드래그 배치의 레이아웃 영속화=JSON API 요구가 *앱 프론트와 같은 선행조건*을 건드린다 봐 리치 버전을 전환 스코프에 묶었으나, **재검토 끝에 배치는 경량 SSR로 선출하**(PR #345): 작은 저장 엔드포인트 + (axis,code,cell) 좌표 스키마면 충분하고 **그 API·스키마는 전환 시 재활용**(재작업은 캔버스 UI뿐 = "절반만 두 번"). 드래그 고도화·실시간성 등 **풀 리치 UI는 여전히 전환 스코프에 남아** 정원을 전환 정당화 동인으로 둔다.
> - **실질 의사결정 = "SNS를 두 번 짤 거냐":** SSR로 SNS를 다 짠 뒤 SPA로 교체하면 UI를 **두 번** 만든다.
>   API/SPA부터 깔면 SNS UI는 **한 번**. 갈림길은 **SNS 확신도** —
>   - SNS가 아직 가설(검증 전) → **SSR로 싸게 먼저 검증**(책 인기 카운트 같은 가벼운 집계 선출시가 후보), UI 재작업 감수.
>   - SNS·앱이 확정 방향 → **API부터 추출**해 SNS는 새 스택 위에 한 번만.
> - **단, SNS 데이터 설계(관계·공개범위·IDOR·Flyway 스키마)는 프론트와 완전히 독립** — 되돌리기 가장 어렵고
>   가장 프론트-무관한 부분이라, 프론트 결정과 무관하게 **지금 먼저 분리해서 진행 가능**(§SNS "구현 전 설계 먼저").

> 🧪 **테스트 가능성 축 — 트리거는 "프레임워크"가 아니라 "빌드+모듈" (결정 2026-06-15, 사용자 합의).** 정원이 Phaser 게임으로 커지며 로드순서·반응성 런타임 버그(#358 reactive proxy·#364 defer/TDZ)가 헤드리스/mock으론 안 잡히고 실 브라우저로만 잡히는 일이 반복됐다. 그러나 **풀 SPA 전환은 여전히 보류**(위 트리거 0·SEO=제휴매출). 테스트 가능성은 컴포넌트 프레임워크가 아니라 **빌드 스텝+모듈 경계**에서 나오므로(learning-notes N-084·N-017 보강), 대응은 점증한다:
> - ~~**현행 유지(지금)**~~ → **빌드+모듈화 A→B 점진 착수 (2026-06-18, 트리거 발동)** — 트리거(헤드리스-블라인드 정원 런타임 버그)가 발동돼 정원부터 점진 착수. **1차 A 출하 완료**: `.preview` 표류 하니스를 `frontend/test/garden-pure.test.mjs`(Vitest, 398단언)로 커밋·CI 그물로 승격, npm 토대(`frontend/package.json`) 마련(브라우저 런타임·서버 0 변경). **2차 B 출하 완료**: `garden.html` 540줄 인라인 스크립트를 Vite+TypeScript 4 모듈(`pure.ts`·`scene.ts`·`component.ts`·`main.ts`)로 추출 + Phaser npm import — CDN defer가 T-054의 TDZ 원인이었으므로 번들 정적 import로 구조 소멸. `ensureGardenScene()` 함수래핑 패턴 삭제 → `class GardenScene extends Phaser.Scene` 직접 선언. SSR·보기모드·저장계약·Docker·build.gradle 불변. 번들(`static/garden/garden.js`) 커밋·CI stale 게이트. (PR #391, N-097·N-098·N-099, T-062·T-063)
> - **섬 아키텍처 현황**: 정원 편집 클라가 Vite 번들로 모듈화됨(Thymeleaf 페이지층 유지). N-082 Alpine reactive Proxy 격리는 번들 클로저 변수로 계속 유지. 다음 수 있다면 컴포넌트 프레임워크(Vue/Svelte) 스왑 — 하지만 현 구조로 단위/통합 테스트가 이미 열렸으므로 즉시 필요성 낮음.
> - **E2E 표적 도입 (2026-06-26)**: 마을 SPA 분리(S4)·정원 게임화로 N-084 트리거 1·2 충족 → **로컬 수동 Playwright 2개**(`frontend/e2e/`: ① 로그인 성공/실패 ② 정원 진입→꾸미기→저장 200·콘솔0). webServer 없이 떠 있는 `bootRun`에 붙고, vitest는 `test/`만 잡게 `include` 한정해 e2e/와 분리. **전면 아님**(커밋 게이트 미포함 — 느린 E2E가 `gradlew test`를 안 막게, CI는 vitest·JUnit만). 도입 즉시 **content-hash 정적자산 인증 누수** 버그 검출·수정(`PwaStaticAccessTest` 회귀가드 추가, T-101·N-126). 판단 근거는 learning-notes **N-084 갱신된 입장**.
> - **3차 → 착수 중 (2026-06-18, 사용자 결정)**: 정원(마을) 섬을 **Vue SPA + /api/garden JSON API**로 전환. 개발 코드 관리·개발 루프·기술 자유 목적의 아키텍처 투자(사용자 대면 가치 0, 점진 전환). 병행 컷오버 5단계(S1~S5): **S1 완료(PR #402)** — 백엔드 `GET /api/garden`·`POST /api/garden/layout` 신설 + DTO 평탄화. **S2 완료(PR #403)** — `GardenGame.vue` 신설(N-082 markRaw), `owned`·`characters` API 추가, Alpine 도감 탭 공존, Vue 마운트 포인트 전환. **S3 완료(PR #405)** — `GardenDex.vue`+`DexCell.vue` 신설(도감 6축 Vue 이전), 6 DTO 잠금 라벨 필드 보강, Alpine 완전 제거(main.ts·package.json), `#village-dex` 마운트 포인트화(SSR 폴백 유지). **S4 완료(2026-06-19)** — `VillageApp.vue` 신설(셸 컴포넌트, `/api/garden` 1회 fetch), `GardenGame.vue`·`GardenDex.vue` self-fetch 제거→defineProps 수신, `main.ts` 단일 마운트, `garden.html` SSR 폴백 전부 제거·`#village-app` div만(얇은 셸), `GardenController.village()` nickname만 모델(`GardenService` 의존성 완전 제거). TDD: `village_spaShell_hasNicknameOnly` RED→GREEN. 브라우저: `/api/garden` 1회/페이지로드·콘솔 에러 0. **S5 폐지** — S4에서 `garden.html`을 이미 얇은 셸로 완전 교체했으므로 별도 정리 PR 불필요. 마을(`/village`)은 이제 Vue SPA(`/api/garden` 단일 fetch, Thymeleaf 얇은 셸) 체제 완성. (근거: N-083·N-082, 계획: claude-docs/plans/2026-06-18-garden-spa-vue.md) — **풀스크린 단일 게임 셸 재편 S1 완료(2026-06-19)**: 카드 2분할 → 뷰포트 전체 단일 게임 스테이지. `garden.html` `.container.garden-wide`·헤더 제거·`data-nickname`·`viewport-fit=cover`. `VillageApp.vue` HUD 오버레이(브랜드·닉네임·꾸미기·도감·대시보드) + 도감 전체 오버레이(ESC·백드롭). `GardenGame.vue` `Scale.RESIZE`·편집 패널 하단 오버레이·`defineExpose({startEdit})`. `scene.ts` resize 핸들러(줌·센터 재계산). `app.css` 풀스크린 레이아웃 288줄(fixed 루트·HUD·dex·편집 패널·≥900px 액자·dvh/safe-area). `component.ts` 삭제. vitest 429 Green. — **모바일 세로 자동 가로 S2 완료(2026-06-19)**: `cam.setRotation(Math.PI/2)` + 줌 기준축 전환(`applyOrientation(w,h)`) + 팬 `getWorldPoint` 교체로 DOM CSS rotate 대신 카메라 회전(Phaser#7175 입력 깨짐 우회). 모바일≤900px 세로 진입 시 게임 가로 꽉 참. 실 기기(iOS Safari·안드) 스파이크 게이트 필요. — **S2 HUD 동반 회전 완료(2026-06-19)**: HUD·도감 오버레이·편집 패널을 `.village-ui-wrap` 래퍼로 묶고 `rotate(-90deg)` + 치수 스왑으로 카메라와 동일 시각 방향 회전. portrait·max-width:900px 미디어 쿼리, 데스크톱·태블릿 회귀 0. — **강제 회전 폐기·순수 반응형+보기 줌 S2 재설계 완료(2026-06-19)**: #411·#412 강제 회전 폐기. `scene.ts` `applyOrientation`→`fitCamera(w,h)` — `containZoomFor` 신설(세로/가로 비율 contain 줌), 초기줌=min(식물36px, contain) 절충(세로 모드에서 월드 전체 가시), `ZOOM_MIN=0.25`로 완화. 팬·핀치·휠 입력 보기/편집 공통 분리(readonly 보기 모드에서도 줌·팬). `VillageApp.vue` `.village-ui-wrap` 래퍼 제거→HUD 직배치. portrait 미디어 쿼리 삭제. `pure.ts containZoomFor` 순수함수 + TDD 435 Green. "세로에도 방향이 있어 고정 회전 부적합" 교훈(troubleshooting·learning-notes 후보). — **HUD 데스크톱 액자 안 배치 완료(2026-06-20, PR #428)**: `.village-hud { inset:0 }`이 풀 뷰포트에 펼쳐져 로고·버튼이 액자 **밖**에 뜨던 문제 수정. `app.css`에 `@media (min-width: 900px) .village-hud` 블록 추가 — `.village-game-root`와 동일 식(`top:50%; left:50%; transform:translate(-50%,-50%); width:min(100%,1000px); height:min(100dvh,800px)`)으로 HUD를 액자에 정확히 포갬. 모바일(<900px) 무변경. CSS만(Vue·garden.js 무변경). 실 브라우저 측정: `hudWithinFrame=true` 입증.

### 🎨 입구 디자인 트랙 — 미감 리프레시 (진행 중, 2026-06-10~)

> 입구(랜딩·가입·온보딩·대시보드 첫인상)에서 미감 때문에 이탈하는 것을 막는 디자인 트랙. 워크플로·소유권 분할 정본: [frontend-design-workflow.md](claude-docs/frontend-design-workflow.md), 데이터 계약: [template-data-contract.md](claude-docs/template-data-contract.md). 범위 기본값 = **"토큰 먼저 깐 리프레시"**(app.css 토큰 + 점진 적용, 마크업 최소 변경).

- **랜딩 리프레시 — 종이톤 + 세이지 토큰 시스템 + 고운돋움 폰트** ✅ (PR #287): `app.css` `:root` 색 토큰을 범용 인디고(`#4f46e5`) → **종이톤**(`#F3EEE4` 배경·`#FCFAF5` 카드) + **세이지 그린**(`#6E8A6A` 악센트·`#4F6B4C` 딥세이지) 팔레트로 교체. **토큰 이름 유지(무파괴)**라 25개 화면 전역이 한 번에 리프레시(`var` 참조). 본문 폰트 **'고운돋움'**(둥근 한글 산세리프, Google Fonts)을 `body`에 전역 적용 + 보조 토큰(`--sage-soft`·`--radius-sm`) 추가 + landing 히어로 위계 강화(태그라인 2rem). 달성색 `--ok`는 세이지와 변별되게 `#2F8F6B`로 톤 조정. 회귀 0(책장·기록 등 정적 목업 + computed-style 스폿체크). 다음: signup → onboarding → dashboard 순차 적용.

- **인디고 잔재 토큰화 + signup 환영 헤더** ✅ (PR #289): #287 토큰 교체가 못 건드린 **`var()` 아닌 raw 옛 인디고(`rgba(79,70,229,…)`)** 하드코딩 2곳(폼 입력칸 `:focus` box-shadow·`.highlight-tile:hover` 그림자)을 세이지로 토큰화 — `:root`에 공유 토큰 `--focus-ring: rgba(110,138,106,.30)` 신설, 전역 grep 0건 확인(focus 링·타일 그림자는 전 화면 공통이라 사이트 전역 동시 수선). signup 1차: `.greeting "회원가입"` → `.entry-hero`(환영 헤드라인+서브카피, landing 히어로 축소판) 교체 + `.field-hint` 종이톤 명시. 데이터 바인딩 보존. 순수 시각(컨트롤러·폼·DB·Flyway 무변경). 다음: onboarding → dashboard 빈상태(후속 분리 PR, dashboard는 라이브 영역 회귀 위험 커 단독).

- **onboarding 마감** ✅ (PR #290): signup이 확립한 `.entry-hero`(환영 헤드라인+동적 서브카피) 패턴을 onboarding에도 통일(`.greeting` 동적 한 줄 → 헤드라인+`needsLoginId` 분기 서브카피). 하루목표·7일 윈도우 안내 문단을 전역 공유 `.status-line` 대신 입구 전용 `.entry-note`(연한 세이지 박스 + strong 딥세이지)로 띄워 **가치 전달 위계 강화**. `needsLoginId` 두 형태(소셜 loginId 칸 / 로컬 없음) + 중복 에러 보존. 순수 시각(컨트롤러·폼·DB·Flyway 무변경, 기존 OnboardingController 테스트 그린). 정적 목업(`.preview/onboarding-mock.html`) computed-style 검증. 다음: dashboard 빈상태(단독 PR, 라이브 영역 회귀 위험).

- **dashboard 빈상태 마감** ✅ (PR #291): 입구 트랙 마지막 화면. 신규 첫 진입(`readingBooks`·`finishedBooks` 모두 empty)에서 타이머 카드가 측정 폼 대신 보이던 `.status-line muted` 안내 한 줄("책장에 책을 추가하면…")을 **`.timer-empty`**(환영 문구 "아직 책장이 비어 있어요…" + 풀폭 세이지 CTA "첫 책 추가하기" → `/books`)로 강화 — "텅 빈 화면"을 환영+첫 행동 유도로. **고위험 라이브 영역(htmx `hx-*`·Alpine `x-data/x-text/x-show`·`data-*`·`#dashboard-live` 프래그먼트·잔디 루프)은 구조 절대 보존**, 빈상태 `th:if` 조건식도 글자 단위 유지(시각·카피만). `a.btn`이 inline이라 `width:100%` 미적용 → `display:block`으로 측정 시작 버튼처럼 풀폭. 순수 시각(컨트롤러·서비스·DB·Flyway·JS 무변경) → **전체 테스트 그린**(라이브 프래그먼트·빈상태 분기·`ReadingSessionController` 공유 swap 회귀 가드) + 정적 목업(`.preview/dashboard-empty-mock.html`) computed-style·모바일 검증. 입구 디자인 트랙(landing #287·인디고+signup #289·onboarding #290·dashboard #291) **완료**.

- **랜딩 전면 리디자인 — 와이드 마케팅 랜딩(1080px, 세리프, 3단 반응형)** ✅ (PR #392, 2026-06-18): 460px 단일 컬럼 랜딩을 Claude Design 시안 기반 마케팅 페이지로 전면 교체. 핵심 결정: `body.landing-page`로 전역 앱 셸 격리·`landing.css` 별 파일 분리(전역 `app.css` 비오염). Gowun Batang 세리프 헤드라인 / 2단 비대칭 그리드(`.85fr 1.15fr` 등) / 잔디 CSS 목업(7×13 칸) / 정원·히어로 플레이스홀더(실제 캡처는 후속) / 4컬럼 기능 그리드 / 3단계 how-it-works / 반응형(860/480px). 음영 토큰 15개 추가. `LandingPageTest` 5/5 Green(신규 2건: garden 동선·핵심 키워드). 다음(백로그): 플레이스홀더 → 실제 화면 캡처 교체.

- **페이지별 Claude Design 재스킨(롤링)** ✅ 진행 중: 입구 이후 인터랙티브 페이지를 시안대로 페이지 스코프(`body.*-page` + 전용 프리픽스 클래스)로 순차 재스킨 — `/history`·`/books`(2026-06-25)·로그인/가입(2026-06-26)·책방 헤더(2026-06-27)·`/personality`(책BTI, 2026-06-27)·`/settings`(2026-07-01)에 이어 **책 상세(`/books/{id}`) 재스킨** ✅ (2026-07-03): 표지 무표지 플레이스홀더(고정 세이지+이니셜) 신설, 제목 Gowun Batang 격상, 상태 배지 3색+공개범위 칩(읽기전용) 신설, 구매 버튼 세이지 pill+SVG 셰브론, 월 브라우저 SVG 원형 버튼, 빈 상태 모래시계 아이콘, 하단 네비 세로 스택. `body.bookdetail-page` 스코프 — 책장·독서기록과 공유하는 `.record-date`/`.record-time`/`.month-nav-btn`/`.buy-menu`/`.book-status-badge` 등은 전부 스코프 오버라이드로 타 화면 무손상. SSR Thymeleaf·백엔드/라우트 무변경. 상세는 [changelog.md](claude-docs/changelog.md).

- **랜딩 정합성·기능 보강·디자인 통일** ✅ (2026-06-29): #392 와이드 랜딩을 실사이트와 화해(멀티에이전트 감사). 깨진 `/social`·비로그인 `/village` 네비를 **페이지 내 앵커**(`#village`/`#together`)로, 폐기된 '식물 심기' 마을 카피→현행 컨셉(작가·건물), 기능 그리드 4→6칸(독서 기록·책BTI 추가), **'함께 읽기' 정식 섹션 신설**(검색→팔로우→공개 프로필 동선 + 팔로잉 미리보기 CSS 목업), CTA 라운드 8px·카드 14px/24px 토큰 통일. 순수 마크업+CSS(Java 무변경), static-preview 정량 검증. **백로그 유지**: 히어로·마을 placeholder → 실 화면 캡처 교체(#392 백로그 그대로).

- **랜딩 로고·아이콘 SVG 통일 + '남의 잔디·마을 구경' 거짓 카피 정정** ✅ (2026-06-29): 위 #596에서 신설한 '함께 읽기' 섹션의 "서로의 공개 프로필에서 독서 잔디와 마을을 구경" 카피가 **현재 미구현**(마을·잔디 라우트는 본인 전용 `currentUserService.resolve(principal)`, 남의 공개 잔디는 `ProfileService`가 계산해도 `ProfileResponse` 직렬화에서 빠진 dead-code — 멀티에이전트 감사 3/3 반증 확인)이라, 실제 기능인 **"팔로우 + 서로의 책방(공개한 책·책BTI) 구경"**으로 정정하고 '팔로잉 미리보기' 카드의 미니 잔디바를 실제 노출값('공개 책 N권')으로 리워크. 함께 시각도 통일 — 랜딩에만 남아 있던 옛 `📚` 이모지 브랜드 로고를 앱 전역 통일 SVG(`brand-ico` 책등)로, 기능카드 6 이모지(⏱️🎯📖📊🧬👥)·잔디 footer `🌱`를 앱 라인아트 SVG 스킨(navIcons '이모지 금지' 원칙)으로 교체. 순수 SSR 마크업+CSS(Java·번들 무변경), TDD 회귀 가드 2건(로고 통일·거짓광고 부재) RED→GREEN + static-preview 정량 검증(로고 22px·아이콘 32px sage·미니잔디 0). **백로그 유지**: placeholder(🌿🏡)→실 화면 캡처, 소셜 아바타 동물(🦊🐢🦉)→user SVG.

### 📱 PWA 도입 — 홈 화면 설치 · 오프라인 · 설치 유도 (L1·L2·L3c ✅ 유지 / 웹 푸시 L3a·L3b 제거 2026-07-09 → 네이티브 백로그)

> **동기**: iPad 가로에서 브라우저 주소창·탭바가 화면을 잡아먹어 마을 게임 영역이 좁아지는 문제 해소. `display: standalone`으로 홈 화면 설치 시 브라우저 UI 제거(상태바=시계·배터리 유지). 단계별 출하 내역은 아래 항목 + [changelog.md](claude-docs/changelog.md).
>
> **❌ 웹 푸시 제거 (2026-07-09)**: **매일 독서 알림(L3a)·복귀 알림(L3b) 웹 푸시를 제품에서 제거**했다 — 웹 푸시는 사실상 네이티브 앱 기능(iOS PWA 푸시 제약·설치 강제·구독 관리 부담)이라, 웹에선 미점등으로 UI만 마스킹돼 있던 것을 걷어내고 **네이티브 패키징 백로그로 강등**([[native-packaging-backlog]]). 대신 **이메일 재참여 넛지(복귀 안내 메일)만 유지**(SES 점등 완료). 제거 범위: `push/` 패키지·`retention/RetentionPush*`·`User` 푸시 필드·`ReadingSessionRepository.findRetentionPushTargets`·`notification-settings.js`·`sw.js` 푸시 핸들러·`build.gradle` 푸시 의존성(web-push·bcprov·jose4j·httpcore). **DB(V50·V51)는 forward-only라 보존**(dead — 두 NOT NULL 컬럼은 `DEFAULT FALSE`라 매핑 제거 후에도 INSERT 무해). **아래 L3a·L3b·알림 설정 통합 항목은 구현 이력(근거 보존)이며 현재 코드에서 제거됨.** L1(풀스크린)·L2(오프라인 SW)·L3c(설치 유도 칩)·정적자산 해시는 유지.

- **L1 풀스크린 ✅ (2026-06-20)**: `static/manifest.json`(name·short_name·start_url=/(최초 `/dashboard`였으나 매핑 없는 경로라 콜드 런치 404 → `/`로 수정. 단 **이미 설치된 PWA는 start_url을 동결**해 manifest만 고치면 구 install은 여전히 `/dashboard` 콜드 런치 404 → 서버가 `/dashboard`를 `/` 별칭으로 수신(`DashboardController` 매핑 확장 + permitAll)해 동결 install 구제, changelog 2026-06-24)·display=standalone·scope=/·theme_color=#6E8A6A·background_color=#F3EEE4·icons 3종) + 임시 아이콘 4종(`icons/` — 192·512·maskable-512·apple-touch-icon 180, sharp SVG→PNG) + `templates/fragments/pwa-head.html`(manifest link·theme-color·apple 메타·apple-touch-icon 공통 fragment) → garden·dashboard·landing·login 주입 + `SecurityConfig` `/manifest.json`·`/icons/**` permitAll. TDD: vitest `pwa-manifest.test.ts` 8종 RED→GREEN + `PwaStaticAccessTest`(MockMvc 미인증 200) RED→GREEN.
- **L2 설치형/오프라인 ✅ (2026-06-20)**: `static/sw.js` 신설(CACHE='shell-v1'·install precache·activate 구 캐시 삭제·clients.claim·fetch: HTML navigate = network-first·garden.js = network-first·정적자산 = cache-first) + `pwa-head` fragment에 `serviceWorker.register('/sw.js')` 전역 등록 + SecurityConfig `/sw.js` permitAll. vitest `pwa-sw.test.ts` 7종 + `PwaStaticAccessTest.swJs_isPublic` RED→GREEN. 실 브라우저 게이트(오프라인 셸·갱신·설치 배너) 필요.
- **L3a 본인 독서 리마인더 푸시 ✅ (2026-06-20)**: VAPID 키쌍(환경변수 주입, 실키 커밋 0) + `PushSubscription`(1:N 멀티 디바이스, endpoint unique) + V50 Flyway + `PushApiController`(subscribe upsert·unsubscribe IDOR 차단·public-key) + `PushSenderService`(404/410 만료 구독 삭제) + `PushReminderScheduler`(매일 KST 20시, `@ConditionalOnProperty push.enabled`) + `PushReminderService`(23h 멱등) + sw.js push/notificationclick + VillageApp HUD 알림 토글(iOS 미설치 게이트). TDD 19종 RED→GREEN. VAPID 키 설정 후 `BOOKTIMER_PUSH_ENABLED=true`로 점등.
- **L3b 비활동 복귀 nudge 푸시 ✅ (2026-06-20)**: `marketingPushConsent`(이메일·L3a와 채널별 독립, §50 opt-in) + `marketingPushNudgeSentAt`(채널별 멱등) + V51 Flyway + `RetentionPushService`(7일 비활동·exists 구독·null-state 가드·EXPIRED 구독 삭제) + `RetentionPushScheduler`(매일 KST 19시, `@ConditionalOnProperty nudge-push.enabled`) + `/api/push/marketing-consent` 토글 API + VillageApp 복귀 알림 토글(§50: "(광고)" payload, OFF VAPID 불필요). TDD 20종 RED→GREEN. `BOOKTIMER_NUDGE_PUSH_ENABLED=true`·VAPID 설정 후 점등.
- **L3c 설치 유도 칩 ✅ (2026-06-20)**: `static/pwa-install.js` ESM(빌드 없음) — 우하단 알약 칩 + × 7일 침묵(localStorage). 크로미움(beforeinstallprompt 디퍼드)·iOS Safari(공유→홈 화면 안내 오버레이)·standalone/기타 숨김. `pwa-head.html`에 `<script type="module">` 전역 주입 → 모든 페이지 노출. SecurityConfig `/pwa-install.js` permitAll. vitest 순수 함수 21종 RED→GREEN. 실 브라우저 게이트: 안드 크롬 칩→prompt·iOS Safari 칩→오버레이→수동 설치·× 7일 침묵·타이머 동선 비침범.
- **알림 설정 통합 ✅ (2026-06-21)**: 마을 HUD 푸시 버튼 2개(L3a·L3b) 제거 → 설정 페이지 "알림" 섹션으로 통합. `notification-settings.js`(빌드 없는 ESM 섬, `pwa-install.js`와 동일 패턴, readyState 타이밍 보호·iOS 미설치 게이트·VAPID 미설정 비활성 포함) 신설. `SettingsController`에 vapidPublicKey·marketingPushConsent 모델 추가. `GardenController`·`garden.html`에서 vapid 주입 제거. VillageApp.vue 푸시 로직 전면 제거·garden.js 재빌드. CSS `.btn-push-toggle` 신설. 로직 중립 추출로 추후 설정 Vue 전환 시 재사용 가능.
  - ❌ **제거됨 (2026-07-09)**: 위 알림 통합 UI(푸시 토글 2개·`notification-settings.js`·`inert` 오버레이 "알람 기능은 아직 개발 중")는 제거되고, `/settings` "알림" 카드는 **재참여 안내 메일 수신 동의(이메일)만** 남았다(마스킹 해제). 웹 푸시는 네이티브 백로그.
- **정적 자산 해시 도입 — 캐시 stale 해결 ✅ (2026-06-21)**: `spring.web.resources.chain.strategy.content.enabled=true`로 Spring이 정적 자산에 내용 MD5 해시 URL 부여(`/css/app.css`→`/css/app-<md5>.css`). Thymeleaf `@{}`이 `ResourceUrlProvider`로 자동 변환해 빌드 파이프라인 무변경. `sw.js` NETWORK_FIRST 졸업(해시 URL은 cache-first 안전 → `shell-v5`). `pwa-head.html` `/pwa-install.js` 하드코딩→`th:src="@{}"`. 선별 SPA 로드맵 단계 0 완료.
- **선별 SPA 단계 1b — history 페이지 Vue 섬 전환 ✅ (2026-06-21)**: `HistoryApiController`(`GET /api/history` — 통합 한 방, `GrowthStage` enum→`GraphDto`에서 `growthEmoji`·`growthLabel` 문자열로 평탄화)·`HistoryController` SSR 슬림화(nickname만)·`history.html` Vue 얇은 셸 교체. `HistoryApp.vue`·`ContributionGraph.vue`·`MonthlyRecords.vue`·`WeeklyShortfall.vue` 신설. Vite 멀티빌드에 history 1줄 추가(`build:history` → `static/history/history.js`). vitest 프론트 8종 + 백엔드 TDD 4종 RED→GREEN. 실 브라우저 게이트: 잔디 377셀·탭 전환·콘솔 에러 0·해시 URL 자동 적용.
- **선별 SPA 단계 1a — search 페이지 Vue 섬 전환 ✅ (2026-06-21)**: Vite 페이지별 독립 멀티빌드(`cross-env APP=search vite build` → 단일 파일 `search.js`), Spring chain 해시 URL 자동 적용. `SearchApiController`(`/api/search` — 아이디 검색+추천, `myLoginId`, `rateLimited`)·`FollowApiController`(`/api/follow`, `/api/unfollow` — CSRF, IDOR 차단, 멱등, 자기팔로우 400, 없는 loginId 404)·`SearchController` SSR 슬림화(Vue 셸 `data-*`만). `search.html` Vue 셸로 교체(`<div id="search-app" data-my-login-id data-initial-q>`). `SearchApp.vue`(onMounted 즉시 API 호출·팔로우 토글·rateLimited 안내). vitest 프론트 5종 + 백엔드 TDD 13종(SearchApiController 5·FollowApiController 8) RED→GREEN. 실 브라우저 게이트: 검색·콘솔 에러 0·해시 URL 변경 확인.
- **선별 SPA 단계 1c — personality Vue 섬 전환 ✅ (2026-06-21, 단계 1 완료)**: `PersonalityApiController`(`GET /api/personality`·`POST /refresh`·`POST /select/{id}` — 상태 3종 READY/COLD_START/FALLBACK, 한도초과 429, IDOR 가드 서비스 위임, serve-stale, ZoneId 미노출·generatedAtLabel 서버 포맷 평탄화). `PersonalityController` SSR 슬림화(nickname·loginId만, POST 매핑 API 이관·제거). `personality.html` Vue 얇은 셸 교체. `static/js/personality.js` 삭제(Vue 이관 완료). `PersonalityApp.vue`(상태 분기 v-if·refresh 동기 LLM 30초 타임아웃·로딩 스피너·CSRF 헤더)+`PersonalityCarousel.vue`(scroll-snap step/sync Vue ref+onMounted 이관·선택 버튼) 신설. Vite 멀티빌드에 personality 1줄 추가(`build:personality` → `static/personality/personality.js`). vitest 프론트 9종 + 백엔드 TDD 8종(PersonalityApiControllerTest — 인증게이트·상태3종·직렬화·한도429·IDOR) RED→GREEN. 실 브라우저 게이트: COLD_START 상태·Vue 마운트·zone 미노출·콘솔 에러 0. **선별 SPA 단계 1(읽기전용 3페이지: search·history·personality 섬 전환) 완료.**
- **선별 SPA 단계 2(a~d) + 단계 3 — 소셜·books Vue 섬 전환 ✅ (2026-06-22)**: follow-list(2a)·book-readers(2b)·block-list(2c)·profile + report(2d)·books(3) 5페이지 Vue 섬 전환, htmx 완전 제거. 상세는 changelog.md.
- **선별 SPA 백로그 정리 — 옛 SSR POST 핸들러 제거 ✅ (2026-06-22)**: `FollowController`·`ReportController` 삭제, `BlockController`·`BookController` POST 핸들러 제거(GET·buy* 4종·`/me/blocks` 유지). dead CSS `#tag-books-panel` 제거, `garden.html` htmx `<script>` 태그 제거. `GlobalExceptionHandler`에 `HttpRequestMethodNotSupportedException` 405 보존 추가. `SsrMutationRemovedTest` 9종 RED→GREEN.
- **선별 SPA 단계 4 — 대시보드(/) Vue 섬 전환 ✅ (2026-06-22)**: `DashboardApiController`(`GET /api/dashboard`·`POST /api/sessions/start|stop`) 신설, `DashboardModel.computeLive(User)` 도메인 추출. `DashboardController` 얇은 셸(인증·역할·온보딩 분기만). `dashboard.html` Vue 셸 교체. `ReadingSessionController` htmx 분기 제거(Vue REST API 흡수). `useReadingTimer.ts`(wall-clock 자가보정 composable)·`DashboardApp.vue` 외 8개 Vue 컴포넌트 신설. TDD RED→GREEN(백엔드 20종·프론트 5종). 상세는 changelog.md.

### 📨 사용자 피드백/문의 (완료 ✅ 2026-06-08, PR #233 · 답장·유형필터 PR #234)

> **한 줄**: 일반 사용자가 버그·제안을 개발자에게 보내고, 개발자(ADMIN)가 읽음/처리완료를 표시하면 **그 표시는 작성자 본인만** 본다.

- **사용자 동선**: `GET/POST /feedback`([FeedbackController](src/main/java/com/booktimer/web/FeedbackController.java)) — 유형(버그/제안/기타)+제목+내용 작성, 아래 "내 문의"에 **본인 글만** 처리 상태(미확인/읽음/처리완료) 배지와 함께 노출. `POST /feedback/{id}/delete`는 **본인 글만** 삭제. 대시보드 진입점은 **우상단 아바타 드롭다운(사용자 메뉴)의 "문의"**(설정·로그아웃과 일원화 — 옛 빠른이동 타일에서 이전).
- **관리자 동선**: `GET /admin/feedback`([AdminFeedbackController](src/main/java/com/booktimer/web/AdminFeedbackController.java)) — 전체 문의를 작성자와 함께 조회(유형 필터 탭 전체/버그/제안/기타, `?type=`), `read`/`resolve`/`reply`/`delete` POST로 상태 표시·답장·삭제. `/admin/**` ADMIN 인가 + CSRF. admin 홈에 "문의함" 카드.
- **노출 스코핑·IDOR**: 내 목록은 `findByAuthor` 쿼리로 태생적 스코핑(남의 글 안 보임), 삭제는 작성자 일치 검사(`deleteOwn` — 남의 글 id면 조용히 무시, 존재 누설 없음). 상태 표시 결과는 작성자 본인 화면에만. 보안 경계는 기존 매처로 충분(`/feedback`=authenticated, `/admin/**`=ADMIN) — **SecurityConfig 무변경**.
- **모델**: `Feedback`(author FK·type·title·content·status, BaseTimeEntity) + `FeedbackType`(BUG/SUGGESTION/ETC)·`FeedbackStatus`(SUBMITTED→READ→RESOLVED, 단조 진행) enum, **V23** `feedback` 테이블(report 구조 모방). 관리자 목록은 `AdminFeedbackRow` DTO로 트랜잭션 안에서 조립(LAZY author OSIV 비의존). 탈퇴 정리(`AccountService.purge`)에 `deleteByAuthor` 추가(author_id FK).
- **v1 (PR #233)**: 작성·본인 조회·상태 표시·삭제. TDD Red→Green: 도메인 6·서비스 5(IDOR)·컨트롤러 13(미인증·USER 403·스코핑·삭제 IDOR) + 전체 그린(FlywayMigrationTest가 V23↔엔티티).
- **후속 — 개발자 답장 + 유형 필터 (PR #234)**: 개발자 **단일 답장**(`Feedback.reply`·**V24**, 덮어쓰기) — 저장 시 자동 '읽음'(처리완료는 별도, `applyReply`), 작성자 본인만 봄(`POST /admin/feedback/{id}/reply`). 관리자 목록 **유형 필터**(`?type=` 탭 — `FeedbackType.parse`가 없음/잘못된 값을 null=전체로, `from`의 ETC 폴백과 구분). 작성자 수정은 여전히 없음(삭제만). 답장↔상태는 '자동 읽음, 처리는 별도'로 결합(사용자 결정). TDD Red→Green: 도메인 +4·서비스 +2·컨트롤러 +3(답장 저장+자동읽음·USER 403·유형 필터·작성자 답장 노출) + 전체 그린(V24↔엔티티).

### 🌍 영미권(글로벌) 진출 — 검색·구매 소스 region 분리 + UI 영어화 (계획 ⏳ 2026-06-12, 우선순위: 나중 / 백로그)

> **나중에 할 일 — 영미권 홍보를 실제로 추진할 때 착수.** (사용자 지시 2026-06-12: "plan.md에 정리, 나중에 할 일로".)
> **동기**: 영미권 홍보. **문제**: 알라딘·쿠팡은 한국 전용이라 영미권 사용자에겐 책 검색·구매가 무의미.
> 그래서 region별로 **검색 소스·구매처·UI 언어**를 분기한다. 상세 설계 초안 = `claude-docs/plans/2026-06-12-en-region-split.md`(로컬, `.gitignore` — 착수 시 재생성·갱신. 아래 요약이 단일 출처).

**핵심 통찰 — "HTTP 헤더로 분리"의 실체**: 헤더 분리는 *라우팅*(언어/지역 추정)만 푼다 — **Host(도메인)는 확실**, Accept-Language(브라우저 언어)·GeoIP(IP 국가)는 *보조 추정*(현 ALB 직결이라 GeoIP 헤더도 없음). **진짜 작업은 헤더가 아니라 책 데이터 소스·제휴 교체**다 — 이게 아래 「4단계 i18n」이 "동적 데이터는 번역 안 됨 ③"으로 남겨뒀던 문제의 정면 해결이다(영미권은 한국어 알라딘 데이터가 아니라 영어 Google Books 데이터를 받는다).

**✅ 확정 결정 (사용자 합의 2026-06-12)**:

| 항목 | 결정 |
|---|---|
| 아키텍처 | **단일 앱 + region 분기** (별도 인스턴스 X, 백엔드 하나) |
| region 판정 | **같은 도메인 + 언어 전환** (별도 도메인 X, 인프라 추가 0) |
| 영미권 검색 | 알라딘 → **Google Books / Open Library** |
| 영미권 구매 | 알라딘·쿠팡 → **Amazon 제휴**(검색링크 + Associate 태그, 쿠팡 패턴) |
| UI 영어화(i18n) | 백엔드(검색·구매) 먼저, **UI 영어화는 다음 단계**로 분리 |

**아키텍처 골자** (포트·쿠팡 빌더·전역 ModelAdvice 패턴이 이미 있어 그대로 재사용):
- **Region 표현**: Spring `Locale` 단일 진실 → `Market(KR/EN)` 파생(`Market.from(Locale)`, null·미지정·기타언어→KR로 **현행 보존**). 별도 enum 이중관리 회피(4단계 i18n과 일관).
- **검색 분기**: `RoutingBookSearchClient implements BookSearchClient`(`@Primary`)가 포트 뒤에서 `LocaleContextHolder` 보고 위임 → `BookService`/`BookController` **시그니처 무변경**(회귀 최소). KR=`AladinBookSearchClient`(현행), EN=신규 `GoogleBooksSearchClient`(JSON→`BookSearchResult` 매핑).
- **구매 분기**: `AmazonLinkBuilder`(=`CoupangLinkBuilder` 복제: 환경변수 Associate 태그·URL 템플릿·미설정 시 버튼 숨김) + `Book.amazonClickCount` + `recordAmazonClick`/`recordPublicAmazonClick` + 엔드포인트 `/books/{id}/buy/amazon`(쿠팡 대칭). region별 버튼 토글(`CoupangModelAdvice` 패턴).
- **판정 우선순위**: 로그인 `User.preferredLocale`(신규 컬럼) > 쿠키(`CookieLocaleResolver`) > Accept-Language > 기본 ko. 전환=`?lang=en` 토글 + 로그인 시 저장.

**PR 분할** (각 독립 PR, TDD RED→GREEN. 회귀 가드 = 기본 KR 경로가 안 깨지는지 명시 단언):
1. **PR1 — region 골격 + Amazon 구매**(묶음): `Market`·LocaleResolver·전환·`User.preferredLocale`(+Flyway)·`AmazonLinkBuilder`·`amazonClickCount`(+Flyway)·구매 엔드포인트·버튼 토글. **검증**: EN→Amazon 버튼·집계, KR→알라딘/쿠팡 현행 그대로. *1·2를 묶어야 "EN 모드에서 Amazon 버튼이 뜬다"는 눈에 보이는 산출이 나옴.*
2. **PR2 — Google Books 검색**: `GoogleBooksSearchClient`·`RoutingBookSearchClient`(@Primary)·고정 fixture 파싱. **검증**: EN→영어책 검색, KR→알라딘.
3. **PR3 — UI 영어화(i18n)** = 아래 「4단계」 흡수. 사용자 대면 문자열 수백 개 추출이라 **가장 큼**(별도 상세 계획 필요).

**미결정 (착수 시 확정 — 추천 있음)**: ① 구매 버튼 노출 = **요청 region 기준**(추천·단순) vs 책 출처 기준 ② `lookupByIsbn` 백필 region(ThreadLocal 안 흐름 → 3단계서 결정) ③ 언어 전환 UI(`?lang=` **쿠키 토글 추천**).

**연관**: §리브랜딩(엔진 B 완성 트리거와 겹칠 수 있음)·§도메인 TLD 이전(나중에 별도 도메인 택 시)·§홍보/마케팅(영미권 홍보가 동기)·§eBook 제휴(Amazon 제휴 인프라 공유 여지). **법규**(실제 공개 시 별도): 영어 UI·CAN-SPAM(미)·GDPR(EU)은 이번 백엔드 범위 밖.

#### ▼ 4단계(PR3) — UI 영어화(i18n) 상세 메모 (구 「다국어」 항목 보존, 2026-06-09)

> 이 절은 위 진출의 **마지막 단계**다(백엔드 검색·구매 분리 뒤). Accept-Language·i18n 추출·함정 기록.

- **무엇**: 브라우저가 요청마다 보내는 `Accept-Language` 헤더로 언어를 협상(content negotiation)해 한국어/영어 분기. ⚠️ 이 헤더는 **IP/지역이 아니라 브라우저·OS 언어 설정** 기반이다(한국 IP라도 영어 브라우저면 en). 지역 기반은 GeoIP라는 별개 기술(부정확·프라이버시 이슈로 언어 판별엔 비권장).
- **Spring 현황**: 헤더 읽기는 Spring Boot 기본 `AcceptHeaderLocaleResolver`로 거의 공짜. **진짜 작업은 i18n 추출** — 현재 모든 UI 문구가 템플릿에 한글 하드코딩(messages 번들 0 · LocaleResolver/MessageSource 커스텀 0, 2026-06-09 확인). `messages.properties`(기본/영어)·`messages_ko.properties`로 빼고 템플릿을 `th:text="#{key}"`로 교체 + 영어 번역(사용자 대면 문자열 수백 개). 작업량의 8할이 이 추출.
- **실무 고려 3가지**: ① 헤더는 "첫 추정"으로만 — 명시적 언어 토글(`CookieLocaleResolver` + `LocaleChangeInterceptor`, `?lang=en`) 병행 권장(헤더가 틀린 사용자 대비). ② **SEO**: 같은 URL에 헤더로 분기하면 크롤러가 한 언어만 색인 → 다국어 검색 노출 원하면 `/en/`·`/ko/` 경로 분리 + `hreflang`(더 무겁지만 SEO 유리). ③ **동적 데이터는 번역 안 됨** — 작가 격언(DB)·책 제목/저자(알라딘)·책BTI 서술(LLM 한국어)은 데이터 자체가 한국어라 "절반만 영어"가 되기 쉬움(메시지 번들은 UI 껍데기만 번역). ← **위 진출의 검색 소스 교체(Google Books)가 이 ③의 책 데이터 절반을 해결**한다.

### 🎵 책 맞춤 선곡 — 책에 어울리는 플레이리스트 (아이디어 📝 2026-07-14, 우선순위: 낮음 / 기록만)

> ⚠️ **기록만 — 구현 미착수.** 책 옆에 "그 책에 어울리는 **긴 플레이리스트**"를 붙이는 몰입/디라이트 기능. 외부 음악 플랫폼·원작연도 데이터 소스는 웹 리서치로 검증(2026-07-14, 적대적 검증 포함). 착수 시 별도 설계 문서 합의 필수(책BTI·SNS와 동일 원칙 — 데이터 파이프라인·저작권 경계가 핵심).

**컨셉**: 책 상세(또는 책방)에서 그 책에 어울리는 **긴 플레이리스트**를 큐레이션해 임베드로 들려준다. 아예 짧은 몇 곡이 아니라 *플레이리스트를 통째로 주는* 느낌이 베스트. 독서 세션의 몰입을 높이고("이 책엔 이 음악"), "이 책엔 이런 플레이리스트"라는 공유거리(디라이트 후크)가 된다.

**어울림의 기준(사용자 발상, 2026-07-14)**:
- ① **작가 국적** — 그 나라·문화권의 음악.
- ② **원작 시대** — 작품이 쓰인/배경이 된 시대에 나온 음악.
- ③ **작품 분위기** — 알려진 작품 분위기와 매칭되는 분위기의 곡.

**⚠️ 데이터 공백 — 이 기능의 진짜 난제 (핵심)**: 현재 `Book`은 위 세 기준 중 **어느 것도 바로 못 준다**. [Book.java](src/main/java/com/booktimer/book/Book.java)엔 `author`(문자열)·`category`(알라딘 분류)·`pubDate`밖에 없다.
- **작가 국적** — 저장 안 됨. `category`의 "러시아소설/미국소설/일본소설…"이 국적을 **거칠게** 인코딩(국내도서/외국도서 구분 포함)하나 정밀 국적은 아님.
- **원작 시대 — `pubDate`를 쓰면 틀린다(가장 위험)**. `pubDate`는 원작이 아니라 **한국어판 출간일**이다(예: 1866년 러시아 소설의 2020년 번역판 날짜). ②의 "그 시대 노래"에 이 값을 쓰면 시대가 통째로 어긋난다. 원작 최초 출간연도는 **별도로** 구해야 한다.
- **작품 분위기** — 저장 안 됨. 객관 정답도 없음.

→ 그래서 이 기능의 8할은 "선곡 UI"가 아니라 **책을 국적·원작연도·분위기로 보강(enrich)하는 데이터 파이프라인**이다. 책BTI가 장르·출간일을 적재했듯, 여기선 한 겹 더 깊은 메타를 채워야 한다.

**보강 소스 (리서치 검증, 2026-07-14 — 적대적 검증 통과)**:
- **원작 최초 출간연도·작가 국적 = Wikidata 작품(work) 아이템**이 사실상 유일한 무료·무인증 소스. 작품 아이템의 `P577`(publication date)=원작연도, 저자 `P27`=국적, `P569/P570`=생몰, `P407`=원어. 무인증 SPARQL 한 번(query.wikidata.org/sparql). (실측: 『죄와 벌』 Q165318 → P577=**1866**·P27=러시아제국.)
- **⚠️ OpenLibrary `first_publish_year`·Google Books `publishedDate`는 원작연도가 아님** — 번역판/에디션 연도를 준다(실측: OL이 『죄와 벌』 상위 결과에 1941/2014 반환, 1866 아님). 원작연도로 신뢰 금지(표지·기본 메타 폴백용으로만).
- **링크 키 = ISBN이 아니라 원제+저자**. 한국어판 ISBN-13은 Wikidata에 대개 없어 ISBN 직접 조회로는 원작 work에 못 닿는다. **알라딘 상품 API `subInfo`의 원제(원서 제목)**가 그 매칭 키를 주고, **번역서에만 채워지므로 "번역서인가" 플래그**로도 쓰인다.
- **값싼 분기**: 원제 있음=번역서 → `pubDate`(한국어판)로 시대 판정 **금지**, Wikidata로 원작연도. 원제 없음=한국 원작 추정 → `pubDate`≈원작연도 근사해도 대체로 안전(번역 지연 없음).
- **분위기** — 그라운딩할 사실이 없어 **LLM + 닫힌 어휘(소수 enum 버킷) 분류**가 유일한 통제(책BTI 8종족 태거와 같은 정신). 카테고리→분위기 매핑은 매우 거칠어 최후 폴백·낮은 신뢰도로만.
- **LLM 신뢰도** — 캐논/유명작엔 원작연도·국적·분위기 추론이 쓸 만하나 무명·롱테일엔 환각 → **Wikidata 그라운딩 우선, LLM은 미스 폴백 + enum 제약 디코딩**.

**A(결정적) vs B(LLM) — 책BTI와 같은 갈래**:
- **A. 데이터/규칙 기반** — Wikidata(원작연도·국적) + `category`(국적·거친 장르) → 결정적으로 "국적·시대 버킷" 확정. 외부 비용 낮고 설명가능. **MVP·검증용으로 적합.**
- **B. LLM(분위기·선곡 서술)** — 분위기 버킷 분류 및 "왜 이 곡"의 표현 레이어. A로 뼈대를 잡고 B를 얹는 하이브리드가 후보.

**음악 소스 (리서치 검증, 2026-07-14) — 임베드 우선**:
- **YouTube IFrame Player 임베드 = 1순위**. `youtube.com/embed/videoseries?list=<ID>` — **무료·무로그인·무쿼터**로 로그아웃 방문자도 **긴 플레이리스트 전곡 풀 재생**(광고 포함). 한국 음악 스트리밍 1위(유튜브뮤직 ~42%)라 도달도 최대. 책별로 **큐레이션한 공개 플레이리스트 ID만 저장**해 iframe에 주입하고 **Data API는 쓰지 않는다**(검색 quota 10k/day·search=100 units → 하루 ~100회 상한 회피).
- **Spotify** — oEmbed(무인증)/iframe 임베드는 되나, **비로그인은 30초 미리듣기만**이고 전체 재생은 **로그인+Premium(주로 데스크톱)**이라 "긴 플레이리스트 감상"엔 마찰이 크다(검증: "로그인=전체곡"은 오류, Premium 필수). Web API OAuth는 2026 정책상 소규모 앱엔 사실상 막힘(dev mode 5인·오너 Premium / extended quota 250k MAU 요건). → **보조**로만, 사용자 계정 연결 설계는 피한다.
- **Apple Music** — `embed.music.apple.com` iframe은 토큰 없이 되나 **비구독자 30초 미리듣기**라 역시 감상엔 부적합(보조).
- **한국 음원사(Melon·Genie·FLO·Bugs)** — 공개 임베드 플레이어·공개 API **없음** → 플레이어로 못 붙인다. 필요하면 **"멜론에서 듣기" 아웃바운드 딥링크**로만.
- **⚠️ 저작권** — 플랫폼 공식 iframe 임베드만 합법 경로. **오디오 자체 호스팅·가사 재현 금지**(글로벌 저작권 규칙·플랫폼 ToS).

**비용 이점 — 책BTI보다 훨씬 쌈 (핵심)**: 플레이리스트·원작연도·국적·분위기는 **유저가 아니라 책(isbn13) 단위** 파생물이다. 한 번 만들어 **모든 사용자가 공유**(Wikidata/LLM 호출·큐레이션이 책당 1회, 이후 캐시 히트). 책BTI(유저 단위 재계산)보다 캐시 재사용률이 압도적. 파생 캐시 테이블(Flyway) + 책BTI가 깐 LLM 포트·타임아웃·폴백 패턴 재사용.

**재사용할 기존 인프라**: [ReadingPersonalityNarrator](src/main/java/com/booktimer/personality/ReadingPersonalityNarrator.java)(LLM 포트·Gemini 어댑터·isEnabled 게이트·타임아웃·예외→폴백), 파생 캐시+`ProfileSignature` 패턴([ReadingPersonalityCache](src/main/java/com/booktimer/personality/ReadingPersonalityCache.java)), 결정적 닫힌 어휘 태거([ReadingTagger](src/main/java/com/booktimer/personality/ReadingTagger.java)/[ReadingTribe](src/main/java/com/booktimer/personality/ReadingTribe.java))가 "카테고리→버킷" 분류의 본보기, 알라딘 [lookupByIsbn](src/main/java/com/booktimer/book/BookSearchClient.java) 백필(원제·`subInfo`도 여기서 받도록 확장).

**thesis 정합성**: 몰입/디라이트 레이어라 **엔진 A(입문자 습관)·B(소셜) 어느 쪽에도 강하게 안 묶이는 nice-to-have**다. 독서 세션을 더 붙잡는 약한 retention 보탬 + "이 책엔 이 플레이리스트" 공유거리(엔진 B 결). **핵심 retention 레버(재참여 넛지)도 아니고 수익 축도 아니라 우선순위는 낮음** — §전략상 솔로 retention·밀도가 먼저다. 선곡 큐레이션 유지보수 부담도 고려(책마다 사람이 고르면 안 커짐 → 초기엔 인기·대표작 소수만).

**설계 시 결정할 것(착수 시)**:
- **큐레이션 방식** — 책별 사람 손 큐레이션(품질↑·확장성↓) vs 국적·시대·분위기 **버킷 → 버킷별 대표 플레이리스트** 매핑(자동·거침) vs LLM이 곡 목록 생성 후 사람 검수. 시작은 **버킷 매핑 + 인기 책 소수 수동**이 가벼움(§작가 격언 내장목록·§독서 도구 정적 시드와 같은 정신).
- **매칭 단위** — 책마다 개별 플레이리스트 vs (국적×시대×분위기) 버킷 공유 플레이리스트. 후자가 큐레이션 부담을 **상수**로 만든다.
- **노출 위치** — 책 상세 곁 작은 섹션(§작가 격언·§독서 도구 제휴의 비침습 원칙). 홈 전면 배너 금지(타이머 정체성 훼손).
- **보강 파이프라인** — Wikidata work 매칭(원제+저자)·원제 없음 분기·미스 시 LLM 폴백·enum 분위기 버킷·캐시 무효화(책 메타 변동 시).
- **콜드스타트/미스** — Wikidata·플레이리스트 없는 롱테일 책은 **카드 숨김**(책BTI 콜드스타트 패턴) — 억지 매칭보다 미노출.

**연관**: §작가 격언(독서 표면의 디라이트 콘텐츠 형제)·§책BTI(enrich 인프라·A/B 갈래·닫힌 어휘 태거·캐시 재사용)·§독서 도구 제휴(비침습 노출·정적 시드 큐레이션)·§영미권 진출(원작 국적·다국적 데이터가 겹침). 개념: [learning-notes N-055](claude-docs/learning-notes.md)(null-state 누출 — 보강 실패 책이 목록에 새지 않게).

---

### 📱 토스 앱인토스(미니앱) 퍼블리싱 — 모바일 동반 채널 (✅ 출시·운영 중 2026-08-11~ · 계속 확장)

> ⚠️ **이 헤더는 2026-08-25까지 「진행 중(다음 할 일 마커) · 2026-08-05 · 설계 합의 완료」였다** — 출시하고 2주 넘게 운영하며
> 배포를 6차까지 돌린 뒤에도 「설계 합의 단계」로 읽히는 문장이 머리에 남아 있었다. 섹션 헤더는 아무도 체크하지
> 않으니 가장 오래 낡는다(2026-08-25 마커 감사).

> **채널 전략: 이전이 아니라 병행.** 웹(booktimer.app)은 PC 사용자를 위해 계속 본진으로 유지하고, 토스 미니앱은
> 모바일 동반 채널이다. **같은 사람이 PC에선 웹, 모바일에선 토스로 같은 기록을 봐야 한다** — 그래서 DB(=기존 서버·
> 도메인 로직)를 공유하고, 프론트만 토스 UX로 새로 만든다. 기존 계정 ↔ 토스 신원 **연결이 MVP 필수**다.

**구조**: 미니앱 프론트(신규 `miniapp/` — Vite + React + TDS) → Bearer 토큰으로 **기존 `/api/**` JSON API 호출**(CORS)
→ 기존 Spring Boot 서버 → 기존 MySQL. 웹이 이미 Vue 섬 + `/api/**` 구조라 **미니앱이 재사용할 API는 대부분 이미 있다**.

**설계 핵심 결정**:
- **인증 = 자체 불투명 Bearer 토큰**(JWT 아님 — 새 의존성 0·즉시 폐기 가능). 세션 쿠키 재사용은 WebView 서드파티
  쿠키 차단 리스크라 배제. 토스 accessToken은 신원 확인에만 쓰고 버린다.
- **신원 = `users.toss_user_key`**(이메일 아님). 토스 email은 null일 수 있고 소유 보증이 없어 **이메일 자동 연결은
  계정 탈취 벡터** → 금지. 기존 계정 연결은 **웹에서 발급한 일회용 코드**로만(계정 유형 LOCAL/GOOGLE 무관하게 동작).
- **Security = `@Order(0)` 별도 체인**(STATELESS·CSRF off). 라우팅 스위치는 Bearer 헤더 유무라 **기존 세션 체인 무수정**
  — 웹 채널이 그대로 산다(회귀 가드 테스트로 고정).
- **온보딩은 login_id 미강제** — 미니앱 신규 계정은 핸들 없이 시작(불변 핸들 작명 마찰 제거). `onboarded ⟹ login_id`
  CHECK 불변식은 그대로 만족하고, null-state 유저가 발견/목록에서 빠지는 기존 동작(N-055)이 프라이버시 기본값으로 맞다.
  (2026-08-14 보정: 미강제는 유지하되 **본인이 원할 때 만들 길**을 미니앱 안에 뒀다 — 아래 「핸들(@아이디) 만들기」.
  그 전까지 토스-시작 계정은 웹 로그인이 불가해 핸들을 만들 경로 자체가 없었다.)

**PR 로드맵**:
- **PR-0 스파이크** ✅ (2026-08-06~ · 하위 항목 전부 완료, 미니앱은 08-11 출시) — 앱인토스 콘솔 앱 등록 · mTLS 인증서 발급·배치 · 샌드박스에서 **WebView 오리진 실측**(CORS 설정값).
  - **mTLS 인증서 배치** ✅ (2026-08-06) — 발급받은 PEM 2개를 SSM SecureString(`/booktimer/TOSS_MTLS_{CERT,KEY}`)에
    등록하고, `render-env.sh`가 배포마다 `./toss/`에 600으로 렌더 → compose가 `/etc/booktimer/toss`로 읽기전용 마운트.
    Spring이 SSL 번들을 **지연 생성**하는 것을 실측했으므로 렌더 실패가 앱 기동을 막지는 않는다(토스 호출 시점에만 실패).
  - 남은 것: 콘솔 앱 등록 · 샌드박스 WebView 오리진 실측 → `booktimer.miniapp.allowed-origins` 확정(별도 작업).
  ⚠️ 게이트: 여기서 막히면 설계 재소집(커스텀 스킴 등으로 CORS가 성립 안 하면 프록시 등 대안 필요).
- **PR-1 서버: 토스 로그인 + Bearer 인증 기반** ✅ — V61 마이그레이션(`toss_user_key`·`api_token`·`toss_link_code`),
  `TossUserProvisioningService`·`TossLoginClient`(mTLS)·`ApiTokenService`·`TossLinkCodeService`·`BearerTokenFilter`·
  `TossAuthApiController`(login/register/link/logout) + CORS 프로퍼티 외부화.
- **PR-2 연결 코드 발급 UI + 미니앱 온보딩 API** ✅ — 웹 설정의 "토스 앱 연결" 절(**웹 쪽 유일한 신규 UI** — 미연결이면
  발급 버튼, 연결됐으면 상태만. 평문 코드는 저장하지 않으므로 플래시로 한 번만 노출), `POST /api/miniapp/goal`
  (하루 목표만 설정 — `completeOnboarding()`을 부르지 않아 onboarded·login_id 불변).
- **PR-3 프론트 `miniapp/`** ✅ — Vite + React 18 + TDS + `@apps-in-toss/web-framework`로 화면 5개(로그인 브릿지·
  계정 연결·타이머 홈·기록·목표) 구현. `src/api.ts`가 서버 계약 단일 창구(Bearer 헤더·401 재로그인·`registered:false`
  분기). 배포는 앱인토스 CLI(`ait build`/`ait deploy` — 우리 CI 밖, 커맨드는 `miniapp/README.md`).
  ⚠️ **실기기 검증은 PR-0 완료가 전제** — 샌드박스 없이는 `TossAuth.login()` 이후 화면을 확인할 수 없다.
  (2026-08-13 보정: **화면/UI 확인은** `npm --prefix miniapp run dev:mock`으로 브라우저에서 된다 — 목이 서버·SDK를
  대신한다. 실기기가 필요한 건 **SDK 연동 자체**(실로그인·광고·알림 동의)로 좁혀졌다. 샌드박스 핫 리로드는 웹
  미니앱에서 원리상 불가 — T-152.)
- **PR-0 잔여 전부 완료** ✅ (2026-08-10) — 사업자·정산 승인, 토스 로그인 검토 통과, WebView 오리진 실측
  (`https://booktimer.private-web.tossmini.com` → SSM `MINIAPP_ALLOWED_ORIGINS` 배선 #708), Bearer LazyInit 500
  수정(#709, T-142). **실기기 1사이클(로그인→계정연결→대시보드) 통과.**
- **v2 확장** ✅ (2026-08-10 결정 → **2026-08-11 심사 통과·출시**) — 실기기 확인 후 "UI 빈약·웹 기능 미반영" 판정으로 **출시 심사 전 확장**으로 방향 변경.
  범위 = 홈 리치화 · 서재 탭 · 소셜(책방·팔로우·스토리). 설계 정본: [docs/2026-08-10-miniapp-v2-design.md](docs/2026-08-10-miniapp-v2-design.md)
  (PR-4~8 분할 — 서버 변경 0, 미니앱 화면 작업).
  진행: **PR-4 홈 리치화 + `request()` 확장 ✅** · **PR-5 탭 구조(홈·서재·기록) + 서재 탭 ✅** ·
  **PR-6 소셜(책방·팔로우·차단/신고) ✅** · **PR-7 스토리 ✅** · **UI 폴리시 ✅**(실기기 "이질감" 피드백 —
  하단 탭바 자체 구현 교체·표지 썸네일·라이트 캔버스 고정) · **웹 브랜드 테마 ✅**(2026-08-11 — 미니앱을
  웹 종이톤+세이지로 재색칠. TDS 구조는 그대로 두고 색·폰트만 웹 팔레트로) · **PR-8 출시 준비 ✅**(아래 「출시」 항목).
  ⚠️ PR-5 시점엔 탭을 3개로 출하했고(빈 탭 미출하), 소셜은 PR-6에서 화면과 함께 붙여 4탭이 됐다.
  ⚠️ UI 폴리시 실측: TDS `Text`가 호출부의 `display: block`을 `inline-block`으로 덮어써 앱 전역에서 줄이 붙어 보였고
  (전역 CSS 한 줄로 루트픽스), 앱이 배경을 안 칠해 다크 모드 기기에서 본문이 안 보였다(라이트 고정).
  ⚠️ 브랜드 테마 실측: TDS는 색을 `--adaptive*` CSS 변수로 소비하지만 **Button만 예외** — JS 팔레트의 리터럴 hex를
  인라인 커스텀 프로퍼티로 박아 변수 오버라이드가 안 닿는다(버튼만 별도 규칙). 전역 스타일도 TDS가 런타임에 뒤늦게
  주입해 `body`(0-0-1) 동률로는 순서에서 지므로 캔버스·폰트는 `html body`(0-0-2)로 눌러야 한다.
  ⚠️ PR-5 실측 드리프트(설계 §4 PR-5 엣지 정정): 중복 추가는 409가 아니라 **멱등 200**(서버가 기존 책 반환),
  알라딘 검색 실패는 5xx가 아니라 **빈 결과로 격리**(0건과 구분 불가), `/api/**` 에러 본문은 평문이 아니라 **HTML `error` 뷰**.
  - 후속 fix(2026-08-14): PR-4 홈 리치화가 실었던 **작가 격언 카드를 홈에서 제거**(사용자 요청) — 웹 대시보드의
    격언(§작가 격언)은 그대로다. 서버 `/api/dashboard`도 무변경이고 미니앱이 그 필드를 안 받을 뿐.
  - 후속 fix(2026-08-14): **히어로 카드 비우기 + 프로필·설정 최상단**(사용자 결정). 카드 우상단 칩이던 목표 손잡이,
    카드 안에 있던 광고 버튼·지급 문구가 전부 **「남은시간 ⓘ」 확장 상자**로 들어갔고, 카드에는 오늘 읽은 시간·게이지·
    남은시간만 남는다. `AccountSection`은 피드 박스 뒤(스크롤 끝)에서 **카드 위 한 줄**로 올라왔다 — 소셜 기능이
    늘수록 프로필의 무게가 커지는데 자리가 반대로 묻혀 있었다. 곁들여 상자 손잡이를 **항상 펼침 가능**으로 바꿨고
    (조건부로 잠그면 목표·광고에 닿을 길이 함께 사라진다), 밀린 시간이 0이면 그 줄과 안내 문구는 접는다.
    ⚠️ 트레이드오프: 리워드 광고 버튼이 접힌 상자 뒤로 숨어 노출이 준다 — 시청·수익 추이를 지켜본다.
    목표 0이면 게이지 줄 자체가 없어 상자로 갈 길이 없으므로 그때만 카드에 손잡이가 남는다.
    바로 뒤 실기기 제보로 그 손잡이를 **자작 칩 → TDS 버튼**(광고 버튼과 같은 꼴)으로 바꾸고 라벨도
    값(「목표 30분 ›」)에서 동작(「하루 목표 바꾸기」)으로 뒤집었다 — 상자 안에서는 바로 위 「오늘 목표」
    줄이 값을 이미 말하므로, 값을 되풀이하는 대신 눌러서 바꾸는 것임을 말해야 한다.
    같은 제보로 최상단 「프로필·설정」도 `weak` 알약 → **`light` 채움 + 테두리**로 바꿨다(TDS에 outline
    변형이 없다). ⚠️ TDS `Button`은 색을 CSS 변수가 아니라 **인라인 리터럴**로 박아 전역 세이지 재테마가
    안 닿는다 — `light` 채움의 기본 글자색이 브랜드 파랑이라 인라인으로 눌렀고 렌더 가드를 달았다.
    - **후속 fix(2026-08-17): 그 알약을 걷고 인사말 + 아바타 헤더로**(사용자 지적 — 자리가 아쉽다). 알약은
      버튼으로는 잘 읽혔지만 흰 채움 + 테두리라 **화면에서 가장 밝은 것**이 되어, 가장 안 중요한 손잡이가
      첫 시선을 받았다(홈에 제목이 없어 사실상 헤더 노릇을 하고 있었다). 같은 행을 **헤더로** 만든다 —
      왼쪽 `닉네임님 / @아이디`, 오른쪽 38px 아바타 원, **행 전체가 탭 대상**(표적은 오히려 커졌다).
      아바타는 책방 프로필과 **같은 이니셜 원**을 쓴다(`ui.Avatar`로 승격 — 화면마다 색이 다르면 같은 사람이
      다른 사람으로 읽힌다). 겨뤄서 기각한 두 안: **히어로 카드 우상단 아이콘**(46px 회수는 되나 정보 카드에
      컨트롤이 섞이고 「이 카드의 설정」으로 읽힌다) · **책방 탭으로 이관**(책방이 이미 내 프로필 화면이라
      개념은 가장 맞지만, 「책방」이 「내 정보」로 안 읽혀 발견성을 잃는다 — 시안까지 만들어 비교 후 사용자 선택).
      ⚠️ 덤: 기본 닉네임(「토스유저」)인 사람이 매일 자기 이름을 보게 되어 **바꿀 이유가 생긴다**.
  - 후속 fix(2026-08-14): 소셜 아이디 입력 두 곳(검색 · @아이디 만들기)의 placeholder에서 **실계정을 뺀다** —
    「예: goospel」은 운영 어드민 계정이라 예시로 노출할 아이디가 아니다(사용자 제보). 「아이디 입력」으로 교체.
    ⚠️ 앞으로 예시 문구에 실존 계정·이름을 쓰지 않는다.
  - 후속 fix(2026-08-16): **책방 공개 책 격자에 상태 필터 칩**(전체·읽는 중·다 읽음·읽고 싶어요, 기본 전체).
    격자 전환(#813) 때 상태·읽은 시간 메타를 버린 「알고 한 교환」의 비용이 드러난 자리다 — 완독·읽는 중·
    읽고 싶어요가 구분 없이 섞여 보였다(사용자 제보). ⚠️ **「완독만 보여주기」는 기각했다**: 격자 칸이 그 책
    여백으로 가는 **유일한 문**이라, 읽는 중 책을 빼면 거기 남긴 글과 발광이 책방에서 영영 안 보인다 —
    여백의 주 사용처가 「읽다가 남기는 글」이라 자기 발등이다(목 모드 실측에서도 발광 책이 읽는 중 책이었다).
    상단 「공개 책 N」과 격자 권수가 어긋나는 문제, PUBLIC이라는 사용자 의사를 앱이 뒤집는 문제도 있었다.
    웹 완전 이식(배지·정렬)도 기각 — 80px 표지 칸에 배지 자리가 없고(이미 발광 점·선택 테두리가 신호 예산을
    쓴다) 정렬은 앱 수요 미확인. **서버·`api.ts`·`dev-mock`·`BookGrid` 전부 무변경**(셋 다 이미 `status`를
    지원하는데 호출부만 없었다). ⚠️ 어휘는 웹(완독/읽고 싶음)이 아니라 **서재 탭 `SECTIONS`**(다 읽음/읽고
    싶어요)를 재사용한다 — 같은 앱 안의 두 화면이 같은 말을 쓰는 게 우선. ⚠️ 성향 태그 드릴다운과는 **배타**:
    태그 API에 status가 없어 AND는 클라의 한글 라벨 역필터를 강요하는데 그건 `api.ts`가 금지한 패턴이다.
  - 후속 fix(2026-08-16): **긴 제목 옆 표지가 내려앉던 격자 어긋남**(사용자 제보). 격자 기본값 `stretch`가 칸을
    그 줄 최대 높이로 늘리는데 늘어난 `<button>`은 남는 높이만큼 내용을 세로 가운데로 미는 UA 기본 동작이 있어,
    제목이 4줄인 칸 옆의 짧은 제목 표지만 36px 내려갔다 — 격자에 `align-items: start` 한 줄로 닫았다(T-178).
    #820 파란 제목과 같은 「맨 `<button>`의 UA 기본값이 콘텐츠로 샌다」 군의 2회차다. ⚠️ **긴 제목 2줄 자르기는
    보류**: TDS `Text`가 인라인 `display`를 자기 값으로 덮어써 `-webkit-line-clamp`가 죽고, emotion 런타임
    주입이라 클래스 특이도 싸움까지 가야 해서 이득 대비 비용이 안 맞는다(제목은 다 보이는 대신 줄이 벌어진다).
  - 후속 fix(2026-08-21): **입력칸 힌트가 입력값 행세를 하던 자리 — 팔레트의 빈 칸 하나**(사용자 제보 「책 추가에
    예시로 '자바 최적화'가 뜬다」). 위 2026-08-14 placeholder 건의 한 걸음 더다 — 그땐 «어떤 아이디를 예로 드는가»가
    문제였는데, 이번엔 **예시라는 형식 자체와 그 색**이었다. TDS `TextField`는 힌트 색을
    `--text-field-box-placeholder-color: var(--adaptiveGrey500)`로 그리는데 이 앱은 색 토큰을 종이톤으로 갈아끼우며
    **Grey500만 빠뜨렸다**(100·200·600·700·800만 정의). 빈 `var()`는 fallback 없이 선언을 통째로 무효로 만들어 힌트가
    `input`의 색·굵기를 그대로 물려받는다 — 실측 `rgb(44,42,36)`/700, **실제 입력값과 한 글자도 다르지 않았다**.
    고친 자리는 컴포넌트가 아니라 **팔레트 한 줄**(`rgba(44,42,36,.45)`)이라 책방 「아이디 입력」 칸도 함께 나았다.
    화면마다 인라인 스타일을 바르면 다음 입력칸이 또 잉크색으로 태어난다 — 구멍은 컴포넌트가 아니라 팔레트에 있었다.
    ⚠️ 불투명 회색이 아니라 **잉크의 알파**인 이유: 종이 결 위에 회색 판을 덧칠하면 뜬 스티커로 보이고, 같은 잉크를
    옅게 쓰면 결이 글자를 통과해 「연필로 흐리게 적어 둔 안내」가 된다(결을 색이 아니라 알파로 얹은 T-191과 같은 판단).
    굵기는 700 유지 — 개구는 400·700만 있어 400은 획이 무너진다(시안 2안 비교 후 사용자가 A안 채택).
    ⚠️ **앞으로 입력칸 힌트에 예시를 들지 않는다** — 무엇을 적는 자리인지만 말한다. 회귀 가드는 「예:」 부재까지 단언.
    ⚠️ 덤: 이 작업 중 **main이 이 PC에서만 red**였음이 드러나 함께 고쳤다 — `typography.test.tsx`가 넘기던
    `new URL(...).pathname`이 Windows에서 `/C:/…`라 node가 드라이브를 덧붙여 ENOENT로 죽는데, **POSIX에선 유효한
    경로라 우분투 CI도 리눅스에서 도는 클라우드 세션도 영영 초록**이었다(`fileURLToPath`로 교체, T-200).
  - 후속 fix(2026-08-21): **여백 문을 「책 고르는 카드」 안으로 — 고아 버튼이 빈 카드로 읽히던 자리**(사용자 지적
    「여백 버튼이 별로 눈에 안 띄네. 책 고르기와 하나의 흐름으로 보는 게 맞는 것 같다」). 원인은 문구도 크기도 아니라
    **테두리 위계**였다: 이 앱의 `weak` 버튼은 css가 연필 테두리를 얹는데(`global.css` weak 규칙 — 채움 없이 흐린
    연필선), 그 버튼을 연필 테두리 카드들 **사이에 홀로** 두면 버튼이 아니라 **글자 한 줄만 든 빈 카드 세 번째 장**으로
    읽힌다 — 위아래와 테두리가 똑같으니 눈이 그냥 지나간다. 카드 **안**으로 들이면 바깥 진한 선(카드) 안의 흐린
    선(버튼)이 되어 위계가 서고, 「책을 고른다 → 그 책의 여백에 적는다」가 한 흐름으로 읽힌다(`marginTop` 24→16).
    ⚠️ **캐러셀 카드에만 넣으면 측정 중엔 문이 통째로 사라진다** — 측정을 시작하면 그 카드가 「읽는 중」 카드로 갈리는데,
    정작 「읽다가 떠오른 생각」을 적을 순간이 그때다. 그래서 규칙을 **「지금 이 화면이 어느 책을 뜻하는지 말하는 카드
    안에 산다」**로 두고 두 카드 모두에 꽂았다(문 노드는 하나 — 상태는 둘이어도 규칙이 하나라서). `ReadingNowCard`에
    `children` 슬롯 1개가 그 대가.
    ⚠️ **회귀 가드는 순서가 아니라 「포함」을 잰다** — 옛 테스트는 `indexOf(캐러셀헤더) < indexOf(문)`이라 문이 카드
    **밖** 바로 아래 고아로 서 있어도 그대로 통과했다(실제로 그게 옛 배치였다). 되돌아가도 초록인 테스트는 배치를
    지켜 주지 못한다 — `<section>` 한 장을 잘라 그 안을 보는 `card()` 헬퍼로 바꿨고, **측정 중 노출은 마크업
    테스트가 아예 없던 사각**이라 새로 막았다.
    ⚠️ **앞으로 `weak` 버튼을 카드 밖에 홀로 세우지 않는다** — 이 앱에선 그게 「빈 카드」와 같은 그림이다.
    목 모드 실측: 대기·측정 두 상태 모두 카드 안, 첫 진입 덮개 0개(심사 규칙 재현), 코치마크는 카드 안에서도 딤 위로
    부상(문 z=102 > 딤 z=101 — 카드가 스태킹 컨텍스트를 안 만든다는 예측이 실측으로 확인), 문 탭 시 작성 화면 1탭
    직행, 콘솔 에러 0.
  - 후속 fix(2026-08-21): **검색 손잡이를 칸 안으로 — 「책 추가」·「친구 찾기」 공용 `SearchField` 신설**(사용자 지적
    「애초에 보통 사람들이 제목을 치고 키보드의 엔터를 치지 밑의 검색 버튼을 굳이 누르지 않잖아」). 칸 아래 전폭
    「검색」 버튼을 걷고 칸 안 돋보기 아이콘(직접 그린 SVG · 탭 타깃 44×44)으로 옮겼다. 두 화면이 **같은 구조**였어서
    한쪽만 고치면 앱 안에 검색이 두 모양으로 남는다 → 공용 컴포넌트로 꺼내 한 번에 바꿨다.
    ⚠️ **`enterKeyHint`가 이 변경의 절반이다** — form 래핑으로 엔터 제출은 이미 되고 있었는데(2026-08 작업),
    키캡엔 여전히 「완료」라고 적혀 있었다(`enterkeyhint`가 **레포 전체에 0건**이었다). 버튼만 걷고 이걸 안 넣으면
    「눌러도 되는지 모르는 사람」에게는 제출 수단이 통째로 사라진 화면이 된다. 그래서 회귀 가드도 「버튼이 없다」와
    「키캡이 검색이다」를 **따로** 단언한다 — 하나만 봐서는 반쪽 변경이 초록으로 통과한다.
    ⚠️ **정적 렌더 마크업의 속성 철자는 브라우저 DOM과 다르다** — React 18 SSR은 `enterKeyHint="search"`를
    **camelCase 그대로** 뱉고 브라우저가 HTML 파싱에서 소문자로 바꾼다(`enterkeyhint`). 브라우저에서 본 철자로
    마크업을 단언하면 **구현이 맞는데도 빨갛다**(실제로 그렇게 한 번 헛짚었다). 단언은 대소문자 무시로.
    ⚠️ 손잡이를 절대위치로 얹지 않는다 — TDS `TextField`가 `right` 슬롯을 이미 준다(`right?: ReactNode`).
    덤: 버튼이 빠지며 세로 73px이 비고, 그 자리가 다음 작업(추천 카드)의 공간이 된다.
  - 후속 fix(2026-08-21): **「책 추가」 화면에 추천 카드 — 화면의 55%가 빈 종이였다**(사용자 지적 「책 추가하기
    버튼 누르면 이동하는 페이지가 너무 비어보여」. 검색 버튼을 걷기 전 실측 460px). 채우는 데 **새 추천 엔진은
    필요 없었다** — 조각이 이미 다 있었다: 성향 집계기(`ReadingProfileAggregator`) · 저자명 정규화(`WriterName`) ·
    알라딘 저자 검색 · owned 판정. 그래서 이건 추천이라기보다 **「이어 읽기」**다(내가 읽은 책의 저자가 쓴 다른 책).
    서재가 비면 알라딘 베스트셀러(`ItemList`)로 폴백한다. 화면은 **전략을 모른다** — 서버가 `title`·`reason`을
    문장으로 만들어 주고 화면은 그대로 그리므로, 전략이 늘어도 클라이언트는 안 바뀐다.
    ⚠️ **`ReadingProfileService.profileOf()`를 그대로 쓰면 안 된다** — 그건 책BTI용이라 **완독 책만** 센다.
    그대로 썼으면 「책은 담았는데 완독은 아직 0권」인 사람에게 추천이 0건이 됐을 것이고, 하필 그 사람들이 이 화면을
    가장 많이 본다. 집계기 자체는 상태를 안 가리므로(거르는 쪽이 서비스다) READING+FINISHED를 넘겨 **집계기를
    그대로 재사용**했다 — 새 집계 로직 0줄. 담기만 한 책(WANT_TO_READ)은 뺀다: 이유 문구가 「읽으셨네요」라
    거짓말이 된다(데이터와 문구는 같은 것을 가리켜야 한다).
    ⚠️ **협업 필터는 의도적 보류** — 코드는 싸지만(공동 출현 카운트) 모집단이 PUBLIC 책뿐인데 `Book.visibility`
    기본값이 PRIVATE이라 대부분에게 0건이 나온다. 지금 고치려는 빈 화면이 그대로 재발한다.
    ⚠️ **카드 안 행은 배경을 채우지 않는다** — 카드지(`#FCFAF5`)가 이미 명도 위쪽 끝이라 안쪽 행을 채우면
    어두워지는 방향밖에 없는데, 그 방향엔 **이미 「서재에 있어요」 톤(`#EFE9DC`)이 자리를 잡고 있다**. 채운 행은
    「지나간 칸」으로 읽힌다 → 배경 없이 실선으로만 나눈다.
    ⚠️ **목록 높이는 실측 줄 높이로 잡는다** — 눈대중 81px로 잡았더니 마지막 줄이 거의 다 보여 「더 있다」 신호가
    죽었다(실측 75px). `--reco-row`·`--reco-peek` 커스텀 프로퍼티로 「N줄 + 반 줄」을 계산하고, 낮은 화면
    (`max-height: 700px`)은 2.5줄로 줄여 카드가 탭바를 안 넘게 한다. **인라인이 아니라 클래스인 이유는 미디어
    쿼리**이고, 그래서 이 높이는 마크업 테스트가 못 본다 — 실브라우저가 유일한 게이트다.
    ⚠️ **`overscroll-behavior: contain`을 넣지 않는다** — 그게 손가락을 가두는 스위치다. 기본값이면 목록 끝에서
    페이지로 연쇄된다.
    목 모드 실측(390×844 / 375×667): 두 화면 크기 모두 반 줄이 잘려 보이고 카드가 탭바 위에 남는다(여유 70 / 44px),
    카드 안 스크롤 동작, 추천 탭 → 검색 결과와 **같은 시트**(어디에 담을지), 검색하면 추천이 사라지고 결과만 남는다,
    첫 진입 덮개 0개(탭바뿐), 콘솔 에러 0.
  - 후속 fix(2026-08-21): **검색칸이 「안 보이는 라벨」에 27px을 내주고 있었다 — 「책 추가」·「친구 찾기」 여백 정리**
    (사용자 지적 「책 검색바랑 최상단의 책 추가 문구 사이에 공백이 너무 넓어. 검색바의 위 아래 높이도 더 좁아도 돼」).
    실측 63px의 내역: 제목 여백 20 + TDS 기본 위 패딩 16 + **라벨 자리 27**.
    ⚠️ **TDS `TextField`의 라벨은 값이 있을 때만 그린다** — 빈 칸에서는 `visibility: hidden`으로 **자리만 잡는다**.
    사용자가 처음 보는 그 상태에서 27px이 아무것도 안 그린 채 서 있었다는 뜻이다. DOM에는 잉크 색·굵기까지 멀쩡히
    살아 있어서(`rgb(44,42,36)`/700) 마크업만 읽으면 「라벨이 보이는데?」로 오독하기 딱 좋다 — **화면과 DOM이
    갈리는 자리**라 스크린샷을 봐야 갈린다. 라벨을 걷고 이름은 `aria-label`로 옮긴다(**안 그리는 것과 이름이 없는
    것은 다르다** — 같이 안 옮기면 접근성만 조용히 깎인다). 값이 생겨 라벨이 떠도 화면 제목이 이미 「책 추가」라
    보탤 말이 없다. 위 패딩은 TDS가 주는 `paddingTop` prop으로 0(아래 16은 남긴다 — 칸과 카드가 붙으면 한
    덩어리로 읽힌다).
    ⚠️ **칸을 부풀린 건 글자가 아니라 손잡이다** — 입력 글자는 27px인데 칸 안 돋보기가 44px(손가락이 닿는
    최소치)이라 TDS 기본 패딩 14px과 합쳐 73px이 됐다. **손잡이는 그대로 두고 패딩만** 4px로 줄여 53px
    (탭 영역 44px 보존). 손잡이를 줄여 칸을 낮추면 탭 영역이 같이 깎인다.
    ⚠️ **선택자를 emotion 해시에 묶지 않는다** — 이 칸의 클래스(`css-ig959b`)는 TDS를 올리면 조용히 바뀌는데,
    그러면 규칙이 죽는 게 아니라 **안 먹은 채 남아** 칸만 다시 부푼다(에러 0). `.search-field div:has(> input)`로
    구조를 잡는다 — 「input을 직접 품은 div」는 해시가 뭐로 바뀌든 같은 자리다.
    ⚠️ **걷어낸 63px은 목록에 준다** — 안 그러면 위가 좁아진 만큼 카드 밑 빈 자리가 넓어질 뿐이다(실측: 그냥
    두면 탭바까지 여유가 70 → 133px로 벌어진다). 목록 창을 4.5 → 5.5줄(낮은 화면 2.5 → 3.5줄)로 올려 **추천이
    한 줄 더** 보인다. 「위를 줄였으면 그만큼 아래가 늘어난다」는 세로 예산의 기본이고, 이 화면은 애초에
    「비어 보인다」로 시작한 자리라 특히 그렇다.
    목 모드 실측(390×844 / 375×667): 제목↔칸 63 → **20px**, 칸 73 → **53px**, 탭 타깃 **44px 유지**, 보이는 줄
    4 → **5**(낮은 화면 2 → 3), 반 줄 42px 생존, 카드가 탭바 위에 남는다(여유 58 / 31px), 글자를 쳐도 레이아웃이
    안 튄다(라벨 div 자체가 없다), 한 탭 = 검색 1회, 첫 진입 덮개 0개, 콘솔 에러 0. 미니앱 **905건 그린** · tsc 클린.
    「친구 찾기」는 검색칸이 제목 **위**에 서는 구조라(`Screen`의 `above` 슬롯) 벌어져 있던 곳이 제목이 아니라
    **나가는 길과 칸 사이**였다 — 걷어 내는 값은 같고, 두 화면이 같은 `SearchField`를 쓰므로 한 번에 바뀐다.
  - 후속 fix(2026-08-21): **서재의 「책 추가하기」 버튼을 걷고 캐러셀 0번 칸으로 — 홈의 「책 없이」와 같은 부품**
    (사용자 지시 「서재에서 책 추가 버튼을 없애고 홈의 책 없이 측정 디자인을 차용하자」). 버튼은 여백 박스
    **아래 스크롤 끝**에 있어 책을 훑다가 닿기까지 멀었고, 「가운데 온 것이 곧 대상」이라는 캐러셀 문법 밖의
    갈래였다. `BookCarousel`의 `noBookCard: boolean`을 **`leadCard: LeadCard | null`로 일반화**해(문구만 갈아
    끼운다) 두 화면이 **0번은 언제나 특수 칸**이라는 한 문장으로 통일된다. 칸 위치는 **맨 앞(B안)** —
    「맨 뒤」도 검토했으나(내 책이 먼저다) 규약을 화면마다 가르지 않는 쪽을 사용자가 택했다.
    ⚠️ **`null`이 이미 다른 뜻으로 쓰이고 있었다** — `resolveSelected(rows, null)`은 「첫 책으로 떨어져라」다.
    그대로 두면 칸을 고르는 순간 첫 책으로 튕긴다. 홈이 먼저 푼 구분을 그대로 가져와 `selectedId`를
    **세 값**(`id` · `null`=칸을 고름 · `undefined`=아직 안 고름)으로 넓혔다. 탭을 옮기면 `undefined`로 되돌린다.
    ⚠️ **홈의 0번과 뜻이 다르다** — 홈 0번은 측정 대상의 한 갈래(그 자체가 값)인데 서재 0번은 **검색 화면으로
    가는 문**이다. 그래서 이 칸이 가운데면 아래가 통째로 갈린다(손잡이 「검색해서 담기」 하나 · 여백 박스 숨김).
    안 갈면 **다른 책의 여백을 이 칸의 것으로 읽는다**.
    ⚠️ **버튼을 지우면 코치마크가 대상을 잃는다** — 「게이트와 안내는 한 몸」이 `AddBookButton`의 설계였다.
    `Coachmark`에 `enabled`를 더해 그 성질을 유지한 채 캐러셀로 옮겼다(플래그가 꺼지면 칸도 안내도 함께 없다).
    ⚠️ **검색이 꺼진 날의 폴백은 지우지 않는다** — 0권 + 검색 꺼짐이면 캐러셀에 세울 것이 하나도 없어, 옛
    「아직 책이 없어요」 문장이 남지 않으면 **빈 화면**이 된다. 테스트로 못 박았다.
    목 모드 실측(390×844): 0번이 점선 칸(84×118), 진입 시 **첫 책**이 가운데, 「책 추가하기」 0개, 칸 선택 시
    제목·부제·손잡이 전환 확인, 빈 탭은 「읽고 싶은 책이 없어요」 + 카드 한 장 캐러셀(실제로 삭제해 확인),
    첫 진입 덮개 **1개(탭바뿐)**, 콘솔 에러 0. 미니앱 **936건 그린** · tsc 클린.
    ⚠️ **남은 흠 — 칸이 가운데일 때 아래가 364px(화면의 43%) 빈다.** 「책 추가하기 페이지가 비어 보인다」로
    추천 카드를 깔았던 것과 **같은 클래스**다. 「출렁임 -103px」이라는 측정이 이걸 가렸다 — 콘텐츠가 짧아진
    값이지 **화면이 비는 값이 아니다**(화면 높이는 고정이라 그만큼 빈 자리가 는다). 지나가는 상태이고 한 탭
    뒤의 「책 추가」 화면이 이미 추천 카드로 차 있어 **중복을 피해 그대로 두기로 사용자와 합의**했다. 채우려면
    `RecommendCard`를 칸이 가운데 왔을 때만 지연 fetch로 붙이면 된다.
  - 후속 fix(2026-08-21): **추천 줄 하나가 카드를 통째로 먹었다 — 줄 높이가 「가정」일 뿐이었다**
    (실기기 제보: 「노벨라33 세트 - 전33권」의 저자 필드에 이름이 40개 넘게 들어와 한 줄이 세로 **약 900px**로 자랐다).
    ⚠️ **원인은 세트가 아니라 무제한 줄 높이다.** 카드 창은 `calc(5 × --reco-row + --reco-peek)` = 「75px짜리 줄
    5개 + 반 줄」인데 **줄 자체엔 제한이 없었다** — 줄 하나가 창보다 커지는 순간 그 계산도, 반 줄이 말하던
    「더 있다」 신호도 함께 죽는다. 세트를 걸러도 **제목·저자가 긴 다음 책이 그대로 재발**한다.
    세 방향(필터 / 축약 / 줄 고정)을 시안으로 비교해 **셋 다** 넣었다 — 축약이 읽기 좋게, 줄 고정이 다시는 못
    터지게, 필터가 애초에 안 오게.
    ⚠️ **저자 축약을 `author`에 하면 DB가 오염된다** — 「담기」가 검색 행의 `author`를 그대로 되돌려 **저장**하므로
    (miniapp `addBook`), 축약본을 그 자리에 넣으면 책 저자가 「미겔 데 세르반떼스 외 32명」으로 **영구 저장**된다.
    표시용 `authorShort`를 **따로** 두고 원문은 손대지 않는다. 회귀 가드를 테스트로 박았다.
    ⚠️ **세트 필터의 진짜 비용은 오탐이다** — 걸러진 책은 검색에서 사라져 **가진 책을 담을 길이 막힌다**. 그래서
    ① 판정을 좁게(「전N권」은 숫자를 **요구**해 『셜록 홈즈 전집』을 통과시킨다) ② **검색어가 세트를 찾으면 아예
    적용하지 않는다**(유일한 탈출구). 판정은 `BookSetTitle` 한 클래스에 몰아 튜닝 지점을 하나로 뒀다.
    필터는 `BookService.search()` **한 곳**이면 된다 — 검색 API와 추천이 둘 다 그 길목을 지난다.
    ⚠️ **자르기를 `-webkit-box` line-clamp로 하지 않는다** — TDS `Text`가 인라인 `display`를 덮어써 죽는다
    (서재 격자 제목이 그래서 두 줄 자르기를 포기했다). `display:block` + `nowrap + ellipsis`가 이 레포에서
    세 번 검증된 방식이다(둘러보기·기록·홈).
    ⚠️ **목 픽스처가 전부 얌전해서 이 버그가 목 모드 전 경로 검증을 통과했다** — 데이터가 깨뜨리는 버그는
    데이터가 얌전하면 원리상 안 잡힌다. `dev-mock`의 추천 첫 줄을 **일부러 비정상값**(아주 긴 제목·저자)으로 박아 뒀다.
    목 모드 실측(390×844): 줄 높이 **74/75px 균일**(옛 900px), `--reco-row: 75px`가 이제 가정이 아니라 사실,
    창 415px = 5×75+40, 안쪽 스크롤 522>415, 반 줄 생존, 제목이 실제로 말줄임(`scrollW 387 > clientW 269`),
    검색 결과 행도 같은 고침 적용, 콘솔 에러 0. 서버 + 미니앱 **940건 그린** · tsc 클린.
  - 후속 fix(2026-08-23): **흐린 글자가 하나도 흐리지 않았다 — `<Text color="grey600">` 87곳이 통째로 죽어 있었다**
    (직전 PR이 「TDS `Text` 21개 전부 `rgb(0,0,0)`」을 후속 과제로 넘긴 자리의 결말).
    ⚠️ **그 `rgb(0,0,0)`은 관측 아티팩트였다** — 독서등 전환 규칙의 `transition: … color 0.45s`가 **페이지 로드 때**
    발화하는데(정적 css가 잉크를 안 잡아 첫 계산은 UA 기본 검정 → 마운트 때 TDS가 `body{color:#212529}` 주입 →
    색이 바뀌어 트랜지션 시작) 백그라운드 탭이라 렌더가 스로틀돼 **시작값에 얼어붙은** 것이다. 스크린샷으로 페인트를
    강제하면 전부 `rgb(33,37,41)`로 풀리고 운영 번들도 같았다(중간에 잰 `rgb(23,26,28)` = 보간 중간값이 결정적 물증).
    T-207 · T-196 재발 2회차.
    ⚠️ **그 밑에 진짜가 있었다.** TDS `Text`의 `color`는 **CSS 색 문자열**인데 이 앱은 **토큰 이름**을 넘겼다 —
    무효값이라 선언이 통째로 버려지고 색이 **상속으로 떨어진다**(폴백 `var(--adaptiveGrey900)`도 낮 모드엔 미정의라
    같은 결말). 실측상 `color="grey600"`인 Text와 prop 없는 Text가 **한 값**이었다. 흐림·강조 위계가 통째로 없었다.
    고친 자리는 호출부 87곳이 아니라 **`ui.tsx`의 `Text` 래퍼 하나**(토큰 이름 → `var(--adaptive*, #hex)`)와
    **`html body`의 잉크 한 줄**이다 — 화면 12개는 import 경로만 바뀐다(팔레트 구멍이던 위 Grey500 건과 같은 판단).
    잉크 한 줄이 덤으로 둘을 더 닫는다: 낮 모드 본문이 TDS의 `#212529`가 아니라 브랜드 `#2C2A24`가 되고,
    로드마다 있던 0.45초 검정→잉크 페이드가 **애초에 발화하지 않는다**(= 계측 신뢰도 회복).
    fallback을 반드시 함께 주는 이유: `--adaptiveRed500`처럼 이 앱이 아예 정의 안 하는 토큰이 있어, 없으면 다시 상속이다.
    회귀 가드는 **화면이 TDS `Text`를 직접 import 하지 않는다**는 소스 단언이다 — 래퍼는 길이 하나일 때만 가드다.
- **출시** ✅ (2026-08-11) — 심사·출시가 하루에 두 사이클 돌았다. ① v2 번들 `20260811-6` 심사 승인 → 13:14 정식 출시
  (스토어 스크린샷은 파란 테마 4장). ② 리워드 광고+세이지 테마 번들 `20260811-8` 심사 승인(20:30 메일) → 출시,
  스토어 스크린샷도 세이지 세트로 교체해 앱 정보 재승인(21:19 메일). 콘솔 작업은 PR 밖(사용자 직접)이라 이 문서 갱신이
  뒤따라온 것. 스크린샷 원본은 `BookTimer-captures/toss-console/store-v3/`(버전별 폴더 규칙은 그 폴더 README).
  ~~**남은 확인**: 정식 진입점의 WebView 오리진~~ → **해소 ✅ (2026-08-15)** — 실측한 정식 오리진
  `booktimer.web.tossmini.com`을 SSM `MINIAPP_ALLOWED_ORIGINS`에 추가했다. **인수 시나리오 = 웹 PC ↔ 토스 모바일
  교차**(웹에서 시작한 측정이 미니앱에, 미니앱 측정이 웹 잔디에)는 실기기에서 계속 유효.
- **미니앱 주소 이전 — 토스가 도메인을 옮긴다** ✅ 선반영 (2026-08-21) — 콘솔 공지로 알았다: **8/25부터
  `web`·`private-web` → `apps`·`private-apps`**. 우리 CORS는 `setAllowedOrigins`라 **문자 그대로 일치**해야 해서
  그냥 뒀으면 그날 미니앱의 모든 API 호출이 403이 됐다. 공지 당일 운영에 프리플라이트를 던져 실측했다 — 새 오리진
  **403 Forbidden**, 옛 오리진 200. **코드 변경은 0**: SSM `MINIAPP_ALLOWED_ORIGINS`를 **신·구 4개 병기**(v3)로
  올리고 재배포하니 넷 다 200, 통제군(`evil.example.com`)은 403 유지 = 전면 개방이 아님도 확인. 값만 바꾸면 안 먹는다 —
  `.env`는 배포 때 `deploy/render-env.sh`가 SSM에서 다시 렌더하므로 **SSM 수정 + 재배포가 한 세트**다.
  옛 주소를 남긴 건 지금 서빙이 아직 `web`이라서다 — **양쪽이 열려 있어 전환 순간에 끊기는 구간이 없다.**
  코드 전체에 미니앱 주소가 박힌 곳이 0이라 폭발 반경은 CORS 하나였다(딥링크·템플릿 grep 확인).
  ⚠️ **오리진은 채널마다 다를 뿐 아니라 토스가 통째로 옮기기도 한다** — 콘솔 공지 배너를 흘려보내면 그날 앱이 죽는다.
  ⬜ **8/25 이후 옛 2개(`web`·`private-web`) 정리** — 지금 지우면 당장 깨지니 전환을 눈으로 확인한 뒤에.
- **스토어 스크린샷 재촬영 — 6장이 한꺼번에 낡았다** ✅ (2026-08-21, 출시 직전 점검) — 08-20 촬영분 이후
  **타이포 스케일 개편**이 들어가 글자 크기·강조가 **전 화면에서** 바뀌었다. 여기에 여백 문 이사(`01-home`)와
  「책 추가」 점선 칸 신설(`03-library`)이 얹혔다. `shoot.mjs`로 6장 재촬영.
  - **바이트째 그대로인 컷이 하나도 없다 — 처음이다.** 옛 재촬영은 늘 「안 바뀐 화면」이 증거로 남았는데
    (08-18은 `03-library`·`05-goal`, 08-20은 `01-home`·`05-goal`), 이번엔 6장 전부 바뀌었다. **한 PR이 전 화면을
    낡게 만들 수 있다**는 뜻이라, 앞으로 타이포·팔레트처럼 전역을 건드리는 변경은 그 자체로 재촬영 트리거다.
  - **촬영기는 안 고쳤다** — 「책 추가하기」 버튼을 걷은 변경이 `shoot.mjs`를 깨뜨릴 자리였지만, 이 스크립트는
    그 버튼을 누른 적이 없고(서재는 **상태 탭을 기준선**으로 잡는다) 셀렉터가 깨지면 조용히 넘어가지 않고
    **죽게** 돼 있다 — 6장 전부 정상 촬영으로 그 설계가 값을 했다.
  - ⚠️ **「출시 직전 점검」이 또 낡음을 잡았다(5회째).** 화면을 바꾼 PR에서 sweep 하는 규율은 다섯 번 다 안 먹혔다.
  - **출시노트도 함께 썼다**(콘솔 붙여넣기용, PR 밖) — 범위는 직전 출시분 `01a0159c`(08-19) 이후 미니앱 PR 20건.
    「심사 참고」 두 줄(탭바 무변경 · 사용법 안내는 수동 시작)을 붙였다 — 08-17·08-18 반려가 정확히 그 두 항목이었다.
- **리워드 광고(IAA) — "밀린 하루 지우개"** ✅ 출시 (2026-08-11 설계·구현·출시 — 하루에 종결) — 광고 시청 1회로 빠뜨린 날 하나의 부채를 0 처리.
  **용서 장치와 수익화를 한 기능으로 겸한다** — thesis의 알려진 미비점(부채가 밀리면 죄책감 → 이탈)을 자발적 시청 포맷으로
  푼다. 진입점은 홈 부채 문구 옆 버튼 1개(부채가 없으면 광고의 존재 자체가 안 보인다 — 입문자 압박 0).
  설계 정본: [docs/2026-08-11-reward-ad-iaa-design.md](docs/2026-08-11-reward-ad-iaa-design.md).
  진행: **PR-A 서버(V62 + 부채 반영 + 지급 API) ✅** · **PR-B 미니앱 클라(SDK 배선 + 홈 버튼) ✅** — 실기기 5항목
  검증을 통과한 뒤 리워드 광고를 실은 번들 `20260811-8`이 심사 **당일 승인**(20:30)돼 그날 저녁 **정식 출시**됐다.
  ⚠️ **SSV(서버사이드 보상 검증) 부재를 실측**했다 — 클라 콜백은 신뢰 경계 밖이고, 방어는 피해를 캡하는 방식이다.
  ~~하루 1회~~ → **일일 1회 상한은 2026-08-14 폐지**(V67로 `uk_goal_waiver_grant` 제거): 부채 7일 자동 소멸을 함께
  폐지해 빚이 계속 누적되므로 갚을 수단도 계속 열려 있어야 한다. 상한 역할은 이제 **부채 자체**가 든다 — 지울 과거
  날이 없으면 400이고 오늘 몫은 애초에 대상이 아니라, 무한히 시청해도 "이미 밀린 날들을 지우는 것"을 넘어설 수 없다.
  `(user, waived_date)` 유니크는 유지(같은 날 중복 소거·동시 요청 race 방어). 먹이·잔디·스트릭은 세션에서 별도
  유도되므로 광고로 파밍 불가.
  ✅ PR-B 머지 게이트였던 **SDK 이벤트 순서**(`userEarnedReward` ↔ `dismissed`)는 실기기에서 통과했다 — 문서에 순서
  보장이 없어 래퍼를 **두 순서 모두에서 같은 답이 나오는 플래그 방식**으로 짜 뒀고, 중간 이탈 무보상까지 5항목을 확인했다.
  SDK 시그니처·이벤트 타입은 `node_modules`의 `.d.ts`로 실측(설계의 문서 기반 가정과 일치).
  ✅ **운영 점등 완료** — 콘솔 광고 그룹 등록 후 실 ID `ait.v2.live.b6bbeff2c57e4777`를 `VITE_REWARD_AD_GROUP_ID`로
  주입해 재빌드·배포했고, 지금은 `miniapp/.env.production`에 커밋돼 있다(T-148). 미설정 빌드는 config-gate로 버튼이
  안 보여 안전하다.
  ⚠️ **이 그룹은 2026-08-23부터 홈 전용이다** — 책방 「책 성향 분석」이 같은 그룹을 쓰다가 자기 그룹
  `ait.v2.live.75558b51a0444cc7`으로 갈라져 나갔다(아래 「독서 성향(책BTI) 추출」 절). **그래서 콘솔에서 이 행의 과거
  수치는 두 지면이 합쳐진 값이고, 분리 배포 시점에 의미가 바뀐다** — 노출이 줄어 보여도 감소가 아니라 성향 분석 몫이
  빠져나간 것이다. 리워드 포맷의 광고 그룹은 이제 둘이다.
  🔜 **남은 것은 코드가 아니라 세무다** — 첫 정산 후 홈택스에서 현금영수증을 자체 발행한다(간이과세자 의무).
  **2개월 미발급이면 광고 송출이 중단**된다. 광고 수익 5,000원 이하는 익월로 이월.

- **전면 광고(IAA) — 「목표 바꾸기」 진입** ✅ 코드 (2026-08-14) — 리워드에 이은 두 번째 광고 지면. 홈 우상단
  목표 손잡이를 눌러 목표 수정 화면으로 들어가는 **화면 전환 순간**에 전면 광고 1장(콘솔 광고 그룹
  `ait.v2.live.32f30171507d4dd1`, 리워드와 **다른 그룹**이라 성과·정산이 분리된다).
  자리를 여기로 고른 이유: 목표 수정은 **드물고·짧고·중단돼도 잃을 게 없는** 진입이라 타이머가 도는 핵심 동선
  (측정 시작·종료)을 건드리지 않는다(웹 AdSense의 「핵심 동선엔 광고 0」 원칙과 같은 정신).
  구현은 `showInterstitialAd()`를 `App.onGoGoal`에서 **await한다 — 광고가 끝난 뒤에 화면을 전환**(2026-08-14
  실기기 실측 후 정정). ~~await하지 않는다~~던 초기 배선은 화면을 먼저 바꿔 버려서, 1~2초 뒤 로드가 끝난
  광고가 목표 화면이 아니라 **그때 떠 있는 아무 화면 위에** 덮였다(사용자 실측: 목표 화면을 지나 메인으로
  돌아온 뒤 노출) — 노출 시점이 로드 속도에 좌우되는 경주 상태였다. 대신 래퍼가 **절대 reject하지 않게** 해
  진입이 막히는 사각을 닫았다: 그룹 미설정·노 필·SDK 부재·무응답(5초 하드 타임아웃) 전부 조용히 resolve.
  기다리는 1~2초는 손잡이를 「준비 중…」 + 비활성으로 바꿔 알린다(겉보기 무반응이면 재탭 → 광고 2장).
  ⏸ 사전 로드(preload)는 보류 — 탭 즉시 노출이 되지만 "로드해 놓고 안 쓴 광고"의 노출 지표·유효기간이
  문서에 없다. 대기가 실측으로 거슬리면 그때 승격(코드에 `ponytail:` 주석으로 경로 표시).
  **신규 계정 첫 목표 설정엔 안 붙는다** — 그 경로는 로그인 브릿지에서 곧장 `load('goal')`이라 `onGoGoal`을
  거치지 않는다(첫 인상이 광고가 되지 않게. 별도 분기 없이 배선 자체가 게이트).
  빈도 제한은 두지 않았다 — 목표 변경 자체가 드문 진입이라. 실측으로 거슬리면 그때 쿨다운.
  ⚠️ 콘솔 그룹이 「구글 반영 중」이라 반영 전까지는 로드 실패로 조용히 안 뜬다(정상 경로).

- **배너 광고(IAA) — 「여백」 두 화면** ✅ 코드 · ✅ 점등 (2026-08-25) — 세 번째 광고 포맷. 지면은 **둘**이다:
  사람축 `MarginView`(내 여백·남의 여백)와 책축 `BookMarginAllView`(「모두의 여백」 — 탭·검색 진입).
  각각 게시판 상자 **바깥 바로 위**(`ErrorMessage`와 `MarginBoard` 사이)에 한 장. 자리를 여기로 고른 이유는
  전면 광고와 같다 — 여백은 **읽고 나서 머무는** 화면이라 타이머가 도는 핵심 동선을 안 건드린다.
  **광고 그룹은 지면마다 따로**(`VITE_MARGIN_BANNER_AD_GROUP_ID` · `VITE_BOOK_MARGIN_BANNER_AD_GROUP_ID`) —
  콘솔 리포트의 단위가 광고 그룹이라 합치면 어느 자리가 버는지 영영 못 가른다(리워드 2그룹 분리와 같은 이유).
  옵션은 `theme:'light'`(앱이 크림색 라이트 고정이라 `auto`면 다크 기기에서 검은 배너가 뜬다) ·
  `tone:'grey'` · `variant:'card'`(게시판과 같은 「둥근 카드」 문법). 폭은 문서 요구대로 화면 너비 —
  `Screen`의 좌우 20px을 **`margin: '0 -10px'`**로 되돌린다. ⚠️ **-20px이 아니다**: `variant:'card'`일 때
  SDK가 배너 래퍼에 `padding: '0 10px'`를 하드코딩하므로(`dist/index.js` 872행 실측) -10px이라야
  SDK 10 + 마진 10 = 20px로 게시판과 같은 열에 선다. **`variant`를 바꾸면 이 값도 함께 바꾼다.**
  **96px을 예약하고 죽으면 0으로 접는다**: 인라인이면 광고가 뜨는 성공 경로마다 게시판이 밀리고(layout shift),
  항상 예약이면 노 필인 날 빈 구멍이 남는다. 접기는 트랜지션 없이 즉시(높이 애니메이션은 레이아웃 유발 — T-176).
  실패는 **5겹**이 전부 「자리 없음」으로 수렴한다 — 그룹 미설정·`window` 부재·`isSupported` false/동기 throw는
  렌더 전 `null`, 그 뒤 `onNoFill`·`onAdFailedToRender`·초기화 실패는 96px→0, 그리고 **아무 콜백도 안 오는
  브릿지 침묵**은 렌더 상한 5초(`BANNER_RENDER_TIMEOUT_MS`)가 받는다 — SDK 번들 전체에서 `setTimeout`이
  스크립트 로더 하나뿐이라 광고 요청 자체엔 상한이 없다(실측). 전면 광고와 같은 패턴·같은 값이다.
  거꾸로 **`onAdRendered`는 자리를 되살린다** — 슬롯이 `autoLoad`로 계속 갱신하는데 접힌 채 두면 나중에
  채워진 광고가 `height:0` 뒤에서 렌더돼 **안 보이는 노출만 집계**된다(무효 트래픽 축).
  SDK 초기화는 `toss.ts`의 **모듈 레벨 memoized Promise**라 두 지면이 공유해도 1회이고, 여백을 열 때만 돈다
  (App 최상위에 두면 여백을 안 여는 대부분의 세션에서도 광고 SDK가 깨어난다).
  ✅ **점등 완료 (2026-08-25)** — 콘솔 그룹 2개(「내가쓴여백_배너」 `ait.v2.live.5a5ce4d4d4654fce` ·
  「모두의여백_배너」 `ait.v2.live.673c1d77a6f9451e`, 둘 다 **배너 종류 「문구 강조」**)를 만들어
  `.env.production` 실값 + `env-production.test.ts` 단언 2건으로 잠갔다. ⚠️ **「이미지 강조」로 바꾸면
  큰 이미지 카드가 와서 96px 컨테이너에 안 맞는다** — 콘솔만 바꿔선 안 되고 컨테이너를 인라인 높이로
  고치는 코드 변경이 함께 필요하다. 두 그룹의 미디에이션 설정은 같다(폭포식 자동 구성 + 하이브리드 최적화 ON) —
  다르면 AdMob이 다른 광고 풀을 물어와 eCPM 차이가 지면 차이인지 카테고리 차이인지 못 가린다.
  ⚠️ **5차 배포(`01a037b6`)에는 빈 값이 구워져 있다** — 그 번들에서는 코드가 있어도 config-gate로 꺼져 있으므로,
  실제 점등은 이 값을 담은 **재배포부터**다. card 인셋 정합은 실기기 항목이었다가 **SDK 소스 실측으로 앞당겨
  닫았다**(위 -10px). ✅ **원장 종료 — A6 충족 (2026-08-25 사용자 실기기 확인)**: 「내가 쓴 여백 ↔ 모두의 여백」
  탭 왕복에서 **유령 슬롯·중복 배너·이중 구멍 없음**. 배너가 실제로 그려지는 것을 사람 눈으로 본 첫 자리이기도
  하다(목 모드엔 광고가 원리상 없어 이 항목만 끝까지 남았다). **같은 날 심사 제출** — 결과 대기 중.
  ⚠️ **한동안 기록이 엇갈렸다** — 6차 배포 changelog는 「남은 것은 A6 하나」인데 자동 메모리는 「확인 완료」였다.
  실기기 확인처럼 **저장소 밖에서 끝나는 항목**은 결과가 돌아올 통로가 없어, 아무도 틀리지 않았는데도 두 기록이
  갈린다. 처방은 **끝난 자리를 물어서 닫는 것**뿐이다(2026-08-25 마커 감사 계열).

- **핸들(@아이디) 만들기** ✅ (2026-08-14) — 토스로 가입한 계정(`login_id=null`)이 **미니앱 안에서 스스로 공개 핸들을**
  만든다. 그 전까지 이 계정들은 소셜 전 경로(검색·친구추천·팔로우 목록·스토리 피드·프로필)의 `login_id is not null`
  필터에 걸려 **영구히 발견 불가**였고, 소셜 탭 안내문은 "웹에서 아이디를 정하라"고 했지만 **토스-시작 계정은 비밀번호가
  없어 웹 로그인 자체가 불가능**한 죽은 안내였다(운영 6명 해당).
  서버는 `POST /api/miniapp/handle` + `OnboardingService.assignHandle` 신설 — `complete()`에서 닉네임·하루 목표를 뺀 것으로,
  핸들 확정 후 `completeOnboarding()`까지 부른다(CHECK가 `onboarded ⟹ login_id` 방향이라 순서상 안전. 목표는 미니앱이
  이미 `setDailyGoal`로 정했으므로 여기서 다시 기록하면 목표 이력이 중복된다). 응답은 400(형식·예약어)/409(중복·이미 있음)
  **한국어 평문** — 미니앱이 에러 본문을 그대로 띄우는 기존 계약을 그대로 쓴다. SecurityConfig·노출 필터·검색·웹 온보딩은
  **무변경**(Bearer + `/api/**`면 미니앱 체인이 자동으로 맡는다).
  미니앱은 소셜 탭의 죽은 안내를 **배너 + [아이디 만들기] → 자체 `Sheet`**로 교체했다. 형식(3~20자 `[a-z0-9_]`)만
  순수 함수 `validateHandleFormat`으로 프리검증하고 **예약어·중복은 서버가 유일한 권위**(클라가 규칙을 복제하면 서버와
  어긋난 순간 조용히 거짓말한다). 성공하면 서버가 정규화한 소문자 핸들을 그대로 반영해 배너가 즉시 「내 책방 보기」로 바뀌고
  대시보드를 다시 받는다.
  **비목표(의도)**: 닉네임 변경 UI(검색은 loginId만 본다) · 기존 6명 마이그레이션(핸들은 본인만 지을 수 있다) ·
  핸들 변경·해제(once-set 불변 유지) · 강제 온보딩(불변 값을 첫 진입에서 강요하지 않는다).
  ⚠️ 동시 중복 경합은 사전 확인을 둘 다 통과할 수 있어 최종 방어가 DB 유니크(500)다 — 웹 가입과 같은 기존 트레이드오프라
  추가 대응 안 함(관측되면 `DataIntegrityViolation` → 409 매핑).
  ⚠️ 배포 순서 = **서버 먼저**(엔드포인트 없이 미니앱이 나가면 404) → 미니앱 업로드. 소셜 탭 배너라 스토어 스크린샷
  4장(홈·서재·기록·목표)엔 영향 없다.

- **프로필·설정 화면 + 닉네임 변경** ✅ (2026-08-14) — 토스로 가입한 계정(운영 6명)은 닉네임이 전원 기본값
  **"토스유저"**인데 바꿀 길이 없었다: 변경 경로가 웹 `/settings` 하나뿐인데 그 계정들은 비밀번호가 없어 **웹 로그인
  자체가 불가능**하다. 위 핸들 만들기가 「발견」을 열었다면 이번은 발견된 뒤의 「표시」다(검색 결과가 `@bookworm_1 토스유저` /
  `@nabi 토스유저`처럼 전원 같은 이름으로 뜨던 문제).
  서버는 **`POST /api/miniapp/nickname` 하나만** 신설했다 — 기존 미니앱 계약(단일 목적 POST + 한국어 평문 에러)과
  동형이고, 하루 목표는 `/api/miniapp/goal`, 핸들은 `/api/miniapp/handle`을 그대로 재사용한다. 부분 갱신 PATCH는
  기각(null=지우기인가 안 보냄인가가 버그 농장이고, 묶을 필드도 하나뿐).
  **닉네임 규칙을 도메인으로 단일화**했다 — `User.NICKNAME_MAX_LENGTH = 30` 상수 + `updateProfile` 길이 검사 신설,
  웹 `SettingsForm`·`OnboardingForm`의 `@Size`가 그 상수를 참조한다. 그전엔 설정 30 / 온보딩 50 / 도메인 무제한으로
  **웹 안에서도 이미 어긋나 있어**, 40자로 온보딩한 계정은 설정에서 영영 닉네임을 못 바꿨다(온보딩 허용 폭 50→30 축소는
  사용자 승인. 기존 값은 `updateProfile`이 새 값만 검사해 그대로 산다).
  미니앱 진입점은 **`AccountSection` 교체** — 그 자리의 "계정 관리·상세 설정은 booktimer.app에서"가 바로 그
  죽은 안내였다(당시엔 홈 하단. 2026-08-14에 히어로 카드 위 최상단으로 올라갔다 — 위 후속 fix). **탭바는 한 줄도 안 건드렸다**(플로팅 탭바는 2026-08-12 심사 반려 대응 산물 — 5칸 확장은 재심사 노출면).
  설정 화면에 든 것은 닉네임 · @아이디(없으면 기존 `HandleSheet` 재사용 — *2026-08-15부터는 있으면 평생 1회
  「바꾸기」, 이미 썼으면 소진 표기*. 위 「아이디 평생 1회 변경」 절) · 하루 목표 진입 ·
  로그아웃 2단 확인 넷이다 — **미니앱 채널에서 실제로 작동하는 것이 이 넷 전부**라서다.
  **비목표(의도)**: 타임존(전원 `Asia/Seoul` — 잘못 바꾸면 자정 경계가 깨진다) · 이메일 인증/마케팅 수신(토스 계정 email은
  발송하지 않는 synthetic 자리표시) · 비밀번호 변경(없다) · 토스 연결 코드(미니앱 안에서는 이미 그 신원) · 회원 탈퇴 ·
  프로필 사진 · 빚 이월 토글(아래 백로그).
  ⚠️ 배포 순서 = **서버 먼저**(엔드포인트 없이 미니앱이 나가면 404) → 미니앱 업로드. 홈 **하단** footer 교체라 스토어
  스크린샷 4장에 잡힐 가능성은 낮지만, 심사 제출 전 `miniapp/screenshots/01-home`이 하단까지 담고 있는지 확인할 것.

  **남는 백로그(이 작업 아님)**:
  - ✅ **미니앱 전용 계정 탈퇴 경로 + 개인정보 처리방침** — 해소됨(2026-08-14, 바로 아래 항목).
  - ⏸ 프로필 사진(도감 작가) 선택 — 미니앱이 `profileCharacterCode`를 **어디서도 렌더하지 않는다**(타입·픽스처뿐).
    아바타 렌더가 먼저 생겨야 의미가 있고, 그때 owned-characters GET + select POST와 함께.
  - ⏸ 빚 이월 토글(`debtCarryover`) — 표시 전용이고 기본값으로 충분. 요청이 생기면 엔드포인트 하나.

- **미니앱 회원 탈퇴 + 개인정보 처리방침** ✅ (2026-08-14) — 위 백로그의 ⚠️ 항목을 닫았다. 미니앱 전용(토스로 시작한)
  계정은 **탈퇴 경로가 0개**였다: 웹 `/settings/delete`는 비밀번호가 없어 로그인조차 못 하고, 소셜 탈퇴의 @핸들 재입력은
  `login_id`가 null일 수 있어 확인 수단 자체가 성립하지 않는다. 처리방침·약관에 닿을 길도 없었다(웹 푸터가 유일했다).
  *(2026-08-15 후속: **웹** 소셜 계정 쪽은 핸들이 없으면 **이메일 재입력**으로 확인하도록 열었다 — 온보딩 전 구글 가입자가
  `/settings`에서 「@null을 입력하세요」를 보고 탈퇴할 수 없던 것. 미니앱 전용 계정은 애초에 웹 로그인이 불가하므로
  이 절의 결론(토스 재인증)은 그대로다.)*
  **확인 수단 = 토스 재인증**이다 — `tossLogin()`으로 받은 fresh 인가코드를 서버가 `TossLoginClient.fetchUserInfo`로
  userKey로 바꿔 본인 `toss_user_key`와 대조하고, **일치할 때만** 지운다. 핸들 유무와 무관하게 전 계정에서 성립하고
  (미니앱에 온 사람은 정의상 그 키가 있다), 토큰을 훔친 쪽은 토스 앱 본인 인증을 통과할 수 없어 **웹의 확인 수단보다 오히려
  강하다**. 닉네임 재입력·확인 문구 타이핑은 신원 증명이 0이라 기각.
  **삭제 로직은 새로 짜지 않았다** — 웹이 운영 중인 `AccountService.purge`(FK 순서대로 16개 리포지토리)를 그대로 부르고,
  신규 코드는 확인 게이트 하나뿐인 `deleteTossVerifiedAccount`다. `purge`가 `api_token`까지 지우므로 **탈퇴 즉시 미니앱
  토큰이 전부 무효화**된다(추가 작업 없음).
  ⚠️ **이 엔드포인트만 401을 쓰지 않는다** — `api.ts request()`는 401을 받으면 토큰을 버리고 로그인 화면으로 튕기므로,
  그 계약을 쓰면 **인가코드 만료가 로그아웃으로 둔갑**해 탈퇴하려던 사용자가 대신 로그아웃당한다. 토스 인증 실패=**400**,
  userKey 불일치=**403**(둘 다 한국어 평문). 클라이언트도 대칭으로 **성공했을 때만** 토큰을 버린다(로그아웃과 반대 방향).
  **토스 연결 계정(웹 계정에 link한 사용자)도 막지 않는다** — 웹 탈퇴도 대칭적으로 미니앱 기록까지 지우는 같은 User라
  "반쪽 탈퇴"는 데이터 모델에 존재하지 않는다. 숨기지 않고 시트에 상시 고지한다("웹(booktimer.app)에서 쓰던 계정이라면
  웹 기록까지 모두 삭제됩니다"). 서버 분기·provider 노출 필드는 만들지 않았다.
  처리방침·약관은 `openExternal`(=`Device.openURL`, 실패 시 `window.open` 폴백)로 기기 브라우저에 넘긴다 — `/privacy`·
  `/terms`는 `permitAll`이라 로그인 불필요하고, 인앱 렌더는 같은 문서를 두 곳에 두는 일(drift 필연)이라 기각.
  **비목표**: 웹 탈퇴 UI/로직 변경 · 소프트 삭제·유예 기간·데이터 내보내기(웹도 즉시 하드 삭제 — 채널 간 일관성) ·
  탭바 변경(심사 반려 대응 산물). SecurityConfig·Flyway 무변경(`/api/**` + Bearer면 미니앱 체인이 자동).
  ⚠️ 배포 순서 = **서버 먼저**(엔드포인트 없이 미니앱이 나가면 404) → 미니앱 업로드.

  - ⬜ **탈퇴 성공 경로 실기기 확인** (2026-08-21 이후 · 출시 후) — **아직 아무도 실행해 본 적이 없는 유일한 구간**이다.
    확인 수단이 토스 SDK라 목 모드로는 첫 관문(`tossLogin()`)부터 막히고(T-163), 자동 테스트는 서버 로직까지만 덮는다.
    미검증 구간은 「SDK 재인증 → 서버 호출」 배선 하나.
    **방법(잃을 것이 0인 경로)**: 미니앱 진입 → "새로 시작"으로 **신규 토스 계정 생성** → 아무것도 쌓지 않은 그 계정으로
    바로 탈퇴 → **재진입 시 `registered:false`(새로 시작 화면)** 확인. 탈퇴해도 재가입이 막히지 않으므로 같은 토스
    계정으로 다시 만들 수 있고, 「지워졌다」는 증거가 같은 흐름에서 나온다. 검증 보조로 `/admin/users` 총원 대조.
    ⚠️ **다른 사용자의 계정으로 하지 않는다** — 재접속이 뜸한 계정이라도 본인 데이터고, 동의 없는 삭제는 설명할 수 없는
    행위다(하물며 이번에 붙인 것이 개인정보 처리방침이다). 2026-08-14 논의에서 "재접속률 최저 사용자로 테스트" 안이
    나왔다가 이 이유로 기각됐다.
    급하지 않은 이유: 배선이 틀렸을 때의 실패가 **안전한 방향**이다(에러 후 미삭제). 게이트가 userKey를 대조하므로
    엉뚱한 계정이 지워지는 실패는 구조적으로 불가능하다(돌연변이로 실측).

- **홈 UX 정정** ✅ (2026-08-11) — 실사용 피드백 2건. ① **책 고르기를 웹식 「칩 + 바꾸기」로** — 이전엔 책 버튼 탭이
  곧 측정 시작이라 여러 책을 번갈아 읽는 사용자가 시작 전에 책을 바꿀 수 없었다. 웹 `BookPickForm`의 의미론을 옮겨 홈엔
  고른 책 칩 하나(이니셜 상자 + 제목 + 「바꾸기」)만 두고, 「바꾸기」로 칩 아래 목록을 **인라인으로** 편다(시트·모달은
  화면 다섯 개짜리 앱에 과하다). 초기 칩은 이어 읽기 기본값 `defaultBookId`(최근 읽은 책 → 없으면 첫 책 → 0권이면 null),
  시작은 「측정 시작」·탈출구는 「책 없이 시작」. 웹 `books/pure.ts`의 `initialOf`·`coverColor`를 이식해 같은 책이 웹·미니앱에서
  같은 색이 되고, 고르기 목록과 종료 후 태깅 목록은 `BookList` 하나로 합쳤다. ② **홈 잔디가 카드 폭을 채운다** —
  `GrassGrid`에 `fill` 모드(주 컬럼 `flex:1` + 칸 `aspect-ratio` 정사각)를 더해 5주 × 14px 고정 → 15주 채움. 기록 화면은
  가로 스크롤이 전제라 무변경.
  ⚠️ 하니스 사각지대(실측): 정적 렌더라 `onClick`이 마크업에 안 남아 「바꾸기가 목록을 편다·고르면 접힌다·주 버튼이 고른
  책으로 시작한다」를 직접 계측할 수 없다 — 접힌 상태는 홈 마크업, 편 목록은 `BookList` 직접 렌더로 나눠 잡고 나머지는
  실기기·프리뷰 확인을 게이트로 둔다.

- **완독 축하 푸시** ✅ 코드 완료 / ✅ **점등 완료** (2026-08-11 코드 · 2026-08-12 점등) — 사용자가 책을 **완독으로 전환하는 순간** 토스 앱의
  네이티브 푸시·알림함으로 축하 한 통. 앱인토스 메신저 API(`POST /api-partner/v1/apps-in-toss/messenger/send-message`,
  헤더 `x-toss-user-key`) — 로그인과 **같은 mTLS 인증서·같은 호스트**라 새 인프라 0, 이벤트 기반이라 **스케줄러도 없다**.
  ⚠️ **위 「웹 푸시 제거(2026-07-09)」와 모순되지 않는다** — 그건 브라우저 Web Push(VAPID·SW·iOS 제약)를 걷어낸 것이고,
  이건 **토스 앱이 자기 채널로 대신 쏴 주는 서버 발송**이다. 웹 전용 사용자(`toss_user_key` null)는 대상이 아니다.
  **범위 밖(의도)**: 데일리 독서 리마인더 — 동의 UI·크론이 필요해 미뤘다(하게 되면 발송 시각은 "사용자 설정 시각" 방식).
  → ⚠️ **이 문구는 2026-08-18에 절반만 유효해졌다**: 선결이던 동의 UI·크론은 그 뒤 둘 다 생겼고, 그 위에서 아래
  「재참여 넛지 푸시」(7일 비활동)가 나왔다. 다만 **데일리 리마인더 자체는 여전히 범위 밖**이며 이유가 바뀌었다 —
  선결 부재가 아니라 **아래 목표 달성 푸시와 합치면 사실상 전원 매일 1푸시**가 되기 때문이다(스팸 체감).
  **설계 결정**: ① 등록-시-완독(검색에서 곧장 FINISHED로 담기)은 **제외** — 과거에 읽은 책을 아카이빙할 때 푸시가
  쏟아진다. ② 중복 방지 기준은 `finishedAt`이 아니라 **전이**(직전 상태 ≠ FINISHED). 재완독 시 다시 축하는 허용
  (빈도 낮고 무해 — 발송 이력 테이블을 만들지 않는다). ③ 발송은 **절대 예외를 던지지 않는다** — 축하가 완독 처리를
  깨면 본말전도. 타임아웃 연결 2s/응답 3s.
  ✅ **점등 완료 (2026-08-12)**: 앱인토스는 **콘솔 문구 검수 승인 전 발송을 거부**(에러 5004)하므로 2중 게이트
  (`messenger.enabled` 기본 OFF + 템플릿 코드 미설정)로 다크런치 머지했고, 콘솔 승인 후 SSM을 실값으로 갱신했다 —
  **SSM 실측(2026-08-18)**: `TOSS_MESSENGER_ENABLED=true` · `TOSS_FINISH_TEMPLATE_CODE=booktimer-finish-celebration`
  (v1 `placeholder` → v2 실값, 파라미터 이력에 그대로 남아 있다 — 이 `placeholder`/`false` 초기값이 **다크런치 선생성 규약**이다).
  ⚠️ 열린 리스크: 검수에서 축하를 **광고형으로 분류**하면 클라 SDK `Notification.requestAgreement`(동의 UI)가
  후속으로 필요하다 — 이번 범위 밖. → **현실화됨**: 아래 「알림 동의 UI」 참고.

- **알림 동의 UI (미니앱)** ✅ 코드 완료 / ✅ **점등 완료** (2026-08-12) — 콘솔이 푸시 캠페인 2종(완독 축하 ·
  하루 목표 달성)을 **"알림동의문에 동의한 유저에게만 발송 가능"**으로 판정해, 위 리스크가 그대로 현실이 됐다.
  동의를 받는 주체는 **미니앱**이다 — SDK `Notification.requestAgreement`(3.0.2에 이미 있음, 토스앱 5.255.0+)를
  부르면 토스가 동의 화면을 띄우고 결과를 준다. **동의 상태의 정본은 토스**라 서버·DB 변경이 0이다.
  **설계 결정**: ① 진입점 = 홈 상단 카드 1장(설정 화면이 미니앱에 없고, 알림의 가치가 타이머 홈과 직결).
  ② 두 캠페인이 **같은 동의문 한 장**을 쓰므로 `booktimer-daily-goal-met` 하나만 호출 — 동의 단위는 캠페인이
  아니라 동의문이다(점등 검증에서 완독 쪽 수신으로 이 가정을 실측한다). ③ 결과를 localStorage
  (`booktimer.notificationAgreement`)에 캐시해 카드 노출을 끈다 — **거절도 캐시**(다시 조르지 않는다).
  미지원 토스앱이면 카드를 아예 안 띄운다.
  ✅ **점등 완료 (2026-08-12)**: 미니앱 배포 후 실기기에서 카드 노출 → 동의 → 카드 사라짐 → 푸시 2종 실수신까지
  확인했다. **동의문 한 장이 캠페인 2개를 함께 덮는다**는 설계 가정(②)이 이때 실측으로 확인된 셈이다 — 다만 그것이
  *앞으로 추가될* 캠페인까지 덮는지는 별개 문제다(아래 「재참여 넛지 푸시」의 열린 리스크 ①).

- **오늘 목표 달성 푸시** ✅ 코드 완료 / ✅ **점등·실기기 수신 확인 완료** (2026-08-11 코드) — **읽는 도중** 오늘 누적이 하루 목표를 넘는
  **그 순간** 토스 앱으로 한 통. 사용자가 진짜 원한 알람은 이것이다 — 남은 시간을 보려면 앱을 계속 들여다봐야 하는데
  미니앱은 진입 경로가 길다. 완독 축하가 남긴 `TossMessengerClient`를 그대로 재사용해 신규는 감지 로직뿐이다.
  **왜 폴링인가**: 달성 순간엔 사용자가 아무 행동도 안 해 요청 훅이 없고 토스 API엔 예약 발송이 없다 →
  `GoalMetPushScheduler`가 **분마다**(fixedDelay 60s) 활성 세션만 훑는다. 지연 상한 ≈ 1분(합의된 "분 단위 오차").
  **설계 결정**: ① 오늘 누적 = 오늘 시작해 끝난 세션 합 + 지금 도는 세션의 경과 — **세션은 `startedAt`의 날짜에 귀속**
  (`ReadingHistoryService`와 같은 규칙)이라 어제 시작해 아직 도는 세션은 오늘에 안 들어간다(자정 직후 오발송 차단).
  ② 하루 1회 멱등은 `users.goal_met_pushed_on`(유저 TZ 날짜, V63) — **발송 성공 시에만** 마킹해 실패는 다음 틱 재시도.
  ③ 목표 0·토스 미연동은 대상 제외(스팸 방지). ④ 타이머를 **끄면서** 목표를 넘긴 경우는 안 보낸다 — 그 순간엔 앱을
  보고 있다. ⑤ per-user try/catch 격리(한 명 실패가 배치를 안 멈춤).
  ✅ **점등 완료 (2026-08-12)**: `messenger.goal-met-enabled` 기본 OFF(스케줄러 빈 자체가 미등록) + 템플릿 코드
  미설정 2중 게이트로 다크런치한 뒤, 콘솔 승인 후 실값으로 갱신했다 — **SSM 실측(2026-08-18)**:
  `TOSS_GOAL_MET_ENABLED=true`(v1 `false` → v2 `true`) · `TOSS_GOAL_MET_TEMPLATE_CODE=booktimer-daily-goal-met`.
  ⚠️ 열린 리스크: ① 완독 축하와 동일한 광고형 분류 리스크. ② 분당 폴링이 유저 증가로 무거워지면 per-user 트랜잭션
  분리로 승격(코드에 `ponytail:` 주석). ③ 멀티 인스턴스가 되면 스케줄러 중복 실행 — `RetentionNudgeScheduler`와 같은
  기존 한계라 새로 만들지 않았다.

- **재참여 넛지 푸시 (7일 비활동 win-back)** ✅ 코드 완료 / ✅ **점등 완료** (2026-08-18 코드 · **08-25 사용자 확인**) — **§전략 「retention
  레버 ①」의 채널 우회**다. 그 레버는 채널을 이메일로 확정한 뒤 SES 프로덕션 액세스 2차 거부로 2026-06-24부터 잠겨
  있었는데, 여기 푸시 인프라가 다 깔린 지금 **토스 연동 사용자에게만 먼저 보낸다**(웹 전용은 이메일 대기 유지).
  본질은 새 기능이 아니라 **이미 있는 자산을 채널 하나로 잇는 것** — 대상 선정·멱등·(광고) 규약은 이메일 넛지
  (`RetentionNudgeService`)가, 발송은 위 두 푸시가 쓰던 `TossMessengerClient`가 이미 하고 있었다.
  **설계 결정**: ① **트리거 = 7일 비활동, 이탈 구간당 1통** — 2026-06-06 합의였던 「그날 세션 0 → 저녁 1통」은
  기각했다(위 목표 달성 푸시와 합치면 전원 매일 1푸시 = 스팸 체감·동의 철회. 그 합의는 축하 푸시가 없던 시절
  전제였다). ② **멱등은 이메일 넛지와 같은 `last_nudge_sent_at`을 공유** — 그 공유가 곧 채널 간 중복 발송 방지라
  **Flyway 0건**이고, 이메일이 훗날 점등돼도 10:00(이메일) → 19:00(푸시) 순서라 같은 날은 이메일이 선점한다.
  ③ **채널 추상화를 만들지 않았다** — 공통부는 "조회·루프·마킹"뿐이고 대상 조건도 페이로드도 달라, 인터페이스가
  절약하는 코드보다 만드는 코드가 많다(토스 발송 소비자 3개가 각자 얇게 클라이언트를 쓰는 기존 구조에 맞춤).
  ④ 발송 KST 19:00 — 저녁은 실제로 읽으러 갈 수 있는 시간이고, §50 야간(21~08시) 제한 앞이라 자연 회피된다.
  ⑤ **알림 동의를 서버가 조건에 넣지 않는다** — 동의 정본은 토스다. 미동의면 토스가 거부해 `sendMessage=false`가
  되고, 실패는 마킹을 안 하므로 비활동인 동안 매일 재시도된다(현 규모에선 로그 소음뿐 — 코드에 `ponytail:` 주석).
  신규는 `retention/TossRetentionNudge{Service,Scheduler}` 2파일(~75줄) + 대상 쿼리 `findTossNudgeTargets` 1개뿐.
  TDD 16건 RED→GREEN(쿼리 6·서비스 6·게이트 4), 돌연변이 15건 사살, 전체 1692건 그린.
  ✅ **점등 완료 (2026-08-18, 코드 머지 당일)**: 다크런치 2중 게이트로 머지 → SSM 선생성 → 콘솔 등록·승인 →
  SSM 실값 갱신·재배포까지 하루에 끝냈다. **검증**: EC2 `.env`에 `BOOKTIMER_TOSS_RETENTION_ENABLED=true` ·
  `..._TEMPLATE_CODE=booktimer-retention-nudge`가 실제로 박힌 것을 `ssm send-command`로 확인했다(SSM 값이
  앱까지 도달했는지는 `.env`를 봐야 안다 — SSM만 바꾸고 재배포를 안 하면 앱은 옛 값을 계속 쓴다).
  ⚠️ **SSM 선생성이 머지 전 필수였다**: `render-env.sh` SECRET_MAP에 키가 있는데 SSM에 없으면 배포가 `exit 1`로
  죽는다(누락을 침묵시키지 않는 의도적 설계). #722가 같은 이유로 `placeholder`/`false`를 선생성했고, 이번에도
  그 규약을 그대로 따라 **머지 배포가 무사히 통과**했다.
  📍 **콘솔 경로 (이번에 처음 기록 — 없어서 헤맸다)**: 앱인토스 콘솔 → 「유입·성장」 → **스마트 발송**
  (`/workspace/69821/mini-app/booktimer/smart-message`). 탭이 **광고성 / 기능성 / 알림동의문** 셋으로 갈리고,
  기존 2종과 이번 넛지 모두 **기능성**에 있다. 캠페인 생성 시 **발송 방법 = 「직접 API로 발송하기」**(우리 서버가
  부르는 방식 — 「토스에게 발송 요청하기」가 아니다).
  ⚠️ **콘솔의 제약 3가지**: ① **푸시 제목 7자 제한**(현재 문구 「책을 읽읍시다」) ② **발송 코드에 `booktimer-`
  접두가 자동으로 붙는다** — 콘솔엔 뒤 조각만 넣고 SSM엔 전체(`booktimer-retention-nudge`)를 넣는다
  ③ **변수(`{{ }}`)를 쓰면 안 된다** — 이 캠페인은 서버가 빈 컨텍스트(`Map.of()`)로 보내므로 채울 값이 없다
  (완독 축하는 `{{ bookTitle }}`을 쓰므로 캠페인마다 다르다).
  ✅ **열린 리스크 ①②는 해소됐다**: 콘솔이 **저장 시 AI로 내용을 검토해 알림동의문 필요 여부를 자동 판정**하는데,
  이 문구는 동의문 요구 없이 **즉시 「승인됨」**이 됐다(검수 대기조차 없었다 — 리마인더성이라 반려될까 걱정했으나
  기능성으로 통과). 다만 **미동의 사용자에게 실제로 닿는지는 별개**라, 첫 발송이 나가야 최종 확인된다.
  🔜 **남은 검증**: 7일 비활동 계정을 인위로 만들 수 없어 경계는 H2 테스트가 맡고, 실발송은 **첫 19시 배치 로그**
  (`토스 재참여 넛지 배치 완료 — N명 발송`) + 자연 발생 시 실기기 수신으로 사후 확인한다. 미니앱 출시가
  2026-08-17이라 **초기 몇 번은 「0명 발송」이 정상**이다(아직 7일 비활동자가 쌓일 시간이 없었다).
  📝 **관찰 — 콘솔에 「하루 목표 달성 푸시」가 2개 등록돼 있다**(캠페인명·소재 동일). 서버는
  `booktimer-daily-goal-met` 하나만 부르므로 나머지는 쓰이지 않는 잔재로 보인다. 당장 해는 없으나 정리 대상.

- **스마트 발송 현황 — 우리는 이미 절반을 쓰고 있었다** ✅ 기능성 3종 운영 중 / ✅ **광고성 신규 유입 발송 중**
  (2026-08-25) — 「스마트발송을 도입하자」에서 출발했는데 **조사해 보니 절반은 2026-08부터 운영 중**이었다.
  다음에 또 「우리한테 없는 기능」으로 오해하지 않도록 현황을 한자리에 박는다.

  | 탭 | 우리 상태 | 코드 |
  |---|---|---|
  | **기능성** — 직접 API로 발송하기 | ✅ 캠페인 3종 운영 중(완독 축하 · 하루 목표 달성 · 재참여 넛지) | `TossMessengerClient` + 각 서비스 |
  | **기능성** — 토스에게 발송 요청하기 | ❌ 불필요 | 우리 서버 배치가 같은 일을 한다 |
  | **광고성** — 신규 유입 유도 | ✅ **2026-08-25 등록 · 발송 예정** | **0줄**(콘솔 등록만) |
  | **광고성** — 재방문 유도 | ✅ 등록 / ⏸ **꺼짐**(의도적) | 우리 7일 비활동 넛지와 대상이 겹친다 |

  ⚠️ **광고성의 값어치는 「우리가 코드로 원리상 못 하는 일」에 있다** — 대상이 **미니앱을 안 써 본 토스 사용자**라,
  우리 DB에 없는 사람에게 닿는 유일한 채널이다(우리는 연동한 사용자만 안다). 미니앱 출시가 2026-08-17이라
  밀도가 낮은 지금이 제 시점이다. AI가 pCTR로 고관여자를, 사용 패턴으로 발송 시각을 고른다.

  📍 **등록한 소재 (2026-08-25 실제 등록값)**

  | 그룹 | | 제목 | 본문 | 각도 |
  |---|---|---|---|---|
  | 신규 유입 | a | `독서 습관` | `토스에서 타이머로 시작해봐요.` | 도구로 시작 |
  | 신규 유입 | b | `하루 목표` | `토스에서 독서 목표를 세워봐요.` | 목표 세우기 |
  | 재방문(꺼짐) | a | `오늘 독서` | `토스에서 타이머를 다시 켜봐요.` | 습관 복귀 |
  | 재방문(꺼짐) | b | `읽던 책` | `토스에서 이어서 읽어봐요.` | 남겨둔 것 |

  ⚠️ **재방문은 등록 후 자동으로 「꺼짐」이다 — 이게 중복 문제를 저절로 풀었다.** 폼이 「각 섹션에 소재 1개 이상」을
  요구해 재방문도 채울 수밖에 없었는데, 등록 결과 목록에서 신규 유입만 「발송 예정」이고 재방문 2건은 「꺼짐 +
  발송 시작 버튼」으로 앉았다. 덕분에 **`BOOKTIMER_TOSS_RETENTION_ENABLED`를 끌 필요가 없어졌다**(우리 7일 비활동
  넛지가 그대로 유일한 재참여 채널이다). 🔜 **9월 말에 판단한다** — 수수료 면제 종료(아래)와 함께 콘솔 리포트를 보고,
  광고성 재방문을 켤 거면 그때 서버 배치를 끈다.

  📍 **콘솔 실측 — 문서에 없어서 헤맨 것들 (2026-08-25 등록하며 확인)**
  - **앱이름은 `booktimer`**. 「클릭 시 이동할 화면 URL」은 자유 입력이 아니라 **라디오 2개**(홈 화면 / 특정 화면)이고,
    「특정 화면」을 고르면 **`intoss://booktimer/` 접두가 고정으로 붙고 뒤만** 입력한다. 신규 유입은 어차피
    로그인·온보딩이 먼저라 **홈 화면**으로 뒀다.
  - **방해 금지 21~08시** — 그 시간엔 발송되지 않고 다음 날 08시로 밀린다.
  - **수수료: 9월 30일까지 면제(건당 0원)**, 이후 별도 고지 후 변동. 테스트 발송 구간을 면제 기간에 태우는 게 이득이고,
    **10월부터 건당 과금이 붙을 수 있으니 9월 말에 리포트를 한 번 봐야 한다**.
  - **필수 항목 2개가 문서에 없었다**: ① 신규 유입·재방문 **각 섹션에 소재 최소 1개** ② 각 섹션에 **발송 대상 「포함할
    유저」 조건 최소 1개**(「제외할 유저」는 안 채워도 통과). 조건은 4갈래(거래정보 / 유저정보 / 유저활동 / 유저프로파일)이고,
    **모수를 안 깎으려면 `유저정보 → 나이 → 한국 나이 → 19~99`**(슬라이더 최대치가 곧 19~99다)로 필수 칸만 채운다.
    관심사·성별로 좁히고 싶은 유혹을 참는 게 핵심 — 스마트 발송은 **AI가 고관여자를 골라 주는** 도구라, 우리가 미리
    좁히면 AI가 고를 풀만 줄고 테스트 발송 조건(클릭 25건 또는 발송 2,500건) 충족도 늦어진다.
  - **저장 시 AI가 문구를 자동 심사하고 반려 사유를 준다**(기능성과 같은 방식). 4건 중 **2건이 반려**됐고 사유는 둘 다
    같은 뿌리였다 — **「본문만 떼어 놓고 읽어도 무엇을 하는지 알 수 있어야 한다」**. `토스에서 할 수 있어요.`는
    「범용적 문구」로, `독서 기록 다시 해봐요.`는 「표현이 어색하고 행동이 불명확」으로 걸렸다. 제목과 합쳐야 뜻이
    완성되는 문장은 통과하지 못한다.
  - ⚠️ **소재 템플릿과 AI 대안 문구는 참고용이지 정답이 아니다.** 템플릿(「토스에서 클릭률이 높았던」 5탭 — 서비스 소개 /
    업데이트형 / 혜택 / 정보성 콘텐츠 / 리텐션)을 그대로 따른 `토스에서 할 수 있어요.`가 **반려됐고**, 반려 후 콘솔이
    제안한 대안은 ① 신규 유입 쪽이 **제목·본문이 a안과 거의 같아** A/B 테스트를 무의미하게 만들었고 ② 재방문 쪽
    `독서 시간 다시 정해봐요.`는 **목표 재설정으로 읽혀 제품 동작과 어긋났다**(돌아온 사람이 할 일은 목표 재설정이
    아니라 다시 읽기다). 문장은 다듬어 주지만 제품은 모른다.
  - ✅ **다만 템플릿에서 건진 것 하나**: 클릭률 높은 문구는 **본문에 「토스에서/토스로」를 쓴다**(서비스 소개 4/4,
    업데이트형 5/7). 가이드가 막는 건 「토스 + 서비스명」을 붙여 쓰는 것(`토스 북타이머`)이지 본문의 「토스에서」가
    아니었다 — 받는 사람은 그게 어느 앱 얘긴지 모르므로, **「설치도 가입도 없이 여기서 된다」는 신호**가 클릭 장벽을
    직접 낮춘다. 등록한 4건 전부 이 패턴을 따랐다.

  ⚠️ **광고성은 기능성과 달리 알림 동의문이 없다** — 그래서 사용자가 스팸으로 체감할 여지가 크다.
  그 대신 **기존 사용자에게는 아무 푸시도 더 가지 않는다**(대상이 미사용자다).

- **딥링크 착지 탭 (`?tab=`)** ✅ 코드 완료 · **원장 종료(실기기 충족)** / 🔜 출시 후 콘솔 URL 반영 (2026-08-25) — 푸시를 누르고 들어온 사람이
  홈이 아니라 **그 푸시가 말한 화면**에 서게 한다. `initialTab(search)`이 `?tab=history` 꼴을 `TabKey`로 옮기고,
  **모르는 값은 전부 홈**이다(콘솔에 손으로 적는 URL이라 오타·옛 링크가 온다 — 그때 필요한 건 오류가 아니라 홈).

  ⚠️ **신규 유입 캠페인에는 쓸모가 없다** — 대상이 처음 여는 사람이라 어느 탭을 지정하든 로그인·온보딩이 먼저다.
  실수요는 **이미 돌고 있는 기능성 푸시 3종**의 랜딩이다(완독 축하 → 서재, 목표 달성 → 기록, 재참여 → 홈).

  🧾 **미검증 가정 원장**
  - `CHECK:` 목 모드에서 `?tab=history`·`?tab=library`·`?utm_source=toss&tab=nope`·파라미터 없음으로 진입 /
    `EXPECT:` 각각 「내 기록」·「내 서재」·홈·홈 / `EVIDENCE:` ✅ 4종 전부 일치(2026-08-25, `localhost:5300` 실측.
    스크린샷 없음 — 브라우저 패널이 화면에 없어 합성이 안 됐다. 판정은 `document.body.innerText` 선두 문자열).
  - `CHECK:` 콘솔이 화면 주소를 자유 입력으로 받는가 / `EXPECT:` 임의 경로·쿼리를 적을 수 있다 /
    `EVIDENCE:` ✅ **충족**(2026-08-25 콘솔 실측) — 「클릭 시 이동할 화면 URL」에서 **「특정 화면」**을 고르면
    **`intoss://booktimer/` 접두가 고정으로 붙고 뒤를 자유 입력**한다. **앱이름은 `booktimer`로 확정**됐고,
    콘솔이 목록에서 고르게 하는 방식이 아니므로 **우리 `?tab=` 규약을 그대로 쓸 수 있다**.
  - `CHECK:` 실기기에서 쿼리를 얹어 진입 / `EXPECT:` 기록 탭이 첫 화면 / `EVIDENCE:` ✅ **충족 — 사용자가 폰에서
    「기록 탭 떴어」로 확인**(2026-08-25, 배포 `01a037b6`). **토스는 딥링크의 쿼리를 우리 SPA의 `location.search`까지
    그대로 넘긴다.**
    📍 **출시를 안 태우고 확인한 방법(재사용할 것)**: `deploy.sh`가 배포 끝에 뱉는 **테스트 스킴이 그 자체로 증거이자
    도구였다** — `intoss-private://booktimer?_deploymentId=<id>&host=appsInTossHost` 형태라 **쿼리를 넘긴다는 사실이
    거기 이미 박혀 있었고**, 뒤에 `&tab=history`를 덧붙여 열자 기록 탭이 떴다. 즉 **심사·출시 없이 그날 올린 번들로
    딥링크·URL 파라미터를 실기기 검증할 수 있다**(샌드박스 앱 필요). 「실기기 확인 = 출시 후」라는 전제가 이 클래스에는
    틀렸다.
  - 🔜 **남은 것은 코드가 아니라 콘솔이고, 순서가 있다**: 라이브 번들에 이 코드가 있어야 `?tab=`이 읽힌다.
    **출시 전에 콘솔 이동 URL을 바꾸면 라이브 사용자에겐 그냥 홈이 뜬다**(무해하지만 무효). 그래서 **출시 → 그다음
    콘솔 URL** 순서를 지킨다. 대상은 **기능성 푸시 3종**이다(광고성 신규 유입은 어차피 로그인·온보딩이 먼저라
    **홈 화면**으로 등록했다):

    | 캠페인 | 이동 URL | 왜 |
    |---|---|---|
    | 완독 축하 | `intoss://booktimer/?tab=library` | 방금 다 읽은 책이 있는 곳 |
    | 하루 목표 달성 | `intoss://booktimer/?tab=history` | 오늘 얼마나 읽었는지 보는 곳 |
    | 재참여 넛지 | (홈 — 파라미터 없음) | 돌아온 사람이 할 일은 새 측정 시작이다 |

- **심사 반려 대응 — 진입 인트로 + 플로팅 탭바** ✅ (2026-08-12 코드 · 재심사 승인) — 토스 심사가 두 가지로
  반려했다. ① **"서비스 설명 없이 즉시 토스 로그인을 유도"** — `LoginBridge`가 마운트 즉시 `appLogin()`을 불러
  첫 화면이 곧 인가 화면이었다. `intro` phase를 초기 상태로 두고(자동 로그인 effect 제거) 서비스 소개 3줄 + 「토스로
  시작하기」를 먼저 보여준 뒤, **버튼을 눌러야** 인가가 시작되게 했다. 토큰 보유 사용자는 인트로를 보지 않는다(App
  라우팅 무변경). ② **"탭바는 토스 브랜딩 가이드의 플로팅 형태로"** — 전폭 부착(상단 1px 보더) → 좌우 16px 띄운
  알약(`border-radius:28px` + 그림자, 하단 `calc(12px + safe-area)`)으로 스타일만 교체하고 `TABS`·`role=tablist`·
  접근성 계약은 불변. 홈 인디케이터 회피는 `padding-bottom`이 아니라 띄운 높이가 맡는다(둘 다 두면 safe-area 이중
  가산). 서버 무접점 — 미니앱 파일 2개.
  ✅ **재심사 승인**(2026-08-12, 번들 `20260812-12`): `bash miniapp/deploy.sh --expect "토스로 시작하기" --expect "borderRadius:28"` → 재심사 통과.

- **배포 검증 게이트 `deploy.sh`** ✅ (2026-08-12) — 위 반려 대응 배포가 **스테일 번들로 나가 동일 사유 재반려**된 뒤
  만든 하드픽스. `npm run build`가 완료 로그 없이 조용히 끝나도 exit 0이라 체인이 계속됐고, `dist/`에 남은 직전 릴리스
  산출물이 그대로 패키징·업로드됐다 — 배포 전 확인이 운영 env 값만 봤는데 **그 값은 직전 번들에도 있어 신·구를 구별하지
  못했다**(T-150). 이제 배포 표준 경로는 `miniapp/deploy.sh` 하나다: 클린 빌드(`rm -rf dist`) → dist 검증(참조 js 실존 ·
  `booktimer.app` 있음 · `localhost:8080` 없음 · 각 `--expect` 마커 포함) → `ait build` → **.ait 속 js가 dist의 js와
  바이트 동일**(패키징 스테일 차단) → 배포 → deploymentId. 어느 검증이든 실패하면 배포 전에 exit 1.
  원칙 두 가지: **빌드 성공은 exit code가 아니라 산출물 내용으로 판정**하고, **마커는 직전 번들과 구별되는 것**이어야 한다.
  게이트 자체는 `miniapp/tests/test-deploy-gate.sh`(npm/npx 스텁 · 6케이스 19단언 · 돌연변이 6종 사살)가 지킨다.

- **UI/UX 마감 1차** ✅ 코드 완료 / ✅ 출시 (2026-08-12 코드 · 2026-08-17 출시) — 출시 후 실기기에서 드러난 "웹을 축소한 티"를
  한 브랜치로 걷어냈다. 서버 무접점 · 미니앱 화면 작업. ① **버그 3건** — 스토리 뷰어 위로 플로팅 탭바가 뚫고 올라오던
  z-index, 서재 삭제 확인이 다른 행으로 옮겨도 살아남던 잔류(오삭제가 한 탭 거리 — 열림+확인을 한 덩어리로 묶어 해소),
  `fetch`의 영문 `TypeError` 노출(`NetworkError`로 한글 문구 부여). ② **홈 카운트업 리프레이밍** — "오늘 남은 시간"
  카운트다운 → 웹에서 검증된 "오늘 읽은 시간" 카운트업(성취를 세지 빚을 세지 않는다), 밀린 시간은 용서 문구로 강등,
  책 0권 빈 상태에 「첫 책 추가하기」 CTA. ③ **앱감 기초 3종** — 안드로이드 back이 서브뷰를 하나씩 닫는 히스토리
  스택(`back.ts`), 화면 안 「다시 시도」(이전엔 미니앱을 껐다 켜야 했다), 앱 복귀 시 조용한 재조회(60s 스로틀).
  ④ **책 고르기 바텀시트** — 인라인 목록 → 딤+하단 패널, **시작·태깅 겸용**으로 통합. ⑤ **기록 탭** — 서버가 실어
  주는데 안 그리던 `monthLabels` 배치, 최신 주로 초기 스크롤(오늘을 보려고 매번 끝까지 스와이프해야 했다), 색 범례.
  ⑥ **소셜 마감** — 스토리 스트립을 원형 이니셜 아바타+링+캡션으로(인스타 관습 — 텍스트 알약은 "스토리"로 안 읽혔다),
  뷰어 "1/3" → 세그먼트 바 + 노치 대응, 책방 상단 back, 차단 2단계 확인(1탭 즉시 실행이었다), 3곳 엔터 제출.
  ⑦ **계정 진입점** — `api.logout()`은 있는데 부르는 UI가 없었다 → 홈 맨 아래 웹 안내 한 줄 + 2단계 확인 로그아웃.
  미니앱 테스트 199 → 276건, `npm run build` 클린.
  ⚠️ 하니스 사각(정적 렌더라 클릭·effect가 안 돈다)은 늘 그랬듯 순수 함수로 꺼내 계측했다 — 그 중 `logoutAndLeave`는
  RED가 실제 버그를 잡았다(`try/finally`라 `onDone`은 부르면서 예외를 되던져 호출부 `void`가 unhandled rejection).
  ✅ **출시됨** (2026-08-17 심사 통과). 🔜 남은 것은 실기기 육안 확인뿐 — 스토리 링·세그먼트·safe-area·기록 탭 초기 스크롤.

- **첫 세션 가치 경험 3점 세트** ✅ 코드 완료 / ✅ 출시 (2026-08-13 코드 · 2026-08-17 출시) — 정식 출시 후 첫 신규 코호트 실측이 나빴다:
  신규 토스 유저 3명 전원이 로그인·타이머 시작까지 도달했으나 **실독서(5분+) 0건**(완료 세션 1분 미만 8건 · 1~5분 2건,
  콘솔 체류 10~45초). 리텐션 이전에 **첫 세션 안에서 핵심 루프를 한 번 완주시키는 것**이 병목이라 보고, 코드에서 짚은
  원인 셋을 각각 막았다. 서버 2파일 + 미니앱 3파일, 스키마 무변경(Flyway 0), 웹 프론트 무변경.
  ① **측정 중 안심 문구** — "화면을 꺼도 측정은 계속돼요. 책 읽고 오세요 🌿". 이 앱의 핵심 계약은 서버 권위 측정인데
  측정 중 화면이 "측정 중 MM:SS"만 띄워 사용자가 그 사실을 알 방법이 없었다(몇 초 돌려보고 끄는 관측 패턴과 일치).
  ② **첫 완료 축하 + 잔디 하이라이트** — stop 응답에 `firstCompletedSession` 추가(완료 세션 수가 **정확히 1**이 되는
  순간만 참). 잔디는 1초만 읽어도 lv1로 점등되는데 미리보기가 폴드 아래라 첫 보상을 아무도 못 봤다 → 축하 배너 +
  잔디 카드 세이지 테두리로 시선을 아래로 보낸다. 상태는 메모리만(새로고침하면 사라진다 — 축하는 그 순간 1회면 족하다).
  ③ **firstRun 목표 초기 선택 10분** — 신규 기본이 1시간이라 첫 세션에서 「🌿 오늘 목표 달성」 경험 확률이 사실상 0이었다.
  서버 `DEFAULT_DAILY_INCREMENT_SECONDS`(웹 신규 가입과 공유)는 **건드리지 않고** firstRun 화면의 초기 선택만 내린다.
  ⚠️ 하니스 사각: 축하 상태는 stop 응답으로만 켜지는데 정적 렌더는 「측정 끝내기」를 누를 수 없다 — 배너·하이라이트는
  조각을 직접 렌더해 계측하고(`BookSheet`와 같은 처지), 플래그→UI 배선 자체는 목 모드 브라우저 확인이 게이트다.
  ✅ **출시됨** (2026-08-17 심사 통과). 🔜 **효과 측정이 남았다** — 다음 신규 코호트에서 실독서(5분+) 전환·재방문 재실측.

- **전환 이벤트 계측(`reading_session_started` / `reading_session_completed`)** ✅ 코드 완료 / ✅ 출시 (2026-08-13 코드 · 2026-08-17 출시) —
  위 3점 세트의 효과를 재려면 콘솔 「핵심 지표」에 우리 핵심 전환이 있어야 하는데, 전환 지표 템플릿엔 독서 완료가 없어
  대표 전환이 차선책인 「토스로그인 완료」로 잡혀 있었다. 콘솔의 「직접 조합하기」는 **실제로 발생한 적 있는 이벤트**에서만
  고를 수 있으므로, 먼저 쏘기 시작해야 선택기에 나타난다. SDK `Analytics.log`를 `toss.ts`의 `trackEvent` 한 곳으로 감싸
  홈의 측정 시작·종료 **성공 경로**에서 발사(완료엔 `duration_seconds`). 래퍼는 던지지 않는다 — 이 호출이 성공 경로
  한가운데라 실패가 새면 지표가 기능을 망가뜨린다(브라우저엔 주입 상수가 없어 동기 TypeError, 앱 안에선 거부된 Promise).
  🔜 **출시됐으니 이제 할 것**(사용자 몫, 콘솔 조작): 실기기 측정 1회 → 콘솔 이벤트 선택기에 두 이벤트 등장 확인 →
  **대표 전환을 「토스로그인 완료」에서 `reading_session_completed`로 교체**. 이걸 해야 위 3점 세트의 효과가 측정된다.

- **콘솔 「성능 대시보드」(크래시율·P90 로딩·평균 FPS) ⏸ 보류 (2026-08-17)** — 우리 계정에선 전 지표가 「-」이고
  「SDK를 설치하고」라 안내하는데, **웹 미니앱엔 설치할 SDK가 없다**: 출신이 게임(Unity) 미니앱이라 요구 버전이
  「SDK 1.11.2+」(Unity 체계)이고, `@apps-in-toss/web-framework` 3.0.2의 공개 API 117개를 훑어도 성능·크래시·FPS
  수집 API가 0개다(계측은 `Analytics.log/screen/impression/click`뿐 — 위 항목이 쓰는 그것). granite(RN) 개발자
  문의에도 운영진 답이 「조치중」이고, 개념상 WebView의 「크래시」는 이 대시보드가 세는 네이티브 프로세스 죽음이
  아니다. **재개 트리거**: 웹 미니앱용 SDK 공지 · `web-framework`에 성능 API 등장 · [문의 4224](https://techchat-apps-in-toss.toss.im/t/topic/4224)의
  「조치중」이 완료 공지로 전환. 그때 값어치가 큰 건 **FPS** — T-176(발광 `box-shadow` 재래스터화)이 목 모드·로컬
  웹 검증을 모두 통과한 클래스라, 지금 유일한 게이트인 「사용자 실기기」를 대신할 자동 계측기가 된다.

- **목표 설정 화면 휠 피커(시/분)** ✅ 코드 완료 / ✅ 출시 (2026-08-13 코드 · 2026-08-17 출시) — 목표를 프리셋 6개(10분·20분·30분·
  1시간·1시간 30분·2시간) 중에서만 고를 수 있어 "45분"처럼 그 사이 값을 원하는 사람에게 길이 없었다. TDS 기성품
  `Wheel` 2열(시간 0~12 · 분 0~59)로 갈아 1분 단위 자유 선택으로 연다. 서버는 이미 0 이상 아무 초 값이나 받으므로
  **순수 UI 작업**(서버·api.ts 무변경). 초(`selected`)가 여전히 단일 소스고 시/분은 풀었다 다시 합치는 파생값이라,
  변환 두 함수(`wheelIndices`/`combineWheel`)만 경계 전수로 못 박았다 — 12시간 초과 clamp·자투리 초 버림·음수·왕복 보존.
  0시간 0분은 저장 버튼 disabled(서버는 허용하지만 휠을 끝까지 내린 실수일 가능성이 높다). firstRun 초기 선택 10분은
  그대로(위 3점 세트 ③) — 첫 실행 휠이 0시간 10분에서 시작한다.
  ⚠️ 하니스 사각: `Wheel`은 정적 렌더에서 껍데기(`role="radiogroup"`+aria-label)만 내고 옵션은 클라이언트에서 채운다
  → 렌더 단언은 "휠 두 열이 붙어 있다"까지고, **드래그·스크롤 동작은 목 모드 브라우저 확인이 게이트**다.
- **홈 책 고르기 = 표지 캐러셀** ✅ (2026-08-13 코드 · 2026-08-14 실기기 확인) — 위 「홈 UX 정정」(2026-08-11)의
  **칩 + 「바꾸기」 시트를 대체**한다. 고르려면 시트를 열고 고르고 닫는 3탭이었고, 칩은 제목 첫 글자 상자라 책장에
  꽂힌 책을 눈으로 알아볼 수 없었다. 읽는 중 책 표지를 가로로 나열하고 **가운데 온 표지가 곧 측정할 책**이다
  (`scroll-snap-type: x mandatory` + 아이템 `scroll-snap-align: center`, 트랙 좌우에 `50% - 표지폭/2` 여백을 둬
  양끝 책도 가운데로 온다). 가운데 책만 scale 1.1·opacity 1이고 나머지는 0.45, 아래에 제목·저자 + 점 인디케이터.
  표지는 기존 `BookCover`(로드 실패 폴백 내장) 재사용이고 `coverUrl`이 없는 책(손 등록)은 `CoverInitial`로 떨어진다.
  데이터는 서버 `BookOption`에 **`coverUrl`·`author` 두 필드 추가**가 전부(하위호환 — 옛 클라이언트는 무시).
  시트는 **측정 종료 후 태깅 자리 하나로 좁아졌다**(`start` 모드 진입점 제거, 겸용 구조 자체는 보존).
  ⚠️ 하니스 사각: 스크롤·스냅은 정적 렌더로 못 돈다 → 계산을 순수 함수 `centeredIndex`(양끝 클램프)로 꺼내
  경계 전수로 못 박고, **스냅·선택 갱신은 목 모드 브라우저 확인이 게이트**다.
  실기기 버그 2건 후속 수정 ✅ (2026-08-13) — ① 선택 표지의 `scale(1.1)`이 세로 패딩 4px을 넘겨 트랙이
  세로로 넘쳤고 `overflow-x:auto`면 `overflow-y`도 auto로 계산돼 표지가 손가락에 들썩였다 → 세로 패딩을
  `COVER_HEIGHT`·확대분에서 유도(`TRACK_V_PAD`)하고 `overflowY: 'hidden'`을 벨트로. ② 탭 전환 재마운트마다
  표지가 늦게 떴다 → `BookCover`에 `eager` opt-in을 열고 **홈 캐러셀만** 켰다(목록은 lazy 유지).
  실기기 버그 2건 재수정 ✅ (2026-08-13) — 배포본에서 **좌우로 밀리지 않고, 옆 표지를
  탭해도 가운데로 오지 않았다**. 셋을 한꺼번에 걷어냈다: ① 위 ①의 벨트였던 `overflowY: 'hidden'`이 웹뷰에서
  트랙의 **터치 스크롤을 통째로 죽였다** → `touchAction: 'pan-x'`로 교체(세로 제스처는 그대로 막고 가로는 산다.
  세로로 넘칠 여지는 이미 `TRACK_V_PAD`가 0으로 만들어 뒀다) ② `scrollTo({behavior:'smooth'})`는 옵션 객체를
  무시하는 웹뷰가 있어 → 트랙 CSS `scroll-behavior: smooth` + `scrollLeft` 대입으로(첫 진입만 잠시 `auto`로
  꺼 즉시 이동) ③ 스크롤 **도중** 선택을 갱신하면 그 리렌더가 `scroll-snap: mandatory`의 재스냅을 불러 손가락에서
  스크롤을 빼앗는다 → `SCROLL_SETTLE_MS`(120ms) 디바운스로 **손을 뗀 뒤 한 번만** 고른다.
  실기기 재확인 후 잔여 2건 ✅ (2026-08-13 · 실기기 확인 2026-08-14) — ① 위 배포 뒤에도 **읽는 중 책이
  2권이면** 고른 표지가 가운데로 오지 않았다(4권이면 정상). 원인은 가운데 정렬 여백을 트랙 `padding`으로
  준 것 — **WebKit은 스크롤 컨테이너의 끝쪽 padding을 스크롤 영역에서 뺀다**. 폭 390 화면에서 2권이면
  밀 여지가 0이 되고(그래서 아예 안 움직임) 4권이면 절반만 남는다. 여백을 **첫·마지막 표지의 margin**
  (`EDGE_SPACE`)으로 옮겨 어느 엔진에서나 스크롤 영역에 들어가게 했다 — 목표 위치 식(`i * stride`)은 그대로.
  ② 서재에서 책을 「읽는 중」으로 바꿔도 홈 캐러셀에 반영되려면 앱을 나갔다 와야 했다 — 대시보드는 `App`이
  들고 있고 재조회가 앱 복귀(+60초 스로틀)뿐이었다. `Library`에 `onShelfChanged`를 열어 뮤테이션 성공 시
  `silentRefresh(force)`로 **스로틀을 건너뛰고** 조용히 다시 받는다(화면 유지, 성공 시만 교체).
  **「책 없이 시작」 보조 버튼 → 캐러셀 0번 「책 없이」 카드로 흡수** ✅ 코드 완료 / ✅ 출시 (2026-08-14 코드 · 2026-08-17 출시) —
  캐러셀이 "가운데 온 것이 곧 측정 대상"이라는 단일 문법을 세웠는데, 「책 없이」만 그 밖의 **버튼 갈래**로 남아
  문법을 깨고 있었다. 표지 줄 맨 앞에 점선 「책 없이」 카드를 **항상** 세우고 보조 버튼을 없앴다 — 고르는 방법이
  하나가 되고, 주 버튼은 「측정 시작」 하나다(가운데가 「책 없이」면 `start(null)`). 선택값은 센티넬 없이 `null`
  그대로 쓰고(`start(bookId: number | null)`가 이미 그 값을 받는다 — 변환 계층 0), 캐러셀 안에서
  `[null, ...books]`로 합성해 **+1 오프셋을 순수 함수 `carouselIndexOf`/`selectionAt` 둘로만** 통과시킨다
  (스냅과 선택이 한 칸 어긋나는 게 최대 리스크라 왕복 불변식 테스트로 못 박았다). 곁가지로 **책 0권 첫 사용자의
  빈 상태 섹션("아직 책이 없어요" + 「첫 책 추가하기」)이 사라졌다** — 이제 카드 한 장이 서고 부제가 서재로
  안내한다(진입은 하단 탭바 몫, 사용자 결정). 도달 불가였던 `'start'` 시트 모드(`SHEET_COPY`·`pickHandler`·
  `openSheetMode`)도 함께 걷어냈다 — 「책 없이 시작」 문자열의 마지막 서식지였다.
  ⚠️ `marginLeft: EDGE_SPACE`의 주인이 첫 책 → 「책 없이」 카드로 바뀌었다(T-160 영역) — 0권이면 한 카드에
  양쪽 margin이 동시에 붙는다. 목 모드 실측으로 구조 보존 확인(가로 여지 200=100×2 · 세로 0 · `pan-x`).
  **하단 풀폭 「목표 바꾸기」 → 타이머 카드 우상단 손잡이로 흡수** ✅ 코드 완료 / ✅ 실기기 확인(2026-08-19) (2026-08-14) —
  홈 맨 아래 풀폭 보조 버튼이 「측정 시작」과 폭이 같아 무게까지 같아 보였다. 타이머 카드 우상단의 작은 칩으로
  옮기되, 아이콘 팩이 없으므로(의존성은 `@toss/tds-mobile` 하나) 뜻은 그림이 아니라 **목표 값 자체**가 말한다 —
  「목표 30분 ›」은 무엇을 바꾸는 버튼인지 스스로 밝혀 아이콘 손잡이의 모호함이 성립하지 않는다. 덤으로 게이지
  줄에서 목표 값을 빼 같은 카드의 중복(`오늘 목표 30분` + 칩)을 없앴다 — 그 줄은 이제 「그래서 지금 어떤가」만
  말한다(미달성=`목표까지 05:00` / 초과=`+10분 더 읽었어요` / 정확히 달성=줄 생략, 히어로가 이미 말했다).
  목표 0이면 게이지 줄이 통째로 안 그려지므로 그때 이 칩이 목표 화면의 **유일한 길**이라 라벨이 「목표 정하기 ›」
  권유로 바뀐다(`goalHandleLabel`).
  **홈 화면 제목 「○○님의 오늘」 제거** ✅ 코드 완료 / ✅ 실기기 확인(2026-08-19) (2026-08-14) —
  제목이 정보를 하나도 안 보태면서 자리만 먹고 있었다: 「오늘」은 바로 아래 카드의 「오늘 읽은 시간」과 중복이고,
  이 화면의 이름은 하단 탭바가 이미 「홈」으로 말한다. 「오늘」로 줄이는 안도 검토했지만 **단어를 남길 이유가
  없어**(남길 근거가 "탭 전환 시 세로 점프"라는 레이아웃 사정뿐이었다) 통째로 뺐다. `Screen`의 `title`을
  optional로 열고 없으면 **헤더 행을 통째로 생략**한다(빈 행만 남기면 지운 값이 없다) — 제목을 다는 나머지
  화면(「내 서재」·「내 기록」·「책방 둘러보기」)은 그대로다. 홈에서 닉네임 노출이 사라지지만 소셜·책방에는 남는다.
  **게이지 최대치 = 목표 + 밀린 시간 · 「남은시간」 설명 상자** ✅ 코드 완료 / ✅ 실기기 확인(2026-08-19) (2026-08-14) —
  게이지가 목표만 분모로 써서 **꽉 찼는데도 남은 시간이 있는** 상태가 생겼다. 이월 중이면 오늘 실제로 채워야
  할 양은 목표+밀린이므로 분모를 그리로 바꾸고, 라벨도 「목표까지」 → 「남은시간 : mm:ss」로 총량을 말하게 했다
  (`remaining`은 곧 서버가 준 총 남은 시간 — 항등식이라 재계산할 자리가 없다). 그 합을 숫자만 보고는 못 읽으므로
  **탭하면 펼쳐지는 설명 상자**(`RemainingNote`)가 목표·밀린·남은시간 내역과 7일 자동 소멸 안내를 밝힌다 —
  히어로 아래 상시 노출이던 응원 문구(「뒤처져도 괜찮아요」)는 없앴다(밀린 시간이 있을 때마다 빚을 상기시키면서
  정작 "왜 이 값인가"엔 답하지 않던 자리). 손잡이는 설명할 내역이 있을 때만 단다(`showRemainingHandle` —
  밀린 0이거나 이월이 꺼져 있으면 밑줄 없이 텍스트만). ⚠️ **`achieved`는 여전히 목표만 본다(결정 a)** —
  밀린 시간은 벌이 아니라 이월이라 빚 때문에 축하를 미루지 않는다. 그래서 「목표는 달성, 게이지는 아직」인
  구간이 의도적으로 존재하고, 남은 몫은 「남은시간」 줄이 말한다.

- **앱 아이콘 교체** ✅ 이미지 완료 / ✅ **콘솔 업로드 완료** (2026-08-14 이미지 · **08-25 사용자 확인**) — 「책 · 성장 · 자라남」을 축으로
  **펼친 책의 접힘선에서 그대로 이어지는 새싹**을 굵기 하나(34)의 선그림으로 그렸다. 웹 PWA 아이콘(책 표지 +
  본문 줄)을 그대로 줄이면 홈화면 크기(96px)에서 디테일이 뭉개져 재사용하지 않았고, 요소마다 색을 달리 준
  초안들은 "도형을 쌓아 올린" 인상이라 **크림 한 색·굵기 하나**로 통일해 한 그림으로 묶었다(사용자 피드백).
  원본 `miniapp/icon/app-icon.svg` + 재생성 스크립트 `gen-icon.mjs`(→ `app-icon-600.png`, 콘솔 로고 규격)를 커밋해 재현 가능하게
  남긴다. 미니앱 코드·번들 무변경 — **업로드는 앱인토스 콘솔이라 사용자 몫**이다(Claude 접근 차단).

- **홈 잔디 카드 → 「소식」·「책 뉴스」 2탭 피드 박스** ✅ (2026-08-14, 뉴스만 점등 대기) — 홈에서 잔디
  미리보기를 걷어내고 그 자리에 탭 두 개짜리 피드 박스를 놓는다. 잔디 전체는 기록 탭에 이미 있어 손실이 없다.
  처음엔 「연속 N일 · 기록 보기 ›」 한 줄을 남겼으나, **실기기 확인 후 그것도 뺐다**(사용자 결정 2026-08-14) —
  기록 탭이 하단 탭바에서 한 번에 닿아 홈의 진입 손잡이는 자리만 먹는 중복이었다. 이제 「측정 시작」 다음이
  곧 피드 박스다. ① 「소식」 = 팔로우한 사람의 **공개(PUBLIC)** 책 활동
  (완독했어요 / 읽기 시작했어요 + 상대 시간) ② 「책 뉴스」 = 내가 **완독한** 책 관련 뉴스(구글 뉴스 RSS를
  서버가 새벽 배치로 수집·캐시). 리스트는 미리보기 3줄 + 「더 보기」 인라인 확장(≤30건).
  - **PR-1 서버** ✅ (2026-08-14) — `book.started_reading_at`(V64) 신설로 "읽기 시작" 이벤트의 시각을 얻는다
    (`finished_at`의 미러이되 완독으로 넘어가도 지우지 않는다 — 두 이벤트가 시간순 공존해야 피드가 자연스럽다).
    피드 쿼리는 `StoryRepository.feedOf` 미러(팔로우 theta 조인·PUBLIC only·ADMIN 제외·`login_id` null 제외).
    **이벤트 적재 테이블을 만들지 않고 현재 상태에서 유도**한다 — 비공개 전환·삭제된 책이 피드에서 저절로
    사라지는 게 공짜로 따라온다. 전달은 대시보드 동봉이 아니라 별도 `GET /api/home-feed`(히어로 렌더가 피드
    쿼리에 인질 잡히지 않게 + 서버·미니앱 PR 배포 분리).
  - **PR-2 미니앱** ✅ (2026-08-14) — 잔디 카드 제거, 그 자리에 「연속 N일 · 기록 보기 ›」 한 줄 + `HomeFeed.tsx`
    신설(탭·3줄 미리보기·더 보기 인라인 확장·빈 상태). 뉴스 줄은 `Device.openURL`로 기기 브라우저에 넘긴다
    (최상위 `openURL`은 deprecated). 「더 보기」는 접기 없는 일방 확장이고 탭을 옮기면 3줄로 돌아간다.
    조사는 제목 마지막 글자의 받침으로 을/를을 고른다 — 피드가 남의 책 제목을 그대로 싣는 자리라
    「사피엔스을」처럼 어긋나는 경우가 잦았다(한글 음절 종성 인덱스 + 숫자는 읽는 소리).
  - **PR-3 뉴스 수집** ✅ (2026-08-14) — `book_news`(V65) + 새벽 04:30(KST) 배치. 수집원은 처음 네이버 검색
    API로 만들었다가 **구글 뉴스 RSS로 갈아끼웠다**(아래 ⚠️).
    매칭은 기사에 **제목 AND 저자**가 둘 다 있어야 채택(『총, 균, 쇠』류 오탐 차단 — 대신 저자 표기가
    흔들리는 번역서를 좀 놓치는 트레이드오프). 수집은 **isbn당 1회**(사용자별이 아니라 합집합 — 같은 isbn을
    여러 사람이 제목 표기를 달리 갖고 있으면 `distinct`가 행을 갈라 같은 isbn을 두 번 지우고 쓰므로 `group by`로
    계약을 보장한다), 상위 3건 delete-then-insert.
  - **수집원 전환: 네이버 검색 API → 구글 뉴스 RSS** ✅ (2026-08-14) — ⚠️ **네이버 검색 API는 신규 권한 발급이
    닫혔다.** 애플리케이션을 등록해도 「사용 API」 목록에 「검색」이 없어 scope가 안 붙는다(실측: 발급 키로 검색
    호출 시 `401 Scope Status Invalid` / 같은 키로 캡차 호출은 `200` — 키는 정상이고 scope만 부재). 네이버의
    「API 제휴 신청」은 개발자 셀프서비스가 아니라 **기업 제휴 제안 채널**이라 현실적 경로가 아니었다.
    → 키가 필요 없는 **구글 뉴스 RSS**로 전환. 테이블·매처·배치·캐시·조회는 그대로 두고 클라이언트(JSON→XML,
    JDK 내장 DOM · **XXE 차단 필수**)와 출처 처리만 교체했다. 출처는 RSS `<source>` 엘리먼트에서 오고,
    **링크가 구글 리다이렉트라 호스트명 파생이 불가능**해 서버가 매체명을 실어 준다(`NewsItem.source`).
    게이트는 키가 사라져 **킬스위치 `booktimer.news.enabled`(기본 켜짐)**로 바뀌었다 — 공식 API가 아니라
    형식이 바뀔 수 있어 SSM 값 하나로 끌 수 있게 남긴다.
    실측 매칭 성능(제목+저자 질의, 매처 통과): 미움받을 용기 11 / 데미안 28 / 사피엔스 24 / 총,균,쇠 11건.
    기각된 것이 정확히 막고 싶던 오탐이다(「웨이코 그룹 **데미안** 월튼 사장 주식 매각」, 「삼겹살 **사피엔스**」).
    ⚠️ 파싱 픽스처는 **실제 응답에서 잘라 만들었다** — 지난 네이버 구현이 공개 문서 기준 추측 픽스처였고
    그게 미검증으로 남았던 게 이 전환의 교훈이다.
  - **⚠️ 첫 실행 0건 → 제목·저자 정규화** ✅ (2026-08-14) — 배포 후 관리자 수동 실행에서 **대상 29종 · 저장 0건**.
    원인은 네트워크가 아니라 **알라딘 원문을 그대로 질의·매칭에 쓴 것**이었다: 운영 DB의 값이
    `코스모스 - 특별판` / `칼 세이건 (지은이), 홍승수 (옮긴이)` 꼴이라, 기사 제목에 「제목 전체 AND 저자 전체」가
    들어 있을 리 없어 **전 종목 기각**됐다. 제목에서 부제·괄호·판매단위 꼬리(세트·전N권)를, 저자에서 역할표기와
    역자를 걷어내고(기존 `WriterName.lead()` 재사용) **질의와 판정이 같은 정규화 값을 쓰게** 고쳤다.
    실측(구글 RSS 수신 100건 기준 채택 수): 코스모스 0→45 · 1984 0→32 · 이방인 0→18 · 데미안 0→28.
    성(姓) 폴백은 이득이 미미하고(32→37) 오탐 위험만 늘어 기각했다.
    **교훈**: 매처를 만들 때 검증 입력을 내가 골랐다(「데미안」·「헤르만 헤세」처럼 깨끗한 값). 설계·구현·리뷰를
    다 통과했지만 **실데이터를 한 번도 안 봐서** 전 종목 0건이 배포까지 갔다 — 이제 테스트 픽스처가 운영 DB 원문이다.
  - **관리자 수동 수집** ✅ (2026-08-14) — `POST /admin/books/collect-news` + `/admin` 버튼. 수집이 하루 1회
    배치라 새로 완독한 책의 뉴스는 다음 새벽에야 붙는데, 배포 직후 첫 점등 확인이나 "완독하고 바로 보고 싶을 때"
    기다릴 길밖에 없었다. `AdminBackfillController`와 같은 경계(`/admin/**`=ADMIN · POST+CSRF · 결과 플래시).
    `collect()`가 `NewsCollectionResult(enabled, targets, saved)`를 돌려주도록 바꿨다 — `enabled`를 따로 싣는 건
    킬스위치가 내려간 상태의 "0종 0건"이 "완독한 책이 없음"과 구분되지 않아 관리자가 엉뚱한 곳을 뒤지기 때문이다.
  - ⚠️ 홈 UI가 크게 바뀌므로 **다음 심사 제출 전 `miniapp/screenshots/01-home` 갱신 필요**.

- **홈 피드에 「함께 읽는 사람」 탭 신설** ✅ 코드 완료 / ⬜ 실기기 확인 대기 (2026-08-19) — 위 피드 박스에
  **맨 왼쪽 세 번째 탭**을 붙인다. 게임의 친구 상태창처럼 「팔로우한 사람이 지금 읽고 있는지, 아니면 마지막
  기록이 언제인지」를 한 화면에 세운다. 탭은 **이름표 없이 사람 그림**이고 세로선 한 획으로 나머지 둘과 갈린다
  (「소식」·「책 뉴스」가 *읽을 거리*라면 이쪽은 *사람*이다). 맨 왼쪽이지만 **기본으로 열리는 탭은 「소식」**
  (`DEFAULT_TAB`) — 위치가 곧 기본값이 되면 팔로우 0명인 사람이 홈에서 빈 목록부터 만난다.
  - **관계 모델은 안 바꿨다 (핵심 판단)** — 사용자가 "팔로우가 단방향이라 공개 범위가 애매하니 친구 시스템으로
    바꿔야 하나"를 물었고, 코드를 읽어 **바꿀 필요가 없다**고 답했다: 이 앱의 공개 결정권은 사람이 아니라
    **책**에 있다(`BookVisibility`, 기본 PRIVATE opt-in). 관계 방향을 바꿔도 「무엇이 보이는가」는 한 글자도
    안 변하고, 「소식」 탭이 단방향 전제로 돌고 있어 맞팔로 좁히면 기존 사용자 피드가 줄며, 이미 맺힌 단방향
    팔로우의 처리(자동 친구/요청 대기/삭제)는 어느 쪽도 설명이 안 된다. 대신 **「친구감」만** 가져왔다 —
    맞팔이면 「서로」 배지 + 정렬 우선(판정 쿼리는 친구 추천이 쓰던 것의 거울). **새 테이블·마이그레이션 0개**.
  - **불변식 한 줄: 이 탭은 공개 책의 독서만 본다** — 그래서 새로 공개되는 것이 0이고, 「내 상태를 누구에게
    보일까」를 새로 묻는 **설정 화면도 0개**다(책마다 있는 공개 스위치가 이미 그 설정이다). 비공개 책을 읽는
    중이면 「읽는 중」 표시조차 안 뜬다 — 제목만 가려도 지금 뭘 하는지가 새기 때문이다. 미태깅 세션
    (`book=null`)도 공개 여부를 판정할 수 없어 비공개로 취급한다.
  - **문구는 판단하지 않는다** — 「3일째 안 읽었어요」가 아니라 **「마지막 기록 3일 전」**이다. 압박을 피하려는
    것이기도 하지만 무엇보다 **비공개로만 읽는 사람에겐 「안 읽었다」가 거짓**이라서다. 같은 이유로 기록이
    없으면 「공개된 기록이 없어요」라고 **「공개된」을 밝혀**, 조용한 사람을 안 읽는 사람으로 만들지 않는다.
  - **서버**: `/api/home-feed` 응답에 `readers` 추가(별도 엔드포인트를 파지 않았다 — 같은 박스 안 탭이라
    탭별 로딩·에러·fetch 배선이 새로 생긴다). 쿼리 4개 고정, 팔로잉 수와 무관(N+1 없음), 팔로잉 0명이면
    나머지 쿼리를 아예 안 탄다. 정렬은 비교자 하나로: 읽는 중 → 맞팔 → 최근 활동순 → 기록 없음.
    ⏸ **업그레이드 경로** — 팔로잉이 커져 홈 진입이 느려지면 `/api/reading-status`로 떼어 탭 첫 진입에 lazy fetch.
  - **미니앱**: `feed.readers ?? []`로 읽어 **서버·미니앱 배포 순서에 화면이 의존하지 않는다**(구버전 서버와도 붙는다).
  - ⚠️ **감수한 것**: 글자가 없어 처음 보는 사람은 이게 뭔지 모른다. 보통은 툴팁·코치마크로 알려주지만
    **진입 직후 화면을 덮는 것이 금지**(T-183, 툴팁 포함)라 그 수단을 못 쓴다 — 눌러 보면 알게 되는 구조에 맡겼다.
    터치 크기도 실측 40×30으로 권장 44px보다 작은데, 기존 글자 탭(49×32)과 같은 수준이라 회귀는 아니다
    (사람 탭만 키우면 혼자 튄다 — 키운다면 탭 줄 전체가 별건이다).
- **여백 좋아요 — 남의 문장에 하트를 단다** ✅ 코드 완료 / ⬜ 실기기 확인 대기 (2026-08-20) — 여백에 쌓인
  글 하나하나에 좋아요를 달 수 있다(사용자 요청). 시안 3안(하트 / 밑줄 / 모서리 접기)을 비교해 **A안 「하트」**를
  골랐다(사용자 결정) — 밑줄은 종이·연필 세계관에 더 맞았지만 익숙함이 이겼고, 모서리 접기는 「한 모서리를 셋이
  접는다」가 성립하지 않아 개수와 어긋났다.
  - **누를 수 있는 사람은 이미 정해져 있었다** — 여백은 그때 `self`나 팔로워에게만 글을 내려줬으므로
    좋아요의 관객은 자동으로 **공개 책을 팔로우하는 사람**뿐이었다. 새로 정한 규칙이 아니라 기존 게이트의 결과다.
    **⚠️ 이 전제는 2026-08-22에 사라졌다** — 팔로우 축을 걷어 관객이 「공개 책을 보는 누구나」로 넓어졌다(PR-4).
    거기서 둘이 따라 나왔다: ~~비공개 책엔 자리 자체를 안 만들고~~, ~~내 글엔 개수만 보인다~~
    (자기 좋아요는 도메인이 금지). **⚠️ 이 둘은 같은 날 뒤집혔다 — 아래 「누가 눌렀는지」 항목 참조.**
  - ⚠️ **손잡이의 게이트를 `self`로 두지 않았다 — 이게 이 작업에서 가장 미끄러운 자리다.** 서재의 인라인 여백
    미리보기는 **내 글인데도 `self={false}`**를 넘긴다(거기서 그 프롭은 「삭제 UI를 감춰」라는 뜻이다). `!self`로
    켰다면 내 서재에서 내 글에 하트가 떴다. 대신 **핸들러(`onToggleLike`)의 유무**를 조건으로 삼아, 안 넘기면
    구조적으로 버튼이 없다. 회귀 테스트가 그 자리를 못 박는다.
  - **서버 게이트는 목록 게이트를 그대로 재사용한다**(`assertLikable` ← `marginOf`) — 어긋나는 순간 안 보이는
    글 id에 눌러 보고 **200/404로 존재를 알아낼 수 있다**. ⚠️ **취소에는 게이트를 안 건다**: 걸면 누른 뒤
    언팔한 사람이 자기 좋아요를 되돌릴 수 없게 갇힌다(행의 존재 자체가 「한때 볼 수 있었다」의 증거다).
  - **POST/DELETE 두 경로이고 POST는 멱등**이다 — 토글 하나로 두면 모바일 타임아웃 뒤 재전송이 좋아요를
    **취소**해 버린다. 응답은 서버가 센 `{likeCount, liked}`라 클라의 ±1 추정치가 오래 남지 않는다.
  - **하트 색은 `currentColor`다** — 배경 팔레트 6종 중 sunset이 붉은 주황(#c96a4a)이라 빨간 하트는 거기서
    사라진다. 카드 본문 색을 상속하면 여섯 배경 전부에서 읽히고(실측 3.51~14.09:1, 비텍스트 UI 기준 3:1 충족)
    상태 구분은 색이 아니라 **채움 여부**가 진다.
  - **클라이언트 둘 다 했다**(미니앱 + 웹 Vue 아일랜드) — 미니앱에만 달면 웹에서 글을 쓴 사람은 자기가 받은
    좋아요를 영영 못 본다.
  - ~~**명단은 없다** — 개수만 준다~~ → **같은 날 붙였다**(아래 항목). ⚠️ 손잡이 터치 크기 실측
    34~46×27~31로 권장 44px보다 작다 — 옆의 지우기·신고와 같은 스타일이라 회귀는 아니지만, 좋아요는 훨씬
    자주 눌리므로 실기기에서 오탭이 잦으면 그때 카드 하단 전체를 키운다.
- **여백 좋아요 후속 — 누가 눌렀는지 보이고, 내 글에도 누른다** ✅ 코드 완료 / ⬜ 실기기 확인 대기
  (2026-08-20) — 바로 위 항목의 후속이자 두 결정을 뒤집은 작업이다. 계기는 실사용이었다: **여백에 글을 쓴
  사람이 아직 없어 좋아요를 확인할 길 자체가 없다**(사용자 지적).
  - **자기 좋아요를 허용했다 — 특수 케이스를 「지우는」 방향이었다.** `StoryLike.of`의 자기 글 검증,
    `assertLikable`의 자기 글 404, `withLikes`의 `liked` 강제 false 셋을 걷어내니 규칙이 하나로 줄었다:
    **「받은 글은 전부 누를 수 있다」**(`marginOf`가 자기 것 아니면 이미 공개+팔로워만 내려주므로). 그 결과
    클라이언트 양쪽의 `likable()` 함수가 **통째로 사라졌고**, 비공개 책 예외도 같이 없어져 내 비공개 메모에도
    하트를 찍을 수 있다. 막을 근거도 약했다 — 트위터·인스타 모두 자기 글에 눌린다.
  - ⚠️ **`self` 함정은 그대로 닫혀 있다** — 서재 인라인 미리보기는 `onToggleLike`를 안 넘겨서 버튼이
    구조적으로 안 생긴다(목 모드 실측: 하트 버튼 0개, 개수는 `<p>` 글자로만). 게이트를 `self`가 아니라
    **핸들러 유무**로 둔 판단이 규칙이 뒤집혀도 살아남았다.
  - **명단은 `GET /api/stories/{id}/likes`** — 검색·팔로워 목록이 쓰는 **같은 행 DTO**(`UserSearchResult` +
    `UserRowAssembler`)라 새 DTO가 0개다. 게이트는 목록과 같은 판정을 재사용(`assertLikable` → **`assertVisible`**로
    개명, 좋아요·명단이 하나를 공유) — 어긋나면 임의의 글 id로 명단을 열어 「그 글이 있다」와 「누가 눌렀다」를
    한꺼번에 알아낼 수 있다. **명단에서 둘을 뺀다**: 핸들 없는 사람(N-055)과 **차단 관계**(차단은 팔로우를
    양방향으로 끊지만 **이미 눌린 행은 남아서**, 안 거르면 차단한 사람의 활동이 이 명단으로 샌다).
  - **손잡이를 둘로 갈랐다** — 하트는 누르기/취소만, 개수는 아래 줄 「좋아요 N명」이 명단을 연다. 한 버튼이
    두 일을 하면 명단을 보려다 좋아요가 눌린다. 푸터에 넷째 칸을 넣지 않고 **아래 줄로 내린** 이유는 좁은
    폭에서 접히기 때문이고, 덤으로 명단 손잡이가 어제 지적된 27~31px보다 큰 타깃이 됐다.
  - ⚠️ **명단 → 책방 이동은 「탭 밖 전역 뷰」로 세웠다 — 여기가 함정이었다.** 책방 탭으로 점프시키는 배선이
    먼저 떠오르는데, 그건 **이 코드베이스가 이미 하고 되돌린 실수**다(`MarginState` 주석: 뒤로 가면 출발한 탭이
    아니라 책방이 나온다). 그래서 `App.tsx`가 여백과 **같은 자리**에 `Profile`을 든다. 전환은 「닫으면서 여는」
    교체 경로(검색 시트와 같은 관례 · T-166)라 뒤로 가면 출발한 탭으로 돌아가고, 그 책방에서 연 여백은 위에
    서서 닫으면 책방이 다시 나온다(2단 스택). 웹은 `UserRow.vue`가 `/u/{handle}` 링크를 이미 달고 있어 공짜다.
  - **미니앱은 시트, 웹은 인라인 목록** — 미니앱은 팔로워 시트가 쓰던 `Sheet`+`UserList`를 그대로 재사용했고
    (그 과정에 `UserList`를 `Bookshop`→`ui`로 옮겼다: 여백이 쓰기 시작하며 화면끼리 순환 import가 생긴다),
    웹은 카드 안에 접어 넣었다 — 패널(z-150) 위에 또 오버레이를 얹지 않는다(새 z 층 발명 금지).
  - ⚠️ **감수한 것**: 명단과 카드의 개수가 차단 때문에 한둘 어긋날 수 있다(개수는 서버 집계 그대로 — 집계까지
    관계를 태우면 목록 응답이 비싸진다). 명단에 상한이 없다(팔로워 목록과 같은 처지 — 관객이 이미 좁다).
- **스토어 스크린샷 재촬영 — 3장이 낡아 있었다** ✅ (2026-08-20, 출시 직전 점검) — 08-19 출시분 이후
  화면 셋이 바뀌어 콘솔 그림과 앱이 어긋나 있었다: ① `04-history`에 **표지 더미·펼침**이 없고(#880)
  ② `03-library`에 **읽은 시간·공개 칩**이 없고(#879) ③ `02-feed`에 소식 **표지·「다 읽음」 배지**와
  **「함께 읽는 사람」 탭**이 없었다(#870·#878). `shoot.mjs`로 6장을 다시 찍었다.
  - **`01-home`·`05-goal`은 바이트째 그대로였다** — 그 두 화면이 안 바뀌었다는 증거다. 독서등·「읽는 중」
    카드는 **측정 중에만** 서므로 대기 상태의 홈은 예전과 같다.
  - **`04-history`는 구도도 바꿨다** — 최상단에서 찍으면 연속·통계 카드가 위를 다 먹어 새 얼굴인 표지 더미가
    **탭바 아래로 밀려 한 줄도 안 보였다**(실측). 잔디 제목을 기준선으로 잡아 잔디와 날짜 줄을 한 컷에 담고,
    **여러 권 읽은 첫 날을 펼쳐 둔다** — 접힌 더미만으론 「겹쳐 있다」까지만 보이고 요점인 **책별로 얼마나
    읽었나**가 그림에 없다.
  - ⚠️ **「출시 직전 점검」이 또 낡음을 잡았다**(4회째). 화면을 바꾼 PR에서 sweep 하는 규율은 네 번 다
    안 먹혔다 — 점검을 심사 제출 절차의 고정 단계로 두는 판단이 옳았다.
- **기록 — 하루에 읽은 책을 더미로 쌓고, 눌러 펼치면 책별 시간** ✅ 코드 완료 / ⬜ 실기기 확인 대기 (2026-08-20) —
  하루에 여러 권 읽은 날이 표지를 **옆으로 나열**하기만 해서, 네 권이면 세 장 + 「+1」로 늘어서고
  **어느 책을 오래 읽었는지는 어디에도 없었다**(그날 총합 하나뿐). 사용자 요청으로 겹쳐 쌓고 펼치게 했다.
  - **서버가 책별 시간을 준다.** `DailyReadingRecord.bookTitles: List<String>` → `books: List<BookRead>`
    (제목 · 표지주소 · 그 책만 읽은 초, **오래 읽은 순**). `left join fetch s.book`이 이미 있어 **추가 쿼리는 0**.
    같은 책의 여러 세션은 한 줄로 합산하고, 동률이면 안정 정렬이 **먼저 편 책**을 앞에 남긴다.
  - **표지 주소가 같이 오면서 기록 화면의 첫 글자 타일이 진짜 표지가 된다** — 홈 소식과 어긋나 있던 자리다.
  - **접히면 총합, 펼치면 책별.** 펼칠 수 있는 기준은 「몇 권인가」가 아니라 **「펼치면 줄이 둘 이상인가」**다 —
    한 권만 읽은 날은 펼쳐도 같은 숫자 하나라 손잡이를 안 단다(없는 것을 약속하지 않는다).
  - **「책 안 고른 기록」 줄.** 책을 안 고르고 잰 세션 시간은 `books`에 없고 총합에만 있다. 그냥 버리면
    **펼친 시간을 더해도 위 총합과 안 맞으므로**, 차액을 회색 줄로 밝힌다.
  - **막대 정렬을 구조로 고쳤다(사용자 지적).** 전에는 flex라 표지 개수와 시간 글자 폭이 행마다 달라
    막대의 **시작점·끝점이 둘 다 흔들렸다** — 같은 퍼센트가 행마다 다른 픽셀로 그려져 **막대 길이 비교
    자체가 거짓**이었다. `50px 56px 1fr 76px 16px` 고정 격자로 못 박았다(손잡이 칸은 못 펼치는 날에도 비워 남긴다).
  - 목 모드 실측이 **결함 둘을 잡았다**: ① 겹침 -13px + 권수 타일 최상단 → 가운데 표지가 **아예 안 보임**
    (겹침 -8px, 권수 타일은 더미 밖으로) ② `textAlign: 'right'`가 TDS `Text`에 **걸러져 조용히 죽음**(T-198).
- **화면 밀도 손질 2건(후속) — 요약을 앞으로, 상태를 칩으로** ✅ 코드 완료 / ⬜ 실기기 확인 대기 (2026-08-20) —
  앞 3건에서 「시안엔 올렸지만 안 한 후보」로 남겨 둔 둘을 마저 했다.
  - **④ 책방 — 태그가 서술보다 먼저다.** 성향 서술은 서버가 써 준 다섯 줄짜리 문단이고 태그(`소설`·`인문`·
    `완독형`)는 **그 요약**인데, 문단이 앞에 있어 「이 사람이 어떤 독자인가」를 스크롤해야 알 수 있었다
    (요약이 원문 뒤에 있는 꼴). 순서를 뒤집어 세 단어가 이름 바로 아래 선다. 문단은 **카드에 담았다** —
    사용자가 쓴 소개가 아니라 **분석 결과**라는 출처가 형태로 드러나고, 「더보기」도 그 카드 안 꼬리가 된다.
  - **⑤ 서재 — 읽은 시간·공개 여부를 칩으로 뗐다.** 전에는 저자 다음 줄에 「2시간 · 공개」로 붙어, 같은
    회색 글자 두 줄이라 **「누가 썼나」와 「내 상태」가 한 덩어리로 뭉쳤다**. 특히 **공개 여부는 이 화면에서
    가장 중요한 토글**인데 저자 이름과 같은 무게였다(바꾸는 자리는 「관리」 시트인데, 지금 어느 쪽인지가
    안 보이면 열어 볼 이유도 안 생긴다). 저자는 글자, 상태는 형태로 갈렸다.
    - 시간은 「2시간」이 아니라 **「2시간 읽음」**이다 — 칩 하나만 떼어 놓고 보면 무엇의 2시간인지 모른다.
    - **공개는 세이지로 켜지고 비공개는 외곽선으로 눌린다** — 색이 곧 「누구에게 보이는가」다.
    - 덤: 저자를 줄바꿈으로 분리하던 `pre-line` 수법이 필요 없어졌다. 긴 알라딘 저자 문자열이 되접혀도
      칩은 **다른 요소**라 그 사이에 끼어들 수가 없다(전에는 한 문자열이라 가능했고, 그게 실기기 버그였다).
  - **새 색·서체는 0이다** — 칩 세 톤이 앞 PR의 소식 배지와 같은 값이다.
- **화면 밀도 손질 3건 — 텍스트만 있던 자리에 형태를 준다** ✅ 코드 완료 / ⬜ 실기기 확인 대기 (2026-08-20) —
  「UI가 텍스트로만 이루어져 단순하다 / 강조될 것이 안 강조된다 / 한곳에 과하게 모여 있다」는 지적에서
  출발했다. 화면별 `img`·`svg` 개수를 실측하니 **기록·책방이 0/0**이었다 — 표지·잔디·연필 테두리를 다
  만들어 놓고 **그 자리에만 안 데려다 놓은 것**이라, 새 시각 언어를 만들지 않고 있는 것을 옮겼다.
  - **① 소식 피드에 표지와 사건 배지** — 서버 `SocialEvent`에 `coverUrl`을 실어(세 종류 모두) 문장을
    읽기 전에 「무슨 책」이 보이게 했다. 배지 문구는 **서재가 이미 쓰는 말**(「다 읽음」·「읽는 중」)을 그대로
    가져온다 — 같은 상태를 화면마다 다른 이름으로 부르면 두 화면이 서로 다른 앱처럼 읽힌다.
    ⚠️ **완독만 채운 배지**다: 전에는 7px 색점이 종류를 말해 이 앱에서 **가장 축하할 사건이 「읽기 시작」과
    같은 무게**였다. 남의 글(발췌)은 세로선 안으로 들여 문장·발췌·시각 세 줄을 한 덩어리로 묶었다.
  - **② 기록의 성장 단계를 카드로** — 이 앱이 주는 유일한 보상이 스탯과 잔디 사이 13px 한 줄로 끼어
    있었다. 카드로 올리고 **연속 일수를 그 안에 들여** 「연속 N일째 → 지금 단계 → 다음 단계까지」 한 흐름으로
    묶었다(연속이 곧 단계의 정본인데 그 관계가 화면 어디에도 없었다). 사다리는 서버 `GrowthStage`가
    `minStreak`·`next()`로 한 곳에서만 갖는다 — 클라가 임계값을 베끼면 두 곳이 따로 늙는다.
  - **③ 기록 날짜 행에 표지와 막대** — 제목을 쉼표로 이어 붙이고 `nowrap`으로 잘라, **두 권만 돼도**
    「미움받을 용기, 사피…」가 됐다(몇 권인지도 무슨 책인지도 안 남는 잘림). 표지 칸으로 옮겨 권수가
    자리로 보이고, 넘치면 `+N`으로 **밝혀서** 뺀다. 막대 기준은 **그 달의 최대치**다(전체 최대로 재면 한가한
    달이 죄다 납작해진다). 서버 계약은 안 건드렸다 — 자리 표지가 제목색 첫 글자라 표지 주소가 필요 없다.
  - ⚠️ **덤으로 잡은 것 둘**: 조사 버그(「나무이 돼요」)를 테스트가 잡아 종성 판정을 `format.ts` 한 곳으로
    모았고(피드의 을/를과 성장 문구의 이/가가 같은 판정을 쓴다), 목이 **서버에 없는 단계**(`SAPLING`·🌿·
    「어린 나무」)를 그리고 있던 것을 실제 사다리로 바로잡았다 — 목이 실제와 다르면 브라우저 확인이
    통과해도 아무것도 증명하지 못한다.
  - **시안에서 바뀐 것**: 소식 문장은 그대로 뒀다(배지가 동사를 대신 말하되 문장까지 고치면 조사 처리 등
    검증된 로직이 흔들린다). 연속은 스탯에서 강조하는 대신 성장 카드 안으로 옮겼다 — 한 화면에서 같은
    숫자를 두 번 읽게 하지 않으려고.
  - ~~**아직 안 한 후보 둘**: 책방 자기소개 덩어리(태그를 위로), 서재 책 행의 과밀(상태와 액션 사이 경계).~~
    → ✅ **둘 다 바로 위 항목(「화면 밀도 손질 2건(후속)」 ④⑤)에서 처리됐다.** 이 줄을 안 닫아 둔 탓에
    2026-08-25 세션이 **이걸 「남은 작업」으로 읽고 착수했다** — 코드를 열어 보고서야 이미 된 것을 알았다.
    plan.md는 최근 항목이 위로 오므로 **꼬리에 남긴 「안 한 것」은 그 위에서 처리되면 반드시 닫아야 한다**
    (T-211 군 — 낡은 문장이 「읽히는」 자리에서 「소비되는」 자리로 넘어간 6회차).
- **「함께 읽는 사람」에 마지막으로 읽은 책** ✅ 코드 완료 / ⬜ 실기기 확인 대기 (2026-08-20) —
  친구 줄의 문장이 세 갈래인데 가운데가 비어 있었다: 읽는 중이면 `『데미안』 읽는 중`인데, 지금 안 읽고
  있으면 `마지막 기록 3일 전`이라 **어느 책이었는지가 없었다**. 그 자리를 책 제목으로 채웠다.
  - **마이그레이션이 없다** — 컬럼을 안 늘렸다. 이미 DB에 있는 사실을 **안 읽고 있었을 뿐**이다.
    원천 쿼리가 `group by user, max(startedAt)`이라 시각만 뽑고 그게 어느 책이었는지는 버리고 있었다.
  - ⚠️ **시각과 제목을 같은 행에서 뽑는 것이 이 작업의 핵심 판단이다.** 둘을 따로 뽑으면
    「A책 제목 + B세션 시각」이라는 **있지도 않은 독서를 화면이 지어낸다**. 그래서 상관 서브쿼리로
    사람별 최신 세션 한 행을 잡는다(greatest-n-per-group — JPQL엔 윈도 함수가 없다). **쿼리 수는 그대로**다.
  - **완료 세션을 전부 끌어와 자바에서 고르는 방식은 기각했다** — 코드는 제일 짧지만 행 수가 그 사람의
    독서량에 비례해 무제한이 된다(30명 × 수백 세션). 지금 방식은 반환 행이 **사람 수로 고정**이다.
  - **문구는 `『아버지의 해방일지』 · 3일 전`** — 읽는 중 줄과 같은 자리에 책이 서서, 목록을 훑으면
    이름 아래로 **책이 쭉 읽힌다**(그게 이 요청의 목적이다). 제목 없이 시각만 오는 조합은 새 쿼리론
    안 생기지만 계약상 null이 가능해 옛 문구로 떨어뜨린다.
  - **불변식은 하나도 안 건드렸다** — 공개 책 세션만 본다는 게이트가 제목에도 그대로 걸린다.
    비공개로만 읽는 사람은 여전히 「공개된 기록이 없어요」이고, 새로 공개되는 사실이 없다.
  - **웹에는 이 목록이 없어**(미니앱 전용) 건드릴 곳도 없었다.
- **여백에 인용문 — 「책의 문장 + 내 주석」 두 층** ✅ 코드 완료 / ⬜ 실기기 확인 대기 (2026-08-20) —
  여백의 글이 지금까지 「사용자가 쓴 글」 하나였는데, 거기에 **책에서 옮긴 문장**을 얹고 그에 **주석을 다는**
  형식을 요청받았다(사용자). 시안을 만들어 방향을 확정한 뒤 구현했다.
  - **데이터는 한 칸만 늘렸다** — `story.quote varchar(200) null`(V75). 순수 추가에 nullable이라 **옛 글은
    전부 NULL로 남아 예전과 픽셀 하나 다르지 않게 그려진다**(백필 0). `text`는 그대로 두고 의미만 「주석」이 됐다.
  - **주석은 인용이 있어도 필수다**(사용자 확정) — 인용만 남기는 글은 도메인이 거부한다. 이유 둘: 여백의 본문은
    내 생각이라는 정체성, 그리고 **정당한 인용의 요건**(내 글이 主·인용이 從). 200자 상한도 같은 규칙의 수치 표현이다.
  - ⚠️ **인용을 크게 뽑지 않은 것이 이 작업의 핵심 판단이다.** 크게 뽑으면 예쁘지만 **카드의 주인공이 남의
    문장**이 되어 「내 독서 기록」이 명언 카드가 되고, 좋아요도 원저자가 받는 모양이 된다. 그래서 인용은
    **세로선 + 15px + opacity .85**, 주석은 16px 그대로 — 시각 위계가 곧 위의 규칙이다.
  - ⚠️ **세리프로 가르지 않는다** — 시안에선 명조였지만 **미니앱엔 한글 세리프가 없다**(`serif`가 안드로이드는
    명조·iOS는 고딕으로 갈려 폰마다 다른 카드가 된다). 웹은 `Gowun Batang`이 이미 로드돼 공짜지만 **본문이
    이미 명조라 서체로는 애초에 못 가른다**. 양쪽 다 크기·농도·세로선이 유일한 신호다.
  - **공백뿐인 인용은 null로 떨어뜨린다**(`Story.of`) — 빈 문자열이 남으면 화면에 **글 없는 인용선**이 그려진다.
    「인용 없음」의 표현은 NULL 하나뿐이라는 불변식을 도메인이 지킨다.
  - **쪽수는 넣지 않았다**(사용자 결정) — ebook은 글자 크기·기기마다 쪽이 달라 적어 봐야 읽는 사람이 못 찾는
    숫자이고, 종이책 판본까지 섞이면 더 어긋난다. 컬럼·입력칸·카드 한 줄이 통째로 없어졌다.
  - **홈 소식의 발췌는 여전히 주석이다** — 인용을 미끼로 걸면 피드가 명언 모음이 되고, 카드에서 인용을 從으로
    둔 위계와도 어긋난다. 회귀 테스트가 이 자리를 못 박는다.
  - **`Story.of`는 4-arg 오버로드를 남겼다** — 호출부가 서른 곳 넘고 전부 「인용 없음」이라, 인자로 `null`을
    하나씩 붙이는 편집은 diff만 늘리고 정보를 주지 않는다.
  - **서재 인라인 미리보기는 공짜로 따라왔다**(같은 `MarginCard` 재사용). 컴포저는 **한 배경 안에 두 칸**이라
    쓰는 동안 보이는 것이 그대로 카드가 된다.
- **측정 중 홈 — 표지를 남기고, 탭을 잠근다** ✅ 코드 완료 / ⬜ 실기기 확인 대기 (2026-08-19) — 아래 독서등의
  후속. 측정을 누르면 「무엇으로 측정할까요?」 섹션이 통째로 사라져 **표지가 없어지고 텍스트 한 줄만 남던 것**을
  고쳤다(사용자 지적). 그 자리에 **「읽는 중」 카드**가 서서 지금 읽는 책의 표지·제목·저자와 그 책 누적 시간을
  보여 준다. 고를 게 없으니 캐러셀일 이유가 없을 뿐, 지금 읽는 책을 보여 줄 이유는 그대로였다.
  - **서버에 `activeBook`(BookOption) 한 필드를 얹었다** — 대시보드가 `activeBookTitle`(제목 문자열)만 주고
    id·표지·저자를 안 줬다. 제목으로 `readingBooks`에서 되찾는 우회는 동명 책에서 틀리고, 측정 중에 그 책을
    완독으로 옮기면 목록에서 빠져 표지가 조용히 사라진다. ⚠️ **`TimerState`에도 넣었다** — 시작 응답이
    그것이라, 빠지면 시작 직후 카드가 빈 채로 섰다가 다음 재조회에야 채워진다. `activeBookTitle`은 웹 SSR이
    쓰고 있어 그대로 뒀다.
  - **탭 잠금** ✅ — 측정 중엔 서재·책방·기록이 잠긴다(사용자 지시, 범위는 **탭만**). 홈 안의 문(여백 문·설정)은
    잠그지 않는다.
  - ⚠️ **홈 탭은 안 잠근다 — 이게 설계의 요점이다.** 넷 다 잠그면 탈출구가 사라진다: 코치마크 투어는
    `flowTabChange` effect가 `onTabChange`를 **직접** 불러 이 잠금을 안 타므로, 투어가 사용자를 서재에
    데려다 놓으면 측정을 끊기 전엔 홈으로 못 돌아온다. 홈을 열어 두면 그 덫이 원천적으로 없고 「내가 있을
    곳은 여기」라는 표시도 함께 된다.
  - 잠긴 탭은 흐려지고(0.35), 누르면 **「측정을 끝내면 이동할 수 있어요」**가 1.8초 뜬다 — 말없이 무반응이면
    고장으로 읽힌다. `disabled`가 아니라 `aria-disabled`인 이유가 이것이다(진짜로 잠그면 클릭이 안 와서
    이유를 말할 기회가 없다). 가운데 측정 액션은 어떤 경우에도 안 잠근다.
  - ⚠️ **감수한 것**: 측정이 길면 그동안 서재·책방·기록에 못 간다. 읽다가 책을 추가하려면 측정을 끊어야 한다 —
    딴짓 차단이 목적이므로 의도된 비용이다.
- **독서등 — 측정 중 홈이 밤이 된다** ✅ 코드 완료 / ⬜ 실기기 확인 대기 (2026-08-19) — 측정을 켜면 **홈의 종이
  자체가 밤**(`#2B251D`)이 되고, 히어로 카드만 스탠드 밑에 펼쳐진 페이지로 남는다. 시안 3안(덮개 스포트라이트 /
  주변 흐리기 / 밤 종이)을 비교해 **C안 「스탠드」**를 골랐다 — 덮개는 아래 글자를 안 읽히게 만들고, 흐리기는
  "어두워진다"가 아니라 "흐려진다"에 가까웠다.
  - **밝게 남는 것은 히어로 카드와 하단 탭바 전체다.** 탭바를 뺀 건 취향이 아니라 규칙이다 — 가운데 원이
    **측정을 끝내는 유일한 버튼**이라 어둠에 잠기면 끄는 법을 잃는다.
  - **그 규칙을 코드가 아니라 구조가 지킨다** — 어두운 토큰을 `body`가 아니라 **`<main>`에만** 얹는다. 탭바는
    `MainTabs`가 화면의 **형제**로 그리므로 사정권 밖이다(캔버스만 `body`가 맡는다 — 캔버스가 곧 body 배경이라
    다른 자리가 없다). 코치마크 스포트라이트가 층 순서로 성립하는 것과 같은 수법이고, 여기선 DOM 바깥이 그
    역할을 한다. `app.test.tsx`가 `<nav>`가 `</main>` 뒤에 오는지 계측한다.
  - **재진입은 어두운 채로**(사용자 결정) — 켜짐 조건이 `tab === 'home' && hasActiveSession` 둘뿐이라 **상태를
    안 든다**. 나갔다 오면 대시보드가 다시 「측정 중」이라 말하고 화면이 어두운 채로 열린다: 「방금 눌렀는지」를
    기억하는 코드가 0줄이라 이 규칙이 공짜로 따라왔다(콜드 스타트 실측 확인).
  - **범위는 홈만**(사용자 결정) — 서재·책방·기록·설정은 그대로 밝다. 전 화면으로 넓히면 표지·잔디·격자를 전부
    밤 종이 위에서 다시 봐야 하는데 그 색은 만든 적이 없다. 측정 중 다른 탭으로 가면 밝은 화면이 튀어나오는
    경계를 감수했다.
  - **컴포넌트를 다시 칠하지 않았다** — 홈의 카드가 이미 거의 다 `var(--adaptive*)`를 소비하고 있어 토큰 한 벌만
    갈아 끼우면 따라온다. 유일한 구멍이던 `ui.tsx` `sectionStyle`의 리터럴 `#FCFAF5`를 토큰으로 바꾼 게 전부다.
    실질 변경은 판단 함수(`lampOn`) + `body` 클래스 토글 + 히어로 표식 한 줄 + css 한 블록.
  - **움직이지 않는다** — 켜고 끌 때 0.45초 한 번 넘어갈 뿐 맥동·호흡이 없다(T-176: 값이 변하는 그림자는
    프레임마다 재페인트). 대비는 눈이 아니라 **카드지 기준**으로 재서 잡았다 — 첫 값 `#9A9080`이 카드 위에서
    4.44:1로 기준에 못 미쳐 `#A0968A`(4.80:1)로 올렸다.
  - ⚠️ **미검증 1건**: 빛 원뿔이 전면 `fixed` 레이어라 실기기 스크롤에 영향이 없는지. 값이 고정된
    그라데이션이라 페인트는 1회지만, 데스크톱에선 원리상 판정되지 않는 클래스다(T-176 계열).
  - ⚠️ 홈 화면이 바뀌므로 **다음 심사 제출 전 `miniapp/screenshots/01-home` 갱신 필요**(측정 중 화면을 담을지는
    별도 판단 — 지금 스크린샷은 측정 전 상태다).
- **기록 탭 — 잔디 아래 날짜별 독서 기록** ✅ 코드 완료 / ✅ 실기기 확인(2026-08-19) (2026-08-14) — 기록 화면이
  잔디·통계·범례에서 끝나 아래가 통째로 비어 있었다. 웹 `/history`가 이미 보여 주던 **날짜(요일) · 그날 읽은
  책 제목 · 읽은 시간**을 잔디 아래에 잇는다. 데이터는 웹 history 섬이 쓰던 `GET /api/history`를 그대로 쓴다 —
  Bearer 토큰이 붙으면 미니앱 시큐리티 체인으로 라우팅되므로 **서버는 한 줄도 안 고쳤다**(`isMiniappApiRequest`).
  웹의 월 ◀▶ 이동은 옮기지 않았다(**A안**, 사용자 선택) — 폰은 세로 스크롤이 자연스럽고, 한 달만 담으면
  "잔디 아래가 빈다"는 원래 불만이 그대로 남는다. 월 머리글(`2026년 8월` + 그 달 합계) 아래 일자 행이 이어지고
  최신 월부터 과거로 계속 붙는다. 책 미지정 세션만 있는 날은 제목 줄을 생략하되 **행은 남긴다**(시간은 있는데
  행이 사라지면 합계가 안 맞아 보인다). 이 목록만 실패하면 잔디는 그대로 두고 아래에만 사유를 남긴다.
  - ⚠️ 기록 화면이 바뀌므로 **다음 심사 제출 전 `miniapp/screenshots/03-history` 갱신 필요**.
- **서재 탭 — 3섹션 세로 나열 → 상태 탭 + 표지 캐러셀 + 시트** ✅ 코드 완료 / ✅ 실기기 확인(2026-08-19) (2026-08-14) —
  서재가 「읽는 중 / 다 읽음 / 읽고 싶어요」 세 섹션을 전부 세로로 쌓아 권수가 늘수록 한없이 길어졌다(사용자 지적).
  ① 세 상태를 **상단 세그먼트 탭**으로 접고(빈 탭도 라벨·권수는 남긴다 — 탭이 사라지면 누르려던 자리가 옮겨 다닌다)
  ② 고르기는 **홈과 같은 표지 캐러셀**을 재사용한다(`BookCarousel`에 `noBookCard`·`metaOf` 두 프롭을 열어 분기 —
  서재엔 「책 없이」 칸이 없고, 아래 한 줄이 저자 대신 `저자 · 읽은 시간 · 공개`다) ③ 캐러셀로 훑기 답답한 권수를 위해
  제목 줄 우상단 **「펼쳐보기」 → 3열 격자 시트**(화면 이동 없이 같은 화면 위, 고르면 캐러셀이 그 책으로 옮겨간다)
  ④ 행마다 펼쳐지던 액션 넷은 **「관리」 시트**로 접었다 — 늘 보이던 「삭제」가 한 탭 거리에 있던 것도 함께 사라지고,
  삭제 확인은 시트 안에서 나머지 길을 감춘 채 받는다. 선택 state는 하나(`selectedId`)뿐이고 탭이 바뀌거나 그 책이
  다른 상태로 옮겨가면 `resolveSelected`가 첫 책으로 되돌린다(탭별 선택을 따로 들지 않는다). 딤·바텀시트 껍데기는
  `ui.Sheet`으로 꺼내 홈 태깅 시트까지 셋이 공유한다. TDD 13테스트 RED → GREEN(413/413, tsc 클린).
  - ⚠️ 서재 화면이 바뀌므로 **다음 심사 제출 전 `miniapp/screenshots/02-library` 갱신 필요**.
  - **후속 fix — 캐러셀 메타 두 줄(저자 / 「읽은 시간 · 공개」)** ✅ (2026-08-14, 실기기 제보) — 알라딘 저자
    문자열이 긴 책에서 한 줄이 되접히며 읽은 시간·공개가 저자 이름 사이에 끼어 들었다. `metaLine`이 `\n`으로
    나누고 렌더가 `white-space: pre-line`으로 살린다(둘은 한 쌍 — 스타일 배선도 렌더 단언으로 못 박음).
  - **후속 fix — 「펼쳐보기」로 고른 책으로 캐러셀도 옮겨간다** ✅ (2026-08-14, 사용자 제보) — 위 ③의 "고르면
    캐러셀이 그 책으로 옮겨간다"가 실제로는 **제목 줄만** 갈리고 트랙은 옛 자리에 남았다(트랙을 옮기는 길이
    마운트 1회와 표지 탭 둘뿐이었다). `BookCarousel`에 `selectedId` 동기화 effect를 두어 밖에서 바뀐 선택도
    따라가게 했다 — 손가락으로 민 결과엔 `recenterIndex`가 `null`을 줘 재스크롤하지 않는다(스냅 강탈 방지).
  - **후속 fix — 검색 결과를 탭하면 담을 곳을 먼저 묻는다** ✅ 코드 완료 / ✅ 실기기 확인(2026-08-19) (2026-08-19, 사용자 제보) —
    「책 추가」에서 결과를 탭하면 되묻지 않고 「읽는 중」으로 들어갔다(「가장 잦은 의도」라는 이유로 넣은 단축).
    읽고 싶어 담은 책까지 읽는 중이 된 뒤 서재에서 다시 옮겨야 해 **한 탭을 아끼려다 세 탭을 물렸다**.
    위 ④ 관리 시트와 **같은 껍데기(`ui.Sheet`)·같은 어휘(`SECTIONS`)**로 `AddStatusSheet`을 세워 세 곳을 다
    고르게 한다 — 순서가 곧 자주 쓰는 순이라 「읽는 중」이 첫 줄이다. 배선(`onAdd(row, status)` → `addBook`)은
    이미 상태를 받고 있어 **서버는 한 줄도 안 고쳤다**(하드코딩된 `'READING'` 한 줄이 원인). 대안이던 「줄 안
    버튼 2개」는 390px 폭에서 「다 읽음」 자리가 없어, 「상단 모드 토글」은 모드를 잊고 잘못 담는 사고 때문에 뺐다.
  - **후속 fix — 이미 서재에 있는 책을 형태로 알린다** ✅ 코드 완료 / ⏳ 실기기 확인 대기 (2026-08-19, 사용자 제보) —
    담긴 책의 안내를 저자 이름 뒤에 「 · 이미 서재에 있어요」로 이어 붙였는데, 저자와 같은 `st12 · grey600` 한 벌이라
    **이름의 일부처럼 읽혀** 훑으면 그냥 지나쳤다. 더 큰 문제는 그 행이 `disabled`라는 것 — 안내를 놓치면
    **눌러도 아무 일이 없는 칸**이 되어 앱 고장으로 읽힌다. 그래서 신호를 두 겹으로 나눴다: **읽어야 아는 것**
    (세이지 칩 「서재에 있어요」)과 **읽지 않아도 보이는 것**(눕힌 종이 + 표지 도장 + 접힌 오른쪽 위 모서리 =
    `.book-owned`). 형태가 먼저 말하고 문구가 확인해 주는 순서다. 시안 3안(문구 강조 / 박스 자체 / 「서재에서
    보기」 손잡이)을 그려 비교했고 형님이 **박스 + 칩**을 골랐다 — 손잡이 안은 서재 화면 이동 배선이 필요해
    별건으로 남겼다. 계측을 위해 결과 행을 `SearchResultRow`로 꺼냈다(정적 렌더 하니스에선 검색 응답이 안 돌아
    `results`가 영영 `null`이라 `BookSearch` 통째로는 단언을 세울 수 없다 — `AddStatusSheet`와 같은 처지).
    TDD 5건 RED → GREEN(765/765, `tsc` 클린). **애니메이션은 넣지 않았다** — 표지를 품은 행이라 T-176과 같은 자리다.
    - ⚠️ 「책 추가」 화면은 스토어 스크린샷 4종(홈·서재·기록·목표)에 없어 **갱신 대상 아님**.
    - **웹도 같은 언어로 맞춤** ✅ (2026-08-19) — 같은 검색 API의 같은 `owned` 플래그를 그리는 웹 두 자리
      (책장 검색 결과 `BooksApp` · 대시보드 책 시트 `BookPickSheet`)가 `📚 이미 책장에 있음` / `📚 있음`으로
      **이모지 + 서로 다른 문구**였다. 기본 이모지를 걷고(체크는 CSS `::before` mask로 그린다) 흐린 회색
      글자를 세이지 칩으로 올려 미니앱과 같은 표기로 통일했다. ⚠️ **문구는 「책장」을 유지한다** — 웹에서
      「서재」는 작가 캐릭터가 사는 마을을 가리켜(`garden.html`·랜딩) 여기서 미니앱 어휘를 쓰면 뜻이 충돌한다.
      즉 맞춘 것은 **표기 방식이지 명칭이 아니다**. jsdom 마운트 테스트 4건(RED → GREEN, 416/416).
    - **웹 제품 UI의 기본 이모지 전량 정리 + 가드** ✅ (2026-08-19) — 위 배지를 고치다 드러난 것: 미니앱은
      2026-08-18에 걷어냈는데 **웹은 61줄이 그대로**였다(admin 11줄 포함 전량 정리). 미니앱
      `no-emoji.test.ts`를 웹으로 이식해 **가드부터 세우고**(실패 목록이 곧 작업 리스트) 걷어냈다.
      53줄은 옆 텍스트가 이미 뜻을 져서 이모지만 제거했고, **이모지가 유일한 표시이던 8곳은 글자로 풀었다**
      (도감 「잠김」 · 애정도 「정 12/20」 · HUD 「먹이 3」 · 랜딩 아바타 이니셜 원 등). 서버
      `AuthorCharacter.emoji`는 **필드·시드·DB를 건드리지 않고** 뷰 4곳의 폴백만 이름 첫 글자로 바꿨다.
      `🌱🌿`는 미니앱과 같은 예외(핵심 은유 + 서버 `GrowthStage` 값) — **자체 일러스트로 제대로 만드는
      자리라 백로그로 남긴다**. 420/420 그린, 11섬 재빌드, `bootRun`으로 5개 화면 실측.
      - ⚠️ 그 실측이 **가드가 GREEN인 채 놓친 자리**를 잡았다(T-195) — 스캔 뿌리에 없던
        `static/pwa-install.js`. 소스 스캔 가드를 세울 때는 **GREEN 상태에서 실행 화면을 한 번 훑는다**.
- **책방(프로필)도 같은 캐러셀로** ✅ 코드 완료 / ✅ 실기기 확인(2026-08-19) (2026-08-15, 사용자 지적) — 서재만 고치고
  책방은 옛 세로 카드 나열이 남아 "쭈루룩 길게" 보였다. 공개 책 목록을 **표지 캐러셀 + 「펼쳐보기」 격자 시트**로
  바꿔 서재와 같은 문법으로 맞췄다. `GridSheet`·`resolveSelected`·`handleStyle`을 서재에서 export해 그대로
  재사용한다(격자 시트는 표지·제목만 쓰므로 타입도 `{id,title,coverUrl}`까지만 받게 완화 — 두 화면이 한 구현을
  공유). 메타는 `저자 / 상태 · 읽은 시간` 두 줄(`profileMetaLine`). **상태 탭은 넣지 않았다** — 책방엔 이미
  책BTI 태그 드릴다운이라는 필터 축이 있고 서재 탭의 목적(내 책 상태 이동)은 남의 책방에 없다. 태그로 목록이
  갈리면 캐러셀을 재마운트하고(`key={activeTag}`) 사라진 선택은 `resolveSelected`가 첫 책으로 되돌린다.
  - ⚠️ 책방 화면이 바뀌지만 스토어 스크린샷 4종(홈·서재·기록·목표)엔 책방이 없어 **갱신 대상 아님**.
  - **후속 fix — 「펼쳐보기」를 제목 줄에서 「공개한 책 N」 줄로 내린다** ✅ (2026-08-15, 사용자 제보) —
    서재를 그대로 따라 손잡이를 `Screen`의 제목 줄에 뒀는데, 책방은 제목과 캐러셀 사이에 핸들·성향·태그·
    팔로우가 깔려 **손잡이와 그 대상(책)이 화면 절반쯤 떨어져** 보였다. 같은 컴포넌트를 옮겨 심을 때
    화면의 헤더 두께가 다르면 자리도 다시 정해야 한다는 것 — 목록 바로 위 줄이 그 자리다.
  - **후속 fix — 제목 옆 ← 를 없앤다** ✅ (2026-08-15, 사용자 제보) — 배경도 테두리도 없는 화살표 글자라
    **버튼으로 안 읽혔다**(직전 홈 손잡이 2건과 같은 진단 — 「딱 봐도 버튼」). 원래 세운 이유였던 "긴 목록
    끝까지 스크롤해야 출구를 만난다"도 목록이 캐러셀로 바뀌며 사라져(하단 「돌아가기」가 한 화면 안에 들어온다),
    모양을 고치는 대신 지웠다. 출구는 「돌아가기」·플로팅 탭바·하드웨어 back 셋. 로딩/에러 화면의 ← 는
    남긴다 — 거긴 로딩 중 화면 안의 유일한 출구다.
- **책방 상단을 인스타 프로필 문법으로 접는다** ✅ 코드 완료 / ✅ 실기기 확인(2026-08-19) (2026-08-16, 사용자 시안) —
  앞의 두 후속 fix로 손잡이 자리는 고쳤지만 **상단 자체의 두께**는 그대로였다: 큰 제목(「…님의 책방」)·검색바·
  스토리·전폭 카운트 버튼 2개·10줄짜리 성향 문단이 차례로 쌓여, 정작 이 화면의 본체인 「공개한 책」이 첫 화면
  밖(플로팅 탭바 아래)으로 밀렸다. 사용자가 인스타/스레드 프로필을 시안으로 주고 그 상단부만 옮기게 했다 —
  **아바타 + 카운트 3개(공개 책·팔로워·팔로잉) 한 줄 → 닉네임 → @아이디 → 성향 bio(3줄 접기·「더보기」) → 태그**.
  큰 제목은 지웠다(닉네임이 이름 줄로 내려와 같은 정보를 반이 안 되는 높이로 말한다). 아바타는 스토리 링과 같은
  이니셜 원을 쓰되 **링은 두르지 않는다** — 이 화면 위 스트립에서 링은 "새 스토리 있음"의 뜻이라 신원 아바타에
  두르면 그 뜻이 흐려진다. 카운트는 `#788`의 규칙을 그대로 지킨다(내 책방만 버튼, 남의 책방은 못 누르는 숫자).
  - **공개 책 수의 출처는 `profile.books`** — 화면이 들고 있는 `books`는 태그 드릴다운으로 갈리는 목록이라
    그걸 세면 필터마다 상단 숫자가 요동친다. 경계 테스트로 못 박았다.
  - **곁들여 잡은 것 — 「더보기」가 한 화면에 둘** : bio 접기 손잡이를 달자 남의 책방의 차단·신고 진입 버튼과
    이름이 겹쳤다(테스트가 먼저 잡았다). 안전 진입 쪽을 **「신고·차단」**으로, 하는 일로 부른다.
  - **후속 — 검색 진입바를 스토리 줄 오른쪽 끝 아이콘으로** ✅ (2026-08-16, 사용자 승인 A안) — 시안 하단부의
    나머지 한 조각. 전폭 알약이 최상단 한 줄(≈55px)을 통째로 먹던 것을 **스토리 스트립과 같은 줄**로 접는다.
    스토리도 검색도 "사람"을 다뤄 층위가 맞고, 줄을 새로 만들지 않아 절약분이 온전히 남는다.
    ⚠️ **토스 셸 헤더(`‹ 북타이머 ⋯ ✕`)는 앱 소유라 인스타처럼 헤더 우측에 못 얹는다** — 화면 안에서 가장
    가까운 줄을 그 자리로 삼은 이유다. 대안 B(이름 줄 오른쪽)는 그 블록이 "나"의 신원인데 검색은 "남 찾기"라
    층위가 어긋나 접었다. 아이콘은 스토리 링과 같은 지름(56)에 캡션 「친구 찾기」를 남긴다 — 돋보기 하나로는
    무엇을 찾는지가 안 서고 옆이 사람 링이라 책 검색으로도 읽힌다. 스크린리더 이름은 옛 문구 그대로
    (`아이디로 친구 찾기`) — "아이디로 찾는다"가 이 검색의 유일한 사용법이다. 스트립만 스크롤하고 아이콘은
    구분선 오른쪽에 고정된다(친구가 많아져도 검색이 밀려 나가지 않게). 폰 폭 390 실측: 행 350 = 스트립 273 +
    gap 10 + 검색 67, 겹침 0.
    - ⚠️ **발견성은 확실히 떨어진다** — 최상단 전폭이 주던 "여기서 사람을 찾는다"는 신호가 아이콘으로 줄었다.
      팔로우가 아직 한 자릿수인 초기 단계라 이 교환을 사용자에게 알리고 승인받았다. 검색 유입이 눈에 띄게
      줄면 되돌릴 자리다.
  - **되돌림 — 전폭 검색바로** ✅ (2026-08-18, 사용자 결정) — 바로 위 「되돌릴 자리」가 그 자리다. 다만 계기는
    검색 유입 지표가 아니라 **모양**이었다: 짝이던 스토리 스트립이 은퇴하자 아이콘이 「오른쪽 끝 정렬인데 왼쪽은
    빈」 줄로 홀로 남았고, 책방 탭 루트는 `onBack`도 `title`도 없어 **화면의 첫 요소가 우측 끝 원 하나**가 됐다.
    전폭은 한 줄을 다 쓰므로 그 정렬 문제 자체가 없다.
    ⚠️ **당시 폐기 논거(「그 줄만큼 책이 밀린다」)는 지금 형태엔 안 맞았다** — 캡션이 붙으면서 아이콘 쪽이 세로로
    **더** 먹고 있었다(원 56 + 여백 4 + 캡션 ≈ 80px 대 바 44px). 되돌리는 쪽이 오히려 책에 자리를 돌려준다.
    위 「토스 셸 헤더에 못 얹는다」는 제약은 그대로고, 화면 안 첫 줄을 쓰는 것도 그대로다 — 바뀐 건 그 줄의 **모양**뿐.
    - **입력창이 아니라 시트를 여는 가짜 바다** — 인라인 form이면 결과 패널이 내 책방 본문을 통째로 갈아끼워야
      하는데 그게 애초에 검색을 시트로 뺀 이유다. 테스트가 `<input>` 부재로 못 박는다.
    - **남의 책방에는 없다**(사용자 결정) — 셸이 남의 책방 분기에 `header`를 안 넘겨 이미 그랬고, 이번에 단언을
      세웠다. 전폭으로 커져 「상단이 허전하니 여기 두자」는 유혹이 실제로 생기는 자리라 부정 단언 홀로 두지 않고
      긍정 짝(header를 주면 그린다)과 함께 세웠다.
    - **후속 — 돋보기 이모지를 빼고 문구를 가운데로** ✅ (2026-08-18, 실기기 확인 후) — 아래 「기본 이모지 걷기」의
      첫 자리다. 문구가 이미 의미를 다 말해 아이콘 없이 잃는 게 없다.

- **기본 이모지를 제품 UI에서 걷는다** ✅ (2026-08-18, 사용자 지정)
  - **왜**: 불쾌감의 원인은 이모지라는 **형식이 아니라 「만들지 않고 집어왔다」는 표식**이다. 상용 앱은 컨셉에 맞는
    아이콘을 자체 제작하는데, 흔한 기본 이모지가 붙어 있으면 「AI가 만든 화면」으로 읽히고 사람들은 그 인상을
    불쾌하게 여긴다 — 기능이 멀쩡해도 그 표식 하나로 신뢰가 깎인다(사용자 판단).
  - **두 갈래 중 「안 쓴다」를 택했다**(자체 아이콘 세트 제작이 대안이었다). 근거: ① 자체 세트는 만드는 비용이
    아니라 **유지** 비용이 문제다 — 새 기능마다 같은 선 굵기·광학 크기로 계속 그려야 하고, 그 규율이 끊기면
    어정쩡한 자체 아이콘이 기본 이모지보다 **더** 아마추어로 보인다(실패 방향이 더 나쁘다). ② 이 앱은 활자가
    주인공이라(종이톤 + 세리프 제목) 아이콘을 덜어내는 쪽이 컨셉과 같은 방향이다. ③ 문제의 자리 대부분은 옆에
    문구가 있어 아이콘이 의미를 보태지 않았다. ④ 어떤 자리는 자체 해법(`CoverInitial`)이 이미 있는데 **안 닿았을 뿐**이었다.
  - **걷어낸 여섯 자리**: 책방 검색바 `🔍`(제거 + 문구 가운데) · 홈 안내 배너 `👋`(제거) · 홈 「책 없이」 카드
    `⏱️`(점선 상자가 이미 「표지 아님」을 말한다) · 설정 닉네임 저장 안내 `✅`(제거) · 무표지 책 `📚`(→ `CoverInitial`) ·
    홈 소식 피드 `✅📖✍️`(→ **자체 팔레트 색 점**).
    - **피드만 지우지 않고 색으로 바꿨다** — 여러 줄이 세로로 쌓여 **훑어보는** 자리라 아이콘이 제 값을 하던
      유일한 곳이다. 세이지(완독)·베이지(시작)·연보라(글)는 표지 색 팔레트와 같은 계열이라 기성품 표식 문제가 없다.
    - **덤 — 삼항 세 개가 사라졌다**: `BookCover`가 `title`을 받아 자리 채움을 `CoverInitial`에 위임하면서,
      호출부(홈 캐러셀·서재 상세·여백 머리)가 손으로 하던 `coverUrl !== null ? … : …` 분기를 흡수했다.
  - **가드는 소스 스캔 테스트**(`miniapp/src/no-emoji.test.ts`) — 자리마다 고쳐 봐야 다음 기능에서 무심코 다시
    집으면 인상은 되돌아온다. 실제로 이 가드가 **손으로 훑을 때 놓친 여섯 번째 자리**(설정 `✅`)를 잡았다.
    주석은 대상이 아니다(개발자용). 허용 목록은 `✕`(기호지 이모지가 아니다)와 `🌱🌿`.
  - ⏸ **식물 이모지(`🌱🌿`)는 남겼다 — 여기는 「안 쓴다」가 아니라 「제대로 만든다」가 답이다.** 「정원·마을」이라는
    이 앱의 핵심 은유이고, 서버가 내려주는 `growthStageEmoji`의 값이며, 반복 노출된다 — 자체 일러스트가 진짜
    브랜드 자산이 되는 유일한 자리다. 서버 필드 변경 + 단계별 그림 세트가 필요해 별도 작업으로 남긴다.
  - **후속 — 스토어 스크린샷 재촬영** ✅ (2026-08-18, 심사 제출 점검) — 걷어낸 이모지가 콘솔 그림엔 그대로
    남아 있었다(`02-feed`의 소식 세 줄 · `01-home`의 「책 없이」). `shoot.mjs` 한 방으로 6장 재촬영 —
    `03-library`·`05-goal`은 바이트째 그대로였고(안 바뀌었다는 증거), 책방은 스토어 컷에 없어 전폭 검색바는
    영향이 없었다. ⚠️ **「출시 직전 점검」이 낡음을 잡은 게 3회 연속**이라, 화면 바꾼 PR에서의 sweep에
    기대지 말고 **점검을 심사 제출 절차의 고정 단계**로 둔다.
  - ✅ **심사 반려 — 사유가 이 변경에 안 닿았다 → 재제출로 해소** (2026-08-18, 배포 ID `01a0140a-…`) — **결말: 코드를 고치지 않고 같은 번들을 재제출해 통과했고, 이후 08-19·08-22·08-25 배포가 전부 심사를 통과했다.** 이 판단이 T-185가 됐다(반려 사유가 직전 통과분 diff에 안 닿으면 고치지 말고 재제출). ⚠️ 「문의 대기」인 채 다음-할-일 마커를 달고 일주일 넘게 남아 있었다 — **끝난 대기는 스스로 닫히지 않는다**(2026-08-25 마커 감사). 이하 당시 기록: 반려 사유는
    「탭바를 브랜딩 가이드의 플로팅 형태로」인데, 직전 **통과·출시** 버전 `20260817-71` 이후 `BottomTabBar`는
    무변경이다(이번 diff = 책방 상단 검색바 + 이 이모지 걷기). 스테일 번들도 `deploy.sh`의 `.ait`↔`dist` 바이트
    대조로 배제된다 — 남는 설명은 **심사 판정 편차**다(08-17에도 19:52 반려 → 21:01 통과). **코드는 고치지 않는다.**
    채널톡에 통과 버전 번호를 대고 「어긋난 지점 + 근거 스크린샷」을 요구했고(08-18 접수, 업무시간 외라 익일 회신),
    병행해 같은 번들 재검토 요청을 건다. 경위·감별 순서는 T-185.
    ⚠️ **이미 출시된 UI(탭바 가운데 측정 원)를 되돌리자는 제안은 철회했다** — 그 원은 `20260817-71`에 실려 라이브였다.
  - **후속 — 공개 책을 캐러셀에서 3열 격자로** ✅ (2026-08-16, 사용자 승인 B안) — 시안의 마지막 조각.
    캐러셀은 한 번에 한 권만 보여 주고 나머지는 「펼쳐보기」 시트를 열어야 했다 — 책장을 훑는 화면인데
    왕복이 끼어 있었다. **그 시트를 본문에 그대로 펼친다**: `GridSheet`의 격자 본체를 `BookGrid`로 꺼내
    책방이 인라인으로 쓰고, 서재는 여전히 시트 안에서 같은 컴포넌트를 쓴다(새로 만든 물건 0).
    - **A안(표지만·인스타 그대로)을 접은 이유**: 메타를 되찾으려면 「탭 → 책 정보 시트」라는 **이 화면에
      없던 층**을 새로 만들어야 하고, 남의 책방에서 그 시트에 무엇을 담을지(구매 링크? 팔로우 유도?)는
      별도 설계 판단이라 지금 결정에 딸려 오면 안 됐다. B안은 자리만 옮긴 것이라 되돌리기도 싸다.
    - ⚠️ **상태·읽은 시간은 사라진다** — 캐러셀 아래 있던 메타 자리가 격자엔 없다. 알고 한 교환이고
      테스트로 못 박았다(`다 읽음`이 더 이상 안 실린다). 아쉬우면 되살릴 자리는 격자 셀 제목 아래 한 줄.
    - `onPick`을 주지 않으면 각 칸을 **버튼으로 만들지 않는다** — 책방 격자는 보기만 하는 목록이라,
      버튼처럼 생겼는데 눌러도 아무 일이 없으면 그게 곧 죽은 UI다(카운트 손잡이 #788과 같은 규율).
    - 함께 걷어낸 것: `selectedId`·`gridOpen` 상태와 그 프롭 4개, 격자 시트의 뒤로가기 스택 한 겹,
      `profileMetaLine`(캐러셀 전용이라 동반 사망).
  - **후속 fix — 「광고 보고 다시 분석하기」와 「보관함에서 비교하기」를 한 줄에** ✅ (2026-08-16, 사용자 지적) —
    광고는 전폭 버튼, 보관함은 그 아래 오른쪽 끝 알약(`handleStyle`)이라 **같은 층위의 동작 둘이 서로 다른
    물건처럼** 흩어져 있었다. 폭을 반씩 나눠 나란히 세우고 보관함도 같은 `Button`(weak·small)으로 통일한다.
    행의 유무·칸 수는 순수 함수 `personalityActions`가 한 곳에서 정한다 — 두 술어를 화면에서 각각 부르면
    "행은 그렸는데 칸이 0개"처럼 어긋난다. 하나만 자격이 되면 그 칸이 전폭(빈 칸을 남기지 않는다).
    ⚠️ 목 모드는 광고 그룹 ID가 빈 값이라(`.env.mock`, 브라우저엔 광고 SDK가 없다) **두 칸이 함께 선 모습을
    목으로 볼 수 없다** — 임시로 ID를 채워 폰 폭 390 기준을 실측하고(각 171×32px·`top` 동일 = 줄바꿈 없음)
    되돌렸다. 나란히 세울 때의 진짜 리스크는 문구가 좁은 칸에서 두 줄로 접히는 것이라 이 실측이 게이트였다.
- **독서 성향(책BTI) 추출 — 리워드 광고 관문** ✅ 코드 완료 / ✅ 출시 (2026-08-15 코드 · 2026-08-17 출시) / ✅ 실기기 확인(2026-08-19) — 내 책방은 성향을
  **보기만** 했다(`profile.personality`는 저장된 대표의 읽기 전용 복사). 생성 경로가 미니앱에 없어, 웹에 들른 적
  없는 토스 사용자에겐 영영 빈 자리였다. 결과가 보이는 그 자리에 「광고 보고 성향 분석 받기」를 세운다(대표가
  있으면 「광고 보고 다시 분석하기」). **진입점은 내 책방 단독** — 홈은 성향을 표시하지도 않아 거기 버튼을 두면
  맥락 없는 광고 노출이고, 최근 홈 다이어트 흐름과도 역행한다.
  - **광고 그룹은 부채 청산과 분리한다** ✅ (2026-08-23 — ~~같은 리워드 그룹을 쓴다~~에서 정정). 콘솔 리포트의
    단위가 **광고 그룹**이라, 포맷이 같다고 한 그룹을 공유하면 두 지면의 노출·시청·수익이 **한 줄로 합산돼**
    어느 자리가 버는지 영영 못 가른다. 전면형을 처음부터 별도 그룹으로 뗀 것과 같은 이유인데 리워드끼리만
    기준이 갈려 있었다. 신설 그룹 `ait.v2.live.75558b51a0444cc7`(보상 표기 **「성향 분석 / 1」** — 부채 쪽
    **「밀린 하루 지우기 / 1」**과 달라야 콘솔 표기도 실제 보상과 맞는다. **두 값은 콘솔 실측이다(2026-08-23)** —
    설계 문서(2026-08-11)가 적은 「밀린 하루 지우개」는 등록된 값과 다르다)을 `VITE_PERSONALITY_AD_GROUP_ID`로 주입하고
    책방만 그쪽을 본다. ⚠️ **config-gate 순서**: 빈 값이면 성향 광고 버튼이 통째로 사라지므로 콘솔 그룹의
    구글 등록이 끝난 뒤에 번들을 올린다.
  - **상한 = 중간안 C(사용자 확정)** — 웹 무광고 3회/일 그대로 + 광고 경로는 하루 **총량 10회**. 판정을 정한 건
    LLM 원가 실계산이다: `gemini-2.5-flash` + thinking 비활성이라 1회 ≈ 2원인데 리워드 광고 1회 수익 ≈ 10원 —
    "상한 3회로 LLM 비용을 방어한다"는 전제가 무너졌다. 다만 SDK에 **서버사이드 보상 검증이 없어** "광고를 봤다"는
    클라 주장뿐이라, 남용 최대 손실을 계정당 하루 ≈20원으로 묶는 **총량 상한이 유일한 실질 방어**로 남는다.
  - **카운터는 하나**(스키마 변경 0 · Flyway 불필요) — 천장만 파라미터로 뺐다. 웹 3회는 총량 10의 앞 3칸이라,
    미니앱 광고 분석이 그날 웹 무광고 칸도 함께 소진한다. 이 비대칭은 "하루 분석 총량은 하나"라는 정신 모델로
    받아들이기로 했다(사용자 확정 2026-08-15) — 카운터 분리는 컬럼 추가 + 마이그레이션이라 값에 비해 비싸다.
  - **서버 신규는 둘**: 부작용 없는 `GET /api/personality/status`(콜드스타트·대표 보유·총량 기준 잔여)와
    `POST /api/personality/ad-refresh`(천장만 총량). 웹 `POST /refresh`는 **한 글자도 안 건드렸다**(천장 3 불변).
    status가 따로 필요한 이유 = 기존 `GET /api/personality`는 히스토리가 비면 **첫 분석을 LLM으로 부트스트랩
    생성**해 광고 관문을 무력화하고 로드도 최악 25초다. `/api/personality/**`는 이미 `/api/**`+Bearer라 미니앱
    stateless 체인이 자동으로 맡는다 — SecurityConfig·CORS 무변경(테스트로 고정).
  - **관문은 광고 앞에 선다** — 콜드스타트·잔여 0이면 버튼을 아예 안 그리고 안내만 둔다("광고만 보고 보상 없음"
    차단). status 조회가 실패하면(서버 미배포 포함) 관문 자리를 통째로 접는다(fail-closed) → **배포 순서 자유**.
  - **대표 승격 체이닝** — 서버 `reanalyze`는 새 분석을 후보로만 추가하므로(대표 불변), 히스토리·선택 UI가 없는
    미니앱은 최신 행이 미선택이면 기존 `POST /select/{id}`를 이어 대표로 올린다(서버 변경 0). 안 그러면 광고를
    보고도 책방 표시가 안 바뀐다. LLM 실패(응답에 서술 없음)면 광고는 이미 소비됐으니 **광고 없는** 재시도를 연다.
  - ⚠️ 하니스 사각: 정적 렌더라 클릭이 안 돈다 → 노출 조건은 순수 술어 `showPersonalityAdButton`, 광고→분석
    흐름은 순수 async `claimPersonality`로 꺼내 계측했다(홈 `claimDebtWaiver` 선례). **광고 실시청·이탈은
    실기기가 게이트**(목 모드로는 원리상 불가). 서버 1583건 · 미니앱 506건 통과, tsc·빌드 클린.
  - ⚠️ 신규 광고 노출 화면이라 **심사 제출 필요**. 진입점이 소셜 탭 안 책방이라 스토어 스크린샷 4종(홈·서재·
    기록·목표)엔 안 나온다 — 갱신 대상 아님.
- **독서 성향 보관함 — 히스토리 비교 + 대표 되돌리기** ✅ 코드 완료 / ✅ 출시 (2026-08-15 코드 · 2026-08-17 출시) — 위 광고 관문의
  후속. 사용자 제보: **"광고를 봤는데 문구가 바뀐 건지 안 바뀐 건지 모르겠다."** 원인은 둘인데 주범은 비교 대상이
  화면에 없다는 것이다 — 미니앱 책방은 대표 서술 **하나만** 보여주는데(서버는 이미 최대 3건 보관), 책장이 크게
  안 바뀌면 새 서술도 미묘하게만 달라 교체 자체가 지각되지 않는다. 웹은 캐러셀·시각 라벨·대표 배지로 그 차이를
  드러내지만 미니앱엔 그 장치가 전무했다. 성향 서술 아래 「보관함에서 비교하기」 손잡이를 달고, 시트에 최근 분석
  최대 3건을 **세로로 나란히**(시각 라벨 · 「지금 대표」 배지 · **서술 전문** · stale 캡션) 펼친다. 미대표 카드의
  「이 성향으로 바꾸기」는 기존 `POST /select/{id}` 재사용.
  - **자동 대표 승격은 유지**(사용자 확정) — 광고를 본 새 결과는 그대로 즉시 대표가 되고, 보관함은 **비교와
    되돌리기**를 맡는다. 웹처럼 후보로만 두면 "광고를 봤는데 책방이 그대로"라 지금 불만을 오히려 제도화한다.
  - **서버 신규 엔드포인트 0 · 스키마 변경 0** — 기존 `GET /status` 응답에 `entries`를 additive로 얹었다.
    `history()`가 `@Transactional(readOnly)`·LLM 무호출이라 **이 GET의 무부작용 계약이 그대로 유지**된다(테스트로
    고정). self 책방 로드마다 이미 부르는 GET이라 fetch 추가도 0. 보관 상한 3(`MAX_HISTORY`)은 요구("똑같이 3개
    정도")와 이미 일치해 손대지 않았다. 웹 경로(`GET /api/personality`·`POST /refresh`)는 무변경.
  - **손잡이는 2건 이상일 때만** — 1건은 책방에 걸린 그 문구 하나뿐이라 열 이유가 없다. 0건·`entries` 부재(옛
    서버)·status 미수신도 같이 숨는다(광고 버튼과 같은 fail-closed → 배포 순서 자유).
  - 시트 껍데기는 `ui.tsx`의 자체 `Sheet` 재사용(TDS `BottomSheet`은 포털이라 정적 렌더에서 마크업이 빈다),
    뒤로가기는 `useBackClose`가 시트부터 닫는다. 서술은 **줄이지 않는다** — 줄이면 정작 달라진 대목이 잘려
    비교라는 목적이 사라진다. 목 픽스처도 서술이 서로 다른 시드 2건 + 재분석 순환으로 바꿔, **목 모드만으로
    비교 화면을 실제로 볼 수 있다**(광고 SDK와 무관한 기능이라 실기기 재검증 불필요).
- **「소셜」 탭 → 「책방」 탭 개편** ✅ 코드 완료 / ✅ 출시 (2026-08-15 코드 · 2026-08-17 출시) — 내 책방까지 **두 탭**이었다
  (소셜 → 「내 책방 보기」). 게다가 책방 헤더의 `팔로워 N · 팔로잉 N`은 순수 텍스트라 눌러도 아무 일이 없어,
  누가 팔로우하는지 보려면 다시 소셜 화면으로 돌아와야 했다. 탭을 **책방**(storefront 아이콘)으로 바꾸고 누르면
  **곧장 내 책방**이 뜨게 했다 — 옛 소셜 중간 화면은 사라진다.
  - **서버 API 신설 0.** `GET /api/follow-list`는 `Principal` 본인 목록만 주므로 카운트 클릭은 **내 책방에서만**
    열린다(`followCountsOpenable`) — 남의 책방은 지금처럼 숫자 텍스트다. 남의 팔로우 목록 열람은 프라이버시
    판단이 필요한 별개 결정이라 범위 밖. `dev-mock.ts`도 무변경(엔드포인트 집합이 그대로다).
  - **얇은 셸 구조** — `Social.tsx` → `Bookshop.tsx`로 개명하고, 셸은 전체 화면 전환(스토리 뷰어·작성기·남의
    책방)과 시트 셋만 든다. 본문은 `Profile`이 그대로 그린다(프롭 3개 추가: `header` 슬롯 · `onOpenFollowList` ·
    `onBack` optional). `Profile`을 탭 루트로 승격하는 안은 714줄 파일을 더 비대하게 만들어 기각.
  - **부속 목록은 시트** — 팔로워/팔로잉은 `FollowListSheet`, 검색은 책방 상단 **슬림 진입바 → `SearchSheet`**
    (인라인 form을 두면 결과 패널이 내 책방 본문을 통째로 갈아끼워야 한다). 둘 다 **순수 표시**고 데이터는 셸이
    받아 프롭으로 준다 — 정적 렌더 하니스가 0명/N명 분기에 닿는 유일한 길(`ArchiveSheet`와 같은 이유).
  - **스토리 스트립은 내 책방 상단으로, 차단 목록은 설정으로**(사용자 결정). 차단 해제는 미니앱의 유일 경로라
    이설 중 증발하면 안 돼 `BlockedSection`을 테스트로 못 박았다(0명이면 섹션 자체를 안 그린다).
  - **핸들 없는 계정(login_id=null) 경로 보존** — 옛 `MyShelfEntry`가 하던 안내+「아이디 만들기」를 탭 루트의
    null 분기가 물려받는다. 검색·스토리는 그 분기에도 그린다(남의 책방 구경은 핸들 없이도 된다).
  - **뒤로가기 깊이 어긋남(T-166) ✅ 고쳤다** — 시트를 닫으면서 다른 화면을 여는 교체 경로에서
    `history.back()`(비동기)이 새 `pushState`를 되삼켜, 서브뷰가 열려 있는데 히스토리 인덱스는 앱 루트였다
    → 그 화면의 back·「돌아가기」가 **미니앱을 종료**시켰다. 이번 개편 이전부터 있던 `back.ts` 결함이지만
    (스토리 뷰어 → 「책방 보기」가 같은 경로 — 코드 무변경으로 실측), 남의 책방 진입이 전부 시트 경유가 되며
    주 경로로 올라와 함께 고쳤다. 해법은 **엔트리 물려주기** — `close()`가 반납을 마이크로태스크로 미루고,
    같은 커밋의 `open()`이 `handoff`를 물려받아 push도 back도 하지 않는다(깊이 보존). `selfPops`는 콜백만
    지켰지 깊이는 못 지켰다는 것이 이 함정의 핵심이다.
  - **상단 재배치 ✅ (2026-08-15 후속, 사용자 시안)** — 검색·스토리는 **화면 소속이 아니라 그 위에 얹히는
    도구**라 제목보다 위로 올렸다(`Screen`에 `above` 슬롯 신설, 순서는 검색 → 스토리). `@아이디`는 카운트와
    한 줄로 섞여 있어 닉네임과 안 붙어 보였다 → 제목 **바로 아래 밀착**(`subtitle` 슬롯 — 있으면 제목 행
    여백이 20 → 3). 카운트는 밑줄 친 작은 글자에서 **테두리 있는 큰 버튼 두 개**(전폭 반씩)로 세웠다 —
    배경 없는 글자가 눌리는 것으로 안 읽히는 #788과 같은 진단이고 손가락 표적도 작았다. **남의 책방은
    텍스트 그대로** 둔다(서버가 남의 목록을 안 주므로 누를 수 없는 것을 버튼처럼 보이게 하지 않는다).
  - ⚠️ 탭 라벨이 바뀌었다 — 다음 심사 제출 전 스토어 스크린샷 점검 대상(4종엔 탭바가 찍혀 있다).

- **탭바 가운데 측정 액션 — 4칸 → 5칸** ✅ 코드 완료 / ✅ 실기기 확인(2026-08-19) (2026-08-17, 사용자 결정) —
  측정 시작·종료가 **홈에만** 있어 서재·책방·기록 탭에서 읽기 시작하려면 홈으로 돌아와야 했다. 하단 플로팅
  탭바 가운데(렌더 index 2)에 **채운 원 44px**(대기 = 세이지 ▶ / 측정 중 = danger 빨강 ■)을 세워
  **어느 탭에서든 1탭**으로 여닫는다. 홈의 주 버튼 「측정 시작/끝내기」는 제거했다(중복 CTA 금지 — 사용자 결정).
  가운데 자리를 두고 「친구 찾기」안과 겨뤘으나 기각 — 검색은 책방 상단 진입 + 친구 찾기 시트로, 남의 여백은
  홈 소식 피드로 **이미 있는 기능**이라 가장 비싼 자리를 세 번째 입구에 쓰는 셈이었다. 인스타 가운데 버튼의
  원전도 검색이 아니라 핵심 동사(「+」)다.
  - **측정 대상의 단일 출처 유지** — 어느 탭에서 눌러도 홈 캐러셀 선택(`homeBookId`, App 소유)으로 시작한다.
    순수 함수 `timerStartBookId`가 `undefined`(안 고름) → `defaultBookId`(이어 읽기 → 첫 책) → `null`(책 없이)로
    떨어뜨려 **죽은 버튼이 구조적으로 불가능**하다. 서재에서 보던 책으로 시작하지 않는다(출처가 둘이면
    무엇으로 시작될지 예측 불가). 「책 없이」(`null`)는 `undefined`와 다른 값으로 그대로 존중된다.
  - **로직은 `MainTabs`로 승격**(App까지 올리지 않는다 — 여백·목표·설정 전체 화면엔 탭바 자체가 없다).
    덤으로 태깅 시트·첫 완독 축하가 **탭 전환에 살아남는다**(홈 소유 시절엔 언마운트로 사라졌다).
    액션 실패 문구는 다른 탭에 홈의 ErrorMessage 자리가 없어 **탭바 위 고정 스트립**으로 띄운다.
  - ⚠️ **`role="tablist"/"tab"`을 버리고 `aria-current="page"`로** — ARIA tabs 패턴은 `tabpanel`과 짝일 때만
    성립하는데 이 앱엔 그 짝이 애초에 없어 **지금까지가 규약 위반**이었다. 하단 내비게이션 표준(APG)이 이쪽이고,
    탭이 아닌 액션 버튼이 형제로 서는 구조도 여기서 자연스러워진다.
  - 애니메이션 0 — 발광·펄스는 표지를 재래스터화하는 실측 사고 클래스라(T-176) 상태 전환은 즉시 교체다.
  - ⚠️ **스토어 스크린샷 재촬영 대상** — 홈(주 버튼 소멸)과 탭바(5칸) 둘 다 바뀐다. 심사 제출 전
    `miniapp/screenshots/` 01~04를 다시 찍어야 한다(탭바는 4종 전부에 찍혀 있다).

- **첫 진입 코치마크 — 측정 버튼을 1회 가리킨다** ✅ (2026-08-17, 사용자 결정 · 위 5칸 개편의 후속) —
  「측정 시작」이 홈에서 탭바 가운데로 옮겨가며 **처음 온 사람도, 이미 쓰던 사람도** 시작 자리를 모르게 됐다.
  첫 진입 1회, 화면 위에 반투명 덮개를 깔고 **탭바만 밝게 남긴 채** 가운데 원을 말풍선으로 가리킨다.
  「덮개 없이 말풍선만」 시안과 겨뤄 채택 — 1회성 안내는 방해 비용이 거의 없고, 홈 정보량이 많아
  덮개 없는 쪽은 그냥 지나칠 위험이 컸다(둘 다 목 모드에 실제로 얹어 캡처해 비교).
  - **스포트라이트를 마스크 없이 층 순서로 얻는다** — 덮개를 탭바(`TAB_BAR_Z_INDEX`)보다 **한 층 아래**
    깔면 불투명 배경의 탭바만 저절로 위에 남는다. SVG 마스크로 구멍을 뚫는 코드가 통째로 없고, 덤으로
    **원이 그대로 눌려 안내를 보고 곧장 측정이 시작된다**(덮개를 위에 얹으면 가리키는 대상 자체를 막는
    자기모순 — 그 부등식이 유일한 성립 조건이라 테스트로 박았다).
  - **위치 계산 0** — ref·`getBoundingClientRect`·리사이즈 관측이 없다. 탭바가 좌우 같은 여백의 `fixed`라
    **다섯 칸 중 가운데 = 화면 가로 정중앙**이고, 세로는 탭바와 같은 식에 여백만 더한다.
  - **기록은 기기 로컬**(`localStorage['booktimer.coachmark.timer']`) — 서버 컬럼·Flyway·API 왕복 0.
    기기를 바꾸면 한 번 더 뜨는 것이 유일한 대가다. **키가 없으면 뜨므로 기존 사용자도 1회 본다**(요점).
  - 애니메이션 0(T-176). 닫는 법은 아무 데나 탭 + 측정 버튼 실제 누름 두 가지.
  - ✅ 실기기 확인(2026-08-19): 덮개가 홈 인디케이터 영역까지 덮는지, 말풍선이 원을 정확히 가리키는지.

- **코치마크를 5개로 — 탭바 투어 3걸음 + 그 자리 안내 2개** ✅ (2026-08-17, 사용자 결정 · 위 코치마크의 확장) —
  「서재와 책방이 어떻게 다른지」·「여백은 무엇에 쓰는지」·「책은 어떻게 담는지」를 처음 온 사람이 알 수 없다는
  문제. **두 갈래로 나눈 것이 설계의 핵심**이다:
  - **탭바 투어(첫 진입, 순서 고정)** = 측정 원(#833) → 서재 → 책방. 서재/책방의 차이는 *지도* 문제라
    이웃한 두 칸을 연달아 짚어야 대비가 보인다. 딤 탭이 곧 「다음」이고 진행은 점 3개로 보인다.
  - **그 자리 안내(해당 화면 첫 방문, 각 1회)** = 서재의 「책 추가하기」, 홈의 「여백에 글 남기기」.
    행동 문제라 그 화면에 서 있을 때 알려야 읽힌다 — 첫 진입에 5장을 몰면 넘기기만 연타하게 된다.
  - **인라인 안내도 마스크·좌표 계산이 0이다** — 딤을 깔고 **대상이 스스로 그 위로 올라오고**
    (`position: relative` + 층), 말풍선은 대상 바로 아래 **흐름에 놓인 형제**다(레이아웃이 위치를 계산한다).
    탭바 투어와 반대로 딤이 **탭바 위**를 덮는다 — 가리킨 것이 콘텐츠일 때 바까지 밝으면 초점이 둘이 된다.
  - **순서 제어 코드가 없다** — 스텝별 키(`booktimer.coachmark.<이름>`)라 #833의 `timer` 키가 그대로 첫 걸음이
    되고(마이그레이션 0), 인라인 안내는 `after`로 투어 마지막 걸음 뒤에 선다. 신규 사용자는 책이 0권이라
    **홈 여백 문 자체가 없으므로** 그 안내는 책을 담은 뒤에야 저절로 차례가 온다.
  - **게이트와 안내가 한 몸** — 「책 추가하기」는 서버 플래그(`searchEnabled`)에 달려 있어, 따로 두면
    플래그가 꺼진 날 **없는 버튼을 가리키는 안내만** 남는다. `AddBookButton` 하나가 둘을 함께 든다.
  - ⚠️ **정적 렌더로는 못 잡고 실 브라우저에서 잡은 것 2건** — ① 뜰 차례 판정을 마운트 1회로 두면 투어를
    막 끝낸 홈에서 침묵한다(매 렌더 파생으로 고침) ② 늦게 도착한 콘텐츠(서재 인라인 여백 박스)가 대상을
    화면 밖으로 밀어내 **딤만 깔린 검은 화면**이 된다(`scrollIntoView` + 안내가 떠 있는 동안만 `ResizeObserver`).
  - ✅ 실기기 확인(2026-08-19): 투어 3걸음의 꼬리가 각 칸을 정확히 가리키는지(목 모드에선 꼬리 중앙 = 칸 중앙 실측
    123/123·266/267), 인라인 안내가 스크롤 끝에서도 화면 안에 들어오는지.
  - **다섯 걸음을 한 줄로 이어 자동 이동** ✅ (2026-08-17, 사용자 결정 — 「첫 진입 1회짜리라 이 정도 강제 흐름은
    괜찮다」) — 인라인 2개가 사용자가 그 화면에 스스로 갈 때까지 기다리던 구조를, **걸음마다 그 화면으로 데려간 뒤
    설명**하는 한 줄 흐름으로 합쳤다(홈 측정 → 서재 이동·설명 → 서재의 책 담기 → 책방 이동·설명 → 홈 복귀·여백).
    「다음을 누르면 이동」이 아니라 **「이동한 뒤 설명」**인 이유는 딤이 반투명이라 문구를 읽는 동안 **그 화면이
    뒤에 보인다**는 것이다. 마지막이 홈이라 걷기가 출발한 자리로 돌아와 끝난다.
    - **닫힘 구독으로 배선했다** — 인라인 안내는 화면 안에서 닫히므로 흐름을 이끄는 `MainTabs`가 알 길이 없었다.
      `dismissCoachmark`에 모듈 지역 리스너를 달아 어디서 닫혀도 커서가 넘어간다(prop을 Home·Library까지 안 내린다).
    - **포기 규칙이 두 종류를 갈라 다룬다** — 걷는 중 스스로 탭을 누르면 **길 안내(탭바 걸음)만** 본 것으로 접고,
      **인라인 걸음은 남긴다**(대상이 있는 화면에 섰을 때가 제 차례다). 인라인 걸음 중에는 딤이 탭바를 덮어 이 경우가 없다.
    - 목 모드 실측: 5걸음 완주(탭 자동 전환 홈→서재→서재→책방→홈, 키 5개 저장) · 기록 탭 직접 누름 시 키 3개만
      저장되고 서재 재방문에 책 담기 안내가 살아 있음 · 콘솔 에러 0.
  - ⚠️ **심사 반려 → 자동 노출을 걷고 배너로 시작한다** ✅ (2026-08-17, 반려 당일 대응) — 토스 심사:
    「**미니앱 접속 직후 바텀시트가 바로 노출돼요.** 진입 시 즉시 나타나지 않도록 수정해 주세요.」
    우리가 만든 것은 툴팁이지만 렌더 결과가 **전체 딤 + 하단 좌우 24px 와이드 패널**이라 시트와 구별되지
    않았다(폭 342px = 화면의 88%, 하단 고정, 배경 잠김). 2026-08-12 「서비스 설명 없이 즉시 로그인 유도」
    반려와 **같은 계열**이다 — 심사는 진입 직후 들이대는 것을 일관되게 막는다.
    - **고친 방식**: 안내 구현은 그대로 두고 **시작 트리거만** 자동 → 수동. 홈 헤더 아래 1회성 배너
      (`👋 처음이신가요? 앱 사용법 보기 ›` + ✕)를 누르면 지금의 5걸음이 그대로 돈다. 배너는 화면 안
      요소라 시트가 아니고 배경을 잠그지 않는다. 표시 기준은 **길 안내(탭바 걸음) 미완**뿐이다 —
      「전 걸음 완료」로 하면 책 0권이라 여백 문이 없는 신규 사용자에게 배너가 평생 뜬다.
    - ⚠️ **실 브라우저가 잡은 구멍 2건**(정적 렌더로는 원리상 못 잡는다): ① ✕를 눌러도 배너가 그 자리에
      남았다 — 배너만 보고 있을 때 커서는 이미 `-1`이라 `setFlowIndex(-1)`이 같은 값이고 React가 리렌더를
      건너뛴다(닫힘을 별도 상태로 들어 해결) ② **더 큰 것**: ✕로 거절하면 길 안내 키 3개가 남아 인라인
      안내의 `after` 게이트가 열리고, **다음 진입 때 홈에서 여백 안내 딤이 즉시 깔렸다** = 반려 사유 재발.
      인라인 안내에 **「걷는 중」 잠금**(`coachmarkWalking`)을 달아 흐름이 살아 있을 때만 뜨게 했다.
    - ✅ **재심사 승인 → 출시** (2026-08-17, 번들 `01a00f99-5e2f-7cc8-8d72-ff59222dd31f`). 재제출 때 출시 노트의
      코치마크 줄을 「진입 시 자동 표시」가 아니라 **「사용법 보기를 누르면 안내」**로 고쳐 적었다 — 심사자가
      그 줄을 보고 다시 확인하기 때문이다.
  - **설정에 「처음 안내 다시 보기」** ✅ (2026-08-17 — 실기기 검증이 막혀 드러난 구멍) — 기록이 **기기**에 붙어 있어
    로그아웃·탈퇴로도 안 지워지고 미니앱엔 devtools가 없다. 앱 안에 손잡이가 없으면 **한 기기에서 두 번 볼 방법이
    아예 없다**(실제로 검증하려던 기기가 막혔다). 「하루 목표 바꾸기」 옆 버튼 하나 = 접두어로 코치마크 키만 지우고
    **화면을 넘긴다** — 설정 화면이 탭 셸을 언마운트하므로 돌아오는 순간 커서가 처음부터 다시 계산된다(리셋 배선 0).
    이름 목록 대신 접두어(`booktimer.coachmark.`)로 훑어 안내를 더 만들어도 손댈 곳이 없고, 토큰·알림 동의는 접두어가 가른다.

**⏸ v2 이후로 미룬 것**: 연결 해제 UX(문의 처리) · 역방향 연결(토스에서 시작한 유저의 웹 로그인 수단 — login_id·비밀번호가
없어 범위가 큼, 우회로는 "웹에서 가입 후 연결") · 슬라이딩 토큰 연장 · 통계 ·
~~**책BTI 히스토리·대표 선택 UI**~~ → **2026-08-15 들어왔다**(위 「독서 성향 보관함」 — 시트 + 세로 카드 3장.
"새 분석 = 곧 대표"라는 단순화는 그대로 두고, 보관함이 비교와 되돌리기를 맡는 형태) ·
서재 마을(Phaser 성능·TDS 이질감 검증 필요). ~~미니앱 소셜 기능~~ → v2 범위로 승격(2026-08-10).
기능 동등성 원칙은 유지 — 웹이 본진.

**⚠️ 리스크**: ① WebView 오리진/CORS 실측 불확실(PR-0에서 첫 확정) ② mTLS 인증서 만료·갱신이 운영 목록에 추가됨
③ 미니앱 노출로 트래픽 급증 시 EC2 단일 인스턴스의 `/api/dashboard`(무거운 단일 응답)가 첫 병목 후보 — MVP는 관찰만.

### 🤖 AI 사서 — 대화형 책 추천 (⏸ 백로그, 트리거: 수익 안정화. 발상 2026-08-13)

사용자가 "요즘 무기력한데 짧고 따뜻한 소설 추천해줘"처럼 채팅하면, AI가 그 사용자의 독서 기록을 조회하고
알라딘 API로 검색해 **대화로 책을 추천**하는 기능.

- **구조**: BookTimer 서버 안에 에이전트 루프를 내장(Claude API 직접 호출 또는 Agent SDK) — 도구 = ① 독서기록
  DB 조회 ② 알라딘 책 검색. Claude Code(개발 도구)와는 무관한 층 — 서버 코드로 직접 구현해야 한다.
- **⏸ 보류 사유 = 재개 트리거**: 호출당 LLM API 비용이 나가는 구조라 **사용자가 쓸수록 원가가 증가**한다.
  광고·제휴 기반의 현 수익 규모로는 수지가 안 맞음 → **수익이 안정적으로 나기 시작하면 재검토**.
- 재개 시 검토할 것: 호출량 제한(일 N회)·응답 캐싱, 모델 티어(저가 모델로 시작), 월 비용 상한 알람.

---

## 🩹 실사용에서 발견한 문제 (계획 외 — UX/사용성)

> 로드맵·설계에 **없던** 문제들. 내가 앱을 실제로 써 보며(주로 스크린샷으로) 발견해 즉석 수정한 것들이다.
> 기능 로드맵(계획적 확장)과 구분해 여기 모은다 — **"계획에 없었지만 실사용이 드러낸 결함"의 누적 기록**.
> 공통 교훈: **CSS flex 자식이 0으로 줄면 한글이 글자 단위로 깨진다**(N-032 류) / **검색·필터는 사용자 의도(기본값)를
> 명시적으로 고정**해야 한다. 새 문제를 발견하면 계속 추가.
>
> **날것 수집함은 따로 있다** — 모바일 실기기에서 막 발견한(아직 분석 전) 개선안은
> [claude-docs/mobile-improvements.md](claude-docs/mobile-improvements.md)에 `M-#`로 접수한다. 여기 표는 **원인·해결이 확정된 것**만 담는다.

| 증상 (실사용에서 본 것) | 원인 | 해결 | 상태 |
|---|---|---|---|
| 책장에서 **긴 책 제목이 깨져** 레이아웃이 무너짐 | `.book-meta`가 flex 안에서 0폭까지 줄어 한글이 글자 단위 줄바꿈 | `.book-meta flex:1 1 220px` + `.book-row flex-wrap` + `word-break:keep-all` (CSS만) | ✅ PR #110 |
| 대시보드 **하단 "내 책장·독서 기록" 링크가 글자 단위로 줄바꿈**(지저분) | 좁은 flex 컨테이너에서 링크 텍스트가 글자별로 쪼개짐 | 하단 링크를 **퀵 액션 타일 2열 그리드**로 재구성(`.quick-actions`/`.quick-tile`) | ✅ PR #116 |
| 측정 카드 **"읽을 책" 라벨이 세로로 깨지고** select가 카드 밖으로 튀어나옴 | inline-flex + 자식 min-width 미설정으로 라벨 수축·select 오버플로 | `.book-pick` flex+`white-space:nowrap`, select `flex:1 1 auto; min-width:0` | ✅ PR #117 |
| 페이지마다 **하단 네비 디자인이 제각각**(텍스트 링크 vs 타일) | 표준 양식 부재 — 페이지별로 따로 마크업 | `.link-row`를 타일 그리드 양식으로 **CSS만 일괄 통일**(전 페이지, 양식 표준 확정) | ✅ PR #122 |
| #122는 **CSS만** 통일했고 하단 네비 **아이콘은 페이지별 제각각**(이모지/인라인 SVG/컴포넌트)이라 대시보드 버튼과 통일성↓ | 아이콘·마크업 표준 부재 — 24곳에 따로 흩어짐 | 아이콘 라인 SVG 통일(색=대시보드 타일 `--accent-hover`) + Vue `shared/NavLinks`·Thymeleaf `nav-links` fragment로 **마크업 템플릿화**(한 곳 수정=전체 반영, 두 런타임이 같은 `.link-row` CSS·아이콘 사전 공유) | ✅ |
| 내 책장에서 **상태별로 책을 골라 볼 수 없음**(전부 한 목록) | 필터 기능 자체가 없었음 | 서버사이드 상태 필터(`?status=`, 전체/읽고싶음/읽는중/완독 칩, q 유지) | ✅ PR #122 |
| **"모기" 검색 시 제목이 아닌 저자명("모기 겐이치로")이 먼저** 뜸 — 의도와 어긋남 | 알라딘 `QueryType=Keyword`가 제목·저자를 한꺼번에 매칭 | 검색 기준 **제목(기본)/저자 분리**(`BookSearchType`→`QueryType` Title/Author, 라디오 선택) | ✅ PR #123 |
| 위 분리 후에도 **제목 검색에 저자 매칭이 계속 섞임** — 알라딘 `QueryType=Title`이 문서("제목만")와 달리 저자까지 섞어 반환(비엄격) | 외부 API 동작이 문서와 불일치 — 클라이언트가 신뢰할 수 없음 | **결과 후필터**(`BookService.filterToSearchType`): 고른 기준 필드(제목/저자)에 검색어가 실제로 든 결과만 남김(공백·대소문자 정규화 contains). 외부 API 동작과 무관하게 보장 | ✅ PR #125 |
| 검색 폼 — **검색 바가 좁고 버튼이 과도하게 큼**(가로 배치라 버튼이 입력칸 폭을 잠식) | 입력+버튼을 한 줄 flex로 둬 폭이 경쟁 | 세로 배치 — 넓은 검색 바 위, 전체폭 버튼 아래(`.search-row` column) | ✅ PR #125 |
| 독서 기록 일자별 **"N회"(세션 수)가 1분도 안 읽고 멈춘 측정까지 세어** 의미가 약함 | 측정 시작 직후 종료해도 카운트 증가 — 횟수 지표가 부풀고 정보가치 낮음 | **횟수 제거**, 대신 그날 **읽은 책 제목** 표시(총 시간은 유지). `DailyReadingRecord.sessionCount`→`bookTitles`, 책 미지정(레거시)만 있으면 책 줄 생략 | ✅ PR #138 |
| 사용자 검색(/search)에서 **닉네임 입력칸이 비정상적으로 세로로 길쭉**함 | `.book-search-form`이 column flex인데 input이 폼 바로 밑(직계)이라 `flex:1 1 160px`의 basis가 **높이**로 먹힘 — 책 검색은 `.search-row`가 리셋하지만 이쪽은 래퍼가 없었음 | `.book-search-form > input[type=text]`에 `flex:none; width:100%`로 한 줄 높이 리셋(직계 셀렉터라 `.search-row` 하위인 책 검색은 무영향, CSS만) | ✅ PR #139 |
| 대시보드에서 **'재미' 동선(내 책장·내 공개 프로필)이 맨 밑 바로가기 5타일에 섞여 묻힘** — 노출 우선순위가 낮음 | 이 둘이 설정·검색·기록과 같은 등급의 작은 타일로 한 묶음에 들어가 있어 시각적 우선순위가 없음 | 측정 카드 바로 밑에 **하이라이트 밴드**(`.highlight-actions`/`.highlight-tile`, 포인트 테두리·부제) 신설해 둘을 승격, 맨 밑 바로가기에선 빼 중복 제거(타이머는 히어로 유지, HTML/CSS만) | ✅ PR #142 |
| 내 책장에 책이 많으면 **전부 한 번에 렌더**돼 리스트가 길어지고, 하단 네비(← 대시보드 등)까지 스크롤이 멀어짐 | 페이징/지연 표시가 없어 `myBooks` 전체를 통째로 그림 | **고정 높이 스크롤 박스**(`.shelf-scroll{max-height:60vh;overflow-y:auto}`, 공용 클래스, CSS만) — 목록만 화면 60% 박스 안에서 스크롤, 페이지 자체는 짧게 유지돼 하단 네비가 늘 바로 아래. 책 적으면 스크롤바 안 생김(자연 폴백). **먼저 무한 스크롤(`books.js`, PR #188·#189)을 시도했다 철회**(#190): IntersectionObserver가 큰 화면에선 센티넬이 계속 보여 한꺼번에 다 펼쳐져(연쇄 reveal) 효과 없고, 무한 스크롤은 스크롤할수록 목록이 늘어 *하단 도달*이라는 원래 목표와 어긋남 | ✅ PR #188·#189(무한스크롤, 철회) → #190(스크롤 박스) |
| 책방(공개 프로필 `/u/{id}`)의 **공개 책장도 책이 많으면 똑같이 길어짐** — 내 책장만 고쳐 일관성 깨짐 | 스크롤 박스를 내 책장(`#shelf-list`)에만 적용했었음 | 위 스크롤 박스를 **공용 클래스 `.shelf-scroll`로 일반화**(id→class)해 책방 공개 책장에도 동일 적용. 커버에 `loading="lazy"`도 통일(CSS/HTML만) | ✅ PR #191 |
| 내 책장 상단 **'책 검색'·'직접 추가'가 별개 카드 2개**로 나뉘어 — 둘 다 "책 추가"인데 분리돼 중복·산만 | 처음부터 두 `<section class="card">`로 따로 마크업 | **한 '책 추가' 카드로 통합** + 직접 추가는 `<details>` 폴백(검색 우선, 못 찾을 때만 펼침). 검색 0건·검색 비활성이면 `th:open`으로 자동 펼침. native `<details>`라 **JS 0**(CSS/HTML만). **#192 직후 핫픽스(#193)**: `.book-manual-form{display:flex}`(author)가 UA의 닫힘-숨김(`details:not([open])>*{display:none}`)을 이겨 접어도 폼이 늘 보였음 → `.manual-add:not([open]) .book-manual-form{display:none}`로 재숨김(특정성 0,3,0 > 0,1,0). 예전 `.book-row[hidden]` 함정과 동일 계열 | ✅ PR #192 → #193(접힘 핫픽스) |
| 책의 **공개/비공개 설정이 드롭다운**이라 직관적이지 않음 — "공개"가 *책방에 보인다*는 인과를 사용자가 모름 | `<select>`는 2-state에 부적합(현재 상태가 한눈에 안 보임)·문구가 결과 아닌 추상 상태("공개/비공개")·책방과의 연결 단서 없음 | **"책방 공개" 토글 스위치**로 교체(🌍 책방 공개/🔒 비공개, 버튼이 '현재의 반대' 값 POST → JS 0) + 내 책장 상단에 **"🌍 책방 공개로 켠 책은 내 책방에서 누구나 볼 수 있어요"** 안내(책방 `/u/{loginId}` 링크 겸 — books→내 책방 동선도 신설). 백엔드 visibility 엔드포인트 무변경, 안 쓰게 된 `visibilities` 모델 제거. 링크용 `loginId` 모델 적재(TDD). **후속 #196**: 토글이 상태를 이미 보여줘 변경 시 뜨던 성공 플래시 메시지 제거 | ✅ PR #195 → #196(플래시 제거) |

| 남의 책방(`/u/{id}`)에서 본 책을 **그 자리에서 살 수 없음** — 구매하려면 내 책장으로 가 검색·추가 후에야 구매 버튼이 떠 동선이 길고 비효율 | 공개 프로필 책장에 제목·저자·상태·시간만 렌더하고 구매 버튼이 없었음. 기존 `/books/{id}/buy`는 소유권(IDOR) 전용이라 남의 책엔 못 씀 | 책방 공개책 행에 **구매 버튼** 추가 + 소유권 대신 **공개 여부**를 게이트로 둔 새 경로 `GET /u/{loginId}/books/{id}/buy`(`recordPublicPurchaseClick` — 비공개·없는 책은 임의 id 탐침 차단). 클릭은 **책 주인 카운트**에 집계. profile.html+BookService+BookController, TDD. **후속 #202**: 본인 책방엔 구매 버튼 미노출(`self` 게이트 — 내 책은 이미 내 것)·제휴 안내문도 숨김 | ✅ PR #199 → #202(본인 책방 숨김) |
| 메인 대시보드로 돌아가려면 **매 페이지 최하단 '← 대시보드' 버튼**까지 스크롤해야 함 — 동선이 멂 | 상단 로고(`📚 BookTimer`)가 단순 텍스트라 클릭 불가 | 로고(이모지+제목)를 **`/` 홈 링크로** 감쌈(`.brand-home`, 표준 "로고=홈" 관행). 로그인 후 앱 페이지 10곳 적용, 인증 전(로그인/회원가입/온보딩)·관리자 페이지는 제외(리다이렉트 루프·별도 허브). HTML/CSS만 | ✅ PR #201 |
| 사용자 검색 친구 추천·검색 결과의 **'팔로우'(채움) 버튼이 '언팔로우'(아웃라인)보다 부담스럽게 큼** — 리스트 행 버튼 크기 불일치 | `.book-actions`의 크기 축소 규칙(`padding:6px 10px; font-size:.85rem`)에 `btn-ghost`·`btn-danger`만 넣고 `btn-primary`(팔로우)를 빠뜨려, 팔로우만 베이스 크기(`padding:12px 18px`+기본 폰트) 유지 | 그 규칙에 **`.book-actions .btn-primary` 추가** — 리스트 행 팔로우 버튼을 언팔로우 크기로 통일. 독립 `btn-primary`(검색·추가 등)·프로필 상단 팔로우는 `.book-actions` 밖이라 무영향. 프리뷰 하네스로 검증(둘 다 padding 6/10·font 13.6px·line-height 20.4px 일치, 높이차 2px=ghost 외곽선뿐). CSS 1줄 | ✅ PR #203 |
| 책 검색 결과에서 **이미 내 책장에 있는 책이 다시 "추가" 가능**해 보여 혼란 — 재추가가 중복 책장 행을 만드는 풋건(`addFromSearch` 무방비) | 검색 결과 행이 소유 여부와 무관하게 추가 폼을 항상 렌더 | 검색 결과 중 이미 가진 책에 **"📚 이미 책장에 있음" 배지**(followPopularity와 같은 메타 줄, 중립 톤) + **추가 폼 `th:unless`로 숨김**(UI 경로 재추가 차단). 컨트롤러가 `myBooks` 전체(상태 필터 무관)의 isbn 집합 `myShelfIsbns`를 모델에 실어 템플릿은 조회만(새 쿼리 0, null isbn 제외 N-055). 서버측 `(user,isbn13)` 중복 가드는 범위 밖(후속) | ✅ PR #273 |
| 책 검색 결과에서 **이미 가진 책을 재추가하면 책장에 중복 행**이 생김 — UI는 #273이 추가 폼을 숨겨 닫았지만 `/books/add` **직접 POST로는 여전히 중복 생성** 가능(데이터 무결성) | `addFromSearch`가 중복 검사 없이 `register`→`save`, `(user,isbn13)` 유니크 제약도 없음 | `addFromSearch`에 **서버측 멱등 가드**: isbn `Isbn.normalize` 후 `findFirstByUserAndIsbn13`로 기존 책 조회 → 있으면 새 행 안 만들고 **기존 책 반환·상태 보존**(isbn null 수동책은 키 없어 면제). 서비스 레벨(Option A)로 처리, DB 유니크 제약은 기존 중복 정리 마이그레이션 부담 커 후속 보류. TDD 4테스트(중복 미생성·상태 보존·null 다중·사용자 격리) | ✅ PR #274 |
| 정원 **'꾸미기' 편집 식물이 태블릿에서 너무 작아** 잘 안 보이고 터치로 고르기 어려움(데스크톱은 멀쩡) | 앱이 모바일-우선 `max-width:460px` 단일 컬럼인데 정원에 태블릿+ 반응형이 없어 큰 화면에서도 정원이 460px에 갇힘 — #356 Phaser 전환 후엔 편집 캔버스(`width:100%` FIT)가 460이라 식물이 작게 렌더. 데스크톱은 마우스·모니터로 덜 거슬렸을 뿐 | **정원 페이지만 큰 화면(`min-width:600px`)에서 넓힘**(#356 Phaser 전환에 맞춰 재작업) — `.container`에 전용 클래스 `garden-wide` + 미디어쿼리로 컨테이너 460→720px. 편집 Phaser 캔버스는 컨테이너 따라 커져 식물 FIT 비례 확대, 보기·팔레트·도감은 DOM이라 함께 키움. 정원 페이지 한정이라 회귀 0, 모바일 불변. 프리뷰 하니스로 폭별 실측(태블릿 편집 캔버스 283→660px·보기 식물 30.4→41.6px·팔레트 44→60px, 모바일 불변) | ✅ PR #357 |
| 독서 기록 **일자별 목록이 쌓일수록 페이지에 그대로 주르륵** 늘어져 길고, 특정 달만 골라 보기 어려움 | `dailyHistory` 전체를 한 번에 렌더 + `.record-list`에 높이 제한 없음 + 월 구분 부재(책장 #190과 같은 계열의 "길어짐" 문제, 독서 기록판) | **한 번에 한 달만(◀▶ 이전/다음) + 그 달은 고정 높이 스크롤 박스**로 개편 — 서버에 월별 묶음(`MonthlyReadingSection`·`monthlyHistory`, 최신월·최신일 먼저 + 월 합계), `HistoryController` `records`→`months`, 뷰는 최신 달만 보이고 바닐라 JS(Alpine/htmx 0)가 ◀▶로 hidden 토글(JS-off 폴백=최신 달), `.record-scroll{max-height:360;overflow-y:auto}`. 잔디·빠뜨린날 무손·history 전용 셀렉터라 회귀 0. 서비스/컨트롤러 TDD(RED→GREEN) + preview 실측 | ✅ PR #359 |
| 남의 책방(`/u/{id}`)에서 **'구매' 드롭다운(알라딘·쿠팡)이 화면 밖으로 잘려** 가로 스크롤해야 보임 | `.buy-menu-items`가 `left:0`(버튼 왼쪽 기준 펼침)인데 `.book-actions`가 `margin-left:auto`라 버튼이 행 우측 끝 → 메뉴가 우측 경계 넘고 `.shelf-scroll`(overflow-y:auto)이 가로도 클리핑 | **펼침 방향을 안쪽으로** — `.book-actions .buy-menu-items{left:auto;right:0}`(버튼 우측 기준 왼쪽 펼침) + `flex-direction:column→row`(가로 배치로 세로 높이↓). book-detail은 `.book-actions` 밖 `<p>` 직속이라 기본 left:0 유지(회귀 0). preview 하니스로 모바일 375 메뉴 우측<박스 우측·가로 스크롤 0 실측. CSS만(.java 0) | ✅ PR #360 |
| 정원 도감 **필터 탭(전체·⏳시간·🌸장르…)이 태블릿/PC에서 한 줄 pill이 아니라 풀폭으로 세로 스택**돼 늘어짐(모바일은 좁아 자연스러워 안 보였음) | 전역 `button,.btn{width:100%}`가 `.garden-tab`으로 새는데 `.garden-tab`이 padding·radius만 덮고 width를 안 덮어 100% 상속 → `.garden-tabs`가 `flex-wrap`이라 풀폭 pill이 각자 한 줄씩 세로로 쌓임(`app.css` 233·421줄 주석·#286과 동일 함정) | **`.garden-tab{width:auto}` 1줄**로 누수 상쇄 → 내용 너비 pill 복원. 넓은 화면=한 줄 가로(넘치면 wrap)·모바일=2줄 접힘. preview 하니스(실 `.garden-controls` 마크업+app.css·`garden-wide` 720 캡)로 데스크톱 1280/태블릿 768=5개 한 줄·모바일 375=2줄 wrap·풀폭 0·computed width 49px 실측. CSS만(.java 0) | ✅ PR #368 |
| 데스크톱 와이드(≥880px)에서 **내 책장이 책 수만큼 길어짐** + 상단/하단 이동 링크가 폭 따라 갈려 일관성 깨짐 | 와이드 `@media`가 `#books-app .shelf-list`를 `max-height:none;overflow:visible`로 60vh 스크롤 박스를 **의도적 해제**(2열 그리드 "시안")하고, 이동 링크를 좁은 폭=하단 `.link-row` 타일 ↔ 와이드=상단 `shelf-greeting-nav` 텍스트로 토글했었음(좁은 폭은 #190으로 이미 해결돼 있었음) | 해제를 제거해 책방 `.shop-shelf>.shop-books`처럼 **grid 2열 + `.shelf-scroll` 60vh 내부 스크롤 공존** 복원 + 상단 `shelf-greeting-nav` 마크업·CSS 제거하고 **하단 `.link-row` 타일(대시보드·독서기록·내 책방)을 전 폭 단일 이동 수단**으로 통일. static-preview 하니스로 와이드 1280/모바일 375 양폭 실측(60vh 스크롤 박스·하단 타일 노출·상단 링크 부재). CSS+Vue(`.java` 0) | ✅ |
| 메인 페이지를 곳에 따라 '대시보드'로 불러 명칭이 흔들림 — 이 웹의 메인은 '홈'으로 통일하고 싶음 | UI 표시 텍스트가 '대시보드'(하단 네비 라벨·로고 툴팁·탭 제목·인라인 링크·admin 안내)로 흩어져 있었음 | 화면에 보이는 '대시보드'를 전수 '홈'으로 — 하단 네비 라벨(Thymeleaf 5+Vue/ts 7)·로고 툴팁('홈으로')·`<title>`·마을/책방 인라인·admin 문구. URL `/dashboard` 별칭(PWA 404 방지)·영문 식별자·파일명·CSS·주석은 보존(사용자 비노출, 메인 URL은 이미 `/`). grep 전수 + `booksNavLinks` 라벨 TDD로 검증, 번들 8개 재빌드 | ✅ |
| 책장·책방 하단의 **제휴 수수료 안내문이 늘 펼쳐져** 본문 아래 공간을 차지 — 평소엔 안 봐도 되는 법적 고지인데 상시 노출 | 고지문 2줄을 `.affiliate-note`(책장)·`.shop-affiliate`(책방) `<p>`로 본문 하단에 항상 렌더 | 책BTI `?` 헬프 팝오버처럼 **우상단 ⓘ 토글 팝오버**로 이전(클릭 펼침/Esc·밖클릭 닫힘, 아이콘은 `?`와 구분되는 ⓘ) — 공용 `.affiliate-pop-*` 1세트(앵커=ⓘ 버튼 래퍼라 말풍선 꼬리 정확, 책BTI `.pbti-help-*` 무손상 복제). 책장='내 책장' 카드 헤드 우측(`.shelf-mine-head`), 책방='공개한 책' 카드 헤드 우측(필터칩과 `.shop-shelf-tools` 묶음, `!self && books>0` 게이트 그대로) — 둘 다 카드 안 우상단으로 통일(처음엔 책장이 인사말 헤더였으나 책방 기준으로 맞춤). 하단 상시 문구 제거(`.affiliate-note` 규칙은 book-detail용 보존, `.shop-affiliate`는 소비처 사라져 정리). 로컬 bootRun+Chrome 실측(2번째 유저 시드한 `!self` 책방 포함, ⓘ 배치·토글·Esc·밖클릭·콘솔0·하단문구 제거·self 숨김)·vitest 775 회귀 0, 번들 books·profile 재빌드 | ✅ |
| 「측정 끝내기」를 깜빡하면 **21시간짜리 세션이 그대로 기록**돼 통계·잔디가 왜곡됨 + 방치된 `ended_at IS NULL` 세션이 무기한 남아 새 측정 시작까지 막음(운영 실측 2026-08-13) | 실시간 측정 경로(`stop`)에 인정 시간 상한이 없었고(수동 입력 경로엔 24시간 cap 존재 — 비대칭), 방치 세션을 정리하는 배치도 없었음 | 한 세션 인정 상한 **6시간 cap** 도입 — `ReadingSessionService.stop`이 초과 경과를 `startedAt+6h`로 클램프(엔티티 불변식 `duration=end-start`는 유지, 클램프는 서비스 정책) + 신규 `StaleSessionSweeper`(10분 주기, 게이트 없이 상시 등록)가 6시간 넘게 열린 세션을 정확히 cap 길이로 자동 종료(조회~종료 사이 사용자가 stop을 눌러 이미 닫힌 건은 건별 스킵해 나머지 진행). 스키마 무변경(Flyway 0), TDD 8테스트(경계 5h59m·정확히 6h·6h+1s·21h / 스위퍼 단건·경계·다건·경합) + 돌연변이 4종 사살 | ✅ |
| 측정을 **멈출 때만** 독서등이 부드럽게 꺼지지 않고 화면이 뚝 밝아짐(콠 때는 정상) | `transition` 선언을 `body.reading-lamp` **안에** 남겨 둔 탓에, 클래스가 걷힐 때 전환 규칙까지 함께 사라져 값이 보간 없이 점프 | 전환을 클래스 밖(`body`, `body main`, `body .lamp-page`)으로 옮겨 양쪽 상태가 다 보게 함(css 셀렉터 2줄) | ✅ |
| 측정 중에 여백을 열면 타이머가 그대로 돌아 「글 쓴 시간」이 독서 시간에 섬임 | 여백은 탭 밖 전역 뷰라 탭 잠금(서재·책방·기록)에서 빠져 있었고, 세션은 서버 상태라 화면을 갈아도 계속 돈다 | 여백을 여는 문을 `openMargin` 하나로 모으고 그 문에서 측정을 끝낸다(재시작은 수동) + 화면에 종료 고지 한 줄 | ✅ |
| 여백에 들어가면 **하단 탭바가 통째로 사라져** 나갈 길이 「뒤로가기」 하나뿐 — 여백에서 바로 측정을 시작할 수도 없음 | 여백은 `MainTabs`보다 **먼저 조기 반환**되는 탭 밖 전체 화면이라, 탭바를 든 컴포넌트가 함께 언마운트된다 | 여백 전용 껍데기 `MarginShell`로 탭바를 되살리고, 가운데 원은 **홈으로 나가며** 측정을 시작한다 — 탭·원이 같은 문(`leaveMargin`)을 지나 「시작은 됐는데 여백에 남아 있다」가 구조적으로 불가능하다 | ✅ |
| 미니앱 첫 사용 안내의 진입점(홈 헤더 아래 **얇은 배너**)이 **놓치기 쉬움** — 처음 온 사람이 안내가 있는 줄 모르고 지나침 | 진입 직후 노출을 금지한 심사 대응(T-183)으로 안내를 수동 시작으로 뒤집으며 문을 배너 한 줄로 뒀는데, 홈에서 가장 눈에 안 띄는 크기였다 | 그 문을 **히어로 카드 자체**로 승격 — 안내가 남아 있으면 카드 <b>속</b>이 「처음이신가요? / 앱 사용법 보기」가 되고(껍데기·자리 그대로), 닫거나 다 보면 평소 타이머로 돌아온다. 측정 중에는 뜨지 않는다(「측정 중 N분」을 덮지 않게) | ✅ M-1 |

**관찰 중 / 후속 후보** (아직 미착수 — 발견했으나 우선순위 낮음):
- [x] 검색에 **출판사** 검색 기준 추가 ✅ (PR #326) — `BookSearchType.PUBLISHER`(알라딘 `QueryType=Publisher`) + 후필터 `switch` 확장. 라디오는 `values()` 자동 노출·결과 행은 기존 `book-pub`로 출판사 표시. (※ *검색 기준*만 — 내 책장 목록을 출판사로 거르는 필터는 범위 밖.)
- [x] 상태 필터에 **공개여부(PUBLIC/PRIVATE) 차원** 추가 ✅ (PR #327, 디자인 후속 #328) — 상태 × 공개여부 **직교 2차원 AND 필터**. `BookController.books`에 `visibility` 파라미터·`parseVisibility`(parseStatus 쌍둥이)·기존 status와 동일한 메모리 stream 필터, `books.html`에 🌍공개/🔒비공개 줄(상태와 **상호 파라미터 보존**)·htmx 부분 swap 유지. Repository·서비스 무변경. 표시는 **#328에서 재디자인** — 당초 상태 칩 `.shelf-filter`를 재사용했으나, 공개여부가 상태의 하위 차원임이 드러나게 책 행 공개 토글 룩의 **세그먼티드 토글**(`.vis-filter`: 연한 트랙 + 흰 알약 active·우측 정렬)로 분리(동작·URL 불변, 마크업·CSS만).
- [~] 책 동일성 키 **ISBN 정규화**(인기 카운트 정확도·중복 표시에 영향, SNS 카운트 선결과 연계).
  - ✅ **적재 시점 정규화**(PR #164) — `Isbn.normalize`(하이픈·공백 제거, 빈 값→null)를 `Book` 생성자 단일 통로에서 적용 + 기존 행 백필(V16). 빈 ISBN을 `""`로 저장해 서로 다른 책이 뭉치는 group-by 키 오염 차단.
  - [ ] (후속) **ISBN10→13 변환** — 현재 적재는 알라딘 `isbn13`만 받아 노출 적음(수동 입력에 ISBN 칸 생기면 선결).
  - **(보류 — 아마 불필요, 참고 기록만)** **개정판/세트 동일성** — "다른 ISBN = 같은 작품" 문제. **능동 백로그 아님**(2026-06-05 결정: 손대지 않기로). 왜 어렵고 왜 안 하는지만 남긴다.
    - **근본 원인 — ISBN은 작품(Work)이 아니라 판본(Manifestation)을 가리킨다(FRBR)**. 작품 = "마틴이 쓴 *클린 코드* 그 자체"(ISBN 없음), 판본 = 1판/2판/개정판/양장·문고(여기에 ISBN이 붙음). 그래서 "같은 작품, 다른 판본"은 태생적으로 ISBN이 다르고, isbn13으로 세는 인기 카운트는 판본별로 조각난다.
    - **개정판** — *합칠 수도, 그대로 둘 수도* 있고 정답 없음. 합치려면 ISBN 위 "작품 ID" 상위 키가 필요한데 알라딘이 안 줌 → 제목+저자 **fuzzy 매칭 추론**이 되어 오탐·미탐 범벅. 그래서 정규화(#1·#2)와 다른 범주.
    - **세트** — *조회를 막는 문제가 아님*. 세트는 그냥 검색에 나오는 책. 문제는 **동일성 모호**: 세트 ISBN ≠ 낱권 ISBN이라, "1권 읽는 사람" 셀 때 세트 보유자를 포함할지 깔끔한 키가 없음(세트→낱권 매핑 데이터도 알라딘 미제공). 역시 fuzzy 추론 영역.
    - **왜 안 하나** — 친구·소규모 단계에선 판본/세트 조각남이 카운트를 *조금* 흩뜨릴 뿐 치명적이지 않고, 제대로 하려면 fuzzy 매칭 = 오탐 관리 = 유지보수 부담이 큼. ROI는 **인기 카운트가 진짜 가치를 낼 만큼 사용자·책 수가 커져 "조각남"이 눈에 띌 때** 비로소 생긴다. 그 전까진(어쩌면 영영) 불필요.
- [x] ~~검색 후필터(#125)의 **페이저 과대 집계**~~ — **자연 해소 ✅ 2026-06-10 (PR #279)**. 페이저(`/books` 검색)를 한 배치(~40) 고정높이 스크롤 박스로 교체해 페이저 자체가 사라짐 → "N페이지" 과대 집계 표면 소멸. (필터로 40→소수가 되는 케이스는 트레이드오프로 수용: 박스에 들어 있는 것만 보이고 더보기 없음.)

---

## ⚖️ 법무 / 지식재산

### 저작권 등록 — 컴퓨터프로그램 저작물 (계획 ⏳ 2026-06-04, 우선순위: 낮음)

**왜**: 분쟁(소스코드 도용 등) 대비 **증거 확보**. 한국은 무방식주의라 등록 없이도 저작권은 이미 우리 것이지만,
등록하면 추정력(저작자·창작일)·대항력·법정손해배상·과실 추정의 이점이 생긴다. 창작 후 **1년 내** 등록해야
창작일 추정이 유효하므로 빠를수록 유리.

> 📄 **상세 가이드 → [claude-docs/copyright-registration.md](claude-docs/copyright-registration.md)** (조건·서류·절차·비용 정본).

- [ ] 저작자 확정 — 개인 vs 법인(업무상저작물)
- [ ] 등록 범위 정리 — 직접 작성 코드 / 오픈소스·서드파티(Spring·드라이버 등) 제외분 구분
- [ ] 발췌할 소스코드 범위 결정(영업비밀 고려, 일부 발췌 가능)
- [ ] CROS(cros.or.kr) 온라인 신청 — 프로그램 등록신청서 + 등록신청명세서 + 소스 업로드 + 수수료(온라인 5만 원대)
- [ ] (선택) 서비스명 "BookTimer" 보호 원하면 **상표 출원**(특허청 KIPO, 저작권과 별개) 검토
- [ ] (선택) 핵심코드 **임치(Escrow)** 제도 병행 검토
- **메모**: 분쟁 대비가 주목적이면 신청 전 한국저작권위원회 무료 상담(1800-5455) 1회 권장. 법률 자문 아님.

### [ ] (백로그) BookTimer 전용 이메일 분리 — 개인 메일 노출/결합 줄이기 (⏳ 2026-06-05, 우선순위: 낮음)

**왜**: 개인정보처리방침 문의처·OAuth 동의화면에 **개인 메일이 노출**되는 게 꺼려짐. 서비스 전용 메일을 따로 두면
공개 노출과 개인 신원의 결합을 끊을 수 있다.

**현황**: 전용 **Gmail 생성 시도했으나 같은 전화번호로 너무 자주 만들어 Google이 차단** — 당장 신규 Gmail 발급 불가.
임시로 문의처를 결합이 적은 다른 개인 메일(`tlatldhs0504@naver.com`)로 교체해 둠(PR #179). **전용 메일 분리는 미해결로 남김.**

**나중에 할 일** — "이메일"은 서로 독립된 3층이라 보이는 곳만 갈아끼우면 됨(프로젝트 이전 불필요):
- [ ] **A. 앱 문의처** (`privacy.html` `mailto:`) — 전용 메일로 교체. HTML 한 줄.
- [ ] **B. Google OAuth 동의화면** — *User support email* / *Developer contact* 를 전용 메일로. (전용 메일을 GCP IAM 멤버로 추가하면 support email 드롭다운에 뜸. 테스트/미검증 앱이면 재심사 트리거 안 함)
- [ ] **C. GCP 프로젝트 소유 계정** — 사용자에게 안 보이므로 **이전 불필요**. 정 불안하면 전용 메일을 IAM *Owner* 로 공동 추가만(이전 아님).
- **메모**: 전용 Gmail이 막히면 다른 도메인 메일/별칭(+전화번호 안 묶인 것)도 후보. 핵심은 B의 support email이 "내가 접근권한 가진 주소"여야 한다는 것.

---

## 💰 비즈니스 모델 / 수익화

> 서비스로 **돈을 버는 축**. 현재 **실수익 토대는 ① 제휴(알라딘 3%, 구매 클릭 추적 §책 단위 기록)뿐** — ② **디스플레이 광고(Google AdSense)**는 연동을 마쳤으나 **심사 거부로 보류 ⏸️**(아래). 제휴는 거래가 일어나야 들어오는 **간헐적·소액**, 광고는 승인 시 **노출 기반 상시·소액**이 될 축.
> 아래는 추가 구상 — ① **기존 제휴 축 확장**(eBook 제휴 링크·독서 도구 제휴) ② **반복 매출(MRR)** 을 만드는 **월 정액 구독**. ⚠️ 이 둘은 **아이디어 단계 — 기록만. 구현 금지.** 가격·무료/유료 경계·법적/운영 부담 전부 미확정.

### 📢 디스플레이 광고 (Google AdSense) — 연동 완료 / 심사 거부 → 보류 ⏸️ (연동 코드 #226·#227·#230 / 심사 거부 2026-06-17)

> **⏸️ 심사 결과 — 거부 → 보류 (2026-06-17):** AdSense가 `booktimer.click` 사이트를 **"가치가 별로 없는 콘텐츠(최소 콘텐츠 요건 미달)"**로 거부. 공개 랜딩(#230)을 뒀어도 **실콘텐츠가 전부 로그인 뒤**라 크롤러가 보는 공개 면이 랜딩 1장+폼 수준 → 기준 미달. **도메인/리다이렉트가 아니라 공개 콘텐츠 부족**이 사유라, 콘텐츠를 늘리지 않는 한 재심사해도 동일 거부 → **현재 보류**(연동 코드·ads.txt·랜딩은 그대로 두고, 공개 콘텐츠 확충 시 재심사). 아래 본문의 "완료" 서술은 *연동 코드* 기준이며 *심사 통과가 아니다*.

> **한 줄**: "마찰 최소 독서 습관"이라는 정체성과 충돌하지 않게, **부가 화면 하단에만 1개씩** 비침습적으로. 핵심 동선(타이머가 도는 대시보드 상단·온보딩·로그인)엔 광고 0.

- **노출 위치**: 독서 기록(`/history`)·책 목록(`/books`)·책 상세(`/book-detail`)·프로필(`/u/{loginId}`)·검색(`/search`) 각 하단 + **대시보드 하단**(라이브 영역 밖 — 측정 start/stop 시 광고가 재요청되지 않음). 페이지당 광고 단위 1개.
- **config-gated 스캐폴드**: `booktimer.ads.client-id`(=`ADSENSE_CLIENT_ID` ENV)가 비면 [AdsProperties](src/main/java/com/booktimer/config/AdsProperties.java) `isEnabled()=false` → 템플릿 fragment([ads.html](src/main/resources/templates/ads.html))가 **아무것도 렌더 안 함**. AdSense 사이트 승인 전까지 ID를 비워 깨진 빈 광고·정책 위반을 막고, 승인 후 ENV/SSM로 실값 주입 시 켜진다(실값 커밋 금지 — OAuth 키와 동일 정책).
- **구조**: 공유 레이아웃이 없어 재사용 fragment(`loader`=head 스크립트, `unit`=본문 광고) + 전역 `@ModelAttribute`([AdsModelAdvice](src/main/java/com/booktimer/web/AdsModelAdvice.java))로 `ads`를 모든 뷰에 주입. `.ad-slot`에 "광고" 라벨(투명성·정책).
- **소유권 검증 — ads.txt 방식 (PR #227)**: 이 앱은 default-deny + 루트가 `/login`으로 리다이렉트라, AdSense "코드 스니펫" 검증은 크롤러가 공개 HTML에서 코드를 봐야 해 까다롭다. 그래서 **정적 [ads.txt](src/main/resources/static/ads.txt)**(`google.com, pub-..., DIRECT, f08c47fec0942fa0`)로 검증 — 로그인·리다이렉트·JS 영향 0이라 가장 확실하고, 추후 "수익 위험(earnings at risk)" 경고도 예방. `SecurityConfig`에 `/ads.txt` permitAll 추가(비인증 공개). **검증(소유권)과 serving(광고 노출)을 분리** — ads.txt는 소유권만 증명, 실제 광고는 위 config-gated client-id/slot로 승인 후 켠다.
- **공개 소개(랜딩) 페이지 — 콘텐츠 심사 대비 (PR #230)**: 심사는 *콘텐츠 크롤링*도 하는데 이 앱은 본문이 전부 로그인 뒤라 크롤러가 볼 게 거의 없어 "저가치 콘텐츠"로 반려될 위험이 컸다. 그래서 루트 `/`를 **익명이면 공개 소개 페이지([landing.html](src/main/resources/templates/landing.html)), 로그인이면 기존 대시보드**로 분기([DashboardController](src/main/java/com/booktimer/web/DashboardController.java) `principal==null` 가드). Google은 루트 도메인을 크롤하므로 루트에 "무엇을 하는 서비스인가"(소개·핵심 기능·동작 방식·CTA) 실본문을 둬 심사에 노출. `SecurityConfig`에 `/` permitAll 추가 — 단 대시보드 데이터는 principal이 있을 때만 로드돼 노출 위험 없음(보호 경로 default-deny는 그대로, `/books` 등은 여전히 `/login`으로 튕김). 히어로 CTA는 로그인 카드와 같은 세로 스택 — **무료로 시작하기(이메일) → "또는" → Google로 시작하기**(`/oauth2/authorization/google`, 신규면 자동 가입이라 한 탭 진입, PR #231).
- **EEA/UK 동의(CMP)**: AdSense 동의 메시지는 **Google CMP "3가지 선택(동의·동의하지 않음·옵션 관리)"** 선택 — 거부를 동의만큼 쉽게(GDPR 원칙). 배너는 `adsbygoogle.js`가 자동 게재해 코드 변경 0.
- **남은 외부 작업(코드 아님)**: AdSense 사이트 검토 요청 ✅ → **심사 거부(2026-06-17, "가치가 별로 없는 콘텐츠")로 보류 ⏸️** — 공개 콘텐츠를 확충해 재심사를 통과해야 → 광고 단위 생성 → `ADSENSE_CLIENT_ID`/`ADSENSE_SLOT` SSM 주입 → **⚠️ 광고가 실제로 뜨는지 확인(CSP 때문에 새로 생긴 단계, 아래)**.
- **⚠️ 광고를 켜는 날 반드시 확인 — CSP가 막을 수 있다 (2026-08-15 추가)**: 2026-08-15 CSP를 걸었는데(`script-src 'nonce-…' 'strict-dynamic' …`), **그 시점 광고는 심사 거부로 꺼져 있어(`ADSENSE_CLIENT_ID` 미설정 → 프래그먼트가 아무것도 렌더 안 함) 실제 노출을 한 번도 검증하지 못했다.** 즉 이 조합은 **미검증 상태로 잠들어 있다.**
  `'strict-dynamic'`은 "nonce 받은 스크립트가 심은 것은 믿는다"로 신뢰를 전파하므로 원리상 AdSense 로더가 런타임에 심는 크리에이티브 스크립트까지 통과해야 하고(로더 태그엔 nonce가 붙어 있다 — [ads.html](src/main/resources/templates/ads.html)), 그래서 이 방식을 골랐다. 다만 **원리와 실측은 다르다.**
  ⚠️ **위험한 건 실패 형태다** — 막히면 에러 페이지가 아니라 **광고 자리만 조용히 비고**, 원인은 브라우저 콘솔의 CSP 위반 메시지에만 남는다. "승인은 났는데 광고가 안 뜬다"를 CSP와 연결할 사람이 없으면 수익 축이 통째로 멈춘 채 방치된다.
  **켜는 순서**: SSM 주입 → 광고 있는 화면(책 상세·책장·대시보드)을 실 브라우저로 열기 → **개발자도구 콘솔에 `Content Security Policy` 위반이 없는지** 확인 → 광고 렌더 확인.
  **막히면 최소 롤백**: [CspNonceFilter](src/main/java/com/booktimer/security/CspNonceFilter.java)의 `script-src`에서 **`'strict-dynamic'`만 빼고** 구글 광고 호스트(`*.googlesyndication.com`·`*.doubleclick.net`·`*.googleadservices.com`·`*.google.com`·`*.gstatic.com`)를 허용목록으로 나열한다 — 그러면 호스트 허용목록이 다시 유효해진다. 나머지 지시자(`object-src 'none'`·`base-uri`·`form-action`·`frame-ancestors`)는 그대로 유지한다.
- **로그인 뒤 페이지 광고 품질 — 크롤러 로그인 정보 (운영 후속, 코드 아님, 2026-06-09)**: 광고 단위는 로그인 뒤 화면(대시보드·`/history`·`/books` 등)에 있는데, AdSense 크롤러가 로그인 뒤를 못 보면 맥락(contextual) 광고 매칭이 약해 **관련성 낮은 광고**가 뜰 수 있다(빈 광고가 되는 건 아님). **AdSense 콘솔 → 설정 → 크롤러 액세스에 로그인 정보(크롤러 전용 계정 등) 등록**하면 로그인 뒤 페이지도 크롤링해 게재 품질이 오른다. ⚠️ *심사 통과·사이트 접근성* 자체는 공개 랜딩(PR #230) + robots `Allow: /` + `permitAll`로 이미 충족 — 이건 그 다음 단계인 **게재 품질** 후속이다. 구글 AdSense "사이트에 접근할 수 없는 경우" 체크리스트 3번(로그인 필요 페이지에 광고 게재)이 정확히 이 경우. 코드 변경 없음(콘솔 설정).

### 📕 eBook 제휴 링크 추가 — 종이책→eBook 귀속 모호 제거 (아이디어 ⏳ 2026-06-07, 우선순위: 미정 / 기록만, 선결 검증 있음)

> ⚠️ **기록만 — 구현 전 (b) eBook 수수료 검증 필수.** (논의: 2026-06-07.)

**배경**: 현재 도서 검색은 **종이책만** 뜬다(`AladinBookSearchClient.buildSearchUrl`이 `SearchTarget=Book` 하드코딩). 사용자가 우리 "구매" 링크로 알라딘에 가면 종이책 페이지가 뜨고, 거기서 eBook으로 갈아타 사면 **그 구매가 내 TTBKey로 정산되는지 모호**하다(제휴 귀속은 알라딘 정책 — TTB는 클릭 후 24h 창 기준이라 갈아타도 잡힐 *가능성*은 있으나 단정 불가).

**아이디어**: eBook을 직접 검색·등록할 수 있게 해서(`SearchTarget=eBook`), **구매 링크가 처음부터 eBook 상품을 가리키게** 한다 → 판본 전환 없이 직접 랜딩이라 귀속 모호함이 사라진다.

**⚠️ 걱정의 종류가 (a)→(b)로 바뀔 뿐, 사라지는 게 아님 (핵심)**:
- (a) **어느 판본이 내 TTBKey로 잡히나**(추적/귀속) → 직접 eBook 링크면 ✅ 해결.
- (b) **eBook이 애초에 수수료 대상인가 / 율이 얼만가** → ❌ 링크와 무관한 **알라딘 정책 사실**. eBook이 0%거나 낮은 율이면 직접 링크는 그 율을 *확실히* 받게 할 뿐 더 받게 하지 않는다.
- **그래서 만들기 전 (b)부터 검증**: TTB 실적 페이지 / 고객센터 / 테스트 구매 1건(단 self-referral은 제외될 수 있어 남의 구매로 봐야 깨끗).

**설계 시 결정할 것**(검증 통과 후):
- **책 동일성(isbn13)** — eBook은 ISBN이 다르거나 없을 수 있어, 같은 책이라도 책BTI 집계·인기 카운트에서 *다른 책*으로 잡힌다(우리 식별 키가 isbn13). 섞임 처리 정책.
- **노출 방식** — 종이책/eBook 토글 vs 검색타입 추가 vs **한 책에 두 링크(종이/eBook) 동시 제공**(UX·전환·정산 최선이나 작업량 최대).
- **대체 아닌 추가** — 종이책 원하는 사용자도 있으니 eBook은 더하는 것.

**선결/연계**: (b) 검증 → 통과 시 설계. 책BTI Phase 1(장르 적재)이 `SearchTarget` 근처를 이미 만졌으므로 검색 타깃 파라미터화는 작은 변경.

### 🔧 독서 도구 제휴 — 책받침대·책갈피 등 독서 보조 용품 (아이디어 📝 2026-06-12, 우선순위: 낮음 / 기록만)

> ⚠️ **기록만 — 구현 미착수.** 직접 판매(결제·배송·재고)가 아니라 **제휴 검색 링크 중개**로 한정. (논의: 2026-06-12.)

**아이디어**: 책뿐 아니라 **독서를 돕는 도구**(독서대·책받침대·책갈피·북커버·리딩 라이트 등)를 제휴 링크로 노출·홍보해 **기존 제휴 수익 축을 상품군으로 확장**한다.

**왜 가벼운가 — 인프라 8할이 이미 있음 (핵심)**: 쿠팡 제휴는 이미 `f(검색어, 추적코드, 템플릿)`로 검색 링크를 런타임 생성하는데([CoupangLinkBuilder](src/main/java/com/booktimer/book/CoupangLinkBuilder.java) `buildSearchLink(template, query, trackingCode)`), 이 빌더는 **임의 검색어**를 받게 일반화돼 있어 책 대신 `"독서대"`·`"책갈피"`를 넣으면 그대로 동작한다. 추적코드·URL 템플릿·공정위 고지문구·`coupangEnabled` 플래그·클릭 추적([CoupangModelAdvice](src/main/java/com/booktimer/web/CoupangModelAdvice.java))이 전부 재사용 가능 → **새로 짤 건 노출 화면 한 곳 + 보여줄 도구 큐레이션 목록 정도.** (쿠팡 파트너스는 책이 아닌 일반 상품도 커버.)

**thesis 정합성**: 독서대·리딩 라이트 등은 "독서 마찰을 낮추는" 도구라 §전략 thesis(입문/포기형 친화)와 **안 어긋난다**. 단 본질은 습관 형성이지 용품이 아니므로 **부수 컨텐츠**로 둔다.

**경계 (정체성 희석 방지)**:
- **노출 위치** — 홈·대시보드 전면 배너 ✗(습관 타이머 정체성 훼손). **책 상세·책장 곁의 맥락 있는 작은 섹션**("이 독서를 돕는 도구") ○. AdSense 비침습 원칙(§디스플레이 광고)과 동일 정신.
- **제휴 중개 한정** — "구매"는 쿠팡/알라딘으로 보내는 것. **직접 커머스(장바구니·결제·배송·재고·반품)로 확대 금지** — 차원이 다른 일.
- **공정위 고지** — 책 링크와 동일 패턴 재사용(이미 처리).

**설계 시 결정할 것**(착수 시):
- **큐레이션 출처·규모** — 보여줄 도구 목록을 정적 시드로 둘지, 관리자 입력으로 둘지. 시작은 정적이 가벼움.
- **알라딘 병행 여부** — 알라딘 굿즈/문구도 일부 커버하나 쿠팡 파트너스가 일반 상품엔 더 넓음. 쿠팡 우선이 자연스러움.
- **노출 트리거** — 고정 추천 vs 책 메타(장르 등) 연계 추천.

**우선순위 낮음 (근거)**: retention 레버가 아니라 **수익화 축**이고, 현재 최우선은 엔진 A retention(§전략 ① 재참여 이메일 넛지)·이메일 점등이다. 트래픽·밀도가 붙은 뒤 가볍게 붙이는 게 ROI가 좋다.

**연관**: §디스플레이 광고(비침습 노출 원칙), §eBook 제휴 링크(같은 "제휴 축 확장" 형제), [CoupangLinkBuilder](src/main/java/com/booktimer/book/CoupangLinkBuilder.java).

### 🔗 "독서 소셜" 묶음 — 월 정액제 (아이디어 ⏳ 2026-06-05, 우선순위: 미정 / 기록만)

> 🎯 **audience·순서 주의 (2026-06-06)**: 이 묶음은 통째로 **§전략의 엔진 B(헤비 리더용 사람 잇는 재미)**다 —
> thesis의 핵심 audience(입문자)가 아니다. **밀도(사람 수)가 차기 전엔 가치 ≈ 0**(텅 빈 추천·채팅은 마이너스)이고,
> **채팅(3단계)은 가장 무겁고 위험**(모더레이션·법적, 아래 선결 목록). 따라서 §전략 순서를 따른다 —
> **솔로 retention(엔진 A)으로 사람을 모으고 → 밀도 신호 뒤 가벼운 소셜부터 → 채팅은 맨 끝.** 지금은 기록만.

**한 줄**: 책장으로 **나를 알고(AI 성향)** → **사람을 잇고(성향 매칭)** → **대화한다(채팅)**. 이 세 단계를 하나의 **프리미엄 구독**으로 묶어 월 정액 수익을 만든다.

**묶는 3기능** (단계가 자연스럽게 이어짐):

| # | 기능 | 토대 | 비고 |
|---|---|---|---|
| 1 | **AI 책장 성향 파악** — 내 책장을 AI가 읽고 "이런 독자다" 설명문 | [reading-personality-design.md](claude-docs/reading-personality-design.md) | 성향 = 매칭의 입력(비교용 태그) |
| 2 | **사람 추천** — **비슷한 성향** 독자 추천 + **정반대 성향** 독자 추천 | 1의 태그/점수 + SNS 팔로우([sns-design.md](claude-docs/sns-design.md)) | "끼리"뿐 아니라 "정반대"도 — 새 책·관점 발견의 재미 |
| 3 | **채팅** — 추천으로 만난 사람과 1:1(또는 그룹) 대화 | SNS 관계 토대 | 추천→대화로 **관계가 닫혀** 리텐션·결제 동기 |

> **왜 이 묶음인가**: 1→2→3이 **하나의 흐름**(나를 알고 → 닮은/반대 사람을 찾고 → 말을 건다)이라 **하나의 구독**으로 파는 게 자연스럽다. 각각 떼서 팔면 가치가 약하지만, 묶으면 "독서로 사람을 만나는 경험"이 된다.

**왜 정액제(구독)인가**:
- AI 성향(LLM 호출)·매칭·채팅은 **계속 쓰는 기능** → 1회 결제보다 **반복 과금**이 맞다.
- LLM **변동비**(호출당 과금)를 **구독료가 상쇄** — 무한 무료로 풀면 비용이 샌다(성향 분석 메모 §캐시·비용 참고).
- 제휴(간헐·소액)와 **상호보완** — 구독은 예측 가능한 MRR, 제휴는 거래 발생 시 추가 업사이드.

**무료 / 유료 경계 (제안, 미확정)**:
- **무료**: 독서 타이머·잔디·책장·기본 SNS(팔로우/공개 프로필) — **핵심 가치는 무료**로 유지(획득·리텐션).
- **유료(구독)**: ① AI 성향 설명문(주기적 재분석) ② 사람 추천(비슷/정반대) ③ 채팅. = **"관계/발견" 레이어가 프리미엄.**
- 무료 맛보기 후크: 성향 분석 **1회 무료** 또는 추천 **N명까지** 미리보기 → 결제 전환 유도.

**선결 / 리스크 (착수 전 반드시)**:
- [ ] **채팅 = 운영·법적 부담 큼** — 신고/차단/모더레이션, 미성년 보호, 통신/개인정보, 악용(스팸·괴롭힘). MVP는 채팅을 **뒤로** 미루거나 제한적으로(추천 매칭 수락 시에만) 여는 것 검토.
- [ ] **프라이버시** — 성향·매칭이 타인에게 노출 → **PUBLIC 책 기반만**(비공개 간접 누출 차단, sns §3.5 원칙). 정반대 추천도 같은 경계.
- [ ] **결제 인프라** — PG/구독 결제(국내 PG·앱스토어 인앱결제 수수료), 정기결제·환불·세금계산서.
- [ ] **⚠️ 계정 무결성 선결 — 결제·채팅 전에 이메일 인증부터** — 지금은 로컬 가입이 이메일을 검증 안 해
      **account pre-hijacking** 갭이 열려 있다(N-053: 공격자가 피해자 이메일로 계정 선점 → 진짜 주인이 소셜
      로그인하며 올라탐). 친구·소규모·가벼운 데이터일 땐 *감수 가능한 위험*으로 보류했지만, **계정에 돈(결제)이나
      사칭 표면(채팅)이 묶이는 순간 탈취 가치가 폭증**해 더는 보류할 수 없다. 따라서 **§하드닝 「이메일 발송
      인프라」(가입 이메일 인증)는 결제·채팅의 선행조건** — 이 묶음을 닫기 전에 1·2단계는 가능해도 **결제/채팅(3단계)
      착수는 금지**. 개념: learning-notes **N-053**.
- [ ] **콜드스타트** — 사용자·책 데이터 적으면 추천/매칭 품질 낮음. 임계 사용자 수 전엔 가치 약함(네트워크 효과).
- [ ] **가격** — 월 얼마? 단일 티어 vs 다단계. 출발 가설은 아래 **「가격 가설」** 참고(미확정).
- [ ] **LLM 단가 통제** — 구독자당 분석 빈도 상한·캐시(성향 메모 §6).

**선후**: 1(AI 성향) → 2(매칭, 1의 태그 필요) → 3(채팅, 가장 무겁고 리스크 큼). **1·2 먼저 검증 후 3은 별도 판단.** 전부 **SNS·성향 토대가 깔린 뒤** 얹는 상위 레이어 — 그 전엔 착수 금지.

**💵 가격 가설 (출발점, 미확정)**:

> **출시 4,900~5,900원/월 (연 49,000원 ≈ 월 4,083원), 만원 밑 단일 티어.** 네트워크가 차고 매칭 품질이 증명되면 **9,900원까지** 인상 여지.

근거(가설일 뿐 — 락인 전 검증 필요):
- **원가 기준 폐기 — 가치 기준**: LLM 변동비는 캐시하면 호출당 1원 미만(마진 ≈ 100%). "원가+마진"이 아니라 **지불의향(WTP)** 으로 매겨야 한다.
- **앵커링은 밀리(전자책 무제한 ~9,900) 아래**: 우리는 **콘텐츠(책)를 주는 게 아니라** "성향 통찰 + 사람 발견 + 대화"라는 **소셜/재미 레이어**다. "무제한 책도 아닌데 9,900?" 저항을 피해 만원 밑에 둔다. (데이팅앱 15,000~30,000은 '절박' 페인이라 가격대가 다름 — 우리는 nice-to-have.)
- **포지셔닝 일관성**: 성향 분석을 "MBTI처럼 가볍게"로 잡았으니, 가벼운 재미엔 낮은 가격이 맞다.
- **초기 침투가격(penetration)**: 추천·채팅은 네트워크 효과라 콜드스타트 땐 실가치가 낮다 → 낮게 풀어 기반을 키우고, 밀도가 차서 추천이 진짜 맞기 시작하면 인상.
- **연간 묶기**: 49,000원(≈2개월치 할인)으로 현금흐름 선확보 + 리텐션(가벼운 상품일수록 월 결제는 쉽게 해지됨). 초기엔 단일 티어로 단순하게.
- **앱스토어 컷 반영**: iOS/안드로이드 인앱결제는 30%(영세 15%) 공제 → 9,900도 실수령 ~6,930. 가능하면 **웹 결제(국내 PG ~2~3%)** 유도. floor 잡을 때 이 컷으로 LLM·서버·PG 비용을 덮는지 점검.
- **검증 권장**: 락인 전 **Van Westendorp(가격 민감도) 설문** 또는 베타 A/B로 실제 WTP 측정 — 마진이 높아 "너무 싸게 받는" 기회손실이 오히려 리스크.

---

## 📣 홍보 / 마케팅

> 서비스를 알리는 축. 아직 구체화 전 — 아이디어만 모아두는 단계.

### 커뮤니티 기반 홍보 (계획 ⏳ 2026-06-04, 우선순위: 미정)

**아이디어**: 북카페 같은 **독서 커뮤니티를 통한 홍보**를 우선 검토.
독서 타이머·책 기록이 핵심이라 책 좋아하는 사람이 모인 곳과 결이 맞는다.

- [ ] 자세한 채널·방식·메시지는 **아직 미정** — 추후 구체화.

### ⚠️ 홍보 전 선수과정 — 서버 용량 (실측 완료 ✅ 2026-08-13)

> **Claude 지시(고정)**: 사용자가 "홍보글 써줘"라고 하면, 홍보글을 쓰기 전에 이 절의 **실측 결과부터 안내한다.**
> (2026-08-13 이전엔 "증설 체크리스트부터"였다 — 실측이 끝나 증설 항목 대부분이 근거를 갖고 닫혔으므로,
> 이제 이 게이트는 "증설하라"가 아니라 **"이미 버틴다, 다만 이런 조건에서 꺾인다"**를 알리는 역할이다.)

**결론: 지금 스펙으로 홍보해도 된다.** 꺾이는 지점은 **200 VU**이고, 현재 실트래픽은 그 근처에도 못 간다
(평시 CPU 1.7%, 버스트 크레딧 576 만땅 유지 = baseline의 1/10도 안 씀).

**실측** (2026-08-13, k6 v2.2.0 → 운영 `booktimer.app`, `load-test/booktimer-load.js`, 총 8.2만 요청)

| 부하 | 처리량 | median | p95 | 판정 |
|---|---|---|---|---|
| 100 VU | 229 req/s | 78ms | 271ms | 여유 |
| 150 VU | 214 req/s | 204ms | 773ms | 상승 시작 |
| 200 VU | 199 req/s | 447ms | **1,624ms** | ← 게이트(p95 1.5s) 초과 |
| 250 VU | 190 req/s | 693ms | 2,464ms | 꺾임 |

- **처리량이 100 VU에서 최대(229 req/s)를 찍고 이후 감소** = 포화 신호. 150 VU부터는 더 처리하는 게 아니라 큐만 쌓인다.
- **실패율 0%** — 250 VU에서도 에러·타임아웃·OOM·커넥션 고갈 없이 **지연만** 늘었다. 죽는 게 아니라 느려진다.
- CPU(CloudWatch): 평시 1.7% → 60 VU 50% → 150 VU 76% → 300 VU **95%(포화)**.
- 크레딧: 20분 부하에 576 → 559(−17). 몇 시간짜리 홍보 스파이크는 크레딧으로 넘긴다.

**병목은 CPU다 — 옛 추정이 틀렸다.** 이 절은 원래 "① 단일 태스크 → ② 세션 DB → ③ HikariCP 풀 10 →
④ 버스트 크레딧" 순으로 추정했는데, **실측에선 ②③이 아예 안 걸렸다**(커넥션 고갈·세션 병목 0건). CPU가 먼저 포화한다.
따라서 세션 Redis 외부화·풀 크기 조정은 **근거 없음으로 닫는다**(아래 체크리스트).

**실사용자 환산** — k6 VU는 think-time 1초로 쉬지 않는 패턴이라 실사용자보다 훨씬 공격적이다.
"활발히 쓰는 중 = 분당 3요청"(읽는 중엔 타이머가 클라에서만 돌아 **서버 요청 0**) 기준:

- **지속**(CPU 20% = 크레딧 무소모, 무기한): 약 40 req/s → **동시 활동 800명**
- **버스트**(CPU 95%, 크레딧으로 6~11시간): 약 230 req/s → **동시 활동 4,000명대**

> ⚠️ **낙관 보정 (이 숫자를 그대로 믿지 말 것)**: ① 단일 테스트 계정이라 모두 같은 행을 읽어 MySQL buffer pool
> 히트율이 실사용보다 유리하다(가장 큰 낙관 요인) ② 측정 경로는 대시보드·독서기록 2개뿐 — 서재·잔디·소셜 미측정
> ③ 로그인은 VU당 1회라 **BCrypt가 비싼 신규 가입·유입 폭주는 별개 시나리오**다.
> **위 숫자를 절반으로 깎아 보는 게 안전** — 그래도 동시 활동 400명 / DAU 수천 명이라 현 트래픽 대비 여유가 크다.

**체크리스트 (실측으로 정리됨)**
- [x] **부하 테스트로 실측** ✅ 2026-08-13 — 위 표. 꺾이는 지점 200 VU 확정(추정 → 사실). 실행 함정은 T-158.
- [x] **세션 DB→Redis(ElastiCache) 외부화** — ❌ **불필요로 닫음**. 세션 DB는 병목이 아니었다(CPU가 먼저 포화).
- [x] **HikariCP 풀 10 조정** — ❌ **불필요로 닫음**. 250 VU에서도 커넥션 고갈 0건.
- [x] **RDS 한 단계 업** — ❌ **무효**. 2026-07-28 EC2 이전으로 RDS 자체가 없다(MySQL이 같은 EC2에 컨테이너로 동거).
- [ ] (필요해지면) **CloudWatch 알람**(CPU·5xx) 사전 경보 — 증설보다 먼저 할 값싼 안전판.
- [ ] (필요해지면) **인스턴스 상향** — 병목이 CPU이므로 메모리를 늘리는 t3.medium이 아니라 **c 계열(compute-optimized)**이 맞다.

> 인프라 스펙 근거(현행): `deploy/compose.prod.yaml`(EC2 t3.small 1대에 app 700m·MySQL 600m·Caddy 128m 동거,
> 스왑 2GB), `application-prod.properties`(Spring Session JDBC), `claude-docs/deploy-ec2.md`.
>
> **⚠️ 아래는 ECS Fargate 시절(~2026-07-28) 이력** — §ECS→EC2 이전으로 **전제가 통째로 바뀌었다**.
> 당시 판단 근거로만 남긴다: ECS 오토스케일링 min2/max4·CPU70 점등(2026-06-12, #322·#324, 적용 함정 T-045) →
> 비용 지혈로 min 2→1 되돌림(2026-07-28, 월 −$18) → **EC2 이전으로 둘 다 무효**. 지금은 오토스케일링이 **없다**
> — 단일 인스턴스이므로 상향은 수동이고, 그래서 위 "꺾이는 지점"을 아는 것이 오토스케일보다 중요해졌다.

---

## 🧹 기술 부채 / 후속 정리

### 종이·연필 테마 — 남은 검증 2건 (2026-08-18)
> 웹·미니앱 전면을 「거친 종이 위에 연필로」(A안)로 바꿨다. 구현은 끝났고 아래 둘만 확인이 남았다.
> ⚠️ **1차 구현은 실기기에서 반려됐다(2026-08-18)** — 「어색하다, 그냥 선의 픽셀을 깨뜨린 것 같다」. 선을 미는 방식(`feDisplacementMap`)이 `border-image`의 `stretch` 압축을 만나 좁은 요소에서 가장자리가 깎인 탓이었다. 굴곡을 걷고 **농도 얼룩**(고주파 알파 노이즈)으로 바꿔 해결했다(T-190).
> ⚠️ **2차 반려(2026-08-18)** — 「책이 약간 반투명해지고 흐릿해져 보인다」 + 「버튼 빗살무늬가 지저분하다」. 앞의 것은 **종이 결 레이어**가 범인이었다(무채색 노이즈 30% = 표지 사진 위에선 안개). 노이즈를 색이 아니라 알파로 옮겨 해결(T-191). 뒤의 것은 사선 해칭을 걷고 연한 세이지 채움으로 교체.
> ⚠️ **3차 반려(2026-08-18)** — 「스크롤하면 배경·요소는 움직이는데 결 무늬만 제자리다」. 결 레이어가 `position: fixed`라 뷰포트에 못 박혀 있었다. `absolute` + `body{position:relative}`로 종이와 함께 움직이게 하고, z-index를 9999→90으로 낮춰 떠 있는 것들(탭바·시트·모달) 아래로 넣었다(T-192). 
> ⚠️ **4차 반려(2026-08-19)** — 「너무 어둡고 얼룩이 과하다」. 결 입자가 픽셀의 61%를 덮어 실제로 보이는 종이색이 토큰(#F3EEE4)이 아니라 합성 결과(#E5E0D6)였다. 켜지는 픽셀을 25%로 줄이고 종이색을 #F7F2E8로 밝혀 합성 결과를 #F4EFE5로 맞췄다(T-193). 아래 두 항목은 그대로 유효하다.
- ✅ **웹 로그인 뒤 화면 확인 — 완료 (2026-08-25)** · **예고한 그 자리가 실제로 있었다.**
      로컬 `bootRun` + 시드 계정(`testid`)으로 7화면을 실브라우저 계측(요소마다 `borderImageSource`를 재
      「테두리는 그리는데 프레임이 없는 것」을 셈). 위에서 예고한 **자체 클래스** 함정이 그대로 나왔다:
      - **홈 `/`** — `.dash-card` **4개 전부**(측정 히어로·잔디·바로가기·정원)가 매끈했다. 대시보드 전용
        자체 색(`#D8CDB9`)으로 테두리를 선언해 일괄 적용의 기준(`.card` 또는 `var(--border)`)에서 빠졌다.
        정작 그 카드 **안**의 `.dash-state-panel`·`.dash-nav-tile`엔 연필선이 있어 **한 화면에서 두 양식이 갈렸다**.
      - **책방 `/u/{id}`** — 같은 `.dash-card` 3곳(헤더·책BTI·탭 카드). 홈과 책방은 로그인 뒤 가장 많이 보는 두 화면이다.
      - **설정 `/settings`** — `.set-card` 6개는 정상인데 **`.set-card-danger` 하나만** 빠졌다. 뿌리가 다르다:
        **`border` 단축 선언이 `border-image`를 initial로 리셋**한다(바로 옆 `.set-input-danger`는 그래서
        `border-color`만 쓰고 있었다). `.set-check--card`·피드백 폼 입력 3종도 같은 이유.
      - 서재·기록·정원·책BTI는 **프레임 없음 0건**으로 원래 정상이었다.
      → 5곳에 확립된 양식을 이식하고, 자리마다 고치는 대신 **소스 전수 스캔 가드**(`frontend/src/pencilFrame.test.ts`)를
      세웠다 — 다음에 카드를 새로 만들며 프레임을 빠뜨리면 테스트가 먼저 운다. 재측정 결과 7화면 전부 0건,
      회귀 0, 콘솔 에러 0. ⚠️ **`border-radius`가 죽을 것이라는 우려는 실측에서 기우였다** — 프레임 SVG 자체가
      `rx='7'` 라운드라 모서리가 그대로 살아 있다. `.dash-card`의 `box-shadow`도 걷지 않았다(`.set-card`가
      이미 그림자를 유지한 채 프레임을 쓰고 있어, 그림자 제거는 `.card`만의 선택이지 양식의 일부가 아니다).
- ⬜ **실기기 성능** — 책방 격자처럼 카드가 수십 개 깔리는 화면. 데스크톱·목 모드는 여유가 커서 이 클래스를
      **원리상 못 잡는다**(T-176 발광 box-shadow와 같은 자리). 애니메이션이 0이고 `mix-blend-mode`도 피했으며
      필터는 data URI 디코드 때 1회만 계산되지만, 그 근거는 전부 이론이라 실기기 확인 전엔 미검증이다.
      ✅ 2026-08-18 수정으로 `feDisplacementMap`(픽셀 재배치)이 빠져 **디코드 비용 자체는 더 싸졌다** — 남은 건 `feTurbulence` 한 번뿐이다. 종이 결 레이어도 3겹→2겹으로 줄었다(T-191). 그래도 「수십 개 카드」 확인은 여전히 실기기 몫이다.
      ⚠️ 이 항목은 「읽고 끝낼 목록」이 아니라 **다음 미니앱 배포 전 체크리스트**다(T-176의 교훈).

### 미니앱 세션 캐시 — 탭·캐러셀을 오갈 때마다 화면이 비던 자리 (완료 ✅ 2026-08-21)
> 사용자 제보: 「간혹 책 표지·여백 같은 요소들의 렌더링이 느리다」. 조사해 보니 **렌더가 느린 게 아니라
> 매번 다시 받아오고 있었다** — 미니앱엔 캐싱 계층이 한 겹도 없었다(클라이언트 데이터 캐시 0, `@Cacheable`·
> Caffeine·Redis 0, API 응답은 Spring Security 기본 `no-store`라 브라우저 HTTP 캐시도 없음).
- **뿌리 1 — 탭 전환이 곧 재조회다.** `App.tsx`의 `{tab === 'home' && <Home/>}` 꼴이라 탭을 벗어나면 화면이
  언마운트되고 상태가 날아간다. 돌아오면 각 화면이 mount effect에서 fetch를 처음부터 다시 하고 그동안
  `<Loading/>`을 그린다. 서재가 가장 아팠다(화면 **전체**가 로딩). 홈 히어로·기록 잔디는 App이 든 prop이라
  원래 즉시 뜬다 — 아픈 곳은 피드·서재·책방·기록 하단이었다.
- **뿌리 2 — 여백 박스가 스냅을 하나만 든다.** `MarginBox`가 `bookId`마다 300ms 디바운스 후 조회하는데
  스냅이 한 개뿐이라, 캐러셀에서 **방금 본 책으로 되돌아와도** 렌더 가드가 로딩으로 떨어뜨리고 왕복이 다시 났다.
  훑을수록 나빠지는 구조.
- **한 일** — 언마운트 구조는 **그대로 두고** 모듈 레벨 세션 캐시(`cache.ts`, ~25줄) + stale-while-revalidate.
  - [x] 각 화면의 `useState` 초기값을 캐시에서 읽는다 → 재마운트 **첫 렌더부터** 옛 데이터가 선다.
  - [x] fetch는 지금처럼 매번 나가 캐시·화면을 갱신한다 — **요청을 아끼는 캐시가 아니다.** 그래서 stale 노출이
        최대 1 왕복이고, 무효화 난제가 구조적으로 축소된다(기존 `silentRefresh` 60초 스로틀보다 짧다).
  - [x] `MarginBox`는 캐시를 **렌더 중에** 고른다. effect로 갈아끼우면 페인트 뒤에 도는 탓에 캐러셀을 옮길 때마다
        「불러오는 중…」이 한 프레임 깜빡인다 — 없애려던 그 장면이 그대로 남는다.
  - [x] 무효화 3곳 — `token.clear()`에 `cacheClear()`(로그아웃·401·탈퇴가 전부 이 문을 지난다 = 계정 전환 누수 차단),
        `createStory`·`deleteStory`에 **요청 전** `cacheDrop('margin:')`(실패해도 캐시가 없어 fail-safe).
- **⚠️ `display:none` keep-mounted는 기각**했다 — `useBackClose` 스택 오염(숨은 화면이 뒤로가기를 삼킨다),
  화면 밖 effect·타이머 상시 동작, 게다가 이 앱은 document 스크롤 하나라 **스크롤 보존도 안 된다**. react-query도
  기각 — 필요한 건 캐시+재검증 20줄인데 `useQuery`는 effect 기반이라 정적 렌더 하니스와 궁합이 미지수다.
- **가드** — 신규 `cache.test.ts` 6건 + 화면별 캐시 단언(캐시 miss 시 기존 로딩이 유지되는 **대조군**, 계정 교차 격리,
  `createStory` 실패 시 fail-safe 포함) 총 22건. 캐시를 만지는 파일은 전부 `beforeEach(cacheClear)`.
- **결과**(목 모드 실측 — 「불러오는 중…」 DOM 출현 횟수): 서재 재진입 **0회** ↔ 캐시를 비운 대조군 **2회**,
  캐러셀에서 책 A 되돌아오기 **0회** ↔ 책 B 첫 방문(캐시 miss) 1회. 목 모드는 왕복이 0이라 원래 판별이 안 되는데,
  여백 박스의 300ms 디바운스가 대조군을 만들어 줘 **캐시가 원인임을 실증**했다.
- ⬜ **실기기 확인 (남음)** — 체감 지연이 실제로 줄었는지, 재검증 응답이 캐시 렌더를 덮을 때 깜빡임·스크롤 점프가
      없는지. 목 모드는 왕복 0이라 **원리상 이 판정을 못 한다**(T-176 계열). 다음 배포 전 체크리스트다.
- ⏸ **표지 이미지 HTTP 캐시는 범위 밖** — 표지는 알라딘 원본 직결(`Cover=MidBig` ≈200px)이라 크기 자체는 과대하지
      않다. 재마운트마다 재다운로드되는지는 실기기에서 확인할 후속 과제로 남긴다(프록시·리사이즈·CDN은 안 한다).

### 미니앱 타이포 스케일 — 개구 전환이 남긴 크기·강조 붕괴 (완료 ✅ 2026-08-21)
> 위 「종이·연필 테마」(#857)의 **후속**이다. 사용자 제보: 「폰트는 마음에 드는데 글자 크기·강조가 아쉽고
> 전체적으로 가독성이 떨어진 느낌」. 취향이 아니라 **웹 규칙을 미니앱에 그대로 옮긴 데서 생긴 기계적 결함**
> 셋이었다(목 모드 실측 · 4개 탭 텍스트 노드 486개 전수).
- **뿌리 1 — 크기 보정이 한 글자도 안 닿았다.** `html body { font-size: 112.5% }`는 웹 app.css에서 옮겨 온 개구
  보정인데, 웹은 본문이 전부 `rem`이라 먹지만 **미니앱 글자는 전부 px**다(TDS 타이포 토큰도 px, 인라인
  `fontSize: 13`도 px). `body`가 18px로 계산돼도 그 값을 물려받는 텍스트 노드가 **0개**였다. 개구는 같은 px에서
  본문용 한글 폰트보다 글자가 0.85배 낮고 0.83배 좁아, 보정이 빠진 만큼 앱 전체가 작아진 채였다.
- **뿌리 2 — 굵기가 「버튼 안이냐 밖이냐」로 갈렸다.** TDS `Text`는 굵기를 `var(--tds-paragraph-font-weight)`로
  그리는데 그 변수는 `fontWeight` prop을 넘겼을 때만 채워진다. 안 넘기면 선언이 무효가 돼 **굵기가 상속으로
  떨어지고**, UA 기본 `button { font: 400 }`이 body의 700 보정을 버튼 안에서만 취소한다. 앱의 `Text` 181개 중
  prop을 넘기는 건 7개뿐이라 나머지 174개의 굵기가 우연히 정해졌다 — 실측에서 400:700이 238:234로 갈렸다.
  결과는 뒤집힌 위계다: **누를 수 있는 것이 못 누르는 것보다 얇았다**(기록 날짜가 한 줄 걸러 얇고, 책방의
  `공개 책`과 `팔로워·팔로잉`이 한 줄에서 갈렸다).
- **뿌리 3 — 강조에 쓸 축이 없었다.** 본문이 이미 700이라 굵기로는 더 못 가고, 크기는 12·14px 두 값에 79%가
  몰렸고, `color="grey600"`이 타이포 선언 110개 중 77개였다. 웹이 쓰는 세리프(고운바탕) 축은 app.css 39곳인데
  미니앱은 화면 제목 **1곳**뿐이었다. `fontWeight: 500`(개구에 없어 400으로 떨어짐)·`<b>`의 900(700으로
  올림 = 주변과 동일)처럼 **선언은 했는데 화면엔 아무 일도 안 일어나는** 자리도 셋 있었다.
- **한 일** — 위계를 **크기 · 색 · 세리프** 셋이 맡고 굵기는 700 한 값으로 눕혔다.
  - [x] `global.css`에 `button { font-weight: inherit }` 한 줄 — 뿌리 2를 통째로 닫는다.
  - [x] `html:root`에 **TDS 타이포 토큰 9쌍 재정의**(크기 + 줄높이). 색 토큰(`--adaptive*`)을 갈아끼운 것과 같은
        수법이라 `Text` 181개가 한 줄도 안 바뀌고 따라온다. `t2`는 TDS 내부 사용이 0이라 히어로 전용으로 26→44px.
  - [x] `html b, html strong` 강조를 **세이지 색**으로(웹 `.entry-note strong`과 같은 처방). 명시도를 `html`로
        올린 이유는 TDS 리셋이 우리 css 뒤에 주입되기 때문 — `html body`와 같은 처지다.
  - [x] `ui.tsx`에 `SERIF_VALUE`(값·성취 이름) + `SectionTitle`(st10·잉크) 신설. 인라인 스타일 상수인 이유는
        정적 렌더 하니스가 **css를 적용하지 않아** 클래스로는 계측이 안 되기 때문(`PENCIL_FRAME`과 같은 이유).
  - [x] 강조가 필요한데 없던 자리 손질 — 홈 히어로 시계·기록 통계값·성장 단계·책방 카운트·닉네임·섹션 제목
        6종, 기록 하루 시간의 흐린 색 제거.
  - [x] 인라인 `fontSize` 리터럴 41줄을 같은 계단으로 올리고 `10`·`12.5`·`13.5` 같은 계단 밖 값을 없앴다.
- **결과**(같은 실측 기준): ≤14px 비중 **95% → 46%**, 세리프 노드 **2 → 9**, 굵기 400 **238개 → 0개**.
  ⚠️ 남은 600 3개는 TDS `Button` 내부값(`semibold`)이라 우리 소스 밖이고, 개구가 700으로 올려 화면은 같다.
- **가드** — `typography.test.tsx` 신설(13건): css 규칙·토큰·강조색, **소스 전수 스캔 2종**(개구에 없는 굵기 선언 0 /
  계단 밖 `fontSize` 리터럴 0), `SectionTitle`·세리프 값의 실제 마크업. 자리마다 고치면 다음 화면에서 또 샌다 —
  no-emoji 가드와 같은 사고방식이다.
- ✅ **실기기 확인 — 해소 (2026-08-25 확인)** · ~~iOS WebView는 같은 px에서 데스크톱 크롬보다 굵게 그린다.
      굵기를 700으로 통일했으니 실기기에서 **너무 굵을 수 있다**.~~ → **전제가 그 뒤 뒤집혔다.** 아래
      「또렷한 연필」 ③-A(2026-08-24)가 `html body`를 고운돋움으로 바꾸며 전역 굵기를 **`700` → `400`으로 명시**해,
      이 항목이 걱정한 「700 통일」 상태 자체가 사라졌다. 그리고 같은 클래스의 실기기 판정인 **U-1(합성 볼드)이
      2026-08-24 사용자 폰 확인으로 충족**됐다(그 항목의 EXPECT가 「읽기 어렵지 않다」는 육안 판정이었다).
- ✅ **스토어 스크린샷 재촬영 — 완료 (2026-08-24)** · ~~`miniapp/screenshots/` 4장이 전부 이 변경의 사정권이다.~~
      → #949에서 6장 재촬영, #951에서 서재 컷 갱신, #955까지 반영됐다(파일 타임스탬프 08-24 22:04로 확인).
      ⚠️ 「4장」도 낡은 숫자였다 — 지금 `miniapp/screenshots/`는 **6장**이다(`01-home`·`02-feed`·`03-library`·
      `04-history`·`05-goal`·`10-landscape`).

### 미니앱 디자인 개선 핸드오프 — 「또렷한 연필」 + UX 수정 2건 (완료 ✅ 2026-08-24 — 배포 업로드까지)
> 클로드 디자인 핸드오프(`private-docs/standardHTML/design_handoff_miniapp_ux/`)를 세 덩어리로 나눠 받는다.
> 시안 캔버스는 턴2 = 5개 화면 전체 시안 · 턴3 = UX 감사 6건 · 턴4 = 이번 구현 대상(4a·4b·4c).
> **순서는 ① → ② → ③** — 위험이 낮고 가치가 분명한 UX 결함부터 내보내고, 전면 재테마를 마지막 단독 PR로 둔다.
>
> 🚚 **배포는 ③-D까지 묶어 한 번에** (사용자 결정 2026-08-23, **2026-08-24 재확인**) — ③이 4개 PR(A~D)로 쪼개진 뒤 「A까지만 낼지」를 다시 물었고 **D까지 기다린다**로 정해졌다. 이유는 같다: 심사가 배포마다 붙고, 중간 상태는 「시안이 절반만 반영된 화면」이라 스토어 스크린샷을 두 번 찍어야 한다. — main 머지는 미니앱 배포가 아니다(`ci.yml`은 테스트만,
> `deploy.yml`은 EC2 전용). ①은 머지됐지만 **미배포**이고, 심사가 배포마다 붙으니 ③까지 모아 `deploy.sh` 1회로 끝낸다.
> **→ 결과 (2026-08-24)**: ①#936 · ②#940 · ③-A#944 · B#946 · C#947 · D#948 + 글자색 #937을 묶어 배포했다. **업로드는 3회**다 — ⑴ D 머지 뒤 독립 리뷰가 실제 결함 2건(측정 중 탭바 링이 초록으로 남음 · 서재 「여백에 글쓰기」 연필 프레임 누락)을 잡아 #950으로 고쳐 다시 올렸고, ⑵ 그 뒤 사용자의 「zip 계획 다 했나」 질문에 핸드오프를 재대조하다 **섹션 머리 실선 누락**을 찾아 #955로 보강해 또 올렸다. **라이브 후보 deploymentId = `01a033bc-5190-7380-a62a-f6caf7b56147`**(1차 `01a0329b…` · 2차 `01a032db…`는 폐기). **U-1(합성 볼드 실기기)은 사용자가 폰에서 확인해 2026-08-24 충족** — 원장 8항목이 전부 닫혔다. 남은 것은 콘솔 몫(심사 제출·[출시하기])뿐이다.
> 그때 지켜야 할 두 가지:
> - **`--expect` 마커 = ②가 들여온 문구다(확정, 2026-08-24 소스 실측).** `App.tsx`의 `startToastMessage`가 템플릿 리터럴로 `` `${target} 측정을 시작했어요` `` / `` `${target} 측정으로 바꿨어요` ``를 만드는데, **보간은 앞부분뿐이고 뒷문장은 소스에 따옴표째 실린 정적 조각**이라 번들에 리터럴로 남는다(T-153 판별법 충족). ②(#940)는 한 번도 배포된 적이 없어 「이전 번들과 구별된다」도 충족한다. → `--expect "측정을 시작했어요" --expect "측정으로 바꿨어요"`.
>   🛑 **이 두 마커는 다 썼다 — 다음 배포에 재사용하면 안 된다(2026-08-24).** 1차 업로드에 정확히 그대로 써서 통과했고, **그 순간부터 배포된 번들 안에 있다.** 다시 쓰면 스테일 번들에도 초록이 떠 게이트가 무력화된다(위 ⚠️가 말하는 바로 그 T-150). 2차는 그래서 새 리터럴 `--expect "0 0 0 3px rgba(240,68,82,.2)"`(#950의 빨간 링)로 잡았고, 넘기기 전에 직전 소스에 0건임을 확인했다 — **그 확인이 마커 선정 절차의 일부다.** 규칙: **마커는 재사용 가능한 지식이 아니라 매 배포 새로 고르는 일회용**이다.
>   ⚠️ **③-A의 서체는 마커로 못 쓴다** — `Gaegu`·`Gowun`이 소스에 이미 여러 건 있어 이전 번들에도 있다.
>   ⚠️ **`deploy.sh`의 검사는 「번들에 그 문자열이 있는가」 단순 포함일 뿐, 이전 번들과 비교하지 않는다.** 즉 「새 문구인가」는 스크립트가 아니라 **사람이 지키는 규율**이다 — 옛 문구를 마커로 주면 스테일 번들에도 초록이 뜬다(T-150이 그 사고였다).
> - **`shoot.mjs`의 기본 포트를 5300으로 맞췄다(2026-08-24)** — 옛 기본값 5174는 T-197로 포트를 옮긴 뒤 줄곧 거짓이었고, 이 줄도 그 거짓을 「알고도 우회하는 법」으로 적어 두고 있었다. 이제 `node miniapp/screenshots/shoot.mjs`가 그냥 돈다. 머신이 다르면 `MINIAPP_MOCK_URL`로 넘긴다(`.claude/launch.json`이 포트의 단일 출처이고 gitignore 대상이다).
> - **업로드까지가 Claude 몫, 심사 제출·[출시하기]는 앱인토스 콘솔(사용자) 몫이다** — 배포 보고 때 그 경계를 명시하고 「출시 완료」로 오보고하지 않는다.
> - ~~`--expect` 마커는 ②③에서 새로 생긴 UI 문구로 잡는다~~ (위로 확정 이관) — ①에는 쓸 마커가 없다: 새 UI 문구가 0이고,
>   후보였던 `var(--adaptiveGrey600, #6F6A5E)`·`var(--adaptiveGrey700, #57534A)`·`var(--adaptiveBlue700, #4F6B4C)`가
>   **이전 번들에도 2건씩 이미 있다**(`App.tsx`가 원래 쓰던 값 — 2026-08-23 dist 실측). 「이전 번들과 구별되는
>   문자열」 조건을 못 채우는 마커는 게이트를 무력화만 한다(T-150·T-153). ②③에서도 새 문구가 0이면 마커 없이
>   클린 빌드 + `.ait` 해시 검증에 맡긴다.
> - **스토어 스크린샷 6장을 배포 직전 최종 UI로 다시 찍는다.** 마지막 갱신이 2026-08-21(#899)인데 그 뒤
>   `miniapp/src` 커밋이 계속 쌓였고(①의 2열 통계 · 글자색 전역 변경 · ②의 토스트/시트 · ③의 서체 축 전환) 이미 낡았다. ⚠️ **③이 들어가면 6장 전부가 사정권**이다 — 본문 서체·굵기·계단이 전 화면 공통이라 예외가 없다. 그대로 올리면
>   「실제 앱과 다르다」 반려 사유다. 목 모드 + 390×844@3x로 찍는다(`screenshots/shoot.mjs`).
- [x] **① 목표 진입 개선 (감사 3e → 시안 4a) — 완료 ✅ 2026-08-23**
      홈 히어로의 목표 진입이 `남은시간 : 15:00 ⓘ` 대시 밑줄 **한 줄**뿐이었고, 그 한 줄이 설명과 이동을
      겸했다 — ⓘ는 설명으로 읽히지 이동으로 읽히지 않는다. 역할을 갈랐다: **ⓘ = 설명(툴팁) · 「변경 ›」 = 이동.**
      옛 배치가 그 줄을 `remaining > 0`으로 잠근 탓에 **목표를 다 채운 사람은 홈에서 목표를 바꿀 길이 통째로
      없던 구멍**도 함께 닫았다(통계 행은 이제 목표 유무로만 갈린다).
      ⚠️ 시안이 **광고 손잡이(「밀린 하루 지우개」)의 거처를 말하지 않았다** — 「변경 ›」이 목표 손잡이를
      가져갈 때 함께 쓸려 나갈 뻔한 자리다(수익 경로). ⓘ 툴팁이 그 집을 물려받도록 명시하고 테스트로 박았다.
- [x] **② 측정 시작 토스트 + 바꾸기 시트 (감사 3f → 시안 4b·4c) — 완료 ✅ 2026-08-24**
      다른 탭에서 가운데 ▶를 누르면 `timerStartBookId`(홈 캐러셀 선택)로 시작되는데 **그 화면 어디에도 대상
      책이 안 보였다.** 시작 토스트가 무슨 책인지 말하고, [바꾸기]가 그 자리에서 교체 시트를 연다.
      홈에선 안 띄운다 — 「읽는 중」 카드가 이미 그 책을 말한다.
      ⚠️ **핸드오프가 제시한 「서버 변경 0」안(b)은 실측 결과 불가능했다** — `tag-book`은 이미 책이 지정된
      세션에 **409**를 던지고(`ReadingSessionService.tagBook`), 애초에 `TimerState`에 **진행 중 세션 id가 없어**
      클라이언트가 대상을 지목할 수단 자체가 없다. 그래서 **전용 엔드포인트**를 냈다:
      `POST /api/sessions/active/book`(body `{bookId: number|null}`, 응답 `TimerState`). 세션 좌표가 요청에
      없어 **세션 IDOR이 구조적으로 성립하지 않고**, 종료 세션의 재태깅 409 가드도 손대지 않는다. Flyway 0.
      기각한 (b')안(`TimerState`에 세션 id 노출 + `tag-book` 완화)은 `tagBook`의 의미가 세션 상태로 갈려
      엔티티·서비스·컨트롤러·기존 테스트 **4층의 계약을 조건부로** 만든다.
      ⚠️ **목 모드가 아니면 못 잡았을 결함을 하나 만들었다가 고쳤다** — 토스트 타이머 정리를
      `hasActiveSession` effect의 cleanup에 얹었더니, 측정을 시작하는 순간이 곧 그 값이 뒤집히는 순간이라
      **방금 건 5초 타이머가 그 자리에서 지워졌다**(시작 토스트가 영영 안 사라짐). 교체 토스트는 그 사이
      값이 안 변해 멀쩡했던 탓에 증상이 절반만 보였다. 언마운트 전용 effect로 분리하고, 「그 코드가 다시
      섞이는지」를 소스 단언으로 박았다(하니스는 effect를 안 돌려 이 경합을 재현할 수 없다).
- [x] **③ 「또렷한 연필」 타이포·위계 시스템 (시안 2a~2e) — 완료 ✅ 2026-08-24 (4 PR: A 토대 #944 → B 홈·피드 #946 → C 서재·기록·목표 #947 → D 탭바 #948)**
      - [x] **A. 토대 — 서체 축 전환 (2026-08-24)**: `html body`를 고운돋움으로 뒤집고 개구 보정 둘을 걷었다
            (`112.5%` 삭제 — 상속 노드 0개 실측인 죽은 스위치 / `700` → `400` 명시). `--tds-t-*` 9쌍을 시안 눈금으로
            되돌리고(옛 값은 개구 보정이라 계단 전체가 한 칸 올라가 있었다) `SCALE`도 재정의했다.
            ⚠️ **숨은 절반이 있었다** — 레포 전체에서 Gaegu를 명시한 tsx가 **0건**이라, 표지 이니셜·placeholder·여백
            글이 전부 body 상속으로 손글씨였다. 기본값만 뒤집으면 장식이 **에러 없이 통째로 사라진다**. 그래서
            `HANDWRITING` 상수를 만들어 같은 PR 안에서 명시 opt-in으로 뒤집었다(CoverInitial·Avatar·캐러셀
            placeholder·피드 인용·MarginCard 인용/본문).
            채움 주 버튼(`FilledButton`)은 **마커 커스텀 프로퍼티를 선택자 키로** 쓴다 — 기존 hex-키 방식과 달리
            이름만 보므로 직렬화 공백 함정이 없고 **danger red를 css에 적을 일 자체가 안 생긴다**.
            ⚠️ 그 규칙은 primary 재색칠보다 **반드시 뒤에** 온다(둘 다 매칭 + 명시도 동급 = 나중이 이긴다).
      - [x] **B. 홈 + 피드 (2a·2b) — 완료 ✅ 2026-08-24**: 인사말 세리프 **26**/700(시안 25 → 계단 t3로 올림) · 히어로 오버라인 12px+자간 3+세이지 ·
            배지 11 + 채움 세이지 800 + 여백 인용 보더 세이지 200 ·
            2열 라벨 11 · 피드 문장의 『제목』만 세리프 700 · 「여백 N」 배지 숫자 세리프.
            ⚠️ 문장은 `eventLine`이 **한 덩어리 문자열**로 만든다(조사 처리가 그 안에 있다) — 조각 배열을 돌려주게
            바꾸면 그 로직과 테스트가 통째로 흔들리므로, 렌더 직전에 쪼개는 순수 함수 `quotedParts`를 뒀다.
            정규식은 **비탐욕**이다: 제목 안에 『』이 든 책(『『책』을 읽는 법』)에서 탐욕적으로 끊으면 문장 절반이
            세리프가 된다 — 제목이 어중간하게 잘리는 쪽이 덜 눈에 띄는 고장이다.
            ⚠️ **시안에서 일부러 안 따라간 두 자리(ABANDON)**: ① 연한 채움 `rgba(199,184,155,.35)` — 토큰을 생 rgba로
            바꾸면 다크모드 적응을 잃고 「내 책」 칩과의 짝이 끊긴다. ② 외곽선 연필선 — 300×300 rx7 SVG를 20px 배지로
            늘이면 결이 뭉개진다. 둘 다 코드 주석에 이유째 박아 뒀다.
            **원장 진척**: U-4(히어로 최장값 넘침 0)·U-6(rem/em 0건)·U-7(첫 진입 덮개 0) **충족** — U-1은 배포 후
            실기기, U-2·U-3은 C(목표 화면), U-5는 D(5탭 전수) 몫이라 대기.
      - [x] **C. 서재 + 기록 + 목표 (2c·2d·2e) — 완료 ✅ 2026-08-24**: 세그먼트 굵기·그림자·권수 세리프 ·
            칩 값 세리프 · 여백 헤더 카운트 세리프 16 · 「여백에 글쓰기」 채움 · 기록 세리프 5자리(20/20/14/15/13) ·
            가이드라인 16 + 세이지 200 · 휠 밴드 + 선택 행 세리프 23 · 저장 `FilledButton`.
            ⚠️ **U-3이 1차안보다 좋게 풀렸다** — TDS 휠 항목이 `role="radio"` + `aria-checked`를 달아(실측)
            emotion 해시가 아닌 **접근성 계약**으로 선택 행만 집힌다. 「전 행 세리프」 후퇴가 불필요해졌다.
            **원장 진척**: U-2(저장 버튼 `rgb(79,107,76)`/`rgb(247,242,232)`)·U-3 **충족**. 남은 것은 U-1(실기기)·U-5(D).
            ⚠️ ABANDON 2건: 「여백에 글쓰기」 radius는 14 유지(옆 「관리」가 연필 프레임+14라 짝) ·
            휠의 인접 행 17 계단(1px에 `:has()`가 필요 — 설계의 「1px는 유지비만」 대역).
      - [x] **D. 탭바 (시안 4c) — 완료 ✅ 2026-08-24**: 고른 칸 아이콘 알약(38×26 r13 반투명 세이지) ·
            선택 색 세이지 700 · 라벨 11 + 고른 칸만 700 · 아이콘 22 + 획 2/1.8 · 가운데 원 46 + 링(고정값 단층).
            ⚠️ 안 고른 칸도 **같은 38×26 span**을 쓴다(배경만 비움) — 알약이 있다가 없으면 레이아웃이 튄다.
            ⚠️ 링에 애니메이션을 걸지 않는다(T-176 계열 — 데스크톱·목 모드가 원리상 못 잡는 클래스).
            **원장 U-5 충족**: 4탭 + 관리 시트에서 가로 넘침 0 · 글자 잘림 0.
      → **③ A~D 전부 완료. 남은 것은 배포 1회**(위 「배포는 ③-D까지 묶어 한 번에」 절의 조건을 그때 지킨다).
        원장 잔여였던 **U-1(합성 볼드 실기기)**은 2026-08-24 사용자 폰 확인으로 충족 — **원장 전 항목 종료**.
      - [x] **누락분 보강 — 섹션 머리 아래 실선 ✅ 2026-08-24**
            사용자의 「zip 계획 다 했나」 질문에 핸드오프 README를 값 단위로 재대조하다 나온 것. 핸드오프
            「타이포 스케일」의 섹션 제목 줄에 **크기(16.5)와 구분선이 함께** 적혀 있었는데 설계 md엔 크기만
            건너왔다 — 즉 **설계에 옮겨 적는 단계에서 증발**해 구현도 ABANDON 기록도 없었다.
            ⚠️ **원장(§7)이 못 잡는 종류다** — 원장은 「해봐야 아는 것」을 담지 「명세에서 빠뜨린 것」은 안 담는다.
            **시안(턴2 `#t2`)이 선을 그은 자리는 넷**이다 — 홈 캐러셀 머리 · 피드 탭 머리 · 서재 여백 헤더 ·
            기록 월 헤더. 홈 「읽는 중」은 **시안에 없고** 판단으로 더했다(`Home`이 삼항으로 캐러셀↔`ReadingNowCard`를
            같은 슬롯에서 갈아끼우므로 한쪽만 그으면 측정 시작 순간 카드가 변한다). 「읽은 날짜」는 시안도 민무늬라 뒀다.
            ⚠️ **처음엔 「시안이 다섯을 그었다」고 적었다가 독립 리뷰에 잡혔다** — 시안 파일의 `border-bottom` 히트가
            5건인데 그중 하나가 **README가 「방향 탐색 기록이라 참고만」이라 못 박은 `#t1` 캔버스의 재탕**이었다.
            히트 수를 자리 수로 읽은 것이고, T-211 ⑵(시안에 대한 단정을 원본을 안 열고 씀)가 **T-213을 쓰는 중에
            재발**한 셈이다. 선은 `SectionTitle`이 아니라 **줄**에
            건다 — 다섯 중 셋은 제목 옆에 카운트·손잡이·합계가 서서, 제목에 걸면 선이 줄 한가운데서 끊긴다.
            색은 시안의 생 `rgba(44,42,36,.12)` 대신 `--adaptiveGrey200` 토큰 — 그 생값은 잉크를 12%로 깐 것이라
            **측정 중 독서등(밤)에서 카드지와 함께 어두워져 사라진다**(밤 실측으로 확인: 카드 `#322B22` 위에 선
            `#463E32`로 살아 있다). 낮 계산값(≈`#E3E1DC`)이 토큰과 한 톤 안이라 낮 그림은 그대로다.
      - [x] **3차 배포 완료 ✅ 2026-08-24** — `deploymentId: 01a033bc-5190-7380-a62a-f6caf7b56147` **(라이브 후보)**
            #955(섹션 머리 실선 누락 보강 + 독립 리뷰 반영)를 담았다. ⚠️ **마커 없이 올렸다** — 이번 릴리스는
            순수 시각 변경이라 **새 UI 문구가 0**이고, 유일한 후보였던 `1px solid var(--adaptiveGrey200, #E4DDD0)`은
            직전 번들의 `History.tsx` `borderTop`에 **이미 있어** T-150의 「이전 번들과 구별된다」를 못 채운다.
            레포 규칙대로 억지 마커 대신 클린 빌드 + `.ait` 바이트 동일 검증에 맡겼다.
      - [x] ~~재배포 2차 ✅ 2026-08-24~~ — `deploymentId: 01a032db-8e68-7b26-a9be-72b532192878` **(폐기 — 섹션 머리 실선 누락)**
            독립 리뷰가 잡은 결함 2건(#950 — 측정 중 원의 **초록 후광**, 「여백에 글쓰기」의 **빠진 연필 프레임**)을
            고쳐 다시 올렸다. 마커는 **이번 릴리스에서 새로 들어간 빨간 링 문자열**
            (`--expect "0 0 0 3px rgba(240,68,82,.2)"`)로 잡았다 — 직전 번들의 `App.tsx`에 `rgba(240,68,82`가
            **0건**임을 확인해 T-150의 「이전 번들과 구별된다」를 충족시켰다. 그 마커가 통과했다는 것은
            **수정이 실제로 산출물에 들어갔다**는 증거다. 스크린샷은 `03-library` 한 장만 갱신됐다(#951).
      - [x] ~~배포(업로드) 1차 ✅ 2026-08-24~~ — `deploymentId: 01a0329b-861a-746d-8c00-3f807ad6157c` **(폐기 — 위 결함 2건 포함)**
            (`--expect "측정을 시작했어요" --expect "측정으로 바꿨어요"` — ②가 들여온 문구이고 ②는 한 번도
            배포된 적이 없어 「이전 번들과 구별된다」를 충족. dist 검증 + `.ait` 바이트 동일 검증 통과.)
            ⚠️ **심사 제출·[출시하기]는 앱인토스 콘솔(사용자) 몫이다** — Claude 접근이 차단돼 있다.
            아직 **미제출·미출시** 상태다(업로드까지만 됨).
            ✅ **U-1(합성 볼드) 충족 — 원장이 닫혔다 (2026-08-24)**. 사용자가 이 배포분을 **폰에서 직접 열어**
            확인했고 「잘된 것 같다」로 회신했다. 목·데스크톱은 합성 볼드 렌더러가 달라 원리상 못 잡는
            항목이라(T-176 계열) 사용자 폰이 유일 경로였다. ⚠️ 근거의 성격을 분명히 해 둔다 — 이건
            **수치가 아니라 사용자의 육안 판정**이다. EXPECT가 애초에 「읽기 어렵지 않다」는 사람 판정이었으므로
            이것이 이 항목의 정본이고, 나중에 굵기가 문제 되면 새 관측이지 이 기록의 번복이 아니다.
      ⚠️ **홈 채움 「측정 시작」은 신설하지 않는다**(사용자 결정 2026-08-24) — 그 액션은 이미 탭바 가운데 원으로
      의도적으로 승격됐고(`App.tsx` MainTabs 「홈에서 승격」), 같은 일을 하는 문이 둘이면 어느 쪽이 진짜인지
      사용자가 고민한다. 시안 2a가 그 승격 이전을 그린 것으로 본다.
      기능 글자를 개구(손글씨)에서 고운돋움으로 옮기고, 값은 고운바탕 세리프로 통일. 채움 버튼을 화면당 1개로
      제한해 강조 축을 만든다. 탭바 선택 알약·가운데 액션 원(44→46px)도 이 몫.
      ⚠️ **위 「미니앱 타이포 스케일」이 실측으로 잡아 둔 값이 전부 개구 기준이다** — `html body`의 `112.5%` ·
      전역 `700` · `--tds-t-*` 9쌍이 그렇고, `typography.test.tsx`가 그 전제를 못 박고 있다(「개구엔 500·600·900이
      없다」 등). 서체를 옮기면 **그 테스트 파일을 함께 다시 써야 하고 5개 화면이 전부 시각 회귀 대상**이다.
      순수 시각 변경이라 **실기기가 유일한 게이트**다(T-176 계열 — 목 모드·데스크톱 크롬은 원리상 못 잡는다).


### 홈 소식 피드 재부상 — 완독↔읽는중 토글로 남의 피드 맨 위를 반복 점유 (완료 ✅ 2026-08-18)
> 완독 축하 푸시 반복 발송(같은 날 수정 — 책당 1회 마커)과 **같은 뿌리**의 두 번째 표면이다: "전이했는가"만 보고
> "이미 알렸는가"를 안 보는 구조. 푸시를 먼저 고치고, 이쪽은 제품 결정을 받은 뒤 별도 PR로 처리했다.
- **증상**: 완독↔읽는중을 토글하면 `book.finished_at`이 매번 오늘로 재스탬프되고, 읽는중 재진입 시 `started_reading_at`도
  재스탬프됐다. 홈 소식 피드는 이벤트를 따로 적재하지 않고 **이 두 시각에서 파생**하므로(`BookRepository#feedFinished`·
  `#feedStarted`, 창 14일·상한 30건), **3주 전 완독한 책이 팔로워 피드 맨 위에 "방금 완독한 것처럼" 반복 재부상**했다.
  시각이 거짓말을 하는 게 본질. 푸시(자기 자신)와 달리 **타인에게 노출**되는 표면이었다.
- **제품 결정 (사용자 확정)**: 「진짜 재독해서 다시 완독한 책도 팔로워 피드에 다시 뜨지 않는다 — **피드 이벤트는 책당 1회**」.
  같은 날 머지한 축하 푸시(책당 1회)와 같은 사고방식으로 맞췄다.
- **한 일**:
  - [x] **읽기시작 절반은 새 컬럼 없이** — 스탬프 조건에 `startedReadingAt == null`을 AND로 추가(소비자가 피드뿐이라 가능).
        ⚠️ **전이 조건은 유지**했다 — `null` 단독 조건으로 하면 V64 이전 레거시 READING 책(시각 null)의 no-op 재저장에서
        오늘 날짜가 찍혀 **가짜 "방금 시작" 이벤트**가 생긴다.
  - [x] **완독 절반은 새 컬럼 `first_finished_at`**(V73, 첫 완독에 스탬프·절대 안 지움) + 쿼리를
        `status = FINISHED and firstFinishedAt > :cutoff` / `order by firstFinishedAt desc`로. `finished_at`은 무변경이라
        책방 「완독 최신순」 정렬에 영향이 없다.
  - [x] V73 백필 `first_finished_at = finished_at` — 배포 전후 피드가 동일하게 보인다(안 하면 창 안 이벤트가 즉시 전멸).
- ⚠️ **기각한 안 2개 (다음 사람이 되돌아오지 않게)**:
  **ⓐ `finished_at` 재스탬프를 막는다** — 못 쓴다. 피드 쿼리에 `status` 조건이 없어서 **완독 이탈 시 `finished_at`이 null이 되는
  것이 피드에서 사라지는 유일한 제거 장치**였다. 보존하면 완독을 취소해도 "완독했어요"가 피드에 계속 남는 새 버그가 된다.
  (그래서 이번에 제거 장치를 `status = FINISHED`로 **문면에 드러냈다**.)
  **ⓑ 방금 만든 `finish_celebrated_at` 재사용** — 등록-시-완독 경로(`stampIfFinished`)가 그 마커를 안 찍으므로(축하 폭주 방지)
  아카이빙 책이 피드에서 조용히 사라지는 **미요청 제품 변경**이 된다. 게다가 축하 정책과 의미가 결합돼 한쪽을 바꾸면 다른 쪽이
  깨진다. 아끼는 건 마이그레이션 파일 한 장뿐이라 남는 장사가 아니었다.

### Flyway 마이그레이션 도입 (완료 ✅ 2026-06-02)
- **왜**: `ddl-auto=update`는 기존 컬럼 제약(NOT NULL 등)을 못 바꿔 스키마 드리프트 발생 — 실제로 소셜 계정
  `password_hash` nullable 변경이 prod에 미반영돼 500 사고(T-015, N-023).
- **한 일**:
  - [x] `spring-boot-flyway`(autoconfig 모듈) + `flyway-mysql` 의존 추가 — Boot 4는 Flyway autoconfig가
        별도 모듈(`flyway-core`만으론 빈 미생성, T-016/N-024)
  - [x] `V1__init_schema.sql` baseline 작성 — enum→varchar로 MySQL·H2 공통 실행, 시각 datetime(6)
  - [x] 기존 운영 DB **baseline** (`baseline-on-migrate=true`, `baseline-version=1` → 기존 DB는 V1 적용
        표시만 하고 실행 X, 신규 환경만 V1 실행)
  - [x] `ddl-auto`를 prod·test 모두 `none`으로 전환 (validate 대신 none — 크로스-다이얼렉트 validate
        취약성 + 운영 기동 실패 위험 회피. 드리프트는 `FlywayMigrationTest`가 격리 H2에서 validate로 검증)
  - [x] **`PasswordHashNullableSchemaFix` 제거** (V1이 nullable 보장)
  - [x] (부수) @DataJpaTest 슬라이스 3종이 `@Import(JpaConfig.class)` 누락으로 순서 의존이던 것 수정(T-017)

### 회원 인증/계정 보안 하드닝 (우선순위: 높음)
> 2026-06-02 보안 점검 결과. 기본기(BCrypt·CSRF·세션고정보호·재인증·IDOR 없음·XSS 없음)는 양호.
> 아래는 보강 항목. **상세 위협 분석은 공개 노출 부담이 있어 private 노트에 별도 기록**(이 repo 공개).
- [x] **OAuth 이메일 검증 강제** (완료 2026-06-02) — `provision`에 `email_verified` 게이트(아니면 거부, null=미검증 처리).
      자동 계정 연결 탈취 방어. 개념 **N-026**.
- [x] **로그인 무차별 대입 방어** (완료 2026-06-02) — IP별 연속 실패 5회→15분 잠금(`LoginAttemptService` + 인증 이벤트 집계
      + `LoginAttemptFilter` 단락). 키를 이메일 아닌 IP로(피해자 잠금 DoS 회피). 개념 **N-026**.
- [x] **세션 쿠키 `SameSite=Lax` 명시** (완료 2026-06-02) — `WebConfig#cookieSerializer` 명시 빈으로 SameSite=Lax
      + HttpOnly + (prod)Secure. 세션 외부화 후 세션 쿠키는 `DefaultCookieSerializer`가 써서
      `server.servlet.session.cookie.*` 프로퍼티가 무동작이라 명시 빈 필요(T-021, N-031). **파생 수정**: 이
      함정 탓에 prod의 Secure/HttpOnly도 SESSION 쿠키엔 안 먹던 잠재 갭(#73 이후)을 같이 잡음.
- [x] **소셜 계정 탈퇴 재확인 + 가입 계정 열거 완화** (완료 ✅ 2026-06-05) —
      ① **가입 이메일 열거 완화**: email은 login_id 도입 후 **비공개 속성**인데 "이미 가입된 이메일입니다"가
      이를 확인해 줘 열거가 됐다. `UserRegistrationService.register` 검사 순서를 **login_id(형식→유니크) 먼저,
      email 마지막**으로 바꾸고, `SignupController`가 `EmailAlreadyExistsException`·`DataIntegrityViolationException`을
      **가입 성공과 동일한 `redirect:/login?registered`로 흡수**(계정 미생성, 응답만 동일 → 존재 여부 미노출).
      login_id는 **공개 @핸들**이라 "사용 중" 노출이 무해·UX상 필요해 그대로 필드 에러. 트레이드오프: 이메일 발송
      인프라가 없어 "조용히 수락+메일 통지"의 통지는 불가 → 잊고 재가입한 사용자는 로그인 단계에서 알게 됨(열거 저항 표준 비용).
      ② **소셜 탈퇴 재확인**: OAuth 계정은 비번이 없어 탈퇴 재인증이 없었음(`confirm()` JS뿐, 우회 가능). 본인
      **@핸들(login_id) 타이핑**을 서버사이드 게이트로 요구(GitHub "저장소 이름 입력" 패턴). `deleteSocialAccount(email, confirmHandle)`
      불일치 시 `AccountDeletionConfirmationException`(공백·선행 @·대소문자 정규화 후 비교), LOCAL은 비번 재확인 유지.
      TDD(서비스 일치/관대매칭/불일치/LOCAL거부 · 컨트롤러 끝단 일치삭제/불일치미삭제 · 가입 이메일중복 silent-success Red→Green) + 전체 그린.
- [x] **forwarded 헤더 신뢰 경계 확립** (완료 ✅ 2026-08-15) — 위 "로그인 무차별 대입 방어"가 **잠금 키로 쓰는 IP를
      클라이언트가 지정할 수 있었다.** 앱은 `ForwardedHeaderFilter`(WebConfig)로 `getRemoteAddr()`를 forwarded 헤더에서
      다시 읽는데, 앞단 Caddy가 `Forwarded`(RFC 7239)는 세팅도 삭제도 안 해 들어온 값이 그대로 통과했고 **Spring은 이
      헤더를 `X-Forwarded-*`보다 우선**한다. `deploy/Caddyfile`에 `header_up -Forwarded` 한 줄로 입구에서 버린다.
      곁들여 **배포 반영 경로가 아예 없었다** — Caddyfile 변경이 배포 성공 뒤에도 무반영으로 남는 구조였다. **T-167**.
      원인이 **두 겹**이라 1차 수정(reload 추가)만으로는 안 뚫렸다: ① Caddy는 설정을 스스로 다시 안 읽고
      ② `compose.prod.yaml`이 **파일 하나**를 bind mount 해서, 배포의 `aws s3 sync`가 rename으로 교체하면
      컨테이너가 기동 시점 inode에 고정된 **옛 내용을 영원히** 본다(그 상태의 `caddy reload`는 낡은 설정을
      다시 읽고 exit 0으로 성공한다 — 운영 실측으로 잡았다). 디렉터리 마운트(`./caddy:/etc/caddy:ro`)로 전환하고,
      배포에 validate → `up -d caddy` → reload 3단계를 넣었다.
      회귀 가드: `deploy/tests/test-caddy-forwarded.sh`(실 Caddy 컨테이너로 스푸핑 요청, CI 매 PR).
      ⚠️ `X-Forwarded-For`는 Caddy가 이미 리셋하고 있었다(실측) — 그쪽은 이 테스트가 회귀만 지킨다.
- [x] **비밀번호 변경·재설정 시 세션 강제 만료** (완료 ✅ 2026-08-15) — 비밀번호 해시를 바꿔도 `SPRING_SESSION`에
      이미 만들어진 세션 행은 그대로 살아 있어, **비번이 샜다고 판단해 바꾼 사용자가 정작 침입자를 못 쫓아냈다.**
      `SessionInvalidator`가 `PRINCIPAL_NAME` 인덱스로 그 사용자 세션을 찾아 지운다 — 재설정(비로그인 흐름)은
      전부, 변경은 **현재 창만 남기고** 나머지(방금 현재 비번을 증명한 본인이고, 성공 플래시도 그 세션에 실린다).
      principal name은 폼·OIDC 다 `login_id`지만 온보딩 전 첫 세션만 email이라 **둘 다 조회**해 구멍을 막는다.
- [x] **구매 리다이렉트 오픈 리다이렉트 차단** (완료 ✅ 2026-08-15) — `book.purchase_link`는 `POST /api/books`가
      검증 없이 저장하는 사용자 입력인데 `"redirect:" + link`로 그대로 나갔다. 공개 책 경로(`/u/{loginId}/books/{id}/buy`)는
      **소유자가 아닌 사람이 클릭**하므로, 공격자가 자기 책을 공개해 두면 우리 도메인을 거쳐 임의 사이트에 착지시킬 수 있었다.
      `AladinLink.isTrusted`(URL 파싱 후 스킴+host 완전일치)를 두 구매 클릭의 공통 꼬리 한 곳에 건다. **입력이 아니라
      출구에서** 막아 이미 저장된 행까지 마이그레이션 없이 중화한다. 문자열 접두·포함 비교는
      `www.aladin.co.kr.evil.example`·쿼리스트링 미끼에 뚫려 쓰지 않는다.
- [x] **운영 테스트 계정 폐기** (완료 ✅ 2026-08-15) — 이메일 발송 검증용으로 만든 운영 계정(`testid`)의 비밀번호가
      **공개 레포(CLAUDE.md)에 평문으로** 적혀 있었다. 이메일 작업이 끝나 계정을 지우고 기술도 걷어냈다.
      삭제는 손으로 쓴 DELETE가 아니라 **앱 자신의 탈퇴 경로**(`POST /settings/delete`)로 했다 — `purge()`가
      FK 순서를 알고 `@Transactional`로 원자적이다. 잔여 세션 616건은 별도 정리(아래 `purge()` 결함 참조).
      로컬 시드 계정(`LocalTestAccountSeeder`, `testid@local.test`)은 그대로 — 하드코딩된 로컬 픽스처라 무해하다.
- [x] **`AccountService.purge()`가 안 지우는 FK 4개 — 탈퇴가 실패하던 것** (완료 ✅ 2026-08-15) — users를 FK 참조하는
      테이블 중 `author_affection`·`garden_placement`·`garden_decoration_placement`·`push_subscriptions`가
      `purge()`에 없고 **전 FK가 `NO ACTION`**(cascade 없음)이라, 운영 27명 중 **2명이 탈퇴 불가**였다
      (`author_affection` 7행/2명, `garden_placement` 2행/2명 — 실측).
      **처리를 둘로 갈랐다**: `author_affection`은 살아있는 기능이라 `purge()`에 삭제를 추가했고,
      나머지 셋은 **자바 코드가 한 번도 참조하지 않는 죽은 테이블**이라 `V68`로 DROP했다(죽은 것을 위해
      코드를 새로 쓰지 않는다 — 은퇴 경위는 마이그레이션 주석).
      곁들여 `purge()`가 **세션을 안 지우던 것**도 고쳤다 — 계정이 사라져도 `SPRING_SESSION` 행이 남아
      유령 세션이 됐다(testid 삭제 시 616건 잔존 실측·수동 정리). `SessionInvalidator` 호출을 얹었다.
      **회귀 가드는 `FlywayMigrationTest`에 뒀다** — "users를 FK 참조하는 테이블 집합 == `purge()`가 지우는 집합"을
      **양방향** 단언한다. 메인 스위트는 Hibernate가 엔티티 매핑에서 스키마를 만들어 **JPA 미매핑 테이블이 아예
      존재하지 않으므로**, 이번 결함(Flyway 전용 테이블)을 잡을 수 있는 곳은 실 마이그레이션을 도는 그 테스트뿐이다.
      ✅ 남았던 은퇴 정원 테이블(`decoration` 등)은 아래 좀비 정리에서 `V70`으로 걷어냈다.
- [x] **좀비 코드 정리 8건** (완료 ✅ 2026-08-15) — 전체 코드리뷰의 좀비 축 후속. **지우기 전에 10건을 현재 코드로
      재검증했고, 그중 1건은 오판이라 제외했다** — `POST /api/sessions/start|stop`은 `dashboard.js`가 실제로 호출하는
      측정 시작/종료의 유일한 경로다(`/api` 없는 SSR 변종은 존재하지 않는다).
      지운 것: ① `GardenApiResponse.world` + top-level `characters`(프론트 3곳 전부 `catalog.*`만 읽고,
      `characters`는 `catalog.ownedCharacters`와 **같은 리스트**였다) → `GardenWorld` 클래스도 사망 ②
      은퇴 정원 테이블 8개 `V70` DROP(`plant` 4축·`building`·`publisher_building`·`decoration`·`user_discovered_plant`)
      ③ Lombok 의존 4줄(`src/` 전체 import 0) ④ `BookSearchPage.prevPage()/nextPage()`(호출처가 자기 테스트뿐)
      ⑤ `ReadingDebtService.debtEpoch()` ⑥ `FollowScopePopularity.hasAny()` ⑦ `AuthorCharacter.getDisplayOrder()`.
      app.css는 아래 항목에서 별도로 처리했다.
- [x] **은퇴 정원 CSS 251줄 제거** (완료 ✅ 2026-08-15) — 위 좀비 정리의 남은 한 축.
      ⚠️ **직전 기록의 「리뷰가 틀렸다」는 판정은 내가 틀린 것이었다** — `GardenDex`·`DexCell`이 쓰는
      `.garden-axis`·`.garden-grid`·`.plant-emoji`·`.garden-progress`·`.garden-next`는 전부 **1061 이전**에
      정의돼 있어 삭제 범위 밖이다. "클래스가 쓰인다"와 "그 규칙이 지울 범위 안에 있다"를 혼동했다.
      리뷰의 「1061~1295 삭제, 1183·1185만 유지」가 정확했다.
      지운 것: 꾸미기·편집 UI 전체(`.my-garden-*`·`.garden-edit-btn`·`.garden-stage`·`.garden-view*`·
      `.garden-phaser`·`.canvas-plant`·`.canvas-decor`·`.garden-palette`·`.palette-plant`·`.garden-toolbar`)
      + `@keyframes garden-sway/rise` + `.garden-wide` 미디어쿼리(어디에도 안 쓰임) + `.garden-back`
      + 고아가 된 `.village-edit-topbar` 블록 + 죽은 변수 `--garden-grass`·`--garden-fence`
      (`--garden-sky`는 다른 곳에서 살아 있어 **유지** — 하마터면 같이 지울 뻔했다).
      게이트: 실 브라우저(로컬 bootRun + 시드 계정)로 도감·서재·홈 확인, 콘솔 에러 0.
- [x] **API 가드 2건 — 토스 인증 IP 상한 + 팔로우·차단의 운영자 숨김** (완료 ✅ 2026-08-15) — 전체 코드리뷰 Low 축의 1·2번.
      ① **`/api/toss/*` 레이트리밋이 검증 뒤에 돌던 것**: 기존 제한(`TOSS_AUTH`·`TOSS_LINK`)은 키가 토스 `userKey`인데
      그 키는 **인가코드 검증이 성공해야** 나온다 — 코드가 쓰레기면 검증이 401로 끝나 그 제한기에 **닿지도 못한다**.
      이 경로는 `permitAll`이라, 미인증 요청 하나하나가 토스로 나가는 mTLS 아웃바운드를 **무제한** 유발했다(우리 API
      쿼터·요청 스레드 소모). 신원을 모르는 단계에서 셀 수 있는 유일한 키인 **클라이언트 IP**로 검증 **앞**에
      `TOSS_VERIFY(60/분)`를 한 겹 더 뒀다. 한도를 넉넉히 잡은 건 국내 이동통신 **CGNAT** 때문 — 촘촘하면 공격자가
      아니라 같은 NAT 뒤 정상 사용자가 로그인을 못 한다. 목적은 정밀 차단이 아니라 **폭주의 상한**이고 1분이면 풀린다.
      ⚠️ "실패 횟수로 잠그는"(`LoginAttemptService`) 방식은 **쓰지 않았다** — `TossLoginException`이 인가코드 거부뿐
      아니라 **네트워크 실패·mTLS 번들 누락까지 전부** 던져서, 토스 장애 때 정상 사용자가 무더기로 잠긴다.
      ② **팔로우·차단이 운영자 숨김 불변식을 건너뛰던 것**: 프로필·검색·스토리·추천은 전부 `role <> ADMIN`으로 거르는데
      (`ProfileService.resolveVisibleTarget`) 이 두 API만 `findByLoginId`를 날것으로 써서, 운영자 핸들을 아는 사람이
      200/404 차이로 **존재를 확인**(그 가드가 막으려던 바로 그 누설)하고 팔로우 엣지까지 만들 수 있었다.
      ⚠️ **만드는 쪽(follow·block)에만 걸고 푸는 쪽(unfollow·unblock)엔 걸지 않는다** — 걸면 상대가 나중에 운영자로
      승격됐을 때 이미 만들어진 팔로우·차단을 **영영 못 푼다**. 이 비대칭을 회귀 테스트 2건이 못 박는다(그 둘은
      수정 전에도 통과하므로, 「푸는 쪽에도 필터를 건」 돌연변이로 죽는 걸 봐야 실제 가드임이 증명된다 — 실사살 확인).
- [ ] (후속) 무차별 대입 방어 보강 — 지수 백오프, 다중 인스턴스 대비 공유 저장소(현재 인메모리=인스턴스별), 앞단 WAF 레이트리밋
      (③ 갈래 — 트래픽/세션 쓰기 신호 오면. Redis는 예산 충돌, WAF는 인프라라 코드 가치 낮아 보류.)
- [x] **컨테이너 비root 실행** (완료 ✅ 2026-08-15) — 리뷰 Low 3번. 앱 컨테이너가 uid 0으로 돌아(실측 확인),
      컨테이너 안에서 RCE가 나면 곧바로 root였다. `Dockerfile`에 uid 10001 유저를 만들어 `USER`로 못 박았다.
      jar는 root 소유 읽기전용으로 남긴다(앱이 자기 코드를 못 덮어쓰게 — 그래서 `/app`을 chown하지 않는다).
      ⚠️ **이 변경의 진짜 위험은 토스 mTLS였다**: 배포는 SSM Run Command라 **root**로 돌고(실측 uid=0)
      `render-env.sh`가 PEM을 `chmod 600`으로 떨어뜨려 `root:root 600`이 된다 — 비root 컨테이너는 못 읽는데
      **Spring SSL 번들이 지연 생성**이라 앱은 멀쩡히 뜨고 헬스도 초록이다. 즉 토스 로그인만 죽어 미니앱이
      시작조차 못 하는 **무성 장애**가 된다. 대응을 두 겹으로 뒀다:
      ① `render-env.sh`가 PEM 소유자를 `APP_UID`(=10001)로 넘긴다(권한은 600 유지).
      ② **더 중요한 건 ②다** — `deploy-on-ec2.sh`가 전환 **전에** 새 컨테이너 안에서 PEM 2개를 **실제로 읽어 본다**.
      uid 산수를 믿지 않고 결과를 확인하므로, uid 드리프트뿐 아니라 디렉터리 모드·마운트 누락처럼 원인이 달라도
      똑같이 잡힌다. 실패하면 헬스 실패와 같은 롤백 경로로 합류해 **옛 컨테이너를 그대로 살려둔다**.
      회귀 가드: `test-render-env.sh`가 **Dockerfile의 `USER` uid와 `render-env.sh`의 `APP_UID`가 같은지**
      교차 단언한다(두 값은 다른 파일에 있고 어긋나도 빌드·기동·헬스가 전부 초록이라, 드리프트는 사용자 제보로만
      드러난다 — 그 결합이 이 함정의 유일한 재발 경로다). `test-deploy-bluegreen.sh`는 「헬스는 OK인데 인증서를
      못 읽는」 경우를 케이스로 둔다(게이트가 헬스와 **독립**임을 못 박고, 반대 케이스로 공허함도 막는다).
- [x] **CSP 도입 — nonce + strict-dynamic** (완료 ✅ 2026-08-15) — 리뷰 Low 4번, 전체 리뷰의 마지막 항목.
      `SecurityConfig`에 `headers()` 설정이 아예 없어 CSP가 없었다(Spring 기본은 CSP를 안 붙인다).
      **인벤토리가 좋아서 strict가 가능했다**: 외부 스크립트는 AdSense·GA 2개뿐이고 나머지는 전부 자체 호스팅,
      Alpine/htmx/Phaser가 전부 은퇴해 `eval` 0건(→ `unsafe-eval` 불필요), Vue 번들이 런타임에 `<style>`을
      주입하지 않으며(SFC `<style>` 0건·CSS import 0건), 인라인 핸들러 5개가 전부 같은
      `onsubmit="return confirm()"` 패턴이라 위임 리스너 하나로 없앴다(`js/confirm-submit.js` + `data-confirm`).
      정적 문자열 정책으로는 인라인을 허용하려면 `'unsafe-inline'`이 필요한데 그러면 XSS 방어가 사라지므로,
      **요청마다 nonce를 뽑는 서블릿 필터**(`CspNonceFilter`)로 갔다 — Spring Security의 `contentSecurityPolicy()`는
      정적 문자열만 받아 이걸 못 한다. 템플릿은 `${cspNonce}`로 읽는다(Thymeleaf가 요청 속성을 변수로 노출하는
      `${_csrf}`와 같은 경로).
      ⚠️ **`'strict-dynamic'`의 대가**: 호스트 허용목록(`'self'` 포함)이 **무시**되므로 외부든 우리 번들이든
      **모든 `<script>`에 nonce가 있어야** 실행된다(총 18개 + 신규 5개). 빠뜨리면 에러 없이 그 스크립트만
      조용히 안 돈다 — 화면은 멀쩡한데 기능 하나가 죽는, CSP가 새로 만드는 실패 모드다. 그래서 회귀 가드를
      런타임(헤더 nonce == 렌더된 태그 nonce)과 **템플릿 소스 스캔** 둘로 뒀다 — 소스 스캔이 있어야
      테스트가 렌더해 보지 않은 나머지 화면까지 커버된다.
      strict-dynamic을 쓴 이유는 AdSense다 — 로더가 런타임에 여러 호스트에서 스크립트를 심어 허용목록으로는
      적을 수 없다. 뒤의 `https: 'unsafe-inline'`은 구형 브라우저 폴백이고 최신 브라우저는 무시한다.
      `style-src`는 `'unsafe-inline'`을 유지했다 — 관리자 `<style>` 5개 + 인라인 `style=` 18곳을 다 뜯는
      회귀 위험이 CSS 주입의 위력보다 크다(스크립트는 절대 이렇게 하지 않는다).
      ⚠️ **남은 확인 1건**: 광고는 localhost에서 서빙되지 않아 **AdSense 실제 노출은 배포 후 육안 확인**이 유일한
      게이트다. 깨지면 `script-src`에서 `'strict-dynamic'`만 빼고 호스트 허용목록으로 되돌리는 게 최소 롤백이다.

#### 이메일 발송 인프라 — 가입 이메일 인증 + 열거 통지 + 비번 재설정 + 재참여 넛지 (우선순위: 중→상 승격 검토 / 언젠가 필수)
> ⬆️ **우선순위 상향 신호 (2026-06-06)**: §전략 「retention 레버 ①」(재참여 넛지)이 **이메일로 확정**되면서, 이 인프라가
> 이제 보안 갭 3개뿐 아니라 **retention 최우선 레버의 선결조건**도 된다. 즉 "언젠가 필수"에서 "retention 하려면 곧 필요"로 무게가 올라감.
> **왜 묶나**: 아래 네 기능이 **모두 "메일을 보낼 수 있다"를 공통 선결조건**으로 둔다. 지금은 발송 인프라가
> 없어 각각 차선책(흡수·@핸들 게이트)으로 막아뒀고, 메일이 붙는 날 **한 번에 정석으로 닫는 게 한계비용 최소**다.
> - **N-053 — 가입 이메일 인증** (가장 중요): 로컬 가입이 이메일 소유를 검증 안 해 **account pre-hijacking**이 열려
>   있다(공격자가 피해자 이메일로 계정 선점 → 진짜 주인이 소셜 로그인하며 올라탐). `email_verified`는 *역방향*만
>   막고 이 *정방향* 갭은 가입 측 미검증이 원인 → 가입 인증이 근본 처방.
> - **N-052 — 열거 통지**: 현재 가입 이메일 중복을 "성공처럼 흡수"(계정 미생성)만 한다. 정석은 "조용히 수락 + **이미
>   계정 있음을 메일로 통지**" — 잊고 재가입한 정직한 사용자를 로그인 실패까지 안 기다리게 더 친절히 처리 가능.
> - **비밀번호 재설정**(현재 미구현): 로컬 계정이 비번을 잊으면 복구 경로가 없다. 표준 "재설정 링크 메일"이 필요.
> - **재참여 넛지** (retention 레버 ①, 2026-06-06 결정): "오늘 안 읽었어요" 저녁 1통. 위 셋과 달리 *보안*이 아니라 *retention*이지만
>   같은 발송 인프라를 쓴다. ⚠️ 영리목적 광고성 정보 소지 → opt-in·(광고)표시·수신거부·야간(21~08시) 제한(정보통신망법 제50조)이 **넛지에만** 붙는다 → 아래 "📐 안전한 착수 순서" 2단계에서 처리.
>
> **비용 구조 — 보류 사유는 "돈"이 아니다** (개념: learning-notes 후보 / 메일 인프라 비용):
> - **직접 요금 ≈ 0**: 이 규모(가입 1건당 1~2통, 월 수십~수백)는 무료 티어로 충분. AWS **SES**는 1,000통당 $0.10
>   (월 몇 센트), Resend/Brevo/SendGrid 등 무료 티어(월 3,000통/하루 100~300통)면 $0. 예산($50) 위협 없음.
> - **진짜 비용은 셋업·운영**: ① 발신 도메인 검증 + **SPF/DKIM/DMARC** DNS 레코드(일회성·필수, 안 하면 스팸함),
>   ② 딜리버러빌리티(신규 발신 도메인은 평판 0 → 초기 스팸 처리 위험 — `.click` TLD 평판 이슈 N-036과 같은 뿌리),
>   ③ 개발 시간(`JavaMailSender`/SES SDK 연동 + 토큰 발급·만료·검증 플로우 + 템플릿 + 재시도·실패 처리).
> - **보류 근거**: 셋업·운영 손이 들고, 친구 한정 규모라 *현재* 위협 ROI가 낮다. 트래픽이 크거나 규모가 늘면 필수.
> - **✅ 외부 관문 해제 — SES 프로덕션 액세스 승인 (2026-07-06)**: 1차(6/17)·2차(6/23) 거부로 한때 "기술적으로도 보류"였으나, 거부 사유(deliverability·sender reputation)를 **코드 근거로 조목조목 반박한 4차 재요청(6/29 제출)이 승인**됨(case 178123901400162, 발신 한도 50,000통/일·14통/초, 서울 리전 샌드박스 해제). 신규·저트래픽·실적 0에서도 상세·정직한 반박이 통했다([[N-091]] 결론 갱신). 이제 메일 인프라의 외부 차단은 없다 — (a) AWS 승인문이 명시한 **바운스/컴플레인 처리 루프 ✅ 구현+운영 배선 완료**(`email_suppression` + SES→SNS 웹훅 `/internal/ses/notifications` + 발송 게이트, SNS 서명검증·SSRF 가드; **SES 피드백 알림→SNS 토픽 `booktimer-ses-bounce-complaint`→HTTPS 구독 자동확인 + `BOOKTIMER_SES_SNS_TOPIC_ARN` 허용목록**까지 콘솔·env 배선, 2026-07-08). (b) **transactional 실발송은 이미 ON**(`BOOKTIMER_EMAIL_ENABLED=true` — 샌드박스로만 제약됐다가 7/6 승인으로 해제). 남은 건 **마케팅 넛지 점등**(`BOOKTIMER_NUDGE_ENABLED`, 아래 법무 9박스 충족 후)뿐.
>
> **착수 트리거(이때 하면 한계비용 최소)**: `.click` → `.com`/`.app` **도메인 이전**(§도메인 TLD 이전)과 **함께** —
> 어차피 ACM·Route 53·DNS를 만지는 김에 발신 도메인 검증·SPF/DKIM/DMARC를 같이 박으면 작업이 겹친다. 또는 트래픽/회원 신호.
>
> **🔒 하드 선행조건 — 결제·채팅보다 먼저**: pre-hijacking(N-053)은 친구·소규모·가벼운 데이터일 땐 감수 가능한
> 위험이지만, **§비즈니스 모델의 결제·채팅이 붙으면 계정 탈취 가치가 폭증**해 더는 보류 불가다. 즉 가입 이메일
> 인증은 **구독 3단계(채팅)·결제 인프라의 선행조건** — 그 기능들 착수 전에 반드시 닫는다(§비즈니스 모델 선결 목록과 상호참조).
>
> **📐 안전한 착수 순서 — 법적 부담 기준 2단계 분리 (✅ 결정 2026-06-10, "추후 문제 없는 방향")**:
> 같은 발송 인프라를 쓰지만 **법적 부담은 재참여 넛지에만** 붙는다 — transactional(가입 인증·비번 재설정·열거 통지)은
> *서비스 이행 안내*라 정보통신망법 광고성 규제 무관. 그래서 발송 종류를 둘로 갈라 순서를 정한다: 깨끗한 것부터 출하하고,
> 규제 무거운 넛지는 컴플라이언스를 갖춘 뒤 얹는다.
> - **왜 이 순서**: ① 1단계만으로 보안 '높음' 갭 3개(pre-hijacking·열거·비번복구)가 닫히고 법적 리스크 ~0. ② 넛지를
>   *컴플라이언스 없이* 먼저 쏘면 정보통신망법 위반(과태료)에 더해, 미검증 주소 반송으로 **발신 도메인 평판이 깎여
>   1단계 transactional 메일까지 스팸함**으로 동반 사망(연쇄). → transactional이 먼저여야 평판도 안전.

**1단계 — 인프라 + transactional (광고성 규제 무관 · 보안 '높음' 갭 닫힘):**
- [x] **발송 포트 토대 (PR-A)** — `com.booktimer.email`: `EmailSender` 포트 + `SmtpEmailSender`(prod·JavaMailSender)/`LoggingEmailSender`(기본·로그만) 어댑터 + `booktimer.email.enabled` 토글(기본 OFF). 인프라 준비 전 코드 선개발·테스트, 준비되면 토글만 켬(`ReadingPersonalityNarrator` 포트 + Gemini `isEnabled` 게이트와 동형). (PR #293)
- [x] **발송 수단 = SES SMTP 확정·점등** — 프로덕션 액세스 신청 + SMTP 자격증명 발급(SSM SecureString) + `task-definition.json` 주입(`BOOKTIMER_EMAIL_ENABLED=true` → `SmtpEmailSender` 활성, transactional 실발송 ON). 딜리버러빌리티 위해 `.click`→`.app` 이전 선행(N-036 · #310/#311). (PR #312) **+ mail 헬스체크 부활(E단계)**: 첫 가동 안전을 위해 `management.health.mail.enabled=false`로 막아뒀던 헬스체크를 실발송 검증 후 제거해 되살림 — mail DOWN이 진짜 SMTP 장애 신호로 작동(#295 핫픽스의 정상화). (PR #313)
- [x] **발신 도메인 인증 (DKIM·SPF·DMARC)** — `booktimer.app` Easy DKIM 검증 완료(SES가 Route53에 CNAME×3 자동 게시·확인, PR #312). **딜리버러빌리티 보강(PR #317)**: 사용자 지정 MAIL FROM(`mail.booktimer.app`) + SPF(`v=spf1 include:amazonses.com ~all`)·DMARC(`_dmarc` `p=none`) 게시로 DKIM 단독 정렬→**SPF·DKIM 이중 정렬**. Gmail·호서대(`vision.hoseo.edu`=Google Workspace) 실발송 검증 — `Authentication-Results` 셋 다 `pass`(spf `smtp.mailfrom=…@mail.booktimer.app`·dkim `header.i=@booktimer.app`·dmarc)·`Return-Path:<…@mail.booktimer.app>`·받은편지함 안착. 코드 0(SES 콘솔·Route53). (PR #312/#317)
- [x] **가입 이메일 인증 (PR-B)** — `EmailToken`(SHA-256 해시저장·일회용·24h 만료) + `EmailTokenService.issue/consume`(Clock) + 인증 링크 메일(`/verify-email`) + `User.emailVerified`(V31 컬럼·기존 true 백필). **pre-hijacking 차단**: 미검증 LOCAL 선점 계정을 OAuth provision에서 폐기 후 신규(flush로 uk_users_email 순서 강제). 미검증 정책=허용+OAuth 자동연결만 게이트(thesis 마찰 최소). → N-053 정방향 갭 닫음. (PR #294) **+ 하드닝(PR #296)**: GET은 확인 페이지만·POST에서 토큰 소비(메일 링크 프리페치가 토큰 소진 못 하게) + SMTP 발송 비동기화(`EmailDispatcher` @Async — 블로킹·DB커넥션 점유·열거 타이밍 사이드채널 제거). 차후: SHA-256 공용 util(잔여).
- [x] **열거 통지 (PR-D)** — 가입 시 이메일 중복이면 `SignupNotificationService.notifyExistingAccount(email)`가 그 이메일의 **실소유자**에게 "이미 계정 있음" 통지 메일(LOCAL=로그인/비번 재설정, 소셜=Google 로그인). 응답은 여전히 동일(`redirect:/login?registered`)·계정 미생성 — 통지는 시도자가 아니라 주인에게만 가 열거 아님(부재면 무발송). 발송 격리(`EmailDispatcher` @Async). → N-052 "조용히 수락+통지"의 정석 완성. (PR #301)
- [x] **비밀번호 재설정 (PR-C)** — `PASSWORD_RESET` 토큰(1h·SHA-256·일회용, PR-B 토큰 인프라 재사용) + 재설정 링크 메일 + 폼. `requestReset(email)`은 계정 존재/부재/소셜 무관 동일 응답(열거완화 N-052), LOCAL 계정에만 발송. `GET /password/reset`은 토큰 미소비(프리페치 안전, #296 패턴), `POST`에서만 소비해 `changePassword`. `/password/**` permitAll + 로그인 "비밀번호를 잊으셨나요?" 링크. (PR #297)
- [x] **미검증 인증 배너 (정책 ③)** — `emailVerified=false`면 대시보드·설정 화면에 인증 유도 배너(인증 메일 재발송 버튼 → `POST /verify-email/resend`)를 띄운다. 미검증이어도 로그인·사용은 막지 않고 권유만 한다(thesis 마찰 최소). 설정 화면은 재발송 결과(`verifyResendResult`)도 안내. 기존 가입자는 V31 백필(true)이라 안 뜸. 입구 디자인 마감(#287~#291) 후 진행(파일 충돌 회피). → 이메일 인프라 **1단계 코드 완료**. (PR #304)

**2단계 — 재참여 넛지 (정보통신망법 제50조 광고성 규제 동반 · 1단계 위에 얹음):**
- [x] **컴플라이언스(처리방침)** — `privacy.html` §2에 마케팅 수신을 **선택동의로 분리**(필수와 끼워팔기 금지 "동의 안 해도 서비스 이용 제한 없음") + transactional/마케팅 2항목 분리 + 철회방법(설정·구독해지) + §1 수집항목에 동의여부·동의시각. (PR #308)
- [x] **수신동의 토글**(opt-in, **기본 OFF**) — `User.marketingEmailConsent`(기본 false)·`marketingConsentAt` + 가입 폼 선택 체크박스(끼워팔기 금지) + 설정 "소식·알림" 토글(`POST /settings/marketing`). Flyway V32(백필 없음 — 동의 위조 방지). (PR #305)
- [x] **발송 규약(코드)** — `RetentionNudgeService`가 제목 `(광고)` 접두 + 발신자 정보 + **서명 일회용 토큰 수신거부 링크**(`EmailTokenType.UNSUBSCRIBE` 30일, 추측 불가·IDOR 방어) + **인증된 주소에만**(`emailVerified` 게이트, 반송·평판 보호) 발송. 야간(21~08시) 회피는 KST 10시 단일 배치로 해결. 수신거부 소비 엔드포인트(`/unsubscribe`)는 PR-3. (PR #306 / PR-3) *실발송 점등: `RetentionNudgeScheduler`가 독립 게이트 `booktimer.nudge.enabled`(기본 OFF)로 transactional과 분리됨(#312) — 법무 9박스 충족 후 `BOOKTIMER_NUDGE_ENABLED=true`로 점등.*
- [x] **넛지 로직** — `findNudgeTargets`(7일 비활동+동의+검증+구간당 1회) + `RetentionNudgeService`(per-recipient 격리·멱등 `lastNudgeSentAt`) + `RetentionNudgeScheduler` `@Scheduled`(매일 KST 10시 — 저녁 대신 야간 제한 자연 회피). cutoff·경계는 Clock(N-010). (PR #306)
- [x] **OAuth emailVerified 정합** — 소셜 가입(`registerOAuth`)이 검증 표시 + 기존 소셜 백필(Flyway V33). 넛지의 `emailVerified` 게이트(위 발송 규약)가 소셜 사용자를 빠뜨려 동의해도 못 받던 갭 보정(원 설계 외 추가 — phase2 발송 머지 뒤 발견). `provision`의 미검증 거부(N-026) 보존. (PR #308)
- [~] (운영) **수신동의 2년마다 재확인** 의무 인지(넛지 실발송 시작 후 주기 관리) + 반송·스팸신고 피드백 루프 관리 → **피드백 루프는 ✅ 구현+배선 완료**(`email_suppression` + SES→SNS 웹훅으로 영구반송·불만 자동 억제·불만 시 마케팅 동의 철회, changelog 2026-07-08). 2년 재확인만 넛지 점등 후 잔여.

> **🔒 법무 9박스 — 점등 게이트 (정보통신망법 §50 감사, 2026-06-12):** 마케팅 넛지 실발송(`BOOKTIMER_NUDGE_ENABLED=true`)의 법적 선결. §50 광고성 정보 9개 의무를 현재 구현과 대조한 감사표 — 점등 직전 1~8이 모두 ✅인지 확인하는 체크리스트.
>
> | # | §50 의무 | 충족 증거 (코드/문서) | 상태 |
> |---|---|---|---|
> | 1 | 사전 수신동의(opt-in) | `marketingEmailConsent` 기본 false(V32·백필 없음)·가입 선택 체크박스(끼워팔기 금지)·설정 토글 | ✅ |
> | 2 | 제목 `(광고)` 표시 | `RetentionNudgeService.SUBJECT` "(광고) [BookTimer]…" | ✅ |
> | 3 | 전송자 명칭 | 본문 "발신: BookTimer" | ✅ |
> | 4 | 전송자 연락처 | 본문 "문의: tlatldhs0504@naver.com"(처리방침 §7과 동일) — 본 작업 보완 | ✅ |
> | 5 | 수신거부 방법 명시 | 본문 무료 수신거부 링크(`UNSUBSCRIBE` 토큰 30일) | ✅ |
> | 6 | 무료·쉬운 수신거부 | one-click(로그인 불필요)·"(무료)" 명시·IDOR 방어(`UnsubscribeService`) | ✅ |
> | 7 | 야간(21~08시) 전송 제한 | KST 10시 단일 배치로 자연 회피(`RetentionNudgeScheduler`) | ✅ |
> | 8 | 수신동의 증빙 보관 | `marketingConsentAt`(철회해도 보존) | ✅ |
> | 9 | 2년마다 동의 재확인 | 데이터 근거(`marketingConsentAt`) 有·발송 로직 無 → 운영 항목(위 [ ], 동의 2년 후 도래라 점등 비차단) | ⏳ |
>
> 부가: **처리결과 통지**=동의/철회 시 화면 즉시 안내(설정 flash·`unsubscribe-done.html`)로 갈음 ✅. **처리방침**(`privacy.html`) §1 동의여부·시각 수집 + §2 선택동의·끼워팔기 금지·철회방법 ✅.
>
> **📡 점등 runbook (`BOOKTIMER_NUDGE_ENABLED=true`) — 아직 켜지 않음**: 전제 게이트 2개 통과 후 켠다. ① **SES 샌드박스 해제(프로덕션 액세스)** — ✅ **완료 (2026-07-06)**. 케이스 178123901400162가 1차(6/17)·2차(6/23) 거부 후 거부 사유를 코드 근거로 반박한 **4차 재요청(6/29)으로 승인** — 서울 리전 샌드박스 해제, 발신 한도 50,000통/일·14통/초. 이제 미검증 실주소로도 발송 가능(승인 확인은 예고대로 케이스 상태가 아니라 SES 콘솔 Account dashboard의 발신 한도 급증으로 확인됨). ✅ 승인문이 명시한 **바운스/컴플레인 처리 루프도 구현 완료** — SES 아이덴티티 알림→SNS→웹훅(`/internal/ses/notifications`)이 영구 반송·불만을 `email_suppression`에 반영하고 발송 게이트(`SuppressionAwareEmailSender`)가 재발송을 차단(SNS 서명검증·SSRF 가드 포함). 운영 배선(SES→SNS 토픽·구독 + `BOOKTIMER_SES_SNS_TOPIC_ARN` 주입)만 남음. (배경·재발 방지: [learning-notes N-091](claude-docs/learning-notes.md), [troubleshooting T-058](claude-docs/troubleshooting.md)). ② **법무 9박스** 1~8 ✅. **절차**: `task-definition.json` `environment`에 `{"name":"BOOKTIMER_NUDGE_ENABLED","value":"true"}` 추가 → main push → `deploy.yml` 자동 배포(스케줄러 빈 등록) → 다음 KST 10시 배치 발송. **점등 후**: 반송·스팸신고율·DMARC 정렬(N-071) 모니터링, 안정 시 DMARC `p=none`→`quarantine` 상향.

### 측정 세션 `book_id` NOT NULL 제약 — ❌ 폐기(방향 역전, 2026-07-07 발견 1)
> **폐기 이유**: UX 리뷰 **발견 1(PR-E)**로 "책 없이 측정 시작 + 종료 후 태깅"을 도입하며 방향이 **역전**됐다.
> 이제 `book_id IS NULL`은 정리해야 할 레거시가 아니라 **정당한 1급 상태**(무엇을 읽을지 안 정한 채 시작)다.
> 따라서 `book_id`를 `NOT NULL`로 조이는 건 "책 없이 시작"을 원천 차단하므로 **더 이상 목표가 아니다** — 이 백로그를 접는다.
>
> **(역사 보존) 원래 배경**: 한때 "측정은 무조건 책을 골라야 한다"(어떤 책을 얼마나 읽었는지 명확히)를 도입해(PR #133)
> 유스케이스 경계(Service + Controller)에서 book 필수를 강제했다(DB·엔티티 필드는 nullable 유지). 당시엔 이 앱-레이어
> 강제 위에 DB `NOT NULL`을 "벨트+멜빵"으로 얹는 걸 후속으로 뒀으나, 발견 1이 그 앱-레이어 강제 자체를 걷어냈다.
> 이미 그때도 DB 제약을 미룬 이유(레거시 `book_id IS NULL` 행 때문에 마이그레이션 실패 + 고아 세션 backfill 불가,
> N-039)가 있었는데, 이제는 그 nullable 유지가 **오히려 새 설계와 맞아떨어진다**(마이그레이션 0으로 발견 1 구현).
- 관련: learning-notes **N-039**(제약 강화는 백필 먼저). 집계는 null-book 세션을 잔디·부채엔 포함·책별 통계엔 이미 제외
  (`ReadingSessionRepository` `where s.book is not null`, `ReadingSessionRepositoryTest`가 불변식 잠금) — 발견 1이 이 갈림을 그대로 활용.

### Fargate CPU 상향 — 로그인(BCrypt) 지연 (완료 ✅ 2026-06-04, PR #132 / 배포 검증은 run)
- **증상**: 로그인이 체감상 느림.
- **원인**: DB 아님(`findByEmail`은 유니크 인덱스 단건 조회 — 수 ms). 범인은 **BCrypt 비밀번호 검증**(의도적 CPU 집약) ×
  **태스크 `cpu:256`=0.25 vCPU**(Fargate 최소). 1/4 코어 스로틀이라 BCrypt가 수백 ms~1s까지 늘어남. JVM JIT 워밍업도 가중.
  실측: health·/login GET는 60~150ms 정상 → 차이는 로그인 POST의 BCrypt뿐.
- **한 일**: `deploy/task-definition.json`의 `cpu` **256→512**(0.5 vCPU), `memory` **512→1024**로 상향.
  (Fargate는 CPU·메모리 조합이 정해져 있어 cpu 512면 memory 최소 1024 — JVM 힙 여유도 같이 확보.) BCrypt 강도(10)는
  **낮추지 않음**(보안). DB는 손대지 않음. ※ Fargate는 vCPU·메모리 비례 과금 — 비용 소폭 증가.
- 개념: learning-notes(로그인 지연 ≠ DB, BCrypt×작은 vCPU / CPU 집약 해시는 의도된 느림 — 해법은 강도↓가 아니라 CPU↑).
- **검증**: 설정 변경은 머지 후 실제 배포(run)에서 로그인 POST 지연 단축으로 확인.

### 전역 예외 핸들러가 404를 500으로 삼킴 (완료 ✅ 2026-06-02, PR #72)
- `GlobalExceptionHandler(@ExceptionHandler(Exception.class))`가 `NoResourceFoundException`(예: `/favicon.ico`)까지
  잡아 **500**으로 응답·로그 도배하던 문제.
- **한 일**: `{ResponseStatusException, NoResourceFoundException}`를 잡는 좁은 핸들러를 catch-all 위에 두고
  `((ErrorResponse) ex).getStatusCode()`로 상태코드 보존(404는 404로). Boot 4에서 `NoResourceFoundException`이
  `ResponseStatusException`을 더는 상속 안 해(둘 다 `ErrorResponse` 구현) 타입 지정에 주의(T-019/N-028).

### GitHub Actions Node 20 deprecation (완료 ✅ 2026-06-02)
- **왜**: 2026-06-16부터 GitHub Actions가 Node 24를 강제 — Node 20 런타임 액션은 경고/중단.
- **한 일**: 배포 워크플로의 node20 액션을 node24 최신 major로 갱신 —
  `actions/checkout@v4→@v6`, `actions/setup-java@v4→@v5`, `aws-actions/configure-aws-credentials@v4→@v6`
  (v5는 아직 node20이라 v6 필요). `amazon-ecr-login@v2`·`amazon-ecs-render-task-definition@v1`·
  `amazon-ecs-deploy-task-definition@v2`는 이미 node24라 유지. breaking change는 우리 사용 패턴(bare 사용,
  boolean 입력 없음, hosted runner)에 무해함을 릴리스 노트로 확인. 검증은 머지 후 실제 배포(run).

### DB 쿼리 헬스체크 — `book.isbn13` 인덱스 (완료 ✅ 2026-06-05, PR #143)
> "코드가 쌓였는데 최적화 한 번 안 해도 되나?" 질문에 **읽기 전용 헬스체크**부터 수행. 결과: 데이터 계층은
> 건강(전 연관 LAZY·인기 카운트 group-by 일괄·FK 자동 인덱스). 전면 리팩터는 **조기 최적화**라 보류.
> 손댈 가치 있는 단 하나만 반영.
- **왜**: 팔로우 인기 카운트(`followScopePopularity`: `b.isbn13 in :isbns`)·드릴다운(`followScopeReaders`:
  `b.isbn13 = :isbn`)이 **isbn13으로 필터**하는데, isbn13은 FK가 아니라 InnoDB 자동 인덱스 대상이 아님 →
  책 테이블이 커지면 풀스캔.
- **한 일**: `V12__book_isbn13_index.sql` — `create index ix_book_isbn13 on book (isbn13)`. 단일 컬럼(선택도 높은
  필터). 검증은 `FlywayMigrationTest`(격리 H2 적용 + Hibernate validate) 통과.
- **N+1 감사(2026-06-22) 5건 전부 완료 ✅**: ① ~~`findByUser` lazy Book N+1~~ — **PR-A** (2026-06-22): `findByUserWithBook` + `sumSecondsByBook`/`sumSecondsByPublicBook`(DB GROUP BY) + 뮤테이션 단건 `secondsForBook`(#5 부수 해소). ② ~~Admin 신고함/문의함 행당 lazy author N+1~~ — **PR-B** (2026-06-22): `@EntityGraph` 즉시 로딩(#3·#4). ③ ~~`recommend()` `findAll`+`existsBetween` N+1~~ — **PR-C** (2026-06-23): `findRecommendCandidates` JPQL(DB 필터·랜덤·Pageable LIMIT)(#2). ④ ~~`UserRowAssembler` 행당 count+isFollowing(+팔로워/차단 목록의 lazy User ×N)~~ — **후속 PR** (2026-06-23): 배치 `toRows`(공개책수 `countPublicByUsers` group by 1쿼리 + `followingAmong` 1쿼리) + 관계 행 쿼리 `@EntityGraph`(follower/followee/blocked 즉시 로딩)로 소비처 5곳(search·recommend·followers·following·blockedUsers·readers) 일괄 해소. 중복 인덱스(`ix_book_user`·`ix_follow_*`=FK 자동인덱스와 겹침) — 무해(잔여).
