package com.booktimer.study;

import com.booktimer.study.StudyAiUsage.Kind;
import com.booktimer.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * AI 호출의 하루 상한 — 「선점 → 호출 → 실패하면 환불」의 선점·환불 쪽.
 *
 * <p><b>트랜잭션을 클래스에 두르지 않는다.</b> 호출부(분석)는 AI 호출을 트랜잭션 밖에 두려고
 * {@code SUPPORTS}로 도는데, 여기서 트랜잭션을 열어 감싸면 그 안에서 외부 호출을 기다리는 모양이 된다.
 * 대신 리포지터리 메서드마다 자기 트랜잭션이 붙어 있어(그쪽 javadoc) 각 문장이 즉시 커밋된다 —
 * 그래야 동시 요청이 서로의 선점을 본다.
 *
 * <p>설계는 {@code REQUIRES_NEW}였다. 바꾼 이유는 <b>테스트에서만 다른 결과</b>가 나오기 때문이다:
 * {@code @Transactional} 통합 테스트는 사용자를 커밋하지 않은 채 두는데, REQUIRES_NEW로 열린 안쪽
 * 트랜잭션은 그 사용자를 못 봐 FK 위반으로 죽는다. 운영 경로는 애초에 트랜잭션이 없어 둘이 같다.
 */
@Service
public class StudyAiUsageService {

    private static final Logger log = LoggerFactory.getLogger(StudyAiUsageService.class);

    private final StudyAiUsageRepository usageRepository;

    public StudyAiUsageService(StudyAiUsageRepository usageRepository) {
        this.usageRepository = usageRepository;
    }

    /**
     * 오늘 몫에서 한 번을 선점한다.
     *
     * <p>흐름이 「UPDATE 먼저, 없으면 INSERT 후 다시 UPDATE」인 것이 요점이다 — 판단이 전부 DB의 WHERE
     * 안에 있어 동시 요청 열이 와도 정확히 {@code max}개만 통과한다. INSERT 경합은 UNIQUE가 심판하고,
     * 진 쪽은 예외를 삼키고 두 번째 UPDATE로 간다(그때는 행이 있으므로 정상 경쟁이 된다).
     *
     * @return 선점했으면 {@code true}, 오늘 몫을 다 썼으면 {@code false}(호출부가 429)
     */
    public boolean tryConsume(User user, LocalDate day, Kind kind) {
        if (usageRepository.consume(user, day, kind, kind.max()) == 1) {
            return true;
        }
        if (usageRepository.existsByUserAndUsageDateAndKind(user, day, kind)) {
            return false; // 행은 있는데 못 늘렸다 = 오늘 몫 소진
        }
        try {
            usageRepository.save(StudyAiUsage.of(user, day, kind));
        } catch (DataAccessException e) {
            log.debug("상한 카운터 생성 경합 — 다시 선점 시도: {}", e.toString());
        }
        return usageRepository.consume(user, day, kind, kind.max()) == 1;
    }

    /**
     * 선점한 몫을 되돌린다 — <b>외부 호출이 실패했을 때만</b> 부른다. 장애로 오늘 몫을 잃지 않게 하는
     * 장치이지, 취소 기능이 아니다.
     */
    public void refund(User user, LocalDate day, Kind kind) {
        usageRepository.refund(user, day, kind);
    }

    /** 오늘 남은 몫 — 화면이 버튼 옆에 그린다. 행이 없으면 아직 아무것도 안 쓴 것이다. */
    public int remaining(User user, LocalDate day, Kind kind) {
        int used = usageRepository.findByUserAndUsageDateAndKind(user, day, kind)
                .map(StudyAiUsage::getUsed)
                .orElse(0);
        return Math.max(0, kind.max() - used);
    }
}
