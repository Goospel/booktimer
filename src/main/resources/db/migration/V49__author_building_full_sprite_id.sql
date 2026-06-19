-- V49 — 마을 캐릭터·건물 SVG 승격 전종 완료(파일럿 V48 → 나머지 28종). (2026-06-19)
--
-- V48 파일럿(한강·민음사)에서 32px 가독성·접지·배회 룩을 실 게임으로 검증했고, 이번에 나머지
-- 작가 19종·건물 9종을 같은 패턴으로 승격해 전종을 SVG로 채운다. 식물 V40/V41 정신 그대로
-- spriteId = code(별도 매핑 없이 #sprite-{code} 참조). 뷰는 spriteId가 있으면 <symbol>을
-- 텍스처/<use>로 그리고, 없으면(null) 이모지로 폴백한다 — 이제 author_character·building 전 행이
-- 비null이 되어 전부 SVG로 렌더된다(미승격 잔존 0). H2·MySQL 공통.
--
-- 컬럼은 V45(author_character.sprite_id)·V46(building.sprite_id)에서 이미 추가됨 → 여기선 UPDATE만.
-- 파일럿 2행(han_gang·minumsa)은 V48에서 이미 채워졌으나 멱등하게 동일값으로 재설정해도 무해.

-- 작가 캐릭터 — 나머지 19종(han_gang은 V48).
update author_character set sprite_id = 'kim_young_ha'    where code = 'kim_young_ha';
update author_character set sprite_id = 'park_kyung_ri'   where code = 'park_kyung_ri';
update author_character set sprite_id = 'cho_jung_rae'    where code = 'cho_jung_rae';
update author_character set sprite_id = 'jeong_yu_jeong'  where code = 'jeong_yu_jeong';
update author_character set sprite_id = 'kim_cho_yeop'    where code = 'kim_cho_yeop';
update author_character set sprite_id = 'murakami_haruki' where code = 'murakami_haruki';
update author_character set sprite_id = 'higashino_keigo' where code = 'higashino_keigo';
update author_character set sprite_id = 'bernard_werber'  where code = 'bernard_werber';
update author_character set sprite_id = 'victor_hugo'     where code = 'victor_hugo';
update author_character set sprite_id = 'albert_camus'    where code = 'albert_camus';
update author_character set sprite_id = 'guillaume_musso' where code = 'guillaume_musso';
update author_character set sprite_id = 'hermann_hesse'   where code = 'hermann_hesse';
update author_character set sprite_id = 'kafka'           where code = 'kafka';
update author_character set sprite_id = 'dostoevsky'      where code = 'dostoevsky';
update author_character set sprite_id = 'tolstoy'         where code = 'tolstoy';
update author_character set sprite_id = 'marquez'         where code = 'marquez';
update author_character set sprite_id = 'hemingway'       where code = 'hemingway';
update author_character set sprite_id = 'george_orwell'   where code = 'george_orwell';
update author_character set sprite_id = 'jane_austen'     where code = 'jane_austen';

-- 출판사 건물 — 나머지 9종(minumsa는 V48).
update building set sprite_id = 'munhak'      where code = 'munhak';
update building set sprite_id = 'changbi'     where code = 'changbi';
update building set sprite_id = 'gimyoungsa'  where code = 'gimyoungsa';
update building set sprite_id = 'woongjin'    where code = 'woongjin';
update building set sprite_id = 'wisdomhouse' where code = 'wisdomhouse';
update building set sprite_id = 'hangilsa'    where code = 'hangilsa';
update building set sprite_id = 'eulyoo'      where code = 'eulyoo';
update building set sprite_id = 'sigongsa'    where code = 'sigongsa';
update building set sprite_id = 'yolimwon'    where code = 'yolimwon';
