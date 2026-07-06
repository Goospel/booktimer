<script setup lang="ts">
// 온보딩 실시간 미리보기 카드 (발견 12) — "다른 사람에게 이렇게 보여요".
// 아이디(@핸들)와 닉네임을 입력하는 즉시 정제된 최종 모습으로 보여줘, 소셜 가입자가 불변인
// 아이디를 확정 전에 확인하게 한다(발견 11 앰버 경고와 짝). 로컬 가입자는 아이디칸이 없어 닉네임만.
import { computed } from 'vue';
import { sanitizeLoginId, isValidLoginId } from './loginIdPreview';

const props = defineProps<{
    loginId: string;
    nickname: string;
    needsLoginId: boolean;
}>();

const handle = computed(() => sanitizeLoginId(props.loginId));
const handleValid = computed(() => isValidLoginId(handle.value));
const displayName = computed(() => props.nickname.trim() || '나');
// 표시 이니셜 — 아바타 자리 채움(닉네임 첫 글자, 없으면 책 이모지 폴백).
const initial = computed(() => Array.from(displayName.value)[0] ?? '📖');
</script>

<template>
    <div class="onb-preview" aria-live="polite">
        <p class="onb-preview-label">이렇게 보여요</p>
        <div class="onb-preview-card">
            <span class="onb-preview-avatar" aria-hidden="true">{{ initial }}</span>
            <div class="onb-preview-meta">
                <span class="onb-preview-name">{{ displayName }}</span>
                <span v-if="needsLoginId"
                      class="onb-preview-handle"
                      :class="{ 'is-muted': !handleValid }">{{ handle ? '@' + handle : '@아이디' }}</span>
            </div>
        </div>
        <p v-if="needsLoginId && handle && !handleValid" class="onb-preview-hint">
            아이디는 3자 이상이어야 해요.
        </p>
    </div>
</template>
