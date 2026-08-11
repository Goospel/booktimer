-- V62 — 리워드 광고 보상: 밀린 하루 용서(waiver). 부채는 유도값(무저장)이라 "용서한 날짜" 마킹만 저장한다.
--
-- uk_goal_waiver_grant(user_id, granted_on)가 일일 1회 상한, uk_goal_waiver_date(user_id, waived_date)가
-- 같은 날 중복 용서 방지 — 상한을 애플리케이션 검사가 아니라 DB 제약으로 강제한다(동시 요청 race 포함).
-- 왜 DB 제약인가: 앱인토스 SDK에 서버사이드 보상 검증(SSV)이 없어 지급 요청은 클라이언트 주장일 뿐이다.
-- 신뢰 대신 상한으로 캡한다 — 최악의 경우에도 "하루 1회, 밀린 하루 표시 소거"가 전부다.
--
-- datetime(6)은 MySQL·H2 동일 동작(V31·V56·V61 관례).

create table reading_goal_waiver (
    id          bigint      not null auto_increment,
    user_id     bigint      not null,
    waived_date date        not null,  -- 용서된 날(유저 TZ 일자)
    granted_on  date        not null,  -- 지급된 날(유저 TZ 오늘) — 일일 상한의 키
    created_at  datetime(6) not null,
    updated_at  datetime(6) not null,
    primary key (id),
    constraint uk_goal_waiver_date  unique (user_id, waived_date),
    constraint uk_goal_waiver_grant unique (user_id, granted_on),
    constraint fk_goal_waiver_user  foreign key (user_id) references users (id)
);
