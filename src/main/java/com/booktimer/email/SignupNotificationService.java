package com.booktimer.email;

import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 중복 가입 통지(이메일 인프라 1단계 PR-D) — 이미 가입된 이메일로 누군가 다시 가입을 시도했을 때, 그 이메일의
 * <b>실소유자(기존 계정주)</b>에게 "이미 계정이 있다"는 안내 메일을 보낸다.
 *
 * <p>가입 응답 자체는 성공과 동일하게 흡수되므로(열거완화 — 시도자는 이메일 존재 여부를 알 수 없다), 정직하게
 * 잊고 재가입한 주인이 막막해지지 않도록 안내는 메일로 대신 전한다(N-052 "조용히 수락 + 통지"의 정석). 통지는
 * 시도자가 아니라 기존 주인 메일함으로만 가므로 열거가 되지 않는다. 계정 종류에 따라 안내가 갈린다 — LOCAL은
 * 로그인/비밀번호 재설정, 소셜은 해당 provider로 로그인.
 */
@Service
public class SignupNotificationService {

    private final UserRepository userRepository;
    private final EmailDispatcher emailDispatcher;
    private final String baseUrl;

    public SignupNotificationService(UserRepository userRepository,
                                     EmailDispatcher emailDispatcher,
                                     @Value("${booktimer.base-url:http://localhost:8080}") String baseUrl) {
        this.userRepository = userRepository;
        this.emailDispatcher = emailDispatcher;
        this.baseUrl = baseUrl;
    }

    /**
     * 그 이메일로 가입된 계정이 있으면 기존 주인에게 "이미 계정 있음" 안내를 보낸다. 계정이 없으면
     * (login_id 레이스 등) 아무 것도 하지 않는다 — 보낼 주인이 없다. 발송 실패는 비동기 측에서 격리된다.
     */
    @Transactional
    public void notifyExistingAccount(String email) {
        userRepository.findByEmail(email).ifPresent(user ->
                emailDispatcher.dispatch(user.getEmail(),
                        "[BookTimer] 이미 가입된 이메일로 가입 시도가 있었어요", buildBody(user)));
    }

    /** 통지 메일 HTML 본문 — 계정 종류에 맞는 로그인 경로를 안내한다(간단한 문자열 빌더). */
    private String buildBody(User user) {
        String greeting = "<p>%s님, 안녕하세요.</p>".formatted(escape(user.getNickname()));
        String intro = "<p>방금 회원님의 이메일 주소로 BookTimer 회원가입 시도가 있었어요. 이미 이 이메일로 가입된 "
                + "계정이 있어 새 계정은 만들어지지 않았습니다.</p>";
        if (user.isLocalAccount()) {
            return greeting + intro + """
                    <p>기존 계정으로 로그인해 주세요:</p>
                    <p><a href="%s/login">로그인하기</a></p>
                    <p>비밀번호가 기억나지 않는다면 재설정할 수 있어요:</p>
                    <p><a href="%s/password/forgot">비밀번호 재설정하기</a></p>
                    <p>본인이 시도한 게 아니라면 이 메일을 무시하셔도 됩니다 — 계정은 그대로 안전합니다.</p>
                    """.formatted(baseUrl, baseUrl);
        }
        return greeting + intro + """
                <p>회원님은 Google 계정으로 가입돼 있어요. 아래에서 Google로 로그인해 주세요:</p>
                <p><a href="%s/login">Google로 로그인하기</a></p>
                <p>본인이 시도한 게 아니라면 이 메일을 무시하셔도 됩니다 — 계정은 그대로 안전합니다.</p>
                """.formatted(baseUrl);
    }

    /** 닉네임 등 사용자 입력이 HTML 본문에 들어가므로 최소한의 이스케이프(XSS 방지). */
    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
