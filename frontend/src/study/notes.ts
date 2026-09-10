/**
 * 공부 필기의 순수 절반 — 목록 라벨 · 시각 문구 · 자동저장 상태기계.
 *
 * <p>화면(.vue)에서 떼어 둔 이유는 `pure.ts`와 같다 — 타이머·fetch가 얽힌 코드를 통째로 재면 어느
 * 규칙이 죽었는지 안 보인다. 특히 아래 상태기계는 <b>400 / 409 / 5xx가 서로 다른 갈래</b>라는 것이
 * 규칙 자체라, 셋을 각각 죽일 수 있는 자리에 둔다.
 */

/** 목록 라벨의 길이 — 좌측 목록 한 줄에 들어가는 만큼. */
const LABEL_MAX = 40;

/**
 * 자동저장의 지금 상태.
 *
 * <p>실패가 셋으로 갈리는 것이 요점이다:
 * <ul>
 *   <li>{@code invalid}(400) — 사용자가 글을 고치기 전엔 <b>영원히 400</b>이다. 다시 보내지 않는다.
 *   <li>{@code invalid} + {@code permanent}(404·429) — 글을 고쳐도 안 풀린다. 아예 되살아나지 않는다.
 *   <li>{@code conflict}(409) — 다른 곳에서 고쳐졌다. 자동저장을 <b>멈춘다</b>(덮어쓰기보다 정직).
 *   <li>{@code error}(5xx·네트워크) — 같은 내용으로도 성공할 수 있다. 다음 변경·버튼에 <b>재시도</b>.
 * </ul>
 */
export type SaveState =
    | { kind: 'idle' }
    | { kind: 'dirty' }
    | { kind: 'saving' }
    | { kind: 'saved'; at: string }
    | { kind: 'error'; message: string }
    /**
     * 지금 보낼 수 없는 글. {@code permanent}면 <b>사용자가 고쳐도</b> 안 풀린다 —
     * 404(다른 곳에서 지워짐)·429(레이트리밋)가 그렇다. 이게 없으면 편집할 때마다 영원히 실패할
     * 요청이 나가고, 특히 429는 자동저장이 1.5초마다 <b>스스로 레이트리밋을 때린다</b>.
     */
    | { kind: 'invalid'; message: string; permanent?: boolean }
    | { kind: 'conflict'; message: string };

export type SaveEvent =
    | { type: 'edit' }
    /** 디바운스 만료 · 즉시 플러시 · 「다시 저장」 — 보내려는 순간. */
    | { type: 'flush' }
    | { type: 'ok'; at: string }
    | { type: 'fail'; status: number; message: string };

export function nextSaveState(state: SaveState, event: SaveEvent): SaveState {
    switch (event.type) {
        case 'edit':
            // conflict는 끝이다 — 멈춘 뒤에 무엇을 쳐도 다시 두드리지 않는다(서버 판을 덮지 않는다).
            if (state.kind === 'conflict') return state;
            // 404·429도 끝이다. 400과 달리 글을 줄여도 같은 답이 온다.
            if (state.kind === 'invalid' && state.permanent) return state;
            return { kind: 'dirty' };
        case 'flush':
            // invalid에서 보내지 않는 것이 이 기계의 핵심이다. 400은 사용자가 고치기 전엔 영원히
            // 400이라, 재시도에 넣으면 키 입력마다 무한 왕복이고 진짜 이유는 화면에서 밀려난다.
            return state.kind === 'dirty' || state.kind === 'error' ? { kind: 'saving' } : state;
        case 'ok':
            // 저장 중에 또 고쳤으면(dirty) 그 사실을 지우지 않는다 — 큐가 한 번 더 돈다.
            return state.kind === 'saving' ? { kind: 'saved', at: event.at } : state;
        case 'fail':
            if (event.status === 409) return { kind: 'conflict', message: event.message };
            if (event.status === 400) return { kind: 'invalid', message: event.message };
            // 404 문구는 화면 몫이다 — 공용 `errorMessage`의 404는 「책을 찾을 수 없어요」라 여기선 어긋난다.
            if (event.status === 404) {
                return { kind: 'invalid', permanent: true, message: '이 필기가 없어요 — 다른 곳에서 지워졌을 수 있어요.' };
            }
            // 429는 서버가 「언제까지 몇 장」을 말해 준다.
            if (event.status === 429) return { kind: 'invalid', permanent: true, message: event.message };
            return { kind: 'error', message: event.message };
    }
}

/**
 * 목록에 뜨는 이름 — 제목이 있으면 제목, 없으면 본문 첫 줄에서 마크다운 표식을 벗긴 것.
 *
 * <p>서버는 제목을 파생하지 않는다(저장값이 곧 사용자가 친 것) — 파생은 화면 몫이라 여기 있다.
 */
export function noteLabel(title: string | null, body: string): string {
    const trimmed = (title ?? '').trim();
    if (trimmed) return clip(trimmed);
    const line = body.split('\n').map(stripMarkers).find((s) => s.length > 0);
    return line ? clip(line) : '제목 없음';
}

/** `2026-09-10T05:32:11Z` → `14:32`(로컬). 상태줄 「저장됨 · 14:32」의 뒷부분. */
export function savedAtLabel(iso: string): string {
    const at = new Date(iso);
    if (Number.isNaN(at.getTime())) return '';
    return `${pad(at.getHours())}:${pad(at.getMinutes())}`;
}

/** 목록 행의 「9월 10일 14:32」. */
export function noteDateLabel(iso: string): string {
    const at = new Date(iso);
    if (Number.isNaN(at.getTime())) return '';
    return `${at.getMonth() + 1}월 ${at.getDate()}일 ${savedAtLabel(iso)}`;
}

function pad(n: number): string {
    return String(n).padStart(2, '0');
}

function clip(text: string): string {
    return text.length > LABEL_MAX ? `${text.slice(0, LABEL_MAX)}…` : text;
}

/** 줄머리 표식(`#`·`-`·`*`·`>`·`1.`·`[ ]`)과 강조 표식(`**`·`==`·`++`·`` ` ``)을 벗긴다. */
function stripMarkers(line: string): string {
    return line
        .replace(/^\s*(?:#{1,6}\s+|[-*>]\s+|\d+\.\s+)+/, '')
        .replace(/^\s*\[[ xX]\]\s*/, '')
        .replace(/\*\*|==|\+\+|`/g, '')
        .trim();
}
