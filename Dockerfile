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

EXPOSE 8080
# 운영 프로필 활성화 — DB 접속 등은 컨테이너 환경변수로 주입(application-prod.properties)
ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
