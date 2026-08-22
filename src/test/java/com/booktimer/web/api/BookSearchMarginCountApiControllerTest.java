package com.booktimer.web.api;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookSearchClient;
import com.booktimer.book.BookSearchPage;
import com.booktimer.book.BookSearchResult;
import com.booktimer.book.BookSearchType;
import com.booktimer.book.BookStatus;
import com.booktimer.story.Story;
import com.booktimer.story.StoryRepository;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRegistrationService;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/books/search — 검색 행의 「여백 N」 배지 집계({@code marginCounts}).
 *
 * <p>{@code BookSearchClient}를 mock으로 갈아 끼우므로 다른 API 테스트의 검색 동작에 손대지 않게
 * 별도 클래스로 뒀다({@code BookRecommendApiControllerTest}와 같은 이유).
 *
 * <p>여기서 재는 것 둘: ① 배지 숫자가 응답에 실리는가 ② <b>페이지당 집계 쿼리가 1회인가</b>.
 * ②를 재지 않으면 행마다 세는 N+1이 조용히 들어와도 응답만 보고는 알 수 없다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BookSearchMarginCountApiControllerTest {

    private static final String SEOUL = "Asia/Seoul";
    private static final String ISBN_A = "9791168340084";
    private static final String ISBN_B = "9788954699914";

    @Autowired MockMvc mockMvc;
    @Autowired UserRegistrationService registrationService;
    @Autowired UserRepository userRepository;
    @Autowired BookRepository bookRepository;
    @Autowired Clock clock;

    @MockitoBean BookSearchClient searchClient;
    @MockitoSpyBean StoryRepository storyRepository;

    private User register(String email, String loginId, String nickname) {
        registrationService.register(email, "pw1234qwer!!", loginId, nickname, SEOUL, Role.USER,
                LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL)));
        return userRepository.findByLoginId(loginId).orElseThrow();
    }

    private Book publicBookWithIsbn(User owner, String title, String isbn) {
        Book book = Book.register(owner, title, "저자", isbn, null, null, null, BookStatus.READING);
        book.makePublic();
        return bookRepository.save(book);
    }

    private void sharedStory(User author, Book book, String text) {
        Story story = Story.of(author, text, book, null);
        story.markShared(true);
        storyRepository.save(story);
    }

    private void stubSearchWith(String... isbns) {
        List<BookSearchResult> results = java.util.Arrays.stream(isbns)
                .map(isbn -> new BookSearchResult("책 " + isbn, "저자", isbn, null, "출판사", null))
                .toList();
        when(searchClient.search(any(), any(BookSearchType.class), anyInt()))
                .thenReturn(new BookSearchPage(results, 1, results.size(), results.size()));
    }

    @Test
    @DisplayName("검색 응답에 isbn별 「여백 N」이 실린다 — 함께 걸린 글이 없는 책은 키 자체가 없다(배지 미표시)")
    void search_carriesMarginCounts() throws Exception {
        User author = register("margincount-author@booktimer.com", "margincountauthor", "글쓴이");
        register("margincount-viewer@booktimer.com", "margincountviewer", "보는이");
        Book bookA = publicBookWithIsbn(author, "함께 걸린 책", ISBN_A);
        sharedStory(author, bookA, "함께 건 글 1");
        sharedStory(author, bookA, "함께 건 글 2");
        Book bookB = publicBookWithIsbn(author, "안 걸린 책", ISBN_B);
        storyRepository.save(Story.of(author, "안 건 글", bookB, null));
        stubSearchWith(ISBN_A, ISBN_B);

        mockMvc.perform(get("/api/books/search").param("q", "책")
                        .with(user("margincount-viewer@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marginCounts." + ISBN_A).value(2))
                .andExpect(jsonPath("$.marginCounts." + ISBN_B).doesNotExist());
    }

    @Test
    @DisplayName("배지 집계는 페이지당 1쿼리다 — 행마다 세는 N+1 금지")
    void search_aggregatesInOneQuery() throws Exception {
        register("margincount-solo@booktimer.com", "margincountsolo", "보는이");
        stubSearchWith(ISBN_A, ISBN_B, "9788936434120");

        mockMvc.perform(get("/api/books/search").param("q", "책")
                        .with(user("margincount-solo@booktimer.com")))
                .andExpect(status().isOk());

        verify(storyRepository, times(1)).sharedCountsByIsbn(any());
    }
}
