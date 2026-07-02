<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import AuthorAvatar from '../AuthorAvatar.vue'
import ReportModal from '../ReportModal.vue'
import {
    STORY_DURATION_MS, excerptForReport, firstUnviewedIndex, formatStoryAge, progressStates,
    type AuthorStories, type StoryViewerEntry,
} from './storyFeed'
import { deleteStory, fetchStoryViewers, markStoryViewed } from './storyApi'

// 풀스크린 스토리 뷰어 (sns-design §13.7) — 장당 ~5초 진행바·자동 넘김·좌/우 탭·Esc/배경 탭 닫기.
// 오버레이 z-150(도감 50 < 뷰어 150 < 신고 모달 200). Esc는 document 리스너(포커스 무관 — 도감 전례).
// 카드 표시 시점에 열람 POST(서버 멱등) — 본인 카드는 서버가 no-op이므로 클라이언트에서 생략.
const props = defineProps<{
    groups: AuthorStories[]
    startGroup: number
    myLoginId: string
}>()
const emit = defineEmits<{
    (e: 'close'): void
    (e: 'changed'): void   // 삭제로 피드가 달라짐 — 부모가 새로고침
}>()

const g = ref(props.startGroup)
const i = ref(firstUnviewedIndex(props.groups[props.startGroup]?.stories ?? []))
const progress = ref(0) // 현재 카드 진행 0..1

const group = computed(() => props.groups[g.value])
const card = computed(() => group.value?.stories[i.value])
const mine = computed(() => group.value?.loginId === props.myLoginId)
const segments = computed(() => progressStates(group.value?.stories.length ?? 0, i.value))

const viewersOpen = ref(false)
const viewers = ref<StoryViewerEntry[]>([])
const reportOpen = ref(false)
const deleting = ref(false)
// 시트·모달·삭제 왕복 동안 자동 넘김 일시정지 — 삭제 await 중 tick이 g/i를 옮기면
// 엉뚱한 카드를 로컬에서 지우는 경합이 된다(리뷰 파인딩)
const paused = computed(() => viewersOpen.value || reportOpen.value || deleting.value)

const TICK_MS = 50
let timerId: ReturnType<typeof setInterval> | undefined

function tick() {
    if (paused.value || !card.value) return
    progress.value += TICK_MS / STORY_DURATION_MS
    if (progress.value >= 1) next()
}

/** 카드가 화면에 등장 — 진행 초기화 + 열람 기록(타인 카드만, 멱등) + 링 강조 로컬 동기화. */
function onCardShown() {
    progress.value = 0
    viewersOpen.value = false
    const c = card.value
    if (!c || mine.value) return
    if (!c.viewed) {
        markStoryViewed(c.id)
        c.viewed = true
        const grp = group.value
        if (grp) grp.allViewed = grp.stories.every(s => s.viewed)
    }
}

function next() {
    const grp = group.value
    if (!grp) return emit('close')
    if (i.value < grp.stories.length - 1) {
        i.value++
    } else if (g.value < props.groups.length - 1) {
        g.value++
        i.value = firstUnviewedIndex(props.groups[g.value].stories)
    } else {
        emit('close')
    }
}

function prev() {
    if (i.value > 0) {
        i.value--
    } else if (g.value > 0) {
        g.value--
        i.value = Math.max(0, props.groups[g.value].stories.length - 1)
    } else {
        progress.value = 0 // 첫 카드에서 이전 — 처음부터 다시
    }
}

async function openViewers() {
    const c = card.value
    if (!c) return
    viewers.value = [] // 이전 카드 목록 잔존 방지
    viewersOpen.value = true
    const list = await fetchStoryViewers(c.id)
    // 왕복 중 카드가 바뀌었거나 시트가 닫혔으면 늦은 응답은 버린다(경합 가드)
    if (viewersOpen.value && card.value?.id === c.id) {
        viewers.value = list
    }
}

async function removeCard() {
    const c = card.value
    const grp = group.value
    if (!c || !grp || deleting.value) return
    deleting.value = true // 삭제 왕복 동안 자동 넘김 정지 (paused)
    try {
        if (!confirm('이 스토리를 삭제할까요?')) return
        if (!(await deleteStory(c.id))) return
        const idx = grp.stories.indexOf(c) // i가 아니라 카드 자신을 재탐색 — 인덱스 드리프트 방어
        if (idx >= 0) grp.stories.splice(idx, 1)
        emit('changed')
        if (grp.stories.length === 0) {
            emit('close')
            return
        }
        if (i.value >= grp.stories.length) i.value = grp.stories.length - 1
        onCardShown()
    } finally {
        deleting.value = false
    }
}

/** 시트가 열려 있으면 탭은 카드 이동이 아니라 시트 닫기 — 바텀시트 바깥 탭 관례. */
function closeSheetIfOpen(): boolean {
    if (viewersOpen.value) {
        viewersOpen.value = false
        return true
    }
    return false
}

function onTapPrev() {
    if (closeSheetIfOpen()) return
    prev()
}

function onTapNext() {
    if (closeSheetIfOpen()) return
    next()
}

function onKeydown(e: KeyboardEvent) {
    if (reportOpen.value) return // 신고 모달이 위에 떠 있음 — Esc는 모달 몫
    if (e.key === 'Escape') {
        if (closeSheetIfOpen()) return // 시트 먼저, 뷰어는 그 다음
        emit('close')
    }
    if (e.key === 'ArrowRight') next()
    if (e.key === 'ArrowLeft') prev()
}

watch([g, i], onCardShown, { immediate: true })

onMounted(() => {
    document.addEventListener('keydown', onKeydown)
    timerId = setInterval(tick, TICK_MS)
})
onUnmounted(() => {
    document.removeEventListener('keydown', onKeydown)
    if (timerId) clearInterval(timerId)
})
</script>

<template>
    <div class="story-viewer-overlay" @click.self="emit('close')">
        <div v-if="group && card" class="story-viewer-panel" :class="'story-bg-' + (card.bgCode ?? 'paper')">

            <!-- 진행바 — 지난 카드 done / 현재 active(폭 = 진행률) / 남은 카드 todo -->
            <div class="story-progress" aria-hidden="true">
                <span v-for="(state, idx) in segments" :key="idx" class="story-progress-seg" :class="state">
                    <span v-if="state === 'active'" class="story-progress-fill"
                          :style="{ width: (progress * 100) + '%' }"></span>
                </span>
            </div>

            <!-- 작성자 헤더 -->
            <div class="story-viewer-head">
                <span class="dash-header-avatar story-viewer-avatar" aria-hidden="true">
                    <AuthorAvatar :code="group.profileCharacterCode" :fallback-text="group.loginId" />
                </span>
                <span class="story-viewer-author">
                    <span class="story-viewer-nickname">{{ group.nickname }}</span>
                    <span class="story-viewer-age">{{ formatStoryAge(card.createdAt, Date.now()) }}</span>
                </span>
                <button type="button" class="story-viewer-close" aria-label="닫기" @click="emit('close')">✕</button>
            </div>

            <!-- 문장 본문 -->
            <div class="story-viewer-text">{{ card.text }}</div>

            <!-- 책 라벨 — 서버가 표시 시점 재검사를 통과시킨 공개 책만 내려줌(§13.2). 링크는 후속(§13.1). -->
            <div v-if="card.bookTitle" class="story-viewer-book">
                <img v-if="card.bookCoverUrl" :src="card.bookCoverUrl" alt="" class="story-viewer-book-cover">
                <span>《{{ card.bookTitle }}》</span>
            </div>

            <!-- 좌/우 탭 존 — 가장자리 30%만(본문 중앙은 긴 문장 스크롤 영역으로 비워둠). 시트 열림 중엔 시트 닫기. -->
            <button type="button" class="story-tap story-tap-left" aria-label="이전 스토리" @click="onTapPrev"></button>
            <button type="button" class="story-tap story-tap-right" aria-label="다음 스토리" @click="onTapNext"></button>

            <!-- 하단 바 — 본인: 열람자·삭제 / 타인: 신고 -->
            <div class="story-viewer-foot">
                <template v-if="mine">
                    <button type="button" class="story-foot-btn" @click="openViewers">본 사람 보기</button>
                    <button type="button" class="story-foot-btn story-foot-danger" @click="removeCard">삭제</button>
                </template>
                <template v-else>
                    <span></span>
                    <button type="button" class="story-foot-btn" @click="reportOpen = true">신고</button>
                </template>
            </div>

            <!-- 열람자 목록 시트 (본인 전용 — 서버도 작성자만 허용) -->
            <div v-if="viewersOpen" class="story-viewers-sheet">
                <div class="story-viewers-head">
                    <strong>본 사람 {{ viewers.length }}</strong>
                    <button type="button" class="story-viewer-close" aria-label="닫기"
                            @click="viewersOpen = false">✕</button>
                </div>
                <p v-if="viewers.length === 0" class="story-viewers-empty">아직 본 사람이 없어요.</p>
                <ul v-else class="story-viewers-list">
                    <li v-for="v in viewers" :key="v.loginId" class="story-viewers-row">
                        <span class="dash-header-avatar story-viewers-avatar" aria-hidden="true">
                            <AuthorAvatar :code="v.profileCharacterCode" :fallback-text="v.loginId" />
                        </span>
                        <a class="story-viewers-name" :href="'/u/' + v.loginId">{{ v.nickname }}
                            <span class="story-viewers-handle">@{{ v.loginId }}</span></a>
                        <span class="story-viewers-age">{{ formatStoryAge(v.viewedAt, Date.now()) }}</span>
                    </li>
                </ul>
            </div>
        </div>

        <!-- 신고 모달 (z-200 — 뷰어 위) — detail에 스토리 원문 발췌 자동 첨부(§13.5) -->
        <ReportModal v-if="reportOpen && card && group" :login-id="group.loginId"
                     :detail-prefix="excerptForReport(card.id, card.text)"
                     @close="reportOpen = false" />
    </div>
</template>
