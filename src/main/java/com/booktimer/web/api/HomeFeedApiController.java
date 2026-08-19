package com.booktimer.web.api;

import com.booktimer.book.Book;
import com.booktimer.book.BookNews;
import com.booktimer.book.BookNewsRepository;
import com.booktimer.book.BookRepository;
import com.booktimer.book.GoogleNewsRssClient;
import com.booktimer.follow.FollowRepository;
import com.booktimer.security.CurrentUserService;
import com.booktimer.session.ReadingSession;
import com.booktimer.session.ReadingSessionRepository;
import com.booktimer.story.Story;
import com.booktimer.story.StoryRepository;
import com.booktimer.user.User;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 미니앱 홈 피드 박스용 JSON API — GET /api/home-feed.
 *
 * <p>대시보드({@code /api/dashboard})에 동봉하지 않고 따로 둔다: 히어로(타이머) 렌더가 피드 쿼리에
 * 인질로 잡히지 않고, 계약이 독립이라 서버·미니앱 PR이 묶이지 않는다.
 *
 * <p>「소식」 = 팔로우한 사람의 PUBLIC 책 활동(읽기 시작·완독). 노출 게이트는 전부 쿼리에 있고
 * ({@link BookRepository#feedFinished}·{@link BookRepository#feedStarted}) 여기선 두 목록을 최신순으로
 * 합쳐 상한까지 자르기만 한다 — JPQL에 union이 없어 쿼리 2개 + 인메모리 병합이 가장 단순하고
 * 충분하다(창 14일 · 팔로우 규모 소형).
 *
 * <p>「책 뉴스」 = 내가 완독한 책(isbn13)의 기사. 실제 수집은 새벽 배치({@code BookNewsCollector})가 하고
 * 여기선 캐시를 읽기만 한다 — 요청 경로에서 외부 API를 부르지 않는다. 노출 게이트는 <b>서버의
 * {@code newsEnabled}</b>(킬스위치 {@code booktimer.news.enabled}, 기본 켜짐): 끄면 false + 빈 목록이라
 * 미니앱이 탭 자체를 안 그린다(죽은 탭 금지) — 구글 RSS 형식이 바뀌어도 미니앱 재배포 없이 끌 수 있다.
 * SecurityConfig default-deny로 자동 인증.
 */
@RestController
public class HomeFeedApiController {

    /** 피드 창 — 7일이면 소셜 그래프가 작은 초기 사용자에게 빈 피드가 너무 잦다. */
    private static final Duration WINDOW = Duration.ofDays(14);

    /** 응답 상한. 미니앱은 미리보기 3줄 + 「더 보기」로 이 안에서 펼친다(페이지네이션 없음). */
    private static final int MAX_EVENTS = 30;

    /** 뉴스 응답 상한. 소식과 같은 이유(미리보기 + 「더 보기」로 이 안에서 펼친다). */
    private static final int MAX_NEWS = 30;

    /** 여백 글 미리보기 길이 — 말줄임(…)을 포함해 이 길이를 넘지 않는다. */
    private static final int EXCERPT_MAX_LENGTH = 80;

    /** 「함께 읽는 사람」 상한. 소식·뉴스와 같은 값 — 미리보기 + 「더 보기」로 이 안에서 펼친다. */
    private static final int MAX_READERS = 30;

    private final CurrentUserService currentUserService;
    private final BookRepository bookRepository;
    private final BookNewsRepository bookNewsRepository;
    private final StoryRepository storyRepository;
    private final FollowRepository followRepository;
    private final ReadingSessionRepository sessionRepository;
    private final GoogleNewsRssClient newsClient;
    private final Clock clock;

    public HomeFeedApiController(CurrentUserService currentUserService,
                                 BookRepository bookRepository,
                                 BookNewsRepository bookNewsRepository,
                                 StoryRepository storyRepository,
                                 FollowRepository followRepository,
                                 ReadingSessionRepository sessionRepository,
                                 GoogleNewsRssClient newsClient,
                                 Clock clock) {
        this.currentUserService = currentUserService;
        this.bookRepository = bookRepository;
        this.bookNewsRepository = bookNewsRepository;
        this.storyRepository = storyRepository;
        this.followRepository = followRepository;
        this.sessionRepository = sessionRepository;
        this.newsClient = newsClient;
        this.clock = clock;
    }

    @GetMapping("/api/home-feed")
    public HomeFeedResponse homeFeed(Principal principal) {
        User viewer = currentUserService.resolve(principal);
        Instant cutoff = clock.instant().minus(WINDOW);

        List<SocialEvent> events = new ArrayList<>();
        for (Book book : bookRepository.feedFinished(viewer, cutoff)) {
            // 첫 완독 시각 — finishedAt은 토글마다 재스탬프돼 옛 책을 "방금"처럼 재부상시킨다.
            events.add(event(book, "FINISHED", book.getFirstFinishedAt()));
        }
        for (Book book : bookRepository.feedStarted(viewer, cutoff)) {
            events.add(event(book, "STARTED", book.getStartedReadingAt()));
        }
        events.addAll(marginEvents(viewer, cutoff));
        events.sort(Comparator.comparing(SocialEvent::occurredAt).reversed());

        return new HomeFeedResponse(
                events.size() > MAX_EVENTS ? List.copyOf(events.subList(0, MAX_EVENTS)) : events,
                newsClient.isEnabled(), newsFor(viewer), readersFor(viewer));
    }

    /**
     * 「함께 읽는 사람」 — 내가 팔로우한 사람들의 <b>공개 책</b> 독서 상태.
     *
     * <p><b>이 목록의 불변식은 한 줄이다: 공개 책의 독서만 본다.</b> 그래서 새로 공개되는 것이 없고,
     * 「내 상태를 누구에게 보일까」를 새로 물을 필요도 없다 — 책마다 이미 있는 공개 스위치가 그 설정이다.
     * 비공개 독서는 「읽는 중」이라는 표시조차 남기지 않는다(그것만으로 지금 뭘 하는지가 샌다).
     *
     * <p>쿼리 3개 + 모집단 1개로 고정이다(팔로잉 수와 무관 — N+1 없음). 팔로잉이 0명이면
     * 나머지 쿼리를 아예 타지 않는다.
     */
    private List<ReaderStatus> readersFor(User viewer) {
        List<User> followees = followRepository.findVisibleFollowees(viewer, PageRequest.of(0, MAX_READERS));
        if (followees.isEmpty()) {
            return List.of();
        }
        List<Long> ids = followees.stream().map(User::getId).toList();

        Set<Long> mutualIds = Set.copyOf(followRepository.findFollowerIdsAmong(viewer, ids));
        Map<Long, ReadingSession> activeByUser = new HashMap<>();
        for (ReadingSession session : sessionRepository.findActivePublicSessions(ids)) {
            // 진행 중 세션은 사람당 하나가 불변식이나(서비스가 중복 start를 막는다) 방어적으로 최신을 남긴다.
            activeByUser.merge(session.getUser().getId(), session,
                    (a, b) -> a.getStartedAt().isAfter(b.getStartedAt()) ? a : b);
        }
        Map<Long, Instant> lastReadByUser = new HashMap<>();
        for (ReadingSessionRepository.UserReadRecency row : sessionRepository.lastPublicReadAtByUser(ids)) {
            lastReadByUser.put(row.getUserId(), row.getLastAt());
        }

        List<ReaderStatus> readers = new ArrayList<>();
        for (User followee : followees) {
            ReadingSession active = activeByUser.get(followee.getId());
            readers.add(new ReaderStatus(
                    followee.getLoginId(), followee.getNickname(), mutualIds.contains(followee.getId()),
                    active == null ? null : active.getBook().getTitle(),
                    active == null ? null : active.getStartedAt(),
                    lastReadByUser.get(followee.getId())));
        }
        readers.sort(READER_ORDER);
        return List.copyOf(readers);
    }

    /**
     * 읽는 중 → 맞팔 → 최근 활동순 → 기록 없음. 동률은 loginId로 결정적 정렬.
     *
     * <p>비교자 하나로 끝내는 이유: 화면은 「읽는 중」과 「최근 기록」 두 묶음으로 보이지만 규칙을 둘로
     * 나누면 두 곳이 따로 늙는다. 묶음 머리는 미니앱이 이 순서 위에 그린다.
     */
    private static final Comparator<ReaderStatus> READER_ORDER =
            Comparator.comparing((ReaderStatus r) -> r.readingSince() != null).reversed()
                    .thenComparing(Comparator.comparing(ReaderStatus::mutual).reversed())
                    .thenComparing(HomeFeedApiController::lastActivityOf,
                            Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(ReaderStatus::loginId);

    /** 정렬 기준이 되는 마지막 활동 — 읽는 중이면 그 시작 시각, 아니면 마지막 공개 기록. */
    private static Instant lastActivityOf(ReaderStatus reader) {
        return reader.readingSince() != null ? reader.readingSince() : reader.lastReadAt();
    }

    /**
     * 내 완독 책(isbn13)의 캐시된 기사 — 발행 최신순, 상한 {@value #MAX_NEWS}건.
     *
     * <p>게이트가 꺼져 있거나 완독 책이 없으면 쿼리 없이 빈 목록. isbn → 내 책 제목 맵이
     * {@code bookTitle} 라벨("내 어느 책의 기사인가")을 만든다 — 같은 isbn을 여러 번 완독 등록했으면
     * 먼저 만난 제목 하나를 쓴다(라벨일 뿐이라 어느 쪽이든 같은 책이다).
     */
    private List<NewsItem> newsFor(User viewer) {
        if (!newsClient.isEnabled()) {
            return List.of();
        }
        Map<String, String> titleByIsbn = new HashMap<>();
        for (Book book : bookRepository.findFinishedWithIsbn(viewer)) {
            titleByIsbn.putIfAbsent(book.getIsbn13(), book.getTitle());
        }
        if (titleByIsbn.isEmpty()) {
            return List.of();
        }
        List<BookNews> cached = bookNewsRepository.findByIsbn13InOrderByPublishedAtDesc(
                titleByIsbn.keySet(), PageRequest.of(0, MAX_NEWS));
        return cached.stream()
                .map(n -> new NewsItem(n.getTitle(), n.getLink(), n.getPublishedAt(),
                        titleByIsbn.get(n.getIsbn13()), n.getSource()))
                .toList();
    }

    /**
     * 「○○님이 『책』의 여백에 글을 남겼어요」 이벤트 — <b>사람+책 단위로 묶어</b> 한 줄로 만든다.
     *
     * <p>묶는 자리가 서버인 이유: ① 상한 30건을 서버가 자르는데 묶기 <i>전</i> 개수로 자르면 한 사람의
     * 연속 작성이 완독·시작 소식을 부당하게 밀어낸다(묶는 이유 자체가 그 밀림 방지다) ② SQL로 묶으려면
     * 「그룹별 최신 text」를 뽑을 윈도 함수가 필요한데 JPQL엔 없어 억지 서브쿼리가 된다.
     *
     * <p>스캔이 최신순이라 각 그룹에서 <b>먼저 만난 글이 그 그룹의 최신</b>이다 — 시각·발췌는 거기서 온다.
     */
    private List<SocialEvent> marginEvents(User viewer, Instant cutoff) {
        Map<MarginKey, List<Story>> byBook = new LinkedHashMap<>();
        for (Story story : storyRepository.feedRecent(viewer, cutoff)) {
            byBook.computeIfAbsent(
                    new MarginKey(story.getUser().getId(), story.getBook().getId()),
                    key -> new ArrayList<>()).add(story);
        }
        List<SocialEvent> events = new ArrayList<>();
        for (List<Story> group : byBook.values()) {
            Story newest = group.get(0); // 스캔이 최신순
            events.add(new SocialEvent(newest.getUser().getLoginId(), newest.getUser().getNickname(),
                    newest.getBook().getTitle(), "STORY", newest.getCreatedAt(),
                    newest.getBook().getId(), excerptOf(newest.getText()), group.size()));
        }
        return events;
    }

    /** 묶기 단위 — 같은 사람이라도 책이 다르면 별개 행이다(책이 곧 여백이므로). */
    private record MarginKey(Long userId, Long bookId) {
    }

    /**
     * 여백 글의 미리보기 1줄 — {@value #EXCERPT_MAX_LENGTH}자를 넘으면 잘라 말줄임을 붙인다.
     *
     * <p>서버에 두는 이유는 <b>페이로드 절단</b>이다: 글은 최대 500자인데 소식이 30건이면 15KB가
     * 미리보기 한 줄 때문에 오간다. 폭에 맞춘 시각적 말줄임은 CSS clamp가 따로 맡는다 —
     * 화면 폭은 서버가 알 수 없다.
     */
    static String excerptOf(String text) {
        if (text == null || text.length() <= EXCERPT_MAX_LENGTH) {
            return text;
        }
        return text.substring(0, EXCERPT_MAX_LENGTH - 1) + "…"; // 말줄임 포함해 상한을 넘지 않게
    }

    private static SocialEvent event(Book book, String type, Instant occurredAt) {
        return new SocialEvent(book.getUser().getLoginId(), book.getUser().getNickname(),
                book.getTitle(), type, occurredAt, null, null, 0); // 여백 전용 필드는 STORY 행만 채운다
    }

    public record HomeFeedResponse(List<SocialEvent> social, boolean newsEnabled, List<NewsItem> news,
                                   List<ReaderStatus> readers) {
    }

    /**
     * 「함께 읽는 사람」 한 줄 — 팔로우한 사람의 <b>공개 책</b> 독서 상태.
     *
     * @param mutual           서로 팔로우 중인가 — 「서로」 배지 + 정렬 우선. 관계 모델은 단방향 그대로다
     * @param readingBookTitle 지금 읽는 중인 PUBLIC 책 제목. <b>비공개 책·미태깅 세션·미독서면 null</b>
     * @param readingSince     그 세션의 시작 시각. {@code readingBookTitle}과 항상 함께 채워지거나 함께 null
     * @param lastReadAt       끝낸 PUBLIC 책 세션 중 가장 최근의 시작 시각.
     *                         공개 기록이 없으면 null → 미니앱이 「공개된 기록이 없어요」로 그린다
     *                         (「안 읽었어요」가 아니다 — 비공개로 읽는 사람에겐 그게 거짓이다)
     */
    public record ReaderStatus(String loginId, String nickname, boolean mutual,
                               String readingBookTitle, Instant readingSince, Instant lastReadAt) {
    }

    /**
     * @param type       "STARTED" | "FINISHED" | "STORY" — 미니앱이 문구로 옮긴다
     *                   ("읽기 시작했어요"/"완독했어요"/"『책』의 여백에 글을 남겼어요")
     * @param bookId     STORY 행만 채운다 — 탭하면 그 책의 여백으로 점프한다.
     *                   STARTED·FINISHED 행은 갈 곳이 없어 비클릭이라 {@code null}(죽은 링크 금지)
     * @param excerpt    STORY 행만 채운다 — 그 묶음 <b>최신</b> 글의 발췌 1줄. 아니면 {@code null}
     * @param count      그 묶음의 글 수(1이면 "글을 남겼어요", 2 이상이면 "글 N개를 남겼어요").
     *                   STARTED·FINISHED 행은 0
     */
    public record SocialEvent(String loginId, String nickname, String bookTitle,
                              String type, Instant occurredAt,
                              Long bookId, String excerpt, int count) {
    }

    /**
     * @param bookTitle 내 어느 책의 기사인지 보여주는 라벨
     * @param source    매체명(구글 뉴스 {@code <source>}). 응답에 없었으면 null — 미니앱이 라벨을 생략한다
     */
    public record NewsItem(String title, String link, Instant publishedAt, String bookTitle,
                           String source) {
    }
}
