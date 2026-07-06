<script setup lang="ts">
import { computed, ref } from 'vue'
import type { CatalogDto } from './types'
import { visibleAuthors, centeredIndex } from './timerProgress'

const props = defineProps<{ garden: CatalogDto }>()

// affection/level/title은 대시보드에서 0 고정이라 참조 금지 — name·emoji·spriteId만.
// 입주한 작가 전체를 가로 스크롤 갤러리로. 사용자가 직접 넘기며(휠·드래그·터치)
// 중앙에 온 작가 이름을 상단에 한 줄로 보여준다(발견 5: 2.5초 자동 스크롤 폐지).
const residents = computed(() => visibleAuthors(props.garden.ownedCharacters))

const stageEl = ref<HTMLElement | null>(null)

// 가운데(중앙)에 온 작가 인덱스 + 그 이름. 사용자가 스크롤할 때마다 onScroll이 갱신해
// 작가가 중앙을 지날 때 상단 이름도 함께 바뀐다.
const focusIdx = ref(0)
const focusName = computed(() => residents.value[focusIdx.value]?.name ?? '')

// 한 칸 너비 = 첫 작가 카드 폭 + gap. 측정 실패 시 0 → centeredIndex가 0으로 가드.
function measureStep(el: HTMLElement): number {
    const first = el.querySelector('.dash-garden-resident') as HTMLElement | null
    if (!first) return 0
    const gap = parseFloat(getComputedStyle(el).columnGap || '0') || 0
    return first.offsetWidth + gap
}

function onScroll() {
    const el = stageEl.value
    if (!el) return
    focusIdx.value = centeredIndex(el.scrollLeft, measureStep(el), residents.value.length)
}
</script>

<template>
    <section class="dash-card dash-garden">
        <div class="dash-garden-head">
            <span class="dash-pill">서재</span>
            <a class="dash-garden-link" href="/village">서재 가기 →</a>
        </div>

        <div class="dash-garden-stage-wrap">
            <div class="dash-garden-ground" aria-hidden="true"></div>
            <!-- 상단 중앙: 지금 가운데에 온 작가 이름(사용자 스크롤 따라 바뀜). 한 명만 표시라 잘릴 일 적음. -->
            <div v-if="residents.length > 0" class="dash-garden-focus-name" aria-live="polite">{{ focusName }}</div>
            <div ref="stageEl" class="dash-garden-stage" :class="{ 'is-empty': residents.length === 0 }"
                 @scroll="onScroll">
                <div v-for="(a, i) in residents" :key="a.code ?? i" class="dash-garden-resident"
                     :title="a.name" :aria-label="a.name">
                    <svg v-if="a.spriteId" class="dash-garden-sprite" aria-hidden="true">
                        <use :href="'#sprite-' + a.spriteId"></use>
                    </svg>
                    <span v-else class="dash-garden-resident-emoji" aria-hidden="true">{{ a.emoji }}</span>
                </div>
                <span v-if="residents.length === 0" class="dash-garden-empty">아직 입주한 작가가 없어요</span>
            </div>
        </div>

        <p class="dash-garden-summary">
            작가 {{ garden.ownedAuthorCharacterCount }}/{{ garden.totalAuthorCharacterCount }}
        </p>
    </section>
</template>
