-- V29 — 책BTI 분석 히스토리: 사용자당 1행 → 최대 3행(대표 1 + 후보 2)으로 확장. (2026-06-08)
--
-- 배경: "다시 분석"을 돌려도 과거 서술이 사라지지 않고 남도록(사용자가 비교하고 대표를 직접 고르게).
-- selected=대표(공개 책방 노출 + 교체에서 보호) 컬럼을 추가하고, user_id unique 제약을 없앤다.
--
-- 이식성: user_id unique 드롭은 MySQL(DROP INDEX) vs H2(DROP CONSTRAINT)로 SQL이 갈려 위험하다(V26 주석).
-- 그래서 ALTER로 드롭하지 않고, 양 DB가 공통으로 지원하는 CREATE + INSERT-SELECT + DROP + RENAME 로
-- 테이블을 재생성한다(V26/V28이 안전하게 쓴 CREATE/DROP 패턴의 확장). 기존 1행은 현재 대표이므로
-- selected=true 로 이관한다. id는 다시 부여한다(reading_personality.id를 FK로 참조하는 자식 테이블이 없어
-- id 연속성이 불필요 → 명시 복사에 따른 식별자 시퀀스 보정 이슈를 애초에 피한다).

create table reading_personality_v2 (
    id              bigint        not null auto_increment,
    user_id         bigint        not null,
    narrative       varchar(4000) not null,
    tags            varchar(500),
    input_signature varchar(64)   not null,
    generated_at    datetime(6)   not null,
    selected        boolean       not null default false,
    created_at      datetime(6)   not null,
    updated_at      datetime(6)   not null,
    primary key (id),
    constraint fk_reading_personality_v2_user foreign key (user_id) references users (id)
);

-- 기존 1행/유저 = 현재 대표 → selected=true 로 이관
insert into reading_personality_v2
    (user_id, narrative, tags, input_signature, generated_at, selected, created_at, updated_at)
select user_id, narrative, tags, input_signature, generated_at, true, created_at, updated_at
from reading_personality;

drop table reading_personality;
alter table reading_personality_v2 rename to reading_personality;
