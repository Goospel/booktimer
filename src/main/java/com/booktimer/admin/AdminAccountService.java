package com.booktimer.admin;

import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;

/**
 * 운영자(ADMIN) 시드 승격 유스케이스.
 *
 * <p>가입은 항상 {@code Role.USER}로 고정된다(권한 상승 벡터 차단) — ADMIN은 가입으로 만들 수 없다.
 * 대신 환경변수({@code BOOKTIMER_ADMIN_EMAILS})로 지정한 이메일을 앱 기동 시 한 번 승격한다.
 * 승격 자체는 {@link User#promoteToAdmin()}(멱등)이 책임지고, 여기선 목록을 돌며 적용한다.
 *
 * <p>멱등·안전: 이미 ADMIN이거나(변화 0), 존재하지 않거나(무시), 공백/null(무시)인 항목은 조용히 건너뛴다.
 * 잘못된 설정값 하나로 기동이 깨지지 않게 한다.
 */
@Service
public class AdminAccountService {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountService.class);

    private final UserRepository userRepository;

    public AdminAccountService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * 주어진 이메일 목록의 사용자를 ADMIN으로 승격한다.
     *
     * @param emails 승격 대상 이메일(공백/null/미존재는 무시)
     * @return 이번 호출에서 실제로 USER→ADMIN으로 바뀐 계정 수
     */
    @Transactional
    public int seedAdmins(Collection<String> emails) {
        if (emails == null || emails.isEmpty()) {
            return 0;
        }
        int promoted = 0;
        for (String raw : emails) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String email = raw.strip();
            User user = userRepository.findByEmail(email).orElse(null);
            if (user == null) {
                log.warn("ADMIN 시드: 이메일 '{}' 에 해당하는 사용자가 없어 건너뜀", email);
                continue;
            }
            if (user.promoteToAdmin()) {
                userRepository.save(user);
                promoted++;
                log.info("ADMIN 시드: '{}' 를 ADMIN으로 승격함", email);
            }
        }
        return promoted;
    }
}
