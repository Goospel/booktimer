package com.booktimer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 일부러 실패하는 probe — branch protection이 빨강 PR의 머지를 잠그는지 확인용. 머지하지 않고 PR 닫은 뒤 삭제. */
class ProtectionRedProbeTest {

    @Test
    void alwaysFails_toProveRedBlocksMerge() {
        assertThat(true).as("intentional RED probe — must block merge").isFalse();
    }
}
