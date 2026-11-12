package com.booktimer.blog;

import java.time.Instant;
import java.util.List;

/**
 * 블로그 API 응답(계획 §5.1 「DTO」) — 엔티티 직렬화 금지 · 평탄 record 화이트리스트 · 필드 이름이 앱 {@code types.ts}의 단일 출처.
 * 인용 박스(B1b)도 여기 둔다 — 쪽지 응답({@code MarginView.quotes})이 같은 {@link QuoteBoxView}를 쓴다(DTO 단일 출처).
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
     * @param hangBook 글이 인용한 책 가운데 보는 사람 독서 서재에 있는 첫 책 — 없으면 null(J75)
     * @param draft    주인만(남에게는 null)
     */
    public record PostView(long id, String title, String authorName, boolean mine, Instant publishedAt,
                           Instant republishedAt, List<Block> blocks, Highlight highlight, HangBook hangBook, Draft draft) {
    }

    /**
     * @param type      {@code "P"} 문단 · {@code "Q"} 인용 박스
     * @param sentences {@code Q}면 빈 목록(null 아님)
     * @param quote     {@code Q}만 — {@code P}는 null
     */
    public record Block(String type, List<SentenceView> sentences, QuoteBoxView quote) {
    }

    /**
     * 인용 박스 한 개(§5.2 · §6.7). 값이 없으면 null — 보는 사람에게 가지 않는 판정은 null로 둔다(REQ-238 · 240).
     *
     * @param kind          {@code BLOG} · {@code MARGIN} · {@code BOOK}
     * @param sourceLink    블로그 박스의 지금 구간 주소 {@code /p/{id}/s/{a}-{b}}(한 문장이면 {@code /s/{sid}})
     * @param sourceDeleted 원문이 지워졌나
     * @param updatable     지금 원문이 박스 글과 다르다(반영할 수 있다)
     * @param sourceNote    여백 · 책 박스의 원 쪽지 — 박스 주인에게만
     */
    public record QuoteBoxView(long id, int label, String kind, String text, Instant quotedAt, String attribution,
                               String sourceLink, Boolean sourceDeleted, Boolean updatable, SourceNote sourceNote) {
    }

    public record SourceNote(long id, Long bookId) {
    }

    public record HangBook(long bookId, String title) {
    }

    /** 쪽지 생각의 문장 나누기(내 여백 가져오기 — REQ-292). {@code revision} = 쪽지 판(고른 색인과 함께 보낸다). */
    public record NoteSentences(int revision, List<NoteSentence> sentences) {
    }

    /**
     * @param n       0부터
     * @param br      같은 문단에서 이 문장 뒤에 줄이 바뀐다
     * @param paraEnd 문단 끝 문장
     */
    public record NoteSentence(int n, String text, boolean br, boolean paraEnd) {
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

    /** @param quotes 그 글의 박스 전부 — 배치에 아직 없는 것 포함 · label 순 */
    public record Draft(String title, String body, int revision, Instant updatedAt, List<QuoteBoxView> quotes) {
    }

    public record RevView(int rev, String text, Instant at) {
    }
}
