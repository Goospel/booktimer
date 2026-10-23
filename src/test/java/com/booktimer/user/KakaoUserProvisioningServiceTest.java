package com.booktimer.user;

import com.booktimer.testsupport.MockedBoundaryTest;
import com.booktimer.timer.ReadingTimerRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 아이폰 앱 카카오 신원 프로비저닝(REQ-81) — 조회(login)는 만들지 않고, 신규(register)는 합성 이메일 계정을 한 번만 만든다.
 * 카카오 이메일·닉네임은 받지도 쓰지도 않는다 — 신원은 회원번호(sub) 하나다. 실 H2.
 */
@MockedBoundaryTest
@Transactional
class KakaoUserProvisioningServiceTest {

    private static final String SUB = "4012345678";

    @Autowired KakaoUserProvisioningService kakaoUsers;
    @Autowired UserRepository userRepository;
    @Autowired ReadingTimerRepository timerRepository;

    @Test
    @DisplayName("REQ-81 · 카카오 LOGIN은 모르는 회원번호면 빈 값이고 계정을 만들지 않는다")
    void loginUnknownCreatesNothing() {
        long before = userRepository.count();
        assertThat(kakaoUsers.login(SUB)).isEmpty();
        assertThat(userRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("REQ-81 · 카카오 REGISTER는 합성 이메일 계정을 한 번만 만든다")
    void registerCreatesSyntheticAccountOnce() {
        long before = userRepository.count();

        User created = kakaoUsers.register(SUB);
        User again = kakaoUsers.register(SUB);

        assertThat(again.getId()).isEqualTo(created.getId());
        assertThat(userRepository.count()).isEqualTo(before + 1);
        User row = userRepository.findByKakaoSub(SUB).orElseThrow();
        assertThat(row.getEmail()).isEqualTo("kakao-" + SUB + User.SYNTHETIC_EMAIL_DOMAIN);
        assertThat(row.hasSyntheticEmail()).isTrue();
        assertThat(row.getAuthProvider()).isEqualTo(AuthProvider.KAKAO);
        assertThat(row.getNickname()).isEqualTo("독자");
        assertThat(row.getTimezone()).isEqualTo("Asia/Seoul");
        assertThat(row.isEmailVerified()).isFalse();
        assertThat(timerRepository.findByUser(row)).isPresent();
        assertThat(kakaoUsers.login(SUB)).map(User::getId).contains(created.getId()); // 양성 대조 — 만든 뒤엔 조회된다
    }
}
