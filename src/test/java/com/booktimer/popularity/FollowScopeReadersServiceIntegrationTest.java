package com.booktimer.popularity;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.book.BookVisibility;
import com.booktimer.follow.Follow;
import com.booktimer.follow.FollowRepository;
import com.booktimer.search.UserSearchResult;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 팔로우 스코프 인기 카운트 <b>drill-down</b>(누가 읽는지) 집계 통합 테스트 (실제 빈 + H2).
 *
 * <p>카운트({@link FollowScopePopularityService})와 <b>정확히 같은 스코프</b>만 신원을 노출하는지 본다:
 * ① 내 팔로우(followee)만 · ② PUBLIC 책만(PRIVATE 간접 누출 차단) · ③ distinct user(중복 행이 명단을
 * 부풀리지 않음) · ④ status 버킷(원함=WANT_TO_READ, 읽음=READING∪FINISHED) · ⑤ 본인은 자기 팔로우가
 * 없어 자연 제외. 화면에 이미 보이는 팔로우 프로필의 PUBLIC 책을 책 기준으로 모은 것뿐 — 새 노출 없음.
 */
@SpringBootTest
@Transactional
class FollowScopeReadersServiceIntegrationTest {

    @Autowired
    private FollowScopeReadersService service;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BookRepository bookRepository;
    @Autowired
    private FollowRepository followRepository;

    private static final String ISBN = "9788900000001";

    private User user(String email, String nick, String loginId) {
        User u = User.of(email, "$2a$10$x", nick, "Asia/Seoul", Role.USER);
        u.assignLoginId(loginId); // N-055 필터 통과에 loginId 필요
        return userRepository.saveAndFlush(u);
    }

    private void book(User owner, String isbn, BookStatus status, BookVisibility visibility) {
        Book b = Book.register(owner, "책-" + isbn, null, isbn, null, null, null, status);
        b.changeVisibility(visibility);
        bookRepository.saveAndFlush(b);
    }

    private static List<String> nicks(List<UserSearchResult> rows) {
        return rows.stream().map(UserSearchResult::nickname).toList();
    }

    @Test
    @DisplayName("팔로우 스코프·PUBLIC·distinct·status 버킷 명단이 카운트와 같은 경계로 나온다")
    void listsFollowScopeReaders() {
        User viewer = user("viewer@booktimer.com", "뷰어", "viewerid");
        User want = user("w@booktimer.com", "원함이", "wantid");      // 팔로우함, 원함
        User read = user("r@booktimer.com", "읽음이", "readid");      // 팔로우함, 읽음(중복 행)
        User done = user("d@booktimer.com", "완독이", "doneid");      // 팔로우함, 완독→읽음 버킷
        User priv = user("p@booktimer.com", "비공개", "privid");      // 팔로우함이지만 PRIVATE
        User stranger = user("s@booktimer.com", "모르는이", "strangerid"); // 팔로우 안 함
        followRepository.saveAndFlush(Follow.of(viewer, want));
        followRepository.saveAndFlush(Follow.of(viewer, read));
        followRepository.saveAndFlush(Follow.of(viewer, done));
        followRepository.saveAndFlush(Follow.of(viewer, priv));

        book(want, ISBN, BookStatus.WANT_TO_READ, BookVisibility.PUBLIC);   // 원함 ← 원함이
        book(read, ISBN, BookStatus.READING, BookVisibility.PUBLIC);        // 읽음 ← 읽음이
        book(read, ISBN, BookStatus.READING, BookVisibility.PUBLIC);        // 같은 사람 중복 행 → distinct로 1명
        book(done, ISBN, BookStatus.FINISHED, BookVisibility.PUBLIC);       // 읽음 ← 완독이(완독도 읽음 버킷)
        book(priv, ISBN, BookStatus.READING, BookVisibility.PRIVATE);       // PRIVATE → 제외
        book(stranger, ISBN, BookStatus.READING, BookVisibility.PUBLIC);    // 비팔로우 → 제외
        book(viewer, ISBN, BookStatus.READING, BookVisibility.PUBLIC);      // 본인 → 제외(자기 팔로우 없음)

        FollowScopeReaders readers = service.readers(viewer, ISBN);

        assertThat(nicks(readers.wanting())).containsExactly("원함이");
        assertThat(nicks(readers.reading())).containsExactlyInAnyOrder("읽음이", "완독이"); // distinct, PRIVATE·비팔로우·본인 제외
    }

    @Test
    @DisplayName("isbn이 null·공백이면 빈 명단(쿼리 안 탐)")
    void blankIsbn_empty() {
        User viewer = user("v2@booktimer.com", "뷰어2", "viewerid2");
        assertThat(service.readers(viewer, null).isEmpty()).isTrue();
        assertThat(service.readers(viewer, "   ").isEmpty()).isTrue();
    }

    @Test
    @DisplayName("아무도 팔로우 안 했으면(또는 팔로이가 그 책을 PUBLIC으로 안 가짐) 빈 명단")
    void noFollow_empty() {
        User viewer = user("v3@booktimer.com", "뷰어3", "viewerid3");
        User stranger = user("s3@booktimer.com", "남", "strangerid3");
        book(stranger, ISBN, BookStatus.READING, BookVisibility.PUBLIC); // 팔로우 안 한 사람 책 → 안 보임

        FollowScopeReaders readers = service.readers(viewer, ISBN);

        assertThat(readers.isEmpty()).isTrue();
    }
}
