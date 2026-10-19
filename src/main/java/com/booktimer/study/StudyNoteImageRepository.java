package com.booktimer.study;

import com.booktimer.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

/**
 * 필기 사진 영속성 — 조회는 언제나 주인을 함께 건다(IDOR, {@link StudyNoteRepository}와 같은 규율).
 */
public interface StudyNoteImageRepository extends JpaRepository<StudyNoteImage, Long> {

    /** 내 사진일 때만 — id만으로 찾는 메서드를 두지 않는다(IDOR, StudyNoteRepository와 같은 규율). */
    Optional<StudyNoteImage> findByIdAndUser(Long id, User user);

    /** 사람당 사용량 — byte_size만 더한다(바이트를 읽지 않는다). */
    @Query("select coalesce(sum(i.byteSize), 0) from StudyNoteImage i where i.user = :user")
    long totalBytes(@Param("user") User user);

    /** 서비스 전체 사용량 — 전체 상한({@link StudyNoteImage#TOTAL_QUOTA_BYTES})용. 역시 byte_size만. */
    @Query("select coalesce(sum(i.byteSize), 0) from StudyNoteImage i")
    long totalBytesAll();

    /**
     * 탈퇴 정리 — <b>벌크</b>여야 한다. 파생 deleteByUser는 지우기 전에 행마다 엔티티를 읽어 사진 바이트를 전부 힙에 올린다
     * (사람당 최대 200MB, 힙 약 350MB — OOM이면 탈퇴 자체가 실패한다). flush·clear는 ChatRoomRepository#deleteByMember 선례.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from StudyNoteImage i where i.user = :user")
    void deleteByUser(@Param("user") User user);

    /**
     * 「주인의 필기 중 하나가 이 주소를 가리킨다」 — 닫는 괄호가 경계라 images/12가 images/1을 살리지 않는다.
     * 마크다운 꼴이 아니어도 주소만 보이면 산 것으로 센다(넓게 = 지울 쪽으로 틀리지 않는다).
     * ⚠️ 이 레포의 첫 네이티브 SQL이다 — H2 테스트와 MySQL 8.4 원장(U-3)이 <b>같은 문장</b>을 그대로 재게 하려는 것이다.
     * 삭제 대상 표에 별칭을 쓰지 않는다(MySQL·H2 공통 문법 — 스파이크와 같은 꼴). 표·열 이름을 바꾸면 여기가 깨진다.
     * ponytail: 상관 LIKE 스캔이라 밤마다 (사진 수 × 주인 필기 글자 수). 사람당 수백 장·수백 장 필기면 수 초. 넘치면 참조 표(저장 때 갱신)로.
     */
    String REFERENCED = "EXISTS (SELECT 1 FROM study_note n WHERE n.user_id = study_note_image.user_id "
            + "AND n.body LIKE CONCAT('%/api/study/notes/images/', study_note_image.id, ')%'))";

    /** 정리 ① — 표시된 사진을 다시 가리키면(되돌리기·붙여넣기) 표시를 푼다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = "UPDATE study_note_image SET orphaned_at = NULL WHERE orphaned_at IS NOT NULL AND " + REFERENCED)
    int unmarkReferenced();

    /** 정리 ② — 표시된 지 {@code cutoff} 전이고 지금도 아무도 안 가리키는 사진을 지운다(지우는 문장에도 미참조를 다시 건다). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = "DELETE FROM study_note_image WHERE orphaned_at < :cutoff AND NOT " + REFERENCED)
    int deleteOrphanedBefore(@Param("cutoff") Instant cutoff);

    /** 정리 ③ — 새로 아무도 안 가리키게 된 사진에 표시를 찍는다(다음 밤에도 그러면 ②가 지운다). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = "UPDATE study_note_image SET orphaned_at = :now WHERE orphaned_at IS NULL AND NOT " + REFERENCED)
    int markOrphans(@Param("now") Instant now);
}
