package com.booktimer.blog;

import com.booktimer.user.User;
import com.booktimer.web.AppLinkCardPolicy;
import com.booktimer.writing.WritingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * E2(2026-12-01) 전 블로그 쓰기 허용 목록(J45 · U-154 ④ · 계획 §4.8)과 운영자 글쓰기 막기(B2b · 약관 ⑧). 쓰기 문의 <b>첫 줄</b>이다
 * (소유 404 · 몸 400 · 상한 429보다 앞). 읽기 · 지우기는 막지 않는다. 이미 읽힌 {@link User} 필드만 본다 — DB를 읽지 않는다(올리기의
 * 첫 DB 문장은 글 행 잠금 · §4.11).
 *
 * <p>{@link #check} = 허용 목록 + 막힘 — 공개 글 · 댓글을 만들거나 바꾸는 문(초안 만들기 · 저장 · 올리기 · 글 박스 만들기 · 반영 ·
 * 다시 고르기 · 댓글). {@link #checkListed} = 허용 목록만 — 본인만 보는 여백(blog/me · 걸기 · 붙이기 · 쪽지 박스 반영)과 신고.
 */
@Component
public class BlogAccess {

    static final String CLOSED = BlogNames.SPACE + " 기능은 " + WritingService.AI_EXPANDED_EFFECTIVE.getMonthValue() + "월 "
            + WritingService.AI_EXPANDED_EFFECTIVE.getDayOfMonth() + "일부터 열려요";

    /** final이 아닌 건 REQ-244 403 시험이 {@code ReflectionTestUtils}로 바꿔 끼우기 때문이다(T-235 — 새 컨텍스트 금지). */
    @Value("${booktimer.blog.allowed-users:none}")
    private String allowed;

    static final String RESTRICTED = BlogNames.SPACE + " 글쓰기와 댓글이 운영 정책으로 막혔어요 — 이의는 설정의 [문의하기]로 보내 주세요";

    /** 허용 목록 + 운영자 글쓰기 막기(약관 ⑧ 「글쓰기와 댓글」). */
    public void check(User user) {
        checkListed(user);
        if (user.getBlogRestrictedAt() != null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, RESTRICTED);
        }
    }

    /** 허용 목록만. 둘째 인자는 늘 false — Apple 예외를 넘기면 Apple로 가입한 앱 사용자 전부가 목록을 비켜 간다(§4.8 · 비평 L5). */
    public void checkListed(User user) {
        if (!AppLinkCardPolicy.visible(user.getId(), false, allowed)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, CLOSED);
        }
    }
}
