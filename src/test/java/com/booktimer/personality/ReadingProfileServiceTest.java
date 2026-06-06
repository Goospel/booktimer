package com.booktimer.personality;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.session.ReadingSession;
import com.booktimer.session.ReadingSessionRepository;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 독서 프로필 서비스 통합 테스트 — 레포지토리 배선과 <b>사용자 범위(교차 누출 없음)</b>만 본다(책BTI Phase 2).
 *
 * <p>집계 규칙 자체는 {@link ReadingProfileAggregatorTest}(빠른 단위)가 전수로 본다. 여기선 무거운 컨텍스트로
 * "DB에서 그 사용자 것만 읽어 집계기에 넘기는가"를 확인한다 — 특히 남의 책/세션이 내 프로필에 새지 않아야 한다.
 */
@SpringBootTest
@Transactional
class ReadingProfileServiceTest {

    @Autowired
    private ReadingProfileService service;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BookRepository bookRepository;
    @Autowired
    private ReadingSessionRepository sessionRepository;

    private User newUser(String email) {
        return userRepository.save(User.of(email, "$2a$10$abcdefghijklmnopqrstuv", "독자", "Asia/Seoul", Role.USER));
    }

    @Test
    @DisplayName("프로필 집계: 사용자의 책장·세션을 읽어 권수·완독률·시간을 낸다")
    void profileOf_aggregatesUsersShelf() {
        User u = newUser("me@booktimer.com");
        bookRepository.save(Book.register(u, "책A", "김영하", null, null, null, null,
                "국내도서>소설/시/희곡>한국소설", "2020-03-15", BookStatus.FINISHED));
        bookRepository.save(Book.register(u, "책B", "김영하", null, null, null, null, null, null, BookStatus.READING));
        ReadingSession s = ReadingSession.start(u, Instant.parse("2026-06-07T00:00:00Z"));
        s.end(Instant.parse("2026-06-07T00:10:00Z")); // 600초
        sessionRepository.save(s);

        ReadingProfile p = service.profileOf(u);

        assertThat(p.totalBooks()).isEqualTo(2);
        assertThat(p.finishedBooks()).isEqualTo(1);
        assertThat(p.finishedRatio()).isEqualTo(0.5);
        assertThat(p.totalReadingSeconds()).isEqualTo(600);
        assertThat(p.topAuthors()).containsExactly(new LabeledCount("김영하", 2));
        assertThat(p.topGenres()).containsExactly(new LabeledCount("소설/시/희곡", 1));
    }

    @Test
    @DisplayName("사용자 범위: 다른 사용자의 책·세션은 내 프로필에 새지 않는다")
    void profileOf_scopedToUser_noCrossLeak() {
        User me = newUser("me2@booktimer.com");
        User other = newUser("other@booktimer.com");
        bookRepository.save(Book.register(me, "내책", null, null, null, null, null, null, null, BookStatus.FINISHED));
        // 남의 책 3권 + 남의 세션 — 내 프로필엔 안 잡혀야
        bookRepository.save(Book.register(other, "남책1", null, null, null, null, null, null, null, BookStatus.FINISHED));
        bookRepository.save(Book.register(other, "남책2", null, null, null, null, null, null, null, BookStatus.READING));
        ReadingSession os = ReadingSession.start(other, Instant.parse("2026-06-07T00:00:00Z"));
        os.end(Instant.parse("2026-06-07T01:00:00Z"));
        sessionRepository.save(os);

        ReadingProfile p = service.profileOf(me);

        assertThat(p.totalBooks()).isEqualTo(1); // 내 책 1권만
        assertThat(p.totalReadingSeconds()).isZero(); // 남의 세션 안 셈
    }
}
