-- V68 — 죽은 테이블 3개 제거. (2026-08-15)
--
-- 셋 다 **자바 코드가 한 번도 참조하지 않는다** — 엔티티도 리포지터리도 네이티브 쿼리도 없고, 오직
-- 마이그레이션에만 존재했다. 그런데 users를 향한 FK가 `NO ACTION`으로 살아 있어서, 행이 하나라도 있으면
-- **그 사용자의 탈퇴가 FK 제약 위반으로 실패**했다(AccountService.purge()가 지울 수단 자체가 없다).
-- 운영 실측(2026-08-15): garden_placement 2행/2명 · garden_decoration_placement 0행 · push_subscriptions 0행
-- → 27명 중 2명이 탈퇴 불가 상태였다.
--
-- 은퇴 경위:
--   garden_placement / garden_decoration_placement (V39·V42·V43·V44) — 격자 배치 기반 데스크톱 정원.
--     작가 캐릭터를 좌표 배치에서 배회로 돌리고(V47) 무대 자체를 돌봄 뷰(서재)로 바꾸면서 렌더 경로가 사라졌다.
--   push_subscriptions (V50) — 웹 푸시(PWA L3a). 알림은 토스 미니앱 푸시로 옮겨 갔다.
--
-- 그래서 purge()에 삭제를 덧붙이는 대신 **테이블을 없앤다** — 죽은 것을 위해 코드를 새로 쓰지 않는다.
-- 회귀 가드: FlywayMigrationTest#everyTableWithForeignKeyToUsersIsClearedByPurge 가
-- "users를 FK 참조하는 테이블 집합 == purge()가 지우는 집합"을 양방향으로 단언한다.
--
-- ⚠️ decoration(소품 카탈로그, V44)은 users FK가 없어 탈퇴를 막지 않으므로 이번 범위 밖이다 —
--    남은 은퇴 정원 테이블과 함께 별도 좀비 정리에서 다룬다.

DROP TABLE IF EXISTS garden_decoration_placement;
DROP TABLE IF EXISTS garden_placement;
DROP TABLE IF EXISTS push_subscriptions;
