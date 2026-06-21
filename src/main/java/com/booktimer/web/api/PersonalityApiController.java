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
        User user = currentUserService.resolve(principal);
        if (!user.tryConsumePersonalityRefresh(todayFor(user))) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new RefreshLimitResponse(
                            "REFRESH_LIMIT_EXCEEDED", 0, User.DAILY_PERSONALITY_REFRESH_LIMIT));
        }
        userRepository.save(user);
        ReadingPersonality result = personalityService.reanalyze(user);
        PersonalityView view = PersonalityView.from(
                result, personalityService.history(user),
                ReadingPersonalityService.COLD_START_MIN_BOOKS, ZoneId.of(user.getTimezone()));
        return ResponseEntity.ok(new MutationResponse(
                toViewDto(view),
                user.remainingPersonalityRefreshes(todayFor(user)),
                User.DAILY_PERSONALITY_REFRESH_LIMIT));
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
