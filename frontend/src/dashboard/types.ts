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
    carryover: boolean
    hasActiveSession: boolean
    activeStartedAt: string | null
    activeBookTitle: string | null
    activeBookTotalSeconds: number
    readingBooks: BookOption[]
    finishedBooks: BookOption[]
    recentBookId: number | null
}

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
