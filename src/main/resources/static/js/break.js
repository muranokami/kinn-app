// ------------------------------------------------------------------
// トップページの休憩機能(①〜⑥⑪〜⑮⑰⑳㉑)。
//
// 状態(休憩開始時刻など)は必ずサーバー側(AttendanceRecord)に保持されており、
// このスクリプトはあくまで「サーバーの状態を取得して表示し、ボタン操作をAPIへ送るだけ」
// にとどめる。60分経過の確定・勤怠への反映はサーバー側(AttendanceBreakService)が行うため、
// ページの再読み込みやブラウザを閉じた後の再訪問でも、GET /api/attendance/break/status を
// 呼べば必ず正しい状態に復元される(⑪⑫)。
// ------------------------------------------------------------------

const BREAK_RESYNC_INTERVAL_MS = 20000; // 休憩中、サーバーの残り時間とズレないよう定期的に再取得する
const BREAK_URGENT_SECONDS = 10;

let breakCountdownTimer = null;
let breakResyncTimer = null;
let breakRemainingSeconds = 0;
let breakWasOnBreak = false; // このページ表示中に「休憩中」を観測していたか(自動終了アラート表示の判定用)
let notificationPermissionRequested = false;

window.addEventListener("DOMContentLoaded", () => {
  document.getElementById("breakStartBtn").addEventListener("click", onBreakStartClick);
  document.getElementById("breakEndBtn").addEventListener("click", onBreakEndClick);
  document.getElementById("breakAlertCloseBtn").addEventListener("click", closeBreakAlert);
  refreshBreakStatus(false);
});

function breakReadErrorMessage(res, fallback) {
  return res.json().then((body) => body?.message || fallback).catch(() => fallback);
}

async function refreshBreakStatus(triggeredByCountdown) {
  try {
    const res = await fetch("/api/attendance/break/status");
    if (!res.ok) throw new Error(await breakReadErrorMessage(res, "休憩状態の取得に失敗しました。"));
    const dto = await res.json();
    applyBreakStatus(dto, triggeredByCountdown);
    setBreakStatusMessage("");
  } catch (e) {
    console.error(e);
    // 通信エラーでも画面を無反応にしない(㉓)。パネル自体は直前の表示のまま残す
    setBreakStatusMessage(e.message || "通信エラーが発生しました。");
  }
}

async function onBreakStartClick() {
  const btn = document.getElementById("breakStartBtn");
  setButtonLoading(btn, true);
  ensureNotificationPermission();
  try {
    const res = await fetch("/api/attendance/break/start", { method: "POST" });
    if (!res.ok) throw new Error(await breakReadErrorMessage(res, "休憩の開始に失敗しました。"));
    const dto = await res.json();
    // 成功時はapplyBreakStatus側がボタンの表示・有効/無効を状態に応じて設定し直すため、
    // ここではis-loading表示だけ解除しておく
    setButtonLoading(btn, false);
    applyBreakStatus(dto, false);
    setBreakStatusMessage("");
  } catch (e) {
    console.error(e);
    setBreakStatusMessage(e.message || "休憩の開始に失敗しました。");
    setButtonLoading(btn, false);
  }
}

async function onBreakEndClick() {
  const btn = document.getElementById("breakEndBtn");
  setButtonLoading(btn, true);
  try {
    const res = await fetch("/api/attendance/break/end", { method: "POST" });
    if (!res.ok) throw new Error(await breakReadErrorMessage(res, "休憩の終了に失敗しました。"));
    const dto = await res.json();
    setButtonLoading(btn, false);
    applyBreakStatus(dto, false); // 手動終了なので、大きなアラートモーダルは出さない
    // 「残り◯分取れます」「本日の休憩時間を使い切りました」など、サーバーからの案内文を表示する
    setBreakStatusMessage(dto.message || "");
  } catch (e) {
    console.error(e);
    setBreakStatusMessage(e.message || "休憩の終了に失敗しました。");
    setButtonLoading(btn, false);
  }
}

/**
 * サーバーから取得した状態を画面へ反映する(⑰状態に応じたボタン表示)。
 * @param triggeredByCountdown ブラウザ側カウントダウンが0になったのを契機にstatusを
 *   取得した場合はtrue。この場合だけ「60分経過アラート」(⑤⑳)を表示する
 *   (ページを開いた瞬間から常にBREAK_FINISHEDだった場合にまで毎回アラートを出さないため)。
 */
function applyBreakStatus(dto, triggeredByCountdown) {
  stopBreakCountdown();
  stopBreakResync();

  const panel = document.getElementById("breakPanel");
  const idleBlock = document.getElementById("breakIdleBlock");
  const activeBlock = document.getElementById("breakActiveBlock");
  const doneBlock = document.getElementById("breakDoneBlock");
  const startBtn = document.getElementById("breakStartBtn");

  panel.hidden = false;
  idleBlock.hidden = true;
  activeBlock.hidden = true;
  doneBlock.hidden = true;

  switch (dto.status) {
    // 出勤時刻の入力は前提にしない(利用者フィードバックにより変更)。休憩が始まっておらず
    // 退勤もしていなければ、誰でもいつでも[休憩開始]を押せる。休憩は1日に何度でも
    // 分けて取れる(刻んで取る運用に対応)ので、それまでの使用状況をヒントとして表示する
    case "WORKING":
      idleBlock.hidden = false;
      startBtn.disabled = false;
      startBtn.textContent = "☕ 休憩開始";
      renderBreakUsageHint(dto);
      breakWasOnBreak = false;
      break;

    case "ON_BREAK":
      activeBlock.hidden = false;
      document.getElementById("breakActiveStart").textContent = fmtTime(dto.breakStartTime);
      document.getElementById("breakActiveEnd").textContent = fmtTime(dto.scheduledEndTime);
      document.getElementById("breakEndBtn").disabled = false;
      breakWasOnBreak = true;
      startBreakCountdown(dto.remainingSeconds);
      startBreakResync();
      break;

    case "BREAK_EXHAUSTED":
      doneBlock.hidden = false;
      document.getElementById("breakDoneStart").textContent = fmtTime(dto.breakStartTime);
      document.getElementById("breakDoneEnd").textContent = fmtTime(dto.breakEndTime);
      document.getElementById("breakDoneMinutes").textContent =
        dto.actualBreakMinutes != null ? `${dto.actualBreakMinutes}分` : "-";
      // 「休憩中だった状態から今回のカウントダウン満了で終了(=本日の予算を使い切った)を
      // 検知した」場合のみアラートを出す(⑤⑳。分割して取った休憩でも、予算を使い切った
      // 回には必ずこの条件を満たすので、刻んで取った場合でもアラームは正しく鳴る)
      if (triggeredByCountdown && breakWasOnBreak) {
        showBreakFinishedAlert(dto);
      }
      breakWasOnBreak = false;
      break;

    case "CLOCKED_OUT":
      // 退勤後は休憩を開始できないが、パネルごと非表示にはしない(⑰)。
      // 以前はここでパネル自体を隠していたが、退勤時刻をあらかじめ(例えば朝のうちに
      // その日の予定として)入力しているユーザーの場合、実際にはまだ勤務中でも
      // このCLOCKED_OUT状態になり、休憩ボタンが理由もわからず消えて見える不具合の
      // 原因になっていたため、必ず理由が分かる形で表示する
      idleBlock.hidden = false;
      startBtn.disabled = true;
      startBtn.textContent = `本日の休憩時間は${formatDurationLabel(dto.breakDurationMinutes)}経ちました`;
      document.getElementById("breakUsageHint").hidden = true;
      breakWasOnBreak = false;
      break;

    default:
      panel.hidden = true;
      breakWasOnBreak = false;
      break;
  }
}

/** 本日ここまでの休憩使用状況(分割して取っている場合の目安)を表示する */
function renderBreakUsageHint(dto) {
  const hintEl = document.getElementById("breakUsageHint");
  if (dto.usedMinutesToday > 0) {
    hintEl.textContent =
      `本日の休憩: ${dto.usedMinutesToday}分使用済み(残り${dto.remainingBudgetMinutes}分)`;
    hintEl.hidden = false;
  } else {
    hintEl.hidden = true;
  }
}

// ------------------------------------------------------------------
// カウントダウン(③④)。サーバーから受け取った残り秒数を起点に、1秒ごとに見た目だけ減らす。
// 0になったら必ずサーバーへ再確認し(⑭)、サーバーが確定した結果で表示を更新する。
// ------------------------------------------------------------------
function startBreakCountdown(initialSeconds) {
  breakRemainingSeconds = Math.max(0, initialSeconds);
  renderBreakRemaining();
  if (breakRemainingSeconds <= 0) {
    refreshBreakStatus(true);
    return;
  }
  breakCountdownTimer = setInterval(() => {
    breakRemainingSeconds -= 1;
    if (breakRemainingSeconds <= 0) {
      stopBreakCountdown();
      refreshBreakStatus(true); // ⑭ JSタイマーだけで確定させず、必ずサーバーに確認する
      return;
    }
    renderBreakRemaining();
  }, 1000);
}

function stopBreakCountdown() {
  if (breakCountdownTimer) {
    clearInterval(breakCountdownTimer);
    breakCountdownTimer = null;
  }
}

/** 休憩中、サーバーの残り時間と定期的に同期する(タブのスリープ等でのズレを補正する) */
function startBreakResync() {
  breakResyncTimer = setInterval(() => refreshBreakStatus(false), BREAK_RESYNC_INTERVAL_MS);
}

function stopBreakResync() {
  if (breakResyncTimer) {
    clearInterval(breakResyncTimer);
    breakResyncTimer = null;
  }
}

function renderBreakRemaining() {
  const el = document.getElementById("breakRemaining");
  const m = Math.floor(breakRemainingSeconds / 60);
  const s = breakRemainingSeconds % 60;
  el.textContent = `${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`;
  el.classList.toggle("is-urgent", breakRemainingSeconds <= BREAK_URGENT_SECONDS);
}

// ------------------------------------------------------------------
// 60分経過アラート(⑤⑳)。音・ブラウザ通知に対応するが、いずれも使えない環境を想定し、
// 画面表示(モーダル)は必ず出す。
// ------------------------------------------------------------------
function showBreakFinishedAlert(dto) {
  const message = dto.message || "休憩時間が終了しました。勤怠に休憩時間を反映しました。";
  document.getElementById("breakAlertMessage").textContent = message;
  document.getElementById("breakAlertOverlay").hidden = false;

  playBreakAlarmSound();
  showBrowserNotification(message);
}

function closeBreakAlert() {
  document.getElementById("breakAlertOverlay").hidden = true;
}

/** Web Audio APIで短いアラーム音を生成する(音声ファイルを追加で用意しなくてよい) */
function playBreakAlarmSound() {
  try {
    const AudioCtx = window.AudioContext || window.webkitAudioContext;
    if (!AudioCtx) return;
    const ctx = new AudioCtx();
    const beepAt = (delaySec, freq) => {
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.type = "sine";
      osc.frequency.value = freq;
      gain.gain.setValueAtTime(0.0001, ctx.currentTime + delaySec);
      gain.gain.exponentialRampToValueAtTime(0.25, ctx.currentTime + delaySec + 0.02);
      gain.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + delaySec + 0.35);
      osc.connect(gain).connect(ctx.destination);
      osc.start(ctx.currentTime + delaySec);
      osc.stop(ctx.currentTime + delaySec + 0.4);
    };
    beepAt(0, 880);
    beepAt(0.45, 880);
    beepAt(0.9, 1046.5);
  } catch (e) {
    // ブラウザの自動再生制限などで失敗しても、画面上の通知(モーダル)は既に表示済みなので無視する(⑳)
    console.error("休憩アラーム音の再生に失敗しました", e);
  }
}

function ensureNotificationPermission() {
  if (notificationPermissionRequested) return;
  notificationPermissionRequested = true;
  try {
    if ("Notification" in window && Notification.permission === "default") {
      Notification.requestPermission().catch(() => {});
    }
  } catch (e) {
    console.error(e);
  }
}

function showBrowserNotification(message) {
  try {
    if ("Notification" in window && Notification.permission === "granted") {
      new Notification("休憩時間が終了しました", { body: message, icon: undefined });
    }
  } catch (e) {
    // 通知許可が無い/対応していない環境でも、画面上の通知は既に表示済みなので無視する(⑤)
    console.error("ブラウザ通知の表示に失敗しました", e);
  }
}

// ------------------------------------------------------------------

function fmtTime(hhmmss) {
  if (!hhmmss) return "-";
  return hhmmss.substring(0, 5);
}

// 休憩の合計持ち時間(分)を「1時間」「90分」のような読みやすい表記に変換する。
// 60分ちょうどの場合は「1時間」、それ以外は「n分」と表示する。
function formatDurationLabel(minutes) {
  const total = typeof minutes === "number" && minutes > 0 ? minutes : 60;
  if (total % 60 === 0) {
    return `${total / 60}時間`;
  }
  return `${total}分`;
}

function setBreakStatusMessage(text) {
  const el = document.getElementById("breakStatusMsg");
  el.textContent = text;
  el.hidden = !text;
}
