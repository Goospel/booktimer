<script setup lang="ts">
import { ref } from 'vue';
import NavLinks from '../shared/NavLinks.vue';
import UserSearchPanel from '../shared/UserSearchPanel.vue';

// 얇은 셸 — 검색·추천 본체는 shared/UserSearchPanel(내 책방 self 뷰와 공유).
// 여기선 page-stack·greeting·하단 네비만 소유. myLoginId는 '내 책방' 링크용.
const appEl = document.getElementById('search-app');
const myLoginId = ref(appEl?.dataset.myLoginId ?? '');
const initialQ = appEl?.dataset.initialQ ?? '';
</script>

<template>
    <div class="page-stack">
        <p class="greeting">사용자 검색</p>
        <UserSearchPanel :initial-q="initialQ" />
        <NavLinks :links="[
            { href: '/', icon: 'home', label: '홈' },
            { href: `/u/${myLoginId}`, icon: 'user', label: '내 책방' },
        ]" />
    </div>
</template>
