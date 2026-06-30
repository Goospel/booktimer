-- 프로필 사진 기능: 사용자가 도감에서 보유(완독)한 작가의 얼굴(SVG 스프라이트)을 아바타로 쓸 수 있게,
-- 선택한 작가 캐릭터 코드(author_character.code)를 저장한다.
-- NULL = 미선택 → 기존 로그인ID 이니셜 원형으로 폴백(opt-in, 기존 사용자 영향 없음).
-- 파일 업로드/이미지 저장 없이 기존 스프라이트(#sprite-{code})와 보유 판정을 재사용하므로 컬럼 하나로 충분하다.
ALTER TABLE users ADD COLUMN profile_character_code varchar(50) DEFAULT NULL;
