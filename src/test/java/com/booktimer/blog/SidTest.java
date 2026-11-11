package com.booktimer.blog;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** REQ-232 — 문장 주소 sid: base62 8자 · 충돌이면 다시 뽑는다(이번 올리기에서 뽑은 것도 겹침으로 · 계획 §5.1 · §2 #23). */
class SidTest {

    @Test
    void randomIsEightBase62Characters() {
        Set<String> drawn = new HashSet<>();
        IntStream.range(0, 200).forEach(i -> drawn.add(Sid.random()));
        assertThat(drawn).allMatch(Sid::valid).hasSizeGreaterThan(190);
        // 대소문자를 가르는 base62 — 소문자 · 대문자 · 숫자가 다 나온다(36진으로 줄면 깨진다)
        String all = String.join("", drawn);
        assertThat(all).containsPattern("[a-z]").containsPattern("[A-Z]").containsPattern("[0-9]");
    }

    @Test
    void validAcceptsOnlyEightBase62() {
        assertThat(Sid.valid("aZ09bY18")).isTrue();
        assertThat(Sid.valid("aZ09bY1")).isFalse();
        assertThat(Sid.valid("aZ09bY189")).isFalse();
        assertThat(Sid.valid("aZ09-Y18")).isFalse();
        assertThat(Sid.valid(null)).isFalse();
    }

    @Test
    void REQ_232_충돌이면_다시_뽑는다() {
        Deque<String> supply = new ArrayDeque<>(List.of("taken001", "taken001", "freshOne"));
        String sid = Sid.fresh(Set.of("taken001")::contains, supply::pop);
        assertThat(sid).isEqualTo("freshOne");
    }

    @Test
    // 「이번 올리기 ∪ DB」 합집합은 이 술어를 넘기는 BlogPublishService가 만든다 — 여기선 Sid.fresh가 술어대로 다시 뽑는지만 잰다
    @org.junit.jupiter.api.DisplayName("REQ-232 · Sid.fresh는 넘긴 술어(이번에 뽑은 것 ∪ DB)가 참이면 다시 뽑는다")
    void REQ_232_이번에_뽑은_것도_겹침으로_본다() {
        Set<String> thisPublish = new HashSet<>();
        Set<String> inDb = Set.of("dbRow001");
        Deque<String> supply = new ArrayDeque<>(List.of("first001", "first001", "dbRow001", "second01"));

        String first = Sid.fresh(s -> thisPublish.contains(s) || inDb.contains(s), supply::pop);
        thisPublish.add(first);
        String second = Sid.fresh(s -> thisPublish.contains(s) || inDb.contains(s), supply::pop);

        assertThat(first).isEqualTo("first001");
        assertThat(second).isEqualTo("second01");
    }

    @Test
    @org.junit.jupiter.api.DisplayName("REQ-232 · 한 번 올리기 안에서 이번에 뽑은 sid도 겹침으로 본다(같은 값을 두 번 내는 공급자 → 둘째는 다시 뽑는다)")
    void REQ_232_합집합_세_인자() {
        Set<String> drawn = new HashSet<>();
        Set<String> inDb = Set.of("dbRow001");
        Deque<String> supply = new ArrayDeque<>(List.of("same0001", "same0001", "dbRow001", "other002"));

        String first = Sid.fresh(drawn, inDb::contains, supply::pop);
        String second = Sid.fresh(drawn, inDb::contains, supply::pop);

        assertThat(first).isEqualTo("same0001");
        assertThat(second).isEqualTo("other002");
        assertThat(drawn).containsExactlyInAnyOrder("same0001", "other002");
    }

    @Test
    void 열번_다_겹치면_예외() {
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> Sid.fresh(s -> true, () -> {
            calls.incrementAndGet();
            return "same0001";
        }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(calls).hasValue(10);
    }
}
