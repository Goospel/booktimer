package com.booktimer.search;

import com.booktimer.block.BlockRepository;
import com.booktimer.book.BookRepository;
import com.booktimer.book.CoReadCount;
import com.booktimer.follow.FollowRepository;
import com.booktimer.follow.FollowService;
import com.booktimer.follow.FriendOfFriendCount;
import com.booktimer.session.ActiveDayCount;
import com.booktimer.session.ReadingSessionRepository;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 사용자 검색 유스케이스 — <b>login_id(공개 @핸들) 기준</b> (sns-design §7.3, login-id-design §7 PR-3).
 *
 * <p>부분일치(LIKE)로 <b>login_id</b>를 찾아(닉네임이 아니라 아이디로 검색 — 인스타/X 모델) 결과 한 줄마다
 * 공개 책 수·내 팔로우 여부·본인 여부를 채운다.
 * 가드: <b>최소 2글자</b>(미만이면 빈 결과), <b>상한 20</b>(리포지토리 Top20) — 열거·크롤링 완화(§9).
 * 검색은 사용자 <i>존재</i>만 노출하고 독서 <i>내용</i>은 노출하지 않는다(공개 책 게이트는 프로필이 담당).
 *
 * <p><b>차단 숨김</b>: 나와 차단 관계(어느 방향이든)인 사용자는 결과에서 제외한다 — 차단하면 프로필이
 * 대칭으로 404이므로 검색 결과에 떠도 열 수 없어, 애초에 노출하지 않는다(sns-design §7.5 후속).
 */
@Service
@Transactional(readOnly = true)
public class UserSearchService {

    /** 이 길이 미만이면 검색하지 않는다(한 글자 광역 매칭으로 인한 열거 방지). */
    static final int MIN_QUERY_LENGTH = 2;

    private final UserRepository userRepository;
    private final UserRowAssembler rowAssembler;
    private final BlockRepository blockRepository;
    private final FollowRepository followRepository;
    private final FollowService followService;
    private final BookRepository bookRepository;
    private final ReadingSessionRepository readingSessionRepository;
    private final Clock clock;

    public UserSearchService(UserRepository userRepository, UserRowAssembler rowAssembler,
                             BlockRepository blockRepository, FollowRepository followRepository,
                             FollowService followService, BookRepository bookRepository,
                             ReadingSessionRepository readingSessionRepository, Clock clock) {
        this.userRepository = userRepository;
        this.rowAssembler = rowAssembler;
        this.blockRepository = blockRepository;
        this.followRepository = followRepository;
        this.followService = followService;
        this.bookRepository = bookRepository;
        this.readingSessionRepository = readingSessionRepository;
        this.clock = clock;
    }

    public List<UserSearchResult> search(User viewer, String query) {
        if (query == null) {
            return List.of();
        }
        String q = query.trim();
        if (q.length() < MIN_QUERY_LENGTH) {
            return List.of();
        }
        List<User> targets = userRepository.findTop20ByLoginIdContainingIgnoreCaseOrderByLoginIdAsc(q).stream()
                .filter(u -> u.getRole() != Role.ADMIN) // 운영자는 일반 사용자에게 노출하지 않음
                .filter(u -> !blockRepository.existsBetween(viewer, u)) // 차단 관계는 숨김(대칭, bounded Top20 — 범위 밖)
                .toList();
        return rowAssembler.toRows(viewer, targets);
    }

    /**
     * 친구 추천 — 계단식 하이브리드(친구 추천 1단계). 우선순위 생성기 사다리:
     * G1 맞팔 후보 → G2 친구의 친구(공통 친구 수) → C 같은 책(겹친 권수)
     * → E 활동량(최근 14일 distinct 활동일 수) → F 랜덤 폴백. userId로 dedup하며
     * 중복 시 이유 칩을 합치고(예: 같은 책이자 활동량), {@code limit}을 채울 때까지 우선순위 순으로 모은다.
     * 모든 생성기는 노출 불변식(운영자·본인·차단 양방향·login_id null 제외) + 이미 팔로우한 사람 제외를 지킨다.
     */
    public List<RecommendedUser> recommend(User viewer, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        Long viewerId = viewer.getId();
        Pageable cap = PageRequest.of(0, limit);

        // 우선순위 생성기 사다리: 후보 id(삽입 순서 = 우선순위) → 이유 칩. dedup하며 이유는 합집합.
        LinkedHashMap<Long, List<String>> reasonsById = new LinkedHashMap<>();

        // G1 맞팔 후보 — "나를 팔로우함"
        for (Long id : followRepository.findFollowBackCandidateIds(viewerId, cap)) {
            addReason(reasonsById, id, "나를 팔로우함", limit);
        }
        // G2 친구의 친구 — "공통 친구 N명"
        for (FriendOfFriendCount fof : followRepository.findFriendsOfFriends(viewerId, cap)) {
            addReason(reasonsById, fof.getUserId(), "공통 친구 " + fof.getCommonFollowCount() + "명", limit);
        }
        // C 같은 책 — "같이 읽은 책 N권" (내가 읽은 책이 없으면 스킵)
        List<String> myIsbns = bookRepository.findReadIsbnsByUser(viewerId);
        if (!myIsbns.isEmpty()) {
            for (CoReadCount co : bookRepository.findCoReadCandidates(viewerId, myIsbns, cap)) {
                addReason(reasonsById, co.getUserId(), "같이 읽은 책 " + co.getSharedBookCount() + "권", limit);
            }
        }
        // E 활동량 폴백 — "요즘 꾸준히 읽어요"(최근 14일 distinct 활동일 수 기준; 신호로 limit을 못 채웠을 때만)
        if (reasonsById.size() < limit) {
            java.time.Instant since = clock.instant().minus(14, ChronoUnit.DAYS);
            for (ActiveDayCount ac : readingSessionRepository.findActiveReaderCandidates(viewerId, since, cap)) {
                addReason(reasonsById, ac.getUserId(), "요즘 꾸준히 읽어요", limit);
            }
        }
        // F 폴백(랜덤) — 신호로 limit을 못 채웠을 때만, 이미 모은/팔로우한 사람 제외
        if (reasonsById.size() < limit) {
            fillWithFallback(viewer, viewerId, reasonsById, limit);
        }

        // 행 조립 — 후보 순서(우선순위)를 유지하며 공개책수·팔로우여부·본인여부 배치 조회
        List<Long> orderedIds = new ArrayList<>(reasonsById.keySet());
        Map<Long, User> byId = userRepository.findAllById(orderedIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        List<User> targets = orderedIds.stream().map(byId::get).filter(Objects::nonNull).toList();
        List<UserSearchResult> rows = rowAssembler.toRows(viewer, targets);

        List<RecommendedUser> result = new ArrayList<>(rows.size());
        for (int i = 0; i < targets.size(); i++) {
            List<String> reasons = reasonsById.getOrDefault(targets.get(i).getId(), List.of());
            result.add(new RecommendedUser(rows.get(i), reasons));
        }
        return result;
    }

    /** 후보를 우선순위 순으로 모은다 — 기존 후보엔 이유만 추가(상한 무관), 새 후보는 limit까지만. */
    private void addReason(Map<Long, List<String>> reasonsById, Long id, String reason, int limit) {
        List<String> existing = reasonsById.get(id);
        if (existing != null) {
            existing.add(reason);
            return;
        }
        if (reasonsById.size() >= limit) {
            return;
        }
        List<String> reasons = new ArrayList<>(2);
        reasons.add(reason);
        reasonsById.put(id, reasons);
    }

    /** 신호 후보가 limit에 못 미치면 랜덤 폴백으로 채운다 — 이미 모은/내가 팔로우한 사람은 제외(이유 칩 없음). */
    private void fillWithFallback(User viewer, Long viewerId,
                                  LinkedHashMap<Long, List<String>> reasonsById, int limit) {
        int collected = reasonsById.size();
        // 폴백 풀이 이미 모은 후보와 겹칠 수 있어 넉넉히 받아 채운다.
        List<User> randPool = userRepository.findRecommendCandidates(
                viewerId, PageRequest.of(0, limit + collected));
        List<Long> poolIds = randPool.stream().map(User::getId).toList();
        Set<Long> followed = followService.followingAmong(viewer, poolIds); // 폴백도 이미 팔로우한 사람 제외
        for (User u : randPool) {
            if (reasonsById.size() >= limit) {
                break;
            }
            Long id = u.getId();
            if (reasonsById.containsKey(id) || followed.contains(id)) {
                continue;
            }
            reasonsById.put(id, List.of()); // 폴백 후보 = 이유 칩 없음
        }
    }
}
