-- V37 — 독서 정원 트랙 B 숨은 레시피(책 조합 발견). (2026-06-13)
--
-- 설계: 완독(FINISHED)+읽고싶음(WANT_TO_READ) 책의 '조합'을 발견하면 식물을 얻는다. 트랙 A(시간축 plant·
-- 장르축 genre_plant)와 별개의 수집축. 트랙 A는 보유를 저장 안 하고 유도하지만(부채 모델 N-058), 트랙 B는
-- 발견을 '저장'한다 — 발견은 '처음 만족된 순간'의 이벤트라 박아야 하고(NEW·토스트), 책장이 바뀌어도 보유가
-- 유지돼야 하기 때문(설계 §2.2). 시간축 plant/V35·장르축 genre_plant/V36은 무변경(회귀 0).
--
-- 조건↔식물 연결은 코드(RecipeDefinition, 임의 술어라 SQL화가 부자연)에 두고, 이 테이블엔 결과 식물의
-- 표시 메타(이름·이모지·스토리)만 시드한다. code = RecipeDefinition.plantCode와 일치해야 발견이 도감 칸으로
-- 렌더된다. 이모지: 트랙 A 시간축·장르축과 겹치지 않는 풀(꽃·열매·채소). utf8mb4. H2(테스트)·MySQL(운영) 공통.
--
-- ⚠️ 베타 큐레이션: 이 8종은 메커닉을 작동시키는 '예시'일 뿐 확정이 아니다. 식물 카탈로그를 직접 커스텀
-- (관리자 입력)으로 관리하는 단계에서 식물·레시피를 함께 교체한다(계획 §8, 사용자 결정 2026-06-13).
-- discovered_at: 발견 시각(Instant) — 코드베이스 관례대로 datetime(6)(예: users.marketing_consent_at).

create table recipe_plant (
    id            bigint       not null auto_increment,
    code          varchar(50)  not null,
    name          varchar(100) not null,
    emoji         varchar(20)  not null,
    story         varchar(255) not null,
    display_order int          not null,
    primary key (id),
    constraint uk_recipe_plant_code unique (code)
);

-- 베타 예시 레시피 결과 식물 8종 (조건은 RecipeDefinition.ALL 참조).
insert into recipe_plant (code, name, emoji, story, display_order) values
    ('lotus',      '연꽃',         '💮', '감성으로 읽고 이성을 탐낸다',   1),
    ('curiosity',  '호기심 열매',  '🥝', '원리를 알고 의미를 묻는다',     2),
    ('sunset',     '노을 토마토',  '🍅', '이야기에서 아름다움으로',       3),
    ('bluebell',   '도전 멜론',    '🍈', '나를 갈고 세상에 나선다',       4),
    ('explorer',   '탐험가 야자열매','🥥', '편식 없는 탐험가',            5),
    ('harvest',    '황금 옥수수',  '🌽', '꾸준한 수확과 다음 씨앗',       6),
    ('regular',    '단골 당근',    '🥕', '한 작가를 깊이 따라간다',       7),
    ('timekeeper', '시간의 올리브','🫒', '지나간 시간을 더 깊이 판다',    8);

-- 발견 이력 — 한 번 발견하면 영구 보유. (user_id, plant_code) 유니크로 중복 발견 차단(멱등·동시성 안전망).
create table user_discovered_plant (
    id            bigint       not null auto_increment,
    user_id       bigint       not null,
    plant_code    varchar(50)  not null,
    discovered_at datetime(6)  not null,
    primary key (id),
    constraint uk_user_discovered_plant unique (user_id, plant_code)
);
