package com.booktimer.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 개인정보처리방침·이용약관 정적 페이지.
 *
 * <p>인증 없이 접근 가능한 고지 페이지({@code privacy.html}·{@code terms.html})를 그린다. 목적:
 * ① 개인정보보호법상 수집·이용 고지 의무, ② Google OAuth 동의 화면(Production 게시)이 요구하는
 * 공개 개인정보처리방침 URL, ③ 앱인토스 콘솔의 토스 로그인 등록이 요구하는 공개 이용약관 URL.
 * 내용은 동적 데이터가 없는 정적 텍스트라 컨트롤러는 뷰만 반환한다.
 */
@Controller
public class PrivacyController {

    @GetMapping("/privacy")
    public String privacy() {
        return "privacy";
    }

    @GetMapping("/terms")
    public String terms() {
        return "terms";
    }
}
