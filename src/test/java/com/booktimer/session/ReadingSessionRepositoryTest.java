package com.booktimer.session;

import com.booktimer.config.JpaConfig;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ReadingSessionRepository 슬라이스 테스트 (@DataJpaTest, H2).
 *
 * <p>user별 세션 목록 조회와 "진행 중(미종료) 세션" 조회를 검증한다.
 * 후자는 서비스의 중복 start 방지에 쓰인다. ReadingSession이 FK(user_id)를
 * 소유하므로 User를 먼저 저장한다.
 */
@DataJpaTest
@Import(JpaConfig.class) // BaseTimeEntity auditing(created_at/updated_at) 활성화 — 없으면 INSERT 시 NOT NULL 위반
class ReadingSessionRepositoryTest {

    private static final Instant T0 = Instant.parse("2026-06-01T09:00:00Z");

    @Autowired
    private ReadingSessionRepository sessionRepository;

    @Autowired
    private UserRepository userRepository;

    private User persistedUser(String email) {
        return userRepository.save(
                User.of(email, "$2a$10$abcdefghijklmnopqrstuv", "책벌레", "Asia/Seoul", Role.USER));
    }

    @Test
    @DisplayName("user로 세션들이 조회되고, 다른 user의 세션은 제외된다")
    void findByUser_returnsOnlyOwnSessions() {
        User u1 = persistedUser("u1@booktimer.com");
        User u2 = persistedUser("u2@booktimer.com");
        sessionRepository.save(ReadingSession.start(u1, T0));
        ReadingSession ended = ReadingSession.start(u1, T0.plusSeconds(100));
        ended.end(T0.plusSeconds(200));
        sessionRepository.save(ended);
        sessionRepository.save(ReadingSession.start(u2, T0)); // 다른 유저

        List<ReadingSession> u1Sessions = sessionRepository.findByUser(u1);

        assertThat(u1Sessions).hasSize(2);
        assertThat(u1Sessions).allMatch(s -> s.getUser().getId().equals(u1.getId()));
    }

    @Test
    @DisplayName("진행 중(미종료) 세션만 조회된다")
    void findActive_returnsUnendedOnly() {
        User user = persistedUser("active@booktimer.com");
        ReadingSession ended = ReadingSession.start(user, T0);
        ended.end(T0.plusSeconds(60));
        sessionRepository.save(ended);
        ReadingSession active = sessionRepository.save(ReadingSession.start(user, T0.plusSeconds(100)));

        Optional<ReadingSession> found = sessionRepository.findByUserAndEndedAtIsNull(user);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(active.getId());
        assertThat(found.get().isActive()).isTrue();
    }

    @Test
    @DisplayName("진행 중 세션이 없으면 Optional.empty")
    void findActive_noneActive_empty() {
        User user = persistedUser("done@booktimer.com");
        ReadingSession ended = ReadingSession.start(user, T0);
        ended.end(T0.plusSeconds(60));
        sessionRepository.save(ended);

        Optional<ReadingSession> found = sessionRepository.findByUserAndEndedAtIsNull(user);

        assertThat(found).isEmpty();
    }

    // --- findNudgeTargets: 재참여 넛지 대상 선정 (이메일 인프라 2단계 PR-2 — §3-1 5조건 AND) ---

    private static final Instant CUTOFF = Instant.parse("2026-06-11T00:00:00Z");
    private static final java.time.Clock CONSENT_CLK =
            java.time.Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), java.time.ZoneOffset.UTC);

    /** 동의·검증 상태와 마지막 활동/넛지 시각을 갖춘 사용자를 만들어 저장한다(넛지 대상 픽스처). */
    private User nudgeUser(String email, boolean consent, boolean verified,
                           Instant lastActivity, Instant lastNudge) {
        User u = User.of(email, "$2a$10$abcdefghijklmnopqrstuv", "책벌레", "Asia/Seoul", Role.USER);
        if (consent) {
            u.consentToMarketing(CONSENT_CLK);
        }
        if (verified) {
            u.verifyEmail();
        }
        if (lastNudge != null) {
            u.recordNudgeSent(lastNudge);
        }
        u = userRepository.save(u);
        if (lastActivity != null) {
            sessionRepository.save(ReadingSession.start(u, lastActivity)); // 마지막 활동 = startedAt
        }
        return u;
    }

    @Test
    @DisplayName("findNudgeTargets: 7일 경계 포함 — 정확히 cutoff에 마지막 활동한 동의·검증 사용자는 대상")
    void findNudgeTargets_inactiveAtCutoff_isIncluded() {
        User target = nudgeUser("target@booktimer.com", true, true, CUTOFF, null);

        List<User> targets = sessionRepository.findNudgeTargets(CUTOFF);

        assertThat(targets).extracting(User::getEmail).containsExactly("target@booktimer.com");
        assertThat(targets).extracting(User::getId).containsExactly(target.getId());
    }

    @Test
    @DisplayName("findNudgeTargets: cutoff 이후 활동(최근 활성)은 제외")
    void findNudgeTargets_activeAfterCutoff_excluded() {
        nudgeUser("recent@booktimer.com", true, true, CUTOFF.plusSeconds(1), null);

        assertThat(sessionRepository.findNudgeTargets(CUTOFF)).isEmpty();
    }

    @Test
    @DisplayName("findNudgeTargets: 한 번도 안 읽은 사용자는 제외 (재참여 아닌 온보딩 이탈 — null-state 누출 가드 N-055)")
    void findNudgeTargets_neverRead_excluded() {
        nudgeUser("neverread@booktimer.com", true, true, null, null); // 세션 없음

        assertThat(sessionRepository.findNudgeTargets(CUTOFF)).isEmpty();
    }

    @Test
    @DisplayName("findNudgeTargets: 미동의 사용자는 제외 (opt-in 게이트)")
    void findNudgeTargets_noConsent_excluded() {
        nudgeUser("noconsent@booktimer.com", false, true, CUTOFF.minusSeconds(86400), null);

        assertThat(sessionRepository.findNudgeTargets(CUTOFF)).isEmpty();
    }

    @Test
    @DisplayName("findNudgeTargets: 미검증 이메일은 제외 (반송·평판 보호 N-053)")
    void findNudgeTargets_unverified_excluded() {
        nudgeUser("unverified@booktimer.com", true, false, CUTOFF.minusSeconds(86400), null);

        assertThat(sessionRepository.findNudgeTargets(CUTOFF)).isEmpty();
    }

    @Test
    @DisplayName("findNudgeTargets: 이 비활동 구간에 이미 넛지를 보냈으면 제외 (lastNudge >= lastActivity — 1회 보장)")
    void findNudgeTargets_alreadyNudgedThisPeriod_excluded() {
        Instant lastActivity = CUTOFF.minusSeconds(86400);
        nudgeUser("alreadynudged@booktimer.com", true, true, lastActivity, lastActivity.plusSeconds(3600));

        assertThat(sessionRepository.findNudgeTargets(CUTOFF)).isEmpty();
    }

    @Test
    @DisplayName("findNudgeTargets: 재활동 후 재이탈은 대상 (lastNudge < lastActivity — '1회'는 구간당이지 영구 아님)")
    void findNudgeTargets_reInactiveAfterPriorNudge_included() {
        Instant lastActivity = CUTOFF.minusSeconds(86400);
        // 직전 구간에 넛지를 보냈고(과거), 그 뒤 다시 읽었다가(lastActivity) 또 이탈
        User u = nudgeUser("renudge@booktimer.com", true, true, lastActivity, lastActivity.minusSeconds(7 * 86400));

        assertThat(sessionRepository.findNudgeTargets(CUTOFF))
                .extracting(User::getId).containsExactly(u.getId());
    }
}
