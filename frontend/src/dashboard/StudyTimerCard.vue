<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { useReadingTimer } from './useReadingTimer'
import { fmtMSS, goalLabel } from './timerProgress'
import { studyProgress, minutesToGoalSeconds } from './studyProgress'
import { initialOf, coverColor } from '../books/pure'
import type { StudyBookRow } from '../study/api'

const props = withDefaults(defineProps<{
    /** 오늘 공부한 초(완료 세션 합) — 측정 중 몫은 elapsed로 얹는다(독서와 같은 분업). */
    todaySeconds: number
    hasActiveSession: boolean
    activeStartedAt: string | null
    /** 하루 목표 초. 0 = 「목표 없음」이라는 정당한 상태 — 게이지 대신 「하루 목표 정하기」를 띄운다. */
    goalSeconds?: number
    starting?: boolean
    stopping?: boolean
    savingGoal?: boolean
    /** 내 공부 서재 — 기본 칩이 여기서 골라진다. 빈 서재가 기본값(옛 서버·옛 픽스처). */
    books?: StudyBookRow[]
    /** 마지막으로 책을 걸고 잰 책 — 기본 칩 1순위. */
    recentBookId?: number | null
    /** 측정 중인 책. null이면 「책 없이」(빈칸이 아니라 상태다). */
    activeBook?: StudyBookRow | null
    /** 책 교체 왕복 중 — 「책 바꾸기」를 잠근다. */
    changing?: boolean
}>(), { goalSeconds: 0, books: () => [], recentBookId: null, activeBook: null })

const emit = defineEmits<{
    start: [bookId: number | null]; stop: []; setGoal: [seconds: number]
    openSheet: []; changeBook: []
}>()

// 기본 책 = 최근 걸고 잰 책 → 없으면 첫 책(독서 BookPickForm과 같은 규칙). 서재가 비면 null.
const defaultBook = computed<StudyBookRow | null>(() =>
    props.books.find(b => b.id === props.recentBookId) ?? props.books[0] ?? null)

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

// 게이지는 히어로 숫자와 같은 분자를 본다 — 완료 합 + 측정 중 경과.
const progress = computed(() => studyProgress(props.goalSeconds, props.todaySeconds + elapsed.value))

// 목표 인라인 편집 — 설정 페이지로 보내지 않는다(서버 0줄, 미니앱과 같은 자리).
const editing = ref(false)
const goalMinutes = ref(0)
function openEdit() {
    goalMinutes.value = Math.round(props.goalSeconds / 60)
    editing.value = true
}
function submitGoal() {
    emit('setGoal', minutesToGoalSeconds(goalMinutes.value))
    // 여기서 닫지 않는다 — 부모의 왕복(실측 ≈128ms)이 끝나기 전에 닫으면 ① savingGoal UI가
    // 한 번도 렌더되지 않고 ② 400으로 실패했을 때 사용자가 친 값이 사라진다. 닫는 건 부모다.
}
/** 부모(DashboardApp)가 저장 성공을 확인한 뒤 부른다. */
function closeEdit() { editing.value = false }
defineExpose({ closeEdit })
</script>

<template>
    <section class="dash-card dash-timer-hero is-study">
        <div class="dash-timer-left">
            <div class="dash-timer-head">
                <span class="dash-pill">오늘 공부한 시간</span>
                <slot name="mode" />
            </div>
            <div class="dash-timer-num">{{ fmtMSS(todaySeconds + elapsed) }}</div>

            <div v-if="goalSeconds > 0" class="dash-progress-wrap">
                <div class="dash-progress-track">
                    <div class="dash-progress-fill" :style="{ width: progress.pctStr }"></div>
                </div>
                <div class="dash-progress-meta">
                    <span>하루 목표 {{ goalLabel(goalSeconds) }}
                        <button type="button" class="dash-goal-change" @click="openEdit">변경</button></span>
                    <span class="dash-progress-pct">
                        {{ progress.achieved ? '목표 달성' : `목표까지 ${fmtMSS(progress.remaining)}` }}
                    </span>
                </div>
            </div>
            <button v-else type="button" class="dash-btn-link dash-bookless dash-goal-set" @click="openEdit">
                하루 목표 정하기
            </button>

            <form v-if="editing" class="dash-goal-edit" @submit.prevent="submitGoal">
                <label>하루 목표
                    <!-- step은 스피너 간격이 아니라 **유효성 제약**이다 — step="5"면 7·23분이 stepMismatch가
                         되어 네이티브 검증이 submit을 막는다(실브라우저 실측 2026-09-05). 조용한 건
                         앱 쪽이고(요청 0건) 사용자에겐 크롬이 검증 버블을 띄운다.
                         step="1" = 정수 분만 받는다는 **의도**다 — 7.5를 조용히 7로 내리느니 크롬이
                         「가장 근접한 유효 값 2개는 7 및 8입니다」를 보여주는 편이 낫다.
                         max는 하루(1440분) — 없으면 999999999분이 200으로 통과해 「하루 목표
                         16666666시간 39분」이 렌더된다(리뷰 실측). -->
                    <input type="number" min="0" max="1440" step="1" v-model.number="goalMinutes"
                           aria-label="하루 목표(분)"> 분
                </label>
                <button type="submit" class="dash-btn-fill" :disabled="savingGoal">
                    {{ savingGoal ? '저장하는 중…' : '저장' }}
                </button>
                <!-- 보조 둘은 「책 없이 시작」과 같은 조용한 링크 관용구(.dash-bookless) — 그냥 .dash-btn-link면
                     전역 button의 연필 테두리·18px를 그대로 받아 저장과 같은 무게로 선다(실브라우저 실측). -->
                <button type="button" class="dash-btn-link dash-bookless" @click="editing = false">취소</button>
                <!-- 미니앱 showClearGoal — 공부이고 지울 목표가 있을 때만. 독서엔 이 문이 없다(0이 원장을 깨는 값). -->
                <button v-if="goalSeconds > 0" type="button" class="dash-btn-link dash-bookless"
                        @click="emit('setGoal', 0); editing = false">목표 없이 지내기</button>
            </form>
        </div>

        <div class="dash-timer-right">
            <Transition name="panel-fade" mode="out-in">
                <div v-if="hasActiveSession" key="measuring" class="dash-state-panel">
                    <div class="dash-state-row">
                        <span class="dash-pill dash-pill-pulse"><span class="dash-pulse-dot"></span>측정 중</span>
                        <span class="dash-session-time">{{ fmtMSS(elapsed) }}</span>
                    </div>
                    <div class="dash-divider"></div>
                    <div class="dash-kv">
                        <span class="dash-kv-k">지금 공부하는 책</span>
                        <span class="dash-kv-v">{{ activeBook?.title ?? '책 없이' }}</span>
                    </div>
                    <button type="button" class="dash-btn-outline" :disabled="stopping" @click="emit('stop')">
                        {{ stopping ? '종료하는 중…' : '측정 종료' }}
                    </button>
                    <!-- 잰 시간은 통째로 새 책에 옮겨간다(서버 계약) — 측정을 끊지 않고 바꾼다. -->
                    <button type="button" class="dash-btn-link dash-bookless" :disabled="changing" @click="emit('changeBook')">
                        책 바꾸기
                    </button>
                </div>

                <div v-else key="idle" class="dash-state-panel">
                    <!-- BookPickForm(독서)을 재사용하지 않는다: 문구가 「읽어볼까요」라 prop을 더해야 하고,
                         그게 곧 공용 조각 기본값 사각이다. 인라인 15줄이 싸다(설계 §3.3-C3). -->
                    <template v-if="defaultBook">
                        <span class="dash-idle-label">이 책으로 공부할까요?</span>
                        <div class="dash-book-chip">
                            <span class="dash-book-chip-cover" :style="coverStyle(defaultBook)" aria-hidden="true">{{ initialOf(defaultBook.title) }}</span>
                            <span class="dash-book-chip-title">{{ defaultBook.title }}</span>
                            <button type="button" class="dash-book-chip-change" :disabled="starting" @click="emit('openSheet')">바꾸기</button>
                        </div>
                        <button type="button" class="dash-btn-fill" :disabled="starting" @click="emit('start', defaultBook.id)">
                            {{ starting ? '시작하는 중…' : '공부 측정 시작' }}
                        </button>
                        <button type="button" class="dash-btn-link dash-bookless" :disabled="starting" @click="emit('start', null)">책 없이 시작</button>
                    </template>
                    <!-- 서재가 비어도 시작을 막지 않는다 — 담으러 가는 문은 링크 하나로 곁에 둔다. -->
                    <template v-else>
                        <span class="dash-idle-label">지금 공부를 시작할까요?</span>
                        <button type="button" class="dash-btn-fill" :disabled="starting" @click="emit('start', null)">
                            {{ starting ? '시작하는 중…' : '공부 측정 시작' }}
                        </button>
                        <a class="dash-btn-link dash-bookless" href="/study/books">공부 서재에 책 담기</a>
                    </template>
                </div>
            </Transition>
        </div>
    </section>
</template>
