-- V1 — 초기 스키마 baseline.
--
-- 기존 운영 DB는 baseline-on-migrate=true / baseline-version=1 설정으로 "V1이 이미 적용된 것"으로
-- 표시되며 이 스크립트가 실행되지 않는다. 신규 환경(테스트 H2, 새 배포)에서만 실제로 실행된다.
--
-- enum 컬럼(role / auth_provider)은 @Enumerated(STRING) 의미를 유지하면서 MySQL·H2 양쪽에서
-- 동일하게 도는 varchar로 둔다(네이티브 ENUM 타입은 이식성이 떨어짐). 시각은 Instant → datetime(6).
-- password_hash 는 NULL 허용 — 소셜(OAuth) 계정은 비밀번호가 없다.

create table users (
    id            bigint       not null auto_increment,
    email         varchar(255) not null,
    password_hash varchar(255),
    nickname      varchar(255) not null,
    timezone      varchar(255) not null,
    role          varchar(20)  not null,
    auth_provider varchar(20)  not null default 'LOCAL',
    created_at    datetime(6)  not null,
    updated_at    datetime(6)  not null,
    primary key (id),
    constraint uk_users_email unique (email)
);

create table reading_timer (
    id                      bigint      not null auto_increment,
    user_id                 bigint      not null,
    remaining_seconds       bigint      not null,
    daily_increment_seconds bigint      not null,
    cap_seconds             bigint      not null,
    last_accrual_date       date        not null,
    created_at              datetime(6) not null,
    updated_at              datetime(6) not null,
    primary key (id),
    constraint uk_reading_timer_user unique (user_id),
    constraint fk_reading_timer_user foreign key (user_id) references users (id)
);

create table reading_session (
    id               bigint      not null auto_increment,
    user_id          bigint      not null,
    started_at       datetime(6) not null,
    ended_at         datetime(6),
    duration_seconds bigint      not null,
    created_at       datetime(6) not null,
    updated_at       datetime(6) not null,
    primary key (id),
    constraint fk_reading_session_user foreign key (user_id) references users (id)
);
