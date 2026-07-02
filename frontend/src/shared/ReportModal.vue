<script setup lang="ts">
import { onMounted, ref } from 'vue'
import ShopIcon from '../profile/ShopIcon.vue'
import { report as doReport } from './report'
import { composeReportDetail } from './story/storyFeed'

// 사용자 신고 모달 — 책방(profile)·스토리 뷰어(dashboard/profile) 공용이라 shared/ 소속.
// detailPrefix: 스토리 신고 시 `[스토리#id] 원문 발췌`를 detail 앞에 자동 첨부(§13.5 — 만료 후에도
// 운영자가 관리자 신고함에서 id로 원문 대조). 전체 500자 절삭.
const props = defineProps<{ loginId: string; detailPrefix?: string }>()
defineEmits<{ (e: 'close'): void }>()

const REPORT_REASONS = [
    { value: 'SPAM',          label: '스팸/광고' },
    { value: 'HARASSMENT',    label: '괴롭힘/욕설' },
    { value: 'INAPPROPRIATE', label: '부적절한 콘텐츠' },
    { value: 'OTHER',         label: '기타' },
]

const reason = ref('SPAM')
const detail = ref('')
const submitted = ref(false)
const ok = ref(false)
const overlayEl = ref<HTMLElement | null>(null)

// 포커스를 모달 안으로 — 여는 버튼(모달 밖)에 포커스가 남으면 @keydown.esc가 이벤트를 못 받아
// Esc가 무반응이 된다(리뷰 파인딩). tabindex=-1 오버레이에 focus를 주면 즉시 동작.
onMounted(() => overlayEl.value?.focus())

async function submitReport() {
    const fullDetail = props.detailPrefix
        ? composeReportDetail(props.detailPrefix, detail.value)
        : detail.value
    const result = await doReport(props.loginId, reason.value, fullDetail)
    ok.value = result
    submitted.value = true
}
</script>

<template>
    <div ref="overlayEl" class="shop-report-modal-overlay" @click.self="$emit('close')" @keydown.esc="$emit('close')"
         tabindex="-1">
        <div class="shop-report-modal-panel" role="dialog" aria-modal="true"
             aria-labelledby="report-modal-title">
            <div class="shop-report-modal-head">
                <p id="report-modal-title" class="shop-report-modal-title">이 사용자 신고</p>
                <button type="button" class="shop-report-modal-close" aria-label="닫기"
                        @click="$emit('close')">
                    <ShopIcon name="close" :size="18" />
                </button>
            </div>

            <div v-if="!submitted" class="shop-report-modal-body">
                <p v-if="detailPrefix" class="story-report-context">신고에 첨부됨: {{ detailPrefix }}</p>
                <label class="shop-report-field">사유
                    <select v-model="reason">
                        <option v-for="r in REPORT_REASONS" :key="r.value" :value="r.value">{{ r.label }}</option>
                    </select>
                </label>
                <textarea v-model="detail" rows="3" maxlength="500"
                          placeholder="상세 내용 (선택)"></textarea>
                <button type="button" class="dash-btn-outline shop-report-modal-submit"
                        @click="submitReport">신고하기</button>
            </div>

            <div v-else class="shop-report-modal-body">
                <p class="shop-report-modal-ok">신고가 접수되었습니다.</p>
                <button type="button" class="dash-btn-outline shop-report-modal-submit"
                        @click="$emit('close')">닫기</button>
            </div>
        </div>
    </div>
</template>
