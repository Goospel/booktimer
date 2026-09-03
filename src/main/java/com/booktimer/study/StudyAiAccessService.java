package com.booktimer.study;

import com.booktimer.user.StudyAiAccess;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 공부 화면 AI 기능의 승인제 — 사용자의 신청과 관리자의 수락·거절·회수, 그리고 게이트.
 *
 * <p><b>전이 규칙은 여기 없다</b> — {@link User}의 네 메서드가 든다(설계 §2.6). 이 서비스가 하는 일은
 * 셋뿐이다: 트랜잭션 경계를 두르고, 없는 사용자를 {@code Optional.empty}로 돌려주고(호출부가 404로 옮긴다),
 * 관리자 화면이 쓸 두 목록을 만든다.
 *
 * <p>{@link #requireApproved}는 AI 엔드포인트가 붙는 다음 판을 위한 <b>가드 한 곳</b>이다 — 호출부가
 * 셋(일정 생성·사진 전사·복습 분석)이 될 것이라, 미승인일 때 무엇을 말할지가 세 곳으로 갈라지지 않도록
 * 지금 한 곳에 둔다. 이 게이트는 <b>키 유무 검사·상한 선점·외부 호출보다 앞</b>에 서야 한다: 미승인
 * 사용자에게 「AI가 꺼졌다」가 아니라 「승인이 필요하다」를 말해야 하고, 승인 안 된 요청이 상한 카운터를
 * 깎으면 안 된다.
 */
@Service
@Transactional
public class StudyAiAccessService {

    private final UserRepository userRepository;

    public StudyAiAccessService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * 사용자가 AI 기능을 신청한다.
     *
     * @throws IllegalStateException 이미 대기 중이거나 승인된 상태인 경우(호출부가 409로 옮긴다)
     */
    public User request(User user, Instant now) {
        user.requestStudyAi(now);
        return userRepository.save(user);
    }

    /** @return 대상 사용자, 또는 그 아이디가 없으면 {@code empty}(호출부가 404) */
    public Optional<User> approve(String loginId, Instant now) {
        return transition(loginId, user -> user.approveStudyAi(now));
    }

    /** @return 대상 사용자, 또는 그 아이디가 없으면 {@code empty}(호출부가 404) */
    public Optional<User> reject(String loginId, Instant now) {
        return transition(loginId, user -> user.rejectStudyAi(now));
    }

    /** 승인 회수. 이미 저장된 분석 결과·일정은 지우지 않는다 — 과거 산출물은 사용자 것이다. */
    public Optional<User> revoke(String loginId, Instant now) {
        return transition(loginId, user -> user.revokeStudyAi(now));
    }

    /** 관리자 화면의 대기 큐 — 오래 기다린 사람이 위. */
    @Transactional(readOnly = true)
    public List<AiAccessRow> pending() {
        return rows(StudyAiAccess.PENDING);
    }

    /** 관리자 화면의 승인자 목록 — 회수 대상을 고르는 자리다. */
    @Transactional(readOnly = true)
    public List<AiAccessRow> approved() {
        return rows(StudyAiAccess.APPROVED);
    }

    /**
     * AI 호출 직전의 게이트 — 승인된 사용자가 아니면 403으로 막는다.
     *
     * @throws ResponseStatusException 403, 상태가 {@link StudyAiAccess#APPROVED}가 아닌 경우
     */
    public void requireApproved(User user) {
        if (user.getStudyAiAccess() != StudyAiAccess.APPROVED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "AI 기능은 승인 후 쓸 수 있어요");
        }
    }

    private Optional<User> transition(String loginId, Consumer<User> move) {
        return userRepository.findByLoginId(loginId).map(user -> {
            move.accept(user); // 잘못된 전이는 IllegalStateException — 호출부가 플래시 오류로 옮긴다
            return userRepository.save(user);
        });
    }

    private List<AiAccessRow> rows(StudyAiAccess state) {
        return userRepository.findByStudyAiAccessOrderByStudyAiAccessAtAsc(state).stream()
                .map(user -> new AiAccessRow(user.getLoginId(), user.getNickname(), user.getStudyAiAccessAt()))
                .toList();
    }

    /**
     * 관리자 표의 한 줄.
     *
     * @param at 마지막 전이 시각 — 대기 표에선 「신청」, 승인자 표에선 「승인」 시각이다
     */
    public record AiAccessRow(String loginId, String nickname, Instant at) {
    }
}
