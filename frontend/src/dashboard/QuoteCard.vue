<script setup lang="ts">
import type { QuoteDto } from './types'
defineProps<{ quote: QuoteDto }>()
</script>

<template>
    <section class="dash-card dash-quote-card">
        <p class="dash-quote-text">{{ quote.text }}</p>
        <span class="dash-quote-author">— {{ quote.author }}</span>
    </section>
</template>
