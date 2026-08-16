<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import ReportModal from '../ReportModal.vue'
import StoryComposer from './StoryComposer.vue'
import { coverInitial, hasCover } from '../../profile/format'
import { excerptForReport, formatStoryAge, type MarginEntry, type MarginResponse } from './storyFeed'
import { deleteStory, fetchMargin } from './storyApi'

// 여백 패널 — 책 하나에 쌓인 글 목록 (2026-08-16 책 귀속 재설계). 옛 풀스크린 뷰어(진행바·자동 넘김·
// 열람 기록)를 대체한다. 진입은 책방 리스트 행의 「여백」 손잡이 하나뿐.
// 오버레이 z-150(도감 50 < 여백 패널 150 < 신고 모달 200 — 옛 뷰어의 z 층 계승, 새 값 발명 금지).
// 노출 권한(차단·IDOR·PRIVATE·비팔로워)은 전부 서버 판정 — 여기선 self·following·entries를 표시로 옮긴다.
const props = defineProps<{ loginId: string; bookId: number }>()
const emit = defineEmits<{
    (e: 'close'): void
    (e: 'changed'): void   // 작성·삭제로 발광이 달라짐 — 부모가 책 목록 재조회
}>()

const margin = ref<MarginResponse | null>(null)
const failed = ref(false)
const composerOpen = ref(false)
const reportEntry = ref<MarginEntry | null>(null)
const busy = ref(false)

async function load() {
    failed.value = false
    const data = await fetchMargin(props.loginId, props.bookId)
    if (data) margin.value = data
    else failed.value = true
}

async function remove(entry: MarginEntry) {
    if (busy.value) return
    if (!confirm('이 글을 지울까요?')) return
    busy.value = true
    try {
        if (!(await deleteStory(entry.id))) return
        await load() // 서버가 준 목록이 진실 — 지운 카드를 손으로 빼지 않는다
        emit('changed')
    } finally {
        busy.value = false
    }
}

async function onCreated() {
    await load()
    emit('changed')
}

function onKeydown(e: KeyboardEvent) {
    // 위에 뜬 오버레이(신고·작성)가 Esc의 주인 — 아래 패널이 가로채면 둘이 한꺼번에 닫힌다
    if (reportEntry.value || composerOpen.value) return
    if (e.key === 'Escape') emit('close')
}

onMounted(() => {
    document.addEventListener('keydown', onKeydown)
    load()
})
onUnmounted(() => document.removeEventListener('keydown', onKeydown))
</script>

<template>
    <div class="margin-overlay" @click.self="emit('close')">
        <div class="margin-panel" role="dialog" aria-modal="true" aria-labelledby="margin-book-title">

            <div class="margin-head">
                <template v-if="margin">
                    <img v-if="hasCover(margin.book.coverUrl)" class="shop-cover margin-book-cover"
                         :src="margin.book.coverUrl!" alt="" referrerpolicy="no-referrer">
                    <div v-else class="shop-cover shop-cover-ph margin-book-cover" aria-hidden="true">
                        {{ coverInitial(margin.book.title) }}
                    </div>
                    <div class="margin-book-meta">
                        <span id="margin-book-title" class="margin-book-title">{{ margin.book.title }}</span>
                        <span class="margin-book-sub">
                            {{ [margin.book.author, margin.ownerNickname + ' @' + loginId].filter(Boolean).join(' · ') }}
                        </span>
                    </div>
                </template>
                <span v-else id="margin-book-title" class="margin-book-title">여백</span>
                <button type="button" class="margin-close" aria-label="닫기" @click="emit('close')">✕</button>
            </div>

            <template v-if="margin">
                <p class="margin-count">여백에 남긴 글 {{ margin.entries.length }}</p>

                <!-- 작성 진입은 목록 위 — 글이 쌓일수록 아래로 밀리면 내 책에서 쓰기가 점점 멀어진다 -->
                <button v-if="margin.self" type="button" class="dash-btn-fill margin-compose"
                        @click="composerOpen = true">여백에 글 남기기</button>

                <p v-if="margin.entries.length === 0" class="margin-empty">
                    <template v-if="margin.self">아직 남긴 글이 없어요. 읽다가 마음에 걸린 문장을 남겨 보세요.</template>
                    <template v-else-if="margin.following">아직 남긴 글이 없어요.</template>
                    <!-- 서버가 비팔로워에겐 빈 배열을 준다 — 글이 있는지 없는지도 여기서 말하지 않는다 -->
                    <template v-else>팔로우하면 이 책의 여백에 남긴 글을 볼 수 있어요.</template>
                </p>

                <ul v-else class="margin-cards">
                    <li v-for="e in margin.entries" :key="e.id" class="margin-card"
                        :class="'story-bg-' + (e.bgCode ?? 'paper')">
                        <p class="margin-card-text">{{ e.text }}</p>
                        <div class="margin-card-foot">
                            <span class="margin-card-age">{{ formatStoryAge(e.createdAt, Date.now()) }}</span>
                            <button v-if="margin.self" type="button" class="margin-card-btn margin-card-delete"
                                    :disabled="busy" @click="remove(e)">지우기</button>
                            <button v-else type="button" class="margin-card-btn margin-card-report"
                                    @click="reportEntry = e">신고</button>
                        </div>
                    </li>
                </ul>
            </template>

            <p v-else-if="failed" class="margin-empty">여백을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.</p>
            <p v-else class="margin-empty">불러오는 중…</p>
        </div>

        <!-- 작성 모달 — 진입점이 이미 그 책이라 책 선택이 없다 -->
        <StoryComposer v-if="composerOpen && margin" :book-id="margin.book.id" :book-title="margin.book.title"
                       @close="composerOpen = false" @created="onCreated" />

        <!-- 신고 모달 (z-200 — 패널 위) — detail에 원문 발췌 자동 첨부(§13.5) -->
        <ReportModal v-if="reportEntry" :login-id="loginId"
                     :detail-prefix="excerptForReport(reportEntry.id, reportEntry.text)"
                     @close="reportEntry = null" />
    </div>
</template>
