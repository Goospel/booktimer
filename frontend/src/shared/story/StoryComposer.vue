<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { BG_CODES, canSubmit, remainingChars, type BgCode } from './storyFeed'
import { createStory } from './storyApi'

// 글 남기기 모달 — 500자 카운터·배경 팔레트 스와치 6색.
// 진입점이 이미 그 책이라 고를 것이 없다(옛 첨부 select와 /api/books 조회는 삭제 — 2026-08-16 재설계).
const props = defineProps<{ bookId: number; bookTitle: string }>()
const emit = defineEmits<{
    (e: 'close'): void
    (e: 'created'): void
}>()

const text = ref('')
const bgCode = ref<BgCode>('paper')
const submitting = ref(false)
const error = ref('')
const inputEl = ref<HTMLTextAreaElement | null>(null)

const submittable = computed(() => canSubmit(text.value) && !submitting.value)

// 포커스를 모달 안으로 — 없으면 @keydown.esc가 이벤트를 못 받아 Esc 무반응(리뷰 파인딩)
onMounted(() => inputEl.value?.focus())

async function submit() {
    if (!submittable.value) return
    submitting.value = true
    error.value = ''
    try {
        const res = await createStory({ text: text.value.trim(), bookId: props.bookId, bgCode: bgCode.value })
        if (res.ok) {
            emit('created')
            emit('close')
            return
        }
        // 상태코드 분기 — reason 문자열은 클라이언트에 안 내려오므로 하드코딩(집 관례)
        if (res.status === 429) {
            error.value = '글을 너무 자주 남겼어요. 잠시 후 다시 시도해 주세요.'
        } else if (res.status === 400) {
            error.value = '글을 남기지 못했어요. 문장은 1~500자까지 쓸 수 있어요.'
        } else {
            error.value = '글을 남기지 못했어요. 잠시 후 다시 시도해 주세요.'
        }
    } catch {
        error.value = '네트워크 오류가 발생했어요.'
    } finally {
        submitting.value = false
    }
}
</script>

<template>
    <div class="story-composer-overlay" @click.self="emit('close')" @keydown.esc="emit('close')" tabindex="-1">
        <div class="story-composer-panel" role="dialog" aria-modal="true" aria-labelledby="story-composer-title">
            <div class="story-composer-head">
                <p id="story-composer-title" class="story-composer-title">여백에 글 남기기</p>
                <button type="button" class="margin-close" aria-label="닫기" @click="emit('close')">✕</button>
            </div>

            <p class="story-composer-hint">《{{ bookTitle }}》의 여백</p>

            <!-- 미리보기 겸 입력 — 선택한 배경 위에 바로 쓴다 -->
            <div class="story-composer-preview" :class="'story-bg-' + bgCode">
                <textarea ref="inputEl" v-model="text" class="story-composer-input" rows="6" maxlength="500"
                          placeholder="읽다가 마음에 걸린 문장이나 생각을 남겨 보세요."></textarea>
            </div>
            <div class="story-composer-meta">
                <span class="story-composer-count" :class="{ over: remainingChars(text) < 0 }">
                    {{ remainingChars(text) }}자 남음
                </span>
            </div>

            <!-- 배경 팔레트 — 닫힌 코드 6색(백엔드 Story.BG_CODES와 일치) -->
            <div class="story-composer-swatches" role="radiogroup" aria-label="배경 색">
                <button v-for="code in BG_CODES" :key="code" type="button"
                        class="story-swatch" :class="['story-bg-' + code, { selected: bgCode === code }]"
                        role="radio" :aria-checked="bgCode === code" :aria-label="'배경 ' + code"
                        @click="bgCode = code"></button>
            </div>

            <div v-if="error" class="alert alert-error">{{ error }}</div>

            <button type="button" class="dash-btn-fill story-composer-submit"
                    :disabled="!submittable" @click="submit">남기기</button>
        </div>
    </div>
</template>
