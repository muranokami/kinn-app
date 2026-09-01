// ------------------------------------------------------------------
// 残業申請管理(管理者向け)。
// 会社は常にログイン中の管理者自身の会社(principal)からサーバー側で解決されるため、
// このJSからcompanyIdを送ることは一切ない。対象範囲(部署)はdepartmentId(空欄=全部署)で
// 絞り込む。
// ------------------------------------------------------------------

const STATUS_LABEL = { PENDING: "承認待ち", APPROVED: "承認済み", REJECTED: "却下" };

let currentStatus = "";
let currentDepartmentId = "";
let rejectTargetId = null;

function escapeHtml(s) {
  const div = document.createElement("div");
  div.textContent = s ?? "";
  return div.innerHTML;
}

function readErrorMessage(res, fallback) {
  return res.json().then((body) => body?.message || fallback).catch(() => fallback);
}

function setStatus(text, isError, isLoading) {
  const el = document.getElementById("statusMsg");
  el.textContent = text || "";
  el.classList.toggle("error", !!isError);
  el.classList.toggle("is-loading", !!isLoading);
}

/** LocalDateのJSON表現("2026-08-30")を "MM/DD" に整形 */
function formatDate(iso) {
  if (!iso) return "-";
  const [, m, d] = iso.split("-");
  return `${m}/${d}`;
}

function minutesToHm(minutes) {
  if (minutes == null) return "-";
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  if (h === 0) return `${m}分`;
  if (m === 0) return `${h}時間`;
  return `${h}時間${m}分`;
}

window.addEventListener("DOMContentLoaded", async () => {
  document.getElementById("statusTabs").addEventListener("click", onStatusTabClick);
  document.getElementById("departmentFilter").addEventListener("change", onDepartmentFilterChange);
  document.getElementById("rejectCancelBtn").addEventListener("click", closeRejectModal);
  document.getElementById("rejectForm").addEventListener("submit", onRejectSubmit);

  await loadDepartments();
  await loadOvertimeList();
});

// ------------------------------------------------------------------
// フィルター
// ------------------------------------------------------------------

async function loadDepartments() {
  try {
    const res = await fetch("/api/admin/departments");
    if (!res.ok) return;
    const departments = await res.json();
    const sel = document.getElementById("departmentFilter");
    departments.forEach((d) => {
      const opt = document.createElement("option");
      opt.value = d.id;
      opt.textContent = d.name;
      sel.appendChild(opt);
    });
  } catch (e) {
    console.error(e);
  }
}

function onStatusTabClick(e) {
  const btn = e.target.closest("button[data-status]");
  if (!btn) return;
  currentStatus = btn.dataset.status;
  Array.from(document.getElementById("statusTabs").querySelectorAll("button")).forEach((b) => {
    b.classList.toggle("is-active", b === btn);
  });
  loadOvertimeList();
}

function onDepartmentFilterChange() {
  currentDepartmentId = document.getElementById("departmentFilter").value;
  loadOvertimeList();
}

// ------------------------------------------------------------------
// 一覧
// ------------------------------------------------------------------

async function loadOvertimeList() {
  try {
    const params = new URLSearchParams();
    if (currentStatus) params.set("status", currentStatus);
    if (currentDepartmentId) params.set("departmentId", currentDepartmentId);
    const res = await fetch(`/api/admin/overtime-requests?${params.toString()}`);
    if (!res.ok) {
      setStatus(await readErrorMessage(res, "申請一覧の取得に失敗しました"), true);
      return;
    }
    const list = await res.json();
    renderTable(list);
  } catch (e) {
    console.error(e);
    setStatus("申請一覧の取得に失敗しました", true);
  }
}

function renderTable(list) {
  const tbody = document.getElementById("overtimeTbody");
  tbody.innerHTML = "";
  if (!list || list.length === 0) {
    tbody.innerHTML = '<tr class="empty-row"><td colspan="8">該当する申請はありません</td></tr>';
    return;
  }
  list.forEach((r) => {
    const tr = document.createElement("tr");
    tr.innerHTML = `
      <td><span class="overtime-status-badge status-${r.status}">${escapeHtml(STATUS_LABEL[r.status] || r.status)}</span></td>
      <td>${formatDate(r.targetDate)}</td>
      <td>${escapeHtml(r.departmentName || "-")}</td>
      <td>${escapeHtml(r.applicantName || "-")}</td>
      <td>${minutesToHm(r.plannedMinutes)}</td>
      <td class="col-reason">${escapeHtml(r.reason)}</td>
      <td class="col-decision"></td>
      <td class="row-actions"></td>
    `;
    const decisionTd = tr.querySelector(".col-decision");
    if (r.status === "REJECTED" && r.rejectReason) {
      decisionTd.textContent = `却下理由: ${r.rejectReason}`;
      decisionTd.style.color = "var(--shu-deep, #C33B2E)";
    } else if (r.status === "APPROVED") {
      decisionTd.textContent = `承認者: ${r.approverName || "-"}`;
    } else {
      decisionTd.textContent = "-";
    }

    const actionsTd = tr.querySelector(".row-actions");
    if (r.status === "PENDING") {
      const approveBtn = document.createElement("button");
      approveBtn.type = "button";
      approveBtn.className = "btn btn-success btn-sm";
      approveBtn.textContent = "承認";
      approveBtn.addEventListener("click", () => onApproveClick(r));
      const rejectBtn = document.createElement("button");
      rejectBtn.type = "button";
      rejectBtn.className = "btn btn-danger btn-sm";
      rejectBtn.textContent = "却下";
      rejectBtn.addEventListener("click", () => openRejectModal(r));
      actionsTd.appendChild(approveBtn);
      actionsTd.appendChild(rejectBtn);
    } else {
      actionsTd.textContent = "-";
    }
    tbody.appendChild(tr);
  });
}

// ------------------------------------------------------------------
// 承認
// ------------------------------------------------------------------

async function onApproveClick(r) {
  if (!window.confirm(`${formatDate(r.targetDate)} / ${r.applicantName || "-"} の残業申請を承認しますか？`)) return;
  try {
    const res = await fetch(`/api/admin/overtime-requests/${r.id}/approve`, { method: "PUT" });
    if (!res.ok) {
      setStatus(await readErrorMessage(res, "承認に失敗しました"), true);
      return;
    }
    setStatus("申請を承認しました");
    await loadOvertimeList();
  } catch (e) {
    console.error(e);
    setStatus("承認に失敗しました", true);
  }
}

// ------------------------------------------------------------------
// 却下
// ------------------------------------------------------------------

function openRejectModal(r) {
  rejectTargetId = r.id;
  document.getElementById("rejectStatus").textContent = "";
  document.getElementById("r_rejectReason").value = "";
  document.getElementById("rejectSummary").textContent =
    `${formatDate(r.targetDate)} / ${r.applicantName || "-"} / ${minutesToHm(r.plannedMinutes)}`;
  document.getElementById("rejectModal").hidden = false;
}

function closeRejectModal() {
  document.getElementById("rejectModal").hidden = true;
  rejectTargetId = null;
}

async function onRejectSubmit(e) {
  e.preventDefault();
  if (!rejectTargetId) return;
  const rejectReason = document.getElementById("r_rejectReason").value.trim();
  if (!rejectReason) {
    document.getElementById("rejectStatus").textContent = "却下理由は必須です";
    document.getElementById("rejectStatus").classList.add("error");
    return;
  }
  try {
    const res = await fetch(`/api/admin/overtime-requests/${rejectTargetId}/reject`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ rejectReason }),
    });
    if (!res.ok) {
      document.getElementById("rejectStatus").textContent = await readErrorMessage(res, "却下に失敗しました");
      document.getElementById("rejectStatus").classList.add("error");
      return;
    }
    closeRejectModal();
    setStatus("申請を却下しました");
    await loadOvertimeList();
  } catch (e2) {
    console.error(e2);
    document.getElementById("rejectStatus").textContent = "却下に失敗しました";
    document.getElementById("rejectStatus").classList.add("error");
  }
}
