-- V87 — AI 호출의 전역 하루 상한 카운터.
--
-- 왜 필요한가: 지금까지 상한은 전부 study_ai_usage(user_id, usage_date, kind)로 **사용자당**이었다.
-- 그래서 총액에 천장이 없다 — 비용이 계정 수에 선형으로 늘고, 계정 수는 (가입 레이트리밋이 없어)
-- 공격자가 정한다. 관리자 계정이 뚫려 전원이 승인되는 시나리오에서 마지막으로 남는 방어선이 이 테이블이다.
-- (보안 리뷰 2026-09-08 S-1)
--
-- 왜 usage_date가 UTC인가: study_ai_usage의 날짜 키는 StudyDates.today = **유저 타임존**이다. 그런데
-- 타임존은 사용자가 설정에서 제한 없이 바꿀 수 있어, 같은 순간에 로컬 날짜가 둘이 된다(UTC-12 ~ UTC+14).
-- 전역 상한이 같은 키를 쓰면 자정 근처에 타임존만 바꿔 그 날 몫을 두 배로 쓸 수 있다 — 막으려는 것에
-- 같은 구멍이 뚫린다. 그래서 이 테이블만 UTC 달력일로 센다(사용자에게 보이는 「오늘」과 무관한 내부 키).
--
-- 왜 종류(kind)를 안 나누나: 나누면 테이블·설정·검사가 3배가 되는데 얻는 것은 정밀도뿐이다. 단일 상한을
-- 가장 비싼 호출 기준으로 잡으면 총액이 보수적으로 묶이고, 종류별 내역은 앱 로그에 이미 있다.
--
-- 카운터이지 로그가 아니다(study_ai_usage와 같은 규율) — 외부 호출이 실패하면 되돌려야 하기 때문이다.

create table study_ai_daily_total (
    id          bigint       not null auto_increment,
    usage_date  date         not null,
    used        int          not null default 0,
    created_at  datetime(6),
    updated_at  datetime(6),
    primary key (id),
    constraint uq_study_ai_daily_total unique (usage_date)
);
