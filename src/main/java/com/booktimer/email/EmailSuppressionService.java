package com.booktimer.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Locale;

/**
 * 이메일 억제 목록 관리 — SES 반송(bounce)·불만(complaint) 피드백으로 얻은 나쁜 주소를 앱 레벨에서
 * 억제하고, 발송 전 조회에 쓴다(발신 평판 보호). AWS SES 프로덕션 액세스 승인문이 요구한
 * "bounce/complaint 처리 프로세스"의 앱 측 구현.
 *
 * <p>SES 계정 레벨 suppression과 별개로, 앱이 억제된 주소를 미리 알고 발송을 건너뛴다
 * ({@link SuppressionAwareEmailSender}). 이메일은 정규화(trim+소문자)해 저장·조회하고, email UNIQUE +
 * 존재 검사로 멱등을 보장한다(같은 주소 반복 억제는 no-op).
 */
@Service
public class EmailSuppressionService {

    private static final Logger log = LoggerFactory.getLogger(EmailSuppressionService.class);

    private final EmailSuppressionRepository repository;
    private final Clock clock;

    public EmailSuppressionService(EmailSuppressionRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * 주소를 억제 목록에 넣는다(멱등). 이미 억제됐거나 빈 주소면 아무 것도 하지 않는다.
     *
     * @param email  수신자 이메일(정규화 전 원문)
     * @param reason 억제 사유
     * @param detail SES가 준 상세(예: "Permanent/General") — 진단용, null 허용
     */
    @Transactional
    public void suppress(String email, SuppressionReason reason, String detail) {
        String normalized = normalize(email);
        if (normalized.isEmpty() || repository.existsByEmail(normalized)) {
            return;
        }
        repository.save(new EmailSuppression(normalized, reason, detail, clock.instant()));
        log.info("이메일 억제 등록 — reason={}, email={}", reason, normalized);
    }

    /** 이 주소가 억제되어 발송을 건너뛰어야 하는가. */
    @Transactional(readOnly = true)
    public boolean isSuppressed(String email) {
        String normalized = normalize(email);
        return !normalized.isEmpty() && repository.existsByEmail(normalized);
    }

    /** 발송·저장·조회에서 동일하게 쓰는 이메일 정규화(trim + 소문자). */
    static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
