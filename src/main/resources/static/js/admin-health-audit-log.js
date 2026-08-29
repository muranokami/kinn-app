// ------------------------------------------------------------------
// 健康管理 監査ログ検索(管理者)
// 期間・対象ユーザー・操作種別で絞り込み、自社の従業員分のみを表示する
// (companyIdでのスコープ分離はサーバー側で必ず行われるため、ここでは絞り込み条件を
//  組み立ててAPIへ渡すだけでよい)。
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

window.addEventListener("DOMContentLoaded", () => {
  document.getElementById("searchBtn").addEventListener("click", search);
  loadEmployeeOptions().finally(search);
});

/** 対象ユーザー選択肢を読み込む。既存の従業員一覧API(/api/admin/employees)を再利用する */
async function loadEmployeeOptions() {
  try {
    const res = await fetch("/api/admin/employees");
    if (!res.ok) return;
    const employees = await res.json();
    const select = document.getElementById("targetSelect");
    employees.forEach((e) => {
      const opt = document.createElement("option");
      opt.value = e.loginId;
      opt.textContent = e.fullName;
      select.appendChild(opt);
    });
  } catch (e) {
    console.error(e);
  }
}

async function search() {
  const status = document.getElementById("statusMsg");
  status.textContent = "検索中...";
  status.classList.remove("error");

  const params = new URLSearchParams();
  const from = document.getElementById("fromDate").value;
  const to = document.getElementById("toDate").value;
  const targetLoginId = document.getElementById("targetSelect").value;
  const action = document.getElementById("actionSelect").value;
  const resource = document.getElementById("resourceSelect").value;
  if (from) params.set("from", from);
  if (to) params.set("to", to);
  if (targetLoginId) params.set("targetLoginId", targetLoginId);
  if (action) params.set("action", action);
  if (resource) params.set("resource", resource);

  try {
    const res = await fetch(`/api/admin/health/audit-log?${params.toString()}`);
    if (!res.ok) throw new Error("検索に失敗しました");
    const data = await res.json();
    render(data.items);
    document.getElementById("retentionNote").textContent =
      `監査ログの保持期間: ${data.retentionDays}日(この期間を過ぎた記録は将来的に自動削除の対象となる想定です)`;
    status.textContent = `${data.items.length}件`;
  } catch (e) {
    console.error(e);
    status.textContent = "検索に失敗しました";
    status.classList.add("error");
  }
}

function render(items) {
  const tbody = document.getElementById("logTbody");
  if (!items || items.length === 0) {
    tbody.innerHTML = `<tr class="empty-row"><td colspan="8">該当する記録がありません</td></tr>`;
    return;
  }

  tbody.innerHTML = "";
  items.forEach((log) => {
    const tr = document.createElement("tr");
    const dt = log.occurredAt ? log.occurredAt.replace("T", " ").substring(0, 19) : "";
    const resultClass = AUDIT_RESULT_CLASS[log.result] || "";
    const resultLabel = AUDIT_RESULT_LABELS[log.result] || log.result;
    const target = log.targetName || log.targetEmployeeId || "(個人非紐付け)";
    tr.innerHTML = `
      <td>${escapeHtml(dt)}</td>
      <td>${escapeHtml(log.actorName || log.actorEmployeeId || "-")}</td>
      <td>${escapeHtml(AUDIT_ACTION_LABELS[log.action] || log.action)}</td>
      <td>${escapeHtml(AUDIT_RESOURCE_LABELS[log.resource] || log.resource)}</td>
      <td>${escapeHtml(target)}${log.selfAction ? '<span class="audit-self-tag">本人</span>' : ""}</td>
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
