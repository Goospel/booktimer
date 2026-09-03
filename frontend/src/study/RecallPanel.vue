<script setup lang="ts">
import { computed, ref, watch } from 'vue';

import { analyzeRecall, fetchRecall, saveRecall, transcribePhotos, type Recall, type StudyBookRow } from './api';
import { shrinkForUpload, type ShrunkImage } from './image';
import { prevDay, recallScopePrefill, recallSubjectPrefill, type PlanItem } from './pure';

/** 서버(`ClaudeStudyAssistant.MAX_IMAGES`)와 같은 값 — 화면이 먼저 막아 헛왕복을 없앤다. */
const MAX_PHOTOS = 3;

/**
 * 백지복습 — 그날 기억나는 것을 쏟아내고, 승인된 사용자면 AI 분석까지.
 *
 * <p>분석 버튼은 <b>승인됐고 키도 있을 때만</b> 그린다(`aiEnabled`). 서버의 403은 2중 방어이지 UI 규칙이
 * 아니다 — 못 쓰는 버튼을 보여 주고 누르게 한 뒤 거절하는 화면은 만들지 않는다.
 *
 * <p>실패해도 쓰던 글을 지우지 않는다. 분석은 저장된 글에 붙는 것이라, 분석이 실패해도 글은 서버에 남아
 * 있다는 것이 이 화면의 약속이다.
 */
const props = defineProps<{
    date: string;
    /** 서버(유저 tz) 기준 오늘. 미래 날짜엔 쓰지 않는다. */
    today: string;
    /** 그날 일정 — 과목·범위 프리필의 출처. */
    items: PlanItem[];
    /** 공부 서재 — 글을 책에 걸 때 고르는 목록(일정 추가와 <b>같은 목록</b>을 위에서 내려받는다). */
    books: StudyBookRow[];
    /** 승인됨 AND 키 있음일 때만 분석 버튼을 그린다. */
    aiEnabled: boolean;
    /** 오늘 남은 분석 몫. 0이면 버튼을 잠근다. */
    remainingAnalyze: number;
    /** 오늘 남은 사진 전사 몫. 0이면 「읽어 오기」를 잠근다. */
    remainingTranscribe: number;
    /** 전날 복습에 문제가 붙어 있나 — 참일 때만 전날 글을 한 번 더 불러온다. */
    hasYesterdayQuestions: boolean;
}>();

const emit = defineEmits<{ (e: 'saved', recall: Recall): void }>();

const recall = ref<Recall | null>(null);
const yesterdayQuestions = ref<string[]>([]);
const body = ref('');
const subject = ref('');
const scope = ref('');
const bookId = ref<number | null>(null);
const busy = ref(false);
const error = ref('');
const notice = ref('');

/** 「타이핑」 탭이냐 「사진」 탭이냐 — 저장되는 `source`도 이걸 따라간다. */
const mode = ref<'TEXT' | 'PHOTO'>('TEXT');
const photos = ref<ShrunkImage[]>([]);
const photoError = ref('');
const photoBusy = ref(false);
/** 지금 textarea에 있는 글이 <b>AI가 읽어 준 것</b>인가 — 확인 안내를 띄우는 근거. */
const transcribed = ref(false);
/** 저장할 때 원장에 남길 출처. 사진에서 읽어 온 글은 사용자가 고친 뒤라도 PHOTO다. */
const source = ref<'TEXT' | 'PHOTO'>('TEXT');

const isFuture = computed(() => !props.today || props.date > props.today);
const analyzed = computed(() => recall.value?.analyzedAt != null);
const canSave = computed(() => body.value.trim().length > 0 && !busy.value);
const canAnalyze = computed(() => canSave.value && props.remainingAnalyze > 0 && !analyzed.value);
/** 오늘 몫을 다 썼는데 이 글은 아직 분석 전 — 버튼만 잠그면 「왜 안 되는지」가 화면에 없다. */
const capSpent = computed(() => props.aiEnabled && props.remainingAnalyze === 0 && !analyzed.value);
const photoCapSpent = computed(() => props.remainingTranscribe === 0);
const canTranscribe = computed(() => photos.value.length > 0 && !photoBusy.value && !photoCapSpent.value);

async function load(): Promise<void> {
    error.value = '';
    notice.value = '';
    recall.value = null;
    yesterdayQuestions.value = [];
    body.value = '';
    resetPhotos();
    if (isFuture.value) return;
    try {
        const found = await fetchRecall(props.date);
        recall.value = found;
        body.value = found?.body ?? '';
        subject.value = found?.subject ?? recallSubjectPrefill(props.items);
        scope.value = found?.scope ?? recallScopePrefill(props.items);
        // 저장된 글이 있으면 그때 고른 책을, 없으면 그날 일정이 가리키는 책을 기본으로(대개 같은 책이다).
        bookId.value = found?.bookId ?? props.items.find((i) => i.bookId !== null)?.bookId ?? null;
        source.value = found?.source ?? 'TEXT';
    } catch {
        error.value = '쓴 글을 불러오지 못했어요.';
    }
    if (props.hasYesterdayQuestions) {
        // 문제가 있다고 달력이 이미 알려준 날에만 한 번 더 부른다(없는 날엔 왕복이 없다).
        try {
            yesterdayQuestions.value = (await fetchRecall(prevDay(props.date)))?.questions ?? [];
        } catch {
            yesterdayQuestions.value = [];
        }
    }
}

watch(() => props.date, load, { immediate: true });

function resetPhotos(): void {
    photos.value = [];
    photoError.value = '';
    transcribed.value = false;
    mode.value = 'TEXT';
}

/**
 * 고른 파일을 브라우저에서 줄여 미리보기까지 만든다 — 서버로 원본이 나가는 경로는 없다.
 *
 * <p>장수는 <b>보내기 전에</b> 여기서 막는다. 서버도 같은 규칙으로 400을 주지만(2중 방어), 사용자가
 * 사진 네 장을 올려 놓고 왕복을 기다린 뒤에야 이유를 듣는 것은 나쁜 화면이다.
 */
async function onPickPhotos(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const picked = Array.from(input.files ?? []);
    if (picked.length === 0) return;

    photoError.value = '';
    if (photos.value.length + picked.length > MAX_PHOTOS) {
        photoError.value = `사진은 ${MAX_PHOTOS}장까지 올릴 수 있어요.`;
        input.value = '';
        return;
    }
    photoBusy.value = true;
    try {
        for (const file of picked) {
            photos.value = [...photos.value, await shrinkForUpload(file)];
        }
    } catch {
        photoError.value = '사진을 열지 못했어요. 다른 사진으로 시도해 주세요.';
    } finally {
        photoBusy.value = false;
        input.value = ''; // 같은 파일을 다시 고를 수 있게(change가 안 뜨는 것을 막는다)
    }
}

function removePhoto(index: number): void {
    photos.value = photos.value.filter((_, i) => i !== index);
}

/**
 * 사진을 읽어 textarea를 채운다 — <b>분석은 여기서 돌지 않는다</b>.
 *
 * <p>전사와 분석 사이에 사용자의 확인이 끼는 것이 이 기능의 규칙이다. 모델이 잘못 읽은 글을 그대로
 * 분석하면 「내가 쓰지도 않은 것」에 대한 구멍·문제가 나오고, 그게 다음날 복습으로 이어진다.
 */
async function onTranscribe(): Promise<void> {
    if (!canTranscribe.value) return;
    photoBusy.value = true;
    photoError.value = '';
    try {
        const result = await transcribePhotos(photos.value.map((p) => p.blob));
        if (result.unreadable || !result.text.trim()) {
            photoError.value = '사진의 글씨를 읽지 못했어요. 더 밝은 곳에서 또렷하게 찍어 주세요.';
            return;
        }
        body.value = result.text;
        source.value = 'PHOTO';
        transcribed.value = true;
        mode.value = 'TEXT'; // 확인·수정이 다음 단계라 글 쓰는 화면으로 돌려놓는다
        photos.value = [];
    } catch (e) {
        photoError.value = e instanceof Error && e.message ? e.message : '사진을 읽지 못했어요.';
    } finally {
        photoBusy.value = false;
    }
}

async function onSave(thenAnalyze: boolean): Promise<void> {
    if (!canSave.value) return;
    busy.value = true;
    error.value = '';
    notice.value = '';
    try {
        let saved = await saveRecall({
            date: props.date,
            bookId: bookId.value,
            subject: subject.value.trim(),
            scope: scope.value.trim(),
            body: body.value,
            source: source.value,
        });
        recall.value = saved;
        transcribed.value = false; // 확인이 끝났다 — 저장된 뒤엔 「AI가 읽은 내용」 안내를 내린다
        notice.value = '저장했어요.';
        if (thenAnalyze) {
            saved = await analyzeRecall(props.date);
            recall.value = saved;
            notice.value = '';
        }
        emit('saved', saved);
    } catch (e) {
        // 저장은 됐고 분석만 실패한 경우가 흔하다 — 그래서 본문을 지우지 않고 문구만 바꾼다.
        error.value = e instanceof Error && e.message ? e.message : '저장하지 못했어요.';
    } finally {
        busy.value = false;
    }
}
</script>

<template>
    <div class="study-recall">
        <p class="study-day-label">백지복습</p>

        <div v-if="yesterdayQuestions.length" class="study-recall-yesterday">
            <p class="study-recall-heading">어제의 복습문제</p>
            <ol class="study-recall-list">
                <li v-for="(q, i) in yesterdayQuestions" :key="`y-${i}`">{{ q }}</li>
            </ol>
        </div>

        <p v-if="isFuture" class="status-line muted">아직 오지 않은 날이에요.</p>

        <template v-else>
            <div v-if="aiEnabled" class="study-recall-tabs" role="tablist">
                <button
                    type="button"
                    class="btn btn-ghost btn-small"
                    :class="{ 'is-active': mode === 'TEXT' }"
                    data-testid="recall-tab-text"
                    @click="mode = 'TEXT'"
                >타이핑</button>
                <button
                    type="button"
                    class="btn btn-ghost btn-small"
                    :class="{ 'is-active': mode === 'PHOTO' }"
                    data-testid="recall-tab-photo"
                    @click="mode = 'PHOTO'"
                >사진</button>
            </div>

            <div v-if="mode === 'PHOTO'" class="study-recall-photo">
                <p class="status-line muted">종이에 쓴 메모를 찍어 올리면 AI가 읽어 옮겨 적어요 (최대 3장).</p>
                <input
                    type="file"
                    accept="image/jpeg,image/png,image/webp"
                    multiple
                    aria-label="공부 메모 사진"
                    data-testid="recall-photo-input"
                    @change="onPickPhotos"
                >
                <ul v-if="photos.length" class="study-recall-photo-list">
                    <li v-for="(photo, i) in photos" :key="`p-${i}`">
                        <img :src="photo.dataUrl" alt="올린 사진 미리보기" data-testid="recall-photo-preview">
                        <button type="button" class="btn btn-ghost btn-small" @click="removePhoto(i)">빼기</button>
                    </li>
                </ul>
                <p v-if="photoError" class="status-line study-error" data-testid="recall-photo-error">{{ photoError }}</p>
                <p v-else-if="photoCapSpent" class="status-line muted" data-testid="recall-photo-cap-spent">
                    오늘 몫을 다 썼어요 — 내일 다시 해 주세요.
                </p>
                <button
                    type="button"
                    class="btn btn-primary btn-small"
                    :disabled="!canTranscribe"
                    data-testid="recall-transcribe"
                    @click="onTranscribe"
                >{{ photoBusy ? '읽는 중…' : `읽어 오기 (${remainingTranscribe}회 남음)` }}</button>
            </div>

            <p v-if="transcribed" class="status-line study-recall-transcribed" data-testid="recall-transcribed">
                AI가 읽은 내용이에요. 틀린 곳을 고친 뒤 저장하세요 — [?]는 못 읽은 부분이에요.
            </p>

            <select v-model="bookId" class="study-recall-book" aria-label="공부 책" data-testid="recall-book">
                <option :value="null">책 없이 (직접 입력)</option>
                <option v-for="book in books" :key="book.id" :value="book.id">{{ book.title }}</option>
            </select>
            <input v-model="subject" type="text" maxlength="300" placeholder="과목 (예: 정보처리기사 실기)" aria-label="과목">
            <textarea
                v-model="scope"
                class="study-recall-scope"
                rows="2"
                maxlength="4000"
                placeholder="오늘의 범위 (구멍을 찾는 기준이 돼요)"
                aria-label="범위"
            ></textarea>
            <textarea
                v-model="body"
                class="study-recall-body"
                rows="8"
                maxlength="8000"
                placeholder="책을 덮고, 기억나는 것을 그대로 적어 보세요."
                aria-label="백지복습 본문"
                data-testid="recall-body"
            ></textarea>

            <p v-if="error" class="status-line study-error">{{ error }}</p>
            <p v-else-if="notice" class="status-line muted">{{ notice }}</p>

            <div class="study-recall-actions">
                <button
                    type="button"
                    class="btn btn-ghost btn-small"
                    :disabled="!canSave"
                    data-testid="recall-save"
                    @click="onSave(false)"
                >저장</button>
                <button
                    v-if="aiEnabled"
                    type="button"
                    class="btn btn-primary btn-small"
                    :disabled="!canAnalyze"
                    data-testid="recall-analyze"
                    @click="onSave(true)"
                >저장하고 분석 ({{ remainingAnalyze }}회 남음)</button>
                <span v-if="aiEnabled && analyzed" class="status-line muted">오늘 분석은 끝났어요.</span>
                <span v-else-if="capSpent" class="status-line muted" data-testid="recall-cap-spent">오늘 몫을 다 썼어요 — 내일 다시 해 주세요.</span>
            </div>

            <div v-if="recall && recall.analyzedAt" class="study-recall-result">
                <p class="study-recall-heading">정리</p>
                <p class="study-recall-summary">{{ recall.summary }}</p>

                <template v-if="recall.holes.length">
                    <p class="study-recall-heading">빠진 곳</p>
                    <ul class="study-recall-list">
                        <li v-for="(h, i) in recall.holes" :key="`h-${i}`">{{ h }}</li>
                    </ul>
                </template>

                <template v-if="recall.questions.length">
                    <p class="study-recall-heading">내일 풀 문제</p>
                    <ol class="study-recall-list">
                        <li v-for="(q, i) in recall.questions" :key="`q-${i}`">{{ q }}</li>
                    </ol>
                </template>

                <p class="status-line muted">AI 판단이라 틀릴 수 있어요.</p>
            </div>
        </template>
    </div>
</template>
