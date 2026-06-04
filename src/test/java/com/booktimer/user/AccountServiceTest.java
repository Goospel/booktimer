package com.booktimer.user;

import com.booktimer.block.BlockRepository;
import com.booktimer.book.BookRepository;
import com.booktimer.follow.FollowRepository;
import com.booktimer.session.ReadingSessionRepository;
import com.booktimer.timer.ReadingTimerRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AccountService 단위 테스트 (Mockito — DB/컨텍스트 무관).
 *
 * <p>계정 보안 유스케이스: 현재 비밀번호 검증을 전제로 한 <b>비밀번호 변경</b>과 <b>회원 탈퇴</b>.
 * 비밀번호 검증은 {@link PasswordEncoder#matches}로, 새 비밀번호 해싱은 {@code encode}로 위임한다.
 * 탈퇴는 FK 순서상 세션 → 타이머 → 유저 순으로 지운다(연관 cascade 없음).
 */
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final String EMAIL = "reader@booktimer.com";
    private static final String STORED_HASH = "$2a$10$STORED";

    @Mock
    private UserRepository userRepository;
    @Mock
    private ReadingTimerRepository timerRepository;
    @Mock
    private ReadingSessionRepository sessionRepository;
    @Mock
    private FollowRepository followRepository;
    @Mock
    private BlockRepository blockRepository;
    @Mock
    private BookRepository bookRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AccountService service;

    private User userWithHash() {
        return User.of(EMAIL, STORED_HASH, "책벌레", "Asia/Seoul", Role.USER);
    }

    // --- 비밀번호 변경 ---

    @Test
    @DisplayName("changePassword: 현재 비밀번호가 맞으면 새 비밀번호를 해싱해 교체·저장한다")
    void changePassword_correctCurrent_encodesAndSaves() {
        User user = userWithHash();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("curRaw", STORED_HASH)).thenReturn(true);
        when(passwordEncoder.encode("newRaw1234")).thenReturn("$2a$10$NEW");

        service.changePassword(EMAIL, "curRaw", "newRaw1234");

        verify(passwordEncoder).encode("newRaw1234");
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("$2a$10$NEW");
    }

    @Test
    @DisplayName("changePassword: 현재 비밀번호가 틀리면 예외, 해싱·저장 없음")
    void changePassword_wrongCurrent_throwsAndDoesNotSave() {
        User user = userWithHash();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("badRaw", STORED_HASH)).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(EMAIL, "badRaw", "newRaw1234"))
                .isInstanceOf(InvalidPasswordException.class);

        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
        assertThat(user.getPasswordHash()).isEqualTo(STORED_HASH); // 변경 안 됨
    }

    // --- 회원 탈퇴 ---

    @Test
    @DisplayName("deleteAccount: 비밀번호가 맞으면 세션→타이머→유저 순으로 삭제한다")
    void deleteAccount_correctPassword_deletesInFkOrder() {
        User user = userWithHash();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw", STORED_HASH)).thenReturn(true);

        service.deleteAccount(EMAIL, "pw");

        var ordered = inOrder(sessionRepository, timerRepository, followRepository, blockRepository, bookRepository, userRepository);
        ordered.verify(sessionRepository).deleteByUser(user); // book FK 참조하는 세션 먼저
        ordered.verify(timerRepository).deleteByUser(user);
        ordered.verify(followRepository).deleteByFollower(user);   // FK: 유저 삭제 전에 관계 정리
        ordered.verify(followRepository).deleteByFollowee(user);
        ordered.verify(blockRepository).deleteByBlocker(user);     // FK: 유저 삭제 전에 차단 관계 정리
        ordered.verify(blockRepository).deleteByBlocked(user);
        ordered.verify(bookRepository).deleteByUser(user);    // FK: 유저 삭제 전에 책 정리(세션 이후)
        ordered.verify(userRepository).delete(user);
    }

    @Test
    @DisplayName("deleteAccount: 비밀번호가 틀리면 예외, 아무것도 삭제하지 않는다")
    void deleteAccount_wrongPassword_throwsAndDeletesNothing() {
        User user = userWithHash();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("bad", STORED_HASH)).thenReturn(false);

        assertThatThrownBy(() -> service.deleteAccount(EMAIL, "bad"))
                .isInstanceOf(InvalidPasswordException.class);

        verify(sessionRepository, never()).deleteByUser(any());
        verify(timerRepository, never()).deleteByUser(any());
        verify(userRepository, never()).delete(any());
    }

    // --- 소셜 계정 탈퇴 (비밀번호 없음) ---

    @Test
    @DisplayName("deleteSocialAccount: 소셜(비밀번호 없는) 계정은 비번 확인 없이 세션→타이머→유저 순으로 삭제")
    void deleteSocialAccount_social_deletesInFkOrder() {
        User social = User.ofOAuth(EMAIL, "구글러", "Asia/Seoul", Role.USER, AuthProvider.GOOGLE);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(social));

        service.deleteSocialAccount(EMAIL);

        var ordered = inOrder(sessionRepository, timerRepository, followRepository, blockRepository, bookRepository, userRepository);
        ordered.verify(sessionRepository).deleteByUser(social);
        ordered.verify(timerRepository).deleteByUser(social);
        ordered.verify(followRepository).deleteByFollower(social);
        ordered.verify(followRepository).deleteByFollowee(social);
        ordered.verify(blockRepository).deleteByBlocker(social);
        ordered.verify(blockRepository).deleteByBlocked(social);
        ordered.verify(bookRepository).deleteByUser(social);
        ordered.verify(userRepository).delete(social);
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    @DisplayName("deleteSocialAccount: LOCAL 계정엔 쓸 수 없다 — 예외, 삭제 없음 (비번 경로 강제)")
    void deleteSocialAccount_localAccount_throwsAndDeletesNothing() {
        User local = userWithHash(); // LOCAL
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(local));

        assertThatThrownBy(() -> service.deleteSocialAccount(EMAIL))
                .isInstanceOf(IllegalStateException.class);

        verify(sessionRepository, never()).deleteByUser(any());
        verify(timerRepository, never()).deleteByUser(any());
        verify(userRepository, never()).delete(any());
    }
}
