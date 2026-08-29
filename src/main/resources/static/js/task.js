// ------------------------------------------------------------------
// タスク管理(一般ユーザー: マイタスク + 部署別タスク一覧)
// (①②③④⑤⑥⑦⑧⑨⑩⑪⑬⑭⑮⑰⑱⑳㉓㉞)。
// 会社・部署はサーバー側で常に自分自身に解決されるため、このJSからは一切送らない。
// 担当者(assignedUserId)は登録フォームでのみ、同じ部署のメンバーから選べる(②)。
// ------------------------------------------------------------------

const TASK_STATUS_LABEL = { UNRESOLVED: "未対応", IN_PROGRESS: "対応中", COMPLETED: "完了" };
const TASK_PRIORITY_LABEL = { HIGH: "高", MEDIUM: "中", LOW: "低" };

let currentUser = null;
let boardCache = null;
let editingTaskId = null; // null = 新規登録モード
let deleteTargetId = null;
let departmentMembersCache = []; // ②登録フォームの担当者選択肢
let deptDayDate = ""; // ⑩⑪ 部署別タスク一覧で絞り込み中の日付(YYYY-MM-DD)。""=全期間(①既定表示)
let deptDayUserFilter = ""; // ⑦ ""=すべて
let deptDayStatusFilter = ""; // ⑧ ""=すべて
let deptDayGroupMode = "table"; // "table"=①一覧 / "status"=⑬ステータス別 / "user"=⑭担当者別

/** ⑥自分が担当しているタスクだと分かるように、担当者名の代わりに「自分」と表示する */
function assigneeDisplay(t) {
  if (currentUser && t.assignedUserId === currentUser.userId) return "自分";
  return t.assignedUserName || "未割り当て";
}

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
  document.getElementById("addTaskBtn").addEventListener("click", openCreateForm);
  document.getElementById("taskCancelBtn").addEventListener("click", closeForm);
  document.getElementById("taskForm").addEventListener("submit", onFormSubmit);
  document.getElementById("t_singleDay").addEventListener("change", onSingleDayToggle);
  document.getElementById("t_singleDayDate").addEventListener("change", onSingleDayDateChange);

  document.getElementById("detailCloseBtn").addEventListener("click", closeDetailModal);
  document.getElementById("detailEditBtn").addEventListener("click", onDetailEditClick);
  document.getElementById("detailDeleteBtn").addEventListener("click", onDetailDeleteClick);
  document.getElementById("detailStatusSelect").addEventListener("change", onDetailStatusChange);

  document.getElementById("deleteConfirmCancelBtn").addEventListener("click", closeDeleteConfirm);
  document.getElementById("deleteConfirmOkBtn").addEventListener("click", onDeleteConfirmed);

  document.getElementById("taskViewTabs").addEventListener("click", onViewTabClick);
  document.getElementById("deptPrevDayBtn").addEventListener("click", () => shiftDeptDay(-1));
  document.getElementById("deptNextDayBtn").addEventListener("click", () => shiftDeptDay(1));
  document.getElementById("deptTodayBtn").addEventListener("click", () => setDeptDay(todayIso()));
  document.getElementById("deptAllDatesBtn").addEventListener("click", () => setDeptDay(""));
  document.getElementById("deptDayInput").addEventListener("change", (e) => setDeptDay(e.target.value));
  document.getElementById("deptUserFilter").addEventListener("change", onDeptUserFilterChange);
  document.getElementById("deptStatusTabs").addEventListener("click", onDeptStatusTabClick);
  document.getElementById("deptGroupTabs").addEventListener("click", onDeptGroupTabClick);

  await loadMe();
  await loadDepartmentMembers();
  await loadBoard();
  await loadTaskAlerts();
});

// ------------------------------------------------------------------
// 締め切りアラート(本日締め切り・期限切れの、自分が担当する未完了タスク)
// ------------------------------------------------------------------
async function loadTaskAlerts() {
  const panel = document.getElementById("taskAlertPanel");
  const list = document.getElementById("taskAlertList");
  try {
    const res = await fetch("/api/tasks/alerts");
    if (!res.ok) return;
    const data = await res.json();
    const items = data.items || [];
    if (items.length === 0) {
      panel.hidden = true;
      return;
    }
    list.innerHTML = "";
    items.forEach((item) => {
      const li = document.createElement("li");
      li.className = "task-alert-row";
      li.innerHTML = `
        <span class="task-alert-kind kind-${item.kind}">${escapeHtml(item.kindLabel)}</span>
        <span class="task-alert-title">${escapeHtml(item.title)}</span>
        <span class="priority-badge priority-${item.priority}">${escapeHtml(item.priorityLabel)}</span>
        <span class="task-alert-due">期限: ${escapeHtml(item.dueDate ?? "-")}</span>
      `;
      li.addEventListener("click", () => openDetailModal(item.id));
      list.appendChild(li);
    });
    panel.hidden = false;
  } catch (e) {
    console.error(e);
  }
}

function todayIso() {
  const now = new Date();
  const m = String(now.getMonth() + 1).padStart(2, "0");
  const d = String(now.getDate()).padStart(2, "0");
  return `${now.getFullYear()}-${m}-${d}`;
}

async function loadMe() {
  try {
    const res = await fetch("/api/auth/me");
    if (!res.ok) return;
    currentUser = await res.json();
    if (currentUser.role === "ADMIN") {
      document.getElementById("adminTaskLink").hidden = false;
    }
  } catch (e) {
    console.error(e);
  }
}

// ------------------------------------------------------------------
// マイタスク/部署の日別タスクの表示切り替え
// ------------------------------------------------------------------

function onViewTabClick(e) {
  const btn = e.target.closest("button[data-mode]");
  if (!btn) return;
  const mode = btn.dataset.mode;
  document.querySelectorAll("#taskViewTabs button").forEach((b) => b.classList.toggle("is-active", b === btn));
  document.getElementById("myTaskView").hidden = mode !== "my";
  document.getElementById("deptDayView").hidden = mode !== "deptDay";
  if (mode === "deptDay") {
    loadDeptDay();
  }
}

// ------------------------------------------------------------------
// 部署メンバー(②登録フォームの担当者選択肢・⑮ユーザー別フィルター)
// ------------------------------------------------------------------

async function loadDepartmentMembers() {
  try {
    const res = await fetch("/api/tasks/department/members");
    if (!res.ok) return;
    departmentMembersCache = await res.json();
    populateAssigneeSelect();
    populateDeptUserFilter();
  } catch (e) {
    console.error(e);
  }
}

function populateAssigneeSelect() {
  const sel = document.getElementById("t_assignedUser");
  sel.innerHTML = "";
  departmentMembersCache.forEach((m) => {
    const opt = document.createElement("option");
    opt.value = m.userId;
    const isSelf = currentUser && m.userId === currentUser.userId;
    opt.textContent = isSelf ? `${m.fullName}(自分)` : m.fullName;
    if (isSelf) opt.selected = true;
    sel.appendChild(opt);
  });
}

function populateDeptUserFilter() {
  const sel = document.getElementById("deptUserFilter");
  sel.innerHTML = '<option value="">すべて</option>';
  if (currentUser) {
    const selfOpt = document.createElement("option");
    selfOpt.value = currentUser.userId;
    selfOpt.textContent = "自分";
    sel.appendChild(selfOpt);
  }
  departmentMembersCache
    .filter((m) => !currentUser || m.userId !== currentUser.userId)
    .forEach((m) => {
      const opt = document.createElement("option");
      opt.value = m.userId;
      opt.textContent = m.fullName;
      sel.appendChild(opt);
    });
}

// ------------------------------------------------------------------
// 一覧読み込み・描画
// ------------------------------------------------------------------

async function loadBoard() {
  try {
    const res = await fetch("/api/tasks/my");
    if (!res.ok) {
      setStatus(await readErrorMessage(res, "タスクの取得に失敗しました"), true);
      return;
    }
    boardCache = await res.json();
    renderSummary(boardCache.summary);
    renderColumn("unresolved", "col_unresolved", boardCache.unresolved);
    renderColumn("inProgress", "col_inProgress", boardCache.inProgress);
    renderColumn("completed", "col_completed", boardCache.completed);
    setStatus("");
  } catch (e) {
    console.error(e);
    setStatus("タスクの取得に失敗しました", true);
  }
}

function renderSummary(summary) {
  if (!summary) return;
  document.getElementById("s_unresolved").textContent = summary.unresolvedCount;
  document.getElementById("s_inProgress").textContent = summary.inProgressCount;
  document.getElementById("s_completed").textContent = summary.completedCount;
  document.getElementById("s_overdue").textContent = summary.overdueCount;
}

function renderColumn(key, elementId, tasks) {
  document.getElementById(elementId + "_count").textContent = tasks.length;
  const wrap = document.getElementById(elementId);
  wrap.innerHTML = "";
  if (tasks.length === 0) {
    const empty = document.createElement("p");
    empty.className = "task-column-empty";
    empty.textContent = "タスクはありません";
    wrap.appendChild(empty);
    return;
  }
  tasks.forEach((t) => wrap.appendChild(buildTaskCard(t)));
}

/**
 * タスク1件のカード。マイタスク画面(担当者は常に自分なので省略)と部署の日別タスク画面
 * (⑦「誰が担当している仕事なのか」を確認できるよう担当者を表示する)の両方で使い回す。
 * ステータス変更(プルダウン・クイックボタン)は「自分が担当するタスク」のときだけ表示する(⑯)。
 */
function buildTaskCard(t, opts) {
  opts = opts || {};
  const showAssignee = !!opts.showAssignee;
  const isSelf = currentUser && t.assignedUserId === currentUser.userId;
  const canChangeStatus = !currentUser || isSelf;

  const card = document.createElement("div");
  card.className = "task-card" + (t.overdue ? " is-overdue" : "") + (isSelf ? " is-self" : "");
  card.dataset.id = t.id;

  const dueLabel = t.dueDate ? t.dueDate : "期限なし";
  card.innerHTML = `
    <div class="task-card-top">
      <span class="priority-badge priority-${t.priority}">${escapeHtml(t.priorityLabel)}</span>
      ${t.overdue ? '<span class="overdue-badge">期限超過</span>' : ""}
      ${isSelf ? '<span class="self-badge">自分の担当</span>' : ""}
    </div>
    <div class="task-card-title">${escapeHtml(t.title)}</div>
    <div class="task-card-meta">期限: ${escapeHtml(dueLabel)}</div>
    <div class="task-card-meta task-card-requester">依頼: ${escapeHtml(t.createdByName || "-")}</div>
    ${showAssignee ? `<div class="task-card-meta task-card-assignee">担当: ${escapeHtml(assigneeDisplay(t))}</div>` : ""}
    <div class="task-card-actions"></div>
  `;

  const actions = card.querySelector(".task-card-actions");
  if (canChangeStatus) {
    if (t.status === "UNRESOLVED") {
      actions.appendChild(makeQuickBtn("対応開始", () => changeStatus(t.id, "IN_PROGRESS")));
    } else if (t.status === "IN_PROGRESS") {
      actions.appendChild(makeQuickBtn("完了", () => changeStatus(t.id, "COMPLETED")));
    }

    const select = document.createElement("select");
    select.className = "task-card-status-select";
    ["UNRESOLVED", "IN_PROGRESS", "COMPLETED"].forEach((s) => {
      const opt = document.createElement("option");
      opt.value = s;
      opt.textContent = TASK_STATUS_LABEL[s];
      if (s === t.status) opt.selected = true;
      select.appendChild(opt);
    });
    select.addEventListener("click", (e) => e.stopPropagation());
    select.addEventListener("change", (e) => {
      e.stopPropagation();
      changeStatus(t.id, select.value);
    });
    actions.appendChild(select);
  } else {
    const badge = document.createElement("span");
    badge.className = `task-status-badge status-${t.status}`;
    badge.textContent = TASK_STATUS_LABEL[t.status];
    actions.appendChild(badge);
  }

  card.addEventListener("click", () => openDetailModal(t.id));
  return card;
}

function makeQuickBtn(label, onClick) {
  const btn = document.createElement("button");
  btn.type = "button";
  btn.className = "btn btn-secondary btn-sm";
  btn.textContent = label;
  btn.addEventListener("click", (e) => {
    e.stopPropagation();
    onClick();
  });
  return btn;
}

async function changeStatus(id, status) {
  try {
    const res = await fetch(`/api/tasks/${id}/status`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ status }),
    });
    if (!res.ok) {
      setStatus(await readErrorMessage(res, "ステータスの変更に失敗しました"), true);
      return;
    }
    setStatus("ステータスを変更しました");
    // 現在表示中の画面(マイタスク/部署の日別タスク)を両方最新化しておく
    await loadBoard();
    await loadTaskAlerts();
    if (!document.getElementById("deptDayView").hidden) {
      await loadDeptDay();
    }
  } catch (e) {
    console.error(e);
    setStatus("ステータスの変更に失敗しました", true);
  }
}

// ------------------------------------------------------------------
// 登録・編集フォーム(⑥⑨⑱)
// ------------------------------------------------------------------

function openCreateForm() {
  editingTaskId = null;
  document.getElementById("taskFormTitle").textContent = "タスクを追加";
  document.getElementById("taskSubmitBtn").textContent = "保存";
  document.getElementById("t_statusField").hidden = true;
  document.getElementById("t_assignedUser").closest(".field").hidden = false;
  document.getElementById("taskForm").reset();
  populateAssigneeSelect();
  document.getElementById("t_priority").value = "MEDIUM";
  resetSingleDayFields();
  document.getElementById("taskFormSection").hidden = false;
  document.getElementById("taskFormSection").scrollIntoView({ behavior: "smooth", block: "start" });
}

function openEditForm(t) {
  editingTaskId = t.id;
  document.getElementById("taskFormTitle").textContent = "タスクを編集";
  document.getElementById("taskSubmitBtn").textContent = "更新";
  document.getElementById("t_statusField").hidden = false;
  // 担当者は編集では変更不可(⑧所属情報は編集で変更できない)。登録時のみ選べる項目のため隠す
  document.getElementById("t_assignedUser").closest(".field").hidden = true;
  document.getElementById("t_title").value = t.title || "";
  document.getElementById("t_description").value = t.description || "";
  document.getElementById("t_priority").value = t.priority || "MEDIUM";
  resetSingleDayFields();
  document.getElementById("t_startDate").value = t.startDate || "";
  document.getElementById("t_dueDate").value = t.dueDate || "";
  document.getElementById("t_status").value = t.status || "UNRESOLVED";
  document.getElementById("t_notes").value = t.notes || "";
  document.getElementById("taskFormSection").hidden = false;
  document.getElementById("taskFormSection").scrollIntoView({ behavior: "smooth", block: "start" });
}

function closeForm() {
  document.getElementById("taskFormSection").hidden = true;
  editingTaskId = null;
}

// ⑫「1日タスク」: チェックすると開始日・期限の入力を隠し、実施日1つの入力に置き換える
function resetSingleDayFields() {
  document.getElementById("t_singleDay").checked = false;
  document.getElementById("t_singleDayDate").value = "";
  document.getElementById("t_singleDayDateField").hidden = true;
  document.getElementById("t_startDateField").hidden = false;
  document.getElementById("t_dueDateField").hidden = false;
}

function onSingleDayToggle(e) {
  const checked = e.target.checked;
  document.getElementById("t_singleDayDateField").hidden = !checked;
  document.getElementById("t_startDateField").hidden = checked;
  document.getElementById("t_dueDateField").hidden = checked;
  if (checked) {
    onSingleDayDateChange();
  }
}

function onSingleDayDateChange() {
  const v = document.getElementById("t_singleDayDate").value;
  document.getElementById("t_startDate").value = v;
  document.getElementById("t_dueDate").value = v;
}

async function onFormSubmit(e) {
  e.preventDefault();
  const dto = {
    title: document.getElementById("t_title").value.trim(),
    description: document.getElementById("t_description").value.trim() || null,
    priority: document.getElementById("t_priority").value,
    startDate: document.getElementById("t_startDate").value || null,
    dueDate: document.getElementById("t_dueDate").value || null,
    notes: document.getElementById("t_notes").value.trim() || null,
  };
  if (editingTaskId) {
    dto.status = document.getElementById("t_status").value;
  } else {
    // 担当者(②)は新規登録のときだけ選べる。編集フォームでは項目自体を隠しているため送らない
    const assignedVal = document.getElementById("t_assignedUser").value;
    if (assignedVal) dto.assignedUserId = Number(assignedVal);
  }

  try {
    const url = editingTaskId ? `/api/tasks/${editingTaskId}` : "/api/tasks";
    const method = editingTaskId ? "PUT" : "POST";
    const res = await fetch(url, {
      method,
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(dto),
    });
    if (!res.ok) {
      setStatus(await readErrorMessage(res, "タスクの保存に失敗しました"), true);
      return;
    }
    setStatus(editingTaskId ? "タスクを更新しました" : "タスクを登録しました");
    closeForm();
    await loadBoard();
    await loadTaskAlerts();
  } catch (e2) {
    console.error(e2);
    setStatus("タスクの保存に失敗しました", true);
  }
}

// ------------------------------------------------------------------
// 詳細モーダル(⑰)
// ------------------------------------------------------------------

let detailTaskCache = null;

async function openDetailModal(id) {
  try {
    const res = await fetch(`/api/tasks/${id}`);
    if (!res.ok) {
      setStatus(await readErrorMessage(res, "タスクの取得に失敗しました"), true);
      return;
    }
    const t = await res.json();
    detailTaskCache = t;
    document.getElementById("detailTitle").textContent = t.title;

    const grid = document.getElementById("detailGrid");
    grid.innerHTML = `
      <dt>依頼者</dt><dd>${escapeHtml(t.createdByName || "-")}</dd>
      <dt>担当</dt><dd>${escapeHtml(assigneeDisplay(t))}</dd>
      <dt>部署</dt><dd>${escapeHtml(t.departmentName || "-")}</dd>
      <dt>状態</dt><dd>${escapeHtml(t.statusLabel)}</dd>
      <dt>優先度</dt><dd>${escapeHtml(t.priorityLabel)}</dd>
      <dt>開始日</dt><dd>${escapeHtml(t.startDate || "-")}</dd>
      <dt>期限</dt><dd>${escapeHtml(t.dueDate || "-")}${t.overdue ? ' <span class="overdue-badge">期限超過</span>' : ""}</dd>
      <dt>仕事内容</dt><dd>${escapeHtml(t.description || "-")}</dd>
      <dt>備考</dt><dd>${escapeHtml(t.notes || "-")}</dd>
    `;

    const isAssignee = currentUser && t.assignedUserId === currentUser.userId;
    const isCreator = currentUser && t.createdByUserId === currentUser.userId;

    const statusWrap = document.getElementById("detailStatusChangeWrap");
    statusWrap.hidden = !isAssignee;
    document.getElementById("detailStatusSelect").value = t.status;

    document.getElementById("detailEditBtn").hidden = !(isAssignee || isCreator);
    document.getElementById("detailDeleteBtn").hidden = !isCreator;

    document.getElementById("detailModal").hidden = false;
  } catch (e) {
    console.error(e);
    setStatus("タスクの取得に失敗しました", true);
  }
}

function closeDetailModal() {
  document.getElementById("detailModal").hidden = true;
  detailTaskCache = null;
}

function onDetailEditClick() {
  if (!detailTaskCache) return;
  const t = detailTaskCache;
  closeDetailModal();
  openEditForm(t);
}

function onDetailDeleteClick() {
  if (!detailTaskCache) return;
  deleteTargetId = detailTaskCache.id;
  document.getElementById("deleteConfirmSummary").textContent = detailTaskCache.title;
  document.getElementById("deleteConfirmModal").hidden = false;
}

async function onDetailStatusChange() {
  if (!detailTaskCache) return;
  const status = document.getElementById("detailStatusSelect").value;
  await changeStatus(detailTaskCache.id, status);
  closeDetailModal();
}

function closeDeleteConfirm() {
  document.getElementById("deleteConfirmModal").hidden = true;
  deleteTargetId = null;
}

async function onDeleteConfirmed() {
  if (!deleteTargetId) return;
  try {
    const res = await fetch(`/api/tasks/${deleteTargetId}`, { method: "DELETE" });
    if (!res.ok) {
      setStatus(await readErrorMessage(res, "タスクの削除に失敗しました"), true);
      closeDeleteConfirm();
      closeDetailModal();
      return;
    }
    setStatus("タスクを削除しました");
    closeDeleteConfirm();
    closeDetailModal();
    await loadBoard();
    await loadTaskAlerts();
    if (!document.getElementById("deptDayView").hidden) {
      await loadDeptDay();
    }
  } catch (e) {
    console.error(e);
    setStatus("タスクの削除に失敗しました", true);
  }
}

// ------------------------------------------------------------------
// 部署の日別タスク(⑤⑥⑦⑨⑩⑪⑬⑭⑮)
// ------------------------------------------------------------------

function shiftDeptDay(deltaDays) {
  const d = new Date(deptDayDate + "T00:00:00");
  d.setDate(d.getDate() + deltaDays);
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  setDeptDay(`${d.getFullYear()}-${m}-${day}`);
}

/** 日付を指定して日別表示に切り替える。空文字を渡すと日付指定を解除する(全期間=①) */
function setDeptDay(isoDate) {
  deptDayDate = isoDate || "";
  document.getElementById("deptDayInput").value = deptDayDate;
  loadDeptDay();
}

function onDeptUserFilterChange() {
  deptDayUserFilter = document.getElementById("deptUserFilter").value;
  loadDeptDay();
}

function onDeptStatusTabClick(e) {
  const btn = e.target.closest("button[data-status]");
  if (!btn) return;
  deptDayStatusFilter = btn.dataset.status;
  document.querySelectorAll("#deptStatusTabs button").forEach((b) => b.classList.toggle("is-active", b === btn));
  loadDeptDay();
}

function onDeptGroupTabClick(e) {
  const btn = e.target.closest("button[data-group]");
  if (!btn) return;
  deptDayGroupMode = btn.dataset.group;
  document.querySelectorAll("#deptGroupTabs button").forEach((b) => b.classList.toggle("is-active", b === btn));
  document.getElementById("deptTableView").hidden = deptDayGroupMode !== "table";
  document.getElementById("deptStatusGroupView").hidden = deptDayGroupMode !== "status";
  document.getElementById("deptUserGroupView").hidden = deptDayGroupMode !== "user";
}

async function loadDeptDay() {
  try {
    const params = new URLSearchParams();
    if (deptDayDate) params.set("date", deptDayDate);
    if (deptDayUserFilter) params.set("assignedUserId", deptDayUserFilter);
    if (deptDayStatusFilter) params.set("status", deptDayStatusFilter);

    const res = await fetch(`/api/tasks/department/day?${params.toString()}`);
    if (!res.ok) {
      setStatus(await readErrorMessage(res, "部署のタスク取得に失敗しました"), true);
      return;
    }
    const dto = await res.json();
    renderDeptDay(dto);
    setStatus("");
  } catch (e) {
    console.error(e);
    setStatus("部署のタスク取得に失敗しました", true);
  }
}

function renderDeptDay(dto) {
  const dateLabel = dto.date ? formatDateLabel(dto.date) : "全期間";
  if (!dto.departmentName) {
    document.getElementById("deptDaySummaryTitle").textContent = `${dateLabel} 部署未所属`;
    document.getElementById("deptDayNote").textContent = "部署に所属していないため、部署のタスクは表示できません。";
  } else {
    document.getElementById("deptDaySummaryTitle").textContent = `${dateLabel} ${dto.departmentName} タスク状況`;
    document.getElementById("deptDayNote").textContent = `${dto.departmentName}に所属するメンバーのタスクを表示しています(同じ会社・同じ部署のメンバーのみ閲覧できます)。`;
  }
  document.getElementById("d_total").textContent = dto.summary ? dto.summary.totalCount : "-";
  renderSummaryInto(dto.summary, "d_unresolved", "d_inProgress", "d_completed", "d_overdue");

  // ⑨担当者ごとの件数(概況)。詳細は「担当者別」タブで確認できる
  const countGrid = document.getElementById("deptUserCountGrid");
  countGrid.innerHTML = "";
  (dto.byUser || []).forEach((group) => {
    const card = document.createElement("div");
    card.className = "task-progress-card";
    const name = currentUser && group.userId === currentUser.userId ? `${group.userName}(自分)` : group.userName;
    card.innerHTML = `<div class="tp-name">${escapeHtml(name)}</div><div class="tp-row"><span>担当タスク</span><span class="tp-value">${group.tasks.length}件</span></div>`;
    countGrid.appendChild(card);
  });

  // ①タスク名・担当者・状態・期限を並べた一覧表示
  renderDeptTable(dto.unresolved.concat(dto.inProgress).concat(dto.completed));

  renderDeptColumn("dcol_unresolved", dto.unresolved);
  renderDeptColumn("dcol_inProgress", dto.inProgress);
  renderDeptColumn("dcol_completed", dto.completed);

  const groupList = document.getElementById("deptUserGroupList");
  groupList.innerHTML = "";
  (dto.byUser || []).forEach((group) => {
    const box = document.createElement("div");
    box.className = "task-user-group";
    const header = document.createElement("h3");
    header.className = "task-user-group-title";
    const name = currentUser && group.userId === currentUser.userId ? `${group.userName}(自分)` : group.userName;
    header.textContent = `${name} (${group.tasks.length}件)`;
    box.appendChild(header);
    const list = document.createElement("div");
    list.className = "task-column-list";
    if (group.tasks.length === 0) {
      const empty = document.createElement("p");
      empty.className = "task-column-empty";
      empty.textContent = "タスクはありません";
      list.appendChild(empty);
    } else {
      group.tasks.forEach((t) => list.appendChild(buildTaskCard(t, { showAssignee: false })));
    }
    box.appendChild(list);
    groupList.appendChild(box);
  });
}

/** ①部署別タスク一覧の本体: タスク名・担当者・依頼者・状態・優先度・期限を必ず並べる */
function renderDeptTable(tasks) {
  const tbody = document.getElementById("deptTaskTbody");
  tbody.innerHTML = "";
  if (!tasks || tasks.length === 0) {
    tbody.innerHTML = '<tr class="empty-row"><td colspan="6">該当するタスクはありません</td></tr>';
    return;
  }
  tasks.forEach((t) => {
    const tr = document.createElement("tr");
    const isSelf = currentUser && t.assignedUserId === currentUser.userId;
    tr.className = "task-table-row" + (isSelf ? " is-self-row" : "");
    tr.innerHTML = `
      <td style="text-align:left;">${escapeHtml(t.title)}</td>
      <td class="${isSelf ? "task-self-cell" : ""}">${escapeHtml(assigneeDisplay(t))}</td>
      <td>${escapeHtml(t.createdByName || "-")}</td>
      <td><span class="task-status-badge status-${t.status}">${escapeHtml(t.statusLabel)}</span></td>
      <td><span class="priority-badge priority-${t.priority}">${escapeHtml(t.priorityLabel)}</span></td>
      <td>${escapeHtml(t.dueDate || "-")}${t.overdue ? ' <span class="overdue-badge">期限超過</span>' : ""}</td>
    `;
    tr.addEventListener("click", () => openDetailModal(t.id));
    tbody.appendChild(tr);
  });
}

function renderDeptColumn(elementId, tasks) {
  document.getElementById(elementId + "_count").textContent = tasks.length;
  const wrap = document.getElementById(elementId);
  wrap.innerHTML = "";
  if (tasks.length === 0) {
    const empty = document.createElement("p");
    empty.className = "task-column-empty";
    empty.textContent = "タスクはありません";
    wrap.appendChild(empty);
    return;
  }
  tasks.forEach((t) => wrap.appendChild(buildTaskCard(t, { showAssignee: true })));
}

function renderSummaryInto(summary, unresolvedId, inProgressId, completedId, overdueId) {
  if (!summary) return;
  document.getElementById(unresolvedId).textContent = summary.unresolvedCount;
  document.getElementById(inProgressId).textContent = summary.inProgressCount;
  document.getElementById(completedId).textContent = summary.completedCount;
  document.getElementById(overdueId).textContent = summary.overdueCount;
}

function formatDateLabel(isoDate) {
  if (!isoDate) return "-";
  const WEEKDAY = ["日", "月", "火", "水", "木", "金", "土"];
  const d = new Date(isoDate + "T00:00:00");
  return `${d.getFullYear()}/${d.getMonth() + 1}/${d.getDate()}(${WEEKDAY[d.getDay()]})`;
}
