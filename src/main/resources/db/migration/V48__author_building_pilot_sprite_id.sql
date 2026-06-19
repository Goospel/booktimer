-- V48 — 마을 캐릭터·건물 SVG 승격 파일럿(룩 확정용). (2026-06-19)
--
-- 작가 캐릭터·출판사 건물 비주얼을 이모지 → 코드 벡터 SVG로 승격(식물 A2 패턴 복제).
-- 뷰는 spriteId가 있으면 <symbol>을 텍스처/<use>로 그리고, 없으면(null) 이모지로 폴백한다 —
-- null이 정상 상태다(미승격 종 혼재 무해). 이번 PR은 파일럿 2종(한강·민음사)만 승격해
-- 32px 가독성·접지·배회 룩을 실 게임에서 검증한다. 대표 고유·제네릭 확장은 후속 PR.
--
-- 컬럼은 V45(author_character.sprite_id)·V46(building.sprite_id)에서 이미 추가됨 → 여기선 UPDATE만.
-- spriteId = code 그대로(식물 V40/V41 정신 — 별도 매핑 없이 #sprite-{code} 참조). H2·MySQL 공통.
--
-- (계획 md는 V47 예정이었으나 V47__garden_release_author_placement가 선점 → 다중 세션 번호 조율로 V48.)

update author_character set sprite_id = 'han_gang' where code = 'han_gang';
update building          set sprite_id = 'minumsa'  where code = 'minumsa';
