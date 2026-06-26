<script setup lang="ts">
import { ref, onMounted } from 'vue';
import UserRow from '../shared/UserRow.vue';
import { setBlock } from '../shared/block';
import NavLinks from '../shared/NavLinks.vue';
import type { UserRowData } from '../shared/follow';

const appEl = document.getElementById('block-list-app');
const myLoginId = ref(appEl?.dataset.myLoginId ?? '');
const blocked = ref<UserRowData[]>([]);
const loading = ref(false);

async function load() {
    loading.value = true;
    try {
        const res = await fetch('/api/blocks', { credentials: 'same-origin' });
        if (!res.ok) return;
        const data = await res.json();
        blocked.value = data.blocked;
        if (data.myLoginId) myLoginId.value = data.myLoginId;
    } finally {
        loading.value = false;
    }
}

async function unblock(user: UserRowData) {
    const stillBlocked = await setBlock(user.loginId, false);
    if (!stillBlocked) {
        blocked.value = blocked.value.filter(u => u.loginId !== user.loginId);
    }
}

onMounted(load);
</script>

<template>
    <div class="page-stack">
        <p class="greeting">차단 목록</p>
        <section class="card">
            <h2>내가 차단한 사용자</h2>
            <p class="status-line muted">차단한 사용자와는 서로 팔로우·프로필 열람이 막힙니다. 해제하면 다시 가능합니다.</p>

            <p v-if="loading" class="status-line muted">불러오는 중…</p>
            <p v-else-if="blocked.length === 0" class="status-line">차단한 사용자가 없습니다.</p>
            <ul v-else class="book-list">
                <UserRow v-for="u in blocked" :key="u.loginId" :user="u" :link-profile="false">
                    <template #default>
                        <button class="btn-ghost" @click="unblock(u)">차단 해제</button>
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
