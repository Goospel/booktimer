package com.booktimer.profile;

import com.booktimer.block.BlockRepository;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookVisibility;
import com.booktimer.follow.FollowService;
import com.booktimer.session.BookReadingStatsService;
import com.booktimer.session.ReadingContributionService;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 공개 프로필 조회 유스케이스 (SNS 2단계, sns-design §7.2).
 *
 * <p>닉네임으로 대상 사용자를 찾아 <b>"남에게 보이는 공개 프로필"</b>을 조립한다 — 공개 책장·책별 시간·잔디를
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

    public ProfileService(UserRepository userRepository,
                          BookRepository bookRepository,
                          BookReadingStatsService statsService,
                          ReadingContributionService contributionService,
                          FollowService followService,
                          BlockRepository blockRepository) {
        this.userRepository = userRepository;
        this.bookRepository = bookRepository;
        this.statsService = statsService;
        this.contributionService = contributionService;
        this.followService = followService;
        this.blockRepository = blockRepository;
    }

    /**
     * 닉네임으로 공개 프로필을 조립한다. 없는 닉네임이면 빈 Optional(컨트롤러가 404로 변환 — 존재 누설 회피 §5.3).
     * 팔로우 버튼 분기를 위해 {@code viewer} 기준 following/self를 함께 계산한다(관계는 카운트만 노출).
     */
    public Optional<ProfileView> profileOf(User viewer, String nickname) {
        return userRepository.findByNickname(nickname)
                // 차단 관계(어느 방향이든)면 존재 누설 없이 빈 결과 → 404 (대칭, §7.5)
                .filter(target -> !blockRepository.existsBetween(viewer, target))
                .map(target -> {
            boolean self = target.getId() != null && target.getId().equals(viewer.getId());
            return new ProfileView(
                    target.getNickname(),
                    bookRepository.findByUserAndVisibilityOrderByCreatedAtDesc(target, BookVisibility.PUBLIC),
                    statsService.publicTotalSecondsByBook(target),
                    contributionService.publicContributionGraph(target),
                    followService.followerCount(target),
                    followService.followingCount(target),
                    !self && followService.isFollowing(viewer, target),
                    self);
        });
    }
}
