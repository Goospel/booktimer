/** 서버 `StudyRecall.BODY_MAX`(VARCHAR 8000)와 같은 값 — 한쪽만 고치면 화면과 400이 어긋난다. */
export const BODY_MAX = 8000;

export interface SlashItem {
    id: string;
    label: string;
    /** 사용자가 부를 법한 다른 이름들 — 「체크」와 「할일」이 같은 것을 가리킨다. */
    keywords: string[];
}

/** 슬래시 메뉴 항목. 툴바에 있는 것 중 「글의 뼈대를 바꾸는 것」만 둔다(굵게·형광은 툴바 몫). */
export const SLASH_ITEMS: readonly SlashItem[] = [
    { id: 'h1', label: '큰 제목', keywords: ['제목', '큰제목', 'h1'] },
    { id: 'h2', label: '작은 제목', keywords: ['제목', '작은제목', 'h2'] },
    { id: 'bulletList', label: '글머리 목록', keywords: ['목록', '불릿', 'ul'] },
    { id: 'orderedList', label: '번호 목록', keywords: ['목록', '번호', 'ol'] },
    { id: 'taskList', label: '체크 목록', keywords: ['체크', '할일', '체크박스', 'todo'] },
    { id: 'blockquote', label: '인용', keywords: ['인용', '따옴표', 'quote'] },
    { id: 'horizontalRule', label: '구분선', keywords: ['구분선', '가로선', 'hr'] },
];

/** 라벨·키워드 부분일치. 빈 질의면 전부 — `/`만 친 순간이 곧 「뭐가 있는지 보여 줘」다. */
export function filterSlashItems(query: string, items: readonly SlashItem[] = SLASH_ITEMS): SlashItem[] {
    const q = query.trim().toLowerCase();
    if (!q) return [...items];
    return items.filter((item) =>
        item.label.toLowerCase().replace(/\s/g, '').includes(q)
        || item.keywords.some((k) => k.toLowerCase().includes(q)));
}

/** 팝업 상자 너비 — CSS `min-width`(148px)에 테두리·여백을 더해 넉넉히 잡는다(넘치는 쪽이 안전). */
export const SLASH_WIDTH = 156;
/** 항목 한 줄 높이·상자 여백·최대 높이 — CSS와 짝이다(`.study-editor-slash*`). */
const SLASH_ITEM_HEIGHT = 34;
const SLASH_PADDING = 8;
const SLASH_MAX_HEIGHT = 220;
/** 캐럿과 팝업 사이 틈 — 글자를 덮지 않을 만큼만. */
const SLASH_GAP = 6;

/** 항목 수로 정해지는 팝업 높이. 길어지면 CSS `max-height`에서 멈추고 안에서 스크롤된다. */
export function slashHeight(itemCount: number): number {
    return Math.min(itemCount * SLASH_ITEM_HEIGHT + SLASH_PADDING, SLASH_MAX_HEIGHT);
}

/**
 * 슬래시 팝업을 화면 어디에 둘 것인가 — 좌표는 <b>뷰포트 기준</b>(`position: fixed`)이다.
 *
 * <p>기본은 캐럿 바로 아래지만, 아래가 모자라면 <b>위로 뒤집는다</b>. 세로 보정이 없던 첫 구현은
 * 로컬 실측에서 캐럿이 화면 아래쪽에 있을 때 팝업이 화면 밖으로 82px 나갔다 — 글을 이어 쓰는
 * 자리가 늘 화면 아래쪽이라, 이 경우가 예외가 아니라 오히려 흔한 쪽이다.
 */
export function slashPosition(
    caret: { left: number; top: number; bottom: number },
    viewport: { width: number; height: number },
    itemCount: number,
): { left: number; top: number } {
    const height = slashHeight(itemCount);
    const below = caret.bottom + SLASH_GAP;
    return {
        left: Math.max(0, Math.min(caret.left, viewport.width - SLASH_WIDTH)),
        top: below + height <= viewport.height ? below : Math.max(0, caret.top - SLASH_GAP - height),
    };
}

/** 마크다운 길이 기준 상한 판정 — 서버가 세는 것과 같은 문자열을 센다. */
export function bodyBudget(markdown: string): { length: number; remaining: number; over: boolean } {
    const length = markdown.length;
    return { length, remaining: BODY_MAX - length, over: length > BODY_MAX };
}

/**
 * Tab 키가 무엇을 해야 하는가.
 *
 * <p>목록 밖 Shift+Tab만 `null`이다 — 거기서 키를 먹으면 키보드 사용자가 편집기를 못 빠져나간다.
 */
export function tabAction(inList: boolean, shift: boolean): 'sink' | 'lift' | 'toBullet' | null {
    if (shift) return inList ? 'lift' : null;
    return inList ? 'sink' : 'toBullet';
}
