// ------------------------------------------------------------------
// 締め日(カレンダー開始日)機能の共通処理。
// attendance.html(一般ユーザー)・admin-attendance.html(管理者)の両方から使う。
//
// 期間の開始日・終了日そのものはサーバー側(AttendancePeriodResolver)で計算する。
// このファイルでは、
//   1. 締め日の選択UIを組み立てる
//   2. 選んだ締め日をlocalStorageへ保存し、次回も同じ締め日を使えるようにする
//   3. 期間ラベル("2026年8月20日 ～ 2026年9月19日")を組み立てる
//   4. 「前の期間/今日/次の期間」の移動先(baseDateとして送る日付)を計算する
//      (期間そのものの計算はしない。サーバーが返したstartDate/endDateを基準に
//       日単位でずらすだけなので、締め日ロジックをJS側で重複実装しない)
// をまとめている。
// ------------------------------------------------------------------

const ATTENDANCE_CLOSING_DAY_STORAGE_KEY = "kinn.attendance.closingDay";
const ATTENDANCE_DEFAULT_CLOSING_DAY = 1;

/** 保存されている締め日を取得する(未設定なら1日=暦月) */
function getStoredClosingDay() {
  const raw = window.localStorage.getItem(ATTENDANCE_CLOSING_DAY_STORAGE_KEY);
  const value = parseInt(raw, 10);
  if (!Number.isInteger(value) || value < 1 || value > 31) {
    return ATTENDANCE_DEFAULT_CLOSING_DAY;
  }
  return value;
}

/** 選択した締め日を保存する(⑦締め日の保持。次回カレンダーを開いたときも同じ締め日を使う) */
function setStoredClosingDay(value) {
  window.localStorage.setItem(ATTENDANCE_CLOSING_DAY_STORAGE_KEY, String(value));
}

/** 締め日セレクトボックスへ1〜31日の選択肢を詰める */
function populateClosingDaySelect(selectEl) {
  selectEl.innerHTML = "";
  for (let d = 1; d <= 31; d++) {
    const opt = document.createElement("option");
    opt.value = d;
    opt.textContent = d === 1 ? "1日(暦月)" : `${d}日`;
    selectEl.appendChild(opt);
  }
}

/** "2026-08-20" のようなISO日付文字列を "2026年8月20日" の表示形式にする */
function formatJapaneseDate(isoDateStr) {
  if (!isoDateStr) return "";
  const [y, m, d] = isoDateStr.split("-").map(Number);
  return `${y}年${m}月${d}日`;
}

/** 期間ラベル(例: "2026年8月20日 ～ 2026年9月19日")を組み立てる */
function formatPeriodLabel(startDateStr, endDateStr) {
  return `${formatJapaneseDate(startDateStr)} ～ ${formatJapaneseDate(endDateStr)}`;
}

/**
 * ISO日付文字列(YYYY-MM-DD)に日数を加算する。文字列操作ではなくDateオブジェクトで計算する。
 * 締め日の月境界計算そのものはサーバー側で行うため、ここでは単純な日数の前後移動のみに使う
 * (「前の期間」= 現在の開始日の1日前を基準日にする、「次の期間」= 現在の終了日の1日後を
 * 基準日にする、という単純な移動のみ)。
 */
function addDaysToIsoDate(isoDateStr, days) {
  const [y, m, d] = isoDateStr.split("-").map(Number);
  const date = new Date(Date.UTC(y, m - 1, d));
  date.setUTCDate(date.getUTCDate() + days);
  const yy = date.getUTCFullYear();
  const mm = String(date.getUTCMonth() + 1).padStart(2, "0");
  const dd = String(date.getUTCDate()).padStart(2, "0");
  return `${yy}-${mm}-${dd}`;
}

/** 今日の日付をISO形式(YYYY-MM-DD)で返す */
function todayIsoDate() {
  const now = new Date();
  const yy = now.getFullYear();
  const mm = String(now.getMonth() + 1).padStart(2, "0");
  const dd = String(now.getDate()).padStart(2, "0");
  return `${yy}-${mm}-${dd}`;
}
