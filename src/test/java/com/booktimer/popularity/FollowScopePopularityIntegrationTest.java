package com.booktimer.popularity;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.book.BookVisibility;
import com.booktimer.follow.Follow;
import com.booktimer.follow.FollowRepository;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 팔로우 스코프 인기 카운트 집계 통합 테스트 (실제 빈 + H2).
 *
 * <p>경계 검증: ① 내 팔로우(followee)만 집계(비팔로우·본인 제외) · ② PUBLIC 책만(PRIVATE 간접 누출 차단)
 * · ③ distinct user(같은 책 중복 행이 사람 수를 부풀리지 않음) · ④ status 버킷(원함=WANT_TO_READ,
 * 읽음=READING∪FINISHED). mock으로는 못 잡는 집계 정확도라 실 스키마로 본다.
 */
@SpringBootTest
@Transactional
class FollowScopePopularityIntegrationTest {

    @Autowired
    private FollowScopePopularityService service;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BookRepository bookRepository;
    @Autowired
    private FollowRepository followRepository;

    private static final String ISBN_1 = "9788900000001";
    private static final String ISBN_2 = "9788900000002";

    private User user(String email, String nick) {
        return userRepository.saveAndFlush(User.of(email, "$2a$10$x", nick, "Asia/Seoul", Role.USER));
    }

    private void book(User owner, String isbn, BookStatus status, BookVisibility visibility) {
        Book b = Book.register(owner, "책-" + isbn, null, isbn, null, null, null, status);
        b.changeVisibility(visibility);
        bookRepository.saveAndFlush(b);
    }

    @Test
    @DisplayName("팔로우 스코프·PUBLIC·distinct·status 버킷이 정확히 집계된다")
    void aggregatesFollowScopeCorrectly() {
        User viewer = user("viewer@booktimer.com", "뷰어");
        User a = user("a@booktimer.com", "에이");   // 팔로우함
        User b = user("b@booktimer.com", "비");      // 팔로우함
        User c = user("c@booktimer.com", "씨");      // 팔로우 안 함
        followRepository.saveAndFlush(Follow.of(viewer, a));
        followRepository.saveAndFlush(Follow.of(viewer, b));

        // ISBN_1
        book(a, ISBN_1, BookStatus.READING, BookVisibility.PUBLIC);       // 읽음 ← A
        book(a, ISBN_1, BookStatus.READING, BookVisibility.PUBLIC);       // 같은 사람 중복 행 → distinct로 1명
        book(a, ISBN_1, BookStatus.FINISHED, BookVisibility.PRIVATE);     // PRIVATE → 제외
        book(b, ISBN_1, BookStatus.WANT_TO_READ, BookVisibility.PUBLIC);  // 원함 ← B
        book(c, ISBN_1, BookStatus.READING, BookVisibility.PUBLIC);       // 비팔로우 → 제외
        book(viewer, ISBN_1, BookStatus.READING, BookVisibility.PUBLIC);  // 본인 → 제외(자기 팔로우 없음)

        // ISBN_2: 둘 다 읽음(완독/읽는중) → 읽음=2, 원함=0
        book(a, ISBN_2, BookStatus.FINISHED, BookVisibility.PUBLIC);
        book(b, ISBN_2, BookStatus.READING, BookVisibility.PUBLIC);

        Map<String, FollowScopePopularity> result =
                service.countByIsbn(viewer, List.of(ISBN_1, ISBN_2, "9788900000999"));

        assertThat(result.get(ISBN_1).wantCount()).isEqualTo(1); // B
        assertThat(result.get(ISBN_1).readCount()).isEqualTo(1); // A (distinct, PRIVATE·비팔로우·본인 제외)
        assertThat(result.get(ISBN_2).wantCount()).isEqualTo(0);
        assertThat(result.get(ISBN_2).readCount()).isEqualTo(2); // A + B
        assertThat(result).doesNotContainKey("9788900000999"); // 데이터 없는 isbn은 키 부재
    }

    @Test
    @DisplayName("ADMIN은 카운트에서 빠진다 — 운영 계정은 소셜 집계 대상이 아니다(drill-down 명단과 같은 경계). 양성 대조군: 같은 조건의 일반 팔로우 사용자 2명은 둘 다 세어진다")
    void excludesAdminFromCount_butKeepsRegularFollowee() {
        User viewer = user("cv@booktimer.com", "카운트뷰어");
        User admin = userRepository.saveAndFlush(
                User.of("cadmin@booktimer.com", "$2a$10$x", "운영자", "Asia/Seoul", Role.ADMIN));
        User regular1 = user("creg1@booktimer.com", "일반이");
        User regular2 = user("creg2@booktimer.com", "일반이둘");
        followRepository.saveAndFlush(Follow.of(viewer, admin));
        followRepository.saveAndFlush(Follow.of(viewer, regular1));
        followRepository.saveAndFlush(Follow.of(viewer, regular2));

        book(admin, ISBN_1, BookStatus.READING, BookVisibility.PUBLIC);    // 운영자 → 제외
        book(regular1, ISBN_1, BookStatus.READING, BookVisibility.PUBLIC); // 양성 대조군 → 세어짐
        book(regular2, ISBN_1, BookStatus.READING, BookVisibility.PUBLIC); // 양성 대조군 → 세어짐

        Map<String, FollowScopePopularity> result = service.countByIsbn(viewer, List.of(ISBN_1));

        // 일반 2명만. 일반이 2명인 것이 계측기다 — 조건을 역전(`<>`→`=`)하면 1이 되어 이 단언이 직접 죽는다
        // (대조군이 1명이면 역전에도 1이라 조용히 통과한다).
        assertThat(result.get(ISBN_1).readCount()).isEqualTo(2);
    }
}
