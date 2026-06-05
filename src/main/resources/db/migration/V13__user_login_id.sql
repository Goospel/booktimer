-- V13 — 로그인 아이디(login_id) 컬럼.
--
-- email 대신 비공개 아이디로 로그인/식별하기 위한 토대(설계: claude-docs/login-id-design.md).
-- 공개된 이메일이 로그인 표적으로 노출되는 약점을 제거한다 — login_id는 어디에도 공개 노출하지 않는다
-- (공개 핸들은 nickname뿐).
--
-- 처음 nullable: 가입/온보딩이 login_id를 채우기 전(PR-2)이라 증분 배포가 안전하다. 모든 생성 경로가
-- login_id를 채운 뒤 NOT NULL로 좁힌다(PR-4). 기존 사용자는 인증 컷오버(PR-3) 직전 wipe하므로 백필하지 않는다.
-- unique는 처음부터 — nullable + unique라 (MySQL·H2 공통) NULL은 여럿 허용되고 값은 유일하다.
alter table users add column login_id varchar(50);
alter table users add constraint uk_users_login_id unique (login_id);
