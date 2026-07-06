<script setup lang="ts">
// 하루 목표 프리셋 칩 [20][30][60][직접] (발견 10) — 작게 시작하도록 안내하고 0분 입력을 밀어낸다.
// 칩은 실제 목표 input(SSR th:field)의 값을 바꾸는 조력자 — 값의 단일 소스는 여전히 그 input이다.
// 프리셋 선택 → 그 분(minutes)을 emit, '직접' → null(커스텀 입력 모드)을 emit해 부모가 input에 반영한다.
import { ref } from 'vue';

const props = defineProps<{ initial: number }>();
const emit = defineEmits<{ (e: 'pick', minutes: number | null): void }>();

const PRESETS = [20, 30, 60] as const;
// 초기 목표가 프리셋과 일치하면 그 칩을, 아니면 '직접'(null)을 활성으로 시작한다.
const selected = ref<number | null>(
    (PRESETS as readonly number[]).includes(props.initial) ? props.initial : null,
);

function pick(minutes: number) {
    selected.value = minutes;
    emit('pick', minutes);
}
function custom() {
    selected.value = null;
    emit('pick', null);
}
</script>

<template>
    <div class="onb-presets" role="group" aria-label="하루 목표 빠른 선택">
        <button v-for="m in PRESETS" :key="m" type="button" class="onb-preset-chip"
                :class="{ 'is-active': selected === m }" :aria-pressed="selected === m"
                @click="pick(m)">{{ m }}분</button>
        <button type="button" class="onb-preset-chip"
                :class="{ 'is-active': selected === null }" :aria-pressed="selected === null"
                @click="custom">직접</button>
    </div>
</template>
