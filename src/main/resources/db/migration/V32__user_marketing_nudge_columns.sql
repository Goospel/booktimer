-- V32 — 마케팅 수신동의 + 재참여 넛지 상태(이메일 인프라 2단계 PR-1).
--
-- 1) marketing_email_consent — 영리목적 광고성 정보(재참여 넛지) 수신 동의. 정보통신망법 §50 사전 동의(opt-in)
--    불변식이라 not null default false(기본 미동의). 기존 사용자는 동의한 적 없으므로 false로 둔다(백필 안 함 —
--    email_verified와 달리 grandfather 부여가 동의 위조라 금지). 가입 폼·설정의 별도 선택 항목으로만 켜진다.
-- 2) marketing_consent_at — 마지막 동의 시각(2년 재동의 고지·감사 근거). 철회해도 보존. nullable.
-- 3) last_nudge_sent_at — 마지막 넛지 발송 시각. 비활동 구간당 1회 멱등 보장 상태. nullable.
--    boolean/datetime(6)는 MySQL·H2 양쪽 동일 동작(V6·V19·V31 관례).

alter table users add column marketing_email_consent boolean    not null default false;
alter table users add column marketing_consent_at     datetime(6);
alter table users add column last_nudge_sent_at        datetime(6);
