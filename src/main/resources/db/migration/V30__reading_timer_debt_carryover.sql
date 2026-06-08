-- 밀린 부채를 대시보드 헤드라인 타이머에 합산해 보여줄지 여부(표시 전용 토글). 기본 켜짐.
-- 부채 모델 자체는 7일 윈도우 per-day(날짜별 독립)로 유지된다 — 이 플래그는 표시만 바꾼다.
-- 기존 사용자도 DEFAULT TRUE로 합산 표시가 켜진다(사용자 합의: 기본 ON).
ALTER TABLE reading_timer ADD COLUMN debt_carryover BOOLEAN NOT NULL DEFAULT TRUE;
