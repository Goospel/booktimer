-- V4 — 측정 세션에 책 연결(선택). 책별 누적 시간 집계의 토대.
-- nullable: 기존 세션과 "책 미지정" 측정을 허용한다. book(V3) 이후에 FK를 건다.

alter table reading_session add column book_id bigint;
alter table reading_session add constraint fk_reading_session_book
    foreign key (book_id) references book (id);
create index ix_reading_session_book on reading_session (book_id);
