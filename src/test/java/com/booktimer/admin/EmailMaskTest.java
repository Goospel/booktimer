package com.booktimer.admin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * email 마스킹 규칙 단위 테스트 (관리자 데이터 조회 — admin-data-lookup-design §3).
 *
 * <p>운영 화면 최소 노출: local part 첫 글자만 남기고 가린다. 도메인은 가입 분포 파악용으로 보존한다.
 * 비정상 입력(null·'@' 없음)은 식별 불가한 {@code ***}로 안전 처리한다.
 */
class EmailMaskTest {

    @Test
    @DisplayName("일반 이메일은 local 첫 글자 + *** + @도메인으로 가린다")
    void mask_normal() {
        assertThat(EmailMask.mask("goospel@gmail.com")).isEqualTo("g***@gmail.com");
        assertThat(EmailMask.mask("ab@y.com")).isEqualTo("a***@y.com");
    }

    @Test
    @DisplayName("local이 1글자면 *로만 가린다")
    void mask_singleCharLocal() {
        assertThat(EmailMask.mask("a@x.com")).isEqualTo("*@x.com");
    }

    @Test
    @DisplayName("null·'@' 없는 비정상 입력은 ***로 안전 처리한다")
    void mask_invalid() {
        assertThat(EmailMask.mask(null)).isEqualTo("***");
        assertThat(EmailMask.mask("")).isEqualTo("***");
        assertThat(EmailMask.mask("noatsign")).isEqualTo("***");
    }
}
