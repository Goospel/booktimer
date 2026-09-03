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
 * <p><b>본문을 믿는 것은 400뿐</b>이다 — 그 상태만 컨트롤러의 `@ExceptionHandler(IAE)`가 한국어
 * 완성문을 본문으로 돌려준다. 404·500은 `GlobalExceptionHandler`가 `error.html`을 렌더해
 * <b>HTML 문서 전체</b>가 본문이라, 그대로 던지면 상태줄에 `<!DOCTYPE html>…`이 찍힌다.
 */
export function errorMessage(status: number, bodyText: string): string {
    const fallback = '일정을 추가하지 못했어요.';
    if (status === 400) return bodyText.trim() || fallback;
    if (status === 404) return '책을 찾을 수 없어요';
    return fallback;
}

/** 하단 네비 — 이 화면은 홈과 내 책장으로만 나간다(공부 서재·타이머 동선은 미니앱 몫). */
export function studyNavLinks(): NavLinkSpec[] {
    return [
        { href: '/', icon: 'home', label: '홈' },
        { href: '/books', icon: 'books', label: '내 책장' },
    ];
}
