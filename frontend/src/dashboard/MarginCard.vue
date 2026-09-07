<script setup lang="ts">
import { ref, watch } from 'vue'

import type { BookOption } from './types'
import { fetchMargin } from '../shared/story/storyApi'
import { formatStoryAge, type MarginEntry } from '../shared/story/storyFeed'
import StoryComposer from '../shared/story/StoryComposer.vue'

/**
 * 홈 여백 카드 — 잔디가 있던 자리(2026-09-07).
 *
 * <p>잔디를 걷어낸 이유는 넓어진 폭에서 1년치 격자가 늘어져서였다. 그 자리를 <b>빈 입력칸</b>으로
 * 메우면 안 쓰는 날엔 도로 허전해지므로, 잔디가 하던 「쌓인 것이 보인다」를 <b>내가 쓴 글</b>이 잇는다.
 *
 * <p>범위는 「지금 그 책」 하나다 — 여백은 책에 귀속되고, 사람축으로 내 글을 모아 주는 API가 없다.
 * 타이머 칩과 같은 책을 봐야 하므로 어느 책인지는 이 컴포넌트가 고르지 않고 부모가 내려 준다
 * (`defaultBookOf` 한 곳). 시트를 여는 것도 부모다 — 카드는 「책이 필요하다」고만 말한다.
 */
const PREVIEW_LIMIT = 2

const props = defineProps<{ loginId: string; book: BookOption | null; streak: number }>()
const emit = defineEmits<{ (e: 'open-sheet'): void }>()

/** null = 아직 못 받음(불러오는 중). 빈 배열은 「0건」이고, 실패는 failed로 따로 든다 — 셋을 뭉개면
 *  못 불러온 날 사용자가 자기 글이 사라졌다고 읽는다. */
const entries = ref<MarginEntry[] | null>(null)
const failed = ref(false)
const composerOpen = ref(false)

/**
 * 이 카드가 지금 기다리는 요청의 세대. 책을 바꾸면 올라가고, **늦게 도착한 옛 응답은 버린다** —
 * 「바꿀 때 비운다」만으로는 못 막는다(늦게 온 A가 B의 빈 칸을 다시 채운다).
 */
let generation = 0

async function load(): Promise<void> {
    const mine = ++generation
    entries.value = null
    failed.value = false
    if (!props.book) return
    // fetch 자체가 거부되는 길(오프라인·DNS)이 fetchMargin의 null 수렴 밖이다 — 안 잡으면
    // failed가 false인 채 「불러오는 중」에 영원히 갇힌다(실패 상태가 있는데 안 닿는다).
    let res: Awaited<ReturnType<typeof fetchMargin>>
    try {
        res = await fetchMargin(props.loginId, props.book.id)
    } catch {
        if (mine === generation) failed.value = true
        return
    }
    if (mine !== generation) return
    if (res === null) {
        failed.value = true
        return
    }
    entries.value = res.entries
}

// 책이 바뀌면(측정 시작·시트에서 고르기) 그 책의 여백으로 갈아탄다.
watch(() => props.book?.id ?? null, () => void load(), { immediate: true })

function onCreated(): void {
    composerOpen.value = false
    void load()
}
</script>

<template>
    <section class="dash-card dash-margin-card">
        <div class="dash-card-head">
            <div class="dash-card-head-left">
                <span class="dash-pill">여백</span>
                <!-- 연속일 칩 — 잔디 카드에만 있던 것을 그대로 옮긴다. 타이머 카드의 「연속 N일째」는
                     목표 달성 패널 전용이라 평소엔 안 보인다(여길 걷으면 홈에서 연속일이 사라진다). -->
                <span v-if="streak > 0" class="dash-streak-chip">
                    <svg width="15" height="15" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                        <path d="M5 19c0-8 6-13 14-13 0 8-5 14-13 14-1 0-1-.6-1-1z" />
                    </svg>
                    <strong>{{ streak }}</strong>일 연속 독서
                </span>
                <span v-if="book" class="dash-card-sub">《{{ book.title }}》</span>
            </div>
            <!-- 잔디 카드가 들고 있던 기록 진입점 — 카드 내용이 바뀌어도 이 링크는 자리를 지킨다 -->
            <a class="dash-card-link" href="/history">전체 기록 →</a>
        </div>

        <template v-if="!book">
            <p class="dash-card-note">책을 고르면 그 책의 여백에 글을 남길 수 있어요.</p>
            <button type="button" class="dash-btn-fill" @click="emit('open-sheet')">책 고르기</button>
        </template>

        <template v-else>
            <p v-if="failed" class="dash-card-note">여백을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.</p>
            <p v-else-if="entries === null" class="dash-card-note">불러오는 중…</p>
            <p v-else-if="entries.length === 0" class="dash-card-note">
                아직 이 책엔 남긴 글이 없어요. 읽다가 마음에 걸린 문장을 남겨 보세요.
            </p>

            <!-- 미리보기 2건 — 다 쏟으면 잔디를 걷어낸 이유(길게 늘어짐)를 그대로 되부른다.
                 카드 꼴은 책방 여백 패널(.margin-card)과 같은 클래스를 쓴다: 같은 글이 두 화면에서
                 다르게 보이면 그게 더 어색하다. 좋아요·지우기·신고는 여기 없다 — 홈은 읽는 자리다. -->
            <ul v-else class="dash-margin-cards">
                <li v-for="e in entries.slice(0, PREVIEW_LIMIT)" :key="e.id"
                    class="margin-card" :class="'story-bg-' + (e.bgCode ?? 'paper')">
                    <blockquote v-if="e.quote" class="margin-card-quote">{{ e.quote }}</blockquote>
                    <p class="margin-card-text">{{ e.text }}</p>
                    <div class="margin-card-foot">
                        <span class="margin-card-age">{{ formatStoryAge(e.createdAt, Date.now()) }}</span>
                    </div>
                </li>
            </ul>

            <button type="button" class="dash-btn-fill" @click="composerOpen = true">
                《{{ book.title }}》에 여백 남기기
            </button>
        </template>

        <!-- 작성 모달은 책방과 같은 것을 그대로 쓴다(진입점이 이미 그 책이라 책 선택이 없다) -->
        <StoryComposer v-if="composerOpen && book" :book-id="book.id" :book-title="book.title"
                       @close="composerOpen = false" @created="onCreated" />
    </section>
</template>
