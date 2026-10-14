package com.booktimer.web.api;

import com.booktimer.user.AccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * 토스 로그인 연결 끊기 콜백 — {@code POST /api/toss/unlink-callback}(비게임 체크리스트 82·83).
 *
 * <p>사용자가 토스 앱에서 북타이머 연결을 끊거나(UNLINK) 토스 로그인 약관 동의를 철회하거나(WITHDRAWAL_TERMS)
 * 토스를 탈퇴하면(WITHDRAWAL_TOSS) 토스 서버가 이 경로를 부른다. 본문은 {@code {"userKey": 443731103, "referrer": "UNLINK"}}
 * 꼴이고 <b>userKey가 JSON 숫자</b>다. 처리는 {@link AccountService#unlinkToss}가 한다(토스 전용 계정 삭제 / 그 밖은 연결만 끊기).
 *
 * <p><b>계약</b>
 * <ul>
 *   <li>Authorization 없음 · Basic 아님 · base64 깨짐 · 값 불일치 · 서버 값 미설정 → 401, DB 그대로.
 *       인증을 본문 파싱보다 먼저 한다(인증 없는 호출자에게 파서 반응을 보여 주지 않는다).</li>
 *   <li>인증 통과 + 본문이 JSON 아님 · 최상위 userKey 없음/null/빈값/객체 → 400, DB 그대로.</li>
 *   <li>그 밖에는 200(빈 본문) — 모르는 키·이미 처리한 키도 200이라 재전송에 안전하다.
 *       referrer는 처리에 쓰지 않는다(세 값 모두 「연결이 사라졌다」는 같은 사실). 로그에만 남긴다.</li>
 * </ul>
 *
 * <p><b>보안</b> — 이 시크릿은 「userKey만 알면 토스 전용 계정을 지울 수 있는 권한」이다. 값은 SSM
 * {@code /booktimer/TOSS_UNLINK_BASIC_AUTH}와 토스 콘솔 두 곳에만 둔다. 비교는 {@link MessageDigest#isEqual}(상수시간),
 * 서버 값이 비면 전부 거부한다(fail-closed). 로그에는 헤더 원문을 한 글자도 찍지 않는다 — 스킴 없는 base64면
 * 원문이 곧 시크릿이다. userKey도 형태({@code digits:9})만 찍는다.
 */
@RestController
public class TossUnlinkCallbackController {

    private static final Logger log = LoggerFactory.getLogger(TossUnlinkCallbackController.class);
    private static final String BASIC = "Basic ";
    private static final Pattern REFERRER = Pattern.compile("[A-Z_]{1,40}");

    private final AccountService accountService;
    private final String expectedBasicAuth;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    public TossUnlinkCallbackController(AccountService accountService,
                                        @Value("${booktimer.toss.unlink-callback.basic-auth:}") String expectedBasicAuth) {
        this.accountService = accountService;
        this.expectedBasicAuth = expectedBasicAuth;
        if (expectedBasicAuth.isBlank()) {
            log.error("toss-unlink secret-missing — booktimer.toss.unlink-callback.basic-auth 미설정, 콜백은 전부 401");
        }
    }

    @PostMapping("/api/toss/unlink-callback")
    public ResponseEntity<Void> unlink(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                                       @RequestBody(required = false) String rawBody) {
        if (!authorized(authorization, expectedBasicAuth)) {
            log.warn("toss-unlink auth-failed scheme={}", scheme(authorization));
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody == null ? "" : rawBody); // 빈 본문은 예외 없이 MissingNode
        } catch (RuntimeException e) {                                     // StreamReadException
            return ResponseEntity.badRequest().build();
        }
        String userKey = text(root, "userKey");
        if (userKey == null) {
            return ResponseEntity.badRequest().build();
        }
        String referrer = text(root, "referrer");
        AccountService.TossUnlinkOutcome outcome = accountService.unlinkToss(userKey);
        log.info("toss-unlink outcome={} referrer={} keyShape={}", outcome,
                referrer != null && REFERRER.matcher(referrer).matches() ? referrer : "other", shape(userKey));
        return ResponseEntity.ok().build();
    }

    /** 서버 값이 비면 무조건 false(fail-closed). 스킴은 대소문자 무시(RFC 7235). */
    static boolean authorized(String header, String expected) {
        if (expected == null || expected.isBlank() || header == null) {
            return false;
        }
        String h = header.strip();
        if (h.length() <= BASIC.length() || !h.regionMatches(true, 0, BASIC, 0, BASIC.length())) {
            return false;
        }
        byte[] given;
        try {
            given = Base64.getDecoder().decode(h.substring(BASIC.length()).strip());
        } catch (IllegalArgumentException e) {
            return false;
        }
        return MessageDigest.isEqual(given, expected.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 최상위 필드만 본다 — {@code findValue}는 안쪽 객체·배열까지 뒤져 {@code {"x":{"userKey":1}}}의 1을 집는다.
     * 숫자·문자 어느 쪽으로 와도 10진 문자열로 — {@code textValue()}는 Jackson 3에서 숫자 노드에 던진다.
     */
    static String text(JsonNode root, String field) {
        JsonNode v = root.path(field); // 없으면 MissingNode → isValueNode()=false → null
        if (v == null || !v.isValueNode() || v.isNull()) {
            return null;
        }
        String s = v.asString().strip();
        return s.isEmpty() ? null : s;
    }

    /** 헤더 원문은 한 글자도 안 찍는다 — 스킴 이름만. */
    private static String scheme(String header) {
        if (header == null || header.isBlank()) {
            return "none";
        }
        String s = header.strip();
        int sp = s.indexOf(' ');
        if (sp < 0) {
            return "no-scheme";
        }
        String first = s.substring(0, sp);
        return first.equalsIgnoreCase("Basic") || first.equalsIgnoreCase("Bearer") ? first : "other";
    }

    /** userKey 원문 대신 형태만 — NOT_FOUND가 「형식 불일치」인지 가르는 단서. */
    static String shape(String userKey) {
        return (userKey.chars().allMatch(c -> c >= '0' && c <= '9') ? "digits:" : "other:") + userKey.length();
    }
}
