package com.booktimer.book;

import com.booktimer.toss.TossMessengerClient;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 완독 <b>전환</b>에만 축하 푸시가 나가는지 — 이 클래스가 지키는 건 "언제 보내는가"다.
 *
 * <p>이 테스트가 단독으로 잡는 실패:
 * <ul>
 *   <li>중복 방지를 {@code finishedAt} 유무로 짜서 재저장마다 푸시가 또 나가는 것(FINISHED→FINISHED),</li>
 *   <li>등록-시-완독({@code addFromSearch}에 FINISHED)에도 축하가 붙어, 과거에 읽은 책 N권을 아카이빙할 때
 *       푸시 N발이 쏟아지는 것,</li>
 *   <li>발송 실패가 완독 처리를 되돌리는 것.</li>
 * </ul>
 *
 * <p>{@code TossMessengerClient}를 mock으로 등록해 게이트 OFF(빈 없음) 상태를 우회하고, 실제
 * {@link FinishCelebrationService} 배선을 그대로 통과시킨다 — 전환 감지부터 발송까지가 한 줄로 이어지는지 본다.
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = "booktimer.toss.messenger.finish-template-code=FINISH_CELEBRATION")
class BookServiceCelebrationTest {

    @Autowired
    private BookService bookService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private TossMessengerClient messengerClient;

    private User tossUser(String email) {
        User user = User.of(email, passwordEncoder.encode("rawpw1234"), "독자", "Asia/Seoul", Role.USER);
        user.linkTossUserKey("uk-" + email);
        return userRepository.save(user);
    }

    private static BookSearchResult cleanCode() {
        return new BookSearchResult("클린 코드", "로버트 마틴", "9788966260959", null, "인사이트", null);
    }

    @Test
    @DisplayName("읽는 중 → 완독 전환이면 축하 푸시 1회")
    void readingToFinished_celebrates() {
        User u = tossUser("t1@booktimer.com");
        Book book = bookService.addFromSearch(u, cleanCode(), BookStatus.READING);

        bookService.changeStatus(u, book.getId(), BookStatus.FINISHED);

        verify(messengerClient, times(1))
                .sendMessage(eq("uk-t1@booktimer.com"), eq("FINISH_CELEBRATION"), any());
    }

    @Test
    @DisplayName("이미 완독인 책을 다시 완독으로 저장해도 추가 발송 없음 — 중복 기준은 전이지 finishedAt이 아니다")
    void finishedToFinished_doesNotCelebrateAgain() {
        User u = tossUser("t2@booktimer.com");
        Book book = bookService.addFromSearch(u, cleanCode(), BookStatus.READING);
        bookService.changeStatus(u, book.getId(), BookStatus.FINISHED);

        bookService.changeStatus(u, book.getId(), BookStatus.FINISHED);

        verify(messengerClient, times(1)).sendMessage(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("완독 → 읽는 중 → 완독이면 다시 축하 (재완독은 허용 — 빈도 낮고 무해)")
    void refinished_celebratesAgain() {
        User u = tossUser("t3@booktimer.com");
        Book book = bookService.addFromSearch(u, cleanCode(), BookStatus.READING);
        bookService.changeStatus(u, book.getId(), BookStatus.FINISHED);
        bookService.changeStatus(u, book.getId(), BookStatus.READING);

        bookService.changeStatus(u, book.getId(), BookStatus.FINISHED);

        verify(messengerClient, times(2)).sendMessage(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("완독 상태로 등록하는 경로는 축하 제외 — 서재 아카이빙에 푸시가 쏟아지지 않게")
    void registeringAsFinished_doesNotCelebrate() {
        User u = tossUser("t4@booktimer.com");

        bookService.addFromSearch(u, cleanCode(), BookStatus.FINISHED);
        bookService.addManual(u, "직접 등록한 책", "저자", BookStatus.FINISHED);

        verify(messengerClient, never()).sendMessage(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("발송이 터져도 완독은 저장된다 — 축하가 완독을 되돌리지 않는다")
    void sendFailure_doesNotBlockFinishing() {
        User u = tossUser("t5@booktimer.com");
        Book book = bookService.addFromSearch(u, cleanCode(), BookStatus.READING);
        when(messengerClient.sendMessage(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("toss down"));

        Book finished = bookService.changeStatus(u, book.getId(), BookStatus.FINISHED);

        assertThat(finished.getStatus()).isEqualTo(BookStatus.FINISHED);
        assertThat(finished.getFinishedAt()).isNotNull();
    }
}
