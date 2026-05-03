FROM gradle:9.4.0-jdk21 AS build
RUN apt-get update && apt-get install -y libatomic1 curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app

# コミットハッシュをビルド引数で受け取る（.git/ のコピー不要）
ARG COMMIT_HASH=unknown
ENV COMMIT_HASH=${COMMIT_HASH}

# MaxMind GeoLite2-City DB を build 時に DL（ライセンスキー指定時のみ）
# 鍵未指定なら DB ファイルは生成されず、ランタイムで NoOpIpGeolocationService にフォールバックする。
ARG MAXMIND_LICENSE_KEY=""
RUN mkdir -p /geoip && \
    if [ -n "$MAXMIND_LICENSE_KEY" ]; then \
      cd /tmp && \
      curl -fsSL "https://download.maxmind.com/app/geoip_download?edition_id=GeoLite2-City&license_key=${MAXMIND_LICENSE_KEY}&suffix=tar.gz" -o GeoLite2-City.tar.gz && \
      tar -xzf GeoLite2-City.tar.gz && \
      cp GeoLite2-City_*/GeoLite2-City.mmdb /geoip/GeoLite2-City.mmdb && \
      rm -rf GeoLite2-City.tar.gz GeoLite2-City_*; \
    else \
      echo "MAXMIND_LICENSE_KEY not provided; GeoIP DB will not be bundled."; \
    fi

# 依存解決のレイヤーキャッシュ: ビルドスクリプトのみ先にコピー
COPY gradle/ gradle/
COPY build.gradle.kts settings.gradle.kts gradle.properties gradlew ./
COPY build-logic/ build-logic/
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
RUN gradle :server:buildFatJar --no-daemon \
    -Dorg.gradle.jvmargs="-Xmx2g -Dfile.encoding=UTF-8" \
    -Dorg.gradle.parallel=false

FROM eclipse-temurin:21.0.10_7-jre-noble
WORKDIR /app
COPY --from=build /app/server/build/libs/*-all.jar app.jar
# build ステージで DL した GeoLite2 DB を取り込む。
# 鍵未指定だった場合 /geoip は空ディレクトリのままなので何もコピーされない。
COPY --from=build /geoip/ /app/data/
ENV GEOIP_DB_PATH=/app/data/GeoLite2-City.mmdb
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
  CMD curl -sf http://localhost:8080/ > /dev/null || exit 1
CMD ["java", "-jar", "app.jar"]
