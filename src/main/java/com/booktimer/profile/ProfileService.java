package com.booktimer.profile;

import com.booktimer.block.BlockRepository;
import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookVisibility;
import com.booktimer.follow.FollowService;
import com.booktimer.personality.PersonalityTagAttribution;
import com.booktimer.personality.ReadingPersonalityCacheRepository;
import com.booktimer.personality.ReadingProfileService;
import com.booktimer.personality.ReadingTagger;
import com.booktimer.personality.ReadingTribe;
import com.booktimer.session.BookReadingStatsService;
import com.booktimer.session.ReadingContributionService;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 공개 프로필 조회 유스케이스 (SNS 2단계, sns-design §7.2).
 *
 * <p>login_id(공개 @핸들)로 대상 사용자를 찾아 <b>"남에게 보이는 공개 프로필"</b>을 조립한다 — 공개 책장·책별 시간·잔디를
 * 모두 <b>PUBLIC 범위로 거른다</b>. 이 페이지는 viewer를 가리지 않는다(본인이 봐도 PUBLIC만 — 공개 미리보기):
 * 가시성 게이트를 소유자 예외 없이 균일하게 적용해 로직을 단순·일관하게 둔다.
 *
 * <p>가시성 필터는 각 하위 서비스의 {@code publicXxx} 메서드가 담당하고, 순수 빌더는 viewer를 모른다(§11-7).
 */
@Service
@Transactional(readOnly = true)
public class ProfileService {

    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final BookReadingStatsService statsService;
    private final ReadingContributionService contributionService;
    private final FollowService followService;
    private final BlockRepository blockRepository;
    private final ReadingPersonalityCacheRepository personalityCacheRepository;
    private final ReadingProfileService readingProfileService;

    public ProfileService(UserRepository userRepository,
                          BookRepository bookRepository,
                          BookReadingStatsService statsService,
                          ReadingContributionService contributionService,
                          FollowService followService,
                          BlockRepository blockRepository,
                          ReadingPersonalityCacheRepository personalityCacheRepository,
                          ReadingProfileService readingProfileService) {
        this.userRepository = userRepository;
        this.bookRepository = bookRepository;
        this.statsService = statsService;
        this.contributionService = contributionService;
        this.followService = followService;
        this.blockRepository = blockRepository;
        this.personalityCacheRepository = personalityCacheRepository;
        this.readingProfileService = readingProfileService;
    }

    /**
     * login_id(공개 @핸들)로 공개 프로필을 조립한다. 없는 아이디면 빈 Optional(컨트롤러가 404로 변환 — 존재 누설 회피 §5.3).
     * 팔로우 버튼 분기를 위해 {@code viewer} 기준 following/self를 함께 계산한다(관계는 카운트만 노출).
     */
    public Optional<ProfileView> profileOf(User viewer, String loginId) {
        return resolveVisibleTarget(viewer, loginId).map(target -> {
            boolean self = target.getId() != null && target.getId().equals(viewer.getId());
            List<ProfileTag> tags = ReadingTagger.tagsOf(readingProfileService.publicProfileOf(target)).stream()
                    .map(ProfileService::toProfileTag)
                    .toList();
            return new ProfileView(
                    target.getLoginId(),
                    target.getNickname(),
                    bookRepository.findByUserAndVisibilityOrderByCreatedAtDesc(target, BookVisibility.PUBLIC),
                    statsService.publicTotalSecondsByBook(target),
                    contributionService.publicContributionGraph(target),
                    followService.followerCount(target),
                    followService.followingCount(target),
                    !self && followService.isFollowing(viewer, target),
                    self,
                    personalityNarrative(target),
                    tags);
        });
    }

    /**
     * 책방 헤더 태그(장르 종족 / 한우물형)를 클릭했을 때 보여줄 <b>근거 책</b>을 돌려준다.
     *
     * <p>입력은 {@link #profileOf}와 <b>동일한 가드 체인</b>(운영자·차단·없는 아이디)을 통과한 대상의
     * 공개 책 목록뿐. PRIVATE 책은 애초에 입력에 들어오지 않아 결과에 새지 않는다(누출 방지 — §3.4).
     * 접근 거부(가드 실패)·없는 아이디는 모두 {@code Optional.empty()}로 통합 — 컨트롤러가 404로 변환한다.
     * 알 수 없는 태그(폭 라벨·오타)는 빈 책 목록(가드 통과 → 빈 리스트 반환, 패널이 "없어요"로 그림).
     */
    public Optional<List<Book>> booksForPersonalityTag(User viewer, String loginId, String tag) {
        return resolveVisibleTarget(viewer, loginId)
                .map(target -> PersonalityTagAttribution.booksForTag(
                        bookRepository.findByUserAndVisibilityOrderByCreatedAtDesc(target, BookVisibility.PUBLIC),
                        tag));
    }

    /**
     * 소셜 가시성 가드 — 운영자·차단·없는 아이디를 한 곳에서 거른다. 프로필 조회·드릴다운이 동일 보장 공유
     * (분기 금지). 비어 있으면 컨트롤러가 404로 변환해 존재 누설을 피한다.
     */
    private Optional<User> resolveVisibleTarget(User viewer, String loginId) {
        return userRepository.findByLoginId(loginId)
                // 운영자(ADMIN)는 소셜 프로필 비대상 — 핸들을 직접 알아도 존재 누설 없이 빈 결과 → 404
                // (검색 제외와 일관: 운영자 존재 자체를 숨김. 본인이 봐도 동일하게 404 — 운영자는 /admin이 영역)
                .filter(target -> target.getRole() != Role.ADMIN)
                // 차단 관계(어느 방향이든)면 존재 누설 없이 빈 결과 → 404 (대칭, §7.5)
                .filter(target -> !blockRepository.existsBetween(viewer, target));
    }

    /** 태그 라벨 → ProfileTag. 장르 종족 라벨·{@link ReadingTagger#AUTHOR_LOYAL_TAG}만 클릭 가능. */
    private static ProfileTag toProfileTag(String label) {
        boolean clickable = ReadingTribe.ofLabel(label).isPresent()
                || ReadingTagger.AUTHOR_LOYAL_TAG.equals(label);
        return new ProfileTag(label, clickable);
    }

    /**
     * 책방에 노출할 책BTI 서술 — <b>대표(selected)</b> 분석이 있으면 그 서술, 없으면(콜드스타트=공개 완독 책 부족,
     * 또는 아직 대표 미선정) {@code null}이라 카드가 숨는다.
     *
     * <p>책BTI는 <b>공개 책으로만</b> 뽑혀 항상 노출되므로(공개/비공개 분기 폐지 2026-06-08) opt-in 게이트가 없다.
     * 사용자가 히스토리(최대 3개) 중 고른 대표만 책방에 실린다(2026-06-08 히스토리 도입). 여기선 캐시를
     * <b>읽기만</b> 한다 — 생성/갱신(LLM 호출)은 소유자 행동(본인 페이지 부트스트랩·"다시 분석")에서만 일어난다.
     * 즉 방문자가 남의 프로필을 열어도 LLM이 돌지 않는다(비용 안전장치).
     */
    private String personalityNarrative(User target) {
        return personalityCacheRepository.findByUserAndSelectedTrue(target)
                .map(c -> c.getNarrative())
                .orElse(null);
    }
}
