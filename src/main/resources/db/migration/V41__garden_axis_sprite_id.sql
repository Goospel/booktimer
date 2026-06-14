-- V41 — 독서 정원 타 축(장르·다양성·레시피) 식물 SVG 스프라이트 식별자(A2 후속). (2026-06-15)
--
-- A2(V40)가 시간축(plant)에 깐 sprite_id 파이프라인을 장르·다양성·레시피 3축에 그대로 복제한다.
-- 뷰는 spriteId가 있으면 인라인 <symbol>을 <use href="#sprite-{spriteId}">로 그리고, 없으면(null)
-- 기존 이모지로 폴백한다. 이번 PR로 도감 4축이 전부 SVG가 돼 한 화면 이모지 혼재가 해소된다.
--
-- 33종(장르 13 + 레시피 8 + 다양성 12) 전부 코드 벡터 SVG 부여라 WHERE 없이 전 행 update.
-- spriteId = code 그대로(안정 식별자 재사용 — #sprite-{code}로 참조, 47종 code 전역 유니크).
-- nullable 신규 컬럼이라 미래 추가 행은 null(이모지 폴백)로 무중단. H2(테스트)·MySQL(운영) 공통 SQL.

alter table genre_plant     add column sprite_id varchar(50) null;
alter table diversity_plant add column sprite_id varchar(50) null;
alter table recipe_plant    add column sprite_id varchar(50) null;

-- 장르축 13종.
update genre_plant set sprite_id = code;

-- 다양성축 12종.
update diversity_plant set sprite_id = code;

-- 레시피축 8종.
update recipe_plant set sprite_id = code;
