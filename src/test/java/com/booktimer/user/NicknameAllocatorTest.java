package com.booktimer.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NicknameAllocator 순수 단위 테스트 (스프링·DB 무관).
 *
 * <p>닉네임이 유니크해야 하는데, 소셜 로그인은 provider가 준 표시 이름을 거부할 수 없다(로그인 자체를
 * 막을 순 없으니). 그래서 충돌하면 거부 대신 {@code base-2}, {@code base-3} … 로 비어 있는 첫 후보를
 * 자동 할당한다. 이 "첫 빈 후보 찾기"는 순수 함수라 격리 검증한다.
 */
class NicknameAllocatorTest {

    @Test
    @DisplayName("기본 이름이 비어 있으면 그대로 쓴다")
    void base_whenFree_returnsBase() {
        String result = NicknameAllocator.firstAvailable("구글러", taken -> false);
        assertThat(result).isEqualTo("구글러");
    }

    @Test
    @DisplayName("기본 이름이 이미 쓰이면 -2 를 붙인다")
    void base_whenTaken_appendsSuffix2() {
        Set<String> used = Set.of("구글러");
        String result = NicknameAllocator.firstAvailable("구글러", used::contains);
        assertThat(result).isEqualTo("구글러-2");
    }

    @Test
    @DisplayName("기본·-2 둘 다 쓰이면 -3 으로 넘어간다 (첫 빈 후보)")
    void base_whenBaseAnd2Taken_returns3() {
        Set<String> used = Set.of("구글러", "구글러-2");
        String result = NicknameAllocator.firstAvailable("구글러", used::contains);
        assertThat(result).isEqualTo("구글러-3");
    }
}
