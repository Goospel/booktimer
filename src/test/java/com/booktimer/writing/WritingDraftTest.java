package com.booktimer.writing;

import com.booktimer.book.Book;
import com.booktimer.book.BookStatus;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 글 원장 한 편의 도메인 규칙 — DB 없는 순수 절반(v1 REQ-10 · 16 · 47).
 *
 * <p>겨누는 실패: ① 낡은 판의 저장이 앞의 긴 글을 조용히 덮는 것(대조와 증가가 한 몸이어야 한다) ② 길이 위반이
 * 반쪽 상태를 남기는 것 ③ 내보내기 시각이 두 번째 공유에 덮여 「처음 내보낸 때」가 사라지는 것 ④ AI 결과 저장이
 * 사용자 본문·판 번호를 건드리는 것(AI 글자가 글에 들어가는 길은 없다 — REQ-47).
 */
class WritingDraftTest {

    private User user;
    private Book book;

    @BeforeEach
    void setUp() {
        user = User.of("writer@booktimer.com", "$2a$10$abcdefghijklmnopqrstuv", "글쓴이", "Asia/Seoul", Role.USER);
        book = Book.register(user, "데미안", "헤르만 헤세", null, null, null, null, BookStatus.READING);
    }

    @Test
    @DisplayName("start: 빈 본문 · 판 0 · 제목 없음으로 시작한다")
    void startsEmpty() {
        WritingDraft draft = WritingDraft.start(user, book);

        assertThat(draft.getBody()).isEmpty();
        assertThat(draft.getRevision()).isZero();
        assertThat(draft.getTitle()).isNull();
        assertThat(draft.getBook()).isSameAs(book);
        assertThat(draft.getExportedAt()).isNull();
        assertThat(draft.getQuestionsJson()).isNull();
    }

    @Test
    @DisplayName("edit: 기대 판이 맞으면 제목·본문을 바꾸고 판을 1 올린다")
    void editBumpsRevision() {
        WritingDraft draft = WritingDraft.start(user, book);

        draft.edit("  나의 데미안\n읽기  ", "첫 문단\n\n둘째 문단", 0);

        assertThat(draft.getTitle()).as("제목의 개행은 공백으로 접힌다").isEqualTo("나의 데미안 읽기");
        assertThat(draft.getBody()).as("본문은 친 그대로 — 다듬지 않는다").isEqualTo("첫 문단\n\n둘째 문단");
        assertThat(draft.getRevision()).isEqualTo(1);
    }

    @Test
    @DisplayName("edit: 낡은 판이면 StaleDraftException이고 본문·판이 그대로다")
    void staleRevisionIsRejected() {
        WritingDraft draft = WritingDraft.start(user, book);
        draft.edit(null, "긴 글", 0);

        assertThatThrownBy(() -> draft.edit(null, "짧은 글", 0))
                .isInstanceOf(WritingDraft.StaleDraftException.class);
        assertThat(draft.getBody()).isEqualTo("긴 글");
        assertThat(draft.getRevision()).isEqualTo(1);
    }

    @Test
    @DisplayName("edit: 본문은 BODY_MAX까지, 넘으면 IAE이고 아무것도 안 바뀐다")
    void bodyMax() {
        WritingDraft draft = WritingDraft.start(user, book);
        draft.edit(null, "가".repeat(WritingDraft.BODY_MAX), 0);
        assertThat(draft.getRevision()).isEqualTo(1);

        assertThatThrownBy(() -> draft.edit("새 제목", "가".repeat(WritingDraft.BODY_MAX + 1), 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> draft.edit("새 제목", null, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(draft.getTitle()).isNull();
        assertThat(draft.getRevision()).isEqualTo(1);
    }

    @Test
    @DisplayName("edit: 제목은 TITLE_MAX까지, 넘으면 IAE · 공백 제목은 null")
    void titleMax() {
        WritingDraft draft = WritingDraft.start(user, book);
        draft.edit("가".repeat(WritingDraft.TITLE_MAX), "본문", 0);
        assertThat(draft.getTitle()).hasSize(WritingDraft.TITLE_MAX);

        assertThatThrownBy(() -> draft.edit("가".repeat(WritingDraft.TITLE_MAX + 1), "본문 2", 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(draft.getBody()).isEqualTo("본문");

        draft.edit("   ", "본문", 1);
        assertThat(draft.getTitle()).isNull();
    }

    @Test
    @DisplayName("markExported: 처음 한 번만 시각을 박는다")
    void markExportedOnce() {
        WritingDraft draft = WritingDraft.start(user, book);
        Instant first = Instant.parse("2026-11-24T01:00:00Z");

        draft.markExported(first);
        draft.markExported(Instant.parse("2026-11-25T01:00:00Z"));

        assertThat(draft.getExportedAt()).isEqualTo(first);
    }

    @Test
    @DisplayName("recordRound: 질문 기록·AI 시각·원가만 바꾸고 제목·본문·판은 안 건드린다")
    void recordRoundTouchesAiColumnsOnly() {
        WritingDraft draft = WritingDraft.start(user, book);
        draft.edit("제목", "사용자 글", 0);
        Instant at = Instant.parse("2026-11-24T02:00:00Z");

        draft.recordRound("{\"rounds\":[]}", 1_500L, at);
        draft.recordRound("{\"rounds\":[1]}", 500L, at.plusSeconds(60));
        draft.addCost(7L);

        assertThat(draft.getTitle()).isEqualTo("제목");
        assertThat(draft.getBody()).isEqualTo("사용자 글");
        assertThat(draft.getRevision()).isEqualTo(1);
        assertThat(draft.getQuestionsJson()).isEqualTo("{\"rounds\":[1]}");
        assertThat(draft.getAiUpdatedAt()).isEqualTo(at.plusSeconds(60));
        assertThat(draft.getAiCostMicroUsd()).isEqualTo(2_007L);
    }
}
