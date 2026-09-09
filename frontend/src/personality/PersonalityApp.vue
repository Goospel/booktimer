<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue';
import PersonalityCarousel from './PersonalityCarousel.vue';
import type { EntryDto } from './PersonalityCarousel.vue';
import NavLinks from '../shared/NavLinks.vue';
import { joinLabels } from './personalityView';

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

// 서버는 refreshRemaining·refreshLimit도 함께 보내지만 웹은 더 이상 쓰지 않는다 —
// 「다시 분석」이 앱 전용이 되면서 표시할 자리가 사라졌다(2026-09-08). 남는 필드는 그냥 무시된다.
interface PersonalityResponse {
    nickname: string;
    loginId: string;
    view: ViewDto;
}

interface MutationResponse {
    view: ViewDto;
}

const appEl = document.getElementById('personality-app');
const nickname = ref(appEl?.dataset.nickname ?? '');
const loginId = ref(appEl?.dataset.loginId ?? '');

const view = ref<ViewDto | null>(null);
const error = ref(false);
const selectingId = ref<number | null>(null);

// "내 독서 성향" 카드 우상단 ? 헬프 팝오버(공개 안내 + 정확도 고지). 클릭 토글 / 밖 클릭·Esc 닫힘.
const helpOpen = ref(false);
function onKeydown(e: KeyboardEvent) {
    if (e.key === 'Escape') helpOpen.value = false;
}
onMounted(() => window.addEventListener('keydown', onKeydown));
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown));


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
    // view 교체 시 헬프 팝오버를 닫는다 — READY→FALLBACK 전환 등으로 ? 버튼이 사라져도
    // 백드롭(v-if=helpOpen)이 고아로 남지 않게(refresh/select 후 일관 닫힘).
    helpOpen.value = false;
    view.value = data.view;
    if ('nickname' in data) nickname.value = data.nickname;
    if ('loginId' in data) loginId.value = data.loginId;
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
        <!-- 페이지 제목 — 화면엔 숨기고(sr-only) 문서 제목·접근성·SEO만 보존.
             시각 최상단은 BookTimer 브랜드 로고 → 바로 카드(요청: 상단 제목 제거). -->
        <h1 class="sr-only">{{ nickname }}님의 책BTI</h1>

        <!-- 안내 팝오버 밖 클릭 닫기용 투명 백드롭(READY·열림일 때만) -->
        <div v-if="helpOpen" class="pbti-help-backdrop" @click="helpOpen = false"></div>

        <!-- READY: 성향 히스토리 캐러셀 -->
        <section v-if="view.state === 'READY'" class="pbti-card">
            <div class="pbti-card-head" :class="{ 'is-open': helpOpen }">
                <h2 class="pbti-card-title">내 독서 성향</h2>
                <!-- 우상단 ? 헬프 — 클릭 시 공개 안내·정확도 고지 팝오버. 상시 노출 대신 필요할 때만. -->
                <button type="button" class="pbti-help-btn" :aria-expanded="helpOpen" aria-label="책BTI 안내 보기"
                        @click="helpOpen = !helpOpen">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                        <circle cx="12" cy="12" r="10"/><path d="M9.1 9a3 3 0 0 1 5.82 1c0 2-3 3-3 3"/><path d="M12 17h.01"/>
                    </svg>
                </button>
                <!-- 팝오버: 옛 하단 공개 안내 + 최하단 정확도 고지 두 문구 이전 -->
                <div v-if="helpOpen" class="pbti-help-pop" role="dialog" aria-label="책BTI 안내">
                    <div class="pbti-help-note">
                        <svg class="pbti-note-ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                            <circle cx="12" cy="12" r="9"/><path d="M3 12h18"/><path d="M12 3c2.5 2.6 2.5 15.4 0 18M12 3c-2.5 2.6-2.5 15.4 0 18"/>
                        </svg>
                        <span>이 책BTI는 <strong>공개한 책만</strong>으로 분석해 내 책방(<a :href="`/u/${loginId}`">공개 프로필</a>)에 항상 노출돼요. 비공개 책 취향은 빠져요.</span>
                    </div>
                    <div class="pbti-help-note pbti-help-note--muted">
                        <span>MBTI처럼 가볍게 즐기는 재미예요. 책장이 작거나 장르가 치우치면 부정확할 수 있어요.</span>
                    </div>
                </div>
            </div>
            <PersonalityCarousel :entries="view.entries" @select="selectEntry" />
        </section>

        <!-- COLD_START: 책 부족 안내 -->
        <section v-else-if="view.state === 'COLD_START'" class="pbti-card">
            <div class="pbti-state-head">
                <svg class="pbti-state-ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                    <path d="M8 3c0 4.5 8 4.5 8 9s-8 4.5-8 9"/><path d="M16 3c0 4.5-8 4.5-8 9s8 4.5 8 9"/><path d="M9 6.5h6M8.3 12h7.4M9 17.5h6"/>
                </svg>
                <h2 class="pbti-card-title">조금 더 읽으면 성향이 보여요</h2>
            </div>
            <!-- ⚠️ 「분석해 드릴게요」는 웹에서 생성을 걷은 뒤로 지킬 수 없는 약속이 된다(2026-09-08).
                 사용자는 여기서 웹이 알아서 해 준다고 듣고, 책을 다 읽고 돌아오면 FALLBACK에서
                 「앱으로 가라」를 처음 듣게 된다 — 뒤 화면이 앞 화면을 배신하는 자리였다. -->
            <p class="pbti-state-text">
                지금 완독한 책이 <span class="pbti-num">{{ view.profile.finishedBooks }}</span>권이에요.
                최소 <span class="pbti-num">{{ view.coldStartMinBooks }}</span>권쯤 완독하면
                <strong>토스 앱의 북타이머</strong>에서 성향을 만들 수 있어요.
            </p>
        </section>

        <!-- FALLBACK: 서술 없음 안내. 웹에서 생성을 걷은 뒤(2026-09-08) 이 상태의 지배적 의미는
             「LLM 실패」가 아니라 「아직 한 번도 안 만들었다」이다. -->
        <section v-else-if="view.state === 'FALLBACK'" class="pbti-card">
            <div class="pbti-state-head">
                <svg class="pbti-state-ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                    <path d="M5 22h14M5 2h14"/><path d="M8 2v3.5a4 4 0 0 0 1.6 3.2L12 12l-2.4 2.3A4 4 0 0 0 8 17.8V22"/><path d="M16 2v3.5a4 4 0 0 1-1.6 3.2L12 12l2.4 2.3a4 4 0 0 1 1.6 3.1V22"/>
                </svg>
                <h2 class="pbti-card-title">아직 만들어 둔 성향이 없어요</h2>
            </div>
            <!-- 옛 문구는 「잠시 후 다시 분석해 주세요」였는데, 웹에서 생성을 걷은 뒤로는 거짓말이 된다
                 — 여기서 아무리 기다려도 다시 분석할 방법이 없다(2026-09-08). -->
            <p class="pbti-state-text">성향 서술은 아직 없지만 사실 요약은 아래에서 볼 수 있어요.</p>
            <div class="pbti-hint">
                <svg class="pbti-hint-ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                    <path d="M12 5v14M5 12l7 7 7-7"/>
                </svg>
                <span>아래 '책장 요약'에서 장르·저자·완독 수를 확인할 수 있어요.</span>
            </div>
        </section>

        <!-- 책장 요약 (항상) -->
        <section class="pbti-card">
            <h2 class="pbti-card-title">책장 요약</h2>
            <div v-if="view.tags.length > 0" class="pbti-chip-row">
                <span v-for="tag in view.tags" :key="tag" class="pbti-chip">{{ tag }}</span>
            </div>
            <div class="pbti-divider"></div>
            <div class="pbti-summary">
                <div v-if="view.profile.topGenres.length > 0" class="pbti-summary-row">
                    <span class="pbti-summary-label">자주 읽는 장르</span>
                    <span class="pbti-summary-value">{{ joinLabels(view.profile.topGenres) }}</span>
                </div>
                <div v-if="view.profile.topAuthors.length > 0" class="pbti-summary-row">
                    <span class="pbti-summary-label">자주 읽는 저자</span>
                    <span class="pbti-summary-value">{{ joinLabels(view.profile.topAuthors) }}</span>
                </div>
            </div>
            <p class="pbti-meta">
                완독 <span class="pbti-num">{{ view.profile.finishedBooks }}</span>권 ·
                저자 <span class="pbti-num">{{ view.profile.distinctAuthors }}</span>명 ·
                장르 <span class="pbti-num">{{ view.profile.distinctGenres }}</span>종
            </p>
        </section>

        <!-- 성향 분석 생성은 앱에서만 (2026-09-08) — 앱은 리워드 광고를 봐야 돌릴 수 있어 호출마다
             수익이 붙는데, 웹엔 그 관문이 없어 비용만 나갔다. 여기서는 만들어 둔 분석을 읽기만 한다.
             COLD_START(책이 모자람)일 땐 안내가 겹치므로 위 화면에 맡기고 이 블록을 안 그린다. -->
        <div v-if="view.state !== 'COLD_START'" class="pbti-refresh">
            <div class="pbti-exhausted">
                <svg class="pbti-hint-ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                    <path d="M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20z"/><path d="M12 8v5"/><path d="M12 16h.01"/>
                </svg>
                <span>새 성향 분석은 <strong>토스 앱의 북타이머</strong>에서 만들 수 있어요 — 여기서는 만들어 둔 분석을 볼 수 있습니다.</span>
            </div>
        </div>

        <NavLinks :links="[
            { href: '/', icon: 'home', label: '홈' },
            { href: '/books', icon: 'books', label: '내 책장' },
        ]" />

        <!-- 최하단 정확도 고지 — 비-READY(COLD_START·FALLBACK)에선 ? 팝오버를 달 카드가 없어 그대로 노출.
             READY에선 공개 안내와 함께 위 ? 팝오버로 이전돼 상시 노출하지 않는다. -->
        <p v-if="view.state !== 'READY'" class="pbti-disclaimer">MBTI처럼 가볍게 즐기는 재미예요. 책장이 작거나 장르가 치우치면 부정확할 수 있어요.</p>
    </template>

    <div v-else class="status-line">불러오는 중…</div>
</template>
