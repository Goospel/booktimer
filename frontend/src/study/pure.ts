/**
 * 「공부」 화면의 순수 로직 — 달력 격자·3상태 순환·칸 표식·요약 문구.
 *
 * <p>화면(.vue)에서 떼어 둔 이유는 늘 같다: 하니스가 node 정적 렌더라 effect·클릭이 안 돈다.
 * 그려진 꼴은 실 브라우저가 재고, 규칙은 여기서 잰다.
 *
 * <p>달력 규칙은 미니앱 `screens/StudyCalendar.tsx`에서 <b>복사</b>했다 — 빌드 체계가 달라 공유 패키지를
 * 만들지 않는다(웹 번들은 vite, 미니앱은 별 레포). 한쪽을 고치면 다른 쪽도 봐야 한다는 뜻이고,
 * 그 값이 규칙 자체를 바꿀 만큼 크지 않다는 판단이다.
 */

export interface PlanItem {
    id: number;
    /** `YYYY-MM-DD` */
    date: string;
    bookId: number | null;
    subject: string;
    task: string;
}

export interface RecallMark {
    /** `YYYY-MM-DD` */
    date: string;
    analyzed: boolean;
    hasQuestions: boolean;
}

export interface CalendarDay {
    date: string;
    studiedSeconds: number;
    kept: boolean | null;
}

export interface NavLinkSpec {
    href: string;
    icon: string;
    label: string;
}

/** `YYYY-MM-DD` — 로컬 달력 좌표라 UTC 변환을 태우지 않는다(태우면 자정 근처에서 하루가 밀린다). */
function iso(year: number, month: number, day: number): string {
    return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
}

/**
 * 그 달의 격자 — 앞쪽 요일 오프셋만큼 `null`을 채우고 그 뒤로 날짜를 잇는다.
 *
 * 말일은 <b>다음 달 0일</b>로 구한다(하드코딩한 28·30·31이면 윤년 2월에서 하루가 사라진다).
 */
export function calendarCells(year: number, month: number): (string | null)[] {
    const offset = new Date(year, month - 1, 1).getDay();
    const lastDay = new Date(year, month, 0).getDate();
    const cells: (string | null)[] = Array.from({ length: offset }, () => null);
    for (let day = 1; day <= lastDay; day++) cells.push(iso(year, month, day));
    return cells;
}

/** 무기록 → 지킴 → 못 지킴 → 무기록. 탭 한 번에 한 칸이고, 세 번이면 제자리다(되돌릴 길이 늘 있다). */
export function cycleCheck(kept: boolean | null): boolean | null {
    if (kept === null) return true;
    return kept ? false : null;
}

/** `2026`,`9` → `2026년 9월`. 0 채움 없이 읽는 말로 쓴다. */
export function monthTitle(year: number, month: number): string {
    return `${year}년 ${month}월`;
}

/** `YYYY-MM-DD`를 하루 옮긴다 — Date의 달 넘김·윤년 처리를 그대로 쓴다(직접 세지 않는다). */
function shiftDay(date: string, delta: number): string {
    const [y, m, d] = date.split('-').map(Number);
    const moved = new Date(y, m - 1, d + delta);
    return iso(moved.getFullYear(), moved.getMonth() + 1, moved.getDate());
}

export function prevDay(date: string): string {
    return shiftDay(date, -1);
}

export function nextDay(date: string): string {
    return shiftDay(date, 1);
}

/**
 * 달력 칸 아래에 붙는 표식.
 *
 * `questions`가 <b>전날</b>을 보는 것이 요점이다 — 복습문제는 쓴 다음날 푸는 몫이라, 그 표식은 문제가
 * 만들어진 날이 아니라 <b>풀 날</b>에 선다(그래서 달 첫날은 전달 말일을 본다).
 */
export function cellMarks(date: string, recalls: RecallMark[]): { recall: boolean; questions: boolean } {
    const yesterday = prevDay(date);
    return {
        recall: recalls.some((r) => r.date === date),
        questions: recalls.some((r) => r.date === yesterday && r.hasQuestions),
    };
}

/**
 * 칸의 읽어 주는 이름 — 화면에 보이는 것은 숫자와 색뿐이라, 그대로 두면 스크린리더에 「버튼」만 남는다.
 * 미니앱 달력과 같은 문형이다.
 */
export function cellLabel(date: string, kept: boolean | null, planCount: number): string {
    const state = kept === null ? '' : kept ? ', 지킴' : ', 못 지킴';
    const plans = planCount > 0 ? `, 일정 ${planCount}개` : '';
    return `${Number(date.slice(8))}일${state}${plans}`;
}

/** 칸 안에 한 줄로 넣을 일정 요약 — 첫 할 일 + 나머지 개수(`+N`). 없으면 빈 문자열. */
export function planSummary(items: PlanItem[]): string {
    if (items.length === 0) return '';
    if (items.length === 1) return items[0].task;
    return `${items[0].task} +${items.length - 1}`;
}

/**
 * 실패 응답을 화면에 띄울 한 줄로 옮긴다.
 *
 * <p><b>판정 기준은 두 겹</b>이다. ① 본문이 `<`로 시작하면(=HTML 문서) 상태와 무관하게 버린다 —
 * `GlobalExceptionHandler`가 렌더한 `error.html`이 통째로 본문인 경우라, 그대로 던지면 상태줄에
 * `<!DOCTYPE html>…`이 찍힌다(CSRF가 만료된 403, 서버 자체가 낸 503이 그 경로다). ② 그 관문을
 * 통과한 본문은 <b>400·403·409·429·503</b>에서 믿는다 — 컨트롤러의 `@ExceptionHandler`가 한국어
 * 완성문을 평문으로 돌려주는 상태들이다(400 IAE · 403 미승인 · 409 전이 위반/재분석 · 429 상한 ·
 * 503 AI 꺼짐·응답 없음). ①이 먼저인 것이 요점이다: 같은 상태 코드가 두 출처에서 나오므로
 * 「어떤 상태냐」보다 「본문이 무엇처럼 생겼냐」가 오래 맞는 기준이다.
 *
 * @param fallback 본문을 못 믿을 때 띄울 문구. 부르는 쪽마다 다르라고 인자로 받는다 —
 *                 「일정을 추가하지 못했어요」가 AI 신청 실패에 뜨면 엉뚱하다
 */
export function errorMessage(status: number, bodyText: string,
                             fallback = '일정을 추가하지 못했어요.'): string {
    const body = bodyText.trim();
    // HTML 문서면 무조건 못 믿는다 — `error.html`이 그 상태로 렌더될 수 있는 자리가 계속 늘어난다
    // (403 CSRF 만료, 서버 자체의 503 등). 상태 코드 목록보다 이 한 줄이 더 오래 맞는 가드다.
    const trustworthy = body.length > 0 && !body.startsWith('<');
    if (trustworthy && (status === 400 || status === 409 || status === 403
        || status === 413 || status === 429 || status === 503)) {
        return body;
    }
    if (status === 404) return '책을 찾을 수 없어요';
    return fallback;
}

/**
 * 백지복습의 「범위」 프리필 — 그날 일정의 할 일들.
 *
 * <p>범위는 구멍 판정의 울타리인데, 사용자가 매번 손으로 적기엔 귀찮아 비워 두기 십상이다. 그날 하기로
 * 한 것이 곧 그날의 범위라, 이미 적어 둔 일정을 가져다 쓴다(고쳐도 된다).
 */
export function recallScopePrefill(items: PlanItem[]): string {
    return items.map((i) => i.task).join('\n');
}

/** 백지복습의 「과목」 프리필 — 그날 첫 일정의 과목. 일정이 없으면 빈 문자열이다. */
export function recallSubjectPrefill(items: PlanItem[]): string {
    return items.length > 0 ? items[0].subject : '';
}

/** 서버가 주는 AI 기능 승인 상태 — 관리자가 켜 준 사람만 APPROVED다. */
export type AiAccess = 'NONE' | 'PENDING' | 'APPROVED' | 'REJECTED';

export interface AiStatus {
    /** 상태 줄에 띄울 한 줄. 빈 문자열이면 줄 자체를 그리지 않는다. */
    text: string;
    /** 누를 수 있는 버튼의 이름. `null`이면 버튼 없음(기다리는 중이거나 이미 끝난 상태). */
    button: string | null;
}

/**
 * AI 기능의 지금 상태를 한 줄로 옮긴다.
 *
 * <p><b>승인 판정이 키 판정보다 먼저</b>인 것이 요점이다 — 아직 승인 안 된 사람에게 「AI가 꺼져 있어요」는
 * 틀린 안내다(켜져 있어도 그 사람은 못 쓴다). 그래서 `aiEnabled`는 APPROVED 가지 안에서만 본다.
 *
 * <p>APPROVED × 켜짐 칸이 빈 문자열인 것은 그 자리를 AI 버튼들이 가져가기 때문이다 — 상태 줄은 사라지고
 * 백지복습 패널의 「저장하고 분석」이 그 자리를 대신한다.
 */
export function aiStatusLine(access: AiAccess, aiEnabled: boolean, accessAt: string | null): AiStatus {
    if (access === 'PENDING') {
        if (!accessAt) return { text: '승인 대기 중이에요.', button: null };
        const at = new Date(accessAt);
        return { text: `승인 대기 중 — ${at.getMonth() + 1}월 ${at.getDate()}일 신청`, button: null };
    }
    if (access === 'REJECTED') {
        return { text: '승인되지 않았어요.', button: '다시 신청' };
    }
    if (access === 'APPROVED') {
        return aiEnabled
            ? { text: '', button: null }
            : { text: 'AI 기능이 꺼져 있어 저장만 됩니다.', button: null };
    }
    return { text: 'AI 분석·일정 기능은 승인제예요.', button: 'AI 기능 신청' };
}

/** AI가 만든 일정 한 줄 — 아직 저장 전이라 id가 없다(적용해야 `PlanItem`이 된다). */
export interface DraftDay {
    /** `YYYY-MM-DD` */
    date: string;
    task: string;
}

export interface PlanFormInput {
    subject: string;
    scope: string;
    /** `YYYY-MM-DD`. 비어 있으면 아직 안 고른 것이다. */
    examDate: string;
    dailyMinutes: number;
    daysPerWeek: number;
}

/**
 * 일정 생성 폼 검증 — <b>서버와 같은 규칙</b>을 화면에서 먼저 본다.
 *
 * <p>2중 방어이지 유일한 방어가 아니다(서버가 같은 값을 다시 잰다). 여기 있는 이유는 헛왕복 때문이다 —
 * 잘못 채운 폼 하나에 90초짜리 외부 호출과 오늘 몫 하나를 태울 이유가 없다.
 *
 * @param today 서버(유저 tz) 기준 오늘. <b>아직 모르면 막지 않는다</b> — 화면이 지레 잠기는 편이
 *              틀린 날짜를 보내는 것보다 나쁘다(서버가 어차피 다시 잰다)
 * @return 첫 번째 위반 문구, 통과면 `null`
 */
export function validatePlanForm(input: PlanFormInput, today: string): string | null {
    if (!input.subject.trim()) return '과목을 입력해 주세요.';
    if (!input.examDate) return '시험일을 골라 주세요.';
    if (today) {
        if (input.examDate <= today) return '시험일은 내일 이후로 정해 주세요.';
        if (input.examDate > shiftDay(today, 365)) return '시험일은 1년 안으로 정해 주세요.';
    }
    if (input.dailyMinutes < 10 || input.dailyMinutes > 600) {
        return '하루 공부 시간은 10분에서 600분 사이로 적어 주세요.';
    }
    if (input.daysPerWeek < 1 || input.daysPerWeek > 7) {
        return '주 공부일수는 1일에서 7일 사이로 정해 주세요.';
    }
    if (input.scope.length > 4000) return '범위는 4000자까지 적을 수 있어요.';
    if (today && estimatedItems(today, input.examDate, input.daysPerWeek) > MAX_PLAN_ITEMS) {
        return '기간이 길어 한 번에 만들기 어려워요. 시험일을 앞당기거나 주 공부일수를 줄여 주세요.';
    }
    return null;
}

/**
 * 한 번에 만들 수 있는 예상 항목 수 — 서버 `StudyPlanService.MAX_PLAN_ITEMS`와 **같은 값**이다.
 *
 * 지연에서 역산한 값이다(실측 `ms ≈ 9,200 + 843 × 항목수` → 90항목 ≈ 85초, 타임아웃 90초 바로 아래).
 * 여기서 막는 것은 헛왕복을 없애기 위해서고, **최종 판정은 서버**다(같은 규칙을 두 곳이 든다).
 */
const MAX_PLAN_ITEMS = 90;

/** 후보 날짜 수(오늘~시험 전날) × 주 공부일수 / 7 — 서버와 같은 추정식. */
function estimatedItems(today: string, examDate: string, daysPerWeek: number): number {
    const [ty, tm, td] = today.split('-').map(Number);
    const [ey, em, ed] = examDate.split('-').map(Number);
    const days = Math.round((Date.UTC(ey, em - 1, ed) - Date.UTC(ty, tm - 1, td)) / 86400000);
    return Math.floor((days * daysPerWeek) / 7);
}

/** 그 날짜가 속한 ISO 주(월~일)의 월요일 — 서버 `sanitizePlan`과 <b>같은 주 경계</b>다. */
function isoWeekStart(date: string): string {
    const [y, m, d] = date.split('-').map(Number);
    const dt = new Date(y, m - 1, d);
    dt.setDate(dt.getDate() - ((dt.getDay() + 6) % 7)); // 일=0인 getDay를 월=0으로 옮긴다
    return iso(dt.getFullYear(), dt.getMonth() + 1, dt.getDate());
}

/**
 * 미리보기를 주차로 묶는다 — 30~90일짜리 목록을 한 줄씩 세워 두면 사람이 읽지 못한다.
 *
 * <p>주 경계가 서버의 정제 규칙과 같아야 「주 5일로 짰는데 한 주에 6개가 보인다」는 오해가 안 생긴다.
 */
export function planWeeks(days: DraftDay[]): { label: string; days: DraftDay[] }[] {
    const groups = new Map<string, DraftDay[]>();
    for (const day of days) {
        const key = isoWeekStart(day.date);
        const bucket = groups.get(key);
        if (bucket) bucket.push(day);
        else groups.set(key, [day]);
    }
    return [...groups.keys()].sort().map((key, index) => ({
        label: `${index + 1}주차`,
        days: groups.get(key)!,
    }));
}

/** 이 셸이 그릴 화면 — /study는 달력, /study/history는 기록(같은 셸·같은 번들, 경로로 고른다). */
export function studyView(pathname: string): 'calendar' | 'history' {
    return pathname.endsWith('/study/history') ? 'history' : 'calendar';
}

/** 하단 네비 — 공부 세계 안에서만 돈다(홈 · 공부 기록). 독서 서재로 가는 문은 미니앱 공부 모드에도 없다. */
export function studyNavLinks(): NavLinkSpec[] {
    return [
        { href: '/', icon: 'home', label: '홈' },
        { href: '/study/history', icon: 'history', label: '공부 기록' },
    ];
}
