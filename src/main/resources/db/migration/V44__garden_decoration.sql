-- V44 — 독서 정원 꾸미기 소품(데코 카탈로그) — 살아있는 정원 게임 Phase 3. (2026-06-15)
--
-- 식물(plant 등 4축)은 독서 실적으로 '해금'하는 수집 대상이지만, 소품은 보유 무관 장식 오브젝트다 —
-- 길·연못·울타리·벤치처럼 누구나 쓰고, 같은 소품을 여러 개 놓는다. 그래서 식물 배치(garden_placement)와
-- 두 가지가 근본적으로 다르다: (1) 보유 검증이 없고(카탈로그 존재만), (2) 한 종을 여러 번 놓을 수 있다
-- (식물의 uk(user,axis,plant_code) = 한 식물 한 번이 소품엔 안 맞음). 그래서 별 카탈로그·별 배치 테이블로 둔다 —
-- 검증된 식물 배치 도메인을 0줄 건드리지 않아 회귀 0(설계 §2 결정 ①). 같은 패턴: genre_plant·diversity_plant·recipe_plant.
--
-- z_order는 식물과 '통합 단일 스케일'이다 — 연못은 식물 뒤, 벤치는 식물 앞이라 두 종이 한 z 공간에서 교차 정렬돼야 한다
-- (설계 §2 결정 ③). 각 테이블이 전역 z 인덱스를 보관하고, 조회 시 식물+소품을 z로 병합 정렬한다(layoutItemsOf).
--
-- 변형(rotation·scale)은 식물 배치(V43)와 같은 단위·기본값. pos_x/pos_y는 정규화 좌표(0~1, V42와 동일).
-- H2(MySQL 모드)·MySQL 공통 문법(FlywayMigrationTest가 H2 MySQL 모드에서 실제 실행해 호환 검증).

-- 소품 카탈로그(보유 무관·정적 시드). plant 미러 + 해금 임계 없음 + category(팔레트 그룹·미래용).
create table decoration (
    id            bigint       not null auto_increment,
    code          varchar(50)  not null,
    name          varchar(100) not null,
    emoji         varchar(20)  not null,
    sprite_id     varchar(50),                  -- null이면 이모지 폴백(식물 sprite_id와 동일 불변식). 시드는 code와 동일.
    category      varchar(20)  not null,        -- PATH/WATER/FENCE/FURNITURE/NATURE — Phase 3은 평면 팔레트, 그룹화는 미래
    display_order int          not null,
    primary key (id),
    constraint uk_decoration_code unique (code)
);

insert into decoration (code, name, emoji, sprite_id, category, display_order) values
    ('stone_path',     '돌길',        '🟫', 'stone_path',     'PATH',      1),
    ('stepping_stone', '디딤돌',      '🪨', 'stepping_stone', 'PATH',      2),
    ('pond',           '연못',        '💧', 'pond',           'WATER',     3),
    ('fountain',       '분수',        '⛲', 'fountain',       'WATER',     4),
    ('wood_fence',     '나무 울타리', '🪵', 'wood_fence',     'FENCE',     5),
    ('stone_wall',     '돌담',        '🧱', 'stone_wall',     'FENCE',     6),
    ('bench',          '벤치',        '🪑', 'bench',          'FURNITURE', 7),
    ('well',           '우물',        '🪣', 'well',           'FURNITURE', 8),
    ('lantern',        '등불',        '🏮', 'lantern',        'FURNITURE', 9),
    ('signpost',       '표지판',      '🪧', 'signpost',       'FURNITURE', 10),
    ('rock',           '바위',        '🪨', 'rock',           'NATURE',    11),
    ('stump',          '그루터기',    '🪵', 'stump',          'NATURE',    12),
    ('mushrooms',      '버섯 무리',   '🍄', 'mushrooms',      'NATURE',    13);

-- 소품 배치(중복 허용 → uk(user,code) 없음, 각 행 = 한 인스턴스). 식물 배치와 같은 좌표·변형 컬럼.
create table garden_decoration_placement (
    id              bigint      not null auto_increment,
    user_id         bigint      not null,
    decoration_code varchar(50) not null,
    pos_x           double      not null,                 -- 0~1 정규화
    pos_y           double      not null,                 -- 0~1 정규화
    z_order         int         not null default 0,       -- 식물과 통합 단일 스케일(병합 정렬)
    rotation        double      not null default 0,       -- 0~360
    scale           double      not null default 1,       -- 0.5~2.0
    primary key (id),
    constraint fk_garden_decoration_user foreign key (user_id) references users (id)
);
