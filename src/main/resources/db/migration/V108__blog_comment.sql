-- V108 — 책생글 B2a: 문장 댓글 · 새 댓글 확인 시각(계획 2026-11-16-blog-b2 §5.1).
-- blog_comment — 글의 문장 주소(sid)에 매단 평평한 댓글. sid는 논리 참조(합침 사슬로 묶어 보인다 — §6.3).
-- users FK가 생긴다 — AccountService.purge와 FlywayMigrationTest 목록에 함께 더한다(v1 REQ-14).
-- 기다릴 수 있는 blog_post 바꾸기(메타데이터 잠금)를 맨 앞에 — 거기서 멈춰도 만든 표가 없다(V106과 같은 까닭).
alter table blog_post add column author_seen_comments_at datetime(6) null;
create table blog_comment (
    id         bigint        not null auto_increment,
    post_id    bigint        not null,
    sid        char(8)       character set ascii collate ascii_bin not null,
    user_id    bigint        not null,
    body       varchar(1000) not null,
    created_at datetime(6)   not null,
    updated_at datetime(6)   not null,
    primary key (id),
    constraint fk_blog_comment_post foreign key (post_id) references blog_post (id),
    constraint fk_blog_comment_user foreign key (user_id) references users (id)
);
create index idx_blog_comment_post_sid on blog_comment (post_id, sid);
