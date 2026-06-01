package com.booktimer.security;

import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * BookTimerUserDetailsService 단위 테스트 (Mockito — DB/컨텍스트 무관).
 *
 * <p>도메인 {@link User}(이메일=로그인 식별자, {@link Role})를 Spring Security가 인증에
 * 쓰는 {@link UserDetails}로 변환하는 어댑터를 격리 검증한다. 이메일로 조회하고,
 * 비밀번호 해시를 그대로 싣고, Role을 {@code ROLE_} 권한으로 매핑하며, 없는 이메일은
 * {@link UsernameNotFoundException}으로 거부하는지 본다.
 */
@ExtendWith(MockitoExtension.class)
class BookTimerUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private BookTimerUserDetailsService service;

    @Test
    @DisplayName("loadUserByUsername: 이메일로 찾은 User를 UserDetails로 매핑한다 (USER → ROLE_USER)")
    void loadByEmail_mapsUser() {
        User user = User.of("reader@booktimer.com", "$2a$10$hashedpw", "책벌레", "Asia/Seoul", Role.USER);
        when(userRepository.findByEmail("reader@booktimer.com")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("reader@booktimer.com");

        assertThat(details.getUsername()).isEqualTo("reader@booktimer.com");
        assertThat(details.getPassword()).isEqualTo("$2a$10$hashedpw");
        assertThat(details.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("loadUserByUsername: ADMIN은 ROLE_ADMIN 권한으로 매핑된다")
    void loadByEmail_adminAuthority() {
        User admin = User.of("admin@booktimer.com", "$2a$10$hashedpw", "운영자", "Asia/Seoul", Role.ADMIN);
        when(userRepository.findByEmail("admin@booktimer.com")).thenReturn(Optional.of(admin));

        UserDetails details = service.loadUserByUsername("admin@booktimer.com");

        assertThat(details.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    @DisplayName("loadUserByUsername: 없는 이메일이면 UsernameNotFoundException")
    void loadByEmail_absent_throws() {
        when(userRepository.findByEmail("ghost@booktimer.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("ghost@booktimer.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
