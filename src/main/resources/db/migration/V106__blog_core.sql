-- V106 — 책생글 B1a: 블로그 글 원장 = 초안 글자 + 올린 판(문장 표 · 배치) · 블로그 첫 설정
-- (설계 2026-10-29-app-blog-margin §5.2 · §6.1 B1 · 계획 2026-11-11-b1-blog-core §5.1).
-- 1) blog_post — 초안(draft_*)은 자동저장이 쓰고, title · layout_json은 올릴 때만 바뀐다(J11 · J89). hidden_at = 운영자
--    가리기(B2가 쓴다 — B1은 읽기에서 뺀다). 쓰지 않는 열(새 댓글 확인 시각 · AI 원가)은 쓰는 PR이 더한다.
-- 2) blog_sentence — 문장 주소 sid(8자 base62 · 전역 유일) · 판 · 합침(merged_into) · 나뉨(split_from) · 묘비(deleted_at).
--    산 문장 = deleted_at NULL. 순서의 단일 출처는 blog_post.layout_json이다.
-- 3) blog_sentence_rev — 판마다 한 행(판 1부터 덧붙이기만 — 「예전 문장 보기」 = 지금 판보다 작은 판).
-- 4) users — 블로그에 보일 이름 확인 · 커뮤니티 규칙 동의 시각(J23).
-- users FK는 blog_post 하나다 — AccountService.purge와 FlywayMigrationTest 목록에 함께 더한다(v1 REQ-14).
-- MySQL DDL은 문장마다 커밋된다 — 기다릴 수 있는 users 바꾸기(메타데이터 잠금)를 맨 앞에 두어, 거기서 멈춰도 만든 표가 없게 한다.
-- sid 열은 ascii_bin — 운영 기본 콜레이션(utf8mb4_0900_ai_ci)은 대소문자를 가리지 않고 sid는 대소문자를 가르는 base62다(H2와 같게).
alter table users add column blog_name_confirmed_at datetime(6) null;
alter table users add column ugc_terms_at datetime(6) null;
create table blog_post (
    id             bigint       not null auto_increment,
    user_id        bigint       not null,
    title          varchar(100) null,
    draft_title    varchar(100) null,
    draft_body     text         not null,
    draft_revision int          not null default 0,
    layout_json    text         null,
    published_at   datetime(6)  null,
    republished_at datetime(6)  null,
    hidden_at      datetime(6)  null,
    created_at     datetime(6)  not null,
    updated_at     datetime(6)  not null,
    primary key (id),
    constraint fk_blog_post_user foreign key (user_id) references users (id)
);
create index idx_blog_post_published on blog_post (published_at);
create table blog_sentence (
    id          bigint        not null auto_increment,
    sid         char(8)       character set ascii collate ascii_bin not null,
    post_id     bigint        not null,
    text        varchar(1000) not null,
    rev         int           not null default 1,
    merged_into char(8)       character set ascii collate ascii_bin null,
    split_from  char(8)       character set ascii collate ascii_bin null,
    deleted_at  datetime(6)   null,
    created_at  datetime(6)   not null,
    updated_at  datetime(6)   not null,
    primary key (id),
    constraint uq_blog_sentence_sid unique (sid),
    constraint fk_blog_sentence_post foreign key (post_id) references blog_post (id)
);
create table blog_sentence_rev (
    id          bigint        not null auto_increment,
    sentence_id bigint        not null,
    rev         int           not null,
    text        varchar(1000) not null,
    created_at  datetime(6)   not null,
    updated_at  datetime(6)   not null,
    primary key (id),
    constraint uq_blog_sentence_rev unique (sentence_id, rev),
    constraint fk_blog_sentence_rev_sentence foreign key (sentence_id) references blog_sentence (id)
);
