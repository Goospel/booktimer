<script setup lang="ts">
import { ref, watch, computed, nextTick, onUnmounted } from 'vue'
import { useReadingTimer } from './useReadingTimer'
import { fmtMSS, goalLabel } from './timerProgress'
import { sessionGoalView, minutesToSessionGoal } from './sessionGoal'
import { initialOf, coverColor, hasCover } from '../books/pure'
import { defaultStudyBookOf } from './defaultBook'
import type { StudyBookRow } from '../study/api'

const props = withDefaults(defineProps<{
    /** 오늘 공부한 초(완료 세션 합) — 측정 중 몫은 elapsed로 얹는다(독서와 같은 분업). */
    todaySeconds: number
    hasActiveSession: boolean
    activeStartedAt: string | null
    starting?: boolean
    stopping?: boolean
    /** 회당 시간 저장 왕복 중 — 인라인 폼의 저장 버튼만 잠근다. */
    savingSessionGoal?: boolean
    /** 내 공부 서재 — 기본 칩이 여기서 골라진다. 빈 서재가 기본값(옛 서버·옛 픽스처). */
    books?: StudyBookRow[]
    /** 마지막으로 책을 걸고 잰 책 — 기본 칩 1순위. */
    recentBookId?: number | null
    /** 시트에서 방금 고른 책 — 있으면 기본 규칙을 이긴다(고르기는 시작이 아니다). */
    pickedBook?: StudyBookRow | null
    /** 측정 중인 책. null이면 「책 없이」(빈칸이 아니라 상태다). */
    activeBook?: StudyBookRow | null
    /** 책 교체 왕복 중 — 「책 바꾸기」를 잠근다. */
    changing?: boolean
}>(), { books: () => [], recentBookId: null, pickedBook: null, activeBook: null })

const emit = defineEmits<{
    start: [bookId: number | null]; stop: []
    setSessionGoal: [bookId: number, seconds: number | null]
    openSheet: []; changeBook: []
}>()

// 칩에 설 책 = 시트에서 고른 책 → 최근 걸고 잰 책 → 첫 책(독서 BookPickForm과 같은 규칙). 셋 다 없으면 null.
// 홈 필기 카드도 같은 함수를 본다(DashboardApp) — 각자 계산하면 「칩엔 A, 필기엔 B」로 갈린다.
const defaultBook = computed<StudyBookRow | null>(() =>
    defaultStudyBookOf(props.books, props.recentBookId, props.pickedBook))

// 칩 표지색 — 표지 없는 책의 결정적 플레이스홀더(독서 칩과 같은 seed 규칙).
function coverStyle(b: StudyBookRow) {
    const c = coverColor(b.isbn13 || b.title)
    return { background: c.bg, color: c.fg }
}

// props를 ref로 래핑해 composable에 전달(TimerCard와 같은 3줄). 공부엔 부채가 없어 base는 0 —
// remainingNow는 안 쓰고 elapsed(벽시계 경과)만 쓴다.
const active = ref(props.hasActiveSession)
const startedAtIso = ref<string | null>(props.activeStartedAt)
watch(() => props.hasActiveSession, v => active.value = v)
watch(() => props.activeStartedAt, v => startedAtIso.value = v)

const { elapsed } = useReadingTimer(ref(0), active, startedAtIso)

// ── 책별 회당 시간 ──────────────────────────────────────────────────────────────
// 대상 책 = 측정 중이면 activeBook, 아니면 칩 책. 기준은 그 책의 **현재 값**(설계 §4.1 — 세션 스냅샷 없음).
const goalBook = computed(() => props.hasActiveSession ? props.activeBook : defaultBook.value)
const view = computed(() => props.hasActiveSession
    ? sessionGoalView(props.activeBook?.sessionGoalSeconds, elapsed.value)
    : sessionGoalView(null, 0))

// 탭 제목 알림 — 다른 탭을 보고 있어도 닿았음을 안다. 브라우저 Notification은 쓰지 않는다(사용자 기각).
// 떼는 쪽은 제목 자체를 보고 판정한다 — 남이 바꾼 제목을 저장해 둔 옛 값으로 덮지 않는다.
// (붙이는 쪽은 watch가 false→true 전환에서만 부르므로 중복 가드를 두지 않는다.)
const TITLE_PREFIX = '[회당 시간 달성] '
function titlePrefix(on: boolean) {
    const t = document.title
    if (on) document.title = TITLE_PREFIX + t
    else if (t.startsWith(TITLE_PREFIX)) document.title = t.slice(TITLE_PREFIX.length)
}
watch(() => view.value.kind === 'reached', titlePrefix, { immediate: true })
onUnmounted(() => titlePrefix(false))

// 인라인 편집 — 설정 페이지로 보내지 않는다. 닫는 건 부모다(저장 성공을 본 뒤): 먼저 닫으면 실패했을 때
// 사용자가 친 값이 사라지고, 저장 중 잠금이 한 번도 렌더되지 않는다.
const editing = ref(false)
const goalMinutes = ref<number | ''>('')
const goalInput = ref<HTMLInputElement | null>(null)
async function openEdit() {
    const v = goalBook.value?.sessionGoalSeconds
    goalMinutes.value = v ? Math.round(v / 60) : ''
    editing.value = true
    // 폼은 좌열, 손잡이는 우측 칩 아래 — 좁은 폭에선 폼이 화면 밖일 수 있어 포커스로 스크롤을 부른다.
    await nextTick()
    goalInput.value?.focus()
}
function submitGoal(seconds: number | null) {
    if (goalBook.value) emit('setSessionGoal', goalBook.value.id, seconds)
}
// 폼이 열린 채 대상 책이 바뀌면(칩 교체·측정 시작) 닫는다 — 다른 책에 저장되지 않게.
watch(() => goalBook.value?.id, () => { editing.value = false })
/** 부모(DashboardApp)가 저장 성공을 확인한 뒤 부른다. */
function closeEdit() { editing.value = false }
defineExpose({ closeEdit })
</script>

<template>
    <!-- focus-top — 측정 중이면 홈의 필기 카드와 한 장으로 합쳐지고 이 카드는 그 머리 막대가 된다
         (설계 2026-09-15-study-focus-lamp 결정 5·6). 전환 이름은 app.css 「공부 집중」 절이 붙인다. -->
    <section class="dash-card dash-timer-hero is-study focus-top">
        <!-- 측정 중 = 머리 막대 한 줄. 모드 토글은 없다 — 잠긴 토글은 「왜 못 바꾸나」를 말하려 있었는데,
             안 보이면 물음도 없고 「측정 종료」가 곁에 있다(결정 6). -->
        <div v-if="hasActiveSession" class="focus-bar" data-testid="focus-bar">
            <span class="dash-pill dash-pill-pulse"><span class="dash-pulse-dot"></span>측정 중</span>
            <!-- .vt-clock은 두 상태에 **하나씩만** — 둘 다 그리면 전환 이름이 겹쳐 브라우저가 전환을 통째로 건너뛴다. -->
            <span class="dash-timer-num vt-clock" data-testid="focus-today">{{ fmtMSS(todaySeconds + elapsed) }}</span>
            <div class="focus-kv" data-testid="focus-session">
                <span class="dash-kv-k">이번 측정</span>
                <span class="dash-kv-v dash-kv-v-num">{{ fmtMSS(elapsed) }}</span>
            </div>
            <!-- 회당 시간이 없는 책·책 없이 = 스톱워치라 이 칸이 없다. 닿아도 측정은 계속된다(색·문구만 바뀐다). -->
            <div v-if="view.kind === 'countdown'" class="focus-kv dash-session-line">
                <span class="dash-kv-k">남은 시간 · 회당 {{ goalLabel(view.goal) }}</span>
                <span class="dash-kv-v dash-kv-v-num">{{ fmtMSS(view.remaining) }}</span>
            </div>
            <div v-else-if="view.kind === 'reached'" class="focus-kv dash-session-line is-reached">
                <span class="dash-kv-k">회당 {{ goalLabel(view.goal) }} 달성</span>
                <span class="dash-kv-v dash-kv-v-num">+{{ fmtMSS(view.overflow) }}</span>
            </div>
            <div class="focus-kv is-book" data-testid="focus-book">
                <span class="dash-kv-k">지금 공부하는 책</span>
                <span class="dash-kv-v" :title="activeBook?.title">{{ activeBook?.title ?? '책 없이' }}</span>
            </div>
            <div class="focus-actions">
                <button v-if="activeBook" type="button" class="dash-btn-link dash-bookless" @click="openEdit">
                    회당 시간 변경
                </button>
                <!-- 잰 시간은 통째로 새 책에 옮겨간다(서버 계약) — 측정을 끊지 않고 바꾼다. -->
                <button type="button" class="dash-btn-link dash-bookless" :disabled="changing" @click="emit('changeBook')">
                    책 바꾸기
                </button>
                <button type="button" class="dash-btn-outline" :disabled="stopping" @click="emit('stop')">
                    {{ stopping ? '종료하는 중…' : '측정 종료' }}
                </button>
            </div>
        </div>
        <!-- 회당 시간 폼(측정 중) — 막대 **아래 한 줄**(flex-basis 100%). 막대 안에 끼우면 한 줄 막대가 부푼다.
             (주의) 대기 쪽 폼(아래 패널 안)과 같은 조각이다 — 한쪽을 고치면 다른 쪽도 고친다. -->
        <form v-if="hasActiveSession && editing && goalBook" class="dash-goal-edit" @submit.prevent="submitGoal(minutesToSessionGoal(goalMinutes))">
            <label>이 책 회당 시간
                <input ref="goalInput" type="number" min="10" max="360" step="1" v-model.number="goalMinutes"
                       aria-label="이 책 회당 시간(분)"> 분
            </label>
            <button type="submit" class="dash-btn-fill" :disabled="savingSessionGoal">
                {{ savingSessionGoal ? '저장하는 중…' : '저장' }}
            </button>
            <button type="button" class="dash-btn-link dash-bookless" @click="editing = false">취소</button>
            <button v-if="goalBook.sessionGoalSeconds" type="button" class="dash-btn-link dash-bookless"
                    :disabled="savingSessionGoal" @click="submitGoal(null)">회당 시간 없이</button>
        </form>

        <template v-if="!hasActiveSession">
        <div class="dash-timer-left">
            <span class="dash-pill">오늘 공부한 시간</span>
            <div class="dash-timer-num vt-clock">{{ fmtMSS(todaySeconds + elapsed) }}</div>
        </div>

        <div class="dash-timer-right">
            <!-- 회당 시간 폼은 손잡이(칩 아래 줄) 바로 뒤에 선다 — 좌열에 두었을 땐 넓은 화면에서 손잡이와 ≈650px
                 떨어지고 좁은 화면에선 손잡이 위에 끼어들어 밀어냈다(실측). -->
            <div class="dash-state-panel">
                <!-- BookPickForm(독서)을 재사용하지 않는다: 문구가 「읽어볼까요」라 prop을 더해야 하고,
                     그게 곧 공용 조각 기본값 사각이다. 인라인 15줄이 싸다(설계 §3.3-C3). -->
                <template v-if="defaultBook">
                    <span class="dash-idle-label">이 책으로 공부할까요?</span>
                    <div class="dash-book-chip">
                        <img v-if="hasCover(defaultBook.coverUrl)" class="dash-book-chip-cover"
                             :src="defaultBook.coverUrl!" alt="" loading="lazy" referrerpolicy="no-referrer">
                        <span v-else class="dash-book-chip-cover" :style="coverStyle(defaultBook)" aria-hidden="true">{{ initialOf(defaultBook.title) }}</span>
                        <span class="dash-book-chip-title" :title="defaultBook.title">{{ defaultBook.title }}</span>
                        <button type="button" class="dash-book-chip-change" :disabled="starting" @click="emit('openSheet')">바꾸기</button>
                    </div>
                    <div class="dash-session-goal">
                        <template v-if="defaultBook.sessionGoalSeconds">
                            회당 {{ goalLabel(defaultBook.sessionGoalSeconds) }}
                            <button type="button" class="dash-goal-change" aria-label="회당 시간 변경" @click="openEdit">변경</button>
                        </template>
                        <button v-else type="button" class="dash-btn-link dash-bookless" @click="openEdit">회당 시간 정하기</button>
                    </div>
                </template>
                <!-- 서재가 비어도 시작을 막지 않는다 — 담으러 가는 문은 링크 하나로 곁에 둔다. -->
                <template v-else>
                    <span class="dash-idle-label">지금 공부를 시작할까요?</span>
                    <button type="button" class="dash-btn-fill" :disabled="starting" @click="emit('start', null)">
                        {{ starting ? '시작하는 중…' : '공부 측정 시작' }}
                    </button>
                    <a class="dash-btn-link dash-bookless" href="/study/books">공부 서재에 책 담기</a>
                </template>

                <!-- 회당 시간 폼(대기) — 칩 아래 손잡이 줄 바로 뒤에 선다. 손잡이는 여는 동안에도 남긴다 — 숨기면
                     그 줄이 빠지며 레이아웃이 튄다. 서재 0권(goalBook 없음)이면 열릴 일이 없다.
                     (주의) 측정 중 폼(위 막대 아래)과 같은 조각이다 — 한쪽을 고치면 다른 쪽도 고친다. -->
                <form v-if="editing && goalBook" class="dash-goal-edit" @submit.prevent="submitGoal(minutesToSessionGoal(goalMinutes))">
                    <label>이 책 회당 시간
                        <!-- step은 스피너 간격이 아니라 **유효성 제약**이다 — step="1" = 정수 분만 받는다(7.5는 크롬이
                             「가장 근접한 유효 값」 버블로 막는다). 10분~6시간(서버 600~21600초와 같은 범위). 빈칸은 해제(null). -->
                        <input ref="goalInput" type="number" min="10" max="360" step="1" v-model.number="goalMinutes"
                               aria-label="이 책 회당 시간(분)"> 분
                    </label>
                    <button type="submit" class="dash-btn-fill" :disabled="savingSessionGoal">
                        {{ savingSessionGoal ? '저장하는 중…' : '저장' }}
                    </button>
                    <!-- 보조 둘은 「책 없이 시작」과 같은 조용한 링크 관용구(.dash-bookless). -->
                    <button type="button" class="dash-btn-link dash-bookless" @click="editing = false">취소</button>
                    <button v-if="goalBook.sessionGoalSeconds" type="button" class="dash-btn-link dash-bookless"
                            :disabled="savingSessionGoal" @click="submitGoal(null)">회당 시간 없이</button>
                </form>

                <!-- 대기·책 있음의 시작 버튼 둘은 폼 **뒤**에 둔다(폼이 손잡이와 시작 사이에 끼도록). -->
                <template v-if="defaultBook">
                    <button type="button" class="dash-btn-fill" :disabled="starting" @click="emit('start', defaultBook.id)">
                        {{ starting ? '시작하는 중…' : '공부 측정 시작' }}
                    </button>
                    <button type="button" class="dash-btn-link dash-bookless" :disabled="starting" @click="emit('start', null)">책 없이 시작</button>
                </template>
            </div>
        </div>
        </template>
    </section>
</template>
