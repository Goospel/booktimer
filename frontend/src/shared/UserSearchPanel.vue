<script setup lang="ts">
// 사용자 검색 + 친구 추천 패널 — /search 페이지와 내 책방(self) 상단이 공유하는 단일 출처.
// 마운트 엘리먼트 dataset에 의존하지 않게 prop만 받는다(책방 섬에서도 재사용 가능).
// 팔로우 토글·CSRF는 shared/follow 재사용(SearchApp 인라인과 동일 동작 — 기존 테스트 보존).
import { ref, onMounted } from 'vue';
import { toggleFollow as doToggleFollow, type UserRowData, type RecommendedUser } from './follow';

interface SearchResponse {
    q: string | null;
    results: UserRowData[];
    recommendations: RecommendedUser[];
    myLoginId: string;
    rateLimited: boolean;
}

const props = withDefaults(defineProps<{
    initialQ?: string;
    heading?: string;
    placeholder?: string;
}>(), {
    initialQ: '',
    heading: '아이디로 찾기',
    placeholder: '아이디 검색 (2글자 이상)',
});

const searchQuery = ref(props.initialQ);
const results = ref<UserRowData[]>([]);
const recommendations = ref<RecommendedUser[]>([]);
const rateLimited = ref(false);
const loading = ref(false);
const searched = ref(false);

async function fetchSearch(q: string) {
    loading.value = true;
    try {
        const url = q.trim() ? `/api/search?q=${encodeURIComponent(q.trim())}` : '/api/search';
        const res = await fetch(url, { credentials: 'same-origin' });
        if (!res.ok) return;
        const data: SearchResponse = await res.json();
        results.value = data.results;
        recommendations.value = data.recommendations;
        rateLimited.value = data.rateLimited;
        searched.value = true;
    } finally {
        loading.value = false;
    }
}

onMounted(() => {
    fetchSearch(searchQuery.value);
});

function onSearch() {
    fetchSearch(searchQuery.value);
}
</script>

<template>
    <!-- 아이디(@핸들) 검색 -->
    <section class="card">
        <h2>{{ heading }}</h2>
        <form class="book-search-form" @submit.prevent="onSearch">
            <input
                v-model="searchQuery"
                type="text"
                :placeholder="placeholder"
                minlength="2"
            >
            <button type="submit" class="btn-primary">검색</button>
        </form>

        <p v-if="loading" class="status-line muted">검색 중…</p>
        <p v-else-if="rateLimited" class="status-line muted">검색이 너무 잦습니다. 잠시 후 다시 시도해 주세요.</p>
        <p v-else-if="searched && searchQuery.trim().length > 0 && searchQuery.trim().length < 2"
           class="status-line muted">두 글자 이상 입력해 주세요.</p>
        <p v-else-if="searched && searchQuery.trim().length >= 2 && results.length === 0"
           class="status-line muted">검색 결과가 없습니다.</p>

        <ul v-if="results.length > 0" class="book-list">
            <li class="book-row" v-for="r in results" :key="r.loginId">
                <div class="book-meta">
                    <a class="book-title" :href="`/u/${r.loginId}`">{{ r.nickname }}</a>
                    <span class="book-author">@{{ r.loginId }} · 공개 책 {{ r.publicBookCount }}권</span>
                </div>
                <div class="book-actions">
                    <span v-if="r.self" class="book-status-badge">나</span>
                    <button
                        v-else
                        :class="r.following ? 'btn-ghost' : 'btn-primary'"
                        type="button"
                        @click="doToggleFollow(r)"
                    >{{ r.following ? '언팔로우' : '팔로우' }}</button>
                </div>
            </li>
        </ul>
    </section>

    <!-- 친구 추천 -->
    <section class="card">
        <h2>친구 추천</h2>
        <p v-if="recommendations.length === 0" class="status-line muted">아직 추천할 사용자가 없습니다.</p>
        <ul v-else class="book-list recommend-scroll">
            <li class="book-row" v-for="r in recommendations" :key="r.loginId">
                <div class="book-meta">
                    <a class="book-title" :href="`/u/${r.loginId}`">{{ r.nickname }}</a>
                    <span class="book-author">@{{ r.loginId }} · 공개 책 {{ r.publicBookCount }}권</span>
                    <span v-if="r.reasons && r.reasons.length" class="reason-chips">
                        <span v-for="reason in r.reasons" :key="reason" class="reason-chip">{{ reason }}</span>
                    </span>
                </div>
                <div class="book-actions">
                    <span v-if="r.self" class="book-status-badge">나</span>
                    <button
                        v-else
                        :class="r.following ? 'btn-ghost' : 'btn-primary'"
                        type="button"
                        @click="doToggleFollow(r)"
                    >{{ r.following ? '언팔로우' : '팔로우' }}</button>
                </div>
            </li>
        </ul>
    </section>
</template>
