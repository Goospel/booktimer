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
