// ------------------------------------------------------------------
// 管理者向け: 従業員管理(一覧・検索・部署絞り込み・詳細・新規登録・権限変更・部署変更)
// ------------------------------------------------------------------

let currentEmployeeId = null;
let currentEmployees = [];
let departments = [];

window.addEventListener("DOMContentLoaded", async () => {
  document.getElementById("searchForm").addEventListener("submit", (e) => {
    e.preventDefault();
    load();
  });
  document.getElementById("departmentFilter").addEventListener("change", load);
  document.getElementById("newEmployeeBtn").addEventListener("click", () => openCreateModal());
  document.getElementById("createCloseBtn").addEventListener("click", () => closeCreateModal());
  document.getElementById("createForm").addEventListener("submit", handleCreate);
  document.getElementById("detailCloseBtn").addEventListener("click", () => closeDetailModal());
  document.getElementById("profileSaveBtn").addEventListener("click", handleProfileSave);
  document.getElementById("roleSaveBtn").addEventListener("click", handleRoleSave);
  document.getElementById("departmentSaveBtn").addEventListener("click", handleDepartmentSave);
  document.getElementById("enabledSaveBtn").addEventListener("click", handleEnabledSave);
  document.getElementById("deleteEmployeeBtn").addEventListener("click", handleDelete);
  document.getElementById("resetPasswordBtn").addEventListener("click", handleResetPassword);
  document.getElementById("tempPasswordCloseBtn").addEventListener("click", closeTempPasswordModal);
  document.getElementById("tempPasswordCopyBtn").addEventListener("click", copyTempPassword);

  await loadCompanyAndDepartments();
  load();
  refreshAdminCountWarning();
});

/**
 * ログイン可能な(無効化されていない)管理者が2名未満なら警告を表示する。
 * 検索・部署絞り込み中の一覧(currentEmployees)は全従業員を表しているとは限らないため、
 * ここだけは絞り込み条件を付けずに全件を取得し直して数える。
 */
async function refreshAdminCountWarning() {
  const warningEl = document.getElementById("adminCountWarning");
  try {
    const res = await fetch("/api/admin/employees");
    if (!res.ok) return;
    const all = await res.json();
    const activeAdminCount = all.filter((e) => e.role === "ADMIN" && e.enabled !== false).length;
    if (activeAdminCount < 2) {
      warningEl.textContent =
        `⚠ 現在、ログイン可能な管理者が${activeAdminCount}名です。管理者がパスワードを忘れた場合に復旧できなくなるため、` +
        "管理者を2名以上にすることを強く推奨します。";
      warningEl.hidden = false;
    } else {
      warningEl.hidden = true;
    }
  } catch (e) {
    console.error(e);
  }
}

async function readErrorMessage(res, fallback) {
  try {
    const body = await res.json();
    return body?.message || fallback;
  } catch {
    return fallback;
  }
}

async function loadCompanyAndDepartments() {
  try {
    const meRes = await fetch("/api/auth/me");
    if (meRes.ok) {
      const me = await meRes.json();
      document.getElementById("companyLabel").textContent = me.companyName;
    }
  } catch (e) {
    console.error(e);
  }

  try {
    const res = await fetch("/api/admin/departments");
    if (!res.ok) throw new Error("部署一覧の取得に失敗しました");
    departments = await res.json();

    const filterOptions = ['<option value="">全部署</option>']
      .concat(departments.map((d) => `<option value="${d.id}">${escapeHtml(d.name)}(${d.employeeCount}人)</option>`));
    document.getElementById("departmentFilter").innerHTML = filterOptions.join("");

    const selectOptions = ['<option value="">未所属</option>']
      .concat(departments.map((d) => `<option value="${d.id}">${escapeHtml(d.name)}</option>`));
    document.getElementById("departmentSelect").innerHTML = selectOptions.join("");
    document.getElementById("cDepartment").innerHTML = selectOptions.join("");
  } catch (e) {
    console.error(e);
  }
}

async function load() {
  const statusEl = document.getElementById("listStatus");
  statusEl.textContent = "読み込み中...";
  statusEl.classList.remove("error");

  const keyword = document.getElementById("searchKeyword").value.trim();
  const departmentId = document.getElementById("departmentFilter").value;
  const params = new URLSearchParams();
  if (keyword) params.set("keyword", keyword);
  if (departmentId) params.set("departmentId", departmentId);

  try {
    const res = await fetch(`/api/admin/employees?${params.toString()}`);
    if (!res.ok) throw new Error(await readErrorMessage(res, "読み込みに失敗しました"));
    currentEmployees = await res.json();
    renderTable(currentEmployees);
    statusEl.textContent = "";
  } catch (e) {
    console.error(e);
    statusEl.textContent = e.message || "読み込みエラー";
    statusEl.classList.add("error");
  }
}

function renderTable(employees) {
  const tbody = document.getElementById("employeeTbody");
  tbody.innerHTML = "";
  if (!employees || employees.length === 0) {
    tbody.innerHTML = `<tr class="empty-row"><td colspan="6">従業員が見つかりません</td></tr>`;
    return;
  }
  employees.forEach((emp) => {
    const tr = document.createElement("tr");
    tr.className = "employee-row";
    if (emp.enabled === false) tr.classList.add("employee-row-disabled");
    tr.innerHTML = `
      <td>${escapeHtml(emp.fullName)}</td>
      <td>${escapeHtml(emp.loginId)}</td>
      <td>${escapeHtml(emp.departmentName ?? "未所属")}</td>
      <td>${escapeHtml(emp.position ?? "")}</td>
      <td>${emp.role === "ADMIN" ? "管理者" : "一般ユーザー"}</td>
      <td>${emp.enabled === false ? "無効化" : "有効"}</td>
      <td>${escapeHtml(emp.todayStatus ?? "")}</td>
    `;
    tr.addEventListener("click", () => openDetailModal(emp.userId));
    tbody.appendChild(tr);
  });
}

// ------------------------------------------------------------------
// 詳細・部署変更・権限変更モーダル
// ------------------------------------------------------------------
async function openDetailModal(userId) {
  currentEmployeeId = userId;
  const statusEl = document.getElementById("detailStatus");
  statusEl.textContent = "";
  statusEl.classList.remove("error");

  try {
    const res = await fetch(`/api/admin/employees/${userId}`);
    if (!res.ok) throw new Error(await readErrorMessage(res, "取得に失敗しました"));
    const emp = await res.json();

    document.getElementById("detailGrid").innerHTML = `
      <dt>ユーザーID</dt><dd>${escapeHtml(emp.loginId)}</dd>
      <dt>所属会社</dt><dd>${escapeHtml(emp.companyName)}</dd>
    `;
    document.getElementById("editFullName").value = emp.fullName ?? "";
    document.getElementById("editPosition").value = emp.position ?? "";
    document.getElementById("editEmail").value = emp.email ?? "";
    document.getElementById("departmentSelect").value = emp.departmentId ?? "";
    document.getElementById("roleSelect").value = emp.role;
    document.getElementById("enabledSelect").value = emp.enabled === false ? "false" : "true";
    document.getElementById("attendanceLink").href = `admin-attendance.html?userId=${userId}`;
    document.getElementById("detailModal").hidden = false;
  } catch (e) {
    console.error(e);
    alert(e.message || "取得に失敗しました");
  }
}

function closeDetailModal() {
  document.getElementById("detailModal").hidden = true;
  currentEmployeeId = null;
}

async function handleProfileSave() {
  if (!currentEmployeeId) return;
  const statusEl = document.getElementById("detailStatus");
  const fullName = document.getElementById("editFullName").value.trim();
  const position = document.getElementById("editPosition").value.trim();
  const email = document.getElementById("editEmail").value.trim();
  if (!fullName) {
    statusEl.textContent = "氏名を入力してください";
    statusEl.classList.add("error");
    return;
  }
  statusEl.textContent = "保存中...";
  statusEl.classList.remove("error");
  try {
    const res = await fetch(`/api/admin/employees/${currentEmployeeId}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ fullName, position: position || null, email: email || null }),
    });
    if (!res.ok) throw new Error(await readErrorMessage(res, "基本情報の保存に失敗しました"));
    statusEl.textContent = "保存しました";
    load();
  } catch (e) {
    console.error(e);
    statusEl.textContent = e.message || "保存エラー";
    statusEl.classList.add("error");
  }
}

async function handleDelete() {
  if (!currentEmployeeId) return;
  const fullName = document.getElementById("editFullName").value.trim();
  if (!confirm(`「${fullName || "この従業員"}」を削除します。よろしいですか？(元に戻せません)`)) return;

  const statusEl = document.getElementById("detailStatus");
  statusEl.textContent = "削除中...";
  statusEl.classList.remove("error");
  try {
    const res = await fetch(`/api/admin/employees/${currentEmployeeId}`, { method: "DELETE" });
    if (!res.ok) throw new Error(await readErrorMessage(res, "削除に失敗しました"));
    closeDetailModal();
    await loadCompanyAndDepartments();
    load();
    refreshAdminCountWarning();
  } catch (e) {
    console.error(e);
    statusEl.textContent = e.message || "削除エラー";
    statusEl.classList.add("error");
  }
}

async function handleRoleSave() {
  if (!currentEmployeeId) return;
  const statusEl = document.getElementById("detailStatus");
  const role = document.getElementById("roleSelect").value;
  statusEl.textContent = "保存中...";
  statusEl.classList.remove("error");
  try {
    const res = await fetch(`/api/admin/employees/${currentEmployeeId}/role`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ role }),
    });
    if (!res.ok) throw new Error(await readErrorMessage(res, "権限の変更に失敗しました"));
    statusEl.textContent = "保存しました";
    load();
    refreshAdminCountWarning();
  } catch (e) {
    console.error(e);
    statusEl.textContent = e.message || "保存エラー";
    statusEl.classList.add("error");
  }
}

async function handleDepartmentSave() {
  if (!currentEmployeeId) return;
  const statusEl = document.getElementById("detailStatus");
  const value = document.getElementById("departmentSelect").value;
  const departmentId = value ? Number(value) : null;
  statusEl.textContent = "保存中...";
  statusEl.classList.remove("error");
  try {
    const res = await fetch(`/api/admin/employees/${currentEmployeeId}/department`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ departmentId }),
    });
    if (!res.ok) throw new Error(await readErrorMessage(res, "部署の変更に失敗しました"));
    statusEl.textContent = "保存しました";
    await loadCompanyAndDepartments();
    load();
  } catch (e) {
    console.error(e);
    statusEl.textContent = e.message || "保存エラー";
    statusEl.classList.add("error");
  }
}

async function handleEnabledSave() {
  if (!currentEmployeeId) return;
  const statusEl = document.getElementById("detailStatus");
  const enabled = document.getElementById("enabledSelect").value === "true";
  if (!enabled && !confirm("このアカウントを無効化します。以後ログインできなくなります。よろしいですか？")) return;
  statusEl.textContent = "保存中...";
  statusEl.classList.remove("error");
  try {
    const res = await fetch(`/api/admin/employees/${currentEmployeeId}/enabled`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ enabled }),
    });
    if (!res.ok) throw new Error(await readErrorMessage(res, "アカウント状態の変更に失敗しました"));
    statusEl.textContent = "保存しました";
    load();
    refreshAdminCountWarning();
  } catch (e) {
    console.error(e);
    statusEl.textContent = e.message || "保存エラー";
    statusEl.classList.add("error");
  }
}

// ------------------------------------------------------------------
// パスワード強制リセット・一時パスワード表示モーダル
// ------------------------------------------------------------------
async function handleResetPassword() {
  if (!currentEmployeeId) return;
  const fullName = document.getElementById("editFullName").value.trim();
  if (!confirm(`「${fullName || "この従業員"}」のパスワードを強制的にリセットします。新しい一時パスワードが発行され、本人は次回ログイン後にパスワード変更が必須になります。よろしいですか？`)) return;

  const statusEl = document.getElementById("detailStatus");
  statusEl.textContent = "リセット中...";
  statusEl.classList.remove("error");
  try {
    const res = await fetch(`/api/admin/employees/${currentEmployeeId}/reset-password`, { method: "POST" });
    if (!res.ok) throw new Error(await readErrorMessage(res, "パスワードのリセットに失敗しました"));
    const result = await res.json();
    statusEl.textContent = "";
    document.getElementById("tempPasswordLoginId").textContent = result.loginId;
    document.getElementById("tempPasswordValue").textContent = result.temporaryPassword;
    document.getElementById("tempPasswordModal").hidden = false;
  } catch (e) {
    console.error(e);
    statusEl.textContent = e.message || "リセットエラー";
    statusEl.classList.add("error");
  }
}

/** 閉じたら画面上からも一時パスワードの文字列を消す(二度と参照できない設計を画面上でも徹底する) */
function closeTempPasswordModal() {
  document.getElementById("tempPasswordModal").hidden = true;
  document.getElementById("tempPasswordValue").textContent = "-";
  document.getElementById("tempPasswordLoginId").textContent = "-";
}

async function copyTempPassword() {
  const value = document.getElementById("tempPasswordValue").textContent;
  if (!value || value === "-") return;
  try {
    await navigator.clipboard.writeText(value);
  } catch (e) {
    console.error(e);
  }
}

// ------------------------------------------------------------------
// 新規登録モーダル
// ------------------------------------------------------------------
function openCreateModal() {
  document.getElementById("createForm").reset();
  document.getElementById("createStatus").textContent = "";
  document.getElementById("createStatus").classList.remove("error");
  document.getElementById("createModal").hidden = false;
}

function closeCreateModal() {
  document.getElementById("createModal").hidden = true;
}

async function handleCreate(e) {
  e.preventDefault();
  const statusEl = document.getElementById("createStatus");
  const deptValue = document.getElementById("cDepartment").value;
  const payload = {
    fullName: document.getElementById("cFullName").value.trim(),
    loginId: document.getElementById("cLoginId").value.trim(),
    password: document.getElementById("cPassword").value,
    confirmPassword: document.getElementById("cConfirmPassword").value,
    departmentId: deptValue ? Number(deptValue) : null,
    position: document.getElementById("cPosition").value.trim() || null,
    email: document.getElementById("cEmail").value.trim() || null,
    role: document.getElementById("cRole").value,
  };

  statusEl.textContent = "登録中...";
  statusEl.classList.remove("error");
  try {
    const res = await fetch("/api/admin/employees", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    if (!res.ok) throw new Error(await readErrorMessage(res, "登録に失敗しました"));
    statusEl.textContent = "登録しました";
    await loadCompanyAndDepartments();
    await load();
    refreshAdminCountWarning();
    setTimeout(closeCreateModal, 500);
  } catch (e2) {
    console.error(e2);
    statusEl.textContent = e2.message || "登録エラー";
    statusEl.classList.add("error");
  }
}

function escapeHtml(s) {
  const div = document.createElement("div");
  div.textContent = s ?? "";
  return div.innerHTML;
}
