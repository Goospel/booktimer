package com.booktimer.profile;

import com.booktimer.block.BlockRepository;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookVisibility;
import com.booktimer.follow.FollowService;
import com.booktimer.personality.PublicReadingPersonalityCacheRepository;
import com.booktimer.session.BookReadingStatsService;
import com.booktimer.session.ReadingContributionService;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final PublicReadingPersonalityCacheRepository publicPersonalityCacheRepository;

    public ProfileService(UserRepository userRepository,
                          BookRepository bookRepository,
                          BookReadingStatsService statsService,
                          ReadingContributionService contributionService,
                          FollowService followService,
                          BlockRepository blockRepository,
                          PublicReadingPersonalityCacheRepository publicPersonalityCacheRepository) {
        this.userRepository = userRepository;
        this.bookRepository = bookRepository;
        this.statsService = statsService;
        this.contributionService = contributionService;
        this.followService = followService;
        this.blockRepository = blockRepository;
        this.publicPersonalityCacheRepository = publicPersonalityCacheRepository;
    }

    /**
     * login_id(공개 @핸들)로 공개 프로필을 조립한다. 없는 아이디면 빈 Optional(컨트롤러가 404로 변환 — 존재 누설 회피 §5.3).
     * 팔로우 버튼 분기를 위해 {@code viewer} 기준 following/self를 함께 계산한다(관계는 카운트만 노출).
     */
    public Optional<ProfileView> profileOf(User viewer, String loginId) {
        return userRepository.findByLoginId(loginId)
                // 운영자(ADMIN)는 소셜 프로필 비대상 — 핸들을 직접 알아도 존재 누설 없이 빈 결과 → 404
                // (검색 제외와 일관: 운영자 존재 자체를 숨김. 본인이 봐도 동일하게 404 — 운영자는 /admin이 영역)
                .filter(target -> target.getRole() != Role.ADMIN)
                // 차단 관계(어느 방향이든)면 존재 누설 없이 빈 결과 → 404 (대칭, §7.5)
                .filter(target -> !blockRepository.existsBetween(viewer, target))
                .map(target -> {
            boolean self = target.getId() != null && target.getId().equals(viewer.getId());
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
                    publicPersonality(target));
        });
    }

    /**
     * 책방에 노출할 공개 책BTI 서술 — 대상이 공개(opt-in)했고 공개 캐시가 있을 때만, 아니면 {@code null}.
     *
     * <p>여기선 캐시를 <b>읽기만</b> 한다 — 생성/갱신(LLM 호출)은 소유자 행동에서만 일어난다. 즉 방문자가 남의
     * 프로필을 열어도 LLM이 돌지 않는다(비용 안전장치). 공개 안 한 사용자는 캐시가 있어도 노출하지 않는다(opt-in 게이트).
     */
    private String publicPersonality(User target) {
        if (!target.isPersonalityPublic()) {
            return null;
        }
        return publicPersonalityCacheRepository.findByUser(target)
                .map(c -> c.getNarrative())
                .orElse(null);
    }
}
