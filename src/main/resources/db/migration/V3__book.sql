-- V3 — 책장(Book) 테이블.
-- 사용자가 등록한 책. 검색(알라딘)에서 받은 메타데이터(저자·ISBN·표지·출판사·구매링크)를 담는다.
-- enum(status)은 @Enumerated(STRING) 의미 유지하며 MySQL·H2 공통 varchar로 둔다(V1과 동일 방침).
-- 시각은 Instant → datetime(6). 책별 누적 시간(세션 연결)은 다음 증분의 확장점.

create table book (
    id            bigint        not null auto_increment,
    user_id       bigint        not null,
    title         varchar(300)  not null,
    author        varchar(200),
    isbn13        varchar(20),
    cover_url     varchar(500),
    publisher     varchar(200),
    purchase_link varchar(1000),
    status        varchar(20)   not null,
    created_at    datetime(6)   not null,
    updated_at    datetime(6)   not null,
    primary key (id),
    constraint fk_book_user foreign key (user_id) references users (id)
);

create index ix_book_user on book (user_id);
