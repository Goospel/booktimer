<script setup lang="ts">
import { ref, onMounted } from 'vue';
import PersonalityCarousel from './PersonalityCarousel.vue';
import type { EntryDto } from './PersonalityCarousel.vue';
import NavLinks from '../shared/NavLinks.vue';

interface ReadingProfile {
    totalBooks: number;
    finishedBooks: number;
    readingBooks: number;
    wantToReadBooks: number;
    finishedRatio: number;
    totalReadingSeconds: number;
    finishedSessionCount: number;
    avgSessionSeconds: number;
    distinctAuthors: number;
    topAuthors: { label: string; count: number }[];
    distinctGenres: number;
    topGenres: { label: string; count: number }[];
}

interface ViewDto {
    state: 'READY' | 'COLD_START' | 'FALLBACK';
    narrative: string | null;
    tags: string[];
    profile: ReadingProfile;
    coldStartMinBooks: number;
    entries: EntryDto[];
}

interface PersonalityResponse {
    nickname: string;
    loginId: string;
    view: ViewDto;
    refreshRemaining: number;
    refreshLimit: number;
}

interface MutationResponse {
    view: ViewDto;
    refreshRemaining: number;
    refreshLimit: number;
}

const appEl = document.getElementById('personality-app');
const nickname = ref(appEl?.dataset.nickname ?? '');
const loginId = ref(appEl?.dataset.loginId ?? '');

const view = ref<ViewDto | null>(null);
const refreshRemaining = ref(0);
const refreshLimit = ref(3);
const loading = ref(false);
const error = ref(false);
const refreshing = ref(false);
const selectingId = ref<number | null>(null);

function getCsrfToken(): string {
    return (document.querySelector('meta[name="_csrf"]') as HTMLMetaElement)?.content ?? '';
}

onMounted(async () => {
    try {
        const res = await fetch('/api/personality', { credentials: 'same-origin' });
        if (!res.ok) throw new Error('fetch failed');
        const data: PersonalityResponse = await res.json();
        applyResponse(data);
    } catch {
        error.value = true;
    }
});

function applyResponse(data: PersonalityResponse | MutationResponse) {
    view.value = data.view;
    refreshRemaining.value = data.refreshRemaining;
    refreshLimit.value = data.refreshLimit;
    if ('nickname' in data) nickname.value = data.nickname;
    if ('loginId' in data) loginId.value = data.loginId;
}

async function refresh() {
    if (refreshRemaining.value === 0 || refreshing.value) return;
    refreshing.value = true;
    try {
        const res = await fetch('/api/personality/refresh', {
            method: 'POST',
            credentials: 'same-origin',
            headers: { 'X-CSRF-TOKEN': getCsrfToken() },
            signal: AbortSignal.timeout(30000),
        });
        if (res.status === 429) {
            refreshRemaining.value = 0;
            return;
        }
        if (!res.ok) throw new Error('refresh failed');
        const data: MutationResponse = await res.json();
        applyResponse(data);
    } catch {
        // LLM 실패는 serve-stale — view는 FALLBACK 또는 stale READY로 자연 반영됨
    } finally {
        refreshing.value = false;
    }
}

async function selectEntry(id: number) {
    if (selectingId.value !== null) return;
    selectingId.value = id;
    try {
        const res = await fetch(`/api/personality/select/${id}`, {
            method: 'POST',
            credentials: 'same-origin',
            headers: { 'X-CSRF-TOKEN': getCsrfToken() },
        });
        if (!res.ok) throw new Error('select failed');
        const data: MutationResponse = await res.json();
        applyResponse(data);
    } catch {
        // 서버가 조용히 무시(IDOR) — 응답 view로 자연 반영
    } finally {
        selectingId.value = null;
    }
}
</script>

<template>
    <div v-if="error" class="status-line">데이터를 불러오지 못했습니다.</div>

    <template v-else-if="view">
        <p class="greeting"><span>{{ nickname }}</span>님의 책BTI 🧬</p>

        <!-- 정확도 고지 -->
        <p class="status-line">MBTI처럼 <strong>가볍게 즐기는 재미</strong>예요. 책장이 작거나 장르가 치우치면 부정확할 수 있어요.</p>

        <!-- READY: 성향 히스토리 캐러셀 -->
        <section v-if="view.state === 'READY'" class="card">
            <h2>내 독서 성향</h2>
            <PersonalityCarousel :entries="view.entries" @select="selectEntry" />
            <p class="status-line personality-public-note">
                🌍 이 책BTI는 <strong>공개한 책만</strong>으로 분석해 내 책방(<a :href="`/u/${loginId}`">공개 프로필</a>)에 항상 노출돼요. 비공개 책 취향은 빠져요.
            </p>
        </section>

        <!-- COLD_START: 책 부족 안내 -->
        <section v-else-if="view.state === 'COLD_START'" class="card">
            <h2>조금 더 읽으면 성향이 보여요</h2>
            <p class="status-line">
                지금 완독한 책이 <strong>{{ view.profile.finishedBooks }}</strong>권이에요.
                최소 <strong>{{ view.coldStartMinBooks }}</strong>권쯤 완독하면 성향을 분석해 드릴게요.
            </p>
        </section>

        <!-- FALLBACK: LLM 실패 안내 -->
        <section v-else-if="view.state === 'FALLBACK'" class="card">
            <h2>잠시 후 다시 분석해 주세요</h2>
            <p class="status-line">지금은 성향 서술을 불러오지 못했어요. 사실 요약은 아래에서 볼 수 있어요.</p>
        </section>

        <!-- 책장 요약 (항상) -->
        <section class="card">
            <h2>책장 요약</h2>
            <div v-if="view.tags.length > 0" class="tag-chip-row">
                <span v-for="tag in view.tags" :key="tag" class="tag-chip">{{ tag }}</span>
            </div>
            <ul class="record-list summary-list">
                <li v-if="view.profile.topGenres.length > 0" class="record-row">
                    <span class="record-main">자주 읽는 장르</span>
                    <span class="record-books">{{ view.profile.topGenres.map(g => g.label).join(', ') }}</span>
                </li>
                <li v-if="view.profile.topAuthors.length > 0" class="record-row">
                    <span class="record-main">자주 읽는 저자</span>
                    <span class="record-books">{{ view.profile.topAuthors.map(a => a.label).join(', ') }}</span>
                </li>
            </ul>
        </section>

        <!-- 다시 분석 (COLD_START 아닐 때만) -->
        <template v-if="view.state !== 'COLD_START'">
            <p v-if="refreshRemaining === 0" class="status-line">
                ⏳ 오늘 '다시 분석'을 모두 사용했어요(하루 최대 <strong>{{ refreshLimit }}</strong>번). 자정이 지나면 다시 분석할 수 있어요.
            </p>
            <div class="refresh-form">
                <button type="button" class="btn-primary"
                        :disabled="refreshRemaining === 0 || refreshing"
                        @click="refresh">
                    <template v-if="refreshing">⏳ 분석 중…</template>
                    <template v-else>
                        🔄 다시 분석<span class="refresh-count">오늘 남은 횟수 {{ refreshRemaining }}/{{ refreshLimit }}</span>
                    </template>
                </button>
            </div>
        </template>

        <NavLinks :links="[
            { href: '/', icon: 'home', label: '대시보드' },
            { href: '/books', icon: 'books', label: '내 책장' },
        ]" />
    </template>

    <div v-else class="status-line">불러오는 중…</div>
</template>
