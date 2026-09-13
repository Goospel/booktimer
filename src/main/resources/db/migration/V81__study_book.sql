-- V81 — 공부 서재.
--
-- 독서 책장(book)과 **다른 테이블**인 것이 이 기능의 요구 그 자체다: 서재 탭이 모드에 따라 공부 책만/독서
-- 책만 보여야 하는데, 같은 테이블에 구분 컬럼을 두면 그 격리를 기존 쿼리 전부(책방 공개 책·홈 피드·추천·
-- 책BTI·뉴스 매칭·popularity·완독 축하 배치)가 필터로 지켜야 하고 하나만 빠져도 조용히 샌다. 테이블을
-- 가르면 독서 쿼리가 이 테이블을 아예 모르므로 섞일 경로가 구조적으로 없다(V78~V80과 같은 원칙).
--
-- 컬럼 타입·FK 관례는 V1의 book DDL 그대로다(H2·MySQL 양쪽에서 같게 도는 소문자 표기).
-- 공부 책의 분류는 상태(읽는중/완독)가 아니라 **회독 수**라 status·visibility가 없고, 피드 스탬프·제휴
-- 클릭 카운터·category/pubDate(책BTI 입력)도 소비처가 없어 만들지 않는다.
-- purchase_link는 남긴다 — 검색 응답에 함께 실려 오고 수험서는 구매 전환이 실제로 기대되는 자리다.

create table study_book (
    id            bigint       not null auto_increment,
    user_id       bigint       not null,
    title         varchar(300) not null,
    author        varchar(200),
    isbn13        varchar(20),
    cover_url     varchar(500),
    publisher     varchar(200),
    purchase_link varchar(1000),
    read_count    int          not null default 0,
    created_at    datetime(6)  not null,
    updated_at    datetime(6)  not null,
    primary key (id),
    constraint fk_study_book_user foreign key (user_id) references users (id)
);

-- 서재 목록(user_id + created_at desc)과 isbn 멱등 조회의 진입 경로를 함께 커버한다.
create index idx_study_book_user on study_book (user_id);
