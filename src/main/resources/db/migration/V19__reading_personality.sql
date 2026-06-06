-- V19 — 캐시된 책BTI 결과(reading_personality) 테이블 (책BTI Phase 4: 저장·캐시·갱신).
--
-- LLM이 만든 성향 서술 + 태그를, 그것을 만든 입력 시그니처와 함께 사용자당 1행으로 저장한다.
-- 매 조회마다 LLM을 부르지 않기 위한 파생 캐시 — 책장이 의미있게 변하면(input_signature 불일치)
-- 또는 "다시 분석" 요청 시에만 재생성한다. 사실(프로필)은 싸게 재집계하므로 저장하지 않는다.
--
-- user_id unique = 사용자당 1행. 시각은 Instant → datetime(6) (BaseTimeEntity created/updated, V3 관례와 동일).
-- 신규 테이블 추가뿐이라 기존 데이터에 영향 없음(additive).

create table reading_personality (
    id              bigint        not null auto_increment,
    user_id         bigint        not null,
    narrative       varchar(4000) not null,
    tags            varchar(500),
    input_signature varchar(64)   not null,
    generated_at    datetime(6)   not null,
    created_at      datetime(6)   not null,
    updated_at      datetime(6)   not null,
    primary key (id),
    constraint uk_reading_personality_user unique (user_id),
    constraint fk_reading_personality_user foreign key (user_id) references users (id)
);
