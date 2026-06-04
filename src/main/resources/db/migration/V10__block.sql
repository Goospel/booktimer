-- V10 — 차단(Block) 관계 테이블 (SNS 5단계, sns-design §7.5·§9).
--
-- blocker_id 가 blocked_id 를 차단한다(단방향 저장, 효과는 대칭 — 게이트가 양방향 검사).
-- (blocker_id, blocked_id) 유니크로 중복 차단을 막는다(앱 레벨 existsBy 가드와 이중).
-- 자기 차단 금지는 도메인 검증이 담당(DB CHECK는 MySQL/H2 호환 위해 생략, V9 관례와 동일).
-- 시각은 BaseTimeEntity created_at/updated_at → datetime(6). 신규 테이블 추가뿐이라 기존 데이터 무영향.

create table block (
    id         bigint       not null auto_increment,
    blocker_id bigint       not null,
    blocked_id bigint       not null,
    created_at datetime(6)  not null,
    updated_at datetime(6)  not null,
    primary key (id),
    constraint uk_block unique (blocker_id, blocked_id),
    constraint fk_block_blocker foreign key (blocker_id) references users (id),
    constraint fk_block_blocked foreign key (blocked_id) references users (id)
);

create index ix_block_blocker on block (blocker_id);
create index ix_block_blocked on block (blocked_id);
