package com.booktimer.blog;

import com.booktimer.blog.QuoteBox.Owner;
import com.booktimer.user.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 인용 박스(V107) — 주인이 다형이라 FK가 없다: 주인을 지우는 길이 아래 지우기를 반드시 부른다(계획 §5.2). */
public interface QuoteBoxRepository extends JpaRepository<QuoteBox, Long> {

    List<QuoteBox> findByOwnerTypeAndOwnerIdOrderByLabel(Owner ownerType, Long ownerId);

    /** 반영 · 다시 고르기의 박스 행 잠금 — 그 트랜잭션의 <b>첫 DB 문장</b>(§4.11 · 고아 정리와 줄을 선다). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from QuoteBox q where q.id = :id")
    Optional<QuoteBox> findForUpdate(@Param("id") Long id);

    /** 쪽지에서 빼기 — 박스를 그 쪽지에 묶어 찾는다(남의 · 다른 쪽지의 박스 id는 없음 — 비평 SEC-2). */
    Optional<QuoteBox> findByIdAndOwnerTypeAndOwnerId(Long id, Owner ownerType, Long ownerId);

    long countByOwnerTypeAndOwnerId(Owner ownerType, Long ownerId);

    boolean existsByOwnerTypeAndOwnerId(Owner ownerType, Long ownerId);

    /** 목록의 {@code quoteCount} 한 번에 — [ownerId, count]. {@code ids}가 비면 부르지 않는다(비평 D6). */
    @Query("select q.ownerId, count(q) from QuoteBox q where q.ownerType = :t and q.ownerId in :ids group by q.ownerId")
    List<Object[]> countByOwners(@Param("t") Owner ownerType, @Param("ids") Collection<Long> ids);

    /** 주인(글 · 쪽지)을 지울 때 — 일괄 DELETE · 문맥 비움(B1a 문장 지우기와 같은 꼴). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from QuoteBox q where q.ownerType = :t and q.ownerId = :id")
    void deleteByOwner(@Param("t") Owner ownerType, @Param("id") Long ownerId);

    /** 탈퇴 정리 — 내 글 · 내 쪽지가 가진 박스. 글 · 쪽지보다 앞이어야 하위 질의가 주인을 찾는다(AccountService.purge). */
    default void deleteOwnedBy(User user) {
        deleteOwnedBy(user, Owner.POST, Owner.NOTE);
    }

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from QuoteBox q
            where (q.ownerType = :post and q.ownerId in (select p.id from BlogPost p where p.user = :user))
               or (q.ownerType = :note and q.ownerId in (select s.id from com.booktimer.story.Story s where s.user = :user))
            """)
    void deleteOwnedBy(@Param("user") User user, @Param("post") Owner post, @Param("note") Owner note);

    /**
     * 탈퇴 정리 — 남의 박스에 남은 원 글쓴이 참조를 비운다(설계 B2 줄 「원문 참조 null(C4)」 · 비평 L6). 보기는 「원문 지워짐」과 같고
     * 출처 이름은 「탈퇴한 사용자」 · 박스 글 · 제목 스냅샷은 그대로다(REQ-209).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update QuoteBox q set q.sourceUserId = null, q.sourcePostId = null, q.members = null, q.seenSentenceId = null "
            + "where q.sourceUserId = :id")
    void detachSourceUser(@Param("id") Long userId);
}
