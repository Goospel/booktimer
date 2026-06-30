package com.booktimer.garden;

import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * ProfileCharacterService 단위 테스트 (Mockito 모킹).
 *
 * <p>핵심 보안 경계: <b>보유(해금)한 작가만 프로필로 선택 가능</b>(IDOR 방어 — 먹이주기와 동일 계약).
 * 공백/null 코드는 선택 해제(이니셜 폴백)이며 보유 검증을 타지 않는다. 보유 판정은 FeedingService와
 * 동일하게 {@code GardenService.view().ownedCharacters()}를 신뢰한다.
 */
@ExtendWith(MockitoExtension.class)
class ProfileCharacterServiceTest {

    @Mock private GardenService gardenService;
    @Mock private UserRepository userRepository;

    @InjectMocks private ProfileCharacterService service;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.of("reader@booktimer.com", "hash", "독자", "Asia/Seoul", Role.USER);
    }

    @Test
    @DisplayName("보유 작가 선택 → profileCharacterCode 설정 + 저장")
    void select_ownedCharacter_setsAndSaves() {
        when(gardenService.view(user)).thenReturn(viewWith("han_gang"));

        service.select(user, "han_gang");

        assertThat(user.getProfileCharacterCode()).isEqualTo("han_gang");
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("미보유 작가 선택 → IllegalArgumentException + 저장 안 함(IDOR 방어)")
    void select_unownedCharacter_throwsAndDoesNotSave() {
        when(gardenService.view(user)).thenReturn(viewWith("han_gang")); // 보유: 한강뿐

        assertThatThrownBy(() -> service.select(user, "kim_young_ha"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(user.getProfileCharacterCode()).isNull();
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("공백 코드 → 선택 해제(이니셜 폴백) + 저장, 보유 검증 안 탐")
    void select_blankCode_clearsAndSaves() {
        user.selectProfileCharacter("han_gang"); // 먼저 설정해 둔 상태

        service.select(user, "  ");

        assertThat(user.getProfileCharacterCode()).isNull();
        verify(userRepository).save(user);
        verifyNoInteractions(gardenService); // 해제는 보유 검증 불필요
    }

    @Test
    @DisplayName("null 코드 → 선택 해제 + 저장")
    void select_nullCode_clears() {
        user.selectProfileCharacter("han_gang");

        service.select(user, null);

        assertThat(user.getProfileCharacterCode()).isNull();
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("다른 보유 작가로 교체 → 새 코드로 갱신")
    void select_replaceWithAnotherOwned_updates() {
        user.selectProfileCharacter("han_gang");
        when(gardenService.view(user)).thenReturn(viewWith("han_gang", "kim_young_ha"));

        service.select(user, "kim_young_ha");

        assertThat(user.getProfileCharacterCode()).isEqualTo("kim_young_ha");
        verify(userRepository).save(user);
    }

    /** 주어진 code들을 보유한 GardenView 생성 (같은 패키지라 package-private 접근 가능) */
    private static GardenView viewWith(String... codes) {
        List<AuthorCharacterState> states = new java.util.ArrayList<>();
        for (String code : codes) {
            AuthorCharacter ch = AuthorCharacter.of(code, code, code, "🪶", 1, code);
            states.add(new AuthorCharacterState(ch, true));
        }
        return new GardenView(states, codes.length, codes.length, List.of(), 0, 0);
    }
}
