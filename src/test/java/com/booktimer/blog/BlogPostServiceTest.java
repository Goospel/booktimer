package com.booktimer.blog;

import com.booktimer.block.BlockService;
import com.booktimer.security.RateLimitAction;
import com.booktimer.security.RateLimitService;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 초안 만들기 상한(REQ-230 · 계획 §5.1 — 「첫 저장 때 행」 상태기계가 어긋나면 디바운스마다 새 1만 자 행이 생긴다). 스프링 없이. */
@ExtendWith(MockitoExtension.class)
class BlogPostServiceTest {

    @Mock BlogPostRepository postRepository;
    @Mock BlogSentenceRepository sentenceRepository;
    @Mock BlogSentenceRevRepository revRepository;
    @Mock BlogAccess access;
    @Mock BlockService blockService;
    @Mock RateLimitService rateLimitService;

    private BlogPostService service;
    private User me;

    @BeforeEach
    void setUp() {
        service = new BlogPostService(postRepository, sentenceRepository, revRepository, access, blockService, rateLimitService,
                org.mockito.Mockito.mock(QuoteBoxService.class), org.mockito.Mockito.mock(QuoteBoxRepository.class));
        me = User.of("me@booktimer.com", "$2a$10$abcdefghijklmnopqrstuv", "나", "Asia/Seoul", Role.USER);
        ReflectionTestUtils.setField(me, "id", 1L);
    }

    @Test
    @DisplayName("REQ-230 · 초안 만들기 상한(BLOG_DRAFT_CREATE) 넘으면 429 · 행 0")
    void createRateLimited() {
        when(rateLimitService.allow(RateLimitAction.BLOG_DRAFT_CREATE, 1L)).thenReturn(false);

        assertThatThrownBy(() -> service.create(me, "제목", "본문"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(e.getReason()).isEqualTo("글을 너무 자주 만들었어요 — 잠시 뒤 다시 해 주세요");
                });
        // 상한이 검증(빈 글 400)보다 앞이다 — 깨진 클라가 빈 몸을 보내도 센다
        assertThatThrownBy(() -> service.create(me, null, null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        verify(postRepository, never()).save(any());
    }

    @Test
    @DisplayName("REQ-230 · 상한 안이면 만든다 · 허용 목록 403은 상한을 세지 않는다")
    void createWithinLimitAndAccessFirst() {
        when(rateLimitService.allow(RateLimitAction.BLOG_DRAFT_CREATE, 1L)).thenReturn(true);
        when(postRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        assertThat(service.create(me, "제목", "본문").getDraftTitle()).isEqualTo("제목"); // 양성 대조

        User closed = User.of("closed@booktimer.com", "$2a$10$abcdefghijklmnopqrstuv", "남", "Asia/Seoul", Role.USER);
        ReflectionTestUtils.setField(closed, "id", 2L);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "닫힘")).when(access).check(closed);
        assertThatThrownBy(() -> service.create(closed, "제목", "본문")).isInstanceOf(ResponseStatusException.class);
        verify(rateLimitService, never()).allow(RateLimitAction.BLOG_DRAFT_CREATE, 2L);
    }
}
