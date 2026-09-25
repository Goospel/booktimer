<script setup lang="ts">
import { ref, computed } from 'vue';
import type { BookRead, SessionRow } from './HistoryApp.vue';

/** 월별 목록의 최소 꼴 — 독서(MonthlyReadingSection)·공부 둘 다 이 꼴이라 호출부는 0줄. books는 공부에 없다.
 *  sessions가 있는 날은 펼치면 측정 한 건씩 보이고, 그 줄의 버튼이 assign으로 올라간다(R2 — 붙이는 문은 부모가 고른다:
 *  /history는 독서 원장, /study/history는 공부 원장). sessions가 없는 옛 응답은 펼침 없이 예전 모양 그대로. */
interface RecordsMonth { month: string; totalSeconds: number; days: { date: string; totalSeconds: number; books?: BookRead[]; sessions?: SessionRow[] }[] }
const props = withDefaults(defineProps<{ months: RecordsMonth[]; emptyText?: string }>(),
    { emptyText: '아직 독서 기록이 없습니다. 측정을 시작해 보세요.' });
const emit = defineEmits<{ assign: [row: SessionRow] }>();

const monthIndex = ref(0);
// 펼친 날짜 하나 — 재조회(책을 붙인 뒤)에도 같은 날이 펼쳐진 채 남아 바뀐 줄을 바로 보여 준다.
const openDate = ref<string | null>(null);
function toggle(date: string) {
    openDate.value = openDate.value === date ? null : date;
}

/** 수동 기록의 시각은 서버 앵커(과거 날짜 00:00 등)라 실측이 아니다 — 찍으면 거짓이 된다. */
function sessionWhen(s: SessionRow): string {
    return s.manual || s.start === null || s.end === null ? '직접 기록' : `${s.start}–${s.end}`;
}

/** 한 건의 길이. 1분도 안 되는 측정이 흔하다(멈춘 측정) — 「0분」 대신 사실대로. */
function sessionLength(seconds: number): string {
    if (seconds < 60) return '1분 미만';
    const h = Math.floor(seconds / 3600);
    const min = Math.floor((seconds % 3600) / 60);
    return h > 0 ? `${h}시간 ${min}분` : `${min}분`;
}

const prevDisabled = computed(() => monthIndex.value >= props.months.length - 1);
const nextDisabled = computed(() => monthIndex.value <= 0);

function prevMonth() {
    if (!prevDisabled.value) monthIndex.value++;
}
function nextMonth() {
    if (!nextDisabled.value) monthIndex.value--;
}

function formatMonthLabel(monthStr: string, totalSeconds: number): string {
    const [y, m] = monthStr.split('-').map(Number);
    const h = Math.floor(totalSeconds / 3600);
    const min = Math.floor((totalSeconds % 3600) / 60);
    return `${y}년 ${m}월 · ${h}시간 ${min}분`;
}

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];

function formatRecordDate(dateStr: string): string {
    const [y, m, d] = dateStr.split('-').map(Number);
    const dow = new Date(y, m - 1, d).getDay();
    return `${dateStr} (${WEEKDAYS[dow]})`;
}

function formatTime(totalSeconds: number): string {
    const h = Math.floor(totalSeconds / 3600);
    const min = Math.floor((totalSeconds % 3600) / 60);
    return `${h}시간 ${min}분`;
}
</script>

<template>
    <p class="status-line" v-if="months.length === 0">
        {{ emptyText }}
    </p>

    <div v-else class="month-browser">
        <div class="month-nav">
            <button type="button"
                    class="month-nav-btn month-nav-prev"
                    aria-label="이전 달"
                    :disabled="prevDisabled"
                    @click="prevMonth">◀</button>
            <span class="month-nav-label">
                {{ formatMonthLabel(months[monthIndex].month, months[monthIndex].totalSeconds) }}
            </span>
            <button type="button"
                    class="month-nav-btn month-nav-next"
                    aria-label="다음 달"
                    :disabled="nextDisabled"
                    @click="nextMonth">▶</button>
        </div>

        <ul class="record-list record-scroll">
            <li class="record-row" v-for="r in months[monthIndex].days" :key="r.date"
                :class="{ 'is-expandable': r.sessions?.length, 'is-open': openDate === r.date }">
                <!-- 측정이 있는 날만 머리 전체가 펼침 버튼이다. 없는 날(옛 응답)은 예전 그대로 누를 것이 없다. -->
                <component :is="r.sessions?.length ? 'button' : 'div'" class="record-head"
                           :class="{ 'record-toggle': r.sessions?.length }"
                           v-bind="r.sessions?.length ? { type: 'button', 'aria-expanded': String(openDate === r.date) } : {}"
                           @click="r.sessions?.length && toggle(r.date)">
                    <span class="record-main">
                        <span class="record-date">{{ formatRecordDate(r.date) }}</span>
                        <span class="record-books" v-if="r.books?.length">
                            {{ r.books.map((b) => b.title).join(', ') }}
                        </span>
                    </span>
                    <span class="record-time mono">{{ formatTime(r.totalSeconds) }}</span>
                    <svg v-if="r.sessions?.length" class="record-chevron" viewBox="0 0 24 24" width="16" height="16"
                         fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"
                         aria-hidden="true"><path d="m6 9 6 6 6-6"/></svg>
                </component>

                <!-- 측정 한 건 = 한 줄: [시각 범위 / 길이] · 책 · 버튼. 순서·정렬은 서버가 정한다(실측 시작순 뒤 직접 기록). -->
                <ul v-if="r.sessions?.length && openDate === r.date" class="record-sessions">
                    <li v-for="s in r.sessions" :key="s.id" class="record-session">
                        <span class="record-session-when">
                            <span class="record-session-range">{{ sessionWhen(s) }}</span>
                            <span class="record-session-len">{{ sessionLength(s.seconds) }}</span>
                        </span>
                        <span class="record-session-book" :class="{ 'is-none': s.bookId === null }">{{ s.bookTitle ?? '책 없음' }}</span>
                        <button type="button" class="record-session-btn" :class="{ 'is-attach': s.bookId === null }"
                                :aria-label="`${sessionWhen(s)} 측정 ${s.bookId === null ? '책 붙이기' : '책 바꾸기'}`"
                                @click="emit('assign', s)">{{ s.bookId === null ? '책 붙이기' : '바꾸기' }}</button>
                    </li>
                </ul>
            </li>
        </ul>
    </div>
</template>
