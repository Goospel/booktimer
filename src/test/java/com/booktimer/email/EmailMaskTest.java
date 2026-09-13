package com.booktimer.email;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 로그용 이메일 마스킹 — 평문 이메일이 로그에 남으면 로그 접근권만으로 가입자 명단이 새고,
 * 로그가 백업·수집 파이프라인을 타면서 보존 범위가 통제를 벗어난다.
 *
 * <p>도메인은 남긴다 — 발송 장애 진단(특정 도메인 전체 실패 등)에 필요한 최소 정보다.
 */
class EmailMaskTest {

    @Test
    @DisplayName("정상 주소는 로컬파트 첫 글자만 남기고 가린다(도메인은 진단용으로 보존)")
    void masksLocalPart() {
        assertThat(EmailMask.mask("reader@booktimer.com")).isEqualTo("r***@booktimer.com");
    }

    @Test
    @DisplayName("로컬파트가 1글자여도 원래 길이를 드러내지 않는다 — 고정 *** 이라 길이 추론 불가")
    void singleCharLocalPart() {
        assertThat(EmailMask.mask("a@booktimer.com")).isEqualTo("a***@booktimer.com");
    }

    @Test
    @DisplayName("null·빈값은 (invalid) — 로그 포맷이 깨지거나 NPE로 로깅 자체가 실패하지 않게")
    void nullOrBlank() {
        assertThat(EmailMask.mask(null)).isEqualTo("(invalid)");
        assertThat(EmailMask.mask("   ")).isEqualTo("(invalid)");
    }

    @Test
    @DisplayName("@가 없거나 로컬파트가 비면 (invalid) — 원문을 그대로 흘리지 않는다(가리지 못하면 안 찍는다)")
    void withoutAtSign() {
        assertThat(EmailMask.mask("not-an-email")).isEqualTo("(invalid)");
        assertThat(EmailMask.mask("@booktimer.com")).isEqualTo("(invalid)");
    }
}
