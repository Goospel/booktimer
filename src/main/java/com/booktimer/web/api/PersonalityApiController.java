package com.booktimer.web.api;

import com.booktimer.personality.ReadingPersonality;
import com.booktimer.personality.ReadingPersonalityService;
import com.booktimer.security.CurrentUserService;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import com.booktimer.web.PersonalityView;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * 책BTI(독서 성향) Vue 섬 JSON API (선별 SPA 단계 1c).
 *
 * <p>GET 조회 + POST refresh(LLM 동기 호출, 폴링 없이 응답에 갱신 view 탑재) + POST select(IDOR 가드 서비스 위임).
 * 기존 {@link com.booktimer.web.PersonalityController}의 뮤테이션 엔드포인트를 이관.
 * 도메인 로직은 기존 서비스 재사용 — 새 로직 0. SecurityConfig default-deny 자동 인증 보호.
 */
@RestController
@RequestMapping("/api/personality")
public class PersonalityApiController {

    private final CurrentUserService currentUserService;
    private final ReadingPersonalityService personalityService;
    private final UserRepository userRepository;
    private final Clock clock;

    public PersonalityApiController(CurrentUserService currentUserService,
                                    ReadingPersonalityService personalityService,
                                    UserRepository userRepository,
                                    Clock clock) {
        this.currentUserService = currentUserService;
        this.personalityService = personalityService;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @GetMapping
    public PersonalityResponse get(Principal principal) {
        User user = currentUserService.resolve(principal);
        ReadingPersonality result = personalityService.currentPersonality(user);
        PersonalityView view = PersonalityView.from(
                result, personalityService.history(user),
                ReadingPersonalityService.COLD_START_MIN_BOOKS, ZoneId.of(user.getTimezone()));
        return new PersonalityResponse(
                user.getNickname(),
                user.getLoginId(),
                toViewDto(view),
                user.remainingPersonalityRefreshes(todayFor(user)),
                User.DAILY_PERSONALITY_REFRESH_LIMIT);
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(Principal principal) {
        return doRefresh(currentUserService.resolve(principal), User.DAILY_PERSONALITY_REFRESH_LIMIT);
    }

    /**
     * 미니앱 광고 관문의 사전 판정 — <b>부트스트랩(LLM·저장) 부작용이 없는 유일한 성향 GET</b>이다.
     * {@code GET /api/personality}는 히스토리가 비면 첫 분석을 공짜로 만들어 관문을 무력화하므로 쓸 수 없다(설계 §3.4).
     *
     * <p>잔여는 <b>총량({@link User#DAILY_PERSONALITY_TOTAL_LIMIT}) 기준</b> 하나만 준다 — 미니앱은 광고 경로만
     * 쓰고, 웹은 자기 천장(3) 기준 잔여를 {@code GET /api/personality}에서 이미 받는다(필드가 겹치지 않는다).
     */
    @GetMapping("/status")
    public StatusResponse status(Principal principal) {
        User user = currentUserService.resolve(principal);
        ReadingPersonalityService.AnalysisGate gate = personalityService.gate(user);
        return new StatusResponse(
                gate.coldStart(),
                gate.hasSelected(),
                user.remainingPersonalityRefreshes(todayFor(user), User.DAILY_PERSONALITY_TOTAL_LIMIT),
                User.DAILY_PERSONALITY_TOTAL_LIMIT);
    }

    /**
     * 광고 경로 refresh — 천장만 총량({@link User#DAILY_PERSONALITY_TOTAL_LIMIT})이고 나머지는 웹 refresh와 같다.
     *
     * <p>⚠️ 이름과 달리 <b>서버는 광고 시청을 검증할 수 없다</b>("광고를 봤다"는 클라 주장 — 토스 SDK에 서버사이드
     * 보상 검증이 없다). 이 경로의 실질 계약은 "천장이 하루 총량인 refresh"이고, 남용 방어는 그 총량이 전부다(설계 §3.1).
     */
    @PostMapping("/ad-refresh")
    public ResponseEntity<?> adRefresh(Principal principal) {
        return doRefresh(currentUserService.resolve(principal), User.DAILY_PERSONALITY_TOTAL_LIMIT);
    }

    /**
     * 두 refresh 경로의 공통 골격 — 천장만 다르다. <b>카운트는 선소비</b>라 LLM이 실패해도 소비된다
     * (기존 웹 동작 그대로 — 환불은 만들지 않는다).
     */
    private ResponseEntity<?> doRefresh(User user, int limit) {
        if (!user.tryConsumePersonalityRefresh(todayFor(user), limit)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new RefreshLimitResponse("REFRESH_LIMIT_EXCEEDED", 0, limit));
        }
        userRepository.save(user);
        ReadingPersonality result = personalityService.reanalyze(user);
        PersonalityView view = PersonalityView.from(
                result, personalityService.history(user),
                ReadingPersonalityService.COLD_START_MIN_BOOKS, ZoneId.of(user.getTimezone()));
        return ResponseEntity.ok(new MutationResponse(
                toViewDto(view),
                user.remainingPersonalityRefreshes(todayFor(user), limit),
                limit));
    }

    @PostMapping("/select/{id}")
    public MutationResponse select(Principal principal, @PathVariable("id") Long id) {
        User user = currentUserService.resolve(principal);
        personalityService.select(user, id);
        ReadingPersonality result = personalityService.currentPersonality(user);
        PersonalityView view = PersonalityView.from(
                result, personalityService.history(user),
                ReadingPersonalityService.COLD_START_MIN_BOOKS, ZoneId.of(user.getTimezone()));
        return new MutationResponse(
                toViewDto(view),
                user.remainingPersonalityRefreshes(todayFor(user)),
                User.DAILY_PERSONALITY_REFRESH_LIMIT);
    }

    // ── DTO ─────────────────────────────────────────────────────────────────

    public record PersonalityResponse(
            String nickname,
            String loginId,
            ViewDto view,
            int refreshRemaining,
            int refreshLimit) {
    }

    public record MutationResponse(
            ViewDto view,
            int refreshRemaining,
            int refreshLimit) {
    }

    /**
     * 광고 관문 사전 판정 응답 — 클라는 이 넷으로 광고 버튼을 그릴지 정한다.
     *
     * @param adRefreshRemaining 총량 천장 기준 오늘 남은 분석 횟수(0이면 버튼 대신 "내일 다시" 안내)
     * @param adRefreshLimit     그 천장 값 자체
     */
    public record StatusResponse(
            boolean coldStart,
            boolean hasSelected,
            int adRefreshRemaining,
            int adRefreshLimit) {
    }

    public record RefreshLimitResponse(
            String error,
            int refreshRemaining,
            int refreshLimit) {
    }

    /** ZoneId·enum을 제거하고 문자열로 평탄화한 JSON 계약 DTO. */
    public record ViewDto(
            String state,
            String narrative,
            List<String> tags,
            com.booktimer.personality.ReadingProfile profile,
            int coldStartMinBooks,
            List<EntryDto> entries) {
    }

    /** PersonalityHistoryEntry → JSON DTO: Instant를 ISO 문자열·사용자 타임존 라벨로 분리. */
    public record EntryDto(
            long id,
            String narrative,
            List<String> tags,
            String generatedAt,
            String generatedAtLabel,
            boolean selected,
            boolean stale) {
    }

    // ── 변환 ────────────────────────────────────────────────────────────────

    private ViewDto toViewDto(PersonalityView view) {
        List<EntryDto> entries = view.displayEntries().stream()
                .map(e -> new EntryDto(
                        e.id(),
                        e.narrative(),
                        e.tags(),
                        e.generatedAt() != null ? e.generatedAt().toString() : null,
                        view.formatTime(e.generatedAt()),
                        e.selected(),
                        e.stale()))
                .toList();
        return new ViewDto(
                view.state().name(),
                view.narrative(),
                view.tags(),
                view.profile(),
                view.coldStartMinBooks(),
                entries);
    }

    private LocalDate todayFor(User user) {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(user.getTimezone()));
    }
}
