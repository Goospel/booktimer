package com.booktimer.web.api;

import com.booktimer.book.GeneralBookNewsFeed;
import com.booktimer.book.GoogleNewsRssClient.NewsArticle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/public/news 킬스위치 OFF — {@code booktimer.news.enabled=false}면 스냅샷에 기사가 남아 있어도 내보내지 않는다.
 */
@SpringBootTest(properties = "booktimer.news.enabled=false")
@AutoConfigureMockMvc
class PublicNewsDisabledApiControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired GeneralBookNewsFeed feed;

    @AfterEach
    void clearSnapshot() {
        feed.replaceForTest(List.of());
    }

    @Test
    @DisplayName("킬스위치가 꺼지면 newsEnabled=false, 스냅샷에 기사가 있어도 news=[]")
    void disabled_returnsFalseAndEmpty() throws Exception {
        feed.replaceForTest(List.of(new GeneralBookNewsFeed.Item("신간", new NewsArticle("남은 기사",
                "https://news.google.com/rss/articles/left", Instant.parse("2026-09-16T01:00:00Z"), "연합뉴스"))));

        mockMvc.perform(get("/api/public/news"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newsEnabled").value(false))
                .andExpect(jsonPath("$.news.length()").value(0));
    }
}
