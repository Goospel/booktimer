package com.booktimer.user;

import com.booktimer.auth.ApiTokenRepository;
import com.booktimer.block.BlockRepository;
import com.booktimer.book.BookRepository;
import com.booktimer.email.EmailTokenRepository;
import com.booktimer.feedback.FeedbackRepository;
import com.booktimer.follow.FollowRepository;
import com.booktimer.garden.AuthorAffectionRepository;
import com.booktimer.personality.ReadingPersonalityCacheRepository;
import com.booktimer.report.ReportRepository;
import com.booktimer.security.SessionInvalidator;
import com.booktimer.session.ReadingGoalWaiverRepository;
import com.booktimer.session.ReadingSessionRepository;
import com.booktimer.story.StoryRepository;
import com.booktimer.story.StoryViewRepository;
import com.booktimer.timer.ReadingGoalChangeRepository;
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
    private final ReadingGoalChangeRepository goalChangeRepository;
    private final ReadingGoalWaiverRepository goalWaiverRepository;
    private final ReadingSessionRepository sessionRepository;
    private final FollowRepository followRepository;
    private final BlockRepository blockRepository;
    private final ReportRepository reportRepository;
    private final BookRepository bookRepository;
    private final ReadingPersonalityCacheRepository personalityCacheRepository;
    private final FeedbackRepository feedbackRepository;
    private final EmailTokenRepository emailTokenRepository;
    private final StoryRepository storyRepository;
    private final StoryViewRepository storyViewRepository;
    private final ApiTokenRepository apiTokenRepository;
    private final TossLinkCodeRepository tossLinkCodeRepository;
    private final AuthorAffectionRepository affectionRepository;
    private final SessionInvalidator sessionInvalidator;
    private final PasswordEncoder passwordEncoder;

    public AccountService(UserRepository userRepository,
                          ReadingTimerRepository timerRepository,
                          ReadingGoalChangeRepository goalChangeRepository,
                          ReadingGoalWaiverRepository goalWaiverRepository,
                          ReadingSessionRepository sessionRepository,
                          FollowRepository followRepository,
                          BlockRepository blockRepository,
                          ReportRepository reportRepository,
                          BookRepository bookRepository,
                          ReadingPersonalityCacheRepository personalityCacheRepository,
                          FeedbackRepository feedbackRepository,
                          EmailTokenRepository emailTokenRepository,
                          StoryRepository storyRepository,
                          StoryViewRepository storyViewRepository,
                          ApiTokenRepository apiTokenRepository,
                          TossLinkCodeRepository tossLinkCodeRepository,
                          AuthorAffectionRepository affectionRepository,
                          SessionInvalidator sessionInvalidator,
                          PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.timerRepository = timerRepository;
        this.goalChangeRepository = goalChangeRepository;
        this.goalWaiverRepository = goalWaiverRepository;
        this.sessionRepository = sessionRepository;
        this.followRepository = followRepository;
        this.blockRepository = blockRepository;
        this.reportRepository = reportRepository;
        this.bookRepository = bookRepository;
        this.personalityCacheRepository = personalityCacheRepository;
        this.feedbackRepository = feedbackRepository;
        this.emailTokenRepository = emailTokenRepository;
        this.storyRepository = storyRepository;
        this.storyViewRepository = storyViewRepository;
        this.apiTokenRepository = apiTokenRepository;
        this.tossLinkCodeRepository = tossLinkCodeRepository;
        this.affectionRepository = affectionRepository;
        this.sessionInvalidator = sessionInvalidator;
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
     * 소셜(비밀번호 없는) 계정을 삭제한다. 비밀번호가 없어 비번 재인증을 못 하므로, 대신 <b>본인 공개 @핸들
     * (login_id)을 정확히 입력</b>받아 재확인한다 — 우발적·CSRF성 삭제 방어(GitHub "저장소 이름 입력" 패턴).
     * 입력 핸들은 앞뒤 공백·선행 {@code @}·대소문자만 다른 건 같은 것으로 본다. LOCAL 계정에는 쓸 수 없다 —
     * LOCAL은 반드시 비밀번호 확인 경로({@link #deleteAccount})를 거쳐야 한다.
     *
     * @param confirmHandle 사용자가 재확인용으로 입력한 @핸들(본인 login_id와 일치해야 함)
     * @throws IllegalStateException                 사용자가 없거나, 대상이 LOCAL 계정인 경우
     * @throws AccountDeletionConfirmationException   입력 핸들이 본인 login_id와 일치하지 않는 경우(삭제 안 함)
     */
    public void deleteSocialAccount(String email, String confirmHandle) {
        User user = load(email);
        if (user.isLocalAccount()) {
            throw new IllegalStateException("local account must be deleted with password verification: " + email);
        }
        if (!handleMatches(user, confirmHandle)) {
            throw new AccountDeletionConfirmationException();
        }
        purge(user);
    }

    /**
     * 토스 재인증으로 신원이 확인된 계정을 삭제한다 — 미니앱(앱인토스) 탈퇴 경로.
     *
     * <p>미니앱 전용 계정은 <b>확인 수단이 둘 다 없다</b>: 비밀번호가 없어 {@link #deleteAccount}를 못 쓰고,
     * {@code login_id}가 null일 수 있어(핸들 미작성) {@link #deleteSocialAccount}의 @핸들 재입력도 성립하지
     * 않는다. 대신 컨트롤러가 <b>fresh 인가코드</b>를 토스에 물어 받은 userKey를 넘기고, 여기서 본인
     * {@code toss_user_key}와 대조한다 — 토큰을 훔친 쪽은 토스 앱 본인 인증을 통과할 수 없다.
     *
     * <p>삭제 본체는 웹이 이미 운영 중인 {@link #purge}를 그대로 쓴다(FK 순서·자식 정리를 두 번 짜지 않는다).
     * 이 메서드가 더하는 것은 확인 게이트 하나뿐이다.
     *
     * @param verifiedUserKey 토스가 방금 확인해 준 userKey(컨트롤러가 인가코드에서 얻는다)
     * @throws AccountDeletionConfirmationException userKey 불일치 또는 {@code toss_user_key} 미보유
     *                                             — <b>이때 아무것도 삭제하지 않는다</b>
     */
    public void deleteTossVerifiedAccount(User user, String verifiedUserKey) {
        // toss_user_key가 없으면 무조건 불일치다 — null끼리 같다고 보면 웹 전용 계정이 빈 키로 지워진다.
        if (user.getTossUserKey() == null || !user.getTossUserKey().equals(verifiedUserKey)) {
            throw new AccountDeletionConfirmationException();
        }
        purge(user);
    }

    /** 입력 핸들을 정규화(공백 제거·선행 @ 제거·소문자)해 본인 login_id와 같은지 본다. login_id 미설정/입력 null이면 불일치. */
    private boolean handleMatches(User user, String confirmHandle) {
        String actual = user.getLoginId();
        if (actual == null || confirmHandle == null) {
            return false;
        }
        String typed = confirmHandle.strip();
        if (typed.startsWith("@")) {
            typed = typed.substring(1);
        }
        return actual.equals(typed.toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * 연관 데이터까지 FK 순서로 제거: 세션(N) → 타이머(1:1) → 목표 변경 이력(N) → 용서권(N) → 팔로우(양방향) → 차단(양방향)
     * → 신고(양방향) → 스토리 열람·스토리(N) → 책(N) → 책BTI 캐시(1) → … → 작가 정(N) → 유저 → 로그인 세션.
     * <p>모두 users를 FK 참조하므로 유저 삭제 전에 정리한다. 책은 {@code reading_session.book_id}가
     * book을 FK 참조하므로 <b>세션 이후</b>에 지운다(세션이 책을 가리키는 채로 책을 지우면 위반).
     * 스토리도 같은 이유로 <b>책보다 앞</b>에 지운다({@code story.book_id}가 book을 참조).
     *
     * <p><b>여기서 하나라도 빠지면 그 자식을 가진 사용자는 탈퇴 자체가 실패한다</b> — 모든 FK가
     * {@code NO ACTION}(cascade 없음)이라 DB가 대신 지워 주지 않는다. 실제로 {@code author_affection}이
     * 빠져 있어 운영 27명 중 2명이 탈퇴 불가였다(2026-08-15 실측). 목록이 다시 벌어지지 않도록
     * {@code FlywayMigrationTest#everyTableWithForeignKeyToUsersIsClearedByPurge}가 <b>실제 마이그레이션
     * 스키마의 FK 집합</b>과 이 목록을 양방향으로 대조한다 — 메인 스위트(Hibernate 생성 스키마)는 JPA에
     * 매핑되지 않은 테이블을 아예 못 보기 때문에 거기선 잡히지 않는다.
     *
     * <p>마지막의 세션 정리는 FK와 무관하다 — {@code SPRING_SESSION}은 users를 참조하지 않아 제약 위반이
     * 나지 않고, 대신 <b>지워진 계정의 인증 세션이 그대로 살아남는다</b>.
     */
    private void purge(User user) {
        sessionRepository.deleteByUser(user);
        timerRepository.deleteByUser(user);
        goalChangeRepository.deleteByUser(user);   // FK: reading_goal_change.user_id → users (유저 삭제 전 정리)
        goalWaiverRepository.deleteByUser(user);   // FK: reading_goal_waiver.user_id → users (리워드 광고 용서 기록)
        followRepository.deleteByFollower(user);
        followRepository.deleteByFollowee(user);
        blockRepository.deleteByBlocker(user);
        blockRepository.deleteByBlocked(user);
        reportRepository.deleteByReporter(user);
        reportRepository.deleteByReported(user);
        storyViewRepository.deleteByViewer(user);      // 내가 남긴 열람 기록
        storyViewRepository.deleteByStoryAuthor(user); // 내 스토리에 달린 열람 기록 (스토리 삭제 전)
        storyRepository.deleteByUser(user);            // 내 스토리 — story.book_id 때문에 책보다 앞
        bookRepository.deleteByUser(user);
        personalityCacheRepository.deleteByUser(user);       // 책BTI 캐시도 user_id FK 참조 → 유저 전에 정리
        feedbackRepository.deleteByAuthor(user);             // 문의도 author_id FK 참조 → 유저 전에 정리
        emailTokenRepository.deleteByUser(user);             // 이메일 토큰도 user_id FK 참조 → 유저 전에 정리
        apiTokenRepository.deleteByUser(user);                // 미니앱 Bearer 토큰(api_token.user_id FK)
        tossLinkCodeRepository.deleteByUser(user);            // 토스 연결 코드(toss_link_code.user_id FK)
        affectionRepository.deleteByUser(user);               // 작가 먹이주기 정(author_affection.user_id FK)
        userRepository.delete(user);
        // 세션은 users를 FK 참조하지 않아 계정을 지워도 남는다 — 그대로 두면 계정 없는 principal이
        // 인증된 채 떠다닌다(운영 실측: 계정 하나 삭제에 616건 잔존). 남길 창이 없으므로 전부 끊는다.
        sessionInvalidator.invalidate(user, null);
    }

    /**
     * pre-hijacking 차단(이메일 인프라 1단계 PR-B) — 같은 이메일로 OAuth가 들어왔을 때, 그 이메일을 미검증으로
     * 선점한 LOCAL 계정을 폐기한다. 미검증 = 이메일 소유 미증명이라 폐기가 안전하고, provider(Google)가 소유를
     * 보증한 OAuth가 그 이메일의 진짜 주인이다(N-053). FK 자식까지 {@link #purge}로 정리한다(가입 직후라 거의 비어 있음).
     *
     * <p><b>flush 필수</b>: 호출 직후 같은 이메일로 OAuth 사용자를 새로 INSERT하므로, 삭제를 먼저 DB에 반영하지
     * 않으면 Hibernate 액션 큐가 INSERT를 DELETE보다 먼저 실행해 {@code uk_users_email} 유니크 제약을 위반한다.
     * 그래서 폐기 후 즉시 flush해 같은 트랜잭션 안에서 삭제→삽입 순서를 강제한다.
     */
    public void purgeUnverifiedLocalAccount(User user) {
        purge(user);
        userRepository.flush();
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
