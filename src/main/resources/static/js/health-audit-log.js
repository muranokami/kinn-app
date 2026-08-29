// ------------------------------------------------------------------
// 自分の健康データへのアクセス履歴(一般ユーザー専用)
// 「誰が・いつ・何をしたか」だけを表示する。健康情報の値そのものはここには出てこない
// (サーバー側もそもそも保存していない)。
// ------------------------------------------------------------------

const AUDIT_ACTION_LABELS = { VIEW: "閲覧", CREATE: "登録", UPDATE: "更新", DELETE: "削除" };
const AUDIT_RESOURCE_LABELS = {
  PROFILE: "健康プロフィール",
  DAILY_CHECK: "今日の体調チェック",
  HISTORY: "体調チェック履歴",
  SCORE: "健康スコア",
  TREND: "健康推移グラフ",
  ALERT: "健康アラート",
  ANALYSIS: "勤怠×健康分析",
  MONTHLY_RECORD: "月次健康記録",
  ADMIN_DASHBOARD: "管理者ダッシュボード",
  AUDIT_LOG: "監査ログ",
};
const AUDIT_RESULT_LABELS = { SUCCESS: "成功", DENIED: "認可エラー", FAILURE: "失敗" };
const AUDIT_RESULT_CLASS = { SUCCESS: "is-success", DENIED: "is-denied", FAILURE: "is-failure" };

window.addEventListener("DOMContentLoaded", loadMyAuditLog);

async function loadMyAuditLog() {
  try {
    const res = await fetch("/api/health/audit-log");
    if (!res.ok) throw new Error("読み込みに失敗しました");
    const items = await res.json();
    render(items);
  } catch (e) {
    console.error(e);
    document.getElementById("logTbody").innerHTML =
      `<tr class="empty-row"><td colspan="7">読み込みに失敗しました</td></tr>`;
  }
}

function render(items) {
  const tbody = document.getElementById("logTbody");
  document.getElementById("retentionNote").textContent = "";

  if (!items || items.length === 0) {
    tbody.innerHTML = `<tr class="empty-row"><td colspan="7">記録がありません</td></tr>`;
    return;
  }

  tbody.innerHTML = "";
  items.forEach((log) => {
    const tr = document.createElement("tr");
    if (!log.selfAction) tr.classList.add("audit-self-row");
    const dt = log.occurredAt ? log.occurredAt.replace("T", " ").substring(0, 19) : "";
    const resultClass = AUDIT_RESULT_CLASS[log.result] || "";
    const resultLabel = AUDIT_RESULT_LABELS[log.result] || log.result;
    tr.innerHTML = `
      <td>${escapeHtml(dt)}</td>
      <td>${escapeHtml(log.actorName || log.actorEmployeeId || "-")}${log.selfAction ? '<span class="audit-self-tag">本人</span>' : ""}</td>
      <td>${escapeHtml(AUDIT_ACTION_LABELS[log.action] || log.action)}</td>
      <td>${escapeHtml(AUDIT_RESOURCE_LABELS[log.resource] || log.resource)}</td>
      <td>${escapeHtml(log.targetRef || "-")}</td>
      <td>${escapeHtml(log.ipAddress || "-")}</td>
      <td><span class="audit-result-badge ${resultClass}">${escapeHtml(resultLabel)}</span></td>
    `;
    tbody.appendChild(tr);
  });
}

function escapeHtml(s) {
  const div = document.createElement("div");
  div.textContent = s ?? "";
  return div.innerHTML;
}
