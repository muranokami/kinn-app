#!/usr/bin/env python3
"""
health_report.py
-----------------
勤怠管理アプリ(Kinn)のPostgreSQLデータベースから、指定した月の
勤怠記録(attendance_record)と健康記録(health_record)を読み出して
分析レポートを標準出力(または任意のテキストファイル)に出力する、
Webアプリの実行フローとは独立したバッチ/分析スクリプトです。

Webアプリ(Java/Spring Boot)を起動していなくても、DBにさえ接続できれば
単独で実行できます。

使い方:
    python3 health_report.py --year 2026 --month 8
    python3 health_report.py --year 2026 --month 8 --employee-id default --out report.txt

接続情報は環境変数で上書きできます(未設定時は application.properties の
デフォルト値と同じ値を使用します)。
    KINN_DB_HOST     (default: localhost)
    KINN_DB_PORT     (default: 5432)
    KINN_DB_NAME     (default: kinn_db)
    KINN_DB_USER     (default: postgres)
    KINN_DB_PASSWORD (default: postgres)

必要なパッケージ:
    pip install -r requirements.txt
"""

from __future__ import annotations

import argparse
import calendar
import datetime as dt
import os
import sys
from dataclasses import dataclass, field
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
        SELECT work_date, day_type, start_time, end_time,
               break_minutes, late_or_early, paid_leave_unit
        FROM attendance_record
        WHERE employee_id = %s AND work_date BETWEEN %s AND %s
        ORDER BY work_date
    """
    with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
        cur.execute(sql, (employee_id, first_day, last_day))
        return cur.fetchall()


def fetch_health(conn, employee_id: str, first_day: dt.date, last_day: dt.date):
    sql = """
        SELECT record_date, weight_kg, sleep_hours, steps, exercise_minutes,
               systolic_bp, diastolic_bp, condition, memo
        FROM health_record
        WHERE employee_id = %s AND record_date BETWEEN %s AND %s
        ORDER BY record_date
    """
    with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
        cur.execute(sql, (employee_id, first_day, last_day))
        return cur.fetchall()


# ----------------------------------------------------------------------
# 集計
# ----------------------------------------------------------------------
@dataclass
class AttendanceStats:
    working_days: int = 0
    total_work_hours: float = 0.0
    total_overtime_hours: float = 0.0
    late_or_early_count: int = 0
    absence_days: int = 0
    paid_leave_days: float = 0.0


@dataclass
class HealthStats:
    recorded_days: int = 0
    avg_weight_kg: Optional[float] = None
    avg_sleep_hours: Optional[float] = None
    total_steps: int = 0
    avg_steps: int = 0
    exercise_days: int = 0
    avg_systolic_bp: Optional[float] = None
    avg_diastolic_bp: Optional[float] = None
    good_condition_days: int = 0
    bad_condition_days: int = 0
    low_sleep_dates: list = field(default_factory=list)  # 睡眠6時間未満の日


def summarize_attendance(rows) -> AttendanceStats:
    stats = AttendanceStats()
    for r in rows:
        if r["late_or_early"]:
            stats.late_or_early_count += 1

        day_type = r["day_type"]
        if day_type == "ABSENCE":
            stats.absence_days += 1
        elif day_type == "PAID_LEAVE":
            stats.paid_leave_days += float(r["paid_leave_unit"] or 1.0)
        elif day_type == "NORMAL" and r["start_time"] and r["end_time"]:
            worked_minutes = _minutes_between(r["start_time"], r["end_time"]) - (r["break_minutes"] or 0)
            worked_minutes = max(0, worked_minutes)
            stats.working_days += 1
            stats.total_work_hours += worked_minutes / 60.0
            stats.total_overtime_hours += max(0, worked_minutes - STANDARD_WORK_MINUTES) / 60.0

    stats.total_work_hours = round(stats.total_work_hours, 2)
    stats.total_overtime_hours = round(stats.total_overtime_hours, 2)
    return stats


def summarize_health(rows) -> HealthStats:
    stats = HealthStats()
    weight_sum, weight_n = 0.0, 0
    sleep_sum, sleep_n = 0.0, 0
    systolic_sum, systolic_n = 0.0, 0
    diastolic_sum, diastolic_n = 0.0, 0

    for r in rows:
        has_value = any(
            r[k] is not None
            for k in ("weight_kg", "sleep_hours", "steps", "exercise_minutes",
                      "systolic_bp", "diastolic_bp", "condition")
        )
        if has_value:
            stats.recorded_days += 1

        if r["weight_kg"] is not None:
            weight_sum += float(r["weight_kg"]); weight_n += 1
        if r["sleep_hours"] is not None:
            sleep_sum += float(r["sleep_hours"]); sleep_n += 1
            if float(r["sleep_hours"]) < 6:
                stats.low_sleep_dates.append(r["record_date"])
        if r["steps"] is not None:
            stats.total_steps += int(r["steps"])
        if r["exercise_minutes"] is not None and r["exercise_minutes"] > 0:
            stats.exercise_days += 1
        if r["systolic_bp"] is not None:
            systolic_sum += float(r["systolic_bp"]); systolic_n += 1
        if r["diastolic_bp"] is not None:
            diastolic_sum += float(r["diastolic_bp"]); diastolic_n += 1
        if r["condition"] in ("GREAT", "GOOD"):
            stats.good_condition_days += 1
        elif r["condition"] == "BAD":
            stats.bad_condition_days += 1

    stats.avg_weight_kg = round(weight_sum / weight_n, 1) if weight_n else None
    stats.avg_sleep_hours = round(sleep_sum / sleep_n, 1) if sleep_n else None
    stats.avg_systolic_bp = round(systolic_sum / systolic_n, 1) if systolic_n else None
    stats.avg_diastolic_bp = round(diastolic_sum / diastolic_n, 1) if diastolic_n else None
    day_count_with_steps = sum(1 for r in rows if r["steps"] is not None)
    stats.avg_steps = round(stats.total_steps / day_count_with_steps) if day_count_with_steps else 0
    return stats


def _minutes_between(start: dt.time, end: dt.time) -> int:
    s = start.hour * 60 + start.minute
    e = end.hour * 60 + end.minute
    if e <= s:
        e += 24 * 60
    return e - s


# ----------------------------------------------------------------------
# レポート整形
# ----------------------------------------------------------------------
def build_report(year: int, month: int, employee_id: str,
                  a: AttendanceStats, h: HealthStats) -> str:
    lines = []
    lines.append("=" * 52)
    lines.append(f" Kinn 月次分析レポート  {year}年{month}月  (employee_id={employee_id})")
    lines.append("=" * 52)

    lines.append("\n■ 勤怠サマリー")
    lines.append(f"  勤務日数        : {a.working_days} 日")
    lines.append(f"  総労働時間      : {a.total_work_hours} h")
    lines.append(f"  総残業時間      : {a.total_overtime_hours} h")
    lines.append(f"  遅刻・早退回数  : {a.late_or_early_count} 回")
    lines.append(f"  欠勤日数        : {a.absence_days} 日")
    lines.append(f"  有給使用日数    : {a.paid_leave_days} 日")

    lines.append("\n■ 健康サマリー")
    lines.append(f"  記録日数        : {h.recorded_days} 日")
    lines.append(f"  平均体重        : {fmt(h.avg_weight_kg)} kg")
    lines.append(f"  平均睡眠時間    : {fmt(h.avg_sleep_hours)} h")
    lines.append(f"  合計歩数        : {h.total_steps} 歩")
    lines.append(f"  平均歩数        : {h.avg_steps} 歩/日")
    lines.append(f"  運動した日数    : {h.exercise_days} 日")
    lines.append(f"  平均血圧        : {fmt(h.avg_systolic_bp)} / {fmt(h.avg_diastolic_bp)}")
    lines.append(f"  好調だった日数  : {h.good_condition_days} 日")
    lines.append(f"  不調だった日数  : {h.bad_condition_days} 日")

    lines.append("\n■ 気づき")
    notes = build_notes(a, h)
    if notes:
        for n in notes:
            lines.append(f"  ・{n}")
    else:
        lines.append("  ・特に気になる傾向は見つかりませんでした。")

    lines.append("\n" + "=" * 52)
    return "\n".join(lines)


def fmt(value) -> str:
    return "-" if value is None else str(value)


def build_notes(a: AttendanceStats, h: HealthStats) -> list[str]:
    notes = []
    if h.low_sleep_dates:
        dates = ", ".join(d.strftime("%m/%d") for d in h.low_sleep_dates)
        notes.append(f"睡眠時間が6時間未満だった日が {len(h.low_sleep_dates)} 日あります({dates})。")
    if a.total_overtime_hours >= 40:
        notes.append(f"残業時間が {a.total_overtime_hours}h と多めです。健康管理にも注意しましょう。")
    if a.late_or_early_count >= 3 and h.avg_sleep_hours is not None and h.avg_sleep_hours < 6.5:
        notes.append("遅刻・早退が多く、平均睡眠時間も短めです。生活リズムを見直す余地があるかもしれません。")
    if h.bad_condition_days >= 5:
        notes.append(f"体調不良の日が {h.bad_condition_days} 日と多めです。無理をしていないか確認しましょう。")
    if h.exercise_days == 0 and a.working_days > 0:
        notes.append("この月は運動記録がありません。軽い運動を取り入れてみましょう。")
    return notes


# ----------------------------------------------------------------------
# エントリポイント
# ----------------------------------------------------------------------
def main():
    today = dt.date.today()
    parser = argparse.ArgumentParser(description="Kinnアプリの勤怠・健康データを分析してレポートを出力します。")
    parser.add_argument("--employee-id", default="default", help="集計対象の社員ID(既定: default)")
    parser.add_argument("--year", type=int, default=today.year, help="対象年(既定: 今年)")
    parser.add_argument("--month", type=int, default=today.month, help="対象月(既定: 今月)")
    parser.add_argument("--out", help="レポートの出力先ファイル(省略時は標準出力)")
    args = parser.parse_args()

    if not (1 <= args.month <= 12):
        parser.error("--month は 1〜12 の範囲で指定してください")

    first_day = dt.date(args.year, args.month, 1)
    last_day = dt.date(args.year, args.month, calendar.monthrange(args.year, args.month)[1])

    try:
        conn = connect()
    except psycopg2.OperationalError as e:
        print(f"データベースに接続できませんでした: {e}", file=sys.stderr)
        print("KINN_DB_HOST / KINN_DB_PORT / KINN_DB_NAME / KINN_DB_USER / KINN_DB_PASSWORD "
              "の環境変数を確認してください。", file=sys.stderr)
        sys.exit(1)

    try:
        attendance_rows = fetch_attendance(conn, args.employee_id, first_day, last_day)
        health_rows = fetch_health(conn, args.employee_id, first_day, last_day)
    finally:
        conn.close()

    a_stats = summarize_attendance(attendance_rows)
    h_stats = summarize_health(health_rows)
    report = build_report(args.year, args.month, args.employee_id, a_stats, h_stats)

    if args.out:
        with open(args.out, "w", encoding="utf-8") as f:
            f.write(report + "\n")
        print(f"レポートを {args.out} に出力しました。")
    else:
        print(report)


if __name__ == "__main__":
    main()
