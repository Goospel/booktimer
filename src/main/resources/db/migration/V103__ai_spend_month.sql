-- V103 — 앱 글쓰기 AI 월 원가 계량기(책생글 PR-C) — 전역 차단기.
--
-- 왜 필요한가: 기존 장치(study_ai_usage · study_ai_daily_total)는 호출 「수」만 센다. 글쓰기 AI는 호출마다 원가가
-- 수십 배 다르고(재료 크기·출력 길이), 호출 수로는 월 원가를 묶지 못한다. 이 행 하나가 「이번 달 서비스 전체가 쓴
-- micro-USD」이고, 누적이 booktimer.writing.monthly-budget-usd 이상이면 호출 전에 503이다(AiBudgetService).
--
-- 월 키는 UTC다(study_ai_usage S-3와 같은 이유 — 사용자 타임존은 바꿀 수 있어 키로 쓸 수 없다).
-- 가산은 조건 없는 원자적 UPDATE(micro_usd = micro_usd + :d)이고, 행이 없을 때의 경합은 UNIQUE가 심판한다(T-261 패턴).
-- 공부 AI 호출은 여기에 넣지 않는다(승인 1명 · 하루 몫으로 이미 묶여 있다).
create table ai_spend_month (
    id          bigint      not null auto_increment,
    spend_month char(7)     not null,
    micro_usd   bigint      not null default 0,
    created_at  datetime(6) not null,
    updated_at  datetime(6) not null,
    primary key (id),
    constraint uq_ai_spend_month unique (spend_month)
);
