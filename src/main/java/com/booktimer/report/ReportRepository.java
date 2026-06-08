package com.booktimer.report;

import com.booktimer.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Report 영속성 — 쌍 존재 검사, 관리자 전체 조회(신고함), 탈퇴 정리 메서드를 둔다.
 */
public interface ReportRepository extends JpaRepository<Report, Long> {

    boolean existsByReporterAndReported(User reporter, User reported);

    /**
     * 관리자 신고함 — 최신순 전체 조회(운영자가 후속 처리하려고 본다).
     * id 내림차순을 tiebreak으로 둔다 — 같은 초에 들어온 신고도 결정적으로 최신순 정렬(createdAt 동률 방지).
     */
    List<Report> findAllByOrderByCreatedAtDescIdDesc();

    /** 회원 탈퇴 정리 — 내가 한 신고와 나를 향한 신고를 모두 제거한다. */
    void deleteByReporter(User reporter);

    void deleteByReported(User reported);
}
