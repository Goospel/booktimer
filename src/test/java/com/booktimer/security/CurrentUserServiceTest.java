package com.booktimer.security;

import com.booktimer.user.AuthProvider;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CurrentUserService 단위 테스트 (Mockito).
 *
 * <p>인증된 principal 이름을 도메인 User로 해석한다. 컷오버(PR-4) 후 정상 상태의 principal은 login_id다 →
 * <b>login_id 우선 조회</b>. 단 OAuth 첫 세션은 아직 login_id가 없어 principal이 이메일이라, 그 짧은 창에서만
 * email로 브리지한다. login_id로 찾으면 email 조회는 하지 않는다(정상 경로 우선).
 */
@ExtendWith(MockitoExtension.class)
class CurrentUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CurrentUserService service;

    private User user(String email, String loginId) {
        User u = User.of(email, "$2a$10$h", "닉", "Asia/Seoul", Role.USER);
        u.assignLoginId(loginId);
        return u;
    }

    @Test
    @DisplayName("정상: principal=login_id면 login_id로 찾고 email 조회는 하지 않는다")
    void resolve_byLoginId() {
        User u = user("reader@booktimer.com", "reader1");
        when(userRepository.findByLoginId("reader1")).thenReturn(Optional.of(u));

        assertThat(service.resolve("reader1")).isSameAs(u);
        verify(userRepository, never()).findByEmail("reader1");
    }

    @Test
    @DisplayName("OAuth 첫 세션: login_id가 없어 principal=email이면 email로 브리지해 찾는다")
    void resolve_oauthInterim_byEmail() {
        User u = User.ofOAuth("g@booktimer.com", "구글러", "Asia/Seoul", Role.USER, AuthProvider.GOOGLE); // login_id 아직 없음(OAuth 첫 세션)
        when(userRepository.findByLoginId("g@booktimer.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("g@booktimer.com")).thenReturn(Optional.of(u));

        assertThat(service.resolve("g@booktimer.com")).isSameAs(u);
    }

    @Test
    @DisplayName("아이디 변경 브리지: principal이 옛 아이디면 previous_login_id로 찾아 세션을 살린다")
    void resolve_afterLoginIdChange_byPreviousLoginId() {
        User u = user("reader@booktimer.com", "oldhandle");
        u.changeLoginId("newhandle"); // 이 세션의 principal은 아직 oldhandle이다
        when(userRepository.findByLoginId("oldhandle")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("oldhandle")).thenReturn(Optional.empty());
        when(userRepository.findByPreviousLoginId("oldhandle")).thenReturn(Optional.of(u));

        assertThat(service.resolve("oldhandle")).isSameAs(u);
    }

    @Test
    @DisplayName("정상 경로는 폴백을 건드리지 않는다 — login_id로 찾으면 previous 조회는 하지 않는다")
    void resolve_byLoginId_skipsPreviousFallback() {
        User u = user("reader@booktimer.com", "reader1");
        when(userRepository.findByLoginId("reader1")).thenReturn(Optional.of(u));

        assertThat(service.resolve("reader1")).isSameAs(u);
        verify(userRepository, never()).findByPreviousLoginId("reader1");
    }

    @Test
    @DisplayName("셋 다 없으면 IllegalStateException (인증됐는데 매핑 실패 — 폴백이 해석 실패를 삼키지 않는다)")
    void resolve_absent_throws() {
        lenient().when(userRepository.findByLoginId("ghost")).thenReturn(Optional.empty());
        lenient().when(userRepository.findByEmail("ghost")).thenReturn(Optional.empty());
        lenient().when(userRepository.findByPreviousLoginId("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve("ghost"))
                .isInstanceOf(IllegalStateException.class);
    }
}
