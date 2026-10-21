package com.booktimer.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
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
    @DisplayName("GET /terms: 제10조 대화(맞팔·제재 사다리·자동 7일)와 제12조 문의")
    void getTerms_hasChatArticleBeforeContact() throws Exception {
        mockMvc.perform(get("/terms"))
                .andExpect(content().string(containsString("제10조 (대화)")))
                // PR-K — 제11조에 「생성형 AI 기능」이 들어와 문의는 제12조로 밀렸다
                .andExpect(content().string(containsString("제12조 (문의)")))
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

    /** 처리방침은 사실을 주장한다 — 문구가 분류 규칙(User#canSignInWithoutToss)의 술어를 그대로 옮겨야 한다. */
    @Test
    @DisplayName("REQ-10 · GET /privacy: 토스 연결 끊기 때 미인증 토스 가입 계정 파기·그 밖의 계정 연결 해제를 고지한다")
    void getPrivacy_disclosesTossUnlinkHandling() throws Exception {
        mockMvc.perform(get("/privacy"))
                .andExpect(content().string(containsString("토스 로그인 연결을 끊은 경우")))
                .andExpect(content().string(containsString("토스 미니앱에서 가입하고 이메일 인증을 하지 않은 계정은")))
                .andExpect(content().string(containsString("회원 탈퇴와 같이 계정과 모든 기록을 파기")))
                .andExpect(content().string(containsString("토스 사용자 식별자와 미니앱 로그인 정보를 삭제")))
                .andExpect(content().string(containsString("웹에 로그인할 수 있는 계정은 웹 설정에서 다시 연결할 수 있습니다")));
    }

    @Test
    @DisplayName("REQ-10 · GET /terms: 제3조가 토스 연결 끊기의 결과를 알린다")
    void getTerms_disclosesTossUnlinkResult() throws Exception {
        mockMvc.perform(get("/terms"))
                .andExpect(content().string(containsString("토스 미니앱에서 가입하고 이메일 인증을 하지 않은 계정은 삭제되고")));
    }

    @Test
    @DisplayName("REQ-15 · GET /privacy: 독서 알림 설정 항목과 이용 목적을 고지한다")
    void getPrivacy_disclosesReadingReminder() throws Exception {
        mockMvc.perform(get("/privacy"))
                .andExpect(content().string(containsString("독서 알림을 설정한 경우에만")))
                .andExpect(content().string(containsString("이용자가 직접 켠 독서 알림")));
    }

    /**
     * 처리방침은 사실을 주장한다 — 2절 열거는 {@code study_*} 열(V78~V93, V92가 지운 하루 목표는 뺀다)과,
     * 6-1의 필기 항목은 {@code NoteReference.text}의 헤더(제목·고친 날짜)·본문과 같아야 한다.
     * 이 전송은 2026-09-11(#1087)부터 일어났는데 처리방침엔 「필기」가 없었다.
     */
    @Test
    @DisplayName("REQ-10 · 처리방침 바로잡기 — GET /privacy: 공부 기록 수집 항목·이용 목적과, AI 분석에 같은 책의 공부 필기(제목·본문·날짜)가 함께 간다는 사실을 고지한다")
    void getPrivacy_disclosesStudyRecordsAndNotesSentToAnalysis() throws Exception {
        mockMvc.perform(get("/privacy"))
                // 2절 수집 항목
                .andExpect(content().string(containsString("<li><strong>공부 기록</strong> — 「공부」 화면을 쓰는 경우에만")))
                .andExpect(content().string(containsString("측정한 공부 시간(책별 회당 목표 시간과 목표 도달 알림을 보낸 시각 포함)")))
                .andExpect(content().string(containsString("공부 서재에 담은 책(직접 적은 링크 주소 포함)")))
                .andExpect(content().string(containsString("<strong>공부 필기</strong>(제목·본문)")))
                // 3절 이용 목적
                .andExpect(content().string(containsString("<li>「공부」 화면의 공부 시간 측정·일정·백지복습·공부 필기")))
                // 2절의 「목표 도달 알림을 보낸 시각」과 짝이 되는 목적(StudyGoalPushService)
                .andExpect(content().string(containsString("책별 회당 목표 시간에 닿았을 때의 토스 알림 발송")))
                // 6-1 이전되는 항목
                .andExpect(content().string(containsString("분석하는 글과 같은 책의 <strong>공부 필기</strong>(제목·본문·마지막으로 고친 날짜")));
    }

    /**
     * 필기 사진(V99 study_note_image) — 수집·보관·삭제와 국외 이전 여부가 코드와 같은 말이어야 한다.
     * 「3일 안에」는 {@code StudyNoteService.ORPHAN_GRACE}·04:50 정리 크론과 짝이고, 「표시만 들어간다」는
     * {@code NoteReference.IMAGE_PLACEHOLDER}와 짝이다. 옛 무범위 문장 「사진은 서비스 서버에 저장하지 않습니다」는
     * 필기 사진이 생긴 뒤로 거짓이라 사진 전사로 한정한다.
     * 「탈퇴 즉시」를 {@code <strong>즉시 삭제</strong>} 홀로 재지 않는 이유 — 대화 데이터 줄에 이미 있어 구현 전에도 참이다.
     */
    @Test
    @DisplayName("REQ-23 · GET /privacy: 필기 사진 수집·3일 안 삭제·탈퇴 즉시, AI 분석엔 사진 대신 자리 표시, 무저장 문장은 사진 전사로 한정")
    void getPrivacy_disclosesNoteImages() throws Exception {
        mockMvc.perform(get("/privacy"))
                // 2절 수집 항목
                .andExpect(content().string(containsString("<strong>필기 사진</strong>(화면에서 넣은 사진은 브라우저가 줄여 올립니다")))
                // 3절 이용 목적
                .andExpect(content().string(containsString("공부 필기(필기 사진 포함)")))
                // 5절 보관·삭제
                .andExpect(content().string(containsString("<strong>3일 안에 삭제</strong>")))
                .andExpect(content().string(containsString("회원 탈퇴 시에는 <strong>즉시 삭제</strong>합니다")))
                // 5절 파기 절차 — 백업 보존 7일은 S3 lifecycle(expire-mysql-backups-7d, mysql/ 접두)로 확인한 값(설계 U-16)
                .andExpect(content().string(containsString("<strong>백업 보관 주기(7일)가 지나면서 함께 소멸</strong>")))
                // 6-1 이전되는 항목·보유 기간
                .andExpect(content().string(containsString("그 자리에는 사진이 있었다는 표시만 들어갑니다")))
                .andExpect(content().string(containsString("<strong>사진 전사로 올린 사진은 서비스 서버에 저장하지 않습니다</strong>")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        containsString("<strong>사진은 서비스 서버에 저장하지 않습니다</strong>"))));
    }

    /**
     * 시행일 E — PR-K 머지일 = 개정 공지일(2026-10-21) + 33일(KST, 앞당김 설계 REQ-09 = v1 REQ-74 — 31일 이상).
     * 공지일을 다시 바꾸면 두 문서의 「개정 공지」 날짜를 실제 머지일(KST)로 고치고, E − 머지일 &lt; 31이면 이 값과
     * 두 문서의 E도 다시 센다 — {@code legalPages_noticeDateAndEffectiveDateAgree}가 놓친 자리를 잡는다.
     * PR-B의 {@code AI_NOTICE_EFFECTIVE}도 이 날이다.
     */
    private static final String EFFECTIVE = "2026-11-23";

    /** 앱 글쓰기 질문이 보내는 항목 — v1 §5.1 {@code SentItem.privacyLabel} 다섯 개(PR-C의 REQ-43 가드가 코드 쪽에서 다시 잰다). */
    private static final List<String> SENT_ITEM_LABELS = List.of(
            "책의 제목·저자", "여백(옮긴 문장·메모·작성일)", "지금 쓰는 글", "지금 답하는 질문", "이 글에서 앞서 받은 질문");

    private String render(String path) throws Exception {
        return mockMvc.perform(get(path)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    /** start부터 end 앞까지. 어느 한쪽이 없으면 빈 문자열이라 뒤따르는 contains 단언이 Red가 된다. */
    private static String between(String s, String start, String end) {
        int from = s.indexOf(start);
        if (from < 0) return "";
        int to = s.indexOf(end, from + start.length());
        return to < 0 ? "" : s.substring(from, to);
    }

    @Test
    @DisplayName("R-13 · GET /privacy: 6-3절이 앱 글쓰기 질문의 국외 이전을 다섯 항목·「그 밖의 이용 기록은 보내지 않습니다」·시행일 한 줄로 고지한다")
    void getPrivacy_section63DisclosesAppWritingQuestions() throws Exception {
        String s63 = between(render("/privacy"), "<h2>6-3. 개인정보의 국외 이전 (앱 글쓰기 질문)</h2>", "<h2>7.");
        assertThat(s63).isNotEmpty();
        for (String label : SENT_ITEM_LABELS) assertThat(s63).as(label).contains(label);
        assertThat(s63).contains("그 밖의 이용 기록은 보내지 않습니다")
                .contains("Anthropic PBC").contains("privacy@anthropic.com").contains("미국")
                .contains("「질문 받기」").contains("「꼬리 질문」").contains("30일 이내에 자동 삭제")
                // PR-B의 REQ-74 테스트가 읽는 꼴 — 절 안에 정확히 한 번
                .contains("시행: " + EFFECTIVE);
        assertThat(s63.split("시행: ", -1)).hasSize(2);
    }

    @Test
    @DisplayName("R-13 · GET /privacy: 2절 Apple 로그인 식별자(즉시) · 5절 앱 글 보관(시행일) · 8절 위탁 표와 국외 이전 목록 · 개정 이력")
    void getPrivacy_appleIdentifierAppWritingRetentionAndNotice() throws Exception {
        String html = render("/privacy");
        assertThat(between(html, "<h2>2.", "<h2>3.")).contains("<strong>Apple 로그인 식별자</strong>");
        assertThat(between(html, "<h2>5.", "<h2>6.")).contains("앱에서 쓴 글과 받은 질문").contains(EFFECTIVE + " 시행");
        assertThat(between(html, "<h2>8.", "<h2>9."))
                .contains("Apple Inc.").contains("앱 글쓰기 질문").contains("<strong>6-3절</strong>");
        assertThat(html).contains("6-3절 신설").contains(EFFECTIVE + " 시행");
    }

    @Test
    @DisplayName("R-13 · GET /privacy: 5절·6-3이 보관·이전을 말하는 여백과 앱 글쓰기를 2절이 수집 항목으로, 3절이 이용 목적으로 고지한다")
    void getPrivacy_collectsMarginsAndAppWriting() throws Exception {
        String html = render("/privacy");
        assertThat(between(html, "<h2>2.", "<h2>3."))
                .contains("<strong>여백</strong>").contains("<strong>앱 글쓰기</strong>")
                .contains("받은 AI 질문").contains("AI 질문 동의 여부와 그 시각").contains("(" + EFFECTIVE + " 시행)");
        assertThat(between(html, "<h2>3.", "<h2>4.")).contains("아이폰 앱을 쓰는 경우 글쓰기").contains("AI 질문 생성")
                .contains("(" + EFFECTIVE + "부터)");
    }

    private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern NOTICE = Pattern.compile("(\\d{4}-\\d{2}-\\d{2}) 개정 공지");
    private static final Pattern KOREAN_DATE = Pattern.compile("\\d+월 \\d+일");

    /**
     * 머지일이 밀려 E를 다시 셀 때 ISO 날짜(YYYY-MM-DD) 한 자리라도 놓치면 Red. 두 문서의 개정 공지일이 하나로 같고,
     * 공지일과 E 사이가 31일 이상이며(13절 「시행일 30일 전부터」), 공지일보다 뒤의 날짜는 전부 E다.
     * 「N월 N일」 꼴은 이 검사가 못 보므로 두 문서에 0건임을 함께 못 박는다.
     */
    @Test
    @DisplayName("R-17 · 처리방침·약관: 개정 공지일이 같고 E까지 31일 이상이며, 공지일 뒤의 날짜는 전부 시행일 E다")
    void legalPages_noticeDateAndEffectiveDateAgree() throws Exception {
        LocalDate effective = LocalDate.parse(EFFECTIVE);
        List<LocalDate> notices = new ArrayList<>();
        for (String path : List.of("/privacy", "/terms")) {
            String html = render(path);
            Matcher n = NOTICE.matcher(html);
            assertThat(n.find()).as(path + " 개정 공지일").isTrue();
            LocalDate notice = LocalDate.parse(n.group(1));
            assertThat(n.find()).as(path + " 개정 공지일은 하나").isFalse();
            notices.add(notice);
            assertThat(KOREAN_DATE.matcher(html).find()).as(path + " 「N월 N일」 꼴 날짜").isFalse();
            Matcher d = DATE.matcher(html);
            while (d.find()) {
                LocalDate date = LocalDate.parse(d.group());
                if (date.isAfter(notice)) assertThat(date).as(path + " 공지일 뒤 날짜").isEqualTo(effective);
            }
        }
        assertThat(notices.get(1)).as("두 문서의 개정 공지일").isEqualTo(notices.get(0));
        assertThat(ChronoUnit.DAYS.between(notices.get(0), effective)).as("E − 공지일").isGreaterThanOrEqualTo(31);
    }

    @Test
    @DisplayName("R-11 · GET /terms: 제11조 생성형 AI 기능 — 사전 고지·질문만·글은 이용자의 것·시행일, AI의 일로 구성·교정·다듬을 적지 않는다")
    void getTerms_generativeAiArticle() throws Exception {
        String html = render("/terms");
        String article = between(html, "<h2>제11조 (생성형 AI 기능)</h2>", "<h2>제12조");
        assertThat(article).contains("생성형 AI").contains("질문만").contains("이용자의 것").contains(EFFECTIVE + " 시행")
                .doesNotContain("구성").doesNotContain("교정").doesNotContain("다듬");
        assertThat(html).contains("제11조 생성형 AI 기능 신설");
    }

    /** 음성 판정 전용 — 지금도 초록이다. v1 설계의 예시 날짜를 문안에 베껴 넣는 회귀만 잡는다(REQ-74 수동 확인의 자동화). */
    @Test
    @DisplayName("REQ-74 · 처리방침·약관에 v1 예시 날짜 리터럴(2027-01-07 · 2026-12-08 · 1월 7일)이 없다")
    void legalPages_haveNoExampleDateLiterals() throws Exception {
        for (String path : List.of("/privacy", "/terms")) {
            assertThat(render(path)).as(path)
                    .doesNotContain("2027-01-07").doesNotContain("2026-12-08").doesNotContain("1월 7일");
        }
    }
}
