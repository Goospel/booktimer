<script setup lang="ts">
import UserSearchPanel from '../shared/UserSearchPanel.vue';

// 얇은 셸 — 검색·추천 본체는 shared/UserSearchPanel(내 책방 self 뷰와 공유).
// 여기선 page-stack·greeting만 소유. 페이지 이동은 SSR 양옆 바(fragments/side-rails)가 맡는다.
const appEl = document.getElementById('search-app');
const initialQ = appEl?.dataset.initialQ ?? '';
</script>

<template>
    <div class="page-stack">
        <p class="greeting">사용자 검색</p>
        <UserSearchPanel :initial-q="initialQ" />
    </div>
</template>
