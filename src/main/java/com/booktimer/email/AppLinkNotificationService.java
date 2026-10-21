package com.booktimer.email;

import com.booktimer.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 아이폰 앱 연결 알림(v1 REQ-42) — 웹 계정에 Apple ID가 붙으면 그 계정의 <b>검증된 실제 이메일</b>로 한 통을 보낸다.
 *
 * <p>연결 코드가 피싱·어깨너머로 새면 공격자의 Apple ID가 피해자 계정에 붙는다. 이 메일이 「모르고 지나감」을 막고,
 * 본문이 해제 자리(웹 설정 > 아이폰 앱 연결)를 가리킨다. 합성·미검증 주소엔 보내지 않는다 — 보낼 데가 없거나 남의 주소일 수 있다.
 * 발송 실패는 {@link EmailDispatcher}가 비동기로 격리한다(연결은 되돌리지 않는다).
 */
@Service
public class AppLinkNotificationService {

    static final String SUBJECT = "북타이머 — 아이폰 앱 「책생글」이 이 계정에 연결됐어요";
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final EmailDispatcher emailDispatcher;
    private final String baseUrl;
    private final Clock clock;

    public AppLinkNotificationService(EmailDispatcher emailDispatcher,
                                      @Value("${booktimer.base-url:http://localhost:8080}") String baseUrl,
                                      Clock clock) {
        this.emailDispatcher = emailDispatcher;
        this.baseUrl = baseUrl;
        this.clock = clock;
    }

    public void notifyLinked(User owner) {
        if (!owner.isEmailVerified() || owner.hasSyntheticEmail()) {
            return;
        }
        String when = WHEN.format(clock.instant().atZone(SEOUL));
        String body = """
                <p>%s님, 안녕하세요.</p>
                <p>%s(한국 시간)에 아이폰 앱 「책생글」이 이 북타이머 계정에 연결됐어요. 이제 그 iPhone에서 이 계정의 책과 여백을 열 수 있어요.</p>
                <p><strong>본인이 아니면</strong> 지금 웹 설정 &gt; 아이폰 앱 연결에서 연결을 해제하세요. 해제하면 그 기기는 바로 로그아웃돼요.</p>
                <p><a href="%s/settings#app-link">아이폰 앱 연결 설정 열기</a></p>
                """.formatted(escape(owner.getNickname()), when, baseUrl);
        emailDispatcher.dispatch(owner.getEmail(), SUBJECT, body);
    }

    /** 닉네임은 사용자 입력이라 HTML 본문에 넣기 전에 이스케이프한다({@link SignupNotificationService}와 같은 최소 규칙). */
    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
