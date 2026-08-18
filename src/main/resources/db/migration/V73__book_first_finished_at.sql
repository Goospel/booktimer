-- V73 — 첫 완독 시각. 홈 소식 피드 "완독했어요" 이벤트의 시각을 책당 영구 1회로 고정한다:
-- 한 번 스탬프되면 완독 이탈에도 지우지 않고 재완독에도 밀지 않는다(finished_at은 이탈 시 지워지고
-- 재완독마다 새 시각이라 피드 시각으로 못 쓴다 — 완독↔읽는중 토글로 옛 책이 "방금"처럼 피드 맨 위에
-- 재부상하던 버그의 원인). finished_at 자체는 책방 「완독 최신순」 정렬의 소스라 의미를 그대로 둔다.
--
-- 백필: 지금 피드가 보여주는 완독 시각이 곧 finished_at이므로, 복사하면 배포 전후 피드가 동일하다.
-- 안 하면 신 컬럼이 전부 null이라 창(14일) 안의 완독 이벤트가 배포 즉시 전부 사라진다.
-- 과거에 토글된 책의 finished_at은 이미 재스탬프된 근사치지만 진짜 첫 완독 시각은 어디에도 없다
-- (이벤트를 적재하지 않는 파생 구조) — 알 수 있는 최선값이고 현 표시값과 같아 악화가 아니다.
alter table book add column first_finished_at datetime(6) null;

update book set first_finished_at = finished_at where finished_at is not null;
