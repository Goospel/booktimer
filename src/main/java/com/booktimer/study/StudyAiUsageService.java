package com.booktimer.study;

import com.booktimer.study.StudyAiUsage.Kind;
import com.booktimer.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

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
    private final StudyAiDailyTotalRepository dailyTotalRepository;

    /**
     * 서비스 전체가 하루에 부를 수 있는 AI 호출 수 — <b>차단기</b>다.
     *
     * <p>{@link StudyAiUsage.Kind}의 몫은 상수인데 이건 프로퍼티인 것이 의도다. 거기 javadoc이 「설정으로
     * 빼면 운영에서 몇으로 켜져 있나를 코드에서 못 읽는다」며 상수를 고집하는 것은 <b>제품 규칙</b>이기
     * 때문이고, 이건 규칙이 아니라 사고 대응 장치다 — <b>배포 없이 0으로 내려 전면 차단</b>할 수 있어야 한다.
     *
     * <p>기본값 50의 근거: 가장 비싼 호출(백지복습 분석, 출력 상한 8,192토큰)이 회당 약 119원이라
     * 최악이 하루 약 6,000원이다. 현재 실사용은 승인자 1명이라 하루 한 자릿수 — 정상 운영을 건드리지
     * 않으면서 재앙을 만원 아래로 묶는 값이다.
     */
    private final int dailyCap;

    public StudyAiUsageService(StudyAiUsageRepository usageRepository,
                               StudyAiDailyTotalRepository dailyTotalRepository,
                               @Value("${booktimer.study.ai.daily-cap:50}") int dailyCap) {
        this.usageRepository = usageRepository;
        this.dailyTotalRepository = dailyTotalRepository;
        this.dailyCap = dailyCap;
    }

    /**
     * <b>전역</b> 하루 몫에서 한 번을 선점한다 — 사용자당 상한 위에 얹은 차단기.
     *
     * <p>흐름은 {@link #tryConsume}과 같다(UPDATE 먼저, 없으면 INSERT 후 다시 UPDATE). 다른 것은
     * <b>날짜 키가 UTC</b>라는 점뿐이다: 사용자당 상한은 유저 타임존을 쓰는데 그 값은 사용자가 제한 없이
     * 바꿀 수 있어, 같은 키를 쓰면 자정 근처에 타임존만 바꿔 전역 몫을 두 배로 쓸 수 있다.
     *
     * <p><b>호출 순서는 사용자 몫 다음이다.</b> 전역을 먼저 두면 「내 하루 4번째 요청」 같은 흔한 거절이
     * 매번 이 카운터를 올렸다 되돌린다 — 뒤에 두면 이 수가 「실제로 호출까지 갔을 요청」을 뜻하게 되고,
     * 그게 사고 분석 때 보고 싶은 값이다.
     *
     * @return 선점했으면 {@code true}, 그 날 전체 몫을 다 썼으면 {@code false}(호출부가 503)
     */
    public boolean tryConsumeGlobal(Instant now) {
        LocalDate day = utcDay(now);
        if (dailyTotalRepository.consume(day, dailyCap) == 1) {
            return true;
        }
        if (dailyTotalRepository.existsByUsageDate(day)) {
            return false; // 행은 있는데 못 늘렸다 = 그 날 전체 몫 소진
        }
        try {
            dailyTotalRepository.save(StudyAiDailyTotal.of(day));
        } catch (DataAccessException e) {
            log.debug("전역 상한 카운터 생성 경합 — 다시 선점 시도: {}", e.toString());
        }
        return dailyTotalRepository.consume(day, dailyCap) == 1;
    }

    /**
     * 선점한 전역 몫을 되돌린다 — <b>외부 호출이 실패했을 때만</b>.
     *
     * <p>⚠️ 이 환불을 빠뜨리면 실패한 호출이 <b>서비스 전체의</b> 그 날 예산을 먹는다(사용자당 카운터의
     * 누락보다 파급이 크다). 세 호출부(일정·분석·전사) 전부에 짝이 있어야 한다.
     */
    public void refundGlobal(Instant now) {
        dailyTotalRepository.refund(utcDay(now));
    }

    /** 전역 카운터의 날짜 키 — <b>UTC</b>다(위 경고 참조). */
    private static LocalDate utcDay(Instant now) {
        return LocalDate.ofInstant(now, ZoneOffset.UTC);
    }

    /**
     * 두 몫을 <b>한 번에</b> 선점한다 — 호출부가 짝을 손으로 맞추지 않게 한다.
     *
     * <p>이 메서드가 있는 이유는 순전히 <b>실수 방지</b>다. 호출부는 셋(일정·분석·전사)이고 각각 환불
     * 지점이 둘씩이라, 손으로 짝을 맞추면 6곳 중 하나를 언젠가 빠뜨린다. 그리고 전역 환불 누락은
     * <b>가장 조용한 결함</b>이다 — 실패한 호출이 서비스 전체의 그 날 예산을 야금야금 먹다가, 멀쩡한
     * 사용자가 어느 오후부터 503을 받는다.
     *
     * <p>순서는 <b>사용자 몫이 먼저</b>다({@link #tryConsumeGlobal}의 javadoc 참조). 전역에서 막히면
     * 사용자 몫을 되돌린다 — 서비스 전체 사정으로 개인이 자기 몫을 잃으면 안 된다.
     */
    public Grant tryConsumeBoth(User user, Instant now, Kind kind) {
        LocalDate today = StudyDates.today(user, now);
        if (!tryConsume(user, today, kind)) {
            return Grant.USER_EXHAUSTED;
        }
        if (!tryConsumeGlobal(now)) {
            refund(user, today, kind);
            log.warn("전역 AI 하루 상한 소진 — cap={} user={}", dailyCap, user.getId());
            return Grant.GLOBAL_EXHAUSTED;
        }
        return Grant.OK;
    }

    /** 두 몫을 한 번에 되돌린다 — {@link #tryConsumeBoth}의 짝. 외부 호출이 실패했을 때만 부른다. */
    public void refundBoth(User user, Instant now, Kind kind) {
        refund(user, StudyDates.today(user, now), kind);
        refundGlobal(now);
    }

    /**
     * 선점 결과 — {@code boolean}이 아닌 이유는 <b>거절의 두 갈래가 서로 다른 안내</b>이기 때문이다.
     * 내 몫 소진은 「내일 다시」(429)이고, 전역 소진은 서비스 사정이라 사용자가 고칠 것이 없다(503).
     */
    public enum Grant {
        OK,
        /** 이 사용자가 오늘 자기 몫을 다 썼다. */
        USER_EXHAUSTED,
        /** 서비스 전체가 오늘 몫을 다 썼다 — 사용자 몫은 되돌려 두었다. */
        GLOBAL_EXHAUSTED
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
