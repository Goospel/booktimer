package com.booktimer.blog;

import com.booktimer.user.User;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 신고 · 차단의 대상 판정 한 곳(B2b · 계획 §4.4 · §4.7) — <b>공개 대상</b>이면 그 글쓴이: 글 = 올림 ∧ 가리지 않음 / 댓글 = 가리지 않음 ∧
 * 그 글이 올림 ∧ 가리지 않음. 아니면(초안 · 가린 것 · 없는 id) 빈 값 — 부르는 쪽이 같은 404로 답한다(존재 · 이름 비노출).
 *
 * <p><b>보는 사람 쪽 술어(차단 관계 · 내 신고)는 일부러 넣지 않는다</b>: 차단이 피해자의 신고 길을 막지 않게 · 신고 뒤 차단과 두 번
 * 누른 차단이 404가 되지 않게. 공개 대상의 글쓴이 이름은 로그인하지 않은 웹에서도 누구나 본다(약관 ⑤ · D14) — 숨길 것이 없다.
 */
@Component
class BlogTargets {

    private final BlogPostRepository postRepository;
    private final BlogCommentRepository commentRepository;

    BlogTargets(BlogPostRepository postRepository, BlogCommentRepository commentRepository) {
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
    }

    Optional<User> publicAuthor(BlogReport.Target type, long id) {
        return switch (type) {
            case POST -> postRepository.findById(id).filter(BlogPost::quotable).map(BlogPost::getUser);
            case COMMENT -> commentRepository.findById(id).filter(c -> !c.hidden() && c.getPost().quotable()).map(BlogComment::getUser);
        };
    }
}
