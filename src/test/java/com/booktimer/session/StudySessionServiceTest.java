package com.booktimer.session;

import com.booktimer.book.StudyBook;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * StudySessionService 오케스트레이션 단위 테스트 (Mockito — DB/컨텍스트 무관).
 *
 * <p>독서와 <b>같은 불변식</b>(중복 start 거부 · 무세션 stop 거부 · 6h 클램프 · 방치 스윕)을 지키는지와,
 * 공부에만 있는 규칙 하나 — <b>독서 세션이 진행 중이면 공부 시작을 거부</b>한다 — 를 본다.
 * 두 원장이 같은 시간을 이중으로 세지 않게 하는 유일한 자리라 여기서 못 박는다.
 */
@ExtendWith(MockitoExtension.class)
class StudySessionServiceTest {

    private static final Instant T0 = Instant.parse("2026-09-01T09:00:00Z");

    @Mock
    private StudySessionRepository studyRepository;

    @Mock
    private ReadingSessionRepository readingRepository;

    @InjectMocks
    private StudySessionService service;

    private User user;
    private StudyBook book;
    private StudyBook other;

    @BeforeEach
    void setUp() {
        user = User.of("student@booktimer.com", "$2a$10$abcdefghijklmnopqrstuv", "공부벌레", "Asia/Seoul", Role.USER);
        book = StudyBook.register(user, "정보처리기사 실기", "저자", null, null, null, null);
        other = StudyBook.register(user, "토익 RC", "저자", null, null, null, null);
    }

    // --- start ---

    @Test
    @DisplayName("start: 진행 중 세션이 없으면 새 공부 세션을 저장한다")
    void start_savesNewSession() {
        when(studyRepository.findByUserAndEndedAtIsNull(user)).thenReturn(Optional.empty());
        when(readingRepository.findByUserAndEndedAtIsNull(user)).thenReturn(Optional.empty());
        when(studyRepository.save(any(StudySession.class))).thenAnswer(returnsFirstArg());

        StudySession started = service.start(user, T0, null);

        assertThat(started.getStartedAt()).isEqualTo(T0);
        assertThat(started.isActive()).isTrue();
        assertThat(started.getBook()).isNull();
        verify(studyRepository).save(any(StudySession.class));
    }

    @Test
    @DisplayName("start: 책을 주면 그 책으로 시작한다 — 시작 시 대상 선택")
    void start_withBook_savesWithBook() {
        when(studyRepository.findByUserAndEndedAtIsNull(user)).thenReturn(Optional.empty());
        when(readingRepository.findByUserAndEndedAtIsNull(user)).thenReturn(Optional.empty());
        when(studyRepository.save(any(StudySession.class))).thenAnswer(returnsFirstArg());

        StudySession started = service.start(user, T0, book);

        assertThat(started.getBook()).isSameAs(book);
    }

    @Test
    @DisplayName("start: 진행 중 공부 세션이 있으면 거부한다(중복 측정 금지)")
    void start_rejectsWhenStudyActive() {
        when(studyRepository.findByUserAndEndedAtIsNull(user))
                .thenReturn(Optional.of(StudySession.start(user, T0)));

        assertThatThrownBy(() -> service.start(user, T0.plusSeconds(60), null))
                .isInstanceOf(IllegalStateException.class);
        verify(studyRepository, never()).save(any(StudySession.class));
    }

    @Test
    @DisplayName("start: 진행 중 '독서' 세션이 있어도 거부한다 — 두 원장이 같은 시간을 이중으로 세지 않는다")
    void start_rejectsWhenReadingActive() {
        when(studyRepository.findByUserAndEndedAtIsNull(user)).thenReturn(Optional.empty());
        when(readingRepository.findByUserAndEndedAtIsNull(user))
                .thenReturn(Optional.of(ReadingSession.start(user, T0)));

        assertThatThrownBy(() -> service.start(user, T0.plusSeconds(60), null))
                .isInstanceOf(IllegalStateException.class);
        verify(studyRepository, never()).save(any(StudySession.class));
    }

    // --- stop ---

    @Test
    @DisplayName("stop: 진행 중 세션을 종료하고 경과를 초로 계산한다")
    void stop_endsActiveSession() {
        StudySession active = StudySession.start(user, T0);
        when(studyRepository.findByUserAndEndedAtIsNull(user)).thenReturn(Optional.of(active));
        when(studyRepository.save(active)).thenReturn(active);

        StudySession stopped = service.stop(user, T0.plusSeconds(1800));

        assertThat(stopped.getDurationSeconds()).isEqualTo(1800);
        assertThat(stopped.isActive()).isFalse();
    }

    @Test
    @DisplayName("stop: 진행 중 세션이 없으면 거부한다")
    void stop_rejectsWhenNoActiveSession() {
        when(studyRepository.findByUserAndEndedAtIsNull(user)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.stop(user, T0))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("stop: 6시간을 넘긴 경과는 cap으로 잘라 인정한다(독서와 같은 상한)")
    void stop_clampsToCap() {
        StudySession active = StudySession.start(user, T0);
        when(studyRepository.findByUserAndEndedAtIsNull(user)).thenReturn(Optional.of(active));
        when(studyRepository.save(active)).thenReturn(active);

        StudySession stopped = service.stop(user, T0.plus(Duration.ofHours(21)));

        assertThat(stopped.getDurationSeconds())
                .isEqualTo(ReadingSessionService.MAX_SESSION_DURATION.toSeconds());
    }

    // --- 방치 스윕 ---

    @Test
    @DisplayName("closeStaleSessions: 방치된 세션을 정확히 cap 길이로 닫는다")
    void closeStaleSessions_closesAtCap() {
        StudySession stale = StudySession.start(user, T0);
        when(studyRepository.findByEndedAtIsNullAndStartedAtBefore(any(Instant.class)))
                .thenReturn(List.of(stale));

        int closed = service.closeStaleSessions(T0.plus(Duration.ofHours(9)));

        assertThat(closed).isEqualTo(1);
        assertThat(stale.getDurationSeconds())
                .isEqualTo(ReadingSessionService.MAX_SESSION_DURATION.toSeconds());
    }

    // --- 종료 후 태깅 ---

    @Test
    @DisplayName("tagBook: 내 세션이면 책을 붙여 저장한다")
    void tagBook_attachesAndSaves() {
        StudySession ended = StudySession.start(user, T0);
        ended.end(T0.plusSeconds(600));
        when(studyRepository.findByIdAndUser(7L, user)).thenReturn(Optional.of(ended));
        when(studyRepository.save(ended)).thenReturn(ended);

        StudySession tagged = service.tagBook(user, 7L, book);

        assertThat(tagged.getBook()).isSameAs(book);
        verify(studyRepository).save(ended);
    }

    /**
     * 소유 경계 — 남의 세션 id로는 태깅이 성립하면 안 된다. {@code findByIdAndUser}가 아니라
     * {@code findById}로 찾는 구현이면 이 테스트만 빨개진다(그 구현은 남의 기록을 내 책에 붙인다).
     */
    @Test
    @DisplayName("tagBook: 내 세션이 아니면 거부한다(IDOR — 컨트롤러가 404로 마스킹)")
    void tagBook_rejectsForeignSession() {
        when(studyRepository.findByIdAndUser(99L, user)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.tagBook(user, 99L, book))
                .isInstanceOf(IllegalArgumentException.class);
        verify(studyRepository, never()).save(any(StudySession.class));
    }

    // --- 측정 중 교체 ---

    @Test
    @DisplayName("changeActiveBook: 진행 중 세션의 대상을 바꾼다(세션은 안 멈춘다)")
    void changeActiveBook_swapsTarget() {
        StudySession active = StudySession.start(user, T0, book);
        when(studyRepository.findByUserAndEndedAtIsNull(user)).thenReturn(Optional.of(active));
        when(studyRepository.save(active)).thenReturn(active);

        StudySession changed = service.changeActiveBook(user, other);

        assertThat(changed.getBook()).isSameAs(other);
        assertThat(changed.isActive()).isTrue();
    }

    /** null을 IAE로 막는 구현이면 「책 없이」로 되돌릴 길이 사라진다 — 그 구현을 여기서 잡는다. */
    @Test
    @DisplayName("changeActiveBook: null이면 「책 없이」로 되돌린다")
    void changeActiveBook_nullClearsBook() {
        StudySession active = StudySession.start(user, T0, book);
        when(studyRepository.findByUserAndEndedAtIsNull(user)).thenReturn(Optional.of(active));
        when(studyRepository.save(active)).thenReturn(active);

        assertThat(service.changeActiveBook(user, null).getBook()).isNull();
    }

    @Test
    @DisplayName("changeActiveBook: 진행 중 세션이 없으면 거부한다(컨트롤러가 409로 — stop과 같은 계약)")
    void changeActiveBook_rejectsWhenNoActiveSession() {
        when(studyRepository.findByUserAndEndedAtIsNull(user)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeActiveBook(user, book))
                .isInstanceOf(IllegalStateException.class);
        verify(studyRepository, never()).save(any(StudySession.class));
    }

    // --- 책별 집계 · 최근 책 ---

    @Test
    @DisplayName("totalSecondsByBook: 집계 행을 책 id → 초 맵으로 옮긴다(null 초는 0)")
    void totalSecondsByBook_mapsRows() {
        when(studyRepository.sumSecondsByBook(user))
                .thenReturn(List.of(new BookSecondsRow(11L, 1200L), new BookSecondsRow(12L, null)));

        assertThat(service.totalSecondsByBook(user)).containsOnly(entry(11L, 1200L), entry(12L, 0L));
    }

    @Test
    @DisplayName("recentBookId: 책이 붙은 가장 최근 세션의 책 id (없으면 null)")
    void recentBookId_readsLatestTaggedSession() {
        when(studyRepository.findFirstByUserAndBookIsNotNullOrderByStartedAtDesc(user))
                .thenReturn(Optional.empty());
        assertThat(service.recentBookId(user)).isNull();
    }

    // --- 당일 누적 ---

    @Test
    @DisplayName("todaySeconds: 유저 타임존의 오늘 경계로 완료 세션 합을 묻는다")
    void todaySeconds_usesUserTimezoneDayBoundary() {
        // 2026-09-01T09:00Z = KST 18:00 → 오늘은 09-01(KST), 경계는 08-31T15:00Z ~ 09-01T15:00Z
        when(studyRepository.sumCompletedSeconds(
                user,
                Instant.parse("2026-08-31T15:00:00Z"),
                Instant.parse("2026-09-01T15:00:00Z")))
                .thenReturn(1500L);

        assertThat(service.todaySeconds(user, T0)).isEqualTo(1500L);
    }

    // ==========================================================================
    // 자정 분할 — 종료 2경로 배선 (stop / closeStaleSessions)
    //
    // 순수 함수 splitByMidnight 자체의 경계 규칙(0초 조각 금지·DST·다중 경계)은
    // ReadingSessionServiceTest #1~#9가 이미 전수로 잡는다 — 같은 함수를 공유하므로
    // 여기서 다시 세지 않고, 공부 쪽 「배선이 실제로 걸렸나」만 계측한다.
    // ==========================================================================

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /**
     * 이 머신(KST)·서버 UTC 어느 쪽과도 다른 TZ. 분할 기준이 <b>유저</b> TZ임을 계측하려면 픽스처 TZ가
     * 시스템 기본값과 달라야 한다 — Asia/Seoul 유저만으로는 {@code ZoneId.systemDefault()}로 바꾼
     * 돌연변이가 이 머신에서 살아남는다(독서 쪽과 같은 사각 방지).
     */
    private static final ZoneId AUCKLAND = ZoneId.of("Pacific/Auckland");

    private static User aucklandUser() {
        return User.of("kiwi@booktimer.com", "$2a$10$abcdefghijklmnopqrstuv", "키위", "Pacific/Auckland", Role.USER);
    }

    /** 유저 TZ 로컬 시각 문자열("2026-06-01T23:50")을 Instant로 — 손으로 UTC를 환산하지 않는다. */
    private static Instant at(String localDateTime, ZoneId zone) {
        return LocalDateTime.parse(localDateTime).atZone(zone).toInstant();
    }

    private static Instant kst(String localDateTime) {
        return at(localDateTime, KST);
    }

    @Test
    @DisplayName("stop: 23:50 시작 → 익일 00:40 종료면 2행으로 저장하고 마지막 조각을 반환한다(경계는 유저 TZ 자정)")
    void stop_acrossMidnight_savesTwoRowsReturnsLast() {
        // 유저 TZ를 Auckland로 둔다 — 이 구간은 KST·UTC 기준으로는 같은 날 안이라(20:50~21:40 KST,
        // 11:50~12:40Z) 서버 TZ로 자르는 구현이면 1조각이 된다. 그래서 이 테스트가 곧 「유저 TZ로 자른다」의 계측기다.
        User kiwi = aucklandUser();
        Instant started = at("2026-06-01T23:50", AUCKLAND);
        Instant now = at("2026-06-02T00:40", AUCKLAND);
        Instant midnight = LocalDate.of(2026, 6, 2).atStartOfDay(AUCKLAND).toInstant();
        StudySession active = StudySession.start(kiwi, started);
        when(studyRepository.findByUserAndEndedAtIsNull(kiwi)).thenReturn(Optional.of(active));
        when(studyRepository.save(any(StudySession.class))).thenAnswer(returnsFirstArg());

        StudySession result = service.stop(kiwi, now);

        verify(studyRepository, times(2)).save(any(StudySession.class));
        // 기존 행이 첫 조각 — startedAt 은 그대로, endedAt 만 자정으로 확정된다.
        assertThat(active.getStartedAt()).isEqualTo(started);
        assertThat(active.getEndedAt()).isEqualTo(midnight);
        assertThat(active.getDurationSeconds()).isEqualTo(600L);
        // 반환은 now 가 속한 마지막 조각.
        assertThat(result).isNotSameAs(active);
        assertThat(result.getStartedAt()).isEqualTo(midnight);
        assertThat(result.getEndedAt()).isEqualTo(now);
        assertThat(result.getDurationSeconds()).isEqualTo(2400L);
        assertThat(result.getUser()).isSameAs(kiwi);
    }

    @Test
    @DisplayName("stop: 자정을 안 넘기면 지금처럼 1행만 저장한다(핫패스 회귀 방지)")
    void stop_withinOneDay_savesOnce() {
        Instant started = kst("2026-06-01T10:00");
        StudySession active = StudySession.start(user, started);
        when(studyRepository.findByUserAndEndedAtIsNull(user)).thenReturn(Optional.of(active));
        when(studyRepository.save(any(StudySession.class))).thenAnswer(returnsFirstArg());

        StudySession result = service.stop(user, started.plusSeconds(1800));

        verify(studyRepository, times(1)).save(any(StudySession.class));
        assertThat(result).isSameAs(active);
        assertThat(result.getDurationSeconds()).isEqualTo(1800L);
    }

    @Test
    @DisplayName("stop: 정확히 자정에 끝나면 1행이다(0초 조각 금지)")
    void stop_endsExactlyAtMidnight_savesOnce() {
        User kiwi = aucklandUser();
        Instant started = at("2026-06-01T23:00", AUCKLAND);
        Instant midnight = LocalDate.of(2026, 6, 2).atStartOfDay(AUCKLAND).toInstant();
        StudySession active = StudySession.start(kiwi, started);
        when(studyRepository.findByUserAndEndedAtIsNull(kiwi)).thenReturn(Optional.of(active));
        when(studyRepository.save(any(StudySession.class))).thenAnswer(returnsFirstArg());

        StudySession result = service.stop(kiwi, midnight);

        verify(studyRepository, times(1)).save(any(StudySession.class));
        assertThat(result).isSameAs(active);
        assertThat(result.getDurationSeconds()).isEqualTo(3600L);
    }

    @Test
    @DisplayName("stop: 클램프가 분할보다 먼저다 — 20:00 시작 + 9시간이면 6h cap 뒤 02:00까지만 두 조각")
    void stop_clampBeforeSplit() {
        Instant started = kst("2026-06-01T20:00");
        StudySession active = StudySession.start(user, started);
        when(studyRepository.findByUserAndEndedAtIsNull(user)).thenReturn(Optional.of(active));
        when(studyRepository.save(any(StudySession.class))).thenAnswer(returnsFirstArg());

        StudySession result = service.stop(user, started.plusSeconds(9 * 3600));

        verify(studyRepository, times(2)).save(any(StudySession.class));
        // 분할을 먼저 하고 조각마다 클램프하면 마지막 조각이 05:00까지 살아남는다 — 그 꼴이 아님을 단언.
        assertThat(result.getEndedAt()).isEqualTo(kst("2026-06-02T02:00"));
        assertThat(active.getDurationSeconds() + result.getDurationSeconds())
                .isEqualTo(ReadingSessionService.MAX_SESSION_DURATION.toSeconds());
    }

    @Test
    @DisplayName("closeStaleSessions: 22:00 방치 세션은 익일 04:00(cap)으로 닫히며 2조각이 되고 closed는 여전히 1이다")
    void closeStaleSessions_acrossMidnight_countsOriginalSessions() {
        Instant started = kst("2026-06-01T22:00");
        StudySession stale = StudySession.start(user, started);
        when(studyRepository.findByEndedAtIsNullAndStartedAtBefore(any(Instant.class)))
                .thenReturn(List.of(stale));
        when(studyRepository.save(any(StudySession.class))).thenAnswer(returnsFirstArg());

        int closed = service.closeStaleSessions(started.plusSeconds(21 * 3600));

        assertThat(closed).isEqualTo(1); // 조각 수가 아니라 원본 세션 수
        verify(studyRepository, times(2)).save(any(StudySession.class));
        assertThat(stale.getEndedAt()).isEqualTo(LocalDate.of(2026, 6, 2).atStartOfDay(KST).toInstant());
        assertThat(stale.getDurationSeconds()).isEqualTo(2 * 3600L);
    }
}
