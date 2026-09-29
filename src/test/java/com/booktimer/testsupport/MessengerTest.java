package com.booktimer.testsupport;

import com.booktimer.toss.TossMessengerClient;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 토스 메신저(푸시) 테스트 세계 — 메신저 클라이언트 목 + 가변 시계({@link MutableClock}) + 캠페인 속성 7줄.
 * 이 애너테이션을 단 테스트 클래스는 스프링 컨텍스트 하나를 공유한다(T-235).
 *
 * <p>켜는 것 = 완독·목표·공부 템플릿 코드 + DM·운영 알림(스위치와 템플릿). 끄는 것 = 스케줄러 점등
 * ({@code goal-met-enabled}·{@code study-goal-enabled}·N3 독서 알림 {@code reading-reminder-enabled}) —
 * 켜면 공유 컨텍스트에서 배치가 돈다. 템플릿 코드 값은 옛 클래스별 속성 그대로다 — 검증 문자열이 안 바뀐다.
 *
 * <p>한 흐름이 다른 캠페인 푸시를 함께 낼 수 있다(예: {@code ChatOpsAlertTest}의 방 준비 send가 {@code DM_TEST}).
 * 그래서 검증은 템플릿 코드까지 좁힌다({@code eq("DM_TEST")} 등) — {@code anyString()}으로 세면 섞인다.
 * 속성을 런타임에 바꾸는 테스트(목표·공부 푸시의 다크런치)는 {@code finally}로 되돌린다 — 안 되돌리면 세계 전체에 남는다.
 *
 * <p>{@code @MockitoBean} 필드·{@code @TestPropertySource}·{@code @SpringBootTest}를 클래스에 새로 달지 않는다 —
 * 필드 이름·속성 문자열이 캐시 키에 들어가 세계가 갈라지고, build.gradle의 컨텍스트 예산 가드가 빌드를 막는다.
 * 메신저 목은 {@code @Autowired TossMessengerClient}, 시계는 {@code @Autowired MutableClock}으로 받는다.
 *
 * <p>매 테스트 뒤 {@link MutableClock.ResetExtension}이 시계를 시스템 시각으로 되돌리고 {@code RateLimitService}의
 * 창을 비운다 — 이 세계의 클래스는 다른 세계처럼 {@code clearForTest()}를 따로 부르지 않는다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest(properties = {
        "booktimer.toss.messenger.finish-template-code=FINISH_CELEBRATION",
        "booktimer.toss.messenger.goal-met-template-code=DAILY_GOAL_MET",
        "booktimer.toss.messenger.study-goal-template-code=STUDY_GOAL_MET",
        "booktimer.toss.messenger.dm-message-enabled=true",
        "booktimer.toss.messenger.dm-message-template-code=DM_TEST",
        "booktimer.toss.messenger.ops-alert-enabled=true",
        "booktimer.toss.messenger.ops-alert-template-code=OPS_TEST"
})
@MockitoBean(types = TossMessengerClient.class)
@Import(MutableClock.Config.class)
@ExtendWith(MutableClock.ResetExtension.class)
public @interface MessengerTest {
}
