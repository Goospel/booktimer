-- V11 — 신고(Report) 관계 테이블 (SNS 5단계, sns-design §7.5·§9).
--
-- reporter_id 가 reported_id 를 신고한다. 신고는 저장만 한다(관리자 검토 화면은 추후).
-- (reporter_id, reported_id) 유니크로 쌍당 1건 — 중복 신고 스팸을 막는다(앱 레벨 existsBy 가드와 이중).
-- 자기 신고 금지는 도메인 검증이 담당(DB CHECK는 MySQL/H2 호환 위해 생략, V9·V10 관례와 동일).
-- reason 은 enum 을 STRING 으로 저장(varchar 20, BookStatus 류 관례). detail 은 선택(nullable).
-- 시각은 BaseTimeEntity created_at/updated_at → datetime(6). 신규 테이블 추가뿐이라 기존 데이터 무영향.

create table report (
    id          bigint       not null auto_increment,
    reporter_id bigint       not null,
    reported_id bigint       not null,
    reason      varchar(20)  not null,
    detail      varchar(500),
    created_at  datetime(6)  not null,
    updated_at  datetime(6)  not null,
    primary key (id),
    constraint uk_report unique (reporter_id, reported_id),
    constraint fk_report_reporter foreign key (reporter_id) references users (id),
    constraint fk_report_reported foreign key (reported_id) references users (id)
);

create index ix_report_reporter on report (reporter_id);
create index ix_report_reported on report (reported_id);
