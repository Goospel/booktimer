-- PWA L3a: Web Push 구독 테이블 + users 리마인더 컬럼 추가
-- H2(테스트) / MySQL(운영) 호환 SQL

-- 푸시 구독 저장소.
-- endpoint 는 브라우저별 고유 URL — 유니크 제약으로 동일 endpoint 중복 행 방지.
-- 한 사용자가 여러 디바이스(multi-device)에 구독할 수 있어 user_id + endpoint 조합으로 관리한다.
CREATE TABLE push_subscriptions (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    user_id         BIGINT          NOT NULL,
    endpoint        VARCHAR(512)    NOT NULL,
    p256dh          VARCHAR(255)    NOT NULL,
    auth            VARCHAR(255)    NOT NULL,
    created_at      DATETIME(6)     NOT NULL,
    updated_at      DATETIME(6)     NOT NULL,
    last_success_at DATETIME(6),
    CONSTRAINT pk_push_subscriptions PRIMARY KEY (id),
    CONSTRAINT uk_push_sub_endpoint  UNIQUE (endpoint),
    CONSTRAINT fk_push_sub_user      FOREIGN KEY (user_id) REFERENCES users (id)
);

-- 매일 독서 리마인더 opt-in 플래그 + 발송 멱등 시각
ALTER TABLE users ADD COLUMN daily_reminder_enabled  BOOLEAN     NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN last_reminder_sent_at   DATETIME(6);
