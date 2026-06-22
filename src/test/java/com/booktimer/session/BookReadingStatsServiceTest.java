package com.booktimer.session;

import com.booktimer.user.Role;
import com.booktimer.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * BookReadingStatsService 단위 테스트 (Mockito — DB/컨텍스트 무관).
 *
 * <p>서비스는 DB GROUP BY 집계({@link ReadingSessionRepository#sumSecondsByBook} /
 * {@link ReadingSessionRepository#sumSecondsByPublicBook})를 위임받아 {@code Map<Long,Long>}으로
 * 변환만 한다. 필터 진실(완료·책지정·PUBLIC 조건)은 {@link ReadingSessionRepositoryTest}(@DataJpaTest)가 담당.
 */
@ExtendWith(MockitoExtension.class)
class BookReadingStatsServiceTest {

    private static final long HOUR = 3600L;

    @Mock
    private ReadingSessionRepository sessionRepository;

    @InjectMocks
    private BookReadingStatsService service;

    private User user() {
        return User.of("reader@booktimer.com", "$2a$10$abcdefghijklmnopqrstuv", "책벌레", "Asia/Seoul", Role.USER);
    }

    @Test
    @DisplayName("totalSecondsByBook: sumSecondsByBook 결과를 bookId→seconds 맵으로 변환한다")
    void totalSecondsByBook_mapsRows() {
        User u = user();
        when(sessionRepository.sumSecondsByBook(u))
                .thenReturn(List.of(new BookSecondsRow(1L, HOUR), new BookSecondsRow(2L, 1800L)));

        Map<Long, Long> totals = service.totalSecondsByBook(u);

        assertThat(totals).containsEntry(1L, HOUR).containsEntry(2L, 1800L);
    }

    @Test
    @DisplayName("totalSecondsByBook: seconds=null인 row는 0으로 폴백한다")
    void totalSecondsByBook_nullSecondsFallbackZero() {
        User u = user();
        when(sessionRepository.sumSecondsByBook(u))
                .thenReturn(List.of(new BookSecondsRow(1L, null)));

        Map<Long, Long> totals = service.totalSecondsByBook(u);

        assertThat(totals).containsEntry(1L, 0L);
    }

    @Test
    @DisplayName("publicTotalSecondsByBook: sumSecondsByPublicBook 결과를 맵으로 변환하고 PUBLIC 아닌 책은 포함되지 않는다")
    void publicTotalSecondsByBook_mapsPublicOnlyRows() {
        User u = user();
        // 필터는 repo 테스트가 담당 — 여기선 서비스가 rows→Map 매핑을 올바르게 하는지만 검증
        when(sessionRepository.sumSecondsByPublicBook(u))
                .thenReturn(List.of(new BookSecondsRow(1L, HOUR)));

        Map<Long, Long> totals = service.publicTotalSecondsByBook(u);

        assertThat(totals).containsOnlyKeys(1L);
        assertThat(totals.get(1L)).isEqualTo(HOUR);
    }
}
