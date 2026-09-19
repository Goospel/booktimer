package com.booktimer.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 개인정보처리방침 페이지 테스트 (MockMvc).
 *
 * <p>Google OAuth 동의 화면 게시(Production) 전제이자 개인정보보호법 고지 의무를 위해,
 * <b>인증 없이</b> 접근 가능한 정적 고지 페이지({@code privacy.html})를 서빙하는지 본다.
 * 미인증 접근이 로그인으로 리다이렉트되지 않고 200으로 떠야 한다(공개 링크라야 Google 동의 화면에 걸 수 있음).
 */
@SpringBootTest
@AutoConfigureMockMvc
class PrivacyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /privacy: 인증 없이 200으로 처리방침 뷰를 렌더한다")
    void getPrivacy_isPublicAndRendersView() throws Exception {
        mockMvc.perform(get("/privacy"))
                .andExpect(status().isOk())
                .andExpect(view().name("privacy"));
    }

    @Test
    @DisplayName("GET /privacy: 수집하는 개인정보 항목(이메일)을 고지한다")
    void getPrivacy_disclosesCollectedItems() throws Exception {
        mockMvc.perform(get("/privacy"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("이메일")));
    }

    @Test
    @DisplayName("GET /terms: 인증 없이 200으로 이용약관 뷰를 렌더한다")
    void getTerms_isPublicAndRendersView() throws Exception {
        mockMvc.perform(get("/terms"))
                .andExpect(status().isOk())
                .andExpect(view().name("terms"))
                // 뷰 이름만 보면 템플릿이 깨져도(fragment 참조 오류 등) 통과할 수 있어, 실제 렌더 결과까지 본다.
                .andExpect(content().string(containsString("제1조 (목적)")));
    }

    /** 정책 문서 §1 — 「대화」 조를 제10조로 넣고 문의를 제11조로 민다. 채널톡 제출물과 문구가 같아야 한다. */
    @Test
    @DisplayName("GET /terms: 제10조 대화(맞팔·제재 사다리·자동 7일)와 제11조 문의")
    void getTerms_hasChatArticleBeforeContact() throws Exception {
        mockMvc.perform(get("/terms"))
                .andExpect(content().string(containsString("제10조 (대화)")))
                .andExpect(content().string(containsString("제11조 (문의)")))
                .andExpect(content().string(containsString("서로 팔로우한 이용자 사이")))
                .andExpect(content().string(containsString("경고 → 7일 정지 → 영구 정지")))
                .andExpect(content().string(containsString("대화 기능이 7일간 자동으로 정지")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("제10조 (문의)"))));
    }

    /** 정책 문서 §5 — 처리방침은 사실을 주장하므로 코드의 30일·암호화 방식과 같아야 한다. */
    @Test
    @DisplayName("GET /privacy: 대화 항목·AES-256-GCM·탈퇴 즉시 삭제·차단 종료 30일·신고 건 보존")
    void getPrivacy_disclosesChatData() throws Exception {
        mockMvc.perform(get("/privacy"))
                .andExpect(content().string(containsString("대화 기능을 제공하는 경우")))
                .andExpect(content().string(containsString("AES-256-GCM")))
                .andExpect(content().string(containsString("차단으로 종료된 대화방은 <strong>30일 뒤 삭제</strong>")))
                .andExpect(content().string(containsString("신고 처리가 끝날 때까지 삭제하지 않습니다")))
                // 리뷰 #1169 — 운영자 열람은 신고 시점까지(ChatSafetyService가 chat_last_message_id로 자른다)
                .andExpect(content().string(containsString("신고 시점까지의 대화 기록")))
                // 키는 render-env가 EC2 .env에도 쓰므로 「SSM에만」은 사실보다 강하다
                .andExpect(content().string(containsString("데이터베이스·백업과 분리해")));
    }
}
