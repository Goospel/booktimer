-- 스토리 열람 기록 — 미열람 링(기기 무관) + 작성자의 열람자 목록 근거 (sns-design §13).
create table story_view (
    id          bigint      not null auto_increment,
    story_id    bigint      not null,
    viewer_id   bigint      not null,
    created_at  datetime(6) not null,
    updated_at  datetime(6) not null,
    primary key (id),
    constraint uk_story_view unique (story_id, viewer_id),   -- 열람 기록 멱등
    constraint fk_story_view_story  foreign key (story_id)  references story (id),
    constraint fk_story_view_viewer foreign key (viewer_id) references users (id)
);
create index ix_story_view_viewer on story_view (viewer_id);
