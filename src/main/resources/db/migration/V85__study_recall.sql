-- V85 — 백지복습 원장 + AI 하루 상한 카운터.
--
-- study_recall: 행 1개 = 「그날 백지에 쏟아낸 글 한 장」. UNIQUE(user_id, recall_date)가 「하루 한 장」을
-- 강제한다 — 상한이 아니라 달력 대응이다(칸 하나에 글 하나). 같은 날 다시 저장하면 덮어쓴다.
--
-- summary/holes_json/questions_json은 분석 산출물이고 analyzed_at IS NULL이 「저장만 함」이다. 셋을 JSON
-- 문자열로 두는 이유: 항목 안에 줄바꿈이 들어올 수 있어 개행 구분 텍스트가 안전하지 않고, 별도 테이블은
-- 「분석 한 번 = 자식 N행 교체」라는 절차를 새로 만든다(읽는 쪽은 늘 통째로 읽는다 — 쪼갤 값이 없다).
--
-- book_id nullable + 앱 코드가 FK를 푸는 규칙은 V82·V83 그대로다(StudyBookService.delete가
-- StudyRecallRepository.unlinkBook 호출). subject는 제목 스냅샷이라 책이 사라져도 「무슨 과목이었나」가 남는다.

create table study_recall (
    id             bigint        not null auto_increment,
    user_id        bigint        not null,
    recall_date    date          not null,
    book_id        bigint,
    subject        varchar(300),
    scope_text     varchar(4000),
    body           text          not null,
    source         varchar(10)   not null,
    summary        text,
    holes_json     text,
    questions_json text,
    model          varchar(60),
    analyzed_at    datetime(6),
    created_at     datetime(6)   not null,
    updated_at     datetime(6)   not null,
    primary key (id),
    constraint fk_study_recall_user foreign key (user_id) references users (id),
    constraint fk_study_recall_book foreign key (book_id) references study_book (id),
    constraint uq_study_recall unique (user_id, recall_date)
);

-- AI 하루 상한 — 행 1개 = 「이 사람이 이 날 이 종류를 몇 번 썼나」.
--
-- 왜 카운터 행인가: 상한 검사가 「COUNT 후 INSERT」면 동시 두 요청이 둘 다 통과한다(TOCTOU). 여기서는
-- UPDATE … SET used = used + 1 WHERE used < :max 한 문장이라 DB가 행을 잠그고, 통과 여부가 갱신 행 수로
-- 그대로 나온다. 행이 없을 때의 INSERT 경합은 아래 UNIQUE가 잡는다(진 쪽은 다시 UPDATE로 간다).
--
-- 외부 호출이 실패하면 used를 되돌린다(환불) — 장애로 사용자가 오늘 몫을 잃지 않게. 그래서 「호출 로그」가
-- 아니라 「가감 가능한 카운터」다.
create table study_ai_usage (
    id         bigint      not null auto_increment,
    user_id    bigint      not null,
    usage_date date        not null,
    kind       varchar(20) not null,
    used       int         not null default 0,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id),
    constraint fk_study_ai_usage_user foreign key (user_id) references users (id),
    constraint uq_study_ai_usage unique (user_id, usage_date, kind)
);
