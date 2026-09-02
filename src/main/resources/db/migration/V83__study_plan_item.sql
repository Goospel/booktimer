-- V83 — 공부 일정 원장. 행 1개 = 「그날 이 과목으로 이걸 한다」 한 줄.
--
-- study_daily_check(V80)와 **다른 축**이다: 저쪽은 「그날 지켰나」라는 사후 판정 한 칸이고, 이쪽은
-- 「그날 뭘 할 건가」라는 사전 계획이다. 한 테이블에 합치면 「계획 없이 지킴만」·「계획만 여럿」이
-- 표현되지 않는다(하루에 일정은 여러 개, 판정은 하나 — 카디널리티부터 다르다). 그래서 UNIQUE도 없다.
--
-- book_id가 nullable인 것이 요구다 — 서재에 없는 과목(자유 제목)으로도 일정을 짜는 것이 정당한 사용이고,
-- 책을 서재에서 지워도 그 일정 기록은 남아야 한다. 후자는 **앱 코드**가 푼다: StudyBookService.delete가
-- StudyPlanItemRepository.unlinkBook으로 book_id를 null로 만든 뒤 책을 지운다. DDL에 on delete set null을
-- 걸지 않는 이유는 V82 주석 그대로다(메인 테스트는 Hibernate가 스키마를 만들어 FK 옵션이 거기 없다).
--
-- subject는 **제목 스냅샷**이다 — book_id가 풀려도 「무슨 과목이었나」가 남아야 화면이 빈 줄이 되지 않는다.
-- 컬럼 타입·FK 표기 관례는 V80~V82의 study_* DDL 그대로다(H2·MySQL 양쪽에서 같게 도는 소문자 표기).

create table study_plan_item (
    id         bigint       not null auto_increment,
    user_id    bigint       not null,
    plan_date  date         not null,
    book_id    bigint,
    subject    varchar(300) not null,
    task       varchar(500) not null,
    created_at datetime(6)  not null,
    updated_at datetime(6)  not null,
    primary key (id),
    constraint fk_study_plan_item_user foreign key (user_id) references users (id),
    constraint fk_study_plan_item_book foreign key (book_id) references study_book (id)
);

-- 달력의 유일한 조회 경로(내 것 + 날짜 구간)를 그대로 덮는다.
create index idx_study_plan_item_user_date on study_plan_item (user_id, plan_date);
