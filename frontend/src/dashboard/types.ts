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

export interface PlantDto {
    code: string
    emoji: string
    name: string
    owned: boolean
}

export interface CatalogDto {
    plants: PlantDto[]
    ownedCount: number
    totalCount: number
    achievedDays: number
    daysToNextUnlock: number | null
    nextPlantName: string | null
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
