package com.booktimer.personality;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 알라딘 author 원문에서 대표 글쓴이 추출 — 옮긴이·그림 등 비저자 역할 제외(책BTI 집계 전용).
 *
 * <p>경계: 역할 토큰 유무 / 옮긴이만 / 비저자만(그림·사진·엮음·감수) / 복합 역할 / 공저 / null·blank.
 */
class WriterNameTest {

    @Test
    @DisplayName("지은이+옮긴이: 옮긴이를 떼고 지은이 이름만(괄호 제거, 내부 공백 보존)")
    void dropsTranslator_keepsAuthor() {
        assertThat(WriterName.lead("레프 니콜라예비치 톨스토이 (지은이), 연진희 (옮긴이)"))
                .isEqualTo("레프 니콜라예비치 톨스토이");
        assertThat(WriterName.lead("베르나르 베르베르 (지은이), 전미연 (옮긴이)"))
                .isEqualTo("베르나르 베르베르");
    }

    @Test
    @DisplayName("역할 표기 없는 순수 이름은 그대로 저자로(수동 입력 호환)")
    void plainName_keptAsAuthor() {
        assertThat(WriterName.lead("김영하")).isEqualTo("김영하");
    }

    @Test
    @DisplayName("옮긴이만 있고 글쓴이가 없으면 null(분포 제외)")
    void translatorOnly_returnsNull() {
        assertThat(WriterName.lead("연진희 (옮긴이)")).isNull();
    }

    @Test
    @DisplayName("글을 쓰지 않은 역할만(그림·사진·엮음·감수)이면 null")
    void nonWriterRolesOnly_returnsNull() {
        assertThat(WriterName.lead("이수지 (그림)")).isNull();
        assertThat(WriterName.lead("아무개 (사진)")).isNull();
        assertThat(WriterName.lead("엮은이 (엮음)")).isNull();
        assertThat(WriterName.lead("아무개 (감수)")).isNull();
    }

    @Test
    @DisplayName("복합: 글 역할은 채택·그림 역할은 제외, 옮긴이가 앞서도 첫 '글쓴이'를 반환")
    void writerAmongOthers_keepsWriter() {
        assertThat(WriterName.lead("백희나 (글), 그린이 (그림)")).isEqualTo("백희나");
        assertThat(WriterName.lead("연진희 (옮긴이), 진짜저자 (지은이)")).isEqualTo("진짜저자");
    }

    @Test
    @DisplayName("공저(지은이 여럿): 대표 1명(첫 글쓴이)")
    void coAuthors_returnsLead() {
        assertThat(WriterName.lead("이문열 (지은이), 김훈 (지은이)")).isEqualTo("이문열");
        assertThat(WriterName.lead("이름1, 이름2 (지은이)")).isEqualTo("이름1");
    }

    @Test
    @DisplayName("공저·편저 등 '저' 역할은 글을 쓴 사람이므로 채택")
    void coWritingRole_accepted() {
        assertThat(WriterName.lead("홍길동 (공저), 김철수 (공저)")).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("전각 괄호 표기도 역할로 인식 — 역할이 이름에 남지 않는다")
    void fullWidthParens_handled() {
        assertThat(WriterName.lead("한강（지은이）, 김역자（옮긴이）")).isEqualTo("한강");
    }

    @Test
    @DisplayName("null·빈·공백 → null")
    void nullOrBlank_returnsNull() {
        assertThat(WriterName.lead(null)).isNull();
        assertThat(WriterName.lead("")).isNull();
        assertThat(WriterName.lead("   ")).isNull();
    }
}
