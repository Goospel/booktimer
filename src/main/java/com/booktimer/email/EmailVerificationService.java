package com.booktimer.email;

import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * 가입 이메일 인증 — 인증 메일 발송 + 토큰 검증 반영(이메일 인프라 1단계 PR-B).
 *
 * <p>발송: VERIFICATION 토큰을 발급해 평문을 인증 링크({@code /verify-email?token=})에 실어 메일로 보낸다
 * (DB엔 해시만 — {@link EmailTokenService}). 검증: 링크의 토큰을 소비해 유효하면 {@link User#verifyEmail()}로
 * 검증 완료를 반영한다. 미검증이어도 로그인·사용은 막지 않는다(thesis — 입문자 마찰 최소).
 */
@Service
public class EmailVerificationService {

    private final EmailTokenService tokenService;
    private final EmailDispatcher emailDispatcher;
    private final UserRepository userRepository;
    private final String baseUrl;

    public EmailVerificationService(EmailTokenService tokenService,
                                    EmailDispatcher emailDispatcher,
                                    UserRepository userRepository,
                                    @Value("${booktimer.base-url:http://localhost:8080}") String baseUrl) {
        this.tokenService = tokenService;
        this.emailDispatcher = emailDispatcher;
        this.userRepository = userRepository;
        this.baseUrl = baseUrl;
    }

    /**
     * 인증 메일을 보낸다 — VERIFICATION 토큰 발급 후 인증 링크가 담긴 본문을 사용자 이메일로 발송한다.
     * 발송 실패({@link EmailSendException})는 호출자가 흐름에 맞게 격리/안내한다(가입은 성공 유지·재발송 안내).
     */
    @Transactional
    public void sendVerification(User user) {
        String rawToken = tokenService.issue(user, EmailTokenType.VERIFICATION);
        String link = baseUrl + "/verify-email?token="
                + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        // 토큰 발급(DB)은 동기로 끝내고, SMTP 발송은 비동기로 위임 — 요청 스레드 블로킹·트랜잭션 내 I/O 제거.
        emailDispatcher.dispatch(user.getEmail(), "[BookTimer] 이메일 인증을 완료해 주세요", buildBody(user, link));
    }

    /**
     * 인증 링크의 토큰을 검증한다. 유효하면 이메일을 검증 완료로 표시·저장하고 {@code true},
     * 무효/만료/이미 사용/type 불일치면 {@code false}(아무 것도 바꾸지 않음).
     */
    @Transactional
    public boolean verify(String rawToken) {
        Optional<User> consumed = tokenService.consume(rawToken, EmailTokenType.VERIFICATION);
        if (consumed.isEmpty()) {
            return false;
        }
        User user = consumed.get();
        user.verifyEmail();
        userRepository.save(user);
        return true;
    }

    /** 인증 메일 HTML 본문(간단한 문자열 빌더 — 별도 템플릿 엔진 배선 없이). */
    private static String buildBody(User user, String link) {
        return """
                <p>%s님, 안녕하세요.</p>
                <p>BookTimer 가입을 환영합니다. 아래 버튼을 눌러 이메일 인증을 완료해 주세요.</p>
                <p><a href="%s">이메일 인증하기</a></p>
                <p>링크가 열리지 않으면 다음 주소를 붙여넣어 주세요:<br>%s</p>
                <p>이 링크는 24시간 동안 유효합니다. 본인이 가입하지 않았다면 이 메일을 무시하셔도 됩니다.</p>
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
