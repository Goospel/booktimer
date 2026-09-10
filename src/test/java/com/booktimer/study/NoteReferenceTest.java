package com.booktimer.study;

import com.booktimer.book.StudyBook;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 정답지 고르기 — <b>네트워크·DB 없는 순수 절반</b>.
 *
 * <p>여기서 재는 규칙은 둘이고 둘 다 「조용한 거짓말」을 막는 자리다.
 *
 * <p>① <b>장 단위로 자른다.</b> 글자 단위로 자르면 정답지가 문장 중간에서 끊겨, 모델은 그 뒤를 「안 배운
 * 것」으로 보거나(거짓 구멍) 잘린 문맥을 잘못 이어 붙인다(거짓 통과). 장 단위면 「이 장은 통째로 빠졌다」를
 * 화면에 정직하게 말할 수 있다. 그래서 아래 {@code select_cutsByNoteNotByChar}가 <b>글자 단위 구현을
 * 배제하는 대조군</b>이다 — 1자짜리 장이 상한 뒤에 있으면 글자 단위 구현은 그것을 끼워 넣는다.
 *
 * <p>② <b>빠진 장을 센다.</b> {@code excluded}가 화면 경고 카드의 유일한 근거라, 여기가 늘 비어 있는
 * 구현이면 사용자는 자기 필기가 채점에서 빠진 것을 영영 모른다.
 */
class NoteReferenceTest {

    private static final int MAX = 24_000;

    private User user;
    private StudyBook book;

    @BeforeEach
    void setUp() {
        user = User.of("student@booktimer.com", "$2a$10$abcdefghijklmnopqrstuv", "공부벌레", "Asia/Seoul", Role.USER);
        book = StudyBook.register(user, "정보처리기사 실기", "저자", null, null, null, null);
    }

    /** {@code updatedAt}은 auditing이 채우는 필드라 순수 테스트에선 손으로 심는다(헤더 날짜의 출처). */
    private StudyNote note(String title, String body, String updatedAt) {
        StudyNote note = StudyNote.of(user, book, title, body);
        ReflectionTestUtils.setField(note, "updatedAt", Instant.parse(updatedAt));
        return note;
    }

    private StudyNote sized(String title, int chars) {
        return note(title, "ㄱ".repeat(chars), "2026-09-10T05:00:00Z");
    }

    @Test
    @DisplayName("select: 필기가 없으면 포함 0 · 제외 0 · 0자 — 카드는 「필기 없음」을 말할 근거를 얻는다")
    void select_empty_isEmpty() {
        NoteReference reference = NoteReference.select(List.of(), MAX);

        assertThat(reference.included()).isEmpty();
        assertThat(reference.excluded()).isEmpty();
        assertThat(reference.chars()).isZero();
        assertThat(reference.text(ZoneId.of("Asia/Seoul"))).isEmpty();
    }

    @Test
    @DisplayName("select: 상한을 넘으면 그 장을 통째로 뺀다 — 글자 단위로 자르는 구현을 배제하는 대조군")
    void select_cutsByNoteNotByChar() {
        // 7000 + 8000 + 8000 = 23,000. 다음 장(8000)을 넣으면 31,000이라 <b>통째로</b> 빠진다.
        // 글자 단위 구현이면 남은 1,000자만큼 「4장」의 앞부분을 잘라 넣어 chars가 24,000이 된다 —
        // 그 잘린 문장이 모델에게 거짓 구멍·거짓 통과를 만든다. 그래서 여기가 대조군이다.
        List<StudyNote> newestFirst = List.of(
                sized("1장", 7000), sized("2장", 8000), sized("3장", 8000),
                note("4장", "ㄴ".repeat(8000), "2026-09-01T05:00:00Z"));

        NoteReference reference = NoteReference.select(newestFirst, MAX);

        assertThat(reference.included()).extracting(StudyNote::getTitle)
                .as("최근 3장이 순서 그대로 — 뒤집힌 구현이면 「4장」이 앞에 낀다")
                .containsExactly("1장", "2장", "3장");
        assertThat(reference.excluded()).extracting(StudyNote::getTitle).containsExactly("4장");
        assertThat(reference.chars())
                .as("23,000이어야 한다 — 24,000이면 남은 자리에 「4장」을 잘라 넣은 것이다")
                .isEqualTo(23_000);
        assertThat(reference.text(ZoneId.of("Asia/Seoul")))
                .as("잘린 조각조차 실리면 안 된다")
                .doesNotContain("ㄴ");
    }

    @Test
    @DisplayName("select: 합이 정확히 상한이면 포함한다 — 경계 안쪽")
    void select_exactlyAtLimit_isIncluded() {
        // 한 장의 상한이 StudyNote.BODY_MAX(8000)이라 24,000자는 최소 3장으로 만들어야 한다.
        NoteReference reference = NoteReference.select(
                List.of(sized("1장", 6000), sized("2장", 8000), sized("3장", 8000), sized("4장", 2000)), MAX);

        assertThat(reference.included()).hasSize(4);
        assertThat(reference.excluded()).isEmpty();
        assertThat(reference.chars()).isEqualTo(24_000);
    }

    @Test
    @DisplayName("select: 상한을 넘긴 뒤의 장은 작아도 안 들어간다 — 최근순이 뒤죽박죽이 되지 않게")
    void select_afterOverflow_stopsTaking() {
        NoteReference reference = NoteReference.select(
                List.of(sized("1장", 8000), sized("2장", 8000), sized("3장", 8000),
                        sized("4장", 10), sized("5장", 10)), MAX);

        assertThat(reference.excluded()).extracting(StudyNote::getTitle).containsExactly("4장", "5장");
    }

    @Test
    @DisplayName("text: 장마다 제목·날짜 헤더가 서고 본문은 마크다운째로 실린다 — 제목이 없으면 「제목 없음」")
    void text_hasHeaderPerNoteAndVerbatimBody() {
        String body = "# 미분계수\n- [x] 정의\n==접선의 기울기==";
        NoteReference reference = NoteReference.select(
                List.of(note(null, body, "2026-09-10T15:30:00Z")), MAX);

        String text = reference.text(ZoneId.of("Asia/Seoul"));

        // 05:00 KST가 아니라 2026-09-11 — UTC로 찍으면 하루가 어긋난다(유저 타임존으로 읽는 이유).
        assertThat(text).isEqualTo("### 필기: 제목 없음 (2026-09-11)\n" + body + "\n\n");
    }

    @Test
    @DisplayName("text: 여러 장이 최근순 그대로 이어 붙는다 — 제외된 장은 실리지 않는다")
    void text_joinsIncludedOnly() {
        // 상한을 10자로 좁혀 「옛것」이 밀려나게 한다(한 장 상한이 8000이라 큰 값으로는 못 만든다).
        NoteReference reference = NoteReference.select(
                List.of(note("최근", "새 내용", "2026-09-10T01:00:00Z"),
                        note("옛것", "ㄱ".repeat(20), "2026-09-01T01:00:00Z")), 10);

        String text = reference.text(ZoneId.of("Asia/Seoul"));

        assertThat(text).contains("### 필기: 최근 (2026-09-10)").contains("새 내용");
        assertThat(text).doesNotContain("옛것");
    }
}
