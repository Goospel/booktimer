package com.booktimer.story;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.security.RateLimitAction;
import com.booktimer.security.RateLimitService;
import com.booktimer.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 여백 유스케이스 (sns-design §13) — 글 작성·본인 삭제.
 *
 * <p><b>여백은 책에 딸린 자리다</b>(2026-08-16 재설계). 사람 단위 스트립·전체화면 뷰어·열람 기록은
 * 전부 폐기됐고(V71이 story_view를 드롭), 글은 「책방 격자 → 책 → 그 책의 글 목록」으로만 도달한다.
 * 그래서 {@code bookId}가 선택이 아니라 필수다 — 책 없는 글은 아무 데도 실리지 않는다.
 *
 * <p>게이트 실패를 API 상태코드로 직접 표현해야 해서(레이트리밋 429·미노출 404) 이 서비스는
 * 예외적으로 {@link ResponseStatusException}을 던진다 — 프론트는 상태코드로 분기해 안내한다.
 */
@Service
@Transactional
public class StoryService {

    private final StoryRepository storyRepository;
    private final BookRepository bookRepository;
    private final RateLimitService rateLimitService;

    public StoryService(StoryRepository storyRepository,
                        BookRepository bookRepository,
                        RateLimitService rateLimitService) {
        this.storyRepository = storyRepository;
        this.bookRepository = bookRepository;
        this.rateLimitService = rateLimitService;
    }

    /**
     * 여백에 글을 남긴다. 게이트 순서: 레이트리밋(429 — FOLLOW의 무음 드롭과 달리 안내한다: 작성은
     * 콘텐츠 소실이라 사용자가 원인을 알아야 한다, §13.5) → 책 검증(없는 bookId는 400, 남의 책·미존재는
     * 404로 존재 누설 방지, 내 책인데 비공개면 400 — 공개 책만 여백을 갖는다, §13.2).
     *
     * <p>개수 상한은 없다 — 도배 방어는 레이트리밋(시간당 10)이 맡고, 목록 폭주는 읽기 쪽 상한이 맡는다.
     */
    public Story create(User author, String text, Long bookId, String bgCode) {
        if (!rateLimitService.allow(RateLimitAction.STORY_CREATE, author.getId())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "글을 너무 자주 남겼습니다");
        }
        if (bookId == null) {
            // 여백은 책에 귀속 — 진입점이 이미 책이므로 정상 클라는 여기 오지 않는다(직접 호출 방어)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "책을 지정해야 합니다");
        }
        Book book = bookRepository.findByIdAndUser(bookId, author)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "책을 찾을 수 없습니다"));
        if (!book.isPublic()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "공개 책에만 글을 남길 수 있습니다");
        }
        return storyRepository.save(Story.of(author, text, book, bgCode));
    }

    /** 본인 글 즉시 삭제(실수 게시 회수 — §13.6). 없거나 타인 것이면 404(IDOR — 존재 비노출). */
    public void delete(User actor, Long storyId) {
        Story story = storyRepository.findById(storyId)
                .filter(s -> isSameUser(s.getUser(), actor))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "글을 찾을 수 없습니다"));
        storyRepository.delete(story);
    }

    private static boolean isSameUser(User a, User b) {
        if (a == b) {
            return true;
        }
        return a.getId() != null && a.getId().equals(b.getId());
    }
}
