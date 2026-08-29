# 勤 - 勤怠・健康・スケジュール管理アプリ

Spring Boot(Java) + PostgreSQL + HTML/CSS/JavaScript で作成した統合管理アプリです。
トップページから「勤怠管理」「健康管理」「スケジュール管理」の3機能に遷移できます。

- **勤怠管理**: 日ごとの出退勤を入力すると、月ごとに12項目を自動集計
  (勤務日数 / 勤務時間 / 残業時間 / 深夜残業 / 遅早回数 / 欠勤日数 /
  所定休日勤務 / 所定休日深夜 / 有給使用日数 / 振休代休 / 法定休日勤務 / 法定休日深夜)
- **健康管理**: 体重・睡眠時間・歩数・運動時間・血圧・体調を日ごとに記録し、月次で集計する
  従来の月表画面に加えて、以下を備えた拡張版の健康管理システムを搭載しています(詳細は後述)。
  - 健康プロフィール(身長・体重・BMI自動計算・血圧・喫煙/飲酒状況など)
  - 今日の体調チェック(体調5段階・睡眠・疲労度・ストレス度・運動・体温・メモ)
  - 健康スコア(0〜100点、内訳つき、算出ロジックは独立したServiceに集約)
  - 健康状態の推移グラフ(1週間/1か月/3か月/6か月)
  - 勤怠×健康の連携分析(残業時間と健康スコア・睡眠と疲労度などの基本集計)
  - 健康アラート(睡眠不足・高疲労・高ストレス・残業過多の継続を検知する一般的な注意喚起。
    医療診断は行いません)
  - 管理者ダッシュボード(個人情報を含まない、会社・部署単位の集計)
  - **食事管理(Phase 1)**: 朝・昼・夕(将来的な間食にも対応できる構造)の食事を
    「何を食べたか」だけでも手軽に記録でき、料理名・量・カロリー・たんぱく質・脂質・
    炭水化物・食物繊維・塩分・写真URL・メモは任意で追加入力できます。今日の食事内容と
    栄養素の合計を1画面で可視化し、今日/昨日/1週間/1か月の履歴と朝昼夕の記録日数の
    傾向も確認できます。健康管理トップページにも今日の食事ウィジェットを表示し、
    健康ダッシュボードと緩やかに統合しています。週間/月間の詳しい分析、勤怠・健康データ
    との連携分析、AIによる献立提案・買い物リスト生成は、Phase 2以降で段階的に追加予定です
    (未実装)。
- **スケジュール管理**: 予定(日時・タイトル・分類・メモ)を登録・編集・削除し、月ごとに一覧表示

さらに、Webアプリとは独立したPython分析スクリプトで、勤怠・健康データを横断的に
分析したレポートを出力できます(`python/health_report.py` は従来の月次健康記録との
横断分析、`python/health_check_analysis.py` は今日の体調チェックを使った
残業時間×健康スコアなどの簡易統計分析です)。

## 動作環境

- JDK 17以上
- Maven 3.8以上(またはVS Codeの拡張機能に同梱のもの)
- PostgreSQL 13以上
- Python 3.9以上(分析スクリプトを使う場合のみ)

VS Codeでは以下の拡張機能を入れておくとスムーズです。
- **Extension Pack for Java**(Microsoft)
- **Spring Boot Extension Pack**(VMware / Microsoft)

## セットアップ手順

### 1. PostgreSQLにデータベースを作成

```sql
CREATE DATABASE kinn_db;
```

### 2. 接続情報を設定

`src/main/resources/application.properties` を自分の環境に合わせて書き換えます。

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/kinn_db
spring.datasource.username=postgres
spring.datasource.password=postgres
```

テーブルは初回起動時に**Flyway**が自動的に作成します。以前は
`spring.jpa.hibernate.ddl-auto=update`でHibernateに自動生成させていましたが、
マイグレーションのバージョン管理ができないため、Flywayへ移行しました
(`spring.jpa.hibernate.ddl-auto`は現在`validate`。Hibernateは起動時にEntityの
定義とDBの実スキーマが一致しているかをチェックするだけで、スキーマの作成・変更は
一切行いません)。

#### 今後のテーブル変更の運用

Entityクラスを変更するだけではDBのテーブルは変わりません。テーブル構造の変更が
必要な場合は、必ず対応するマイグレーションファイルを追加してください。

1. `src/main/resources/db/migration/` に、`V{番号}__{説明}.sql` という名前で
   SQLファイルを追加する(番号は既存の最大値+1、説明は英数字とアンダースコアのみ)。
   - 例: `V2__add_email_verified_to_app_user.sql`
   - 例: `V3__create_notification_channel.sql`
2. アプリを起動すると、Flywayが未適用のマイグレーションを`flyway_schema_history`
   テーブルの記録と照合し、新しいものだけ順番に自動実行する。
3. 一度リリース済みのマイグレーションファイル(内容)は変更しないこと
   (Flywayはチェックサムで改ざんを検知してエラーにする)。修正が必要な場合は
   新しい番号のマイグレーションファイルを追加すること。

`src/main/resources/db/migration/V1__baseline.sql`は、Flyway導入時点
(2026-08-29)で実際に稼働していたスキーマをそのまま書き起こした「後追いベースライン」
です。既にテーブルが存在する既存のDB(本番・開発)に対しては
`spring.flyway.baseline-on-migrate=true`により、V1は実行されず「適用済み」として
記録されるだけになります(baseline化)。まだテーブルが存在しない新しいDBに対しては、
V1がそのまま実行されて同じスキーマが作られます。

### 3. 起動

VS Codeでこのフォルダを開き、`KinnApplication.java` を実行(F5)するか、
ターミナルで以下を実行します。

```bash
mvn spring-boot:run
```

> 環境によっては、JDKのバージョンとLombokの相性でアノテーション処理(getter/setter等の自動生成)が
> 効かずコンパイルエラーになることがあります。その場合は `pom.xml` の `lombok.version` を
> 手元のJDKに対応する新しいバージョンに変更してください(`mvn -q dependency:tree` で実際に
> 解決されているバージョンを確認できます)。

### 4. ブラウザで開く

```
http://localhost:8080
```

トップページが表示されます。3つのカードから各機能に遷移してください。
各画面で年月を選んで「読込」を押すとその月のデータが表示され、入力後「保存」で
サーバー(PostgreSQL)に保存されます(スケジュール管理は予定ごとに追加・編集・削除)。

## 計算の前提(必要に応じて変更してください)

`AttendanceService.java` の先頭にある定数で調整できます。

| 項目 | デフォルト値 |
|---|---|
| 所定労働時間 | 8時間(残業時間 = 実働 − 8時間) |
| 深夜帯 | 22:00 〜 翌5:00 |

- 「所定休日勤務」「法定休日勤務」は、その区分の日に出退勤時刻が入力されていれば1日としてカウントします。
- 「有給使用日数」は `paidLeaveUnit`(1.0=全休、0.5=半休)で調整可能です。
- 「振休代休」は振替休日・代休をまとめて日数カウントしています。

## API

### 勤怠管理

| メソッド | パス | 説明 |
|---|---|---|
| GET | `/api/attendance/{year}/{month}` | 指定月の日次データ＋集計を取得 |
| POST | `/api/attendance/{year}/{month}` | 指定月の日次データを一括保存 |
| PUT | `/api/attendance/day` | 1日分だけ保存 |
| DELETE | `/api/attendance/day/{date}` | 1日分を削除(未入力に戻す) |

### 健康管理(従来: 月次健康記録)

| メソッド | パス | 説明 |
|---|---|---|
| GET | `/api/health/{year}/{month}` | 指定月の日次データ＋集計を取得 |
| POST | `/api/health/{year}/{month}` | 指定月の日次データを一括保存 |
| PUT | `/api/health/day` | 1日分だけ保存 |
| DELETE | `/api/health/day/{date}` | 1日分を削除(未入力に戻す) |

### 健康管理(拡張機能)

| メソッド | パス | 説明 |
|---|---|---|
| GET | `/api/health/profile` | 健康プロフィールを取得(BMIは自動計算して返す) |
| PUT | `/api/health/profile` | 健康プロフィールを保存 |
| GET | `/api/health/check?date=` | 指定日(省略時は今日)の体調チェックを取得 |
| PUT | `/api/health/check` | 体調チェックを保存 |
| GET | `/api/health/check/history?from=&to=` | 体調チェックの履歴を取得 |
| GET | `/api/health/score?date=` | 指定日(省略時は今日)の健康スコア＋内訳を取得 |
| GET | `/api/health/score/trend?period=` | 健康状態の推移(1w/1m/3m/6m)を取得 |
| GET | `/api/health/alerts?days=` | 健康アラート(一般的な注意喚起)を評価・取得 |
| GET | `/api/health/analysis?period=` | 勤怠×健康の連携分析(基本集計)を取得 |
| GET | `/api/admin/health/dashboard?days=` | 管理者向け: 会社・部署単位の集計を取得(個人情報は含まない) |

### 食事管理(Phase 1)

| メソッド | パス | 説明 |
|---|---|---|
| GET | `/api/meal/day?date=` | 指定日(省略時は今日)の食事記録+栄養合計を取得 |
| GET | `/api/meal/history?from=&to=` | 期間内の食事記録を日付ごとに取得(履歴画面用) |
| PUT | `/api/meal` | 食事を登録・更新(朝/昼/夕は当日分を上書き、間食は新規追加) |
| DELETE | `/api/meal/{id}` | 食事記録を削除(主に間食の取り消し用) |

朝・昼・夕は同じ日・区分のレコードを上書きする運用(体調チェックと同様の1日1件)、
間食(`SNACK`)は`unique`制約を設けておらず1日に複数件登録できます。
カロリー・たんぱく質・脂質・炭水化物・食物繊維・塩分・写真URL・メモはすべて任意項目で、
「食べたもの(`items`)」だけの入力でも登録できます。

### スケジュール管理

| メソッド | パス | 説明 |
|---|---|---|
| GET | `/api/schedule/{year}/{month}` | 指定月の予定一覧＋集計を取得 |
| POST | `/api/schedule` | 予定を新規登録 |
| PUT | `/api/schedule/{id}` | 予定を更新 |
| DELETE | `/api/schedule/{id}` | 予定を削除 |

いずれもクエリパラメータ `employeeId`(省略時 `default`)で従業員を切り替えられます
(複数人管理への拡張用)。

## セキュリティ

このアプリは会社(`company`)単位のマルチテナント構成で、ログイン(`AppUser`)・
ロール(`ADMIN`/`USER`)ベースのアクセス制御を実装しています。実装箇所は主に
`SecurityConfig` / `AuthController` / `AuthService` / `PasswordService` /
`RateLimiter` / `LoginAttemptListener` / `AccountSecurityLogService`
(いずれも `src/main/java/com/kinn/app/...`)です。

### 認証・パスワード

- パスワードは BCrypt でハッシュ化して保存し、平文は一切保持しません。
- ログイン試行を一定回数失敗すると、そのアカウントを一時的にロックします
  (既定: 5回失敗で15分ロック。`app.security.login.*` で調整可能)。
- パスワードのリセットは**管理者による強制リセットのみ**です。メール経由の
  セルフサービス型リセット(本人がメールのリンクから再設定する方式)は、
  外部のメール経路を伴い個人情報漏洩の経路になり得るという判断であえて
  実装していません。
- 現時点で多要素認証(MFA/TOTP)は導入していません。

### セッション・CSRF

- セッションベース認証(HttpSession)。ログイン成功時にセッションIDを
  再発行し、セッション固定攻撃を防ぎます。
- 同一アカウントの同時ログインセッション数を制限します
  (既定: 3セッションまで。超過時は最も古いセッションを失効)。
- CSRF対策として Cookie(`XSRF-TOKEN`)+ リクエストヘッダ
  (`X-XSRF-TOKEN`)によるトークン検証を行います。
- セッション用Cookie(`JSESSIONID`)・CSRF用Cookie(`XSRF-TOKEN`)は
  `Secure`/`SameSite` 属性を設定可能です。**本番環境では必ずHTTPS配信の上で
  `--spring.profiles.active=prod` を指定してください**
  (`application-prod.properties` で `Secure=true` / `SameSite=Strict` に
  厳格化されます。開発時の既定値はHTTP動作を優先し `Secure=false` です)。

### アクセス制限・監査ログ

- ログイン・新規登録・会社コード照会 API に IPアドレス単位のレート制限を
  かけています(既定: ログイン10回/60秒、新規登録5回/60秒)。
- 管理者専用の画面・APIは Spring Security のロール制御(`hasRole("ADMIN")`)
  で保護し、一般ユーザーからのアクセスは拒否されます。
- ログイン成功/失敗・ロック・管理者による操作などは `account_security_log`
  テーブルに、健康データへのアクセス(誰が・いつ・誰の記録を見たか)は
  `health_audit_log` テーブルに、それぞれ改ざん検知用の追記専用ログとして
  記録されます(管理者は `admin-health-audit-log.html` から検索可能)。
- 認証・認可レベルで拒否されたアクセス(未ログイン・権限不足)は
  アプリの監査テーブルには本人を特定できないため記録できませんが、
  専用のセキュリティログファイル(`logs/security.log`)に別途記録します。
  このファイルは実際のアクセス試行(IPアドレス等)を含むため
  `.gitignore` でリポジトリから除外しています。

### HTTPセキュリティヘッダー

- Content-Security-Policy(スクリプト/画像/フォント等を自ドメインに限定)、
  HTTP Strict Transport Security、`X-Frame-Options: DENY`、
  `X-Content-Type-Options: nosniff` を設定しています。

### 通知・外部送信

- 健康アラートやタスク期限アラートは、メール/Slack等の外部への通知送信を
  行わず、本人がアプリ画面を開いた際にその場で表示するアプリ内アラートのみ
  にしています。個人情報を含みうる通知を外部チャネルへ送信する経路を
  そもそも持たない設計です。

### 秘匿情報の扱い

- DB接続パスワードやAI APIキー等は `application.properties` 内で
  環境変数(`${DB_PASSWORD:postgres}` など)から注入する形にしており、
  実際の値をリポジトリにコミットしていません。ローカル動作用のデフォルト値
  (`postgres` 等)は本番では必ず環境変数で上書きしてください。
- `.env` / `application-local.properties` / `*.log` / `logs/` は
  `.gitignore` で除外し、実データ・実ログをリポジトリに含めないようにして
  います。

### 既知の制約

- 現状はアプリケーションサーバー自体はHTTPで動作します(HTTPS終端は
  リバースプロキシ等での対応を想定)。本番投入時は、HTTPS配信・
  `prod` プロファイルの有効化・DB接続情報等の環境変数の上書きをあわせて
  行ってください。
- 依存ライブラリ(Maven)に既知の脆弱性が見つかった場合、GitHub Dependabot
  (`.github/dependabot.yml`)が自動的に更新用のPull Requestを作成します
  (週次チェック。GitHub Actionsのワークフローを追加した場合は月次チェックも
  自動的に有効になります)。実際にPRが作られるタイミングはGitHub側のスケジュール
  によるため、リポジトリ側の設定だけでは即時には反映されません。

## Python分析スクリプト

`python/health_report.py` は、Webアプリの実行フローとは独立して動く分析バッチです。
PostgreSQLに直接接続し、指定した月の勤怠・健康データを集計してレポートを出力します。

```bash
cd python
pip install -r requirements.txt
python3 health_report.py --year 2026 --month 8
```

- `--employee-id`: 集計対象の社員ID(既定: `default`)
- `--out`: 出力先ファイル(省略時は標準出力)
- 接続情報は環境変数 `KINN_DB_HOST` / `KINN_DB_PORT` / `KINN_DB_NAME` /
  `KINN_DB_USER` / `KINN_DB_PASSWORD` で上書き可能(既定値は `application.properties` と同じ)

睡眠不足の傾向や残業過多、体調不良の日数など、簡単な「気づき」も出力します。

`python/health_check_analysis.py` は、拡張健康管理機能の「今日の体調チェック」
(`health_check`)と勤怠実績(`attendance_record`)を使い、残業時間と健康スコアの
相関係数、睡眠時間と疲労度の相関係数など、基本的な統計分析を行う独立バッチです。

```bash
cd python
python3 health_check_analysis.py --days 90
```

同じく `--employee-id` / `--out` / `KINN_DB_*` 環境変数に対応しています。
将来的に予測モデルやAI/機械学習を追加する場合も、このスクリプトのように
「DBから読む→分析する→出力する」独立バッチとして追加していく想定です
(Webアプリ側からProcessBuilder等で呼び出す方式は採用していません)。

## ディレクトリ構成

```
kinn-app/
├── pom.xml
├── src/main/java/com/kinn/app/
│   ├── KinnApplication.java        起動クラス
│   ├── entity/                     AttendanceRecord, HealthRecord, ScheduleEvent,
│   │                                HealthProfile, HealthCheck, HealthAlert,
│   │                                MealRecord / MealType(食事管理) ほか
│   ├── repository/                 JPAリポジトリ
│   ├── service/                    AttendanceService / HealthService / ScheduleService /
│   │                                HealthProfileService / HealthCheckService /
│   │                                HealthScoreService(スコア算出ロジックを独立管理) /
│   │                                HealthTrendService / HealthAlertService /
│   │                                HealthAnalysisService / AdminHealthService /
│   │                                MealService(食事管理、Phase 1)
│   ├── controller/                 REST API(拡張健康管理は複数コントローラに分割、
│   │                                MealControllerが食事管理API)
│   └── dto/                        DTO(APIの入出力用。MealRecordDto / DayMealsDto /
│                                    MealNutritionSummaryDtoなど)
├── src/main/resources/
│   ├── application.properties      DB接続設定
│   └── static/                     フロントエンド(HTML/CSS/JS)
│       ├── index.html              トップページ
│       ├── attendance.html         勤怠管理
│       ├── health.html             健康管理(従来: 月次健康記録)
│       ├── health-top.html         健康管理トップ(拡張機能のハブ)
│       ├── health-profile.html     健康プロフィール
│       ├── health-check.html       今日の体調チェック
│       ├── health-score.html       健康スコア
│       ├── health-history.html     健康履歴
│       ├── health-graph.html       健康グラフ(推移)
│       ├── health-analysis.html    勤怠×健康分析
│       ├── admin-health.html       管理者ダッシュボード
│       ├── meal.html               食事記録(朝・昼・夕・間食の入力+今日の食事の可視化)
│       ├── meal-history.html       食事履歴(今日/昨日/1週間/1か月)
│       ├── schedule.html           スケジュール管理
│       ├── css/                    style.css(共通) / top.css(トップページ演出) /
│       │                            health-extra.css(拡張健康管理・食事管理の追加スタイル)
│       └── js/                     top.js / app.js / health.js / schedule.js /
│                                    health-common.js(共通ユーティリティ・簡易チャート描画) /
│                                    health-profile.js / health-check.js / health-score.js /
│                                    health-history.js / health-graph.js /
│                                    health-analysis.js / admin-health.js /
│                                    health-top.js(今日の食事ウィジェット) /
│                                    meal.js / meal-history.js
└── python/
    ├── health_report.py            勤怠・健康データの分析バッチ(独立スクリプト)
    ├── health_check_analysis.py    勤怠×健康の統計分析バッチ(独立スクリプト)
    └── requirements.txt
```
