# syntax=docker/dockerfile:1

# --- 빌드 스테이지: JDK 21로 부트 실행가능 jar 생성 ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# 의존성 캐시 레이어: 빌드 스크립트/래퍼 먼저 복사
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x ./gradlew && ./gradlew dependencies --no-daemon || true

# 소스 복사 후 빌드. 테스트는 CI에서 별도 게이트로 돌리므로 이미지 빌드에선 제외.
COPY src src
RUN ./gradlew bootJar -x test --no-daemon

# --- 런타임 스테이지: 가벼운 JRE만 ---
FROM eclipse-temurin:21-jre
WORKDIR /app

# 부트 jar만 복사(plain jar는 build.gradle에서 비활성)
COPY --from=build /app/build/libs/*.jar app.jar

# 비root로 돌린다 — 컨테이너 안에서 RCE가 나도 곧바로 root를 쥐지 못하게. jar는 root 소유 읽기전용으로
# 남겨 앱이 자기 코드를 덮어쓰지 못하게 한다(그래서 /app을 chown 하지 않는다). 쓰기가 필요한 곳은
# /tmp(1777) 뿐이다 — 이 앱은 파일을 쓰지 않는다.
#
# ⚠️ uid를 숫자로 못 박고, deploy/render-env.sh 의 APP_UID 와 **한 쌍**으로 유지한다.
#    그 스크립트가 토스 mTLS PEM(600)을 이 uid로 chown 한다. 둘이 어긋나면 컨테이너가 PEM을 못 읽는데,
#    Spring SSL 번들은 **지연 생성**이라 앱은 멀쩡히 뜨고 헬스도 통과한다 — 토스 로그인만 조용히 죽는다.
#    (그 무성 장애를 잡으려고 deploy-on-ec2.sh 가 전환 전에 가독성을 직접 확인한다.)
# gid도 숫자로 못 박는다. `useradd --user-group`은 그룹을 **시스템 대역(999 등)**에 만들어 uid와 어긋나는데,
# 600 파일은 소유자 비트만 보므로 그 불일치가 지금은 티가 안 나고 나중에(예: 640으로 완화) 조용히 깨진다.
RUN groupadd --system --gid 10001 app \
 && useradd --system --uid 10001 --gid 10001 --home-dir /app --shell /usr/sbin/nologin app
USER 10001:10001

EXPOSE 8080
# 운영 프로필 활성화 — DB 접속 등은 컨테이너 환경변수로 주입(application-prod.properties)
ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
