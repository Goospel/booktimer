package com.booktimer.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 운영 BCrypt 강도 가드 — 운영 설정 파일·배포 env 렌더러에 {@code booktimer.security.bcrypt-strength}를
 * 두지 못하게 한다(T-235).
 *
 * <p>운영 강도는 {@link PasswordEncoderConfig}의 {@code @Value} 기본값 10에만 맡긴다. 그 기본값은
 * {@code PasswordEncoderConfigTest}가 잠그지만, 메인 {@code application.properties}에 {@code =4}를 넣는
 * 회귀는 테스트 컨텍스트가 같은 이름의 테스트 리소스로 메인 파일을 가려서 구조적으로 못 잡는다 —
 * 그래서 파일을 직접 읽는다. 표기는 relaxed binding을 따라 구분자·대소문자를 지워 비교한다
 * ({@code bcrypt-strength} · {@code BCRYPT_STRENGTH} · {@code bcryptStrength} 모두 잡힘).
 */
class BcryptStrengthProductionGuardTest {

    private static final Path MAIN_RESOURCES = Path.of("src/main/resources");
    private static final Path RENDER_ENV = Path.of("deploy/render-env.sh");

    private static List<Path> guardedFiles() throws IOException {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> s = Files.list(MAIN_RESOURCES)) {
            s.filter(p -> p.getFileName().toString().matches("application.*\\.(properties|ya?ml)"))
                    .forEach(files::add);
        }
        files.add(RENDER_ENV);
        return files;
    }

    @Test
    @DisplayName("검사 대상에 메인 application.properties와 render-env.sh가 실제로 있다 (공허 통과 방지)")
    void guardedFiles_includeKnownTargets() throws IOException {
        List<Path> files = guardedFiles();

        assertThat(files).contains(MAIN_RESOURCES.resolve("application.properties"), RENDER_ENV);
        assertThat(files).allSatisfy(p -> assertThat(p).isRegularFile());
    }

    @Test
    @DisplayName("운영 설정·배포 env에 BCrypt 강도 설정이 없다 — 운영 강도는 기본값 10만 쓴다")
    void productionConfig_hasNoBcryptStrength() throws IOException {
        for (Path p : guardedFiles()) {
            String normalized = Files.readString(p).toLowerCase(Locale.ROOT).replaceAll("[-_.]", "");
            assertThat(normalized.contains("bcryptstrength")).as(p + "에 BCrypt 강도 설정이 있다").isFalse();
        }
    }
}
