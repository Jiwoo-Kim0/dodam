# 빌드 스테이지 — 테스트까지 여기서 수행한다.
# (Jenkins 컨테이너에는 JDK21/gradle이 없고, DooD 환경에서 워크스페이스 마운트가
#  호스트 경로와 어긋나므로, 빌드/테스트를 전부 이미지 빌드 안에서 처리한다.)
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /workspace

# 의존성 레이어 캐싱: 소스보다 먼저 복사한다.
COPY gradlew ./
COPY gradle gradle
COPY settings.gradle build.gradle gradle.properties ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

COPY src src
# gradle이 bootJar와 plain jar 두 개를 만들기 때문에, 실행 가능한 쪽만 골라
# 고정된 이름으로 정규화한다. (와일드카드로 COPY하면 classic builder에서
# "destination must be a directory"로 실패한다)
RUN ./gradlew --no-daemon clean build \
    && cp "$(ls build/libs/*.jar | grep -v -- '-plain\.jar$' | head -1)" /workspace/app.jar

# 런타임 스테이지
FROM eclipse-temurin:21-jre
WORKDIR /app

# healthcheck용 curl
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=builder /workspace/app.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD curl -fsS http://localhost:8080/api/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
