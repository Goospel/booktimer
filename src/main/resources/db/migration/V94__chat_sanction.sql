-- V94 — 맞팔 DM 제재 상태(설계 2026-09-18 §5-1·§5-5).
--
-- 사다리는 경고 → 7일 정지 → 영구다. 경고는 신고 메모(PR-2)라 컬럼이 없고, 정지·영구만 여기 둔다.
-- 둘 다 null이 「제재 없음」이다 — 기존 전 유저가 그 상태로 시작한다(기본값 불필요).
-- 정지 중이면 새 방·발송이 막히고 열람은 된다(ChatEligibility → RESTRICTED).
alter table users add column chat_restricted_until datetime(6) null;
alter table users add column chat_banned_at datetime(6) null;
