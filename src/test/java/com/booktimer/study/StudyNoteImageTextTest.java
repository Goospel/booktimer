package com.booktimer.study;

import com.booktimer.book.StudyBook;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 필기 본문 속 사진 표식을 읽는 <b>순수 절반</b> — 스프링·DB 없음(NoteReferenceTest 선례).
 *
 * <p>여기서 재는 것은 둘이다. ① 옛 편집기 가드(REQ-07)가 「사진이 든 필기」를 고르는 판정 — 넓으면 사진 없는 필기를
 * 옛 탭에서 막고, 좁으면 사진 든 필기를 못 지킨다. ② 목록 preview가 사진만 있는 줄을 이름으로 쓰지 않는 것(REQ-08) —
 * 줄머리 표식(`- `·`> `·`# `·`1. `·`- [ ] `) 뒤의 사진 줄까지. 아래 preview 표는 화면 {@code notes.test.ts}의
 * {@code previewOf}가 <b>같은 리터럴</b>로 잰다(설계 T2·T13 — 두 곳이 갈리면 같은 장의 이름이 열고 닫을 때 달라진다).
 */
class StudyNoteImageTextTest {

    private static final User USER = User.of("text@booktimer.com", "$2a$10$abcdefghijklmnopqrstuv", "공부벌레",
            "Asia/Seoul", Role.USER);
    private static final StudyBook BOOK = StudyBook.register(USER, "책", "저자", null, null, null, null);

    @Test
    @DisplayName("REQ-07 · referencedIn은 편집기가 쓰는 꼴만 사진으로 본다 — 이스케이프된 !\\[\\](…)·외부 주소·글로 적은 경로는 아니다")
    void referencedIn_onlyOwnMarkdown() {
        assertThat(StudyNoteImage.referencedIn("앞 ![](/api/study/notes/images/12) 뒤")).isTrue();
        assertThat(StudyNoteImage.referencedIn("![](/api/study/notes/images/1)")).isTrue();

        assertThat(StudyNoteImage.referencedIn(null)).isFalse();
        assertThat(StudyNoteImage.referencedIn("글만 있는 필기")).isFalse();
        // 평문으로 친 `![](…)`는 편집기가 이스케이프해 저장한다 — 사진이 아니다.
        assertThat(StudyNoteImage.referencedIn("!\\[\\](/api/study/notes/images/12)")).isFalse();
        assertThat(StudyNoteImage.referencedIn("![](https://example.com/a.jpg)")).isFalse();
        assertThat(StudyNoteImage.referencedIn("주소만 적음 /api/study/notes/images/12")).isFalse();
        assertThat(StudyNoteImage.referencedIn("![칠판](/api/study/notes/images/12)")).isFalse();
        assertThat(StudyNoteImage.referencedIn("![](/api/study/notes/images/0)")).isFalse();
    }

    /** 입력의 {@code \n}은 줄바꿈이다(CSV에 개행을 못 넣어 이스케이프로 적는다). */
    @ParameterizedTest(name = "[{index}] {0} → {1}")
    @CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
            "![](/api/study/notes/images/12)\\n설명 줄"                                   | "설명 줄"
            "![](/api/study/notes/images/12)![](/api/study/notes/images/13)"              | "![](/api/study/notes/images/12)![](/api/study/notes/images/13)"
            "글 ![](/api/study/notes/images/12) 글"                                       | "글 ![](/api/study/notes/images/12) 글"
            "\\n  \\n![](/api/study/notes/images/12)  \\n\\n두 번째 글"                    | "두 번째 글"
            "- ![](/api/study/notes/images/12)\\n- 설명"                                  | "- 설명"
            "> ![](/api/study/notes/images/12)\\n> 설명"                                  | "> 설명"
            "# ![](/api/study/notes/images/12)\\n설명"                                    | "설명"
            "- [ ] ![](/api/study/notes/images/12)\\n- [ ] 설명"                          | "- [ ] 설명"
            "1. ![](/api/study/notes/images/12)\\n2. 설명"                                | "2. 설명"
            "- ![](/api/study/notes/images/12)"                                          | "- ![](/api/study/notes/images/12)"
            "- 글 ![](/api/study/notes/images/12)"                                        | "- 글 ![](/api/study/notes/images/12)"
            """)
    @DisplayName("REQ-08 · preview는 사진만 있는 줄을 건너뛰고 다음 글 줄을 준다 — 글 줄이 없으면 첫 사진 줄 그대로")
    void preview_skipsPhotoOnlyLines(String body, String expected) {
        StudyNote note = StudyNote.of(USER, BOOK, null, body.replace("\\n", "\n"));

        assertThat(note.preview()).isEqualTo(expected);
    }
}
