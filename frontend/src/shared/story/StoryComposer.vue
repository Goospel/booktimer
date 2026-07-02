<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { BG_CODES, canSubmit, remainingChars, type BgCode } from './storyFeed'
import { createStory } from './storyApi'

// 스토리 작성 모달 (sns-design §13.7) — 500자 카운터·배경 팔레트 스와치 6색·공개 책 드롭다운.
// 책 목록은 기존 내 책장 API(/api/books)를 재사용해 isPublic만 거른다(별도 엔드포인트 없음 — 계획 §3).
const emit = defineEmits<{
    (e: 'close'): void
    (e: 'created'): void
}>()

interface PublicBookOption { id: number; title: string }

const text = ref('')
const bgCode = ref<BgCode>('paper')
const bookId = ref<number | null>(null)
const books = ref<PublicBookOption[]>([])
const submitting = ref(false)
const error = ref('')
const inputEl = ref<HTMLTextAreaElement | null>(null)

const submittable = computed(() => canSubmit(text.value) && !submitting.value)

onMounted(async () => {
    // 포커스를 모달 안으로 — 없으면 @keydown.esc가 이벤트를 못 받아 Esc 무반응(리뷰 파인딩)
    inputEl.value?.focus()
    try {
        const res = await fetch('/api/books', { credentials: 'same-origin' })
        if (!res.ok) return
        const data = await res.json()
        books.value = (data.books ?? [])
            .filter((b: { isPublic: boolean }) => b.isPublic)
            .map((b: { id: number; title: string }) => ({ id: b.id, title: b.title }))
    } catch {
        // 책 목록 실패는 치명적이지 않다 — 책 없이 작성 가능
    }
})

async function submit() {
    if (!submittable.value) return
    submitting.value = true
    error.value = ''
    try {
        const res = await createStory({ text: text.value.trim(), bookId: bookId.value, bgCode: bgCode.value })
        if (res.ok) {
            emit('created')
            emit('close')
            return
        }
        // 상태코드 분기 — reason 문자열은 클라이언트에 안 내려오므로 하드코딩(집 관례)
        if (res.status === 429) {
            error.value = '작성이 너무 잦아요. 잠시 후 다시 올려주세요.'
        } else if (res.status === 400) {
            error.value = '지금은 올릴 수 없어요 — 활성 스토리가 가득 찼거나(최대 20장) 입력이 올바르지 않아요.'
        } else {
            error.value = '스토리를 올리지 못했어요. 잠시 후 다시 시도해 주세요.'
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
                <p id="story-composer-title" class="story-composer-title">스토리 올리기</p>
                <button type="button" class="story-viewer-close" aria-label="닫기" @click="emit('close')">✕</button>
            </div>

            <!-- 미리보기 겸 입력 — 선택한 배경 위에 바로 쓴다 -->
            <div class="story-composer-preview" :class="'story-bg-' + bgCode">
                <textarea ref="inputEl" v-model="text" class="story-composer-input" rows="6" maxlength="500"
                          placeholder="인상 깊은 문장을 남겨보세요 (24시간 뒤 사라져요)"></textarea>
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

            <!-- 공개 책 첨부 (선택) -->
            <label class="story-composer-book">책 함께 올리기 (선택)
                <select v-model="bookId">
                    <option :value="null">책 없이 올리기</option>
                    <option v-for="b in books" :key="b.id" :value="b.id">{{ b.title }}</option>
                </select>
            </label>
            <p class="story-composer-hint">비공개 책은 책장에서 공개로 바꾸면 붙일 수 있어요.</p>

            <div v-if="error" class="alert alert-error">{{ error }}</div>

            <button type="button" class="dash-btn-fill story-composer-submit"
                    :disabled="!submittable" @click="submit">올리기</button>
        </div>
    </div>
</template>
