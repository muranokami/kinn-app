// ------------------------------------------------------------------
// 管理者向けタスク管理(⑦⑩〜⑳㉖㉗㉘)。
// 部署・担当者・依頼者・ステータス・優先度・日付で絞り込んだ一覧+集計を表示し、
// タスクの登録・編集・削除・担当ユーザーの割り当てを行う。会社は常にログイン中の管理者
// 自身の会社(principal)からサーバー側で解決されるため、このJSからcompanyIdを送ることは一切ない。
// ------------------------------------------------------------------

const TASK_STATUS_LABEL = { UNRESOLVED: "未対応", IN_PROGRESS: "対応中", COMPLETED: "完了" };
const TASK_PRIORITY_LABEL = { HIGH: "高", MEDIUM: "中", LOW: "低" };

let departmentsCache = [];
let currentDepartmentId = ""; // "" = 全部署
let currentAssignedUserId = ""; // "" = すべて
let currentRequesterUserId = ""; // "" = すべて(⑲依頼者別確認)
let currentStatus = ""; // "" = すべて
let currentPriority = ""; // "" = すべて
let currentDate = ""; // "" = 日付指定なし(全期間)。⑰⑱
let editingTaskId = null;
let deleteTargetId = null;

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

window.addEventListener("DOMContentLoaded", async () => {
  document.getElementById("departmentFilter").addEventListener("change", onDepartmentFilterChange);
  document.getElementById("userFilter").addEventListener("change", onUserFilterChange);
  document.getElementById("requesterFilter").addEventListener("change", onRequesterFilterChange);
  document.getElementById("priorityFilter").addEventListener("change", onPriorityFilterChange);
  document.getElementById("statusTabs").addEventListener("click", onStatusTabClick);

  document.getElementById("dateFilter").addEventListener("change", (e) => setDateFilter(e.target.value));
  document.getElementById("datePrevBtn").addEventListener("click", () => shiftDateFilter(-1));
  document.getElementById("dateNextBtn").addEventListener("click", () => shiftDateFilter(1));
  document.getElementById("dateTodayBtn").addEventListener("click", () => setDateFilter(todayIso()));
  document.getElementById("dateClearBtn").addEventListener("click", () => setDateFilter(""));

  document.getElementById("c_department").addEventListener("change", onCreateDepartmentChange);
  document.getElementById("createForm").addEventListener("submit", onCreateSubmit);

  document.getElementById("e_department").addEventListener("change", onEditDepartmentChange);
  document.getElementById("editForm").addEventListener("submit", onEditSubmit);
  document.getElementById("editCloseBtn").addEventListener("click", closeEditModal);
  document.getElementById("editDeleteBtn").addEventListener("click", onEditDeleteClick);

  document.getElementById("deleteConfirmCancelBtn").addEventListener("click", closeDeleteConfirm);
  document.getElementById("deleteConfirmOkBtn").addEventListener("click", onDeleteConfirmed);

  await loadDepartments();
  await populateEmployeeSelect(document.getElementById("requesterFilter"), null, true); // 依頼者は会社全体から選ぶ(⑲)
  await onDepartmentFilterChange();
  await onCreateDepartmentChange();
});

function todayIso() {
  const now = new Date();
  const m = String(now.getMonth() + 1).padStart(2, "0");
  const d = String(now.getDate()).padStart(2, "0");
  return `${now.getFullYear()}-${m}-${d}`;
}

// ------------------------------------------------------------------
// 部署・従業員セレクトの用意
// ------------------------------------------------------------------

async function loadDepartments() {
  try {
    const res = await fetch("/api/admin/departments");
    if (!res.ok) return;
    departmentsCache = await res.json();

    const filterSel = document.getElementById("departmentFilter");
    const cSel = document.getElementById("c_department");
    const eSel = document.getElementById("e_department");
    [filterSel, cSel, eSel].forEach((sel) => {
      // フィルター用selectだけ既存の「全部署」optionを残す
      const keepFirst = sel === filterSel;
      Array.from(sel.querySelectorAll("option")).forEach((o, i) => {
        if (!(keepFirst && i === 0)) o.remove();
      });
    });
    departmentsCache.forEach((d) => {
      [filterSel, cSel, eSel].forEach((sel) => {
        const opt = document.createElement("option");
        opt.value = d.id;
        opt.textContent = d.name;
        sel.appendChild(opt);
      });
    });
  } catch (e) {
    console.error(e);
  }
}

async function fetchEmployees(departmentId) {
  const url = departmentId ? `/api/admin/employees?departmentId=${departmentId}` : "/api/admin/employees";
  const res = await fetch(url);
  if (!res.ok) return [];
  return res.json();
}

async function populateEmployeeSelect(selectEl, departmentId, includeAllOption) {
  const employees = await fetchEmployees(departmentId);
  selectEl.innerHTML = "";
  if (includeAllOption) {
    const opt = document.createElement("option");
    opt.value = "";
    opt.textContent = "すべて";
    selectEl.appendChild(opt);
  }
  employees.forEach((u) => {
    const opt = document.createElement("option");
    opt.value = u.userId;
    opt.textContent = u.fullName;
    selectEl.appendChild(opt);
  });
}

// ------------------------------------------------------------------
// フィルター(⑫⑬⑭)
// ------------------------------------------------------------------

async function onDepartmentFilterChange() {
  currentDepartmentId = document.getElementById("departmentFilter").value;
  currentAssignedUserId = "";
  await populateEmployeeSelect(document.getElementById("userFilter"), currentDepartmentId || null, true);
  await reloadAll();
}

function onUserFilterChange() {
  currentAssignedUserId = document.getElementById("userFilter").value;
  loadTaskList();
}

function onRequesterFilterChange() {
  currentRequesterUserId = document.getElementById("requesterFilter").value;
  loadTaskList();
}

function onPriorityFilterChange() {
  currentPriority = document.getElementById("priorityFilter").value;
  loadTaskList();
}

function onStatusTabClick(e) {
  const btn = e.target.closest("button[data-status]");
  if (!btn) return;
  currentStatus = btn.dataset.status;
  document.querySelectorAll("#statusTabs button").forEach((b) => b.classList.toggle("is-active", b === btn));
  loadTaskList();
}

function setDateFilter(isoDate) {
  currentDate = isoDate || "";
  document.getElementById("dateFilter").value = currentDate;
  loadTaskList();
}

function shiftDateFilter(deltaDays) {
  const base = currentDate || todayIso();
  const d = new Date(base + "T00:00:00");
  d.setDate(d.getDate() + deltaDays);
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  setDateFilter(`${d.getFullYear()}-${m}-${day}`);
}

async function reloadAll() {
  await loadTaskList();
  await loadUserProgress();
  await loadDepartmentProgress();
}

// ------------------------------------------------------------------
// タスク一覧+集計(⑩⑪⑮⑯㉖)
// ------------------------------------------------------------------

async function loadTaskList() {
  try {
    const params = new URLSearchParams();
    if (currentDepartmentId) params.set("departmentId", currentDepartmentId);
    if (currentAssignedUserId) params.set("assignedUserId", currentAssignedUserId);
    if (currentRequesterUserId) params.set("requesterUserId", currentRequesterUserId);
    if (currentStatus) params.set("status", currentStatus);
    if (currentPriority) params.set("priority", currentPriority);
    if (currentDate) params.set("date", currentDate);

    const res = await fetch(`/api/admin/tasks?${params.toString()}`);
    if (!res.ok) {
      setStatus(await readErrorMessage(res, "タスク一覧の取得に失敗しました"), true);
      return;
    }
    const dto = await res.json();
    const dateLabel = dto.date ? formatDateLabel(dto.date) : "全期間";
    document.getElementById("summaryTitle").textContent =
      `${dateLabel} ${dto.departmentName || "全部署"} タスク状況`;
    renderSummary(dto.summary);
    renderTaskTable(dto.tasks);
    setStatus("");
  } catch (e) {
    console.error(e);
    setStatus("タスク一覧の取得に失敗しました", true);
  }
}

function formatDateLabel(isoDate) {
  const WEEKDAY = ["日", "月", "火", "水", "木", "金", "土"];
  const d = new Date(isoDate + "T00:00:00");
  return `${d.getFullYear()}/${d.getMonth() + 1}/${d.getDate()}(${WEEKDAY[d.getDay()]})`;
}

function renderSummary(summary) {
  if (!summary) return;
  document.getElementById("s_unresolved").textContent = summary.unresolvedCount;
  document.getElementById("s_inProgress").textContent = summary.inProgressCount;
  document.getElementById("s_completed").textContent = summary.completedCount;
  document.getElementById("s_overdue").textContent = summary.overdueCount;
  document.getElementById("s_total").textContent = summary.totalCount;
}

function renderTaskTable(tasks) {
  const tbody = document.getElementById("taskTbody");
  tbody.innerHTML = "";
  if (!tasks || tasks.length === 0) {
    tbody.innerHTML = '<tr class="empty-row"><td colspan="9">該当するタスクはありません</td></tr>';
    return;
  }
  tasks.forEach((t) => {
    const tr = document.createElement("tr");
    tr.innerHTML = `
      <td>${escapeHtml(t.assignedUserName || "-")}</td>
      <td>${escapeHtml(t.departmentName || "-")}</td>
      <td style="text-align:left;">${escapeHtml(t.title)}</td>
      <td style="text-align:left;">${escapeHtml(t.description || "-")}</td>
      <td>${escapeHtml(t.createdByName || "-")}</td>
      <td><span class="task-status-badge status-${t.status}">${escapeHtml(t.statusLabel)}</span></td>
      <td><span class="priority-badge priority-${t.priority}">${escapeHtml(t.priorityLabel)}</span></td>
      <td>${escapeHtml(t.dueDate || "-")}${t.overdue ? ' <span class="overdue-badge">期限超過</span>' : ""}</td>
      <td class="row-actions"></td>
    `;
    const actionsTd = tr.querySelector(".row-actions");
    const editBtn = document.createElement("button");
    editBtn.type = "button";
    editBtn.className = "btn btn-secondary btn-sm";
    editBtn.textContent = "編集";
    editBtn.addEventListener("click", () => openEditModal(t));
    const delBtn = document.createElement("button");
    delBtn.type = "button";
    delBtn.className = "btn btn-danger btn-sm";
    delBtn.textContent = "削除";
    delBtn.addEventListener("click", () => openDeleteConfirm(t));
    actionsTd.appendChild(editBtn);
    actionsTd.appendChild(delBtn);
    tbody.appendChild(tr);
  });
}

// ------------------------------------------------------------------
// ユーザー別・部署別進捗(㉗㉘)
// ------------------------------------------------------------------

async function loadUserProgress() {
  try {
    const params = new URLSearchParams();
    if (currentDepartmentId) params.set("departmentId", currentDepartmentId);
    const res = await fetch(`/api/admin/tasks/progress/users?${params.toString()}`);
    if (!res.ok) return;
    const rows = await res.json();
    renderProgressGrid(document.getElementById("userProgressGrid"), rows, "タスクなし");
  } catch (e) {
    console.error(e);
  }
}

async function loadDepartmentProgress() {
  try {
    const res = await fetch("/api/admin/tasks/progress/departments");
    if (!res.ok) return;
    const rows = await res.json();
    renderProgressGrid(document.getElementById("deptProgressGrid"), rows, "タスクなし");
  } catch (e) {
    console.error(e);
  }
}

function renderProgressGrid(container, rows, emptyLabel) {
  container.innerHTML = "";
  if (!rows || rows.length === 0) {
    container.innerHTML = `<p class="task-column-empty">${escapeHtml(emptyLabel)}</p>`;
    return;
  }
  rows.forEach((r) => {
    const card = document.createElement("div");
    card.className = "task-progress-card";
    card.innerHTML = `
      <div class="tp-name">${escapeHtml(r.name)}</div>
      <div class="tp-row"><span>未対応</span><span class="tp-value">${r.unresolvedCount}</span></div>
      <div class="tp-row"><span>対応中</span><span class="tp-value">${r.inProgressCount}</span></div>
      <div class="tp-row"><span>完了</span><span class="tp-value">${r.completedCount}</span></div>
    `;
    container.appendChild(card);
  });
}

// ------------------------------------------------------------------
// 登録(⑦)
// ------------------------------------------------------------------

async function onCreateDepartmentChange() {
  const deptId = document.getElementById("c_department").value;
  await populateEmployeeSelect(document.getElementById("c_assignedUser"), deptId || null, false);
}

async function onCreateSubmit(e) {
  e.preventDefault();
  const dto = {
    departmentId: Number(document.getElementById("c_department").value),
    assignedUserId: Number(document.getElementById("c_assignedUser").value),
    title: document.getElementById("c_title").value.trim(),
    description: document.getElementById("c_description").value.trim() || null,
    priority: document.getElementById("c_priority").value,
    startDate: document.getElementById("c_startDate").value || null,
    dueDate: document.getElementById("c_dueDate").value || null,
    notes: document.getElementById("c_notes").value.trim() || null,
  };
  try {
    const res = await fetch("/api/admin/tasks", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(dto),
    });
    if (!res.ok) {
      setStatus(await readErrorMessage(res, "タスクの登録に失敗しました"), true);
      return;
    }
    setStatus("タスクを登録しました");
    document.getElementById("createForm").reset();
    document.getElementById("c_priority").value = "MEDIUM";
    await onCreateDepartmentChange();
    await reloadAll();
  } catch (e2) {
    console.error(e2);
    setStatus("タスクの登録に失敗しました", true);
  }
}

// ------------------------------------------------------------------
// 編集・削除(⑲⑳)
// ------------------------------------------------------------------

async function onEditDepartmentChange() {
  const deptId = document.getElementById("e_department").value;
  await populateEmployeeSelect(document.getElementById("e_assignedUser"), deptId || null, false);
}

async function openEditModal(t) {
  editingTaskId = t.id;
  document.getElementById("editStatus").textContent = "";
  document.getElementById("e_department").value = String(t.departmentId);
  await populateEmployeeSelect(document.getElementById("e_assignedUser"), t.departmentId, false);
  document.getElementById("e_assignedUser").value = String(t.assignedUserId);
  document.getElementById("e_title").value = t.title || "";
  document.getElementById("e_description").value = t.description || "";
  document.getElementById("e_status").value = t.status;
  document.getElementById("e_priority").value = t.priority;
  document.getElementById("e_startDate").value = t.startDate || "";
  document.getElementById("e_dueDate").value = t.dueDate || "";
  document.getElementById("e_notes").value = t.notes || "";
  document.getElementById("editModal").hidden = false;
}

function closeEditModal() {
  document.getElementById("editModal").hidden = true;
  editingTaskId = null;
}

async function onEditSubmit(e) {
  e.preventDefault();
  if (!editingTaskId) return;
  const dto = {
    departmentId: Number(document.getElementById("e_department").value),
    assignedUserId: Number(document.getElementById("e_assignedUser").value),
    title: document.getElementById("e_title").value.trim(),
    description: document.getElementById("e_description").value.trim() || null,
    status: document.getElementById("e_status").value,
    priority: document.getElementById("e_priority").value,
    startDate: document.getElementById("e_startDate").value || null,
    dueDate: document.getElementById("e_dueDate").value || null,
    notes: document.getElementById("e_notes").value.trim() || null,
  };
  try {
    const res = await fetch(`/api/admin/tasks/${editingTaskId}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(dto),
    });
    if (!res.ok) {
      document.getElementById("editStatus").textContent = await readErrorMessage(res, "タスクの更新に失敗しました");
      document.getElementById("editStatus").classList.add("error");
      return;
    }
    closeEditModal();
    setStatus("タスクを更新しました");
    await reloadAll();
  } catch (e2) {
    console.error(e2);
    document.getElementById("editStatus").textContent = "タスクの更新に失敗しました";
    document.getElementById("editStatus").classList.add("error");
  }
}

function onEditDeleteClick() {
  if (!editingTaskId) return;
  deleteTargetId = editingTaskId;
  document.getElementById("deleteConfirmSummary").textContent = document.getElementById("e_title").value;
  document.getElementById("deleteConfirmModal").hidden = false;
}

function openDeleteConfirm(t) {
  deleteTargetId = t.id;
  document.getElementById("deleteConfirmSummary").textContent = t.title;
  document.getElementById("deleteConfirmModal").hidden = false;
}

function closeDeleteConfirm() {
  document.getElementById("deleteConfirmModal").hidden = true;
  deleteTargetId = null;
}

async function onDeleteConfirmed() {
  if (!deleteTargetId) return;
  try {
    const res = await fetch(`/api/admin/tasks/${deleteTargetId}`, { method: "DELETE" });
    if (!res.ok) {
      setStatus(await readErrorMessage(res, "タスクの削除に失敗しました"), true);
      closeDeleteConfirm();
      closeEditModal();
      return;
    }
    setStatus("タスクを削除しました");
    closeDeleteConfirm();
    closeEditModal();
    await reloadAll();
  } catch (e) {
    console.error(e);
    setStatus("タスクの削除に失敗しました", true);
  }
}
