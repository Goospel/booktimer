import { getCsrfToken } from '../shared/follow';
import { errorMessage, type CalendarDay, type PlanItem, type RecallMark } from './pure';

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
    aiEnabled: boolean;
    remaining: { plan: number; transcribe: number; analyze: number };
    items: PlanItem[];
    recalls: RecallMark[];
}

export interface StudyCalendar {
    goalSeconds: number;
    days: CalendarDay[];
}

async function json<T>(res: Response): Promise<T> {
    if (!res.ok) {
        throw new Error(errorMessage(res.status, await res.text().catch(() => '')));
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
