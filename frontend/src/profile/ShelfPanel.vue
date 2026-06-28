<script setup lang="ts">
import ShopIcon from './ShopIcon.vue'
import { hasCover, coverInitial, statusBadgeClass, formatReadingTime } from './format'

// 공개한 책 패널 — 상태필터칩 + 책 리스트 + 구매 + 제휴 고지. 모바일 탭·와이드 메인 공유.
// 데이터/액션 로직은 ProfileApp이 소유(필터는 selectStatus emit). showTitle은 와이드에서만
// "공개한 책" 소제목을 띄우는 presentational 플래그(모바일은 탭 라벨이 대신).
interface BookSummary {
    id: number; title: string; author: string | null; coverUrl: string | null;
    status: string; seconds: number; purchaseLink: string | null;
}

const STATUS_OPTIONS = [
    { value: 'READING',      label: '읽는 중' },
    { value: 'FINISHED',     label: '완독' },
    { value: 'WANT_TO_READ', label: '읽고 싶음' },
]

defineProps<{
    books: BookSummary[]
    shelfFilter: string | null
    self: boolean
    coupangEnabled: boolean
    loginId: string
    showTitle?: boolean
}>()
defineEmits<{ (e: 'selectStatus', value: string | null): void }>()
</script>

<template>
    <div class="shop-shelf">
        <div class="shop-shelf-head">
            <h2 v-if="showTitle" class="shop-shelf-title">공개한 책</h2>
            <nav class="shop-filter" aria-label="공개한 책 상태 필터">
                <button type="button" :class="{ active: shelfFilter === null }"
                        @click="$emit('selectStatus', null)">전체</button>
                <button v-for="s in STATUS_OPTIONS" :key="s.value" type="button"
                        :class="{ active: shelfFilter === s.value }"
                        @click="$emit('selectStatus', s.value)">{{ s.label }}</button>
            </nav>
        </div>

        <p v-if="books.length === 0 && shelfFilter === null" class="shop-empty">아직 공개한 책이 없습니다.</p>
        <p v-else-if="books.length === 0" class="shop-empty">이 상태의 공개 책이 없습니다.</p>
        <ul v-else class="shop-books">
            <li v-for="b in books" :key="b.id" class="shop-book">
                <img v-if="hasCover(b.coverUrl)" class="shop-cover" :src="b.coverUrl!"
                     alt="" loading="lazy" referrerpolicy="no-referrer">
                <div v-else class="shop-cover shop-cover-ph" aria-hidden="true">{{ coverInitial(b.title) }}</div>
                <div class="shop-book-meta">
                    <span class="shop-book-title">{{ b.title }}</span>
                    <span v-if="b.author" class="shop-book-author">{{ b.author }}</span>
                    <div class="shop-book-foot">
                        <span class="shop-badge" :class="statusBadgeClass(b.status)">{{ b.status }}</span>
                        <span v-if="formatReadingTime(b.seconds)" class="shop-time">
                            <ShopIcon name="clock" :size="13" />{{ formatReadingTime(b.seconds) }}
                        </span>
                    </div>
                    <!-- 구매 — other + (제휴링크 or 쿠팡). 둘 다면 드롭다운(구매처 추가 대비),
                         하나면 단일 링크. 리다이렉트 경유, 외부링크 nofollow(§4) — 현행 로직 유지. -->
                    <div v-if="!self && (b.purchaseLink || coupangEnabled)" class="shop-buy-row">
                        <details v-if="b.purchaseLink && coupangEnabled" class="shop-buy-menu">
                            <summary>구매<ShopIcon name="chevron" :size="12" class="shop-buy-caret" /></summary>
                            <div class="shop-buy-menu-items">
                                <a :href="`/u/${loginId}/books/${b.id}/buy`"
                                   target="_blank" rel="noopener nofollow">알라딘<ShopIcon name="external" :size="12" /></a>
                                <a :href="`/u/${loginId}/books/${b.id}/buy/coupang`"
                                   target="_blank" rel="noopener nofollow">쿠팡<ShopIcon name="external" :size="12" /></a>
                            </div>
                        </details>
                        <a v-else-if="b.purchaseLink && !coupangEnabled" :href="`/u/${loginId}/books/${b.id}/buy`"
                           target="_blank" rel="noopener nofollow" class="shop-buy">구매<ShopIcon name="external" :size="13" /></a>
                        <a v-else-if="!b.purchaseLink && coupangEnabled" :href="`/u/${loginId}/books/${b.id}/buy/coupang`"
                           target="_blank" rel="noopener nofollow" class="shop-buy">쿠팡<ShopIcon name="external" :size="13" /></a>
                    </div>
                </div>
            </li>
        </ul>

        <!-- 제휴 고지(법적 필수) — other + 책 1권↑. 쿠팡 고지는 coupangEnabled일 때만. -->
        <template v-if="books.length > 0 && !self">
            <p class="shop-affiliate">
                ※ "구매" 링크는 제휴(알라딘) 링크로, 구매 시 일부 수수료를 받을 수 있습니다.
            </p>
            <p v-if="coupangEnabled" class="shop-affiliate">
                이 포스팅은 쿠팡 파트너스 활동의 일환으로, 이에 따른 일정액의 수수료를 제공받습니다.
            </p>
        </template>
    </div>
</template>
