<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue';
import { getCsrfToken } from '../shared/follow';
import { setBlock } from '../shared/block';
import { report as doReport } from '../shared/report';

// ── 상수 ────────────────────────────────────────────────────────────────
const REPORT_REASONS = [
    { value: 'SPAM',          label: '스팸/광고' },
    { value: 'HARASSMENT',    label: '괴롭힘/욕설' },
    { value: 'INAPPROPRIATE', label: '부적절한 콘텐츠' },
    { value: 'OTHER',         label: '기타' },
];
const STATUS_OPTIONS = [
    { value: 'READING',       label: '읽는 중' },
    { value: 'FINISHED',      label: '완독' },
    { value: 'WANT_TO_READ',  label: '읽고 싶음' },
];

// ── dataset ──────────────────────────────────────────────────────────────
const appEl = document.getElementById('profile-app');
const loginId  = appEl?.dataset.loginId  ?? '';
const myLoginId = appEl?.dataset.myLoginId ?? '';

// ── 타입 ─────────────────────────────────────────────────────────────────
interface TagChip  { label: string; clickable: boolean; }
interface BookSummary {
    id: number; title: string; author: string | null; coverUrl: string | null;
    status: string; seconds: number; purchaseLink: string | null;
}
interface ProfileData {
    loginId: string; nickname: string;
    followerCount: number; followingCount: number;
    following: boolean; self: boolean;
    personality: string | null; personalityTags: TagChip[];
    books: BookSummary[]; coupangEnabled: boolean;
}

// ── 상태 ref ──────────────────────────────────────────────────────────────
const profile    = ref<ProfileData | null>(null);
const books      = ref<BookSummary[]>([]);
const loading    = ref(true);
const notFound   = ref(false);
const activeTab  = ref<'bti' | 'shelf'>('shelf');
const shelfFilter = ref<string | null>(null);
const tagPanel   = ref<BookSummary[] | null>(null);
const tagLabel   = ref('');
const reported   = ref(false);
const reportReason  = ref('SPAM');
const reportDetail  = ref('');
const reportSubmitted = ref(false);

// ── URL 헬퍼 ──────────────────────────────────────────────────────────────
function urlFor(): string {
    const p = new URLSearchParams();
    if (shelfFilter.value) p.set('status', shelfFilter.value);
    if (activeTab.value === 'shelf') p.set('tab', 'shelf');
    const qs = p.toString();
    return `/u/${loginId}` + (qs ? `?${qs}` : '');
}

// ── 로드 ─────────────────────────────────────────────────────────────────
async function load() {
    loading.value = true;
    try {
        const res = await fetch(`/api/profile?loginId=${encodeURIComponent(loginId)}`, { credentials: 'same-origin' });
        if (res.status === 404) { notFound.value = true; return; }
        if (!res.ok) return;
        const data: ProfileData = await res.json();
        profile.value = data;
        books.value   = data.books;
        // 기본 탭 결정: personality 있고 필터 신호 없으면 bti, 콜드스타트·필터 신호면 shelf
        if (activeTab.value !== 'shelf') {
            activeTab.value = (data.personality != null && shelfFilter.value == null) ? 'bti' : 'shelf';
        }
    } finally {
        loading.value = false;
    }
}

async function loadBooks() {
    const p = new URLSearchParams({ loginId });
    if (shelfFilter.value) p.set('status', shelfFilter.value);
    const res = await fetch(`/api/profile/books?${p}`, { credentials: 'same-origin' });
    if (res.ok) books.value = (await res.json()).books;
}

// ── 탭·필터 ──────────────────────────────────────────────────────────────
function selectTab(tab: 'bti' | 'shelf') {
    activeTab.value = tab;
    history.pushState({ tab, status: shelfFilter.value }, '', urlFor());
}

async function selectStatus(s: string | null) {
    shelfFilter.value = s;
    activeTab.value   = 'shelf';
    await loadBooks();
    history.pushState({ tab: 'shelf', status: s }, '', urlFor());
}

// ── 태그 드릴다운 ─────────────────────────────────────────────────────────
async function openTag(label: string) {
    const p = new URLSearchParams({ loginId, tag: label });
    const res = await fetch(`/api/profile/personality-tag?${p}`, { credentials: 'same-origin' });
    if (res.ok) {
        const data = await res.json();
        tagPanel.value = data.books;
        tagLabel.value = label;
    }
}
function closeTag() { tagPanel.value = null; }

// ── 팔로우 ───────────────────────────────────────────────────────────────
async function toggleFollow() {
    if (!profile.value) return;
    const endpoint = profile.value.following ? '/api/unfollow' : '/api/follow';
    const res = await fetch(endpoint, {
        method: 'POST',
        credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': getCsrfToken() },
        body: JSON.stringify({ loginId }),
    });
    if (res.ok) {
        const data = await res.json();
        const was = profile.value.following;
        profile.value.following = data.following;
        if (data.following && !was)  profile.value.followerCount++;
        if (!data.following && was)  profile.value.followerCount--;
    }
}

// ── 차단 ─────────────────────────────────────────────────────────────────
async function doBlock() {
    if (!confirm('이 사용자를 차단할까요? 서로 팔로우·프로필 열람이 막힙니다.')) return;
    await setBlock(loginId, true);
    location.href = '/me/blocks';
}

// ── 신고 ─────────────────────────────────────────────────────────────────
async function submitReport() {
    if (!confirm('이 사용자를 신고할까요?')) return;
    const result = await doReport(loginId, reportReason.value, reportDetail.value);
    reported.value = result;
    reportSubmitted.value = true;
}

// ── popstate ─────────────────────────────────────────────────────────────
function onPopState(e: PopStateEvent) {
    const s = e.state ?? {};
    shelfFilter.value = s.status ?? null;
    activeTab.value   = s.tab ?? 'shelf';
    if (activeTab.value === 'shelf') loadBooks();
}

// ── mount ─────────────────────────────────────────────────────────────────
onMounted(() => {
    // 딥링크 초기 파싱
    const params = new URLSearchParams(location.search);
    const statusParam = params.get('status');
    const tabParam    = params.get('tab');
    if (statusParam)       shelfFilter.value = statusParam;
    if (tabParam === 'shelf') activeTab.value = 'shelf';

    load();
    window.addEventListener('popstate', onPopState);
});
onUnmounted(() => window.removeEventListener('popstate', onPopState));
</script>

<template>
    <div>
        <!-- 404 -->
        <p v-if="notFound" class="status-line">프로필을 찾을 수 없습니다.</p>

        <!-- 로딩 -->
        <p v-else-if="loading" class="status-line muted">불러오는 중…</p>

        <!-- 본체 -->
        <template v-else-if="profile">

            <!-- ① 프로필 헤더 카드 -->
            <div class="card profile-card">
                <p class="greeting">{{ profile.nickname }}님의 책방</p>
                <p class="muted">@{{ profile.loginId }}</p>

                <!-- 책BTI 태그칩 행 -->
                <div v-if="profile.personalityTags.length > 0" class="tag-chip-row">
                    <template v-for="t in profile.personalityTags" :key="t.label">
                        <button v-if="t.clickable" type="button"
                                class="tag-chip tag-chip-click"
                                @click="openTag(t.label)">{{ t.label }}</button>
                        <span v-else class="tag-chip">{{ t.label }}</span>
                    </template>
                </div>

                <!-- 팔로우 카운트 + 액션 -->
                <div class="profile-follow">
                    <a v-if="profile.self" class="follow-count" href="/me/followers">
                        팔로워 <strong>{{ profile.followerCount }}</strong>
                    </a>
                    <span v-else class="follow-count">팔로워 <strong>{{ profile.followerCount }}</strong></span>

                    <a v-if="profile.self" class="follow-count" href="/me/following">
                        팔로잉 <strong>{{ profile.followingCount }}</strong>
                    </a>
                    <span v-else class="follow-count">팔로잉 <strong>{{ profile.followingCount }}</strong></span>

                    <span v-if="!profile.self" class="follow-action profile-actions">
                        <button type="button"
                                :class="profile.following ? 'btn-ghost' : 'btn-primary'"
                                @click="toggleFollow">
                            {{ profile.following ? '언팔로우' : '팔로우' }}
                        </button>
                        <button type="button" class="btn-ghost btn-block" @click="doBlock">차단</button>
                    </span>
                </div>
            </div>

            <!-- ② 신고 박스 (타인 전용) -->
            <details v-if="!profile.self" class="report-box">
                <summary>🚩 이 사용자 신고</summary>
                <div class="report-form">
                    <label>사유
                        <select v-model="reportReason">
                            <option v-for="r in REPORT_REASONS" :key="r.value" :value="r.value">{{ r.label }}</option>
                        </select>
                    </label>
                    <textarea v-model="reportDetail" rows="2" maxlength="500"
                              placeholder="상세 내용 (선택)"></textarea>
                    <button type="button" class="btn-ghost btn-block" @click="submitReport">신고하기</button>
                    <!-- reported:true일 때만 접수됨 표시 — 한도 초과+미신고(reported:false)에선 거짓 접수됨 금지 -->
                    <p v-if="reportSubmitted && reported" class="status-line">신고가 접수되었습니다.</p>
                </div>
            </details>

            <!-- ③ 탭 + 패널 -->
            <section class="card record-card">
                <!-- 탭 레이블 -->
                <a href="#" class="record-tab" :class="{ active: activeTab === 'bti' }"
                   @click.prevent="selectTab('bti')">책BTI</a>
                <a href="#" class="record-tab" :class="{ active: activeTab === 'shelf' }"
                   @click.prevent="selectTab('shelf')">공개 책장</a>

                <!-- 책BTI 패널 -->
                <div v-if="activeTab === 'bti'" class="panel-bti">
                    <!-- 태그 드릴다운 결과 패널 -->
                    <div v-if="tagPanel" class="tag-books-panel">
                        <div class="tag-books-head">
                            <h3>「{{ tagLabel }}」를 만든 책 {{ tagPanel.length }}권</h3>
                            <button type="button" class="btn-ghost btn-close" @click="closeTag">× 닫기</button>
                        </div>
                        <p v-if="tagPanel.length === 0" class="status-line">해당하는 공개 완독 책이 없어요.</p>
                        <ul v-else class="book-list tag-books-scroll">
                            <li v-for="b in tagPanel" :key="b.id" class="book-row">
                                <img v-if="b.coverUrl" class="book-cover" :src="b.coverUrl"
                                     alt="" loading="lazy" referrerpolicy="no-referrer">
                                <div class="book-meta">
                                    <span class="book-title">{{ b.title }}</span>
                                    <span v-if="b.author" class="book-author">{{ b.author }}</span>
                                </div>
                            </li>
                        </ul>
                    </div>

                    <!-- 책BTI 서술 or 콜드스타트 안내 -->
                    <template v-if="profile.personality">
                        <p class="personality-narrative">{{ profile.personality }}</p>
                        <p class="muted personality-note">
                            {{ profile.self ? '내 공개 책 기준으로 분석했어요.' : '이 사람이 공개한 책 기준이에요.' }}
                            MBTI처럼 가볍게 즐겨 주세요.
                        </p>
                    </template>
                    <p v-else class="status-line">
                        {{ profile.self
                            ? '아직 공개 완독 책이 부족해 책BTI가 분석되지 않았어요. 공개 완독 책이 쌓이면 자동으로 분석돼요.'
                            : '이 사람은 아직 책BTI가 없어요. 공개 완독 책이 쌓이면 분석돼요.' }}
                    </p>
                </div>

                <!-- 공개 책장 패널 -->
                <div v-else class="panel-shelf">
                    <!-- 상태 필터 칩 -->
                    <nav class="shelf-filter">
                        <a href="#" :class="{ active: shelfFilter === null }"
                           @click.prevent="selectStatus(null)">전체</a>
                        <a v-for="s in STATUS_OPTIONS" :key="s.value"
                           href="#" :class="{ active: shelfFilter === s.value }"
                           @click.prevent="selectStatus(s.value)">{{ s.label }}</a>
                    </nav>

                    <p v-if="books.length === 0 && shelfFilter === null" class="status-line">아직 공개한 책이 없습니다.</p>
                    <p v-else-if="books.length === 0" class="status-line">이 상태의 공개 책이 없습니다.</p>
                    <ul v-else class="book-list shelf-scroll">
                        <li v-for="b in books" :key="b.id" class="book-row">
                            <img v-if="b.coverUrl" class="book-cover" :src="b.coverUrl"
                                 alt="" loading="lazy" referrerpolicy="no-referrer">
                            <div class="book-meta">
                                <span class="book-title">{{ b.title }}</span>
                                <span v-if="b.author" class="book-author">{{ b.author }}</span>
                                <span class="book-status-badge">{{ b.status }}</span>
                                <span v-if="b.seconds > 0" class="book-time mono">
                                    ⏱ {{ Math.floor(b.seconds / 3600) }}시간 {{ Math.floor((b.seconds % 3600) / 60) }}분
                                </span>
                            </div>
                            <!-- 구매 액션: 타인 책방 + 구매 링크 또는 쿠팡 활성 시 -->
                            <div v-if="!profile.self && (b.purchaseLink || profile.coupangEnabled)"
                                 class="book-actions">
                                <details v-if="b.purchaseLink && profile.coupangEnabled" class="buy-menu">
                                    <summary class="btn-ghost">구매</summary>
                                    <div class="buy-menu-items">
                                        <a :href="`/u/${loginId}/books/${b.id}/buy`"
                                           target="_blank" rel="noopener nofollow">알라딘</a>
                                        <a :href="`/u/${loginId}/books/${b.id}/buy/coupang`"
                                           target="_blank" rel="noopener nofollow">쿠팡</a>
                                    </div>
                                </details>
                                <a v-else-if="b.purchaseLink && !profile.coupangEnabled"
                                   :href="`/u/${loginId}/books/${b.id}/buy`"
                                   target="_blank" rel="noopener nofollow" class="btn-ghost">구매</a>
                                <a v-else-if="!b.purchaseLink && profile.coupangEnabled"
                                   :href="`/u/${loginId}/books/${b.id}/buy/coupang`"
                                   target="_blank" rel="noopener nofollow" class="btn-ghost">쿠팡</a>
                            </div>
                        </li>
                    </ul>

                    <!-- 제휴 안내문 (타인 책방 + 책 있을 때) -->
                    <template v-if="books.length > 0 && !profile.self">
                        <p class="affiliate-note">
                            ※ "구매" 링크는 제휴(알라딘) 링크로, 구매 시 일부 수수료를 받을 수 있습니다.
                        </p>
                        <p v-if="profile.coupangEnabled" class="affiliate-note">
                            이 포스팅은 쿠팡 파트너스 활동의 일환으로, 이에 따른 일정액의 수수료를 제공받습니다.
                        </p>
                    </template>
                </div>
            </section>

            <!-- ④ 하단 링크 -->
            <p class="link-row">
                <a href="/">← 대시보드</a>
                <a href="/books">📚 내 책장</a>
                <a v-if="profile.self" href="/me/blocks">🚫 차단 목록</a>
            </p>

        </template>
    </div>
</template>
