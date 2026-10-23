package com.booktimer.web.api;

import com.booktimer.auth.ApiTokenService;
import com.booktimer.auth.AppleIdentityVerifier;
import com.booktimer.auth.AppleTokenRevoker;
import com.booktimer.auth.IdTokenVerifier;
import com.booktimer.auth.IdTokenVerifier.GoogleIdentity;
import com.booktimer.user.KakaoUserProvisioningService;
import com.booktimer.user.Role;
import com.booktimer.user.VerifiedIdentity;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.Map;
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
    @Autowired IdTokenVerifier idTokens;
    @Autowired KakaoUserProvisioningService kakaoUsers;
    @Autowired JdbcTemplate jdbcTemplate;
    @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager entityManager;

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

    // ── 구글 · 카카오로 확인하는 탈퇴 (PR-M — REQ-83 · 111) ─────────────────────

    private static final String KAKAO_SUB = "4012345678";
    private static final String GOOGLE_SUB = "109876543210000000003";

    private ResultActions deleteVia(String token, String provider, String appleToken, String appleCode) throws Exception {
        String json = "{\"provider\":\"" + provider + "\",\"identityToken\":\"fresh\""
                + (appleToken == null ? "" : ",\"appleIdentityToken\":\"" + appleToken + "\"")
                + (appleCode == null ? "" : ",\"appleAuthorizationCode\":\"" + appleCode + "\"") + "}";
        return mockMvc.perform(post("/api/app/account/delete").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private void googleSays(String sub, String email, String hd) {
        when(idTokens.verifyGoogle(any())).thenReturn(Optional.of(new GoogleIdentity(sub, email, "읽는사람", hd)));
    }

    private User verifiedEmailAccount(String email, AuthProvider provider) {
        User u = provider == AuthProvider.LOCAL
                ? User.of(email, "hash", "주인", "Asia/Seoul", Role.USER)
                : User.ofOAuth(email, "주인", "Asia/Seoul", Role.USER, provider);
        u.verifyEmail();
        return userRepository.saveAndFlush(u);
    }

    private User googleAccount(String email) {
        return registrationService.registerOAuth(email, "구글러", "Asia/Seoul", AuthProvider.GOOGLE, LocalDate.of(2026, 10, 21));
    }

    private void pin(User u, String sub) {
        jdbcTemplate.update("update users set google_sub = ? where id = ?", sub, u.getId());
        entityManager.clear(); // 영속성 컨텍스트의 낡은 인스턴스(google_sub=null)가 판정에 섞이지 않게
    }

    private Map<String, Object> row(Long id) {
        return jdbcTemplate.queryForMap("select * from users where id = ?", id);
    }

    @Test
    @DisplayName("REQ-83 · 카카오 계정은 카카오로 확인하면 지운다")
    void kakaoAccountDeletedByKakao() throws Exception {
        User me = kakaoUsers.register(KAKAO_SUB);
        withChildren(me);
        String token = apiTokenService.issue(me);
        when(idTokens.verifyKakao(any())).thenReturn(Optional.of(KAKAO_SUB));

        deleteVia(token, "KAKAO", null, null).andExpect(status().isNoContent());

        assertThat(userRepository.findByKakaoSub(KAKAO_SUB)).isEmpty();
        verify(revoker, never()).revoke(any()); // 서버 쪽 외부 호출 0 — 카카오 연결 끊기는 앱 SDK 몫
    }

    @Test
    @DisplayName("REQ-83 · 다른 카카오 회원번호면 403이고 아무것도 안 지운다")
    void otherKakaoSub403() throws Exception {
        User me = kakaoUsers.register(KAKAO_SUB);
        withChildren(me);
        String token = apiTokenService.issue(me);
        long books = bookRepository.count();
        when(idTokens.verifyKakao(any())).thenReturn(Optional.of("4099999999"));

        deleteVia(token, "KAKAO", null, null).andExpect(status().isForbidden());

        assertThat(userRepository.findByKakaoSub(KAKAO_SUB)).isPresent();
        assertThat(bookRepository.count()).isEqualTo(books);
    }

    @Test
    @DisplayName("REQ-83 · 웹 계정에 붙인 Apple로 확인하면 409이고 아무것도 안 지운다")
    void webAccountWithAppleProvider409() throws Exception {
        User web = googleAccount("apple-on-web@gmail.com");
        appleUsers.link(web, SUB);
        String token = apiTokenService.issue(web);
        when(verifier.verify(any())).thenReturn(Optional.of(SUB));

        deleteVia(token, "APPLE", null, null).andExpect(status().isConflict());

        assertThat(userRepository.findById(web.getId())).isPresent();
        verify(revoker, never()).revoke(any());
    }

    @Test
    @DisplayName("REQ-111 · Apple이 붙은 구글 계정을 구글로 지우면 동봉한 Apple 코드로 철회한다")
    void googleWithAppleRevokesWithEnclosedCode() throws Exception {
        User web = googleAccount("both@gmail.com");
        appleUsers.link(web, SUB);
        withChildren(web);
        String token = apiTokenService.issue(web);
        googleSays(GOOGLE_SUB, "both@gmail.com", null);
        when(verifier.verify("apple-fresh")).thenReturn(Optional.of(SUB));
        when(revoker.revoke(any())).thenReturn(true);

        deleteVia(token, "GOOGLE", "apple-fresh", "apple-code").andExpect(status().isNoContent());

        assertThat(userRepository.findById(web.getId())).isEmpty();
        verify(revoker).revoke("apple-code");
    }

    @Test
    @DisplayName("REQ-111 · Apple 코드가 없거나 다른 Apple ID면 철회만 건너뛰고 지운다")
    void appleRevokeSkippedWithoutMatchingAppleToken() throws Exception {
        User noToken = googleAccount("no-apple-token@gmail.com");
        appleUsers.link(noToken, SUB);
        googleSays(GOOGLE_SUB, "no-apple-token@gmail.com", null);
        deleteVia(apiTokenService.issue(noToken), "GOOGLE", null, null).andExpect(status().isNoContent());
        assertThat(userRepository.findById(noToken.getId())).isEmpty();

        User otherApple = googleAccount("other-apple@gmail.com");
        appleUsers.link(otherApple, "000111.mine.0001");
        googleSays("109876543210000000004", "other-apple@gmail.com", null);
        when(verifier.verify("someone-elses-apple")).thenReturn(Optional.of("000222.theirs.0002"));
        deleteVia(apiTokenService.issue(otherApple), "GOOGLE", "someone-elses-apple", "their-code")
                .andExpect(status().isNoContent());
        assertThat(userRepository.findById(otherApple.getId())).isEmpty();

        verify(revoker, never()).revoke(any());
    }

    @Test
    @DisplayName("REQ-83 · 구글 가입 계정과 gmail·워크스페이스 주소의 이메일 가입 계정은 구글로 확인하면 지운다")
    void googleVouchedAccountsDeletedByGoogle() throws Exception {
        User google = googleAccount("plain@booktimer.com"); // 구글 가입은 주소와 무관
        googleSays(GOOGLE_SUB, "plain@booktimer.com", null);
        deleteVia(apiTokenService.issue(google), "GOOGLE", null, null).andExpect(status().isNoContent());
        assertThat(userRepository.findById(google.getId())).isEmpty();

        User gmailLocal = verifiedEmailAccount("local@gmail.com", AuthProvider.LOCAL); // 고정 없음
        googleSays("109876543210000000005", "local@gmail.com", null);
        deleteVia(apiTokenService.issue(gmailLocal), "GOOGLE", null, null).andExpect(status().isNoContent());
        assertThat(userRepository.findById(gmailLocal.getId())).isEmpty();

        User workspace = verifiedEmailAccount("me@school.ac.kr", AuthProvider.LOCAL);
        googleSays("109876543210000000006", "me@school.ac.kr", "school.ac.kr");
        deleteVia(apiTokenService.issue(workspace), "GOOGLE", null, null).andExpect(status().isNoContent());
        assertThat(userRepository.findById(workspace.getId())).isEmpty();
    }

    @Test
    @DisplayName("REQ-83 · 고정된 sub로 오면 토큰 이메일이 달라도 지운다")
    void pinnedSubWithOtherEmailDeletes() throws Exception {
        User google = googleAccount("old-address@gmail.com");
        pin(google, GOOGLE_SUB);
        googleSays(GOOGLE_SUB, "new-address@gmail.com", null);

        deleteVia(apiTokenService.issue(google), "GOOGLE", null, null).andExpect(status().isNoContent());
        assertThat(userRepository.findById(google.getId())).isEmpty();
    }

    @Test
    @DisplayName("REQ-83 · 구글이 보증하지 않는 주소의 이메일 가입 계정은 구글로 확인해도 403이고 아무것도 안 지운다(LOCAL · TOSS)")
    void unvouchedEmailAccounts403() throws Exception {
        User local = verifiedEmailAccount("me@example.com", AuthProvider.LOCAL);
        Map<String, Object> localBefore = row(local.getId());
        googleSays(GOOGLE_SUB, "me@example.com", null);
        deleteVia(apiTokenService.issue(local), "GOOGLE", null, null).andExpect(status().isForbidden());
        assertThat(row(local.getId())).isEqualTo(localBefore); // 행 그대로 · 고정 0

        User toss = verifiedEmailAccount("toss@example.com", AuthProvider.TOSS);
        googleSays("109876543210000000007", "toss@example.com", null);
        deleteVia(apiTokenService.issue(toss), "GOOGLE", null, null).andExpect(status().isForbidden());
        assertThat(userRepository.findById(toss.getId())).isPresent();

        // 서비스를 직접 불러도 지우지 않는다(2중 게이트)
        assertThatThrownBy(() -> accountService.deleteProviderVerifiedAccount(toss, AuthProvider.GOOGLE,
                new VerifiedIdentity("109876543210000000007", "toss@example.com", null)))
                .isInstanceOf(AccountDeletionConfirmationException.class);
        assertThat(userRepository.findById(toss.getId())).isPresent();
    }

    @Test
    @DisplayName("REQ-83 · 다른 구글 계정이 고정된 계정은 같은 이메일이어도 403이고 아무것도 안 지운다")
    void pinnedToAnotherGoogle403() throws Exception {
        User local = verifiedEmailAccount("pinned-local@gmail.com", AuthProvider.LOCAL);
        pin(local, "another-google-sub");
        googleSays(GOOGLE_SUB, "pinned-local@gmail.com", null);
        deleteVia(apiTokenService.issue(local), "GOOGLE", null, null).andExpect(status().isForbidden());
        assertThat(userRepository.findById(local.getId())).isPresent();

        User google = googleAccount("pinned-google@gmail.com");
        pin(google, "another-google-sub-2");
        googleSays(GOOGLE_SUB, "pinned-google@gmail.com", null);
        deleteVia(apiTokenService.issue(google), "GOOGLE", null, null).andExpect(status().isForbidden());
        assertThat(userRepository.findById(google.getId())).isPresent();
    }

    @Test
    @DisplayName("REQ-83 · 미검증 LOCAL은 구글로 확인해도 403이다")
    void unverifiedLocal403() throws Exception {
        User squatter = userRepository.saveAndFlush(User.of("squat@gmail.com", "hash", "선점자", "Asia/Seoul", Role.USER));
        googleSays(GOOGLE_SUB, "squat@gmail.com", null);

        deleteVia(apiTokenService.issue(squatter), "GOOGLE", null, null).andExpect(status().isForbidden());
        assertThat(userRepository.findById(squatter.getId())).isPresent();
    }

    @Test
    @DisplayName("REQ-83 · 구글 토큰이 연 계정이 지금 사용자가 아니면 403이고 두 계정 다 그대로다(외부 호출 0)")
    void googleTokenOpeningAnotherAccount403() throws Exception {
        User victim = appleUsers.register(SUB); // 연결 코드를 가로챈 쪽이 Apple로 Bearer를 얻은 피해자 계정 U
        withChildren(victim);
        User attacker = googleAccount("attacker@gmail.com"); // 공격자 자신의 구글 계정 V
        googleSays(GOOGLE_SUB, "attacker@gmail.com", null); // 판정은 V를 OPEN한다
        when(verifier.verify(any())).thenReturn(Optional.of(SUB));

        deleteVia(apiTokenService.issue(victim), "GOOGLE", "apple-fresh", "apple-code").andExpect(status().isForbidden());

        assertThat(userRepository.findById(victim.getId())).isPresent();
        assertThat(userRepository.findById(attacker.getId())).isPresent();
        verify(revoker, never()).revoke(any());
    }

    @Test
    @DisplayName("REQ-83 · 그 이메일의 계정이 없으면 403이다")
    void noAccountForEmail403() throws Exception {
        User me = appleUsers.register(SUB);
        googleSays(GOOGLE_SUB, "nobody@gmail.com", null);

        deleteVia(apiTokenService.issue(me), "GOOGLE", null, null).andExpect(status().isForbidden());
        assertThat(userRepository.findById(me.getId())).isPresent();
    }

    @Test
    @DisplayName("REQ-83 · 서비스도 판정한 계정의 id가 지금 사용자와 같을 때만 지운다")
    void serviceChecksResolvedAccountId() {
        User victim = appleUsers.register(SUB);
        googleAccount("attacker2@gmail.com");

        assertThatThrownBy(() -> accountService.deleteProviderVerifiedAccount(victim, AuthProvider.GOOGLE,
                new VerifiedIdentity(GOOGLE_SUB, "attacker2@gmail.com", null)))
                .as("다른 계정을 OPEN").isInstanceOf(AccountDeletionConfirmationException.class);
        assertThatThrownBy(() -> accountService.deleteProviderVerifiedAccount(victim, AuthProvider.GOOGLE,
                new VerifiedIdentity(GOOGLE_SUB, "nobody2@gmail.com", null)))
                .as("NONE").isInstanceOf(AccountDeletionConfirmationException.class);
        assertThatThrownBy(() -> accountService.deleteProviderVerifiedAccount(victim, AuthProvider.KAKAO,
                new VerifiedIdentity(KAKAO_SUB, null, null)))
                .as("카카오 신원 없음").isInstanceOf(AccountDeletionConfirmationException.class);
        assertThat(userRepository.findById(victim.getId())).isPresent();
    }

    @Test
    @DisplayName("REQ-83 · 11분 전에 발급된 토큰이면 400이고 아무것도 안 지운다")
    void staleTokenRejected400() throws Exception {
        // 발급 시각 규칙은 검증기의 몫(IdTokenVerifierTest) — 여기선 검증기가 거부했을 때의 문 동작을 잰다
        User google = googleAccount("stale@gmail.com");
        when(idTokens.verifyGoogle(any())).thenReturn(Optional.empty());
        deleteVia(apiTokenService.issue(google), "GOOGLE", null, null).andExpect(status().isBadRequest());
        assertThat(userRepository.findById(google.getId())).isPresent();

        User kakao = kakaoUsers.register(KAKAO_SUB);
        when(idTokens.verifyKakao(any())).thenReturn(Optional.empty());
        deleteVia(apiTokenService.issue(kakao), "KAKAO", null, null).andExpect(status().isBadRequest());
        assertThat(userRepository.findById(kakao.getId())).isPresent();
    }

    @Test
    @DisplayName("REQ-83 · provider 없는 옛 본문은 APPLE 갈래이고, 모르는 provider는 400이다")
    void legacyBodyIsAppleAndUnknownProvider400() throws Exception {
        User me = appleUsers.register(SUB);
        String token = apiTokenService.issue(me);
        deleteVia(token, "TOSS", null, null).andExpect(status().isBadRequest());
        assertThat(userRepository.findById(me.getId())).isPresent();

        when(verifier.verify(any())).thenReturn(Optional.of(SUB));
        delete(token).andExpect(status().isNoContent()); // 옛 본문 {identityToken, authorizationCode}
        assertThat(userRepository.findById(me.getId())).isEmpty();
        verify(idTokens, never()).verifyGoogle(any());
        verify(idTokens, never()).verifyKakao(any());
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
