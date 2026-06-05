package com.booktimer.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 기동 시 1회 — 환경변수로 지정한 <b>login_id</b>를 ADMIN으로 승격한다.
 *
 * <p>{@code BOOKTIMER_ADMIN_LOGIN_IDS}(쉼표 구분, relaxed binding → {@code booktimer.admin.login-ids})를
 * 읽어 {@link AdminAccountService#seedAdmins}에 넘긴다. 인증 컷오버(PR-4)로 식별자가 login_id가 됐으므로
 * 시드도 login_id 기준이다. 값은 <b>repo에 커밋하지 않고</b> 환경변수로만 주입한다. 미설정이면 빈 목록 → 무동작.
 *
 * <p>승격 로직(멱등·미존재 무시)은 서비스가 담당하므로 이 러너는 배선만 한다.
 */
@Component
public class AdminAccountSeeder implements ApplicationRunner {

    private final AdminAccountService adminAccountService;
    private final List<String> adminLoginIds;

    public AdminAccountSeeder(AdminAccountService adminAccountService,
                              @Value("${booktimer.admin.login-ids:}") List<String> adminLoginIds) {
        this.adminAccountService = adminAccountService;
        this.adminLoginIds = adminLoginIds;
    }

    @Override
    public void run(ApplicationArguments args) {
        adminAccountService.seedAdmins(adminLoginIds);
    }
}
