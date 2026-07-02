-- 독서 스토리 — 팔로워에게 24시간만 보이는 텍스트 카드 (sns-design §13).
-- 만료는 표시 필터(created_at 기준 24h)라 만료 컬럼·잡이 없다. 데이터는 보존(신고 대응 원문 근거).
create table story (
    id          bigint       not null auto_increment,
    user_id     bigint       not null,             -- 작성자
    book_id     bigint       null,                 -- 선택 첨부(본인 소유+PUBLIC만, §13.2)
    text        varchar(500) not null,
    bg_code     varchar(20)  null,                 -- 배경 팔레트 닫힌 코드(자유 hex 금지 — 스타일 주입 차단)
    created_at  datetime(6)  not null,
    updated_at  datetime(6)  not null,
    primary key (id),
    constraint fk_story_user foreign key (user_id) references users (id),
    constraint fk_story_book foreign key (book_id) references book (id)
);
create index ix_story_user_created on story (user_id, created_at);
