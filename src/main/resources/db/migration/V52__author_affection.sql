-- 작가 캐릭터 먹이주기 정(affection) 영속성 — 먹이 루프 SPEND 측 저장
-- feed_count: 해당 사용자가 해당 작가 캐릭터에게 먹인 횟수 누계
-- unique(user_id, character_code): 사용자 × 캐릭터 1행 보장(upsert용)
CREATE TABLE author_affection (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL,
    character_code VARCHAR(50)  NOT NULL,
    feed_count     INT          NOT NULL DEFAULT 0,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_author_affection_user_code (user_id, character_code),
    CONSTRAINT fk_author_affection_user FOREIGN KEY (user_id) REFERENCES users (id)
);
