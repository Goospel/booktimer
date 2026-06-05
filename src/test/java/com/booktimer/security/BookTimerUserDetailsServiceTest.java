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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * BookTimerUserDetailsService 단위 테스트 (Mockito — DB/컨텍스트 무관).
 *
 * <p>도메인 {@link User}를 Spring Security가 인증에 쓰는 {@link UserDetails}로 변환하는 어댑터를
 * 격리 검증한다. <b>로그인 식별자는 login_id</b>다(login-id-design §5 PR-4): login_id로 조회하고,
 * principal({@code username})을 login_id로 싣고, Role을 {@code ROLE_} 권한으로 매핑한다.
 * <b>이메일로는 로그인할 수 없다</b> — 공개된 이메일을 로그인 표적에서 분리한 것이 이 컷오버의 목적이다.
 */
@ExtendWith(MockitoExtension.class)
class BookTimerUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private BookTimerUserDetailsService service;

    private User localUser(String email, String loginId, String nickname, Role role) {
        User u = User.of(email, "$2a$10$hashedpw", nickname, "Asia/Seoul", role);
        u.assignLoginId(loginId);
        return u;
    }

    @Test
    @DisplayName("loadUserByUsername: login_id로 찾은 User를 UserDetails로 매핑한다 (principal=login_id, USER→ROLE_USER)")
    void loadByLoginId_mapsUser() {
        User user = localUser("reader@booktimer.com", "reader1", "책벌레", Role.USER);
        when(userRepository.findByLoginId("reader1")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("reader1");

        assertThat(details.getUsername()).isEqualTo("reader1"); // principal = login_id (email 아님)
        assertThat(details.getPassword()).isEqualTo("$2a$10$hashedpw");
        assertThat(details.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("loadUserByUsername: ADMIN은 ROLE_ADMIN 권한으로 매핑된다")
    void loadByLoginId_adminAuthority() {
        User admin = localUser("admin@booktimer.com", "rootadmin", "운영자", Role.ADMIN);
        when(userRepository.findByLoginId("rootadmin")).thenReturn(Optional.of(admin));

        UserDetails details = service.loadUserByUsername("rootadmin");

        assertThat(details.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    @DisplayName("loadUserByUsername: 없는 login_id면 UsernameNotFoundException")
    void loadByLoginId_absent_throws() {
        when(userRepository.findByLoginId("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    @DisplayName("보안: 이메일로는 로그인할 수 없다 — login_id로만 조회하므로 이메일 문자열은 못 찾는다")
    void loginByEmail_rejected() {
        // 이메일을 username으로 넘겨도 login_id 조회라 매칭되지 않는다(이메일은 로그인 식별자가 아님).
        lenient().when(userRepository.findByLoginId("reader@booktimer.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("reader@booktimer.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
