<script setup lang="ts">
defineProps<{ loginId: string }>()
</script>

<template>
    <section class="dash-card dash-nav">
        <span class="dash-pill">빠른 이동</span>
        <nav class="dash-nav-grid" aria-label="빠른 이동">
            <a class="dash-nav-tile" href="/books">
                <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
                    <rect x="4" y="4" width="3.4" height="16" rx="1" /><rect x="9.3" y="4" width="3.4" height="16" rx="1" /><path d="M15 5.4l3.3-.7 2.4 15.4-3.3.7z" />
                </svg>
                <span class="dash-nav-label">내 책장</span>
            </a>
            <a class="dash-nav-tile" href="/history">
                <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M3 20h18" /><path d="M6 20v-6" /><path d="M12 20V6" /><path d="M18 20v-9" />
                </svg>
                <span class="dash-nav-label">독서 기록</span>
            </a>
            <a class="dash-nav-tile" href="/search">
                <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
                    <circle cx="11" cy="11" r="7" /><path d="M21 21l-4.3-4.3" />
                </svg>
                <span class="dash-nav-label">탐색</span>
            </a>
            <a class="dash-nav-tile" :href="`/u/${loginId}`">
                <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
                    <circle cx="12" cy="8" r="3.6" /><path d="M5 20a7 7 0 0 1 14 0" />
                </svg>
                <span class="dash-nav-label">내 책방</span>
            </a>
            <a class="dash-nav-tile" href="/personality">
                <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M7 4c0 5 10 5 10 8s-10 3-10 8" /><path d="M17 4c0 5-10 5-10 8s10 3 10 8" /><path d="M8.5 7h7" /><path d="M8.5 17h7" />
                </svg>
                <span class="dash-nav-label">책BTI</span>
            </a>
            <a class="dash-nav-tile" href="/settings">
                <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
                    <circle cx="12" cy="12" r="3.2" /><path d="M12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3M5.1 5.1l2.1 2.1M16.8 16.8l2.1 2.1M18.9 5.1l-2.1 2.1M7.2 16.8l-2.1 2.1" />
                </svg>
                <span class="dash-nav-label">설정</span>
            </a>
            <a class="dash-nav-tile" href="/feedback">
                <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
                </svg>
                <span class="dash-nav-label">문의·피드백</span>
            </a>
        </nav>
    </section>
</template>
