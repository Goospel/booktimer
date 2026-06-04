package com.booktimer.popularity;

import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.search.UserRowAssembler;
import com.booktimer.search.UserSearchResult;
import com.booktimer.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * 팔로우 스코프 인기 카운트 <b>drill-down</b> 유스케이스 — "이 책 누가 원함/읽음?"(내 팔로우 한정).
 *
 * <p>카운트({@link FollowScopePopularityService})가 숫자만 보여주는 데서 한 발 더 — 같은 게이트
 * (팔로우·PUBLIC·distinct)로 <b>신원</b>까지 펼친다. 노출되는 건 각 팔로우 프로필의 PUBLIC 책장에서
 * 어차피 볼 수 있는 책뿐이라 새 노출이 없다. 모르는 사람은 안 보여 "궁금하면 팔로우" 동기를 키운다.
 */
@Service
@Transactional(readOnly = true)
public class FollowScopeReadersService {

    private static final List<BookStatus> READ_BUCKET = List.of(BookStatus.READING, BookStatus.FINISHED);

    private final BookRepository bookRepository;
    private final UserRowAssembler rowAssembler;

    public FollowScopeReadersService(BookRepository bookRepository, UserRowAssembler rowAssembler) {
        this.bookRepository = bookRepository;
        this.rowAssembler = rowAssembler;
    }

    /**
     * @param viewer 조회 주체(이 사람의 팔로우 관계가 스코프 — 본인은 자기 팔로우가 없어 자연 제외)
     * @param isbn   대상 책의 isbn13(null·공백이면 빈 명단, 쿼리 안 탐)
     */
    public FollowScopeReaders readers(User viewer, String isbn) {
        if (isbn == null || isbn.isBlank()) {
            return new FollowScopeReaders(List.of(), List.of());
        }
        return new FollowScopeReaders(
                rows(viewer, isbn, List.of(BookStatus.WANT_TO_READ)),
                rows(viewer, isbn, READ_BUCKET));
    }

    private List<UserSearchResult> rows(User viewer, String isbn, Collection<BookStatus> statuses) {
        return bookRepository.followScopeReaders(viewer, isbn, statuses).stream()
                .sorted(Comparator.comparing(User::getNickname)) // DB 무관하게 닉네임 정렬(distinct+order by 회피)
                .map(target -> rowAssembler.toRow(viewer, target))
                .toList();
    }
}
