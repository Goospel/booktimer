-- V40 — 독서 정원 식물 SVG 스프라이트 식별자(A2). (2026-06-15)
--
-- A2: 식물 비주얼을 이모지 → 코드 벡터 SVG로 승격한다. 뷰는 spriteId가 있으면 인라인 <symbol>을
-- <use href="#sprite-{spriteId}">로 그리고, 없으면(null) 기존 이모지로 폴백한다 — null이 정상 상태다.
-- 이번 PR 범위는 시간축(plant) 14종 완결. 타 축(장르·다양성·레시피)은 후속 PR에서 같은 패턴 복제.
--
-- nullable 신규 컬럼이라 기존 행은 null(이모지 폴백)로 무중단. H2(테스트)·MySQL(운영) 공통 SQL.

alter table plant add column sprite_id varchar(50) null;

-- 시간축 14종 전부 코드 벡터 SVG 부여(성장 서사 일관성 — 일부만 SVG면 도감서 섞여 어색).
-- spriteId = code 그대로(안정 식별자 재사용 — 별도 매핑 없이 #sprite-{code}로 참조).
update plant set sprite_id = 'sprout'         where code = 'sprout';
update plant set sprite_id = 'herb'           where code = 'herb';
update plant set sprite_id = 'clover'         where code = 'clover';
update plant set sprite_id = 'seedling_pot'   where code = 'seedling_pot';
update plant set sprite_id = 'tulip'          where code = 'tulip';
update plant set sprite_id = 'daisy'          where code = 'daisy';
update plant set sprite_id = 'sunflower'      where code = 'sunflower';
update plant set sprite_id = 'cherry_blossom' where code = 'cherry_blossom';
update plant set sprite_id = 'rose'           where code = 'rose';
update plant set sprite_id = 'cactus'         where code = 'cactus';
update plant set sprite_id = 'bamboo'         where code = 'bamboo';
update plant set sprite_id = 'maple'          where code = 'maple';
update plant set sprite_id = 'evergreen'      where code = 'evergreen';
update plant set sprite_id = 'deciduous_tree' where code = 'deciduous_tree';
