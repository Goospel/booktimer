<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import StoryComposer from './StoryComposer.vue'
import { coverInitial, hasCover } from '../../profile/format'
import { coverStyle } from '../../books/pure'
import { formatStoryAge, type MarginEntry, type MarginResponse } from './storyFeed'
import { deleteStory, fetchMargin } from './storyApi'

// 여백 패널 — 책 하나에 쌓인 글 목록 (2026-08-16 책 귀속 재설계). 옛 풀스크린 뷰어(진행바·자동 넘김·
// 열람 기록)를 대체한다. 진입은 내 책방 · 책장 리스트 행의 「여백」 손잡이 하나뿐.
// 오버레이 z-150(도감 50 < 여백 패널 150 — 옛 뷰어의 z 층 계승, 새 값 발명 금지).
// 여백은 나만 본다(책생글 M1 · 2026-10-30) — 좋아요 · 명단 · 신고 단추를 걷었다(남이 이 패널에 닿는 길이 없다).
const props = defineProps<{ loginId: string; bookId: number }>()
const emit = defineEmits<{
    (e: 'close'): void
    (e: 'changed'): void   // 작성·삭제로 발광이 달라짐 — 부모가 책 목록 재조회
}>()

const margin = ref<MarginResponse | null>(null)
const failed = ref(false)
const composerOpen = ref(false)
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
    // 위에 뜬 오버레이(작성)가 Esc의 주인 — 아래 패널이 가로채면 둘이 한꺼번에 닫힌다
    if (composerOpen.value) return
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
                    <div v-else class="shop-cover shop-cover-ph margin-book-cover" :style="coverStyle(margin.book.title)" aria-hidden="true">
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

                <!-- 남의 여백이 비면 그냥 비었다고 말한다 — 옛 「팔로우하면 볼 수 있어요」는 2026-08-22에
                     걷었다(팔로우가 열람 권한에서 빠져 거짓말이 됐다). 미니앱 Story.tsx와 같은 문구다. -->
                <p v-if="margin.entries.length === 0" class="margin-empty is-blank">
                    <!-- 판화 한 점은 빈 여백에만 — 실패·불러오는 중 분기(아래)엔 없다. 모달은 자기 층이라 책장 머리 판화와 따로 센다. -->
                    <span class="woodcut woodcut-148" aria-hidden="true"></span>
                    <template v-if="margin.self">아직 남긴 글이 없어요. 읽다가 마음에 걸린 문장을 남겨 보세요.</template>
                    <template v-else>아직 남긴 글이 없어요.</template>
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
                            <button v-if="margin.self" type="button" class="margin-card-btn margin-card-delete"
                                    :disabled="busy" @click="remove(e)">지우기</button>
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
    </div>
</template>
