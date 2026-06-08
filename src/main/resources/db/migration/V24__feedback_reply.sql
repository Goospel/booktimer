-- V24 — feedback 에 개발자 답장(reply) 컬럼 추가.
--
-- 작성자 본인만 보는 단일 답장(덮어쓰기). 답장을 달면 앱이 자동으로 읽음 처리한다(별도 처리완료와 구분).
-- nullable varchar — 대부분 답장 없음. 신규 컬럼 추가뿐이라 기존 데이터 무영향.
alter table feedback add column reply varchar(1000);
