-- V88 — 서재 캐릭터 기능 전면 폐기 (2026-09-09). 코드는 직전 배포(PR-1)에서 이미 걷혔다 —
-- 이 파일은 그 뒤에 따로 배포한다(옛 컨테이너가 이 컬럼을 SELECT 하는 채로 drop 하면 전환 중 500).
-- author_affection(V52)은 users FK 자식이라 먼저, author_character(V45)는 독립, users 컬럼은 V54.
DROP TABLE IF EXISTS author_affection;
DROP TABLE IF EXISTS author_character;
ALTER TABLE users DROP COLUMN profile_character_code;
