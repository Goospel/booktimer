package com.booktimer.user;

import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

/**
 * 구글 신원 판정(REQ-115) — 구글 ID 토큰(sub · 검증된 이메일 · hd)이 어느 계정을 어떻게 여는지 하나의 표로 정한다.
 * 웹 구글 로그인 · 앱 LOGIN · 앱 REGISTER · 앱 탈퇴가 모두 이것을 부른다.
 *
 * <p><b>부작용 없음</b>: 조회만 한다. 고정(쓰기)·정책 ①②·로그는 호출부({@link OAuthUserProvisioningService})의 몫이다.
 * 그 서비스 안에 두지 않는 이유 — 그 서비스는 {@link AccountService}를 받는데, 탈퇴 재대조가 이 판정을 부르면 순환 의존이 된다.
 *
 * <p>전제: 호출부가 {@code email_verified}가 참이 아니거나 합성 도메인 주소인 토큰을 이미 거부했다.
 * 그래서 합성 주소를 쓰는 Apple · 카카오 계정은 이메일로 여기 걸리지 않는다.
 */
@Service
public class GoogleAccountResolver {

    /** 판정 결과의 종류 — 설계 §3.1 판정표. */
    public enum Kind {
        /** 그 계정을 연다 — 고정이 비었으면 호출부가 그 자리에서 고정한다(탈퇴는 고정하지 않는다). */
        OPEN,
        /** 그 계정을 열되 고정하지 않는다 — 구글이 그 주소에 권한이 없는 검증된 이메일 가입 계정(§9-6 ②). */
        OPEN_UNPINNED,
        /** 다른 구글 계정에 고정된 계정이다 — 아무것도 바꾸지 않고 거부한다. */
        MISMATCH,
        /** 같은 이메일의 미검증 LOCAL — 정책 ①(폐기 뒤 새 구글 계정). */
        PURGE_LOCAL,
        /** 토스를 붙인 미검증 계정 — 정책 ②(이메일만 합성 주소로 비키고 새 구글 계정). */
        REASSIGN_TOSS,
        /** 이 sub · 이메일의 계정이 없다. */
        NONE
    }

    /** 판정 결과. {@code user}는 판정에 쓴 계정({@link Kind#NONE}이면 null). */
    public record GoogleMatch(Kind kind, User user) {
    }

    private static final String GMAIL_SUFFIX = "@gmail.com";

    private final UserRepository userRepository;

    public GoogleAccountResolver(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * @param sub   검증된 ID 토큰의 sub(필수 — 비면 {@link IllegalArgumentException})
     * @param email 검증된 ID 토큰의 이메일
     * @param hd    토큰의 구글 워크스페이스 도메인(없으면 null)
     */
    public GoogleMatch resolve(String sub, String email, String hd) {
        if (sub == null || sub.isBlank()) {
            // findByGoogleSub(null)은 IS NULL — 고정 없는 계정 전부를 찾는다. 호출부 결함이라 조용히 넘기지 않는다.
            throw new IllegalArgumentException("google sub must not be blank");
        }
        Optional<User> bySub = userRepository.findByGoogleSub(sub);
        if (bySub.isPresent()) {
            // 정확히 같을 때만 연다 — 운영 정렬이 대소문자를 가리지 않아도 기대지 않는다. 이메일이 달라도 연다(구글 쪽 이메일 변경).
            User pinned = bySub.get();
            return new GoogleMatch(sub.equals(pinned.getGoogleSub()) ? Kind.OPEN : Kind.MISMATCH, pinned);
        }
        Optional<User> byEmail = userRepository.findByEmail(email);
        if (byEmail.isEmpty()) {
            return new GoogleMatch(Kind.NONE, null);
        }
        User user = byEmail.get();
        if (user.getGoogleSub() != null) {
            return new GoogleMatch(Kind.MISMATCH, user); // 이메일은 같지만 다른 구글 계정에 고정돼 있다
        }
        if (!user.isEmailVerified()) {
            // 정책 ②가 ①보다 먼저다 — 미검증 LOCAL이라도 토스가 붙어 있으면 폐기가 아니라 재배정(미니앱 기록 보존).
            if (user.getAuthProvider() == AuthProvider.TOSS
                    || (user.getTossUserKey() != null && user.isLocalAccount())) {
                return new GoogleMatch(Kind.REASSIGN_TOSS, user);
            }
            if (user.isLocalAccount()) {
                return new GoogleMatch(Kind.PURGE_LOCAL, user);
            }
        }
        if (user.getAuthProvider() == AuthProvider.GOOGLE) {
            return new GoogleMatch(Kind.OPEN, user); // 구글 가입은 주소와 무관하게 첫 열기 · 고정
        }
        // 검증된 LOCAL · TOSS — 구글이 그 주소에 권한이 있을 때만 고정한다(§9-6 ②).
        return new GoogleMatch(googleVouchesFor(email, hd) ? Kind.OPEN : Kind.OPEN_UNPINNED, user);
    }

    /**
     * 구글이 그 이메일에 권한이 있는가 — gmail 주소이거나 토큰에 hd(워크스페이스)가 있다(Google 「Verify the Google ID token」).
     * 그 밖의 주소는 email_verified가 참이어도 「구글 계정을 만들 때 한 번 확인했을 뿐」이다. {@code @googlemail.com} 같은
     * 다른 꼴은 권한 없음 쪽 — 안전한 쪽으로 틀린다.
     */
    private static boolean googleVouchesFor(String email, String hd) {
        return (hd != null && !hd.isBlank())
                || (email != null && email.toLowerCase(Locale.ROOT).endsWith(GMAIL_SUFFIX));
    }
}
