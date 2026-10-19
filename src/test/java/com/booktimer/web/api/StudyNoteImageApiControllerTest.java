package com.booktimer.web.api;

import com.booktimer.security.RateLimitAction;
import com.booktimer.security.RateLimitService;
import com.booktimer.study.StudyNoteImage;
import com.booktimer.study.StudyNoteImageRepository;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRegistrationService;
import com.booktimer.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 필기 사진의 문 — {@code POST /api/study/notes/images}(올리기) · {@code GET /api/study/notes/images/{id}}(보기).
 *
 * <p>겨누는 실패: ① 응답 {@code src}가 편집기 정규식과 어긋나 편집기가 사진을 넣지 않는 것 ② 새 경로가 인증·CSRF 밖에
 * 놓이는 것 ③ Content-Type만 믿고 JPEG 아닌 바이트를 저장하는 것 ④ 사람당·서비스 전체·시간당 상한의 배선 누락
 * ⑤ 남의 사진에 닿는 것(IDOR). 413은 MockMvc가 multipart 상한을 안 타므로 <b>앱 검사</b>만 잰다 — 전역 상한은
 * {@code StudyRecallTranscribeUploadTest}가 경로와 무관하게 이미 잰다(설계 §4-5).
 *
 * <p>사진 행 단언은 전역 {@code count()}가 아니라 <b>그 사람의 행 수</b>로 한다(공유 H2에 다른 컨텍스트의 행이 있을 수 있다).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StudyNoteImageApiControllerTest {

    private static final String SEOUL = "Asia/Seoul";
    /** 편집기 OWN_IMAGE_PATH와 같은 꼴 — 응답이 이 꼴이 아니면 편집기가 넣지 않는다. */
    private static final String OWN_IMAGE_PATH = "^/api/study/notes/images/[1-9]\\d*$";
    private static final long MAX_IMAGE_BYTES = 3L * 1024 * 1024;

    @Autowired MockMvc mockMvc;
    @Autowired UserRegistrationService registrationService;
    @Autowired UserRepository userRepository;
    @Autowired StudyNoteImageRepository imageRepository;
    @Autowired RateLimitService rateLimitService;
    @Autowired EntityManager entityManager;
    @Autowired Clock clock;

    @BeforeEach
    void clearRateLimits() {
        rateLimitService.clearForTest();
    }

    private User register(String loginId) {
        registrationService.register(loginId + "@booktimer.com", "pw1234qwer!!", loginId,
                "닉네임_" + loginId, SEOUL, Role.USER,
                LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL)));
        return userRepository.findByLoginId(loginId).orElseThrow();
    }

    /** JPEG 머리(FF D8 FF)로 시작하는 {@code size}바이트 — 서버는 머리만 본다. */
    private static byte[] jpeg(int size) {
        byte[] b = new byte[size];
        Arrays.fill(b, (byte) 0x41);
        b[0] = (byte) 0xFF;
        b[1] = (byte) 0xD8;
        b[2] = (byte) 0xFF;
        b[3] = (byte) 0xE0;
        return b;
    }

    private static MockMultipartHttpServletRequestBuilder upload(byte[] bytes) {
        return multipart("/api/study/notes/images")
                .file(new MockMultipartFile("image", "note.jpg", "image/jpeg", bytes));
    }

    private long rows(User user) {
        return entityManager.createQuery("select count(i) from StudyNoteImage i where i.user = :u", Long.class)
                .setParameter("u", user).getSingleResult();
    }

    // ── 올리기 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("REQ-01 · 업로드: JPEG 한 장이면 200 {src}이고 그 꼴이 ^/api/study/notes/images/[1-9]\\d*$이며 행 1개·byte_size=길이")
    void upload_jpeg_isStored() throws Exception {
        User user = register("imgup");
        byte[] bytes = jpeg(1234);

        String src = mockMvc.perform(upload(bytes).with(user("imgup")).with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()
                .replaceAll(".*\"src\"\\s*:\\s*\"([^\"]*)\".*", "$1");

        assertThat(src).matches(OWN_IMAGE_PATH);
        Long id = Long.parseLong(src.substring(src.lastIndexOf('/') + 1));
        assertThat(rows(user)).isEqualTo(1);
        assertThat(entityManager.createQuery("select i.byteSize from StudyNoteImage i where i.id = :id", Integer.class)
                .setParameter("id", id).getSingleResult()).isEqualTo(1234);
    }

    @Test
    @DisplayName("REQ-02 · 업로드: 미인증은 로그인으로, CSRF가 없으면 403 — 행 0 · 같은 요청에 로그인·CSRF를 붙이면 200(대조군)")
    void upload_requiresLoginAndCsrf() throws Exception {
        User user = register("imgauth");

        mockMvc.perform(upload(jpeg(100)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        mockMvc.perform(upload(jpeg(100)).with(user("imgauth")))
                .andExpect(status().isForbidden());
        assertThat(rows(user)).isZero();

        // 대조군 — 문이 없거나 URL이 틀려 난 302·403이 아니라 진짜 거절이었음을 가른다.
        mockMvc.perform(upload(jpeg(100)).with(user("imgauth")).with(csrf()))
                .andExpect(status().isOk());
        assertThat(rows(user)).isEqualTo(1);
    }

    @Test
    @DisplayName("REQ-02 · 업로드: JPEG 머리가 아니면 400 「JPG 사진만 올릴 수 있어요」 — 0으로 채운 40만 바이트·PNG 머리·셋째 바이트만 틀린 머리, 행 0")
    void upload_nonJpeg_is400() throws Exception {
        User user = register("imgfmt");
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
        byte[] almost = {(byte) 0xFF, (byte) 0xD8, 0, 0, 0};   // 첫 두 바이트만 맞다 — 머리 검사가 첫 바이트만 보면 통과한다

        for (byte[] bytes : new byte[][] {new byte[400_000], png, almost}) {
            mockMvc.perform(upload(bytes).with(user("imgfmt")).with(csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string("JPG 사진만 올릴 수 있어요"));
        }
        assertThat(rows(user)).isZero();
    }

    @Test
    @DisplayName("REQ-02 · 업로드: 파트가 없으면 400 「사진을 골라 주세요」")
    void upload_withoutPart_is400() throws Exception {
        register("imgnopart");

        mockMvc.perform(multipart("/api/study/notes/images").with(user("imgnopart")).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("사진을 골라 주세요"));
    }

    @Test
    @DisplayName("REQ-02 · 업로드: 빈 파트(0바이트)면 400 「사진을 골라 주세요」 — 행 0")
    void upload_emptyPart_is400() throws Exception {
        User user = register("imgempty");

        mockMvc.perform(upload(new byte[0]).with(user("imgempty")).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("사진을 골라 주세요"));
        assertThat(rows(user)).isZero();
    }

    @Test
    @DisplayName("REQ-02 · 업로드: 3MB를 넘으면 413 「사진은 3MB 이하로 올려 주세요」 — 앱 검사, 행 0")
    void upload_overThreeMegabytes_is413() throws Exception {
        User user = register("imgbig");

        mockMvc.perform(upload(jpeg((int) MAX_IMAGE_BYTES + 1)).with(user("imgbig")).with(csrf()))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(content().string("사진은 3MB 이하로 올려 주세요"));
        assertThat(rows(user)).isZero();
    }

    @Test
    @DisplayName("REQ-03 · 업로드: 사람당 200MB를 넘으면 400이고 행이 늘지 않는다")
    void upload_overPerUserQuota_is400() throws Exception {
        User user = register("imgquota");
        // 진짜 200MB를 만들지 않는다 — 작은 행의 byte_size만 상한 바로 밑으로 올린다.
        StudyNoteImage filler = StudyNoteImage.of(user, jpeg(10), clock.instant());
        ReflectionTestUtils.setField(filler, "byteSize", (int) (StudyNoteImage.QUOTA_BYTES - 10));
        imageRepository.saveAndFlush(filler);

        mockMvc.perform(upload(jpeg(100)).with(user("imgquota")).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.startsWith("사진은 한 사람당 200MB까지")));
        assertThat(rows(user)).isEqualTo(1);
    }

    @Test
    @DisplayName("REQ-03 · 업로드: 서비스 전체 합계가 2GB를 넘으면 503 「지금은 사진을 더 넣을 수 없어요」이고 행이 늘지 않는다 — 올리는 사람의 몫은 작아도")
    void upload_overTotalQuota_is503() throws Exception {
        User uploader = register("imgtotal");
        // 다른 두 사람이 1GB씩 — 합계가 정확히 TOTAL_QUOTA_BYTES. 올리는 사람 자기 몫은 0이라 사람당 검사는 통과한다.
        for (String other : new String[] {"imgtotala", "imgtotalb"}) {
            StudyNoteImage big = StudyNoteImage.of(register(other), jpeg(10), clock.instant());
            ReflectionTestUtils.setField(big, "byteSize", 1 << 30);
            imageRepository.saveAndFlush(big);
        }

        mockMvc.perform(upload(jpeg(100)).with(user("imgtotal")).with(csrf()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(org.hamcrest.Matchers.startsWith("지금은 사진을 더 넣을 수 없어요")));
        assertThat(rows(uploader)).isZero();
    }

    @Test
    @DisplayName("REQ-03 · 업로드: 시간당 60장까지 200, 61번째만 429")
    void upload_beyondHourlyLimit_is429() throws Exception {
        User user = register("imgrate");
        for (int i = 0; i < 59; i++) {
            rateLimitService.allow(RateLimitAction.STUDY_NOTE_IMAGE, user.getId());
        }
        // 검증이 상한보다 먼저 — 잘못 만든 요청(400)은 그 시간의 몫을 깎지 않는다. 깎으면 아래 60번째가 429가 된다.
        mockMvc.perform(upload(new byte[] {1, 2, 3, 4}).with(user("imgrate")).with(csrf()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(upload(jpeg(100)).with(user("imgrate")).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(upload(jpeg(100)).with(user("imgrate")).with(csrf()))
                .andExpect(status().isTooManyRequests());
        assertThat(rows(user)).isEqualTo(1);
    }

    // ── 보기 ────────────────────────────────────────────────────────────────

    private String uploadAs(String loginId, byte[] bytes) throws Exception {
        return mockMvc.perform(upload(bytes).with(user(loginId)).with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()
                .replaceAll(".*\"src\"\\s*:\\s*\"([^\"]*)\".*", "$1");
    }

    @Test
    @DisplayName("REQ-04 · 조회: 업로드가 준 src를 그대로 GET하면 같은 바이트 · image/jpeg · nosniff · no-store")
    void get_ownImage_returnsSameBytesWithSafeHeaders() throws Exception {
        register("imgview");
        byte[] bytes = jpeg(777);
        // 응답의 src 문자열을 그대로 GET한다 — StudyNoteImage.PATH와 @GetMapping 리터럴이 갈리면 여기서 깨진다.
        String src = uploadAs("imgview", bytes);

        mockMvc.perform(get(src).with(user("imgview")))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(content().bytes(bytes))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
    }

    @Test
    @DisplayName("REQ-04 · 조회: 남의 사진과 없는 id는 똑같이 404(빈 본문) — 주인은 200(대조군)")
    void get_othersOrMissing_is404() throws Exception {
        register("imgowner");
        register("imgthief");
        String src = uploadAs("imgowner", jpeg(100));
        long missing = Long.parseLong(src.substring(src.lastIndexOf('/') + 1)) + 1000;

        mockMvc.perform(get(src).with(user("imgthief")))
                .andExpect(status().isNotFound())
                .andExpect(content().string(""));
        mockMvc.perform(get("/api/study/notes/images/" + missing).with(user("imgowner")))
                .andExpect(status().isNotFound())
                .andExpect(content().string(""));
        mockMvc.perform(get(src).with(user("imgowner")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("REQ-04 · 조회: 미인증은 로그인으로 — 같은 GET에 로그인을 붙이면 200(대조군)")
    void get_unauthenticated_isBlocked() throws Exception {
        register("imganon");
        String src = uploadAs("imganon", jpeg(100));

        mockMvc.perform(get(src))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        mockMvc.perform(get(src).with(user("imganon")))
                .andExpect(status().isOk());
    }
}
