<script setup lang="ts">
import { getCsrfToken } from '../shared/follow'
const csrf = getCsrfToken()
</script>

<template>
    <div class="alert alert-warn verify-banner">
        <span>이메일 인증이 아직 안 됐어요. 받은 메일의 링크로 인증을 완료해 주세요.</span>
        <form action="/verify-email/resend" method="post">
            <input type="hidden" name="_csrf" :value="csrf">
            <button type="submit" class="btn-inline-link">인증 메일 다시 받기</button>
        </form>
    </div>
</template>
