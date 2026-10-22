package com.booktimer.web.api;

import com.booktimer.auth.ApiTokenService;
import com.booktimer.auth.AppleIdentityVerifier;
import com.booktimer.auth.AppleTokenRevoker;
import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.session.ReadingSession;
import com.booktimer.session.ReadingSessionRepository;
import com.booktimer.story.Story;
import com.booktimer.story.StoryRepository;
import com.booktimer.testsupport.MockedBoundaryTest;
import com.booktimer.user.AccountDeletionConfirmationException;
import com.booktimer.user.AccountService;
import com.booktimer.user.AppleUserProvisioningService;
import com.booktimer.user.WebAccountDeletionException;
import com.booktimer.user.AuthProvider;
import com.booktimer.user.User;
import com.booktimer.user.UserRegistrationService;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 앱 탈퇴 {@code POST /api/app/account/delete}(v1 REQ-07) — 실제 H2 스키마로 FK 순서까지 잰다.
 *
 * <p>핵심은 <b>연결 코드가 새도 웹 계정을 앱에서 지울 수 없다</b>는 것이다: 공격자는 자기 Apple 재인증을
 * 통과하지만 웹에서 만든 계정은 409로 막히고, 그때 철회도 부르지 않는다(아무것도 바꾸지 않는다).
 */
@MockedBoundaryTest
@Transactional
class AppAccountDeletionIntegrationTest {

    private static final String SUB = "000777.deadbeef.0007";

    @Autowired MockMvc mockMvc;
    @Autowired AppleIdentityVerifier verifier;
    @Autowired AppleTokenRevoker revoker;
    @Autowired AppleUserProvisioningService appleUsers;
    @Autowired AccountService accountService;
    @Autowired UserRegistrationService registrationService;
    @Autowired UserRepository userRepository;
    @Autowired ApiTokenService apiTokenService;
    @Autowired BookRepository bookRepository;
    @Autowired ReadingSessionRepository sessionRepository;
    @Autowired StoryRepository storyRepository;

    @BeforeEach
    void setUp() {
        when(verifier.isConfigured()).thenReturn(true);
    }

    private ResultActions delete(String token) throws Exception {
        return mockMvc.perform(post("/api/app/account/delete").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identityToken\":\"fresh\",\"authorizationCode\":\"code-1\"}"));
    }

    private void withChildren(User me) {
        Book book = Book.register(me, "앱에서 읽던 책", null, null, null, null, null, BookStatus.READING);
        book.makePublic(); // 여백은 공개 책에만 열린다(Story.of의 불변식)
        bookRepository.saveAndFlush(book);
        sessionRepository.saveAndFlush(ReadingSession.start(me, Instant.now().minusSeconds(600), book));
        storyRepository.saveAndFlush(Story.of(me, "여백 한 줄.", book, "paper"));
    }

    @Test
    @DisplayName("REQ-07 · 자식 데이터를 가진 Apple 계정이 FK 위반 없이 삭제된다(실 H2)")
    void appleAccountWithChildrenIsPurged() throws Exception {
        User me = appleUsers.register(SUB);
        withChildren(me);
        String token = apiTokenService.issue(me);
        String otherDevice = apiTokenService.issue(me);
        when(verifier.verify(any())).thenReturn(Optional.of(SUB));
        when(revoker.revoke(any())).thenReturn(true);

        delete(token).andExpect(status().isNoContent());

        assertThat(userRepository.findByAppleSub(SUB)).isEmpty();
        verify(revoker).revoke("code-1");
        mockMvc.perform(get("/api/dashboard").header("Authorization", "Bearer " + otherDevice))
                .andExpect(status().isUnauthorized()); // 다른 기기 토큰도 함께 죽는다
    }

    @Test
    @DisplayName("REQ-07 · 다른 Apple 신원의 탈퇴 요청은 403이고 아무것도 지우지 않는다")
    void otherSub403() throws Exception {
        User me = appleUsers.register(SUB);
        withChildren(me);
        String token = apiTokenService.issue(me);
        when(verifier.verify(any())).thenReturn(Optional.of("someone.else.1"));
        long books = bookRepository.count();

        delete(token).andExpect(status().isForbidden());

        assertThat(userRepository.findByAppleSub(SUB)).isPresent();
        assertThat(bookRepository.count()).isEqualTo(books);
        verify(revoker, never()).revoke(any());
        mockMvc.perform(get("/api/dashboard").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("REQ-07 · 웹에서 만든 계정의 앱 탈퇴는 409이고 행·토큰이 그대로이며 철회를 부르지 않는다")
    void webAccount409() throws Exception {
        User web = registrationService.registerOAuth("victim-web@booktimer.com", "구글러", "Asia/Seoul",
                AuthProvider.GOOGLE, LocalDate.of(2026, 10, 21));
        appleUsers.link(web, SUB); // 연결 코드가 샜다 — 공격자 Apple ID가 붙었다
        withChildren(web);
        String token = apiTokenService.issue(web);
        when(verifier.verify(any())).thenReturn(Optional.of(SUB)); // 공격자는 자기 Apple 재인증을 통과한다

        delete(token).andExpect(status().isConflict())
                .andExpect(content().string("웹에서 만든 계정은 웹 설정에서 지울 수 있어요"));

        assertThat(userRepository.findById(web.getId())).isPresent();
        verify(revoker, never()).revoke(any());
        mockMvc.perform(get("/api/dashboard").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("REQ-07 · 서비스도 한 번 더 대조한다 — 웹 계정·다른 sub는 컨트롤러를 거치지 않아도 지우지 않는다")
    void serviceDoubleChecks() {
        User web = registrationService.registerOAuth("svc-web@booktimer.com", "구글러", "Asia/Seoul",
                AuthProvider.GOOGLE, LocalDate.of(2026, 10, 21));
        appleUsers.link(web, SUB);
        User app = appleUsers.register("000888.app.0008");

        assertThatThrownBy(() -> accountService.deleteAppleVerifiedAccount(web, SUB))
                .isInstanceOf(WebAccountDeletionException.class);
        assertThatThrownBy(() -> accountService.deleteAppleVerifiedAccount(app, "someone.else.1"))
                .isInstanceOf(AccountDeletionConfirmationException.class);
        assertThat(userRepository.findById(web.getId())).isPresent();
        assertThat(userRepository.findById(app.getId())).isPresent();
    }

    @Test
    @DisplayName("REQ-07 · Apple 철회가 실패해도 탈퇴는 204")
    void revokeFailureStillDeletes() throws Exception {
        User me = appleUsers.register(SUB);
        String token = apiTokenService.issue(me);
        when(verifier.verify(any())).thenReturn(Optional.of(SUB));
        when(revoker.revoke(any())).thenReturn(false);

        delete(token).andExpect(status().isNoContent());
        assertThat(userRepository.findByAppleSub(SUB)).isEmpty();
    }

    @Test
    @DisplayName("REQ-07 · Apple이 재인증 토큰을 거부하면 400(401이 아니다 — 앱의 401은 로그아웃이다)이고 지우지 않는다")
    void reauthRejected400() throws Exception {
        User me = appleUsers.register(SUB);
        String token = apiTokenService.issue(me);
        when(verifier.verify(any())).thenReturn(Optional.empty());

        delete(token).andExpect(status().isBadRequest());
        assertThat(userRepository.findByAppleSub(SUB)).isPresent();
    }
}
