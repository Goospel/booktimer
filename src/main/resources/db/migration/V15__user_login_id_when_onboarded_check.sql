-- V15 — login_id 무결성: 온보딩 끝난 정식 계정은 login_id가 반드시 있다.
--
-- 단순 NOT NULL은 불가능하다 — OAuth 사용자는 프로비저닝(INSERT) 시점엔 login_id가 없고
-- 온보딩에서 비로소 정한다(설계: claude-docs/login-id-design.md, PR-4). 그 전환 창에선
-- login_id=null이 정상이다. 그래서 상태에 의존하는 조건부 불변식으로 좁힌다:
--   onboarded = true  ⟹  login_id IS NOT NULL
-- (로컬 가입은 가입에서 login_id를 받으므로 항상 만족. OAuth는 온보딩 완료와 동시에 채워진다.)
--
-- H2(테스트, MySQL 모드)·MySQL 8 모두 CHECK 제약을 강제한다. 메인 테스트 스위트는 Hibernate가
-- 엔티티 매핑으로 스키마를 생성하므로 이 제약이 없다(V14 nickname-unique-drop과 동일) — 이 제약은
-- 운영(MySQL, Flyway)에서만 실효가 있고, FlywayMigrationTest가 Flyway 스키마에 적용해 검증한다.
alter table users
    add constraint ck_users_login_id_when_onboarded
    check (onboarded = false or login_id is not null);
