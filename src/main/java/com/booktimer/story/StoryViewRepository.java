package com.booktimer.story;

import com.booktimer.user.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface StoryViewRepository extends JpaRepository<StoryView, Long> {

    boolean existsByStoryAndViewer(Story story, User viewer);

    /** viewer가 이미 본 스토리 id 부분집합 — 피드 조립 배치 조회(N+1 회피). */
    @Query("select v.story.id from StoryView v where v.viewer = :viewer and v.story.id in :storyIds")
    List<Long> findViewedStoryIds(@Param("viewer") User viewer, @Param("storyIds") Collection<Long> storyIds);

    /** 열람자 목록 — viewer를 한 쿼리로 초기화(N+1 회피), 최신 열람 먼저. */
    @EntityGraph(attributePaths = "viewer")
    List<StoryView> findByStoryOrderByCreatedAtDesc(Story story);

    void deleteByStory(Story story);

    void deleteByViewer(User viewer);

    /**
     * 작성자의 모든 스토리에 달린 열람 기록 일괄 삭제 — 탈퇴 정리용.
     * JPQL delete는 묵시 조인이 안 되므로 서브쿼리로 묶는다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from StoryView v where v.story in (select s from Story s where s.user = :author)")
    void deleteByStoryAuthor(@Param("author") User author);
}
