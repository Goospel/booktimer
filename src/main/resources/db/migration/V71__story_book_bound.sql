-- V71 — 여백 책 귀속 전환: 열람 기록 은퇴 + book_id 필수화. (2026-08-16 재설계)
--
-- 여백이 「사람에게 딸린 인스타식 스토리」에서 「책에 딸린 자리」가 됐다. 그래서:
--
-- 1) story_view 드롭 — 열람 기록의 용도는 ① 미열람 링 강조 ② 「본 사람」 목록뿐이었고, 둘 다
--    스트립·전체화면 뷰어와 함께 폐기됐다. 카드 목록 UI에 열람 개념이 남을 자리가 없다.
--    코드만 떼고 테이블을 남기는 안은 기각 — 죽은 테이블은 다음 마이그레이션·백업·탈퇴 정리 코드의
--    인지 비용이 되고, 열람 '행동' 기록은 신고 대응 원문(story.text)과 달리 보존 가치가 없다(V70 전례).
--
-- 2) book_id IS NULL 행 삭제 — 새 UI의 도달 경로는 「책방 격자 → 책 → 그 책의 글」뿐이라, 책 없는 글은
--    표시 표면이 0인 유령 행이 된다. 백필은 불가능하다(어떤 책의 글인지 알 방법이 없다).
--
-- 3) book_id NOT NULL 승격 — "여백은 책에 귀속"을 앱 코드 한 줄이 아니라 DB 제약이 지키게 한다.
--
-- 순서 주의: story_view가 story를 FK 참조하므로 먼저 드롭한다(그래야 2)의 행 삭제에 자식 정리가 불필요).
-- ix_story_user_created는 유지. book_id 조회는 fk_story_book이 만든 인덱스로 충분하다(책당 글 수 소량).

DROP TABLE IF EXISTS story_view;

DELETE FROM story WHERE book_id IS NULL;

ALTER TABLE story MODIFY book_id BIGINT NOT NULL;
