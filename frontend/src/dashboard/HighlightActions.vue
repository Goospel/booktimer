<script setup lang="ts">
defineProps<{ loginId: string }>()
</script>

<template>
    <nav class="highlight-actions" aria-label="둘러보기">
        <a class="highlight-tile" href="/books">
            <span class="ht-icon" aria-hidden="true">📚</span>
            <span class="ht-text">
                <span class="ht-label">내 책장</span>
                <span class="ht-sub">읽은 책을 모아보기</span>
            </span>
        </a>
        <a class="highlight-tile" :href="`/u/${loginId}`">
            <span class="ht-icon" aria-hidden="true">👤</span>
            <span class="ht-text">
                <span class="ht-label">내 책방</span>
                <span class="ht-sub">남에게 보이는 내 책방</span>
            </span>
        </a>
    </nav>
</template>
