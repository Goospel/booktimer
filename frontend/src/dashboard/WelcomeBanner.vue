<script setup lang="ts">
defineProps<{ nickname: string }>()
defineEmits<{ close: [] }>()
</script>

<template>
    <div class="alert welcome-banner">
        <div class="welcome-banner-body">
            <strong>환영합니다, {{ nickname }}님!</strong>
            <span>아래에서 <b>첫 독서 측정</b>을 시작해 오늘의 독서를 기록해보세요.</span>
        </div>
        <button type="button" class="welcome-banner-close" @click="$emit('close')" aria-label="닫기">✕</button>
    </div>
</template>
