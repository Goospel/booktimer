<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { avatarInitial } from './timerProgress'
import { getCsrfToken } from '../shared/follow'

defineProps<{ loginId: string }>()

// 아바타 클릭 드롭다운: 설정·문의·로그아웃을 한곳으로 일원화. 토글 + 바깥클릭·Esc 닫힘.
const open = ref(false)
const root = ref<HTMLElement | null>(null)

function toggle() { open.value = !open.value }
function close() { open.value = false }

// 메뉴 바깥(루트 밖) 클릭이면 닫는다. 메뉴 내부 클릭은 @click.stop으로 여기까지 안 온다.
function onDocClick(e: MouseEvent) {
    if (root.value && !root.value.contains(e.target as Node)) close()
}
function onKeydown(e: KeyboardEvent) {
    if (e.key === 'Escape') close()
}

onMounted(() => {
    document.addEventListener('click', onDocClick)
    document.addEventListener('keydown', onKeydown)
})
onUnmounted(() => {
    document.removeEventListener('click', onDocClick)
    document.removeEventListener('keydown', onKeydown)
})
</script>

<template>
    <div ref="root" class="dash-header-user">
        <div class="dash-header-text">
            <span class="dash-header-name"><strong>{{ loginId }}</strong> 님</span>
            <span class="dash-header-sub">오늘도 만나서 반가워요</span>
        </div>

        <div class="dash-header-menu-wrap" @click.stop>
            <button type="button" class="dash-header-avatar" aria-haspopup="menu"
                    :aria-expanded="open" aria-label="사용자 메뉴" @click="toggle">
                {{ avatarInitial(loginId) }}
            </button>

            <div v-if="open" class="dash-header-menu" role="menu">
                <a class="dash-header-menu-item" role="menuitem" href="/settings">
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                        <circle cx="12" cy="12" r="3.2" /><path d="M12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3M5.1 5.1l2.1 2.1M16.8 16.8l2.1 2.1M18.9 5.1l-2.1 2.1M7.2 16.8l-2.1 2.1" />
                    </svg>
                    <span>설정</span>
                </a>
                <!-- 차단 목록: 자주 안 쓰는 계정 관리라 책방 하단 상시 버튼에서 이 사용자 메뉴로 이동.
                     설정 다음(계정 관리끼리)·로그아웃 앞. block 아이콘(금지원, navIcons와 동일 모양). -->
                <a class="dash-header-menu-item" role="menuitem" href="/me/blocks">
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                        <circle cx="12" cy="12" r="8.5" /><path d="M6 6l12 12" />
                    </svg>
                    <span>차단 목록</span>
                </a>
                <a class="dash-header-menu-item" role="menuitem" href="/feedback">
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                        <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
                    </svg>
                    <span>문의</span>
                </a>
                <form class="dash-header-menu-logout" action="/logout" method="post">
                    <input type="hidden" name="_csrf" :value="getCsrfToken()">
                    <button type="submit" class="dash-header-menu-item" role="menuitem">
                        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                            <path d="M15 17l5-5-5-5" /><path d="M20 12H9" /><path d="M9 4H5v16h4" />
                        </svg>
                        <span>로그아웃</span>
                    </button>
                </form>
            </div>
        </div>
    </div>
</template>
