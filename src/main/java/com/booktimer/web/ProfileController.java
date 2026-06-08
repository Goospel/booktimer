package com.booktimer.web;

import com.booktimer.book.Book;
import com.booktimer.book.BookStatus;
import com.booktimer.profile.ProfileService;
import com.booktimer.profile.ProfileView;
import com.booktimer.report.ReportReason;
import com.booktimer.security.CurrentUserService;
import com.booktimer.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;

/**
 * 개인 공개 프로필 페이지 (SNS 2·3단계, sns-design §7.2·§7.3).
 *
 * <p>{@code GET /u/{loginId}} — login_id(공개 @핸들)로 공개 프로필(PUBLIC 책장 + 잔디 + 팔로우 카운트)을 SSR로 그린다.
 * 이 페이지는 "남에게 보이는 공개 프로필"이라 viewer를 가리지 않는다(본인이 봐도 PUBLIC만, {@link ProfileService}).
 * 팔로우 버튼만 viewer 기준으로 분기한다(내가 팔로우 중인지 / 본인이면 버튼 없음).
 *
 * <p>접근 제어: 로그인 사용자만(비로그인은 SecurityConfig {@code anyRequest().authenticated()}로 차단).
 * 없는 아이디는 <b>404</b>(존재 누설 회피 §5.3).
 */
@Controller
public class ProfileController {

    private final ProfileService profileService;
    private final CurrentUserService currentUserService;

    public ProfileController(ProfileService profileService, CurrentUserService currentUserService) {
        this.profileService = profileService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/u/{loginId}")
    public String profile(@PathVariable String loginId,
                          @RequestParam(value = "status", required = false) String status,
                          Principal principal, Model model) {
        User viewer = currentUser(principal);
        ProfileView profile = profileService.profileOf(viewer, loginId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "프로필을 찾을 수 없습니다."));

        // 공개 책장 상태 필터(읽고싶음/읽는중/완독) — 책장(/books)과 동일하게 서버사이드 ?status= 로 거른다.
        // 값이 없거나 잘못되면 전체(null). 잔디·팔로우 등 다른 영역은 영향받지 않는다(책 목록만 필터).
        BookStatus shelfFilter = parseStatus(status);
        List<Book> shelfBooks = shelfFilter == null
                ? profile.books()
                : profile.books().stream().filter(b -> b.getStatus() == shelfFilter).toList();

        model.addAttribute("loginId", profile.loginId());
        model.addAttribute("nickname", profile.nickname());
        model.addAttribute("books", shelfBooks);
        model.addAttribute("shelfFilter", shelfFilter); // 필터 칩 활성 표시·빈 메시지 분기
        model.addAttribute("statuses", BookStatus.values());
        model.addAttribute("bookTimes", profile.bookTimes());
        model.addAttribute("graph", profile.graph());
        model.addAttribute("followerCount", profile.followerCount());
        model.addAttribute("followingCount", profile.followingCount());
        model.addAttribute("following", profile.following());
        model.addAttribute("self", profile.self());
        model.addAttribute("personality", profile.personality()); // 공개 책BTI 서술(opt-in+캐시 있을 때만, 아니면 null)
        model.addAttribute("reportReasons", ReportReason.values());
        return "profile";
    }

    /** 공개 책장 필터 파라미터를 BookStatus로 — 없거나 잘못된 값이면 null(전체). (BookController와 동일 규칙) */
    private BookStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return BookStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private User currentUser(Principal principal) {
        return currentUserService.resolve(principal);
    }
}
