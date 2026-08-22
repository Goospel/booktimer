-- V77 — 여백 글의 「함께 걸기」(책축 노출 opt-in). 기본 false = 기존 글 전부 꺼짐(소급 노출 0 —
-- 지금 사용자들은 「팔로워에게 보여요」를 보고 썼으므로 약속을 깨지 않는다).
-- 노출 판정은 읽기 시점에 book.visibility(상위 AND) ∧ shared — Story.java 불변식 참조.
-- 인덱스 없음: 책축 조회는 ix_book_isbn13(V12) → fk_story_book 경유라 shared는 잔여 필터로 충분.
alter table story add column shared boolean not null default false;
