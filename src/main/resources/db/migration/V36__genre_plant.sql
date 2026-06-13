-- V36 — 독서 정원 장르 매핑 식물 카탈로그(트랙 A 수집 확장). (2026-06-13)
--
-- 설계: 시간축 plant(V35, 누적 목표 달성일로 해금)와 별개의 수집축. 어떤 '장르'의 책을 완독하면 그 장르
-- 식물을 보유로 유도한다(부채 모델 N-058 — 보유 이력 저장 안 함). 두 축은 해금 의미가 근본적으로 달라
-- (날짜 누적 vs 장르 완독) 테이블을 분리한다 — 시간축 plant/V35는 무변경(회귀 0).
--
-- genre_label = ReadingProfileAggregator.primaryGenre 출력(알라딘 카테고리 대분류, 2번째 세그먼트)과
-- '정확히 일치'해야 그 식물이 보유로 잡힌다. genre_label이 null인 행은 폴백 식물('이름 모를 들꽃')로,
-- 시드 어느 장르에도 안 맞는 장르를 완독했을 때 "읽었는데 0개"인 허무를 막는다.
--
-- 이모지: 시간축 14종(🌱🌿☘️🪴🌷🌼🌻🌸🌹🌵🎍🍁🌲🌳)과 겹치지 않는 풀(꽃·곡식·과일·버섯). utf8mb4
-- (V35 선례 — 기존 책 제목이 이미 이모지/한글을 저장 중이라 charset 무문제). H2(테스트)·MySQL(운영) 공통 SQL.

create table genre_plant (
    id            bigint       not null auto_increment,
    code          varchar(50)  not null,
    genre_label   varchar(100),
    name          varchar(100) not null,
    emoji         varchar(20)  not null,
    display_order int          not null,
    primary key (id),
    constraint uk_genre_plant_code unique (code)
);

-- 시드 12종(알라딘 흔한 대분류) + 폴백 1종(genre_label = null, 진열은 끝).
insert into genre_plant (code, genre_label, name, emoji, display_order) values
    ('novel',      '소설/시/희곡',  '소설나무',     '🪷', 1),
    ('econ',       '경제경영',      '경제 야자수',  '🌴', 2),
    ('self_help',  '자기계발',      '성장 클로버',  '🍀', 3),
    ('humanities', '인문학',        '교양 벼',      '🌾', 4),
    ('history',    '역사',          '세월의 밤',    '🌰', 5),
    ('science',    '과학',          '탐구 버섯',    '🍄', 6),
    ('social',     '사회과학',      '사회 무궁화',  '🌺', 7),
    ('essay',      '에세이',        '일상 히아신스','🪻', 8),
    ('art',        '예술/대중문화', '예술 꽃장식',  '🏵️', 9),
    ('computer',   '컴퓨터/모바일', '데이터 포도',  '🍇', 10),
    ('religion',   '종교/역학',     '봉헌 꽃다발',  '💐', 11),
    ('comic',      '만화',          '발랄 딸기',    '🍓', 12),
    ('wildflower', null,            '이름 모를 들꽃','🍃', 99);
