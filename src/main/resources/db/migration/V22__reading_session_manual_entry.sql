-- 독서 세션에 "수동 입력(빠뜨린 날 직접 채우기)" 여부 플래그 추가.
-- 잔디에서 실시간 측정과 손으로 채운 날을 테두리로 구분하는 데 쓴다.
-- boolean은 MySQL(tinyint(1))·H2 양쪽에서 동일하게 동작한다(V6 선례).
alter table reading_session add column manual_entry boolean not null default false;

-- 기존 행 백필(휴리스틱): 측정 시작일과 행 생성일이 다르면 = 과거를 나중에 적은 백데이트 기록 = 수동 입력.
-- 실시간 측정은 시작과 동시에 행이 생겨 두 날짜가 같다. (같은 날 수동 입력은 못 가려내지만, 그 경우
-- 시각적으로 측정과 구별 의미가 약하고, 이후 신규 기록은 manual_entry를 정확히 박으므로 과거치만의 근사다.)
update reading_session set manual_entry = true
where date(started_at) <> date(created_at);
