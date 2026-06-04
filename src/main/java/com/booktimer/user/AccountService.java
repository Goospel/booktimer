package com.booktimer.user;

import com.booktimer.block.BlockRepository;
import com.booktimer.book.BookRepository;
import com.booktimer.follow.FollowRepository;
import com.booktimer.session.ReadingSessionRepository;
import com.booktimer.timer.ReadingTimerRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계정 보안 유스케이스 — 비밀번호 변경, 회원 탈퇴.
 *
 * <p>둘 다 <b>현재 비밀번호 재확인</b>을 전제로 한다(세션이 살아있어도 민감한 작업은 다시 묻는다).
 * 비밀번호 검증은 {@link PasswordEncoder#matches}, 새 비밀번호 해싱은 {@code encode}로 위임하고,
 * 도메인 교체는 {@link User#changePassword}가 책임진다(엔티티는 평문을 받지 않는다).
 *
 * <p>탈퇴는 연관 cascade가 없으므로 FK 순서대로 <b>세션 → 타이머 → 유저</b> 순으로 지운다.
 */
@Service
@Transactional
public class AccountService {

    private final UserRepository userRepository;
    private final ReadingTimerRepository timerRepository;
    private final ReadingSessionRepository sessionRepository;
    private final FollowRepository followRepository;
    private final BlockRepository blockRepository;
    private final BookRepository bookRepository;
    private final PasswordEncoder passwordEncoder;

    public AccountService(UserRepository userRepository,
                          ReadingTimerRepository timerRepository,
                          ReadingSessionRepository sessionRepository,
                          FollowRepository followRepository,
                          BlockRepository blockRepository,
                          BookRepository bookRepository,
                          PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.timerRepository = timerRepository;
        this.sessionRepository = sessionRepository;
        this.followRepository = followRepository;
        this.blockRepository = blockRepository;
        this.bookRepository = bookRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 현재 비밀번호를 확인한 뒤 새 비밀번호로 교체한다.
     *
     * @throws InvalidPasswordException 현재 비밀번호가 일치하지 않는 경우
     * @throws IllegalStateException    사용자가 없는 경우
     */
    public void changePassword(String email, String currentRawPassword, String newRawPassword) {
        User user = load(email);
        verifyPassword(user, currentRawPassword);
        user.changePassword(passwordEncoder.encode(newRawPassword));
        userRepository.save(user);
    }

    /**
     * 비밀번호를 확인한 뒤 계정과 연관 데이터(세션·타이머)를 모두 삭제한다.
     *
     * @throws InvalidPasswordException 비밀번호가 일치하지 않는 경우
     * @throws IllegalStateException    사용자가 없는 경우
     */
    public void deleteAccount(String email, String rawPassword) {
        User user = load(email);
        verifyPassword(user, rawPassword);
        purge(user);
    }

    /**
     * 소셜(비밀번호 없는) 계정을 삭제한다. 비밀번호가 없으므로 재확인 대신 provider 인증 세션을 전제로 한다.
     * LOCAL 계정에는 쓸 수 없다 — LOCAL은 반드시 비밀번호 확인 경로({@link #deleteAccount})를 거쳐야 한다.
     *
     * @throws IllegalStateException 사용자가 없거나, 대상이 LOCAL 계정인 경우
     */
    public void deleteSocialAccount(String email) {
        User user = load(email);
        if (user.isLocalAccount()) {
            throw new IllegalStateException("local account must be deleted with password verification: " + email);
        }
        purge(user);
    }

    /**
     * 연관 데이터까지 FK 순서로 제거: 세션(N) → 타이머(1:1) → 팔로우(양방향) → 차단(양방향) → 책(N) → 유저.
     * <p>모두 users를 FK 참조하므로 유저 삭제 전에 정리한다. 책은 {@code reading_session.book_id}가
     * book을 FK 참조하므로 <b>세션 이후</b>에 지운다(세션이 책을 가리키는 채로 책을 지우면 위반).
     */
    private void purge(User user) {
        sessionRepository.deleteByUser(user);
        timerRepository.deleteByUser(user);
        followRepository.deleteByFollower(user);
        followRepository.deleteByFollowee(user);
        blockRepository.deleteByBlocker(user);
        blockRepository.deleteByBlocked(user);
        bookRepository.deleteByUser(user);
        userRepository.delete(user);
    }

    private User load(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("user not found: " + email));
    }

    private void verifyPassword(User user, String rawPassword) {
        if (rawPassword == null || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new InvalidPasswordException();
        }
    }
}
