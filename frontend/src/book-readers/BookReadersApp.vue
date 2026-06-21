<script setup lang="ts">
import { ref, onMounted } from 'vue';
import UserRow from '../shared/UserRow.vue';
import FollowAction from '../shared/FollowAction.vue';
import type { UserRowData } from '../shared/follow';

const appEl = document.getElementById('book-readers-app');
const isbn = appEl?.dataset.isbn ?? '';
const title = appEl?.dataset.title ?? '';

const reading = ref<UserRowData[]>([]);
const wanting = ref<UserRowData[]>([]);
const loading = ref(false);

async function load() {
    if (!isbn) return;
    loading.value = true;
    try {
        const res = await fetch(`/api/book-readers?isbn=${encodeURIComponent(isbn)}`, {
            credentials: 'same-origin',
        });
        if (!res.ok) return;
        const data = await res.json();
        reading.value = data.reading;
        wanting.value = data.wanting;
    } finally {
        loading.value = false;
    }
}

onMounted(load);
</script>

<template>
    <div>
        <p class="greeting">👥 내 팔로우 중 이 책을 보는 사람</p>
        <section class="card">
            <h2>{{ title || '이 책' }}</h2>

            <p v-if="loading" class="status-line muted">불러오는 중…</p>
            <p v-else-if="!reading.length && !wanting.length" class="status-line muted">
                팔로우한 사람 중 이 책을 공개로 가진 사람이 아직 없습니다.<br>
                더 많은 사람을 <a href="/search">팔로우</a>하면 여기에서 보여요.
            </p>

            <template v-if="reading.length">
                <h3 class="reader-bucket">📖 읽는 중 · 완독</h3>
                <ul class="book-list">
                    <UserRow v-for="u in reading" :key="u.loginId" :user="u">
                        <template #default="{ user }">
                            <FollowAction :user="user" />
                        </template>
                    </UserRow>
                </ul>
            </template>

            <template v-if="wanting.length">
                <h3 class="reader-bucket">📌 읽고 싶음</h3>
                <ul class="book-list">
                    <UserRow v-for="u in wanting" :key="u.loginId" :user="u">
                        <template #default="{ user }">
                            <FollowAction :user="user" />
                        </template>
                    </UserRow>
                </ul>
            </template>
        </section>

        <p class="link-row">
            <a href="/books">← 내 책장</a>
            <a href="/search">🔍 사용자 검색</a>
        </p>
    </div>
</template>
