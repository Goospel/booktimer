-- V109 — 책생글 B2b: 블로그 신고 · 운영자 가리기(임시조치 · 저작권 중단 · 정책 · 삭제) · 처리 기록 · 글쓰기 막기(계획 2026-11-16-blog-b2 §5.3).
-- 가림 = hidden_at(가린 시각 · null = 보임) · hide_kind(TEMP 임시조치 30일 — 약관 ⑥ / COPYRIGHT 저작권 중단 요구 — ⑦ · 만료 없음
--        / POLICY 정책 위반 — ⑧ / REMOVED 운영자 삭제) · hide_note(게시자에게 보이는 운영자 글 — COPYRIGHT면 중단 요청서 사본, 처리방침 6절).
--        hidden_at과 hide_kind는 늘 함께 쓴다 — hidden_at만 있고 hide_kind가 null인 행은 POLICY로 읽는다(Moderation.kindOf).
-- blog_report — (신고자, 대상) 하나 · 대상은 논리 참조(POST · COMMENT — 대상이 지워져도 신고자 기록으로 남는다, 처리방침 5절)
--        · target_user_id = 신고할 때의 대상 글쓴이(FK 없음 — 그 사람이 탈퇴하면 purge가 null로 비운다).
-- blog_moderation_log — 가리기 · 다시 보이기 · 삭제 · 신고 처리 · 글쓰기 막기마다 한 줄(쓰기만 · users FK 없음 · purge가 건드리지 않는다
--        — 처리방침 5절 「그 처리 기록은 처리가 끝난 뒤 3년」 · 3년 지난 줄은 운영 정리 U-155).
-- 기다릴 수 있는 바꾸기(메타데이터 잠금)를 맨 앞에 — 거기서 멈춰도 만든 표가 없다(V106 · V108과 같은 까닭).
alter table users add column blog_restricted_at datetime(6) null;
alter table blog_post add column hide_kind varchar(10) null;
alter table blog_post add column hide_note varchar(2000) null;
alter table blog_comment add column hidden_at datetime(6) null;
alter table blog_comment add column hide_kind varchar(10) null;
alter table blog_comment add column hide_note varchar(2000) null;
alter table quote_box add column hidden_at datetime(6) null;
alter table quote_box add column hide_kind varchar(10) null;
alter table quote_box add column hide_note varchar(2000) null;
create table blog_report (
    id             bigint       not null auto_increment,
    reporter_id    bigint       not null,
    target_type    varchar(10)  not null,
    target_id      bigint       not null,
    target_user_id bigint       null,
    reason         varchar(20)  not null,
    detail         varchar(500) null,
    status         varchar(12)  not null,
    result         varchar(16)  null,
    resolved_at    datetime(6)  null,
    created_at     datetime(6)  not null,
    updated_at     datetime(6)  not null,
    primary key (id),
    constraint uq_blog_report_target unique (reporter_id, target_type, target_id),
    constraint fk_blog_report_reporter foreign key (reporter_id) references users (id)
);
create index idx_blog_report_target_user on blog_report (target_user_id);
-- 열린 신고 수(신고마다 · 관리자 홈 · 매일 09:00)와 신고함의 오래된 열린 신고 · 같은 대상의 열린 신고(resolve — 글 행 잠금을 쥔 채 돈다).
create index idx_blog_report_status on blog_report (status, created_at);
create index idx_blog_report_target on blog_report (target_type, target_id, status);
-- 관리자 신고함 「이 글을 인용한 박스」(행마다 한 번 — V107엔 source_post_id 색인이 없다).
create index idx_quote_box_source_post on quote_box (source_post_id);
create table blog_moderation_log (
    id         bigint        not null auto_increment,
    unit       varchar(8)    not null,
    unit_id    bigint        not null,
    action     varchar(12)   not null,
    report_id  bigint        null,
    note       varchar(2000) null,
    created_at datetime(6)   not null,
    updated_at datetime(6)   not null,
    primary key (id)
);
