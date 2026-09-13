-- V89 — 공부 필기 원장.
--
-- study_note: 행 1개 = 공부하며 책을 보고 그때그때 적은 필기 한 장. 하루 한 장 제약이 없는 것이
-- study_recall과 다르다 — 필기는 날짜가 아니라 **책**이 정리 축이고(자유 다장), 달력 하루 패널은
-- 진입점일 뿐이다. 그래서 recall_date에 해당하는 컬럼 자체가 없다.
--
-- ⚠️ book_id가 **NOT NULL**인 것이 study_recall·study_session·study_plan_item과 갈리는 자리다.
-- 저 셋은 책 삭제 시 book_id = null로 풀지만(unlinkBook), 필기는 그럴 수 없다: 책이 정리 축이자
-- 채점 기준(백지복습 분석의 정답지)의 연결고리라, 풀린 필기는 화면의 어느 목록에도 안 뜨고 영영
-- 채점에 안 들어가는 **조용한 누락**이 된다. 그래서 StudyBookService.delete는 필기가 있으면
-- unlink가 아니라 409로 막는다(필기를 먼저 지워야 책을 뺄 수 있다).
--
-- revision은 자동저장의 낙관적 판 번호다. JPA @Version이 아니라 손으로 비교·증가시킨다 — 잡아야 하는
-- 것은 「클라가 본 판」과의 어긋남(다른 탭이 먼저 저장했다)이지 같은 트랜잭션 안의 경합이 아니다.
--
-- body는 V85와 같은 text 컬럼 규율이다(엔티티는 @Column(length=8000) — @Lob은 H2가 CHARACTER
-- VARYING으로 보고해 ddl-auto=validate가 깨진다. 엔티티의 length는 입력 검증 상한이고 운영 MySQL의
-- 실타입은 TEXT다 — 둘은 원래 다르며 이 차이를 잡아 주는 테스트는 없다, V85 주석 ②).
create table study_note (
    id         bigint       not null auto_increment,
    user_id    bigint       not null,
    book_id    bigint       not null,
    title      varchar(200),
    body       text         not null,
    revision   int          not null default 0,
    created_at datetime(6)  not null,
    updated_at datetime(6)  not null,
    primary key (id),
    constraint fk_study_note_user foreign key (user_id) references users (id),
    constraint fk_study_note_book foreign key (book_id) references study_book (id)
);

-- 목록 조회 키 그대로 — (내, 이 책의) 필기를 최근 고친 순으로 편다.
create index idx_study_note_user_book_updated on study_note (user_id, book_id, updated_at);
