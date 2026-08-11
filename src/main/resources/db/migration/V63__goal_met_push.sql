-- V63 — "오늘 목표 달성" 푸시: 하루 1회 발송 멱등 마킹 + 활성 세션 스캔 인덱스.
--
-- 1) users.goal_met_pushed_on — 그 사용자에게 목표 달성 푸시를 보낸 날(유저 TZ 일자). 분당 폴링이
--    같은 사람에게 계속 쏘지 않도록 하는 유일한 상태다. 시각(datetime)이 아니라 date인 이유: 멱등 단위가
--    "유저 타임존의 하루"라서 — 시각으로 두면 자정 경계 판정을 읽는 쪽마다 다시 해야 한다.
--    NULL = 아직 한 번도 안 보냄(웹 전용 사용자 다수라 기본값 NULL).
-- 2) idx_reading_session_ended_at — 스케줄러가 분마다 `ended_at is null`로 전 사용자 활성 세션을 훑는다.
--    MySQL은 IS NULL도 인덱스를 타므로 풀스캔을 막는다(세션 테이블은 계속 커지지만 활성은 항상 소수).

alter table users add column goal_met_pushed_on date null;

create index idx_reading_session_ended_at on reading_session (ended_at);
