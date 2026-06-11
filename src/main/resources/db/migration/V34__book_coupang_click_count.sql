-- 쿠팡 파트너스 "구매" 클릭 누적 컬럼 — 알라딘 click_count(V5)와 분리 집계.
-- 기존 책 전부 0으로 시작(백필 의미 없음 — 클릭은 발생 시점부터 쌓임).
ALTER TABLE book ADD COLUMN coupang_click_count BIGINT NOT NULL DEFAULT 0;
