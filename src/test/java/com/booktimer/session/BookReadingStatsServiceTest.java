package com.booktimer.session;

import com.booktimer.book.Book;
import com.booktimer.book.BookStatus;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * BookReadingStatsService 단위 테스트 (Mockito — DB/컨텍스트 무관).
 *
 * <p>책별 누적 시간을 집계하되, 프로필(SNS 2단계)용 공개 집계는 <b>PUBLIC 책만</b> 센다(sns-design §3.5).
 * 책 id가 필요한 검증이라 영속성 없이 리플렉션으로 id를 심는다(엔티티 setter 없음).
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

    /** 영속성 없이 Book.id를 심는다(JPA가 채우는 PK를 단위테스트에서 흉내). */
    private Book bookWithId(User owner, long id, boolean isPublic) {
        Book b = Book.register(owner, "책-" + id, null, null, null, null, null, BookStatus.READING);
        if (isPublic) {
            b.makePublic();
        }
        try {
            Field f = Book.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(b, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return b;
    }

    private ReadingSession sessionWithBook(User user, Instant start, long durationSeconds, Book book) {
        ReadingSession s = ReadingSession.start(user, start, book);
        s.end(start.plusSeconds(durationSeconds));
        return s;
    }

    @Test
    @DisplayName("publicTotalSecondsByBook: PUBLIC 책만 집계하고 PRIVATE 책·책 미지정 세션은 제외한다")
    void publicTotalSecondsByBook_onlyPublicBooks() {
        User u = user();
        Book pub = bookWithId(u, 1L, true);
        Book priv = bookWithId(u, 2L, false);
        Instant t = Instant.parse("2026-06-01T01:00:00Z");
        when(sessionRepository.findByUser(u)).thenReturn(List.of(
                sessionWithBook(u, t, HOUR, pub),     // 포함
                sessionWithBook(u, t, HOUR, priv),    // 제외 (PRIVATE)
                ReadingSession.start(u, t)));          // 제외 (book=null, 진행 중)

        Map<Long, Long> totals = service.publicTotalSecondsByBook(u);

        assertThat(totals).containsOnlyKeys(1L);
        assertThat(totals.get(1L)).isEqualTo(HOUR);
    }
}
