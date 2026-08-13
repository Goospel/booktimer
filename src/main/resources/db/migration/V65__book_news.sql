-- V65 — 완독한 책의 관련 뉴스 캐시(네이버 뉴스 검색 API, 새벽 배치가 채운다).
--
-- FK가 없다: 이 행의 스코프는 책 카탈로그(isbn13)지 특정 사용자의 book 행이 아니다. 같은 책을 여러 명이
-- 완독해도 기사는 한 벌이면 되고(외부 호출·저장 절약), book으로의 FK를 두지 않으니 책 삭제 경로에
-- FK 정리 부담도 생기지 않는다(T-023 클래스 원천 회피). 노출은 조회 시점에 "내 완독 책의 isbn"과 조인해 거른다.
--
-- uk_book_news_isbn_link — 재수집 중복을 DB에서 막는다. 갱신은 isbn 단위 delete-then-insert라 무한 증식이 없다.
-- link를 varchar(500)으로 둔 이유: 유니크 키에 들어가므로 MySQL InnoDB 인덱스 상한(3072바이트, utf8mb4)
-- 안에 들어야 한다((20+500)*4 = 2080). 뉴스 링크는 이보다 훨씬 짧다.
-- idx_book_news_isbn13 — 홈 조회가 isbn 집합으로 조인하므로 조회 키에 인덱스를 둔다.
--
-- updated_at이 없다: 이 행은 수정되지 않고 통째로 교체된다(created_at만 의미가 있다).
-- datetime(6)은 MySQL·H2 동일 동작(V31·V56·V61·V62 관례).

create table book_news (
    id           bigint        not null auto_increment,
    isbn13       varchar(20)   not null,
    title        varchar(300)  not null,
    link         varchar(500)  not null,
    published_at datetime(6)   null,
    created_at   datetime(6)   not null,
    primary key (id),
    constraint uk_book_news_isbn_link unique (isbn13, link)
);

create index idx_book_news_isbn13 on book_news (isbn13);
