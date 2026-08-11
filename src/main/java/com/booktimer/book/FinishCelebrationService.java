package com.booktimer.book;

import com.booktimer.config.TossProperties;
import com.booktimer.toss.TossMessengerClient;
import com.booktimer.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * 완독 축하 푸시 — "읽던 책을 다 읽었다"는 전환 순간에 토스 앱으로 축하 한 통을 보낸다.
 *
 * <p>대상은 <b>토스 계정이 연동된 사용자</b>뿐이다({@code users.toss_user_key}). 웹 전용 사용자는 보낼
 * 채널이 없어 조용히 넘어간다 — 정상 동작이지 오류가 아니다.
 *
 * <p><b>절대 던지지 않는다.</b> 축하는 완독의 곁가지라 어떤 실패도 완독 처리를 막아선 안 된다.
 */
@Service
public class FinishCelebrationService {

    private static final Logger log = LoggerFactory.getLogger(FinishCelebrationService.class);

    /** 게이트 OFF(다크런치)면 빈이 없다 — 그래도 이 서비스와 앱 컨텍스트는 떠야 하므로 Optional 주입. */
    private final Optional<TossMessengerClient> client;
    private final TossProperties properties;

    public FinishCelebrationService(Optional<TossMessengerClient> client, TossProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    /**
     * 완독 축하 푸시를 보낸다(보낼 수 없으면 조용히 넘어간다).
     *
     * <p>ponytail: 호출자의 트랜잭션 안에서 동기 HTTP를 친다 — 발송 후 롤백되면 "푸시는 갔는데 완독은
     * 취소"가 이론상 가능하다(축하 문구뿐이라 무해). 타임아웃은 클라이언트에서 연결 2s/응답 3s로 잡아
     * 완독 응답 지연을 제한한다. 문제가 되면 {@code @TransactionalEventListener(AFTER_COMMIT)}로 승격.
     */
    public void celebrate(User user, Book book) {
        String userKey = user.getTossUserKey();
        if (userKey == null) {
            return; // 웹 전용 사용자 — 토스 채널 없음
        }
        String templateCode = properties.getMessenger().getFinishTemplateCode();
        if (templateCode == null || templateCode.isBlank()) {
            return; // 콘솔 검수 전 다크런치
        }
        if (client.isEmpty()) {
            return; // 게이트 OFF
        }
        try {
            client.get().sendMessage(userKey, templateCode.strip(), Map.of("bookTitle", book.getTitle()));
        } catch (Exception e) {
            // 클라이언트는 던지지 않기로 돼 있지만, 그 계약이 깨져도 완독은 살아야 한다.
            log.warn("완독 축하 푸시 실패 (bookId={}): {}", book.getId(), e.toString());
        }
    }
}
