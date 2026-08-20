<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import ReportModal from '../ReportModal.vue'
import UserRow from '../UserRow.vue'
import StoryComposer from './StoryComposer.vue'
import { coverInitial, hasCover } from '../../profile/format'
import type { UserRowData } from '../follow'
import { excerptForReport, formatStoryAge, type MarginEntry, type MarginResponse } from './storyFeed'
import { deleteStory, fetchMargin, fetchStoryLikers, toggleStoryLike } from './storyApi'

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
// 누르는 중인 글 — 왕복이 겹치면 개수가 어긋나므로 한 번에 하나만 받는다.
const likeBusy = ref<number | null>(null)

// 펴진 명단의 글 — 한 번에 하나만 편다(여럿을 펴 두면 문장 사이가 명단으로 밀려 카드가 목록이 된다).
// null이면 아직 받는 중 — 빈 배열(0명)·실패와 구분해야 "아직 아무도"를 먼저 깜빡이거나 실패를
// 「아무도 안 눌렀다」로 둔갑시키지 않는다.
const likersOf = ref<number | null>(null)
const likers = ref<UserRowData[] | null>(null)
const likersFailed = ref(false)

// 명단은 펼 때 받는다 — 여백 한 장에 글이 100개까지 실려서, 목록 응답에 글마다 동봉하면 열지도 않을
// 명단이 한꺼번에 날아온다.
async function toggleLikers(entry: MarginEntry) {
    if (likersOf.value === entry.id) {
        likersOf.value = null
        return
    }
    likersOf.value = entry.id
    likers.value = null
    likersFailed.value = false
    const rows = await fetchStoryLikers(entry.id)
    if (likersOf.value !== entry.id) return // 그 사이 다른 글을 폈다 — 늦게 온 답이 덮지 않게
    if (rows === null) likersFailed.value = true
    else likers.value = rows
}

// 낙관적으로 먼저 칠하고, 서버가 센 값으로 덮고, 실패하면 되돌린다. 서버 값을 그대로 덮는 것이 핵심 —
// 그 사이 남이 누른 것까지 반영된 진짜 개수가 오므로 ±1 추정치가 오래 남지 않는다.
async function toggleLike(entry: MarginEntry) {
    if (likeBusy.value !== null) return
    const before = { likeCount: entry.likeCount, liked: entry.liked }
    entry.likeCount += before.liked ? -1 : 1
    entry.liked = !before.liked
    likeBusy.value = entry.id
    try {
        const state = await toggleStoryLike(entry.id, entry.liked)
        if (state === null) Object.assign(entry, before) // 되돌린다 — 틀린 개수를 남기지 않는다
        else Object.assign(entry, state)
    } finally {
        likeBusy.value = null
    }
}

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
                        <!-- 인용은 主가 아니라 從이다 — 작게·옅게 두고 세로선으로만 가른다(크게 뽑으면
                             카드의 주인공이 남의 문장이 되어 「내 독서 기록」이 명언 카드가 된다).
                             본문이 이미 명조라 서체로는 못 가른다 — 크기·농도·세로선이 유일한 신호. -->
                        <blockquote v-if="e.quote" class="margin-card-quote">{{ e.quote }}</blockquote>
                        <p class="margin-card-text">{{ e.text }}</p>
                        <div class="margin-card-foot">
                            <span class="margin-card-age">{{ formatStoryAge(e.createdAt, Date.now()) }}</span>
                            <!-- 하트는 누르기/취소만 진다. 받은 글은 전부 누를 수 있어(자기 글·내 비공개 책
                                 포함 — 2026-08-20) 화면에 옮길 게이트가 없다. -->
                            <button type="button" class="margin-card-btn margin-card-like"
                                    :class="{ 'is-liked': e.liked }" :aria-pressed="e.liked"
                                    :aria-label="e.liked ? '좋아요 취소' : '좋아요'"
                                    :disabled="likeBusy !== null" @click="toggleLike(e)">
                                <svg class="margin-like-ico" viewBox="0 0 24 24" aria-hidden="true">
                                    <path d="M12 20.6 4.2 12.8a4.9 4.9 0 0 1 6.9-6.9l.9.9.9-.9a4.9 4.9 0 0 1 6.9 6.9Z" />
                                </svg>
                            </button>
                            <button v-if="margin.self" type="button" class="margin-card-btn margin-card-delete"
                                    :disabled="busy" @click="remove(e)">지우기</button>
                            <button v-else type="button" class="margin-card-btn margin-card-report"
                                    @click="reportEntry = e">신고</button>
                        </div>

                        <!-- 개수는 데이터라 언제나 보이고(주인도 남이 눌러 준 걸 안다), 누르면 명단이 편다.
                             0은 줄 자체를 안 만든다 — 빈 상태를 숫자로 박제하면 카드마다 반복된다.
                             푸터가 아니라 아래 줄인 이유: 시각·하트·지우기 옆 넷째 칸은 좁은 폭에서 접힌다. -->
                        <button v-if="e.likeCount > 0" type="button" class="margin-card-likes"
                                :aria-expanded="likersOf === e.id" @click="toggleLikers(e)">
                            좋아요 {{ e.likeCount }}명
                        </button>
                        <ul v-if="likersOf === e.id" class="margin-likers">
                            <li v-if="likersFailed" class="margin-likers-note">명단을 불러오지 못했어요.</li>
                            <li v-else-if="likers === null" class="margin-likers-note">불러오는 중…</li>
                            <!-- 0명은 그 사이 누른 사람이 취소한 경우다 — 개수 줄이 있었으니 여기 닿을 수 있다 -->
                            <li v-else-if="likers.length === 0" class="margin-likers-note">아직 아무도 누르지 않았어요.</li>
                            <UserRow v-for="u in likers ?? []" :key="u.loginId" :user="u" />
                        </ul>
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
