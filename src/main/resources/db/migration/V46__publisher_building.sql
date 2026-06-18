-- 출판사 건물 카탈로그 (Phase B2 완성 — 정적 시드, 특정 출판사 N권 완독으로 해금)
-- matchName: Book.publisher와 contains+normalize(공백 제거) 매칭 기준 출판사명
--            모호·짧은 matchName 회피 — 부분문자열 충돌 방지(큐레이션 책임)
-- threshold_count: 해금에 필요한 해당 출판사 완독 권수
-- sprite_id: 저폴리 스프라이트 후속 아트 패스까지 null → 이모지 폴백
create table building (
    id              bigint       not null auto_increment,
    code            varchar(50)  not null,
    match_name      varchar(200) not null,
    name            varchar(100) not null,
    emoji           varchar(20)  not null,
    threshold_count int          not null,
    display_order   int          not null,
    sprite_id       varchar(50),
    primary key (id),
    constraint uk_building_code unique (code)
);

-- 대표 한국 출판사 시드 (임계: 3·5·10·15·20권)
-- matchName은 알라딘 Book.publisher에 실제 등장하는 값 기준으로 큐레이션
insert into building (code, match_name, name, emoji, threshold_count, display_order, sprite_id) values
    ('minumsa',    '민음사',    '민음사',    '🏛️', 3,  1, null),
    ('munhak',     '문학동네',  '문학동네',  '📚', 5,  2, null),
    ('changbi',    '창비',      '창비',      '🏡', 5,  3, null),
    ('gimyoungsa', '김영사',    '김영사',    '🏢', 10, 4, null),
    ('woongjin',   '웅진',      '웅진',      '🏤', 10, 5, null),
    ('wisdomhouse','위즈덤하우스','위즈덤하우스','🏬', 15, 6, null),
    ('hangilsa',   '한길사',    '한길사',    '🏗️', 15, 7, null),
    ('eulyoo',     '을유문화사','을유문화사','🏰', 20, 8, null),
    ('sigongsa',   '시공사',    '시공사',    '🏯', 20, 9, null),
    ('yolimwon',   '열린책들',  '열린책들',  '📖', 20, 10, null);

-- 출판사 다양성 식물 제거 (건물축으로 흡수 — 다양성축은 작가만 남음)
delete from diversity_plant where kind = 'PUBLISHER';
