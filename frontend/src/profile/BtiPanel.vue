<script setup lang="ts">
import ShopIcon from './ShopIcon.vue'
import { hasCover, coverInitial } from './format'

// 책BTI 패널 — 서술/콜드스타트 + 태그 드릴다운. 모바일 탭·와이드 사이드바 양쪽에서 공유.
// 태그칩 자체는 헤더 카드(ProfileApp)가 소유하고 openTag를 호출 → tagPanel/tagLabel을
// 여기로 흘려 드릴다운을 그린다. 닫기만 emit.
interface BookSummary {
    id: number; title: string; author: string | null; coverUrl: string | null;
    status: string; seconds: number; purchaseLink: string | null;
}

defineProps<{
    personality: string | null
    self: boolean
    tagPanel: BookSummary[] | null
    tagLabel: string
}>()
defineEmits<{ (e: 'closeTag'): void }>()
</script>

<template>
    <div class="shop-bti">
        <!-- 서술 or 콜드스타트 -->
        <template v-if="personality">
            <p class="shop-bti-text">{{ personality }}</p>
            <p class="shop-bti-note">
                {{ self ? '내 공개 책 기준으로 분석했어요.' : '이 사람이 공개한 책 기준이에요.' }}
                MBTI처럼 가볍게 즐겨 주세요.
            </p>
        </template>
        <p v-else class="shop-bti-cold">
            {{ self
                ? '아직 공개 완독 책이 부족해 책BTI가 분석되지 않았어요. 공개 완독 책이 쌓이면 자동으로 분석돼요.'
                : '이 사람은 아직 책BTI가 없어요. 공개 완독 책이 쌓이면 분석돼요.' }}
        </p>

        <!-- 태그 드릴다운(근거책) — seconds 항상 0이라 시간/구매 미표시(현행 로직 유지) -->
        <div v-if="tagPanel" class="shop-drill">
            <div class="shop-drill-head">
                <h3>「{{ tagLabel }}」를 만든 책 {{ tagPanel.length }}권</h3>
                <button type="button" class="shop-drill-close" @click="$emit('closeTag')">
                    <ShopIcon name="close" :size="14" />닫기
                </button>
            </div>
            <p v-if="tagPanel.length === 0" class="shop-empty">해당하는 공개 완독 책이 없어요.</p>
            <ul v-else class="shop-books shop-drill-books">
                <li v-for="b in tagPanel" :key="b.id" class="shop-book">
                    <img v-if="hasCover(b.coverUrl)" class="shop-cover" :src="b.coverUrl!"
                         alt="" loading="lazy" referrerpolicy="no-referrer">
                    <div v-else class="shop-cover shop-cover-ph" aria-hidden="true">{{ coverInitial(b.title) }}</div>
                    <div class="shop-book-meta">
                        <span class="shop-book-title">{{ b.title }}</span>
                        <span v-if="b.author" class="shop-book-author">{{ b.author }}</span>
                    </div>
                </li>
            </ul>
        </div>
    </div>
</template>
