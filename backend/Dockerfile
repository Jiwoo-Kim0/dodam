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

# 비루트 실행 (infra/todo/security.md R3)
# 컨테이너 탈출 취약점이 터졌을 때 피해 범위를 줄인다.
# 8080은 1024 이상이라 비특권 사용자로도 바인딩할 수 있다.
# 앱은 파일을 쓰지 않으므로 /app 소유권만 넘겨주면 충분하다.
RUN useradd --system --uid 10001 --no-create-home --shell /usr/sbin/nologin dodam \
    && chown -R 10001:0 /app
USER 10001

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD curl -fsS http://localhost:8080/api/health || exit 1

# JVM 힙 상한 (infra/todo/day-05.md)
# 상한이 없으면 JVM은 컨테이너 메모리의 약 1/4를 기본 최대 힙으로 잡는데,
# 호스트 16GB 기준으로는 넉넉해 보여도 MySQL(버퍼풀 2GB)·Jenkins·Day 6 모니터링과
# 겹치면 전체가 빠듯해진다. 명시적으로 고정한다.
# MaxRAMPercentage 대신 절대값을 쓰는 이유: 컨테이너 메모리 제한을 아직 걸지 않아
# JVM이 호스트 전체를 기준으로 계산해버리기 때문이다.
ENTRYPOINT ["java", "-XX:MaxHeapSize=1g", "-XX:InitialHeapSize=256m", "-jar", "/app/app.jar"]
