-- V68 — 로그인 아이디 평생 1회 변경: 옛 핸들 보관 컬럼.
--
-- 이 컬럼 하나가 두 가지를 겸한다:
--   ① 옛 핸들 영구 예약 — UNIQUE라 제3자가 그 아이디로 갈아탈 수 없다(사칭·핸들 세탁 차단).
--   ② 변경권 소진 판정 — NOT NULL이면 이미 1회 썼다는 뜻이라, 별도 카운터·이력 테이블이 필요 없다.
--
-- nullable + unique라 (MySQL·H2 공통) NULL은 여럿 허용된다 — 아직 안 바꾼 계정 전원이 NULL이어도 무충돌.
-- login_id(V13)와 같은 길이(varchar(50))를 쓴다 — 옛 login_id 값이 그대로 옮겨오기 때문.
alter table users add column previous_login_id varchar(50);
alter table users add constraint uk_users_previous_login_id unique (previous_login_id);
