-- V5 — 책별 제휴 구매 클릭 수.
-- 사용자가 "구매"(알라딘 제휴링크)를 누른 횟수를 책 단위로 집계한다(제휴 수익 데이터 토대).
-- 어떤 책이 실제 구매 의향(클릭)을 내는지 보는 가장 단순한 형태 — 기존 행은 default 0.
-- 추후 기간별/전환율 분석이 필요하면 별도 이벤트 테이블로 확장 가능.

alter table book add column click_count bigint not null default 0;
