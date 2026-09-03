package com.booktimer.web.api;

import com.booktimer.study.ClaudeStudyAssistant;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRegistrationService;
import com.booktimer.user.UserRepository;
import jakarta.servlet.ServletContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * 사진 업로드의 <b>실서버</b> 계약 — 「저장하지 않는다」와 「413」에 계측기를 다는 유일한 자리.
 *
 * <p><b>왜 MockMvc로는 안 되는가.</b> MockMvc는 {@code MockMultipartHttpServletRequest}를 <b>이미 파싱된
 * 채로</b> 넘긴다 — 톰캣의 multipart 파서를 아예 타지 않는다. 그래서 {@code spring.servlet.multipart.*}
 * 세 값은 MockMvc 스위트에서 <b>한 건도 검증되지 않는다</b>: 값을 지우거나 낮춰도 전체 스위트가 green이다.
 * 게다가 {@code src/test/resources/application.properties}가 main을 <b>덮어쓰므로</b>(병합이 아니다)
 * 나머지 테스트는 애초에 {@code file-size-threshold} 기본값(=0B) 세계에서 돈다.
 *
 * <p>그 두 구멍이 겹치면 <b>처리방침이 조용히 거짓</b>이 된다: threshold가 0이면 톰캣이 모든 파트를 즉시
 * 임시파일로 떨어뜨려 사진이 디스크를 거치는데, {@code privacy.html} 5-1절은 「사진은 서비스 서버에
 * 저장하지 않습니다」라고 적혀 있다. 그래서 이 클래스는 운영과 <b>같은 세 값</b>을 명시하고 진짜 톰캣을
 * 띄운다.
 *
 * <p>재는 것은 둘이다:
 * <ol>
 *   <li><b>413</b> — 3MB를 넘는 업로드가 500 + {@code error.html}이 아니라 413 + 평문 한국어여야 한다.
 *       {@code MaxUploadSizeExceededException}은 {@code DispatcherServlet.checkMultipart()}에서, 즉
 *       <b>핸들러를 고르기 전에</b> 터지므로 컨트롤러의 {@code @ExceptionHandler}로는 영영 못 잡는다(T-224).</li>
 *   <li><b>무저장</b> — 핸들러가 도는 <b>바로 그 순간</b> 서블릿 임시 디렉터리에 업로드 파일이 0개여야
 *       한다. 요청이 끝난 뒤에 세면 톰캣이 이미 지운 뒤라 <b>threshold가 0이어도 통과하는 공허한 단언</b>이
 *       된다 — 그래서 어댑터 목의 answer 안에서 센다(요청 처리 한복판이다).</li>
 * </ol>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        // 운영(application.properties)과 같은 세 값을 여기 명시한다 — test properties가 main을 덮어쓰므로
        // 이 배열이 없으면 이 테스트마저 기본값 세계에서 돌아 「무저장」을 검증하지 못한다.
        properties = {
                "spring.servlet.multipart.max-file-size=3MB",
                "spring.servlet.multipart.max-request-size=10MB",
                "spring.servlet.multipart.file-size-threshold=3MB"
        })
class StudyRecallTranscribeUploadTest {

    private static final String LOGIN_ID = "uploadprobe";
    private static final String PASSWORD = "pw1234qwer!!";
    private static final String SEOUL = "Asia/Seoul";
    private static final Pattern CSRF_INPUT = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"");
    private static final Pattern CSRF_META = Pattern.compile("name=\"_csrf\"[^>]*content=\"([^\"]+)\"");

    @LocalServerPort int port;
    @Autowired UserRegistrationService registrationService;
    @Autowired UserRepository userRepository;
    @Autowired ServletContext servletContext;
    @Autowired Clock clock;

    /** 업로드가 핸들러에 닿은 <b>그 순간</b>의 디스크 상태를 여기서 훔쳐본다. */
    @MockitoBean ClaudeStudyAssistant assistant;

    private final AtomicReference<List<String>> filesDuringRequest = new AtomicReference<>(null);
    private HttpClient client;

    @BeforeEach
    void setUp() throws Exception {
        if (userRepository.findByLoginId(LOGIN_ID).isEmpty()) {
            registrationService.register(LOGIN_ID + "@booktimer.com", PASSWORD, LOGIN_ID, "업로드",
                    SEOUL, Role.USER, LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL)));
            User user = userRepository.findByLoginId(LOGIN_ID).orElseThrow();
            user.requestStudyAi(clock.instant());
            user.approveStudyAi(clock.instant());
            userRepository.save(user);
        }
        given(assistant.isEnabled()).willReturn(true);
        given(assistant.transcribe(any())).willAnswer(invocation -> {
            filesDuringRequest.set(uploadTempFiles());
            return ClaudeStudyAssistant.AiResult.ok(new ClaudeStudyAssistant.Transcript("읽은 글", false));
        });
        client = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        logIn();
    }

    @Test
    @DisplayName("3MB를 넘는 업로드는 413 + 평문 한국어 — 500도 error.html도 아니다(전역 핸들러가 필요한 자리)")
    void oversizedUpload_is413PlainText() throws Exception {
        HttpResponse<String> response = postPhoto(new byte[4 * 1024 * 1024]);

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.body()).isEqualTo("사진은 3MB 이하로 올려 주세요");
        // error.html이 렌더되면 본문이 <!DOCTYPE html>…로 시작한다 — 화면은 그걸 상태줄에 찍는다.
        assertThat(response.body().stripLeading()).doesNotStartWith("<");
        assertThat(response.headers().firstValue("Content-Type").orElse(""))
                .startsWith("text/plain");
    }

    @Test
    @DisplayName("업로드는 디스크를 거치지 않는다 — 핸들러가 도는 순간 서블릿 임시 디렉터리에 업로드 파일 0개")
    void upload_neverTouchesDisk() throws Exception {
        HttpResponse<String> response = postPhoto(new byte[400_000]);

        assertThat(response.statusCode()).isEqualTo(200);
        // null이면 어댑터가 안 불린 것 — 그럼 아무것도 안 잰 채 통과할 뻔했다는 뜻이라 함께 막는다.
        assertThat(filesDuringRequest.get())
                .as("어댑터가 호출되지 않아 디스크를 재지 못했다")
                .isNotNull();
        assertThat(filesDuringRequest.get())
                .as("사진이 임시파일로 떨어졌다 — file-size-threshold를 확인하라(처리방침 5-1이 걸려 있다)")
                .isEmpty();
    }

    /**
     * 톰캣이 파트를 떨어뜨릴 수 있는 두 곳을 본다: 서블릿 임시 디렉터리(location 미지정 시 기본값)와
     * {@code java.io.tmpdir} 바로 아래. 이름 규칙은 톰캣의 {@code upload_<uuid>_….tmp}다.
     */
    private List<String> uploadTempFiles() {
        List<String> found = new ArrayList<>();
        Object servletTempDir = servletContext.getAttribute(ServletContext.TEMPDIR);
        if (servletTempDir instanceof File dir) {
            collectUploads(dir.toPath(), found);
        }
        collectUploads(Path.of(System.getProperty("java.io.tmpdir")), found);
        return found;
    }

    private static void collectUploads(Path dir, List<String> found) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> entries = Files.list(dir)) {
            entries.filter(p -> p.getFileName().toString().startsWith("upload_"))
                    .map(Path::toString)
                    .forEach(found::add);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ── HTTP 배선 (진짜 서버라 로그인·CSRF를 실제로 밟는다) ──

    private void logIn() throws Exception {
        String loginPage = get("/login").body();
        String token = extract(CSRF_INPUT, loginPage, "로그인 폼의 CSRF 토큰");
        String form = "username=" + LOGIN_ID + "&password="
                + java.net.URLEncoder.encode(PASSWORD, StandardCharsets.UTF_8) + "&_csrf=" + token;
        HttpResponse<String> response = client.send(HttpRequest.newBuilder()
                .uri(uri("/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("로그인 실패").isEqualTo(302);
    }

    /** 세션 고정 보호로 로그인 뒤 토큰이 새로 발급되므로, 매 요청 직전에 화면에서 다시 읽는다. */
    private String csrfToken() throws Exception {
        return extract(CSRF_META, get("/study").body(), "/study의 CSRF 메타");
    }

    private HttpResponse<String> postPhoto(byte[] bytes) throws Exception {
        String boundary = "----booktimer" + System.nanoTime();
        byte[] body = multipartBody(boundary, bytes);
        return client.send(HttpRequest.newBuilder()
                .uri(uri("/api/study/recall/transcribe"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("X-CSRF-TOKEN", csrfToken())
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private static byte[] multipartBody(String boundary, byte[] bytes) throws IOException {
        var out = new java.io.ByteArrayOutputStream();
        out.write(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"images\"; filename=\"memo.jpg\"\r\n"
                + "Content-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
        out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder().uri(uri(path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private static String extract(Pattern pattern, String html, String what) {
        Matcher matcher = pattern.matcher(html);
        assertThat(matcher.find()).as(what + "을 찾지 못했다").isTrue();
        return matcher.group(1);
    }
}
