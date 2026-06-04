-- V12 — book.isbn13 인덱스.
--
-- 팔로우 스코프 인기 카운트(BookRepository.followScopePopularity: `b.isbn13 in :isbns`)와
-- 드릴다운(followScopeReaders: `b.isbn13 = :isbn`)이 isbn13으로 필터한다. isbn13은 FK가 아니라
-- InnoDB 자동 인덱스 대상이 아니어서, 책 테이블이 커지면 이 조회가 풀스캔이 된다.
-- 단일 컬럼 인덱스로 충분(isbn13이 선택도 높은 필터 컬럼). visibility/status는 group by·distinct
-- 단계라 복합으로 묶어도 이득이 제한적이라 단순하게 둔다. isbn13 nullable이지만 인덱스는 무해
-- (null 책은 isbn 기반 조회에 어차피 매칭 안 됨).
create index ix_book_isbn13 on book (isbn13);
