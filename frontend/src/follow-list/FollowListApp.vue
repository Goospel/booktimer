<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue';
import UserRow from '../shared/UserRow.vue';
import FollowAction from '../shared/FollowAction.vue';
import type { UserRowData } from '../shared/follow';
import NavLinks from '../shared/NavLinks.vue';

const appEl = document.getElementById('follow-list-app');
const myLoginId = ref(appEl?.dataset.myLoginId ?? '');
const activeTab = ref<'followers' | 'following'>(
    appEl?.dataset.initialTab === 'following' ? 'following' : 'followers'
);
const users = ref<UserRowData[]>([]);
const loading = ref(false);

async function load(tab: 'followers' | 'following') {
    loading.value = true;
    try {
        const res = await fetch(`/api/follow-list?type=${tab}`, { credentials: 'same-origin' });
        if (!res.ok) return;
        const data = await res.json();
        users.value = data.users;
    } finally {
        loading.value = false;
    }
}

function selectTab(tab: 'followers' | 'following') {
    activeTab.value = tab;
    load(tab);
    history.pushState({ tab }, '', `/me/${tab}`);
}

function onPopState(e: PopStateEvent) {
    const tab = e.state?.tab ?? 'followers';
    activeTab.value = tab;
    load(tab);
}

onMounted(() => {
    load(activeTab.value);
    window.addEventListener('popstate', onPopState);
});

onUnmounted(() => {
    window.removeEventListener('popstate', onPopState);
});
</script>

<template>
    <div class="page-stack">
        <p class="greeting">{{ activeTab === 'followers' ? '내 팔로워' : '내 팔로잉' }}</p>
        <section class="card">
            <nav class="follow-tabs">
                <a href="/me/followers"
                   :class="{ active: activeTab === 'followers' }"
                   @click.prevent="selectTab('followers')">팔로워</a>
                <a href="/me/following"
                   :class="{ active: activeTab === 'following' }"
                   @click.prevent="selectTab('following')">팔로잉</a>
            </nav>

            <p v-if="loading" class="status-line muted">불러오는 중…</p>
            <p v-else-if="users.length === 0" class="status-line">
                {{ activeTab === 'followers' ? '아직 나를 팔로우한 사람이 없습니다.' : '아직 팔로우한 사람이 없습니다.' }}
            </p>
            <ul v-else class="book-list">
                <UserRow v-for="u in users" :key="u.loginId" :user="u">
                    <template #default="{ user }">
                        <FollowAction :user="user" />
                    </template>
                </UserRow>
            </ul>
        </section>

        <NavLinks :links="[
            { href: '/', icon: 'home', label: '대시보드' },
            { href: `/u/${myLoginId}`, icon: 'user', label: '내 책방' },
        ]" />
    </div>
</template>
