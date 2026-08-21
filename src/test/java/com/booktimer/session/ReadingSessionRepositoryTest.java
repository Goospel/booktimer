package com.booktimer.session;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.config.JpaConfig;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.hibernate.Hibernate;
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

    @Autowired
    private BookRepository bookRepository;

    private User persistedUser(String email) {
        return userRepository.save(
                User.of(email, "$2a$10$abcdefghijklmnopqrstuv", "책벌레", "Asia/Seoul", Role.USER));
    }

    private Book persistedBook(User owner, boolean isPublic) {
        Book b = Book.register(owner, "테스트책", null, null, null, null, null, BookStatus.READING);
        if (isPublic) b.makePublic();
        return bookRepository.save(b);
    }

    // --- findByUserWithBook: LEFT join fetch — N+1 제거 + null-book 세션 보존 ---

    @Test
    @DisplayName("findByUserWithBook: book=null 세션도 보존한다 (LEFT join — INNER면 null-book 세션 누락)")
    void findByUserWithBook_keepsNullBookSessions() {
        User u = persistedUser("wbook1@booktimer.com");
        Book b = persistedBook(u, false);
        sessionRepository.save(ReadingSession.start(u, T0, b));
        sessionRepository.save(ReadingSession.start(u, T0.plusSeconds(100))); // book=null

        List<ReadingSession> result = sessionRepository.findByUserWithBook(u);

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("findByUserWithBook: 반환된 book이 즉시 초기화된다 (N+1 없음)")
    void findByUserWithBook_initializesBook() {
        User u = persistedUser("wbook2@booktimer.com");
        Book b = persistedBook(u, false);
        sessionRepository.save(ReadingSession.start(u, T0, b));

        List<ReadingSession> result = sessionRepository.findByUserWithBook(u);

        assertThat(result).hasSize(1);
        assertThat(Hibernate.isInitialized(result.get(0).getBook())).isTrue();
    }

    // --- sumSecondsByBook: 완료·책지정 세션만 DB GROUP BY 집계 ---

    @Test
    @DisplayName("sumSecondsByBook: 세션이 없으면 빈 리스트")
    void sumSecondsByBook_empty() {
        User u = persistedUser("sum1@booktimer.com");

        assertThat(sessionRepository.sumSecondsByBook(u)).isEmpty();
    }

    @Test
    @DisplayName("sumSecondsByBook: 같은 책 완료 2건의 시간을 합산한다")
    void sumSecondsByBook_sumsCompletedSessions() {
        User u = persistedUser("sum2@booktimer.com");
        Book b = persistedBook(u, false);
        ReadingSession s1 = ReadingSession.start(u, T0, b);
        s1.end(T0.plusSeconds(1800));
        ReadingSession s2 = ReadingSession.start(u, T0.plusSeconds(3600), b);
        s2.end(T0.plusSeconds(3600 + 3600));
        sessionRepository.save(s1);
        sessionRepository.save(s2);

        List<BookSecondsRow> rows = sessionRepository.sumSecondsByBook(u);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).bookId()).isEqualTo(b.getId());
        assertThat(rows.get(0).seconds()).isEqualTo(5400L);
    }

    @Test
    @DisplayName("sumSecondsByBook: 진행 중(endedAt=null) 세션은 제외된다")
    void sumSecondsByBook_excludesActiveSession() {
        User u = persistedUser("sum3@booktimer.com");
        Book b = persistedBook(u, false);
        sessionRepository.save(ReadingSession.start(u, T0, b)); // 미종료

        assertThat(sessionRepository.sumSecondsByBook(u)).isEmpty();
    }

    @Test
    @DisplayName("sumSecondsByBook: book=null 세션은 제외된다")
    void sumSecondsByBook_excludesNullBookSession() {
        User u = persistedUser("sum4@booktimer.com");
        ReadingSession s = ReadingSession.start(u, T0); // book=null
        s.end(T0.plusSeconds(3600));
        sessionRepository.save(s);

        assertThat(sessionRepository.sumSecondsByBook(u)).isEmpty();
    }

    @Test
    @DisplayName("sumSecondsByBook: PUBLIC·PRIVATE 책 세션 모두 포함한다 (가시성 무관)")
    void sumSecondsByBook_includesPublicAndPrivate() {
        User u = persistedUser("sum5@booktimer.com");
        Book pub = persistedBook(u, true);
        Book priv = persistedBook(u, false);
        ReadingSession s1 = ReadingSession.start(u, T0, pub);
        s1.end(T0.plusSeconds(3600));
        ReadingSession s2 = ReadingSession.start(u, T0.plusSeconds(7200), priv);
        s2.end(T0.plusSeconds(7200 + 1800));
        sessionRepository.save(s1);
        sessionRepository.save(s2);

        List<BookSecondsRow> rows = sessionRepository.sumSecondsByBook(u);

        assertThat(rows).hasSize(2);
        assertThat(rows.stream().mapToLong(BookSecondsRow::seconds).sum()).isEqualTo(5400L);
    }

    // --- sumSecondsByPublicBook: PUBLIC 책 세션만 (비공개 시간 누출 방지 sns-design §3.5) ---

    @Test
    @DisplayName("sumSecondsByPublicBook: PRIVATE 책 세션은 제외하고 PUBLIC만 포함한다")
    void sumSecondsByPublicBook_onlyPublic() {
        User u = persistedUser("pub1@booktimer.com");
        Book pub = persistedBook(u, true);
        Book priv = persistedBook(u, false);
        ReadingSession s1 = ReadingSession.start(u, T0, pub);
        s1.end(T0.plusSeconds(3600));
        ReadingSession s2 = ReadingSession.start(u, T0.plusSeconds(7200), priv);
        s2.end(T0.plusSeconds(7200 + 1800));
        sessionRepository.save(s1);
        sessionRepository.save(s2);

        List<BookSecondsRow> rows = sessionRepository.sumSecondsByPublicBook(u);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).bookId()).isEqualTo(pub.getId());
        assertThat(rows.get(0).seconds()).isEqualTo(3600L);
    }

    @Test
    @DisplayName("sumSecondsByPublicBook: book=null 세션은 제외된다")
    void sumSecondsByPublicBook_excludesNullBook() {
        User u = persistedUser("pub2@booktimer.com");
        ReadingSession s = ReadingSession.start(u, T0);
        s.end(T0.plusSeconds(3600));
        sessionRepository.save(s);

        assertThat(sessionRepository.sumSecondsByPublicBook(u)).isEmpty();
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

    // --- findByIdAndUser: 종료 후 태깅의 IDOR-안전 세션 조회 ---

    @Test
    @DisplayName("findByIdAndUser: 그 사용자의 세션이면 반환한다")
    void findByIdAndUser_ownSession_returns() {
        User u = persistedUser("fbiau1@booktimer.com");
        ReadingSession s = sessionRepository.save(ReadingSession.start(u, T0));

        assertThat(sessionRepository.findByIdAndUser(s.getId(), u)).isPresent();
    }

    @Test
    @DisplayName("findByIdAndUser: 다른 사용자의 세션이면 빈 결과 (IDOR 경계 — 남의 세션 태깅 차단)")
    void findByIdAndUser_othersSession_empty() {
        User owner = persistedUser("fbiau2@booktimer.com");
        User other = persistedUser("fbiau3@booktimer.com");
        ReadingSession s = sessionRepository.save(ReadingSession.start(owner, T0));

        assertThat(sessionRepository.findByIdAndUser(s.getId(), other)).isEmpty();
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

    // --- findTossNudgeTargets: 토스 푸시 재참여 넛지 대상 (이메일 동의·검증 대신 토스 연동 조건) ---

    /** 토스 연동 여부·이메일 동의/검증 상태·마지막 활동/넛지 시각을 갖춘 사용자를 저장한다(토스 넛지 픽스처). */
    private User tossNudgeUser(String email, String tossUserKey, boolean consent, boolean verified,
                               Instant lastActivity, Instant lastNudge) {
        User u = User.of(email, "$2a$10$abcdefghijklmnopqrstuv", "책벌레", "Asia/Seoul", Role.USER);
        if (consent) {
            u.consentToMarketing(CONSENT_CLK);
        }
        if (verified) {
            u.verifyEmail();
        }
        if (tossUserKey != null) {
            u.linkTossUserKey(tossUserKey);
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
    @DisplayName("findTossNudgeTargets: 7일 경계 포함 — 정확히 cutoff에 마지막 활동한 토스 연동 사용자는 대상")
    void findTossNudgeTargets_inactiveAtCutoff_isIncluded() {
        User target = tossNudgeUser("tosstarget@booktimer.com", "tkey-target", true, true, CUTOFF, null);

        assertThat(sessionRepository.findTossNudgeTargets(CUTOFF))
                .extracting(User::getId).containsExactly(target.getId());
    }

    @Test
    @DisplayName("findTossNudgeTargets: cutoff 이후 활동(최근 활성)은 제외 — 활성 사용자에게 스팸 금지")
    void findTossNudgeTargets_activeAfterCutoff_excluded() {
        tossNudgeUser("tossrecent@booktimer.com", "tkey-recent", true, true, CUTOFF.plusSeconds(1), null);

        assertThat(sessionRepository.findTossNudgeTargets(CUTOFF)).isEmpty();
    }

    @Test
    @DisplayName("findTossNudgeTargets: 한 번도 안 읽은 토스 사용자는 제외 (온보딩 이탈 — null-state 누출 가드 N-055)")
    void findTossNudgeTargets_neverRead_excluded() {
        tossNudgeUser("tossneverread@booktimer.com", "tkey-never", true, true, null, null); // 세션 없음

        assertThat(sessionRepository.findTossNudgeTargets(CUTOFF)).isEmpty();
    }

    @Test
    @DisplayName("findTossNudgeTargets: 토스 미연동(웹 전용) 사용자는 제외 — 발송할 채널이 없다")
    void findTossNudgeTargets_noTossUserKey_excluded() {
        tossNudgeUser("tossweb@booktimer.com", null, true, true, CUTOFF.minusSeconds(86400), null);

        assertThat(sessionRepository.findTossNudgeTargets(CUTOFF)).isEmpty();
    }

    @Test
    @DisplayName("findTossNudgeTargets: 이 비활동 구간에 이미 넛지를 보냈으면 제외 (구간당 1회 멱등)")
    void findTossNudgeTargets_alreadyNudgedThisPeriod_excluded() {
        Instant lastActivity = CUTOFF.minusSeconds(86400);
        tossNudgeUser("tossnudged@booktimer.com", "tkey-nudged", true, true,
                lastActivity, lastActivity.plusSeconds(3600));

        assertThat(sessionRepository.findTossNudgeTargets(CUTOFF)).isEmpty();
    }

    @Test
    @DisplayName("findTossNudgeTargets: 이메일 미동의·미검증이어도 토스 연동이면 대상 (이메일 조건 복사 금지 — 대상 0명 침묵 실패)")
    void findTossNudgeTargets_emailUnconsentedAndUnverified_stillIncluded() {
        User target = tossNudgeUser("tossnoemail@booktimer.com", "tkey-noemail", false, false,
                CUTOFF.minusSeconds(86400), null);

        assertThat(sessionRepository.findTossNudgeTargets(CUTOFF))
                .extracting(User::getId).containsExactly(target.getId());
    }

    // --- findByEndedAtIsNull / sumCompletedSeconds: 목표 달성 푸시 감지의 두 재료 ---

    @Test
    @DisplayName("findByEndedAtIsNull: 진행 중 세션만 반환한다 (완료 세션 제외)")
    void findByEndedAtIsNull_activeOnly() {
        User u = persistedUser("active1@booktimer.com");
        ReadingSession done = ReadingSession.start(u, T0);
        done.end(T0.plusSeconds(600));
        sessionRepository.save(done);
        ReadingSession active = sessionRepository.save(ReadingSession.start(u, T0.plusSeconds(1000)));

        assertThat(sessionRepository.findByEndedAtIsNull())
                .extracting(ReadingSession::getId).containsExactly(active.getId());
    }

    @Test
    @DisplayName("findByEndedAtIsNull: 여러 사용자의 진행 중 세션을 모두 반환한다 (배치 스캔)")
    void findByEndedAtIsNull_acrossUsers() {
        User a = persistedUser("active2@booktimer.com");
        User b = persistedUser("active3@booktimer.com");
        sessionRepository.save(ReadingSession.start(a, T0));
        sessionRepository.save(ReadingSession.start(b, T0));

        assertThat(sessionRepository.findByEndedAtIsNull()).hasSize(2);
    }

    @Test
    @DisplayName("sumCompletedSeconds: 세션이 없으면 0 (null 아님)")
    void sumCompletedSeconds_emptyIsZero() {
        User u = persistedUser("csum1@booktimer.com");

        assertThat(sessionRepository.sumCompletedSeconds(u, T0, T0.plusSeconds(86400))).isZero();
    }

    @Test
    @DisplayName("sumCompletedSeconds: 범위 안 완료 세션만 합산한다 (범위 밖·진행 중 제외)")
    void sumCompletedSeconds_sumsOnlyCompletedInRange() {
        User u = persistedUser("csum2@booktimer.com");
        Instant from = T0;
        Instant to = T0.plusSeconds(86400);

        ReadingSession before = ReadingSession.start(u, from.minusSeconds(1)); // 범위 직전
        before.end(from.plusSeconds(600));
        sessionRepository.save(before);

        ReadingSession in1 = ReadingSession.start(u, from.plusSeconds(10));
        in1.end(from.plusSeconds(10 + 600));
        sessionRepository.save(in1);

        ReadingSession in2 = ReadingSession.start(u, from.plusSeconds(3600));
        in2.end(from.plusSeconds(3600 + 900));
        sessionRepository.save(in2);

        sessionRepository.save(ReadingSession.start(u, from.plusSeconds(7200))); // 진행 중

        assertThat(sessionRepository.sumCompletedSeconds(u, from, to)).isEqualTo(1500L);
    }

    @Test
    @DisplayName("sumCompletedSeconds: from은 포함 경계, to는 제외 경계 (자정 경계 이중계상 방지)")
    void sumCompletedSeconds_boundaries() {
        User u = persistedUser("csum3@booktimer.com");
        Instant from = T0;
        Instant to = T0.plusSeconds(86400);

        ReadingSession atFrom = ReadingSession.start(u, from);          // 포함
        atFrom.end(from.plusSeconds(100));
        sessionRepository.save(atFrom);

        ReadingSession atTo = ReadingSession.start(u, to);              // 제외(다음 날 시작)
        atTo.end(to.plusSeconds(100));
        sessionRepository.save(atTo);

        assertThat(sessionRepository.sumCompletedSeconds(u, from, to)).isEqualTo(100L);
    }

    @Test
    @DisplayName("sumCompletedSeconds: 남의 세션은 합산하지 않는다")
    void sumCompletedSeconds_otherUserExcluded() {
        User me = persistedUser("csum4@booktimer.com");
        User other = persistedUser("csum5@booktimer.com");
        ReadingSession s = ReadingSession.start(other, T0);
        s.end(T0.plusSeconds(600));
        sessionRepository.save(s);

        assertThat(sessionRepository.sumCompletedSeconds(me, T0, T0.plusSeconds(86400))).isZero();
    }

}
