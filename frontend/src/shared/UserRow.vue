<script setup lang="ts">
import type { UserRowData } from './follow';
withDefaults(defineProps<{ user: UserRowData; linkProfile?: boolean }>(), { linkProfile: true });
</script>

<template>
    <li class="book-row">
        <div class="book-meta">
            <a v-if="linkProfile" class="book-title" :href="`/u/${user.loginId}`">{{ user.nickname }}</a>
            <span v-else class="book-title">{{ user.nickname }}</span>
            <span class="book-author">@{{ user.loginId }} · 공개 책 {{ user.publicBookCount }}권</span>
        </div>
        <div class="book-actions">
            <slot :user="user" />
        </div>
    </li>
</template>
