package com.booktimer.book;

import com.booktimer.config.JpaConfig;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * BookRepository 슬라이스 테스트 (@DataJpaTest, H2).
 *
 * <p>{@link BookRepository#countPublicByUsers}의 배치 집계를 검증한다 — 사용자 행 목록 조립의
 * 행당 {@code countByUserAndVisibility} N+1을 단일 group by로 대체한 쿼리. 특히 <b>공개책 0권
 * 사용자는 결과 행이 없다</b>는 의미를 못 박는다(호출부의 0-디폴트 경로가 이 누락에 의존).
 */
@DataJpaTest
@Import(JpaConfig.class) // BaseTimeEntity auditing(created_at/updated_at) 활성화
class BookRepositoryTest {

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private UserRepository userRepository;

    private User persistedUser(String email) {
        return userRepository.save(
                User.of(email, "$2a$10$abcdefghijklmnopqrstuv", "책벌레", "Asia/Seoul", Role.USER));
    }

    private void publicBook(User owner, String title) {
        Book b = Book.register(owner, title, null, null, null, null, null, BookStatus.READING);
        b.changeVisibility(BookVisibility.PUBLIC);
        bookRepository.save(b);
    }

    private void privateBook(User owner, String title) { // 기본 visibility=PRIVATE
        bookRepository.save(Book.register(owner, title, null, null, null, null, null, BookStatus.READING));
    }

    @Test
    @DisplayName("countPublicByUsers: 여러 사용자의 PUBLIC 책 수를 한 번에 group 집계한다")
    void groupsPublicCountsPerUser() {
        User a = persistedUser("a@booktimer.com");
        User b = persistedUser("b@booktimer.com");
        publicBook(a, "공개1");
        publicBook(a, "공개2");
        publicBook(b, "공개1");

        List<UserPublicBookCount> rows =
                bookRepository.countPublicByUsers(List.of(a.getId(), b.getId()));

        assertThat(rows).extracting(UserPublicBookCount::getUserId, UserPublicBookCount::getPublicCount)
                .containsExactlyInAnyOrder(tuple(a.getId(), 2L), tuple(b.getId(), 1L));
    }

    @Test
    @DisplayName("countPublicByUsers: 공개책 0권 사용자는 결과 행이 없다 (0-디폴트가 이 누락에 의존)")
    void zeroPublicBookUserIsAbsent() {
        User a = persistedUser("a@booktimer.com");
        User c = persistedUser("c@booktimer.com"); // 공개책 없음
        publicBook(a, "공개1");

        List<UserPublicBookCount> rows =
                bookRepository.countPublicByUsers(List.of(a.getId(), c.getId()));

        assertThat(rows).extracting(UserPublicBookCount::getUserId).doesNotContain(c.getId());
    }

    @Test
    @DisplayName("countPublicByUsers: PRIVATE 책만 가진 사용자는 집계에서 제외된다")
    void excludesPrivateBooks() {
        User d = persistedUser("d@booktimer.com");
        privateBook(d, "비공개1");
        privateBook(d, "비공개2");
        publicBook(d, "공개1");

        List<UserPublicBookCount> rows =
                bookRepository.countPublicByUsers(List.of(d.getId()));

        assertThat(rows).extracting(UserPublicBookCount::getUserId, UserPublicBookCount::getPublicCount)
                .containsExactly(tuple(d.getId(), 1L)); // PRIVATE 2권은 안 세고 PUBLIC 1권만
    }
}
