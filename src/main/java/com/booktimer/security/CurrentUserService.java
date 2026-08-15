package com.booktimer.security;

import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.springframework.stereotype.Service;

import java.security.Principal;

/**
 * 인증된 주체(principal)를 도메인 {@link User}로 해석하는 공유 서비스.
 *
 * <p>인증 컷오버(PR-4) 후 {@code principal.getName()}은 <b>login_id</b>다 — 로컬은 가입에서 확정한 login_id로
 * 로그인하고, OAuth는 온보딩에서 login_id를 정한 뒤 재로그인부터 login_id가 principal이 된다. 따라서 정상
 * 상태의 해석은 항상 {@code findByLoginId}다.
 *
 * <p><b>OAuth 첫 세션 브리지</b>: OAuth로 처음 들어온 사용자는 온보딩 전이라 login_id가 아직 {@code null}이고,
 * 그 세션의 principal은 이메일이다({@link BookTimerOidcUserService}가 login_id 부재 시 email을 name으로 사용).
 * 그 짧은 창에서만 email로 브리지 조회한다 — 온보딩에서 login_id를 확정하고 재로그인하면 이후로는 login_id 경로다.
 * 이메일 <i>로그인</i>이 아니라 이미 인증된 principal의 <i>해석</i>이므로 보안 목표(이메일로 로그인 불가)와 무관하다.
 *
 * <p><b>아이디 변경 브리지</b>: 아이디를 바꾸면(평생 1회) 이미 열려 있던 세션들의 principal은 여전히 <b>옛</b>
 * login_id다 — 그 세션을 그대로 두면 앞의 두 조회가 모두 빗나가 설정을 저장한 직후 전 기기가 터진다. 그래서
 * 3단째로 {@code previous_login_id}를 본다. 이 한 줄이 <b>모든 기기·JDBC 영속 세션</b>을 동시에 고친다(현재
 * 요청의 SecurityContext만 갈아끼우는 방법으로는 다른 기기 세션이 남는다). 옛 핸들은 UNIQUE로 잠겨 있고
 * 새 아이디 후보와의 충돌도 사전 검사가 막으므로 <b>다른 사람으로 오해석될 수 없다</b>.
 *
 * <p>이 폴백은 <i>해석</i>에만 붙지 <i>로그인</i>에는 붙지 않는다 — {@code BookTimerUserDetailsService}는
 * login_id만 보므로 옛 아이디로 새로 로그인하는 것은 자동으로 불가능하다(의도된 동작). 크리덴셜은 즉시 새
 * 아이디로 넘어가고, 살아 있던 세션만 이 브리지로 연명한다.
 */
@Service
public class CurrentUserService {

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** 인증된 {@link Principal}을 도메인 User로 해석한다. */
    public User resolve(Principal principal) {
        return resolve(principal.getName());
    }

    /** principal 이름(login_id, OAuth 첫 세션은 email, 아이디 변경 전 세션은 옛 login_id)을 도메인 User로 해석한다. */
    public User resolve(String principalName) {
        return userRepository.findByLoginId(principalName)
                .or(() -> userRepository.findByEmail(principalName))
                .or(() -> userRepository.findByPreviousLoginId(principalName)) // 아이디 변경 전 세션 브리지(전 기기)
                .orElseThrow(() -> new IllegalStateException(
                        "authenticated user not found: " + principalName));
    }
}
