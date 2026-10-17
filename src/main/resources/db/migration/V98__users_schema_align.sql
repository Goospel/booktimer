-- V98 — 운영 users를 마이그레이션 정의로 맞춘다 + 웹 푸시 시절 죽은 열 5개 제거 (2026-10-12).
--
-- ① role: 운영 users는 V1이 아니라 Flyway 도입(2026-06-02) 전 ddl-auto(Hibernate 7)가 만들어서,
--    @Enumerated(STRING)이 MySQL 네이티브 enum('ADMIN','USER')로 생성돼 있었다(2026-10-12 운영 information_schema 실측).
--    새 DB·테스트(V1)는 varchar(20)이라 두 환경이 갈려, Role 상수를 더하면 운영에서만 저장이 실패한다.
--    새 DB에선 같은 정의라 아무것도 바뀌지 않고, 운영 값('ADMIN'/'USER')은 그대로 남는다.
--    ⚠️ 이 문장을 맨 앞에 둔다 — MySQL DDL은 문장마다 바로 확정돼 롤백되지 않으므로, 값을 변환하는(실패할 수
--    있는) 유일한 문장이 먼저 실패해야 아무것도 안 바뀐 채 끝난다.
-- ② V50·V51 웹 푸시의 죽은 열 5개. 엔티티 매핑은 2026-07-09(#675)에 지워졌고 운영 값은 0건(2026-10-12, 46명).
--    marketing_push_consent_at·marketing_push_nudge_sent_at은 이 레포의 유일한 timestamp 열이었다
--    (세션 time_zone에 따라 해석이 바뀌고 2038-01-19가 상한).
--    2026-07-09 이후 이미지는 이 열을 읽지 않으므로 V88·V92 같은 2단계 배포가 필요 없다.
-- 회귀 가드: FlywayMigrationTest REQ-01(5열 부재) · REQ-02(운영 ENUM을 재현한 DB에서 varchar 수렴·값 보존). T-263.
alter table users modify role varchar(20) not null;
alter table users drop column marketing_push_consent;
alter table users drop column marketing_push_consent_at;
alter table users drop column marketing_push_nudge_sent_at;
alter table users drop column daily_reminder_enabled;
alter table users drop column last_reminder_sent_at;
