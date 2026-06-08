-- V23 — 사용자 피드백/문의(Feedback) 테이블.
--
-- 일반 사용자(author_id)가 개발자에게 버그·제안·문의를 보낸다.
-- type(BUG/SUGGESTION/ETC)·status(SUBMITTED/READ/RESOLVED)는 enum을 STRING으로 저장(varchar 20, report 류 관례).
-- status는 개발자가 읽음/처리완료를 표시하고, 그 표시는 작성자 본인만 조회한다(노출 스코핑은 앱 레벨).
-- 시각은 BaseTimeEntity created_at/updated_at → datetime(6). 신규 테이블 추가뿐이라 기존 데이터 무영향.

create table feedback (
    id         bigint        not null auto_increment,
    author_id  bigint        not null,
    type       varchar(20)   not null,
    title      varchar(150)  not null,
    content    varchar(2000) not null,
    status     varchar(20)   not null,
    created_at datetime(6)   not null,
    updated_at datetime(6)   not null,
    primary key (id),
    constraint fk_feedback_author foreign key (author_id) references users (id)
);

create index ix_feedback_author on feedback (author_id);
