package com.booktimer.blog;

import java.time.Instant;
import java.util.List;

/**
 * 블로그 API 응답(계획 §5.1 「DTO」) — 엔티티 직렬화 금지 · 평탄 record 화이트리스트 · 필드 이름이 앱 {@code types.ts}의 단일 출처.
 * B1b 자리({@code hangBook} · {@code Block.quote} · {@code Draft.quotes})는 B1a에서 null · 빈 목록이다.
 */
public final class BlogViews {

    private BlogViews() {
    }

    public record MeView(String name, boolean nameConfirmed, boolean termsAgreed) {
    }

    public record PostRow(long id, String title, String authorName, Instant publishedAt, boolean mine) {
    }

    /** @param next 다음 쪽 커서 — 없으면 null */
    public record Page(List<PostRow> posts, String next) {
    }

    /** @param title 올린 제목 — 아직 올리지 않은 초안이면 null */
    public record MyPostRow(long id, String title, String draftTitle, Instant publishedAt, Instant republishedAt,
                            Instant updatedAt) {
    }

    /**
     * @param hangBook B1b — B1a는 null
     * @param draft    주인만(남에게는 null)
     */
    public record PostView(long id, String title, String authorName, boolean mine, Instant publishedAt,
                           Instant republishedAt, List<Block> blocks, Highlight highlight, Object hangBook, Draft draft) {
    }

    /**
     * @param type  {@code "P"} 문단 — B1b가 {@code "Q"}(박스)를 더한다
     * @param quote B1b — B1a는 null
     */
    public record Block(String type, List<SentenceView> sentences, Object quote) {
    }

    /**
     * @param edited 판 &gt; 1
     * @param br     이 문장 뒤에서 줄이 바뀐다(문단 안 줄바꿈 — 문단 끝 문장은 거짓)
     */
    public record SentenceView(String sid, String text, boolean edited, boolean br) {
    }

    /** @param status {@code LIVE} · {@code EDITED} · {@code MERGED} · {@code DELETED} */
    public record Highlight(String status, List<String> sids) {
    }

    /** @param quotes B1b — B1a는 빈 목록 */
    public record Draft(String title, String body, int revision, Instant updatedAt, List<Object> quotes) {
    }

    public record RevView(int rev, String text, Instant at) {
    }
}
