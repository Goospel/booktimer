-- V100 — users.apple_sub: 아이폰 앱 「책생글」의 Apple 로그인 신원(identity token의 sub — 최대 64자, 영숫자·점).
--
-- toss_user_key(V61)와 같은 규칙이다: NULL이면 미연결이고, 붙으면 once-set(User.linkAppleSub). 해제는 웹 설정의
-- 「앱 연결 해제」와 탈퇴만 한다. 한 Apple 신원이 두 계정에 붙지 않도록 UNIQUE가 최종 방어선이다 — NULL 여러 개는
-- 허용된다(MySQL·H2 UNIQUE 동작 같음).
--
-- auth_provider에 'APPLE'이 새로 들어간다. 운영 users.auth_provider·role이 V98 이전처럼 네이티브 ENUM이면 새 상수
-- 저장이 운영에서만 실패하므로(T-263) 먼저 쟀다 — 2026-10-21 운영 information_schema 실측 둘 다 varchar(20)
-- (설계 원장 U-13). 그래서 MODIFY를 두지 않는다.
alter table users add column apple_sub varchar(64) null;
create unique index uk_users_apple_sub on users (apple_sub);
