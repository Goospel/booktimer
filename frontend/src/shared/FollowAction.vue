<script setup lang="ts">
import { toggleFollow } from './follow';
import type { UserRowData } from './follow';
defineProps<{ user: UserRowData }>();
</script>

<template>
    <span v-if="user.self" class="book-status-badge">나</span>
    <button v-else
            :class="user.following ? 'btn-ghost' : 'btn-primary'"
            type="button"
            @click="toggleFollow(user)">
        {{ user.following ? '언팔로우' : '팔로우' }}
    </button>
</template>
