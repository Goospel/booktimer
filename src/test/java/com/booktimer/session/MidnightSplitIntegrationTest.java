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
}
