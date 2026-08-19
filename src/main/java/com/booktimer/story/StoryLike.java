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
 * <p><b>자기 글은 못 누른다</b>: 여백은 자기 노트라 내 여백의 모든 글이 자기 좋아요 대상이 되고,
 * 그러면 개수가 「남이 인정한 문장」이라는 의미를 잃는다. 팔로우가 자기 자신을 금지하는 것과 같은 이유다.
 *
 * <p>나머지 노출 게이트(차단·비공개 책·비팔로워)는 <b>관계</b>를 봐야 해서 여기가 아니라
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

    /** 누른 사람. 글 작성자와 같을 수 없다. */
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
     * @throws IllegalArgumentException user/story가 없거나, 자기 글인 경우
     */
    public static StoryLike of(User user, Story story) {
        if (user == null || story == null) {
            throw new IllegalArgumentException("user/story must not be null");
        }
        if (isSameUser(story.getUser(), user)) {
            throw new IllegalArgumentException("cannot like own story");
        }
        return new StoryLike(user, story);
    }

    private static boolean isSameUser(User a, User b) {
        if (a == b) {
            return true;
        }
        return a.getId() != null && a.getId().equals(b.getId());
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
