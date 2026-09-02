# 勤 - 勤怠・健康・スケジュール管理アプリ

Spring Boot(Java) + PostgreSQL + HTML/CSS/JavaScript で作成した統合管理アプリです。
トップページから「勤怠管理」「健康管理」「スケジュール管理」「お知らせ」「残業申請」
「タスク管理」の6機能に遷移できます。管理者は別途、管理者コンソール(`admin-top.html`)
から組織・従業員・お知らせ・残業申請・タスク・健康監査ログ等を横断的に管理できます。

- **勤怠管理**: 日ごとの出退勤を入力すると、月ごとに12項目を自動集計
  (勤務日数 / 勤務時間 / 残業時間 / 深夜残業 / 遅早回数 / 欠勤日数 /
  所定休日勤務 / 所定休日深夜 / 有給使用日数 / 振休代休 / 法定休日勤務 / 法定休日深夜)
- **健康管理**: 体重・睡眠時間・歩数・運動時間・血圧・体調を日ごとに記録し、月次で集計する
  従来の月表画面に加えて、以下を備えた拡張版の健康管理システムを搭載しています(詳細は後述)。
  - 健康プロフィール(身長・体重・BMI自動計算・血圧・喫煙/飲酒状況など)
  - 今日の体調チェック(体調5段階・睡眠・疲労度・運動・体温・メモ)
  - 健康スコア(0〜100点、内訳つき、算出ロジックは独立したServiceに集約)
  - 健康状態の推移グラフ(1週間/1か月/3か月/6か月)
  - 勤怠×健康の連携分析(残業時間と健康スコア・睡眠と疲労度などの基本集計)
  - 健康管理は診断・治療の提案を行わず、健康情報の記録・閲覧・可視化の範囲に限定しています。
    以前は睡眠不足・高疲労・残業過多を検知する健康アラート機能や、会社・部署単位で
    集計する管理者ダッシュボードも搭載していましたが、本アプリはポートフォリオとしての
    位置づけ上、健康管理は管理者が確認するものではなく本人が自由なタイミングで記録する
    個人利用の機能に限定する方針としたため、いずれも完全に削除しています
    (docs/health-audit-legal-checklist.md 参照)。以前は「ストレス度」も測定項目・
    アラートに含めていましたが、労働安全衛生法上のストレスチェック制度(第66条の10)と
    紛らわしい外形(心理的な負担の程度を個別に測定・表示する機能)を作らないため、
    ストレス関連の測定・表示も完全に削除しています
  - **食事管理(Phase 1)**: 朝・昼・夕(将来的な間食にも対応できる構造)の食事を
    「何を食べたか」だけでも手軽に記録でき、料理名・量・カロリー・たんぱく質・脂質・
    炭水化物・食物繊維・塩分・写真URL・メモは任意で追加入力できます。今日の食事内容と
    栄養素の合計を1画面で可視化し、今日/昨日/1週間/1か月の履歴と朝昼夕の記録日数の
    傾向も確認できます。健康管理トップページにも今日の食事ウィジェットを表示し、
    健康ダッシュボードと緩やかに統合しています。AIによる献立提案(後述「レシピ・AI献立
    提案」)はPhase 3として実装済みです。週間/月間の詳しい分析、勤怠・健康データとの
    連携分析、買い物リスト生成は今後の拡張候補です(未実装)。
- **スケジュール管理**: 予定(日時・タイトル・分類・メモ)を登録・編集・削除し、月ごとに一覧表示
- **お知らせ**: 管理者が全社・部署単位でお知らせを配信し、公開日時(予約投稿)・
  表示終了日時を設定できます。一般ユーザーは既読管理・未読件数の確認ができます。
- **残業申請**: 「◯月◯日に◯時間残業予定」という事前申請を行い、管理者が承認/却下します。
  勤怠実績(実際の打刻から計算される残業時間)とは別物として扱い、両方を並べて確認できます。
- **タスク管理**: 担当者・期限・優先度付きのタスクを登録・編集・削除できます。
  期限が近い/超過したタスクは、メール等の外部通知を使わずアプリ内アラートとして
  本人が画面を開いた際に表示されます。管理者は部署内のタスクを横断的に確認できます。
- **レシピ・AI献立提案**: 前日の食事記録・健康情報・食の好みをもとに、朝昼夕の献立を
  AI(Anthropic Messages API)が提案します(`AiMealSuggestionController`)。AI未設定・
  失敗時はルールベースの提案ロジックに自動フォールバックするため、AI APIキーが無い
  環境でも動作します。提案を保存するとレシピ(材料・手順・栄養素)として管理でき、
  AI提案の履歴も確認できます(`meal-ai-history.html`)。

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
   - 例: `V2__remove_stress_related_health_data.sql`(実例。ストレス関連機能撤廃に伴う
     データクリーンアップ。テーブル定義の変更だけでなく、既存データの削除・更新も
     マイグレーションとして書ける)
   - 例: `V3__add_email_verified_to_app_user.sql`
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
| GET | `/api/health/analysis?period=` | 勤怠×健康の連携分析(基本集計)を取得 |

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

- タスク期限アラートは、メール/Slack等の外部への通知送信を行わず、本人が
  アプリ画面を開いた際にその場で表示するアプリ内アラートのみにしています。
  個人情報を含みうる通知を外部チャネルへ送信する経路をそもそも持たない設計です。
  なお健康管理機能は診断・治療の提案を行わず記録・閲覧・可視化に限定する方針のため、
  自動判定で注意喚起を生成する健康アラート機能自体を撤廃しています。

### 秘匿情報の扱い

- DB接続パスワードやAI APIキー等は `application.properties` 内で
  環境変数(`${DB_PASSWORD:postgres}` など)から注入する形にしており、
  実際の値をリポジトリにコミットしていません。ローカル動作用のデフォルト値
  (`postgres` 等)は本番では必ず環境変数で上書きしてください。
- `.env` / `application-local.properties` / `*.log` / `logs/` は
  `.gitignore` で除外し、実データ・実ログをリポジトリに含めないようにして
  います。

### 健康管理データの保存時暗号化

- 身長・体重・血圧・体温・睡眠時間・疲労度・運動時間・歩数・喫煙/飲酒状況・
  体調・メモ(`health_profile` / `health_record` / `health_check`)は要配慮
  個人情報にあたるため、DBへ保存する前にアプリ層でAES-256-GCM暗号化して
  います(`HealthDataEncryptor` / JPA `AttributeConverter`。V5マイグレーション
  で対象列を`text`型へ変更済み)。
- 食の好み・アレルギー・食事制限(`user_food_preference`の`favorite_foods`/
  `disliked_foods`/`allergies`/`dietary_restrictions`)も同様に暗号化しています
  (V6マイグレーション)。
  (2026-08-30追記: 健康アラート機能自体を撤廃したため、`health_alert`テーブルの
  暗号化列・対応するJPAコンバータはもう存在しない。既存の`health_alert`データは
  V7マイグレーションで削除済み。docs/health-audit-legal-checklist.md参照。)
- 食事記録(`meal_record`の料理名・食べたもの・量・カロリー・たんぱく質・脂質・
  炭水化物・食物繊維・塩分・写真URL・メモ)も、食生活から健康状態を推測しうる
  情報として同様に暗号化しています(V8マイグレーション)。`meal_type`/`meal_date`/
  `meal_time`/`recipe_id`は日付・区分での絞り込みクエリに使うため対象外です。
- レシピ(`recipe`の料理名・調理器具・メモ・栄養素・再加熱方法、`recipe_ingredient`の
  材料名・数量・単位、`recipe_step`の調理手順)・AI献立提案(`ai_meal_suggestion`の
  総評、`ai_meal_suggestion_item`の料理名・使用食材・栄養素・提案理由)も、同じ理由で
  暗号化しています(V9マイグレーション)。`cooking_method`/`source`/`meal_type`等の
  区分値、`display_order`/`step_no`等の並び順制御用の数値は対象外です。レシピ名の
  暗号化に伴い、DB側での完全一致検索(重複レシピ防止)はアプリ層での復号後比較に
  変更しています(`RecipeService#findExistingByName`参照)。
- 暗号化鍵は環境変数 `APP_HEALTH_ENCRYPTION_KEY`(Base64エンコードされた
  32バイト。`openssl rand -base64 32` で生成)に設定してください。
  **未設定の場合はローカル開発専用の固定キーにフォールバックし、起動のたびに
  警告ログを出します。本番・共有環境では必ず設定してください**
  (設定し忘れると、このリポジトリを閲覧できる誰もが健康データを復号できて
  しまいます)。
- 鍵をローテーションする場合、既に暗号化済みのデータは旧鍵でしか復号できない
  ため、単純に環境変数を差し替えるだけでは復号できなくなります(再暗号化の
  仕組みは未実装)。ローテーションが必要になった場合は別途対応を検討して
  ください。

### 既知の制約

- 現状はアプリケーションサーバー自体はHTTPで動作します。Cookieのsecure属性・HSTS
  ヘッダーはHTTPS接続を前提に設定済みですが、「HTTPで届いたリクエストをHTTPSへ
  リダイレクトする」処理自体はアプリ内に実装していません。本番投入時は、
  下記「本番HTTPS配信チェックリスト」・`prod`プロファイルの有効化・DB接続情報等の
  環境変数の上書きをあわせて行ってください。
- 依存ライブラリ(Maven)に既知の脆弱性が見つかった場合、GitHub Dependabot
  (`.github/dependabot.yml`)が自動的に更新用のPull Requestを作成します
  (週次チェック。GitHub Actionsのワークフローを追加した場合は月次チェックも
  自動的に有効になります)。実際にPRが作られるタイミングはGitHub側のスケジュール
  によるため、リポジトリ側の設定だけでは即時には反映されません。

### 本番HTTPS配信チェックリスト(デプロイ構成確定後に対応)

本番のデプロイ構成(リバースプロキシを使うか、アプリ自身がTLSを終端するか)は
未確定のため、ここでは両パターンのチェックリストを記載する。**構成が決まらないうちに
HTTPS強制設定だけを先取りして有効化すると、構成によっては無限リダイレクトでサイトに
アクセスできなくなる、または後述のヘッダー偽装を許してしまうため、構成確定後に
該当する方を適用すること。**

#### A. リバースプロキシ(nginx / ALB / Cloudflare等)でTLS終端し、アプリへはHTTPで中継する場合

- [ ] プロキシ側でHTTP→HTTPSリダイレクトを行う(推奨。アプリまでリクエストを到達させず
      プロキシだけで完結させたほうが安全かつ低コスト)。
- [ ] プロキシがアプリへ `X-Forwarded-Proto` / `X-Forwarded-For` / `X-Forwarded-Host`
      を転送するよう設定する。
- [ ] アプリ側で `server.forward-headers-strategy=framework` を設定し、上記ヘッダーを
      信頼して `request.isSecure()` 等を正しく判定できるようにする
      (これが無いと、secure Cookie判定やリダイレクト先URLの組み立てが実際の
      接続方式と食い違う)。
- [ ] **上記を設定する場合、アプリがプロキシを経由せず直接インターネットから到達できる
      経路が残っていないことを必ず確認する。** `X-Forwarded-Proto` はクライアントが
      任意の値を送れる普通のHTTPヘッダーのため、信頼できるプロキシ以外からの直接
      アクセスを許したまま `forward-headers-strategy=framework` を有効にすると、
      `X-Forwarded-Proto: https` を偽装した平文HTTPリクエストが「HTTPS経由」として
      扱われてしまう(secure Cookieの保護・HTTPS強制チェックの回避に繋がる)。
      Tomcatの `server.tomcat.remoteip.internal-proxies` 等で信頼するプロキシの
      IPアドレスを明示的に制限することを推奨する。

#### B. アプリ自身(Tomcat)でTLSを終端する場合(リバースプロキシを使わない)

- [ ] `server.ssl.*`(証明書・秘密鍵等)を設定し、TLSをTomcatで直接有効化する。
- [ ] `SecurityConfig` の `securityFilterChain` に
      `http.requiresChannel(channel -> channel.anyRequest().requiresSecure())` を
      追加し、HTTPでの到達をHTTPSへ強制リダイレクトする(この構成ではプロキシによる
      ヘッダー偽装の懸念が無いため、安全に有効化できる)。

## デプロイ(Render + Neon)

デプロイ構成として上記パターンA(リバースプロキシがTLS終端)を採用し、
`server.forward-headers-strategy=framework`(`application-prod.properties`)を
既に有効化済み。RenderはコンテナがRender自身のプロキシを経由せず外部から直接
到達できる経路を持たないため、この構成で安全に有効化できる。

### 事前準備

1. **Neon**(https://neon.tech )でプロジェクトを作成し、PostgreSQLの接続文字列
   (ホスト名・DB名・ユーザー名・パスワード)を控える。
2. `openssl rand -base64 32` で`APP_HEALTH_ENCRYPTION_KEY`用の本番鍵を生成し、
   安全な場所(パスワードマネージャー等)に保存する。**このリポジトリのどこにも
   書き込まないこと。**

### Renderでの設定

1. RenderでこのGitHubリポジトリを連携し、"Web Service"としてデプロイする
   (ルートの`Dockerfile`を自動検出する)。
2. 環境変数を設定する:

   | 変数名 | 値 |
   |---|---|
   | `SPRING_PROFILES_ACTIVE` | `prod` |
   | `SPRING_DATASOURCE_URL` | Neonの接続文字列(`jdbc:postgresql://<host>/<db>?sslmode=require`) |
   | `DB_USERNAME` | Neonが発行したユーザー名 |
   | `DB_PASSWORD` | Neonが発行したパスワード |
   | `APP_HEALTH_ENCRYPTION_KEY` | 上記で生成した鍵 |
   | `ANTHROPIC_API_KEY` | (任意)AI献立提案を使う場合のみ |

   `PORT`はRenderが自動的に注入するため設定不要
   (`application.properties`の`server.port=${PORT:8080}`が受け取る)。
3. 初回デプロイ時、Flywayが`V1`から`V9`までのマイグレーションを空のDBに
   自動適用する。アプリ起動時に`ProductionSafetyChecker`が上記環境変数の
   未設定・既定値のままの起動を検知して失敗させるため、設定漏れがあれば
   ここで気付ける。

### デプロイ後の確認

- `https://<サービス名>.onrender.com/login.html` にアクセスできること。
- 新規登録 → ログインが通ること。
- 管理者アカウントで`admin-health-audit-log.html`を開き、ログイン等の操作が
  実際のクライアントIP(Renderのプロキシ経由でも正しいIP)で記録されていること
  (`server.forward-headers-strategy=framework`が効いているかの確認)。

### 運用メモ

- Neon等の接続情報をコマンドラインで`psql "postgresql://user:password@host/db"`のように
  URL形式で手動組み立てする際、パスワードに記号(`@` `#` `%` 等)が含まれていると
  URLの区切り文字と衝突するため、URLエンコードが必要になることがあります。
  アプリ自体は`DB_USERNAME` / `DB_PASSWORD`を環境変数から個別に読み込み、
  URLパースを経由せず接続するため、この問題の影響を受けません。

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
├── Dockerfile                      Render等のPaaS向けマルチステージビルド
├── .github/
│   └── dependabot.yml               Maven依存関係の週次脆弱性チェック(前述「既知の制約」参照)
├── src/main/java/com/kinn/app/
│   ├── KinnApplication.java        起動クラス
│   ├── entity/                     AttendanceRecord, HealthRecord, ScheduleEvent,
│   │                                HealthProfile, HealthCheck, MealRecord / MealType,
│   │                                Task, Announcement / AnnouncementRead, OvertimeRequest,
│   │                                Recipe / RecipeIngredient / RecipeStep,
│   │                                AiMealSuggestion / AiMealSuggestionItem,
│   │                                UserFoodPreference, Company / Department / AppUser ほか
│   ├── repository/                 JPAリポジトリ(entityと概ね1対1)
│   ├── service/                    AttendanceService / HealthService / ScheduleService /
│   │                                HealthProfileService / HealthCheckService /
│   │                                HealthScoreService(スコア算出ロジックを独立管理) /
│   │                                HealthTrendService / HealthAnalysisService /
│   │                                MealService(食事管理、Phase 1) /
│   │                                MealRecommendationService(AI献立提案、Phase 3。
│   │                                AI未設定時はルールベースにフォールバック) /
│   │                                RecipeService / TaskService / AnnouncementService /
│   │                                OvertimeRequestService / UserFoodPreferenceService /
│   │                                Admin*Service(管理者コンソール向け集計・操作)
│   ├── controller/                 REST API(機能ごとに分割。管理者専用APIは
│   │                                Admin*Controllerに分離しロール制御)
│   ├── dto/                        DTO(APIの入出力用。機能ごとに1〜数個)
│   ├── audit/                      HealthAuditAspect等(AOPベースの監査ログ・
│   │                                認可拒否時のセキュリティログ出力)
│   ├── security/                   SecurityConfig / AppUserDetailsService /
│   │                                RateLimiter / LoginAttemptListener ほか(前述「セキュリティ」参照)
│   └── config/                     ProductionSafetyChecker(本番起動時の安全チェック)や
│                                    各種マイグレーション補助Runnerなど
├── src/main/resources/
│   ├── application.properties      DB接続設定など共通設定
│   ├── application-prod.properties 本番プロファイルでの上書き設定
│   ├── logback-spring.xml          ロギング設定(セキュリティログの出力先を
│   │                                prod以外はファイル、prodはコンソールに切り替え)
│   ├── db/migration/               Flywayマイグレーション(V1〜。詳細は前述「今後の
│   │                                テーブル変更の運用」参照)
│   └── static/                     フロントエンド(HTML/CSS/JS)
│       ├── index.html              トップページ
│       ├── login.html / register.html / change-password.html  認証系
│       ├── attendance.html         勤怠管理
│       ├── schedule.html           スケジュール管理
│       ├── announcement.html       お知らせ
│       ├── overtime.html           残業申請
│       ├── task.html               タスク管理
│       ├── health-top.html         健康管理トップ(拡張機能のハブ)
│       ├── health.html             健康管理(従来: 月次健康記録)
│       ├── health-profile.html     健康プロフィール
│       ├── health-check.html       今日の体調チェック
│       ├── health-score.html       健康スコア
│       ├── health-history.html     健康履歴
│       ├── health-graph.html       健康グラフ(推移)
│       ├── health-analysis.html    勤怠×健康分析
│       ├── health-audit-log.html   健康データアクセスの自分の履歴確認
│       ├── meal.html               食事記録(朝・昼・夕・間食の入力+今日の食事の可視化)
│       ├── meal-history.html       食事履歴(今日/昨日/1週間/1か月)
│       ├── meal-ai-history.html    AI献立提案の履歴
│       ├── recipe.html             レシピ・調理方法
│       ├── admin-top.html          管理者コンソールのトップ
│       ├── admin-dashboard.html    管理者ダッシュボード
│       ├── admin-employees.html / admin-departments.html  従業員・部署管理
│       ├── admin-attendance.html / admin-schedule.html    勤怠・スケジュールの管理者閲覧
│       ├── admin-announcement.html / admin-overtime.html / admin-task.html
│       │                          お知らせ配信・残業申請の承認・タスクの管理者操作
│       ├── admin-health-audit-log.html  健康データアクセス監査ログの検索(管理者専用)
│       ├── css/                    style.css(共通) / top.css(トップページ演出) /
│       │                            機能ごとの*-extra.css(health/meal/task/announcement/
│       │                            overtime/attendance/schedule) / auth-common.css / break.css
│       └── js/                     top.js / app.js(共通ユーティリティ) /
│                                    機能ごとの*.js(html一覧と概ね1対1。health-common.jsは
│                                    健康管理系で共有する簡易チャート描画等のユーティリティ、
│                                    recipe-shared.jsはrecipe.html/meal-ai-history.htmlで共有)
└── python/
    ├── health_report.py            勤怠・健康データの分析バッチ(独立スクリプト)
    ├── health_check_analysis.py    勤怠×健康の統計分析バッチ(独立スクリプト)
    └── requirements.txt
```

上記は主要ディレクトリの要約です。ファイル単位の網羅的な一覧は
`find src/main/resources/static -maxdepth 1 -name "*.html"` 等で随時確認してください。
