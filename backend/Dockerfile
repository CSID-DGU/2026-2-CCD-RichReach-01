# ── build ──
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
COPY src src

# Gradle 캐시를 BuildKit 캐시 마운트로 재사용해 재빌드 시간을 줄인다.
RUN --mount=type=cache,target=/root/.gradle \
    chmod +x gradlew && ./gradlew --no-daemon bootJar -x test

# ── run ──
# OR-Tools 등 네이티브 라이브러리가 glibc를 필요로 하므로 alpine(musl)은 사용하지 않는다.
FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app \
    && useradd --system --gid app --no-create-home app

WORKDIR /app
COPY --from=build /workspace/build/libs/app.jar app.jar

USER app
ENV TZ=Asia/Seoul
EXPOSE 8080

ENTRYPOINT ["java", "-Duser.timezone=Asia/Seoul", "-jar", "app.jar"]
