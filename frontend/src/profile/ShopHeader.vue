<script setup lang="ts">
import ShopIcon from './ShopIcon.vue'
import { avatarInitial } from '../dashboard/timerProgress'

// 책방 정체성 헤더 카드 — 아바타·책방명·핸들·책BTI 태그칩·팔로우 카운트·(other)액션.
// 모바일 상단·와이드 좌 사이드바 양쪽에서 공유(§7 DRY 추출). self/other 분기를 한 곳에.
// 데이터/액션은 ProfileApp 소유 — 태그 클릭·팔로우·차단은 emit으로 올린다.
interface TagChip { label: string; clickable: boolean }

defineProps<{
    nickname: string
    loginId: string
    personalityTags: TagChip[]
    followerCount: number
    followingCount: number
    self: boolean
    following: boolean
}>()
defineEmits<{
    (e: 'openTag', label: string): void
    (e: 'toggleFollow'): void
    (e: 'doBlock'): void
}>()
</script>

<template>
    <div class="dash-card shop-header-card">
        <!-- 정체성: 이니셜 아바타(대시보드 헤더와 동일 42px 세이지 원) + 책방명/핸들 -->
        <div class="shop-id-head">
            <div class="dash-header-avatar shop-avatar" aria-hidden="true">{{ avatarInitial(loginId) }}</div>
            <div class="shop-id-text">
                <div class="shop-id-title">{{ nickname }}님의 책방</div>
                <div class="shop-id-handle">@{{ loginId }}</div>
            </div>
        </div>

        <!-- 책BTI 태그칩 — clickable이면 드릴다운 버튼, 아니면 정적 칩 -->
        <div v-if="personalityTags.length > 0" class="shop-tags">
            <template v-for="t in personalityTags" :key="t.label">
                <button v-if="t.clickable" type="button" class="shop-tag shop-tag-click"
                        @click="$emit('openTag', t.label)">{{ t.label }}</button>
                <span v-else class="shop-tag">{{ t.label }}</span>
            </template>
        </div>

        <!-- 팔로워/팔로잉 카운트 + (other) 팔로우·차단 액션 -->
        <div class="shop-counts">
            <a v-if="self" class="shop-count" href="/me/followers">
                팔로워 <strong>{{ followerCount }}</strong>
            </a>
            <span v-else class="shop-count">팔로워 <strong>{{ followerCount }}</strong></span>

            <a v-if="self" class="shop-count" href="/me/following">
                팔로잉 <strong>{{ followingCount }}</strong>
            </a>
            <span v-else class="shop-count">팔로잉 <strong>{{ followingCount }}</strong></span>

            <span v-if="!self" class="shop-actions">
                <button type="button" :class="following ? 'dash-btn-outline' : 'dash-btn-fill'"
                        @click="$emit('toggleFollow')">
                    <ShopIcon :name="following ? 'unfollow' : 'follow'" :size="16" />{{ following ? '언팔로우' : '팔로우' }}
                </button>
                <button type="button" class="dash-btn-outline" @click="$emit('doBlock')">
                    <ShopIcon name="block" :size="16" />차단
                </button>
            </span>
        </div>
    </div>
</template>
