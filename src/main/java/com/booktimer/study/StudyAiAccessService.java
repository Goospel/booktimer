package com.booktimer.study;

import com.booktimer.user.StudyAiAccess;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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

    private static final Logger log = LoggerFactory.getLogger(StudyAiAccessService.class);

    /**
     * 동시에 승인해 둘 수 있는 최대 인원 — <b>2차 방어선</b>이다.
     *
     * <p>1차는 {@link StudyAiUsageService}의 전역 하루 상한(총액을 묶는다)이고, 이건 그 위에서 사고의
     * <b>모양</b>을 작게 만든다: 정원이 있으면 관리자 계정이 뚫려도 「전원 승인」 자체가 불가능해진다.
     * 둘은 서로 독립이라 하나가 뚫려도 다른 하나가 남는다(보안 리뷰 2026-09-08 S-1).
     *
     * <p><b>프로퍼티가 아니라 상수인 것이 의도다</b> — 전역 상한은 사고 중에 배포 없이 0으로 내려야 하는
     * 차단기라 프로퍼티지만, 정원 확대는 사고 대응이 아니라 <b>제품 결정</b>이다(누구에게 AI를 열어
     * 줄 것인가). 배포를 거치는 편이 맞고, 그 마찰이 여기서는 기능이다.
     *
     * <p>1인 것은 2026-09-08 현재 실제로 승인자가 소유자 한 명뿐이기 때문이다 — 즉 <b>정원이 이미 차 있다</b>.
     * 베타로 사람을 붙이는 날 이 값을 올리는 PR을 낸다.
     */
    static final int MAX_APPROVED = 1;

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

    /**
     * @return 대상 사용자, 또는 그 아이디가 없으면 {@code empty}(호출부가 404)
     * @throws IllegalStateException 정원({@value #MAX_APPROVED}명)이 찼거나 잘못된 전이(호출부가 플래시 오류)
     */
    public Optional<User> approve(String loginId, Instant now) {
        // 정원 검사가 전이보다 앞이다 — 뒤에 두면 상태를 바꾼 뒤 되돌려야 한다.
        long approved = userRepository.countByStudyAiAccess(StudyAiAccess.APPROVED);
        if (approved >= MAX_APPROVED) {
            log.warn("AI 승인 거절(정원 초과) — target={} approved={}/{}", loginId, approved, MAX_APPROVED);
            throw new IllegalStateException(
                    "승인 정원(" + MAX_APPROVED + "명)이 찼어요 — 기존 승인을 회수한 뒤 다시 시도해 주세요");
        }
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
     * <p>{@code SUPPORTS}인 것은 이 메서드가 DB를 만지지 않기 때문이다 — 클래스 레벨
     * {@code @Transactional}을 그대로 물려받으면, 호출부가 트랜잭션 밖에서 부를 때 빈 트랜잭션이
     * 열렸다 닫힌다(AI 호출을 트랜잭션 밖에 두는 다음 판에서 정확히 그 모양이 된다).
     *
     * @throws ResponseStatusException 403, 상태가 {@link StudyAiAccess#APPROVED}가 아닌 경우
     */
    @Transactional(propagation = Propagation.SUPPORTS)
    public void requireApproved(User user) {
        if (user.getStudyAiAccess() != StudyAiAccess.APPROVED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "AI 기능은 승인 후 쓸 수 있어요");
        }
    }

    private Optional<User> transition(String loginId, Consumer<User> move) {
        return userRepository.findByLoginId(loginId).map(user -> {
            StudyAiAccess before = user.getStudyAiAccess();
            move.accept(user); // 잘못된 전이는 IllegalStateException — 호출부가 플래시 오류로 옮긴다
            User saved = userRepository.save(user);
            // 감사 로그 — 이 전이는 「돈이 나가는 문」을 여닫는다. 여기 로그가 없어서, 하룻밤에 전원이
            // 승인돼도 알려 주는 것이 없었다(보안 리뷰 2026-09-08 S-5: 탐지 계층이 통째로 비어 있었다).
            // warn인 것은 의도다 — 정상 운영에서 드문 사건이라 info에 묻히면 안 되고, 사고 때 한 번의
            // grep으로 전부 나와야 한다.
            log.warn("AI 접근 전이 — target={} {} -> {}", loginId, before, saved.getStudyAiAccess());
            return saved;
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
