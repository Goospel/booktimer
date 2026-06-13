-- V35 — 독서 정원 식물 카탈로그(트랙 A 1단계). (2026-06-13)
--
-- 설계: 보유 식물은 저장하지 않고 독서 실적의 함수로 유도한다(부채 모델 N-058·Lazy accrual N-001).
-- 따라서 보유 이력 테이블은 없고, 이 정적 카탈로그 한 장만 둔다 — "누적 목표 달성일 >= unlock_threshold_days"
-- 면 보유로 친다. 해금은 임계 오름차순 순차(다음 식물 예측 동기). 비주얼은 1차엔 이모지(utf8mb4 — 기존
-- 책 제목이 이미 이모지/한글을 저장 중이라 charset 무문제). H2(테스트)·MySQL(운영) 공통 SQL.
--
-- 임계 곡선: 초반 촘촘(즉시 보상 — 1일차 첫 새싹) → 후반 띄엄(장기 동기 — 365일 큰나무). thesis(하루 최소 습관) 정합.

create table plant (
    id                    bigint       not null auto_increment,
    code                  varchar(50)  not null,
    name                  varchar(100) not null,
    emoji                 varchar(20)  not null,
    display_order         int          not null,
    unlock_threshold_days bigint       not null,
    primary key (id),
    constraint uk_plant_code unique (code)
);

insert into plant (code, name, emoji, display_order, unlock_threshold_days) values
    ('sprout',         '새싹',      '🌱', 1,  1),
    ('herb',           '허브',      '🌿', 2,  3),
    ('clover',         '클로버',    '☘️', 3,  5),
    ('seedling_pot',   '화분 모종', '🪴', 4,  7),
    ('tulip',          '튤립',      '🌷', 5,  10),
    ('daisy',          '데이지',    '🌼', 6,  14),
    ('sunflower',      '해바라기',  '🌻', 7,  21),
    ('cherry_blossom', '벚꽃',      '🌸', 8,  30),
    ('rose',           '장미',      '🌹', 9,  45),
    ('cactus',         '선인장',    '🌵', 10, 60),
    ('bamboo',         '대나무',    '🎍', 11, 90),
    ('maple',          '단풍나무',  '🍁', 12, 120),
    ('evergreen',      '침엽수',    '🌲', 13, 180),
    ('deciduous_tree', '큰나무',    '🌳', 14, 365);
