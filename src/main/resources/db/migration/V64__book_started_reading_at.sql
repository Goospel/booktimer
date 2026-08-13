-- V64 — 홈 소식 피드 "읽기 시작했어요" 이벤트용 시작 시각(started_reading_at).
-- READING 진입 시 기록(재독이면 새 시각). finished_at의 미러이되 한 곳이 다르다:
-- 완독으로 넘어가도 지우지 않는다 — 시작·완독 두 이벤트가 시간순으로 공존해야 피드가 자연스럽다.
--
-- 백필은 하지 않는다: 피드 창이 최근 14일이라 과거 책의 시작 이벤트는 어차피 창 밖이고,
-- null이면 시작 피드에 안 뜰 뿐 깨지지 않는다(updated_at 근사는 "시작" 의미가 아니라 오염만 낳는다).

alter table book add column started_reading_at datetime(6) null;
