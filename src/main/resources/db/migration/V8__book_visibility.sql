-- V8 — 책별 공개 범위(SNS) 컬럼.
--
-- 사용자가 책장에서 책마다 공개/비공개를 켤 수 있게 한다. SNS에서 남에게 보이는 단위가 책이다.
-- @Enumerated(STRING) 의미를 유지하면서 MySQL·H2 공통으로 도는 varchar로 둔다(V1 enum 관례와 동일).
--
-- 기본값 'PRIVATE' + NOT NULL → 기존 모든 책이 자동으로 PRIVATE로 백필된다(갑작스런 공개 사고 방지).
-- 공개는 사용자가 책마다 명시적으로 켜는 opt-in이다. (별도 UPDATE 불필요 — default가 기존 행을 채운다.)

alter table book add column visibility varchar(20) not null default 'PRIVATE';
