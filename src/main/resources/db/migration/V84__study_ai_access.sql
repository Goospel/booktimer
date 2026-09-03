-- V84 — 공부 화면 AI 기능의 관리자 승인 상태(NONE | PENDING | APPROVED | REJECTED).
--
-- **테이블이 아니라 users의 컬럼**인 이유(V79와 같은 판단): 관리자 화면이 필요로 하는 것은 「대기 목록」과
-- 「승인자 목록」 둘뿐이고, 그건 이 컬럼 하나의 조회(where study_ai_access = ?)로 끝난다. 신청 이력 테이블을
-- 지금 만들면 아무도 안 읽는 행만 쌓인다. 이력이 필요해지는 날엔 이 컬럼을 현재 상태 캐시로 남긴 채
-- 테이블을 얹어 승격할 수 있다.
--
-- DEFAULT 'NONE' = 기존 전 유저 「신청한 적 없음」 — 아무도 자동으로 AI가 켜지지 않는다(관리자 본인 포함).
-- study_ai_access_at은 마지막 전이 시각(대기 큐 정렬 · 「M월 D일 신청」 표시)이라 미신청자는 null이다.

alter table users add column study_ai_access    varchar(10) not null default 'NONE';
alter table users add column study_ai_access_at datetime(6);
