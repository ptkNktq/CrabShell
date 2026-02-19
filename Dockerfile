FROM gradle:8.12-jdk21 AS build
RUN apt-get update && apt-get install -y libatomic1 && rm -rf /var/lib/apt/lists/*
WORKDIR /app

# コミットハッシュをビルド引数で受け取る（.git/ のコピー不要）
ARG COMMIT_HASH=unknown
ENV COMMIT_HASH=${COMMIT_HASH}

# 依存解決のレイヤーキャッシュ: ビルドスクリプトのみ先にコピー
COPY gradle/ gradle/
COPY build.gradle.kts settings.gradle.kts gradle.properties gradlew ./
COPY shared/build.gradle.kts shared/
COPY server/build.gradle.kts server/
COPY app/build.gradle.kts app/
COPY core/auth/build.gradle.kts core/auth/
COPY core/network/build.gradle.kts core/network/
COPY core/ui/build.gradle.kts core/ui/
COPY feature/auth/build.gradle.kts feature/auth/
COPY feature/dashboard/build.gradle.kts feature/dashboard/
COPY feature/feeding/build.gradle.kts feature/feeding/
COPY feature/money/build.gradle.kts feature/money/
COPY feature/payment/build.gradle.kts feature/payment/
COPY feature/report/build.gradle.kts feature/report/
COPY feature/settings/build.gradle.kts feature/settings/
COPY gradle/libs.versions.toml gradle/

# ソースコードをコピーしてビルド
COPY shared/ shared/
COPY server/ server/
COPY app/ app/
COPY core/ core/
COPY feature/ feature/
RUN gradle :server:buildFatJar --no-daemon

FROM eclipse-temurin:21-jre-noble
WORKDIR /app
COPY --from=build /app/server/build/libs/*-all.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
  CMD curl -sf http://localhost:8080/ > /dev/null || exit 1
CMD ["java", "-jar", "app.jar"]
