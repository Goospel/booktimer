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
 * 스토리 열람 기록 — 미열람 링(기기 무관)과 작성자의 열람자 목록("누가 봤나")의 근거 (sns-design §13).
 *
 * <p>{@code uk_story_view}(story_id, viewer_id)가 열람 기록의 멱등을 DB 레벨에서 보장한다.
 * 열람 시각은 {@code createdAt}(BaseTimeEntity)로 충분해 별도 컬럼을 두지 않는다.
 */
@Entity
@Table(name = "story_view", uniqueConstraints = {
        @UniqueConstraint(name = "uk_story_view", columnNames = {"story_id", "viewer_id"})
})
public class StoryView extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "story_id")
    private Story story;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "viewer_id")
    private User viewer;

    protected StoryView() {
        // JPA
    }

    private StoryView(Story story, User viewer) {
        this.story = story;
        this.viewer = viewer;
    }

    /**
     * 열람 기록을 만든다. 본인 열람 금지는 서비스 게이트가 담당한다(작성자 no-op — §13.4).
     *
     * @throws IllegalArgumentException story/viewer가 null인 경우
     */
    public static StoryView of(Story story, User viewer) {
        if (story == null || viewer == null) {
            throw new IllegalArgumentException("story/viewer must not be null");
        }
        return new StoryView(story, viewer);
    }

    public Long getId() {
        return id;
    }

    public Story getStory() {
        return story;
    }

    public User getViewer() {
        return viewer;
    }
}
