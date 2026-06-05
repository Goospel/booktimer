-- V17 — 작가 격언(Quote) 테이블 + 초기 큐레이션 seed.
--
-- 대시보드 인사말 자리에 랜덤 노출하는 격언을 정적 JSON에서 DB로 옮긴다 — 운영자가 admin에서
-- 추가/삭제하면 재시작 없이 반영된다. 컬럼은 Quote 엔티티 매핑과 일치해야 한다(FlywayMigrationTest는
-- ddl-auto=validate라 어긋나면 컨텍스트 로딩이 실패한다): text varchar(500), author varchar(100),
-- 시각은 BaseTimeEntity → datetime(6). 신규 테이블 추가뿐이라 기존 데이터 무영향.
--
-- seed는 기존 quotes.json의 18개 큐레이션 격언. created_at/updated_at은 JPA auditing이 아니라
-- 여기서 직접 채운다(INSERT는 엔티티 리스너를 안 타므로). current_timestamp는 MySQL·H2 공통.

create table quote (
    id         bigint        not null auto_increment,
    text       varchar(500)  not null,
    author     varchar(100)  not null,
    created_at datetime(6)   not null,
    updated_at datetime(6)   not null,
    primary key (id)
);

insert into quote (text, author, created_at, updated_at) values
    ('오늘 읽을 수 있는 책을 내일로 미루지 마라.', '홀브룩 잭슨', current_timestamp, current_timestamp),
    ('책은 한 권 한 권이 하나의 세계다.', '윌리엄 워즈워스', current_timestamp, current_timestamp),
    ('독서는 정신에 있어 운동이 신체에 하는 것과 같다.', '리처드 스틸', current_timestamp, current_timestamp),
    ('좋은 책을 읽는 것은 지난 몇 세기의 가장 훌륭한 사람들과 대화하는 것이다.', '르네 데카르트', current_timestamp, current_timestamp),
    ('오늘의 나를 만든 것은 우리 마을 도서관이었다.', '빌 게이츠', current_timestamp, current_timestamp),
    ('책이 없는 방은 영혼이 없는 육체와 같다.', '키케로', current_timestamp, current_timestamp),
    ('독서가 정신을 만든다면, 사색은 그것을 다듬는다.', '존 로크', current_timestamp, current_timestamp),
    ('나는 읽지 않은 책 위에서는 결코 잠들지 못한다.', '토머스 제퍼슨', current_timestamp, current_timestamp),
    ('어떤 책은 맛보고, 어떤 책은 삼키고, 소수의 책은 씹어서 소화해야 한다.', '프랜시스 베이컨', current_timestamp, current_timestamp),
    ('글쓰기에 대한 규칙은 단 하나뿐이다. 많이 읽고, 많이 쓰는 것.', '스티븐 킹', current_timestamp, current_timestamp),
    ('책을 읽는 데 들인 시간은 결코 낭비가 아니다.', '카슨 매컬러스', current_timestamp, current_timestamp),
    ('당신이 만나게 될 가장 좋은 친구들 중 일부는 책 속에 있다.', '헬렌 켈러', current_timestamp, current_timestamp),
    ('한 시간의 독서로 누그러지지 않는 슬픔은 없다.', '몽테스키외', current_timestamp, current_timestamp),
    ('오늘의 독자가 내일의 지도자가 된다.', '마거릿 풀러', current_timestamp, current_timestamp),
    ('책을 읽지 않는 사람은 읽지 못하는 사람보다 나을 것이 없다.', '마크 트웨인', current_timestamp, current_timestamp),
    ('위대한 책은 끝까지 읽고 나면 가장 친한 친구처럼 느껴지고, 다시 펼치면 옛 친구를 만나는 것 같다.', '윈스턴 처칠', current_timestamp, current_timestamp),
    ('독서만큼 값싸게 지속되는 즐거움은 없다.', '메리 워틀리 몬터규', current_timestamp, current_timestamp),
    ('한 권의 책을 읽음으로써 자신의 삶에서 새 시대를 본 사람이 너무도 많다.', '헨리 데이비드 소로', current_timestamp, current_timestamp);
