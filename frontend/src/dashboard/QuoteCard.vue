<script setup lang="ts">
import type { QuoteDto } from './types'
defineProps<{ quote: QuoteDto }>()
</script>

<template>
    <section class="dash-card dash-quote-card">
        <span class="dash-pill">오늘의 문장</span>
        <p class="dash-quote-text">{{ quote.text }}</p>
        <span class="dash-quote-author">— {{ quote.author }}</span>
    </section>
</template>
