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
}

/** 보유 작가 — 대시보드는 name·emoji만 쓴다(affection/level/title은 0 고정이라 참조 금지). */
export interface OwnedAuthor {
    code: string
    emoji: string
    name: string
    spriteId: string
}

export interface CatalogDto {
    ownedAuthorCharacterCount: number
    totalAuthorCharacterCount: number
    ownedCharacters: OwnedAuthor[]
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

/** `/api/dashboard`의 `study` 블록 — 서버 StudyState 8필드 중 웹 1차가 읽는 넷만 선언한다(나머지는 무시). */
export interface StudyState {
    hasActiveSession: boolean
    activeStartedAt: string | null
    /** 오늘 공부한 초(완료 세션 합) — 진행 중 몫은 클라가 activeStartedAt으로 매초 얹는다(독서와 같은 분업). */
    todaySeconds: number
    goalSeconds: number
}

/** study가 없는 응답(옛 서버·옛 픽스처)의 폴백 — 공부 진행 0 → 독서 모드로 떨어진다. */
export const IDLE_STUDY: StudyState = { hasActiveSession: false, activeStartedAt: null, todaySeconds: 0, goalSeconds: 0 }

export interface DashboardResponse extends TimerState {
    nickname: string
    loginId: string
    profileCharacterCode: string | null
    // 읽고싶음 책 — 시작 드롭다운엔 없지만 "종료 후 태깅" 시트에서 고를 수 있다(발견 1). 초기 로드에만 실린다.
    wantToReadBooks: BookOption[]
    graph: GraphDto
    garden: CatalogDto
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
