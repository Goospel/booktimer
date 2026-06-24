<script setup lang="ts">
import { avatarInitial } from './timerProgress'
defineProps<{ loginId: string }>()
</script>

<template>
    <div class="dash-header-user">
        <div class="dash-header-text">
            <span class="dash-header-name"><strong>{{ loginId }}</strong> 님</span>
            <span class="dash-header-sub">오늘도 만나서 반가워요</span>
        </div>
        <div class="dash-header-avatar" aria-hidden="true">{{ avatarInitial(loginId) }}</div>
    </div>
</template>
