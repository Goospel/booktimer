package com.booktimer.search;

import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 닉네임 검색 유스케이스 (sns-design §7.3, 요구사항 6).
 *
 * <p>부분일치(LIKE)로 닉네임을 찾아 결과 한 줄마다 공개 책 수·내 팔로우 여부·본인 여부를 채운다.
 * 가드: <b>최소 2글자</b>(미만이면 빈 결과), <b>상한 20</b>(리포지토리 Top20) — 열거·크롤링 완화(§9).
 * 검색은 사용자 <i>존재</i>만 노출하고 독서 <i>내용</i>은 노출하지 않는다(공개 책 게이트는 프로필이 담당).
 */
@Service
@Transactional(readOnly = true)
public class UserSearchService {

    /** 이 길이 미만이면 검색하지 않는다(한 글자 광역 매칭으로 인한 열거 방지). */
    static final int MIN_QUERY_LENGTH = 2;

    private final UserRepository userRepository;
    private final UserRowAssembler rowAssembler;

    public UserSearchService(UserRepository userRepository, UserRowAssembler rowAssembler) {
        this.userRepository = userRepository;
        this.rowAssembler = rowAssembler;
    }

    public List<UserSearchResult> search(User viewer, String query) {
        if (query == null) {
            return List.of();
        }
        String q = query.trim();
        if (q.length() < MIN_QUERY_LENGTH) {
            return List.of();
        }
        return userRepository.findTop20ByNicknameContainingIgnoreCaseOrderByNicknameAsc(q).stream()
                .map(u -> rowAssembler.toRow(viewer, u))
                .toList();
    }
}
