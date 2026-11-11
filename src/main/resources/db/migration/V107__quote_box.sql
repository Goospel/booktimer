-- V107 — 책생글 B1b: 인용 박스 하나를 글 · 쪽지가 함께 쓴다(설계 §5.3 · 계획 2026-11-11-b1-blog-core §5.2).
-- owner = POST(blog_post.id) · NOTE(story.id) — 다형이라 FK가 없다. 주인을 지우는 길(글 지우기 · 쪽지 지우기 · 탈퇴)이
-- QuoteBoxRepository의 지우기를 반드시 부른다(안 부르면 고아 박스가 조용히 남는다 — REQ-214 · 243 · 204가 잰다).
-- 원문 참조(source_*)는 논리 참조 — 원문이 지워져도 박스 글은 남는다(D5).
-- BLOG: members = 인용 · 반영 때 구간 sid(쉼표) · seen_sentence_id = 그때 원 글의 가장 큰 blog_sentence.id(그 뒤의 나뉜
--       조각만 구간에 든다 — J72) · source_user_id = 원 글쓴이(이름은 읽을 때 지금 이름 · 탈퇴하면 NULL → 「탈퇴한 사용자」)
--       · attribution = 원 글 제목 스냅샷(원 글이 지워지면 이것).
-- BOOK: attribution = 쪽지의 quote_source(『제목』 · 저자 — J84) · source_isbn13 = 쪽지 책 ISBN(문장 걸기 기본 책 — J75).
create table quote_box (
    id               bigint        not null auto_increment,
    owner_type       varchar(10)   not null,
    owner_id         bigint        not null,
    label            int           not null,
    kind             varchar(10)   not null,
    source_post_id   bigint        null,
    source_user_id   bigint        null,
    members          text          null,
    seen_sentence_id bigint        null,
    source_story_id  bigint        null,
    source_isbn13    varchar(20)   null,
    text             varchar(1000) not null,
    attribution      varchar(510)  null,
    quoted_at        datetime(6)   not null,
    created_at       datetime(6)   not null,
    updated_at       datetime(6)   not null,
    primary key (id),
    constraint uq_quote_box_owner_label unique (owner_type, owner_id, label)
);
