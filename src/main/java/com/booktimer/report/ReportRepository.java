package com.booktimer.report;

import com.booktimer.user.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Report 영속성 — 쌍 존재 검사, 관리자 전체 조회(신고함), 탈퇴 정리 메서드를 둔다.
 */
public interface ReportRepository extends JpaRepository<Report, Long> {

    boolean existsByReporterAndReported(User reporter, User reported);

    java.util.Optional<Report> findByReporterAndReported(User reporter, User reported);

    /**
     * 자동 7일 정지의 판정 — 이 사용자를 <b>대화방에서</b> 신고한 미처리 신고의 <b>서로 다른</b> 신고자 수.
     * 프로필 신고(방 없음)와 처리 끝난 신고는 세지 않는다(정책 문서 §2 「자동 조치」).
     */
    @org.springframework.data.jpa.repository.Query("""
            select count(distinct r.reporter) from Report r
            where r.reported = :u and r.status = com.booktimer.report.ReportStatus.OPEN and r.chatRoomId is not null
            """)
    long countOpenChatReporters(@org.springframework.data.repository.query.Param("u") User reported);

    /** 관리자 배너 — 미처리 신고 수. */
    long countByStatus(ReportStatus status);

    /** 이 방들을 가리키는 신고 — 보존 스케줄러가 「지켜야 할 방」과 「풀어 줄 신고」를 가른다. */
    List<Report> findByChatRoomIdIn(java.util.Collection<Long> roomIds);

    /**
     * 관리자 신고함 — 최신순 전체 조회(운영자가 후속 처리하려고 본다).
     * id 내림차순을 tiebreak으로 둔다 — 같은 초에 들어온 신고도 결정적으로 최신순 정렬(createdAt 동률 방지).
     */
    @EntityGraph(attributePaths = {"reporter", "reported"})
    List<Report> findAllByOrderByCreatedAtDescIdDesc();

    /** 회원 탈퇴 정리 — 내가 한 신고와 나를 향한 신고를 모두 제거한다. */
    void deleteByReporter(User reporter);

    void deleteByReported(User reported);
}
