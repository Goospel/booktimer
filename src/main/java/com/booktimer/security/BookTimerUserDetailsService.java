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
 * <p>이메일이 로그인 식별자다 — {@code username = email}. 비밀번호는 이미 해시된 값을
 * 그대로 싣고(검증은 Security가 PasswordEncoder로 수행), {@link com.booktimer.user.Role}은
 * {@code ROLE_} 접두를 붙여 권한으로 매핑한다(엔티티는 순수 도메인 값만 보관 — 접두는 여기서).
 *
 * <p>이 어댑터는 인증 시점(로그인)에 Security의 DaoAuthenticationProvider가 호출한다.
 */
@Service
public class BookTimerUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public BookTimerUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("no user with email: " + email));
        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())))
                .build();
    }
}
