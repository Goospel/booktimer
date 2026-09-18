package com.booktimer.chat;

import com.booktimer.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    /** 정규화된 쌍(a.id &lt; b.id)으로 찾는다 — 호출자는 {@link ChatRoom#of}와 같은 순서로 넘긴다. */
    Optional<ChatRoom> findByUserAAndUserB(User userA, User userB);

    /** 내가 참여한 열린 방 전부(숨김 여부는 호출자가 거른다). 상대를 같이 읽어 목록 조립의 lazy ×N을 없앤다. */
    @Query("""
            select r from ChatRoom r join fetch r.userA join fetch r.userB
            where (r.userA = :u or r.userB = :u) and r.status = com.booktimer.chat.ChatRoom.Status.OPEN
            """)
    List<ChatRoom> findOpenByMember(@Param("u") User user);

    /**
     * 회원 탈퇴 정리 — 메시지를 먼저 지운 뒤 부른다({@link ChatMessageRepository#deleteByRoomMember}).
     *
     * <p><b>벌크 삭제 + 컨텍스트 비우기</b>인 이유: 같은 영속성 컨텍스트에 방·메시지가 올라와 있으면(발송 직후
     * 같은 트랜잭션 등) 벌크로 지운 행이 관리 엔티티로 남아, 뒤의 유저 삭제 flush가 「삭제된 유저를 참조한다」로
     * 죽는다. 엔티티 단위 삭제로 바꾸면 메시지를 전부 <b>복호화하며 읽어야</b> 해서, 키가 빠진 환경에선 탈퇴가
     * 막힌다. 그래서 벌크로 지우고 컨텍스트를 비운다 — 호출 전 변경은 {@code flushAutomatically}가 먼저 반영하고,
     * purge 뒤의 호출자는 새 엔티티만 만든다(OAuth 선점 폐기 → 새 계정 INSERT).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ChatRoom r where r.userA = :u or r.userB = :u")
    void deleteByMember(@Param("u") User user);
}
