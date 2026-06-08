-- V26 — 책방 노출용 공개 책BTI 캐시(reading_personality_public). (책BTI Phase 6)
--
-- 본인용 캐시(V19 reading_personality)와 구조는 같되 입력 책 범위가 다르다(공개+완독 책만) — 같은 사용자라도
-- 본인용/공개용 두 결과가 공존하므로 별도 테이블에 따로 저장한다(reading-personality-design §7).
-- 한 테이블 + scope 컬럼으로 합치지 않은 이유: V19의 user_id unique를 (user_id, scope) 복합으로 바꾸려면
-- 기존 unique 제약 드롭이 필요한데 그 SQL이 MySQL(DROP INDEX)·H2(DROP CONSTRAINT)에서 갈려 이식이 위험하다.
-- 별도 테이블은 CREATE만 하므로(V19와 동일) 양쪽 안전 + 기존 본인용 경로/테스트 무손상(additive).

create table reading_personality_public (
    id              bigint        not null auto_increment,
    user_id         bigint        not null,
    narrative       varchar(4000) not null,
    tags            varchar(500),
    input_signature varchar(64)   not null,
    generated_at    datetime(6)   not null,
    created_at      datetime(6)   not null,
    updated_at      datetime(6)   not null,
    primary key (id),
    constraint uk_reading_personality_public_user unique (user_id),
    constraint fk_reading_personality_public_user foreign key (user_id) references users (id)
);
