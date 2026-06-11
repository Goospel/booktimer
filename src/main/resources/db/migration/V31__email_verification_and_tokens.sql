-- V31 — 가입 이메일 인증 + 이메일 토큰(이메일 인프라 1단계 PR-B).
--
-- 1) users.email_verified — 가입 이메일 검증 여부. 신규 가입은 false로 시작해 인증 링크를 따라야 true.
--    기존 사용자는 true로 백필(grandfather — 이미 활성·신뢰, 신규 가입부터 검증). pre-hijacking은 *신규 미검증
--    선점* 벡터라 신규부터 막으면 충분(onboarded V6와 동일한 백필 패턴).
-- 2) email_token — 가입 인증·비밀번호 재설정 공용 일회용 토큰. 평문은 메일 링크에만 싣고 DB엔 SHA-256 해시만
--    저장(token_hash, 평문 미저장). type 일치·만료 미경과·미사용(used_at IS NULL)을 모두 통과해야 소비 가능.
--    boolean/datetime(6)는 MySQL·H2 양쪽 동일 동작(V6·V19 관례).

alter table users add column email_verified boolean not null default false;
update users set email_verified = true;

create table email_token (
    id          bigint       not null auto_increment,
    user_id     bigint       not null,
    type        varchar(20)  not null,
    token_hash  varchar(64)  not null,
    expires_at  datetime(6)  not null,
    used_at     datetime(6),
    created_at  datetime(6)  not null,
    updated_at  datetime(6)  not null,
    primary key (id),
    constraint fk_email_token_user foreign key (user_id) references users (id),
    index idx_email_token_hash (token_hash),
    index idx_email_token_user_type (user_id, type)
);
