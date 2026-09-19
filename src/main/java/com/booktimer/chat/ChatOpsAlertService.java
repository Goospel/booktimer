package com.booktimer.chat;

import com.booktimer.config.TossProperties;
import com.booktimer.toss.TossMessengerClient;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 대화방 신고 → 운영자 토스 푸시(정책 문서 §2 「알림」). 받는 사람은 <b>토스에 연결된 ADMIN</b>뿐이다.
 *
 * <p>게이트는 DM 푸시와 같다: 메신저 클라이언트 빈 · 캠페인 토글({@code ops-alert-enabled}) · 템플릿 코드. 하나라도
 * 없으면 운영자를 찾지도 않고 끝난다(기본값 = 다크). 템플릿은 콘솔 등록 후 수정·삭제가 안 돼 사용자 몫이다.
 * 발송은 <b>커밋 뒤</b>다 — 롤백된 신고로 운영자를 깨우지 않고, 토스 호출이 신고 트랜잭션을 붙잡지 않는다.
 * 관리자 배너({@code /admin})는 이 게이트와 무관하게 늘 뜬다.
 */
@Service
public class ChatOpsAlertService {

    private final Optional<TossMessengerClient> client;
    private final TossProperties properties;
    private final UserRepository userRepository;

    public ChatOpsAlertService(Optional<TossMessengerClient> client, TossProperties properties,
                               UserRepository userRepository) {
        this.client = client;
        this.properties = properties;
        this.userRepository = userRepository;
    }

    /** 신고 트랜잭션 안에서 부른다. 수신자 userKey를 지금 읽어 두고, 발송은 커밋 뒤로 미룬다. */
    public void notifyNewChatReport() {
        TossProperties.Messenger m = properties.getMessenger();
        String code = m.getOpsAlertTemplateCode();
        if (!m.isOpsAlertEnabled() || code == null || code.isBlank() || client.isEmpty()) {
            return;
        }
        List<String> keys = userRepository.findByRoleAndTossUserKeyIsNotNull(Role.ADMIN).stream()
                .map(User::getTossUserKey)
                .toList();
        if (keys.isEmpty() || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        String template = code.strip();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                keys.forEach(k -> client.get().sendMessage(k, template, Map.of())); // 클라이언트는 던지지 않는다
            }
        });
    }
}
