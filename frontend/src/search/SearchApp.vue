<script setup lang="ts">
import { ref, onMounted } from 'vue';

interface UserResult {
    loginId: string;
    nickname: string;
    publicBookCount: number;
    following: boolean;
    self: boolean;
}

interface SearchResponse {
    q: string | null;
    results: UserResult[];
    recommendations: UserResult[];
    myLoginId: string;
    rateLimited: boolean;
}

const appEl = document.getElementById('search-app');
const myLoginId = ref(appEl?.dataset.myLoginId ?? '');
const searchQuery = ref(appEl?.dataset.initialQ ?? '');

const results = ref<UserResult[]>([]);
const recommendations = ref<UserResult[]>([]);
const rateLimited = ref(false);
const loading = ref(false);
const searched = ref(false);

function getCsrfToken(): string {
    return (document.querySelector('meta[name="_csrf"]') as HTMLMetaElement)?.content ?? '';
}

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

async function toggleFollow(user: UserResult) {
    const endpoint = user.following ? '/api/unfollow' : '/api/follow';
    const res = await fetch(endpoint, {
        method: 'POST',
        credentials: 'same-origin',
        headers: {
            'Content-Type': 'application/json',
            'X-CSRF-TOKEN': getCsrfToken(),
        },
        body: JSON.stringify({ loginId: user.loginId }),
    });
    if (res.ok) {
        const data = await res.json();
        user.following = data.following;
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
    <div class="page-stack">
        <p class="greeting">사용자 검색</p>
        <!-- 아이디(@핸들) 검색 -->
        <section class="card">
            <h2>아이디로 찾기</h2>
            <form class="book-search-form" @submit.prevent="onSearch">
                <input
                    v-model="searchQuery"
                    type="text"
                    placeholder="아이디 검색 (2글자 이상)"
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
                            @click="toggleFollow(r)"
                        >{{ r.following ? '언팔로우' : '팔로우' }}</button>
                    </div>
                </li>
            </ul>
        </section>

        <!-- 친구 추천 -->
        <section class="card">
            <h2>친구 추천</h2>
            <p v-if="recommendations.length === 0" class="status-line muted">아직 추천할 사용자가 없습니다.</p>
            <ul v-else class="book-list">
                <li class="book-row" v-for="r in recommendations" :key="r.loginId">
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
                            @click="toggleFollow(r)"
                        >{{ r.following ? '언팔로우' : '팔로우' }}</button>
                    </div>
                </li>
            </ul>
        </section>

        <p class="link-row">
            <a href="/">← 대시보드</a>
            <a :href="`/u/${myLoginId}`">📖 내 책방</a>
        </p>
    </div>
</template>
