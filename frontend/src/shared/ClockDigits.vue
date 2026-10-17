<!-- 칸 시계 — 명조 Noto Serif KR엔 고정폭 숫자가 없어(tabular-nums가 안 먹는다) 매초 바뀌는 시계가 좌우로 흔들린다.
     숫자만 한 자씩 1ch 칸(app.css `.clock-d`)에 세우고, 칸마다 읽으면 「0, 1, …」로 끊기므로 읽기 이름은 그릇이 글자 그대로 든다.
     미니앱 Home.tsx HeroClock과 같은 처방. 템플릿은 한 줄이어야 한다 — 칸 사이 공백 텍스트가 생기면 폭과 text()가 어긋난다. -->
<script setup lang="ts">
defineProps<{ text: string }>()
const isDigit = (ch: string) => ch >= '0' && ch <= '9'
</script>

<template>
    <span class="clock-digits" role="timer" :aria-label="text"><span v-for="(ch, i) in text" :key="i" aria-hidden="true" :class="isDigit(ch) ? 'clock-d' : undefined">{{ ch }}</span></span>
</template>
