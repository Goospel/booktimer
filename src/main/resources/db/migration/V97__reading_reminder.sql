-- V97 — 사용자가 직접 켜는 독서 알림(N3 재설계, 2026-09-26).
--
-- users의 컬럼인 이유: 사용자당 1행이고 탈퇴는 users 행 하드 삭제라 파기가 공짜다(별도 테이블이면 FK와
-- purge 가드가 따라 움직인다 — V68 경위). V84·V94와 같은 판단.
-- ⚠️ V50의 daily_reminder_enabled·last_reminder_sent_at(웹 푸시 시절, 매핑 없음)과 무관하다 — 재사용하지 않는다.
--
-- reading_reminder_kind     OFF | DAILY | REST. 기본 OFF = 기존 전 유저 꺼짐(동의 없이 켜지는 사람 0).
-- reading_reminder_hour     보낼 현지 시(8~22). 꺼져 있어도 저장한다(설정에서 켜기 전에 고른다).
-- reading_reminder_on_at    마지막으로 OFF→켬이 된 시각. REST 카운트의 하한 + 「켠 적 있음」(홈 제안 제외).
-- reading_reminder_sent_at  마지막 발송(선점) 시각 — 멱등. 쓰기는 컬럼 단독 UPDATE(UserRepository).
-- 쉬는 날수는 3일 고정 상수다(ReadingReminderService.REST_DAYS) — 선택지를 붙이면 그때 컬럼을 더한다.
-- 인덱스 없음 — 후보 조회는 수백 행 users 풀스캔, 매시 1회.
alter table users add column reading_reminder_kind    varchar(10) not null default 'OFF';
alter table users add column reading_reminder_hour    int         not null default 20;
alter table users add column reading_reminder_on_at   datetime(6) null;
alter table users add column reading_reminder_sent_at datetime(6) null;
