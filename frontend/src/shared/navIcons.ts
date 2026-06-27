/**
 * 공통 네비 라인 아이콘 사전 — 전 페이지 하단 네비(.link-row)와 대시보드 타일(QuickNav)이
 * 공유한다. 이모지 전면 금지(대시보드 스킨 통일). 각 값 = viewBox "0 0 24 24" 안의 inner
 * SVG 프리미티브 문자열. NavIcon이 공통 stroke/fill 래퍼를 씌워 currentColor(부모 color)를
 * 상속한다. profile의 icons.ts는 이 사전을 재export해 흡수한다(단일 출처).
 */
export const NAV_ICONS: Record<string, string> = {
    // 책등 3권 (내 책장 — QuickNav와 동일)
    books: '<rect x="4" y="4" width="3.4" height="16" rx="1"/><rect x="9.3" y="4" width="3.4" height="16" rx="1"/><path d="M15 5.4l3.3-.7 2.4 15.4-3.3.7z"/>',
    // 사람 (내 책방)
    user: '<circle cx="12" cy="8" r="3.6"/><path d="M5 20a7 7 0 0 1 14 0"/>',
    // 사람 + (팔로우/회원가입)
    follow: '<circle cx="10" cy="8" r="3.4"/><path d="M4 20a6 6 0 0 1 12 0"/><path d="M19 8v6M16 11h6"/>',
    // 사람 − (언팔로우)
    unfollow: '<circle cx="10" cy="8" r="3.4"/><path d="M4 20a6 6 0 0 1 12 0"/><path d="M16 11h6"/>',
    // 금지원 (차단)
    block: '<circle cx="12" cy="12" r="8.5"/><path d="M6 6l12 12"/>',
    // 깃발 (신고)
    report: '<path d="M5 21V4"/><path d="M5 5h11l-2 3 2 3H5"/>',
    // 외부 링크 (구매)
    external: '<path d="M14 4h6v6"/><path d="M20 4l-8 8"/><path d="M18 13v5a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h5"/>',
    // 시계 (독서시간)
    clock: '<circle cx="12" cy="12.5" r="7.5"/><path d="M12 8.5v4.2l2.8 1.8"/><path d="M9.5 3.5h5"/>',
    // 집 (대시보드)
    home: '<path d="M4 11l8-6 8 6"/><path d="M6 10v9h12v-9"/>',
    // 좌화살표 (뒤로/돌아가기)
    back: '<path d="M20 12H4"/><path d="M10 6l-6 6 6 6"/>',
    // 캐럿 (드릴다운/펼침 — CSS rotate)
    chevron: '<path d="M9 6l6 6-6 6"/>',
    // 닫기
    close: '<path d="M6 6l12 12"/><path d="M18 6L6 18"/>',
    // ── 하단 네비 통일로 추가 (QuickNav와 동일 라인아트 스타일) ──
    // 막대 그래프 (독서 기록 — QuickNav와 동일)
    history: '<path d="M3 20h18"/><path d="M6 20v-6"/><path d="M12 20V6"/><path d="M18 20v-9"/>',
    // 돋보기 (탐색/검색 — QuickNav와 동일)
    search: '<circle cx="11" cy="11" r="7"/><path d="M21 21l-4.3-4.3"/>',
    // DNA 나선 (책BTI — QuickNav와 동일)
    personality: '<path d="M7 4c0 5 10 5 10 8s-10 3-10 8"/><path d="M17 4c0 5-10 5-10 8s10 3 10 8"/><path d="M8.5 7h7"/><path d="M8.5 17h7"/>',
    // 방패 (개인정보처리방침)
    privacy: '<path d="M12 3l7 2.6v5.4c0 4.2-2.9 7.2-7 8.5-4.1-1.3-7-4.3-7-8.5V5.6z"/>',
    // 인용부호 (격언)
    quote: '<path d="M7 7c-1.7 0-3 1.3-3 3s1.3 3 3 3c0 1.6-1 2.6-2.6 3.1"/><path d="M17 7c-1.7 0-3 1.3-3 3s1.3 3 3 3c0 1.6-1 2.6-2.6 3.1"/>',
    // 말풍선 (문의)
    feedback: '<path d="M4 5.5A1.5 1.5 0 0 1 5.5 4h13A1.5 1.5 0 0 1 20 5.5v7a1.5 1.5 0 0 1-1.5 1.5H9l-5 4z"/>',
    // 복수 사람 (사용자 목록)
    users: '<circle cx="9" cy="8" r="3.2"/><path d="M3.5 19a5.5 5.5 0 0 1 11 0"/><path d="M16 5.4a3 3 0 0 1 0 5.2"/><path d="M17.5 13.2A5.5 5.5 0 0 1 20.5 19"/>',
    // 자물쇠 (비밀번호 찾기)
    lock: '<rect x="5" y="11" width="14" height="9" rx="1.5"/><path d="M8 11V8a4 4 0 0 1 8 0v3"/>',
    // 케밥(세로 점 3개) — 더보기/액션 메뉴 (점=채운 원 → 인라인 fill 지정)
    more: '<circle cx="12" cy="5"  r="1.6" fill="currentColor" stroke="none"/>'
        + '<circle cx="12" cy="12" r="1.6" fill="currentColor" stroke="none"/>'
        + '<circle cx="12" cy="19" r="1.6" fill="currentColor" stroke="none"/>',
}
