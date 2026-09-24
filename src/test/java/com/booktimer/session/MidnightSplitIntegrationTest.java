package com.booktimer.session;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRegistrationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 자정 분할이 <b>DB를 한 번 왕복한 뒤에도</b> 의도대로 보이는지 — 실 스키마·실 쿼리 통합 테스트.
 *
 * <p>단위 테스트(Mockito)는 「서비스가 2행을 save 했다」까지만 본다. 여기선 저장된 두 행이
 * 실제로 <b>두 날짜에 나뉘어 집계되는지</b>(이 기능의 목적)와, 조각 링크로 쓰는
 * <b>시각 인접성 등치 비교</b>가 컬럼 왕복 후에도 성립하는지를 본다.
 */
@SpringBootTest
@Transactional
class MidnightSplitIntegrationTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Autowired ReadingSessionService sessionService;
    @Autowired ReadingSessionRepository sessionRepository;
    @Autowired ReadingHistoryService historyService;
    @Autowired UserRegistrationService registrationService;
    @Autowired BookRepository bookRepository;

    private static Instant kst(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(SEOUL).toInstant();
    }

    private static final Instant STARTED = kst("2026-06-01T23:50");
    private static final Instant ENDED = kst("2026-06-02T00:40");
    private static final Instant MIDNIGHT = LocalDate.of(2026, 6, 2).atStartOfDay(SEOUL).toInstant();

    private User register(String email, String nickname) {
        return registrationService.register(email, "rawpw1234", nickname, SEOUL.getId(), Role.USER,
                LocalDate.of(2026, 6, 1));
    }

    @Test
    @DisplayName("#20: 23:50~00:40 세션을 저장하면 기록이 두 날짜에 10분/40분으로 나뉜다")
    void acrossMidnightSession_splitsAcrossTwoDaysInHistory() {
        User user = register("split1@booktimer.com", "split1");
        Book book = bookRepository.save(
                Book.register(user, "클린 코드", null, null, null, null, null, BookStatus.READING));
        sessionService.start(user, STARTED, book);
        sessionService.stop(user, ENDED);

        List<DailyReadingRecord> history = historyService.dailyHistory(user);

        assertThat(history).extracting(DailyReadingRecord::date, DailyReadingRecord::totalSeconds)
                .containsExactly( // dailyHistory는 최신 일자 먼저
                        org.assertj.core.groups.Tuple.tuple(LocalDate.of(2026, 6, 2), 2400L),
                        org.assertj.core.groups.Tuple.tuple(LocalDate.of(2026, 6, 1), 600L));
    }

    @Test
    @DisplayName("#20: 각 날짜 창의 sumCompletedSeconds가 조각 길이와 정확히 맞는다(오늘 읽은 시간·목표 판정의 원천)")
    void acrossMidnightSession_sumCompletedSecondsPerDay() {
        User user = register("split2@booktimer.com", "split2");
        Book book = bookRepository.save(
                Book.register(user, "클린 코드", null, null, null, null, null, BookStatus.READING));
        sessionService.start(user, STARTED, book);
        sessionService.stop(user, ENDED);

        Instant day1Start = LocalDate.of(2026, 6, 1).atStartOfDay(SEOUL).toInstant();
        Instant day3Start = LocalDate.of(2026, 6, 3).atStartOfDay(SEOUL).toInstant();

        assertThat(sessionRepository.sumCompletedSeconds(user, day1Start, MIDNIGHT)).isEqualTo(600L);
        assertThat(sessionRepository.sumCompletedSeconds(user, MIDNIGHT, day3Start)).isEqualTo(2400L);
    }

    @Test
    @DisplayName("A1: 조각 경계 시각이 컬럼 왕복 후에도 등치라 체인 쿼리가 앞 조각을 정확히 1건 찾는다")
    void adjacentPieceLookup_survivesColumnRoundTrip() {
        User user = register("split3@booktimer.com", "split3");
        sessionService.start(user, STARTED, null); // 책 없이 측정 → 종료 후 태깅 대상
        sessionService.stop(user, ENDED);
        sessionRepository.flush();

        Optional<ReadingSession> earlier =
                sessionRepository.findByUserAndEndedAtAndBookIsNullAndManualEntryFalse(user, MIDNIGHT);

        assertThat(earlier).isPresent();
        assertThat(earlier.get().getStartedAt()).isEqualTo(STARTED);
        assertThat(earlier.get().getEndedAt()).isEqualTo(MIDNIGHT);
        // 체인의 끝 — 최초 조각의 startedAt으로 한 번 더 물으면 없어야 무한 순회가 안 난다.
        assertThat(sessionRepository.findByUserAndEndedAtAndBookIsNullAndManualEntryFalse(user, STARTED))
                .isEmpty();
    }

    @Test
    @DisplayName("A1: 종료 후 태깅이 마지막 조각 id 하나로 두 조각을 모두 책에 붙인다(실 DB 경로)")
    void tagBook_tagsBothPiecesThroughRealRepository() {
        User user = register("split4@booktimer.com", "split4");
        Book book = bookRepository.save(
                Book.register(user, "클린 코드", null, null, null, null, null, BookStatus.WANT_TO_READ));
        sessionService.start(user, STARTED, null);
        ReadingSession last = sessionService.stop(user, ENDED);

        sessionService.tagBook(user, last.getId(), book);
        sessionRepository.flush();

        assertThat(sessionRepository.findByUser(user))
                .hasSize(2)
                .allSatisfy(s -> assertThat(s.getBook().getId()).isEqualTo(book.getId()));
        assertThat(sessionRepository.sumDurationByUserAndBook(user, book)).isEqualTo(3000L);
        assertThat(book.getStartedReadingAt()).isEqualTo(STARTED); // 스탬프는 최초 조각 시각
    }

    // ── assignBook — 기록 화면 정정(양방향 체인, 실 DB 경로) ──────────────────

    private Book book(User user, String title) {
        return bookRepository.save(Book.register(user, title, null, null, null, null, null, BookStatus.READING));
    }

    /** 그 사용자의 세션 전부, 시작 시각 순. */
    private List<ReadingSession> rows(User user) {
        sessionRepository.flush();
        return sessionRepository.findByUser(user).stream()
                .sorted(java.util.Comparator.comparing(ReadingSession::getStartedAt))
                .toList();
    }

    private static Long bookIdOf(ReadingSession s) {
        return s.getBook() == null ? null : s.getBook().getId();
    }

    @Test
    @DisplayName("assignBook: 앞 조각(전날)을 고쳐도 뒤 조각까지 함께 바뀐다")
    void assignBook_earlierPiece_relabelsLaterPiece() {
        User user = register("assign1@booktimer.com", "assign1");
        Book book = book(user, "클린 코드");
        sessionService.start(user, STARTED, null);
        sessionService.stop(user, ENDED);
        List<ReadingSession> pieces = rows(user);

        sessionService.assignBook(user, pieces.get(0).getId(), book);

        assertThat(rows(user)).extracting(MidnightSplitIntegrationTest::bookIdOf)
                .containsExactly(book.getId(), book.getId());
    }

    @Test
    @DisplayName("assignBook: 뒤 조각을 고쳐도 앞 조각까지 함께 바뀐다 — 책→다른 책도 체인을 걷는다")
    void assignBook_laterPiece_relabelsEarlierPiece() {
        User user = register("assign2@booktimer.com", "assign2");
        Book first = book(user, "클린 코드");
        Book other = book(user, "리팩터링");
        sessionService.start(user, STARTED, first);
        ReadingSession last = sessionService.stop(user, ENDED);

        sessionService.assignBook(user, last.getId(), other);

        assertThat(rows(user)).extracting(MidnightSplitIntegrationTest::bookIdOf)
                .containsExactly(other.getId(), other.getId());
    }

    /** 「고치기 전과 같은 라벨」 조건의 계측기 — 이미 따로 고친 이웃 조각은 체인이 아니다. */
    @Test
    @DisplayName("assignBook: 자정 이웃 조각의 라벨이 고치기 전과 다르면(이미 따로 고침) 딸려오지 않는다")
    void assignBook_neighbourWithDifferentLabel_isNotDragged() {
        User user = register("assign10@booktimer.com", "assign10");
        Book separately = book(user, "따로 고친 책");
        Book picked = book(user, "새 책");
        sessionService.start(user, STARTED, null);
        ReadingSession last = sessionService.stop(user, ENDED);
        last.assignBook(separately); // 뒤 조각만 따로 라벨이 붙은 상태(엔티티 직접 — 픽스처)
        sessionRepository.save(last);
        ReadingSession first = rows(user).get(0);

        sessionService.assignBook(user, first.getId(), picked);

        assertThat(rows(user)).extracting(MidnightSplitIntegrationTest::bookIdOf)
                .containsExactly(picked.getId(), separately.getId());
    }

    /** 「고치기 전과 같은 라벨」 — 뒤로 걷기 쪽 계측기(위 테스트의 거울). */
    @Test
    @DisplayName("assignBook: 뒤 조각을 고칠 때 앞 조각 라벨이 고치기 전과 다르면(이미 따로 고침) 딸려오지 않는다")
    void assignBook_earlierNeighbourWithDifferentLabel_isNotDragged() {
        User user = register("assign11@booktimer.com", "assign11");
        Book separately = book(user, "따로 고친 책");
        Book picked = book(user, "새 책");
        sessionService.start(user, STARTED, null);
        ReadingSession last = sessionService.stop(user, ENDED);
        ReadingSession first = rows(user).get(0);
        first.assignBook(separately); // 앞 조각만 따로 라벨이 붙은 상태(엔티티 직접 — 픽스처)
        sessionRepository.save(first);

        sessionService.assignBook(user, last.getId(), picked);

        assertThat(rows(user)).extracting(MidnightSplitIntegrationTest::bookIdOf)
                .containsExactly(separately.getId(), picked.getId());
    }

    /*
     * 다른 사용자 격리 — 방향마다 붙이기를 한 번만 한다. 한 테스트에서 둘 다 붙이면 둘째 호출이 첫 호출에
     * 끌려온 행을 제 책으로 다시 덮어써, finder 하나의 user 조건이 빠져도 초록이 된다.
     */
    @Test
    @DisplayName("assignBook: 뒤로 걷기 — 자정 직전에 끝난 다른 사용자의 같은 라벨(null) 세션은 딸려오지 않는다")
    void assignBook_backwardWalk_doesNotDragOtherUsersSession() {
        User alice = register("assign3a@booktimer.com", "assign3a");
        User bob = register("assign3b@booktimer.com", "assign3b");
        Book bobBook = book(bob, "밥책");
        sessionService.start(alice, kst("2026-06-01T23:30"), null);
        sessionService.stop(alice, MIDNIGHT);                                      // [23:30, 자정]
        sessionService.start(bob, MIDNIGHT, null);
        ReadingSession bobRow = sessionService.stop(bob, kst("2026-06-02T00:30")); // [자정, 00:30]

        sessionService.assignBook(bob, bobRow.getId(), bobBook);

        assertThat(rows(alice)).extracting(MidnightSplitIntegrationTest::bookIdOf).containsExactly((Long) null);
        assertThat(rows(bob)).extracting(MidnightSplitIntegrationTest::bookIdOf).containsExactly(bobBook.getId());
    }

    @Test
    @DisplayName("assignBook: 앞으로 걷기 — 자정에 시작한 다른 사용자의 같은 라벨(null) 세션은 딸려오지 않는다")
    void assignBook_forwardWalk_doesNotDragOtherUsersSession() {
        User alice = register("assign12a@booktimer.com", "assign12a");
        User bob = register("assign12b@booktimer.com", "assign12b");
        Book aliceBook = book(alice, "앨리스책");
        sessionService.start(alice, kst("2026-06-01T23:30"), null);
        ReadingSession aliceRow = sessionService.stop(alice, MIDNIGHT);            // [23:30, 자정]
        sessionService.start(bob, MIDNIGHT, null);
        sessionService.stop(bob, kst("2026-06-02T00:30"));                         // [자정, 00:30]

        sessionService.assignBook(alice, aliceRow.getId(), aliceBook);

        assertThat(rows(alice)).extracting(MidnightSplitIntegrationTest::bookIdOf).containsExactly(aliceBook.getId());
        assertThat(rows(bob)).extracting(MidnightSplitIntegrationTest::bookIdOf).containsExactly((Long) null);
    }

    @Test
    @DisplayName("assignBook: 과거 날짜 수동 기록(00:00 시작)을 바꿔도 자정을 넘긴 다른 수동 조각은 그대로다")
    void assignBook_manualEntries_areNotChained() {
        User user = register("assign4@booktimer.com", "assign4");
        Book demian = book(user, "데미안");
        Book other = book(user, "싯다르타");
        // 9/23 01:00에 「오늘 2시간 데미안」 → P1 9/22 23:00–00:00, P2 9/23 00:00–01:00
        sessionService.recordManual(user, kst("2026-09-22T23:00"), kst("2026-09-23T01:00"), demian);
        // 「9/23에 30분 데미안」(과거 날짜 앵커 00:00) → P3 9/23 00:00–00:30
        sessionService.recordManual(user, kst("2026-09-23T00:00"), kst("2026-09-23T00:30"), demian);
        List<ReadingSession> before = rows(user);
        ReadingSession p1 = before.get(0);
        ReadingSession p3 = before.stream()
                .filter(s -> s.getEndedAt().equals(kst("2026-09-23T00:30"))).findFirst().orElseThrow();

        sessionService.assignBook(user, p3.getId(), other); // 뒤로 걷기였다면 P1이 바뀐다
        sessionService.assignBook(user, p1.getId(), other); // 앞으로 걷기였다면 P2·P3 중 아무거나 바뀐다

        List<ReadingSession> after = rows(user);
        assertThat(after).filteredOn(s -> s.getId().equals(p1.getId()) || s.getId().equals(p3.getId()))
                .allSatisfy(s -> assertThat(bookIdOf(s)).isEqualTo(other.getId()));
        assertThat(after).filteredOn(s -> s.getEndedAt().equals(kst("2026-09-23T01:00")))  // P2
                .singleElement().satisfies(s -> assertThat(bookIdOf(s)).isEqualTo(demian.getId()));
    }

    @Test
    @DisplayName("assignBook: 실측 조각과 자정 인접한 수동 기록은 딸려오지 않는다(앞·뒤 양방향)")
    void assignBook_realtimePieceAdjacentToManual_doesNotDragManual() {
        User user = register("assign5@booktimer.com", "assign5");
        Book demian = book(user, "데미안");
        Book other = book(user, "싯다르타");
        // 앞으로: 실측 [6/1 23:30, 자정] + 수동 [자정, 00:30]
        sessionService.start(user, kst("2026-06-01T23:30"), demian);
        ReadingSession realtimeBefore = sessionService.stop(user, MIDNIGHT);
        sessionService.recordManual(user, MIDNIGHT, kst("2026-06-02T00:30"), demian);
        // 뒤로: 수동 [6/4 23:00, 자정] + 실측 [자정, 00:30]
        Instant midnight5 = LocalDate.of(2026, 6, 5).atStartOfDay(SEOUL).toInstant();
        sessionService.recordManual(user, kst("2026-06-04T23:00"), midnight5, demian);
        sessionService.start(user, midnight5, demian);
        ReadingSession realtimeAfter = sessionService.stop(user, kst("2026-06-05T00:30"));

        sessionService.assignBook(user, realtimeBefore.getId(), other);
        sessionService.assignBook(user, realtimeAfter.getId(), other);

        assertThat(rows(user)).filteredOn(ReadingSession::isManualEntry).hasSize(2)
                .allSatisfy(s -> assertThat(bookIdOf(s)).isEqualTo(demian.getId()));
        assertThat(rows(user)).filteredOn(s -> !s.isManualEntry()).hasSize(2)
                .allSatisfy(s -> assertThat(bookIdOf(s)).isEqualTo(other.getId()));
    }

    @Test
    @DisplayName("assignBook: 같은 시각(자정 아님)에 시작·종료한 0초 실측 세션 둘은 서로 딸려오지 않는다(고정 클락 시나리오)")
    void assignBook_zeroSecondSessionsAtSameInstant_areNotChained() {
        User user = register("assign6@booktimer.com", "assign6");
        Book book = book(user, "클린 코드");
        Instant t = kst("2026-06-01T18:00");
        sessionService.start(user, t, null);
        sessionService.stop(user, t);
        sessionService.start(user, t, null);
        ReadingSession second = sessionService.stop(user, t);

        sessionService.assignBook(user, second.getId(), book);

        assertThat(rows(user)).extracting(MidnightSplitIntegrationTest::bookIdOf)
                .containsExactlyInAnyOrder(book.getId(), null);
    }

    @Test
    @DisplayName("assignBook: 같은 자정에 시작한 진행 중 세션은 딸려오지 않는다(EndedAtIsNotNull)")
    void assignBook_activeSessionAtSameMidnight_isNotDragged() {
        User user = register("assign7@booktimer.com", "assign7");
        Book book = book(user, "클린 코드");
        sessionService.start(user, kst("2026-06-01T23:30"), null);
        ReadingSession ended = sessionService.stop(user, MIDNIGHT);
        ReadingSession active = sessionService.start(user, MIDNIGHT, null);

        sessionService.assignBook(user, ended.getId(), book);

        assertThat(bookIdOf(sessionRepository.findById(ended.getId()).orElseThrow())).isEqualTo(book.getId());
        assertThat(sessionRepository.findById(active.getId()).orElseThrow().getBook()).isNull();
    }

    @Test
    @DisplayName("assignBook: 책 삭제로 풀린 수동 기록(book=null, manual=true)에 책을 다시 붙일 수 있다(P19)")
    void assignBook_manualEntryUnlinkedByBookDeletion_canBeReattached() {
        User user = register("assign8@booktimer.com", "assign8");
        Book gone = book(user, "지울 책");
        Book other = book(user, "새 책");
        ReadingSession manual = sessionService.recordManual(
                user, kst("2026-06-01T10:00"), kst("2026-06-01T10:30"), gone);
        sessionRepository.unlinkBook(gone); // 서재에서 책 삭제 경로 — 수동 기록도 미태깅이 된다
        assertThat(sessionRepository.findById(manual.getId()).orElseThrow().getBook()).isNull();

        sessionService.assignBook(user, manual.getId(), other);

        ReadingSession reloaded = sessionRepository.findById(manual.getId()).orElseThrow();
        assertThat(reloaded.isManualEntry()).isTrue();
        assertThat(bookIdOf(reloaded)).isEqualTo(other.getId());
    }

    @Test
    @DisplayName("monthlyHistory: 자정을 넘긴 두 조각의 start/end가 \"23:30\"–\"00:00\" / \"00:00\"–\"00:45\"")
    void monthlyHistory_splitPieces_carryUserTimezoneClock() {
        User user = register("assign9@booktimer.com", "assign9");
        sessionService.start(user, kst("2026-06-01T23:30"), null);
        sessionService.stop(user, kst("2026-06-02T00:45"));
        sessionRepository.flush();

        List<DailyReadingRecord> days = historyService.monthlyHistory(user, d -> 0L).get(0).days();

        assertThat(days).extracting(DailyReadingRecord::date)
                .containsExactly(LocalDate.of(2026, 6, 2), LocalDate.of(2026, 6, 1));
        assertThat(days.get(0).sessions()).singleElement()
                .satisfies(r -> assertThat(List.of(r.start(), r.end())).containsExactly("00:00", "00:45"));
        assertThat(days.get(1).sessions()).singleElement()
                .satisfies(r -> assertThat(List.of(r.start(), r.end())).containsExactly("23:30", "00:00"));
    }
}
