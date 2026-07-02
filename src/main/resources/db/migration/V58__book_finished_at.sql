-- V58 — 책방 "완독 시간 순" 정렬용 완독 시각(finished_at).
-- 완독(FINISHED) 진입 시 기록, 이탈 시 클리어(불변식: FINISHED ⇒ finished_at 존재).
-- 기존 완독 책은 updated_at으로 백필한다 — 최선 근사(클릭 집계·공개 토글로 다소 밀렸을 수 있음).
-- 이후의 상태 변경부터는 도메인(Book.changeStatus)이 정확한 시각을 기록한다.

alter table book add column finished_at datetime(6) null;

update book set finished_at = updated_at where status = 'FINISHED';
