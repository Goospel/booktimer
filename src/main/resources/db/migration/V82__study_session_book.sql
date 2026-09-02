-- V82 — 공부 측정을 어떤 책으로 쟀는지 (study_session.book_id → study_book).
--
-- nullable인 것이 요구다: 책 없이 재는 것이 정당한 사용이고(시작을 책 선택으로 가로막지 않는다),
-- 책을 서재에서 지워도 그날 공부한 시간은 남아야 한다. 후자는 **앱 코드**가 푼다 —
-- StudyBookService.delete가 StudySessionRepository.unlinkBook으로 book_id를 null로 만든 뒤 지운다.
-- DDL에 on delete set null을 걸지 않는 이유: 메인 테스트 스위트는 Hibernate가 엔티티에서 스키마를
-- 만들어 FK 옵션이 거기 존재하지 않는다(그러면 삭제 테스트가 두 환경에서 다르게 돌고, 맞추려면
-- @OnDelete까지 달아 규칙의 출처가 둘이 된다). 독서 book 삭제(V4 + BookService.delete)와 같은 선례다.
--
-- 컬럼 타입·FK·인덱스 관례는 V4의 reading_session_book DDL 그대로다.

alter table study_session add column book_id bigint;
alter table study_session add constraint fk_study_session_book
    foreign key (book_id) references study_book (id);
create index ix_study_session_book on study_session (book_id);
