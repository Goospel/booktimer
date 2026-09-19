package com.booktimer.chat;

import com.booktimer.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /** 커서 이후 오래된 순 200건 — 폴링이 {@code after=마지막 id}로 이어 받는다. */
    List<ChatMessage> findTop200ByRoomAndIdGreaterThanOrderByIdAsc(ChatRoom room, long afterId);

    Optional<ChatMessage> findTopByRoomOrderByIdDesc(ChatRoom room);

    /** 관리자 대본 — 방 전체(신고된 방만 여기 온다). */
    List<ChatMessage> findByRoomOrderByIdAsc(ChatRoom room);

    /** 보존 삭제 — 지울 방들의 메시지. 방보다 먼저 지운다(FK). */
    @Modifying(flushAutomatically = true)
    @Query("delete from ChatMessage m where m.room.id in :roomIds")
    void deleteByRoomIdIn(@Param("roomIds") java.util.Collection<Long> roomIds);

    /** 미읽음 = 상대가 보낸, 내 마지막 읽음 이후의 메시지. */
    long countByRoomAndSenderNotAndIdGreaterThan(ChatRoom room, User me, long lastReadId);

    /**
     * 회원 탈퇴 정리 — 내가 참여한 방의 메시지 전부(내가 보낸 것 + 상대가 보낸 것). 상대 메시지도 방 FK로
     * 방 삭제를 막으므로 sender 기준이 아니라 방 기준으로 지운다.
     */
    // 컨텍스트 비우기는 바로 뒤의 ChatRoomRepository#deleteByMember가 한다(이유는 그쪽 주석).
    @Modifying(flushAutomatically = true)
    @Query("""
            delete from ChatMessage m where m.room.id in
              (select r.id from ChatRoom r where r.userA = :u or r.userB = :u)
            """)
    void deleteByRoomMember(@Param("u") User user);
}
