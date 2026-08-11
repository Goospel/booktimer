package com.booktimer.book;

import com.booktimer.config.TossProperties;
import com.booktimer.toss.TossMessengerClient;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 완독 축하 푸시 배선 — <b>누구에게 보내고 누구에게 안 보내는가</b>만 본다(문구는 토스 콘솔 소유).
 *
 * <p>이 테스트가 단독으로 잡는 실패: ① 토스 미연동(웹 전용) 사용자에게 발송을 시도해 매번 에러가 나는 것,
 * ② 템플릿 코드가 아직 없는 다크런치 상태에서 발송을 시도하는 것, ③ 발송 실패가 예외로 새어 완독 처리를
 * 깨뜨리는 것.
 */
class FinishCelebrationServiceTest {

    private TossMessengerClient client;
    private TossProperties properties;

    @BeforeEach
    void setUp() {
        client = mock(TossMessengerClient.class);
        properties = new TossProperties();
        properties.getMessenger().setFinishTemplateCode("FINISH_CELEBRATION");
    }

    private FinishCelebrationService service() {
        return new FinishCelebrationService(Optional.of(client), properties);
    }

    private static User tossUser() {
        User user = User.of("toss@booktimer.com", "pw", "독자", "Asia/Seoul", Role.USER);
        user.linkTossUserKey("uk-1");
        return user;
    }

    private static User webUser() {
        return User.of("web@booktimer.com", "pw", "독자", "Asia/Seoul", Role.USER);
    }

    private static Book book(User user) {
        return Book.register(user, "클린 코드", "로버트 마틴", null, null, null, null, BookStatus.READING);
    }

    @Test
    @DisplayName("토스 연동 사용자: userKey·템플릿코드·책 제목을 실어 1회 발송")
    void celebrate_sendsToLinkedUser() {
        User user = tossUser();

        service().celebrate(user, book(user));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> context = ArgumentCaptor.forClass(Map.class);
        verify(client).sendMessage(org.mockito.ArgumentMatchers.eq("uk-1"),
                org.mockito.ArgumentMatchers.eq("FINISH_CELEBRATION"), context.capture());
        assertThat(context.getValue()).containsEntry("bookTitle", "클린 코드");
    }

    // ⚠️ never() + anyString() 조합은 쓰지 않는다 — Mockito의 anyString()은 null 인자를 매칭하지 않아
    // "userKey=null로 발송을 시도"하는 회귀를 통과시킨다(돌연변이 실측으로 확인). verifyNoInteractions로 본다.

    @Test
    @DisplayName("토스 미연동(웹 전용) 사용자면 발송하지 않는다 — 보낼 곳이 없다")
    void celebrate_skipsWhenNoTossUserKey() {
        User user = webUser();

        service().celebrate(user, book(user));

        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("템플릿 코드 미설정(다크런치)이면 발송하지 않는다")
    void celebrate_skipsWhenTemplateCodeBlank() {
        properties.getMessenger().setFinishTemplateCode("  ");
        User user = tossUser();

        service().celebrate(user, book(user));

        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("게이트 OFF로 클라이언트 빈이 없어도 조용히 넘어간다(예외 없음)")
    void celebrate_noClient_doesNothing() {
        User user = tossUser();
        FinishCelebrationService noClient = new FinishCelebrationService(Optional.empty(), properties);

        assertThatCode(() -> noClient.celebrate(user, book(user))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("발송이 실패(false)하거나 클라이언트가 터져도 예외를 전파하지 않는다 — 완독 처리 보호")
    void celebrate_swallowsFailures() {
        User user = tossUser();
        when(client.sendMessage(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("boom"));

        assertThatCode(() -> service().celebrate(user, book(user))).doesNotThrowAnyException();
    }
}
