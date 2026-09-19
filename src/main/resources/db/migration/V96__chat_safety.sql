-- V96 — 맞팔 DM 신고·보존(설계 2026-09-18 §5-1, 정책 문서 claude-docs/chat-dm-policy.md §2·§5·§6).
--
-- report.chat_room_id: 대화방에서 한 신고가 가리키는 방. 운영자는 이 값이 있는 신고로만 대본을 연다
--   (신고 없는 대화방은 열람 불가). 방이 30일 보존으로 지워지면 처리 끝난 신고의 이 값만 null로 풀린다.
-- report.status: OPEN(미처리) / RESOLVED(처리 완료). 기존 신고 행은 전부 OPEN으로 시작한다 — 지금까지는
--   처리한 신고를 삭제하는 운용이라 남은 행이 곧 미처리다.
-- report.resolution: 처리 때 고른 조치(NONE·WARN·SUSPEND_7D·BAN) — 경고의 기록이 여기 남는다(제재 사다리의
--   「다음 위반 시 정지」를 운영자가 판단할 근거).
-- report.legal_hold: 수사기관 요청 등 법적 보존. 보존 스케줄러(자동 삭제)만 이 방을 건너뛴다 — 회원 탈퇴는
--   정책 §5대로 즉시 삭제한다.
alter table report add column chat_room_id bigint null;
alter table report add column status varchar(12) not null default 'OPEN';
alter table report add column resolution varchar(16) null;
alter table report add column legal_hold boolean not null default false;
alter table report add constraint fk_report_chat_room foreign key (chat_room_id) references chat_room (id);
alter table report add constraint ck_report_status check (status in ('OPEN', 'RESOLVED'));

-- 쌍 정규화(a.id < b.id)를 DB에서도 막는다(PR-1 리뷰 사소 7). 어긋난 행이 생기면 uk_chat_room_pair가
-- (a,b)/(b,a)를 다른 쌍으로 보고 같은 두 사람의 방이 둘이 된다. 상태값도 같이 묶는다.
alter table chat_room add constraint ck_chat_room_pair_order check (user_a_id < user_b_id);
alter table chat_room add constraint ck_chat_room_status check (status in ('OPEN', 'CLOSED'));

-- 보존 스케줄러가 「방을 가리키는 미처리·보존 신고」를 찾는 키.
create index idx_report_chat_room on report (chat_room_id);
