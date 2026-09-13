package com.booktimer.security;

import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 도메인 {@link User}를 Spring Security 인증 주체({@link UserDetails})로 변환하는 어댑터.
 *
 * <p><b>login_id가 로그인 식별자다</b>(login-id-design §5 PR-4) — {@code username = login_id}.
 * 이메일로는 로그인할 수 없다(공개된 이메일을 로그인 표적에서 분리한 것이 이 컷오버의 목적). 비밀번호는
 * 이미 해시된 값을 그대로 싣고(검증은 Security가 PasswordEncoder로 수행), {@link com.booktimer.user.Role}은
 * {@code ROLE_} 접두를 붙여 권한으로 매핑한다(엔티티는 순수 도메인 값만 보관 — 접두는 여기서).
 *
 * <p>이 어댑터는 인증 시점(로그인)에 Security의 DaoAuthenticationProvider가 호출한다 — 폼이 보낸
 * {@code username} 파라미터가 곧 login_id다. 이메일 폴백은 없다(표적 약점 즉시 차단).
 */
@Service
public class BookTimerUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public BookTimerUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String loginId) throws UsernameNotFoundException {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new UsernameNotFoundException("no user with login_id: " + loginId));
        if (user.getPasswordHash() == null) {
            // 소셜·토스 계정은 비밀번호가 없어 폼 로그인이 원리상 불가하다. 이때 UserDetails를 그냥 내주면
            // BCryptPasswordEncoder.matches(raw, null)이 해시 계산 없이 즉시 false를 돌려줘(실측: 7.1.0의
            // AbstractValidatingPasswordEncoder.matches가 null 인코딩 값에 바로 false) "존재하는 소셜 계정"만
            // 유독 빨리 실패한다 — 계정 열거의 시간 채널이다. UsernameNotFoundException으로 던지면
            // DaoAuthenticationProvider가 이 예외에서만 미리 계산한 더미 해시로 matches를 한 번 돌려
            // (mitigateAgainstTimingAttack) "없는 계정"과 같은 시간을 쓰고, hideUserNotFoundExceptions 기본값이
            // BadCredentialsException으로 감싸 응답도 동일해진다.
            throw new UsernameNotFoundException("no password credential for login_id: " + loginId);
        }
        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getLoginId())
                .password(user.getPasswordHash())
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())))
                .build();
    }
}
