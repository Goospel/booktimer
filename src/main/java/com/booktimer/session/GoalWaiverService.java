package com.booktimer.session;

import com.booktimer.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;

/**
 * 밀린 하루 용서권 지급 — 리워드 광고 1회 시청의 보상(설계 §5.1-④).
 *
 * <p><b>대상 날짜를 서버가 고른다</b> — 클라이언트는 날짜를 보내지 않아 조작 표면 자체가 없다. 고르는 규칙은
 * "과거 빠뜨린 날 중 잔여 부채가 가장 큰 날, 동률이면 최신 날"이다. 최대 부채를 고르는 이유는 한 번의
 * 시청으로 체감 감소가 최대가 되게 하기 위함이고, 동률의 최신 우선은 결정적 선택을 위한 고정 규칙이다.
 *
 * <p><b>오늘은 대상이 아니다</b> — 진행 중인 오늘을 광고로 지우는 것은 습관 형성과 정면 충돌한다.
 * 1분 미만 부채인 날도 제외한다(기존 {@code forgivenSubMinute}가 이미 용서한 날이라 지울 빚이 없다).
 *
 * <p><b>횟수 제한은 없다</b>(2026-08-14 — 부채 7일 자동 소멸 폐지와 한 세트). 부채가 계속 누적되므로
 * 갚을 수단도 계속 열려 있어야 한다. 상한 역할은 <b>부채 자체</b>가 한다 — 지울 과거 날이 없으면 400이고,
 * 오늘 몫은 애초에 대상이 아니라, 광고를 무한히 봐도 "이미 밀린 날들을 지우는 것"을 넘어설 수 없다.
 * SSV(서버사이드 보상 검증)가 없어 지급 요청은 클라 주장일 뿐이지만, 그 최악의 피해가 이 경계 안에 갇힌다.
 *
 * <p>남은 제약은 {@code (user, waived_date)} 유니크다 — 같은 날을 두 번 지우는 건 무의미하고, 동시 요청
 * (두 탭)이 같은 날을 집어도 제약이 잡아 {@code DataIntegrityViolationException}이 된다.
 */
@Service
@Transactional(readOnly = true)
public class GoalWaiverService {

    private final ReadingDebtService debtService;
    private final ReadingGoalWaiverRepository waiverRepository;

    public GoalWaiverService(ReadingDebtService debtService, ReadingGoalWaiverRepository waiverRepository) {
        this.debtService = debtService;
        this.waiverRepository = waiverRepository;
    }

    /**
     * 용서권 1회를 지급한다 — 대상 날짜는 서버가 고른다. 횟수 제한은 없고, 지울 날이 없으면 거부된다.
     *
     * @return 용서된 날짜와 그 날 소거된 부채(초)
     * @throws IllegalArgumentException 지울 밀린 날이 없는 경우(→ 400)
     */
    @Transactional
    public WaiveResult waive(User user) {
        LocalDate today = debtService.today(user);
        DayDebtTrace target = debtService.weeklyDebtTrace(user, today).days().stream()
                .filter(d -> !d.isToday() && d.remainingSeconds() >= WeeklyDebtCalculator.MIN_MISSED_DEBT_SECONDS)
                .max(Comparator.comparingLong(DayDebtTrace::remainingSeconds)
                        .thenComparing(DayDebtTrace::date))   // 최대 부채, 동률이면 최신 날
                .orElseThrow(() -> new IllegalArgumentException("지울 밀린 날이 없어요."));

        waiverRepository.save(ReadingGoalWaiver.create(user, target.date(), today));
        return new WaiveResult(target.date(), target.remainingSeconds());
    }

    /**
     * 지금 지급 가능한가 — 미니앱 버튼 노출 조건. 지울 밀린 날이 하나라도 있으면 참이다(횟수 제한 없음).
     *
     * <p>계산 주체를 여기 두어 컨트롤러가 리포지토리를 직접 주입받지 않게 한다.
     */
    public boolean availableFor(User user) {
        return !debtService.weeklyDebt(user).missedDays().isEmpty();
    }

    /**
     * @param waivedDate    용서된 날(유저 TZ 일자)
     * @param waivedSeconds 그 날 소거된 잔여 부채(초) — "N분을 지웠어요" 문구의 원천
     */
    public record WaiveResult(LocalDate waivedDate, long waivedSeconds) {}
}
