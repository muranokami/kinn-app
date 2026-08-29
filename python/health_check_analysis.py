#!/usr/bin/env python3
"""
health_check_analysis.py
-------------------------
勤怠管理アプリ(Kinn)の拡張健康管理機能が使う PostgreSQL テーブル
(health_check: 今日の体調チェック, attendance_record: 勤怠実績)を読み出し、
「残業時間と健康スコアの関係」「睡眠時間と疲労度の関係」などの基本的な
統計分析(単純な相関係数)を行うバッチ/分析スクリプトです。

health_report.py と同じく、Webアプリ(Java/Spring Boot)の実行フローとは
完全に独立しています。Javaアプリからこのスクリプトを ProcessBuilder 等で
呼び出すことはありません。PostgreSQLにさえ接続できれば単独で実行できます。

役割分担(READMEにも記載):
    Java/Spring Boot : Web API・業務処理・DBアクセス・画面連携
    PostgreSQL       : 勤怠・健康・スケジュールデータの永続化
    Python           : 健康データ分析・勤怠×健康分析・統計分析・
                       将来的な予測モデル/AIの実験場

将来的にここへ scikit-learn 等を使った予測モデルを追加する場合も、
このファイルのように「DBから読む→集計/分析する→出力する」独立バッチ
として追加していく想定です(Webアプリの起動有無に依存しない)。

使い方:
    python3 health_check_analysis.py --days 90
    python3 health_check_analysis.py --employee-id default --days 30 --out report.txt

接続情報は環境変数で上書きできます(health_report.py と同じ既定値)。
    KINN_DB_HOST / KINN_DB_PORT / KINN_DB_NAME / KINN_DB_USER / KINN_DB_PASSWORD

必要なパッケージ:
    pip install -r requirements.txt
"""

from __future__ import annotations

import argparse
import datetime as dt
import os
import sys
from dataclasses import dataclass
from typing import Optional

try:
    import psycopg2
    import psycopg2.extras
except ImportError:
    print(
        "psycopg2 がインストールされていません。\n"
        "  pip install -r requirements.txt\n"
        "を実行してから、もう一度お試しください。",
        file=sys.stderr,
    )
    sys.exit(1)


STANDARD_WORK_MINUTES = 8 * 60  # Java側 AttendanceService と同じ前提


# ----------------------------------------------------------------------
# DB接続
# ----------------------------------------------------------------------
def connect():
    return psycopg2.connect(
        host=os.environ.get("KINN_DB_HOST", "localhost"),
        port=os.environ.get("KINN_DB_PORT", "5432"),
        dbname=os.environ.get("KINN_DB_NAME", "kinn_db"),
        user=os.environ.get("KINN_DB_USER", "postgres"),
        password=os.environ.get("KINN_DB_PASSWORD", "postgres"),
    )


# ----------------------------------------------------------------------
# データ取得
# ----------------------------------------------------------------------
def fetch_attendance(conn, employee_id: str, first_day: dt.date, last_day: dt.date):
    sql = """
        SELECT work_date, day_type, start_time, end_time, break_minutes
        FROM attendance_record
        WHERE employee_id = %s AND work_date BETWEEN %s AND %s
        ORDER BY work_date
    """
    with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
        cur.execute(sql, (employee_id, first_day, last_day))
        return cur.fetchall()


def fetch_health_checks(conn, employee_id: str, first_day: dt.date, last_day: dt.date):
    sql = """
        SELECT check_date, condition_level, sleep_hours, fatigue_level,
               stress_level, exercise_minutes, body_temperature
        FROM health_check
        WHERE employee_id = %s AND check_date BETWEEN %s AND %s
        ORDER BY check_date
    """
    with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
        cur.execute(sql, (employee_id, first_day, last_day))
        return cur.fetchall()


# ----------------------------------------------------------------------
# 集計・分析
# ----------------------------------------------------------------------
def overtime_minutes(row) -> Optional[int]:
    """Java側 AttendanceService.getDailyStats と同じ考え方で日次残業時間(分)を計算する。
    通常勤務(NORMAL)以外や、出退勤未入力の日は None を返す。"""
    if row["day_type"] != "NORMAL" or not row["start_time"] or not row["end_time"]:
        return None
    s = row["start_time"].hour * 60 + row["start_time"].minute
    e = row["end_time"].hour * 60 + row["end_time"].minute
    if e <= s:
        e += 24 * 60
    worked = max(0, (e - s) - (row["break_minutes"] or 0))
    return max(0, worked - STANDARD_WORK_MINUTES)


def simple_health_score(row) -> Optional[float]:
    """Java側 HealthScoreService の簡易版(Python単独で動かすための概算値)。
    正式なスコアはJavaアプリのAPI(/api/health/score)を参照してください。"""
    sleep_h = row["sleep_hours"]
    fatigue = row["fatigue_level"]
    stress = row["stress_level"]
    if sleep_h is None and fatigue is None and stress is None:
        return None

    def sleep_score(h):
        if h is None:
            return 50
        diff = max(0.0, (7.0 - h) if h < 7 else (h - 8.0) if h > 8 else 0.0)
        return max(0, min(100, round(100 - diff * 18)))

    def level_score(lv):
        if lv is None:
            return 50
        lv = max(1, min(5, lv))
        return (5 - lv) * 25

    return round(sleep_score(float(sleep_h) if sleep_h is not None else None) * 0.4
                 + level_score(fatigue) * 0.3
                 + level_score(stress) * 0.3, 1)


def pearson_correlation(xs: list[float], ys: list[float]) -> Optional[float]:
    """外部ライブラリ(numpy等)に依存しない単純なピアソン相関係数の実装。
    データが2件未満、または分散が0の場合は None を返す。"""
    n = len(xs)
    if n < 2 or n != len(ys):
        return None
    mean_x = sum(xs) / n
    mean_y = sum(ys) / n
    cov = sum((x - mean_x) * (y - mean_y) for x, y in zip(xs, ys))
    var_x = sum((x - mean_x) ** 2 for x in xs)
    var_y = sum((y - mean_y) ** 2 for y in ys)
    if var_x == 0 or var_y == 0:
        return None
    return round(cov / ((var_x ** 0.5) * (var_y ** 0.5)), 3)


@dataclass
class AnalysisResult:
    sample_days: int = 0
    corr_overtime_score: Optional[float] = None
    corr_sleep_fatigue: Optional[float] = None
    corr_overtime_stress: Optional[float] = None
    avg_score_low_overtime: Optional[float] = None
    avg_score_high_overtime: Optional[float] = None


def analyze(attendance_rows, health_rows) -> AnalysisResult:
    overtime_by_date = {}
    for r in attendance_rows:
        ot = overtime_minutes(r)
        if ot is not None:
            overtime_by_date[r["work_date"]] = ot / 60.0

    score_by_date = {}
    sleep_by_date = {}
    fatigue_by_date = {}
    stress_by_date = {}
    for r in health_rows:
        score = simple_health_score(r)
        if score is not None:
            score_by_date[r["check_date"]] = score
        if r["sleep_hours"] is not None:
            sleep_by_date[r["check_date"]] = float(r["sleep_hours"])
        if r["fatigue_level"] is not None:
            fatigue_by_date[r["check_date"]] = r["fatigue_level"]
        if r["stress_level"] is not None:
            stress_by_date[r["check_date"]] = r["stress_level"]

    result = AnalysisResult()

    # 残業時間 × 健康スコア
    common_dates = sorted(set(overtime_by_date) & set(score_by_date))
    result.sample_days = len(common_dates)
    if common_dates:
        ot_vals = [overtime_by_date[d] for d in common_dates]
        score_vals = [score_by_date[d] for d in common_dates]
        result.corr_overtime_score = pearson_correlation(ot_vals, score_vals)

        low = [score_by_date[d] for d in common_dates if overtime_by_date[d] < 2.0]
        high = [score_by_date[d] for d in common_dates if overtime_by_date[d] >= 2.0]
        result.avg_score_low_overtime = round(sum(low) / len(low), 1) if low else None
        result.avg_score_high_overtime = round(sum(high) / len(high), 1) if high else None

    # 睡眠時間 × 疲労度
    sf_dates = sorted(set(sleep_by_date) & set(fatigue_by_date))
    if sf_dates:
        result.corr_sleep_fatigue = pearson_correlation(
            [sleep_by_date[d] for d in sf_dates], [fatigue_by_date[d] for d in sf_dates]
        )

    # 残業時間 × ストレス度
    os_dates = sorted(set(overtime_by_date) & set(stress_by_date))
    if os_dates:
        result.corr_overtime_stress = pearson_correlation(
            [overtime_by_date[d] for d in os_dates], [stress_by_date[d] for d in os_dates]
        )

    return result


# ----------------------------------------------------------------------
# レポート整形
# ----------------------------------------------------------------------
def build_report(employee_id: str, first_day: dt.date, last_day: dt.date, r: AnalysisResult) -> str:
    lines = []
    lines.append("=" * 56)
    lines.append(f" Kinn 勤怠×健康 統計分析レポート  {first_day} 〜 {last_day}")
    lines.append(f" employee_id={employee_id}")
    lines.append("=" * 56)

    lines.append(f"\n分析対象日数: {r.sample_days} 日")

    lines.append("\n■ 残業時間 と 健康スコアの関係(概算値)")
    lines.append(f"  相関係数(残業時間 vs 健康スコア): {fmt(r.corr_overtime_score)}")
    lines.append(f"  残業が少ない日(2h未満)の平均スコア: {fmt(r.avg_score_low_overtime)}")
    lines.append(f"  残業が多い日(2h以上)の平均スコア  : {fmt(r.avg_score_high_overtime)}")
    if r.corr_overtime_score is not None and r.corr_overtime_score <= -0.3:
        lines.append("  → 残業が多い日ほど健康スコアが低下する傾向が見られます。")

    lines.append("\n■ 睡眠時間 と 疲労度の関係")
    lines.append(f"  相関係数(睡眠時間 vs 疲労度): {fmt(r.corr_sleep_fatigue)}")
    if r.corr_sleep_fatigue is not None and r.corr_sleep_fatigue <= -0.3:
        lines.append("  → 睡眠時間が短い日ほど疲労度が高くなる傾向が見られます。")

    lines.append("\n■ 残業時間 と ストレス度の関係")
    lines.append(f"  相関係数(残業時間 vs ストレス度): {fmt(r.corr_overtime_stress)}")
    if r.corr_overtime_stress is not None and r.corr_overtime_stress >= 0.3:
        lines.append("  → 残業が多い日ほどストレス度が高くなる傾向が見られます。")

    lines.append(
        "\n※ 相関係数は -1〜1 の値で、0に近いほど関係が薄く、"
        "絶対値が1に近いほど強い関係があることを示す簡易的な目安です。"
        "医療的な診断や因果関係の証明を行うものではありません。"
    )
    lines.append("\n" + "=" * 56)
    return "\n".join(lines)


def fmt(value) -> str:
    return "-" if value is None else str(value)


# ----------------------------------------------------------------------
# エントリポイント
# ----------------------------------------------------------------------
def main():
    parser = argparse.ArgumentParser(
        description="Kinnアプリの勤怠・健康チェックデータを使った基本的な統計分析バッチ。"
    )
    parser.add_argument("--employee-id", default="default", help="集計対象の社員ID(既定: default)")
    parser.add_argument("--days", type=int, default=90, help="分析対象の直近日数(既定: 90日)")
    parser.add_argument("--out", help="レポートの出力先ファイル(省略時は標準出力)")
    args = parser.parse_args()

    last_day = dt.date.today()
    first_day = last_day - dt.timedelta(days=max(args.days, 1) - 1)

    try:
        conn = connect()
    except psycopg2.OperationalError as e:
        print(f"データベースに接続できませんでした: {e}", file=sys.stderr)
        print("KINN_DB_HOST / KINN_DB_PORT / KINN_DB_NAME / KINN_DB_USER / KINN_DB_PASSWORD "
              "の環境変数を確認してください。", file=sys.stderr)
        sys.exit(1)

    try:
        attendance_rows = fetch_attendance(conn, args.employee_id, first_day, last_day)
        health_rows = fetch_health_checks(conn, args.employee_id, first_day, last_day)
    finally:
        conn.close()

    result = analyze(attendance_rows, health_rows)
    report = build_report(args.employee_id, first_day, last_day, result)

    if args.out:
        with open(args.out, "w", encoding="utf-8") as f:
            f.write(report + "\n")
        print(f"レポートを {args.out} に出力しました。")
    else:
        print(report)


if __name__ == "__main__":
    main()
