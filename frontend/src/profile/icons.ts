/**
 * 책방(/u/{loginId}) 아이콘 사전 — 공통 navIcons로 흡수(단일 출처). ShopIcon은 그대로
 * `import { ICONS } from './icons'`를 쓰므로 이 재export만으로 무수정 호환된다. 신규
 * 아이콘은 ../shared/navIcons.ts 에 추가한다(여기 직접 추가 금지 — drift 방지).
 */
export { NAV_ICONS as ICONS } from '../shared/navIcons'
