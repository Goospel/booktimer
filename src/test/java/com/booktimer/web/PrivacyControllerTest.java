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
    @DisplayName("GET /terms: 제10조 대화(맞팔·제재 사다리·자동 7일)와 제13조 문의")
    void getTerms_hasChatArticleBeforeContact() throws Exception {
        mockMvc.perform(get("/terms"))
                .andExpect(content().string(containsString("제10조 (대화)")))
                // PR-K — 제11조에 「생성형 AI 기능」이 들어와 문의는 제12조로, K2 — 제12조에 「블로그와 게시물」이 들어와 제13조로 밀렸다
                .andExpect(content().string(containsString("제13조 (문의)")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("제12조 (문의)"))))
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

    /** PR-K 개정 공지일(KST) — 시행일 {@link #EFFECTIVE}의 짝. */
    private static final String PRK_NOTICE = "2026-10-21";

    /**
     * 책생글 K2 개정 공지일(KST) = K2 머지 예정일. 머지가 이 날을 넘기면 두 문서의 「개정 공지」 날짜와 이 값을 실제 머지일로 고치고,
     * E2 − 이 값 &lt; 31이면 E2({@code WritingService.AI_EXPANDED_EFFECTIVE})와 두 문서의 E2 자리도 다시 센다.
     */
    private static final String K2_NOTICE = "2026-10-30";

    /** 넓어진 AI · 블로그 시행일 — 한 곳({@code WritingService.AI_EXPANDED_EFFECTIVE})에서 읽는다(WritingNoticeDateTest가 두 문서와 대조). */
    private static final String E2 = com.booktimer.writing.WritingService.AI_EXPANDED_EFFECTIVE.toString();

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
        // 끝은 6-4절 머리 — 6-5절(K2)의 「시행: E2」를 6-3절로 세지 않는다
        String s63 = between(render("/privacy"), "<h2>6-3. 개인정보의 국외 이전 (앱 글쓰기 질문)</h2>", "<h2>6-4.");
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
    /** PR-M 즉시 시행 개정일(KST) — 머지일이 바뀌면 두 문서의 그 날짜 자리와 함께 고친다. */
    private static final String IMMEDIATE_REVISION = "2026-10-23";
    private static final Pattern KOREAN_DATE = Pattern.compile("\\d+월 \\d+일");

    /**
     * 머지일이 밀려 E · E2를 다시 셀 때 ISO 날짜(YYYY-MM-DD) 한 자리라도 놓치면 Red. 두 문서의 개정 공지일이 둘(PR-K · K2)로 같고,
     * 공지일마다 짝 시행일까지 31일 이상이며(13절 「시행일 30일 전부터」), 첫 공지일보다 뒤의 날짜는 전부 E · E2 · K2 공지일이다.
     * 「N월 N일」 꼴은 이 검사가 못 보므로 두 문서에 0건임을 함께 못 박는다.
     *
     * <p>예외 하나 — PR-M의 즉시 시행 개정일({@link #IMMEDIATE_REVISION}, 「YYYY-MM-DD 개정 —」 줄과 본문의 「… 이후 처음 로그인한
     * 구글 계정」)은 공지일 뒤지만 시행일이 아니다. 그 날짜는 두 문서에 「개정 —」 줄로 하나씩 있어야만 허용한다. 머지일이 바뀌면 두 문서의
     * 그 날짜 자리(privacy 2절 · 개정 이력, terms 제3조 ② · 개정 이력)와 이 상수를 함께 고친다.
     */
    @Test
    @DisplayName("R-17 · REQ-200 · 처리방침·약관: 개정 공지일이 PR-K · K2 둘로 같고 각 시행일(E · E2)까지 31일 이상이며, 첫 공지일 뒤의 날짜는 전부 E · E2 · K2 공지일이다(PR-M 즉시 시행 개정일 하나만 예외)")
    void legalPages_noticeDateAndEffectiveDateAgree() throws Exception {
        LocalDate immediate = LocalDate.parse(IMMEDIATE_REVISION);
        LocalDate prk = LocalDate.parse(PRK_NOTICE);
        LocalDate k2 = LocalDate.parse(K2_NOTICE);
        LocalDate effective = LocalDate.parse(EFFECTIVE);
        LocalDate e2 = LocalDate.parse(E2);
        for (String path : List.of("/privacy", "/terms")) {
            String html = render(path);
            List<LocalDate> notices = new ArrayList<>();
            Matcher n = NOTICE.matcher(html);
            while (n.find()) notices.add(LocalDate.parse(n.group(1)));
            assertThat(notices).as(path + " 개정 공지일").containsExactlyInAnyOrder(prk, k2);
            assertThat(KOREAN_DATE.matcher(html).find()).as(path + " 「N월 N일」 꼴 날짜").isFalse();
            assertThat(html.split(IMMEDIATE_REVISION + " 개정 —", -1)).as(path + " 즉시 시행 개정 줄은 하나").hasSize(2);
            Matcher d = DATE.matcher(html);
            while (d.find()) {
                LocalDate date = LocalDate.parse(d.group());
                if (date.isAfter(prk)) assertThat(date).as(path + " 공지일 뒤 날짜").isIn(effective, e2, k2, immediate);
            }
        }
        assertThat(ChronoUnit.DAYS.between(prk, effective)).as("E − PR-K 공지일").isGreaterThanOrEqualTo(31);
        assertThat(ChronoUnit.DAYS.between(k2, e2)).as("E2 − K2 공지일").isGreaterThanOrEqualTo(31);
    }

    @Test
    @DisplayName("REQ-200 · REQ-202 · GET /privacy: 6-5절(블로그 글쓰기 AI)이 시행일 E2 한 줄 · Anthropic · 웹 검색 하위 처리자 셋 · 본문을 대신 쓰지 않음을 고지하고, 6-3절은 E2부터의 여백 AI 설명을, 8절은 6-5절을 더한다")
    void getPrivacy_section65AndExpandedSection63() throws Exception {
        String html = render("/privacy");
        String s65 = between(html, "<h2>6-5.", "<h2>7.");
        assertThat(s65).contains("시행: " + E2).contains("Anthropic PBC").contains("privacy@anthropic.com").contains("미국")
                .contains("Brave Search").contains("TurboPuffer").contains("Parallel Web Systems, Inc.")
                .contains("대신 쓰지 않").contains("그 밖의 이용 기록은 보내지 않습니다").contains("30일 이내에 자동 삭제");
        assertThat(s65.split("시행: ", -1)).as("6-5절 시행일은 한 줄").hasSize(2);
        assertThat(between(html, "<h2>6-3.", "<h2>6-4.")).contains(E2 + "부터").contains("「AI에게 묻기」").contains("대신 쓰지 않");
        assertThat(between(html, "<h2>8.", "<h2>9.")).contains("<strong>6-5절</strong>").contains("블로그 글쓰기 AI(" + E2 + "부터");
    }

    @Test
    @DisplayName("REQ-203 · GET /privacy: 2·3·4·5절이 블로그 글·문장 이력·인용·출처·댓글·신고·로그인 없는 공개·예전 판 지우기·탈퇴 처리를 고지하고 「모두의 여백」이 없다")
    void getPrivacy_blogDataInSections2to5() throws Exception {
        String html = render("/privacy");
        assertThat(between(html, "<h2>2.", "<h2>3.")).contains("<strong>블로그</strong>").contains("(" + E2 + " 시행)")
                .contains("예전 판").contains("인용").contains("출처").contains("댓글").contains("블로그 글·댓글을 신고한 내역")
                .contains("AI 사용에 든 원가(서비스가 부담)").doesNotContain("이용 금액");
        assertThat(between(html, "<h2>3.", "<h2>4.")).contains("블로그 글 게시").contains("(" + E2 + "부터)");
        assertThat(between(html, "<h2>4.", "<h2>5.")).contains("로그인하지 않은 사람도").contains("여백은 본인만");
        assertThat(between(html, "<h2>5.", "<h2>6.")).contains("예전 판을 지울 수").contains("「탈퇴한 사용자」")
                .contains("글·문장·예전 판을 지우거나 회원 탈퇴해도, 다른 이용자가 인용해 간 문장은 인용할 때의 문장 그대로");
        // 쪽지 여러 장·책 없는 쪽지는 M1(E2 전)이 연다 — E2 시행 표시는 블로그 문장·여백 AI 쪽에만 붙는다.
        assertThat(between(html, "<strong>여백</strong>", "<strong>앱 글쓰기</strong>"))
                .contains("(" + E2 + " 시행) 쪽지에 건 블로그 문장").doesNotContain("(" + E2 + " 시행) 한 책에 여러 장");
        assertThat(html).doesNotContain("모두의 여백");
    }

    /** B3에서 SentItem · BlogAiSentItem 라벨로 옮기면 지운다 — 그 전엔 가드(코드 ⊆ 문서)가 C12로 넓게 적은 이 항목들을 지키지 않는다. */
    @Test
    @DisplayName("C12 · GET /privacy: 6-3절의 E2부터 더해질 항목 4개와 6-5절의 「글에 넣은 인용」이 문서에 남아 있다(B3 전 임시)")
    void getPrivacy_c12BroadSentItemsUntilB3() throws Exception {
        String html = render("/privacy");
        assertThat(between(html, "<h2>6-3.", "<h2>6-4."))
                .contains("<li>지금 쪽지(옮긴 문장·생각)와 그 쪽지에 건 블로그 문장</li>")
                .contains("<li>같은 책의 다른 쪽지(옮긴 문장·생각·작성일)</li>")
                .contains("<li>「AI에게 묻기」에 적은 질문</li>")
                .contains("<li>전에 여백에서 AI에게 물은 질문과 받은 설명</li>");
        assertThat(between(html, "<h2>6-5.", "<h2>7."))
                .contains("<li>글에 넣은 인용(남의 블로그 문장·내 여백에서 가져온 글)</li>");
    }

    /** 줄바꿈 · 들여쓰기를 공백 하나로 — 문장이 줄을 넘어가도 금지 문장 검사가 공허하게 통과하지 않게. */
    private static String squash(String s) {
        return s.replaceAll("\\s+", " ");
    }

    /** 4회차 리뷰(K1 · K5 · K6 · F1-1 · F1-10 · F1-11 · F2-1 · F3-1 · F3-4)가 짚은 문장 — 머지 뒤에 더하면 30일 재공지가 걸리는 것들. */
    @Test
    @DisplayName("K2 4회차 · GET /privacy: 남이 건 · 인용한 블로그 문장의 전송(4절 · 6-3 · 6-5) · 올리기 준비 자동 검토 · 권리침해 요청(2 · 5 · 6절) · 공개 날짜 · 남의 여백에 남는 인용 · 차단은 로그인 상태만 · 여백 공개 없애는 날짜 약속 없음")
    void getPrivacy_round4Fixes() throws Exception {
        String html = squash(render("/privacy"));
        String s2 = between(html, "<h2>2.", "<h2>3.");
        String s4 = between(html, "<h2>4.", "<h2>5.");
        String s5 = between(html, "<h2>5.", "<h2>6.");
        String s63 = between(html, "<h2>6-3.", "<h2>6-4.");
        String s65 = between(html, "<h2>6-5.", "<h2>7.");
        assertThat(s4).contains("그 이용자의 요청에 담겨 함께 국외로 전송될 수 있습니다").contains("올리거나 고쳐 올린 날짜")
                .contains("로그인하지 않고").doesNotContain("차단하면 서로의 블로그 글과 댓글이 보이지 않습니다.");
        assertThat(s63).contains("쪽지에 건 뒤 그 이용자가 여백 AI를 요청하면")
                .doesNotContain("요청하지 않으면 쓴 글과 여백은 서비스 서버에만 저장되고 국외로 전송되지 않습니다.");
        assertThat(s65).contains("그 이용자의 요청에 담겨 함께 국외로 전송될 수 있습니다").contains("「올리기 준비」 화면에 들어가면")
                .doesNotContain("직접 요청하는 경우에 한해")
                .doesNotContain("요청하지 않으면 쓴 글은 서비스 서버에만 저장되고 국외로 전송되지 않습니다.");
        assertThat(s2).contains("저작권 중단 요구").contains("쪽지를 공유(내보내기)한 시각").doesNotContain(E2 + " 전에 없애며");
        assertThat(s5).contains("권리침해 요청서").contains("그 이용자의 여백(쪽지에 건 문장").contains("블로그 글에 인용한 설명(「AI의 답」)");
        assertThat(between(html, "<h2>6.", "<h2>6-1.")).contains("시행령 제41조");
    }

    @Test
    @DisplayName("K2 4회차 · GET /terms: 남이 인용 · 건 문장을 AI 요청에 담는 이용 범위 · 임시조치 단위에 댓글 · 「AI의 답」 공개 · 읽는 화면의 AI 표시 · 직접 적은 책 문장의 출처 · 통지 수단 지정")
    void getTerms_round4Fixes() throws Exception {
        String html = squash(render("/terms"));
        String a11 = between(html, "<h2>제11조 (생성형 AI 기능)</h2>", "<h2>제12조");
        String a12 = between(html, "<h2>제12조 (블로그와 게시물)</h2>", "<h2>제13조");
        assertThat(a11).contains("「AI의 답」으로 인용하면 그 부분은 공개됩니다")
                .doesNotContain("물은 내용과 받은 설명은 그 이용자만 봅니다.").doesNotContain("블로그 글을 읽는 화면에는 AI가 없습니다");
        assertThat(a12).contains("그 요청에 담아").contains("(댓글 하나, 인용 하나 또는 글 전체)")
                .contains("본문에 직접 적은 책 문장에는 출처가 자동으로 붙지 않으니").contains("받을 수단(메일 등)을 정할 수 있습니다")
                .contains("그 글이나 여백에는");
    }

    /** 5회차 리뷰 — 인용 박스에 남는 원 글 제목 · 게시자 통지 수단 · 단정 문장의 반례 · 글쓴이가 전송을 막는 길 · 빠진 잠금. */
    @Test
    @DisplayName("K2 5회차 · GET /privacy: 인용에 원 글 제목이 남는다(2 · 4 · 5절) · 3년 보관 · 6-3 · 6-5 단정의 예외 · 글쓴이가 전송을 막는 길 · 여백 비공개는 공개를 없앤 뒤")
    void getPrivacy_round5Fixes() throws Exception {
        String html = squash(render("/privacy"));
        String s2 = between(html, "<h2>2.", "<h2>3.");
        String s4 = between(html, "<h2>4.", "<h2>5.");
        String s5 = between(html, "<h2>5.", "<h2>6.");
        String s63 = between(html, "<h2>6-3.", "<h2>6-4.");
        String s65 = between(html, "<h2>6-5.", "<h2>7.");
        assertThat(s2).contains("그 출처 이름·원 글 제목");
        assertThat(s4).contains("여백은 본인만 보며(여백 공개를 없앤 뒤");
        assertThat(s5).contains("원 글쓴이의 블로그 이름과 원 글의 제목(인용할 때의 것이 남을 수 있습니다)이 출처로")
                .contains("<strong>3년</strong>");
        assertThat(s63).contains("블로그 글쓰기 AI를 쓰면 그 글에 인용한 여백이 6-5절대로 전송될 수 있고")
                .contains("내 문장이 다른 이용자의 AI 요청에 담기지 않게 하는 방법은 6-5절과 같습니다");
        assertThat(s65).contains("(글쓴이가 자기 쪽지에 건 경우 포함)")
                .contains("블로그에 올린 내 문장이 다른 이용자의 AI 요청에 담기지 않게 하려면 그 문장을 블로그에 올리지 않아야 합니다");
    }

    @Test
    @DisplayName("K2 5회차 · GET /terms: 인용에 원 글 제목 · 게시자 통지는 앱 표시와 메일(미리 정하는 수단 없음) · 독자는 여백에서 AI에게 물을 수 있다 · 여백 비공개는 공개를 없앤 뒤 · 차단의 로그인 예외 잠금")
    void getTerms_round5Fixes() throws Exception {
        String html = squash(render("/terms"));
        String a11 = between(html, "<h2>제11조 (생성형 AI 기능)</h2>", "<h2>제12조");
        String a12 = between(html, "<h2>제12조 (블로그와 게시물)</h2>", "<h2>제13조");
        assertThat(a11).contains("문장을 자기 여백에 걸어 여백에서 AI에게 물을 수는 있습니다")
                .doesNotContain("AI를 부르지 않으며, 독자가 AI와 이야기하는 기능은 없습니다.");
        assertThat(a12).contains("인용에는 원 글쓴이의 블로그 이름과 원 글의 제목이 출처로 표시됩니다")
                .contains("게시자에게는 앱 안 「가려졌어요」 표시로")
                .doesNotContain("게시자는 아래 문의처로 알림 받을 수단을 미리 정할 수 있고")
                .contains("여백은 본인만 보며(여백 공개를 없앤 뒤)")
                .contains("로그인하지 않고 웹 공개 페이지로 보면 보입니다");
    }

    /** 다른 이용자의 글에 이미 인용된 문장 · 차단 전에 내 여백에 건 문장은 차단해도 남는다(M-2) — privacy 4절과 terms 제12조 ⑤가 같은 문장. */
    private static final String BLOCK_EXCEPTION = "다른 이용자의 글에 이미 인용된 문장과, 차단 전에 내 여백에 건 문장도 보일 수 있습니다";

    /**
     * 6회차 — 사용자 결정(2026-10-30) D-A 운영자 메일함(8절 위탁 · 국외 이전 · 5절 3년) · D-C 임시조치 30일 뒤 삭제(5절) ·
     * M-2 차단 반례 · M-1 잠금 4개(이미 있는 문장 — 지워지면 Red).
     */
    @Test
    @DisplayName("K2 6회차 · GET /privacy: 8절 운영자 메일함(Gmail) 위탁 · 국외 이전 고지 · 다섯 곳 · 5절 문의 메일 3년 · 임시조치 이의 없으면 삭제 · 차단 반례 · 13절 · 빠진 잠금 4개")
    void getPrivacy_round6Fixes() throws Exception {
        String html = squash(render("/privacy"));
        String s2margin = between(html, "<strong>여백</strong>", "<strong>앱 글쓰기</strong>");
        String s4 = between(html, "<h2>4.", "<h2>5.");
        String s5 = between(html, "<h2>5.", "<h2>6.");
        String s63 = between(html, "<h2>6-3.", "<h2>6-4.");
        String s65 = between(html, "<h2>6-5.", "<h2>7.");
        String s8 = between(html, "<h2>8.", "<h2>9.");
        String s13 = between(html, "<h2>13.", "</section>");
        // D-A — 위탁 표 Google 행 · 28조의8 ② 1~5호 · 곳 수
        assertThat(s8).contains("운영자 메일함(Gmail — 문의와 권리침해 요청의 수신·보관, 보유는 5절)")
                .contains("<strong>운영자 메일함의 국외 이전</strong>")
                .contains("<strong>이전받는 자</strong>: Google LLC (Gmail)")
                .contains("이용자가 문의처로 메일을 보내는 시점에")
                .contains("(" + E2 + "부터) 권리침해 요청")
                .contains("메일을 보내지 않으면 이전되지 않습니다")
                .contains("소명 자료가 필요한 저작권 중단 요구와 회원이 아닌 사람의 요청은 지금 메일로만 받습니다")
                .contains("<strong>운영자 메일함</strong>(Gmail — 문의·권리침해 요청) 다섯 곳")
                .doesNotContain("네 곳");
        // D-A 5 · D-C — 5절
        assertThat(s5).contains("문의처(운영자 메일함)로 받은 메일은 처리가 끝난 뒤 <strong>3년</strong>(분쟁 대비)까지 보관한 뒤 지웁니다")
                .contains("임시조치로 가린 것은 그 기간(30일 이내)이 끝날 때까지 게시자의 이의가 없으면 삭제합니다 — 약관 제12조 ⑥");
        // M-2
        assertThat(s4).contains(BLOCK_EXCEPTION);
        // 13절 개정 공지 줄
        assertThat(s13).contains(K2_NOTICE + " 개정 공지").contains("8절 운영자 메일함");
        // M-1 잠금 4개
        assertThat(s65).contains("「올리기 준비」 화면에 들어갈 때(동의했고 몫이 남았으면 자동으로 한 번)");
        assertThat(s8).contains("블로그 글쓰기 AI(" + E2 + "부터, 동의한 이용자가 쓰는 경우에 한함)");
        assertThat(between(s8, "블로그 글쓰기 AI(" + E2, "</td>")).isNotEmpty().doesNotContain("동의하고 직접 요청한");
        assertThat(s2margin).contains("쪽지에서 쓴 AI의 하루 사용 횟수");
        assertThat(s63).contains("블로그 글에 인용한 설명은 그 글을 지우거나 회원 탈퇴할 때 파기");
    }

    @Test
    @DisplayName("K2 6회차 · GET /terms: 제12조 ⑥ 임시조치 기간 안에 이의가 없으면 삭제 · ⑤ 차단 반례 · 개정 공지일")
    void getTerms_round6Fixes() throws Exception {
        String html = squash(render("/terms"));
        String a12 = between(html, "<h2>제12조 (블로그와 게시물)</h2>", "<h2>제13조");
        assertThat(a12).contains("임시조치 기간이 끝날 때까지 게시자의 이의가 없으면 그 게시물(임시조치한 단위 그대로)을 삭제하고 게시자에게 알립니다")
                .contains(BLOCK_EXCEPTION);
        assertThat(html).contains(K2_NOTICE + " 개정 공지");
    }

    @Test
    @DisplayName("REQ-200 · REQ-201 · GET /terms: 제12조 블로그와 게시물 — 시행일 E2 · 게시물 책임 · 금지 행위(무관용) · 신고 24시간 검토 · 임시조치 30일 · 게시자 통지 · 이의 · 저작권 중단 요구 수령인 · 운영자 조치")
    void getTerms_blogArticle() throws Exception {
        String html = render("/terms");
        String article = between(html, "<h2>제12조 (블로그와 게시물)</h2>", "<h2>제13조");
        assertThat(article).contains("시행: " + E2).contains("책임은 올린 이용자에게").contains("무관용")
                .contains("신고는 24시간 안에 검토").contains("임시조치").contains("30일 이내").contains("게시자에게 알립니다")
                .contains("이의").contains("복제·전송의 중단").contains("kimsadol369785@gmail.com").contains("글쓰기와 댓글을 막을")
                .contains("인용 하나 또는 글 전체").contains("중단 요구를 받는 사람")
                .contains("그 사실을 표시해").contains("다른 이용자도 알 수 있게")
                .contains("(남이 이미 인용해 간 문장은 그대로 남습니다)");
        assertThat(html).contains("제12조 블로그와 게시물 신설");
    }

    @Test
    @DisplayName("REQ-202 · GET /terms: 제11조가 E2부터 넓어지는 AI(여백 설명 · 맞춤법 제안 · 제목 추천 · 출처 찾기 · 주소 확인)를 알리고 본문을 대신 쓰지 않는다를 지킨다")
    void getTerms_generativeAiArticleExpanded() throws Exception {
        String article = between(render("/terms"), "<h2>제11조 (생성형 AI 기능)</h2>", "<h2>제12조");
        assertThat(article).contains("(" + E2 + " 시행)").contains("풀어서 설명").contains("맞춤법").contains("제목")
                .contains("출처").contains("대신 쓰지 않").contains("틀렸다는 뜻은 아닙니다");
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

    @Test
    @DisplayName("PR-A · GET /terms: 제2조에 아이폰 앱, 제3조 ②에 구글·Apple 로그인 계정 — 개정 이력에 즉시 시행으로")
    void getTerms_appleAndGoogleAccounts() throws Exception {
        String html = render("/terms");
        assertThat(between(html, "<h2>제2조", "<h2>제3조")).contains("아이폰 앱");
        assertThat(between(html, "<h2>제3조", "<h2>제4조")).contains("구글 로그인 계정").contains("Apple 로그인 계정(아이폰 앱)");
        assertThat(html).contains("제3조 구글·Apple 로그인 계정(즉시)");
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
