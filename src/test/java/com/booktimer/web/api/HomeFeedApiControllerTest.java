package com.booktimer.web.api;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.book.BookVisibility;
import com.booktimer.block.BlockService;
import com.booktimer.follow.Follow;
import com.booktimer.follow.FollowRepository;
import com.booktimer.story.Story;
import com.booktimer.story.StoryRepository;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/home-feed — 미니앱 홈 「소식」 박스용 피드 API.
 *
 * <p>노출 게이트(PUBLIC·팔로우·ADMIN·핸들 null)는 {@code BookRepositoryTest}가 쿼리 레벨에서 못 박으므로
 * 여기선 <b>컨트롤러의 몫</b>만 본다: 시작·완독 두 쿼리를 하나로 합쳐 최신순 정렬 + 상한 30건.
 * 「책 뉴스」는 {@code HomeFeedNewsApiControllerTest}(기본 ON의 조인)와
 * {@code HomeFeedNewsDisabledApiControllerTest}(킬스위치 OFF)가 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HomeFeedApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private FollowRepository followRepository;

    @Autowired
    private StoryRepository storyRepository;

    @Autowired
    private BlockService blockService;

    @Autowired
    private Clock clock;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    private User saveUser(String email, String loginId, String nickname) {
        User u = User.of(email, "$2a$10$abcdefghijklmnopqrstuv", nickname, "Asia/Seoul", Role.USER);
        u.assignLoginId(loginId);
        return userRepository.save(u);
    }

    /** 소식 행을 만들지 않는 공개 책(시작·완독 시각 없음) — 여백 이벤트만 골라보려고. */
    private Book quietPublicBook(User owner, String title) {
        Book b = Book.register(owner, title, null, null, null, null, null, BookStatus.READING);
        b.changeVisibility(BookVisibility.PUBLIC);
        return bookRepository.save(b);
    }

    /** 그 책의 여백에 글을 남기고 생성 시각을 원하는 값으로 되돌린다(@CreatedDate 우회). */
    private void storyAt(User owner, Book book, String text, Instant createdAt) {
        Story story = storyRepository.save(Story.of(owner, text, book, null));
        entityManager.createQuery("update Story s set s.createdAt = :t where s.id = :id")
                .setParameter("t", createdAt)
                .setParameter("id", story.getId())
                .executeUpdate();
        entityManager.clear();
    }

    private void publicBook(User owner, String title, Instant startedAt, Instant finishedAt) {
        Book b = Book.register(owner, title, null, null, null, null, null, BookStatus.WANT_TO_READ);
        b.changeVisibility(BookVisibility.PUBLIC);
        if (startedAt != null) {
            b.changeStatus(BookStatus.READING, startedAt);
        }
        if (finishedAt != null) {
            b.changeStatus(BookStatus.FINISHED, finishedAt);
        }
        bookRepository.save(b);
    }

    private Instant hoursAgo(long hours) {
        return clock.instant().minus(Duration.ofHours(hours));
    }

    @Test
    @DisplayName("소식 행에 그 책의 표지가 함께 온다 — 완독·시작·여백 모두")
    void socialEventsCarryCoverUrl() throws Exception {
        User me = saveUser("hf-cv@booktimer.com", "hfcv", "나");
        User followee = saveUser("hf-cv2@booktimer.com", "hfcv2", "친구");
        followRepository.save(Follow.of(me, followee));

        Book read = Book.register(followee, "표지책", null, null, "https://img/cover.jpg", null, null,
                BookStatus.WANT_TO_READ);
        read.changeVisibility(BookVisibility.PUBLIC);
        read.changeStatus(BookStatus.READING, hoursAgo(3));
        bookRepository.save(read);

        Book margin = Book.register(followee, "여백책", null, null, "https://img/margin.jpg", null, null,
                BookStatus.READING);
        margin.changeVisibility(BookVisibility.PUBLIC);
        storyAt(followee, bookRepository.save(margin), "남긴 문장", hoursAgo(1));

        mockMvc.perform(get("/api/home-feed").with(user("hfcv")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.social[0].type").value("STORY"))
                .andExpect(jsonPath("$.social[0].coverUrl").value("https://img/margin.jpg"))
                .andExpect(jsonPath("$.social[1].type").value("STARTED"))
                .andExpect(jsonPath("$.social[1].coverUrl").value("https://img/cover.jpg"));
    }

    @Test
    @DisplayName("표지가 없는 책은 coverUrl이 null — 화면이 첫 글자 자리 표지로 떨어뜨린다")
    void coverUrlIsNullWhenBookHasNone() throws Exception {
        User me = saveUser("hf-cv3@booktimer.com", "hfcv3", "나");
        User followee = saveUser("hf-cv4@booktimer.com", "hfcv4", "친구");
        followRepository.save(Follow.of(me, followee));
        publicBook(followee, "민책", hoursAgo(3), null);

        mockMvc.perform(get("/api/home-feed").with(user("hfcv3")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.social[0].coverUrl").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/home-feed 미인증 → 302 로그인 리다이렉트 (기본 잠김)")
    void unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/api/home-feed"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("시작·완독 이벤트를 한 목록으로 합쳐 최신순(desc)으로 준다")
    void mergesStartedAndFinishedEventsNewestFirst() throws Exception {
        User me = saveUser("hf-me@booktimer.com", "hfme", "나");
        User followee = saveUser("hf-you@booktimer.com", "hfyou", "친구");
        followRepository.save(Follow.of(me, followee));
        publicBook(followee, "느린책", hoursAgo(50), null);          // 시작 (가장 오래됨)
        publicBook(followee, "빠른책", hoursAgo(10), hoursAgo(1));   // 시작 + 완독

        mockMvc.perform(get("/api/home-feed").with(user("hfme")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.social.length()").value(3))
                .andExpect(jsonPath("$.social[0].type").value("FINISHED"))
                .andExpect(jsonPath("$.social[0].bookTitle").value("빠른책"))
                .andExpect(jsonPath("$.social[0].loginId").value("hfyou"))
                .andExpect(jsonPath("$.social[0].nickname").value("친구"))
                .andExpect(jsonPath("$.social[1].type").value("STARTED"))
                .andExpect(jsonPath("$.social[1].bookTitle").value("빠른책"))
                .andExpect(jsonPath("$.social[2].type").value("STARTED"))
                .andExpect(jsonPath("$.social[2].bookTitle").value("느린책"));
    }

    @Test
    @DisplayName("완독을 토글로 다시 찍어도 완독 소식의 시각·정렬 위치는 첫 완독 기준이다 (피드 재부상 차단)")
    void finishedEventUsesFirstFinishTime() throws Exception {
        User me = saveUser("hf-re@booktimer.com", "hfre", "나");
        User followee = saveUser("hf-reyou@booktimer.com", "hfreyou", "친구");
        followRepository.save(Follow.of(me, followee));
        Instant older = hoursAgo(240).truncatedTo(ChronoUnit.SECONDS);  // 10일 전 — 첫 완독
        Instant mid = hoursAgo(120).truncatedTo(ChronoUnit.SECONDS);    // 5일 전 — 완독 취소(읽는중)
        Instant recent = hoursAgo(24).truncatedTo(ChronoUnit.SECONDS);  // 1일 전 — 재완독
        Book toggled = Book.register(followee, "토글된 책", null, null, null, null, null,
                BookStatus.WANT_TO_READ);
        toggled.changeVisibility(BookVisibility.PUBLIC);
        toggled.changeStatus(BookStatus.FINISHED, older);
        toggled.changeStatus(BookStatus.READING, mid);
        toggled.changeStatus(BookStatus.FINISHED, recent);
        bookRepository.save(toggled);

        // 완독 소식이 어제 시각으로 나오면 옛 책이 피드 맨 위로 재부상한 것 — 5일 전 시작 소식보다 아래여야 한다.
        mockMvc.perform(get("/api/home-feed").with(user("hfre")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.social.length()").value(2))
                .andExpect(jsonPath("$.social[0].type").value("STARTED"))
                .andExpect(jsonPath("$.social[1].type").value("FINISHED"))
                .andExpect(jsonPath("$.social[1].occurredAt").value(older.toString()));
    }

    @Test
    @DisplayName("이벤트가 많아도 상한 30건까지만 준다 (최신순으로 자른다)")
    void capsAtThirtyEvents() throws Exception {
        User me = saveUser("hf-cap@booktimer.com", "hfcap", "나");
        User followee = saveUser("hf-capyou@booktimer.com", "hfcapyou", "친구");
        followRepository.save(Follow.of(me, followee));
        for (int i = 0; i < 16; i++) { // 16권 × (시작+완독) = 32 이벤트
            publicBook(followee, "책" + i, hoursAgo(100 + i), hoursAgo(50 + i));
        }

        mockMvc.perform(get("/api/home-feed").with(user("hfcap")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.social.length()").value(30))
                .andExpect(jsonPath("$.social[0].type").value("FINISHED")); // 가장 최근 = 완독
    }

    // ── 여백 소식 (STORY) — 사람+책 단위로 묶어 한 줄 ──────────────────────
    // 묶는 이유: 한 사람이 한 책에 세 장을 남기면 피드가 그 사람으로 도배되고, 상한 30을 묶기 전
    // 개수로 자르면 완독·시작 소식이 부당하게 밀린다. 그래서 묶기는 클라가 아니라 서버 몫이다.

    @Test
    @DisplayName("같은 사람·같은 책에 3장 → 1행(count 3, 발췌·시각은 최신 글 기준)")
    void groupsStoriesByPersonAndBook() throws Exception {
        User me = saveUser("hs-me@booktimer.com", "hsme", "나");
        User followee = saveUser("hs-you@booktimer.com", "hsyou", "친구");
        followRepository.save(Follow.of(me, followee));
        Book book = quietPublicBook(followee, "여백이 열린 책");
        storyAt(followee, book, "첫 글", hoursAgo(30));
        storyAt(followee, book, "둘째 글", hoursAgo(20));
        storyAt(followee, book, "가장 최근 글", hoursAgo(2));

        mockMvc.perform(get("/api/home-feed").with(user("hsme")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.social.length()").value(1))
                .andExpect(jsonPath("$.social[0].type").value("STORY"))
                .andExpect(jsonPath("$.social[0].loginId").value("hsyou"))
                .andExpect(jsonPath("$.social[0].bookTitle").value("여백이 열린 책"))
                .andExpect(jsonPath("$.social[0].bookId").value(book.getId()))
                .andExpect(jsonPath("$.social[0].count").value(3))
                .andExpect(jsonPath("$.social[0].excerpt").value("가장 최근 글"));
    }

    @Test
    @DisplayName("소식 발췌는 인용이 아니라 주석이다 — 남의 문장으로 피드를 채우지 않는다")
    void excerptUsesAnnotationNotQuote() throws Exception {
        User me = saveUser("hs-q-me@booktimer.com", "hsqme", "나");
        User followee = saveUser("hs-q-you@booktimer.com", "hsqyou", "친구");
        followRepository.save(Follow.of(me, followee));
        Book book = quietPublicBook(followee, "인용이 달린 책");
        storyRepository.save(Story.of(followee, "열아홉엔 몰랐다", book, null, "새는 알에서 나오려고 투쟁한다."));

        mockMvc.perform(get("/api/home-feed").with(user("hsqme")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.social[0].excerpt").value("열아홉엔 몰랐다"));
    }

    @Test
    @DisplayName("같은 사람이라도 책이 다르면 별개 행 (묶기 경계는 사람+책)")
    void doesNotGroupAcrossBooks() throws Exception {
        User me = saveUser("hs2-me@booktimer.com", "hs2me", "나");
        User followee = saveUser("hs2-you@booktimer.com", "hs2you", "친구");
        followRepository.save(Follow.of(me, followee));
        Book first = quietPublicBook(followee, "책 하나");
        Book second = quietPublicBook(followee, "책 둘");
        storyAt(followee, first, "하나의 글", hoursAgo(5));
        storyAt(followee, second, "둘의 글", hoursAgo(1));

        mockMvc.perform(get("/api/home-feed").with(user("hs2me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.social.length()").value(2))
                .andExpect(jsonPath("$.social[0].bookTitle").value("책 둘"))    // 최신순
                .andExpect(jsonPath("$.social[0].count").value(1))
                .andExpect(jsonPath("$.social[1].bookTitle").value("책 하나"));
    }

    @Test
    @DisplayName("글을 남긴 뒤 책을 비공개로 돌리면 소식에서 사라진다 (표시 시점 재검사 — 비공개 책장 누출 차단)")
    void excludesStoriesOnBooksTurnedPrivate() throws Exception {
        User me = saveUser("hs3-me@booktimer.com", "hs3me", "나");
        User followee = saveUser("hs3-you@booktimer.com", "hs3you", "친구");
        followRepository.save(Follow.of(me, followee));
        // PRIVATE 책엔 애초에 글을 못 남긴다(Story.of 불변식) — 누출 경로는 「남긴 뒤 비공개 전환」뿐이라
        // 쿼리가 표시 시점에 visibility를 다시 봐야 한다.
        Book book = quietPublicBook(followee, "나중에 비공개가 될 책");
        storyAt(followee, book, "새면 안 되는 문장", hoursAgo(1));
        book.makePrivate();
        bookRepository.save(book);

        mockMvc.perform(get("/api/home-feed").with(user("hs3me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.social").isEmpty());
    }

    @Test
    @DisplayName("창(14일) 밖 글은 소식에 안 나온다")
    void excludesStoriesOutsideWindow() throws Exception {
        User me = saveUser("hs4-me@booktimer.com", "hs4me", "나");
        User followee = saveUser("hs4-you@booktimer.com", "hs4you", "친구");
        followRepository.save(Follow.of(me, followee));
        Book book = quietPublicBook(followee, "오래된 책");
        storyAt(followee, book, "15일 전 글", hoursAgo(24 * 15));

        mockMvc.perform(get("/api/home-feed").with(user("hs4me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.social").isEmpty());
    }

    @Test
    @DisplayName("팔로우하지 않은 사람의 글은 안 나온다 — 내가 팔로우한 사람 글만 (쿼리 게이트)")
    void excludesStoriesOfStrangers() throws Exception {
        User me = saveUser("hs5-me@booktimer.com", "hs5me", "나");
        User friend = saveUser("hs5-friend@booktimer.com", "hs5friend", "친구");
        User stranger = saveUser("hs5-you@booktimer.com", "hs5you", "남");
        User someoneElse = saveUser("hs5-else@booktimer.com", "hs5else", "제3자");
        // 픽스처가 두 겹인 이유: ① 내 팔로우가 0건이면 어떤 게이트를 떼도 결과가 비어 테스트가
        // 공허해진다 ② 「남」을 아무도 팔로우하지 않으면 f.followee 조건만으로도 걸러져, 정작
        // 「내 팔로우인가」(f.follower = :viewer)를 떼도 안 잡힌다. 그래서 제3자가 남을 팔로우한다 —
        // 남의 팔로우가 내 피드를 열어 주면 그건 누출이다.
        followRepository.save(Follow.of(me, friend));
        followRepository.save(Follow.of(someoneElse, stranger));
        Book mine = quietPublicBook(friend, "친구 책");
        storyAt(friend, mine, "친구 글", hoursAgo(2));
        Book theirs = quietPublicBook(stranger, "남의 책");
        storyAt(stranger, theirs, "남의 글", hoursAgo(1));

        mockMvc.perform(get("/api/home-feed").with(user("hs5me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.social.length()").value(1))
                .andExpect(jsonPath("$.social[0].loginId").value("hs5friend"));
    }

    @Test
    @DisplayName("여백 소식이 완독·시작 소식과 최신순으로 한 목록에 섞인다")
    void mergesStoryEventsWithBookEvents() throws Exception {
        User me = saveUser("hs6-me@booktimer.com", "hs6me", "나");
        User followee = saveUser("hs6-you@booktimer.com", "hs6you", "친구");
        followRepository.save(Follow.of(me, followee));
        publicBook(followee, "완독한 책", hoursAgo(50), hoursAgo(10));
        Book margin = quietPublicBook(followee, "여백 책");
        storyAt(followee, margin, "가장 최근 사건", hoursAgo(1));

        mockMvc.perform(get("/api/home-feed").with(user("hs6me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.social.length()").value(3))
                .andExpect(jsonPath("$.social[0].type").value("STORY"))     // 1시간 전
                .andExpect(jsonPath("$.social[1].type").value("FINISHED"))  // 10시간 전
                .andExpect(jsonPath("$.social[2].type").value("STARTED"));  // 50시간 전
    }

    @Test
    @DisplayName("완독·시작 행은 여백 전용 필드를 갖지 않는다 (bookId null · count 0)")
    void bookEventsCarryNoMarginFields() throws Exception {
        User me = saveUser("hs7-me@booktimer.com", "hs7me", "나");
        User followee = saveUser("hs7-you@booktimer.com", "hs7you", "친구");
        followRepository.save(Follow.of(me, followee));
        publicBook(followee, "완독한 책", null, hoursAgo(1));

        mockMvc.perform(get("/api/home-feed").with(user("hs7me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.social[0].type").value("FINISHED"))
                .andExpect(jsonPath("$.social[0].bookId").doesNotExist())
                .andExpect(jsonPath("$.social[0].excerpt").doesNotExist())
                .andExpect(jsonPath("$.social[0].count").value(0));
    }

    @Test
    @DisplayName("차단하면 팔로우가 끊겨 그 사람의 여백 소식이 사라진다 (차단 불변식 행동 회귀)")
    void blockingRemovesStoriesFromFeed() throws Exception {
        User me = saveUser("hs8-me@booktimer.com", "hs8me", "나");
        User followee = saveUser("hs8-you@booktimer.com", "hs8you", "친구");
        followRepository.save(Follow.of(me, followee));
        Book book = quietPublicBook(followee, "차단 전 책");
        storyAt(followee, book, "차단 전 글", hoursAgo(1));
        mockMvc.perform(get("/api/home-feed").with(user("hs8me")))
                .andExpect(jsonPath("$.social.length()").value(1));

        // feedRecent 쿼리엔 차단 필터가 없다 — "팔로우 존재 → 차단 없음" write-시점 불변식에 기댄다.
        // 그 불변식(차단 시 팔로우 양방향 해제)이 깨지면 여기서 잡힌다.
        blockService.block(followee, me);

        mockMvc.perform(get("/api/home-feed").with(user("hs8me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.social").isEmpty());
    }

    // ── excerptOf — 페이로드 절단용 순수 함수(500자 × 30건 방지) ────────────

    @Test
    @DisplayName("excerptOf: 80자는 그대로, 81자는 79자 + 말줄임 (경계)")
    void excerptOf_truncatesAboveEighty() {
        String exactly80 = "가".repeat(80);
        String just81 = "나".repeat(81);

        org.assertj.core.api.Assertions.assertThat(HomeFeedApiController.excerptOf(exactly80))
                .isEqualTo(exactly80);
        org.assertj.core.api.Assertions.assertThat(HomeFeedApiController.excerptOf(just81))
                .isEqualTo("나".repeat(79) + "…")
                .hasSize(80);
    }
}
