-- V61 — 토스 앱인토스(미니앱) 서버 기반: 토스 신원 + 자체 Bearer 토큰 + 기존 계정 연결 코드.
--
-- 1) users.toss_user_key — 토스가 준 앱 단위 사용자 식별자. 이메일이 아니라 이 값이 미니앱의 신원이다
--    (토스 email은 null일 수 있고 소유 보증이 없어 자동 연결이 계정 탈취 벡터가 된다 — 설계 §2.2).
--    NULL 허용(웹 전용 사용자가 다수) + UNIQUE(한 토스 신원은 한 계정에만). MySQL·H2 모두 UNIQUE 인덱스는
--    NULL 중복을 허용하므로 미연결 계정이 여럿이어도 충돌하지 않는다.
-- 2) api_token — 미니앱이 쓰는 불투명 Bearer 토큰. 평문은 발급 응답에만 실리고 DB엔 SHA-256 해시만 둔다
--    (email_token과 같은 정신). 즉시 폐기(logout) 가능하도록 JWT 대신 DB 조회 방식(설계 §2.1).
-- 3) toss_link_code — 웹 설정에서 발급하는 일회용 계정 연결 코드(TTL 5분). 코드 평문 미저장.
--    발급 UI·컨트롤러는 PR-2지만, PR-1의 /api/toss/link가 검증에 의존하므로 저장소는 여기서 만든다.
--
-- datetime(6)은 MySQL·H2 동일 동작(V31·V56 관례).

alter table users add column toss_user_key varchar(64) null;
create unique index uk_users_toss_user_key on users (toss_user_key);

create table api_token (
    id            bigint      not null auto_increment,
    user_id       bigint      not null,
    token_hash    varchar(64) not null,
    expires_at    datetime(6) not null,
    last_used_at  datetime(6),
    created_at    datetime(6) not null,
    updated_at    datetime(6) not null,
    primary key (id),
    constraint uk_api_token_hash unique (token_hash),
    constraint fk_api_token_user foreign key (user_id) references users (id)
);

create table toss_link_code (
    id          bigint      not null auto_increment,
    user_id     bigint      not null,
    code_hash   varchar(64) not null,
    expires_at  datetime(6) not null,
    used_at     datetime(6),
    created_at  datetime(6) not null,
    updated_at  datetime(6) not null,
    primary key (id),
    constraint uk_toss_link_code_hash unique (code_hash),
    constraint fk_toss_link_code_user foreign key (user_id) references users (id)
);
