import type { StudyBookRow } from '../study/api'

export interface ContributionDay {
    date: string | null
    totalSeconds: number
    level: number
    manual: boolean
}

export interface MonthLabel {
    weekIndex: number
    label: string
}

export interface GraphDto {
    weeks: ContributionDay[][]
    monthLabels: MonthLabel[]
    totalSeconds: number
    activeDays: number
    currentStreak: number
    // 식물 성장 단계는 2026-08-29에 폐기했다 — 서버 응답에도 더 이상 없다.
}

export interface BookOption {
    id: number
    title: string
    /**
     * 표지 주소 — 서버 `BookOption`이 <b>처음부터 보내던 필드</b>인데 이 타입에 없어서 화면이 버리고
     * 색 박스만 그렸다(2026-09-07 사용자 지적). 옛 픽스처엔 없으므로 optional이다.
     */
    coverUrl?: string | null
}

export interface QuoteDto {
    text: string
    author: string
}

export interface TimerState {
    remainingSeconds: number
    carriedDebtSeconds: number
    todayGoalSeconds: number
    /** 오늘 읽은 초(완료 세션 합, 상한 없음) — 히어로 카운트업의 출처. 측정 중 몫은 클라가 얹는다. */
    todayReadSeconds: number
    carryover: boolean
    hasActiveSession: boolean
    activeStartedAt: string | null
    activeBookTitle: string | null
    activeBookTotalSeconds: number
    readingBooks: BookOption[]
    finishedBooks: BookOption[]
    recentBookId: number | null
}

/**
 * `/api/dashboard`의 `study` 블록 — 화면이 쓰는 7필드. 서버가 아직 싣는 하루 목표(`goalSeconds`)는
 * 2026-09-13 컨셉 전환으로 읽지 않는다(서버 필드는 PR-5에서 지운다). 회당 시간은 `StudyBookRow`에 있다.
 */
export interface StudyState {
    hasActiveSession: boolean
    activeStartedAt: string | null
    /** 오늘 공부한 초(완료 세션 합) — 진행 중 몫은 클라가 activeStartedAt으로 매초 얹는다(독서와 같은 분업). */
    todaySeconds: number
    /** 측정 중인 책(없거나 「책 없이」면 null). */
    activeBook: StudyBookRow | null
    /** 마지막으로 책을 걸고 잰 책 — idle 기본 칩이 이걸 고른다. */
    recentBookId: number | null
    /** 내 공부 서재 전체 — 시트·기본 칩이 이 목록을 본다(시트 fetch 0). */
    books: StudyBookRow[]
    /** stop 응답에서만 non-null — 책 없이 끝낸 측정의 태깅 좌표. */
    untaggedSessionId: number | null
}

/** study가 없는 응답(옛 서버·옛 픽스처)의 폴백 — 공부 진행 0 → 독서 모드로 떨어진다. */
export const IDLE_STUDY: StudyState = {
    hasActiveSession: false, activeStartedAt: null, todaySeconds: 0,
    activeBook: null, recentBookId: null, books: [], untaggedSessionId: null,
}

/**
 * 서버 응답·옛 픽스처를 7필드로 채운다 — 필드가 빠진 응답(옛 서버·독서 테스트 픽스처)에서
 * `books.map`이 죽지 않게. **study를 대입하는 모든 자리가 이 함수를 지난다**(applyDashboard·
 * start·stop·session-goal·tag·change) — 한 곳이라도 날것 `res.json()`을 넣으면 그 자리만 옛 서버에서 깨진다.
 *
 * 이 규약은 **6자리 중 5자리가 계측기로 잠겨 있다**(2026-09-13 전체 스위트 돌연변이 실측 — 그 자리의 정규화를
 * 걷으면 해당 테스트가 죽는다): applyDashboard·start·change = `dashboard-study-book.test.ts` (i1)~(i3),
 * session-goal = `dashboard-session-goal.test.ts` (i5), stop = `dashboard-study-book.test.ts` (f3).
 * 관측기는 **측정 중 히어로 숫자**(`todaySeconds + elapsed`)다 — 시작 시각이 과거인 응답에서 todaySeconds가
 * 빠지면 정규화 시 `10:00`, 날것이면 NaN → `fmtMSS`가 `00:00`. (2026-09-13 전엔 하루 목표 게이지가 관측기였다.)
 * stop만 예외로 `s.books.length`를 즉시 읽어 던진다.
 * ⚠️ **관측 불가는 tag 하나다** — 종료 후라 elapsed가 0이어서 NaN·0이 둘 다 `00:00`이고, 나머지 필드는
 * StudyTimerCard가 `withDefaults`로 스스로 메워 화면이 같다. 그 자리는 코드 리뷰가 지킨다.
 */
export function studyStateOf(s?: Partial<StudyState> | null): StudyState {
    return { ...IDLE_STUDY, ...(s ?? {}) }
}

export interface DashboardResponse extends TimerState {
    nickname: string
    loginId: string
    // 읽고싶음 책 — 시작 드롭다운엔 없지만 "종료 후 태깅" 시트에서 고를 수 있다(발견 1). 초기 로드에만 실린다.
    wantToReadBooks: BookOption[]
    graph: GraphDto
    quotes: QuoteDto[]
    emailVerified: boolean
    /** 없으면 옛 서버·옛 픽스처 — IDLE_STUDY로 떨어진다(독서 테스트 픽스처가 이 필드를 모른다). */
    study?: StudyState
}

/**
 * stop 응답 — 방금 종료된 세션 id·미태깅 여부 + 타이머 + 잔디(측정 종료 즉시 갱신용).
 * untagged(책 없이 시작한 세션)면 클라이언트가 sessionId로 "무슨 책?" 태깅 시트를 띄운다(발견 1).
 */
export interface StopResponse {
    sessionId: number
    untagged: boolean
    timer: TimerState
    graph: GraphDto
}

/** tag-book 응답 — 어느 세션에 어떤 책을 붙였는지 확인용. */
export interface TagBookResponse {
    sessionId: number
    bookTitle: string
}
