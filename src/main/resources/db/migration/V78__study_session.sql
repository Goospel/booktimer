-- V78 — 공부 측정 원장.
--
-- 독서(reading_session)와 **다른 테이블**인 것이 이 기능의 요구 그 자체다: 공부 시간이 잔디·부채·기록·
-- 홈피드·책 통계에 섞이면 안 되는데, 같은 테이블에 mode 컬럼을 두면 그 격리를 기존 집계 쿼리 전부가
-- `mode='READING'` 필터로 지켜야 하고 하나만 빠져도 조용히 샌다. 테이블을 가르면 독서 쿼리가 이 테이블을
-- 아예 모르므로 섞일 경로가 구조적으로 없다.
--
-- 컬럼 타입·FK 관례는 V1의 reading_session DDL 그대로다(H2·MySQL 양쪽에서 같게 도는 소문자 표기).
-- book_id·manual_entry는 없다 — 공부는 책이 없고 수동 기록은 이번 범위 밖이다.

create table study_session (
    id               bigint      not null auto_increment,
    user_id          bigint      not null,
    started_at       datetime(6) not null,
    ended_at         datetime(6),
    duration_seconds bigint      not null,
    created_at       datetime(6) not null,
    updated_at       datetime(6) not null,
    primary key (id),
    constraint fk_study_session_user foreign key (user_id) references users (id)
);

-- 당일 합산(user_id + started_at 범위)과 진행 중 세션 조회를 함께 커버한다.
create index idx_study_session_user_started on study_session (user_id, started_at);
