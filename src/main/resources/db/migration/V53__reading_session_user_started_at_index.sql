-- V53 — 친구 추천 활동량 폴백(E 생성기) 최적화.
-- (user_id, started_at) 복합 인덱스: GROUP BY user_id + WHERE started_at >= :since 범위 스캔 지원.
create index ix_reading_session_user_started_at on reading_session (user_id, started_at);
