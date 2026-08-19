package com.booktimer.story;

import com.booktimer.book.Book;
import com.booktimer.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StoryLikeRepository extends JpaRepository<StoryLike, Long> {

    /** 글 하나 → 거기 달린 좋아요 수. {@link #countsByStoryIds} 투영. */
    interface StoryLikeCount {
        Long getStoryId();

        long getCount();
    }

    /**
     * 여백 목록용 배치 집계 — 글 여럿의 좋아요 수를 한 쿼리로 (N+1 금지, {@code recencyByBook}과 같은 모양).
     *
     * <p>좋아요가 없는 글은 <b>행이 아예 없다</b>(group by) — 호출부가 0으로 채운다.
     * 빈 목록이면 호출하지 않는다({@code in ()}은 DB마다 취급이 다르다).
     */
    @Query("""
            select l.story.id as storyId, count(l) as count
            from StoryLike l
            where l.story.id in :storyIds
            group by l.story.id
            """)
    List<StoryLikeCount> countsByStoryIds(@Param("storyIds") Collection<Long> storyIds);

    /**
     * 그 목록 중 <b>viewer가 누른</b> 글의 id — 카드마다 하트를 채울지 가르는 재료.
     *
     * <p>자기 여백에서는 호출하지 않는다: 자기 글엔 누를 수 없어 결과가 구조적으로 항상 비어 있다.
     */
    @Query("""
            select l.story.id
            from StoryLike l
            where l.story.id in :storyIds and l.user = :viewer
            """)
    List<Long> likedStoryIds(@Param("storyIds") Collection<Long> storyIds, @Param("viewer") User viewer);

    Optional<StoryLike> findByStoryAndUser(Story story, User user);

    /** 누르기·취소 직후 돌려줄 갱신값 — 클라가 개수를 추측하지 않게 서버가 센 값을 준다. */
    long countByStory(Story story);

    /** 글 개별 삭제({@code StoryService.delete}) — story_like.story_id FK보다 앞서야 한다. */
    void deleteByStory(Story story);

    /** 책 삭제({@code BookService.delete}) — 그 책 여백의 글들에 달린 좋아요를 통째로. */
    void deleteByStoryBook(Book book);

    /** 탈퇴 — <b>내가 남에게 누른</b> 좋아요(story_like.user_id FK). */
    void deleteByUser(User user);

    /** 탈퇴 — <b>내 글에 달린 남의</b> 좋아요. 내 글을 지우기 전에 정리해야 한다. */
    void deleteByStoryUser(User user);
}
