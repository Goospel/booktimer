package com.booktimer.book;

import com.booktimer.user.Role;
import com.booktimer.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 공부 책 엔티티의 값 규칙 — 바로가기 링크({@code linkUrl})와 제목 경계.
 *
 * <p>링크 검사가 <b>유일한 벨트</b>다: 웹은 {@code :href}에 그대로 실어 열고(Vue는 {@code javascript:}를
 * 걸러 주지 않는다) {@code <input type="url">}도 {@code javascript:}를 통과시킨다. 그래서 스킴 판정은
 * 화면이 아니라 여기 있고, 이 테스트가 그 판정의 계측기다.
 *
 * <p>⚠️ 방어선은 <b>둘</b>이고 서로 다른 입력이 계측한다(돌연변이 실측 2026-09-16) — 스킴 분기 = host가 <b>있는</b>
 * 위험 스킴({@code javascript://host/%0a…}·{@code ftp://}), host 분기 = 파싱은 되는데 host가 없는 값. 맨
 * {@code javascript:alert(1)}은 host가 null이라 host 분기에 먼저 걸려서, 그 값만으로는 스킴 분기를 지워도 초록이다.
 */
class StudyBookTest {

    private static User user() {
        return User.of("study@booktimer.com", "$2a$10$abcdefghijklmnopqrstuv", "학생", "Asia/Seoul", Role.USER);
    }

    @Nested
    @DisplayName("normalizeLinkUrl — 적재 단일 통로")
    class NormalizeLinkUrl {

        @Test
        @DisplayName("빈 값은 전부 null로 모은다 — 「링크 없음」의 표기가 갈리지 않는다")
        void blankBecomesNull() {
            assertThat(StudyBook.normalizeLinkUrl(null)).isNull();
            assertThat(StudyBook.normalizeLinkUrl("")).isNull();
            assertThat(StudyBook.normalizeLinkUrl("   ")).isNull();
        }

        @Test
        @DisplayName("앞뒤 공백을 떼고 http·https를 통과시킨다")
        void stripsAndAcceptsHttpSchemes() {
            assertThat(StudyBook.normalizeLinkUrl("  https://a.b/c ")).isEqualTo("https://a.b/c");
            assertThat(StudyBook.normalizeLinkUrl("http://a.b/c")).isEqualTo("http://a.b/c");
        }

        /**
         * 두 번째 줄이 이 테스트의 본체다 — {@code javascript://evil.example/%0aalert(1)}은 host가 있어 host 검사를
         * 통과하고, {@code :href}에 실리면 {@code //evil.example/} 뒤 개행 다음의 {@code alert(1)}이 실행되는 알려진
         * 벡터다. 이 값을 막는 것은 <b>스킴 분기뿐</b>이라 그 분기의 XSS 계측기가 된다(리뷰 R2).
         */
        @Test
        @DisplayName("javascript: 는 거부한다 — 웹이 :href로 그대로 열기 때문에 여기가 유일한 벨트다")
        void rejectsJavascriptScheme() {
            assertThatThrownBy(() -> StudyBook.normalizeLinkUrl("javascript:alert(1)"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("링크는 http:// 또는 https://로 시작하는 주소여야 해요");
            assertThatThrownBy(() -> StudyBook.normalizeLinkUrl("javascript://evil.example/%0aalert(1)"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("링크는 http:// 또는 https://로 시작하는 주소여야 해요");
        }

        @Test
        @DisplayName("스킴이 없거나 http가 아닌 주소는 거부한다")
        void rejectsOtherSchemes() {
            assertThatThrownBy(() -> StudyBook.normalizeLinkUrl("//evil.example"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> StudyBook.normalizeLinkUrl("ftp://x.y/z"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> StudyBook.normalizeLinkUrl("not a url"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        /**
         * ⚠️ {@code "https://"}만으로는 <b>host 검사를 계측하지 못한다</b> — 그건 URI 파싱 자체가 실패해
         * 앞단 catch에 걸린다(host 분기를 지워도 초록이었다. 돌연변이 실측 2026-09-16).
         * 슬래시 하나짜리 오타는 <b>파싱에 성공하면서 host만 없는</b> 부류의 대표라, 이 줄이 그 분기의 계측기다
         * (같은 부류: {@code https:evil.example}·밑줄 host·한글 도메인 — 전부 host가 null로 파싱된다).
         */
        @Test
        @DisplayName("host가 없는 주소는 거부한다 — 스킴만 맞는 껍데기는 열 곳이 없다")
        void rejectsMissingHost() {
            assertThatThrownBy(() -> StudyBook.normalizeLinkUrl("https://"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> StudyBook.normalizeLinkUrl("https:/lec.example/c"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("1000자까지 통과, 1001자는 거부 — 컬럼 길이가 곧 경계다(초과는 DB에서 500)")
        void rejectsOverLength() {
            String at1000 = "https://a.b/" + "x".repeat(StudyBook.MAX_LINK_URL_LENGTH - "https://a.b/".length());
            assertThat(at1000).hasSize(StudyBook.MAX_LINK_URL_LENGTH);
            assertThat(StudyBook.normalizeLinkUrl(at1000)).isEqualTo(at1000);

            assertThatThrownBy(() -> StudyBook.normalizeLinkUrl(at1000 + "x"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("링크는 1000자 이내로 적어 주세요");
        }
    }

    @Test
    @DisplayName("register: 링크를 실어 등록하면 정규화된 값이 남는다")
    void registerWithLink() {
        StudyBook book = StudyBook.register(user(), "수학 뉴런", "강사", null, null, null, null,
                "  https://lec.example/course/1 ");

        assertThat(book.getLinkUrl()).isEqualTo("https://lec.example/course/1");
    }

    @Test
    @DisplayName("옛 7인자 register는 링크 없이 위임한다 — 기존 호출부 17곳이 그대로 돈다")
    void legacyRegisterHasNoLink() {
        assertThat(StudyBook.register(user(), "기본서", null, null, null, null, null).getLinkUrl()).isNull();
    }

    @Test
    @DisplayName("changeLinkUrl: 빈 값이면 해제된다 — 죽은 링크를 지울 길이 늘 있다")
    void changeLinkUrlClears() {
        StudyBook book = StudyBook.register(user(), "수학 뉴런", null, null, null, null, null,
                "https://lec.example/course/1");

        book.changeLinkUrl("https://new.example/x");
        assertThat(book.getLinkUrl()).isEqualTo("https://new.example/x");

        book.changeLinkUrl("");
        assertThat(book.getLinkUrl()).isNull();
    }

    @Test
    @DisplayName("제목 300자는 통과, 301자는 IAE — DB가 터뜨리는 500을 문 앞 400으로 앞당긴다")
    void titleLengthBoundary() {
        String at300 = "가".repeat(StudyBook.MAX_TITLE_LENGTH);
        assertThat(StudyBook.register(user(), at300, null, null, null, null, null).getTitle()).isEqualTo(at300);

        assertThatThrownBy(() -> StudyBook.register(user(), at300 + "가", null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("제목이 공백뿐이면 거부한다(기존 규칙)")
    void blankTitleRejected() {
        assertThatThrownBy(() -> StudyBook.register(user(), "   ", null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
