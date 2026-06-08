-- 책BTI 공개/비공개 분기 폐지(2026-06-08) — 성향을 공개(PUBLIC)+완독 책만으로 단일 생성해 항상 책방에 노출한다.
-- 어제(PR #240) 도입했던 분리 구조가 군더더기가 됐다:
--   · 공개 전용 캐시 테이블 reading_personality_public(V26) → 단일 캐시 reading_personality로 통합, 제거
--   · users.personality_public opt-in 플래그(V25) → 무조건 공개라 불필요, 제거
-- 먼저 테이블(users를 FK 참조)을 지운 뒤 플래그 컬럼을 드롭한다.
drop table if exists reading_personality_public;
alter table users drop column personality_public;
