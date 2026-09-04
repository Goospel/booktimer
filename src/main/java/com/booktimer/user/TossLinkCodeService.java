package com.booktimer.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 계정 연결 코드의 발급·소비 (설계 §2.2 ⓐ).
 *
 * <p>웹 설정에서 로그인 상태로 발급한 코드를 미니앱에 입력하면 그 계정에 토스 신원이 붙는다. 발급 화면·
 * 컨트롤러는 PR-2지만, PR-1의 {@code POST /api/toss/link}가 소비에 의존하므로 서비스는 여기서 만든다.
 *
 * <p>코드는 <b>사람이 눈으로 옮겨 적는</b> 값이라 혼동 문자(0/O, 1/I/L)를 뺀 알파벳으로 8자를 만든다.
 * 짧은 만큼 엔트로피가 낮으므로(≈32^8) 방어는 <b>TTL 5분 + 일회용 + 레이트리밋</b> 세 겹이다 —
 * 어느 하나도 단독으로는 충분하지 않다.
 */
@Service
@Transactional
public class TossLinkCodeService {

    private static final SecureRandom RANDOM = new SecureRandom();

    /** 혼동 문자를 뺀 코드 알파벳(0·O·1·I·L 제외) — 전화로 불러주거나 손으로 옮겨 적어도 틀리지 않게. */
    private static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final int CODE_LENGTH = 8;

    /** 코드 수명 — 발급 화면을 보며 바로 입력하는 값이라 짧게 잡는다. */
    public static final Duration TTL = Duration.ofMinutes(5);

    private final TossLinkCodeRepository repository;
    private final Clock clock;

    public TossLinkCodeService(TossLinkCodeRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * 연결 코드를 발급한다 — 평문을 돌려주고(화면에만 노출) DB엔 해시·만료를 저장한다.
     * 같은 사용자의 기존 미사용 코드는 무효화해 항상 하나만 유효하게 둔다(EmailTokenService와 동일).
     *
     * <p>이미 토스에 연결된 계정은 거부한다 — {@code toss_user_key}는 once-set 불변이라 이 코드로 할 수 있는
     * 일이 없다. 설정 화면이 버튼을 숨기지만 그건 표시일 뿐이라, 실제 방어는 여기서 한다.
     *
     * @return 사용자에게 보여줄 평문 코드(대문자 8자)
     * @throws TossLinkConflictException 이미 토스 신원이 붙어 있는 계정인 경우
     */
    public String issue(User user) {
        if (user.getTossUserKey() != null) {
            throw new TossLinkConflictException("이미 토스에 연결된 계정입니다");
        }
        return issue(user, TossLinkCode.Purpose.LINK_TOSS);
    }

    /**
     * <b>토스 → 웹</b> 로그인 코드를 발급한다 — 미니앱(Bearer 인증)에서 발급해 PC 웹 로그인 화면에 입력하면
     * 그 계정의 세션이 열린다. 토스에서 시작한 계정은 비밀번호가 없어 폼 로그인이 원리상 불가라 이 경로가
     * 유일한 웹 진입로다.
     *
     * <p>발급 조건은 {@link #issue}의 <b>거울</b>이다 — 토스 신원이 붙어 있어야 한다. Bearer 토큰은
     * {@code /api/toss/login·register·link}에서만 나오므로 실질적으로 항상 참이지만, 두 발급 조건이
     * 상호 배타임을 코드가 보증하게 해 둔다(그래서 한 사용자가 두 목적의 코드를 동시에 가질 수 없고,
     * 아래 "기존 미사용 코드 전부 무효화"가 목적을 가리지 않아도 된다).
     *
     * @throws TossLinkConflictException 토스에 연결되지 않은 계정인 경우
     */
    public String issueWebLogin(User user) {
        if (user.getTossUserKey() == null) {
            throw new TossLinkConflictException("토스 앱에 연결되지 않은 계정입니다");
        }
        return issue(user, TossLinkCode.Purpose.WEB_LOGIN);
    }

    /** 무효화 + 생성 공통 — 두 발급 경로가 목적만 달리해 같은 규칙(항상 하나만 유효)을 쓴다. */
    private String issue(User user, TossLinkCode.Purpose purpose) {
        Instant now = clock.instant();
        List<TossLinkCode> previous = repository.findByUserAndUsedAtIsNull(user);
        for (TossLinkCode code : previous) {
            code.markUsed(now);
        }
        repository.saveAll(previous);

        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        String rawCode = code.toString();
        repository.save(TossLinkCode.issue(user, hash(rawCode), now.plus(TTL), purpose));
        return rawCode;
    }

    /**
     * 코드를 검증·소비한다. 통과하면 발급자 user를 돌려주고 일회용 처리한다.
     *
     * <p><b>목적이 다르면 "없는 코드"와 똑같이 처리한다</b> — 소비 지점이 자기 목적만 받아야 웹→토스 연결
     * 코드가 웹 로그인 토큰으로 승격되지 않는다(V86 주석). 거절은 {@code used_at}을 찍지 않으므로,
     * 엉뚱한 소비 지점에 들이민다고 코드가 소모되지도 않는다.
     *
     * @return 유효하면 발급자, 아니면 빈 Optional(없음·만료·이미 사용·목적 불일치를 호출자가 구분하지 못하게 동일 처리)
     */
    public Optional<User> consume(String rawCode, TossLinkCode.Purpose purpose) {
        if (rawCode == null || rawCode.isBlank()) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        return repository.findByCodeHash(hash(normalize(rawCode)))
                .filter(code -> code.getPurpose() == purpose && code.isConsumableAt(now))
                .map(code -> {
                    code.markUsed(now);
                    repository.save(code);
                    return code.getUser();
                });
    }

    /**
     * 사용자가 옮겨 적은 코드를 관대하게 받아들인다 — 소문자와 <b>모든 공백</b>은 같은 코드로 본다.
     * 발급 화면이 {@code ABCD 2345}처럼 4자씩 띄워 보여줘도 보이는 대로 옮겨 적으면 통과한다.
     * 하이픈은 벗기지 않는다 — 어느 화면도 하이픈 표기를 쓰지 않으므로 받아 줄 이유가 없다.
     */
    private static String normalize(String rawCode) {
        return rawCode.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    /** 평문 코드의 SHA-256 해시(hex 소문자 64자). */
    private static String hash(String rawCode) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawCode.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e); // 표준 JDK에 항상 존재
        }
    }
}
