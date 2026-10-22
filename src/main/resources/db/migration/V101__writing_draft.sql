-- V101 — 앱 「책생글」 글 원장 + AI 전송 동의 시각(설계 v1 §5.1.1 PR-B).
--
-- writing_draft: 행 1개 = 사용자가 앱에서 쓰는 글 한 편. 본문은 사용자 PUT만 바꾼다(REQ-47) — AI 결과는
-- questions_json·ai_updated_at·ai_cost_micro_usd에만 저장되고(PR-C), 엔티티가 @DynamicUpdate라 AI 저장이 본문 열을 쓰지 않는다.
-- body·questions_json은 text다 — 엔티티는 @Column(length=…)로 선언한다(@Lob이면 H2가 validate에서 깨진다 — V85·V89 주석).
-- book_id는 독서 서재(book)만 가리킨다 — 공부 서재(study_book)는 가리킬 수 없다(결정 5 · REQ-48).
-- 책을 지우면 BookService.delete가 book_id를 null로 푼다(REQ-13 — 글은 사용자 것이다). 탈퇴는 AccountService.purge가
-- 지운다(FlywayMigrationTest가 users FK 목록을 대조한다 — REQ-14). FK는 V1 제약 이름을 참조하지 않는다.
-- outline_json·review_json·ai_edits_accepted는 만들지 않는다(설계 개정 2 — 구성·다듬기는 「책 쓰기」로 미뤘다).
create table writing_draft (
    id                bigint       not null auto_increment,
    user_id           bigint       not null,
    book_id           bigint       null,
    title             varchar(100) null,
    body              text         not null,
    revision          int          not null default 0,
    questions_json    text         null,
    ai_updated_at     datetime(6)  null,
    ai_cost_micro_usd bigint       not null default 0,
    exported_at       datetime(6)  null,
    created_at        datetime(6)  not null,
    updated_at        datetime(6)  not null,
    primary key (id),
    constraint fk_writing_draft_user foreign key (user_id) references users (id),
    constraint fk_writing_draft_book foreign key (book_id) references book (id)
);
create index idx_writing_draft_user_updated on writing_draft (user_id, updated_at);

-- AI 전송 동의 시각(REQ-15) — 동의는 ai_consent_at ≥ 처리방침 6-3절 시행일일 때만 유효하다. 철회하면 null.
alter table users add column ai_consent_at datetime(6) null;
