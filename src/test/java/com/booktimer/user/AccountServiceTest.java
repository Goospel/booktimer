package com.booktimer.user;

import com.booktimer.block.BlockRepository;
import com.booktimer.book.BookRepository;
import com.booktimer.feedback.FeedbackRepository;
import com.booktimer.follow.FollowRepository;
import com.booktimer.personality.ReadingPersonalityCacheRepository;
import com.booktimer.report.ReportRepository;
import com.booktimer.session.ReadingSessionRepository;
import com.booktimer.story.StoryRepository;
import com.booktimer.story.StoryViewRepository;
import com.booktimer.timer.ReadingGoalChangeRepository;
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
    private ReadingGoalChangeRepository goalChangeRepository;
    @Mock
    private com.booktimer.session.ReadingGoalWaiverRepository goalWaiverRepository;
    @Mock
    private ReadingSessionRepository sessionRepository;
    @Mock
    private FollowRepository followRepository;
    @Mock
    private BlockRepository blockRepository;
    @Mock
    private ReportRepository reportRepository;
    @Mock
    private BookRepository bookRepository;
    @Mock
    private ReadingPersonalityCacheRepository personalityCacheRepository;
    @Mock
    private FeedbackRepository feedbackRepository;
    @Mock
    private com.booktimer.email.EmailTokenRepository emailTokenRepository;
    @Mock
    private StoryRepository storyRepository;
    @Mock
    private StoryViewRepository storyViewRepository;
    @Mock
    private com.booktimer.auth.ApiTokenRepository apiTokenRepository;
    @Mock
    private TossLinkCodeRepository tossLinkCodeRepository;
    @Mock
    private com.booktimer.garden.AuthorAffectionRepository affectionRepository;
    @Mock
    private com.booktimer.security.SessionInvalidator sessionInvalidator;
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

        var ordered = inOrder(sessionRepository, timerRepository, goalChangeRepository, goalWaiverRepository, followRepository, blockRepository, reportRepository, storyViewRepository, storyRepository, bookRepository, personalityCacheRepository, feedbackRepository, emailTokenRepository, apiTokenRepository, tossLinkCodeRepository, userRepository);
        ordered.verify(sessionRepository).deleteByUser(user); // book FK 참조하는 세션 먼저
        ordered.verify(timerRepository).deleteByUser(user);
        ordered.verify(goalChangeRepository).deleteByUser(user);   // FK: 목표 변경 이력도 유저 전에 정리
        ordered.verify(goalWaiverRepository).deleteByUser(user);   // FK: 용서권도 유저 전에 정리
        ordered.verify(followRepository).deleteByFollower(user);   // FK: 유저 삭제 전에 관계 정리
        ordered.verify(followRepository).deleteByFollowee(user);
        ordered.verify(blockRepository).deleteByBlocker(user);     // FK: 유저 삭제 전에 차단 관계 정리
        ordered.verify(blockRepository).deleteByBlocked(user);
        ordered.verify(reportRepository).deleteByReporter(user);   // FK: 유저 삭제 전에 신고 관계 정리
        ordered.verify(reportRepository).deleteByReported(user);
        ordered.verify(storyViewRepository).deleteByViewer(user);      // FK: 내가 남긴 열람 기록
        ordered.verify(storyViewRepository).deleteByStoryAuthor(user); // FK: 내 스토리의 열람 기록(스토리 전)
        ordered.verify(storyRepository).deleteByUser(user);            // FK: 스토리는 book 참조라 책보다 앞
        ordered.verify(bookRepository).deleteByUser(user);    // FK: 유저 삭제 전에 책 정리(세션·스토리 이후)
        ordered.verify(personalityCacheRepository).deleteByUser(user); // FK: 책BTI 캐시도 유저 전에 정리
        ordered.verify(feedbackRepository).deleteByAuthor(user);  // FK: 문의도 유저 전에 정리
        ordered.verify(emailTokenRepository).deleteByUser(user);  // FK: 이메일 토큰도 유저 전에 정리
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

    private User socialWithHandle() {
        User social = User.ofOAuth(EMAIL, "구글러", "Asia/Seoul", Role.USER, AuthProvider.GOOGLE);
        social.assignLoginId("googler");
        return social;
    }

    @Test
    @DisplayName("deleteSocialAccount: @핸들이 일치하면 비번 확인 없이 세션→타이머→유저 순으로 삭제")
    void deleteSocialAccount_handleMatches_deletesInFkOrder() {
        User social = socialWithHandle();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(social));

        service.deleteSocialAccount(EMAIL, "googler");

        var ordered = inOrder(sessionRepository, timerRepository, goalChangeRepository, goalWaiverRepository, followRepository, blockRepository, reportRepository, bookRepository, personalityCacheRepository, feedbackRepository, emailTokenRepository, apiTokenRepository, tossLinkCodeRepository, userRepository);
        ordered.verify(sessionRepository).deleteByUser(social);
        ordered.verify(timerRepository).deleteByUser(social);
        ordered.verify(goalChangeRepository).deleteByUser(social);   // FK: 목표 변경 이력도 유저 전에 정리
        ordered.verify(goalWaiverRepository).deleteByUser(social);   // FK: 용서권도 유저 전에 정리
        ordered.verify(followRepository).deleteByFollower(social);
        ordered.verify(followRepository).deleteByFollowee(social);
        ordered.verify(blockRepository).deleteByBlocker(social);
        ordered.verify(blockRepository).deleteByBlocked(social);
        ordered.verify(reportRepository).deleteByReporter(social);
        ordered.verify(reportRepository).deleteByReported(social);
        ordered.verify(bookRepository).deleteByUser(social);
        ordered.verify(personalityCacheRepository).deleteByUser(social); // FK: 책BTI 캐시도 유저 전에 정리
        ordered.verify(feedbackRepository).deleteByAuthor(social);  // FK: 문의도 유저 전에 정리
        ordered.verify(emailTokenRepository).deleteByUser(social);  // FK: 이메일 토큰도 유저 전에 정리
        ordered.verify(userRepository).delete(social);
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    @DisplayName("deleteSocialAccount: 입력한 핸들이 @접두·대소문자만 달라도 일치로 보고 삭제")
    void deleteSocialAccount_handleLenientMatch_deletes() {
        User social = socialWithHandle();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(social));

        service.deleteSocialAccount(EMAIL, "  @GoogLer "); // 앞뒤 공백·@접두·대문자 정규화 후 일치

        verify(userRepository).delete(social);
    }

    @Test
    @DisplayName("deleteSocialAccount: @핸들이 일치하지 않으면 확인 예외, 아무것도 삭제하지 않는다")
    void deleteSocialAccount_handleMismatch_throwsAndDeletesNothing() {
        User social = socialWithHandle();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(social));

        assertThatThrownBy(() -> service.deleteSocialAccount(EMAIL, "wrong"))
                .isInstanceOf(AccountDeletionConfirmationException.class);

        verify(sessionRepository, never()).deleteByUser(any());
        verify(userRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleteSocialAccount: LOCAL 계정엔 쓸 수 없다 — 예외, 삭제 없음 (비번 경로 강제)")
    void deleteSocialAccount_localAccount_throwsAndDeletesNothing() {
        User local = userWithHash(); // LOCAL
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(local));

        assertThatThrownBy(() -> service.deleteSocialAccount(EMAIL, "anything"))
                .isInstanceOf(IllegalStateException.class);

        verify(sessionRepository, never()).deleteByUser(any());
        verify(timerRepository, never()).deleteByUser(any());
        verify(userRepository, never()).delete(any());
    }

    // --- 미니앱 탈퇴 (토스 재인증으로 신원 확인) ---
    //
    // 미니앱 전용 계정은 비밀번호도 없고 login_id도 null일 수 있어(핸들 미작성) 위 두 경로의 확인 수단이
    // 둘 다 성립하지 않는다. 대신 컨트롤러가 fresh 인가코드를 토스에 물어 얻은 userKey를 넘기고,
    // 이 게이트가 본인 toss_user_key와 대조한다. **급소는 "불일치면 아무것도 지우지 않는다"**다 —
    // 탈퇴는 되돌릴 수 없어서, 게이트가 새면 남의 계정이 사라진다.

    private User tossUser(String userKey) {
        User toss = User.ofOAuth(EMAIL, "토스유저", "Asia/Seoul", Role.USER, AuthProvider.TOSS);
        toss.linkTossUserKey(userKey);
        return toss;
    }

    @Test
    @DisplayName("deleteTossVerifiedAccount: userKey가 일치하면 세션→…→유저 순으로 삭제한다")
    void deleteTossVerifiedAccount_match_deletesInFkOrder() {
        User toss = tossUser("uk-mine");

        service.deleteTossVerifiedAccount(toss, "uk-mine");

        var ordered = inOrder(sessionRepository, timerRepository, goalChangeRepository, goalWaiverRepository,
                followRepository, blockRepository, reportRepository, storyViewRepository, storyRepository,
                bookRepository, personalityCacheRepository, feedbackRepository, emailTokenRepository,
                apiTokenRepository, tossLinkCodeRepository, userRepository);
        ordered.verify(sessionRepository).deleteByUser(toss);
        ordered.verify(timerRepository).deleteByUser(toss);
        ordered.verify(storyViewRepository).deleteByViewer(toss);
        ordered.verify(storyRepository).deleteByUser(toss);
        ordered.verify(bookRepository).deleteByUser(toss);
        // 미니앱 Bearer 토큰까지 지워야 탈퇴 즉시 그 토큰이 죽는다(계정만 지우면 토큰이 유령으로 남는다).
        ordered.verify(apiTokenRepository).deleteByUser(toss);
        ordered.verify(userRepository).delete(toss);
        verify(passwordEncoder, never()).matches(any(), any()); // 비밀번호가 없는 계정이다
    }

    @Test
    @DisplayName("deleteTossVerifiedAccount: userKey가 다르면 확인 예외, 아무것도 삭제하지 않는다")
    void deleteTossVerifiedAccount_mismatch_throwsAndDeletesNothing() {
        User toss = tossUser("uk-mine");

        assertThatThrownBy(() -> service.deleteTossVerifiedAccount(toss, "uk-someone-else"))
                .isInstanceOf(AccountDeletionConfirmationException.class);

        verify(sessionRepository, never()).deleteByUser(any());
        verify(bookRepository, never()).deleteByUser(any());
        verify(apiTokenRepository, never()).deleteByUser(any());
        verify(userRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleteTossVerifiedAccount: toss_user_key 없는 계정은 예외, 삭제 없음 (null == null 통과 금지)")
    void deleteTossVerifiedAccount_noTossUserKey_throwsAndDeletesNothing() {
        User webOnly = userWithHash(); // toss_user_key = null

        assertThatThrownBy(() -> service.deleteTossVerifiedAccount(webOnly, null))
                .isInstanceOf(AccountDeletionConfirmationException.class);

        verify(sessionRepository, never()).deleteByUser(any());
        verify(userRepository, never()).delete(any());
    }

    // --- 아이디 평생 1회 변경 (previous_login_id로 옛 핸들 잠금) ---

    private User userWithHandle(String loginId) {
        User user = userWithHash();
        user.assignLoginId(loginId);
        return user;
    }

    @Test
    @DisplayName("changeLoginId: 성공하면 새 아이디로 바뀌고 옛 아이디가 previous로 남은 채 저장된다")
    void changeLoginId_success_savesTransition() {
        User user = userWithHandle("oldhandle");
        when(userRepository.existsByLoginId("newhandle")).thenReturn(false);
        when(userRepository.existsByPreviousLoginId("newhandle")).thenReturn(false);

        service.changeLoginId(user, "NewHandle"); // 정규화도 함께 확인

        assertThat(user.getLoginId()).isEqualTo("newhandle");
        assertThat(user.getPreviousLoginId()).isEqualTo("oldhandle");
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("changeLoginId: 남이 현재 쓰는 아이디면 LoginIdAlreadyExistsException — 저장하지 않는다")
    void changeLoginId_takenByCurrentHandle_throws() {
        User user = userWithHandle("oldhandle");
        when(userRepository.existsByLoginId("taken")).thenReturn(true);

        assertThatThrownBy(() -> service.changeLoginId(user, "taken"))
                .isInstanceOf(LoginIdAlreadyExistsException.class);

        assertThat(user.getLoginId()).isEqualTo("oldhandle");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("changeLoginId: 남이 버린 옛 아이디(previous_login_id)도 막는다 — 예약의 본체(사칭 차단)")
    void changeLoginId_takenByPreviousHandle_throws() {
        User user = userWithHandle("oldhandle");
        when(userRepository.existsByLoginId("abandoned")).thenReturn(false);
        when(userRepository.existsByPreviousLoginId("abandoned")).thenReturn(true);

        assertThatThrownBy(() -> service.changeLoginId(user, "abandoned"))
                .isInstanceOf(LoginIdAlreadyExistsException.class);

        assertThat(user.getLoginId()).isEqualTo("oldhandle");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("changeLoginId: 소진 계정은 중복 검사보다 먼저 소진(ISE)으로 끊긴다 — 가드 순서")
    void changeLoginId_alreadyUsed_beatsDuplicateCheck() {
        User user = userWithHandle("oldhandle");
        user.changeLoginId("newhandle"); // 변경권 소진

        // 입력값이 이미 쓰이는 아이디여도, 사용자에게는 "이미 사용 중"이 아니라 "평생 1번"이 나가야 한다.
        assertThatThrownBy(() -> service.changeLoginId(user, "taken"))
                .isInstanceOf(IllegalStateException.class);

        verify(userRepository, never()).existsByLoginId(any());
        verify(userRepository, never()).existsByPreviousLoginId(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("changeLoginId: 현재 아이디와 같으면 중복 검사 전에 IAE — 자기 자신이 '이미 사용 중'으로 오해석되지 않는다")
    void changeLoginId_sameAsCurrent_beatsDuplicateCheck() {
        User user = userWithHandle("oldhandle");

        assertThatThrownBy(() -> service.changeLoginId(user, "OldHandle"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(userRepository, never()).existsByLoginId(any());
        verify(userRepository, never()).save(any());
    }
}
