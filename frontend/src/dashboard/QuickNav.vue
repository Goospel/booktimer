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

    <nav class="quick-actions" aria-label="바로가기">
        <a class="quick-tile" href="/sessions/manual">
            <span class="qt-icon" aria-hidden="true">✏️</span><span class="qt-label">직접 채우기</span>
        </a>
        <a class="quick-tile" href="/history">
            <span class="qt-icon" aria-hidden="true">📖</span><span class="qt-label">독서 기록</span>
        </a>
        <a class="quick-tile" href="/personality">
            <span class="qt-icon" aria-hidden="true">🧬</span><span class="qt-label">책BTI</span>
        </a>
        <a class="quick-tile" href="/search">
            <span class="qt-icon" aria-hidden="true">🔍</span><span class="qt-label">사용자 검색</span>
        </a>
        <a class="quick-tile" href="/settings">
            <span class="qt-icon" aria-hidden="true">⚙️</span><span class="qt-label">설정</span>
        </a>
        <a class="quick-tile" href="/feedback">
            <span class="qt-icon" aria-hidden="true">💬</span><span class="qt-label">문의·피드백</span>
        </a>
    </nav>
</template>
