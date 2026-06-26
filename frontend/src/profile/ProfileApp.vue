<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue';
import { getCsrfToken } from '../shared/follow';
import { setBlock } from '../shared/block';
import { report as doReport } from '../shared/report';
import ShopIcon from './ShopIcon.vue';
import ShopHeader from './ShopHeader.vue';
import BtiPanel from './BtiPanel.vue';
import ShelfPanel from './ShelfPanel.vue';
import NavLinks from '../shared/NavLinks.vue';
import UserSearchPanel from '../shared/UserSearchPanel.vue';

// ── 상수 ────────────────────────────────────────────────────────────────
const REPORT_REASONS = [
    { value: 'SPAM',          label: '스팸/광고' },
    { value: 'HARASSMENT',    label: '괴롭힘/욕설' },
    { value: 'INAPPROPRIATE', label: '부적절한 콘텐츠' },
    { value: 'OTHER',         label: '기타' },
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

// 반응형 분기 — presentational only(데이터/액션 로직 불변). 와이드(≥860px)=2열, 모바일=탭.
const isWide = ref(false);
let mq: MediaQueryList | null = null;
function onMqChange(e: MediaQueryListEvent) { isWide.value = e.matches; }

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

    // 반응형 분기 초기화 + 구독
    mq = matchMedia('(min-width: 860px)');
    isWide.value = mq.matches;
    mq.addEventListener('change', onMqChange);

    load();
    window.addEventListener('popstate', onPopState);
});
onUnmounted(() => {
    window.removeEventListener('popstate', onPopState);
    mq?.removeEventListener('change', onMqChange);
});
</script>

<template>
    <div class="page-stack">
        <!-- 404 (미존재·차단·운영자 통합) -->
        <div v-if="notFound" class="dash-card shop-notfound">
            <ShopIcon name="books" :size="40" />
            <p class="shop-notfound-msg">프로필을 찾을 수 없습니다.</p>
            <a class="shop-notfound-link" href="/"><ShopIcon name="home" :size="15" />대시보드</a>
        </div>

        <!-- 로딩 -->
        <p v-else-if="loading" class="status-line muted">불러오는 중…</p>

        <!-- 본체 -->
        <template v-else-if="profile">

            <!-- 내 책방(self)에서만: '다른 책방 찾기'(사용자 검색) + 친구 추천을 상단에 흡수.
                 탐색은 SNS 발견 기능이라 대시보드 타일에서 내려 책방으로 합침(A안).
                 남의 책방엔 렌더하지 않는다 — 누수 방지 불변식(profile-app.test.ts). -->
            <UserSearchPanel v-if="profile.self"
                             heading="다른 책방 찾기"
                             placeholder="다른 사람 책방 찾기 (아이디 2글자 이상)" />

            <!-- ── 모바일: 단일열(헤더 → (other)신고 → 탭카드 → 링크) ── -->
            <template v-if="!isWide">
                <ShopHeader
                    :nickname="profile.nickname" :login-id="profile.loginId"
                    :personality-tags="profile.personalityTags"
                    :follower-count="profile.followerCount" :following-count="profile.followingCount"
                    :self="profile.self" :following="profile.following"
                    @open-tag="openTag" @toggle-follow="toggleFollow" @do-block="doBlock" />

                <details v-if="!profile.self" class="shop-report">
                    <summary>
                        <ShopIcon name="chevron" :size="14" class="shop-report-caret" />
                        <ShopIcon name="report" :size="15" />이 사용자 신고
                    </summary>
                    <div class="shop-report-form">
                        <label class="shop-report-field">사유
                            <select v-model="reportReason">
                                <option v-for="r in REPORT_REASONS" :key="r.value" :value="r.value">{{ r.label }}</option>
                            </select>
                        </label>
                        <textarea v-model="reportDetail" rows="2" maxlength="500"
                                  placeholder="상세 내용 (선택)"></textarea>
                        <button type="button" class="dash-btn-outline shop-report-submit"
                                @click="submitReport">신고하기</button>
                        <p v-if="reportSubmitted && reported" class="shop-report-ok">신고가 접수되었습니다.</p>
                    </div>
                </details>

                <section class="dash-card shop-tab-card">
                    <div class="shop-tabs">
                        <button type="button" class="shop-tab" :class="{ active: activeTab === 'bti' }"
                                @click="selectTab('bti')">책BTI</button>
                        <button type="button" class="shop-tab" :class="{ active: activeTab === 'shelf' }"
                                @click="selectTab('shelf')">공개 책장</button>
                    </div>
                    <BtiPanel v-if="activeTab === 'bti'"
                              :personality="profile.personality" :self="profile.self"
                              :tag-panel="tagPanel" :tag-label="tagLabel" @close-tag="closeTag" />
                    <ShelfPanel v-else
                                :books="books" :shelf-filter="shelfFilter" :self="profile.self"
                                :coupang-enabled="profile.coupangEnabled" :login-id="loginId"
                                @select-status="selectStatus" />
                </section>
            </template>

            <!-- ── 와이드: 2열(좌 사이드바: 헤더+책BTI 상시+신고 / 우 공개책장) ── -->
            <div v-else class="shop-wide">
                <div class="shop-side">
                    <ShopHeader
                        :nickname="profile.nickname" :login-id="profile.loginId"
                        :personality-tags="profile.personalityTags"
                        :follower-count="profile.followerCount" :following-count="profile.followingCount"
                        :self="profile.self" :following="profile.following"
                        @open-tag="openTag" @toggle-follow="toggleFollow" @do-block="doBlock" />

                    <section class="dash-card shop-bti-card">
                        <span class="dash-pill">책BTI</span>
                        <BtiPanel :personality="profile.personality" :self="profile.self"
                                  :tag-panel="tagPanel" :tag-label="tagLabel" @close-tag="closeTag" />
                    </section>

                    <details v-if="!profile.self" class="shop-report">
                        <summary>
                            <ShopIcon name="chevron" :size="14" class="shop-report-caret" />
                            <ShopIcon name="report" :size="15" />이 사용자 신고
                        </summary>
                        <div class="shop-report-form">
                            <label class="shop-report-field">사유
                                <select v-model="reportReason">
                                    <option v-for="r in REPORT_REASONS" :key="r.value" :value="r.value">{{ r.label }}</option>
                                </select>
                            </label>
                            <textarea v-model="reportDetail" rows="2" maxlength="500"
                                      placeholder="상세 내용 (선택)"></textarea>
                            <button type="button" class="dash-btn-outline shop-report-submit"
                                    @click="submitReport">신고하기</button>
                            <p v-if="reportSubmitted && reported" class="shop-report-ok">신고가 접수되었습니다.</p>
                        </div>
                    </details>
                </div>

                <div class="shop-main">
                    <section class="dash-card">
                        <ShelfPanel :books="books" :shelf-filter="shelfFilter" :self="profile.self"
                                    :coupang-enabled="profile.coupangEnabled" :login-id="loginId"
                                    :show-title="true" @select-status="selectStatus" />
                    </section>
                </div>
            </div>

            <!-- ── 하단 링크 (전 페이지 공유 .link-row 타일) ── -->
            <NavLinks :links="[
                { href: '/', icon: 'home', label: '대시보드' },
                { href: '/books', icon: 'books', label: '내 책장' },
                ...(profile.self ? [{ href: '/me/blocks', icon: 'block', label: '차단 목록' }] : []),
            ]" />

        </template>
    </div>
</template>
