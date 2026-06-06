package com.booktimer.search;

import com.booktimer.block.BlockService;
import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.follow.FollowService;
import com.booktimer.user.AuthProvider;
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
 * UserSearchService 통합 테스트 (실제 빈 + H2) — <b>login_id(공개 @핸들) 검색</b> (login-id-design §7 PR-3).
 *
 * <p>핵심: ① <b>login_id 부분일치</b>(닉네임이 아니라 아이디로 검색 — 인스타/X 모델), ② 최소 2글자 미만이면
 * 빈 결과, ③ 결과 상한 20, ④ 공개 책 수는 PUBLIC만, ⑤ 본인은 self·내가 팔로우 중인 사람은 following 플래그.
 * 결과 행은 <b>핸들(login_id) + 표시 이름(nickname)</b>을 함께 싣는다(링크/식별은 login_id, 표시는 nickname).
 */
@SpringBootTest
@Transactional
class UserSearchServiceTest {

    @Autowired
    private UserSearchService searchService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BookRepository bookRepository;
    @Autowired
    private FollowService followService;
    @Autowired
    private BlockService blockService;

    private User newUser(String email, String loginId, String nickname) {
        User u = User.of(email, "$2a$10$abcdefghijklmnopqrstuv", nickname, "Asia/Seoul", Role.USER);
        u.assignLoginId(loginId);
        return userRepository.save(u);
    }

    private User newAdmin(String email, String loginId, String nickname) {
        User u = User.of(email, "$2a$10$abcdefghijklmnopqrstuv", nickname, "Asia/Seoul", Role.ADMIN);
        u.assignLoginId(loginId);
        return userRepository.save(u);
    }

    private void publicBook(User owner, String title) {
        Book b = Book.register(owner, title, null, null, null, null, null, BookStatus.READING);
        b.makePublic();
        bookRepository.save(b);
    }

    private void privateBook(User owner, String title) {
        bookRepository.save(Book.register(owner, title, null, null, null, null, null, BookStatus.READING));
    }

    @Test
    @DisplayName("login_id 부분일치로 찾고, 핸들·표시이름·공개 책 수·팔로우 여부·본인 플래그를 채운다")
    void search_partialMatch_withFlags() {
        User viewer = newUser("viewer@booktimer.com", "searcher", "검색가");
        User target = newUser("t@booktimer.com", "bookking", "독서왕");
        publicBook(target, "공개책1");
        publicBook(target, "공개책2");
        privateBook(target, "비공개책"); // 공개 책 수에 안 잡혀야 함
        newUser("other@booktimer.com", "relator", "관계자"); // "book" 미포함 → 결과 제외
        followService.follow(viewer, target);

        List<UserSearchResult> results = searchService.search(viewer, "book");

        assertThat(results).hasSize(1);
        UserSearchResult r = results.get(0);
        assertThat(r.loginId()).isEqualTo("bookking");
        assertThat(r.nickname()).isEqualTo("독서왕");
        assertThat(r.publicBookCount()).isEqualTo(2L); // PUBLIC만
        assertThat(r.following()).isTrue();
        assertThat(r.self()).isFalse();
    }

    @Test
    @DisplayName("검색은 login_id 기준이다 — 닉네임으로는 찾히지 않고, 닉네임이 중복돼도 login_id로 정확히 구분된다")
    void search_byLoginId_notNickname() {
        User viewer = newUser("viewer@booktimer.com", "searcher", "검색가");
        newUser("a@booktimer.com", "alpha", "동명이인"); // 같은 닉네임
        newUser("b@booktimer.com", "bravo", "동명이인"); // 같은 닉네임, 다른 아이디

        // 닉네임("동명이인")으로는 검색되지 않는다 — 검색 핸들은 login_id다
        assertThat(searchService.search(viewer, "동명")).isEmpty();
        // login_id로는 정확히 1명만 (닉네임이 같아도 구분됨)
        List<UserSearchResult> byAlpha = searchService.search(viewer, "alpha");
        assertThat(byAlpha).extracting(UserSearchResult::loginId).containsExactly("alpha");
    }

    @Test
    @DisplayName("검색어가 2글자 미만이면 빈 결과(열거 완화)")
    void search_tooShort_empty() {
        User viewer = newUser("viewer@booktimer.com", "searcher", "검색가");
        newUser("t@booktimer.com", "bookking", "독서왕");

        assertThat(searchService.search(viewer, "b")).isEmpty();
        assertThat(searchService.search(viewer, " ")).isEmpty();
        assertThat(searchService.search(viewer, null)).isEmpty();
    }

    @Test
    @DisplayName("본인이 검색 결과에 걸리면 self=true (팔로우 버튼 대신 '나' 표시용)")
    void search_self_flagged() {
        User viewer = newUser("viewer@booktimer.com", "reader", "독서가");

        List<UserSearchResult> results = searchService.search(viewer, "read");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).self()).isTrue();
        assertThat(results.get(0).following()).isFalse();
    }

    @Test
    @DisplayName("차단 관계(양방향)인 사용자는 검색 결과에서 제외된다")
    void search_excludesBlocked() {
        User viewer = newUser("viewer@booktimer.com", "searcher", "검색가");
        User blocked = newUser("b@booktimer.com", "readerone", "독서왕");   // viewer가 차단
        User blocker = newUser("c@booktimer.com", "readertwo", "독서광");   // viewer를 차단(역방향)
        newUser("v@booktimer.com", "readerfree", "독서가");                 // 차단 무관 — 남아야 함
        blockService.block(viewer, blocked);
        blockService.block(blocker, viewer);

        List<UserSearchResult> results = searchService.search(viewer, "reader");

        assertThat(results).extracting(UserSearchResult::loginId)
                .containsExactly("readerfree")            // 차단 무관만 남음
                .doesNotContain("readerone", "readertwo"); // 양방향 모두 숨김
    }

    @Test
    @DisplayName("ADMIN 역할 사용자는 검색 결과에서 제외된다 (운영자는 일반 사용자에게 노출되지 않음)")
    void search_excludesAdmin() {
        User viewer = newUser("viewer@booktimer.com", "searcher", "검색가");
        newUser("u@booktimer.com", "readeruser", "독서가");       // 일반 사용자 — 남아야 함
        newAdmin("admin@booktimer.com", "readeradmin", "운영자"); // 운영자 — 숨겨야 함

        List<UserSearchResult> results = searchService.search(viewer, "reader");

        assertThat(results).extracting(UserSearchResult::loginId)
                .containsExactly("readeruser")     // 일반 사용자만 남음
                .doesNotContain("readeradmin");    // 운영자는 검색 불가
    }

    @Test
    @DisplayName("결과는 최대 20명으로 제한된다(상한 가드)")
    void search_cappedAt20() {
        User viewer = newUser("viewer@booktimer.com", "searcher", "검색가");
        for (int i = 1; i <= 22; i++) {
            newUser("u" + i + "@booktimer.com", String.format("club%02d", i), String.format("북클럽%02d", i));
        }

        assertThat(searchService.search(viewer, "club")).hasSize(20);
    }

    @Test
    @DisplayName("추천 제외: 아직 공개 핸들(login_id)을 안 정한 사용자(OAuth 온보딩 전)는 추천하지 않는다")
    void recommend_excludesUsersWithoutLoginId() {
        User viewer = newUser("viewer@booktimer.com", "searcher", "검색가");
        newUser("real@booktimer.com", "realuser", "정상유저"); // 핸들 있음 — 추천돼야
        // OAuth 프로비저닝 직후 = 온보딩 전: loginId=null(아직 아이디 미정), 닉네임은 provider 표시명.
        // 이 상태면 /u/{loginId} 프로필 링크가 깨지고 팔로우 대상 식별도 불가하므로 추천에 떠선 안 된다.
        userRepository.save(
                User.ofOAuth("pending@booktimer.com", "구글이름", "Asia/Seoul", Role.USER, AuthProvider.GOOGLE));

        List<UserSearchResult> recs = searchService.recommend(viewer, 10);

        // 본인(viewer) 제외 후 남는 후보는 realuser·pending 둘인데, 핸들 없는 pending은 빠지고 realuser만 남아야 한다.
        assertThat(recs).extracting(UserSearchResult::loginId).containsExactly("realuser");
    }
}
