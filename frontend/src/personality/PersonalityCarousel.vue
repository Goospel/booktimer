<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue';

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

// 한 칸 = 첫 슬라이드 폭 + gap (personality.js step() 로직 이관)
function step(): number {
    const track = trackRef.value;
    if (!track) return 0;
    const slide = track.querySelector('.personality-slide') as HTMLElement | null;
    if (!slide) return track.clientWidth;
    const gap = parseFloat(getComputedStyle(track).columnGap) || 0;
    return slide.getBoundingClientRect().width + gap;
}

// 양끝·단일 카드 상태 반영 (personality.js sync() 로직 이관)
function sync() {
    const wrap = wrapRef.value;
    const track = trackRef.value;
    const prev = prevRef.value;
    const next = nextRef.value;
    if (!wrap || !track || !prev || !next) return;
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
    <div class="personality-carousel-wrap" ref="wrapRef">
        <button type="button" class="carousel-nav carousel-nav-prev" ref="prevRef"
                aria-label="이전 분석" @click="prev">‹</button>

        <div class="personality-carousel" ref="trackRef" tabindex="0" role="group"
             aria-label="독서 성향 분석 — 좌우로 넘겨 보기">
            <div v-for="entry in entries" :key="entry.id"
                 class="personality-entry personality-slide"
                 :class="{ 'personality-entry-selected': entry.selected }">
                <div class="personality-entry-head">
                    <span class="personality-badge">{{ entry.selected ? '⭐ 대표 (책방 노출)' : '후보' }}</span>
                    <span class="personality-time">{{ entry.generatedAtLabel }}</span>
                </div>
                <p class="personality-narrative">{{ entry.narrative }}</p>
                <p v-if="entry.stale" class="personality-stale">
                    📚 이 분석 이후 책장이 바뀌었어요 — '다시 분석'하면 지금 책장 기준으로 새로 봐요.
                </p>
                <button v-if="!entry.selected" type="button" class="btn-ghost btn-small"
                        @click="emit('select', entry.id)">이걸로 대표 선택</button>
            </div>
        </div>

        <button type="button" class="carousel-nav carousel-nav-next" ref="nextRef"
                aria-label="다음 분석" @click="next">›</button>
    </div>
</template>
