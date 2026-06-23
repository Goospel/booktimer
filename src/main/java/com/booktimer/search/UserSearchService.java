package com.booktimer.search;

import com.booktimer.block.BlockRepository;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

    public UserSearchService(UserRepository userRepository, UserRowAssembler rowAssembler,
                             BlockRepository blockRepository) {
        this.userRepository = userRepository;
        this.rowAssembler = rowAssembler;
        this.blockRepository = blockRepository;
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
     * 친구 추천 — 무작위 {@code limit}명(요구사항: 우선 단순하게).
     *
     * <p>검색과 같은 노출 불변식을 DB 쿼리로 처리한다: 운영자 제외·본인 제외·차단 관계(대칭) 제외 +
     * 공개 핸들(login_id) 미설정 제외. 필터·랜덤·상한을 모두 DB에서 처리해 findAll() 전체 로드와
     * 후보당 existsBetween N+1을 제거한다(PR-C).
     * 사용자가 많아지면 무작위 방식을 독서 성향 기반 추천 등으로 교체할 자리다(후속 과제).
     */
    public List<UserSearchResult> recommend(User viewer, int limit) {
        List<User> candidates = userRepository.findRecommendCandidates(viewer.getId(), PageRequest.of(0, limit));
        return rowAssembler.toRows(viewer, candidates);
    }
}
