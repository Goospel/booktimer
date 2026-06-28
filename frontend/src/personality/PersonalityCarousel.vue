<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue';
import { dotIndex } from './personalityView';

export interface EntryDto {
    id: number;
    narrative: string;
    tags: string[];
    generatedAt: string | null;
    generatedAtLabel: string;
    selected: boolean;
    stale: boolean;
}

const props = defineProps<{ entries: EntryDto[] }>();
const emit = defineEmits<{ select: [id: number] }>();

const wrapRef = ref<HTMLElement | null>(null);
const trackRef = ref<HTMLElement | null>(null);
const prevRef = ref<HTMLButtonElement | null>(null);
const nextRef = ref<HTMLButtonElement | null>(null);

// 현재 중앙 슬라이드(도트 활성). 스크롤·리사이즈마다 dotIndex로 역산.
const currentIndex = ref(0);

// 한 칸 = 첫 슬라이드 폭 + gap (personality.js step() 로직 이관)
function step(): number {
    const track = trackRef.value;
    if (!track) return 0;
    const slide = track.querySelector('.personality-slide') as HTMLElement | null;
    if (!slide) return track.clientWidth;
    const gap = parseFloat(getComputedStyle(track).columnGap) || 0;
    return slide.getBoundingClientRect().width + gap;
}

// 양끝·단일 카드 상태 반영 (personality.js sync() 로직 이관) + 도트 인덱스 갱신
function sync() {
    const wrap = wrapRef.value;
    const track = trackRef.value;
    const prev = prevRef.value;
    const next = nextRef.value;
    if (!wrap || !track || !prev || !next) return;
    currentIndex.value = dotIndex(track.scrollLeft, step(), props.entries.length);
    const overflow = track.scrollWidth - track.clientWidth;
    if (overflow <= 1) {
        wrap.classList.add('nav-hidden');
        return;
    }
    wrap.classList.remove('nav-hidden');
    prev.disabled = track.scrollLeft <= 1;
    next.disabled = track.scrollLeft >= overflow - 1;
}

let ticking = false;
function onScroll() {
    if (ticking) return;
    ticking = true;
    requestAnimationFrame(() => { ticking = false; sync(); });
}

onMounted(async () => {
    await nextTick();
    const track = trackRef.value;
    if (track) track.addEventListener('scroll', onScroll);
    window.addEventListener('resize', sync);
    sync();
});

onUnmounted(() => {
    const track = trackRef.value;
    if (track) track.removeEventListener('scroll', onScroll);
    window.removeEventListener('resize', sync);
});

function prev() {
    trackRef.value?.scrollBy({ left: -step() });
}
function next() {
    trackRef.value?.scrollBy({ left: step() });
}
</script>

<template>
    <div class="pbti-carousel-block">
        <div class="personality-carousel-wrap" ref="wrapRef">
            <button type="button" class="carousel-nav carousel-nav-prev" ref="prevRef"
                    aria-label="이전 분석" @click="prev">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M15 18l-6-6 6-6"/></svg>
            </button>

            <div class="personality-carousel" ref="trackRef" tabindex="0" role="group"
                 aria-label="독서 성향 분석 — 좌우로 넘겨 보기">
                <div v-for="entry in entries" :key="entry.id" class="personality-entry personality-slide"
                     :class="{ 'is-selected': entry.selected }">
                    <div class="personality-entry-head">
                        <span v-if="entry.selected" class="personality-badge personality-badge-rep">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 3.5l2.6 5.3 5.8.85-4.2 4.1.99 5.75L12 16.9l-5.19 2.7.99-5.75-4.2-4.1 5.8-.85z"/></svg>
                            대표 · 책방 노출
                        </span>
                        <span v-else class="personality-badge personality-badge-cand">후보</span>
                        <span class="personality-time">{{ entry.generatedAtLabel }}</span>
                    </div>
                    <p class="personality-narrative">{{ entry.narrative }}</p>
                    <div v-if="entry.stale" class="personality-stale">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>
                        <span>지난 책장 기준 분석이에요.</span>
                    </div>
                    <button v-if="!entry.selected" type="button" class="pbti-select-rep"
                            @click="emit('select', entry.id)">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 3.5l2.6 5.3 5.8.85-4.2 4.1.99 5.75L12 16.9l-5.19 2.7.99-5.75-4.2-4.1 5.8-.85z"/></svg>
                        이걸로 대표 선택
                    </button>
                    <div v-else class="pbti-selected-mark" role="status">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M8.5 12.4l2.5 2.5 4.6-5.3"/></svg>
                        선택됨
                    </div>
                </div>
            </div>

            <button type="button" class="carousel-nav carousel-nav-next" ref="nextRef"
                    aria-label="다음 분석" @click="next">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M9 18l6-6-6-6"/></svg>
            </button>
        </div>

        <div v-if="entries.length > 1" class="pbti-dots" aria-hidden="true">
            <span v-for="(e, i) in entries" :key="e.id" class="pbti-dot"
                  :class="{ 'is-active': i === currentIndex }"></span>
        </div>
    </div>
</template>
