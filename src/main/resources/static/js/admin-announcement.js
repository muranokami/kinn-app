// ------------------------------------------------------------------
// 管理者向けお知らせ管理。
// 会社は常にログイン中の管理者自身の会社(principal)からサーバー側で解決されるため、
// このJSからcompanyIdを送ることは一切ない。対象範囲(全社/部署)はdepartmentId(空欄=全社)で
// 指定する。
// ------------------------------------------------------------------

const IMPORTANCE_LABEL = { NORMAL: "通常", IMPORTANT: "重要" };

let departmentsCache = [];
let editingAnnouncementId = null;
let deleteTargetId = null;
/** announcementId -> {totalCount, readCount, unreadCount}(既読/未読人数バッジ表示用) */
let readCountsCache = new Map();

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

/** LocalDateTimeのJSON表現("2026-08-30T10:15:30"等)を <input type="datetime-local"> の値("2026-08-30T10:15")へ */
function toDatetimeLocalValue(iso) {
  if (!iso) return "";
  return iso.length >= 16 ? iso.substring(0, 16) : iso;
}

/** 表示用の "MM/DD HH:mm" 整形 */
function formatDateTime(iso) {
  if (!iso) return "-";
  const [datePart, timePart] = iso.split("T");
  if (!datePart || !timePart) return iso;
  const [, m, d] = datePart.split("-");
  return `${m}/${d} ${timePart.substring(0, 5)}`;
}

window.addEventListener("DOMContentLoaded", async () => {
  document.getElementById("createForm").addEventListener("submit", onCreateSubmit);
  document.getElementById("editForm").addEventListener("submit", onEditSubmit);
  document.getElementById("editCloseBtn").addEventListener("click", closeEditModal);
  document.getElementById("editDeleteBtn").addEventListener("click", onEditDeleteClick);
  document.getElementById("deleteConfirmCancelBtn").addEventListener("click", closeDeleteConfirm);
  document.getElementById("deleteConfirmOkBtn").addEventListener("click", onDeleteConfirmed);
  document.getElementById("readStatusCloseBtn").addEventListener("click", closeReadStatusModal);

  await loadDepartments();
  await loadAnnouncementList();
});

// ------------------------------------------------------------------
// 部署セレクトの用意(対象範囲: 先頭の「全社」オプションは残す)
// ------------------------------------------------------------------

async function loadDepartments() {
  try {
    const res = await fetch("/api/admin/departments");
    if (!res.ok) return;
    departmentsCache = await res.json();

    const cSel = document.getElementById("c_department");
    const eSel = document.getElementById("e_department");
    [cSel, eSel].forEach((sel) => {
      Array.from(sel.querySelectorAll("option")).forEach((o, i) => {
        if (i !== 0) o.remove(); // 「全社」オプションだけ残す
      });
    });
    departmentsCache.forEach((d) => {
      [cSel, eSel].forEach((sel) => {
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

// ------------------------------------------------------------------
// 投稿一覧
// ------------------------------------------------------------------

async function loadAnnouncementList() {
  try {
    const res = await fetch("/api/admin/announcements");
    if (!res.ok) {
      setStatus(await readErrorMessage(res, "投稿一覧の取得に失敗しました"), true);
      return;
    }
    const list = await res.json();
    await loadReadCounts();
    renderTable(list);
  } catch (e) {
    console.error(e);
    setStatus("投稿一覧の取得に失敗しました", true);
  }
}

/** 各お知らせの既読/未読人数(バッジ表示用)。氏名までは持たない軽量な一覧 */
async function loadReadCounts() {
  readCountsCache = new Map();
  try {
    const res = await fetch("/api/admin/announcements/read-counts");
    if (!res.ok) return; // バッジは補助情報のため、取得失敗しても一覧本体の表示は続ける
    const counts = await res.json();
    counts.forEach((c) => readCountsCache.set(c.announcementId, c));
  } catch (e) {
    console.error(e);
  }
}

function renderTable(list) {
  const tbody = document.getElementById("announcementTbody");
  tbody.innerHTML = "";
  if (!list || list.length === 0) {
    tbody.innerHTML = '<tr class="empty-row"><td colspan="9">投稿はまだありません</td></tr>';
    return;
  }
  list.forEach((a) => {
    const tr = document.createElement("tr");
    tr.innerHTML = `
      <td><span class="importance-badge importance-${a.importance}">${escapeHtml(IMPORTANCE_LABEL[a.importance] || a.importance)}</span></td>
      <td><span class="scope-badge">${escapeHtml(a.departmentName || "全社")}</span></td>
      <td class="col-title">${escapeHtml(a.title)}</td>
      <td class="col-body">${escapeHtml((a.body || "").slice(0, 40))}${(a.body || "").length > 40 ? "…" : ""}</td>
      <td>${formatDateTime(a.publishedAt)}</td>
      <td>${a.expiresAt ? formatDateTime(a.expiresAt) : "無期限"}</td>
      <td>${escapeHtml(a.createdByName || "-")}</td>
      <td class="read-count-cell"></td>
      <td class="row-actions"></td>
    `;
    const readCountTd = tr.querySelector(".read-count-cell");
    const counts = readCountsCache.get(a.id);
    const badge = document.createElement("button");
    badge.type = "button";
    badge.className = "read-count-badge" + (counts && counts.totalCount > 0 && counts.unreadCount === 0 ? " all-read" : "");
    badge.textContent = counts ? `既読 ${counts.readCount}/${counts.totalCount}人` : "既読 -/-人";
    badge.addEventListener("click", () => openReadStatusModal(a));
    readCountTd.appendChild(badge);
    const actionsTd = tr.querySelector(".row-actions");
    const editBtn = document.createElement("button");
    editBtn.type = "button";
    editBtn.className = "btn btn-secondary btn-sm";
    editBtn.textContent = "編集";
    editBtn.addEventListener("click", () => openEditModal(a));
    const delBtn = document.createElement("button");
    delBtn.type = "button";
    delBtn.className = "btn btn-danger btn-sm";
    delBtn.textContent = "削除";
    delBtn.addEventListener("click", () => openDeleteConfirm(a));
    actionsTd.appendChild(editBtn);
    actionsTd.appendChild(delBtn);
    tbody.appendChild(tr);
  });
}

// ------------------------------------------------------------------
// 投稿
// ------------------------------------------------------------------

async function onCreateSubmit(e) {
  e.preventDefault();
  const deptVal = document.getElementById("c_department").value;
  const dto = {
    departmentId: deptVal ? Number(deptVal) : null,
    importance: document.getElementById("c_importance").value,
    title: document.getElementById("c_title").value.trim(),
    body: document.getElementById("c_body").value.trim(),
    publishedAt: document.getElementById("c_publishedAt").value || null,
    expiresAt: document.getElementById("c_expiresAt").value || null,
  };
  try {
    const res = await fetch("/api/admin/announcements", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(dto),
    });
    if (!res.ok) {
      setStatus(await readErrorMessage(res, "お知らせの投稿に失敗しました"), true);
      return;
    }
    setStatus("お知らせを投稿しました");
    document.getElementById("createForm").reset();
    document.getElementById("c_importance").value = "NORMAL";
    await loadAnnouncementList();
  } catch (e2) {
    console.error(e2);
    setStatus("お知らせの投稿に失敗しました", true);
  }
}

// ------------------------------------------------------------------
// 編集・削除
// ------------------------------------------------------------------

function openEditModal(a) {
  editingAnnouncementId = a.id;
  document.getElementById("editStatus").textContent = "";
  document.getElementById("e_department").value = a.departmentId ? String(a.departmentId) : "";
  document.getElementById("e_importance").value = a.importance;
  document.getElementById("e_title").value = a.title || "";
  document.getElementById("e_body").value = a.body || "";
  document.getElementById("e_publishedAt").value = toDatetimeLocalValue(a.publishedAt);
  document.getElementById("e_expiresAt").value = toDatetimeLocalValue(a.expiresAt);
  document.getElementById("editModal").hidden = false;
}

function closeEditModal() {
  document.getElementById("editModal").hidden = true;
  editingAnnouncementId = null;
}

async function onEditSubmit(e) {
  e.preventDefault();
  if (!editingAnnouncementId) return;
  const deptVal = document.getElementById("e_department").value;
  const dto = {
    departmentId: deptVal ? Number(deptVal) : null,
    importance: document.getElementById("e_importance").value,
    title: document.getElementById("e_title").value.trim(),
    body: document.getElementById("e_body").value.trim(),
    publishedAt: document.getElementById("e_publishedAt").value || null,
    expiresAt: document.getElementById("e_expiresAt").value || null,
  };
  try {
    const res = await fetch(`/api/admin/announcements/${editingAnnouncementId}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(dto),
    });
    if (!res.ok) {
      document.getElementById("editStatus").textContent = await readErrorMessage(res, "お知らせの更新に失敗しました");
      document.getElementById("editStatus").classList.add("error");
      return;
    }
    closeEditModal();
    setStatus("お知らせを更新しました");
    await loadAnnouncementList();
  } catch (e2) {
    console.error(e2);
    document.getElementById("editStatus").textContent = "お知らせの更新に失敗しました";
    document.getElementById("editStatus").classList.add("error");
  }
}

function onEditDeleteClick() {
  if (!editingAnnouncementId) return;
  deleteTargetId = editingAnnouncementId;
  document.getElementById("deleteConfirmSummary").textContent = document.getElementById("e_title").value;
  document.getElementById("deleteConfirmModal").hidden = false;
}

function openDeleteConfirm(a) {
  deleteTargetId = a.id;
  document.getElementById("deleteConfirmSummary").textContent = a.title;
  document.getElementById("deleteConfirmModal").hidden = false;
}

function closeDeleteConfirm() {
  document.getElementById("deleteConfirmModal").hidden = true;
  deleteTargetId = null;
}

async function onDeleteConfirmed() {
  if (!deleteTargetId) return;
  try {
    const res = await fetch(`/api/admin/announcements/${deleteTargetId}`, { method: "DELETE" });
    if (!res.ok) {
      setStatus(await readErrorMessage(res, "お知らせの削除に失敗しました"), true);
      closeDeleteConfirm();
      closeEditModal();
      return;
    }
    setStatus("お知らせを削除しました");
    closeDeleteConfirm();
    closeEditModal();
    await loadAnnouncementList();
  } catch (e) {
    console.error(e);
    setStatus("お知らせの削除に失敗しました", true);
  }
}

// ------------------------------------------------------------------
// 既読状況(既読者一覧・未読者一覧)
// ------------------------------------------------------------------

async function openReadStatusModal(a) {
  document.getElementById("readStatusMsg").textContent = "読み込み中…";
  document.getElementById("readStatusMsg").classList.remove("error");
  document.getElementById("readStatusHeader").innerHTML = "";
  document.getElementById("readUserList").innerHTML = "";
  document.getElementById("unreadUserList").innerHTML = "";
  document.getElementById("readStatusModal").hidden = false;

  try {
    const res = await fetch(`/api/admin/announcements/${a.id}/read-status`);
    if (!res.ok) {
      document.getElementById("readStatusMsg").textContent = await readErrorMessage(res, "既読状況の取得に失敗しました");
      document.getElementById("readStatusMsg").classList.add("error");
      return;
    }
    const status = await res.json();
    renderReadStatus(status);
  } catch (e) {
    console.error(e);
    document.getElementById("readStatusMsg").textContent = "既読状況の取得に失敗しました";
    document.getElementById("readStatusMsg").classList.add("error");
  }
}

function renderReadStatus(status) {
  document.getElementById("readStatusMsg").textContent = "";
  document.getElementById("readStatusHeader").innerHTML = `
    <span class="scope-badge">${escapeHtml(status.departmentName || "全社")}</span>
    <span class="read-status-title">${escapeHtml(status.title)}</span>
  `;
  document.getElementById("readCount").textContent = String(status.readCount);
  document.getElementById("unreadCount").textContent = String(status.unreadCount);

  const readListEl = document.getElementById("readUserList");
  readListEl.innerHTML = "";
  if (!status.readUsers || status.readUsers.length === 0) {
    readListEl.innerHTML = '<li class="empty-item">既読者はまだいません</li>';
  } else {
    status.readUsers.forEach((u) => {
      const li = document.createElement("li");
      li.innerHTML = `<span>${escapeHtml(u.fullName)}</span><span class="read-at">${formatDateTime(u.readAt)}</span>`;
      readListEl.appendChild(li);
    });
  }

  const unreadListEl = document.getElementById("unreadUserList");
  unreadListEl.innerHTML = "";
  if (!status.unreadUsers || status.unreadUsers.length === 0) {
    unreadListEl.innerHTML = '<li class="empty-item">未読者はいません</li>';
  } else {
    status.unreadUsers.forEach((u) => {
      const li = document.createElement("li");
      li.innerHTML = `<span>${escapeHtml(u.fullName)}</span>`;
      unreadListEl.appendChild(li);
    });
  }
}

function closeReadStatusModal() {
  document.getElementById("readStatusModal").hidden = true;
}
