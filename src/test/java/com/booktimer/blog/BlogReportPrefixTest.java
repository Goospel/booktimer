package com.booktimer.blog;

import com.booktimer.blog.BlogReportService.Prefix;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 신고 detail 접두 문법(계획 §4.4 — 순수): {@code sid:XXXXXXXX} · {@code quote:N} 첫 줄 · 그 뒤 사용자 글. */
class BlogReportPrefixTest {

    @Test
    @DisplayName("REQ-252 · detail 접두 — sid: · quote: 첫 줄 · 줄바꿈 뒤 사용자 글 · 접두 없으면 빈 값")
    void parses() {
        assertThat(BlogReportService.prefix("sid:Ab3dE5gH")).contains(new Prefix("Ab3dE5gH", null));
        assertThat(BlogReportService.prefix("sid:Ab3dE5gH\n욕설이 있어요")).contains(new Prefix("Ab3dE5gH", null));
        assertThat(BlogReportService.prefix("quote:12\n남의 글을 그대로")).contains(new Prefix(null, 12L));
        assertThat(BlogReportService.prefix("그냥 사용자 글 sid:Ab3dE5gH")).isEmpty();
        assertThat(BlogReportService.prefix(null)).isEmpty();
    }

    @ParameterizedTest(name = "REQ-252 · 꼴이 틀린 접두 400 · {0}")
    @ValueSource(strings = {"sid:short", "sid:Ab3dE5gH9", "sid:Ab3dE5g!", "quote:abc", "quote:012", "quote:", "quote:99999999999999999999",
            "sid:Ab3dE5gH 뒤에 글"})
    void rejectsBadForm(String detail) {
        assertThatThrownBy(() -> BlogReportService.prefix(detail)).isInstanceOf(IllegalArgumentException.class);
    }
}
