-- V14 — nickname 유니크 제약 제거(uk_users_nickname, V7에서 추가).
--
-- 식별/검색의 공개 핸들이 nickname에서 login_id로 옮겨간다(설계: claude-docs/login-id-design.md, X·인스타 모델).
-- nickname은 이제 단순 표시 이름이라 중복을 허용하고 자유롭게 바꿀 수 있다. 영구 식별자는 불변의 login_id다.
--
-- H2(테스트)는 Hibernate가 엔티티 매핑으로 스키마를 생성하므로 이 제약이 애초에 없다 — 이 마이그레이션은
-- 운영(MySQL, Flyway)에서만 실효가 있다. FlywayMigrationTest는 H2에 V7→V14를 순서대로 적용해 검증한다.
alter table users drop constraint uk_users_nickname;
