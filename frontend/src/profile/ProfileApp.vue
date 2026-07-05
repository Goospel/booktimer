<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted } from 'vue';
import { getCsrfToken } from '../shared/follow';
import { setBlock } from '../shared/block';
import ShopIcon from './ShopIcon.vue';
import ShopHeader from './ShopHeader.vue';
import ReportModal from '../shared/ReportModal.vue';
import BtiPanel from './BtiPanel.vue';
import ShelfPanel from './ShelfPanel.vue';
import NavLinks from '../shared/NavLinks.vue';
import StoryViewer from '../shared/story/StoryViewer.vue';
import { fetchStoriesOf } from '../shared/story/storyApi';
import type { AuthorStories, StoryCard } from '../shared/story/storyFeed';

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
    profileCharacterCode: string | null;
    followerCount: number; followingCount: number;
    following: boolean; self: boolean;
    personality: string | null; personalityTags: TagChip[];
    books: BookSummary[]; coupangEnabled: boolean; yes24Enabled: boolean; kyoboEnabled: boolean;
}

// ── 상태 ref ──────────────────────────────────────────────────────────────
const profile    = ref<ProfileData | null>(null);
const books      = ref<BookSummary[]>([]);
const loading    = ref(true);
const notFound   = ref(false);
const activeTab  = ref<'bti' | 'shelf'>('shelf');
const shelfFilter = ref<string | null>(null);
const shelfSort   = ref<string | null>(null); // null=이름순(기본) / finished_desc / finished_asc — 완독 필터 한정
const tagPanel   = ref<BookSummary[] | null>(null);
const tagLabel   = ref('');
const reportOpen = ref(false);

// ── 독서 스토리 (sns-design §13.7) — 아바타 링 + 뷰어. 비팔로워·차단은 서버가 빈 배열로 수렴(링 미표시).
const stories = ref<StoryCard[]>([]);
const storyViewerOpen = ref(false);
const storiesStale = ref(false); // 뷰어 재생 중 목록 교체 금지 — 닫힐 때 재조회(인덱스 드리프트 방지)
const storyGroups = computed<AuthorStories[]>(() => {
    const p = profile.value;
    if (!p || stories.value.length === 0) return [];
    return [{
        loginId: p.loginId, nickname: p.nickname, profileCharacterCode: p.profileCharacterCode,
        allViewed: stories.value.every(s => s.viewed), stories: stories.value,
    }];
});

async function loadStories() {
    try {
        stories.value = await fetchStoriesOf(loginId);
    } catch {
        stories.value = []; // 네트워크 실패 — 링 미표시로 수렴(unhandled rejection 방지)
    }
}

function onStoryViewerClose() {
    storyViewerOpen.value = false;
    if (storiesStale.value) {
        storiesStale.value = false;
        loadStories();
    }
}

// 반응형 분기 — presentational only(데이터/액션 로직 불변). 와이드(≥860px)=2열, 모바일=탭.
const isWide = ref(false);
let mq: MediaQueryList | null = null;
function onMqChange(e: MediaQueryListEvent) { isWide.value = e.matches; }

// ── URL 헬퍼 ──────────────────────────────────────────────────────────────
function urlFor(): string {
    const p = new URLSearchParams();
    if (shelfFilter.value) p.set('status', shelfFilter.value);
    if (shelfSort.value) p.set('sort', shelfSort.value);
    if (activeTab.value === 'shelf') p.set('tab', 'shelf');
    const qs = p.toString();
    return `/u/${loginId}` + (qs ? `?${qs}` : '');
}

function historyState() {
    return { tab: activeTab.value, status: shelfFilter.value, sort: shelfSort.value };
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
        loadStories(); // 프로필이 보이는 사람에게만 링 시도 — 결과 빈 배열이면 링 없음

        // 기본 탭 결정: personality 있고 필터 신호 없으면 bti, 콜드스타트·필터 신호면 shelf
        if (activeTab.value !== 'shelf') {
            activeTab.value = (data.personality != null && shelfFilter.value == null) ? 'bti' : 'shelf';
        }

        // 딥링크(?status=·?sort=)로 들어온 초기 로드 — 전체 목록 대신 필터·정렬 적용 목록으로 교체
        // (기존엔 칩만 활성화되고 목록은 전체가 뜨던 사각).
        if (shelfFilter.value || shelfSort.value) await loadBooks();
    } finally {
        loading.value = false;
    }
}

async function loadBooks() {
    const p = new URLSearchParams({ loginId });
    if (shelfFilter.value) p.set('status', shelfFilter.value);
    if (shelfSort.value) p.set('sort', shelfSort.value);
    const res = await fetch(`/api/profile/books?${p}`, { credentials: 'same-origin' });
    if (res.ok) books.value = (await res.json()).books;
}

// ── 탭·필터·정렬 ──────────────────────────────────────────────────────────
function selectTab(tab: 'bti' | 'shelf') {
    activeTab.value = tab;
    history.pushState(historyState(), '', urlFor());
}

async function selectStatus(s: string | null) {
    shelfFilter.value = s;
    if (s !== 'FINISHED') shelfSort.value = null; // 완독 한정 정렬 — 다른 필터로 가면 이름순(기본) 복귀
    activeTab.value   = 'shelf';
    await loadBooks();
    history.pushState(historyState(), '', urlFor());
}

async function selectSort(sort: string | null) {
    shelfSort.value = sort;
    await loadBooks();
    history.pushState(historyState(), '', urlFor());
}

// ── 태그 드릴다운 ─────────────────────────────────────────────────────────
async function openTag(label: string) {
    const p = new URLSearchParams({ loginId, tag: label });
    const res = await fetch(`/api/profile/personality-tag?${p}`, { credentials: 'same-origin' });
    if (res.ok) {
        const data = await res.json();
        tagPanel.value = data.books;
        tagLabel.value = label;
        activeTab.value = 'bti';   // 모바일: shelf 탭에서도 드릴다운이 보이도록 BTI 탭으로 전환(와이드는 무영향)
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

// ── popstate ─────────────────────────────────────────────────────────────
function onPopState(e: PopStateEvent) {
    const s = e.state ?? {};
    shelfFilter.value = s.status ?? null;
    shelfSort.value   = s.sort ?? null;
    activeTab.value   = s.tab ?? 'shelf';
    if (activeTab.value === 'shelf') loadBooks();
}

// ── mount ─────────────────────────────────────────────────────────────────
onMounted(() => {
    // 딥링크 초기 파싱
    const params = new URLSearchParams(location.search);
    const statusParam = params.get('status');
    const sortParam   = params.get('sort');
    const tabParam    = params.get('tab');
    if (statusParam)       shelfFilter.value = statusParam;
    if (sortParam)         shelfSort.value = sortParam;
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
            <a class="shop-notfound-link" href="/"><ShopIcon name="home" :size="15" />홈</a>
        </div>

        <!-- 로딩 -->
        <p v-else-if="loading" class="status-line muted">불러오는 중…</p>

        <!-- 본체 -->
        <template v-else-if="profile">

            <!-- 내 책방(self) 상단: 검색창처럼 생긴 '슬림 진입 링크'(트위터식). 누르면 /search로 이동 —
                 검색 본체·친구추천은 전용 페이지가 소유하고 책방엔 진입점만 둔다(책방 비대화 방지).
                 책방=연결 장소라 진입점은 여기 두되, 남의 책방엔 렌더 안 함(누수 방지 불변식, profile-app.test.ts). -->
            <a v-if="profile.self" class="shop-search-entry" href="/search" aria-label="다른 책방 찾기">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                     stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                    <circle cx="11" cy="11" r="7" /><path d="M21 21l-4.3-4.3" />
                </svg>
                <span>다른 책방 찾기</span>
            </a>

            <!-- ── 모바일: 단일열(헤더 → 탭카드 → 링크) ── -->
            <template v-if="!isWide">
                <ShopHeader
                    :nickname="profile.nickname" :login-id="profile.loginId"
                    :profile-character-code="profile.profileCharacterCode"
                    :personality-tags="profile.personalityTags"
                    :follower-count="profile.followerCount" :following-count="profile.followingCount"
                    :self="profile.self" :following="profile.following"
                    :has-stories="stories.length > 0" :stories-unviewed="stories.some(s => !s.viewed)"
                    @open-tag="openTag" @toggle-follow="toggleFollow" @do-block="doBlock"
                    @open-report="reportOpen = true" @open-stories="storyViewerOpen = true" />

                <section class="dash-card shop-tab-card">
                    <div class="shop-tabs">
                        <button type="button" class="shop-tab" :class="{ active: activeTab === 'bti' }"
                                @click="selectTab('bti')">책BTI</button>
                        <button type="button" class="shop-tab" :class="{ active: activeTab === 'shelf' }"
                                @click="selectTab('shelf')">공개한 책</button>
                    </div>
                    <BtiPanel v-if="activeTab === 'bti'"
                              :personality="profile.personality" :self="profile.self"
                              :tag-panel="tagPanel" :tag-label="tagLabel" @close-tag="closeTag" />
                    <ShelfPanel v-else
                                :books="books" :shelf-filter="shelfFilter" :shelf-sort="shelfSort" :self="profile.self"
                                :coupang-enabled="profile.coupangEnabled" :yes24-enabled="profile.yes24Enabled" :kyobo-enabled="profile.kyoboEnabled" :login-id="loginId"
                                @select-status="selectStatus" @select-sort="selectSort" />
                </section>
            </template>

            <!-- ── 와이드: 2열(좌 사이드바: 헤더+책BTI 상시 / 우 공개한 책) ── -->
            <div v-else class="shop-wide">
                <div class="shop-side">
                    <ShopHeader
                        :nickname="profile.nickname" :login-id="profile.loginId"
                        :profile-character-code="profile.profileCharacterCode"
                        :personality-tags="profile.personalityTags"
                        :follower-count="profile.followerCount" :following-count="profile.followingCount"
                        :self="profile.self" :following="profile.following"
                        :has-stories="stories.length > 0" :stories-unviewed="stories.some(s => !s.viewed)"
                        @open-tag="openTag" @toggle-follow="toggleFollow" @do-block="doBlock"
                        @open-report="reportOpen = true" @open-stories="storyViewerOpen = true" />

                    <section class="dash-card shop-bti-card">
                        <span class="dash-pill">책BTI</span>
                        <BtiPanel :personality="profile.personality" :self="profile.self"
                                  :tag-panel="tagPanel" :tag-label="tagLabel" @close-tag="closeTag" />
                    </section>
                </div>

                <div class="shop-main">
                    <section class="dash-card">
                        <ShelfPanel :books="books" :shelf-filter="shelfFilter" :shelf-sort="shelfSort" :self="profile.self"
                                    :coupang-enabled="profile.coupangEnabled" :yes24-enabled="profile.yes24Enabled" :kyobo-enabled="profile.kyoboEnabled" :login-id="loginId"
                                    :show-title="true" @select-status="selectStatus" @select-sort="selectSort" />
                    </section>
                </div>
            </div>

            <!-- 신고 모달 (other only) — 모바일·와이드 공통 단일 인스턴스 -->
            <ReportModal v-if="reportOpen && !profile.self" :login-id="loginId"
                         @close="reportOpen = false" />

            <!-- 스토리 뷰어 — 아바타 링 탭. 삭제(본인) 재조회는 닫힐 때(재생 중 목록 교체 금지) -->
            <StoryViewer v-if="storyViewerOpen && storyGroups.length" :groups="storyGroups"
                         :start-group="0" :my-login-id="myLoginId"
                         @close="onStoryViewerClose" @changed="storiesStale = true" />

            <!-- ── 하단 링크 (전 페이지 공유 .link-row 타일) ──
                 차단 목록은 자주 안 쓰는 계정 관리라 책방 상시 노출 대신 대시보드 아바타 메뉴(DashHeader)로 이동. -->
            <NavLinks :links="[
                { href: '/', icon: 'home', label: '홈' },
                { href: '/books', icon: 'books', label: '내 책장' },
            ]" />

        </template>
    </div>
</template>
