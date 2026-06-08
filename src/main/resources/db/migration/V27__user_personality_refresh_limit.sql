-- 책BTI "다시 분석"(강제 재생성 = 매번 LLM 호출) 일일 횟수 제한 상태.
-- 악의적 반복 클릭이 LLM을 남용해 서버에 부담을 주는 것을 막는다(User.tryConsumePersonalityRefresh).
-- count: 그 날 사용한 횟수, date: 그 카운트가 속한 날짜(사용자 타임존 기준). 날짜가 바뀌면 서비스가 0으로 리셋한다.
-- 기존 사용자는 count=0(default), date=null로 시작 — 첫 클릭 시 그 날짜로 채워지며 자연히 한도가 적용된다.
alter table users add column personality_refresh_count integer not null default 0;
alter table users add column personality_refresh_date date;
