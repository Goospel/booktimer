-- V25 — 책BTI 책방 노출 opt-in 플래그(personality_public). (reading-personality-design Phase 6)
--
-- 기본 false = 비노출. 이 프로젝트의 "기본 PRIVATE·opt-in" 불변식대로, 본인이 명시적으로 켜야
-- 공개 프로필(/u/{loginId})에 책BTI가 뜬다. 기존 사용자도 노출에 동의한 적 없으므로 false 그대로 둔다
-- (V6 onboarded처럼 true 백필하지 않는다 — 노출은 명시 opt-in이어야 하므로).
-- boolean은 MySQL(tinyint(1))·H2 양쪽에서 동일하게 동작한다(이식 안전).

alter table users add column personality_public boolean not null default false;
