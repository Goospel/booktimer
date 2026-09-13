<script setup lang="ts">
import type { TimerMode } from './timerMode'

defineProps<{ mode: TimerMode; locked: boolean; hint?: string | null }>()
const emit = defineEmits<{ change: [mode: TimerMode]; blocked: [] }>()

// 측정 중에도 진짜 disabled로 두지 않는다 — 클릭이 와야 "왜 못 바꾸는지"를 말할 기회가 생긴다.
function pick(next: TimerMode, current: TimerMode, locked: boolean) {
    if (locked) { emit('blocked'); return }
    if (next !== current) emit('change', next)
}
</script>

<template>
    <div class="dash-mode-toggle-wrap">
        <div class="dash-mode-toggle" :class="{ 'is-locked': locked }" role="group" aria-label="타이머 모드">
            <button type="button" :aria-pressed="mode === 'reading'" :aria-disabled="locked || undefined"
                    @click="pick('reading', mode, locked)">독서</button>
            <button type="button" :aria-pressed="mode === 'study'" :aria-disabled="locked || undefined"
                    @click="pick('study', mode, locked)">공부</button>
        </div>
        <span v-if="hint" class="dash-mode-hint" role="status">{{ hint }}</span>
    </div>
</template>
