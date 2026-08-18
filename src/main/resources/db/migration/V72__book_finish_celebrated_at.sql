-- V72 — 완독 축하 푸시 멱등 마커. 책당 영구 1회: 한 번 스탬프되면 지우지 않는다
-- (finished_at은 완독 이탈 시 지워져 마커로 못 씀 — 완독↔읽는중 토글로 축하가 반복 발송되던 버그의 원인).
--
-- 백필: 이미 완독된 책은 "축하 소진"으로 간주한다 — 안 하면 마이그레이션 직후 기존 완독 책 전체가
-- 토글 1회로 축하를 한 통씩 다시 받을 수 있는 창이 열린다. 완독 축하는 이미 운영에서 발송돼 온
-- 기능이라 "이미 받은 걸로 간주"가 맞는 방향이고, 실제로 못 받았던 책의 손해도 축하 1통뿐이다.
alter table book add column finish_celebrated_at datetime(6) null;

update book set finish_celebrated_at = finished_at where finished_at is not null;
