package com.booktimer.blog;

import com.booktimer.blog.BlogViews.MeView;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

/**
 * 블로그 첫 설정(J23 · REQ-246 · REQ-259) — 블로그에 보일 이름 = 닉네임 하나 · 커뮤니티 규칙 동의. 가입 경로와 무관하게
 * 첫 올리기 전에 한 번 확인한다(구글 실명 · Apple 「독자」 기본 이름이 그대로 공개되지 않게).
 */
@Service
@Transactional
public class BlogIdentityService {

    private final BlogAccess access;
    private final UserRepository userRepository;
    private final Clock clock;

    public BlogIdentityService(BlogAccess access, UserRepository userRepository, Clock clock) {
        this.access = access;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MeView me(User user) {
        return new MeView(user.getNickname(), user.getBlogNameConfirmedAt() != null, user.getUgcTermsAt() != null);
    }

    /**
     * 이름 확인 + 규칙 동의를 한 번에. 길이는 UTF-16 {@code length()}(앱 JS {@code length}와 같은 단위 — 이모지는 2).
     *
     * @throws IllegalArgumentException 이름 길이 · 제어 문자 · 동의 없음(→ 400)
     */
    public MeView confirm(User user, String name, Boolean agreeTerms) {
        access.check(user);
        String n = name == null ? "" : name.strip();
        if (n.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("이름에 줄바꿈이나 보이지 않는 글자는 쓸 수 없어요");
        }
        if (n.length() < 2 || n.length() > 20) {
            throw new IllegalArgumentException("이름은 2~20자로 적어 주세요");
        }
        if (!Boolean.TRUE.equals(agreeTerms)) {
            throw new IllegalArgumentException("커뮤니티 규칙에 동의해 주세요");
        }
        user.updateProfile(n, user.getTimezone());
        user.confirmBlogIdentity(clock.instant().truncatedTo(ChronoUnit.MICROS));
        userRepository.save(user);
        return me(user);
    }

    /** 첫 올리기 전 — 이름 확인 · 규칙 동의 둘 중 하나라도 없으면 409. 이미 읽힌 필드만 본다(§4.11). */
    void requireReady(User user) {
        if (user.getBlogNameConfirmedAt() == null || user.getUgcTermsAt() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "블로그에 보일 이름과 커뮤니티 규칙을 먼저 확인해 주세요");
        }
    }
}
