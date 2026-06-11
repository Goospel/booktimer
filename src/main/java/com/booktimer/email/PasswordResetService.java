package com.booktimer.email;

import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * 비밀번호 재설정 — 재설정 메일 발송 + 토큰 검증 후 비밀번호 교체(이메일 인프라 1단계 PR-C).
 *
 * <p><b>발송({@link #requestReset})</b>: 계정 존재/부재/소셜 여부와 무관하게 <b>외부 응답이 동일</b>하다
 * (열거완화 N-052) — 차이는 내부 발송 여부뿐이다. 비밀번호를 가진 LOCAL 계정에만 PASSWORD_RESET 토큰을
 * 발급해 재설정 링크({@code /password/reset?token=})를 메일로 보낸다. 소셜 계정은 재설정할 비밀번호가 없어
 * 보내지 않고, 부재 계정도 보내지 않는다.
 *
 * <p><b>교체({@link #reset})</b>: 링크의 토큰을 소비해 유효하면 새 비밀번호를 해싱해 교체·저장한다. 토큰이
 * 무효/만료/이미 사용/type 불일치면 아무 것도 바꾸지 않는다(일회용 — {@link EmailTokenService}).
 */
@Service
public class PasswordResetService {

    private final UserRepository userRepository;
    private final EmailTokenService tokenService;
    private final EmailDispatcher emailDispatcher;
    private final PasswordEncoder passwordEncoder;
    private final String baseUrl;

    public PasswordResetService(UserRepository userRepository,
                                EmailTokenService tokenService,
                                EmailDispatcher emailDispatcher,
                                PasswordEncoder passwordEncoder,
                                @Value("${booktimer.base-url:http://localhost:8080}") String baseUrl) {
        this.userRepository = userRepository;
        this.tokenService = tokenService;
        this.emailDispatcher = emailDispatcher;
        this.passwordEncoder = passwordEncoder;
        this.baseUrl = baseUrl;
    }

    /**
     * 비밀번호 재설정 메일을 보낸다 — <b>LOCAL 계정에만</b> PASSWORD_RESET 토큰을 발급해 재설정 링크를 발송한다.
     * 부재/소셜 계정엔 아무 것도 하지 않되 반환은 동일하다(열거완화 — 호출자는 계정 존재 여부를 알 수 없다).
     * 발송 실패({@link EmailSendException})는 비동기 발송 측에서 격리된다.
     */
    @Transactional
    public void requestReset(String email) {
        userRepository.findByEmail(email)
                .filter(User::isLocalAccount) // 소셜은 재설정할 비밀번호가 없음 — 발송 안 함
                .ifPresent(user -> {
                    String rawToken = tokenService.issue(user, EmailTokenType.PASSWORD_RESET);
                    String link = baseUrl + "/password/reset?token="
                            + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
                    emailDispatcher.dispatch(user.getEmail(), "[BookTimer] 비밀번호 재설정 안내", buildBody(user, link));
                });
    }

    /**
     * 재설정 링크의 토큰을 검증하고, 유효하면 새 비밀번호를 해싱해 교체·저장한다.
     *
     * @return 유효 토큰이라 교체했으면 {@code true}, 무효/만료/이미 사용이라 아무 것도 바꾸지 않았으면 {@code false}
     */
    @Transactional
    public boolean reset(String rawToken, String newRawPassword) {
        Optional<User> consumed = tokenService.consume(rawToken, EmailTokenType.PASSWORD_RESET);
        if (consumed.isEmpty()) {
            return false;
        }
        User user = consumed.get();
        user.changePassword(passwordEncoder.encode(newRawPassword));
        userRepository.save(user);
        return true;
    }

    /** 재설정 메일 HTML 본문(간단한 문자열 빌더 — 별도 템플릿 엔진 배선 없이). */
    private static String buildBody(User user, String link) {
        return """
                <p>%s님, 안녕하세요.</p>
                <p>BookTimer 비밀번호 재설정 요청을 받았습니다. 아래 버튼을 눌러 새 비밀번호를 설정해 주세요.</p>
                <p><a href="%s">비밀번호 재설정하기</a></p>
                <p>링크가 열리지 않으면 다음 주소를 붙여넣어 주세요:<br>%s</p>
                <p>이 링크는 1시간 동안 유효합니다. 본인이 요청하지 않았다면 이 메일을 무시하셔도 됩니다 — 비밀번호는 바뀌지 않습니다.</p>
                """.formatted(escape(user.getNickname()), link, link);
    }

    /** 닉네임 등 사용자 입력이 HTML 본문에 들어가므로 최소한의 이스케이프(XSS 방지). */
    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
