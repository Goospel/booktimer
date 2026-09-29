package com.booktimer.testsupport;

import com.booktimer.auth.TossLoginClient;
import com.booktimer.book.BookSearchClient;
import com.booktimer.book.CoupangDeeplinkClient;
import com.booktimer.book.CoupangLinkBuilder;
import com.booktimer.book.KyoboLinkBuilder;
import com.booktimer.book.Yes24LinkBuilder;
import com.booktimer.email.SignupNotificationService;
import com.booktimer.personality.ReadingPersonalityNarrator;
import com.booktimer.story.StoryRepository;
import com.booktimer.study.ClaudeStudyAssistant;
import com.booktimer.study.GeminiStudyPlanner;
import com.booktimer.study.StudyNoteRepository;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 외부 경계(책 검색·AI·제휴 링크·토스 로그인·가입 통지)를 목으로 바꾼 통합 테스트 세계. MockMvc 포함.
 * 이 애너테이션을 단 테스트 클래스는 스프링 컨텍스트 하나를 공유한다(T-235).
 *
 * <p><b>목은 여기서만 선언한다.</b> 테스트 클래스에 {@code @MockitoBean} 필드를 달면 필드 이름까지 캐시 키에
 * 들어가 컨텍스트가 하나 더 뜨고, build.gradle의 컨텍스트 예산 가드가 빌드를 막는다. 목이 더 필요하면
 * 아래 {@code types}에 추가하고, 클래스에선 {@code @Autowired}로 받아 스텁한다.
 * 이 애너테이션을 단 클래스에 {@code @SpringBootTest}·{@code @AutoConfigureMockMvc}를 따로 달지 않는다 —
 * 클래스에 직접 단 쪽이 이것을 가려 속성·웹 환경이 조용히 바뀔 수 있다.
 *
 * <p>목·스파이는 매 테스트가 끝나면 Spring이 리셋한다 — 스텁이 다음 테스트로 새지 않는다.
 * 목 기본값(false·null·빈 컬렉션·Optional.empty)은 테스트 환경의 실제 빈(키 미설정 = 비활성)과 대부분 같다.
 * 다른 자리: {@code BookSearchClient.search()}는 목이면 null이고 실제 비활성 빈은 빈 페이지다 —
 * 검색을 타는 테스트는 직접 스텁한다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest
@AutoConfigureMockMvc
@MockitoBean(types = {
        BookSearchClient.class,
        CoupangDeeplinkClient.class,
        CoupangLinkBuilder.class,
        Yes24LinkBuilder.class,
        KyoboLinkBuilder.class,
        ReadingPersonalityNarrator.class,
        GeminiStudyPlanner.class,
        ClaudeStudyAssistant.class,
        TossLoginClient.class,
        SignupNotificationService.class
})
@MockitoSpyBean(types = {StoryRepository.class, StudyNoteRepository.class})
public @interface MockedBoundaryTest {
}
