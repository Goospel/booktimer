-- 작가 캐릭터 카탈로그 (Phase B1 — 정적 시드, 특정 작가 완독으로 해금)
-- matchName: 알라딘 원문 Book.author와 contains+normalize 매칭 기준 작가명 (괄호·역할 토큰·공백 정규화 후 비교)
-- sprite_id: 저폴리 스프라이트 후속 아트 패스까지 null → 이모지 폴백
create table author_character (
    id            bigint       not null auto_increment,
    code          varchar(50)  not null,
    match_name    varchar(200) not null,
    name          varchar(100) not null,
    emoji         varchar(20)  not null,
    sprite_id     varchar(50),
    display_order int          not null,
    primary key (id),
    constraint uk_author_character_code unique (code)
);

-- 국내 작가
insert into author_character (code, match_name, name, emoji, sprite_id, display_order) values
    ('han_gang',        '한강',     '소설가 한강',       '🪶', null, 1),
    ('kim_young_ha',    '김영하',   '여행자 김영하',     '✈️', null, 2),
    ('park_kyung_ri',   '박경리',   '토지의 박경리',     '🌾', null, 3),
    ('cho_jung_rae',    '조정래',   '태백산맥 조정래',   '⛰️', null, 4),
    ('jeong_yu_jeong',  '정유정',   '스릴러 정유정',     '🔪', null, 5),
    ('kim_cho_yeop',    '김초엽',   'SF작가 김초엽',     '🚀', null, 6);

-- 해외 작가 (일본)
insert into author_character (code, match_name, name, emoji, sprite_id, display_order) values
    ('murakami_haruki',  '무라카미 하루키', '무라카미 하루키', '🎹', null, 7),
    ('higashino_keigo',  '히가시노 게이고', '추리의 히가시노', '🔍', null, 10);

-- 해외 작가 (프랑스)
insert into author_character (code, match_name, name, emoji, sprite_id, display_order) values
    ('bernard_werber',   '베르나르 베르베르', '개미의 베르베르', '🐜', null, 8),
    ('victor_hugo',      '빅토르 위고',       '레미제라블 위고', '⚖️', null, 18),
    ('albert_camus',     '알베르 카뮈',       '이방인의 카뮈',   '🌅', null, 12),
    ('guillaume_musso',  '기욤 뮈소',         '기욤 뮈소',       '💌', null, 11);

-- 해외 작가 (독일·스위스)
insert into author_character (code, match_name, name, emoji, sprite_id, display_order) values
    ('hermann_hesse',    '헤르만 헤세',       '수레바퀴의 헤세', '🎨', null, 9),
    ('kafka',            '프란츠 카프카',     '변신의 카프카',   '🪲', null, 15);

-- 해외 작가 (러시아)
insert into author_character (code, match_name, name, emoji, sprite_id, display_order) values
    ('dostoevsky',   '도스토옙스키', '죄와벌 도스토옙스키',    '🕯️', null, 13),
    ('tolstoy',      '톨스토이',     '전쟁과평화 톨스토이',    '🕊️', null, 14);

-- 해외 작가 (남미)
insert into author_character (code, match_name, name, emoji, sprite_id, display_order) values
    ('marquez',      '가르시아 마르케스', '마술사 마르케스', '🦋', null, 16);

-- 해외 작가 (영미)
insert into author_character (code, match_name, name, emoji, sprite_id, display_order) values
    ('hemingway',    '어니스트 헤밍웨이', '빙산의 헤밍웨이',   '🎣', null, 17),
    ('george_orwell','조지 오웰',         '1984의 오웰',        '👁️', null, 19),
    ('jane_austen',  '제인 오스틴',       '오만과편견 오스틴', '💍', null, 20);
