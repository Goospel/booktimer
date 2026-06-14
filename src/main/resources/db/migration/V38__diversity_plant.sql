-- V38 — 독서 정원 작가·출판사 다양성 식물 카탈로그(트랙 A 3축 완성). (2026-06-14)
--
-- 설계: 시간축 plant(V35, 누적 목표 달성일)·장르축 genre_plant(V36, 장르 라벨 1:1 매칭)과 또 다른 수집축.
-- 완독(FINISHED) 책의 '서로 다른(distinct) 작가/출판사 수'가 threshold_count 이상이면 그 식물을 보유로
-- 유도한다(부채 모델 N-058 — 보유 이력 저장 안 함). 작가·출판사는 같은 카운트 메커닉이라 한 테이블에 kind로
-- 구분해 담는다 — 장르축이 별도였던 건 라벨 매칭이라 메커닉이 달라서고, 이건 시간축처럼 '누적 수 >= 임계'라 동형.
-- 작가 표기가 미정규화라 1:1 라벨 시드가 불가·불신이라 '다양성 카운트'를 택했다(설계 §2.1). distinct 정규화는
-- 1차에선 strip+빈 제외만(과다 카운트 감수 — 사용자에게 유리). 시간축 plant·장르축 genre_plant는 무변경(회귀 0).
--
-- 이모지: 시간축 14·장르 13·레시피 8과 겹치지 않는 풀(작가=과일, 출판사=채소). utf8mb4(V35 선례 — 기존 책
-- 제목이 이미 이모지/한글을 저장 중이라 charset 무문제). H2(테스트)·MySQL(운영) 공통 SQL.
--
-- 임계 곡선: 작가 7단계(1·3·5·10·15·20·30), 출판사 5단계(1·3·5·8·12 — 한 출판사가 여러 책을 내 작가보다
-- 천천히 모이므로 낮은 임계). 초반 촘촘(즉시 보상) → 후반 띄엄(장기 동기). thesis(다양한 독서로 폭 넓히기) 정합.

create table diversity_plant (
    id              bigint       not null auto_increment,
    code            varchar(50)  not null,
    kind            varchar(20)  not null,
    threshold_count int          not null,
    name            varchar(100) not null,
    emoji           varchar(20)  not null,
    display_order   int          not null,
    primary key (id),
    constraint uk_diversity_plant_code unique (code)
);

-- 작가 7종(임계 1·3·5·10·15·20·30) + 출판사 5종(임계 1·3·5·8·12). 진열: 작가 묶음 → 출판사 묶음.
-- kind는 DiversityKind enum(EnumType.STRING) — 'AUTHOR'/'PUBLISHER' 문자열로 매핑된다.
insert into diversity_plant (code, kind, threshold_count, name, emoji, display_order) values
    ('author_1',     'AUTHOR',    1,  '새내기 독자의 블루베리', '🫐', 1),
    ('author_3',     'AUTHOR',    3,  '이야기 수집가의 체리',   '🍒', 2),
    ('author_5',     'AUTHOR',    5,  '다독가의 복숭아',        '🍑', 3),
    ('author_10',    'AUTHOR',    10, '탐독가의 사과',          '🍎', 4),
    ('author_15',    'AUTHOR',    15, '장서가의 배',            '🍐', 5),
    ('author_20',    'AUTHOR',    20, '비평가의 귤',            '🍊', 6),
    ('author_30',    'AUTHOR',    30, '문학 미식가의 파인애플', '🍍', 7),
    ('publisher_1',  'PUBLISHER', 1,  '새내기 서가의 피망',     '🫑', 8),
    ('publisher_3',  'PUBLISHER', 3,  '여러 출판사의 브로콜리', '🥦', 9),
    ('publisher_5',  'PUBLISHER', 5,  '안목의 배추',            '🥬', 10),
    ('publisher_8',  'PUBLISHER', 8,  '장서가의 고구마',        '🍠', 11),
    ('publisher_12', 'PUBLISHER', 12, '출판 미식가의 가지',     '🍆', 12);
