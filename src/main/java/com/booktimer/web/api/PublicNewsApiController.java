package com.booktimer.web.api;

import com.booktimer.book.GeneralBookNewsFeed;
import com.booktimer.book.GoogleNewsRssClient;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 게스트(로그인 전) 「책 뉴스」 — 토큰 없이 읽는 공개 경로({@code /api/public/**} = 미니앱 체인의 익명 공개 계약).
 *
 * <p><b>의존은 피드·RSS 클라이언트 둘뿐이다 — 리포지토리가 없다는 것이 계약이다.</b> 사용자 완독 책으로 모은 캐시를
 * 열었던 1차안은 계정 하나로 게스트 화면의 문구·기사 주제를 조종할 수 있었다(리뷰 차단 B-1). 응답의 {@code bookTitle}은
 * 책 제목이 아니라 운영자 고정 주제 라벨이다(미니앱 계약 {@code NewsItem} 유지). 요청당 비용이 메모리 목록 직렬화뿐이라
 * 레이트리밋 대신 10분 캐시만 둔다.
 */
@RestController
public class PublicNewsApiController {

    private final GeneralBookNewsFeed feed;
    private final GoogleNewsRssClient newsClient;

    public PublicNewsApiController(GeneralBookNewsFeed feed, GoogleNewsRssClient newsClient) {
        this.feed = feed;
        this.newsClient = newsClient;
    }

    @GetMapping("/api/public/news")
    public ResponseEntity<PublicNewsResponse> latest() {
        if (!newsClient.isEnabled()) {
            return ResponseEntity.ok(new PublicNewsResponse(false, List.of()));
        }
        List<HomeFeedApiController.NewsItem> news = feed.snapshot().stream()
                .map(i -> new HomeFeedApiController.NewsItem(i.article().title(), i.article().link(),
                        i.article().publishedAt(), i.topic(), i.article().source()))
                .toList();
        // 비었으면(재배포 직후·수집 실패) 캐시 금지 — 공개 캐시하면 수집이 끝난 뒤에도 게스트가 최대 10분 잠금을 본다.
        CacheControl cache = news.isEmpty()
                ? CacheControl.noStore()
                : CacheControl.maxAge(10, TimeUnit.MINUTES).cachePublic();
        return ResponseEntity.ok().cacheControl(cache).body(new PublicNewsResponse(true, news));
    }

    public record PublicNewsResponse(boolean newsEnabled, List<HomeFeedApiController.NewsItem> news) {
    }
}
