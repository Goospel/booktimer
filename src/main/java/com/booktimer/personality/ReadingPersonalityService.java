package com.booktimer.personality;

import com.booktimer.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 책BTI 분석 오케스트레이션 서비스 — 사실 집계(Phase 2) + LLM 서술(Phase 3) + 저장·히스토리·대표(Phase 4).
 *
 * <p><b>성향은 공개(PUBLIC)+완독 책만으로</b> 뽑는다({@link ReadingProfileService#publicProfileOf}) — 책BTI는
 * 사용자끼리 즐기는 재미 요소라 항상 책방(공개 프로필)에 노출되므로, 비공개 책 취향이 새지 않도록 입력 자체를
 * 공개 책으로 한정한다(설계 §7, 공개/비공개 분기 폐지 2026-06-08).
 *
 * <p><b>히스토리(최대 3행) + 대표(selected) 모델(2026-06-08)</b>: "다시 분석"을 돌려도 과거 서술이 사라지지
 * 않고 최대 3개까지 남는다. 그중 하나가 <b>대표</b>로, 본인 {@code /personality}와 책방 {@code /u/{loginId}}에
 * 노출되고 교체에서 보호된다. 진입점은 셋:
 * <ul>
 *   <li>{@link #currentPersonality}(GET 진입) — 대표를 읽기만 한다. 히스토리가 비었고 책이 충분하면 첫 1개를
 *       부트스트랩 생성(자동 대표). <b>책장이 바뀌어도 자동 재생성하지 않는다</b>(예상치 못한 교체 방지).</li>
 *   <li>{@link #reanalyze}("다시 분석") — 새 분석을 후보로 추가만 한다(대표 불변). 4행이 되면 대표를 뺀 가장
 *       오래된 후보 1행을 버린다. LLM 실패 시 기존 대표(stale)를 내보내고 새 행을 만들지 않는다(N-060).</li>
 *   <li>{@link #select} — 사용자가 대표를 직접 바꾼다(LLM 호출 없음, 본인 행만).</li>
 * </ul>
 * 방문자의 책방 조회는 캐시를 읽기만 한다(비용 안전장치 — {@code ProfileService}).
 */
@Service
@Transactional(readOnly = true)
public class ReadingPersonalityService {

    /**
     * 콜드스타트 임계 — <b>(공개) 완독 책</b>이 이 미만이면 분석을 보류한다(reading-personality-design §6).
     * <p>1권으로 설정(2026-06-08): 정확도는 낮아도 "어떤 결과라도 보여주는 재미" + "책을 쌓을수록 결과가
     * 바뀌는 재미"를 우선(사용자 결정). 완독 0권만 보류한다.
     */
    public static final int COLD_START_MIN_BOOKS = 1;

    /** 한 사용자가 보존하는 분석 히스토리의 최대 개수(대표 1 + 후보 2). 초과 시 가장 오래된 후보부터 버린다. */
    public static final int MAX_HISTORY = 3;

    /** 태그를 한 컬럼에 이어 붙일 때 쓰는 구분자(개행) — 태그 안에 잘 안 나오는 문자. */
    private static final String TAG_DELIMITER = "\n";

    private final ReadingProfileService profileService;
    private final ReadingPersonalityNarrator narrator;
    private final ReadingPersonalityCacheRepository cacheRepository;
    private final Clock clock;

    public ReadingPersonalityService(ReadingProfileService profileService,
                                     ReadingPersonalityNarrator narrator,
                                     ReadingPersonalityCacheRepository cacheRepository,
                                     Clock clock) {
        this.profileService = profileService;
        this.narrator = narrator;
        this.cacheRepository = cacheRepository;
        this.clock = clock;
    }

    /** 사용자의 책BTI 결과(사실 + 가능하면 서술)를 <b>항상 새로</b> 만든다(저장 안 함, 공개 책 기반). 서술 실패 시 사실만 폴백. */
    public ReadingPersonality analyze(User user) {
        ReadingProfile profile = profileService.publicProfileOf(user);
        return narrator.narrate(profile)
                .map(narration -> new ReadingPersonality(profile, narration))
                .orElseGet(() -> ReadingPersonality.factsOnly(profile));
    }

    /**
     * GET 진입점 — 대표(selected) 분석을 돌려준다. <b>책장이 바뀌어도 재생성하지 않는다</b>(생성은 "다시 분석"에서만).
     * 단, 히스토리가 비었고 책이 충분하면 첫 1개를 부트스트랩 생성해 자동 대표로 둔다(빈 화면 방지).
     */
    @Transactional(propagation = Propagation.SUPPORTS) // LLM 호출(부트스트랩)을 트랜잭션 밖으로(커넥션 점유 회피)
    public ReadingPersonality currentPersonality(User user) {
        ReadingProfile profile = profileService.publicProfileOf(user);

        // 콜드스타트 — 공개 완독 책 부족: LLM·저장 없이 사실만 보류
        if (profile.finishedBooks() < COLD_START_MIN_BOOKS) {
            return ReadingPersonality.factsOnly(profile);
        }

        Optional<ReadingPersonalityCache> selected = cacheRepository.findByUserAndSelectedTrue(user);
        if (selected.isPresent()) {
            // 대표를 그대로 보여준다 — 책장이 바뀌었어도 자동 재생성하지 않는다(Q3: '다시 분석'에서만).
            return new ReadingPersonality(profile, narrationOf(selected.get()));
        }

        // 히스토리가 비어 있음 — 첫 분석을 부트스트랩 생성해 대표로 둔다.
        Optional<PersonalityNarration> fresh = narrator.narrate(profile);
        if (fresh.isEmpty()) {
            return ReadingPersonality.factsOnly(profile); // LLM 실패 → 폴백(저장 없음)
        }
        PersonalityNarration narration = fresh.get();
        ReadingPersonalityCache entry = newEntry(user, narration, ProfileSignature.of(profile));
        entry.select(); // 첫 분석은 자동 대표
        cacheRepository.save(entry);
        return new ReadingPersonality(profile, narration);
    }

    /**
     * "다시 분석" 진입점 — 새 분석을 <b>후보로 추가</b>한다(대표는 바꾸지 않는다). 4행이 되면 대표를 뺀 가장
     * 오래된 후보 1행을 버린다. 첫 분석(히스토리 빔)만 부트스트랩으로 자동 대표가 된다.
     *
     * <p>LLM 실패 시 새 행을 만들지 않고 기존 대표(stale)를 내보낸다 — 실패가 좋은 히스토리를 망가뜨리지 않게
     * (serve-stale-on-error, N-060). 콜드스타트(책 부족)면 사실만 폴백(버튼이 숨겨져 정상 경로에선 안 옴).
     */
    @Transactional(propagation = Propagation.SUPPORTS) // LLM 호출을 트랜잭션 밖으로(커넥션 점유 회피)
    public ReadingPersonality reanalyze(User user) {
        ReadingProfile profile = profileService.publicProfileOf(user);
        if (profile.finishedBooks() < COLD_START_MIN_BOOKS) {
            return ReadingPersonality.factsOnly(profile);
        }

        Optional<ReadingPersonalityCache> selected = cacheRepository.findByUserAndSelectedTrue(user);
        Optional<PersonalityNarration> fresh = narrator.narrate(profile);
        if (fresh.isEmpty()) {
            // 실패: 새 행 안 만듦. 기존 대표가 있으면 stale 제공, 없으면 사실만 폴백.
            return selected
                    .map(c -> new ReadingPersonality(profile, narrationOf(c)))
                    .orElseGet(() -> ReadingPersonality.factsOnly(profile));
        }
        PersonalityNarration narration = fresh.get();
        ReadingPersonalityCache entry = newEntry(user, narration, ProfileSignature.of(profile));
        if (selected.isEmpty()) {
            entry.select(); // 첫 분석은 자동 대표(이후엔 후보로만 추가 — 대표 불변)
        }
        cacheRepository.save(entry);
        evictOldestNonSelected(user);
        return new ReadingPersonality(profile, narration);
    }

    /**
     * 사용자의 분석 히스토리(최대 3개)를 최신순으로 돌려준다 — 화면 카드용. 각 행에 대표 여부와 <b>stale</b>
     * (이 분석 이후 책장이 바뀜)을 함께 담는다. 읽기 전용 — LLM/저장을 부르지 않는다.
     */
    @Transactional(readOnly = true)
    public List<PersonalityHistoryEntry> history(User user) {
        String currentSignature = ProfileSignature.of(profileService.publicProfileOf(user));
        return cacheRepository.findByUserOrderByGeneratedAtDescIdDesc(user).stream()
                .map(c -> new PersonalityHistoryEntry(
                        c.getId(), c.getNarrative(), splitTags(c.getTags()), c.getGeneratedAt(),
                        c.isSelected(), !currentSignature.equals(c.getInputSignature())))
                .toList();
    }

    /**
     * 광고 관문의 사전 판정 결과 — 광고를 <b>보여주기 전에</b> 알아야 하는 둘.
     *
     * @param coldStart   공개 완독 책이 {@link #COLD_START_MIN_BOOKS} 미만이라 분석이 보류되는 상태
     * @param hasSelected 대표(selected) 분석 보유 — 첫 분석인지 재분석인지가 여기서 갈린다
     */
    public record AnalysisGate(boolean coldStart, boolean hasSelected) {}

    /**
     * 미니앱 광고 관문 사전 판정 — <b>LLM 호출·저장이 없는 읽기 전용</b>.
     *
     * <p>{@link #currentPersonality}는 히스토리가 비면 첫 분석을 부트스트랩 생성(LLM+저장)하므로 관문 판정에
     * 쓸 수 없다 — 그걸로 물으면 광고 없이 첫 분석이 공짜로 만들어져 관문 자체가 무력화된다. 이 메서드가
     * 따로 있는 이유가 그것이다(설계 §3.4).
     */
    @Transactional(readOnly = true)
    public AnalysisGate gate(User user) {
        ReadingProfile profile = profileService.publicProfileOf(user);
        return new AnalysisGate(
                profile.finishedBooks() < COLD_START_MIN_BOOKS,
                cacheRepository.findByUserAndSelectedTrue(user).isPresent());
    }

    /**
     * 사용자가 대표를 직접 바꾼다 — 지정한 행을 대표로, 기존 대표는 후보로 강등한다. LLM 호출 없음.
     * <b>본인 행만</b> 바꿀 수 있다(IDOR 차단) — 없는 id거나 남의 행이면 조용히 무시한다.
     */
    @Transactional
    public void select(User user, Long entryId) {
        cacheRepository.findById(entryId)
                .filter(target -> target.getUser().getId().equals(user.getId())) // IDOR 가드(본인 행만)
                .ifPresent(target -> {
                    cacheRepository.findByUserAndSelectedTrue(user)
                            .ifPresent(ReadingPersonalityCache::deselect);
                    target.select();
                    cacheRepository.save(target);
                });
    }

    // ── 내부 헬퍼 ──────────────────────────────────────────────────────────

    /** 히스토리가 MAX_HISTORY를 넘으면 대표를 뺀 가장 오래된 후보부터 초과분을 버린다(대표는 항상 보호). */
    private void evictOldestNonSelected(User user) {
        List<ReadingPersonalityCache> oldestFirst = cacheRepository.findByUserOrderByGeneratedAtAscIdAsc(user);
        int over = oldestFirst.size() - MAX_HISTORY;
        if (over <= 0) {
            return;
        }
        List<ReadingPersonalityCache> victims = new ArrayList<>();
        for (ReadingPersonalityCache c : oldestFirst) {
            if (victims.size() >= over) {
                break;
            }
            if (!c.isSelected()) { // 대표는 절대 버리지 않는다
                victims.add(c);
            }
        }
        cacheRepository.deleteAll(victims);
    }

    private ReadingPersonalityCache newEntry(User user, PersonalityNarration narration, String signature) {
        return ReadingPersonalityCache.create(
                user, narration.narrative(), joinTags(narration.tags()), signature, Instant.now(clock));
    }

    private PersonalityNarration narrationOf(ReadingPersonalityCache c) {
        return new PersonalityNarration(c.getNarrative(), splitTags(c.getTags()));
    }

    private static String joinTags(List<String> tags) {
        return String.join(TAG_DELIMITER, tags);
    }

    private static List<String> splitTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String t : tags.split(TAG_DELIMITER)) {
            String s = t.strip();
            if (!s.isEmpty()) {
                result.add(s);
            }
        }
        return result;
    }
}
