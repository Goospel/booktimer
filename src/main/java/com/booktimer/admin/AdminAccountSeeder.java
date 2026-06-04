package com.booktimer.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 기동 시 1회 — 환경변수로 지정한 이메일을 ADMIN으로 승격한다.
 *
 * <p>{@code BOOKTIMER_ADMIN_EMAILS}(쉼표 구분, relaxed binding → {@code booktimer.admin.emails})를
 * 읽어 {@link AdminAccountService#seedAdmins}에 넘긴다. 이메일은 <b>repo에 커밋하지 않고</b> 환경변수로만
 * 주입한다(공개 저장소라 운영자 식별정보 노출 방지). 미설정이면 빈 목록 → 무동작.
 *
 * <p>승격 로직(멱등·미존재 무시)은 서비스가 담당하므로 이 러너는 배선만 한다.
 */
@Component
public class AdminAccountSeeder implements ApplicationRunner {

    private final AdminAccountService adminAccountService;
    private final List<String> adminEmails;

    public AdminAccountSeeder(AdminAccountService adminAccountService,
                              @Value("${booktimer.admin.emails:}") List<String> adminEmails) {
        this.adminAccountService = adminAccountService;
        this.adminEmails = adminEmails;
    }

    @Override
    public void run(ApplicationArguments args) {
        adminAccountService.seedAdmins(adminEmails);
    }
}
