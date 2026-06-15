-- 변형·레이어(살아있는 정원 게임 Phase 2) — 자유 위치 식물에 회전·크기 추가.
-- Phase 1(V42) 자유배치 데이터 보존 → drop 아닌 ALTER ADD. 기존 행은 default(rotation 0·scale 1 = 무변형)로
-- 채워져 Phase 1 배치가 그대로 복원된다. H2(MySQL 모드)·MySQL 공통 문법.
alter table garden_placement add column rotation double not null default 0;  -- 도(0~360)
alter table garden_placement add column scale double not null default 1;     -- 배율(0.5~2.0)
