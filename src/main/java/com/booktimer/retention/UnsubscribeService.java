package com.booktimer.retention;

import com.booktimer.email.EmailTokenService;
import com.booktimer.email.EmailTokenType;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 마케팅 메일 구독해지(원클릭) — 넛지 메일의 수신거부 링크가 타는 경로(이메일 인프라 2단계 PR-3).
 *
 * <p>토큰이 신원 증명이라 <b>로그인 없이</b> 처리한다(one-click). UNSUBSCRIBE 토큰을 소비해 유효하면 바인딩된
 * 사용자의 마케팅 동의를 끄고(철회) 저장한다. 무효/만료/이미 사용/위조 토큰이면 아무 것도 바꾸지 않는다.
 * 토큰은 발급 대상에게만 바인딩되므로 남의 동의를 조작할 수 없다(IDOR 방어 — {@link EmailTokenService#consume}가
 * 토큰이 묶인 user만 돌려준다).
 */
@Service
public class UnsubscribeService {

    private final EmailTokenService tokenService;
    private final UserRepository userRepository;

    public UnsubscribeService(EmailTokenService tokenService, UserRepository userRepository) {
        this.tokenService = tokenService;
        this.userRepository = userRepository;
    }

    /**
     * 구독해지 토큰을 소비해 마케팅 동의를 철회한다.
     *
     * @param rawToken 메일 수신거부 링크의 평문 토큰
     * @return 유효해서 해지했으면 true, 무효/만료/사용/위조면 false(상태 불변)
     */
    @Transactional
    public boolean unsubscribe(String rawToken) {
        Optional<User> consumed = tokenService.consume(rawToken, EmailTokenType.UNSUBSCRIBE);
        if (consumed.isEmpty()) {
            return false;
        }
        User user = consumed.get();
        user.withdrawMarketingConsent();
        userRepository.save(user);
        return true;
    }
}
