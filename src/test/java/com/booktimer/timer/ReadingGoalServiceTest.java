package com.booktimer.timer;

import com.booktimer.user.Role;
import com.booktimer.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ReadingGoalService 단위 테스트 (Mockito — DB 무관).
 *
 * <p>현재 하루 목표를 "오늘(유저 TZ)"자 한 행으로 이력에 기록하되, 그날 행이 이미 있으면 목표만 갱신해
 * 중복 행을 만들지 않는지(upsert)와, effectiveDate가 서버 UTC가 아니라 유저 타임존으로 정해지는지(N-010)를 본다.
 */
@ExtendWith(MockitoExtension.class)
class ReadingGoalServiceTest {

    // UTC로는 06-06 저녁이지만 KST로는 06-07 새벽 → effectiveDate가 유저 TZ(KST)로 정해지는지 구분되는 시각.
    private static final Instant NOW = Instant.parse("2026-06-06T20:00:00Z");
    private static final LocalDate TODAY_KST = LocalDate.of(2026, 6, 7);

    @Mock
    private ReadingGoalChangeRepository repository;

    private ReadingGoalService service;
    private User user;

    @BeforeEach
    void setUp() {
        user = User.of("reader@booktimer.com", "$2a$10$abcdefghijklmnopqrstuv", "책벌레", "Asia/Seoul", Role.USER);
        service = new ReadingGoalService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("오늘자 행이 없으면 유저 TZ 오늘로 새 변경 이력을 저장한다")
    void record_noRowForToday_savesNewChangeAtUserTzToday() {
        when(repository.findByUserAndEffectiveDate(user, TODAY_KST)).thenReturn(Optional.empty());

        service.record(user, 3660L);

        ArgumentCaptor<ReadingGoalChange> saved = ArgumentCaptor.forClass(ReadingGoalChange.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getEffectiveDate()).isEqualTo(TODAY_KST);
        assertThat(saved.getValue().getGoalSeconds()).isEqualTo(3660L);
    }

    @Test
    @DisplayName("오늘자 행이 이미 있으면 목표만 갱신하고 새 행을 만들지 않는다 (upsert)")
    void record_rowExistsForToday_updatesGoalNoNewRow() {
        ReadingGoalChange existing = ReadingGoalChange.of(user, TODAY_KST, 3600L);
        when(repository.findByUserAndEffectiveDate(user, TODAY_KST)).thenReturn(Optional.of(existing));

        service.record(user, 3660L);

        assertThat(existing.getGoalSeconds()).isEqualTo(3660L); // 그날 행의 목표만 바뀜
        verify(repository, never()).save(any());               // 중복 행 저장 없음(영속 엔티티 dirty checking)
    }
}
