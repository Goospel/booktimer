package com.booktimer.web.api;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.book.BookVisibility;
import com.booktimer.follow.Follow;
import com.booktimer.follow.FollowRepository;
import com.booktimer.session.ReadingSession;
import com.booktimer.session.ReadingSessionRepository;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/home-feed 의 {@code readers} — 홈 「함께 읽는 사람」 탭.
 *
 * <p><b>이 탭의 불변식은 한 줄이다: 공개 책의 독서만 본다.</b> 그래서 여기 테스트의 절반은
 * "비공개는 흔적조차 남지 않는다"를 서로 다른 각도에서 때린다 — 진행 중 세션·완료 세션·미태깅 세션.
 * 이 불변식이 있어야 새 동의·새 설정 화면 없이 기존 책 단위 공개 스위치를 그대로 물려받을 수 있다.
 *
 * <p>소식·뉴스는 {@code HomeFeedApiControllerTest}·{@code HomeFeedNews*Test}가 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HomeFeedReadersApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private FollowRepository followRepository;

    @Autowired
    private ReadingSessionRepository sessionRepository;

    @Autowired
    private Clock clock;

    private User saveUser(String email, String loginId, String nickname) {
        User u = User.of(email, "$2a$10$abcdefghijklmnopqrstuv", nickname, "Asia/Seoul", Role.USER);
        u.assignLoginId(loginId);
        return userRepository.save(u);
    }

    private Book book(User owner, String title, BookVisibility visibility) {
        Book b = Book.register(owner, title, null, null, null, null, null, BookStatus.READING);
        b.changeVisibility(visibility);
        return bookRepository.save(b);
    }

    /** 진행 중(미종료) 세션 — 「지금 읽는 중」의 원천. */
    private void reading(User user, Book book, Instant since) {
        sessionRepository.save(ReadingSession.start(user, since, book));
    }

    /** 끝난 세션 — 「마지막 기록」의 원천. */
    private void read(User user, Book book, Instant startedAt) {
        ReadingSession s = ReadingSession.start(user, startedAt, book);
        s.end(startedAt.plus(Duration.ofMinutes(30)));
        sessionRepository.save(s);
    }

    private Instant hoursAgo(long hours) {
        return clock.instant().minus(Duration.ofHours(hours)).truncatedTo(ChronoUnit.SECONDS);
    }

    // ── 핵심 불변식: 비공개는 흔적을 남기지 않는다 ────────────────────────

    @Test
    @DisplayName("공개 책을 읽는 중이면 책 제목과 시작 시각이 온다")
    void publicBookInProgressIsVisible() throws Exception {
        User me = saveUser("rd-a@booktimer.com", "rda", "나");
        User friend = saveUser("rd-a2@booktimer.com", "rda2", "친구");
        followRepository.save(Follow.of(me, friend));
        Instant since = hoursAgo(1);
        reading(friend, book(friend, "데미안", BookVisibility.PUBLIC), since);

        mockMvc.perform(get("/api/home-feed").with(user("rda")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readers.length()").value(1))
                .andExpect(jsonPath("$.readers[0].loginId").value("rda2"))
                .andExpect(jsonPath("$.readers[0].nickname").value("친구"))
                .andExpect(jsonPath("$.readers[0].readingBookTitle").value("데미안"))
                .andExpect(jsonPath("$.readers[0].readingSince").value(since.toString()));
    }

    @Test
    @DisplayName("비공개 책을 읽는 중이면 읽는 중 표시가 아예 없다 — 실시간 프레즌스도 누출 금지")
    void privateBookInProgressLeaksNothing() throws Exception {
        User me = saveUser("rd-b@booktimer.com", "rdb", "나");
        User friend = saveUser("rd-b2@booktimer.com", "rdb2", "친구");
        followRepository.save(Follow.of(me, friend));
        reading(friend, book(friend, "몰래 읽는 책", BookVisibility.PRIVATE), hoursAgo(1));

        mockMvc.perform(get("/api/home-feed").with(user("rdb")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readers.length()").value(1))
                .andExpect(jsonPath("$.readers[0].readingBookTitle").doesNotExist())
                .andExpect(jsonPath("$.readers[0].readingSince").doesNotExist())
                // 제목만 가리고 「읽는 중」을 남기면 그것만으로 지금 뭘 하는지가 샌다.
                .andExpect(jsonPath("$.readers[0].lastReadAt").doesNotExist());
    }

    @Test
    @DisplayName("책 미지정 세션은 읽는 중으로 안 친다 — 공개 여부를 판정할 수 없으면 비공개로 취급")
    void untaggedSessionIsNotReading() throws Exception {
        User me = saveUser("rd-c@booktimer.com", "rdc", "나");
        User friend = saveUser("rd-c2@booktimer.com", "rdc2", "친구");
        followRepository.save(Follow.of(me, friend));
        sessionRepository.save(ReadingSession.start(friend, hoursAgo(1))); // book == null

        mockMvc.perform(get("/api/home-feed").with(user("rdc")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readers[0].readingBookTitle").doesNotExist())
                .andExpect(jsonPath("$.readers[0].readingSince").doesNotExist());
    }

    @Test
    @DisplayName("마지막 기록은 공개 책 세션만 센다 — 더 최근의 비공개 독서가 있어도 무시")
    void lastReadAtCountsPublicSessionsOnly() throws Exception {
        User me = saveUser("rd-d@booktimer.com", "rdd", "나");
        User friend = saveUser("rd-d2@booktimer.com", "rdd2", "친구");
        followRepository.save(Follow.of(me, friend));
        Instant publicRead = hoursAgo(72);
        read(friend, book(friend, "공개책", BookVisibility.PUBLIC), publicRead);
        read(friend, book(friend, "비공개책", BookVisibility.PRIVATE), hoursAgo(2)); // 더 최근

        mockMvc.perform(get("/api/home-feed").with(user("rdd")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readers[0].lastReadAt").value(publicRead.toString()));
    }

    @Test
    @DisplayName("공개 기록이 하나도 없으면 lastReadAt이 null — 「공개된 기록이 없어요」로 그린다")
    void noPublicRecordYieldsNull() throws Exception {
        User me = saveUser("rd-e@booktimer.com", "rde", "나");
        User friend = saveUser("rd-e2@booktimer.com", "rde2", "친구");
        followRepository.save(Follow.of(me, friend));

        mockMvc.perform(get("/api/home-feed").with(user("rde")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readers.length()").value(1))
                .andExpect(jsonPath("$.readers[0].lastReadAt").doesNotExist());
    }

    // ── 관계 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("서로 팔로우면 mutual=true, 내가 일방으로 건 팔로우면 false")
    void mutualFlagReflectsReverseEdge() throws Exception {
        User me = saveUser("rd-f@booktimer.com", "rdf", "나");
        User both = saveUser("rd-f2@booktimer.com", "rdf2", "맞팔");
        User oneWay = saveUser("rd-f3@booktimer.com", "rdf3", "일방");
        followRepository.save(Follow.of(me, both));
        followRepository.save(Follow.of(both, me));
        followRepository.save(Follow.of(me, oneWay));

        mockMvc.perform(get("/api/home-feed").with(user("rdf")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readers.length()").value(2))
                .andExpect(jsonPath("$.readers[0].loginId").value("rdf2"))
                .andExpect(jsonPath("$.readers[0].mutual").value(true))
                .andExpect(jsonPath("$.readers[1].loginId").value("rdf3"))
                .andExpect(jsonPath("$.readers[1].mutual").value(false));
    }

    @Test
    @DisplayName("팔로우하지 않은 사람은 목록에 없다 — 나를 팔로우만 한 사람도 마찬가지")
    void onlyPeopleIFollowAppear() throws Exception {
        User me = saveUser("rd-g@booktimer.com", "rdg", "나");
        User stranger = saveUser("rd-g2@booktimer.com", "rdg2", "남");
        User followsMe = saveUser("rd-g3@booktimer.com", "rdg3", "나를팔로우");
        followRepository.save(Follow.of(followsMe, me));
        reading(stranger, book(stranger, "남의 책", BookVisibility.PUBLIC), hoursAgo(1));

        mockMvc.perform(get("/api/home-feed").with(user("rdg")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readers.length()").value(0));
    }

    @Test
    @DisplayName("ADMIN·공개핸들 미설정 팔로위는 제외된다 (노출 불변식 N-055)")
    void adminAndHandlelessAreExcluded() throws Exception {
        User me = saveUser("rd-h@booktimer.com", "rdh", "나");
        User admin = User.of("rd-h2@booktimer.com", "$2a$10$abcdefghijklmnopqrstuv", "관리자",
                "Asia/Seoul", Role.ADMIN);
        admin.assignLoginId("rdh2");
        userRepository.save(admin);
        User onboarding = userRepository.save(User.of("rd-h3@booktimer.com",
                "$2a$10$abcdefghijklmnopqrstuv", "온보딩전", "Asia/Seoul", Role.USER)); // loginId null
        followRepository.save(Follow.of(me, admin));
        followRepository.save(Follow.of(me, onboarding));

        mockMvc.perform(get("/api/home-feed").with(user("rdh")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readers.length()").value(0));
    }

    // ── 정렬·경계 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("정렬: 읽는 중 → 맞팔 → 최근 활동순 → 기록 없음")
    void sortsReadingThenMutualThenRecent() throws Exception {
        User me = saveUser("rd-i@booktimer.com", "rdi", "나");
        User reading = saveUser("rd-i2@booktimer.com", "rdi2", "읽는중");
        User mutualQuiet = saveUser("rd-i3@booktimer.com", "rdi3", "맞팔조용");
        User recent = saveUser("rd-i4@booktimer.com", "rdi4", "최근");
        User never = saveUser("rd-i5@booktimer.com", "rdi5", "기록없음");
        for (User u : new User[]{reading, mutualQuiet, recent, never}) {
            followRepository.save(Follow.of(me, u));
        }
        followRepository.save(Follow.of(mutualQuiet, me));
        // 「읽는 중」이 맞팔·최신 기록을 모두 이기는지 보려고 가장 오래된 기록을 준다.
        reading(reading, book(reading, "지금책", BookVisibility.PUBLIC), hoursAgo(1));
        read(reading, book(reading, "옛책", BookVisibility.PUBLIC), hoursAgo(500));
        read(mutualQuiet, book(mutualQuiet, "맞팔책", BookVisibility.PUBLIC), hoursAgo(300));
        read(recent, book(recent, "최근책", BookVisibility.PUBLIC), hoursAgo(2)); // 맞팔보다 최근이지만 단방향

        mockMvc.perform(get("/api/home-feed").with(user("rdi")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readers.length()").value(4))
                .andExpect(jsonPath("$.readers[0].loginId").value("rdi2"))
                .andExpect(jsonPath("$.readers[1].loginId").value("rdi3"))
                .andExpect(jsonPath("$.readers[2].loginId").value("rdi4"))
                .andExpect(jsonPath("$.readers[3].loginId").value("rdi5"));
    }

    @Test
    @DisplayName("팔로우가 0명이면 빈 배열 — 흰 화면이 아니라 계약이 지켜진 빈 목록")
    void noFollowingYieldsEmptyArray() throws Exception {
        saveUser("rd-j@booktimer.com", "rdj", "나");

        mockMvc.perform(get("/api/home-feed").with(user("rdj")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readers").isArray())
                .andExpect(jsonPath("$.readers.length()").value(0));
    }
}
