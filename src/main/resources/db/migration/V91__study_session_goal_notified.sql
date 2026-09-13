-- V91 — 회당 시간 도달 푸시를 보낸 시각(세션당 1회 멱등). null = 아직/대상 아님.
--
-- 원본 세션 행에만 찍힌다 — 자정 분할 조각은 종료된 행이라 발송 조건 밖이고 상속할 이유가 없다.
-- 마킹은 이 컬럼만 쓰는 UPDATE다(StudySessionRepository.markGoalNotified) — 엔티티 save는 stop과 경합해
-- ended_at을 되살릴 수 있다.
alter table study_session add column goal_notified_at datetime(6) null;
