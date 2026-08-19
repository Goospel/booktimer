-- V74 — 여백 글 좋아요(story_like) 테이블.
--
-- 「누가 어느 글에」 한 번뿐 — (story_id, user_id) 유니크가 중복의 최종 방어다. 앱 레벨 존재 검사는
-- 재전송 멱등(모바일 타임아웃 후 재시도)을 위한 것이고, 진짜 동시 요청을 막는 것은 이 제약이다.
--
-- 자기 글 금지는 도메인 검증(StoryLike.of)이 담당한다 — story.user_id와의 비교라 CHECK로 쓸 수 없고,
-- follow가 자기 팔로우를 같은 이유로 앱에 맡긴 전례(V9)를 따른다.
--
-- 인덱스: uk_story_like가 (story_id, user_id) 선두라 「글별 집계」(countsByStoryIds·countByStory)를
-- 이미 커버한다. 「내가 누른 것」(likedStoryIds)은 story_id in (...) + user_id라 같은 유니크로 충분하고,
-- fk_story_like_user가 만드는 user_id 인덱스가 탈퇴 정리(deleteByUser)를 받는다 — 별도 인덱스 없음.
--
-- 신규 테이블 추가뿐이라 기존 데이터에 영향 없음(additive).

create table story_like (
    id         bigint      not null auto_increment,
    story_id   bigint      not null,
    user_id    bigint      not null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id),
    constraint uk_story_like unique (story_id, user_id),
    constraint fk_story_like_story foreign key (story_id) references story (id),
    constraint fk_story_like_user foreign key (user_id) references users (id)
);
