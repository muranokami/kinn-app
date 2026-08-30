// ------------------------------------------------------------------
// 残業申請(申請者本人向け)。
// 会社・部署・申請者は常にログイン中の本人(principal)からサーバー側で解決されるため、
// このJSからcompanyId/departmentId/applicantUserIdを送ることは一切ない。
// ------------------------------------------------------------------

const STATUS_LABEL = { PENDING: "承認待ち", APPROVED: "承認済み", REJECTED: "却下" };

let withdrawTargetId = null;

function escapeHtml(s) {
  const div = document.createElement("div");
  div.textContent = s ?? "";
  return div.innerHTML;
}

function readErrorMessage(res, fallback) {
  return res.json().then((body) => body?.message || fallback).catch(() => fallback);
}

function setStatus(text, isError) {
  const el = document.getElementById("statusMsg");
  el.textContent = text || "";
  el.classList.toggle("error", !!isError);
}

/** LocalDateのJSON表現("2026-08-30")を "MM/DD" に整形 */
function formatDate(iso) {
  if (!iso) return "-";
  const [, m, d] = iso.split("-");
  return `${m}/${d}`;
}

/** LocalDateTimeのJSON表現("2026-08-30T10:15:30"等)を "MM/DD HH:mm" に整形 */
function formatDateTime(iso) {
  if (!iso) return "-";
  const [datePart, timePart] = iso.split("T");
  if (!datePart || !timePart) return iso;
  const [, m, d] = datePart.split("-");
  return `${m}/${d} ${timePart.substring(0, 5)}`;
}

function minutesToHm(minutes) {
  if (minutes == null) return "-";
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  if (h === 0) return `${m}分`;
  if (m === 0) return `${h}時間`;
  return `${h}時間${m}分`;
}

window.addEventListener("DOMContentLoaded", () => {
  const todayStr = new Date().toISOString().slice(0, 10);
  document.getElementById("c_targetDate").min = todayStr;

  document.getElementById("createForm").addEventListener("submit", onCreateSubmit);
  document.getElementById("withdrawConfirmCancelBtn").addEventListener("click", closeWithdrawConfirm);
  document.getElementById("withdrawConfirmOkBtn").addEventListener("click", onWithdrawConfirmed);

  loadOvertimeList();
});

async function loadOvertimeList() {
  try {
    const res = await fetch("/api/overtime-requests/mine");
    if (!res.ok) {
      setStatus(await readErrorMessage(res, "申請履歴の取得に失敗しました"), true);
      return;
    }
    const list = await res.json();
    renderList(list);
  } catch (e) {
    console.error(e);
    setStatus("申請履歴の取得に失敗しました", true);
  }
}

function renderList(list) {
  const container = document.getElementById("overtimeList");
  const emptyEl = document.getElementById("overtimeEmpty");
  container.innerHTML = "";

  if (!list || list.length === 0) {
    emptyEl.hidden = false;
    return;
  }
  emptyEl.hidden = true;

  list.forEach((r) => {
    const card = document.createElement("div");
    card.className = "overtime-card";

    card.innerHTML = `
      <div class="overtime-card-head">
        <span class="overtime-status-badge status-${r.status}">${escapeHtml(STATUS_LABEL[r.status] || r.status)}</span>
        <span class="overtime-card-date">${formatDate(r.targetDate)} の残業予定</span>
        <span class="overtime-card-minutes">${minutesToHm(r.plannedMinutes)}</span>
      </div>
      <div class="overtime-card-reason">${escapeHtml(r.reason)}</div>
      ${r.status === "REJECTED" && r.rejectReason
        ? `<div class="overtime-card-reject">却下理由: ${escapeHtml(r.rejectReason)}</div>`
        : ""}
      ${r.status === "APPROVED"
        ? `<div class="overtime-card-reject" style="color:var(--jade, #3E6B52);">承認日時: ${formatDateTime(r.approvedAt)}</div>`
        : ""}
    `;

    if (r.status === "PENDING") {
      const actions = document.createElement("div");
      actions.className = "overtime-card-actions";
      const withdrawBtn = document.createElement("button");
      withdrawBtn.type = "button";
      withdrawBtn.className = "btn btn-danger btn-sm";
      withdrawBtn.textContent = "取り下げる";
      withdrawBtn.addEventListener("click", () => openWithdrawConfirm(r));
      actions.appendChild(withdrawBtn);
      card.appendChild(actions);
    }

    container.appendChild(card);
  });
}

// ------------------------------------------------------------------
// 申請
// ------------------------------------------------------------------

async function onCreateSubmit(e) {
  e.preventDefault();
  const dto = {
    targetDate: document.getElementById("c_targetDate").value || null,
    plannedMinutes: Number(document.getElementById("c_plannedMinutes").value),
    reason: document.getElementById("c_reason").value.trim(),
  };
  try {
    const res = await fetch("/api/overtime-requests", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(dto),
    });
    if (!res.ok) {
      setStatus(await readErrorMessage(res, "残業申請に失敗しました"), true);
      return;
    }
    setStatus("残業を申請しました");
    document.getElementById("createForm").reset();
    await loadOvertimeList();
  } catch (e2) {
    console.error(e2);
    setStatus("残業申請に失敗しました", true);
  }
}

// ------------------------------------------------------------------
// 取り下げ
// ------------------------------------------------------------------

function openWithdrawConfirm(r) {
  withdrawTargetId = r.id;
  document.getElementById("withdrawConfirmSummary").textContent =
    `${formatDate(r.targetDate)} / ${minutesToHm(r.plannedMinutes)} / ${r.reason}`;
  document.getElementById("withdrawConfirmModal").hidden = false;
}

function closeWithdrawConfirm() {
  document.getElementById("withdrawConfirmModal").hidden = true;
  withdrawTargetId = null;
}

async function onWithdrawConfirmed() {
  if (!withdrawTargetId) return;
  try {
    const res = await fetch(`/api/overtime-requests/${withdrawTargetId}`, { method: "DELETE" });
    if (!res.ok) {
      setStatus(await readErrorMessage(res, "申請の取り下げに失敗しました"), true);
      closeWithdrawConfirm();
      return;
    }
    setStatus("申請を取り下げました");
    closeWithdrawConfirm();
    await loadOvertimeList();
  } catch (e) {
    console.error(e);
    setStatus("申請の取り下げに失敗しました", true);
  }
}
