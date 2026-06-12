package com.booktimer.admin;

import com.booktimer.user.AuthProvider;
import com.booktimer.user.Role;

import java.time.Instant;
import java.util.List;

/**
 * 관리자 사용자 드릴다운 상세 (admin-data-lookup-design §2.2).
 *
 * <p>계정 메타 + 타이머 설정 + 최근 N세션 + 책장 요약을 한 화면용으로 조립한 스냅샷이다.
 * <b>비밀번호 해시는 담지 않는다.</b> 모든 lazy 접근(세션→책 제목)은 조립 시점(트랜잭션 안)에 끝낸다.
 *
 * @param timer          타이머 설정(온보딩 전 등 없으면 {@code null})
 * @param recentSessions 최근 측정 세션(startedAt 내림차순, 최대 N개)
 * @param bookshelf      책장 요약(상태별·공개 수)
 */
public record AdminUserDetail(
        String loginId,
        String nickname,
        String email,
        String emailMasked,
        AuthProvider provider,
        Instant createdAt,
        boolean onboarded,
        boolean emailVerified,
        Role role,
        String timezone,
        TimerInfo timer,
        List<SessionInfo> recentSessions,
        BookshelfSummary bookshelf) {

    /**
     * 타이머 설정/부채 스냅샷 — 7일 윈도우 부채 모델 기준.
     *
     * @param dailyGoalSeconds   하루 목표(초)
     * @param todayDebtSeconds   오늘 부채(초) = max(0, 목표 − 오늘 읽은 양)
     * @param weeklyDebtSeconds  이번 주 총 부채(초) = 오늘 + 윈도우 내 빠뜨린 날 합
     */
    public record TimerInfo(
            long dailyGoalSeconds,
            long todayDebtSeconds,
            long weeklyDebtSeconds) {
    }

    /** 한 측정 세션 요약 — 책 제목은 조립 시점에 해소(미지정이면 null). */
    public record SessionInfo(
            String bookTitle,
            long durationSeconds,
            Instant startedAt) {
    }

    /** 책장 요약 — 총 수 + 상태별 + 공개(PUBLIC) 수. */
    public record BookshelfSummary(
            long total,
            long wantToRead,
            long reading,
            long finished,
            long publicCount) {
    }
}
