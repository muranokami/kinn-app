# ==============================================================
# kinn-app 本番用Dockerイメージ(Render等のPaaSデプロイ向け)。
#
# マルチステージビルド:
# 1. build stage: Maven + JDK17でソースからkinn-app.jarをビルドする
#    (テストはここでは実行しない。CIで別途 `mvn test` を回す想定。
#    ビルド環境にPostgres等の外部依存が無くても失敗しないようにするため)。
# 2. runtime stage: JRE(JDKではない。実行に不要なコンパイラ等を含めずイメージを小さくする)
#    のみを含む軽量イメージにjarだけをコピーして実行する。
#
# 実行時の設定は環境変数で渡す(コードにもDockerfileにも秘密情報は書かない。
# README「秘匿情報の扱い」と同じ方針):
#   SPRING_PROFILES_ACTIVE=prod (本番プロファイルを有効化。ProductionSafetyCheckerが
#     DB_PASSWORD・APP_HEALTH_ENCRYPTION_KEYの未設定/既定値のままの起動を拒否する)
#   DB_USERNAME / DB_PASSWORD / SPRING_DATASOURCE_URL (接続先DB。Neon等の接続情報)
#   APP_HEALTH_ENCRYPTION_KEY (openssl rand -base64 32 で生成した本番用の鍵)
#   PORT (Renderが自動的に注入する。通常は自分で設定不要)
# ==============================================================

# ---- build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# 依存関係だけを先にダウンロードしてレイヤーキャッシュを効かせる
# (pom.xmlが変わらない限り、ソース変更のたびに依存を再ダウンロードしない)
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

COPY src ./src
RUN mvn -q -B package -DskipTests

# ---- runtime stage ----
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# rootで実行しない(コンテナが乗っ取られた場合の影響範囲を減らすための最小限の対策)
RUN useradd --system --no-create-home --shell /usr/sbin/nologin appuser
COPY --from=build /workspace/target/kinn-app.jar app.jar

# logback-spring.xml がセキュリティログを logs/security.log (=/app/logs/security.log) に
# 書き出す設定になっている(本番プロファイルではコンソール出力のみに切り替わるが、
# SPRING_PROFILES_ACTIVE未設定時など念のためのフォールバックとしてディレクトリを用意しておく)。
# 非rootユーザーが書き込めるよう、chownはUSER切り替え前に行う。
RUN mkdir -p /app/logs && chown -R appuser:appuser /app
USER appuser

# Renderは PORT 環境変数でリッスンポートを渡してくる(application.propertiesの
# server.port=${PORT:8080}参照)。EXPOSEはドキュメント目的のみで実際の待受には影響しない。
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
