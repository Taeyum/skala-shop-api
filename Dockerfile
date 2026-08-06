# docker compose up 한 줄로 기동시키기 위한 최소 구성.
# 하드닝(non-root 유저, layered jar, JRE alpine 슬림화)은 Phase 5에서 진행한다 — PLAN.md 참조

FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# 의존성 해석을 소스 복사보다 먼저 — src만 바뀌면 이 레이어가 캐시된다
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

COPY src src
# 이미지 빌드 단계에서 테스트를 돌리면 Testcontainers가 DinD를 요구한다. 테스트는 CI/로컬에서 실행
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
