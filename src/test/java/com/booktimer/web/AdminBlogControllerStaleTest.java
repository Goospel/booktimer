package com.booktimer.web;

import com.booktimer.blog.BlogComment;
import com.booktimer.blog.BlogModerationService;
import com.booktimer.blog.BlogModerationService.Unit;
import com.booktimer.blog.Moderation.Action;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 관리자 처리 문 — 처리하는 사이 게시물 · 신고 · 사용자 행이 지워져 커밋이 낙관 실패(0행 UPDATE)면 500이 아니라 flash(B2b 리뷰 1차).
 * 실제로 그 예외가 나는지는 {@code BlogSafetyRaceTest}가 실 H2로 잰다 — 여기는 문이 그것을 받아 바꾸는지만(스프링 없는 Mockito · 새 컨텍스트 0).
 */
class AdminBlogControllerStaleTest {

    private final BlogModerationService moderation = mock(BlogModerationService.class);
    private final AdminBlogController controller = new AdminBlogController(moderation);

    @Test
    @DisplayName("REQ-338 · 처리 · 단위 가리기 · 글쓰기 막기 문 셋은 커밋 낙관 실패를 flash 「다시 불러와 주세요」로 바꾼다")
    void staleCommitBecomesFlash() {
        ObjectOptimisticLockingFailureException stale = new ObjectOptimisticLockingFailureException(BlogComment.class, 9L);
        doThrow(stale).when(moderation).resolve(anyLong(), any(), any(), any(), any());
        when(moderation.apply(any(Unit.class), anyLong(), any(Action.class), any())).thenThrow(stale);
        doThrow(stale).when(moderation).restrict(anyLong(), anyBoolean());

        RedirectAttributesModelMap a = new RedirectAttributesModelMap();
        assertThat(controller.resolve(1L, "POLICY", "COMMENT", 9L, null, a)).isEqualTo("redirect:/admin/reports");
        RedirectAttributesModelMap b = new RedirectAttributesModelMap();
        assertThat(controller.moderate("COMMENT", 9L, "POLICY", null, b)).isEqualTo("redirect:/admin/reports");
        RedirectAttributesModelMap c = new RedirectAttributesModelMap();
        assertThat(controller.restriction(3L, true, c)).isEqualTo("redirect:/admin/reports");

        for (RedirectAttributesModelMap r : new RedirectAttributesModelMap[]{a, b, c}) {
            assertThat(r.getFlashAttributes().get("error")).isEqualTo("처리하는 사이 게시물이나 신고가 바뀌었어요 — 다시 불러와 주세요");
        }
    }
}
