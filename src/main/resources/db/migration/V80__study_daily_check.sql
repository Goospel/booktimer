-- V80 — 공부 일정의 「지켰나」 원장. 행 1개 = 하루 1판정.
--
-- **자동 판정이 아니라 사용자의 수동 체크**가 이 원장의 요구 그 자체다. 서버는 어떤 경로로도 이 행을
-- 만들거나 고치지 않는다 — 달력의 자동 정보는 「그날 측정이 있었나」(study_session 합)까지고, 목표 대비
-- 달성 배지는 그리지 않는다: 공부 목표엔 변경 이력이 없어(V79 주석) 과거를 현재 목표로 판정하면
-- 목표를 올린 날 과거 달성일이 소급 취소되는 거짓이 생긴다. 체크는 목표와 무관해 그 함정이 없다.
--
-- kept boolean + **행 부재 = 무기록**으로 3상태를 컬럼 하나에 담는다(지킴 / 못 지킴 / 무기록).
-- UNIQUE(user_id, check_date)가 「하루 한 판정」 불변식을 DB에서 지킨다 — 서비스의 조회-후-갱신이
-- 경합에 지더라도 두 번째 INSERT가 여기서 막힌다.
--
-- 컬럼 타입·FK 표기 관례는 V78의 study_session DDL 그대로다(H2·MySQL 양쪽에서 같게 도는 소문자 표기).

create table study_daily_check (
    id         bigint      not null auto_increment,
    user_id    bigint      not null,
    check_date date        not null,
    kept       boolean     not null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id),
    constraint fk_study_daily_check_user foreign key (user_id) references users (id),
    constraint uq_study_daily_check unique (user_id, check_date)
);
