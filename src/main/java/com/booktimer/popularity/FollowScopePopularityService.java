package com.booktimer.popularity;

import com.booktimer.book.BookRepository;
import com.booktimer.book.FollowScopeCount;
import com.booktimer.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 팔로우 스코프 인기 카운트 유스케이스 (sns-design §7.4, 요구사항 3).
 *
 * <p>책장·검색 결과의 isbn 목록을 받아 "내 팔로우 중 N명 원함 · M명 읽음"을 <b>한 번의 집계</b>로 채운다
 * (N+1 회피). 임계 마스킹은 두지 않는다(사용자 확정 2026-06-04 — drill-down이 없어 재식별 위험 제한적).
 */
@Service
@Transactional(readOnly = true)
public class FollowScopePopularityService {

    private final BookRepository bookRepository;

    public FollowScopePopularityService(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    /**
     * @param viewer 조회 주체(이 사람의 팔로우 관계가 집계 스코프)
     * @param isbns  화면에 보이는 책들의 isbn13(중복·공백·null은 무시)
     * @return isbn13 → 인기 카운트. 데이터 없는 isbn은 키가 없다(호출부에서 미표시로 처리).
     */
    public Map<String, FollowScopePopularity> countByIsbn(User viewer, Collection<String> isbns) {
        if (isbns == null) {
            return Map.of();
        }
        List<String> keys = isbns.stream()
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .toList();
        if (keys.isEmpty()) {
            return Map.of();
        }
        return bookRepository.followScopePopularity(viewer, keys).stream()
                .collect(Collectors.toMap(
                        FollowScopeCount::getIsbn,
                        c -> new FollowScopePopularity(c.getWantCount(), c.getReadCount())));
    }
}
