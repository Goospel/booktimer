/**
 * 책방(/u/{loginId}) 라인 아이콘 사전 — 이모지 전면 금지(대시보드 스킨 통일).
 * 각 값 = viewBox 0 0 24 24 안의 inner SVG 프리미티브 문자열. ShopIcon이 공통
 * stroke/fill 래퍼를 씌워 currentColor(부모 color)를 상속한다. (계획 §6)
 */
export const ICONS: Record<string, string> = {
    // 기존 재사용(QuickNav.vue) — 책등 3권
    books: '<rect x="4" y="4" width="3.4" height="16" rx="1"/><rect x="9.3" y="4" width="3.4" height="16" rx="1"/><path d="M15 5.4l3.3-.7 2.4 15.4-3.3.7z"/>',
    // 사람 베이스
    user: '<circle cx="12" cy="8" r="3.6"/><path d="M5 20a7 7 0 0 1 14 0"/>',
    // 사람 + (팔로우)
    follow: '<circle cx="10" cy="8" r="3.4"/><path d="M4 20a6 6 0 0 1 12 0"/><path d="M19 8v6M16 11h6"/>',
    // 사람 − (언팔로우)
    unfollow: '<circle cx="10" cy="8" r="3.4"/><path d="M4 20a6 6 0 0 1 12 0"/><path d="M16 11h6"/>',
    // 금지원 (차단 버튼 + 차단 목록 공유)
    block: '<circle cx="12" cy="12" r="8.5"/><path d="M6 6l12 12"/>',
    // 깃발 (신고)
    report: '<path d="M5 21V4"/><path d="M5 5h11l-2 3 2 3H5"/>',
    // 외부 링크 (구매)
    external: '<path d="M14 4h6v6"/><path d="M20 4l-8 8"/><path d="M18 13v5a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h5"/>',
    // 시계 (독서시간)
    clock: '<circle cx="12" cy="12.5" r="7.5"/><path d="M12 8.5v4.2l2.8 1.8"/><path d="M9.5 3.5h5"/>',
    // 집 (대시보드)
    home: '<path d="M4 11l8-6 8 6"/><path d="M6 10v9h12v-9"/>',
    // 좌화살표 (뒤로)
    back: '<path d="M20 12H4"/><path d="M10 6l-6 6 6 6"/>',
    // 캐럿 (드릴다운/신고 — CSS rotate로 펼침)
    chevron: '<path d="M9 6l6 6-6 6"/>',
    // 닫기 (드릴다운 닫기)
    close: '<path d="M6 6l12 12"/><path d="M18 6L6 18"/>',
}
