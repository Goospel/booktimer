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

export interface CatalogDto {
    ownedAuthorCharacterCount: number
    totalAuthorCharacterCount: number
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
    quote: QuoteDto
    emailVerified: boolean
}
