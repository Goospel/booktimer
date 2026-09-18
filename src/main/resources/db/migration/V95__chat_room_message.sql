-- V95 — 맞팔 DM 방·메시지(설계 2026-09-18 §5-1).
--
-- chat_room: 두 사람 쌍당 1행. user_a_id < user_b_id로 정규화해 (a,b)/(b,a) 중복을 UNIQUE가 막는다.
-- 「잠김」(언팔·제재)은 저장하지 않는다 — 요청 시점에 follow·block·users에서 파생한다. 저장되는 상태는
-- 차단으로 닫힌 CLOSED뿐이고, 다시 맞팔한 뒤 방을 열면 같은 행이 OPEN으로 되살아난다(옛 메시지 보존).
-- a_/b_ 접두 컬럼은 각 참여자 몫이다: 숨김(「나가기」 — 상대는 모른다) · 마지막 읽은 메시지 id ·
-- 마지막 새 메시지 푸시 시각(방·수신자당 30분 1통).
--
-- chat_message: 본문은 AES-256-GCM 암호문(nonce 12 ‖ ct ‖ tag 16)이라 varbinary다. 평문 상한 1000자는
-- UTF-8로 최대 3000바이트 + 28이라 4096에 들어간다. 수정·삭제 기능이 없어(증거 보존) updated_at이 없다.
--
-- 두 테이블 다 users를 FK로 참조한다 → AccountService.purge가 메시지 → 방 순서로 지운다
-- (FlywayMigrationTest#everyTableWithForeignKeyToUsersIsClearedByPurge가 집합을 대조한다).
create table chat_room (
    id              bigint       not null auto_increment,
    user_a_id       bigint       not null,
    user_b_id       bigint       not null,
    status          varchar(10)  not null,
    a_hidden        boolean      not null default false,
    b_hidden        boolean      not null default false,
    a_last_read_id  bigint       not null default 0,
    b_last_read_id  bigint       not null default 0,
    a_last_push_at  datetime(6)  null,
    b_last_push_at  datetime(6)  null,
    closed_at       datetime(6)  null,
    created_at      datetime(6)  not null,
    updated_at      datetime(6)  not null,
    primary key (id),
    constraint uk_chat_room_pair unique (user_a_id, user_b_id),
    constraint fk_chat_room_a foreign key (user_a_id) references users (id),
    constraint fk_chat_room_b foreign key (user_b_id) references users (id)
);

-- b 쪽으로 「내 방」을 찾는 조회용. a 쪽은 uk_chat_room_pair의 선두 컬럼이 덮는다.
create index idx_chat_room_user_b on chat_room (user_b_id);

create table chat_message (
    id          bigint           not null auto_increment,
    room_id     bigint           not null,
    sender_id   bigint           not null,
    body        varbinary(4096)  not null,
    flagged     boolean          not null default false,
    created_at  datetime(6)      not null,
    primary key (id),
    constraint fk_chat_message_room foreign key (room_id) references chat_room (id),
    constraint fk_chat_message_sender foreign key (sender_id) references users (id)
);

-- 방 안 커서 조회(after=N)·마지막 메시지·미읽음 수가 전부 이 키를 탄다.
create index idx_chat_message_room_id on chat_message (room_id, id);
