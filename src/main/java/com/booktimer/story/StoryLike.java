package com.booktimer.story;

import com.booktimer.common.BaseTimeEntity;
import com.booktimer.user.User;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 여백에 남긴 글에 누른 좋아요 — 「누가 어느 글에」 한 번뿐이다.
 *
 * <p>{@code (story, user)} 유니크가 중복의 최종 방어다({@code Follow}와 같은 관례) — 앱 레벨
 * 존재 검사는 재전송 멱등을 위한 것이고, 진짜 동시 요청을 막는 것은 DB다.
 *
 * <p><b>자기 글도 누를 수 있다</b>(2026-08-20). 처음엔 「개수가 남이 인정한 문장이라는 의미를 잃는다」는
 * 이유로 막았지만, 여백에 글을 쓴 사람이 아직 없어 좋아요를 확인할 길이 자체가 없다는 실사용 요구가
 * 이겼고 트위터·인스타도 자기 글에 눌린다. 그 결과 도메인 불변식이 null 거부만 남았고, 노출 규칙은
 * 「받은 글은 전부 누를 수 있다」 하나로 줄어 클라이언트의 `likable()` 분기가 통째로 사라졌다.
 *
 * <p>노출 게이트(차단·비공개 책·비팔로워)는 <b>관계</b>를 봐야 해서 여기가 아니라
 * {@link StoryService#like}에 있다 — 그쪽이 목록 게이트({@code marginOf})와 같은 판정을 재사용한다.
 */
@Entity
@Table(name = "story_like", uniqueConstraints = {
        @UniqueConstraint(name = "uk_story_like", columnNames = {"story_id", "user_id"})
})
public class StoryLike extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 좋아요가 달린 글. 글이 사라지면 이 행도 사라진다({@code StoryService.delete}가 먼저 지운다). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "story_id", nullable = false)
    private Story story;

    /** 누른 사람. 글 작성자 본인일 수도 있다(자기 좋아요 허용 — 2026-08-20). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    protected StoryLike() {
        // JPA
    }

    private StoryLike(User user, Story story) {
        this.user = user;
        this.story = story;
    }

    /**
     * 좋아요를 만든다.
     *
     * @throws IllegalArgumentException user/story가 없는 경우
     */
    public static StoryLike of(User user, Story story) {
        if (user == null || story == null) {
            throw new IllegalArgumentException("user/story must not be null");
        }
        return new StoryLike(user, story);
    }

    public Long getId() {
        return id;
    }

    public Story getStory() {
        return story;
    }

    public User getUser() {
        return user;
    }
}
