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
    growthStageName: string
    growthStageEmoji: string
    growthStageLabel: string
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
    ownedBuildingCount: number
    totalBuildingCount: number
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
    graph: GraphDto
    garden: CatalogDto
    quotes: QuoteDto[]
    emailVerified: boolean
}

/** stop 응답 — 타이머 + 방금 확정된 세션이 반영된 잔디(측정 종료 즉시 잔디 갱신용). */
export interface StopResponse {
    timer: TimerState
    graph: GraphDto
}
