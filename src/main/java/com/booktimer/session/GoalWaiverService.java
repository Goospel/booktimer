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
 * "윈도우 내 과거 빠뜨린 날 중 잔여 부채가 가장 큰 날, 동률이면 최신 날"이다. 최대 부채를 고르는 이유는
 * 한 번의 시청으로 체감 감소가 최대가 되게 하기 위함이고, 동률에서 최신을 고르는 이유는 가장 오래된 날은
 * 어차피 곧 7일 윈도우 밖으로 자동 소멸해 보상 가치가 하루 만에 증발하기 때문이다.
 *
 * <p><b>오늘은 대상이 아니다</b> — 진행 중인 오늘을 광고로 지우는 것은 습관 형성과 정면 충돌한다.
 * 1분 미만 부채인 날도 제외한다(기존 {@code forgivenSubMinute}가 이미 용서한 날이라 지울 빚이 없다).
 *
 * <p><b>일일 1회 상한</b>은 여기서 한 번, DB unique 제약({@code uk_goal_waiver_grant})에서 또 한 번 막는다.
 * SDK에 서버사이드 보상 검증(SSV)이 없어 지급 요청은 클라이언트 주장일 뿐이므로, 신뢰가 아니라 상한으로
 * 피해를 캡한다 — 광고 없이 API를 직접 때려도 얻는 것은 "하루 1회, 밀린 하루 표시 소거"가 전부다.
 * 동시 요청(두 탭) race는 애플리케이션 검사를 통과할 수 있으나 제약이 잡아 {@code DataIntegrityViolationException}이 된다.
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
     * 용서권 1회를 지급한다 — 대상 날짜는 서버가 고른다.
     *
     * @return 용서된 날짜와 그 날 소거된 부채(초)
     * @throws IllegalStateException    오늘 이미 지급받은 경우(→ 409)
     * @throws IllegalArgumentException 지울 밀린 날이 없는 경우(→ 400)
     */
    @Transactional
    public WaiveResult waive(User user) {
        LocalDate today = debtService.today(user);
        if (waiverRepository.existsByUserAndGrantedOn(user, today)) {
            throw new IllegalStateException("오늘은 이미 사용했어요. 내일 다시 지울 수 있어요.");
        }
        DayDebtTrace target = debtService.weeklyDebtTrace(user, today).days().stream()
                .filter(d -> !d.isToday() && d.remainingSeconds() >= WeeklyDebtCalculator.MIN_MISSED_DEBT_SECONDS)
                .max(Comparator.comparingLong(DayDebtTrace::remainingSeconds)
                        .thenComparing(DayDebtTrace::date))   // 최대 부채, 동률이면 최신 날
                .orElseThrow(() -> new IllegalArgumentException("지울 밀린 날이 없어요."));

        waiverRepository.save(ReadingGoalWaiver.create(user, target.date(), today));
        return new WaiveResult(target.date(), target.remainingSeconds());
    }

    /**
     * 오늘 지급 가능한가 — 미니앱 버튼 노출 조건(빠뜨린 날 존재 + 오늘 미사용).
     *
     * <p>계산 주체를 여기 두어 컨트롤러가 리포지토리를 직접 주입받지 않게 한다.
     */
    public boolean availableFor(User user) {
        return !debtService.weeklyDebt(user).missedDays().isEmpty()
                && !waiverRepository.existsByUserAndGrantedOn(user, debtService.today(user));
    }

    /**
     * @param waivedDate    용서된 날(유저 TZ 일자)
     * @param waivedSeconds 그 날 소거된 잔여 부채(초) — "N분을 지웠어요" 문구의 원천
     */
    public record WaiveResult(LocalDate waivedDate, long waivedSeconds) {}
}
