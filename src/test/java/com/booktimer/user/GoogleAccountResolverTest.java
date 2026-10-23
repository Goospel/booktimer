package com.booktimer.user;

import com.booktimer.user.GoogleAccountResolver.Kind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 구글 신원 판정표(REQ-115) — 저장소 목. 웹 로그인 · 앱 LOGIN · REGISTER · 탈퇴가 같은 판정을 쓴다.
 * 같은 계정 픽스처에 입력(이메일 꼴 · hd)만 바꿔 「고정 없는 열기」와 「첫 열기」를 가른다(양성 대조).
 */
@ExtendWith(MockitoExtension.class)
class GoogleAccountResolverTest {

    @Mock
    private UserRepository userRepository;

    private GoogleAccountResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new GoogleAccountResolver(userRepository);
        lenient().when(userRepository.findByGoogleSub(anyString())).thenReturn(Optional.empty());
        lenient().when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("REQ-115 · 고정된 sub로 오면 이메일이 달라도 그 계정을 연다")
    void pinnedSub_differentEmail_opens() {
        User pinned = google("old@booktimer.com");
        pin(pinned, "108234");
        when(userRepository.findByGoogleSub("108234")).thenReturn(Optional.of(pinned));

        var match = resolver.resolve("108234", "new@booktimer.com", null);

        assertThat(match.kind()).isEqualTo(Kind.OPEN);
        assertThat(match.user()).isSameAs(pinned);
    }

    @Test
    @DisplayName("REQ-115 · 대소문자만 다른 sub는 불일치다")
    void caseOnlyDifferentSub_mismatch() {
        // 정렬이 대소문자를 가리지 않는 DB가 "abcDEF"로 "abcdef" 행을 돌려준 상황 — 정렬에 기대지 않는다.
        User pinned = google("g@booktimer.com");
        pin(pinned, "abcdef");
        when(userRepository.findByGoogleSub("abcDEF")).thenReturn(Optional.of(pinned));

        assertThat(resolver.resolve("abcDEF", "g@booktimer.com", null).kind()).isEqualTo(Kind.MISMATCH);
    }

    @Test
    @DisplayName("REQ-115 · 이메일로 찾은 계정에 다른 sub가 고정돼 있으면 불일치다")
    void emailAccountPinnedToOtherSub_mismatch() {
        User pinned = google("g@booktimer.com");
        pin(pinned, "other-sub");
        when(userRepository.findByEmail("g@booktimer.com")).thenReturn(Optional.of(pinned));

        var match = resolver.resolve("my-sub", "g@booktimer.com", null);

        assertThat(match.kind()).isEqualTo(Kind.MISMATCH);
        assertThat(match.user()).isSameAs(pinned);
    }

    @Test
    @DisplayName("REQ-115 · 고정 없는 구글 가입 계정과 gmail·hd 주소의 이메일 가입 계정은 첫 열기다")
    void unpinnedGoogleOrAuthorizedAddress_opens() {
        // 구글 가입은 주소와 무관하게 OPEN(권한 없는 주소여도).
        User google = google("g@example.com");
        when(userRepository.findByEmail("g@example.com")).thenReturn(Optional.of(google));
        assertThat(resolver.resolve("s1", "g@example.com", null).kind()).isEqualTo(Kind.OPEN);

        // 검증된 LOCAL — gmail(대문자 꼴 포함) 또는 hd가 있으면 OPEN.
        User gmailLocal = verifiedLocal("Reader@GMAIL.com");
        when(userRepository.findByEmail("Reader@GMAIL.com")).thenReturn(Optional.of(gmailLocal));
        assertThat(resolver.resolve("s2", "Reader@GMAIL.com", null).kind()).isEqualTo(Kind.OPEN);

        User corpLocal = verifiedLocal("me@corp.example");
        when(userRepository.findByEmail("me@corp.example")).thenReturn(Optional.of(corpLocal));
        assertThat(resolver.resolve("s3", "me@corp.example", "corp.example").kind()).isEqualTo(Kind.OPEN);

        // 검증된 TOSS도 같다.
        User verifiedToss = verifiedToss("t@gmail.com");
        when(userRepository.findByEmail("t@gmail.com")).thenReturn(Optional.of(verifiedToss));
        var match = resolver.resolve("s4", "t@gmail.com", null);
        assertThat(match.kind()).isEqualTo(Kind.OPEN);
        assertThat(match.user()).isSameAs(verifiedToss);
    }

    @Test
    @DisplayName("REQ-115 · 구글이 보증하지 않는 주소의 이메일 가입 계정은 고정 없는 열기다")
    void unauthorizedAddressEmailAccount_opensUnpinned() {
        User local = verifiedLocal("me@example.com");
        when(userRepository.findByEmail("me@example.com")).thenReturn(Optional.of(local));
        User toss = verifiedToss("t@example.com");
        when(userRepository.findByEmail("t@example.com")).thenReturn(Optional.of(toss));

        var localMatch = resolver.resolve("s1", "me@example.com", null);
        assertThat(localMatch.kind()).isEqualTo(Kind.OPEN_UNPINNED);
        assertThat(localMatch.user()).isSameAs(local);
        assertThat(resolver.resolve("s2", "t@example.com", null).kind()).isEqualTo(Kind.OPEN_UNPINNED);
        // gmail 아닌 비슷한 꼴은 권한 없음 쪽(안전한 쪽으로 틀린다).
        User googlemail = verifiedLocal("me@googlemail.com");
        when(userRepository.findByEmail("me@googlemail.com")).thenReturn(Optional.of(googlemail));
        assertThat(resolver.resolve("s3", "me@googlemail.com", null).kind()).isEqualTo(Kind.OPEN_UNPINNED);

        // 양성 대조 — 같은 계정 픽스처에 hd만 실으면 OPEN.
        assertThat(resolver.resolve("s1", "me@example.com", "example.com").kind()).isEqualTo(Kind.OPEN);
        assertThat(resolver.resolve("s2", "t@example.com", "example.com").kind()).isEqualTo(Kind.OPEN);
    }

    @Test
    @DisplayName("REQ-115 · 미검증 계정은 지금 정책 갈래로 간다")
    void unverifiedAccounts_followPolicyBranches() {
        User unverifiedLocal = User.of("v@gmail.com", "hash", "선점자", "Asia/Seoul", Role.USER);
        when(userRepository.findByEmail("v@gmail.com")).thenReturn(Optional.of(unverifiedLocal));
        assertThat(resolver.resolve("s1", "v@gmail.com", "x").kind()).isEqualTo(Kind.PURGE_LOCAL);

        User tossLinkedLocal = User.of("l@gmail.com", "hash", "선점자", "Asia/Seoul", Role.USER);
        tossLinkedLocal.linkTossUserKey("UK-1");
        when(userRepository.findByEmail("l@gmail.com")).thenReturn(Optional.of(tossLinkedLocal));
        assertThat(resolver.resolve("s2", "l@gmail.com", null).kind()).isEqualTo(Kind.REASSIGN_TOSS);

        User unverifiedToss = User.ofOAuth("t@gmail.com", "토스러", "Asia/Seoul", Role.USER, AuthProvider.TOSS);
        unverifiedToss.linkTossUserKey("UK-2");
        when(userRepository.findByEmail("t@gmail.com")).thenReturn(Optional.of(unverifiedToss));
        assertThat(resolver.resolve("s3", "t@gmail.com", null).kind()).isEqualTo(Kind.REASSIGN_TOSS);

        // 미검증 GOOGLE(+토스)은 흡수 — 지금 provision과 같다.
        User unverifiedGoogle = User.ofOAuth("g@booktimer.com", "구글러", "Asia/Seoul", Role.USER, AuthProvider.GOOGLE);
        unverifiedGoogle.linkTossUserKey("UK-3");
        when(userRepository.findByEmail("g@booktimer.com")).thenReturn(Optional.of(unverifiedGoogle));
        assertThat(resolver.resolve("s4", "g@booktimer.com", null).kind()).isEqualTo(Kind.OPEN);
    }

    @Test
    @DisplayName("REQ-115 · sub로도 이메일로도 없으면 NONE이다")
    void nothingFound_none() {
        var match = resolver.resolve("s1", "nobody@booktimer.com", null);

        assertThat(match.kind()).isEqualTo(Kind.NONE);
        assertThat(match.user()).isNull();
    }

    private static User google(String email) {
        User u = User.ofOAuth(email, "구글러", "Asia/Seoul", Role.USER, AuthProvider.GOOGLE);
        u.verifyEmail();
        return u;
    }

    private static User verifiedLocal(String email) {
        User u = User.of(email, "hash", "주인", "Asia/Seoul", Role.USER);
        u.verifyEmail();
        return u;
    }

    private static User verifiedToss(String email) {
        User u = User.ofOAuth(email, "토스러", "Asia/Seoul", Role.USER, AuthProvider.TOSS);
        u.linkTossUserKey("UK-" + email.hashCode());
        u.verifyEmail();
        return u;
    }

    /** 고정은 저장소의 조건부 UPDATE뿐이라 엔티티 setter가 없다 — 판정 픽스처는 필드에 직접 둔다. */
    private static void pin(User user, String sub) {
        ReflectionTestUtils.setField(user, "googleSub", sub);
    }
}
