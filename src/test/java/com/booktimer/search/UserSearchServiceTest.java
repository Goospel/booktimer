package com.booktimer.search;

import com.booktimer.block.BlockService;
import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.follow.FollowService;
import com.booktimer.session.ReadingSession;
import com.booktimer.session.ReadingSessionRepository;
import com.booktimer.user.AuthProvider;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
    @Autowired
    private ReadingSessionRepository readingSessionRepository;

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

    /** isbn 가진 PUBLIC 완독 책 — "같은 책" 추천 신호 입력. */
    private void publicReadBook(User owner, String isbn) {
        Book b = Book.register(owner, "책-" + isbn, null, isbn, null, null, null, BookStatus.FINISHED);
        b.makePublic();
        bookRepository.save(b);
    }

    private List<String> reasonsOf(List<RecommendedUser> recs, String loginId) {
        return recs.stream().filter(r -> r.row().loginId().equals(loginId))
                .findFirst().orElseThrow().reasons();
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
        newUser("real@booktimer.com", "realuser", "정상유저"); // 핸들 있음 — 폴백으로 추천돼야
        // OAuth 프로비저닝 직후 = 온보딩 전: loginId=null(아직 아이디 미정), 닉네임은 provider 표시명.
        // 이 상태면 /u/{loginId} 프로필 링크가 깨지고 팔로우 대상 식별도 불가하므로 추천에 떠선 안 된다.
        userRepository.save(
                User.ofOAuth("pending@booktimer.com", "구글이름", "Asia/Seoul", Role.USER, AuthProvider.GOOGLE));

        List<RecommendedUser> recs = searchService.recommend(viewer, 10);

        // 본인(viewer) 제외 후 남는 후보는 realuser·pending 둘인데, 핸들 없는 pending은 빠지고 realuser만 남아야 한다.
        assertThat(recs).extracting(r -> r.row().loginId()).containsExactly("realuser");
    }

    @Test
    @DisplayName("추천: 행 배선(공개책수·본인) + 차단 제외 — 적격 1명(폴백)과 차단 1명이 있을 때 적격만 반환")
    void recommend_mapsRowsAndExcludesBlocked() {
        User viewer = newUser("viewer@booktimer.com", "viewer", "뷰어");
        User eligible = newUser("eligible@booktimer.com", "eligible", "적격자"); // 신호 없음 → 폴백
        publicBook(eligible, "공개책1");
        User blocked = newUser("blocked@booktimer.com", "blocked", "차단됨");
        blockService.block(viewer, blocked);

        List<RecommendedUser> recs = searchService.recommend(viewer, 10);

        assertThat(recs).extracting(r -> r.row().loginId())
                .containsExactly("eligible").doesNotContain("blocked");
        RecommendedUser r = recs.get(0);
        assertThat(r.row().nickname()).isEqualTo("적격자");
        assertThat(r.row().publicBookCount()).isEqualTo(1L);
        assertThat(r.row().following()).isFalse();
        assertThat(r.row().self()).isFalse();
        assertThat(r.reasons()).isEmpty(); // 폴백 후보 → 이유 칩 없음
    }

    @Test
    @DisplayName("추천 사다리: 맞팔→FoF→같은책→폴백 우선순위로 정렬되고 각 이유 칩이 붙는다")
    void recommend_ladderOrderAndReasons() {
        User viewer = newUser("viewer@booktimer.com", "viewer", "뷰어");
        // G1 맞팔: fb가 viewer를 팔로우(viewer는 안 함)
        User fb = newUser("fb@booktimer.com", "followback", "맞팔러");
        followService.follow(fb, viewer);
        // G2 FoF: viewer→mid→fof
        User mid = newUser("mid@booktimer.com", "midfriend", "중간");
        User fof = newUser("fof@booktimer.com", "fofcand", "친친");
        followService.follow(viewer, mid);
        followService.follow(mid, fof);
        // C 같은 책: viewer와 co가 같은 PUBLIC 책
        User co = newUser("co@booktimer.com", "coreader", "같은책");
        publicReadBook(viewer, "9788900000099");
        publicReadBook(co, "9788900000099");

        List<RecommendedUser> recs = searchService.recommend(viewer, 10);

        assertThat(recs).extracting(r -> r.row().loginId())
                .containsExactly("followback", "fofcand", "coreader"); // 우선순위 순
        assertThat(reasonsOf(recs, "followback")).containsExactly("나를 팔로우함");
        assertThat(reasonsOf(recs, "fofcand")).containsExactly("공통 친구 1명");
        assertThat(reasonsOf(recs, "coreader")).containsExactly("같이 읽은 책 1권");
    }

    @Test
    @DisplayName("추천 dedup: 한 사람이 여러 신호(FoF·같은책)에 걸리면 한 줄로 합치고 이유를 모두 단다")
    void recommend_dedupMergesReasons() {
        User viewer = newUser("viewer@booktimer.com", "viewer", "뷰어");
        User both = newUser("both@booktimer.com", "bothcand", "둘다");
        User mid = newUser("mid@booktimer.com", "midfriend", "중간");
        followService.follow(viewer, mid);
        followService.follow(mid, both); // FoF
        publicReadBook(viewer, "9788900000088");
        publicReadBook(both, "9788900000088"); // 같은 책

        List<RecommendedUser> recs = searchService.recommend(viewer, 10);

        assertThat(recs).extracting(r -> r.row().loginId())
                .filteredOn(id -> id.equals("bothcand")).hasSize(1); // 한 줄로 dedup
        assertThat(reasonsOf(recs, "bothcand"))
                .containsExactlyInAnyOrder("공통 친구 1명", "같이 읽은 책 1권"); // 이유 합집합
    }

    @Test
    @DisplayName("추천: 내가 이미 팔로우한 사람은 (같은 책이어도) 추천에 안 뜬다")
    void recommend_excludesAlreadyFollowed() {
        User viewer = newUser("viewer@booktimer.com", "viewer", "뷰어");
        User followed = newUser("followed@booktimer.com", "followeduser", "팔로우중");
        publicReadBook(viewer, "9788900000077");
        publicReadBook(followed, "9788900000077");
        followService.follow(viewer, followed);

        List<RecommendedUser> recs = searchService.recommend(viewer, 10);

        assertThat(recs).extracting(r -> r.row().loginId()).doesNotContain("followeduser");
    }

    @Test
    @DisplayName("추천: limit을 넘지 않는다 (상한)")
    void recommend_respectsLimit() {
        User viewer = newUser("viewer@booktimer.com", "viewer", "뷰어");
        for (int i = 1; i <= 15; i++) {
            newUser("u" + i + "@booktimer.com", "cand" + i, "후보" + i);
        }

        assertThat(searchService.recommend(viewer, 5)).hasSize(5);
    }

    // ──────────────────────────────────────────────────────────────
    // E 활동량 폴백 테스트 (6개)
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("추천 E(활동량): 활동일 수가 많은 사용자가 더 앞서 추천된다")
    void recommend_activeReader_orderedByActiveDays() {
        User viewer = newUser("viewer@booktimer.com", "viewer", "뷰어");
        User moreActive = newUser("more@booktimer.com", "moreactive", "활발");
        User lessActive = newUser("less@booktimer.com", "lessactive", "조금활발");

        Instant now = Instant.now();
        // moreActive: 3개 distinct 날에 읽음
        readingSessionRepository.save(ReadingSession.start(moreActive, now.minus(1, ChronoUnit.DAYS)));
        readingSessionRepository.save(ReadingSession.start(moreActive, now.minus(3, ChronoUnit.DAYS)));
        readingSessionRepository.save(ReadingSession.start(moreActive, now.minus(5, ChronoUnit.DAYS)));
        // lessActive: 1개 날에 읽음
        readingSessionRepository.save(ReadingSession.start(lessActive, now.minus(2, ChronoUnit.DAYS)));

        List<RecommendedUser> recs = searchService.recommend(viewer, 10);

        List<String> ids = recs.stream().map(r -> r.row().loginId()).toList();
        assertThat(ids).contains("moreactive", "lessactive");
        assertThat(ids.indexOf("moreactive")).isLessThan(ids.indexOf("lessactive"));
        assertThat(reasonsOf(recs, "moreactive")).containsExactly("요즘 꾸준히 읽어요");
        assertThat(reasonsOf(recs, "lessactive")).containsExactly("요즘 꾸준히 읽어요");
    }

    @Test
    @DisplayName("추천 E(활동량): 14일 이내 활동만 집계 — 15일 전 활동은 신호 대상 아님")
    void recommend_activeReader_windowBoundary() {
        User viewer = newUser("viewer@booktimer.com", "viewer", "뷰어");
        User inWindow = newUser("in@booktimer.com", "inwindow", "기간내");
        User outWindow = newUser("out@booktimer.com", "outwindow", "기간외");

        Instant now = Instant.now();
        readingSessionRepository.save(ReadingSession.start(inWindow, now.minus(13, ChronoUnit.DAYS)));
        readingSessionRepository.save(ReadingSession.start(outWindow, now.minus(15, ChronoUnit.DAYS)));

        List<RecommendedUser> recs = searchService.recommend(viewer, 10);

        // inWindow는 E 신호로 추천됨
        assertThat(recs.stream().anyMatch(r -> "inwindow".equals(r.row().loginId()))).isTrue();
        assertThat(reasonsOf(recs, "inwindow")).containsExactly("요즘 꾸준히 읽어요");
        // outWindow는 E 신호 없음 — 폴백(F)에 나오더라도 이유 칩이 없어야 한다
        recs.stream()
                .filter(r -> "outwindow".equals(r.row().loginId()))
                .forEach(r -> assertThat(r.reasons()).doesNotContain("요즘 꾸준히 읽어요"));
    }

    @Test
    @DisplayName("추천 E(활동량): login_id null인 사용자는 활동 기록이 있어도 추천하지 않는다 (N-055)")
    void recommend_activeReader_excludesNullLoginId() {
        User viewer = newUser("viewer@booktimer.com", "viewer", "뷰어");
        // OAuth 온보딩 전 상태 — loginId=null
        User pending = userRepository.save(
                User.ofOAuth("pending@booktimer.com", "구글이름", "Asia/Seoul", Role.USER, AuthProvider.GOOGLE));
        readingSessionRepository.save(ReadingSession.start(pending, Instant.now().minus(2, ChronoUnit.DAYS)));

        List<RecommendedUser> recs = searchService.recommend(viewer, 10);

        // login_id=null인 사용자는 추천에 떠선 안 됨
        assertThat(recs.stream().anyMatch(r -> r.row().loginId() == null)).isFalse();
        assertThat(recs).isEmpty(); // viewer 외엔 pending뿐이고 그건 제외
    }

    @Test
    @DisplayName("추천 E(활동량): G1/G2/C 신호 없어도 꾸준히 읽는 사람을 이유 칩과 함께 채운다")
    void recommend_activeReader_fillsWhenNoOtherSignals() {
        User viewer = newUser("viewer@booktimer.com", "viewer", "뷰어");
        User activeReader = newUser("active@booktimer.com", "activereader", "꾸준독서가");
        // 팔로우/같은책 신호 없음, 활동 기록만 있음
        readingSessionRepository.save(ReadingSession.start(activeReader, Instant.now().minus(3, ChronoUnit.DAYS)));

        List<RecommendedUser> recs = searchService.recommend(viewer, 10);

        assertThat(recs.stream().anyMatch(r -> "activereader".equals(r.row().loginId()))).isTrue();
        assertThat(reasonsOf(recs, "activereader")).containsExactly("요즘 꾸준히 읽어요");
    }

    @Test
    @DisplayName("추천 E(활동량): 활동 신호 있는 사람이 순수 랜덤 폴백(이유 없음)보다 먼저 나온다")
    void recommend_activeReader_beforeRandomFallback() {
        User viewer = newUser("viewer@booktimer.com", "viewer", "뷰어");
        User activeReader = newUser("active@booktimer.com", "activereader", "꾸준독서가");
        User randomUser = newUser("random@booktimer.com", "randomuser", "무신호");
        // activeReader만 활동 기록 있음; randomUser는 신호 없음(→ F 폴백)
        readingSessionRepository.save(ReadingSession.start(activeReader, Instant.now().minus(3, ChronoUnit.DAYS)));

        List<RecommendedUser> recs = searchService.recommend(viewer, 10);

        List<String> ids = recs.stream().map(r -> r.row().loginId()).toList();
        assertThat(ids).contains("activereader", "randomuser");
        assertThat(ids.indexOf("activereader")).isLessThan(ids.indexOf("randomuser"));
    }

    @Test
    @DisplayName("추천 dedup: 같은 책 읽고(C) + 활동도 꾸준한(E) 사람은 한 줄로 합쳐 이유를 모두 단다")
    void recommend_activeReader_dedupWithCoRead() {
        User viewer = newUser("viewer@booktimer.com", "viewer", "뷰어");
        User overlap = newUser("overlap@booktimer.com", "overlapcand", "중복후보");
        // C 신호: 같은 ISBN PUBLIC 완독
        publicReadBook(viewer, "9788900000011");
        publicReadBook(overlap, "9788900000011");
        // E 신호: 최근 활동
        readingSessionRepository.save(ReadingSession.start(overlap, Instant.now().minus(2, ChronoUnit.DAYS)));

        List<RecommendedUser> recs = searchService.recommend(viewer, 10);

        // 한 줄로 dedup
        assertThat(recs.stream().filter(r -> "overlapcand".equals(r.row().loginId()))).hasSize(1);
        // C 이유 + E 이유 합집합
        assertThat(reasonsOf(recs, "overlapcand"))
                .containsExactlyInAnyOrder("같이 읽은 책 1권", "요즘 꾸준히 읽어요");
    }
}
