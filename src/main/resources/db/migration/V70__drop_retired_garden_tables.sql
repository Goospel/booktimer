-- V70 — 은퇴한 정원(꾸미기·도감) 테이블 제거. (2026-08-15)
--
-- V68이 「FK가 탈퇴를 막던」 죽은 테이블 셋을 걷어냈다면, 여기는 나머지 잔재다 — 탈퇴를 막지는
-- 않지만(users FK 없음) **자바 코드가 엔티티도 리포지터리도 네이티브 쿼리도 없이 한 번도 참조하지
-- 않는다**. 스키마에 남아 있으면 다음 사람이 "이건 뭐지"를 매번 다시 조사해야 한다.
--
-- 은퇴 경위: 격자 배치 + 4축 식물 도감(시간·장르·레시피·다양성) + 건물 축으로 만든 데스크톱 정원이
-- **작가 캐릭터 돌봄(서재)**으로 피벗하면서 통째로 대체됐다. 지금 살아 있는 것은 author_character(V45)와
-- author_affection(V52)뿐이고, 이 둘은 건드리지 않는다.
--
-- 순서: 참조하는 쪽을 먼저 지운다. FK가 남아 있으면 부모 DROP이 실패하는데, 그건
-- FlywayMigrationTest가 실 마이그레이션을 H2에 적용하면서 잡는다(로컬 그린 = 순서 검증 완료).

DROP TABLE IF EXISTS user_discovered_plant;
DROP TABLE IF EXISTS genre_plant;
DROP TABLE IF EXISTS recipe_plant;
DROP TABLE IF EXISTS diversity_plant;
DROP TABLE IF EXISTS plant;
DROP TABLE IF EXISTS decoration;
DROP TABLE IF EXISTS publisher_building;
DROP TABLE IF EXISTS building;
