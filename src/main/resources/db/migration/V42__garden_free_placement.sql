-- V42 — 독서 정원 배치를 격자 셀 → 자유 위치(정규화 좌표)로 전환. (2026-06-15)
--
-- 자유 위치 전환(Phase 1): "어느 격자 칸"이 아니라 "어디든"에 식물을 놓는다. 위치 모델을 정수 cell_index 하나에서
-- 정규화 좌표(pos_x·pos_y, 0~1)+겹침 순서(z_order)로 바꾼다. 정규화라 월드 픽셀 크기가 바뀌어도 데이터가 불변이고
-- 반응형에 자연 정합한다(설계 §2.3). 겹침을 허용하므로 한 좌표에 둘 이상 놓일 수 있어 셀 유니크(uk_..._cell)는 없앤다.
--
-- 기존 배치 폐기: 베타·소수 데이터라 격자 좌표를 자유 좌표로 변환하지 않고 비우고 재생성한다(사용자 합의).
-- drop+create로 가는 이유: alter ... drop constraint의 H2(MySQL 모드)↔MySQL 문법 차이를 피하고 constraint 이름에
-- 의존하지 않기 위해서다(FlywayMigrationTest가 H2 MySQL 모드에서 이 SQL을 실제 실행해 호환을 검증한다).
--
-- 유지: uk(user, axis, plant_code) = 한 식물 한 번만 배치(자유 위치라도 같은 식물을 두 곳에 둘 수 없다).
-- (axis, plant_code) 복합 식별 근거는 V39 주석 참고 — code가 축 간 비유니크라서다.

drop table garden_placement;

create table garden_placement (
    id         bigint      not null auto_increment,
    user_id    bigint      not null,
    axis       varchar(20) not null,
    plant_code varchar(50) not null,
    pos_x      double      not null,
    pos_y      double      not null,
    z_order    int         not null default 0,
    primary key (id),
    constraint uk_garden_placement_plant unique (user_id, axis, plant_code),
    constraint fk_garden_placement_user  foreign key (user_id) references users (id)
);
