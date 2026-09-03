import { getCsrfToken } from '../shared/follow';
import { errorMessage, type AiAccess, type CalendarDay, type PlanItem, type RecallMark } from './pure';

/**
 * 「공부」 화면의 서버 문.
 *
 * <p>달력·체크는 <b>미니앱과 같은 문</b>({@code /api/study/calendar}·{@code /check})을 그대로 쓴다 —
 * Authorization 헤더가 없으면 세션 체인으로 흐르므로 웹에서도 그대로 닿는다(설계 §1.2). 새로 만든 것은
 * 일정 원장(`/api/study/agenda`·`/plan/items`)뿐이다.
 */

export interface Agenda {
    /** 서버(유저 tz) 기준 오늘 — 기기 시계가 아니라 이걸로 미래 잠금을 판정한다. */
    today: string;
    /** 관리자 승인 상태 — 상태 줄이 이걸로 갈린다. */
    aiAccess: AiAccess;
    /** 마지막 상태 전이 시각(ISO). 신청 전이면 null. */
    aiAccessAt: string | null;
    /** 승인됐고 키도 있을 때만 true — 승인만으론 켜지지 않는다. */
    aiEnabled: boolean;
    remaining: { plan: number; transcribe: number; analyze: number };
    items: PlanItem[];
    recalls: RecallMark[];
}

export interface StudyCalendar {
    goalSeconds: number;
    days: CalendarDay[];
}

async function json<T>(res: Response, fallback?: string): Promise<T> {
    if (!res.ok) {
        throw new Error(errorMessage(res.status, await res.text().catch(() => ''), fallback));
    }
    return (await res.json()) as T;
}

function post(url: string, body?: unknown): Promise<Response> {
    return fetch(url, {
        method: 'POST',
        credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': getCsrfToken() },
        body: body === undefined ? undefined : JSON.stringify(body),
    });
}

export async function fetchAgenda(month: string): Promise<Agenda> {
    return json(await fetch(`/api/study/agenda?month=${month}`, { credentials: 'same-origin' }));
}

export async function fetchCalendar(month: string): Promise<StudyCalendar> {
    return json(await fetch(`/api/study/calendar?month=${month}`, { credentials: 'same-origin' }));
}

/** `kept`가 null이면 무기록으로 되돌린다(3상태 순환의 마지막 칸). */
export async function saveCheck(date: string, kept: boolean | null): Promise<void> {
    await json<unknown>(await post('/api/study/check', { date, kept }));
}

export interface AddItemInput {
    date: string;
    bookId: number | null;
    subject: string;
    task: string;
}

export async function addPlanItem(input: AddItemInput): Promise<PlanItem> {
    return json(await post('/api/study/plan/items', input));
}

export async function deletePlanItem(id: number): Promise<void> {
    const res = await post(`/api/study/plan/items/${id}/delete`);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
}

export interface AiAccessState {
    aiAccess: AiAccess;
    aiAccessAt: string | null;
}

/**
 * AI 기능을 신청한다. 이미 대기·승인 상태면 서버가 409로 막고, 그 한국어 본문이 그대로 뜬다.
 *
 * <p>다른 문들과 <b>같은 `json()` 경로</b>를 탄다 — 여기만 본문을 날것으로 던지면 CSRF가 만료된
 * 403에서 `error.html` 문서 전체가 상태줄에 찍힌다.
 */
export async function requestAiAccess(): Promise<AiAccessState> {
    return json(await post('/api/study/ai-access/request'), 'AI 기능을 신청하지 못했어요.');
}

export interface Recall {
    date: string;
    bookId: number | null;
    subject: string | null;
    scope: string | null;
    body: string;
    source: 'TEXT' | 'PHOTO';
    /** 분석 전이면 null — 화면의 「분석됨」 분기 기준은 `analyzedAt`이다. */
    summary: string | null;
    holes: string[];
    questions: string[];
    model: string | null;
    analyzedAt: string | null;
}

/** 그날 쓴 글. 쓴 적이 없으면 404라 `null`로 옮긴다(빈 화면이 정상 상태다). */
export async function fetchRecall(date: string): Promise<Recall | null> {
    const res = await fetch(`/api/study/recall/${date}`, { credentials: 'same-origin' });
    if (res.status === 404) return null;
    return json(res, '쓴 글을 불러오지 못했어요.');
}

export interface SaveRecallInput {
    date: string;
    bookId: number | null;
    subject: string;
    scope: string;
    body: string;
    /** 사진에서 읽어 온 글이면 `PHOTO` — 사용자가 고친 뒤라도 출처는 사진이다. */
    source: 'TEXT' | 'PHOTO';
}

export async function saveRecall(input: SaveRecallInput): Promise<Recall> {
    return json(await post('/api/study/recall', input), '글을 저장하지 못했어요.');
}

export interface TranscriptResult {
    text: string;
    /** 공부 메모가 아니거나 글씨를 전혀 못 읽음 — 이때 `text`는 빈 값이다. */
    unreadable: boolean;
}

/**
 * 사진에 손으로 쓴 메모를 읽어 <b>텍스트만</b> 받아 온다 — 서버는 사진을 저장하지 않는다.
 *
 * <p>여기만 `post()`를 안 쓰는 이유는 `Content-Type`이다: FormData는 <b>브라우저가</b> boundary와 함께
 * 헤더를 붙여야 하고, 손으로 `multipart/form-data`를 적으면 boundary가 빠져 서버가 파트를 못 읽는다.
 * 그 외(자격증명·CSRF 헤더·`errorMessage` 경로)는 다른 문들과 같다.
 */
export async function transcribePhotos(images: Blob[]): Promise<TranscriptResult> {
    const form = new FormData();
    images.forEach((blob, i) => form.append('images', blob, `memo-${i + 1}.jpg`));
    const res = await fetch('/api/study/recall/transcribe', {
        method: 'POST',
        credentials: 'same-origin',
        headers: { 'X-CSRF-TOKEN': getCsrfToken() },
        body: form,
    });
    return json(res, '사진을 읽지 못했어요.');
}

/**
 * 그날 글을 분석한다. 실패해도 <b>글은 서버에 남아 있다</b> — 그래서 실패 문구가 「글은 저장돼 있어요」로
 * 끝나고, 화면은 쓰던 내용을 지우지 않는다.
 */
export async function analyzeRecall(date: string): Promise<Recall> {
    return json(await post(`/api/study/recall/${date}/analyze`), 'AI 분석을 받지 못했어요.');
}

export interface StudyBookRow {
    id: number;
    title: string;
}

/** 공부 서재 — 일정에 책을 걸 때 고르는 목록. 실패하면 자유 제목만 쓰게 두고 화면은 살린다. */
export async function fetchStudyBooks(): Promise<StudyBookRow[]> {
    const res = await fetch('/api/study/books', { credentials: 'same-origin' });
    if (!res.ok) return [];
    const data = (await res.json()) as { books?: StudyBookRow[] };
    return data.books ?? [];
}
