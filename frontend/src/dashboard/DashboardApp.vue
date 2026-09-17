<script setup lang="ts">
import { ref, computed, watch, watchEffect, onMounted, onUnmounted } from 'vue'
import type { DashboardResponse, TimerState, StopResponse, BookOption, StudyState } from './types'
import type { StudyBookRow } from '../study/api'
import { noteIdParam } from '../study/notes'
import { IDLE_STUDY, studyStateOf } from './types'
import { getCsrfToken } from '../shared/follow'
import type { TimerMode } from './timerMode'
import { shouldRefresh, readMode, writeMode, effectiveMode, syncRailMode, studyFocusOn, syncStudyLamp } from './timerMode'
import { withViewTransition } from './viewTransition'
import { allBooksOf, defaultBookOf, defaultStudyBookOf } from './defaultBook'
import TimerCard from './TimerCard.vue'
import StudyTimerCard from './StudyTimerCard.vue'
import ModeToggle from './ModeToggle.vue'
import BookPickSheet from './BookPickSheet.vue'
import StudyBookSheet from './StudyBookSheet.vue'
import MarginCard from './MarginCard.vue'
import StudyNotesCard from './StudyNotesCard.vue'
import BrandQuote from './BrandQuote.vue'
import EmailVerifyBanner from './EmailVerifyBanner.vue'
import WelcomeBanner from './WelcomeBanner.vue'
import DashHeader from './DashHeader.vue'

// justOnboarded: 온보딩 직후 셸 data 속성 → main.ts가 읽어 주입. 1회 환영 배너 트리거(§6.4).
const props = defineProps<{ justOnboarded?: boolean }>()
const showWelcome = ref(props.justOnboarded === true)

const data = ref<DashboardResponse | null>(null)
const loading = ref(true)
const fetchError = ref(false)
const actionError = ref<string | null>(null)
// 서버 왕복 동안 버튼에 "진행 중"을 표시해 멈칫을 의도된 피드백으로 보이게 + 중복 클릭(409) 방지
const starting = ref(false)
const stopping = ref(false)
// 공부 회당 시간 저장 왕복 — 히어로 편집 폼의 저장 버튼만 잠근다(측정 시작/종료와 무관한 별도 문).
// 왕복이 끝날 때까지 폼이 열려 있어야 이 잠금이 실제로 보인다 — 닫기는 성공 분기에서 카드에 알린다.
const savingSessionGoal = ref(false)
const studyCard = ref<{ closeEdit: () => void } | null>(null)

// 타이머 상태 — start/stop 응답으로 부분 갱신
const remainingSeconds = ref(0)
const carriedDebtSeconds = ref(0)
const todayGoalSeconds = ref(0)
const todayReadSeconds = ref(0)
const carryover = ref(true)
const hasActiveSession = ref(false)
const activeStartedAt = ref<string | null>(null)
const activeBookTitle = ref<string | null>(null)
const activeBookTotalSeconds = ref(0)
const readingBooks = ref<BookOption[]>([])
const finishedBooks = ref<BookOption[]>([])
const wantToReadBooks = ref<BookOption[]>([])
const recentBookId = ref<number | null>(null)

// 공부 원장 — /api/dashboard의 study 블록. 없으면(옛 서버) IDLE_STUDY로 떨어져 독서 모드가 된다.
const study = ref<StudyState>(IDLE_STUDY)
const storedMode = ref<TimerMode>(readMode())
// 서버 진실이 저장값을 이긴다 — 진행 중 원장의 모드가 화면 모드다(미니앱 effectiveMode 1:1).
const mode = computed(() => effectiveMode(hasActiveSession.value, study.value.hasActiveSession, storedMode.value))
// 섬 밖 SSR 세로 바의 모드(한쪽 메뉴만 보임)를 같은 모드로 — 섬 밖 DOM 한 속성(HistoryApp의 body 클래스와 같은 관례).
// 아래 독서등과 **같은 이유로 응답을 본 뒤에만** 쓴다: 응답 전엔 저장값이 진실이 아닐 수 있고(공부 측정 중에
// 왼쪽 바를 들르면 저장값이 reading이 된다), 그때 덮으면 인라인 부트가 램프 힌트까지 보고 세운 값을 지워
// 밤 배경에 독서 바가 선명한 구간이 응답까지 남는다(실측 271ms). 부트가 남긴 값이 응답 전 최선의 추정이다.
// 램프 힌트가 낡았어도(세션이 다른 탭에서 끝남) 응답이 오면 정정되므로 더 나빠지지 않는다.
watchEffect(() => {
    if (loading.value) return
    syncRailMode(document, mode.value)
})

// 공부 집중 — 측정 중엔 타이머와 필기가 한 장이 되고 홈이 밤이 된다(설계 2026-09-15-study-focus-lamp).
// 합침 ≡ 독서등 ≡ 이 값 하나. body 클래스(섬 밖 DOM)는 **응답을 본 뒤에만** 쓴다 — 마운트 직후의 기본값(IDLE_STUDY)으로
// 쓰면 인라인 부트가 첫 페인트 전에 붙인 등을 지워, 측정 중 새로고침마다 낮→밤 깜빡임이 되살아난다.
const studyFocus = computed(() => studyFocusOn(mode.value, study.value.hasActiveSession))
watchEffect(() => {
    if (loading.value) return
    syncStudyLamp(document, studyFocus.value)
})
onUnmounted(() => syncStudyLamp(document, false))
const notesCard = ref<{ focusEnd: () => void } | null>(null)
// 캐럿은 마우스·트랙패드에서만 — 태블릿에서 시작을 누르자마자 키보드가 올라와 합쳐진 카드 절반을 덮는 것을 막는다(rail.js HOVER_QUERY와 같은 판별).
const finePointer = () => window.matchMedia?.('(hover: hover) and (pointer: fine)').matches === true

const measuring = computed(() => hasActiveSession.value || study.value.hasActiveSession)
// 왕복 중(starting/stopping)에도 잠근다 — 응답 대기 중에 모드를 바꾸면 반대 카드가 요청도 없이
// 남의 "시작하는 중…" 비활성 버튼을 뒤집어쓰고, 응답이 오면 도로 튄다.
const toggleLocked = computed(() => measuring.value || starting.value || stopping.value)
const modeHint = ref<string | null>(null)
watch(toggleLocked, l => { if (!l) modeHint.value = null })

/**
 * 시트에서 고른 책 — **고르기는 시작이 아니다**(2026-09-12 사용자 지적). 「바꾸기」로 책을 고르면
 * 여기 얹혀 칩·여백 카드가 그 책을 가리키고, 측정은 시작 버튼이 누를 때 시작된다.
 * null이면 지금까지처럼 기본 규칙(최근 읽은 책 → 첫 책)을 따른다.
 * 서버 진실로 되돌리는 자리(conflict)에서 함께 버린다 — 지워진 책이 칩에 눌어붙지 않게.
 */
const pickedBook = ref<BookOption | null>(null)
const pickedStudyBook = ref<StudyBookRow | null>(null)

// 홈의 「지금 그 책」 — 타이머 칩과 여백 카드가 같은 책을 가리켜야 해서 한 곳에서 고른다.
const marginBook = computed(() => pickedBook.value ??
    defaultBookOf(allBooksOf(readingBooks.value, finishedBooks.value, wantToReadBooks.value), recentBookId.value))

// 공부 쪽의 같은 규칙 — 필기 카드의 책 = 측정 중인 책, 아니면 칩 기본 책(StudyTimerCard와 같은 함수).
// 측정 중인 책이 곧 필기할 책이다: 「책 바꾸기」로 activeBook이 바뀌면 필기도 따라간다(NotesPanel watch).
const notesBookId = computed(() => study.value.activeBook?.id
    ?? defaultStudyBookOf(study.value.books, study.value.recentBookId, pickedStudyBook.value)?.id ?? null)

// 필기 화면(/study/notes)의 행 → `/?note=<id>` — 필기 카드가 그 장을 연다(설계 2026-09-17 D2).
// 읽자마자 주소에서 지운다: 남겨 두면 새로고침·로고 재진입마다 그 장이 다시 열려 「홈 진입 = 빈 새 필기」가 깨진다.
// 독서 모드(독서 측정 중)면 카드가 없어 조용히 버려진다 — 측정 중인 독서를 밀어낼 이유가 없다.
const initialNoteId = noteIdParam(location.search)
if (initialNoteId !== null) history.replaceState(null, '', location.pathname)

// 책 고르기/태깅 통합 시트(발견 1, §6.5) — 'start'=측정 전 고르기, 'tag'=종료 후 태깅. 같은 시트를 모드로 겸한다.
const sheetMode = ref<'start' | 'tag' | null>(null)
const pendingSessionId = ref<number | null>(null)
const tagging = ref(false)

// 공부 책 시트 — 독서 시트와 원장이 갈린다(각자 자기 stop 응답에서만 열려 겹치지 않는다).
// 'start'=시작 전 고르기, 'tag'=종료 후 태깅, 'change'=측정 중 교체.
const studySheet = ref<'start' | 'tag' | 'change' | null>(null)
const studyPendingSessionId = ref<number | null>(null)

function applyTimerState(s: TimerState) {
    remainingSeconds.value = s.remainingSeconds
    carriedDebtSeconds.value = s.carriedDebtSeconds
    todayGoalSeconds.value = s.todayGoalSeconds
    todayReadSeconds.value = s.todayReadSeconds
    carryover.value = s.carryover
    hasActiveSession.value = s.hasActiveSession
    activeStartedAt.value = s.activeStartedAt
    activeBookTitle.value = s.activeBookTitle
    activeBookTotalSeconds.value = s.activeBookTotalSeconds
    readingBooks.value = s.readingBooks
    finishedBooks.value = s.finishedBooks
    recentBookId.value = s.recentBookId
}

/** /api/dashboard 응답 전체를 화면 상태에 얹는다(최초 로드·복귀 재조회 공용). graph·quotes는 제외. */
function applyDashboard(d: DashboardResponse) {
    applyTimerState(d)
    wantToReadBooks.value = d.wantToReadBooks ?? []
    study.value = studyStateOf(d.study)
}

// 마지막 /api/dashboard 조회 시각 — 복귀 재조회 스로틀의 기준(요청 "전"에 찍는다).
let lastFetchedAt = 0

onMounted(async () => {
    try {
        lastFetchedAt = Date.now()
        const res = await fetch('/api/dashboard', { credentials: 'same-origin' })
        if (!res.ok) throw new Error(res.statusText)
        data.value = await res.json() as DashboardResponse
        applyDashboard(data.value)
        // 비홈 스위치는 측정 상태를 모른다 — 다른 화면에서 반대 모드를 눌러 왔는데 서버 진실이 되돌렸으면 이유를 말한다(설계 2026-09-17 D2).
        if (toggleLocked.value && storedMode.value !== mode.value) onModeBlocked()
    } catch {
        fetchError.value = true
    } finally {
        loading.value = false
    }
})

/**
 * 탭·창 복귀 시 조용한 재조회 — 다른 기기에서 시작·정지하면 이 화면이 낡기 때문(미니앱 silentRefresh와 같은 규칙).
 * 성공했을 때만 덮고 실패는 무시한다(화면 유지). graph·quotes는 안 덮는다 —
 * 명언이 복귀마다 섞이면 어지럽고, 잔디는 stop 응답이 이미 갱신한다.
 */
async function refresh(force = false) {
    if (!force) {
        if (document.visibilityState !== 'visible') return
        // 내 왕복 응답을 낡은 스냅샷이 덮지 않게. force는 방금 실패한 내 왕복이 부른 것이라 덮을 게 없다.
        if (starting.value || stopping.value || tagging.value || savingSessionGoal.value) return
    }
    if (!shouldRefresh(lastFetchedAt, Date.now(), force)) return
    lastFetchedAt = Date.now()
    try {
        const res = await fetch('/api/dashboard', { credentials: 'same-origin' })
        if (!res.ok) return
        applyDashboard(await res.json() as DashboardResponse)
    } catch {
        /* 조용히 — 다음 복귀·클릭에서 다시 시도한다 */
    }
}

// focus도 듣는 이유: 데스크톱은 창을 갈아타도 visibilityState가 'visible'로 남는 경우가 많다.
// 같은 스로틀을 타므로 둘 다 발화해도 요청은 1회.
const onReturn = () => { refresh() }
onMounted(() => {
    document.addEventListener('visibilitychange', onReturn)
    window.addEventListener('focus', onReturn)
})
onUnmounted(() => {
    document.removeEventListener('visibilitychange', onReturn)
    window.removeEventListener('focus', onReturn)
})

/** 409 = "내 화면이 낡았다"는 신호 — 문구를 띄우고 즉시 최신 상태를 받아 다음에 할 수 있는 일을 화면에 세운다. */
async function conflict(msg: string) {
    actionError.value = msg
    // 고른 책도 함께 버린다 — 「그 책이 서재에 없어요」의 그 책이 칩에 남아 있으면 눌러도 계속 같은 404다.
    pickedBook.value = null
    pickedStudyBook.value = null
    await refresh(true)
}

async function handleStart(bookId: number | null) {
    if (starting.value) return
    actionError.value = null
    starting.value = true
    try {
        const res = await fetch('/api/sessions/start', {
            method: 'POST',
            credentials: 'same-origin',
            headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': getCsrfToken() },
            body: JSON.stringify({ bookId }),
        })
        if (res.status === 409) { await conflict('다른 곳에서 이미 측정 중이에요 — 화면을 최신으로 맞췄어요'); return }
        if (!res.ok) { actionError.value = '측정을 시작할 수 없습니다'; return }
        applyTimerState(await res.json() as TimerState)
    } catch {
        actionError.value = '네트워크 오류가 발생했습니다'
    } finally {
        starting.value = false
    }
}

async function handleStop() {
    if (stopping.value) return
    actionError.value = null
    stopping.value = true
    try {
        const res = await fetch('/api/sessions/stop', {
            method: 'POST',
            credentials: 'same-origin',
            headers: { 'X-CSRF-TOKEN': getCsrfToken() },
        })
        if (res.status === 409) { await conflict('진행 중인 측정이 없어요 — 화면을 최신으로 맞췄어요'); return }
        if (!res.ok) { actionError.value = '측정을 종료할 수 없습니다'; return }
        // stop 응답은 타이머 + 잔디(graph) 동봉 — 측정 종료가 잔디가 변하는 순간이라
        // data.graph를 갈아끼워 새로고침 없이 잔디·연속일을 즉시 갱신한다(Vue deep ref가 재렌더 트리거).
        const r = await res.json() as StopResponse
        applyTimerState(r.timer)
        if (data.value) data.value.graph = r.graph
        // 책 없이 시작한 세션이면 "무슨 책?" 태깅 시트를 띄운다(발견 1). 책 골라 시작했으면 안 뜬다.
        if (r.untagged) {
            pendingSessionId.value = r.sessionId
            sheetMode.value = 'tag'
        }
    } catch {
        actionError.value = '네트워크 오류가 발생했습니다'
    } finally {
        stopping.value = false
    }
}

// 공부 원장 — 독서 핸들러와 같은 골격(starting/stopping 재사용). 응답은 StudyState 그대로다.
async function handleStudyStart(bookId: number | null) {
    if (starting.value) return
    actionError.value = null
    starting.value = true
    try {
        const res = await fetch('/api/study/start', {
            method: 'POST',
            credentials: 'same-origin',
            headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': getCsrfToken() },
            body: JSON.stringify({ bookId }),
        })
        if (res.status === 409) { await conflict('다른 곳에서 이미 측정 중이에요 — 화면을 최신으로 맞췄어요'); return }
        // 404 = 다른 탭에서 지운 책을 고른 것. 재조회가 새 books를 실어 와 화면이 스스로 맞는다.
        if (res.status === 404) { await conflict('그 책이 공부 서재에 없어요 — 화면을 최신으로 맞췄어요'); return }
        if (!res.ok) { actionError.value = '측정을 시작할 수 없습니다'; return }
        const s = studyStateOf(await res.json())
        // 사용자가 누른 시작만 전환으로 감싼다(재조회·409·복귀는 즉시) — 두 카드가 한 장으로 합쳐지는 순간이다.
        await withViewTransition(document, () => {
            study.value = s
            // 고르기는 시작 전까지만 유효하다 — 책을 걸고 시작했으면 이제 서버 recentBookId(가장 최근 책을 건 세션의 책)가
            // 칩을 정한다. 남겨 두면 측정 중 교체한 책으로 필기하다 종료하는 순간 필기가 옛 고른 책으로 튄다(리뷰 Minor-3).
            // 책 없이 시작했으면 비우지 않는다 — 비우면 시작 순간 칩이 recent로 바뀌어 필기가 튄다.
            if (bookId !== null) pickedStudyBook.value = null
        })
        if (finePointer()) notesCard.value?.focusEnd()
    } catch {
        actionError.value = '네트워크 오류가 발생했습니다'
    } finally {
        starting.value = false
    }
}

async function handleStudyStop() {
    if (stopping.value) return
    actionError.value = null
    stopping.value = true
    try {
        const res = await fetch('/api/study/stop', {
            method: 'POST',
            credentials: 'same-origin',
            headers: { 'X-CSRF-TOKEN': getCsrfToken() },
        })
        if (res.status === 409) { await conflict('진행 중인 측정이 없어요 — 화면을 최신으로 맞췄어요'); return }
        if (!res.ok) { actionError.value = '측정을 종료할 수 없습니다'; return }
        const s = studyStateOf(await res.json())
        // 한 장이 둘로 갈라지는 전환 — 시트는 전환이 **끝난 뒤** 올린다(스냅숏에 찍혀 뚝 나타나지 않게).
        await withViewTransition(document, () => { study.value = s })
        // 책 없이 끝낸 측정이면 "무슨 책?" 태깅 시트. 서재가 비었으면 띄우지 않는다 —
        // 고를 게 없는데 매번 「담으러 가기」를 들이미는 건 잔소리다(E10).
        if (s.untaggedSessionId !== null && s.books.length > 0) {
            studyPendingSessionId.value = s.untaggedSessionId
            studySheet.value = 'tag'
        }
    } catch {
        actionError.value = '네트워크 오류가 발생했습니다'
    } finally {
        stopping.value = false
    }
}

/**
 * 공부 책별 회당 시간 — 히어로에서 바로 정한다(2026-09-13 컨셉 전환, 하루 목표 대체).
 * seconds는 카드가 minutesToSessionGoal로 환산한 값이고 null은 해제다. 범위 밖(60~21600초)은 서버가 400.
 * 응답은 StudyState 그대로라 통째로 얹는다 — books·activeBook의 새 값이 손잡이·남은 시간으로 곧장 돈다.
 */
async function handleSessionGoal(bookId: number, seconds: number | null) {
    if (savingSessionGoal.value) return
    actionError.value = null
    savingSessionGoal.value = true
    try {
        const res = await fetch(`/api/study/books/${bookId}/session-goal`, {
            method: 'POST',
            credentials: 'same-origin',
            headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': getCsrfToken() },
            body: JSON.stringify({ sessionGoalSeconds: seconds }),
        })
        // 404 = 다른 곳에서 서재에서 뺀 책(start·change와 같은 규칙) — 폼을 닫고 재조회로 화면을 맞춘다.
        if (res.status === 404) { studyCard.value?.closeEdit(); await conflict('그 책이 공부 서재에 없어요 — 화면을 최신으로 맞췄어요'); return }
        // 그 밖의 실패면 폼을 열어 둔 채 둔다 — 사용자가 친 값이 살아 있어야 다시 누를 수 있다.
        if (!res.ok) { actionError.value = '회당 시간을 저장하지 못했어요'; return }
        study.value = studyStateOf(await res.json())
        studyCard.value?.closeEdit()
    } catch {
        actionError.value = '네트워크 오류가 발생했습니다'
    } finally {
        savingSessionGoal.value = false
    }
}

function setMode(next: TimerMode) {
    writeMode(next)
    storedMode.value = next
    modeHint.value = null
}
// 측정 중 토글은 진짜 disabled가 아니다 — 클릭을 받아 왜 못 바꾸는지 말한다.
function onModeBlocked() { modeHint.value = '측정을 끝내면 바꿀 수 있어요' }

async function tagBook(bookId: number) {
    if (tagging.value || pendingSessionId.value === null) return
    tagging.value = true
    try {
        const res = await fetch(`/api/sessions/${pendingSessionId.value}/tag-book`, {
            method: 'POST',
            credentials: 'same-origin',
            headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': getCsrfToken() },
            body: JSON.stringify({ bookId }),
        })
        if (!res.ok) { actionError.value = '책을 연결하지 못했어요'; return }
        closeSheet()
    } catch {
        actionError.value = '네트워크 오류가 발생했습니다'
    } finally {
        tagging.value = false
    }
}

function openStartSheet() { sheetMode.value = 'start' }
function closeSheet() {
    sheetMode.value = null
    pendingSessionId.value = null
}

// 시트에서 책을 고르면 — start 모드면 **고르기만** 한다(칩이 바뀐다), tag 모드면 방금 세션에 태깅.
function onSheetPick(book: { id: number; title: string; coverUrl: string | null }) {
    if (sheetMode.value === 'tag') { tagBook(book.id); return }
    pickedBook.value = { id: book.id, title: book.title, coverUrl: book.coverUrl }
    sheetMode.value = null
}
// start 모드 하단 CTA — 책 없이 바로 시작.
function onSheetBookless() {
    sheetMode.value = null
    handleStart(null)
}
// ── 공부 책 시트 핸들러 ────────────────────────────────────────────────────────
// 태깅·교체는 tagging 플래그를 공유한다(둘 다 「측정 원장에 책을 붙이는」 왕복이고 동시에 열리지 않는다).
// 응답이 StudyState 통째라 recentBookId·books.totalSeconds까지 한 번에 최신이 된다.

/** 종료된 세션에 책을 붙인다 — 세션 id는 stop 응답이 준 것만 쓴다(지어내지 않는다). */
async function studyTagBook(bookId: number) {
    if (tagging.value || studyPendingSessionId.value === null) return
    tagging.value = true
    try {
        const res = await fetch(`/api/study/sessions/${studyPendingSessionId.value}/tag-book`, {
            method: 'POST',
            credentials: 'same-origin',
            headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': getCsrfToken() },
            body: JSON.stringify({ bookId }),
        })
        // 여기엔 404 자동 복구(시트 닫기 + 재조회)를 두지 않는다 — start·change와 갈리는 자리다.
        // 이 시트가 그 세션을 태깅할 **유일한 진입점**이라(세션 id는 stop 응답에만 실린다) 닫으면
        // 미태깅으로 굳는다. 열어 둬야 사용자가 다른 책을 골라 성공할 수 있다.
        if (!res.ok) { actionError.value = '책을 연결하지 못했어요'; return }
        study.value = studyStateOf(await res.json())
        closeStudySheet()
    } catch {
        actionError.value = '네트워크 오류가 발생했습니다'
    } finally {
        tagging.value = false
    }
}

/** 측정 중 교체 — 지금까지 잰 시간이 통째로 새 책으로 옮겨간다(서버 계약). null = 책 없이. */
async function studyChangeBook(bookId: number | null) {
    if (tagging.value) return
    tagging.value = true
    try {
        const res = await fetch('/api/study/active/book', {
            method: 'POST',
            credentials: 'same-origin',
            headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': getCsrfToken() },
            body: JSON.stringify({ bookId }),
        })
        if (res.status === 409) { closeStudySheet(); await conflict('진행 중인 측정이 없어요 — 화면을 최신으로 맞췄어요'); return }
        // 404 = 다른 곳에서 지운 책을 고른 것(start와 같은 규칙, E8). 시트를 닫지 않으면 지워진 그 행이
        // 목록에 남아 눌러도 계속 실패한다 — 재조회가 새 books를 실어 와 화면이 스스로 낫는다.
        if (res.status === 404) { closeStudySheet(); await conflict('그 책이 공부 서재에 없어요 — 화면을 최신으로 맞췄어요'); return }
        if (!res.ok) { actionError.value = '책을 바꾸지 못했어요'; return }
        study.value = studyStateOf(await res.json())
        closeStudySheet()
    } catch {
        actionError.value = '네트워크 오류가 발생했습니다'
    } finally {
        tagging.value = false
    }
}

function openStudySheet(m: 'start' | 'change') { studySheet.value = m }
function closeStudySheet() {
    studySheet.value = null
    studyPendingSessionId.value = null
}
// 시트에서 책을 고르면 — 모드마다 가는 문이 다르다. start는 **아무 문도 두드리지 않는다**(고르기뿐).
function onStudySheetPick(book: StudyBookRow) {
    if (studySheet.value === 'tag') { studyTagBook(book.id); return }
    if (studySheet.value === 'change') { studyChangeBook(book.id); return }
    pickedStudyBook.value = book
    studySheet.value = null
}
// 하단 CTA — start=책 없이 시작 / tag=건너뛰기(닫기만) / change=책 없이 공부하기.
function onStudySheetNone() {
    if (studySheet.value === 'tag') { closeStudySheet(); return }
    if (studySheet.value === 'change') { studyChangeBook(null); return }
    studySheet.value = null
    handleStudyStart(null)
}

// 담기 성공(시트 안 검색담기) — 담은 책을 대시보드 목록에도 낙관적 반영(칩·시트 최신화).
function onSheetAdded(book: { id: number; title: string; status: string }) {
    const opt: BookOption = { id: book.id, title: book.title }
    const list = book.status === 'READING' ? readingBooks
        : book.status === 'FINISHED' ? finishedBooks : wantToReadBooks
    if (!list.value.find(b => b.id === book.id)) list.value = [opt, ...list.value]
}
</script>

<template>
    <div v-if="loading" class="status-line muted">불러오는 중…</div>

    <div v-else-if="fetchError" class="alert alert-error">
        페이지를 불러오지 못했어요. 잠시 후 새로고침 해주세요.
    </div>

    <template v-else-if="data">
        <!-- 발견 2(상단 정리): 헤더 → 타이머 → 잔디 → 바로가기 → 격언(발밑).
             격언(BrandQuote)은 Teleport로 #brand-quote-slot(대시보드 발밑)에 렌더되므로 여기 순서상 위치는 무관. -->
        <DashHeader :login-id="data.loginId" />

        <!-- 모드 스위치 — 페이지 단위(타이머 카드·아래 카드·양옆 바를 한꺼번에 바꾼다)라 카드 밖, 화면 아래 가운데에
             띄운다(2026-09-17 D안). DOM은 헤더 다음 — Tab 순서가 「헤더 → 스위치 → 타이머」로 지금과 같다.
             공부 측정 중(독서등)엔 그리지 않는다 — 밤 화면엔 합쳐진 카드만 낮 종이로 남긴다(2026-09-15 결정 5·6),
             잠긴 스위치의 설명은 곁의 「측정 종료」가 한다. 독서 측정 중엔 잠긴 채 남아 이유를 말한다. -->
        <ModeToggle v-if="!studyFocus" :mode="mode" :locked="toggleLocked" :hint="modeHint" @change="setMode" @blocked="onModeBlocked" />

        <WelcomeBanner v-if="showWelcome" :nickname="data.nickname" @close="showWelcome = false" />

        <EmailVerifyBanner v-if="!data.emailVerified" />

        <div v-if="actionError" class="alert alert-error">{{ actionError }}</div>

        <template v-if="mode === 'reading'">
        <TimerCard
            :remaining-seconds="remainingSeconds"
            :carried-debt-seconds="carriedDebtSeconds"
            :today-goal-seconds="todayGoalSeconds"
            :today-read-seconds="todayReadSeconds"
            :carryover="carryover"
            :streak="data.graph.currentStreak"
            :has-active-session="hasActiveSession"
            :active-started-at="activeStartedAt"
            :active-book-title="activeBookTitle"
            :active-book-total-seconds="activeBookTotalSeconds"
            :reading-books="readingBooks"
            :finished-books="finishedBooks"
            :want-to-read-books="wantToReadBooks"
            :recent-book-id="recentBookId"
            :picked-book="pickedBook"
            :starting="starting"
            :stopping="stopping"
            @start="handleStart"
            @stop="handleStop"
            @open-sheet="openStartSheet"
        />

        <!-- 잔디가 있던 자리(2026-09-07) — 넓힌 폭에서 1년치 격자가 늘어져 걷었다. 기록은 /history와
             /study/history에 그대로 있고, 홈은 「오늘 쓰는 자리」가 된다.
             (주의) 2026-08-16 재설계 §D5-1의 「대시보드에 여백 대체 진입을 만들지 않는다」를 여기서 뒤집는다 —
             그때는 타임라인 스트립을 없애는 맥락이었고, 지금은 잔디가 비운 자리를 「지금 그 책 하나」로
             채우는 것이다(진입은 여전히 책 한 권 단위다). -->
        <MarginCard :login-id="data.loginId" :book="marginBook"
                    :streak="data.graph.currentStreak" @open-sheet="openStartSheet" />
        </template>

        <!-- 공부 = 타이머 + 필기. 측정 중엔 한 장(.is-merged)이 되고, 이 스택만 독서등 밑 낮 종이(.lamp-page)로 남는다
             (설계 2026-09-15-study-focus-lamp 결정 5·7 — 시트·배너·명언은 스택 밖이라 밤 팔레트). -->
        <div v-else class="focus-stack lamp-page" :class="{ 'is-merged': studyFocus }">
            <StudyTimerCard
                ref="studyCard"
                :today-seconds="study.todaySeconds"
                :has-active-session="study.hasActiveSession"
                :active-started-at="study.activeStartedAt"
                :books="study.books"
                :recent-book-id="study.recentBookId"
                :picked-book="pickedStudyBook"
                :active-book="study.activeBook"
                :starting="starting"
                :stopping="stopping"
                :saving-session-goal="savingSessionGoal"
                :changing="tagging"
                @start="handleStudyStart"
                @stop="handleStudyStop"
                @set-session-goal="handleSessionGoal"
                @open-sheet="openStudySheet('start')"
                @change-book="openStudySheet('change')"
            />
            <StudyNotesCard ref="notesCard" :books="study.books" :default-book-id="notesBookId" :initial-note-id="initialNoteId" />
        </div>


        <BrandQuote :quotes="data.quotes" />

        <!-- 통합 책 시트(발견 1, §6.5) — 'start'=측정 전 고르기, 'tag'=종료 후 태깅. 같은 시트를 모드로 겸한다. -->
        <BookPickSheet
            v-if="sheetMode"
            :mode="sheetMode"
            :reading-books="readingBooks"
            :finished-books="finishedBooks"
            :want-to-read-books="wantToReadBooks"
            :pending="sheetMode === 'tag' ? tagging : starting"
            @pick="onSheetPick"
            @bookless="onSheetBookless"
            @skip="closeSheet"
            @close="closeSheet"
            @added="onSheetAdded"
        />

        <!-- 공부 책 시트 — 목록만(검색·fetch 0). 데이터는 study.books가 이미 들고 있다. -->
        <StudyBookSheet
            v-if="studySheet"
            :mode="studySheet"
            :books="study.books"
            :current-book-id="study.activeBook?.id ?? null"
            :pending="studySheet === 'start' ? starting : tagging"
            @pick="onStudySheetPick"
            @none="onStudySheetNone"
            @close="closeStudySheet"
        />
    </template>
</template>
