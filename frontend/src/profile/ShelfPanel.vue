<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import ShopIcon from './ShopIcon.vue'
import { hasCover, coverInitial, statusBadgeClass, formatReadingTime } from './format'
import { hasFreshStory, showMarginHandle } from '../shared/story/storyFeed'

// 공개한 책 패널 — 상태필터칩 + 책 리스트 + 구매 + 제휴 고지. 모바일 탭·와이드 메인 공유.
// 데이터/액션 로직은 ProfileApp이 소유(필터는 selectStatus emit). showTitle은 와이드에서만
// "공개한 책" 소제목을 띄우는 presentational 플래그(모바일은 탭 라벨이 대신).
interface BookSummary {
    id: number; title: string; author: string | null; coverUrl: string | null;
    status: string; seconds: number; purchaseLink: string | null;
    /** 그 책 여백의 최신 글 시각 — 글 없는 책은 null. 팔로우와 무관하다(2026-08-22). */
    lastStoryAt: string | null;
}

const STATUS_OPTIONS = [
    { value: 'READING',      label: '읽는 중' },
    { value: 'FINISHED',     label: '완독' },
    { value: 'WANT_TO_READ', label: '읽고 싶음' },
]

// 완독 한정 정렬 — null=이름순(서버 기본), finished_desc/asc=완독 시각 순(서버 정렬 파라미터 그대로).
const SORT_OPTIONS: { value: string | null; label: string }[] = [
    { value: null,            label: '이름순' },
    { value: 'finished_desc', label: '최신순' },
    { value: 'finished_asc',  label: '오래된순' },
]

const props = defineProps<{
    books: BookSummary[]
    shelfFilter: string | null
    shelfSort: string | null
    self: boolean
    coupangEnabled: boolean
    yes24Enabled: boolean
    kyoboEnabled: boolean
    loginId: string
    showTitle?: boolean
}>()
defineEmits<{
    (e: 'selectStatus', value: string | null): void
    (e: 'selectSort', value: string | null): void
    (e: 'openMargin', book: BookSummary): void
}>()

// 발광 기준 시각 — 마운트 때 한 번. 초 단위로 살아 움직일 필요가 없는 하루 창 판정이다.
const now = Date.now()

// 구매 옵션 리스트 — 활성 제공자만(알라딘=구매링크 유무 / 쿠팡·Yes24=활성 플래그). 남의 책방 경로 prefix.
// 조합 분기(purchaseLink×coupang×yes24) 폭발을 없애는 리팩터: 2개↑면 드롭다운, 1개면 제공자명 단일 버튼.
function buyOptions(b: BookSummary): { label: string; href: string }[] {
    const o: { label: string; href: string }[] = []
    if (b.purchaseLink) o.push({ label: '알라딘', href: `/u/${props.loginId}/books/${b.id}/buy` })
    if (props.coupangEnabled) o.push({ label: '쿠팡', href: `/u/${props.loginId}/books/${b.id}/buy/coupang` })
    if (props.yes24Enabled) o.push({ label: 'Yes24', href: `/u/${props.loginId}/books/${b.id}/buy/yes24` })
    if (props.kyoboEnabled) o.push({ label: '교보문고', href: `/u/${props.loginId}/books/${b.id}/buy/kyobo` })
    return o
}

// 제휴 고지 ⓘ 팝오버 — 우상단 버튼 클릭 토글 / 밖 클릭·Esc 닫힘(책장·책BTI와 동일 패턴).
// 이 컴포넌트는 모바일 탭·와이드 메인에 각각 마운트되나 noteOpen이 인스턴스 로컬이라 독립.
const noteOpen = ref(false)
function onNoteKeydown(e: KeyboardEvent) { if (e.key === 'Escape') noteOpen.value = false }
onMounted(() => window.addEventListener('keydown', onNoteKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onNoteKeydown))
</script>

<template>
    <div class="shop-shelf">
        <div class="shop-shelf-head">
            <h2 v-if="showTitle" class="shop-shelf-title">공개한 책</h2>
            <div class="shop-shelf-tools">
                <nav class="shop-filter" aria-label="공개한 책 상태 필터">
                    <button type="button" :class="{ active: shelfFilter === null }"
                            @click="$emit('selectStatus', null)">전체</button>
                    <button v-for="s in STATUS_OPTIONS" :key="s.value" type="button"
                            :class="{ active: shelfFilter === s.value }"
                            @click="$emit('selectStatus', s.value)">{{ s.label }}</button>
                </nav>
                <!-- 우상단 제휴 고지 ⓘ — 남의 책방·책 1권↑에서만(구매 링크·고지 노출 조건과 동일). -->
                <span v-if="!self && books.length > 0" class="affiliate-pop-wrap" :class="{ 'is-open': noteOpen }">
                    <button type="button" class="affiliate-pop-btn" :aria-expanded="noteOpen" aria-label="구매 링크 안내 보기"
                            @click="noteOpen = !noteOpen">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                            <circle cx="12" cy="12" r="10"/><path d="M12 16v-4"/><path d="M12 8h.01"/>
                        </svg>
                    </button>
                    <div v-if="noteOpen" class="affiliate-pop" role="dialog" aria-label="구매 링크 안내">
                        <p class="affiliate-pop-item">※ "구매" 링크는 제휴(알라딘) 링크로, 구매 시 일부 수수료를 받을 수 있습니다.</p>
                        <p v-if="coupangEnabled" class="affiliate-pop-item">이 포스팅은 쿠팡 파트너스 활동의 일환으로, 이에 따른 일정액의 수수료를 제공받습니다.</p>
                        <p v-if="yes24Enabled" class="affiliate-pop-item">Yes24 "구매" 링크는 제휴 링크로, 구매 시 일부 수수료를 받을 수 있습니다.</p>
                        <p v-if="kyoboEnabled" class="affiliate-pop-item">교보문고 "구매" 링크는 제휴 링크로, 구매 시 일부 수수료를 받을 수 있습니다.</p>
                    </div>
                </span>
            </div>
            <div v-if="noteOpen" class="affiliate-pop-backdrop" @click="noteOpen = false"></div>
        </div>

        <!-- 완독 정렬 — 완독(FINISHED) 필터에서만 노출(다른 상태엔 완독 시각이 없어 무의미).
             정렬 로직은 서버(sort 파라미터), 여기는 emit만(상태필터와 동일 소유 구조). -->
        <nav v-if="shelfFilter === 'FINISHED'" class="shop-filter shop-sort" aria-label="완독 정렬">
            <button v-for="o in SORT_OPTIONS" :key="o.label" type="button"
                    :class="{ active: shelfSort === o.value }"
                    @click="$emit('selectSort', o.value)">{{ o.label }}</button>
        </nav>

        <p v-if="books.length === 0 && shelfFilter === null" class="shop-empty">아직 공개한 책이 없습니다.</p>
        <p v-else-if="books.length === 0" class="shop-empty">이 상태의 공개 책이 없습니다.</p>
        <ul v-else class="shop-books">
            <li v-for="b in books" :key="b.id" class="shop-book">
                <!-- 표지 발광 — 24시간 안에 여백에 새 글이 달린 책. 색만으로 구분 못 하는 사람을 위해
                     점 배지에 텍스트 라벨을 병기한다(움직임 정지 시에도 배지는 남는다 — app.css). -->
                <span class="shop-cover-wrap" :class="{ 'margin-fresh': hasFreshStory(b.lastStoryAt, now) }">
                    <img v-if="hasCover(b.coverUrl)" class="shop-cover" :src="b.coverUrl!"
                         alt="" loading="lazy" referrerpolicy="no-referrer">
                    <div v-else class="shop-cover shop-cover-ph" aria-hidden="true">{{ coverInitial(b.title) }}</div>
                    <span v-if="hasFreshStory(b.lastStoryAt, now)" class="margin-fresh-dot">
                        <span class="sr-only">새 글</span>
                    </span>
                </span>
                <div class="shop-book-meta">
                    <span class="shop-book-title">{{ b.title }}</span>
                    <span v-if="b.author" class="shop-book-author">{{ b.author }}</span>
                    <div class="shop-book-foot">
                        <span class="shop-badge" :class="statusBadgeClass(b.status)">{{ b.status }}</span>
                        <span v-if="formatReadingTime(b.seconds)" class="shop-time">
                            <ShopIcon name="clock" :size="13" />{{ formatReadingTime(b.seconds) }}
                        </span>
                        <!-- 「여백」 진입 — 본인은 전 책(작성 진입), 남의 책방은 글이 있는 책만.
                             프라이버시 게이트는 서버가 이미 했다(비공개 책은 목록에 없다). -->
                        <button v-if="showMarginHandle(self, b.lastStoryAt)" type="button" class="shop-margin-btn"
                                :aria-label="b.title + ' 여백 보기'" @click="$emit('openMargin', b)">여백</button>
                    </div>
                    <!-- 구매 — 활성 제공자 리스트(알라딘/쿠팡/Yes24). 2개↑면 드롭다운, 1개면 제공자명 단일 버튼.
                         리다이렉트 경유, 외부링크 nofollow(§4). -->
                    <div v-if="!self && buyOptions(b).length > 0" class="shop-buy-row">
                        <details v-if="buyOptions(b).length > 1" class="shop-buy-menu">
                            <summary>구매<ShopIcon name="chevron" :size="12" class="shop-buy-caret" /></summary>
                            <div class="shop-buy-menu-items">
                                <a v-for="opt in buyOptions(b)" :key="opt.label" :href="opt.href"
                                   target="_blank" rel="noopener nofollow">{{ opt.label }}<ShopIcon name="external" :size="12" /></a>
                            </div>
                        </details>
                        <a v-else :href="buyOptions(b)[0].href"
                           target="_blank" rel="noopener nofollow" class="shop-buy">{{ buyOptions(b)[0].label }}<ShopIcon name="external" :size="13" /></a>
                    </div>
                </div>
            </li>
        </ul>

        <!-- 제휴 고지(법적 필수)는 상단 헤드 우상단 ⓘ 팝오버로 이전(하단 상시 노출 폐지). -->
    </div>
</template>
