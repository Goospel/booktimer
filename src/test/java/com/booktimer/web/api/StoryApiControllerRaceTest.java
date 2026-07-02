package com.booktimer.web.api;

import com.booktimer.security.CurrentUserService;
import com.booktimer.story.StoryService;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;

import java.security.Principal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * 열람 기록 동시 경합의 멱등 변환 단위 테스트 — MockMvc로는 유니크 경합을 재현할 수 없어 분리.
 *
 * <p>같은 (story, viewer)의 열람 POST가 동시에 오면 진 쪽 INSERT가 {@code uk_story_view} 위반을 내고
 * 서비스 트랜잭션은 rollback-only가 된다 — 이때 클라이언트가 500이 아니라 멱등 성공(200)을 받는지 고정.
 */
@ExtendWith(MockitoExtension.class)
class StoryApiControllerRaceTest {

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private StoryService storyService;

    @Test
    @DisplayName("열람 POST 동시 경합(uk_story_view 위반) → 500이 아니라 200 멱등 성공")
    void markViewed_racingDuplicate_returns200() {
        StoryApiController controller = new StoryApiController(currentUserService, storyService);
        Principal principal = () -> "viewer@booktimer.com";
        User viewer = User.of("viewer@booktimer.com", "$2a$10$abcdefghijklmnopqrstuv", "열람자", "Asia/Seoul", Role.USER);
        when(currentUserService.resolve(principal)).thenReturn(viewer);
        doThrow(new DataIntegrityViolationException("uk_story_view"))
                .when(storyService).markViewed(viewer, 10L);

        ResponseEntity<Void> response = controller.markViewed(10L, principal);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }
}
