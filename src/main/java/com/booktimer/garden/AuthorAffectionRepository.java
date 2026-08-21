package com.booktimer.garden;

import com.booktimer.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 작가 캐릭터 먹이주기 정(affection) 영속성.
 *
 * <p>SPEND 측 저장 — 먹인 횟수 조회·합산. EARN(달성일)은 {@link DailyQuotaCalculator}가 유도한다.
 */
public interface AuthorAffectionRepository extends JpaRepository<AuthorAffection, Long> {

    /** 사용자의 모든 캐릭터 affection 목록. */
    List<AuthorAffection> findByUser(User user);

    /** 특정 (user, code) 행 단건 조회 — upsert 판단용. */
    Optional<AuthorAffection> findByUserAndCharacterCode(User user, String characterCode);

    /**
     * 사용자의 affection 행을 모두 지운다 — 탈퇴({@code AccountService.purge})용.
     * {@code author_affection.user_id}가 users를 FK 참조하므로 유저 삭제 전에 정리해야 한다.
     */
    void deleteByUser(User user);

    /**
     * 사용자가 지금까지 먹인 총 횟수 합. 행이 없으면 0을 반환한다.
     *
     * <p>COALESCE로 null(행 없음)을 0으로 변환 — {@code foodBalance = earned - spent}에서
     * NPE 없이 쓸 수 있다.
     */
    @Query("SELECT COALESCE(SUM(a.feedCount), 0) FROM AuthorAffection a WHERE a.user = :user")
    long sumFeedCountByUser(@Param("user") User user);
}
