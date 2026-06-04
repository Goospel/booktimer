-- V9 — 팔로우(Follow) 관계 테이블 (SNS 3단계, sns-design §7.3).
--
-- 단방향 팔로우: follower_id 가 followee_id 를 팔로우한다(승인 없음, 언팔 즉시).
-- (follower_id, followee_id) 유니크로 중복 팔로우를 막는다(앱 레벨 existsBy 가드와 이중).
-- 자기 자신 팔로우 금지는 도메인 검증이 담당한다(DB CHECK는 MySQL/H2 호환·이식성 위해 생략).
-- 시각은 Instant → datetime(6) (BaseTimeEntity created_at/updated_at, V3 관례와 동일).
-- 신규 테이블 추가뿐이라 기존 데이터에 영향 없음(additive).

create table follow (
    id          bigint       not null auto_increment,
    follower_id bigint       not null,
    followee_id bigint       not null,
    created_at  datetime(6)  not null,
    updated_at  datetime(6)  not null,
    primary key (id),
    constraint uk_follow unique (follower_id, followee_id),
    constraint fk_follow_follower foreign key (follower_id) references users (id),
    constraint fk_follow_followee foreign key (followee_id) references users (id)
);

create index ix_follow_follower on follow (follower_id);
create index ix_follow_followee on follow (followee_id);
