<script setup lang="ts">
import type { UserRowData } from './follow';
defineProps<{ user: UserRowData }>();
</script>

<template>
    <li class="book-row">
        <div class="book-meta">
            <a class="book-title" :href="`/u/${user.loginId}`">{{ user.nickname }}</a>
            <span class="book-author">@{{ user.loginId }} · 공개 책 {{ user.publicBookCount }}권</span>
        </div>
        <div class="book-actions">
            <slot :user="user" />
        </div>
    </li>
</template>
