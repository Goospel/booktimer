-- V104 — 책생글 M1: 여백 = 쪽지 원장 · 비공개화 · 책 없는 쪽지 · 책 출처 스냅샷(설계 2026-10-29-app-blog-margin §5.1 · §6.1).
--
-- 1) 생각(text)을 1만 200자까지 — 옛 앱 글(제목 100 + 빈 줄 + 본문 10,000)이 늘 들어가는 값(J3). 엔티티는 @Column(length)로
--    선언한다(@Lob이면 H2가 validate에서 깨진다 — V85·V89 주석). MODIFY는 V71 선례.
-- 2) book_id NULL 허용 — 책 없는 쪽지(C9). FK는 그대로다: 책을 지우면 BookService.delete가 연결만 푼다(unlinkBook).
-- 3) 앱 글쓰기 열(판 · AI 기록 · 원가 · 내보낸 시각)을 story로 — writing_draft를 흡수한다(J2). from_draft_id는 V105의 짝 맞추기
--    임시 열이고 M2가 지운다.
-- 4) quote_source — 옮긴 문장의 『제목』 · 저자 스냅샷(J84). 책을 지워도 출처가 남는다. 백필 꼴은 Story.sourceLine과 같다.
-- 5) writing_draft.story_id — 옮긴 쪽지(GET DraftView.movedTo — 옛 JS 호환, J5). FK 없음 — M2가 표째 지운다.
-- 6) 비공개화(C3 · D1): 「함께 걸기」를 끄고 좋아요 행을 지운다(2026-10-22 실측: 남의 여백 · 좋아요 0).
ALTER TABLE story MODIFY text TEXT NOT NULL;
ALTER TABLE story MODIFY book_id BIGINT NULL;
ALTER TABLE story ADD COLUMN revision INT NOT NULL DEFAULT 0;
ALTER TABLE story ADD COLUMN ai_log TEXT NULL;
ALTER TABLE story ADD COLUMN ai_updated_at DATETIME(6) NULL;
ALTER TABLE story ADD COLUMN ai_cost_micro_usd BIGINT NOT NULL DEFAULT 0;
ALTER TABLE story ADD COLUMN exported_at DATETIME(6) NULL;
ALTER TABLE story ADD COLUMN quote_source VARCHAR(510) NULL;
ALTER TABLE story ADD COLUMN from_draft_id BIGINT NULL;
UPDATE story SET quote_source = (SELECT CONCAT('『', b.title, '』',
    CASE WHEN b.author IS NULL OR b.author = '' THEN '' ELSE CONCAT(' · ', b.author) END)
  FROM book b WHERE b.id = story.book_id) WHERE quote IS NOT NULL;
ALTER TABLE writing_draft ADD COLUMN story_id BIGINT NULL;
UPDATE story SET shared = FALSE WHERE shared = TRUE;
DELETE FROM story_like;
