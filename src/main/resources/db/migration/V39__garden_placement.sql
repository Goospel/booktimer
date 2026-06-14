-- V39 — 독서 정원 식물 배치(꾸미기) 저장. (2026-06-14)
--
-- 설계: 도감(시간 V35·장르 V36·레시피 V37·다양성 V38)은 보유를 '유도'(시간·장르·다양성)하거나 발견을
-- '저장'(레시피)하지만, 이 테이블은 "어느 격자 칸에 놓을지"라는 사용자의 꾸미기 의도를 저장한다 — 도감 보유와
-- 생명주기가 다르다(보유를 잃어도 행은 남고, 렌더 시 현재 보유와 교집합해 유령 식물을 거른다 — GardenLayoutService.layoutOf).
--
-- (axis, plant_code) 복합 식별: 식물 code가 축 간 전역 유니크가 아니라서다(uk_plant_code·uk_genre_plant_code·
-- uk_diversity_plant_code가 각자 테이블 안에서만 유니크 — 같은 'lotus'라도 축이 다르면 다른 식물). axis는
-- PlacementAxis enum(EnumType.STRING) — 'TIME'/'GENRE'/'DIVERSITY'/'RECIPE'.
--
-- 두 유니크로 무결성: uk(user,axis,plant_code) = 한 식물 한 번만 배치, uk(user,cell_index) = 한 셀에 하나만.
-- 저장은 유저 범위 통째 교체(delete+insert)라 부분 수정·중복이 행별로 깔끔(설계 §2.4). H2(테스트)·MySQL(운영) 공통 SQL.

create table garden_placement (
    id         bigint      not null auto_increment,
    user_id    bigint      not null,
    axis       varchar(20) not null,
    plant_code varchar(50) not null,
    cell_index int         not null,
    primary key (id),
    constraint uk_garden_placement_plant unique (user_id, axis, plant_code),
    constraint uk_garden_placement_cell  unique (user_id, cell_index),
    constraint fk_garden_placement_user  foreign key (user_id) references users (id)
);
