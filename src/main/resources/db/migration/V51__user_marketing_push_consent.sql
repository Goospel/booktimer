-- PWA L3b: 마케팅 푸시 동의 / 비활동 복귀 nudge 멱등 컬럼 추가
-- marketing_push_consent  : §50 opt-in(기본 false — 끼워팔기 금지)
-- marketing_push_consent_at : 동의 시각(철회 시에도 감사용 보존)
-- marketing_push_nudge_sent_at : L3b 발송 멱등(이메일 last_nudge_sent_at과 독립)
ALTER TABLE users ADD COLUMN marketing_push_consent BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN marketing_push_consent_at TIMESTAMP NULL;
ALTER TABLE users ADD COLUMN marketing_push_nudge_sent_at TIMESTAMP NULL;
