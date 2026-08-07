# 멀티스테이지 빌드 — 최종 이미지에 JDK·Gradle·소스가 남지 않는다.
# 크기 문제만이 아니다: 컴파일러와 빌드 도구가 운영 이미지에 있으면 공격자가 쓸 수 있다.

# ── 1단계: 빌드 ─────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# 의존성 해석을 소스 복사보다 먼저 — src만 바뀌면 이 레이어가 캐시된다
COPY gradlew settings.gradle build.gradle lombok.config ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

COPY src src
# 이미지 빌드 단계에서 테스트를 돌리면 Testcontainers가 DinD를 요구한다. 테스트는 CI/로컬에서 실행
RUN ./gradlew bootJar --no-daemon -x test

# ── 2단계: layered jar 분해 ────────────────────────────────────────────
# 부트 jar를 통째로 COPY하면 코드 한 줄만 바뀌어도 수십 MB 레이어가 통째로 새로 만들어진다.
# 의존성/로더/스냅샷/애플리케이션으로 나누면 **바뀌는 것만** 다시 전송된다 —
# 이 프로젝트에서 application 레이어는 수백 KB고 나머지는 재사용된다.
FROM eclipse-temurin:17-jre AS extract
WORKDIR /layers
COPY --from=build /workspace/build/libs/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# ── 3단계: 실행 ────────────────────────────────────────────────────────
# JRE만 — JDK에는 컴파일러·디버거·jmap 같은 도구가 들어 있고 운영에 필요 없다
FROM eclipse-temurin:17-jre
WORKDIR /app

# non-root 실행. root로 돌면 컨테이너 탈출 시 호스트 권한이 그대로 넘어가고,
# 취약점 하나가 전체 장악으로 이어진다. --system은 로그인 불가 계정을 만든다
RUN groupadd --system --gid 1001 shop \
 && useradd  --system --uid 1001 --gid shop --no-create-home shop

# 레이어 순서 = 변경 빈도의 역순. 뒤로 갈수록 자주 바뀐다
COPY --from=extract --chown=shop:shop /layers/dependencies/         ./
COPY --from=extract --chown=shop:shop /layers/spring-boot-loader/   ./
COPY --from=extract --chown=shop:shop /layers/snapshot-dependencies/ ./
COPY --from=extract --chown=shop:shop /layers/application/          ./

USER shop
EXPOSE 8080

# 컨테이너는 CPU·메모리 제한을 받으므로 JVM이 호스트 전체를 보고 힙을 잡으면 OOMKill이 난다.
# MaxRAMPercentage는 **컨테이너에 할당된 메모리**의 비율로 힙을 정한다
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

# HEALTHCHECK는 컨테이너 런타임이 본다. Actuator의 readiness를 그대로 쓴다 —
# "포트가 열렸다"가 아니라 "DB까지 연결돼 트래픽을 받을 수 있다"를 확인한다.
#
# curl을 쓴다. 처음엔 의존성을 줄이려고 셸 내장 /dev/tcp 로 짰는데 **동작하지 않았다** —
# HEALTHCHECK는 /bin/sh 로 실행되고 이 이미지의 /bin/sh 는 dash 다. /dev/tcp 는 bash 확장이라
# "cannot create /dev/tcp/...: Directory nonexistent"(exit 2)로 매번 실패했고,
# 컨테이너가 영원히 health: starting 에 머물렀다. 코드만 봐서는 알 수 없었다 (JOURNAL 2026-08-07).
# eclipse-temurin:17-jre(jammy)에는 curl 이 이미 들어 있어 추가 설치가 필요 없다.
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=5 \
  CMD curl -fsS http://127.0.0.1:8080/actuator/health/readiness | grep -q '"status":"UP"' || exit 1

# exec 형태 + sh -c 조합 — JAVA_OPTS 확장이 필요하면서 PID 1로 실행돼야
# SIGTERM이 JVM에 직접 전달되고 graceful shutdown이 동작한다
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
