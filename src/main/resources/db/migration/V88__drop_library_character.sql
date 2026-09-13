-- V88 — 서재 캐릭터 기능 전면 폐기 (2026-09-09). 코드는 직전 배포(PR-1)에서 이미 걷혔다 —
-- 이 파일은 그 뒤에 따로 배포한다(옛 컨테이너가 이 컬럼을 SELECT 하는 채로 drop 하면 전환 중 500).
-- author_affection(V52)은 users FK 자식이라 먼저, author_character(V45)는 독립, users 컬럼은 V54.
--
-- ⚠️ 롤백을 Flyway가 막아 주지 않는다 (2026-09-09 독립 리뷰 실측 — 설계 문서의 반대 서술이 틀렸다).
-- Flyway의 ignoreMigrationPatterns 기본값이 `*:future`라 「적용됐는데 로컬에 없는 마이그레이션」을
-- 무시한다 → 이 레포는 그 값을 덮어쓰지 않으므로, V88이 적용된 DB에 V87까지만 든 이미지를 붙여도
-- validate가 PASSED로 통과하고 기동이 성공한다. 즉 PR-1 **이전** 이미지로 되돌리면 컨테이너가
-- 정상적으로 뜨고 readiness(SELECT 1)까지 통과한 뒤, 인증이 걸린 모든 요청이
-- `Unknown column 'profile_character_code'`로 500이 난다 — 기동 실패보다 나쁜 모양이다.
-- PR-1 이후 이미지로의 롤백은 안전하다(그 코드는 이 컬럼·테이블을 읽지 않는다).
--
-- ⚠️ 되돌릴 수 없다: author_affection 7행·profile_character_code 1명은 백업 없이 버렸다(사용자 확인,
-- 2026-09-09 — 읽는 코드가 0건이라 되살려도 쓸 곳이 없다). author_character 20행은 V45+V48+V49로 재구성 가능.
DROP TABLE IF EXISTS author_affection;
DROP TABLE IF EXISTS author_character;
ALTER TABLE users DROP COLUMN profile_character_code;
