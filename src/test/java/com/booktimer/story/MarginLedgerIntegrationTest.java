package com.booktimer.story;

import com.booktimer.auth.ApiTokenService;
import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookService;
import com.booktimer.book.BookStatus;
import com.booktimer.follow.Follow;
import com.booktimer.follow.FollowRepository;
import com.booktimer.security.RateLimitService;
import com.booktimer.user.AccountService;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRegistrationService;
import com.booktimer.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 책생글 M1 — 여백은 본인만(REQ-212) · 책을 지워도 쪽지는 남는다(REQ-219). 실 H2 · 실 빈 · 웹 세션.
 *
 * <p>REQ-212 픽스처는 <b>null-state를 섞는다</b>(CLAUDE.md 「테스트 깊이」): 생각 있는 여백(공개 책 · 「함께 걸기」 켜 둔 옛 행) ·
 * 인용만 남긴 여백 · 비공개 책 여백 · 책 없는 쪽지. 남에게는 경로마다 0건 · 404이고, 본인에게는 책 있는 셋이 보인다(양성 대조).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MarginLedgerIntegrationTest {

    private static final String SEOUL = "Asia/Seoul";
    private static final String ISBN = "9788937460449";

    @Autowired MockMvc mvc;
    @Autowired UserRegistrationService registrationService;
    @Autowired UserRepository userRepository;
    @Autowired BookRepository bookRepository;
    @Autowired BookService bookService;
    @Autowired StoryRepository storyRepository;
    @Autowired StoryLikeRepository storyLikeRepository;
    @Autowired FollowRepository followRepository;
    @Autowired AccountService accountService;
    @Autowired ApiTokenService apiTokenService;
    @Autowired RateLimitService rateLimitService;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired Clock clock;

    @BeforeEach
    void clearRateLimits() {
        rateLimitService.clearForTest();
    }

    private User register(String loginId) {
        registrationService.register(loginId + "@booktimer.com", "pw1234qwer!!", loginId, "닉_" + loginId, SEOUL, Role.USER,
                LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL)));
        return userRepository.findByLoginId(loginId).orElseThrow();
    }

    private Book book(User owner, String title, boolean isPublic) {
        Book b = Book.register(owner, title, "헤르만 헤세", ISBN, null, null, null, BookStatus.READING);
        if (isPublic) {
            b.makePublic();
        }
        return bookRepository.saveAndFlush(b);
    }

    @Nested
    @DisplayName("REQ-212 · 여백은 본인만")
    class OwnerOnly {

        User owner;
        User viewer;
        Book pub;
        Story thought;
        Story quoteOnly;
        Story onPrivate;
        Story bookless;

        @BeforeEach
        void fixture() {
            owner = register("mpowner");
            viewer = register("mpviewer");
            followRepository.saveAndFlush(Follow.of(viewer, owner)); // 소식(feedRecent)은 팔로우한 사람만 — 가장 넓게 열린 관계
            pub = book(owner, "데미안", true);
            Book priv = book(owner, "비공개 책", false);
            thought = Story.of(owner, "공개 책에 남긴 생각", pub, null);
            thought.markShared(true); // 옛 「함께 걸기」 행 — 읽기가 shared를 보지 않음을 함께 잰다
            thought = storyRepository.saveAndFlush(thought);
            quoteOnly = storyRepository.saveAndFlush(Story.of(owner, "", pub, null, "새는 알에서 나오려고 투쟁한다"));
            onPrivate = storyRepository.saveAndFlush(Story.of(owner, "비공개 책의 메모", priv, null));
            bookless = storyRepository.saveAndFlush(Story.of(owner, "책 없는 쪽지", null, null));
            book(viewer, "내 데미안", true); // 책축 경로의 머리(같은 ISBN의 내 책)
        }

        @Test
        @DisplayName("REQ-212 · 공개 책의 생각 있는 여백도 남에게 0건 — 사람축(책 하나 · 전체) · 책축(양성 대조: 본인은 보인다)")
        void readPathsAreEmptyForOthers() throws Exception {
            mvc.perform(get("/api/stories/of/mpowner").param("bookId", pub.getId().toString()).with(user("mpviewer@booktimer.com")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.entries", hasSize(0)));
            mvc.perform(get("/api/stories/of/mpowner/all").with(user("mpviewer@booktimer.com")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
            mvc.perform(get("/api/stories/book/" + ISBN).with(user("mpviewer@booktimer.com")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.entries", hasSize(0)))
                    .andExpect(jsonPath("$.totalCount").value(0));

            mvc.perform(get("/api/stories/of/mpowner/all").with(user("mpowner@booktimer.com")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(3)))
                    .andExpect(jsonPath("$[*].id", not(hasItem(bookless.getId().intValue()))));
            mvc.perform(get("/api/stories/of/mpowner").param("bookId", pub.getId().toString()).with(user("mpowner@booktimer.com")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.entries", hasSize(2)));
        }

        @Test
        @DisplayName("REQ-212 · 홈 소식에 남의 여백 항목 0 · 「여백」 탭 0")
        void homeFeedHasNoMargins() throws Exception {
            mvc.perform(get("/api/home-feed").with(user("mpviewer@booktimer.com")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.social[*].type", not(hasItem("STORY"))))
                    .andExpect(jsonPath("$.discover", hasSize(0)));
        }

        @Test
        @DisplayName("REQ-212 · 좋아요 · 명단 · 좋아요 취소 — 남의 여백은 404(양성 대조: 내 여백엔 누를 수 있다)")
        void likePathsAre404ForOthers() throws Exception {
            mvc.perform(post("/api/stories/" + thought.getId() + "/like").with(user("mpviewer@booktimer.com")).with(csrf()))
                    .andExpect(status().isNotFound());
            mvc.perform(get("/api/stories/" + thought.getId() + "/likes").with(user("mpviewer@booktimer.com")))
                    .andExpect(status().isNotFound());
            storyLikeRepository.saveAndFlush(StoryLike.of(viewer, thought)); // M1 전 운영에 남았을 수 있는 행
            mvc.perform(delete("/api/stories/" + thought.getId() + "/like").with(user("mpviewer@booktimer.com")).with(csrf()))
                    .andExpect(status().isNotFound());

            mvc.perform(post("/api/stories/" + thought.getId() + "/like").with(user("mpowner@booktimer.com")).with(csrf()))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("REQ-212 · 프로필 발광(recencyOf) — 남에게 빈 맵(양성 대조: 본인 프로필엔 마지막 글 시각)")
        void profileRecencyIsEmptyForOthers() throws Exception {
            mvc.perform(get("/api/profile").param("loginId", "mpowner").with(user("mpviewer@booktimer.com")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.books", hasSize(1)))
                    .andExpect(jsonPath("$.books[*].lastStoryAt", everyItem(nullValue())));
            mvc.perform(get("/api/profile/books").param("loginId", "mpowner").with(user("mpviewer@booktimer.com")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.books[*].lastStoryAt", everyItem(nullValue())));

            mvc.perform(get("/api/profile").param("loginId", "mpowner").with(user("mpowner@booktimer.com")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.books[0].lastStoryAt", notNullValue()));
        }
    }

    @Nested
    @DisplayName("REQ-219 · 책을 지워도 쪽지는 남는다")
    class BookDeletion {

        @Test
        @DisplayName("REQ-219 · 책 지우기 뒤 쪽지 남고 book null(실 H2 — 쪽지 · 좋아요를 가진 책, FK 위반 0)")
        void deletingBookUnlinksNotes() {
            User me = register("mdel");
            Book b = book(me, "데미안", true);
            Story note = storyRepository.saveAndFlush(Story.of(me, "남아야 할 생각", b, null));
            storyLikeRepository.saveAndFlush(StoryLike.of(me, note));

            assertThatCode(() -> {
                bookService.delete(me, b.getId());
                em.flush();
            }).doesNotThrowAnyException();
            em.clear();

            assertThat(bookRepository.findById(b.getId())).isEmpty();
            Story after = storyRepository.findById(note.getId()).orElseThrow();
            assertThat(after.getText()).isEqualTo("남아야 할 생각");
            assertThat(after.getBook()).isNull();
        }

        @Test
        @DisplayName("REQ-219 · 책을 지운 쪽지의 quote_source = 『제목』 · 저자 — 비어 있던 행만 채우고 있던 값은 그대로")
        void deletingBookKeepsQuoteSource() {
            User me = register("mdelq");
            Book b = book(me, "데미안", true);
            Story empty = storyRepository.saveAndFlush(Story.of(me, "", b, null, "새는 알에서 나오려고 투쟁한다"));
            Story kept = storyRepository.saveAndFlush(Story.of(me, "", b, null, "알은 세계다"));
            Story plain = storyRepository.saveAndFlush(Story.of(me, "인용 없는 생각", b, null));
            jdbc.update("update story set quote_source = null where id = ?", empty.getId()); // M1 전에 생긴 행
            jdbc.update("update story set quote_source = '『다른 판』 · 옛 출처' where id = ?", kept.getId());

            bookService.delete(me, b.getId());
            em.flush();
            em.clear();

            assertThat(storyRepository.findById(empty.getId()).orElseThrow().getQuoteSource()).isEqualTo("『데미안』 · 헤르만 헤세");
            assertThat(storyRepository.findById(kept.getId()).orElseThrow().getQuoteSource()).isEqualTo("『다른 판』 · 옛 출처");
            assertThat(storyRepository.findById(plain.getId()).orElseThrow().getQuoteSource()).isNull();
        }

        @Test
        @DisplayName("REQ-219 · 책 없는 쪽지가 ?book=none에 · /api/stories/of/{me}/all 200 · 그 쪽지 없음(양성 대조: 책 있는 쪽지는 있음)")
        void unlinkedNoteLeavesWebListsButReachesApp() throws Exception {
            User me = register("mdelw");
            Book gone = book(me, "지울 책", true);
            Book stays = book(me, "남는 책", true);
            Story orphan = storyRepository.saveAndFlush(Story.of(me, "책이 사라진 쪽지", gone, null));
            Story kept = storyRepository.saveAndFlush(Story.of(me, "책 있는 쪽지", stays, null));
            bookService.delete(me, gone.getId());
            em.flush();
            em.clear();

            mvc.perform(get("/api/stories/of/mdelw/all").with(user("mdelw@booktimer.com")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].id", not(hasItem(orphan.getId().intValue()))))
                    .andExpect(jsonPath("$[*].id", hasItem(kept.getId().intValue())));
            mvc.perform(get("/api/app/margins").param("book", "none").header("Authorization", "Bearer " + apiTokenService.issue(me)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].id", hasItem(orphan.getId().intValue())));
        }

        @Test
        @DisplayName("REQ-219 · 옛 API text ≤ 500자(「…」) · 생각 · 옮긴 문장이 다 빈 쪽지 뺌(양성 대조: 1만 자 쪽지는 저장돼 있다 · 인용만 쪽지는 보인다)")
        void legacyApiTruncatesAndHidesEmpty() throws Exception {
            User me = register("mlegacy");
            Book b = book(me, "데미안", true);
            Story longNote = storyRepository.saveAndFlush(Story.of(me, "가".repeat(10_000), b, null));
            Story quoteOnly = storyRepository.saveAndFlush(Story.of(me, "", b, null, "새는 알에서 나오려고 투쟁한다"));
            Story aiOnly = storyRepository.saveAndFlush(Story.of(me, "옮긴 글", b, null));
            aiOnly.recordRound("{\"rounds\":[]}", 0, Instant.parse("2026-11-23T00:00:00Z"));
            aiOnly.edit(null, "", aiOnly.getRevision());
            storyRepository.saveAndFlush(aiOnly);
            assertThat(storyRepository.findById(longNote.getId()).orElseThrow().getText()).hasSize(10_000);

            mvc.perform(get("/api/stories/of/mlegacy/all").with(user("mlegacy@booktimer.com")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[?(@.id == " + longNote.getId() + ")].text", everyItem(endsWith("…"))))
                    .andExpect(jsonPath("$[*].id", hasItem(quoteOnly.getId().intValue())))
                    .andExpect(jsonPath("$[*].id", not(hasItem(aiOnly.getId().intValue()))));
            String body = mvc.perform(get("/api/stories/of/mlegacy").param("bookId", b.getId().toString())
                            .with(user("mlegacy@booktimer.com")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.entries", hasSize(2)))
                    .andReturn().getResponse().getContentAsString();
            String text = body.replaceAll("(?s).*\"text\":\"(가+…)\".*", "$1");
            assertThat(text).hasSize(500).endsWith("…");
        }

        @Test
        @DisplayName("REQ-219 · 웹 · 미니앱 POST /api/stories에 bookId 없으면 400 그대로")
        void legacyCreateStillNeedsBook() throws Exception {
            register("mlegacypost");
            long before = storyRepository.count();
            mvc.perform(post("/api/stories").with(user("mlegacypost@booktimer.com")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"책 없는 웹 글\"}"))
                    .andExpect(status().isBadRequest());
            assertThat(storyRepository.count()).isEqualTo(before);
        }

        @Test
        @DisplayName("M1 · 책 없는 쪽지 · 책 있는 쪽지를 가진 계정의 탈퇴가 FK 위반 없이 정리된다(실 H2)")
        void purgeWithBooklessNotes() {
            User me = register("mpurge");
            Book b = book(me, "데미안", true);
            Story onBook = storyRepository.saveAndFlush(Story.of(me, "책 쪽지", b, null));
            storyRepository.saveAndFlush(Story.of(me, "책 없는 쪽지", null, null));
            storyLikeRepository.saveAndFlush(StoryLike.of(me, onBook));

            assertThatCode(() -> {
                accountService.deleteAccount("mpurge@booktimer.com", "pw1234qwer!!");
                em.flush();
            }).doesNotThrowAnyException();
            assertThat(userRepository.findByLoginId("mpurge")).isEmpty();
            assertThat(jdbc.queryForObject("select count(*) from story where user_id = ?", Long.class, me.getId())).isZero();
        }
    }
}
