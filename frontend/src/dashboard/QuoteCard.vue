<script setup lang="ts">
import type { QuoteDto } from './types'
defineProps<{ quote: QuoteDto }>()
</script>

<template>
    <p class="greeting quote">
        <span class="quote-text">{{ quote.text }}</span>
        <span class="quote-author">— {{ quote.author }}</span>
    </p>
</template>
