-- V18 — 책 카탈로그 메타: 장르(category) + 출간일(pub_date).
-- 책BTI(독서 성향 분석)의 입력 — 장르 편향·신/구 분포를 집계하려면 등록 시 함께 저장해야 한다
-- (설계: claude-docs/reading-personality-design.md §3, §10 Phase 1).
-- 알라딘 검색 응답의 categoryName·pubDate를 적재한다(parse() 매핑). 둘 다 nullable —
-- 기존 행과 수동 등록 책은 null이며, 기존 책은 ISBN으로 알라딘 ItemLookUp 백필 예정(Phase 1b).
-- 컬럼 길이는 엔티티 @Column(length=...)과 일치시킨다(FlywayMigrationTest의 ddl-auto=validate가 드리프트 차단).

alter table book add column category varchar(300);
alter table book add column pub_date varchar(20);
