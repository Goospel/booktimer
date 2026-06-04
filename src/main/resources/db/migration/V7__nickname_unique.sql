-- V7 — 닉네임 유니크화 (SNS 검색·프로필 핸들의 전제).
--
-- 닉네임은 SNS에서 사용자 검색 키이자 프로필 URL 핸들이 되므로 유니크해야 한다.
-- 그런데 기존 데이터에는 중복 닉네임이 있을 수 있다(지금까진 유니크 제약이 없었다).
-- 유니크 제약을 그냥 걸면 기존 중복 때문에 마이그레이션이 실패하므로, 먼저 백필로 중복을 정리한다.
-- (nickname은 V1부터 NOT NULL이라 NULL은 없다 — 중복만 처리하면 된다.)
--
-- 백필 규칙: 같은 닉네임 그룹에서 가장 먼저 가입한(가장 낮은 id) 행은 그대로 두고,
-- 이후 중복 행에는 '-{id}' 접미사를 붙여 유일하게 만든다(id는 PK라 유일 보장).
-- 사용자는 이후 설정에서 닉네임을 바꿀 수 있다.
--
-- 호환성: row_number() 윈도 함수 + 중첩 파생테이블(self-update 시 MySQL "target table" 오류 회피)로
-- MySQL 8 · H2 양쪽에서 동일하게 실행된다. 신규 환경(H2 테스트)에서는 users가 비어 update가 0행이다.

update users
set nickname = concat(nickname, '-', id)
where id in (
    select id from (
        select id,
               row_number() over (partition by nickname order by id) as rn
        from users
    ) ranked
    where ranked.rn > 1
);

alter table users add constraint uk_users_nickname unique (nickname);
