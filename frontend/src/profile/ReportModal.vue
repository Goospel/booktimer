<script setup lang="ts">
import { ref } from 'vue'
import ShopIcon from './ShopIcon.vue'
import { report as doReport } from '../shared/report'

const props = defineProps<{ loginId: string }>()
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

async function submitReport() {
    const result = await doReport(props.loginId, reason.value, detail.value)
    ok.value = result
    submitted.value = true
}
</script>

<template>
    <div class="shop-report-modal-overlay" @click.self="$emit('close')" @keydown.esc="$emit('close')"
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
